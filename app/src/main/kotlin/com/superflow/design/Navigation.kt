package com.superflow.design

/**
 * The app's information architecture, as data.
 *
 * Section 10 of the UI/UX plan collapses five tabs into four and moves
 * settings out of the tab bar entirely. That is a bigger change than it
 * looks: tab indices are persisted (start destination), sent in intents
 * (notifications, widgets, launcher shortcuts) and hard-coded in a handful
 * of fragments. Getting it wrong sends a reminder tap to the wrong screen.
 *
 * So the shape of the navigation lives here, in one pure place that the
 * tests can reach, rather than being spread across MainActivity, the
 * notification builders and the widget. The Android layer asks this object
 * where things go and renders the answer.
 *
 * No Android imports. This file cannot see R, and must not learn to.
 */
object Navigation {

    /**
     * A primary destination — one entry in the tab bar or rail.
     *
     * Settings is deliberately absent. It is reached from the Today header
     * (10.1), not from the tab bar, because a settings tab spends a fifth of
     * the app's most valuable real estate on a screen people open twice a
     * month.
     */
    enum class Tab(
        /** Stable string used in intents, deep links and preferences. */
        val key: String,
        val label: String,
        /** One line for a tooltip or an accessibility hint. */
        val detail: String,
    ) {
        TODAY("today", "Today", "What needs doing now."),
        JOURNEY("journey", "Journey", "Your identities, goals, systems and habits."),
        INSIGHTS("insights", "Insights", "Evidence of what is actually happening."),
        STUDIO("studio", "Studio", "Coaching, blueprints and the AI engine."),
        ;

        /** Position in the bar. Declaration order is the display order. */
        val index: Int get() = ordinal
    }

    /** The tabs, in display order. */
    val tabs: List<Tab> = Tab.entries.toList()

    /** How many primary destinations there are. Four, per 10.1. */
    val tabCount: Int get() = tabs.size

    /**
     * Non-tab destinations that still need to be addressable by key, because
     * something outside the app links to them.
     */
    enum class Route(val key: String) {
        SETTINGS("settings"),
        APPEARANCE("appearance"),
        ONBOARDING("onboarding"),
        ;
    }

    // ---------------------------------------------------------------- lookup

    /** The tab with this key, or null. Case-insensitive, trims whitespace. */
    fun tabOf(key: String?): Tab? {
        val k = key?.trim()?.lowercase() ?: return null
        return tabs.firstOrNull { it.key == k } ?: legacyAliases[k]
    }

    /**
     * Keys that used to mean something and still arrive from the wild.
     *
     * A scheduled notification created before the merge carries "coach"; a
     * pinned launcher shortcut may carry it for the life of the install. We
     * do not get to stop accepting these, so they are mapped rather than
     * dropped. "settings" is not here: it is a route now, not a tab, and
     * [destinationOf] handles it.
     */
    private val legacyAliases: Map<String, Tab> = mapOf(
        "coach" to Tab.STUDIO,
        "blueprint" to Tab.STUDIO,
        "engine" to Tab.STUDIO,
        "ai" to Tab.STUDIO,
        "home" to Tab.TODAY,
        "tree" to Tab.JOURNEY,
        "stats" to Tab.INSIGHTS,
    )

    /** Where a key points: a tab, a route, or nowhere. */
    sealed interface Destination {
        data class ToTab(val tab: Tab) : Destination
        data class ToRoute(val route: Route) : Destination
    }

    /**
     * Resolves any incoming navigation key.
     *
     * Returns null for keys we do not recognise, and callers should then do
     * nothing rather than guessing — landing the user somewhere arbitrary is
     * worse than landing them nowhere.
     */
    fun destinationOf(key: String?): Destination? {
        val k = key?.trim()?.lowercase() ?: return null
        tabOf(k)?.let { return Destination.ToTab(it) }
        Route.entries.firstOrNull { it.key == k }?.let { return Destination.ToRoute(it) }
        return null
    }

    /** The tab at this index, clamped into range. Never throws. */
    fun tabAt(index: Int): Tab = tabs[index.coerceIn(0, tabs.lastIndex)]

    // ------------------------------------------------------------- migration

    /**
     * Translates an index from the old five-tab bar into the new four.
     *
     * Old order was Today, Journey, Insights, Coach, Settings. The first
     * three are unchanged, Coach became Studio at the same index, and
     * Settings has no tab any more — a stored preference pointing at it
     * falls back to Today rather than to Studio, because "the tab that
     * happens to be last now" is not what that user chose.
     */
    fun migrateTabIndex(old: Int): Tab = when (old) {
        0 -> Tab.TODAY
        1 -> Tab.JOURNEY
        2 -> Tab.INSIGHTS
        3 -> Tab.STUDIO
        else -> Tab.TODAY
    }

    // ------------------------------------------------------- adaptive layout

    /**
     * Width buckets, following the Material window size classes.
     *
     * The breakpoints are Google's, not ours, because device makers target
     * them and because a bucket boundary in an unusual place shows up as a
     * layout that changes at the wrong moment during a fold.
     */
    enum class WidthClass { COMPACT, MEDIUM, EXPANDED }

    /** Height buckets. Only the compact case matters — a phone on its side. */
    enum class HeightClass { COMPACT, MEDIUM, EXPANDED }

    fun widthClass(widthDp: Int): WidthClass = when {
        widthDp < 600 -> WidthClass.COMPACT
        widthDp < 840 -> WidthClass.MEDIUM
        else -> WidthClass.EXPANDED
    }

    fun heightClass(heightDp: Int): HeightClass = when {
        heightDp < 480 -> HeightClass.COMPACT
        heightDp < 900 -> HeightClass.MEDIUM
        else -> HeightClass.EXPANDED
    }

    /** Where the primary navigation goes (10.2). */
    enum class NavPlacement {
        /** Bottom bar. Phone portrait. */
        BOTTOM,

        /** Left rail, icons plus short labels. Landscape phones, foldables. */
        RAIL,

        /** Left rail that stays expanded beside the content. Tablets. */
        WIDE_RAIL,
        ;
    }

    /**
     * Chooses a navigation placement for a window.
     *
     * A short window gets a rail even when it is narrow: in landscape on a
     * phone, a bottom bar plus the gesture inset eats a third of the height
     * we have for content, and the thumb is already at the side.
     */
    fun placementFor(widthDp: Int, heightDp: Int): NavPlacement {
        val w = widthClass(widthDp)
        val h = heightClass(heightDp)
        return when {
            w == WidthClass.EXPANDED -> NavPlacement.WIDE_RAIL
            w == WidthClass.MEDIUM -> NavPlacement.RAIL
            h == HeightClass.COMPACT -> NavPlacement.RAIL
            else -> NavPlacement.BOTTOM
        }
    }

    /**
     * Whether content should be laid out in two panes — list on the left,
     * detail on the right — instead of pushing a new screen.
     */
    fun twoPane(widthDp: Int): Boolean = widthClass(widthDp) == WidthClass.EXPANDED

    /**
     * How wide the content column should be, in dp.
     *
     * Long-form text stops being readable past roughly 70 characters, which
     * at our body size is around 600dp. On a tablet the extra width goes to
     * margins rather than to longer lines.
     */
    fun contentWidth(widthDp: Int): Int = if (widthDp <= MAX_CONTENT_WIDTH) widthDp else MAX_CONTENT_WIDTH

    const val MAX_CONTENT_WIDTH = 600

    /** Rail width by placement, in dp. Null when there is no rail. */
    fun railWidth(placement: NavPlacement): Int? = when (placement) {
        NavPlacement.BOTTOM -> null
        NavPlacement.RAIL -> 80
        NavPlacement.WIDE_RAIL -> 220
    }

    // ------------------------------------------------------------ tab labels

    /** Label style for the tab bar, from the Experience settings (15.1). */
    enum class TabLabels { ALWAYS, SELECTED_ONLY, NEVER }

    val tabLabelOptions: List<Choice> = listOf(
        Choice(0, "always", "Labels", "Every tab is named."),
        Choice(1, "selected", "Selected only", "Only the tab you are on is named."),
        Choice(2, "never", "Icons only", "Quietest, but you have to know the icons."),
    )

    fun tabLabels(id: Int): TabLabels = when (id) {
        1 -> TabLabels.SELECTED_ONLY
        2 -> TabLabels.NEVER
        else -> TabLabels.ALWAYS
    }

    /**
     * Whether a tab shows its label.
     *
     * A rail always labels, whatever the setting says: a vertical strip of
     * unlabelled icons with no bottom-bar convention behind it is a
     * guessing game, and the room is there.
     */
    fun showsLabel(style: TabLabels, selected: Boolean, placement: NavPlacement): Boolean = when {
        placement == NavPlacement.WIDE_RAIL -> true
        style == TabLabels.ALWAYS -> true
        style == TabLabels.SELECTED_ONLY -> selected
        else -> false
    }

    /**
     * The spoken description of a tab.
     *
     * Screen readers announce position, because "Journey, tab" alone does
     * not tell you how much of the bar you have left. The label is included
     * even when the visual label is hidden — hiding text is a visual
     * decision and must not remove information.
     */
    fun describeTab(tab: Tab, selected: Boolean): String {
        val position = "tab ${tab.index + 1} of $tabCount"
        val state = if (selected) "selected" else "not selected"
        return "${tab.label}, $position, $state"
    }

    // ---------------------------------------------------------- app shortcuts

    /**
     * A launcher long-press shortcut (10.4).
     *
     * Android allows at most five dynamic shortcuts and shows four on most
     * launchers, so the list is capped at four and ordered by how often the
     * action is wanted from a cold start.
     */
    data class Shortcut(
        val id: String,
        val label: String,
        /** What the shortcut opens, as a [destinationOf] key. */
        val target: String,
        /** Optional action the target screen should perform on arrival. */
        val action: String? = null,
    )

    val shortcuts: List<Shortcut> = listOf(
        Shortcut("check_in", "Check in", "today", "check"),
        Shortcut("add_habit", "Add habit", "journey", "add_habit"),
        Shortcut("ask_studio", "Ask Studio", "studio", "compose"),
        Shortcut("review", "Weekly review", "insights", "review"),
    )

    /** Shortcuts are useless if they point nowhere; the tests hold this. */
    fun shortcutsResolve(): Boolean = shortcuts.all { destinationOf(it.target) != null }

    // ------------------------------------------------------------- gestures

    /**
     * Gesture shortcuts from 10.3, each individually disableable in the
     * Experience tab because gestures that fire by accident are worse than
     * no gestures at all — especially the destructive ones.
     */
    enum class Gesture(val key: String, val label: String, val destructive: Boolean) {
        SWIPE_CHECK("swipeCheck", "Swipe to check in", false),
        SWIPE_SKIP("swipeSkip", "Swipe to skip", true),
        LONG_PRESS_MENU("longPressMenu", "Long press for menus", false),
        PULL_REFRESH("pullRefresh", "Pull to refresh", false),
        DOUBLE_TAP_RING("doubleTapRing", "Double tap the ring for Insights", false),
        ;
    }

    /**
     * Whether a gesture may fire.
     *
     * Every gesture also has a visible equivalent, so switching one off
     * costs discoverability rather than capability. Reduced-motion does not
     * disable gestures — it is about animation, not input — but a
     * destructive gesture is suppressed when confirmations are on, so that
     * a swipe cannot silently skip a habit.
     */
    fun gestureEnabled(gesture: Gesture, enabled: Set<String>, confirmDestructive: Boolean): Boolean {
        if (gesture.key !in enabled) return false
        return !(gesture.destructive && confirmDestructive)
    }

    /** Every gesture on. The default, and the value a fresh install gets. */
    val allGestures: Set<String> = Gesture.entries.map { it.key }.toSet()
}
