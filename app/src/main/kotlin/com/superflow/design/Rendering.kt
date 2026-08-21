package com.superflow.design

/**
 * Which renderer draws each primary screen.
 *
 * SuperFlow is mid-migration. The plan's Phase 2 and 3 screens are written
 * in Compose and live in `ui.screens`; the Views they replace still exist
 * and still work. Both are in the tree at once, which is the normal state
 * of a migration and an abnormal state to leave undocumented, so the choice
 * is made here, once, instead of being implied by whichever fragment
 * happens to inflate what.
 *
 * ### Why this is a constant and not a preference
 *
 * A user cannot meaningfully answer "which UI toolkit would you like".
 * The flag exists so the migration can be landed screen by screen and
 * reverted screen by screen if one of them regresses, which is a
 * developer's decision made before the build ships, not a setting.
 *
 * ### The current state, and why
 *
 * Studio is Compose. It has no View predecessor — it is the merge of Coach,
 * Blueprint and the AI engine (plan 11.4) and was written new — so there
 * was nothing to migrate and no fallback to keep.
 *
 * Today, Journey and Insights are Views. Each has a finished Compose
 * counterpart in `ui.screens`, and each also has a View implementation that
 * has been exercised. The Compose ones are held back for one reason:
 * nothing in this environment can compile a Compose source file, so those
 * screens have been checked statically (`tools/check_compose.py`) and read,
 * but never run. Shipping three unexecuted screens as the app's only path
 * to its own content would be a worse decision than shipping the ones that
 * work, and the flag makes flipping them a one-line change once a Compose
 * toolchain is available.
 *
 * Flipping a screen means: set its entry here to [Renderer.COMPOSE], run
 * the app, and delete the View implementation once it has been through a
 * release. Not before — a fallback nobody can reach is not a fallback.
 */
object Rendering {

    enum class Renderer { VIEWS, COMPOSE }

    /** Screens that have two implementations, and which one is live. */
    val today: Renderer = Renderer.VIEWS
    val journey: Renderer = Renderer.VIEWS
    val insights: Renderer = Renderer.VIEWS

    /** Compose-only from the start; there is no View version to fall back to. */
    val studio: Renderer = Renderer.COMPOSE

    val onboarding: Renderer = Renderer.COMPOSE

    fun rendererFor(tab: Navigation.Tab): Renderer = when (tab) {
        Navigation.Tab.TODAY -> today
        Navigation.Tab.JOURNEY -> journey
        Navigation.Tab.INSIGHTS -> insights
        Navigation.Tab.STUDIO -> studio
    }

    fun isCompose(tab: Navigation.Tab): Boolean = rendererFor(tab) == Renderer.COMPOSE

    /**
     * Every screen that still carries two implementations.
     *
     * Kept as a list so the count can be asserted: it should only ever go
     * down, and a new entry appearing means someone added a second
     * implementation instead of replacing one.
     */
    val dualImplemented: List<Navigation.Tab> = listOf(
        Navigation.Tab.TODAY,
        Navigation.Tab.JOURNEY,
        Navigation.Tab.INSIGHTS,
    )

    /** True once the migration is finished and this file can be deleted. */
    val migrationComplete: Boolean
        get() = Navigation.tabs.all { isCompose(it) }
}
