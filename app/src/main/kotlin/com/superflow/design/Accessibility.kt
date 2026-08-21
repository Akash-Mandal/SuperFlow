package com.superflow.design

/**
 * Accessibility rules, as functions (plan 17).
 *
 * Most accessibility work is a judgement made per widget and forgotten. The
 * parts that generalise — how text scales, how big a target has to be, what
 * a colour-blind user sees instead of green, what a screen reader is told
 * when a check-in lands — are the parts that get inconsistent across a
 * hundred call sites. Putting them here makes them one decision, and one
 * decision is testable.
 *
 * No Android imports.
 */
object Accessibility {

    // -------------------------------------------------------- font scaling

    /**
     * The scale range we commit to supporting (17.2).
     *
     * Android allows more than this; we do not clamp the system setting,
     * which would be user-hostile and is not ours to override. This is the
     * range our layouts are *designed* against, and the range [reflow]
     * makes promises about.
     */
    const val MIN_SCALE = 0.85f
    const val MAX_SCALE = 2.0f

    /** Whether a scale is inside the range our layouts were designed for. */
    fun supported(scale: Float): Boolean = scale in MIN_SCALE..MAX_SCALE

    /**
     * Whether a layout should switch from a row to a stack.
     *
     * At large text, a label and its value side by side stop fitting and
     * one of them truncates — usually the value, which is the part that
     * mattered. Above the threshold the pair stacks instead. 1.3 is where
     * a two-line title starts overflowing a 56dp row at Comfortable
     * density, measured from the type scale rather than guessed.
     */
    fun reflow(scale: Float): Boolean = scale >= REFLOW_AT

    const val REFLOW_AT = 1.3f

    /**
     * How many lines a title may take before it is truncated.
     *
     * Truncating grows with the scale rather than staying fixed: a user at
     * 2x has asked for bigger text, and answering with the same two lines
     * means they see half as many words than everyone else.
     */
    fun titleMaxLines(scale: Float): Int = when {
        scale >= 1.6f -> 4
        scale >= 1.15f -> 3
        else -> 2
    }

    /**
     * Row height for a given base height and text scale, in dp.
     *
     * Heights grow with text, but sub-linearly: a row is mostly padding at
     * small scales, and multiplying the whole thing by 2 wastes a screen.
     * Padding stays put, the text box grows.
     */
    fun rowHeight(baseDp: Int, scale: Float): Int {
        val padding = 16
        val textBox = (baseDp - padding).coerceAtLeast(1)
        val grown = padding + Math.round(textBox * scale.coerceIn(MIN_SCALE, MAX_SCALE))
        return maxOf(grown, MIN_TARGET_DP)
    }

    // -------------------------------------------------------- touch targets

    /** The floor, everywhere, no exceptions (17.3). */
    const val MIN_TARGET_DP = 48

    /** Whether a target is big enough. */
    fun targetOk(widthDp: Int, heightDp: Int): Boolean =
        widthDp >= MIN_TARGET_DP && heightDp >= MIN_TARGET_DP

    /**
     * Invisible padding needed to bring a small visual to a legal target.
     *
     * Returned as a single symmetric value because asymmetric touch padding
     * makes a control feel misaligned even though it looks right, which is
     * a bug people report as "the button is offset".
     */
    fun expansionFor(visualDp: Int): Int =
        ((MIN_TARGET_DP - visualDp).coerceAtLeast(0) + 1) / 2

    // ------------------------------------------------------ colour blindness

    /**
     * Colour vision modes we offer alternatives for (17.2).
     *
     * The app's default success/miss pair is green and red, which is the
     * single most common failure: red-green deficiency affects roughly one
     * man in twelve, and our history strip is nothing but red and green
     * squares.
     */
    enum class ColorVision(val id: Int, val key: String, val label: String, val detail: String) {
        STANDARD(0, "standard", "Standard", "Green for done, red for missed."),
        DEUTERANOPIA(1, "deuteranopia", "Green-blind", "Blue for done, orange for missed."),
        PROTANOPIA(2, "protanopia", "Red-blind", "Blue for done, amber for missed."),
        TRITANOPIA(3, "tritanopia", "Blue-blind", "Teal for done, pink for missed."),
        ;
    }

    val colorVisionOptions: List<Choice> = ColorVision.entries.map {
        Choice(it.id, it.key, it.label, it.detail)
    }

    fun colorVision(id: Int): ColorVision =
        ColorVision.entries.firstOrNull { it.id == id } ?: ColorVision.STANDARD

    /**
     * The hue to use for a semantic state, as a symbolic name the ui layer
     * maps to a colour resource.
     *
     * Every alternative pair is separated along an axis the mode preserves,
     * and every pair also differs in lightness — so even a mode we did not
     * anticipate, or a monochrome screenshot, stays readable.
     */
    fun stateHue(state: String, vision: ColorVision): String = when (vision) {
        ColorVision.STANDARD -> when (state) {
            "done" -> "green"
            "missed" -> "red"
            "skipped" -> "grey"
            else -> "neutral"
        }

        ColorVision.DEUTERANOPIA, ColorVision.PROTANOPIA -> when (state) {
            "done" -> "blue"
            "missed" -> "orange"
            "skipped" -> "grey"
            else -> "neutral"
        }

        ColorVision.TRITANOPIA -> when (state) {
            "done" -> "teal"
            "missed" -> "pink"
            "skipped" -> "grey"
            else -> "neutral"
        }
    }

    /**
     * Whether a state also carries a shape or glyph, not only a colour.
     *
     * The real fix for colour blindness is not a second palette, it is not
     * relying on colour at all. Every state that means something gets a
     * mark: a tick, a dash, a dot. The alternative palettes are the belt;
     * this is the braces.
     */
    fun glyphFor(state: String): String = when (state) {
        "done" -> "check"
        "missed" -> "cross"
        "skipped" -> "dash"
        "pending" -> "dot"
        else -> "none"
    }

    // ------------------------------------------------------- announcements

    /**
     * What a screen reader is told after a check-in (17.1, live region).
     *
     * Announcing only "checked" leaves a blind user with no idea whether
     * that finished the day, so the announcement carries the consequence.
     * It is deliberately short: a live region interrupts, and a long
     * interruption is worse than none.
     */
    fun announceCheckIn(habitTitle: String, done: Int, total: Int): String {
        val head = "$habitTitle checked in"
        return when {
            total <= 0 -> "$head."
            done >= total -> "$head. Day complete."
            else -> "$head. $done of $total done."
        }
    }

    fun announceUndo(habitTitle: String): String = "$habitTitle unchecked."

    fun announceSkip(habitTitle: String): String =
        "$habitTitle skipped. It will not count against your consistency."

    /**
     * The announcement for a screen finishing its load.
     *
     * Sighted users get a skeleton dissolving into content; a screen reader
     * user gets nothing at all unless we say so, and is left tapping at a
     * screen that was empty a moment ago.
     */
    fun announceLoaded(screen: String, itemCount: Int): String = when {
        itemCount <= 0 -> "$screen loaded. Nothing here yet."
        itemCount == 1 -> "$screen loaded. One item."
        else -> "$screen loaded. $itemCount items."
    }

    // ------------------------------------------------------------- headings

    /**
     * Whether a row should be marked as a heading.
     *
     * Screen readers navigate by heading, and marking too many things as
     * headings is as useless as marking none: if every card is a heading,
     * heading navigation is just linear navigation with extra steps.
     * Section headers yes, cards no.
     */
    fun isHeading(role: String): Boolean = role in headingRoles

    private val headingRoles = setOf("sectionHeader", "screenTitle", "dateBreak", "groupLabel")

    // --------------------------------------------------------- motor access

    /**
     * Every swipe gesture must have a button equivalent (17.3).
     *
     * This is a list rather than a rule because it is checked by a test: if
     * someone adds a swipe action and forgets the alternative, the test
     * names it. The pairing is by the action key each surface uses.
     */
    val gestureAlternatives: Map<String, String> = mapOf(
        "swipeCheck" to "checkButton",
        "swipeSkip" to "overflowMenu",
        "longPressMenu" to "overflowMenu",
        "doubleTapRing" to "insightsTab",
        "pullRefresh" to "refreshMenuItem",
        "dragReorder" to "moveAccessibilityAction",
    )

    fun hasAlternative(gestureKey: String): Boolean = gestureKey in gestureAlternatives

    /**
     * No interaction may expire (17.3).
     *
     * Snackbar undo is the one place a timer exists, and it is the one
     * place it is defensible — but it needs a floor well above Material's
     * default, because "long" is 2.75 seconds and that is not enough time
     * to read a sentence, decide, find the button and hit it.
     */
    const val UNDO_MS = 8000L

    /** Undo timeout, extended when a screen reader is driving. */
    fun undoTimeout(screenReader: Boolean): Long = if (screenReader) UNDO_MS * 2 else UNDO_MS

    // -------------------------------------------------------- reduced motion

    /**
     * Whether an animation may run.
     *
     * The system "remove animations" setting wins over the app's motion
     * preference — a user who turned animations off at the OS level has
     * already answered this question, and asking again in our settings and
     * then ignoring their OS answer is exactly the failure the setting
     * exists to prevent.
     *
     * Essential motion still runs: a progress bar that does not move is
     * broken, not calm.
     */
    fun animates(systemRemovesAnimations: Boolean, appMotionEnabled: Boolean, essential: Boolean): Boolean {
        if (essential) return true
        if (systemRemovesAnimations) return false
        return appMotionEnabled
    }

    // ---------------------------------------------------------- bold text

    /**
     * Weight bump for the system bold-text setting (17.2).
     *
     * Applied as a delta rather than a replacement so the type scale keeps
     * its internal hierarchy: if body goes to 500 and titles stay at 600,
     * the difference between them shrinks and the page flattens.
     */
    fun weightFor(baseWeight: Int, boldText: Boolean): Int =
        if (!boldText) baseWeight else (baseWeight + 100).coerceAtMost(900)
}
