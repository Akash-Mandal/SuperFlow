package com.superflow.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.superflow.design.HistoryStates
import com.superflow.ui.theme.SfTheme

/**
 * A compact strip of recent days (§12.1).
 *
 * Each cell is one day, oldest on the left. The colour encoding comes from
 * [HistoryStates], which is pinned to the domain layer's encoding by test -
 * the alternative is a magic-number switch here that silently starts
 * rendering rest days as misses when the domain changes.
 *
 * The whole strip is one accessibility node with a summary, not fourteen
 * unlabelled cells. A screen reader user wants "10 of the last 14 days
 * done, 3 day streak", not to swipe through a fortnight one square at a
 * time.
 */
@Composable
fun SfHistoryStrip(
    states: List<Int>,
    modifier: Modifier = Modifier,
    cellSize: Int = 14,
    maxDays: Int = 14,
) {
    if (states.isEmpty()) return

    val shown = states.takeLast(maxDays)
    val colors = SfTheme.colors
    val scheme = MaterialTheme.colorScheme
    val highContrast = SfTheme.highContrast

    val done = shown.count { it == HistoryStates.COMPLETED }
    val opportunities = shown.count(HistoryStates::countsAsOpportunity)
    val streak = HistoryStates.currentStreak(shown)

    val summary = buildString {
        append("Last ${shown.size} days: ")
        append(if (opportunities == 0) "nothing scheduled" else "$done of $opportunities done")
        if (streak > 1) append(", $streak day streak")
    }

    Row(
        modifier = modifier
            .height(cellSize.dp)
            .semantics { contentDescription = summary },
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        for (state in shown) {
            SfHistoryCell(
                state = state,
                size = cellSize,
                completedColor = colors.success,
                missedColor = colors.stateMissed,
                skippedColor = colors.stateSkipped,
                emptyColor = colors.stateEmpty,
                outline = scheme.outlineVariant,
                outlined = highContrast,
            )
        }
    }
}

@Composable
private fun SfHistoryCell(
    state: Int,
    size: Int,
    completedColor: Color,
    missedColor: Color,
    skippedColor: Color,
    emptyColor: Color,
    outline: Color,
    outlined: Boolean,
) {
    val base = when (state) {
        HistoryStates.COMPLETED -> completedColor
        HistoryStates.MISSED -> missedColor
        HistoryStates.SKIPPED -> skippedColor
        else -> emptyColor
    }
    // Emphasis is applied as alpha rather than by picking a lighter colour,
    // so the same rule works in both light and dark without a second table.
    val alpha = HistoryStates.emphasisFor(state)

    Canvas(
        modifier = Modifier
            .size(size.dp)
            // The parent carries the summary; individual cells would only
            // add noise for a screen reader.
            .clearAndSetSemantics { },
    ) {
        val corner = CornerRadius(size.dp.toPx() * 0.28f)
        drawRoundRect(
            color = base.copy(alpha = if (state == HistoryStates.COMPLETED) 1f else alpha),
            size = Size(this.size.width, this.size.height),
            cornerRadius = corner,
        )
        // In high contrast every cell gets an edge, so that a pale "not
        // scheduled" square is still locatable against the card.
        if (outlined) {
            drawRoundRect(
                color = outline,
                topLeft = Offset.Zero,
                size = Size(this.size.width, this.size.height),
                cornerRadius = corner,
                style = Stroke(width = 1.dp.toPx()),
            )
        }
    }
}
