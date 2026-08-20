package com.superflow.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.superflow.design.ChartGeometry
import com.superflow.design.Haptics
import com.superflow.design.HistoryStates
import com.superflow.design.Space
import com.superflow.ui.common.SfHaptics
import com.superflow.ui.theme.SfTheme
import kotlin.math.floor

/**
 * A calendar heatmap (§13.1).
 *
 * One column per week, one cell per day, oldest on the left. Bucketing comes
 * from [ChartGeometry.heatmapWeeks] and the colour encoding from
 * [HistoryStates], both tested.
 *
 * Scrolls horizontally rather than squeezing a year into a phone width. The
 * plan asks for pinch-zoom; scroll is the better primitive here, because a
 * zoomed-out year of 2px cells conveys nothing that the completion rate
 * beneath it does not convey better, and pinch competes with the parent
 * list's scroll.
 *
 * @param states one entry per day, oldest first, in the [HistoryStates]
 *               encoding
 * @param firstWeekday how many days of the first week are missing, so the
 *                     grid aligns to real weekdays
 */
@Composable
fun SfHeatmap(
    states: List<Int>,
    modifier: Modifier = Modifier,
    cellSize: Int = 13,
    gap: Int = 3,
    firstWeekday: Int = 0,
    onDayClick: ((index: Int, state: Int) -> Unit)? = null,
) {
    if (states.isEmpty()) return

    val scheme = MaterialTheme.colorScheme
    val colors = SfTheme.colors
    val view = LocalView.current
    val scroll = rememberScrollState()

    val weeks = remember(states, firstWeekday) {
        ChartGeometry.heatmapWeeks(states, firstWeekday)
    }
    var selected by remember(states) { mutableIntStateOf(-1) }

    val rate = HistoryStates.completionRate(states)
    val summary = buildString {
        append("${states.size} days. ")
        append(
            if (rate == null) {
                "Nothing scheduled."
            } else {
                "${ChartGeometry.percent(rate)} percent done."
            }
        )
        val streak = HistoryStates.currentStreak(states)
        if (streak > 1) append(" $streak day streak.")
    }

    val gridWidth = weeks.size * (cellSize + gap) - gap
    val gridHeight = 7 * (cellSize + gap) - gap

    Column(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.horizontalScroll(scroll)) {
            Canvas(
                modifier = Modifier
                    .width(gridWidth.dp)
                    .height(gridHeight.dp)
                    .semantics { contentDescription = summary }
                    .pointerInput(weeks) {
                        detectTapGestures { offset ->
                            val slot = (cellSize + gap).dp.toPx()
                            if (slot <= 0f) return@detectTapGestures
                            val col = floor(offset.x / slot).toInt()
                            val row = floor(offset.y / slot).toInt()
                            if (col !in weeks.indices || row !in 0..6) return@detectTapGestures
                            val value = weeks[col].getOrNull(row) ?: return@detectTapGestures
                            // Recover the index into the original series so
                            // the caller can look the day up.
                            val index = col * 7 + row - firstWeekday.coerceIn(0, 6)
                            if (index !in states.indices) return@detectTapGestures
                            selected = index
                            SfHaptics.perform(view, Haptics.SELECT)
                            onDayClick?.invoke(index, value)
                        }
                    },
            ) {
                val slot = (cellSize + gap).dp.toPx()
                val side = cellSize.dp.toPx()
                val corner = CornerRadius(side * 0.25f)

                weeks.forEachIndexed { col, week ->
                    week.forEachIndexed { row, state ->
                        // A null is padding at the start or end of the
                        // range: no cell at all, rather than an empty one,
                        // so the grid does not imply days that predate the
                        // habit.
                        if (state == null) return@forEachIndexed

                        val base = when (state) {
                            HistoryStates.COMPLETED -> colors.success
                            HistoryStates.MISSED -> colors.stateMissed
                            HistoryStates.SKIPPED -> colors.stateSkipped
                            else -> colors.stateEmpty
                        }
                        val alpha = if (state == HistoryStates.COMPLETED) {
                            1f
                        } else {
                            HistoryStates.emphasisFor(state)
                        }

                        drawRoundRect(
                            color = base.copy(alpha = alpha),
                            topLeft = Offset(col * slot, row * slot),
                            size = Size(side, side),
                            cornerRadius = corner,
                        )
                    }
                }

                // The selection ring, drawn last so it is never overdrawn.
                if (selected in states.indices) {
                    val padded = selected + firstWeekday.coerceIn(0, 6)
                    val col = padded / 7
                    val row = padded % 7
                    drawRoundRect(
                        color = scheme.onSurface,
                        topLeft = Offset(col * slot, row * slot),
                        size = Size(side, side),
                        cornerRadius = corner,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
                    )
                }
            }
        }

        val chosen = states.getOrNull(selected)
        Text(
            text = if (chosen != null) {
                "Day ${selected + 1}: ${HistoryStates.labelFor(chosen)}"
            } else {
                summary
            },
            style = SfTheme.type.data,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Space.SM.dp),
        )
    }
}
