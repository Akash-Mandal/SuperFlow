package com.superflow.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.superflow.core.time.SfTime
import com.superflow.data.Prefs
import com.superflow.data.Repository
import com.superflow.data.model.CheckIn
import com.superflow.data.model.CheckInResult
import com.superflow.data.model.Level
import com.superflow.domain.Actor
import com.superflow.domain.CommandBus
import com.superflow.notify.Reminders
import com.superflow.widget.TodayWidget
import java.time.Duration
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Deferrable background work.
 *
 * The plan specifies WorkManager for deferrable jobs, with AlarmManager kept
 * only for exact user-facing reminders. Two jobs run here:
 *
 *  - Daily rollover: closes out yesterday's unresolved opportunities so the
 *    "never miss twice" logic and the widget stay correct after midnight,
 *    a reboot, or time-zone travel.
 *  - Reminder refresh: re-arms alarms, which Android drops on reboot and
 *    can drop after long doze periods.
 *
 * Rollover is deliberately conservative: it records nothing for paused days,
 * planned skips, or days the user has already acted on.
 */
class DailyRolloverWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val repo = Repository.get(applicationContext)
            val today = repo.clock.today()
            closeOut(repo, today.minusDays(1))
            Reminders.rescheduleAll(applicationContext)
            TodayWidget.refresh(applicationContext)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    /**
     * Materialises misses for a finished day.
     *
     * Misses are otherwise only implied by the opportunity series. Writing them
     * down once the day is over makes the Recovery Center and Activity trail
     * honest, and keeps history stable if the schedule changes later.
     */
    private fun closeOut(repo: Repository, date: LocalDate) {
        val pauses = repo.pauses()
        val iso = SfTime.format(date)
        val existing = repo.checkInsFor(iso).map { it.habitId }.toSet()

        for (habit in repo.habitsForDay(date)) {
            if (habit.id in existing) continue
            val paused = pauses.any {
                (it.habitId == null || it.habitId == habit.id) && it.covers(date)
            }
            if (paused) continue
            // A flexible habit is judged on its weekly quota, so a single
            // unused day is never a miss.
            val recurrence = com.superflow.core.schedule.Recurrence.decode(habit.recurrenceRule)
            if (recurrence.isFlexible) continue

            repo.saveCheckIn(
                CheckIn(
                    habitId = habit.id,
                    date = iso,
                    result = if (habit.mode == com.superflow.data.model.HabitMode.REDUCE)
                        CheckInResult.SLIPPED else CheckInResult.MISSED,
                    level = Level.STANDARD,
                    note = "auto: day ended"
                )
            )
        }
    }

    companion object {
        const val NAME = "superflow_daily_rollover"
    }
}

/** Re-arms reminders; cheap, and covers alarms lost to reboot or doze. */
class ReminderRefreshWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        Reminders.rescheduleAll(applicationContext)
        TodayWidget.refresh(applicationContext)
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }

    companion object {
        const val NAME = "superflow_reminder_refresh"
    }
}

object BackgroundWork {

    /** Enqueues the periodic jobs. Safe to call on every app start. */
    fun schedule(context: Context) {
        val manager = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return

        val rollover = PeriodicWorkRequestBuilder<DailyRolloverWorker>(6, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(false).build())
            .setInitialDelay(Duration.ofMinutes(15))
            .build()
        runCatching {
            manager.enqueueUniquePeriodicWork(
                DailyRolloverWorker.NAME, ExistingPeriodicWorkPolicy.KEEP, rollover
            )
        }

        val refresh = PeriodicWorkRequestBuilder<ReminderRefreshWorker>(1, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().build())
            .build()
        runCatching {
            manager.enqueueUniquePeriodicWork(
                ReminderRefreshWorker.NAME, ExistingPeriodicWorkPolicy.KEEP, refresh
            )
        }
    }

    fun cancel(context: Context) {
        runCatching {
            WorkManager.getInstance(context).apply {
                cancelUniqueWork(DailyRolloverWorker.NAME)
                cancelUniqueWork(ReminderRefreshWorker.NAME)
            }
        }
    }
}
