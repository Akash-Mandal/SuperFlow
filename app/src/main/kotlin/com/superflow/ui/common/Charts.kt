package com.superflow.ui.common

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.res.ResourcesCompat
import com.google.android.material.R as MR
import com.superflow.R

/**
 * Hand-drawn charts.
 *
 * A charting library was not available offline, and these are small enough to
 * draw directly - which also keeps them perfectly on-theme and animated.
 */

/** Circular progress ring used on the Today header. */
class ProgressRing @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private var progress = 0f
    private var animated = 0f
    private var animator: ValueAnimator? = null

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    private val bounds = RectF()
    var centerLabel: String = ""
    var centerSub: String = ""

    init {
        val stroke = context.dpf(10f)
        trackPaint.strokeWidth = stroke
        arcPaint.strokeWidth = stroke
        trackPaint.color = themeColor(MR.attr.colorSurfaceVariant)
        arcPaint.color = themeColor(MR.attr.colorPrimary)
        labelPaint.color = themeColor(MR.attr.colorOnSurface)
        subPaint.color = themeColor(MR.attr.colorOnSurfaceVariant)
        labelPaint.textSize = context.sp(24f)
        subPaint.textSize = context.sp(12f)
    }

    fun setProgress(value: Float, animate: Boolean = true) {
        val target = value.coerceIn(0f, 1f)
        progress = target
        animator?.cancel()
        if (!animate) {
            animated = target
            invalidate()
            return
        }
        animator = ValueAnimator.ofFloat(animated, target).apply {
            duration = 700
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                animated = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = resolveSize(context.dp(132), widthMeasureSpec)
        setMeasuredDimension(size, resolveSize(size, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        val pad = trackPaint.strokeWidth / 2f + context.dpf(2f)
        bounds.set(pad, pad, width - pad, height - pad)
        canvas.drawArc(bounds, 0f, 360f, false, trackPaint)
        if (animated > 0f) {
            canvas.drawArc(bounds, -90f, 360f * animated, false, arcPaint)
        }
        if (centerLabel.isNotEmpty()) {
            val cy = height / 2f + labelPaint.textSize / 3f -
                    (if (centerSub.isEmpty()) 0f else context.dpf(7f))
            canvas.drawText(centerLabel, width / 2f, cy, labelPaint)
        }
        if (centerSub.isNotEmpty()) {
            canvas.drawText(centerSub, width / 2f,
                height / 2f + labelPaint.textSize / 3f + context.dpf(14f), subPaint)
        }
    }
}

/** Rounded vertical bar chart for weekly repetitions. */
class BarChart @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    data class Bar(val label: String, val value: Int, val highlighted: Boolean = false)

    private var bars: List<Bar> = emptyList()
    private var maxValue = 1
    private var phase = 0f
    private var animator: ValueAnimator? = null

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    init {
        barPaint.color = themeColor(MR.attr.colorPrimary)
        trackPaint.color = themeColor(MR.attr.colorSurfaceVariant)
        labelPaint.color = themeColor(MR.attr.colorOnSurfaceVariant)
        valuePaint.color = themeColor(MR.attr.colorOnSurfaceVariant)
        labelPaint.textSize = context.sp(11f)
        valuePaint.textSize = context.sp(11f)
    }

    fun setBars(list: List<Bar>, animate: Boolean = true) {
        bars = list
        maxValue = (list.maxOfOrNull { it.value } ?: 1).coerceAtLeast(1)
        animator?.cancel()
        if (!animate) { phase = 1f; invalidate(); return }
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 650
            interpolator = DecelerateInterpolator()
            addUpdateListener { phase = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            resolveSize(context.dp(280), widthMeasureSpec),
            resolveSize(context.dp(150), heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        if (bars.isEmpty()) return
        val labelH = context.dpf(20f)
        val valueH = context.dpf(16f)
        val chartH = height - labelH - valueH
        val slot = width.toFloat() / bars.size
        val barW = minOf(slot * 0.52f, context.dpf(26f))
        val radius = barW / 2f

        for ((i, bar) in bars.withIndex()) {
            val cx = slot * i + slot / 2f
            val left = cx - barW / 2f
            val right = cx + barW / 2f

            canvas.drawRoundRect(
                left, valueH, right, valueH + chartH, radius, radius, trackPaint
            )

            val fraction = bar.value.toFloat() / maxValue
            val barH = chartH * fraction * phase
            if (bar.value > 0 && barH > 1f) {
                barPaint.color = if (bar.highlighted) themeColor(MR.attr.colorPrimary)
                else blend(themeColor(MR.attr.colorPrimary), themeColor(MR.attr.colorSurface), 0.25f)
                canvas.drawRoundRect(
                    left, valueH + chartH - barH, right, valueH + chartH, radius, radius, barPaint
                )
                canvas.drawText(bar.value.toString(), cx, valueH - context.dpf(4f), valuePaint)
            }
            canvas.drawText(bar.label, cx, height - context.dpf(5f), labelPaint)
        }
    }
}

/** GitHub-style contribution heatmap for long-range consistency. */
class HeatmapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    /** intensity 0f..1f, or -1f for "not scheduled". */
    private var cells: List<Float> = emptyList()
    private var columns = 0

    private val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var cellSize = 0f
    private var gap = 0f

    init {
        gap = context.dpf(3f)
    }

    fun setCells(values: List<Float>) {
        cells = values
        columns = (values.size + 6) / 7
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        cellSize = if (columns > 0) (w - gap * (columns - 1)) / columns else context.dpf(12f)
        cellSize = cellSize.coerceAtMost(context.dpf(18f))
        val h = (cellSize * 7 + gap * 6).toInt()
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        if (cells.isEmpty()) return
        val base = themeColor(MR.attr.colorPrimary)
        val emptyColor = themeColor(MR.attr.colorSurfaceVariant)
        val radius = context.dpf(4f)
        for ((index, value) in cells.withIndex()) {
            val col = index / 7
            val row = index % 7
            val left = col * (cellSize + gap)
            val top = row * (cellSize + gap)
            cellPaint.color = when {
                value < 0f -> blend(emptyColor, themeColor(MR.attr.colorSurface), 0.5f)
                value == 0f -> emptyColor
                else -> blend(base, emptyColor, 1f - (0.25f + 0.75f * value))
            }
            canvas.drawRoundRect(
                left, top, left + cellSize, top + cellSize, radius, radius, cellPaint
            )
        }
    }
}

/** Compact 14-day strip shown on a habit card. */
class HistoryStrip @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    /** One of: 1 success, 0 open, -1 missed, -2 skipped, -3 not scheduled. */
    private var states: List<Int> = emptyList()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun setStates(list: List<Int>) {
        states = list
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            resolveSize(context.dp(220), widthMeasureSpec),
            resolveSize(context.dp(28), heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        if (states.isEmpty()) return
        val gap = context.dpf(3f)
        val w = (width - gap * (states.size - 1)) / states.size
        val radius = context.dpf(3f)
        for ((i, s) in states.withIndex()) {
            paint.color = when (s) {
                1 -> themeColor(MR.attr.colorPrimary)
                -1 -> context.getColor(R.color.state_missed)
                -2 -> context.getColor(R.color.state_skipped)
                -3 -> blend(themeColor(MR.attr.colorSurfaceVariant),
                    themeColor(MR.attr.colorSurface), 0.6f)
                else -> themeColor(MR.attr.colorSurfaceVariant)
            }
            val left = i * (w + gap)
            canvas.drawRoundRect(left, 0f, left + w, height.toFloat(), radius, radius, paint)
        }
    }
}
