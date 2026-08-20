package com.superflow.domain

import com.superflow.core.time.SfTime
import com.superflow.data.Prefs
import com.superflow.data.Repository
import com.superflow.data.model.*
import java.time.LocalDate

/**
 * The Progressive Growth Engine.
 *
 * Transforms SuperFlow from a static habit tracker into an adaptive growth
 * system that automatically scales difficulty, metrics, and expectations based
 * on real performance. "Start small, grow every week."
 *
 * Called daily by WorkManager and on-demand by the AI.
 */
object GrowthEngine {

    /**
     * Called daily by WorkManager. Evaluates all active growth plans.
     */
    fun evaluate(repo: Repository, prefs: Prefs) {
        if (!prefs.growthPlansEnabled) return
        val today = repo.clock.today()
        val plans = repo.growthPlans().filter { it.isActive() }

        for (plan in plans) {
            // Weekly review day?
            if (today.dayOfWeek.value == plan.upgradePolicy.upgradeDay) {
                evaluateWeekly(plan, repo, today)
            }

            // Daily struggle detection
            detectStruggle(plan, repo, today)
        }
    }

    /**
     * Weekly evaluation: should we upgrade, hold, or downgrade?
     */
    fun evaluateWeekly(
        plan: GrowthPlan,
        repo: Repository,
        today: LocalDate = repo.clock.today()
    ): WeeklySnapshot {
        val phase = plan.phases[plan.currentPhaseIndex]
        val habit = repo.habit(plan.habitId) ?: error("Habit ${plan.habitId} not found")
        val stats = Insights.forHabit(repo, habit, today)

        val consistency = stats.consistency30
        val recoveries = stats.recoveries
        val missesInARow = stats.missesInARow

        val decision = when {
            consistency >= phase.metrics.minConsistency &&
                    recoveries >= phase.metrics.minRecoveries &&
                    missesInARow <= phase.metrics.maxMissesInARow &&
                    plan.weeksInCurrentPhase() >= plan.upgradePolicy.minWeeksInPhase ->
                if (plan.currentPhaseIndex < plan.phases.lastIndex) UpgradeDecision.UPGRADE
                else UpgradeDecision.HOLD  // Already at max phase

            missesInARow >= plan.upgradePolicy.struggleThreshold &&
                    plan.upgradePolicy.downgradeOnStruggle &&
                    plan.currentPhaseIndex > 0 ->
                UpgradeDecision.DOWNGRADE

            plan.weeksInCurrentPhase() >= plan.upgradePolicy.maxWeeksInPhase ->
                UpgradeDecision.REVIEW_NEEDED

            else -> UpgradeDecision.HOLD
        }

        val snapshot = WeeklySnapshot(
            weekNumber = plan.weeksSinceStart() + 1,
            phaseIndex = plan.currentPhaseIndex,
            consistency = consistency,
            repetitions = stats.repetitions,
            misses = missesInARow,
            recoveries = recoveries,
            averageEnergy = null, // TODO: from energy logs
            decision = decision,
            date = SfTime.format(today)
        )

        // Apply the decision
        when (decision) {
            UpgradeDecision.UPGRADE -> applyUpgrade(plan, repo, today)
            UpgradeDecision.DOWNGRADE -> applyDowngrade(plan, repo, today)
            UpgradeDecision.REVIEW_NEEDED -> notifyReviewNeeded(plan, repo)
            UpgradeDecision.HOLD -> {} // Stay put
        }

        repo.saveGrowthPlan(plan.copy(
            weeklySnapshots = plan.weeklySnapshots + snapshot
        ))

        // Record history
        repo.saveGrowthPhaseHistory(GrowthPhaseHistory(
            growthPlanId = plan.id,
            phaseIndex = plan.currentPhaseIndex,
            action = decision.name,
            consistency = consistency,
            date = SfTime.format(today)
        ))

        return snapshot
    }

    /**
     * Generate a progressive growth plan for a habit.
     */
    fun generateGrowthPlan(habit: Habit, weeks: Int = 8): GrowthPlan {
        val phases = mutableListOf<GrowthPhase>()

        // Week 1-2: Foundation (tiny only)
        phases.add(GrowthPhase(
            weekNumber = 1,
            label = "Foundation",
            tinyStart = habit.tinyStart,
            minimumVersion = habit.tinyStart,
            standardVersion = habit.tinyStart,
            stretchVersion = habit.minimumVersion.ifBlank { habit.tinyStart },
            targetDays = 3,
            notes = "Just show up. The size doesn't matter yet.",
            metrics = PhaseMetrics(minConsistency = 50)
        ))

        // Week 3-4: Building
        phases.add(GrowthPhase(
            weekNumber = 3,
            label = "Building",
            tinyStart = habit.tinyStart,
            minimumVersion = habit.minimumVersion.ifBlank { habit.tinyStart },
            standardVersion = habit.minimumVersion.ifBlank { habit.standardVersion },
            stretchVersion = habit.standardVersion,
            targetDays = 4,
            notes = "You've proven you can show up. Now grow a little.",
            metrics = PhaseMetrics(minConsistency = 60)
        ))

        // Week 5-6: Growing
        phases.add(GrowthPhase(
            weekNumber = 5,
            label = "Growing",
            tinyStart = habit.tinyStart,
            minimumVersion = habit.minimumVersion.ifBlank { habit.tinyStart },
            standardVersion = habit.standardVersion,
            stretchVersion = habit.stretchVersion.ifBlank { habit.standardVersion },
            targetDays = 5,
            notes = "This is becoming who you are.",
            metrics = PhaseMetrics(minConsistency = 70)
        ))

        // Week 7-8: Flourishing
        phases.add(GrowthPhase(
            weekNumber = 7,
            label = "Flourishing",
            tinyStart = habit.tinyStart,
            minimumVersion = habit.minimumVersion.ifBlank { habit.tinyStart },
            standardVersion = habit.standardVersion,
            stretchVersion = habit.stretchVersion.ifBlank { habit.standardVersion },
            targetDays = 7,
            notes = "Full system. You've earned this.",
            metrics = PhaseMetrics(minConsistency = 80)
        ))

        return GrowthPlan(
            habitId = habit.id,
            phases = phases,
            upgradePolicy = UpgradePolicy(
                autoUpgrade = true,
                minWeeksInPhase = 2,
                maxWeeksInPhase = 4,
                downgradeOnStruggle = true,
                struggleThreshold = 3
            )
        )
    }

    /**
     * Upgrade to the next phase.
     */
    fun applyUpgrade(plan: GrowthPlan, repo: Repository, today: LocalDate = repo.clock.today()) {
        val newIndex = (plan.currentPhaseIndex + 1).coerceAtMost(plan.phases.lastIndex)
        repo.saveGrowthPlan(plan.copy(
            currentPhaseIndex = newIndex,
            lastUpgradeDate = SfTime.format(today)
        ))

        // Update habit difficulty if we have a new phase with different versions
        val newPhase = plan.phases.getOrNull(newIndex) ?: return
        val habit = repo.habit(plan.habitId) ?: return
        if (newPhase.standardVersion != habit.standardVersion ||
            newPhase.targetDays != habit.targetCount) {
            val updated = habit.copy(
                standardVersion = newPhase.standardVersion
                    .ifBlank { habit.standardVersion },
                tinyStart = newPhase.tinyStart.ifBlank { habit.tinyStart }
            )
            repo.saveHabit(updated)
        }
    }

    /**
     * Downgrade to the previous phase (compassionate stepping back).
     */
    fun applyDowngrade(plan: GrowthPlan, repo: Repository, today: LocalDate = repo.clock.today()) {
        val newIndex = (plan.currentPhaseIndex - 1).coerceAtLeast(0)
        repo.saveGrowthPlan(plan.copy(
            currentPhaseIndex = newIndex,
            lastUpgradeDate = SfTime.format(today)
        ))

        // Reset habit to earlier phase's versions
        val newPhase = plan.phases.getOrNull(newIndex) ?: return
        val habit = repo.habit(plan.habitId) ?: return
        val updated = habit.copy(
            standardVersion = newPhase.standardVersion.ifBlank { habit.standardVersion },
            tinyStart = newPhase.tinyStart.ifBlank { habit.tinyStart }
        )
        repo.saveHabit(updated)
    }

    /**
     * Detect struggle: 3+ consecutive misses should trigger compassion.
     */
    fun detectStruggle(plan: GrowthPlan, repo: Repository, today: LocalDate) {
        val habit = repo.habit(plan.habitId) ?: return
        val stats = Insights.forHabit(repo, habit, today)
        if (stats.missesInARow >= plan.upgradePolicy.struggleThreshold &&
            plan.upgradePolicy.downgradeOnStruggle &&
            plan.currentPhaseIndex > 0) {

            // Create a proactive suggestion for the struggle
            val suggestion = ProactiveSuggestion(
                type = SuggestionType.STRUGGLE,
                text = "\"${habit.title}\" has missed ${stats.missesInARow} times in a row. " +
                        "I can shrink it to its tiny version for this week.",
                priority = Priority.HIGH,
                autoActionJson = """{"command":"update_habit","args":{"habit":"${habit.id}","field":"standardVersion","value":"${habit.tinyStart}"}}""",
                habitId = habit.id
            )
            repo.saveProactiveSuggestion(suggestion)
        }
    }

    private fun notifyReviewNeeded(plan: GrowthPlan, repo: Repository) {
        val habit = repo.habit(plan.habitId)
        val name = habit?.title ?: "Unknown habit"
        val suggestion = ProactiveSuggestion(
            type = SuggestionType.REVIEW,
            text = "\"$name\" has been in the same phase for ${plan.upgradePolicy.maxWeeksInPhase} weeks. " +
                    "Time to check in and see if the plan still fits.",
            priority = Priority.MEDIUM,
            habitId = plan.habitId
        )
        repo.saveProactiveSuggestion(suggestion)
    }

    /**
     * Estimate habit difficulty based on its design.
     */
    fun estimateDifficulty(habit: Habit): DifficultyRating {
        var score = 0
        val factors = mutableListOf<String>()

        // Time estimate
        val minutes = estimateMinutes(habit.standardVersion)
        when {
            minutes <= 2 -> { score += 0 }
            minutes <= 5 -> { score += 1; factors.add("Takes ~$minutes minutes") }
            minutes <= 15 -> { score += 2; factors.add("Takes ~$minutes minutes") }
            minutes <= 30 -> { score += 3; factors.add("Takes ~$minutes minutes") }
            else -> { score += 4; factors.add("Takes $minutes+ minutes") }
        }

        // Has tiny start? (reduces difficulty)
        if (habit.tinyStart.isNotBlank()) { score -= 1; factors.add("Has a tiny start") }

        // Has anchor? (reduces difficulty)
        if (habit.anchorText.isNotBlank()) { score -= 1; factors.add("Has an anchor") }

        // Has reward? (reduces difficulty)
        if (habit.reward.isNotBlank()) { score -= 1; factors.add("Has a reward") }

        // Time of day (evening habits are harder)
        if (habit.cueTime.isNotBlank()) {
            val mins = SfTime.minutesOfDay(habit.cueTime)
            if (mins > 20 * 60) { score += 1; factors.add("Evening habit (after 8pm)") }
        }

        // Days per week
        val dailyCount = habit.recurrenceRule.count { it == ',' } + 1
        if (dailyCount > 5) { score += 1; factors.add("${dailyCount}x/week") }

        val level = when (score.coerceIn(0, 5)) {
            0, 1 -> DifficultyLevel.EASY
            2, 3 -> DifficultyLevel.MODERATE
            else -> DifficultyLevel.CHALLENGING
        }

        val advice = when (level) {
            DifficultyLevel.EASY -> "A gentle start. You can handle more if you like."
            DifficultyLevel.MODERATE -> "A good challenge. The tiny start is your safety net."
            DifficultyLevel.CHALLENGING -> "This is ambitious. Make sure your tiny start is genuinely tiny."
        }

        return DifficultyRating(
            level = level,
            score = score,
            factors = factors,
            advice = advice
        )
    }

    /**
     * Rough estimate of minutes a version text describes.
     */
    private fun estimateMinutes(versionText: String): Int {
        val s = versionText.lowercase()
        val minutes = Regex("(\\d+)\\s*min").find(s)?.groupValues?.get(1)?.toIntOrNull()
        if (minutes != null) return minutes
        if (s.contains("hour") || s.contains("hr")) return 60
        if (s.contains("page")) return 20
        if (s.contains("glass")) return 1
        return 5
    }

    /**
     * Simulate adding a new habit to see its impact.
     */
    fun simulateAddition(repo: Repository, newHabit: Habit): Simulation {
        val today = repo.clock.today()
        val currentDaily = repo.habitsForDay(today).size
        val currentAvgTime = repo.habits().sumOf { estimateMinutes(it.standardVersion) } /
            (repo.habits().size.coerceAtLeast(1))
        val newTime = estimateMinutes(newHabit.standardVersion)

        val riskLevel = when {
            currentDaily + 1 > 7 -> RiskLevel.HIGH
            currentAvgTime + newTime > 120 -> RiskLevel.HIGH
            currentDaily + 1 > 5 -> RiskLevel.MEDIUM
            else -> RiskLevel.LOW
        }

        val advice = when {
            currentDaily + 1 > 7 ->
                "You already have $currentDaily habits. Research suggests 3-5 new behaviours at once is the maximum."
            currentAvgTime + newTime > 120 ->
                "This would bring your daily commitment to ${currentAvgTime + newTime} minutes. That's a lot."
            else ->
                "This looks manageable. Start with the tiny version."
        }

        return Simulation(
            currentHabits = currentDaily,
            newHabits = currentDaily + 1,
            currentMinutes = currentAvgTime,
            newMinutes = currentAvgTime + newTime,
            riskLevel = riskLevel,
            advice = advice
        )
    }
}