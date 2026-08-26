package com.superflow.domain

import com.superflow.data.model.HabitStats
import com.superflow.data.model.TodayHabit

/**
 * The Focus engine (Plan B F1.2): picks the single best next action from
 * everything the user could do right now.
 *
 * Deterministic, offline, and explainable. AI never decides ranking - it may
 * only phrase what this module already decided. Every candidate carries
 * [FocusCandidate.reasons]: the same facts that raised its score, in plain
 * language, so the Focus card can always answer "why this?" honestly.
 *
 * The model is multiplicative around 1.0 so that a habit neutral on every
 * axis scores exactly [NEUTRAL_SCORE] and no single factor can dominate;
 * penalties subtract afterwards.
 */
object FocusEngine {

    /** Score of an open habit about which we know nothing. */
    const val NEUTRAL_SCORE = 1f

    private const val MISSES_URGENCY_BONUS = 0.15f
    private const val MAX_MISS_BONUS = 0.45f

    /** Momentum range once there is enough data to judge (HabitStats.hasEnoughData). */
    private const val MOMENTUM_LOW = 0.8f
    private const val MOMENTUM_HIGH = 1.2f

    /** Habits linked to an identity are votes toward who the user is becoming. */
    private const val IDENTITY_WEIGHT = 1.15f

    /** Each check-in already made today makes further suggestions slightly weaker. */
    private const val FATIGUE_PER_CHECKIN = 0.05f

    /** Each same-day dismissal of a suggestion suppresses it further. */
    private const val DISMISSAL_PENALTY = 0.3f

    /** A candidate the user dismissed this many times today drops off entirely. */
    private const val DISMISSAL_DROP_THRESHOLD = 2

    /**
     * @param today        today's habits with their check-in state
     * @param statsOf      per-habit statistics (may return null)
     * @param energy       today's logged energy 1..5, null when not logged
     * @param checkedSoFar how many habits are already completed today
     * @param dismissals   per-habit same-day dismissal counts
     */
    fun rank(
        today: List<TodayHabit>,
        statsOf: (String) -> HabitStats?,
        energy: Int? = null,
        checkedSoFar: Int = 0,
        dismissals: Map<String, Int> = emptyMap(),
    ): List<FocusCandidate> =
        today.asSequence()
            .filter { !it.done && !it.skipped }
            .map { th ->
                val s = statsOf(th.habit.id)
                val reasons = mutableListOf<String>()

                var urgency = 1f
                val misses = s?.missesInARow ?: 0
                if (misses > 0) {
                    val bonus = (misses * MISSES_URGENCY_BONUS).coerceAtMost(MAX_MISS_BONUS)
                    urgency += bonus
                    reasons.add(
                        if (misses == 1) "Missed yesterday - easiest day to get back"
                        else "Missed $misses days in a row"
                    )
                }

                var momentum = 1f
                if (s != null && s.hasEnoughData) {
                    momentum = MOMENTUM_LOW +
                        (s.consistency30.coerceIn(0, 100) / 100f) * (MOMENTUM_HIGH - MOMENTUM_LOW)
                    if (s.currentRun >= 3) {
                        momentum += 0.1f
                        reasons.add("A ${s.currentRun}-day run is going")
                    }
                }

                val capacity = capacityFactor(th.habit.estimatedMinutes, energy)
                if (capacity > 1.01f) reasons.add("Fits your energy today")
                if (capacity < 0.99f) reasons.add("Heavy for today's energy")

                var identity = 1f
                if (th.habit.identityId != null) {
                    identity = IDENTITY_WEIGHT
                    reasons.add("Counts toward an identity you chose")
                }

                val d = dismissals[th.habit.id] ?: 0
                val score = urgency * momentum * capacity * identity -
                    checkedSoFar * FATIGUE_PER_CHECKIN -
                    d * DISMISSAL_PENALTY

                FocusCandidate(
                    habit = th.habit,
                    score = score,
                    dropped = d >= DISMISSAL_DROP_THRESHOLD,
                    reasons = reasons,
                )
            }
            .sortedByDescending { it.score }
            .toList()

    /**
     * How well a habit of [estimatedMinutes] fits the logged [energy].
     *
     * Unlogged energy is neutral: absence of data must not tilt ranking.
     * Low energy penalises long sessions gently rather than hiding them -
     * the user still sees them, just not first.
     */
    fun capacityFactor(estimatedMinutes: Int, energy: Int?): Float {
        if (energy == null) return 1f
        val e = energy.coerceIn(1, 5)
        val minutes = estimatedMinutes.coerceAtLeast(1)
        return when {
            e >= 4 -> if (minutes >= 20) 1.15f else 1.05f
            e == 3 -> 1f
            else -> when {
                minutes <= 5 -> 1.15f   // tiny actions shine on hard days
                minutes <= 15 -> 0.95f
                else -> 0.85f
            }
        }
    }

    /** The one habit to show as Today's Focus card, or null when nothing is open. */
    fun focus(
        today: List<TodayHabit>,
        statsOf: (String) -> HabitStats?,
        energy: Int? = null,
        checkedSoFar: Int = 0,
        dismissals: Map<String, Int> = emptyMap(),
    ): FocusCandidate? =
        rank(today, statsOf, energy, checkedSoFar, dismissals).firstOrNull { !it.dropped }
}

/** One rankable next action with its explanation. */
data class FocusCandidate(
    val habit: com.superflow.data.model.Habit,
    val score: Float,
    /** True after enough same-day dismissals: excluded from [FocusEngine.focus]. */
    val dropped: Boolean,
    /** Plain-language reasons, most significant first. Empty when neutral. */
    val reasons: List<String>,
)
