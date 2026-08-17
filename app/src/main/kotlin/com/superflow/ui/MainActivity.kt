package com.superflow.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.superflow.data.Prefs
import com.superflow.data.Repo
import com.superflow.domain.CommandBus
import com.superflow.notify.Reminders

/**
 * Application shell with the five primary destinations:
 * Today, Journey, Insights, AI and Settings.
 */
class MainActivity : Activity() {

    lateinit var bus: CommandBus
    lateinit var repo: Repo
    lateinit var prefs: Prefs

    private lateinit var content: FrameLayout
    private lateinit var tabBar: LinearLayout
    private val tabs = listOf("Today", "Journey", "Insights", "AI", "Settings")
    private var current = 0

    private val screens = ArrayList<Screen>()
    private val onChange: () -> Unit = { runOnUiThread { refresh() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bus = CommandBus.get(this)
        repo = bus.repo
        prefs = Prefs.get(this)

        if (!prefs.onboarded) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        screens.add(TodayScreen(this))
        screens.add(JourneyScreen(this))
        screens.add(InsightsScreen(this))
        screens.add(AiScreen(this))
        screens.add(SettingsScreen(this))

        setContentView(buildRoot())
        bus.addListener(onChange)
        requestNotificationPermissionIfNeeded()
        Reminders.rescheduleAll(this)
        select(0)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::bus.isInitialized) bus.removeListener(onChange)
    }

    override fun onResume() {
        super.onResume()
        if (::content.isInitialized) refresh()
    }

    private fun buildRoot(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Palette.BG)
            layoutParams = lp(MATCH, MATCH)
        }
        content = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, 0, 1f)
        }
        tabBar = buildTabBar()
        root.addView(content)
        root.addView(tabBar)
        return root
    }

    private fun buildTabBar(): LinearLayout {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Palette.SURFACE)
            setPadding(dp(6), dp(8), dp(6), dp(10))
            layoutParams = lp(MATCH, WRAP)
        }
        val top = View(this).apply { setBackgroundColor(Palette.LINE) }
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = lp(MATCH, WRAP)
        }
        wrapper.addView(top, lp(MATCH, dp(1)))
        tabs.forEachIndexed { index, name ->
            val item = TextView(this).apply {
                text = name
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(dp(2), dp(8), dp(2), dp(8))
                isClickable = true
                setOnClickListener { select(index) }
                layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
            }
            bar.addView(item)
        }
        wrapper.addView(bar)
        return wrapper
    }

    private fun styleTabs() {
        val bar = (tabBar.getChildAt(1) as LinearLayout)
        for (i in 0 until bar.childCount) {
            val tv = bar.getChildAt(i) as TextView
            val active = i == current
            tv.setTextColor(if (active) Palette.ACCENT else Palette.INK_FAINT)
            tv.typeface = Typeface.create(
                if (active) "sans-serif-medium" else "sans-serif", Typeface.NORMAL
            )
            tv.background = if (active) rounded(Palette.ACCENT_SOFT, dp(12)) else null
        }
    }

    fun select(index: Int) {
        current = index.coerceIn(0, screens.size - 1)
        styleTabs()
        refresh()
    }

    fun refresh() {
        if (!::content.isInitialized) return
        content.removeAllViews()
        val view = screens[current].build()
        content.addView(view, FrameLayout.LayoutParams(MATCH, MATCH))
    }

    fun openAiTab(prefill: String? = null) {
        (screens[3] as AiScreen).prefill = prefill
        select(3)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && prefs.remindersEnabled) {
            val perm = "android.permission.POST_NOTIFICATIONS"
            if (checkSelfPermission(perm) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(perm), 101)
            }
        }
    }
}

/** A tab screen builds a fresh view tree whenever domain state changes. */
interface Screen {
    fun build(): View
}
