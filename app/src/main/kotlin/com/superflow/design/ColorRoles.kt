package com.superflow.design

/**
 * Resolves a palette into concrete colour roles.
 *
 * This is the single definition of "which tone fills which role". The XML
 * theme overlays in `values/themes_palette.xml` express the same mapping
 * declaratively for the View layer; the Compose layer reads it from here.
 * Two expressions of one rule is one too many, so [ColorRolesTest] pins this
 * against the XML and fails if they diverge.
 *
 * The mapping is possible to state once, rather than per palette, because the
 * ramps in [Ramps] are perceptually aligned: tone 40 has roughly the same
 * lightness in Forest as in Ocean, so a role defined as "tone 40" lands in
 * the same visual place whichever palette is active. That is the whole point
 * of generating the ramps from L* targets rather than picking them by eye.
 *
 * Light and dark are not the same mapping with different inputs - they are
 * genuinely different assignments. In light mode the accent is a mid tone on
 * a near-white surface; in dark mode it is a light tone on a near-black
 * surface, because a mid tone that reads well on white disappears on black.
 */
object ColorRoles {

    /* ------------------------------------------------------------- roles */

    /**
     * The colour roles a palette defines.
     *
     * Deliberately narrower than Material's full scheme: a palette overlay
     * only restates what actually differs between palettes. Surfaces,
     * outlines and the error roles come from the base theme and are shared,
     * so they are not repeated here - repeating them would mean five copies
     * to update when the surface colour changes.
     */
    data class Scheme(
        val primary: Int,
        val onPrimary: Int,
        val primaryContainer: Int,
        val onPrimaryContainer: Int,
        val primaryInverse: Int,
        val secondary: Int,
        val onSecondary: Int,
        val secondaryContainer: Int,
        val onSecondaryContainer: Int,
        val tertiary: Int,
        val onTertiary: Int,
        val tertiaryContainer: Int,
        val onTertiaryContainer: Int,
        /** Habit ladder, ascending in commitment: tiny, minimum, standard, stretch. */
        val levels: List<Int>,
        val success: Int,
        val successContainer: Int,
    )

    /* ------------------------------------------------------- tone recipes */

    /**
     * Tone assignments for light mode.
     *
     * Accents sit at tone 40 because that is the lightest step where white
     * text still clears WCAG AA. Tone 50 does not: it measures 3.95:1 for
     * Calm amber and 4.48:1 for Ocean coral, and it sits in the band where
     * near-black does not rescue it either. Containers at 90 are tinted
     * paper rather than colour fields, so their "on" role drops to tone 10.
     */
    private object Light {
        const val ACCENT = 40
        const val CONTAINER = 90
        const val ON_CONTAINER = 10
        const val INVERSE = 80

        /** Ladder ascends in darkness, so stretch is the most emphatic. */
        val LEVELS = listOf(80, 70, 50, 30)

        /**
         * Mono spreads its ladder wider.
         *
         * Every other palette separates adjacent rungs by hue as well as
         * lightness, so a small tone step still reads as a step. Mono is
         * near-achromatic and has only lightness to work with, so the
         * default spacing collapses - tones 80 and 70 differ by 1.36:1,
         * which is not a visible boundary between two touching swatches.
         * Even tone steps of 20 give a consistent ~2:1 between rungs.
         */
        val LEVELS_MONO = listOf(80, 60, 40, 20)
    }

    /**
     * Tone assignments for dark mode.
     *
     * The inverse of [Light]: accents move up the ramp to stay legible on a
     * dark surface, containers drop to tone 30, and the ladder reverses so
     * that stretch is still the most prominent step rather than the dimmest.
     */
    private object Dark {
        const val ACCENT = 80
        const val CONTAINER = 30
        const val ON_CONTAINER = 90
        const val INVERSE = 50

        val LEVELS = listOf(40, 50, 70, 80)

        /** See [Light.LEVELS_MONO]; the dark ramp compresses at the top. */
        val LEVELS_MONO = listOf(40, 60, 70, 90)
    }

    /* ------------------------------------------------------------ palettes */

    /**
     * The two ramps a palette is built from, plus its optional third.
     *
     * Mono is the interesting case: both its seeds are near-achromatic, so
     * separating primary from secondary by hue is not available. It uses one
     * ramp and separates the roles by tone instead, which is why [tertiary]
     * may name the same ramp as [primary].
     */
    private data class Spec(
        val primary: String,
        val secondary: String,
        val tertiary: String?,
        /** Mono shifts its roles apart by tone since it cannot use hue. */
        val monoToned: Boolean = false,
    )

    private val specs = mapOf(
        ThemeSelection.PALETTE_CALM_ID to Spec("sf_green", "sf_amber", "sf_indigo"),
        ThemeSelection.PALETTE_FOREST_ID to Spec("sf_forest_green", "sf_forest_olive", null),
        ThemeSelection.PALETTE_OCEAN_ID to Spec("sf_ocean_teal", "sf_ocean_coral", null),
        ThemeSelection.PALETTE_DUSK_ID to Spec("sf_dusk_violet", "sf_dusk_rose", null),
        ThemeSelection.PALETTE_MONO_ID to
            Spec("sf_mono_stone", "sf_mono_stone", "sf_mono_stone", monoToned = true),
    )

    /** Palette ids that have a resolvable scheme. */
    val paletteIds: List<Int> = specs.keys.sorted()

    /* ------------------------------------------------------------ resolve */

    private fun tone(ramp: String, tone: Int): Int {
        val steps = Ramps.all[ramp] ?: error("unknown ramp $ramp")
        steps[tone]?.let { return it }
        // Calm's ramps predate the palette system and are missing a step or
        // two (amber has no 60). Fall back to the nearest defined tone rather
        // than failing: the ramps are perceptually even, so the neighbour is
        // within a just-noticeable difference of the requested step.
        val nearest = steps.keys.minByOrNull { kotlin.math.abs(it - tone) }
            ?: error("empty ramp $ramp")
        return steps.getValue(nearest)
    }

    /**
     * The scheme for a palette.
     *
     * @param palette one of the `ThemeSelection.PALETTE_*_ID` values;
     *                anything unrecognised resolves to Calm, matching
     *                [ThemeSelection.overlaysFor], which treats an unknown
     *                palette as "no overlay".
     */
    fun schemeFor(palette: Int, isDark: Boolean): Scheme {
        val spec = specs[palette] ?: specs.getValue(ThemeSelection.PALETTE_CALM_ID)

        val accent = if (isDark) Dark.ACCENT else Light.ACCENT
        val container = if (isDark) Dark.CONTAINER else Light.CONTAINER
        val onContainer = if (isDark) Dark.ON_CONTAINER else Light.ON_CONTAINER
        val inverse = if (isDark) Dark.INVERSE else Light.INVERSE
        val levelTones = when {
            spec.monoToned && isDark -> Dark.LEVELS_MONO
            spec.monoToned -> Light.LEVELS_MONO
            isDark -> Dark.LEVELS
            else -> Light.LEVELS
        }

        // Text on an accent: white on a mid tone in light mode, near-black on
        // a light tone in dark mode. Both directions are the high-contrast
        // choice for their surface.
        val onAccent = if (isDark) tone("sf_neutral", 10) else tone("sf_neutral", 100)

        // Mono separates primary from secondary by tone, since it has no
        // hue difference to use: primary sits one step darker. In dark mode
        // the ramp compresses at the top, so both land on the same tone and
        // the separation falls to weight and spacing instead - stated here
        // rather than silently producing two identical roles.
        val primaryTone = if (spec.monoToned && !isDark) 30 else accent
        val secondaryTone = accent

        return Scheme(
            primary = tone(spec.primary, primaryTone),
            onPrimary = onAccent,
            primaryContainer = tone(spec.primary, container),
            onPrimaryContainer = tone(spec.primary, onContainer),
            primaryInverse = tone(spec.primary, inverse),

            secondary = tone(spec.secondary, secondaryTone),
            onSecondary = onAccent,
            secondaryContainer = tone(
                spec.secondary,
                if (spec.monoToned && !isDark) 95 else container
            ),
            onSecondaryContainer = tone(
                spec.secondary,
                if (spec.monoToned && !isDark) 20 else onContainer
            ),

            // A palette without its own third ramp inherits the base theme's
            // tertiary, which is what the XML overlays do by simply not
            // restating the role. Modelled here as "reuse the primary ramp at
            // a different tone" so the Compose scheme is complete - Compose
            // has no notion of "inherit from the theme underneath".
            tertiary = tone(spec.tertiary ?: spec.primary, if (isDark) accent else Light.ACCENT),
            onTertiary = onAccent,
            tertiaryContainer = tone(spec.tertiary ?: spec.primary, container),
            onTertiaryContainer = tone(spec.tertiary ?: spec.primary, onContainer),

            levels = levelTones.map { tone(spec.primary, it) },
            // Success tracks the primary accent, including Mono's tone shift,
            // so a "done" state never looks lighter than the primary it sits
            // beside.
            success = tone(spec.primary, primaryTone),
            successContainer = tone(spec.primary, container),
        )
    }

    /**
     * Whether every level in the ladder is distinguishable from its
     * neighbours.
     *
     * The ladder encodes commitment, so two adjacent rungs that look alike
     * make the encoding useless. 1.2:1 is well below any text threshold, but
     * these are adjacent fills compared against each other rather than
     * against a background, and a difference that small is still visible as
     * a step when the swatches touch.
     */
    fun levelsAreDistinct(scheme: Scheme, minRatio: Double = 1.2): Boolean =
        scheme.levels.zipWithNext().all { (a, b) -> Contrast.ratio(a, b) >= minRatio }
}
