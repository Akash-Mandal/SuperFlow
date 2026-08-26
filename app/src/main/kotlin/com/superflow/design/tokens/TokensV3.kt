package com.superflow.design.tokens

/**
 * Alpha3 Tier-0 design primitives ("Design System v3", ALPHA3_VISUAL_PLAN §3).
 *
 * Like everything under `com.superflow.design`, this file is deliberately
 * free of Android and Compose imports: raw values and pure decisions only.
 * Compose resolves them into themed tokens in `ui/theme/`; View/XML paths
 * read them directly.
 *
 * Values here are the alpha3 scale. Where a value refines an alpha2 token
 * (`design/DesignTokens.kt`), the alpha2 token remains authoritative for the
 * surfaces not yet migrated; both compile side by side during M0/M1 and the
 * migration lint gate removes direct v2 usage at M2.
 */

/** Corner vocabulary, used semantically rather than by size (§6.1). */
object V3Radius {
    /** Hero/Focus cards, Studio header, sheets' top corners. */
    const val HERO = 28

    /** Standard cards. */
    const val CARD = 20

    /** Nested content inside cards. */
    const val INNER = 14

    /** Buttons, inputs. */
    const val CONTROL = 12

    /** Chips, badges: resolved against view height by callers. */
    const val FULL = -1

    /**
     * Nested-radius rule (§3.2 of the plan): an element inset inside a
     * container uses the container radius minus the inset, floored so tiny
     * insets never produce a sharper-than-control corner or a negative one.
     */
    fun nested(outerDp: Int, insetDp: Int): Int =
        (outerDp - insetDp).coerceAtLeast(CONTROL / 2).coerceAtMost(outerDp)
}

/** Alpha3 motion durations in milliseconds, at the Standard level (§7.1). */
object V3Motion {
    const val INSTANT = 90
    const val FAST = 150
    const val NORMAL = 240
    const val SLOW = 380

    /** Scene-level transitions: onboarding steps, graduation reveal. */
    const val CINEMATIC = 520

    /** Breath Ring idle cycle period. */
    const val BREATH_PERIOD = 6_000L

    /** Breath amplitude as a fraction of ring radius (§1 "Breath Ring"). */
    const val BREATH_AMPLITUDE = 0.02f

    /** Skeleton-to-content crossfade budget beyond data arrival (§17). */
    const val SKELETON_SWAP_EXTRA_MS = 150
}

/**
 * Named spring physics (§7.1).
 *
 * Damping ratio + stiffness pairs as data, so the View property-animation
 * path and the Compose path resolve identical feel from one definition.
 */
data class SpringSpec(val dampingRatio: Float, val stiffness: Float)

object V3Springs {
    /** Enter/exit and shared-element morphs: settles with a hint of life. */
    val STANDARD = SpringSpec(0.85f, 380f)

    /** Check-in bloom, chip selection: fast, minimal overshoot. */
    val SNAPPY = SpringSpec(0.7f, 800f)

    /** Drag settle-backs: critically damped, no bounce at all. */
    val SETTLE = SpringSpec(1f, 300f)
}

/**
 * Material recipes (§6.2) as data.
 *
 * [alphaPct] is surface opacity for translucent materials; [borderAlphaPct]
 * is the hairline border strength; [blur] marks whether the recipe wants a
 * background blur when the platform provides one (API 31+), with [fallback]
 * naming what to draw when it does not.
 */
data class MaterialRecipe(
    val name: String,
    val alphaPct: Int,
    val borderAlphaPct: Int,
    val grainOverlayPct: Int,
    val blur: Boolean,
    val fallback: Fallback,
) {
    enum class Fallback { SOLID_SURFACE, SCRIM }
}

object V3Materials {
    /** Light-mode content cards: paper stack with hairline edge. */
    val PAPER = MaterialRecipe(
        name = "paper", alphaPct = 100, borderAlphaPct = 8,
        grainOverlayPct = 2, blur = false, fallback = MaterialRecipe.Fallback.SOLID_SURFACE,
    )

    /** Sheets, floating bars, command palette: frosted glass. */
    val GLASS = MaterialRecipe(
        name = "glass", alphaPct = 82, borderAlphaPct = 12,
        grainOverlayPct = 0, blur = true, fallback = MaterialRecipe.Fallback.SCRIM,
    )

    /** Dark-mode cards: elevation-tinted ink, no border. */
    val INK = MaterialRecipe(
        name = "ink", alphaPct = 100, borderAlphaPct = 0,
        grainOverlayPct = 0, blur = false, fallback = MaterialRecipe.Fallback.SOLID_SURFACE,
    )

    val byName: Map<String, MaterialRecipe> =
        listOf(PAPER, GLASS, INK).associateBy { it.name }
}
