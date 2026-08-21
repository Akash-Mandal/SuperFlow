package com.superflow.ui.settings

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.superflow.data.Prefs
import com.superflow.design.IconVariants

/**
 * Applies the chosen launcher icon (plan 19.3).
 *
 * All of the thinking is in [IconVariants]; this is the `PackageManager`
 * call and the two Android facts that surround it.
 *
 * **`DONT_KILL_APP` is not optional.** Without it the platform kills the
 * process the moment the alias state changes, which from the user's side
 * looks like the Settings screen crashing as they tap an icon. With it, the
 * change is picked up by the launcher asynchronously - typically within a
 * second, occasionally not until the launcher is next resumed.
 *
 * **The launcher may ignore us for a while.** Some launchers cache the
 * component list and only rescan on a package-changed broadcast, which
 * enabling an alias does send, and some batch that rescan. Nothing here can
 * make it faster, so the UI says so rather than pretending.
 */
object AppIcons {

    /**
     * Enables the alias for [variantId] and disables the rest.
     *
     * Safe to call repeatedly and safe to call with the current value; the
     * calls are idempotent. Failures are swallowed: an icon that did not
     * change is a cosmetic disappointment, and there is no state to repair.
     *
     * @return true if every component setting was applied.
     */
    fun apply(context: Context, variantId: Int): Boolean {
        val pm = context.packageManager ?: return false
        val target = IconVariants.variantFor(variantId)
        var ok = true
        IconVariants.transition(target).forEach { (alias, enable) ->
            val component = ComponentName(context.packageName, context.packageName + alias)
            val state = if (enable) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            try {
                pm.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
            } catch (e: Exception) {
                ok = false
            }
        }
        return ok
    }

    /**
     * Re-asserts the stored preference at startup.
     *
     * An app update resets component states that were set at runtime back
     * to their manifest defaults, which would silently return every user to
     * the default icon on every release. Called from `SuperFlowApp`, and a
     * no-op in the overwhelmingly common case where nothing has changed.
     */
    fun reassert(context: Context) {
        val prefs = Prefs.get(context)
        if (prefs.appIcon == IconVariants.DEFAULT_ID) return
        apply(context, prefs.appIcon)
    }
}
