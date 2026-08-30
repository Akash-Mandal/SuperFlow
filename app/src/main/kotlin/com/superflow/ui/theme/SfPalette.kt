package com.superflow.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.superflow.design.ColorRoles
import com.superflow.design.Ramps
import com.superflow.design.SurfaceRoles
import com.superflow.design.ThemeSelection

/**
 * The five palettes, as Compose colour schemes.
 *
 * Accents come from [ColorRoles] and every other colour from [Ramps], both
 * of which are generated from or tested against the XML theme resources, so
 * the Compose and View layers cannot drift apart. If a colour looks wrong,
 * the fix belongs in the XML - changing it here would fix one rendering path
 * and leave the other broken.
 *
 * Surfaces come from [SurfaceRoles], accents from [ColorRoles]; both are
 * pinned against the XML by RoleTest. There are no colour literals here.
 *
 * @see com.superflow.design.ColorRoles
 */
enum class SfPalette(val id: Int, val label: String) {
    Calm(ThemeSelection.PALETTE_CALM_ID, "Calm"),
    Forest(ThemeSelection.PALETTE_FOREST_ID, "Forest"),
    Ocean(ThemeSelection.PALETTE_OCEAN_ID, "Ocean"),
    Dusk(ThemeSelection.PALETTE_DUSK_ID, "Dusk"),
    Mono(ThemeSelection.PALETTE_MONO_ID, "Mono"),
    Terracotta(ThemeSelection.PALETTE_TERRACOTTA_ID, "Terracotta"),
    Aurora(ThemeSelection.PALETTE_AURORA_ID, "Aurora");

    companion object {
        /** Maps a stored preference value to a palette, defaulting to Calm. */
        fun fromId(id: Int): SfPalette = entries.firstOrNull { it.id == id } ?: Calm
    }
}

/** The three dark flavours (§6.3). Warm is the default. */
enum class SfDarkVariant(val id: Int, val label: String) {
    Warm(ThemeSelection.DARK_WARM_ID, "Warm"),
    Oled(ThemeSelection.DARK_OLED_ID, "OLED"),
    Midnight(ThemeSelection.DARK_MIDNIGHT_ID, "Midnight");

    companion object {
        fun fromId(id: Int): SfDarkVariant = entries.firstOrNull { it.id == id } ?: Warm
    }
}

private fun argb(value: Int) = Color(value)

private fun ramp(name: String, tone: Int): Color =
    argb(Ramps.all.getValue(name).getValue(tone))

/** A flat colour resource, resolved for the current mode. */
private fun flat(name: String, isDark: Boolean): Color = argb(Ramps.flat(name, isDark))

/**
 * Extra colour roles Material 3 has no slot for.
 *
 * Material's scheme covers primary/secondary/tertiary and surfaces, but the
 * app also needs a habit ladder, semantic states that are not "error", and
 * per-entity accents. Those travel in this class rather than being jammed
 * into unrelated Material roles - using [ColorScheme.error] for "missed"
 * would be convenient and would also mean a missed habit inherits every
 * error affordance in the component library.
 */
@Immutable
data class SfColors(
    /** Habit ladder, ascending: tiny, minimum, standard, stretch. */
    val levels: List<Color>,
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    /** Never red: a missed day is information, not an error (§6.4). */
    val caution: Color,
    val cautionContainer: Color,
    val info: Color,
    val infoContainer: Color,
    val recovery: Color,
    val recoveryContainer: Color,
    val celebration: Color,
    val celebrationContainer: Color,
    /** Energy 1..5, cool to warm. */
    val energy: List<Color>,
    /** Entity accents (§6.5): identity, goal, system, habit. */
    val accentIdentity: Color,
    val accentGoal: Color,
    val accentSystem: Color,
    val accentHabit: Color,
    val stateSkipped: Color,
    val stateMissed: Color,
    val stateEmpty: Color,
    val skeletonBase: Color,
    val skeletonHighlight: Color,
) {
    fun levelFor(index: Int): Color = levels[index.coerceIn(0, levels.lastIndex)]

    /** Energy is recorded 1..5; anything outside clamps to the nearest end. */
    fun energyFor(value: Int): Color = energy[(value - 1).coerceIn(0, energy.lastIndex)]
}

/**
 * The semantic roles.
 *
 * Most are palette-independent: "caution" means the same thing whichever
 * palette is active, and giving it five variants would dilute it. Success
 * and the habit ladder are the exceptions - they track the palette, because
 * a completed habit should read as this app's idea of done rather than a
 * generic green pasted onto a violet theme.
 */
private fun semanticColors(scheme: ColorRoles.Scheme, isDark: Boolean) = SfColors(
    levels = scheme.levels.map(::argb),
    success = argb(scheme.success),
    onSuccess = flat("sf_on_success", isDark),
    successContainer = argb(scheme.successContainer),
    warning = flat("sf_warning", isDark),
    onWarning = flat("sf_on_warning", isDark),
    warningContainer = flat("sf_warning_container", isDark),
    caution = flat("sf_caution", isDark),
    cautionContainer = flat("sf_caution_container", isDark),
    info = flat("sf_info", isDark),
    infoContainer = flat("sf_info_container", isDark),
    recovery = flat("sf_recovery", isDark),
    recoveryContainer = flat("sf_recovery_container", isDark),
    celebration = flat("sf_celebration", isDark),
    celebrationContainer = flat("sf_celebration_container", isDark),
    energy = (1..5).map { flat("sf_energy_$it", isDark) },
    // Entity accents follow the palette's three accent roles (§6.5); habit
    // is deliberately neutral so the ladder colours carry its meaning.
    accentIdentity = argb(scheme.primary),
    accentGoal = argb(scheme.secondary),
    accentSystem = argb(scheme.tertiary),
    accentHabit = if (isDark) ramp("sf_neutral", 60) else ramp("sf_neutral", 50),
    stateSkipped = flat("state_skipped", isDark),
    stateMissed = flat("state_missed", isDark),
    stateEmpty = flat("state_empty", isDark),
    skeletonBase = flat("sf_skeleton_base", isDark),
    skeletonHighlight = flat("sf_skeleton_highlight", isDark),
)

/** The Material scheme and the extra roles, resolved together. */
internal data class ResolvedPalette(
    val material: ColorScheme,
    val extras: SfColors,
)

/**
 * Builds both colour sets for a palette.
 *
 * Surfaces come from the base theme (or the chosen dark flavour) and accents
 * from [ColorRoles], mirroring how the XML overlays compose: the palette
 * overlay restates only the accent roles, and the dark flavour overlay
 * restates only the surfaces.
 */
internal fun resolvePalette(
    palette: SfPalette,
    darkVariant: SfDarkVariant,
    isDark: Boolean,
): ResolvedPalette {
    val roles = ColorRoles.schemeFor(palette.id, isDark)
    val extras = semanticColors(roles, isDark)

    // Surfaces, outlines and error roles come from SurfaceRoles, which is
    // pinned against the XML base theme by RoleTest. Only the accents differ
    // per palette, exactly as the XML overlays are structured.
    val surf = SurfaceRoles.surfacesFor(isDark, darkVariant.id)

    val material = if (isDark) {
        darkColorScheme(
            primary = argb(roles.primary),
            onPrimary = argb(roles.onPrimary),
            primaryContainer = argb(roles.primaryContainer),
            onPrimaryContainer = argb(roles.onPrimaryContainer),
            inversePrimary = argb(roles.primaryInverse),
            secondary = argb(roles.secondary),
            onSecondary = argb(roles.onSecondary),
            secondaryContainer = argb(roles.secondaryContainer),
            onSecondaryContainer = argb(roles.onSecondaryContainer),
            tertiary = argb(roles.tertiary),
            onTertiary = argb(roles.onTertiary),
            tertiaryContainer = argb(roles.tertiaryContainer),
            onTertiaryContainer = argb(roles.onTertiaryContainer),
            background = argb(surf.background),
            onBackground = argb(surf.onBackground),
            surface = argb(surf.surface),
            onSurface = argb(surf.onSurface),
            surfaceVariant = argb(surf.surfaceVariant),
            onSurfaceVariant = argb(surf.onSurfaceVariant),
            surfaceContainerLowest = argb(surf.surfaceContainerLowest),
            surfaceContainerLow = argb(surf.surfaceContainerLow),
            surfaceContainer = argb(surf.surfaceContainer),
            surfaceContainerHigh = argb(surf.surfaceContainerHigh),
            surfaceContainerHighest = argb(surf.surfaceContainerHighest),
            surfaceTint = argb(roles.primary),
            outline = argb(surf.outline),
            outlineVariant = argb(surf.outlineVariant),
            error = argb(surf.error),
            onError = argb(surf.onError),
            errorContainer = argb(surf.errorContainer),
            onErrorContainer = argb(surf.onErrorContainer),
            inverseSurface = argb(surf.inverseSurface),
            inverseOnSurface = argb(surf.inverseOnSurface),
            scrim = argb(surf.scrim),
        )
    } else {
        lightColorScheme(
            primary = argb(roles.primary),
            onPrimary = argb(roles.onPrimary),
            primaryContainer = argb(roles.primaryContainer),
            onPrimaryContainer = argb(roles.onPrimaryContainer),
            inversePrimary = argb(roles.primaryInverse),
            secondary = argb(roles.secondary),
            onSecondary = argb(roles.onSecondary),
            secondaryContainer = argb(roles.secondaryContainer),
            onSecondaryContainer = argb(roles.onSecondaryContainer),
            tertiary = argb(roles.tertiary),
            onTertiary = argb(roles.onTertiary),
            tertiaryContainer = argb(roles.tertiaryContainer),
            onTertiaryContainer = argb(roles.onTertiaryContainer),
            background = argb(surf.background),
            onBackground = argb(surf.onBackground),
            surface = argb(surf.surface),
            onSurface = argb(surf.onSurface),
            surfaceVariant = argb(surf.surfaceVariant),
            onSurfaceVariant = argb(surf.onSurfaceVariant),
            surfaceContainerLowest = argb(surf.surfaceContainerLowest),
            surfaceContainerLow = argb(surf.surfaceContainerLow),
            surfaceContainer = argb(surf.surfaceContainer),
            surfaceContainerHigh = argb(surf.surfaceContainerHigh),
            surfaceContainerHighest = argb(surf.surfaceContainerHighest),
            surfaceTint = argb(roles.primary),
            outline = argb(surf.outline),
            outlineVariant = argb(surf.outlineVariant),
            error = argb(surf.error),
            onError = argb(surf.onError),
            errorContainer = argb(surf.errorContainer),
            onErrorContainer = argb(surf.onErrorContainer),
            inverseSurface = argb(surf.inverseSurface),
            inverseOnSurface = argb(surf.inverseOnSurface),
            scrim = argb(surf.scrim),
        )
    }

    return ResolvedPalette(material, extras)
}
