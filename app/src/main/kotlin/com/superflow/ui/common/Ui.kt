package com.superflow.ui.common

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.Snackbar
import com.superflow.R
import com.superflow.data.Prefs

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

fun View.haptic(prefs: Prefs? = null) {
    if (prefs != null && !prefs.hapticsEnabled) return
    performHapticFeedback(
        HapticFeedbackConstants.VIRTUAL_KEY,
        HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
    )
}

fun View.confirmHaptic(prefs: Prefs? = null) {
    if (prefs != null && !prefs.hapticsEnabled) return
    performHapticFeedback(
        if (android.os.Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM
        else HapticFeedbackConstants.VIRTUAL_KEY
    )
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
