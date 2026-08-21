package com.superflow.design

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Chart layout maths (§13).
 *
 * Everything a chart needs to decide before it can draw: where the axis
 * ticks go, how a value maps to a pixel, which bar a touch landed on, and
 * how to bucket a heatmap into weeks. None of it needs a Canvas, all of it
 * is easy to get subtly wrong, and a subtly wrong chart is worse than no
 * chart because it looks authoritative.
 *
 * Keeping it here means it is unit-tested; the Compose layer becomes a thin
 * renderer over values that are already known to be right.
 */
object ChartGeometry {

    /* ------------------------------------------------------------- scales */

    /**
     * A "nice" axis maximum at or above [value].
     *
     * Charts that scale to the exact data maximum produce axis labels like
     * 37 and 74, which nobody can read against. This rounds up to the next
     * 1, 2, 2.5 or 5 times a power of ten, which is the standard trick and
     * gives labels people can actually compare.
     */
    fun niceCeiling(value: Double): Double {
        if (value <= 0.0) return 1.0
        val exponent = floor(kotlin.math.log10(value))
        val magnitude = 10.0.pow(exponent)
        val normalised = value / magnitude
        val nice = when {
            normalised <= 1.0 -> 1.0
            normalised <= 2.0 -> 2.0
            normalised <= 2.5 -> 2.5
            normalised <= 5.0 -> 5.0
            else -> 10.0
        }
        return nice * magnitude
    }

    /**
     * Axis tick values from 0 to a nice ceiling above [maxValue].
     *
     * @param targetTicks how many gridlines to aim for; the result may
     *                    differ, because a readable step matters more than
     *                    an exact count
     */
    fun axisTicks(maxValue: Double, targetTicks: Int = 4): List<Double> {
        if (maxValue <= 0.0 || targetTicks < 1) return listOf(0.0)
        val ceiling = niceCeiling(maxValue)
        val step = niceCeiling(ceiling / targetTicks)
        val out = ArrayList<Double>()
        var v = 0.0
        // Guard the loop on count as well as value: a pathological step
        // could otherwise spin.
        while (v <= ceiling + 1e-9 && out.size <= 64) {
            out.add(v)
            v += step
        }
        if (out.last() < ceiling) out.add(ceiling)
        return out
    }

    /**
     * Maps a value to a fraction of the plot height, 0f at the bottom.
     *
     * Clamped, because a single outlier should overflow the axis rather than
     * escape the chart bounds and draw over the labels.
     */
    fun normalise(value: Double, maxValue: Double): Float {
        if (maxValue <= 0.0) return 0f
        return (value / maxValue).coerceIn(0.0, 1.0).toFloat()
    }

    /* --------------------------------------------------------------- bars */

    /**
     * Bar width and gap for [count] bars across [availableWidth].
     *
     * Bars get thinner as they multiply, down to a floor: below about 3dp a
     * bar stops reading as a bar. Past that point the gap shrinks instead,
     * and past that the chart is simply too dense and should be paged.
     */
    data class BarMetrics(val barWidth: Float, val gap: Float, val overflow: Boolean)

    fun barMetrics(
        availableWidth: Float,
        count: Int,
        preferredGap: Float = 6f,
        minBarWidth: Float = 3f,
    ): BarMetrics {
        if (count <= 0 || availableWidth <= 0f) return BarMetrics(0f, 0f, false)
        val gapTotal = preferredGap * (count - 1)
        val widthWithGaps = (availableWidth - gapTotal) / count
        if (widthWithGaps >= minBarWidth) {
            return BarMetrics(widthWithGaps, preferredGap, false)
        }
        // Not enough room at the preferred gap; give the space to the bars.
        val minTotal = minBarWidth * count
        if (minTotal <= availableWidth) {
            val gap = (availableWidth - minTotal) / max(1, count - 1)
            return BarMetrics(minBarWidth, gap, false)
        }
        // Too dense to render honestly.
        return BarMetrics(availableWidth / count, 0f, true)
    }

    /**
     * Which bar a touch at [x] landed on, or null if it missed.
     *
     * Hit targets extend into the gaps, because asking someone to hit a 4dp
     * bar is not a real interaction. Each touch resolves to the nearest bar
     * within half a slot.
     */
    fun barIndexAt(
        x: Float,
        availableWidth: Float,
        count: Int,
        preferredGap: Float = 6f,
        minBarWidth: Float = 3f,
    ): Int? {
        if (count <= 0 || availableWidth <= 0f) return null
        if (x < 0f || x > availableWidth) return null
        val metrics = barMetrics(availableWidth, count, preferredGap, minBarWidth)
        val slot = metrics.barWidth + metrics.gap
        if (slot <= 0f) return null
        val index = floor(x / slot).toInt()
        return index.coerceIn(0, count - 1)
    }

    /** The x offset of bar [index]'s leading edge. */
    fun barOffset(
        index: Int,
        availableWidth: Float,
        count: Int,
        preferredGap: Float = 6f,
        minBarWidth: Float = 3f,
    ): Float {
        val metrics = barMetrics(availableWidth, count, preferredGap, minBarWidth)
        return index * (metrics.barWidth + metrics.gap)
    }

    /* ------------------------------------------------------------ heatmap */

    /**
     * Splits a day series into calendar weeks.
     *
     * @param values      one entry per day, oldest first
     * @param firstWeekday how many days of the first week are missing, 0..6,
     *                     so the grid starts on the right weekday rather
     *                     than jamming day one into the Monday column
     * @return columns of seven, with nulls padding the incomplete ends
     */
    fun heatmapWeeks(values: List<Int>, firstWeekday: Int = 0): List<List<Int?>> {
        if (values.isEmpty()) return emptyList()
        val offset = firstWeekday.coerceIn(0, 6)
        val padded = ArrayList<Int?>(offset + values.size)
        repeat(offset) { padded.add(null) }
        padded.addAll(values)
        while (padded.size % 7 != 0) padded.add(null)
        return padded.chunked(7)
    }

    /**
     * Cell size for a heatmap that must fit [weeks] columns in [width].
     *
     * Returns at least 1f so a degenerate layout still draws something
     * rather than dividing by zero downstream.
     */
    fun heatmapCellSize(width: Float, weeks: Int, gap: Float = 2f): Float {
        if (weeks <= 0 || width <= 0f) return 1f
        val total = width - gap * (weeks - 1)
        return max(1f, total / weeks)
    }

    /* --------------------------------------------------------------- line */

    /**
     * A rolling mean over [window] samples.
     *
     * Used for the consistency curve (§13.2). Leading positions average what
     * is available rather than being dropped, so the line starts at the left
     * edge instead of floating in from nowhere - a chart that begins a week
     * late looks like missing data.
     */
    fun rollingMean(values: List<Double>, window: Int): List<Double> {
        if (values.isEmpty() || window < 1) return emptyList()
        return values.indices.map { i ->
            val from = max(0, i - window + 1)
            val slice = values.subList(from, i + 1)
            slice.sum() / slice.size
        }
    }

    /**
     * Pearson correlation between two equal-length series.
     *
     * Used by the energy scatter (§13.2). Returns null when it cannot be
     * computed - fewer than three points, or no variance in one series -
     * rather than returning a number the caller would present as a finding.
     * A correlation drawn from two data points is noise with a decimal
     * point.
     */
    fun correlation(xs: List<Double>, ys: List<Double>): Double? {
        if (xs.size != ys.size || xs.size < 3) return null
        val n = xs.size
        val meanX = xs.sum() / n
        val meanY = ys.sum() / n
        var sxy = 0.0
        var sxx = 0.0
        var syy = 0.0
        for (i in 0 until n) {
            val dx = xs[i] - meanX
            val dy = ys[i] - meanY
            sxy += dx * dy
            sxx += dx * dx
            syy += dy * dy
        }
        if (sxx <= 1e-12 || syy <= 1e-12) return null
        val r = sxy / kotlin.math.sqrt(sxx * syy)
        return r.coerceIn(-1.0, 1.0)
    }

    /**
     * How to describe a correlation to a person.
     *
     * Deliberately conservative, and deliberately never causal: "tend to"
     * rather than "because". With the sample sizes a personal habit tracker
     * accumulates, anything stronger is overclaiming.
     */
    fun correlationLabel(r: Double?, sampleSize: Int): String = when {
        r == null || sampleSize < 14 -> "Not enough data yet"
        abs(r) < 0.2 -> "No clear pattern"
        abs(r) < 0.4 -> if (r > 0) "A slight tendency" else "A slight inverse tendency"
        abs(r) < 0.6 -> if (r > 0) "A moderate tendency" else "A moderate inverse tendency"
        else -> if (r > 0) "A strong tendency" else "A strong inverse tendency"
    }

    /* -------------------------------------------------------------- ticks */

    /**
     * Label positions for a horizontal axis of [count] items in [width].
     *
     * Skips labels rather than overlapping them: a chart with 90 unreadable
     * overlapping dates is worse than one with 6 readable ones.
     */
    fun labelStride(count: Int, width: Float, minLabelWidth: Float = 44f): Int {
        if (count <= 0 || width <= 0f) return 1
        val fits = max(1, floor(width / minLabelWidth).toInt())
        if (fits >= count) return 1
        return ceil(count.toDouble() / fits).toInt()
    }

    /** Whether the label at [index] should be drawn, given a stride. */
    fun showLabel(index: Int, count: Int, stride: Int): Boolean {
        if (stride <= 1) return true
        // Always show the last one: the most recent point is the one people
        // look for, and dropping it makes the axis feel arbitrary.
        if (index == count - 1) return true
        return index % stride == 0
    }

    /* ------------------------------------------------------------ helpers */

    /** Rounds a fraction to a whole percentage, clamped to 0..100. */
    fun percent(fraction: Double): Int =
        (fraction.coerceIn(0.0, 1.0) * 100).roundToInt()

    /** The span of a value list, for axis padding. Empty gives 0..1. */
    fun rangeOf(values: List<Double>): ClosedFloatingPointRange<Double> {
        if (values.isEmpty()) return 0.0..1.0
        val lo = values.min()
        val hi = values.max()
        if (lo == hi) {
            // A flat series still needs a visible band, or the line sits on
            // the axis and looks like zero.
            val pad = if (abs(hi) < 1e-9) 1.0 else abs(hi) * 0.1
            return (lo - pad)..(hi + pad)
        }
        return lo..hi
    }

    /** Maps a value into 0f..1f across a range, clamped. */
    fun fractionIn(value: Double, range: ClosedFloatingPointRange<Double>): Float {
        val span = range.endInclusive - range.start
        if (span <= 1e-12) return 0.5f
        return ((value - range.start) / span).coerceIn(0.0, 1.0).toFloat()
    }

    /** Clamps a zoom scale to something a chart can render sensibly. */
    fun clampZoom(scale: Float, minScale: Float = 1f, maxScale: Float = 4f): Float =
        min(maxScale, max(minScale, scale))
}
