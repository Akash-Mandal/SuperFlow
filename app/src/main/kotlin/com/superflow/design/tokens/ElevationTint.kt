package com.superflow.design.tokens

/**
 * Tonal elevation ("Ink & Paper", ALPHA3_VISUAL_PLAN §4.2).
 *
 * Replaces flat surfaces with elevation-driven tinting toward the palette's
 * accent:
 *
 *  - Light ("Paper"): surfaces get cooler and slightly darker as they rise,
 *    like stacked paper.
 *  - Dark ("Ink"): surfaces get warmer/accent-tinted as they rise, like
 *    light from above.
 *
 * Pure ARGB arithmetic so the design package stays Compose-free and the
 * curve is unit-testable. `ui/theme` wraps this for Compose colors.
 */
object ElevationTint {

    /** Elevation at which the dark-mode mix reaches its cap. */
    private const val DARK_FULL_AT_DP = 24f

    /** Cap on how far dark surfaces shift toward the accent (0..1). */
    private const val DARK_MAX_MIX = 0.12f

    /** Elevation at which the light-mode mix reaches its cap. */
    private const val LIGHT_FULL_AT_DP = 96f

    /** Cap on how far light surfaces shift away from base toward depth. */
    private const val LIGHT_MAX_MIX = 0.04f

    /**
     * The mix fraction for an elevation step, signed: positive means "toward
     * accent" (dark mode), negative means "toward shade" (light mode).
     */
    fun mix(elevationDp: Float, isDark: Boolean): Float =
        if (isDark) {
            (elevationDp / DARK_FULL_AT_DP).coerceIn(0f, 1f) * DARK_MAX_MIX
        } else {
            -(elevationDp / LIGHT_FULL_AT_DP).coerceIn(0f, 1f) * LIGHT_MAX_MIX
        }

    /**
     * Resolved surface color for an elevation step.
     *
     * @param baseArgb   the palette's level-0 canvas color
     * @param accentArgb the palette accent; in dark mode the tint target,
     *                   in light mode only used to pick the shade direction
     *                   so paper darkens without hue drift
     * @param oled       true for the OLED dark variant: level 0 pins to pure
     *                   black and the mix curve halves (§4.2)
     */
    fun surfaceArgb(
        elevationDp: Float,
        baseArgb: Long,
        accentArgb: Long,
        isDark: Boolean,
        oled: Boolean = false,
    ): Long {
        if (isDark && oled && elevationDp <= 0f) return 0xFF000000L
        val m = mix(elevationDp, isDark)
        return if (isDark) {
            val t = if (oled) kotlin.math.abs(m) * 0.5f else kotlin.math.abs(m)
            lerpArgb(baseArgb, accentArgb, t)
        } else {
            // Paper stacking: darken the base proportionally, with a slight
            // blue-ward pull so it reads as cool shadow rather than tint.
            val f = 1f - kotlin.math.abs(m)
            fun deep(shift: Int, blueBias: Int = 0): Long {
                val c = (baseArgb shr shift and 0xFF).toInt()
                val v = ((c * f).toInt() + (blueBias * kotlin.math.abs(m) * 50).toInt())
                    .coerceIn(0, 255)
                return v.toLong()
            }
            (0xFFL shl 24) or (deep(16) shl 16) or (deep(8) shl 8) or deep(0, blueBias = 1)
        }
    }

    /** ARGB lerp with t clamped; channels blended independently. */
    fun lerpArgb(startArgb: Long, stopArgb: Long, t: Float): Long {
        val frac = t.coerceIn(0f, 1f)
        fun channel(argb: Long, shift: Int): Long = argb shr shift and 0xFF
        fun blend(s: Long, e: Long) = s + ((e - s) * frac).toLong()
        val r = blend(channel(startArgb, 16), channel(stopArgb, 16))
        val g = blend(channel(startArgb, 8), channel(stopArgb, 8))
        val b = blend(channel(startArgb, 0), channel(stopArgb, 0))
        return (0xFFL shl 24) or (r shl 16) or (g shl 8) or b
    }
}
