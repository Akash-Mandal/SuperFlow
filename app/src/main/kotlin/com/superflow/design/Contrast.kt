package com.superflow.design

/**
 * WCAG contrast maths.
 *
 * Pure integer/double work on packed ARGB, so it lives here and is tested
 * rather than being re-derived by eye in each place that needs to put text on
 * a coloured surface.
 *
 * Colours are packed ARGB ints, matching android.graphics.Color, but nothing
 * here depends on the framework.
 */
object Contrast {

    const val BLACK = 0xFF000000.toInt()
    const val WHITE = 0xFFFFFFFF.toInt()

    /** WCAG AA for normal-size text. */
    const val AA_NORMAL = 4.5

    /** WCAG AA for large text, and the floor for UI component boundaries. */
    const val AA_LARGE = 3.0

    /** WCAG AAA for normal-size text. */
    const val AAA_NORMAL = 7.0

    private fun channel(value: Int): Double {
        val c = (value and 0xFF) / 255.0
        return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
    }

    /** Relative luminance, 0 (black) to 1 (white). Alpha is ignored. */
    fun luminance(color: Int): Double =
        0.2126 * channel(color shr 16) +
            0.7152 * channel(color shr 8) +
            0.0722 * channel(color)

    /**
     * Contrast ratio between two colours, from 1.0 (identical) to 21.0
     * (black on white). Order does not matter.
     */
    fun ratio(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        val hi = if (la > lb) la else lb
        val lo = if (la > lb) lb else la
        return (hi + 0.05) / (lo + 0.05)
    }

    /**
     * Black or white, whichever is more legible on [background].
     *
     * Uses the real luminance curve rather than a brightness average. The two
     * disagree on exactly the colours this app uses most: saturated teals and
     * violets sit near the crossover, and the naive version picks wrong.
     */
    fun onColorFor(background: Int): Int =
        if (ratio(BLACK, background) >= ratio(WHITE, background)) BLACK else WHITE

    /** Whether [foreground] on [background] clears a given threshold. */
    fun meets(foreground: Int, background: Int, threshold: Double): Boolean =
        ratio(foreground, background) >= threshold
}
