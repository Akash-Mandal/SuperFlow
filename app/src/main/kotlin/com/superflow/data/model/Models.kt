package com.superflow.data.model

import java.util.UUID

/**
 * Domain vocabulary.
 *
 * IDENTITY -> GOAL -> SYSTEM -> HABIT -> CHECK-IN -> REVIEW
 */

fun newId(): String = UUID.randomUUID().toString()

enum class LifeArea(val label: String) {
    HEALTH("Health"),
    LEARNING("Learning"),
    RELATIONSHIPS("Relationships"),
    WORK("Work"),
    CREATIVITY("Creativity"),
    FINANCE("Finance"),
    MINDFULNESS("Mindfulness"),
    HOME("Home"),
    CUSTOM("Custom");

    companion object {
        fun from(name: String?): LifeArea =
            values().firstOrNull { it.name.equals(name, true) } ?: CUSTOM
    }
}

enum class Status { ACTIVE, PAUSED, ARCHIVED }

enum class GoalStatus { ACTIVE, MAINTAINING, ACHIEVED, PAUSED, CLOSED }

/** Build a wanted behaviour, or reduce an unwanted one. */
enum class HabitMode { BUILD, REDUCE }

enum class TrackType { BINARY, COUNT, DURATION }

/** Habit Ladder rungs. A day never has to be all-or-nothing. */
enum class Level(val label: String, val weight: Double) {
    TINY("Tiny", 0.4),
    MINIMUM("Minimum", 0.7),
    STANDARD("Standard", 1.0),
    STRETCH("Stretch", 1.15);

    companion object {
        fun from(name: String?): Level = values().firstOrNull { it.name.equals(name, true) } ?: STANDARD
    }
}

enum class CheckInResult {
    DONE,
    SKIPPED,
    MISSED,
    RESISTED,
    SLIPPED
}

enum class Checkpoint(val label: String) {
    MORNING("Morning"), MIDDAY("Midday"), EVENING("Evening")
}

enum class ReviewKind { WEEKLY, MONTHLY, QUARTERLY }

data class Identity(
    val id: String = newId(),
    val statement: String,
    val lifeArea: LifeArea = LifeArea.CUSTOM,
    val status: Status = Status.ACTIVE,
    val isPrimary: Boolean = true,
    val evolutionHistory: List<IdentityEvolution> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

data class IdentityEvolution(
    val previousStatement: String,
    val newStatement: String,
    val reason: String,
    val votesAtEvolution: Int,
    val date: String
)

data class IdentityEvidence(
    val id: String = newId(),
    val identityId: String,
    val text: String,
    val sourceHabitId: String? = null,
    val date: String,
    val createdAt: Long = System.currentTimeMillis()
)

data class Goal(
    val id: String = newId(),
    val identityId: String? = null,
    val title: String,
    val why: String = "",
    val outcomeMetric: String = "",
    val targetValue: Double? = null,
    val targetDate: Long? = null,
    val currentMetricValue: Double? = null,
    val metricUnit: String = "",
    val status: GoalStatus = GoalStatus.ACTIVE,
    val milestones: List<GoalMilestone> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

data class GoalMilestone(
    val id: String = newId(),
    val title: String,
    val achieved: Boolean = false,
    val achievedDate: String? = null,
    val linkedHabitIds: List<String> = emptyList()
)

data class Sys(
    val id: String = newId(),
    val goalId: String? = null,
    val title: String,
    val description: String = "",
    val status: Status = Status.ACTIVE,
    val templateId: String? = null,
    val reviewFrequency: String = "monthly",
    val createdAt: Long = System.currentTimeMillis()
)

data class Habit(
    val id: String = newId(),
    val systemId: String? = null,
    val identityId: String? = null,
    val title: String,
    val mode: HabitMode = HabitMode.BUILD,
    val trackType: TrackType = TrackType.BINARY,
    val targetCount: Int = 1,
    val unit: String = "",
    // Notice
    val cueTime: String = "",
    val cuePlace: String = "",
    val anchorHabitId: String? = null,
    val anchorText: String = "",
    // Want
    val benefit: String = "",
    val temptationBundle: String = "",
    val reframe: String = "",
    // Start
    val tinyStart: String = "",
    val minimumVersion: String = "",
    val standardVersion: String = "",
    val stretchVersion: String = "",
    val frictionPlan: String = "",
    val environmentPrep: String = "",
    // Feel
    val reward: String = "",
    val recoveryPlan: String = "",
    // Scheduling (see core.schedule.Recurrence for the encoded forms)
    val recurrenceRule: String = "WEEKLY:1,2,3,4,5,6,7",
    val scheduleVersion: Int = 1,
    val startDate: String = "",
    val endDate: String? = null,
    val reminderEnabled: Boolean = false,
    val protectedRoutine: Boolean = false,
    // Four Laws living fields
    val rewardSatisfaction: Int? = null,       // 1-5, null = not yet rated
    val rewardLastRated: String? = null,
    val reframeHelpful: Boolean? = null,
    val bundleEffectiveness: Int? = null,      // 1-5
    val frictionPlanActive: Boolean = false,
    val environmentPrepReminderTime: String? = null,
    // Ladder adaptive fields
    val ladderHistory: List<LadderEvolution> = emptyList(),
    val lastDifficultyRating: Int? = null,     // 1=too easy, 3=just right, 5=too hard
    val stretchCount: Int = 0,
    val consecutiveStandards: Int = 0,
    // Capacity fields
    val estimatedMinutes: Int = 5,
    val difficultyRating: Int = 3,             // 1=easy, 5=challenging
    // Presentation
    val colorSeed: Int = 0,
    val orderIndex: Int = 0,
    val status: Status = Status.ACTIVE,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun levelText(level: Level): String = when (level) {
        Level.TINY -> tinyStart.ifBlank { title }
        Level.MINIMUM -> minimumVersion.ifBlank { tinyStart.ifBlank { title } }
        Level.STANDARD -> standardVersion.ifBlank { title }
        Level.STRETCH -> stretchVersion.ifBlank { standardVersion.ifBlank { title } }
    }

    /** Plain-language contract shown before saving. */
    fun contract(): String {
        val sb = StringBuilder()
        val when0 = when {
            anchorText.isNotBlank() -> "After ${anchorText.trim().removePrefix("After ").trim()}"
            cueTime.isNotBlank() && cuePlace.isNotBlank() -> "At $cueTime in $cuePlace"
            cueTime.isNotBlank() -> "At $cueTime"
            cuePlace.isNotBlank() -> "In $cuePlace"
            else -> "Today"
        }
        sb.append(when0).append(", I will ").append(levelText(Level.STANDARD).trimEnd('.')).append(".")
        if (tinyStart.isNotBlank()) sb.append(" On a hard day I can stop after ").append(tinyStart.trimEnd('.')).append(".")
        if (environmentPrep.isNotBlank()) sb.append(" Beforehand: ").append(environmentPrep.trimEnd('.')).append(".")
        if (reward.isNotBlank()) sb.append(" Afterward: ").append(reward.trimEnd('.')).append(".")
        return sb.toString()
    }
}

data class LadderEvolution(
    val level: Level,
    val previousText: String,
    val newText: String,
    val reason: String,
    val date: String
) {
    fun levelText(level: Level): String = when (level) {
        Level.TINY -> tinyStart.ifBlank { title }
        Level.MINIMUM -> minimumVersion.ifBlank { tinyStart.ifBlank { title } }
        Level.STANDARD -> standardVersion.ifBlank { title }
        Level.STRETCH -> stretchVersion.ifBlank { standardVersion.ifBlank { title } }
    }

    /** Plain-language contract shown before saving. */
    fun contract(): String {
        val sb = StringBuilder()
        val when0 = when {
            anchorText.isNotBlank() -> "After ${anchorText.trim().removePrefix("After ").trim()}"
            cueTime.isNotBlank() && cuePlace.isNotBlank() -> "At $cueTime in $cuePlace"
            cueTime.isNotBlank() -> "At $cueTime"
            cuePlace.isNotBlank() -> "In $cuePlace"
            else -> "Today"
        }
        sb.append(when0).append(", I will ").append(levelText(Level.STANDARD).trimEnd('.')).append(".")
        if (tinyStart.isNotBlank()) sb.append(" On a hard day I can stop after ").append(tinyStart.trimEnd('.')).append(".")
        if (environmentPrep.isNotBlank()) sb.append(" Beforehand: ").append(environmentPrep.trimEnd('.')).append(".")
        if (reward.isNotBlank()) sb.append(" Afterward: ").append(reward.trimEnd('.')).append(".")
        return sb.toString()
    }
}

data class CheckIn(
    val id: String = newId(),
    val habitId: String,
    val date: String,
    val result: CheckInResult,
    val level: Level = Level.STANDARD,
    val amount: Double = 0.0,
    val note: String = "",
    // Rich data fields
    val contextTags: List<String> = emptyList(),
    val actualAmount: Double? = null,
    val actualDurationMinutes: Int? = null,
    val qualityRating: Int? = null,         // 1-3
    val difficultyRating: Int? = null,      // 1=too easy, 3=just right, 5=too hard
    val missReason: String? = null,         // "time", "energy", "forgot", "motivation", "circumstance", "other"
    val missReasonDetail: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    val isSuccess: Boolean get() = result == CheckInResult.DONE || result == CheckInResult.RESISTED
    val isMiss: Boolean get() = result == CheckInResult.MISSED || result == CheckInResult.SLIPPED
}

data class FocusItem(
    val id: String = newId(),
    val date: String,
    val habitId: String?,
    val title: String,
    val done: Boolean = false,
    val isPriority: Boolean = false,
    val goalId: String? = null,
    val estimatedMinutes: Int? = null,
    val carryOverCount: Int = 0,
    val orderIndex: Int = 0
)

data class ObstaclePlan(
    val id: String = newId(),
    val habitId: String,
    val ifText: String,
    val thenText: String,
    val category: String? = null,
    val timesUsed: Int = 0,
    val lastUsed: String? = null,
    val effectiveness: Int? = null,    // 1-5, null = not rated
    val createdAt: Long = System.currentTimeMillis()
)

data class ScorecardEntry(
    val id: String = newId(),
    val routine: String,
    val verdict: Int,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

data class Flow(
    val id: String = newId(),
    val title: String,
    val anchor: String = "",
    val estimatedMinutes: Int = 0,
    val completionCount: Int = 0,
    val partialCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

data class FlowStep(
    val id: String = newId(),
    val flowId: String,
    val habitId: String?,
    val title: String,
    val existingBehaviour: Boolean = false,
    val durationMinutes: Int = 0,
    val isBreakpoint: Boolean = false,
    val orderIndex: Int = 0
)

data class Review(
    val id: String = newId(),
    val kind: ReviewKind,
    val periodLabel: String,
    val whatWorked: String = "",
    val whatDidnt: String = "",
    val systemChange: String = "",
    val identityEvidence: String = "",
    val autoGeneratedData: String = "",
    val actionItems: List<ReviewActionItem> = emptyList(),
    val previousReviewId: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

data class ReviewActionItem(
    val id: String = newId(),
    val text: String,
    val completed: Boolean = false,
    val completedDate: String? = null,
    val linkedCommand: String? = null,
    val outcome: String? = null
)

data class EnergyLog(
    val id: String = newId(),
    val date: String,
    val checkpoint: Checkpoint,
    val energy: Int,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

/** Calendar pause: holiday, illness, travel. Paused days never become misses. */
data class PauseWindow(
    val id: String = newId(),
    val habitId: String? = null,          // null = applies to every habit
    val startDate: String,
    val endDate: String,
    val reason: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    fun covers(date: java.time.LocalDate): Boolean {
        val s = runCatching { java.time.LocalDate.parse(startDate) }.getOrNull() ?: return false
        val e = runCatching { java.time.LocalDate.parse(endDate) }.getOrNull() ?: return false
        return !date.isBefore(s) && !date.isAfter(e)
    }
}

/** Profile: locale, zone and week start drive every date calculation. */
data class UserProfile(
    val id: String = "local",
    val displayName: String = "",
    val locale: String = "",
    val zoneId: String = "",
    val weekStart: Int = 1,               // ISO: Monday = 1
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class AuditEntry(
    val id: String = newId(),
    val actor: String,
    val command: String,
    val summary: String,
    val payload: String = "",
    val undoPayload: String = "",
    val groupId: String? = null,
    val undone: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

data class AiMessage(
    val id: String = newId(),
    val role: String,
    val text: String,
    val meta: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

/* --------------------------------------------------------------- blueprint */

enum class RequirementStatus {
    ACCEPTED, CONFLICTED, MODIFIED, IMPLEMENTED, VERIFIED, DEFERRED, REJECTED, GAP
}

data class BlueprintProject(
    val id: String = newId(),
    val name: String,
    val instructions: String = "",
    val version: Int = 1,
    val state: String = "DRAFT",
    val parentVersionId: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

data class BlueprintSource(
    val id: String = newId(),
    val projectId: String,
    val name: String,
    val kind: String,
    val content: String,
    val instructions: String = "",
    val lineCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

data class Requirement(
    val id: String = newId(),
    val projectId: String,
    val text: String,
    val sourceId: String?,
    val citation: String = "",
    val status: RequirementStatus = RequirementStatus.ACCEPTED,
    val assumption: Boolean = false,
    val plannedCommand: String = "",
    val note: String = "",
    val orderIndex: Int = 0
)

/** A saved ledger snapshot, enabling amendment history and version diffing. */
data class BlueprintVersion(
    val id: String = newId(),
    val projectId: String,
    val version: Int,
    val label: String,
    val ledgerJson: String,
    val createdAt: Long = System.currentTimeMillis()
)

/* -------------------------------------------------------------- composites */

/** A habit joined with today's check-in, for list rendering. */
data class TodayHabit(
    val habit: Habit,
    val checkIn: CheckIn?,
    val isReturning: Boolean = false
) {
    val done: Boolean get() = checkIn?.isSuccess == true
    val skipped: Boolean get() = checkIn?.result == CheckInResult.SKIPPED
    val missed: Boolean get() = checkIn?.isMiss == true
    val open: Boolean get() = checkIn == null
}

data class HabitStats(
    val habit: Habit,
    val repetitions: Int,
    val currentRun: Int,
    val bestRun: Int,
    val consistency30: Int,
    val opportunities30: Int,
    val recoveries: Int,
    val missesInARow: Int,
    val needsReturn: Boolean,
    val lastDone: String?
) {
    /** Sample size is disclosed wherever consistency is shown. */
    val hasEnoughData: Boolean get() = opportunities30 >= 5
}
