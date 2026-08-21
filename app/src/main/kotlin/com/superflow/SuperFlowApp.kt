package com.superflow

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatDelegate
import com.superflow.data.Prefs
import com.superflow.notify.Reminders
import com.superflow.security.AppLock
import com.superflow.security.LockActivity
import com.superflow.util.Dates
import com.superflow.work.BackgroundWork

/**
 * Application entry point.
 *
 * Applies the saved theme mode before any activity inflates, makes sure
 * notification channels exist, schedules background work, and enforces the
 * optional app lock via an ActivityLifecycleCallbacks hook.
 */
class SuperFlowApp : Application() {

    private var foreground = 0

    override fun onCreate() {
        super.onCreate()
        val prefs = Prefs.get(this)
        applyTheme(prefs.themeMode)
        Reminders.ensureChannels(this)
        BackgroundWork.schedule(this)
        scheduleDarkMode()

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                foreground++
                if (foreground == 1 && activity !is LockActivity &&
                    AppLock.shouldLock(Prefs.get(this@SuperFlowApp))) {
                    activity.startActivity(Intent(activity, LockActivity::class.java))
                }
            }

            override fun onActivityStopped(activity: Activity) {
                foreground--
                if (foreground == 0) AppLock.onBackgrounded(Prefs.get(this@SuperFlowApp))
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    /**
     * For a "system"/light/dark mode the setting applies immediately. When the
     * user chose a time-based schedule (#77), this switches the live night mode
     * based on the current wall-clock time and re-checks around the next
     * transition.
     */
    private fun scheduleDarkMode() {
        val prefs = Prefs.get(this)
        if (prefs.themeMode != Prefs.THEME_SYSTEM) {
            applyTheme(prefs.themeMode)
            return
        }
        val (from, to) = when (prefs.darkSchedule) {
            "sunset" -> 21 * 60 to 7 * 60
            "custom" -> Dates.minutesOfDay(prefs.darkFrom) to Dates.minutesOfDay(prefs.darkTo)
            else -> { applyTheme(prefs.themeMode); return }
        }
        val now = java.time.LocalTime.now()
        val nowMin = now.hour * 60 + now.minute
        val dark = if (from <= to) nowMin in from until to else (nowMin >= from || nowMin < to)
        AppCompatDelegate.setDefaultNightMode(
            if (dark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
        // Re-evaluate shortly after the next boundary.
        val nextTransition = if (dark) to else from
        val delayMin = ((nextTransition - nowMin + 1440) % 1440).coerceAtLeast(1)
        Handler(Looper.getMainLooper()).postDelayed({ scheduleDarkMode() }, delayMin * 60_000L)
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
