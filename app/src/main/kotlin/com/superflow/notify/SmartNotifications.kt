package com.superflow.notify

import android.content.Context
import com.superflow.core.time.SfTime
import com.superflow.data.Prefs
import com.superflow.data.Repository
import com.superflow.data.model.Checkpoint
import com.superflow.data.model.CheckInResult
import com.superflow.data.model.HabitMode
import com.superflow.domain.Insights

/**
 * Smart Notification Scheduling (Section 8.1-8.2 of the Grand Plan).
 *
 * Scheduled notifications with contextual content intelligence — reminders
 * adapt based on the habit's current state (streak, struggle, energy, etc.).
 *
 * Content intelligence table:
 *   - Normal reminder: "Walk 10 min — or just put on your shoes 🚶"
 *   - Struggling habit: "Walk: even the tiny version counts today"
 *   - After a miss: "Welcome back. Your tiny start: put on your shoes"
 *   - Streak active: "Walk: day 12. Your shoes are by the door?"
 *   - Low energy: "Low energy day. Tiny versions are enough."
 *   - Morning briefing: "Good morning. 4 habits today. Your first: Walk at 07:30"
 *   - Evening reflection: "3 of 4 done. Journal is the last one."
 */
object SmartNotifications {

    fun scheduleAll(context: Context, repo: Repository, prefs: Prefs) {
        if (!prefs.remindersEnabled) return

        val today = repo.clock.today()
        val habits = repo.habitsForDay(today)

        for (habit in habits) {
            if (!habit.reminderEnabled) continue

            if (habit.cueTime.isNotBlank()) {
                scheduleReminder(context, repo, prefs, habit, habit.cueTime)
            }

            // "Getting late" reminder if not done by evening
            if (habit.cueTime.isNotBlank()) {
                val cueMinutes = SfTime.minutesOfDay(habit.cueTime)
                val lateMinutes = cueMinutes + 120
                if (lateMinutes < 22 * 60) {
                    scheduleLateReminder(context, repo, prefs, habit, lateMinutes)
                }
            }
        }

        // Checkpoint reminders
        if (prefs.checkpointsEnabled) {
            scheduleCheckpoint(context, prefs.morningCheckpoint, Checkpoint.MORNING)
            scheduleCheckpoint(context, prefs.middayCheckpoint, Checkpoint.MIDDAY)
            scheduleCheckpoint(context, prefs.eveningCheckpoint, Checkpoint.EVENING)
        }

        // Weekly review reminder
        scheduleWeeklyReview(context, prefs)

        // Growth plan evaluation
        scheduleGrowthEvaluations(context, repo)
    }

    /**
     * Content intelligence: pick the right notification text based on context.
     *
     * Pulls live data from the repo to determine:
     *  - Current streak / best run
     *  - Misses in a row (struggle signal)
     *  - Whether this is a returning habit (after a miss)
     *  - Today's logged energy
     *  - Recent check-in results
     */
    fun notificationContent(
        repo: Repository,
        habit: com.superflow.data.model.Habit,
        today: java.time.LocalDate = repo.clock.today()
    ): String {
        val stats = Insights.forHabit(repo, habit, today)
        val checkIn = repo.checkIn(habit.id, SfTime.format(today))
        val energy = repo.energyFor(SfTime.format(today))
            .firstOrNull()?.energy

        val streakDays = stats.currentRun
        val missedInARow = stats.missesInARow
        val isReturning = checkIn?.result == CheckInResult.MISSED ||
            (missedInARow >= 2 && checkIn == null)
        val tinyStart = habit.tinyStart.ifBlank { "just show up" }

        return when {
            // Struggling: more than 2 consecutive misses
            missedInARow >= 2 -> "${habit.title}: even the tiny version counts today"

            // Just returned after missing
            isReturning -> "Welcome back. Your tiny start: $tinyStart"

            // Active streak
            streakDays >= 7 -> "${habit.title}: day $streakDays. Steady and strong."

            // Low energy day
            energy != null && energy <= 2 -> "Low energy day. Tiny versions are enough."

            // Standard with tiny hint
            habit.tinyStart.isNotBlank() -> "${habit.title} — or just $tinyStart"

            // Default
            else -> "${habit.title} — a small version counts."
        }
    }

    /**
     * Morning briefing: present the day with energy-aware ordering.
     */
    fun morningBriefing(repo: Repository, today: java.time.LocalDate = repo.clock.today()): String {
        val habits = repo.habitsForDay(today)
        if (habits.isEmpty()) return "Nothing scheduled today. A quiet day is allowed."
        val energy = repo.energyFor(SfTime.format(today))
            .firstOrNull { it.checkpoint == Checkpoint.MORNING }?.energy ?: 3
        val ordered = habits.sortedByDescending { habit ->
            scoreForMorning(habit, energy)
        }
        val lines = ordered.take(5).joinToString(" • ") { it.title }
        val first = ordered.firstOrNull()?.title ?: "your first habit"
        return "Good morning. ${habits.size} habits today. Start with $first. • $lines"
    }

    /**
     * Evening reflection: end-of-day summary.
     */
    fun eveningReflection(repo: Repository, today: java.time.LocalDate = repo.clock.today()): String {
        val (done, total) = Insights.dayProgress(repo, today)
        val habits = repo.habitsForDay(today)
        val stillOpen = habits.filter {
            repo.checkIn(it.id, SfTime.format(today)) == null
        }
        return when {
            total == 0 -> "Nothing scheduled today. Rest well."
            done == total -> "All $total habits done. Today, you showed up."
            done >= total - 1 -> "$done of $total done. One habit left."
            else -> "$done of $total done. ${stillOpen.size} still open."
        }
    }

    private fun scoreForMorning(habit: com.superflow.data.model.Habit, energy: Int): Int {
        var score = 0
        if (habit.cueTime.isNotBlank()) {
            val mins = SfTime.minutesOfDay(habit.cueTime)
            // Earlier in the day = higher score
            score += (24 * 60 - mins) / 60
        }
        if (habit.protectedRoutine) score += 10
        if (habit.tinyStart.isNotBlank()) score += 5
        // Low energy? Prioritise simpler habits
        if (energy <= 2 && habit.tinyStart.isNotBlank()) score += 8
        return score
    }

    // Scheduling placeholders — actual AlarmManager scheduling is in Reminders.kt
    private fun scheduleReminder(context: Context, repo: Repository, prefs: Prefs,
                                 habit: com.superflow.data.model.Habit, time: String) {
        // Delegated to existing Reminders infrastructure; the contextual content
        // is computed via notificationContent() when the alarm fires.
    }

    private fun scheduleLateReminder(context: Context, repo: Repository, prefs: Prefs,
                                     habit: com.superflow.data.model.Habit, minutes: Int) {
        // Schedule a late-in-the-day follow-up reminder
    }

    private fun scheduleCheckpoint(context: Context, time: String, checkpoint: Checkpoint) {
        // Checkpoints already handled by Reminders
    }

    private fun scheduleWeeklyReview(context: Context, prefs: Prefs) {
        // Weekly review handled by ProactiveAiWorker
    }

    private fun scheduleGrowthEvaluations(context: Context, repo: Repository) {
        // Growth evaluation handled by DailyRolloverWorker
    }
}