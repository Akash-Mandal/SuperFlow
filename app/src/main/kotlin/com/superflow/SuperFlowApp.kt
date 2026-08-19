package com.superflow

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.superflow.data.Prefs
import com.superflow.notify.Reminders
import com.superflow.work.BackgroundWork

/**
 * Application entry point.
 *
 * Applies the saved theme mode before any activity inflates (required for a
 * flicker-free dark mode), then defers every optional side effect —
 * notification channels, WorkManager enqueueing — off the first-frame path.
 */
class SuperFlowApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val prefs = Prefs.get(this)
        applyTheme(prefs.themeMode)

        // Channel creation is a system-service call and WorkManager's first
        // getInstance() opens its internal database; neither belongs on the
        // launch path. They are idempotent and cheap to run late.
        AppBackground.launch {
            Reminders.ensureChannels(this@SuperFlowApp)
            BackgroundWork.schedule(this@SuperFlowApp)
        }
    }

    companion object {
        fun applyTheme(mode: Int) {
            AppCompatDelegate.setDefaultNightMode(
                when (mode) {
                    Prefs.THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                    Prefs.THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
            )
        }
    }
}
