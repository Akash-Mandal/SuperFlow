package com.superflow.design

/**
 * Launcher icon variants (plan 19.3).
 *
 * Switching an app icon on Android means enabling one `activity-alias` and
 * disabling every other, and getting that wrong has consequences a user
 * notices immediately: two icons in the drawer, or none. The rule is simple
 * enough to state as a function, so it is stated here and tested, and
 * `ui.settings.AppIcons` is left holding nothing but the `PackageManager`
 * call.
 *
 * The aliases are named rather than derived, because the strings are in the
 * manifest and a typo would only show up on a device.
 */
object IconVariants {

    /** Persisted ordinals. Must match `Prefs.ICON_*`. */
    const val DEFAULT_ID = 0
    const val MINIMAL_ID = 1
    const val MONO_ID = 2

    /**
     * @param alias  class name relative to the application id, as written in
     *               the manifest's `android:name`.
     * @param enabledByDefault  the one alias the manifest ships enabled.
     */
    enum class Variant(
        val id: Int,
        val key: String,
        val label: String,
        val alias: String,
        val summary: String,
        val enabledByDefault: Boolean = false,
    ) {
        DEFAULT(
            DEFAULT_ID, "default", "Default", ".Launcher",
            "The rising line on a deep green gradient.",
            enabledByDefault = true,
        ),
        MINIMAL(
            MINIMAL_ID, "minimal", "Minimal", ".LauncherMinimal",
            "One stroke, one point, flat ink.",
        ),
        MONO(
            MONO_ID, "mono", "Paper", ".LauncherMono",
            "Dark mark on a light field.",
        ),
    }

    val all: List<Variant> = Variant.entries.toList()

    /** Unknown or out-of-range ordinals resolve to the default, never crash. */
    fun variantFor(id: Int): Variant = all.firstOrNull { it.id == id } ?: Variant.DEFAULT

    /**
     * The component state changes needed to move to [target].
     *
     * Returns one entry per variant: `true` to enable, `false` to disable.
     * Every alias is listed even when it is already in the right state,
     * because the caller cannot cheaply read the current state and
     * `setComponentEnabledSetting` is idempotent.
     *
     * The enabled alias is deliberately first. Disabling the last enabled
     * launcher alias before enabling the new one leaves a window - short,
     * but real, and observed on some launchers - in which the app has no
     * launcher entry and is dropped from the home screen for good.
     */
    fun transition(target: Variant): List<Pair<String, Boolean>> =
        listOf(target.alias to true) +
            all.filter { it != target }.map { it.alias to false }

    /** Whether anything needs to change at all. */
    fun needsChange(current: Variant, target: Variant): Boolean = current != target

    /**
     * Warning shown before applying.
     *
     * Not a nicety. Toggling a launcher alias removes and re-adds the app in
     * the launcher, which loses its home-screen placement, and a user who
     * was not told will read that as the app having broken something.
     */
    const val PLACEMENT_WARNING: String =
        "Changing the icon removes the app from your home screen and " +
            "re-adds it to the app drawer. You may need to place it again."
}
