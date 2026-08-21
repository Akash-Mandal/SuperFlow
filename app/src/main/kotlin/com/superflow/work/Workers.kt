package com.superflow.work

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.superflow.data.Backups
import com.superflow.data.Prefs
import com.superflow.core.time.SfTime
import com.superflow.data.Repository
import com.superflow.data.model.CheckIn
import com.superflow.data.model.CheckInResult
import com.superflow.data.model.Level
import com.superflow.domain.Insights
import com.superflow.notify.Reminders
import com.superflow.widget.TodayWidget
import java.time.Duration
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Deferrable background work.
 *
 * The plan specifies WorkManager for deferrable jobs, with AlarmManager kept
 * only for exact user-facing reminders. Six periodic jobs run here:
 *
 *  - Daily rollover: closes out yesterday's unresolved opportunities so the
 *    "never miss twice" logic and the widget stay correct after midnight,
 *    a reboot, or time-zone travel.
 *  - Reminder refresh: re-arms alarms, which Android drops on reboot and
 *    can drop after long doze periods.
 *  - Milestones: detects first rep, 7/21-day runs, 21/100 reps and 90%
 *    consistency, notifying once per threshold (idempotent via WorkPrefs).
 *  - Weekly review: pre-generates a weekly review draft on Sundays.
 *  - Snapshot cleanup: deletes snapshots older than 30 days, keeps max 20.
 *  - Widget refresh: periodically updates the home-screen widget.
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

/**
 * Detects milestone thresholds once a day and records them so the UI can
 * surface an acknowledgment (first check-in, 7-day run, 21 reps, etc.).
 *
 * Detection is idempotent: each threshold is written to a marker pref keyed
 * by habit id + milestone, so a re-run never double-counts.
 */
class MilestoneWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        val repo = Repository.get(applicationContext)
        val prefs = WorkPrefs.get(applicationContext)
        val today = repo.clock.today()
        for (h in repo.habits()) {
            val stats = Insights.forHabit(repo, h, today)
            announce(prefs, h.id, "first", stats.repetitions >= 1,
                "First repetition", "${h.title}: your first repetition is on the books.")
            announce(prefs, h.id, "run7", stats.bestRun >= 7,
                "7-day run", "${h.title}: a 7-day run. That is a real streak.")
            announce(prefs, h.id, "run21", stats.bestRun >= 21,
                "21-day run", "${h.title}: 21 days in a row.")
            announce(prefs, h.id, "reps21", stats.repetitions >= 21,
                "21 repetitions", "${h.title}: 21 repetitions total.")
            announce(prefs, h.id, "reps100", stats.repetitions >= 100,
                "100 repetitions", "${h.title}: 100 repetitions. A habit is forming.")
            announce(prefs, h.id, "consistency90",
                stats.hasEnoughData && stats.consistency30 >= 90,
                "90% consistency", "${h.title}: ${stats.consistency30}% consistency over 30 days.")
        }
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }

    /** Records a milestone and posts a notification exactly once when first reached. */
    private fun announce(
        prefs: WorkPrefs, habitId: String, name: String, reached: Boolean,
        title: String, text: String
    ) {
        if (!reached || !prefs.markMilestone(habitId, name)) return
        val id = (habitId + name).hashCode() and 0x7FFFFFFF
        Reminders.notify(
            applicationContext, 8000 + (id % 1000),
            Reminders.CHANNEL_MILESTONES, title, text
        )
    }

    companion object {
        const val NAME = "superflow_milestones"
    }
}

/**
 * Pre-generates a weekly review on Sundays so the Reviews screen has a draft
 * ready when the user opens it. The review text is derived from the same
 * [Insights] numbers the Insights tab shows; it is not AI-generated.
 */
class ReviewWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        val repo = Repository.get(applicationContext)
        val prefs = WorkPrefs.get(applicationContext)
        val today = repo.clock.today()
        // The weekly draft is prepared on Sunday (the end of the ISO week).
        // Runs on other days simply no-op so WorkManager's daily cadence is
        // safe if the Sunday execution was deferred by doze.
        if (today.dayOfWeek != java.time.DayOfWeek.SUNDAY) return Result.success()
        val label = "Week of ${SfTime.shortDay(SfTime.startOfWeek(today))}"
        // One draft per ISO week.
        if (prefs.lastReviewWeek() != label) {
            val stats = Insights.allStats(repo, today)
            val reps = stats.sumOf { it.repetitions }
            val recoveries = stats.sumOf { it.recoveries }
            val best = stats.filter { it.hasEnoughData }.maxByOrNull { it.consistency30 }
            val draft = buildString {
                append("Auto-draft for $label\n\n")
                append("Repetitions this period: $reps\n")
                append("Recoveries after a miss: $recoveries\n")
                best?.let {
                    append("Most consistent: ${it.habit.title} (${it.consistency30}%")
                    if (it.hasEnoughData) append(" of ${it.opportunities30} opportunities")
                    append(")\n")
                }
                append("\nWhat worked? What did not? What is one system change?")
            }
            val review = com.superflow.data.model.Review(
                kind = com.superflow.data.model.ReviewKind.WEEKLY,
                periodLabel = label,
                whatWorked = draft
            )
            repo.saveReview(review)
            prefs.setLastReviewWeek(label)

            // Sunday-evening summary notification (#17).
            val Prefs = com.superflow.data.Prefs.get(applicationContext)
            if (Prefs.remindersEnabled) {
                Reminders.notify(
                    applicationContext, 9100,
                    Reminders.CHANNEL_REVIEWS,
                    "Your week is ready to review",
                    "$reps repetitions, $recoveries recoveries this week. " +
                            "A few minutes on what worked makes next week easier."
                )
            }
        }
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }

    companion object {
        const val NAME = "superflow_review"
    }
}

/**
 * Prunes automatic safety snapshots: delete ones older than 30 days, then
 * keep at most [MAX] most recent regardless of age.
 */
class SnapshotCleanupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        val cutoff = System.currentTimeMillis() - THIRTY_DAYS_MS
        val dir = java.io.File(applicationContext.filesDir, "snapshots")
        val files = dir.listFiles()?.filter { it.name.startsWith("snap-") } ?: emptyList()
        // Age-based deletion.
        files.filter { it.lastModified() < cutoff }.forEach { it.delete() }
        // Keep the newest MAX.
        val survivors = dir.listFiles()?.filter { it.name.startsWith("snap-") }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()
        if (survivors.size > MAX) survivors.drop(MAX).forEach { it.delete() }
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }

    companion object {
        const val NAME = "superflow_snapshot_cleanup"
        private const val MAX = 20
        private val THIRTY_DAYS_MS = Duration.ofDays(30).toMillis()
    }
}

/**
 * Keeps the home-screen widget fresh even when the app is not opened. The
 * widget already refreshes on app pause and check-in broadcasts; this is the
 * periodic backstop.
 */
class WidgetRefreshWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        TodayWidget.refresh(applicationContext)
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }

    companion object {
        const val NAME = "superflow_widget_refresh"
    }
}

/**
 * Scheduled local backup (#20). Writes a full JSON snapshot to app-private
 * storage at the user's chosen cadence (daily/weekly), rotating to the
 * configured max count. Backups never leave the device.
 */
class BackupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        val repo = Repository.get(applicationContext)
        val prefs = Prefs.get(applicationContext)
        if (prefs.autoBackupEnabled) {
            Backups.create(applicationContext, repo, prefs)
        }
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }

    companion object {
        const val NAME = "superflow_backup"
        const val FREQ_DAILY = "daily"
        const val FREQ_3DAYS = "3days"
        const val FREQ_WEEKLY = "weekly"
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

        // Milestones and the weekly review run daily; WorkManager de-duplicates
        // by unique name, and the workers themselves are idempotent.
        periodic<MilestoneWorker>(manager, MilestoneWorker.NAME, 12, TimeUnit.HOURS)
        periodic<ReviewWorker>(manager, ReviewWorker.NAME, 1, TimeUnit.DAYS)
        periodic<SnapshotCleanupWorker>(manager, SnapshotCleanupWorker.NAME, 1, TimeUnit.DAYS)
        // Widget periodic refresh (every 30 minutes is the WorkManager minimum).
        periodic<WidgetRefreshWorker>(manager, WidgetRefreshWorker.NAME, 30, TimeUnit.MINUTES)
        // Backup cadence is user-configurable; UPDATE replaces the prior schedule.
        val prefs = Prefs.get(context)
        if (prefs.autoBackupEnabled) {
            val days = when (prefs.autoBackupFrequency) {
                BackupWorker.FREQ_WEEKLY -> 7L
                BackupWorker.FREQ_3DAYS -> 3L
                else -> 1L
            }
            val backup = PeriodicWorkRequestBuilder<BackupWorker>(days, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().build())
                .build()
            runCatching {
                manager.enqueueUniquePeriodicWork(
                    BackupWorker.NAME, ExistingPeriodicWorkPolicy.UPDATE, backup
                )
            }
        } else {
            runCatching { manager.cancelUniqueWork(BackupWorker.NAME) }
        }
    }

    private inline fun <reified W : CoroutineWorker> periodic(
        manager: WorkManager, name: String, every: Long, unit: TimeUnit
    ) {
        val req = PeriodicWorkRequestBuilder<W>(every, unit)
            .setConstraints(Constraints.Builder().build())
            .build()
        runCatching {
            manager.enqueueUniquePeriodicWork(name, ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }

    fun cancel(context: Context) {
        try {
            WorkManager.getInstance(context).apply {
                cancelUniqueWork(DailyRolloverWorker.NAME)
                cancelUniqueWork(ReminderRefreshWorker.NAME)
                cancelUniqueWork(ProactiveAiWorker.NAME)
                cancelUniqueWork(MilestoneWorker.NAME)
                cancelUniqueWork(ReviewWorker.NAME)
                cancelUniqueWork(SnapshotCleanupWorker.NAME)
                cancelUniqueWork(WidgetRefreshWorker.NAME)
                cancelUniqueWork(BackupWorker.NAME)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not cancel periodic work", e)
        }
    }
}
