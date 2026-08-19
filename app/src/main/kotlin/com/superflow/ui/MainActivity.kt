package com.superflow.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.superflow.R
import com.superflow.data.Prefs
import com.superflow.notify.Reminders
import com.superflow.ui.coach.CoachFragment
import com.superflow.ui.insights.InsightsFragment
import com.superflow.ui.journey.JourneyFragment
import com.superflow.ui.onboarding.OnboardingActivity
import com.superflow.ui.settings.SettingsFragment
import com.superflow.ui.today.TodayFragment
import com.superflow.widget.TodayWidget

/**
 * Application shell.
 *
 * Five primary destinations in a ViewPager2, driven by a Material 3 bottom
 * navigation bar, drawn edge to edge.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var pager: ViewPager2
    private lateinit var nav: BottomNavigationView
    private lateinit var prefs: Prefs

    private val navIds = listOf(
        R.id.nav_today, R.id.nav_journey, R.id.nav_insights, R.id.nav_coach, R.id.nav_settings
    )

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        prefs = Prefs.get(this)

        if (!prefs.onboarded) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)
        pager = findViewById(R.id.pager)
        nav = findViewById(R.id.bottom_nav)

        pager.isUserInputEnabled = false
        pager.offscreenPageLimit = 2
        pager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = 5
            override fun createFragment(position: Int): Fragment = when (position) {
                0 -> TodayFragment()
                1 -> JourneyFragment()
                2 -> InsightsFragment()
                3 -> CoachFragment()
                else -> SettingsFragment()
            }
        }

        nav.setOnItemSelectedListener { item ->
            val index = navIds.indexOf(item.itemId)
            if (index >= 0) {
                pager.setCurrentItem(index, false)
                true
            } else false
        }

        ViewCompat.setOnApplyWindowInsetsListener(nav) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(bottom = bars.bottom)
            insets
        }

        handleIntent(intent)
        requestNotificationPermissionIfNeeded()
        Reminders.rescheduleAll(this)
        TodayWidget.refresh(this)
        com.superflow.Shortcuts.update(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        // Dynamic shortcut: one-tap check-in without opening a screen.
        if (intent?.action == com.superflow.Shortcuts.ACTION_CHECK_IN) {
            val habitId = intent.getStringExtra(com.superflow.Shortcuts.EXTRA_HABIT_ID)
            if (!habitId.isNullOrBlank()) {
                com.superflow.domain.CommandBus.get(this).execute(
                    "check_in",
                    com.superflow.util.jsonOf("habit" to habitId, "level" to "STANDARD"),
                    com.superflow.domain.Actor.USER
                )
            }
        }
        val tab = intent?.getStringExtra(EXTRA_TAB) ?: return
        val index = when (tab) {
            "today" -> 0; "journey" -> 1; "insights" -> 2; "coach" -> 3; "settings" -> 4
            else -> return
        }
        if (::nav.isInitialized) nav.selectedItemId = navIds[index]
    }

    fun goToTab(index: Int) {
        if (::nav.isInitialized && index in navIds.indices) nav.selectedItemId = navIds[index]
    }

    override fun onPause() {
        super.onPause()
        TodayWidget.refresh(this)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && prefs.remindersEnabled) {
            val perm = android.Manifest.permission.POST_NOTIFICATIONS
            if (checkSelfPermission(perm) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                notificationPermission.launch(perm)
            }
        }
    }

    companion object {
        const val EXTRA_TAB = "tab"
    }
}
