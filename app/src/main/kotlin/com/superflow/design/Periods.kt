package com.superflow.design

/**
 * The insights period switcher (§11.3).
 *
 * Which window the charts cover, and - more importantly - what can honestly
 * be said about a window that size. The second part is the reason this is
 * tested logic rather than four buttons: the difference between "you are 12%
 * more consistent on weekdays" and "there is not enough data to say" is a
 * sample-size rule, and getting it wrong turns noise into advice.
 */
object Periods {

    const val WEEK_ID = 0
    const val MONTH_ID = 1
    const val QUARTER_ID = 2
    const val YEAR_ID = 3

    /**
     * @param days       how many days the window covers
     * @param label      the switcher button text
     * @param barBucket  how many days each bar aggregates; a year of daily
     *                   bars is 365 unreadable slivers, so longer windows
     *                   group into weeks or months
     */
    data class Period(
        val id: Int,
        val days: Int,
        val label: String,
        val barBucket: Int,
    ) {
        /** How many bars a chart over this period will draw. */
        val barCount: Int get() = (days + barBucket - 1) / barBucket
    }

    val week = Period(WEEK_ID, 7, "7d", 1)
    val month = Period(MONTH_ID, 30, "30d", 1)
    val quarter = Period(QUARTER_ID, 90, "90d", 7)
    val year = Period(YEAR_ID, 365, "Year", 30)

    val all = listOf(week, month, quarter, year)

    fun byId(id: Int): Period = all.firstOrNull { it.id == id } ?: month

    /**
     * The smallest sample this analysis needs before it says anything.
     *
     * These are not statistical thresholds - a personal habit tracker will
     * never have the sample size for that - they are honesty thresholds. The
     * question each answers is "would a reasonable person be annoyed to
     * learn this claim rested on so little?".
     */
    object MinSamples {
        /** A completion rate is meaningful once there is about a week of it. */
        const val COMPLETION_RATE = 5

        /** A weekday-versus-weekend split needs a few of each. */
        const val DAY_OF_WEEK = 14

        /** Any correlation claim. Matches ChartGeometry.correlationLabel. */
        const val CORRELATION = 14

        /** A trend claim: two windows to compare, so twice the rate minimum. */
        const val TREND = 21
    }

    /**
     * Whether [samples] supports a claim needing [minimum].
     *
     * Trivial, and named so that call sites read as a decision about honesty
     * rather than an arithmetic comparison someone will later "simplify".
     */
    fun canClaim(samples: Int, minimum: Int): Boolean = samples >= minimum

    /**
     * A caveat to attach to a finding, or null when none is needed.
     *
     * Shown next to the claim rather than buried in a help screen. A number
     * with no context invites more confidence than it has earned.
     */
    fun caveatFor(samples: Int, minimum: Int): String? = when {
        samples < minimum -> "Not enough data yet"
        samples < minimum * 2 -> "Based on $samples days so far"
        else -> null
    }

    /**
     * Buckets a daily series for charting over a period.
     *
     * Each bucket is the mean of its days, so a 90-day chart shows weekly
     * averages rather than every seventh day - sampling would let a single
     * unusual Tuesday stand in for its whole week.
     *
     * Partial trailing buckets average what they have. Dropping them would
     * hide the most recent days, which are the ones people look at.
     */
    fun bucket(values: List<Double>, period: Period): List<Double> {
        if (values.isEmpty()) return emptyList()
        if (period.barBucket <= 1) return values
        return values.chunked(period.barBucket).map { chunk -> chunk.sum() / chunk.size }
    }

    /**
     * The most recent [Period.days] entries, oldest first.
     *
     * Shorter input is returned whole rather than padded: padding with zeros
     * would render "no data yet" as "you failed every day", which is the
     * single most demoralising bug a habit tracker can have.
     */
    fun window(values: List<Double>, period: Period): List<Double> =
        if (values.size <= period.days) values else values.takeLast(period.days)
}
