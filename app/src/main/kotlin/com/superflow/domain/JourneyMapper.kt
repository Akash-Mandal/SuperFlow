package com.superflow.domain

import com.superflow.data.model.Goal
import com.superflow.data.model.GoalStatus
import com.superflow.data.model.Habit
import com.superflow.data.model.HabitMode
import com.superflow.data.model.Identity
import com.superflow.data.model.Status
import com.superflow.data.model.Sys
import com.superflow.design.JourneyTree

/**
 * Turns the four entity tables into the flat node list [JourneyTree] wants.
 *
 * Pure: it takes lists, not a Repository, so it can be exercised directly
 * and so the Journey screen's content is decided somewhere a test can see
 * it rather than inside a ViewModel coroutine.
 *
 * Two things here are judgement calls rather than mechanics, and both are
 * about honesty:
 *
 * - A habit that links straight to an identity, skipping the system layer,
 *   is a legitimate state in this app (the designer allows it). It is
 *   reported as unlinked from the *system* chain rather than silently
 *   reparented, because pretending it has a system it does not have is the
 *   kind of small lie that makes the hierarchy untrustworthy.
 * - "Active" means the entity is actually doing something now. A paused
 *   habit, an achieved goal and an archived anything are all inactive. They
 *   still appear; they just stop counting toward the live-habit tallies
 *   that the header and the dormancy treatment read.
 */
object JourneyMapper {

    /**
     * @param habitDetail one line under each habit, usually schedule plus
     *   consistency. Supplied by the caller because computing it needs the
     *   repository and this function deliberately does not have one.
     * @param includeArchived when false, archived entities are dropped
     *   entirely rather than shown greyed out.
     */
    fun nodes(
        identities: List<Identity>,
        goals: List<Goal>,
        systems: List<Sys>,
        habits: List<Habit>,
        identityDetail: (Identity) -> String = { it.lifeArea.label },
        goalDetail: (Goal) -> String = { defaultGoalDetail(it) },
        systemDetail: (Sys) -> String = { it.description },
        habitDetail: (Habit) -> String = { "" },
        includeArchived: Boolean = true,
    ): List<JourneyTree.Node> {
        val out = ArrayList<JourneyTree.Node>(
            identities.size + goals.size + systems.size + habits.size
        )

        for (i in identities) {
            if (!includeArchived && i.status == Status.ARCHIVED) continue
            out.add(
                JourneyTree.Node(
                    id = i.id,
                    kind = JourneyTree.Kind.IDENTITY,
                    parentId = null,
                    title = i.statement,
                    detail = identityDetail(i),
                    active = i.status == Status.ACTIVE,
                    archived = i.status == Status.ARCHIVED,
                )
            )
        }

        for (g in goals) {
            if (!includeArchived && g.status == GoalStatus.CLOSED) continue
            out.add(
                JourneyTree.Node(
                    id = g.id,
                    kind = JourneyTree.Kind.GOAL,
                    parentId = g.identityId,
                    title = g.title,
                    detail = goalDetail(g),
                    // Maintaining still counts as running: keeping something
                    // alive is work, and greying it out would say otherwise.
                    active = g.status == GoalStatus.ACTIVE || g.status == GoalStatus.MAINTAINING,
                    archived = g.status == GoalStatus.CLOSED,
                )
            )
        }

        for (s in systems) {
            if (!includeArchived && s.status == Status.ARCHIVED) continue
            out.add(
                JourneyTree.Node(
                    id = s.id,
                    kind = JourneyTree.Kind.SYSTEM,
                    parentId = s.goalId,
                    title = s.title,
                    detail = systemDetail(s),
                    active = s.status == Status.ACTIVE,
                    archived = s.status == Status.ARCHIVED,
                )
            )
        }

        for (h in habits) {
            if (!includeArchived && h.status == Status.ARCHIVED) continue
            out.add(
                JourneyTree.Node(
                    id = h.id,
                    kind = JourneyTree.Kind.HABIT,
                    parentId = h.systemId,
                    title = h.title,
                    detail = habitDetail(h),
                    // A graduated habit is in "maintenance": it no longer
                    // counts toward the live tallies, which reads as dormant.
                    active = h.status == Status.ACTIVE && !h.graduated,
                    archived = h.status == Status.ARCHIVED,
                    graduated = h.graduated,
                )
            )
        }

        return out
    }

    /** "active · because I want to keep up with my kids" */
    fun defaultGoalDetail(goal: Goal): String = buildString {
        append(goal.status.name.lowercase())
        if (goal.why.isNotBlank()) {
            append(" \u00b7 ")
            append(goal.why.trim().take(WHY_PREVIEW))
            if (goal.why.trim().length > WHY_PREVIEW) append("\u2026")
        }
    }

    /**
     * Enough of the why to recognise it, not enough to reflow the card.
     * Truncation is by character rather than word because a why is one
     * sentence and cutting mid-word with an ellipsis reads as truncated,
     * which is the honest signal; a clean word break reads as complete.
     */
    const val WHY_PREVIEW = 64

    /**
     * The subtitle under a habit: what it is committed to, and how that has
     * been going.
     *
     * Consistency is omitted below the sample threshold rather than shown as
     * a small number, matching every other percentage in the app.
     */
    fun habitDetail(
        habit: Habit,
        daysLabel: String,
        repetitions: Int,
        consistency: Int,
        hasEnoughData: Boolean,
    ): String = buildString {
        append(daysLabel)
        if (habit.cueTime.isNotBlank()) {
            append(" \u00b7 ")
            append(habit.cueTime)
        }
        if (habit.mode == HabitMode.REDUCE) append(" \u00b7 reducing")
        if (repetitions > 0) {
            append(" \u00b7 ")
            append(repetitions)
            append(if (repetitions == 1) " rep" else " reps")
        }
        if (hasEnoughData) {
            append(" \u00b7 ")
            append(consistency)
            append("%")
        }
    }
}
