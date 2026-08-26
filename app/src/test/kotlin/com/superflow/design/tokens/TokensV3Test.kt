package com.superflow.design.tokens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class TokensV3Test {

    /* ------------------------------------------------------------ V3Radius */

    @Test
    fun `nested radius shrinks by inset but never below the floor`() {
        assertEquals(8, V3Radius.nested(V3Radius.CARD, 12))   // 20 - 12 = 8
        assertEquals(14, V3Radius.nested(V3Radius.HERO, 14))  // 28 - 14 = 14
    }

    @Test
    fun `nested radius floors at half control radius`() {
        // 20 - 18 would be 2, which is sharper than any allowed corner.
        assertEquals(V3Radius.CONTROL / 2, V3Radius.nested(V3Radius.CARD, 18))
        assertEquals(V3Radius.CONTROL / 2, V3Radius.nested(V3Radius.CARD, 100))
    }

    @Test
    fun `nested radius never exceeds the outer radius`() {
        assertEquals(V3Radius.CARD, V3Radius.nested(V3Radius.CARD, 0))
        assertEquals(V3Radius.HERO, V3Radius.nested(V3Radius.HERO, -4))
    }

    /* ------------------------------------------------------- spring specs */

    @Test
    fun `named springs have distinct feels`() {
        val feels = setOf(
            V3Springs.STANDARD, V3Springs.SNAPPY, V3Springs.SETTLE,
        )
        assertEquals(3, feels.size)
        // Settle must be critically damped: a snap-back that bounces reads as sloppiness.
        assertEquals(1f, V3Springs.SETTLE.dampingRatio)
    }

    /* -------------------------------------------------------- materials */

    @Test
    fun `material recipes are recoverable by name`() {
        assertEquals(V3Materials.PAPER, V3Materials.byName["paper"])
        assertEquals(V3Materials.GLASS, V3Materials.byName["glass"])
        assertEquals(V3Materials.INK, V3Materials.byName["ink"])
    }

    @Test
    fun `only glass wants blur`() {
        assertTrue(V3Materials.GLASS.blur)
        assertTrue(!V3Materials.PAPER.blur && !V3Materials.INK.blur)
    }
}

class ElevationTintTest {

    /* ---------------------------------------------------------------- mix */

    @Test
    fun `dark mode mixes toward accent with elevation`() {
        val m0 = ElevationTint.mix(0f, isDark = true)
        val m8 = ElevationTint.mix(8f, isDark = true)
        val m24 = ElevationTint.mix(24f, isDark = true)
        assertEquals(0f, m0)
        assertTrue(m8 > m0 && m24 > m8)
        // Capped at 24dp.
        assertEquals(m24, ElevationTint.mix(96f, isDark = true))
    }

    @Test
    fun `light mode mixes away from base (negative) and caps later`() {
        val half = ElevationTint.mix(48f, isDark = false)
        val capped = ElevationTint.mix(96f, isDark = false)
        assertTrue(half < 0f && capped < half)
        // Cap reached: anything beyond 96dp matches.
        assertEquals(capped, ElevationTint.mix(480f, isDark = false))
    }

    /* ------------------------------------------------------------- surface */

    @Test
    fun `zero elevation returns base unchanged in both modes`() {
        val base = 0xFFF6F2ECL
        val accent = 0xFF7A9E7EL
        assertEquals(base, ElevationTint.surfaceArgb(0f, base, accent, isDark = false))
        assertEquals(base, ElevationTint.surfaceArgb(0f, base, accent, isDark = true))
    }

    @Test
    fun `oled level zero pins to pure black`() {
        assertEquals(
            0xFF000000L,
            ElevationTint.surfaceArgb(0f, 0xFF101010L, 0xFF7A9E7EL, isDark = true, oled = true),
        )
    }

    @Test
    fun `dark surfaces warm toward accent and never exceed it`() {
        val base = 0xFF121212L
        val accent = 0xFF7A9E7EL
        val e0 = ElevationTint.surfaceArgb(0f, base, accent, isDark = true)
        val e24 = ElevationTint.surfaceArgb(24f, base, accent, isDark = true)
        val r0 = (e0 shr 16 and 0xFF).toInt()
        val r24 = (e24 shr 16 and 0xFF).toInt()
        val rAccent = 0x7A
        assertTrue(r24 > r0)                       // rose with elevation
        assertTrue(r24 <= rAccent + 1)             // capped below/at the accent channel
    }

    @Test
    fun `light surfaces darken with elevation`() {
        val base = 0xFFF6F2ECL
        val accent = 0xFF7A9E7EL
        val e48 = ElevationTint.surfaceArgb(48f, base, accent, isDark = false)
        val lBase = (base shr 16 and 0xFF).toInt()
        val l48 = (e48 shr 16 and 0xFF).toInt()
        assertTrue(l48 < lBase)
        // But only slightly: paper stacking, not a shadow pit.
        assertTrue(lBase - l48 <= 32)
    }

    /* ---------------------------------------------------------------- lerp */

    @Test
    fun `lerp endpoints and midpoint`() {
        val black = 0xFF000000L
        val white = 0xFFFFFFFFL
        assertEquals(black, ElevationTint.lerpArgb(black, white, 0f))
        assertEquals(white, ElevationTint.lerpArgb(black, white, 1f))
        val mid = ElevationTint.lerpArgb(black, white, 0.5f)
        val g = (mid shr 16 and 0xFF).toInt()
        assertTrue(abs(g - 128) <= 1)
    }

    @Test
    fun `lerp clamps out-of-range t`() {
        val black = 0xFF000000L
        val white = 0xFFFFFFFFL
        assertEquals(black, ElevationTint.lerpArgb(black, white, -3f))
        assertEquals(white, ElevationTint.lerpArgb(black, white, 3f))
    }
}
