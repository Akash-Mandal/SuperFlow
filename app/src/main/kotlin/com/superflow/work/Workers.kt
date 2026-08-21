package com.superflow.work

import android.content.Context
import android.util.Log
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
            val prefs = Prefs.get(applicationContext)
            val today = repo.clock.today()
            closeOut(repo, today.minusDays(1))
            // Evaluate growth plans daily.
            com.superflow.domain.GrowthEngine.evaluate(repo, prefs)
            // rescheduleAllNow runs on the serialized background lane and
            // completes before this worker reports done.
            Reminders.rescheduleAllNow(applicationContext)
            TodayWidget.refresh(applicationContext, force = true)
            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "Daily rollover failed; will retry", e)
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
        private const val TAG = "DailyRollover"
        const val NAME = "superflow_daily_rollover"
    }
}

/** Re-arms reminders; cheap, and covers alarms lost to reboot or doze. */
class ReminderRefreshWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        Reminders.rescheduleAllNow(applicationContext)
        TodayWidget.refresh(applicationContext, force = true)
        Result.success()
    } catch (e: Exception) {
        Log.w(TAG, "Reminder refresh failed; will retry", e)
        Result.retry()
    }

    companion object {
        private const val TAG = "ReminderRefresh"
        const val NAME = "superflow_reminder_refresh"
    }
}

object BackgroundWork {

    private const val TAG = "BackgroundWork"

    /**
     * Enqueues the periodic jobs. Safe to call on every app start; must be
     * called on a background thread (WorkManager's first getInstance opens
     * its internal database). In a standard Gradle build WorkManager is
     * initialized by the androidx.startup provider the manifest merger
     * contributes, so there is exactly one initialization.
     */
    fun schedule(context: Context) {
        val manager = try {
            WorkManager.getInstance(context)
        } catch (e: Exception) {
            // Work is optional: the app is fully functional without the
            // rollover job, but a failure must not vanish.
            Log.w(TAG, "WorkManager unavailable; periodic jobs not scheduled", e)
            return
        }

        val rollover = PeriodicWorkRequestBuilder<DailyRolloverWorker>(6, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(false).build())
            .setInitialDelay(Duration.ofMinutes(15))
            .build()
        try {
            manager.enqueueUniquePeriodicWork(
                DailyRolloverWorker.NAME, ExistingPeriodicWorkPolicy.KEEP, rollover
            )
        } catch (e: Exception) {
            Log.w(TAG, "Could not schedule daily rollover", e)
        }

        val refresh = PeriodicWorkRequestBuilder<ReminderRefreshWorker>(1, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().build())
            .build()
        try {
            manager.enqueueUniquePeriodicWork(
                ReminderRefreshWorker.NAME, ExistingPeriodicWorkPolicy.KEEP, refresh
            )
        } catch (e: Exception) {
            Log.w(TAG, "Could not schedule reminder refresh", e)
        }

        val proactive = PeriodicWorkRequestBuilder<ProactiveAiWorker>(6, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(false).build())
            .setInitialDelay(java.time.Duration.ofMinutes(30))
            .build()
        runCatching {
            manager.enqueueUniquePeriodicWork(
                ProactiveAiWorker.NAME, ExistingPeriodicWorkPolicy.KEEP, proactive
            )
        }
    }

    fun cancel(context: Context) {
        try {
            WorkManager.getInstance(context).apply {
                cancelUniqueWork(DailyRolloverWorker.NAME)
                cancelUniqueWork(ReminderRefreshWorker.NAME)
                cancelUniqueWork(ProactiveAiWorker.NAME)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not cancel periodic work", e)
        }
    }
}
