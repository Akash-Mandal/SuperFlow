package com.superflow.ui.studio

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.isSystemInDarkTheme
import com.superflow.data.Prefs
import com.superflow.design.ThemeSelection
import com.superflow.ui.theme.SfDarkVariant
import com.superflow.ui.theme.SfDensityLevel
import com.superflow.ui.theme.SfMotionLevel
import com.superflow.ui.theme.SfPalette
import com.superflow.ui.theme.SfTheme
import com.superflow.ui.theme.SfTypographyVariant

/**
 * Studio always-on dark ("workshop at night", ALPHA3_VISUAL_PLAN §11.4).
 *
 * Inverts the user's light/dark choice only for the Studio tab so the
 * palette's accent reads as ink on tinted black, regardless of the
 * rest-of-app setting. Aurora gradient header and conversation bubble
 * treatments live in StudioScreen and read this forced dark via
 * SfTheme.
 */
@Composable
fun StudioNightTheme(content: @Composable () -> Unit) {
    val prefs = Prefs.get(LocalContext.current)
    SfTheme(
        palette = SfPalette.fromId(prefs.palette),
        isDark = true,
        darkVariant = SfDarkVariant.fromId(prefs.darkVariant),
        useDynamicColor = prefs.dynamicColor,
        typography = SfTypographyVariant.Default,
        serifAccents = prefs.serifAccents,
        monoFigures = prefs.monoFigures,
        density = SfDensityLevel.fromId(prefs.density),
        motion = SfMotionLevel.fromId(prefs.motionLevel),
        highContrast = prefs.highContrast,
        content = content,
    )
}
