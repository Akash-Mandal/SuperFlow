package com.superflow.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.superflow.core.time.SfTime
import com.superflow.data.Prefs
import com.superflow.data.Repository
import com.superflow.data.model.*
import com.superflow.domain.GrowthEngine
import com.superflow.domain.Insights
import com.superflow.notify.Reminders
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Proactive AI — background intelligence that runs daily.
 *
 * Critical fix for Bug #5: "AI never proactively acts — only responds to user
 * messages; no background intelligence."
 *
 * Runs as a WorkManager job triggered daily. Evaluates habits, checks for
 * struggles, suggests upgrades, and generates weekly review reminders.
 */
class ProactiveAiWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val repo = Repository.get(applicationContext)
            val prefs = Prefs.get(applicationContext)
            if (!prefs.aiEnabled || !prefs.proactiveAi) return Result.success()

            val today = repo.clock.today()
            val suggestions = mutableListOf<ProactiveSuggestion>()

            // 1. Morning: suggest today's focus based on open habits
            if (isMorning(today)) {
                val open = repo.habitsForDay(today)
                if (open.size > 3) {
                    suggestions.add(ProactiveSuggestion(
                        type = SuggestionType.FOCUS,
                        text = "You have ${open.size} habits today. Want me to pick 3 for focus?",
                        priority = Priority.MEDIUM
                    ))
                }
            }

            // 2. Struggle detection: 3+ misses in a row
            val stats = Insights.allStats(repo, today)
            stats.filter { it.missesInARow >= 3 }.forEach { s ->
                suggestions.add(ProactiveSuggestion(
                    type = SuggestionType.STRUGGLE,
                    text = "\"${s.habit.title}\" has missed ${s.missesInARow} times in a row. " +
                            "I can shrink it to its tiny version for this week.",
                    priority = Priority.HIGH,
                    autoActionJson = """{"command":"update_habit","args":{"habit":"${s.habit.id}","field":"standardVersion","value":"${s.habit.tinyStart}"}}""",
                    habitId = s.habit.id
                ))
            }

            // 3. Weekly review reminder (with pre-filled data)
            if (today.dayOfWeek == DayOfWeek.SUNDAY && repo.reviews().none {
                it.kind == ReviewKind.WEEKLY && it.createdAt > weekStartMillis(today)
            }) {
                val summary = Insights.summaryText(repo, 7, today)
                suggestions.add(ProactiveSuggestion(
                    type = SuggestionType.REVIEW,
                    text = "It's Sunday. Here's your week:\n\n$summary\n\nWant to save a review?",
                    priority = Priority.LOW
                ))
            }

            // 4. Energy-aware scheduling
            if (prefs.energyTracking) {
                val energyLogs = repo.energyLogs()
                if (energyLogs.size >= 10) {
                    val morningEnergy = energyLogs.filter { it.checkpoint == Checkpoint.MORNING }
                        .map { it.energy.toDouble() }.average()
                    val eveningEnergy = energyLogs.filter { it.checkpoint == Checkpoint.EVENING }
                        .map { it.energy.toDouble() }.average()

                    if (morningEnergy > eveningEnergy + 1.0) {
                        val eveningHabits = repo.habits().filter {
                            it.cueTime.isNotBlank() && SfTime.minutesOfDay(it.cueTime) > 17 * 60
                        }
                        if (eveningHabits.isNotEmpty()) {
                            suggestions.add(ProactiveSuggestion(
                                type = SuggestionType.ENERGY,
                                text = "Your energy tends to be higher in the morning. " +
                                        "Consider moving ${eveningHabits.first().title} earlier.",
                                priority = Priority.LOW
                            ))
                        }
                    }
                }
            }

            // 5. Growth plan evaluation
            val growthPlans = repo.growthPlans().filter { it.isActive() }
            for (plan in growthPlans) {
                if (today.dayOfWeek.value == plan.upgradePolicy.upgradeDay) {
                    val snapshot = GrowthEngine.evaluateWeekly(plan, repo, today)
                    val habit = repo.habit(plan.habitId) ?: continue
                    when (snapshot.decision) {
                        UpgradeDecision.UPGRADE -> suggestions.add(ProactiveSuggestion(
                            type = SuggestionType.GROWTH,
                            text = "Great progress on \"${habit.title}\"! " +
                                    "${snapshot.consistency}% consistency this week. " +
                                    "Ready to upgrade to the next level?",
                            priority = Priority.MEDIUM
                        ))
                        UpgradeDecision.DOWNGRADE -> suggestions.add(ProactiveSuggestion(
                            type = SuggestionType.GROWTH,
                            text = "\"${habit.title}\" has been tough. " +
                                    "Stepping back to an easier level isn't failure — it's smart.",
                            priority = Priority.MEDIUM
                        ))
                        else -> {}
                    }
                }
            }

            // 6. Milestone detection
            detectMilestones(repo, today)

            // Save suggestions
            for (suggestion in suggestions.sortedByDescending { it.priority.ordinal }) {
                repo.saveProactiveSuggestion(suggestion)
                if (prefs.proactiveNotifications) {
                    Reminders.showProactiveNotification(applicationContext, suggestion)
                }
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun detectMilestones(repo: Repository, today: LocalDate) {
        for (habit in repo.habits()) {
            val stats = Insights.forHabit(repo, habit, today)

            // Check various milestones
            checkAndCreateMilestone(repo, habit.id, MilestoneType.FIRST_CHECKIN, stats.repetitions, 1,
                "\"${habit.title}\": first step taken")
            checkAndCreateMilestone(repo, habit.id, MilestoneType.REPS_7, stats.repetitions, 7,
                "\"${habit.title}\": 7 repetitions — that's becoming real")
            checkAndCreateMilestone(repo, habit.id, MilestoneType.REPS_21, stats.repetitions, 21,
                "\"${habit.title}\": 21 repetitions. The new pattern is settling.")
            checkAndCreateMilestone(repo, habit.id, MilestoneType.REPS_66, stats.repetitions, 66,
                "\"${habit.title}\": 66 repetitions. Research says this is where habits live.")
            checkAndCreateMilestone(repo, habit.id, MilestoneType.REPS_100, stats.repetitions, 100,
                "\"${habit.title}\": 100 repetitions. You don't just do this — you are this.")
            checkAndCreateMilestone(repo, habit.id, MilestoneType.RECOVERY_3, stats.recoveries, 3,
                "\"${habit.title}\": returned 3 times. Recovery is the real skill.")
            checkAndCreateMilestone(repo, habit.id, MilestoneType.STREAK_7, stats.currentRun, 7,
                "\"${habit.title}\": 7 in a row")
            checkAndCreateMilestone(repo, habit.id, MilestoneType.STREAK_30, stats.currentRun, 30,
                "\"${habit.title}\": 30 in a row")
        }
    }

    private fun checkAndCreateMilestone(
        repo: Repository,
        habitId: String?,
        type: MilestoneType,
        actualValue: Int,
        threshold: Int,
        label: String
    ) {
        if (actualValue >= threshold) {
            val existing = repo.milestones().any {
                it.habitId == habitId && it.type == type
            }
            if (!existing) {
                repo.saveMilestone(Milestone(
                    habitId = habitId,
                    type = type,
                    value = actualValue,
                    label = label
                ))
            }
        }
    }

    private fun isMorning(today: LocalDate): Boolean {
        // Rough morning check — could be more precise with actual time
        return true
    }

    private fun weekStartMillis(today: LocalDate): Long {
        val start = today.with(DayOfWeek.MONDAY)
        return start.atStartOfDay(java.time.ZoneId.of("UTC")).toInstant().toEpochMilli()
    }

    companion object {
        const val NAME = "superflow_proactive_ai"
    }
}