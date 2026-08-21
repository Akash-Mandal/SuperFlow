package com.superflow.design

/**
 * The day-state encoding used by history strips and heatmaps.
 *
 * `Insights.historyStates` returns a list of small integers, one per day.
 * Those integers were previously read by eye at each call site, which is
 * fine until someone renders -2 as "missed" instead of "skipped" and turns
 * a deliberate rest day into a failure.
 *
 * The values themselves are fixed by the existing encoding and must not be
 * renumbered - they are produced by the domain layer and consumed by the
 * widget, the charts and now the Compose strip.
 */
object HistoryStates {

    const val COMPLETED = 1
    const val PENDING = 0
    const val MISSED = -1

    /** Deliberately skipped, planned in advance. Not a failure. */
    const val SKIPPED = -2

    /** Not scheduled, or the habit was paused. Nothing was expected. */
    const val INACTIVE = -3

    /** All states, in the order they appear in a legend. */
    val all = listOf(COMPLETED, PENDING, MISSED, SKIPPED, INACTIVE)

    /**
     * A human label, for screen readers and legends.
     *
     * Worded as descriptions of the day rather than of the user. "Missed"
     * rather than "failed", "rest day" rather than "skipped", because the
     * same data read back as judgement is what makes tracking apps
     * unpleasant to use after a bad week.
     */
    fun labelFor(state: Int): String = when (state) {
        COMPLETED -> "Done"
        PENDING -> "Not yet"
        MISSED -> "Missed"
        SKIPPED -> "Rest day"
        else -> "Not scheduled"
    }

    /**
     * How strongly a day should register visually, 0f..1f.
     *
     * Completed days are full strength; everything else recedes. An inactive
     * day is nearly invisible on purpose - it is not information the user
     * needs, and rendering it at full contrast makes a strip of mostly
     * unscheduled days look like a wall of failure.
     */
    fun emphasisFor(state: Int): Float = when (state) {
        COMPLETED -> 1f
        MISSED -> 0.55f
        SKIPPED -> 0.35f
        PENDING -> 0.22f
        else -> 0.12f
    }

    /** Whether this state counts towards a completion rate. */
    fun countsAsOpportunity(state: Int): Boolean =
        state == COMPLETED || state == MISSED

    /** Completion rate over a history window, or null when nothing was scheduled. */
    fun completionRate(states: List<Int>): Double? {
        val opportunities = states.count(::countsAsOpportunity)
        if (opportunities == 0) return null
        return states.count { it == COMPLETED }.toDouble() / opportunities
    }

    /**
     * The current run of completed days, counting back from the most recent.
     *
     * Rest days and unscheduled days do not break a streak - they are not
     * opportunities, so treating them as failures would punish someone for
     * following their own plan. A missed day does break it.
     */
    fun currentStreak(states: List<Int>): Int {
        var streak = 0
        for (state in states.asReversed()) {
            when (state) {
                COMPLETED -> streak++
                SKIPPED, INACTIVE, PENDING -> Unit
                else -> return streak
            }
        }
        return streak
    }
}
