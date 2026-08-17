package com.superflow.ui

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * A small calm design system built directly on Android views.
 *
 * Calm is a feature: warm paper background, one accent colour, generous
 * spacing, no badges, no red failure theatrics.
 */
object Palette {
    const val BG = 0xFFF7F4EF.toInt()
    const val SURFACE = 0xFFFFFFFF.toInt()
    const val SURFACE_ALT = 0xFFF1ECE4.toInt()
    const val INK = 0xFF1E1B18.toInt()
    const val INK_SOFT = 0xFF5F594F.toInt()
    const val INK_FAINT = 0xFF8D8579.toInt()
    const val ACCENT = 0xFF3A7D5C.toInt()
    const val ACCENT_SOFT = 0xFFDCEBE2.toInt()
    const val WARM = 0xFFB4703A.toInt()
    const val WARM_SOFT = 0xFFF6E7D8.toInt()
    const val LINE = 0xFFE3DCD1.toInt()
    const val DANGER = 0xFF9B4A3C.toInt()
}

fun Context.dp(value: Int): Int =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

fun rounded(color: Int, radiusPx: Int, strokeColor: Int? = null, strokeWidth: Int = 0): GradientDrawable =
    GradientDrawable().apply {
        setColor(color)
        cornerRadius = radiusPx.toFloat()
        if (strokeColor != null && strokeWidth > 0) setStroke(strokeWidth, strokeColor)
    }

fun lp(width: Int, height: Int, weight: Float = 0f): LinearLayout.LayoutParams =
    LinearLayout.LayoutParams(width, height, weight)

val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

/* --------------------------------------------------------------- builders */

fun Context.column(padding: Int = 0, block: LinearLayout.() -> Unit = {}): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        if (padding > 0) setPadding(dp(padding), dp(padding), dp(padding), dp(padding))
        layoutParams = lp(MATCH, WRAP)
        block()
    }

fun Context.row(block: LinearLayout.() -> Unit = {}): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = lp(MATCH, WRAP)
        block()
    }

fun Context.title(text: String, size: Float = 26f, color: Int = Palette.INK): TextView =
    TextView(this).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        setTextColor(color)
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        layoutParams = lp(MATCH, WRAP)
    }

fun Context.heading(text: String): TextView =
    TextView(this).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setTextColor(Palette.INK_FAINT)
        letterSpacing = 0.09f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        layoutParams = lp(MATCH, WRAP).apply { topMargin = dp(18); bottomMargin = dp(8) }
    }

fun Context.body(
    text: String,
    size: Float = 15f,
    color: Int = Palette.INK_SOFT,
    bold: Boolean = false
): TextView = TextView(this).apply {
    this.text = text
    setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
    setTextColor(color)
    setLineSpacing(dp(4).toFloat(), 1f)
    if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    layoutParams = lp(MATCH, WRAP)
}

fun Context.card(block: LinearLayout.() -> Unit = {}): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(Palette.SURFACE, dp(18), Palette.LINE, dp(1))
        setPadding(dp(16), dp(16), dp(16), dp(16))
        layoutParams = lp(MATCH, WRAP).apply { bottomMargin = dp(12) }
        block()
    }

fun Context.softCard(color: Int = Palette.ACCENT_SOFT, block: LinearLayout.() -> Unit = {}): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(color, dp(18))
        setPadding(dp(16), dp(16), dp(16), dp(16))
        layoutParams = lp(MATCH, WRAP).apply { bottomMargin = dp(12) }
        block()
    }

fun Context.primaryButton(text: String, onClick: () -> Unit): TextView =
    TextView(this).apply {
        this.text = text
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        background = rounded(Palette.ACCENT, dp(14))
        setPadding(dp(20), dp(14), dp(20), dp(14))
        isClickable = true
        setOnClickListener { onClick() }
        layoutParams = lp(MATCH, WRAP).apply { topMargin = dp(8) }
    }

fun Context.ghostButton(
    text: String,
    color: Int = Palette.ACCENT,
    fill: Int = Palette.SURFACE,
    onClick: () -> Unit
): TextView = TextView(this).apply {
    this.text = text
    gravity = Gravity.CENTER
    setTextColor(color)
    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    background = rounded(fill, dp(12), Palette.LINE, dp(1))
    setPadding(dp(14), dp(10), dp(14), dp(10))
    isClickable = true
    setOnClickListener { onClick() }
}

fun Context.chip(
    text: String,
    active: Boolean = false,
    activeColor: Int = Palette.ACCENT,
    onClick: (() -> Unit)? = null
): TextView = TextView(this).apply {
    this.text = text
    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
    setTextColor(if (active) Color.WHITE else Palette.INK_SOFT)
    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    background = if (active) rounded(activeColor, dp(20))
    else rounded(Palette.SURFACE, dp(20), Palette.LINE, dp(1))
    setPadding(dp(14), dp(8), dp(14), dp(8))
    if (onClick != null) {
        isClickable = true
        setOnClickListener { onClick() }
    }
    layoutParams = lp(WRAP, WRAP).apply { rightMargin = dp(8); bottomMargin = dp(8) }
}

fun Context.field(hint: String, value: String = "", lines: Int = 1, numeric: Boolean = false): EditText =
    EditText(this).apply {
        this.hint = hint
        setText(value)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        setTextColor(Palette.INK)
        setHintTextColor(Palette.INK_FAINT)
        background = rounded(Palette.SURFACE, dp(12), Palette.LINE, dp(1))
        setPadding(dp(14), dp(12), dp(14), dp(12))
        inputType = when {
            numeric -> InputType.TYPE_CLASS_NUMBER
            lines > 1 -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                    InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            else -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        if (lines > 1) {
            setLines(lines)
            gravity = Gravity.TOP or Gravity.START
        }
        layoutParams = lp(MATCH, WRAP).apply { bottomMargin = dp(10) }
    }

fun Context.label(text: String): TextView =
    TextView(this).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setTextColor(Palette.INK_SOFT)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        layoutParams = lp(MATCH, WRAP).apply { bottomMargin = dp(6); topMargin = dp(6) }
    }

fun Context.divider(): View =
    View(this).apply {
        setBackgroundColor(Palette.LINE)
        layoutParams = lp(MATCH, dp(1)).apply { topMargin = dp(10); bottomMargin = dp(10) }
    }

fun Context.spacer(height: Int): View =
    View(this).apply { layoutParams = lp(MATCH, dp(height)) }

fun Context.checkbox(text: String, checked: Boolean = false): CheckBox =
    CheckBox(this).apply {
        this.text = text
        isChecked = checked
        setTextColor(Palette.INK_SOFT)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        layoutParams = lp(MATCH, WRAP)
    }

fun Context.scroller(block: LinearLayout.() -> Unit): ScrollView {
    val content = column(0) { block() }
    return ScrollView(this).apply {
        isFillViewport = true
        addView(content, lp(MATCH, WRAP))
        layoutParams = lp(MATCH, MATCH)
    }
}

fun Context.hScroll(block: LinearLayout.() -> Unit): HorizontalScrollView {
    val content = row { block() }
    return HorizontalScrollView(this).apply {
        isHorizontalScrollBarEnabled = false
        addView(content)
        layoutParams = lp(MATCH, WRAP)
    }
}

/** Chip row that wraps to multiple lines. */
class FlowRow(context: Context) : ViewGroup(context) {
    private val gap = context.dp(8)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        var x = 0
        var y = 0
        var rowHeight = 0
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == GONE) continue
            measureChild(child, MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), heightMeasureSpec)
            if (x + child.measuredWidth > width && x > 0) {
                x = 0
                y += rowHeight + gap
                rowHeight = 0
            }
            x += child.measuredWidth + gap
            rowHeight = maxOf(rowHeight, child.measuredHeight)
        }
        setMeasuredDimension(width, y + rowHeight)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val width = r - l
        var x = 0
        var y = 0
        var rowHeight = 0
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == GONE) continue
            if (x + child.measuredWidth > width && x > 0) {
                x = 0
                y += rowHeight + gap
                rowHeight = 0
            }
            child.layout(x, y, x + child.measuredWidth, y + child.measuredHeight)
            x += child.measuredWidth + gap
            rowHeight = maxOf(rowHeight, child.measuredHeight)
        }
    }
}

fun Context.flowRow(block: FlowRow.() -> Unit): FlowRow =
    FlowRow(this).apply {
        layoutParams = lp(MATCH, WRAP).apply { bottomMargin = dp(4) }
        block()
    }

/** Slim progress bar that celebrates action, not app usage. */
fun Context.progressBar(fraction: Float, color: Int = Palette.ACCENT): View {
    val track = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        background = rounded(Palette.SURFACE_ALT, dp(6))
        layoutParams = lp(MATCH, dp(8)).apply { topMargin = dp(10) }
    }
    val fill = View(this).apply {
        background = rounded(color, dp(6))
        layoutParams = LinearLayout.LayoutParams(0, MATCH, fraction.coerceIn(0.001f, 1f))
    }
    val rest = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(0, MATCH, (1f - fraction).coerceIn(0f, 0.999f))
    }
    track.addView(fill)
    track.addView(rest)
    return track
}

fun Activity.toast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

fun Context.iconDot(color: Int, size: Int = 10): View =
    View(this).apply {
        background = rounded(color, dp(size))
        layoutParams = lp(dp(size), dp(size)).apply { rightMargin = dp(8) }
    }
