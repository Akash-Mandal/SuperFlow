package com.superflow.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
// Aliased because this file defines members named `spring` and `tween`.
// Unaliased, `spring(...)` inside `fun spring(...)` resolves to the member
// and recurses until the stack runs out.
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring as composeSpring
import androidx.compose.animation.core.tween as composeTween
import androidx.compose.runtime.Immutable
import com.superflow.design.Motion
import com.superflow.design.tokens.V3Motion
import com.superflow.design.tokens.V3Springs

/**
 * Motion tokens (§8.1).
 *
 * Durations come from [Motion], which also owns the scaling rule, so the
 * user's motion preference and the system's "remove animations" setting are
 * honoured in one place rather than at every call site.
 *
 * The contract worth knowing: at the None level every duration collapses to
 * zero and [SfMotionSpecs.enabled] is false. Animations must not merely run
 * faster - a user who turned motion off, or who set the system animator
 * scale to zero, has usually done so because motion makes them ill. Callers
 * that cannot express "no animation" as a zero duration should branch on
 * [SfMotionSpecs.enabled] instead.
 */
enum class SfMotionLevel(val id: Int, val label: String) {
    None(Motion.NONE, "None"),
    Reduced(Motion.REDUCED, "Reduced"),
    Standard(Motion.STANDARD, "Standard"),
    Expressive(Motion.EXPRESSIVE, "Expressive");

    companion object {
        fun fromId(id: Int): SfMotionLevel = entries.firstOrNull { it.id == id } ?: Standard
    }
}

/**
 * Easing curves.
 *
 * Standard is Material's, used for anything that moves between two resting
 * states. Emphasised decelerate is for things entering the screen: they
 * arrive fast and settle, which reads as responsive. Emphasised accelerate
 * is for things leaving, which should get out of the way quickly.
 */
object SfEasing {
    val standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val decelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val accelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    val linear: Easing = Easing { it }
}

/**
 * Resolved animation specs for the current motion level.
 *
 * Held in a composition local so a component can animate correctly without
 * every component needing to read preferences.
 */
@Immutable
data class SfMotionSpecs(
    val level: SfMotionLevel,
    /** False when motion is off; branch on this rather than on a zero duration. */
    val enabled: Boolean,
    val instant: Int,
    val fast: Int,
    val quick: Int,
    val normal: Int,
    val slow: Int,
    val deliberate: Int,
    /** Scene-level transitions: onboarding steps, graduation reveal (alpha3 §7.1). */
    val cinematic: Int,
) {
    /** Stagger between items in an orchestrated list entrance (§8.4). */
    fun staggerDelay(index: Int): Int =
        Motion.staggerDelay(index, level.id, systemAnimationsOff = !enabled)

    /**
     * A tween over one of the named durations.
     *
     * @param delayMs stagger offset, for orchestrated sequences. Ignored
     *                when motion is disabled: a delay with no animation is
     *                just content that appears late.
     */
    fun <T> tween(
        durationMs: Int,
        easing: Easing = SfEasing.standard,
        delayMs: Int = 0,
    ): FiniteAnimationSpec<T> =
        if (!enabled || durationMs <= 0) {
            snap()
        } else {
            composeTween(durationMs, delayMillis = delayMs.coerceAtLeast(0), easing = easing)
        }

    /**
     * A spring, for anything the user drags or that should feel physical.
     *
     * Springs ignore duration by nature, so when motion is disabled this
     * returns a snap rather than a stiffer spring - a very stiff spring still
     * moves, and "no motion" has to mean none.
     */
    fun <T> spring(
        damping: Float = Spring.DampingRatioNoBouncy,
        stiffness: Float = Spring.StiffnessMedium,
    ): AnimationSpec<T> =
        if (!enabled) snap() else composeSpring(dampingRatio = damping, stiffness = stiffness)

    /** The bouncy spring used for check marks and completions (§8.3). */
    fun <T> bouncy(): AnimationSpec<T> =
        if (!enabled) snap() else composeSpring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        )

    /**
     * The alpha3 named springs (ALPHA3_VISUAL_PLAN §7.1), resolved against
     * the motion level: standard for enter/exit and shared-element morphs,
     * snappy for the check-in bloom and chip selection, settle for drag
     * snap-backs. All collapse to a snap when motion is off.
     */
    fun <T> springStandard(): FiniteAnimationSpec<T> =
        if (!enabled) snap() else composeSpring(
            dampingRatio = V3Springs.STANDARD.dampingRatio,
            stiffness = V3Springs.STANDARD.stiffness,
        )

    fun <T> springSnappy(): FiniteAnimationSpec<T> =
        if (!enabled) snap() else composeSpring(
            dampingRatio = V3Springs.SNAPPY.dampingRatio,
            stiffness = V3Springs.SNAPPY.stiffness,
        )

    fun <T> springSettle(): FiniteAnimationSpec<T> =
        if (!enabled) snap() else composeSpring(
            dampingRatio = V3Springs.SETTLE.dampingRatio,
            stiffness = V3Springs.SETTLE.stiffness,
        )

    companion object {
        fun forLevel(level: SfMotionLevel, systemAnimationsOff: Boolean): SfMotionSpecs {
            val off = Motion.isDisabled(level.id, systemAnimationsOff)
            fun d(base: Int) = Motion.duration(base, level.id, systemAnimationsOff)
            return SfMotionSpecs(
                level = level,
                enabled = !off,
                instant = d(Motion.INSTANT),
                fast = d(V3Motion.FAST),
                quick = d(Motion.QUICK),
                normal = d(V3Motion.NORMAL),
                slow = d(V3Motion.SLOW),
                deliberate = d(Motion.DELIBERATE),
                cinematic = d(V3Motion.CINEMATIC),
            )
        }
    }
}

/** A spring tuned for the progress ring, which should settle without overshoot. */
internal fun ringSpring(enabled: Boolean): AnimationSpec<Float> =
    if (!enabled) snap() else SpringSpec(
        dampingRatio = 0.75f,
        stiffness = Spring.StiffnessLow,
    )
