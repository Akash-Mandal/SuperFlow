package com.superflow.design

/**
 * Widget sizing and content selection (plan 16).
 *
 * A widget cannot ask what it should show — it is handed a size in dp by the
 * launcher and has to decide. Get it wrong and you either ship a 2x2 that
 * clips its own text or a 4x4 with a ring floating in the middle of an
 * empty field. Both were shipped by real apps this year.
 *
 * The decision is entirely a function of (width, height, how much there is
 * to show), so it lives here where it can be tested against every size a
 * launcher actually hands out, including the ones no phone in this room has.
 *
 * The plan asks for Jetpack Glance. Glance is not in the dependency set, so
 * the Android side stays RemoteViews for now; this model is written so that
 * the switch changes only who reads it.
 *
 * No Android imports.
 */
object WidgetLayout {

    /** The four sizes from 16.1. */
    enum class Size(val label: String, val minWidthDp: Int, val minHeightDp: Int) {
        /** 2x2. Ring and a percentage. */
        SMALL("Small", 110, 110),

        /** 4x2. Ring, the next habit, one check-in button. */
        MEDIUM("Medium", 250, 110),

        /** 5x2. A bar instead of a ring, plus the day's focus items. */
        WIDE("Wide", 320, 110),

        /** 4x4. Ring and the whole day, each row checkable. */
        LARGE("Large", 250, 250),
        ;
    }

    /**
     * Picks a size for a measured widget.
     *
     * Height is checked before width, because a launcher that reports a
     * generous width and a squeezed height is the common case (Pixel's
     * 5-column grid), and rendering a Large layout into a 2-row cell is
     * what produces the clipping.
     */
    fun sizeFor(widthDp: Int, heightDp: Int): Size = when {
        heightDp >= Size.LARGE.minHeightDp && widthDp >= Size.LARGE.minWidthDp -> Size.LARGE
        widthDp >= Size.WIDE.minWidthDp -> Size.WIDE
        widthDp >= Size.MEDIUM.minWidthDp -> Size.MEDIUM
        else -> Size.SMALL
    }

    /** Whether a size draws the progress ring rather than a bar. */
    fun usesRing(size: Size): Boolean = size != Size.WIDE

    /**
     * How many habit rows fit.
     *
     * Only Large lists habits; Medium shows exactly one, and Wide shows the
     * day's focus items rather than habits. The cap exists because a widget
     * that scrolls is a widget people fight with.
     */
    fun habitRows(size: Size, available: Int): Int = when (size) {
        Size.SMALL -> 0
        Size.MEDIUM -> minOf(1, available)
        Size.WIDE -> minOf(2, available)
        Size.LARGE -> minOf(MAX_LARGE_ROWS, available)
    }

    const val MAX_LARGE_ROWS = 5

    /** Whether check-in buttons are drawn next to rows. */
    fun interactive(size: Size): Boolean = size != Size.SMALL

    // -------------------------------------------------------------- content

    /**
     * What the widget says, given the day's state.
     *
     * Four states, and the wording of each matters more than the layout.
     * "0 of 5" on an untouched morning is a scolding; "5 to do" is a plan.
     */
    data class Content(
        val headline: String,
        val subhead: String,
        val percent: Int,
        val showsAction: Boolean,
        val actionLabel: String,
    )

    fun content(done: Int, total: Int, nextHabit: String?, timeOfDay: TimeOfDay): Content {
        val percent = if (total <= 0) 0 else Math.round(done * 100f / total)
        return when {
            total == 0 -> Content(
                headline = "Nothing scheduled",
                subhead = "Add a habit to begin",
                percent = 0,
                showsAction = false,
                actionLabel = "",
            )

            done >= total -> Content(
                headline = "Day complete",
                subhead = if (total == 1) "One vote cast" else "$total votes cast",
                percent = 100,
                showsAction = false,
                actionLabel = "",
            )

            done == 0 -> Content(
                // Contextual by time of day (16.2). The same empty day reads
                // differently at seven in the morning and at nine at night.
                headline = when (timeOfDay) {
                    TimeOfDay.MORNING -> "$total to do"
                    TimeOfDay.AFTERNOON -> "$total still open"
                    TimeOfDay.EVENING -> "$total left today"
                    TimeOfDay.NIGHT -> "$total unfinished"
                },
                subhead = nextHabit ?: "Open to start",
                percent = 0,
                showsAction = nextHabit != null,
                actionLabel = "Check in",
            )

            else -> Content(
                headline = "$done of $total done",
                subhead = nextHabit ?: "Nearly there",
                percent = percent,
                showsAction = nextHabit != null,
                actionLabel = "Check in",
            )
        }
    }

    /** Coarse time buckets used for contextual copy. */
    enum class TimeOfDay { MORNING, AFTERNOON, EVENING, NIGHT }

    /** @param hour 0..23. */
    fun timeOfDay(hour: Int): TimeOfDay = when (hour.coerceIn(0, 23)) {
        in 5..11 -> TimeOfDay.MORNING
        in 12..16 -> TimeOfDay.AFTERNOON
        in 17..21 -> TimeOfDay.EVENING
        else -> TimeOfDay.NIGHT
    }

    /**
     * The widget's spoken description.
     *
     * A widget gets one focus stop on most launchers, so everything the
     * sighted user can see has to fit in one sentence.
     */
    fun describe(content: Content): String = buildString {
        append("SuperFlow. ")
        append(content.headline)
        if (content.subhead.isNotBlank()) {
            append(". ")
            append(content.subhead)
        }
        append(".")
    }

    // -------------------------------------------------------------- refresh

    /**
     * How often a widget may redraw itself, in milliseconds.
     *
     * The system minimum is 30 minutes for periodic updates and we do not
     * fight it: the widget is refreshed on real events (a check-in, the app
     * pausing) and the periodic tick is only a safety net for midnight
     * rollover.
     */
    const val PERIODIC_MS = 30 * 60 * 1000L

    /**
     * Whether a redraw is worth doing.
     *
     * Widgets are redrawn from several places at once — the app pausing, a
     * check-in broadcast, a work-manager tick — and each redraw is an IPC
     * round trip plus a database read. Identical content within the debounce
     * window is dropped.
     */
    fun shouldRedraw(previous: Content?, next: Content, sinceMs: Long): Boolean =
        previous != next || sinceMs >= PERIODIC_MS

    /** Corner radius in dp, matching the app's card shape (16.2). */
    const val CORNER_DP = 20
}
