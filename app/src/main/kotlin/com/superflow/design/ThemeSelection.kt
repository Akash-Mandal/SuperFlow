package com.superflow.design

/**
 * Resolves user appearance preferences into the set of theme overlays an
 * Activity must apply, and in what order.
 *
 * The decision is pure so it can be tested; applying the result is a one-line
 * call in `ui/`. Overlay identifiers are returned as symbolic names rather
 * than resource ids because R is generated at build time and is not available
 * to this package -- `ui/SfTheme` maps them to ids.
 *
 * Order matters and is the reason this exists as an ordered list rather than
 * a set. Android applies overlays in the order given, with later entries
 * winning on conflict:
 *
 *   1. palette      -- sets the colour roles
 *   2. dark flavour -- overrides only the surface colours, so it must come
 *                      after the palette or the palette would restore them
 *   3. density      -- touches only spacing attrs, so it cannot conflict, but
 *                      is applied last for predictability
 *   4. contrast     -- last, so it can strengthen whatever the others chose
 */
object ThemeSelection {

    /** Symbolic overlay names. `ui/` maps these to R.style ids. */
    const val PALETTE_FOREST = "palette_forest"
    const val PALETTE_OCEAN = "palette_ocean"
    const val PALETTE_DUSK = "palette_dusk"
    const val PALETTE_MONO = "palette_mono"
    const val PALETTE_TERRACOTTA = "palette_terracotta"
    const val PALETTE_AURORA = "palette_aurora"
    const val DARK_OLED = "dark_oled"
    const val DARK_MIDNIGHT = "dark_midnight"
    const val DENSITY_COMPACT = "density_compact"
    const val DENSITY_SPACIOUS = "density_spacious"
    const val HIGH_CONTRAST = "high_contrast"

    // Mirrors of the Prefs constants. Duplicated rather than imported so this
    // package stays free of the data layer; ThemeSelectionTest pins them to
    // the Prefs values so the two cannot drift.
    const val PALETTE_CALM_ID = 0
    const val PALETTE_FOREST_ID = 1
    const val PALETTE_OCEAN_ID = 2
    const val PALETTE_DUSK_ID = 3
    const val PALETTE_MONO_ID = 4
    const val PALETTE_TERRACOTTA_ID = 5
    const val PALETTE_AURORA_ID = 6

    const val DARK_WARM_ID = 0
    const val DARK_OLED_ID = 1
    const val DARK_MIDNIGHT_ID = 2

    const val DENSITY_COMPACT_ID = 0
    const val DENSITY_COMFORTABLE_ID = 1
    const val DENSITY_SPACIOUS_ID = 2

    /**
     * The overlays to apply, in order.
     *
     * @param palette      one of the `PALETTE_*_ID` values
     * @param darkVariant  one of the `DARK_*_ID` values
     * @param density      one of the `DENSITY_*_ID` values
     * @param isDark       whether the app is currently rendering dark; the
     *                     dark flavour is ignored in light mode
     * @param highContrast user preference or the system setting
     *
     * Defaults (Calm, Warm dark, Comfortable) contribute no overlay: they are
     * already the base theme, and applying a redundant overlay costs an extra
     * theme resolution on every Activity launch.
     */
    fun overlaysFor(
        palette: Int,
        darkVariant: Int,
        density: Int,
        isDark: Boolean,
        highContrast: Boolean,
    ): List<String> {
        val out = ArrayList<String>(4)

        when (palette) {
            PALETTE_FOREST_ID -> out.add(PALETTE_FOREST)
            PALETTE_OCEAN_ID -> out.add(PALETTE_OCEAN)
            PALETTE_DUSK_ID -> out.add(PALETTE_DUSK)
            PALETTE_MONO_ID -> out.add(PALETTE_MONO)
            PALETTE_TERRACOTTA_ID -> out.add(PALETTE_TERRACOTTA)
            PALETTE_AURORA_ID -> out.add(PALETTE_AURORA)
            // Calm and any unrecognised value use the base theme.
        }

        if (isDark) {
            when (darkVariant) {
                DARK_OLED_ID -> out.add(DARK_OLED)
                DARK_MIDNIGHT_ID -> out.add(DARK_MIDNIGHT)
                // Warm is the base night theme.
            }
        }

        when (density) {
            DENSITY_COMPACT_ID -> out.add(DENSITY_COMPACT)
            DENSITY_SPACIOUS_ID -> out.add(DENSITY_SPACIOUS)
            // Comfortable is the base.
        }

        if (highContrast) out.add(HIGH_CONTRAST)

        return out
    }

    /**
     * Whether dynamic colour should be applied.
     *
     * Dynamic colour derives the scheme from the wallpaper, which would
     * override an explicitly chosen palette. So it applies only when the user
     * is on the default palette: choosing a palette is a deliberate act and
     * must win over the wallpaper.
     */
    fun useDynamicColor(dynamicColorPref: Boolean, palette: Int, supported: Boolean): Boolean =
        dynamicColorPref && supported && palette == PALETTE_CALM_ID
}
