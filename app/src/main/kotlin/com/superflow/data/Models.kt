package com.superflow.data

import java.util.UUID

/**
 * Core domain vocabulary for SuperFlow.
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

/** How a repetition is measured. */
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

/** What actually happened at a scheduled opportunity. */
enum class CheckInResult {
    DONE,            // completed at some level
    SKIPPED,         // intentionally skipped, not a failure
    MISSED,          // opportunity passed
    RESISTED,        // reduce-mode success: the urge was not acted on
    SLIPPED          // reduce-mode return of the unwanted behaviour
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
    val status: GoalStatus = GoalStatus.ACTIVE,
    val createdAt: Long = System.currentTimeMillis()
)

data class Sys(
    val id: String = newId(),
    val goalId: String? = null,
    val title: String,
    val description: String = "",
    val status: Status = Status.ACTIVE,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * A habit carries its whole four-law design so the Habit Designer, the Today
 * card, the Recovery Center and the AI tools all read from one record.
 */
data class Habit(
    val id: String = newId(),
    val systemId: String? = null,
    val identityId: String? = null,
    val title: String,
    val mode: HabitMode = HabitMode.BUILD,
    val trackType: TrackType = TrackType.BINARY,
    val targetCount: Int = 1,
    val unit: String = "",
    // Notice (make it obvious / invisible)
    val cueTime: String = "",          // "07:30"
    val cuePlace: String = "",
    val anchorHabitId: String? = null, // habit stacking
    val anchorText: String = "",       // "After breakfast"
    // Want (attractive / unattractive)
    val benefit: String = "",
    val temptationBundle: String = "",
    val reframe: String = "",
    // Start (easy / difficult)
    val tinyStart: String = "",
    val minimumVersion: String = "",
    val standardVersion: String = "",
    val stretchVersion: String = "",
    val frictionPlan: String = "",
    val environmentPrep: String = "",
    // Feel (satisfying / unsatisfying)
    val reward: String = "",
    val recoveryPlan: String = "",
    // Scheduling
    val daysMask: Int = 0b1111111,     // bit 0 = Monday
    val reminderEnabled: Boolean = false,
    val protectedRoutine: Boolean = false, // survives Minimum Mode
    val orderIndex: Int = 0,
    val status: Status = Status.ACTIVE,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun runsOn(isoDayOfWeek: Int): Boolean = (daysMask shr (isoDayOfWeek - 1)) and 1 == 1

    fun levelText(level: Level): String = when (level) {
        Level.TINY -> tinyStart.ifBlank { title }
        Level.MINIMUM -> minimumVersion.ifBlank { tinyStart.ifBlank { title } }
        Level.STANDARD -> standardVersion.ifBlank { title }
        Level.STRETCH -> stretchVersion.ifBlank { standardVersion.ifBlank { title } }
    }

    /** Plain-language contract shown before saving, per the Habit Designer spec. */
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
    val date: String,                    // yyyy-MM-dd, local
    val result: CheckInResult,
    val level: Level = Level.STANDARD,
    val amount: Double = 0.0,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

/** Up to three actions that deserve emphasis today - not a task backlog. */
data class FocusItem(
    val id: String = newId(),
    val date: String,
    val habitId: String?,
    val title: String,
    val done: Boolean = false,
    val orderIndex: Int = 0
)

/** If-then fallback for a likely barrier. */
data class ObstaclePlan(
    val id: String = newId(),
    val habitId: String,
    val ifText: String,
    val thenText: String,
    val createdAt: Long = System.currentTimeMillis()
)

/** Nonjudgmental inventory of current routines. */
data class ScorecardEntry(
    val id: String = newId(),
    val routine: String,
    val verdict: Int,   // +1 helpful, 0 neutral, -1 unhelpful
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

data class FlowStep(
    val id: String = newId(),
    val flowId: String,
    val habitId: String?,
    val title: String,
    val existingBehaviour: Boolean = false,
    val orderIndex: Int = 0
)

data class Flow(
    val id: String = newId(),
    val title: String,
    val anchor: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

data class Review(
    val id: String = newId(),
    val kind: ReviewKind,
    val periodLabel: String,
    val whatWorked: String = "",
    val whatDidnt: String = "",
    val systemChange: String = "",
    val identityEvidence: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

data class EnergyLog(
    val id: String = newId(),
    val date: String,
    val checkpoint: Checkpoint,
    val energy: Int,     // 1..5
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

/** Every state change, whether typed by the user or executed by AI. */
data class AuditEntry(
    val id: String = newId(),
    val actor: String,          // USER | AI | SYSTEM
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
    val role: String,           // user | assistant | system
    val text: String,
    val meta: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

/** Blueprint Studio ------------------------------------------------------ */

enum class RequirementStatus {
    ACCEPTED, CONFLICTED, MODIFIED, IMPLEMENTED, VERIFIED, DEFERRED, REJECTED, GAP
}

data class BlueprintProject(
    val id: String = newId(),
    val name: String,
    val instructions: String = "",
    val version: Int = 1,
    val state: String = "DRAFT",   // DRAFT | COMPILED | APPLIED | VERIFIED
    val createdAt: Long = System.currentTimeMillis()
)

data class BlueprintSource(
    val id: String = newId(),
    val projectId: String,
    val name: String,
    val kind: String,              // markdown | text | pasted | pdf
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
    val citation: String = "",     // "notes.md:L12"
    val status: RequirementStatus = RequirementStatus.ACCEPTED,
    val assumption: Boolean = false,
    val plannedCommand: String = "",
    val note: String = "",
    val orderIndex: Int = 0
)
