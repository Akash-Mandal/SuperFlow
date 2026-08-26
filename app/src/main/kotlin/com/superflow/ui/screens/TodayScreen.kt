package com.superflow.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.superflow.data.model.Level
import com.superflow.design.Space
import com.superflow.ui.components.SfCard
import com.superflow.ui.components.SfCardVariant
import com.superflow.ui.components.SfHabitCard
import com.superflow.ui.components.SfProgressRing
import com.superflow.ui.components.SfSectionHeader
import com.superflow.ui.components.SfTodaySkeleton
import com.superflow.ui.theme.SfTheme
import com.superflow.ui.today.TodayRow
import com.superflow.ui.today.TodayUiState

/**
 * The Today screen in Compose (§11.1).
 *
 * Renders the same [TodayRow] list the RecyclerView adapter renders, so the
 * ViewModel is untouched and the two implementations can coexist while the
 * migration proceeds. That was the point of the row model already being a
 * sealed class of data classes - it is a UI-agnostic description of the
 * screen, and Compose is simply a second reader of it.
 *
 * @param onAction every user action routes out through this, so the screen
 *                 itself holds no dependencies on the ViewModel and can be
 *                 previewed with a literal state
 */
sealed interface TodayAction {
    data class CheckIn(val habitId: String, val level: Level) : TodayAction
    data class Skip(val habitId: String) : TodayAction
    data class Undo(val habitId: String) : TodayAction
    data class OpenHabit(val habitId: String) : TodayAction
    data class ToggleFocus(val focusId: String, val done: Boolean) : TodayAction
    data class RemoveFocus(val focusId: String) : TodayAction
    data object FocusAdd : TodayAction
    data object FocusSuggest : TodayAction
    data class SuggestionAction(val row: TodayRow.Suggestion) : TodayAction
    data class LogEnergy(val value: Int) : TodayAction
    data object AddHabit : TodayAction
    data object Refresh : TodayAction
}

@Composable
fun TodayScreen(
    state: TodayUiState,
    onAction: (TodayAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = SfTheme.motion

    // The orchestrated entrance (§8.4). Runs once per load rather than on
    // every recomposition, and is skipped entirely when motion is off -
    // staggering content in is a flourish, and flourishes are the first
    // thing to go when a user asks for less movement.
    var entered by remember(state.loading) { mutableStateOf(!motion.enabled) }
    LaunchedEffect(state.loading) {
        if (!state.loading) entered = true
    }

    if (state.loading) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = Space.BASE.dp),
        ) {
            Spacer(modifier = Modifier.height(Space.LG.dp))
            SfTodaySkeleton()
        }
        return
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val maxContent = 600.dp
        val horizPad = if (maxWidth > maxContent) (maxWidth - maxContent) / 2 else 0.dp
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = horizPad),
            contentPadding = PaddingValues(
                start = Space.BASE.dp,
                end = Space.BASE.dp,
                top = Space.SM.dp,
                bottom = Space.XXXL.dp + Space.XL.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(SfTheme.density.cardGap.dp),
        ) {
            todayRows(
                rows = state.rows,
                entered = entered,
                onAction = onAction,
            )
        }
    }
}

/**
 * Emits one lazy item per row.
 *
 * An extension on the lazy scope rather than a composable, so each row stays
 * a separate lazy item with its own key: wrapping them in a composable would
 * collapse the lot into one item and defeat recycling entirely.
 *
 * Named todayRows rather than itemsIndexed to avoid colliding with Compose's
 * own LazyListScope.itemsIndexed, which does something different.
 */
private fun LazyListScope.todayRows(
    rows: List<TodayRow>,
    entered: Boolean,
    onAction: (TodayAction) -> Unit,
) {
    rows.forEachIndexed { index, row ->
        item(key = row.stableId, contentType = row::class.simpleName) {
            TodayRowItem(
                row = row,
                index = index,
                entered = entered,
                onAction = onAction,
            )
        }
    }
}

@Composable
private fun TodayRowItem(
    row: TodayRow,
    index: Int,
    entered: Boolean,
    onAction: (TodayAction) -> Unit,
) {
    val motion = SfTheme.motion

    // The orchestrated sequence (§8.4): each row starts fractionally after
    // the one above, so the screen assembles downward rather than appearing
    // all at once. Motion.staggerDelay caps the delay after a handful of
    // items - past that the last row would arrive noticeably late, which
    // reads as jank rather than as choreography.
    val delay = motion.staggerDelay(index)

    AnimatedVisibility(
        visible = entered,
        enter = fadeIn(motion.tween(motion.normal, delayMs = delay)) +
            slideInVertically(motion.tween(motion.normal, delayMs = delay)) { it / 6 },
    ) {
        when (row) {
            is TodayRow.Progress -> ProgressBlock(row)
            is TodayRow.IdentityCard -> IdentityBlock(row)
            is TodayRow.Section -> SfSectionHeader(title = row.title)
            is TodayRow.HabitRow -> HabitBlock(row, onAction)
            is TodayRow.Empty -> EmptyBlock(row, onAction)
            is TodayRow.Load -> LoadBlock(row)
            is TodayRow.Returning -> ReturningBlock(row, onAction)
            is TodayRow.Focus -> FocusBlock(row, onAction)
            is TodayRow.Checkpoints -> EnergyBlock(row, onAction)
            is TodayRow.GrowthPlanStatus -> GrowthBlock(row)
            is TodayRow.Suggestion -> SuggestionBlock(row, onAction)
            else -> Unit
        }
    }
}

@Composable
private fun ProgressBlock(row: TodayRow.Progress) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Space.SM.dp)
            .semantics { heading() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.LG.dp),
    ) {
        SfProgressRing(done = row.done, total = row.total)
        Text(
            text = row.message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun IdentityBlock(row: TodayRow.IdentityCard) {
    SfCard(variant = SfCardVariant.Accent) {
        Text(
            text = row.statement,
            // Serif italic: the typographic signal that this is reflection
            // rather than data (§5.4).
            style = SfTheme.type.identity,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Spacer(modifier = Modifier.height(Space.SM.dp))
        Text(
            text = if (row.votes == 1) "1 vote" else "${row.votes} votes",
            style = SfTheme.type.data,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.semantics {
                contentDescription = "${row.votes} pieces of evidence for this identity"
            },
        )
    }
}

@Composable
private fun HabitBlock(row: TodayRow.HabitRow, onAction: (TodayAction) -> Unit) {
    val item = row.item
    val habit = item.habit

    SfHabitCard(
        title = habit.title,
        cue = habit.cueTime.ifBlank { habit.anchorText.ifBlank { null } },
        history = row.history,
        done = item.done,
        skipped = item.skipped,
        missed = item.missed,
        onClick = { onAction(TodayAction.OpenHabit(habit.id)) },
        onCheckIn = { level -> onAction(TodayAction.CheckIn(habit.id, level)) },
        onSkip = { onAction(TodayAction.Skip(habit.id)) },
        onUndo = { onAction(TodayAction.Undo(habit.id)) },
    )
}

@Composable
private fun EmptyBlock(row: TodayRow.Empty, onAction: (TodayAction) -> Unit) {
    SfCard(variant = SfCardVariant.Filled) {
        Text(
            text = row.title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(modifier = Modifier.height(Space.SM.dp))
        Text(
            text = row.body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (row.action != null) {
            Spacer(modifier = Modifier.height(Space.MD.dp))
            FilledTonalButton(
                onClick = { onAction(TodayAction.AddHabit) },
            ) {
                Text(row.action)
            }
        }
    }
}

@Composable
private fun LoadBlock(row: TodayRow.Load) {
    // Daily load indicator (§15). A quiet informational strip, not a card:
    // it is context for the list below, not an object to act on.
    val tint = when (row.color) {
        "green" -> MaterialTheme.colorScheme.primary
        "amber" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.XS.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.SM.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(tint, CircleShape),
        )
        Text(
            text = "${row.habits} habits · ~${row.minutes} min today",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReturningBlock(row: TodayRow.Returning, onAction: (TodayAction) -> Unit) {
    SfCard(
        variant = SfCardVariant.Warm,
        onClick = { row.habits.firstOrNull()?.let { onAction(TodayAction.OpenHabit(it.id)) } },
    ) {
        Text(
            text = if (row.habits.size == 1) "Ready to return?" else "Ready to return? (${row.habits.size})",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(modifier = Modifier.height(Space.XS.dp))
        Text(
            text = row.habits.joinToString(", ") { it.title },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

/**
 * The Focus card. Items arrive engine-ranked from the ViewModel (Plan B
 * F1.2): the one most worth doing next is the first checkbox, not buried.
 */
@Composable
private fun FocusBlock(row: TodayRow.Focus, onAction: (TodayAction) -> Unit) {
    SfCard(variant = SfCardVariant.Elevated) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Focus",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            Text(
                text = "${row.items.count { it.done }}/${row.items.size}",
                style = SfTheme.type.data,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(Space.SM.dp))
        row.items.forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = item.done,
                    onCheckedChange = { checked ->
                        onAction(TodayAction.ToggleFocus(item.id, checked))
                    },
                )
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (item.done) TextDecoration.LineThrough else null,
                    color = if (item.done) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onAction(TodayAction.RemoveFocus(item.id)) }) {
                    Icon(
                        painter = painterResource(com.superflow.R.drawable.ic_close),
                        contentDescription = "Remove ${item.title}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(Space.XS.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.SM.dp)) {
            TextButton(onClick = { onAction(TodayAction.FocusAdd) }) {
                Text("Add")
            }
            TextButton(onClick = { onAction(TodayAction.FocusSuggest) }) {
                Text("Suggest")
            }
        }
    }
}

/** Current-checkpoint energy logger (§15). Unlogged state is the default. */
@Composable
private fun EnergyBlock(row: TodayRow.Checkpoints, onAction: (TodayAction) -> Unit) {
    SfCard(variant = SfCardVariant.Filled) {
        Text(
            text = "How is your energy right now?",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(modifier = Modifier.height(Space.SM.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.SM.dp)) {
            for (value in 1..5) {
                val selected = row.energy == value
                FilledTonalButton(
                    onClick = { onAction(TodayAction.LogEnergy(value)) },
                    colors = if (selected) {
                        ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        ButtonDefaults.filledTonalButtonColors()
                    },
                ) {
                    Text("$value")
                }
            }
        }
    }
}

@Composable
private fun GrowthBlock(row: TodayRow.GrowthPlanStatus) {
    SfCard(variant = SfCardVariant.Filled) {
        Text(
            text = "${row.habitTitle} · ${row.phaseLabel}",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(modifier = Modifier.height(Space.SM.dp))
        LinearProgressIndicator(
            progress = { row.phaseIndex.toFloat() / row.totalPhases },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(Space.XS.dp))
        Text(
            text = "Phase ${row.phaseIndex} of ${row.totalPhases}",
            style = SfTheme.type.data,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SuggestionBlock(row: TodayRow.Suggestion, onAction: (TodayAction) -> Unit) {
    SfCard(
        variant = SfCardVariant.Warm,
        onClick = { onAction(TodayAction.SuggestionAction(row)) },
    ) {
        Text(
            text = row.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(modifier = Modifier.height(Space.XS.dp))
        Text(
            text = row.body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}
