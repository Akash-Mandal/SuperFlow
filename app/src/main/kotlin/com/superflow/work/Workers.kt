package com.superflow.work

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.superflow.R
import com.superflow.core.schedule.Opportunities
import com.superflow.core.schedule.Recurrence
import com.superflow.core.time.SfTime
import com.superflow.data.DataPolicy
import com.superflow.data.Prefs
import com.superflow.data.Repository
import com.superflow.data.model.CheckIn
import com.superflow.data.model.CheckInResult
import com.superflow.data.model.Habit
import com.superflow.data.model.Level
import com.superflow.domain.Actor
import com.superflow.domain.CommandBus
import com.superflow.domain.Insights
import com.superflow.notify.Reminders
import com.superflow.ui.MainActivity
import com.superflow.ui.review.ReviewActivity
import com.superflow.widget.TodayWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
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

/**
 * Automatic backup to local storage.
 *
 * Runs on the schedule the user configured (daily / every 3 days / weekly),
 * writes an all-inclusive JSON export to `filesDir/backups`, prunes to the
 * configured number of kept backups, and posts a silent status notification
 * only when something goes wrong (success is quiet by design).
 */
class BackupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = Prefs.get(applicationContext)
        if (!prefs.autoBackupEnabled) return Result.success()
        val repo = Repository.get(applicationContext)
        return try {
            withContext(Dispatchers.IO) {
                val json = DataPolicy.exportFull(repo, prefs).toString(2)
                val dir = File(applicationContext.filesDir, "backups").apply { mkdirs() }
                val date = SfTime.format(repo.clock.today())
                File(dir, "superflow-backup-$date.json").writeText(json)
                dir.listFiles()?.sortedByDescending { it.lastModified() }
                    ?.drop(prefs.maxBackups)?.forEach { it.delete() }
            }
            Result.success()
        } catch (e: Exception) {
            Reminders.notify(
                applicationContext, 8000, Reminders.CHANNEL_BACKUP,
                "Backup failed", "SuperFlow could not save the automatic backup: ${e.message}"
            )
            Result.retry()
        }
    }

    companion object {
        const val NAME = "superflow_backup"

        fun repeatIntervalMillis(prefs: Prefs): Long = when (prefs.autoBackupFrequency) {
            "weekly" -> TimeUnit.DAYS.toMillis(7)
            "3days" -> TimeUnit.DAYS.toMillis(3)
            else -> TimeUnit.DAYS.toMillis(1)
        }
    }
}

/** One week's derived numbers, for the weekly summary notification. */
data class WeekSummary(
    val consistency: Int,
    val repetitions: Int,
    val recoveries: Int,
    val bestHabit: Habit?
)

/**
 * Weekly report, posted on the configured day (Sunday by default).
 *
 * Runs daily and no-ops unless today is the configured day, which keeps the
 * WorkManager schedule trivial and robust to doze, reboots and time-zone
 * changes.
 */
class WeeklySummaryWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        val prefs = Prefs.get(applicationContext)
        if (!prefs.weeklySummaryEnabled) return Result.success()
        val repo = Repository.get(applicationContext)
        if (repo.clock.today().dayOfWeek.value != prefs.weeklySummaryDay) return Result.success()
        postSummary(repo)
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }

    private fun postSummary(repo: Repository) {
        val summary = weekStats(repo)
        val text = buildString {
            append("Consistency: ${summary.consistency}%")
            append(" · Repetitions: ${summary.repetitions}")
            append(" · Recoveries: ${summary.recoveries}")
            summary.bestHabit?.let { append("\nBest habit: ${it.title}") }
        }

        val insights = PendingIntent.getActivity(
            applicationContext, 1,
            Intent(applicationContext, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_TAB, "insights"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val review = PendingIntent.getActivity(
            applicationContext, 2,
            Intent(applicationContext, ReviewActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val actions = listOf(
            NotificationCompat.Action.Builder(R.drawable.ic_insights, "View full report", insights).build(),
            NotificationCompat.Action.Builder(R.drawable.ic_scorecard, "Start review", review).build()
        )
        Reminders.notify(
            applicationContext, 8001, Reminders.CHANNEL_WEEKLY_SUMMARY,
            "Your Week in SuperFlow", text, actions
        )
    }

    private fun weekStats(repo: Repository): WeekSummary {
        val today = repo.clock.today()
        var hits = 0
        var opportunities = 0
        var repetitions = 0
        var recoveries = 0
        var best: Pair<Habit, Int>? = null

        for (h in repo.habits()) {
            val series = Insights.seriesFor(repo, h, 7, today)
            val recurrence = Recurrence.decode(h.recurrenceRule)
            val (hh, oo) = if (recurrence is Recurrence.TimesPerWeek) {
                Opportunities.quotaAdherence(series, recurrence.times)
            } else {
                Opportunities.adherence(series)
            }
            hits += hh
            opportunities += oo
            recoveries += Opportunities.recoveries(series)
            repetitions += series.count { it.succeeded }
            val run = Opportunities.bestRun(series)
            if (best == null || run > best.second) best = h to run
        }
        val consistency = if (opportunities == 0) 0 else (hits * 100) / opportunities
        return WeekSummary(consistency, repetitions, recoveries, best?.first)
    }

    companion object {
        const val NAME = "superflow_weekly_summary"
    }
}

object BackgroundWork {

    /** Enqueues the periodic jobs. Safe to call on every app start. */
    fun schedule(context: Context) {
        val manager = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return
        val prefs = Prefs.get(context)

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

        if (prefs.autoBackupEnabled) {
            val intervalMillis = BackupWorker.repeatIntervalMillis(prefs)
            val backup = PeriodicWorkRequestBuilder<BackupWorker>(intervalMillis, TimeUnit.MILLISECONDS)
                .setConstraints(Constraints.Builder().build())
                .setInitialDelay(backupDelay(intervalMillis))
                .build()
            runCatching {
                manager.enqueueUniquePeriodicWork(
                    BackupWorker.NAME, ExistingPeriodicWorkPolicy.UPDATE, backup
                )
            }
        } else {
            runCatching { manager.cancelUniqueWork(BackupWorker.NAME) }
        }

        val weekly = PeriodicWorkRequestBuilder<WeeklySummaryWorker>(1, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().build())
            .setInitialDelay(summaryDelay(prefs))
            .build()
        runCatching {
            manager.enqueueUniquePeriodicWork(
                WeeklySummaryWorker.NAME, ExistingPeriodicWorkPolicy.UPDATE, weekly
            )
        }
    }

    fun cancel(context: Context) {
        runCatching {
            WorkManager.getInstance(context).apply {
                cancelUniqueWork(DailyRolloverWorker.NAME)
                cancelUniqueWork(ReminderRefreshWorker.NAME)
                cancelUniqueWork(BackupWorker.NAME)
                cancelUniqueWork(WeeklySummaryWorker.NAME)
            }
        }
    }

    /** Delay the first backup so it lands shortly after the next interval. */
    private fun backupDelay(intervalMillis: Long): Duration = Duration.ofMillis(intervalMillis)

    /** Delay until the next occurrence of the configured day + time. */
    private fun summaryDelay(prefs: Prefs): Duration {
        val zone = java.time.ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        val minutes = SfTime.minutesOfDay(prefs.weeklySummaryTime).coerceAtLeast(0)
        var target = now.withHour(minutes / 60).withMinute(minutes % 60)
            .withSecond(0).withNano(0)
        var guard = 0
        while (target.dayOfWeek.value != prefs.weeklySummaryDay && guard++ < 7) {
            target = target.plusDays(1)
        }
        if (!target.isAfter(now)) target = target.plusDays(7)
        return Duration.ofMillis(java.time.Duration.between(now, target).toMillis().coerceAtLeast(60_000))
    }
}
