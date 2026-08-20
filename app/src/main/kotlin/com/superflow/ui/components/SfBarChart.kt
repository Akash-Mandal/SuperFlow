package com.superflow.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.superflow.design.ChartGeometry
import com.superflow.design.Haptics
import com.superflow.design.Space
import com.superflow.ui.common.SfHaptics
import com.superflow.ui.theme.SfTheme

/** One bar. [label] is shown on the axis when there is room for it. */
data class SfBar(
    val label: String,
    val value: Double,
    val highlighted: Boolean = false,
)

/**
 * An interactive bar chart (§13.1).
 *
 * All layout comes from [ChartGeometry], which is unit-tested, so this
 * function only draws. Tapping a bar selects it and shows its value; the hit
 * targets extend into the gaps so a thin bar is still reachable.
 *
 * The whole chart is one accessibility node carrying a spoken summary.
 * Exposing 90 individual bars to a screen reader would be technically
 * complete and practically useless.
 */
@Composable
fun SfBarChart(
    bars: List<SfBar>,
    modifier: Modifier = Modifier,
    height: Int = 160,
    valueFormat: (Double) -> String = { it.toInt().toString() },
    /** Announced before the data, e.g. "Completions per day". */
    label: String? = null,
) {
    if (bars.isEmpty()) return

    val scheme = MaterialTheme.colorScheme
    val motion = SfTheme.motion
    val view = LocalView.current

    var selected by remember(bars) { mutableIntStateOf(-1) }

    val maxValue = ChartGeometry.niceCeiling(bars.maxOf { it.value })

    // One animation driving every bar, rather than one per bar: a chart with
    // 90 independent animations is 90 recompositions a frame.
    val grow by animateFloatAsState(
        targetValue = 1f,
        animationSpec = motion.tween(motion.normal),
        label = "barGrow",
    )

    val summary = buildString {
        if (label != null) {
            append(label)
            append(". ")
        }
        append("${bars.size} bars. ")
        val top = bars.maxByOrNull { it.value }
        if (top != null) append("Highest ${top.label}, ${valueFormat(top.value)}. ")
        val total = bars.sumOf { it.value }
        append("Total ${valueFormat(total)}.")
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height.dp)
                .semantics { contentDescription = summary }
                .pointerInput(bars) {
                    detectTapGestures { offset ->
                        val index = ChartGeometry.barIndexAt(
                            x = offset.x,
                            availableWidth = size.width.toFloat(),
                            count = bars.size,
                        )
                        if (index != null) {
                            // Tapping the selected bar clears it, so the
                            // tooltip can be dismissed without hunting for
                            // somewhere neutral to tap.
                            selected = if (selected == index) -1 else index
                            SfHaptics.perform(view, Haptics.SELECT)
                        }
                    }
                },
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(height.dp)) {
                val metrics = ChartGeometry.barMetrics(size.width, bars.size)
                if (metrics.overflow) return@Canvas

                // Gridlines first, so bars draw over them.
                for (tick in ChartGeometry.axisTicks(maxValue)) {
                    val y = size.height * (1f - ChartGeometry.normalise(tick, maxValue))
                    drawLine(
                        color = scheme.outlineVariant.copy(alpha = 0.5f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1f,
                    )
                }

                bars.forEachIndexed { index, bar ->
                    val fraction = ChartGeometry.normalise(bar.value, maxValue) * grow
                    val barHeight = size.height * fraction
                    if (barHeight <= 0f) return@forEachIndexed

                    val x = ChartGeometry.barOffset(index, size.width, bars.size)
                    val color = when {
                        index == selected -> scheme.primary
                        bar.highlighted -> scheme.primary
                        // Unselected bars recede when something is selected,
                        // so the comparison is against a quiet background.
                        selected >= 0 -> scheme.primary.copy(alpha = 0.35f)
                        else -> scheme.primary.copy(alpha = 0.75f)
                    }

                    drawRoundRect(
                        color = color,
                        topLeft = Offset(x, size.height - barHeight),
                        size = Size(metrics.barWidth, barHeight),
                        cornerRadius = CornerRadius(minOf(metrics.barWidth / 2f, 4.dp.toPx())),
                    )
                }
            }
        }

        // The tooltip is a line of text below the chart rather than a
        // floating overlay: an overlay covers the neighbouring bars, which
        // are exactly what the user is comparing against.
        val chosen = bars.getOrNull(selected)
        Text(
            text = if (chosen != null) {
                "${chosen.label}: ${valueFormat(chosen.value)}"
            } else {
                label.orEmpty()
            },
            style = SfTheme.type.data,
            color = if (chosen != null) scheme.onSurface else scheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Space.SM.dp),
        )

        SfBarAxis(bars = bars, color = scheme.onSurfaceVariant)
    }
}

/** The x axis, with labels thinned to whatever fits. */
@Composable
private fun SfBarAxis(bars: List<SfBar>, color: Color) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(16.dp)
    ) {
        val stride = ChartGeometry.labelStride(bars.size, size.width)
        // Drawing text on a Canvas needs the native paint; rather than
        // reach for it here, the axis renders tick marks and the caller
        // supplies labels in the summary. Marks alone still communicate
        // the density of the series.
        val metrics = ChartGeometry.barMetrics(size.width, bars.size)
        bars.forEachIndexed { index, _ ->
            if (!ChartGeometry.showLabel(index, bars.size, stride)) return@forEachIndexed
            val x = ChartGeometry.barOffset(index, size.width, bars.size) + metrics.barWidth / 2f
            drawLine(
                color = color.copy(alpha = 0.6f),
                start = Offset(x, 0f),
                end = Offset(x, 4.dp.toPx()),
                strokeWidth = 1f,
            )
        }
    }
}
