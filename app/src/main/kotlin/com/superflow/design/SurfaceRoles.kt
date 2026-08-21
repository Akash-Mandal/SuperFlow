package com.superflow.design

/**
 * Surface, outline and error colours, resolved for a mode and dark flavour.
 *
 * The companion to [ColorRoles]: that resolves the accents a palette
 * changes, this resolves everything a palette leaves alone. Together they
 * are the whole colour scheme.
 *
 * This lives in `design` rather than in the Compose theme for the usual
 * reason - it is a pure function of two enums, and `ui/` cannot be tested in
 * this project. [SurfaceRolesTest] pins every value against the XML base
 * theme, so a Compose screen and an XML screen sit on identical surfaces.
 */
object SurfaceRoles {

    /**
     * The non-accent half of a colour scheme.
     *
     * The `surfaceContainer*` ladder is Material 3's, and the XML themes
     * predate it, so those five values are derived rather than mirrored -
     * see [surfacesFor] for how, and why they are not invented greys.
     */
    data class Surfaces(
        val background: Int,
        val onBackground: Int,
        val surface: Int,
        val onSurface: Int,
        val surfaceVariant: Int,
        val onSurfaceVariant: Int,
        val surfaceContainerLowest: Int,
        val surfaceContainerLow: Int,
        val surfaceContainer: Int,
        val surfaceContainerHigh: Int,
        val surfaceContainerHighest: Int,
        val outline: Int,
        val outlineVariant: Int,
        val inverseSurface: Int,
        val inverseOnSurface: Int,
        val error: Int,
        val onError: Int,
        val errorContainer: Int,
        val onErrorContainer: Int,
        val scrim: Int,
    )

    /**
     * Inline literals from the XML themes.
     *
     * These four have no `@color` resource to read - the theme files write
     * the hex directly - so they are repeated here and pinned by the test
     * rather than silently diverging.
     */
    const val ON_ERROR_CONTAINER_LIGHT = 0xFF3B0F09.toInt()
    const val ON_ERROR_DARK = 0xFF5A1B10.toInt()
    const val SCRIM = 0xFF000000.toInt()

    private fun neutral(tone: Int) = Ramps.neutral.getValue(tone)

    /**
     * Surfaces for the given mode.
     *
     * @param darkVariant one of `ThemeSelection.DARK_*_ID`; ignored in light
     *                    mode, where there is only one set of surfaces.
     */
    fun surfacesFor(isDark: Boolean, darkVariant: Int = ThemeSelection.DARK_WARM_ID): Surfaces {
        if (!isDark) {
            return Surfaces(
                background = neutral(98),
                onBackground = neutral(10),
                surface = neutral(100),
                onSurface = neutral(10),
                surfaceVariant = neutral(94),
                onSurfaceVariant = neutral(40),
                // Material 3's container ladder has no XML counterpart here,
                // so it is derived from the neutral ramp between the surface
                // and the surface variant. Picking fresh greys instead would
                // put Compose screens on shades no XML screen ever uses.
                surfaceContainerLowest = neutral(100),
                surfaceContainerLow = neutral(98),
                surfaceContainer = neutral(96),
                surfaceContainerHigh = neutral(94),
                surfaceContainerHighest = neutral(90),
                outline = neutral(70),
                outlineVariant = neutral(90),
                inverseSurface = neutral(20),
                inverseOnSurface = neutral(96),
                error = Ramps.flat("sf_error_40", false),
                onError = neutral(100),
                errorContainer = Ramps.flat("sf_error_90", false),
                onErrorContainer = ON_ERROR_CONTAINER_LIGHT,
                scrim = SCRIM,
            )
        }

        val key = when (darkVariant) {
            ThemeSelection.DARK_OLED_ID -> "oled"
            ThemeSelection.DARK_MIDNIGHT_ID -> "midnight"
            else -> "warm"
        }
        val background = Ramps.flat("sf_dark_${key}_background", true)
        val surface = Ramps.flat("sf_dark_${key}_surface", true)
        val surfaceVariant = Ramps.flat("sf_dark_${key}_surface_variant", true)
        val outlineVariant = Ramps.flat("sf_dark_${key}_outline_variant", true)

        return Surfaces(
            background = background,
            onBackground = neutral(90),
            surface = surface,
            onSurface = neutral(90),
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = neutral(80),
            surfaceContainerLowest = background,
            surfaceContainerLow = surface,
            surfaceContainer = surface,
            surfaceContainerHigh = surfaceVariant,
            surfaceContainerHighest = surfaceVariant,
            outline = neutral(50),
            outlineVariant = outlineVariant,
            inverseSurface = neutral(90),
            inverseOnSurface = neutral(20),
            // Dark mode's error is the caution tone, not a red: the app's
            // error states are still "something needs attention", and the
            // night palette has no true red in it.
            error = Ramps.flat("sf_caution", true),
            onError = ON_ERROR_DARK,
            errorContainer = Ramps.flat("sf_caution_container", true),
            onErrorContainer = Ramps.flat("sf_error_90", true),
            scrim = SCRIM,
        )
    }

    /**
     * Whether body text is legible on each surface.
     *
     * Checks the pairs that actually carry text. The container ladder is
     * excluded: those are backgrounds for cards that supply their own `on`
     * colour, so pairing them with onSurface here would test a combination
     * the app never renders.
     */
    fun textPairsPass(s: Surfaces, minRatio: Double = 4.5): Boolean =
        Contrast.ratio(s.onBackground, s.background) >= minRatio &&
            Contrast.ratio(s.onSurface, s.surface) >= minRatio &&
            Contrast.ratio(s.onSurfaceVariant, s.surfaceVariant) >= minRatio &&
            Contrast.ratio(s.inverseOnSurface, s.inverseSurface) >= minRatio &&
            Contrast.ratio(s.onError, s.error) >= minRatio &&
            Contrast.ratio(s.onErrorContainer, s.errorContainer) >= minRatio
}
