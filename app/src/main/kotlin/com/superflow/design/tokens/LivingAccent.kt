package com.superflow.design.tokens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * Living accent (ALPHA3_VISUAL_PLAN §4.3): the accent shifts subtly with
 * time of day when enabled. Off by default, zero permissions, pure math
 * on the palette's own ramp via HSV hue shift.
 */
object LivingAccent {

    fun shift(base: Color, hour: Int): Color {
        val h = hour.coerceIn(0, 23)
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(base.toArgb(), hsv)
        val delta = when (h) {
            in 6..11 -> 5f
            in 12..17 -> 0f
            in 18..21 -> -8f
            else -> -4f
        }
        if (delta == 0f) return base
        hsv[0] = (hsv[0] + delta).let { v -> ((v % 360f) + 360f) % 360f }
        val shifted = android.graphics.Color.HSVToColor(hsv)
        return Color(shifted).copy(alpha = base.alpha)
    }
}
