package com.superflow.design

/**
 * Design token values and the pure decisions that derive from them.
 *
 * This package is deliberately free of Android and Compose imports. Anything
 * that can be expressed as "given these settings, what should the UI do" lives
 * here as a pure function, so it is unit-testable and shared by the View and
 * Compose layers rather than reimplemented in each.
 *
 * Rendering concerns -- resolving a token to a themed colour, running an
 * animator, firing a vibration -- belong in `ui/`, which consumes this.
 *
 * The dp/sp figures below are the design system's scale (§4.1, §5.2, §7.1) as
 * plain numbers. The XML resources in `res/values/` carry the same values for
 * layouts; these exist for code paths that compute geometry (charts, canvas
 * drawing, Compose modifiers) and must agree with them. `SpacingTest` pins the
 * scale so the two cannot drift silently.
 */
object Space {
    const val XXS = 2
    const val XS = 4
    const val SM = 8
    const val MD = 12
    const val BASE = 16
    const val LG = 24
    const val XL = 32
    const val XXL = 48
    const val XXXL = 64

    /** The scale in ascending order. */
    val scale = intArrayOf(XXS, XS, SM, MD, BASE, LG, XL, XXL, XXXL)

    /**
     * Snaps an arbitrary dp value to the nearest step on the scale.
     *
     * Used when a computed dimension (a chart gutter, a drag offset) needs to
     * land on the grid rather than on whatever the arithmetic produced. Ties
     * round down, toward the tighter spacing.
     */
    fun snap(dp: Int): Int {
        if (dp <= XXS) return XXS
        if (dp >= XXXL) return XXXL
        var best = scale[0]
        var bestDist = Int.MAX_VALUE
        for (step in scale) {
            val d = kotlin.math.abs(step - dp)
            if (d < bestDist) { bestDist = d; best = step }
        }
        return best
    }
}

/** Corner radius scale (§7.1). */
object Radius {
    const val NONE = 0
    const val XXS = 4
    const val XS = 8
    const val SM = 12
    const val MD = 18
    const val LG = 24
    const val XL = 32

    /** Sentinel meaning "fully rounded"; resolved against the view height. */
    const val FULL = -1
}

/** Type scale in sp (§5.2), paired with its line height. */
object TypeScale {
    const val DISPLAY = 40
    const val HEADLINE_L = 32
    const val HEADLINE_M = 24
    const val TITLE_L = 20
    const val TITLE_M = 16
    const val BODY_L = 16
    const val BODY_M = 14
    const val LABEL_L = 14
    const val LABEL_M = 12
    const val OVERLINE = 11
    const val DATA = 13
    const val DATA_LARGE = 28
    const val IDENTITY = 20
    const val JOURNAL = 17
}

/**
 * Motion levels and the durations derived from them (§8).
 *
 * Durations are expressed as base values in milliseconds and scaled by the
 * user's motion preference. Callers must check [Motion.isDisabled] and skip
 * the animation outright rather than running a zero-length one: a
 * zero-duration animator still posts a frame and still fires listeners, which
 * produces a visible flicker on some devices.
 */
object Motion {
    const val NONE = 0
    const val REDUCED = 1
    const val STANDARD = 2
    const val EXPRESSIVE = 3

    /** Base durations, at STANDARD. */
    const val INSTANT = 50
    const val FAST = 120
    const val QUICK = 180
    const val NORMAL = 250
    const val SLOW = 350
    const val DELIBERATE = 500

    /** Stagger between items in an orchestrated list entrance. */
    const val STAGGER = 40

    /** Cap on total stagger, so a long list does not crawl in. */
    const val STAGGER_MAX_ITEMS = 8

    fun scaleFor(level: Int): Float = when (level) {
        NONE -> 0f
        REDUCED -> 0.5f
        EXPRESSIVE -> 1.25f
        else -> 1f
    }

    fun isDisabled(level: Int, systemAnimationsOff: Boolean = false): Boolean =
        level == NONE || systemAnimationsOff

    /**
     * Scales a base duration for the given motion level.
     *
     * Returns 0 when motion is off, which callers treat as "skip". Otherwise
     * the result is clamped to at least 1ms so a heavily reduced short
     * duration never rounds down into the skip sentinel.
     */
    fun duration(base: Int, level: Int, systemAnimationsOff: Boolean = false): Int {
        if (isDisabled(level, systemAnimationsOff)) return 0
        val scaled = (base * scaleFor(level)).toInt()
        return if (scaled < 1) 1 else scaled
    }

    /**
     * Delay before item [index] in a staggered entrance.
     *
     * The stagger stops accumulating after [STAGGER_MAX_ITEMS] so that the
     * last row of a long list is not left waiting; everything beyond the cap
     * animates together with the item at the cap.
     */
    fun staggerDelay(index: Int, level: Int, systemAnimationsOff: Boolean = false): Int {
        if (isDisabled(level, systemAnimationsOff)) return 0
        if (index <= 0) return 0
        val capped = if (index > STAGGER_MAX_ITEMS) STAGGER_MAX_ITEMS else index
        return (capped * STAGGER * scaleFor(level)).toInt()
    }
}

/**
 * Haptic patterns (§9).
 *
 * A pattern is a list of (durationMs, amplitude 0..1) pairs; amplitude is
 * scaled by the user's intensity preference at playback time. Keeping them as
 * data rather than as calls into Vibrator makes the whole vocabulary
 * inspectable and testable, and lets the View and Compose layers share one
 * definition.
 */
data class HapticPattern(val name: String, val steps: List<Pair<Int, Float>>) {

    /** Total wall-clock duration of the pattern. */
    val durationMs: Int get() = steps.sumOf { it.first }

    /**
     * Applies an intensity multiplier, clamping amplitude into 0..1.
     *
     * Returns null when the result would be silent, so callers can skip the
     * vibrator call entirely instead of scheduling a no-op.
     */
    fun scaled(factor: Float): HapticPattern? {
        if (factor <= 0f) return null
        return copy(steps = steps.map { (d, a) ->
            d to (a * factor).coerceIn(0f, 1f)
        })
    }
}

/**
 * The app's haptic vocabulary. Each pattern maps to a specific meaning, so
 * that touch feedback is a consistent language rather than ad-hoc buzzing.
 */
object Haptics {
    /** A tap landed on something interactive. */
    val TICK = HapticPattern("tick", listOf(8 to 0.35f))

    /** Selection moved: chip, tab, segmented control. */
    val SELECT = HapticPattern("select", listOf(10 to 0.5f))

    /** Habit completed. Two beats, second slightly stronger: a small "yes". */
    val COMPLETE = HapticPattern("complete", listOf(
        12 to 0.6f, 40 to 0f, 18 to 0.85f))

    /** Habit un-completed. The complete pattern reversed and softened. */
    val UNDO = HapticPattern("undo", listOf(
        14 to 0.5f, 40 to 0f, 10 to 0.3f))

    /** Swipe passed the action threshold and will commit on release. */
    val THRESHOLD = HapticPattern("threshold", listOf(6 to 0.7f))

    /** Streak milestone. Three ascending beats. */
    val MILESTONE = HapticPattern("milestone", listOf(
        14 to 0.5f, 50 to 0f, 14 to 0.7f, 50 to 0f, 26 to 1f))

    /** Long-press engaged a drag. */
    val LIFT = HapticPattern("lift", listOf(18 to 0.65f))

    /** Item dropped into place. */
    val DROP = HapticPattern("drop", listOf(12 to 0.45f))

    /** Something failed or was rejected. Two flat beats, no crescendo. */
    val REJECT = HapticPattern("reject", listOf(
        20 to 0.55f, 60 to 0f, 20 to 0.55f))

    /** Destructive action confirmed. */
    val CONFIRM_DESTRUCTIVE = HapticPattern("confirmDestructive", listOf(
        30 to 0.8f))

    /** Every pattern, for tests and for the settings preview. */
    val all = listOf(
        TICK, SELECT, COMPLETE, UNDO, THRESHOLD,
        MILESTONE, LIFT, DROP, REJECT, CONFIRM_DESTRUCTIVE,
    )
}

/**
 * Density metrics (§4.2). Whitespace only -- type size is the platform's
 * concern and the user's accessibility setting, never ours to override.
 */
data class DensityMetrics(
    val cardPadding: Int,
    val listItemHeight: Int,
    val cardGap: Int,
    val sectionSpacing: Int,
    val lineSpacing: Float,
)

object Density {
    const val COMPACT = 0
    const val COMFORTABLE = 1
    const val SPACIOUS = 2

    private val compact = DensityMetrics(12, 48, 8, 16, 1.15f)
    private val comfortable = DensityMetrics(20, 56, 12, 24, 1.25f)
    private val spacious = DensityMetrics(24, 64, 16, 32, 1.40f)

    fun metrics(level: Int): DensityMetrics = when (level) {
        COMPACT -> compact
        SPACIOUS -> spacious
        else -> comfortable
    }
}

/**
 * The habit ladder. Ordered weakest to strongest; [ordinalOf] is used by
 * charts to position a level on an axis.
 */
object Levels {
    const val TINY = "tiny"
    const val MINIMUM = "minimum"
    const val STANDARD = "standard"
    const val STRETCH = "stretch"

    val ordered = listOf(TINY, MINIMUM, STANDARD, STRETCH)

    /** -1 for an unrecognised level, so callers can skip rather than crash. */
    fun ordinalOf(level: String?): Int =
        ordered.indexOf(level?.lowercase()?.trim())

    /**
     * Fraction of the ladder a level represents, 0..1.
     *
     * Used to size the progress contribution of a completion. Tiny is
     * deliberately non-zero: showing up at all is the point of the ladder.
     */
    fun weight(level: String?): Float = when (ordinalOf(level)) {
        0 -> 0.4f
        1 -> 0.7f
        2 -> 1f
        3 -> 1.15f
        else -> 0f
    }
}
