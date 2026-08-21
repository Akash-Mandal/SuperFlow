package com.superflow.ui.common

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.provider.Settings
import com.google.android.material.color.DynamicColors
import com.superflow.R
import com.superflow.data.Prefs
import com.superflow.design.ThemeSelection

/**
 * Applies the user's appearance preferences to an Activity.
 *
 * The decision of *which* overlays to apply lives in
 * [com.superflow.design.ThemeSelection], which is pure and unit-tested. This
 * file is only the Android plumbing: map symbolic names to R.style ids and
 * call setTheme.
 *
 * Usage, before super.onCreate():
 *
 *     class SomeActivity : AppCompatActivity() {
 *         override fun onCreate(state: Bundle?) {
 *             SfTheme.apply(this)
 *             super.onCreate(state)
 *             ...
 *         }
 *     }
 *
 * Timing matters. A theme must be set before the Activity inflates its first
 * view, because attribute resolution happens at inflation and is not redone.
 * Changing a preference therefore requires an Activity recreate, which is
 * what [Prefs.appearanceRevision] exists to detect.
 */
object SfTheme {

    private fun styleFor(overlay: String): Int = when (overlay) {
        ThemeSelection.PALETTE_FOREST -> R.style.ThemeOverlay_SuperFlow_Palette_Forest
        ThemeSelection.PALETTE_OCEAN -> R.style.ThemeOverlay_SuperFlow_Palette_Ocean
        ThemeSelection.PALETTE_DUSK -> R.style.ThemeOverlay_SuperFlow_Palette_Dusk
        ThemeSelection.PALETTE_MONO -> R.style.ThemeOverlay_SuperFlow_Palette_Mono
        ThemeSelection.DARK_OLED -> R.style.ThemeOverlay_SuperFlow_Dark_Oled
        ThemeSelection.DARK_MIDNIGHT -> R.style.ThemeOverlay_SuperFlow_Dark_Midnight
        ThemeSelection.DENSITY_COMPACT -> R.style.ThemeOverlay_SuperFlow_Density_Compact
        ThemeSelection.DENSITY_SPACIOUS -> R.style.ThemeOverlay_SuperFlow_Density_Spacious
        ThemeSelection.HIGH_CONTRAST -> R.style.ThemeOverlay_SuperFlow_HighContrast
        else -> 0
    }

    /** True when the Activity is currently rendering in dark mode. */
    fun isDark(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    /**
     * Applies palette, dark flavour, density and contrast overlays.
     *
     * Call before super.onCreate().
     */
    fun apply(activity: Activity, prefs: Prefs = Prefs.get(activity)) {
        val dark = isDark(activity)

        // The system's own accessibility setting counts. A user who has turned
        // on high contrast at the OS level should not have to find our
        // duplicate of the same switch.
        val contrast = prefs.highContrast || systemHighContrast(activity)

        val overlays = ThemeSelection.overlaysFor(
            palette = prefs.palette,
            darkVariant = prefs.darkVariant,
            density = prefs.density,
            isDark = dark,
            highContrast = contrast,
        )

        // Dynamic colour first: it replaces the whole colour scheme, so an
        // explicit overlay applied afterwards must win over it.
        if (ThemeSelection.useDynamicColor(
                prefs.dynamicColor, prefs.palette, DynamicColors.isDynamicColorAvailable()
            )
        ) {
            DynamicColors.applyToActivityIfAvailable(activity)
        }

        for (overlay in overlays) {
            val style = styleFor(overlay)
            // `false` means "merge into the existing theme", not "replace it",
            // which is what makes overlay stacking work at all.
            if (style != 0) activity.theme.applyStyle(style, true)
        }
    }

    /**
     * Whether the OS high-contrast text setting is on.
     *
     * This is a hidden setting with no public constant, so it is read by name
     * and defended: on a device where it does not exist the query simply
     * returns the default. Worth doing anyway, because users who need it have
     * usually already set it system-wide.
     */
    private fun systemHighContrast(context: Context): Boolean = try {
        Settings.Secure.getInt(context.contentResolver, "high_text_contrast_enabled", 0) == 1
    } catch (_: Exception) {
        false
    }

    /**
     * Whether the user has animations switched off system-wide.
     *
     * Honoured independently of the in-app motion preference: the OS setting
     * is an accessibility request (it exists partly for vestibular disorders)
     * and overrides an app-level preference for more motion.
     */
    fun systemAnimationsDisabled(context: Context): Boolean = try {
        Settings.Global.getFloat(
            context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f
        ) == 0f
    } catch (_: Exception) {
        false
    }

    /**
     * Effective motion scale: the in-app preference, forced to zero when the
     * system has animations off.
     */
    fun motionScale(context: Context, prefs: Prefs = Prefs.get(context)): Float =
        if (systemAnimationsDisabled(context)) 0f else prefs.motionScale

    /** Whether motion should be skipped entirely. */
    fun motionDisabled(context: Context, prefs: Prefs = Prefs.get(context)): Boolean =
        prefs.motionDisabled || systemAnimationsDisabled(context)

    /**
     * True if the appearance preferences have changed since [revision], i.e.
     * the Activity must be recreated for them to take effect.
     */
    fun needsRecreate(prefs: Prefs, revision: Int): Boolean =
        prefs.appearanceRevision != revision

    @Suppress("unused")
    fun dynamicColorSupported(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && DynamicColors.isDynamicColorAvailable()
}
