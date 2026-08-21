package com.superflow.ui.settings

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import com.superflow.R
import com.superflow.data.Prefs
import com.superflow.ui.common.SfTheme

/**
 * Host for Settings.
 *
 * Settings stopped being a tab in 10.1 — it is reached from the Today
 * header — so it needs somewhere to live. An Activity rather than a
 * fragment on the main back stack, because the settings sub-screens
 * (Appearance, Data Management) form their own small stack, and pushing
 * that onto the shell's stack made "back" from Appearance ambiguous:
 * it could mean the settings list or the tab you came from.
 *
 * The theme is applied before super.onCreate for the same reason as
 * everywhere else: overlays are merged at inflation, not after it.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private var builtAtRevision = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        prefs = Prefs.get(this)
        SfTheme.apply(this, prefs)
        builtAtRevision = prefs.appearanceRevision
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        if (savedInstanceState == null) {
            val start: Fragment = when (intent.getStringExtra(EXTRA_TAB)) {
                TAB_APPEARANCE -> AppearanceFragment()
                TAB_DATA -> DataManagementFragment()
                else -> SettingsFragment()
            }
            supportFragmentManager.commit {
                setReorderingAllowed(true)
                replace(R.id.settings_host, start)
            }
            // Landing straight on a sub-screen from a deep link would
            // otherwise leave "back" closing the whole activity from a
            // screen that visually looks nested. Put the list underneath.
            if (start !is SettingsFragment) {
                supportFragmentManager.commit {
                    setReorderingAllowed(true)
                    replace(R.id.settings_host, SettingsFragment())
                    addToBackStack(null)
                    replace(R.id.settings_host, start)
                }
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (supportFragmentManager.backStackEntryCount > 0) {
                    supportFragmentManager.popBackStack()
                } else {
                    finish()
                }
            }
        })
    }

    /** Pushes a settings sub-screen. Used by [SettingsFragment]. */
    fun push(fragment: Fragment, tag: String) {
        supportFragmentManager.commit {
            setReorderingAllowed(true)
            replace(R.id.settings_host, fragment, tag)
            addToBackStack(tag)
        }
    }

    override fun onResume() {
        super.onResume()
        // Changing the palette from inside Appearance has to take effect
        // here too, and an overlay can only be swapped by rebuilding.
        if (SfTheme.needsRecreate(prefs, builtAtRevision)) recreate()
    }

    companion object {
        const val EXTRA_TAB = "settings_tab"
        const val TAB_APPEARANCE = "appearance"
        const val TAB_DATA = "data"
    }
}
