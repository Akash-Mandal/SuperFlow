package com.superflow

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.superflow.data.Prefs
import com.superflow.notify.Reminders

/**
 * Application entry point.
 *
 * Applies the saved theme mode before any activity inflates, and makes sure
 * notification channels exist.
 */
class SuperFlowApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val prefs = Prefs.get(this)
        applyTheme(prefs.themeMode)
        Reminders.ensureChannels(this)
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
