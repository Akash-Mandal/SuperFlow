package com.superflow.ui.common

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.snackbar.Snackbar
import com.superflow.design.Navigation
import com.superflow.R
import com.superflow.data.Prefs
import com.superflow.design.HapticPattern
import com.superflow.design.Haptics

/** Shared UI helpers: theme lookups, haptics, snackbars, insets. */

fun Context.themeColor(attr: Int, fallback: Int = Color.GRAY): Int =
    MaterialColors.getColor(this, attr, fallback)

fun View.themeColor(attr: Int, fallback: Int = Color.GRAY): Int =
    MaterialColors.getColor(this, attr, fallback)

fun Context.dp(value: Int): Int =
    TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
    ).toInt()

fun Context.dpf(value: Float): Float =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

fun Context.sp(value: Float): Float =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

fun View.tint(color: Int) {
    backgroundTintList = ColorStateList.valueOf(color)
}

/** Blends a colour toward the surface, for soft chart fills. */
fun blend(color: Int, towards: Int, ratio: Float): Int =
    ColorUtils.blendARGB(color, towards, ratio)

/**
 * A light selection tick.
 *
 * Kept as an extension because it is called from dozens of places, but the
 * implementation now goes through [SfHaptics] so it respects the user's
 * haptic intensity and uses the tuned waveform rather than the system's
 * generic key click.
 */
fun View.haptic(prefs: Prefs? = null) = SfHaptics.perform(this, Haptics.SELECT, prefs)

/** The heavier confirmation pattern, for a completed or committed action. */
fun View.confirmHaptic(prefs: Prefs? = null) = SfHaptics.perform(this, Haptics.COMPLETE, prefs)

/** Plays any pattern from the [Haptics] vocabulary. */
fun View.haptic(pattern: HapticPattern, prefs: Prefs? = null) =
    SfHaptics.perform(this, pattern, prefs)

/** Lazily reads the user's haptics preference when a caller did not pass it. */
private object AppPrefs {
    fun haptics(view: View): Boolean =
        runCatching { Prefs.get(view.context).hapticsEnabled }.getOrDefault(true)
}

fun View.snack(message: String, actionLabel: String? = null, action: (() -> Unit)? = null) {
    val bar = Snackbar.make(this, message, Snackbar.LENGTH_LONG)
    if (actionLabel != null && action != null) bar.setAction(actionLabel) { action() }
    bar.setAnchorView(rootView.findViewById(R.id.bottom_nav) ?: null)
    bar.show()
}

fun View.visible(show: Boolean) {
    visibility = if (show) View.VISIBLE else View.GONE
}

fun View.fadeIn(durationMs: Long = 220) {
    alpha = 0f
    visibility = View.VISIBLE
    animate().alpha(1f).setDuration(durationMs).start()
}

/**
 * Wires pull-to-refresh on a screen that has a `@id/refresh` container.
 *
 * Three things this centralises, all of which were wrong the first time
 * they were written by hand.
 *
 * **The gesture is a preference.** `Navigation.Gesture.PULL_REFRESH` can be
 * switched off, in which case the layout stays but stops intercepting, so
 * the list scrolls normally and the toolbar's Refresh item is the way in.
 *
 * **The spinner must be dismissed by the caller, not by a timer.** A
 * refresh here is a database read that completes in a few milliseconds, and
 * a spinner that vanishes that fast reads as the gesture not having worked.
 * [onRefresh] is handed a `done` callback; the fragment calls it when the
 * new state has actually been rendered, and [SwipeRefreshLayout] holds the
 * spinner until then.
 *
 * **The colours must be set explicitly.** The spinner takes its arrow and
 * background from its own attributes, not from the app theme, so an
 * unstyled one is Material-blue on white in every palette.
 */
fun SwipeRefreshLayout.wireRefresh(prefs: Prefs, onRefresh: (done: () -> Unit) -> Unit) {
    val enabled = prefs.gestureEnabled(Navigation.Gesture.PULL_REFRESH)
    isEnabled = enabled
    if (!enabled) return

    setColorSchemeColors(context.themeColor(androidx.appcompat.R.attr.colorPrimary))
    setProgressBackgroundColorSchemeColor(
        context.themeColor(com.google.android.material.R.attr.colorSurfaceContainerHigh)
    )
    setOnRefreshListener {
        haptic(Haptics.THRESHOLD, prefs)
        onRefresh { isRefreshing = false }
    }
}

/** Staggered entry animation for a freshly populated list. */
fun RecyclerView.runEntryAnimation() {
    val controller = AnimationUtils.loadLayoutAnimation(context, R.anim.layout_slide_up)
    layoutAnimation = controller
    scheduleLayoutAnimation()
}

fun ViewGroup.inflate(layout: Int, attach: Boolean = false): View =
    android.view.LayoutInflater.from(context).inflate(layout, this, attach)

/** Level colours used by chips, rings and history strips. */
object LevelColors {
    fun tiny(context: Context) = context.getColor(R.color.level_tiny)
    fun minimum(context: Context) = context.getColor(R.color.level_minimum)
    fun standard(context: Context) = context.getColor(R.color.level_standard)
    fun stretch(context: Context) = context.getColor(R.color.level_stretch)
    fun skipped(context: Context) = context.getColor(R.color.state_skipped)
    fun missed(context: Context) = context.getColor(R.color.state_missed)
    fun empty(context: Context) = context.getColor(R.color.state_empty)

    fun forLevel(context: Context, level: com.superflow.data.model.Level): Int = when (level) {
        com.superflow.data.model.Level.TINY -> tiny(context)
        com.superflow.data.model.Level.MINIMUM -> minimum(context)
        com.superflow.data.model.Level.STANDARD -> standard(context)
        com.superflow.data.model.Level.STRETCH -> stretch(context)
    }
}
