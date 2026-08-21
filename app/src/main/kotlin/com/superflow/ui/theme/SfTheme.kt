package com.superflow.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.superflow.data.Prefs
import com.superflow.design.ThemeSelection
// Aliased: Compose has its own androidx.compose.ui.unit.Density, and an
// unqualified `Density` in a Compose file is asking for the wrong one to be
// imported by a future edit.
import com.superflow.design.Density as DesignDensity
import com.superflow.design.DensityMetrics as DesignDensityMetrics

/**
 * The app's Compose theme (§3.2).
 *
 * Wraps [MaterialTheme] and adds the four things Material has no slot for:
 * the extra colour roles, the extra type styles, the density metrics, and
 * the resolved motion specs. Each travels in its own composition local so a
 * component reads only what it needs.
 *
 * Parameters default to the user's stored preferences, so most callers write
 * `SfTheme { ... }`. They are parameters at all so that previews and tests
 * can pin a specific appearance without touching stored state.
 */

val LocalSfColors = staticCompositionLocalOf<SfColors> {
    error("LocalSfColors accessed outside SfTheme")
}

val LocalSfTypeStyles = staticCompositionLocalOf<SfTypeStyles> {
    error("LocalSfTypeStyles accessed outside SfTheme")
}

val LocalSfDensity = staticCompositionLocalOf<SfDensityMetrics> {
    error("LocalSfDensity accessed outside SfTheme")
}

val LocalSfMotion = staticCompositionLocalOf<SfMotionSpecs> {
    error("LocalSfMotion accessed outside SfTheme")
}

val LocalSfShapes = staticCompositionLocalOf<SfShapeTokens> {
    error("LocalSfShapes accessed outside SfTheme")
}

/**
 * Whether the app is drawing in high contrast.
 *
 * Not a colour in itself - it tells components to draw borders they would
 * otherwise omit, which is the part of high contrast that colour tokens
 * cannot express.
 */
val LocalSfHighContrast: ProvidableCompositionLocal<Boolean> = compositionLocalOf { false }

/** Density metrics in Compose units (§4.2). */
@Immutable
data class SfDensityMetrics(
    val level: SfDensityLevel,
    val cardPadding: Int,
    val listItemHeight: Int,
    val cardGap: Int,
    val sectionSpacing: Int,
    val lineSpacing: Float,
)

enum class SfDensityLevel(val id: Int, val label: String) {
    Compact(DesignDensity.COMPACT, "Compact"),
    Comfortable(DesignDensity.COMFORTABLE, "Comfortable"),
    Spacious(DesignDensity.SPACIOUS, "Spacious");

    companion object {
        fun fromId(id: Int): SfDensityLevel = entries.firstOrNull { it.id == id } ?: Comfortable
    }
}

private fun DesignDensityMetrics.toCompose(level: SfDensityLevel) = SfDensityMetrics(
    level = level,
    cardPadding = cardPadding,
    listItemHeight = listItemHeight,
    cardGap = cardGap,
    sectionSpacing = sectionSpacing,
    lineSpacing = lineSpacing,
)

/**
 * Accessors for the app's tokens, in the style of `MaterialTheme.colorScheme`.
 *
 * `SfTheme.colors.success` reads better at a call site than
 * `LocalSfColors.current.success`, and it keeps the composition locals an
 * implementation detail.
 */
object SfTheme {
    // Note there is also a `SfTheme` composable function below, and an
    // unrelated `com.superflow.ui.common.SfTheme` that applies XML theme
    // overlays to an Activity. The object and the function coexist by the
    // same convention MaterialTheme uses - Kotlin resolves them by context.
    // The ui.common one does not: a file that needs both must alias one.
    val colors: SfColors
        @Composable @ReadOnlyComposable get() = LocalSfColors.current

    val type: SfTypeStyles
        @Composable @ReadOnlyComposable get() = LocalSfTypeStyles.current

    val density: SfDensityMetrics
        @Composable @ReadOnlyComposable get() = LocalSfDensity.current

    val motion: SfMotionSpecs
        @Composable @ReadOnlyComposable get() = LocalSfMotion.current

    val shapes: SfShapeTokens
        @Composable @ReadOnlyComposable get() = LocalSfShapes.current

    val highContrast: Boolean
        @Composable @ReadOnlyComposable get() = LocalSfHighContrast.current
}

/**
 * @param palette         which of the five palettes to use
 * @param isDark          dark mode; defaults to the system setting
 * @param darkVariant     which dark flavour, ignored in light mode
 * @param useDynamicColor Material You; only honoured on Android 12+ and only
 *                        on the default palette, since a chosen palette is a
 *                        deliberate act that must outrank the wallpaper
 * @param typography      which type variant to use
 * @param serifAccents    serif for identity statements and journal entries;
 *                        off substitutes the sans face at the same metrics
 * @param monoFigures     tabular figures in stats and chart axes
 * @param density         Compact, Comfortable or Spacious
 * @param motion          None, Reduced, Standard or Expressive
 * @param highContrast    stronger borders and text, honouring the OS setting
 */
@Composable
fun SfTheme(
    palette: SfPalette = SfPalette.Calm,
    isDark: Boolean = isSystemInDarkTheme(),
    darkVariant: SfDarkVariant = SfDarkVariant.Warm,
    useDynamicColor: Boolean = true,
    typography: SfTypographyVariant = SfTypographyVariant.Default,
    serifAccents: Boolean = true,
    monoFigures: Boolean = true,
    density: SfDensityLevel = SfDensityLevel.Comfortable,
    motion: SfMotionLevel = SfMotionLevel.Standard,
    highContrast: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current

    // Dynamic colour is decided by the same rule the View layer uses, so the
    // two never disagree about whether the wallpaper wins.
    val dynamicSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val dynamic = ThemeSelection.useDynamicColor(useDynamicColor, palette.id, dynamicSupported)

    val resolved = remember(palette, isDark, darkVariant) {
        resolvePalette(palette, darkVariant, isDark)
    }

    val colorScheme = when {
        dynamic && isDark -> dynamicDarkColorScheme(context)
        dynamic -> dynamicLightColorScheme(context)
        else -> resolved.material
    }

    // The system's animation setting is an accessibility preference, not a
    // suggestion: if the user has switched animations off device-wide, the
    // app's own "Expressive" must not override it.
    val systemAnimationsOff = remember(context) {
        android.provider.Settings.Global.getFloat(
            context.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) == 0f
    }

    val motionSpecs = remember(motion, systemAnimationsOff) {
        SfMotionSpecs.forLevel(motion, systemAnimationsOff)
    }
    val densityMetrics = remember(density) {
        DesignDensity.metrics(density.id).toCompose(density)
    }
    val typeStyles = remember(typography, serifAccents, monoFigures) {
        sfTypeStyles(typography, serifAccents, monoFigures)
    }
    val materialType = remember(typography) { materialTypography(typography) }

    // Status and navigation bar icons must contrast with whatever is behind
    // them. Doing it here rather than per-Activity means a Compose screen
    // that changes theme gets it right without the host knowing.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !isDark
                isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    CompositionLocalProvider(
        LocalSfColors provides resolved.extras,
        LocalSfTypeStyles provides typeStyles,
        LocalSfDensity provides densityMetrics,
        LocalSfMotion provides motionSpecs,
        LocalSfShapes provides sfShapeTokens,
        LocalSfHighContrast provides highContrast,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = materialType,
            shapes = sfShapes,
            content = content,
        )
    }
}

/**
 * [SfTheme] driven by stored preferences.
 *
 * The overload every screen actually uses. Reading preferences here rather
 * than in each screen means a new Compose screen picks up the user's
 * appearance without having to remember to.
 *
 * Note this reads preferences once per composition rather than observing
 * them. Appearance changes recreate the Activity - see `Prefs.appearanceRevision`
 * - so an observer would fire only to be torn down a frame later.
 */
@Composable
fun SfThemeFromPrefs(
    prefs: Prefs = Prefs.get(LocalContext.current),
    content: @Composable () -> Unit,
) {
    val isDark = when (prefs.themeMode) {
        Prefs.THEME_LIGHT -> false
        Prefs.THEME_DARK -> true
        else -> isSystemInDarkTheme()
    }
    SfTheme(
        palette = SfPalette.fromId(prefs.palette),
        isDark = isDark,
        darkVariant = SfDarkVariant.fromId(prefs.darkVariant),
        useDynamicColor = prefs.dynamicColor,
        // No app-level text size: the system font scale already does this,
        // and duplicating it would let the two multiply into unreadable
        // extremes. The typography variant carries the serif/mono switches
        // instead, which the system has no equivalent for.
        typography = SfTypographyVariant.Default,
        serifAccents = prefs.serifAccents,
        monoFigures = prefs.monoFigures,
        density = SfDensityLevel.fromId(prefs.density),
        motion = SfMotionLevel.fromId(prefs.motionLevel),
        highContrast = prefs.highContrast,
        content = content,
    )
}
