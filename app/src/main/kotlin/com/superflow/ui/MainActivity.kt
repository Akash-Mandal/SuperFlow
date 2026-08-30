package com.superflow.ui

import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.enableEdgeToEdge
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
import com.google.android.material.navigation.NavigationBarView
import com.google.android.material.navigationrail.NavigationRailView
import com.superflow.DynamicShortcuts
import com.superflow.R
import com.superflow.data.Prefs
import com.superflow.design.Navigation
import com.superflow.design.Rendering
import com.superflow.notify.Reminders
import com.superflow.ui.common.SfTheme
import com.superflow.ui.insights.ComposeInsightsFragment
import com.superflow.ui.insights.InsightsFragment
import com.superflow.ui.journey.ComposeJourneyFragment
import com.superflow.ui.journey.JourneyFragment
import com.superflow.ui.onboarding.OnboardingActivity
import com.superflow.ui.settings.SettingsActivity
import com.superflow.ui.studio.StudioFragment
import com.superflow.ui.today.ComposeTodayFragment
import com.superflow.ui.today.TodayFragment
import com.superflow.widget.TodayWidget

/**
 * Application shell.
 *
 * Four primary destinations (plan 10.1) in a ViewPager2, driven by either a
 * bottom bar or a navigation rail depending on the window. Settings is no
 * longer one of them: it is reached from the Today header, because a fifth
 * of the most valuable surface in the app was going to a screen people open
 * twice a month.
 *
 * Every navigation decision here — which key means which tab, what an old
 * stored index migrates to, whether this window gets a rail — is deferred to
 * [Navigation], which is pure and tested. This class only wires it to views.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var pager: ViewPager2
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var rail: NavigationRailView
    private lateinit var prefs: Prefs

    /**
     * The appearance revision this instance inflated against.
     *
     * Palette, density and contrast are theme overlays, so they can only be
     * changed by rebuilding the activity. onResume compares this against the
     * current value and recreates when the user changed something while an
     * appearance screen was in front of us.
     */
    private var builtAtRevision = 0

    /** Menu ids in [Navigation.tabs] order. The two must not drift. */
    private val navIds = listOf(
        R.id.nav_today, R.id.nav_journey, R.id.nav_insights, R.id.nav_studio,
    )

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Overlays must be merged into the theme before any view inflates,
        // which means before super.onCreate.
        prefs = Prefs.get(this)
        SfTheme.apply(this, prefs)
        builtAtRevision = prefs.appearanceRevision
        super.onCreate(savedInstanceState)
        // Edge-to-edge on every device, including Samsung with display cutouts.
        // Must be after super.onCreate so the window is attached.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        try {
            enableEdgeToEdge()
        } catch (_: Exception) { }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        if (!prefs.onboarded) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)
        pager = findViewById(R.id.pager)
        bottomNav = findViewById(R.id.bottom_nav)
        rail = findViewById(R.id.nav_rail)

        // Samsung + gesture nav: keep the bottom bar above the system nav bar
        // and the rail clear of the status bar / cutout.
        ViewCompat.setOnApplyWindowInsetsListener(bottomNav) { v, insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            v.updatePadding(bottom = navBars.bottom)
            insets
        }
        rail?.let { r ->
            ViewCompat.setOnApplyWindowInsetsListener(r) { v, insets ->
                val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                v.updatePadding(top = bars.top, bottom = bars.bottom)
                insets
            }
        }

        pager.isUserInputEnabled = false
        pager.offscreenPageLimit = 2
        try {
            val recycler = pager.getChildAt(0) as? androidx.recyclerview.widget.RecyclerView
            recycler?.let {
                it.setHasFixedSize(true)
                it.setItemViewCacheSize(8)
                val pool = androidx.recyclerview.widget.RecyclerView.RecycledViewPool()
                it.setRecycledViewPool(pool)
            }
        } catch (_: Exception) { }
        // Which fragment backs each tab is decided by design.Rendering, not
        // here: three of the four screens exist in both a View and a Compose
        // implementation while the migration lands, and that fact belongs in
        // one documented place rather than spread across four call sites.
        pager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = Navigation.tabCount
            override fun createFragment(position: Int): Fragment {
                val tab = Navigation.tabAt(position)
                return when (tab) {
                    Navigation.Tab.TODAY ->
                        if (Rendering.isCompose(tab)) ComposeTodayFragment() else TodayFragment()
                    Navigation.Tab.JOURNEY ->
                        if (Rendering.isCompose(tab)) ComposeJourneyFragment() else JourneyFragment()
                    Navigation.Tab.INSIGHTS ->
                        if (Rendering.isCompose(tab)) ComposeInsightsFragment() else InsightsFragment()
                    // Compose-only; no View version was ever written.
                    Navigation.Tab.STUDIO -> StudioFragment()
                }
            }
        }

        val onSelected = NavigationBarView.OnItemSelectedListener { item ->
            val index = navIds.indexOf(item.itemId)
            if (index >= 0) {
                pager.setCurrentItem(index, false)
                // Keep the surface that was *not* tapped in step, so a fold
                // or rotation does not reveal a stale selection.
                syncSelection(index)
                true
            } else {
                false
            }
        }
        bottomNav.setOnItemSelectedListener(onSelected)
        rail.setOnItemSelectedListener(onSelected)

        applyLabelMode()
        applyPlacement()

        ViewCompat.setOnApplyWindowInsetsListener(bottomNav) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(bottom = bars.bottom)
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(rail) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // A rail is only ever on one side, but which side depends on the
            // layout direction, so pad both and let the zero win.
            view.updatePadding(top = bars.top, bottom = bars.bottom, left = bars.left)
            insets
        }

        // Start destination, but only on a genuinely cold start: on a
        // rotation or a theme recreate the user is mid-task and being thrown
        // back to their default tab would be hostile. handleIntent can still
        // override this, since an explicit deep link outranks a preference.
        if (savedInstanceState == null) {
            select(Navigation.migrateTabIndex(prefs.startDestination))
        }

        handleIntent(intent)
        requestNotificationPermissionIfNeeded()
        // Both are non-blocking: they run on the serialized background lane
        // (see AppBackground) and must not delay the first frame.
        Reminders.rescheduleAll(this)
        TodayWidget.refresh(this)
        com.superflow.Shortcuts.update(this)
    }

    override fun onStart() {
        super.onStart()
        if (::prefs.isInitialized && prefs.onboarded &&
            com.superflow.security.AppLock.shouldLock(prefs)) {
            startActivity(Intent(this, com.superflow.security.LockActivity::class.java))
        }
    }

    // Public rather than the inherited protected visibility: the deep-link
    // path is covered by MainActivityLaunchTest, which drives a running
    // activity through onNewIntent.
    public override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /**
     * Routes an incoming key.
     *
     * The key can be years old — a pinned launcher shortcut, or a
     * notification scheduled before the Coach/Studio merge — so it goes
     * through [Navigation.destinationOf], which maps the retired names.
     * An unrecognised key does nothing at all, deliberately: dropping
     * someone on an arbitrary screen is worse than leaving them where
     * they are.
     */
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
        val key = intent?.getStringExtra(EXTRA_TAB) ?: return
        when (val destination = Navigation.destinationOf(key)) {
            is Navigation.Destination.ToTab -> select(destination.tab)
            is Navigation.Destination.ToRoute -> openRoute(destination.route)
            null -> Unit
        }
    }

    private fun openRoute(route: Navigation.Route) {
        when (route) {
            Navigation.Route.SETTINGS ->
                startActivity(Intent(this, SettingsActivity::class.java))
            Navigation.Route.APPEARANCE ->
                startActivity(
                    Intent(this, SettingsActivity::class.java)
                        .putExtra(SettingsActivity.EXTRA_TAB, SettingsActivity.TAB_APPEARANCE),
                )
            Navigation.Route.ONBOARDING ->
                startActivity(Intent(this, OnboardingActivity::class.java))
        }
    }

    /** Moves to a tab, whichever navigation surface is currently showing. */
    fun select(tab: Navigation.Tab) {
        if (!::pager.isInitialized) return
        pager.setCurrentItem(tab.index, false)
        syncSelection(tab.index)
    }

    // Both nav surfaces share one OnItemSelectedListener, and assigning
    // selectedItemId on one surface synchronously dispatches that listener,
    // which calls syncSelection again. Without the guard the two surfaces
    // re-enter each other and overflow the stack (setSelectedItemId ->
    // onItemSelected -> syncSelection -> setSelectedItemId -> ...).
    private var syncingSelection = false

    private fun syncSelection(index: Int) {
        if (syncingSelection) return
        val id = navIds.getOrNull(index) ?: return
        syncingSelection = true
        try {
            if (bottomNav.selectedItemId != id) bottomNav.selectedItemId = id
            if (rail.selectedItemId != id) rail.selectedItemId = id
        } finally {
            syncingSelection = false
        }
    }

    /** Legacy entry point; index is interpreted in the old five-tab order. */
    @Deprecated("Use select(Navigation.Tab)", ReplaceWith("select(tab)"))
    fun goToTab(index: Int) = select(Navigation.migrateTabIndex(index))

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // A fold, an unfold or a rotation can change the placement without
        // recreating us. Swapping visibility is enough — the pager and every
        // fragment survive, which is why this is not a layout swap.
        applyPlacement()
    }

    /**
     * Shows the navigation surface this window calls for.
     *
     * Note that a landscape phone gets the rail despite being COMPACT-width:
     * a bottom bar plus the gesture inset costs about a third of the height
     * available for content, and the thumb is already at the edge.
     */
    private fun applyPlacement() {
        val metrics = resources.displayMetrics
        val widthDp = (metrics.widthPixels / metrics.density).toInt()
        val heightDp = (metrics.heightPixels / metrics.density).toInt()
        val placement = Navigation.placementFor(widthDp, heightDp)
        val useRail = placement != Navigation.NavPlacement.BOTTOM

        bottomNav.visibility = if (useRail) View.GONE else View.VISIBLE
        rail.visibility = if (useRail) View.VISIBLE else View.GONE

        Navigation.railWidth(placement)?.let { width ->
            val params = rail.layoutParams
            val px = (width * metrics.density).toInt()
            if (params.width != px) {
                params.width = px
                rail.layoutParams = params
            }
        }
        syncSelection(pager.currentItem)
    }

    /** Applies the Experience tab's label preference (15.1). */
    private fun applyLabelMode() {
        bottomNav.labelVisibilityMode = when (Navigation.tabLabels(prefs.tabLabels)) {
            Navigation.TabLabels.ALWAYS -> NavigationBarView.LABEL_VISIBILITY_LABELED
            Navigation.TabLabels.SELECTED_ONLY -> NavigationBarView.LABEL_VISIBILITY_SELECTED
            Navigation.TabLabels.NEVER -> NavigationBarView.LABEL_VISIBILITY_UNLABELED
        }
        // The rail is always labelled; see Navigation.showsLabel.
        rail.labelVisibilityMode = NavigationBarView.LABEL_VISIBILITY_LABELED

        // Hiding a label is a visual choice and must not remove information,
        // so the spoken description carries the name and the position either
        // way.
        navIds.forEachIndexed { index, id ->
            val tab = Navigation.tabAt(index)
            val selected = bottomNav.selectedItemId == id
            val description = Navigation.describeTab(tab, selected)
            bottomNav.menu.findItem(id)?.contentDescription = description
            rail.menu.findItem(id)?.contentDescription = description
        }
    }

    override fun onResume() {
        super.onResume()
        // A theme overlay changed under us (the user was just in Appearance).
        // Recreating is the only way to pick up new overlays, and doing it
        // here rather than at the change site keeps every activity in the
        // back stack correct, not just the visible one.
        if (SfTheme.needsRecreate(prefs, builtAtRevision)) {
            recreate()
        } else if (::bottomNav.isInitialized) {
            // The label mode is not an overlay, so it changes without a
            // recreate and has to be re-read here.
            applyLabelMode()
        }
    }

    override fun onPause() {
        super.onPause()
        // Debounced + backgrounded: covers the "user goes home" case without
        // doing database work on the main thread on every pause.
        TodayWidget.refresh(this)
        DynamicShortcuts.refresh(this)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < 33) return
        // The system prompt is shown at most once per install (unless the
        // user resets app data or revokes access in settings); re-prompting
        // on every cold start is hostile and it also keeps the dialog on the
        // first-frame path.
        if (prefs.notifPermissionAsked) return
        if (!prefs.remindersEnabled) return
        val perm = android.Manifest.permission.POST_NOTIFICATIONS
        if (checkSelfPermission(perm) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            prefs.notifPermissionAsked = true
            notificationPermission.launch(perm)
        }
    }

    companion object {
        const val EXTRA_TAB = "tab"
    }
}
