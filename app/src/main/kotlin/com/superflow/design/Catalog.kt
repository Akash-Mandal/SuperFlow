package com.superflow.design

/**
 * The user-facing catalogue of appearance and experience choices.
 *
 * Kept here rather than in the settings screen so the option list, the order
 * they appear in, and the copy that describes them are all testable, and so a
 * second surface (onboarding, a widget config screen) presents exactly the
 * same choices without duplicating the list.
 *
 * Colour and icon resources are referenced by symbolic key; `ui/` resolves
 * them, since R is not available to this package.
 */

/** One selectable option in a settings group. */
data class Choice(
    val id: Int,
    val key: String,
    val label: String,
    /** One line explaining what it does, in plain language. */
    val detail: String,
)

object Catalog {

    /**
     * Colour palettes, in the order shown.
     *
     * Descriptions say what the palette feels like rather than naming the
     * hues, because the swatch already shows the colour; the text is there
     * for people who cannot easily distinguish them.
     */
    val palettes: List<Choice> = listOf(
        Choice(
            ThemeSelection.PALETTE_CALM_ID, "calm", "Calm",
            "Muted green and warm clay. The default.",
        ),
        Choice(
            ThemeSelection.PALETTE_FOREST_ID, "forest", "Forest",
            "Deeper and more saturated, with an olive accent.",
        ),
        Choice(
            ThemeSelection.PALETTE_OCEAN_ID, "ocean", "Ocean",
            "Cool teal with a coral accent. The brightest option.",
        ),
        Choice(
            ThemeSelection.PALETTE_DUSK_ID, "dusk", "Dusk",
            "Violet and dusty rose. Softest, lowest contrast.",
        ),
        Choice(
            ThemeSelection.PALETTE_MONO_ID, "mono", "Mono",
            "Near-greyscale. Colour is reserved for meaning only.",
        ),
    )

    val darkVariants: List<Choice> = listOf(
        Choice(
            ThemeSelection.DARK_WARM_ID, "warm", "Warm",
            "Soft near-black with a warm cast. Easiest at night.",
        ),
        Choice(
            ThemeSelection.DARK_OLED_ID, "oled", "Black",
            "True black. Saves power on OLED screens.",
        ),
        Choice(
            ThemeSelection.DARK_MIDNIGHT_ID, "midnight", "Midnight",
            "Cool blue-black.",
        ),
    )

    val densities: List<Choice> = listOf(
        Choice(
            ThemeSelection.DENSITY_COMPACT_ID, "compact", "Compact",
            "More on screen at once.",
        ),
        Choice(
            ThemeSelection.DENSITY_COMFORTABLE_ID, "comfortable", "Comfortable",
            "The default balance.",
        ),
        Choice(
            ThemeSelection.DENSITY_SPACIOUS_ID, "spacious", "Spacious",
            "Larger touch targets and more breathing room.",
        ),
    )

    /**
     * Motion levels. "None" is worded as a full stop rather than "less
     * motion", because someone turning it off for vestibular reasons needs to
     * know it is actually off, not merely reduced.
     */
    val motionLevels: List<Choice> = listOf(
        Choice(0, "none", "None", "No animation anywhere. Screens change instantly."),
        Choice(1, "reduced", "Reduced", "Half speed, and no decorative movement."),
        Choice(2, "standard", "Standard", "The default."),
        Choice(3, "expressive", "Expressive", "Slower, fuller animations."),
    )

    val hapticLevels: List<Choice> = listOf(
        Choice(0, "off", "Off", "No vibration."),
        Choice(1, "light", "Light", "Barely there."),
        Choice(2, "medium", "Medium", "The default."),
        Choice(3, "strong", "Strong", "Firmer, easier to feel through a pocket."),
    )

    val startDestinations: List<Choice> = listOf(
        Choice(0, "today", "Today", "What needs doing now."),
        Choice(1, "journey", "Journey", "Your identities, goals and systems."),
        Choice(2, "insights", "Insights", "Trends and patterns."),
        Choice(3, "studio", "Studio", "Coaching, blueprints and the AI engine."),
    )

    /**
     * Launcher icon variants (19.3).
     *
     * Derived from [IconVariants] rather than restated, so the picker and
     * the alias-switching logic cannot drift apart.
     */
    val appIcons: List<Choice> = IconVariants.all.map {
        Choice(it.id, it.key, it.label, it.summary)
    }

    /** Looks up a choice by id, falling back to the first entry. */
    fun choiceOf(list: List<Choice>, id: Int): Choice =
        list.firstOrNull { it.id == id } ?: list.first()

    /** The label to show for a stored value, safe against unknown ids. */
    fun labelOf(list: List<Choice>, id: Int): String = choiceOf(list, id).label
}
