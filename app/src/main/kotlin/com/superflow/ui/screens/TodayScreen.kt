package com.superflow.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.superflow.R
import com.superflow.core.time.SfTime
import com.superflow.data.model.Level
import com.superflow.design.Space
import com.superflow.design.rememberShouldReduceMotion
import com.superflow.ui.components.SfCard
import com.superflow.ui.components.SfCardVariant
import com.superflow.ui.components.SfFlowLine
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
    val reduceMotion = rememberShouldReduceMotion() || !motion.enabled

    // The orchestrated entrance (§8.4). On low-end devices the list appears
    // immediately — staggering 10 habit cards reads as lag, not choreography,
    // and the AnimatedVisibility that hides rows until `entered` becomes the
    // "elements disappear" bug the user reported.
    var entered by remember(state.loading, reduceMotion) { mutableStateOf(reduceMotion) }
    LaunchedEffect(state.loading, reduceMotion) {
        if (reduceMotion) {
            entered = true
        } else if (!state.loading) {
            entered = true
        }
    }

    if (state.loading) {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Space.BASE.dp,
                end = Space.BASE.dp,
                top = Space.LG.dp,
                bottom = Space.XXXL.dp,
            ),
        ) {
            item {
                SfTodaySkeleton()
            }
        }
        return
    }

    if (reduceMotion) {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Space.BASE.dp,
                end = Space.BASE.dp,
                top = Space.SM.dp,
                bottom = Space.XXXL.dp + Space.XL.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(Space.SM.dp),
        ) {
            todayRows(
                state = state,
                rows = state.rows,
                entered = true,
                onAction = onAction,
                reduceMotion = true,
            )
        }
        return
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val maxContent = 600.dp
        val horizPad = if (maxWidth > maxContent) (maxWidth - maxContent) / 2 else 0.dp
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(horizontal = horizPad),
            contentPadding = PaddingValues(
                start = Space.BASE.dp,
                end = Space.BASE.dp,
                top = Space.SM.dp,
                bottom = Space.XXXL.dp + Space.XL.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(SfTheme.density.cardGap.dp),
        ) {
            todayRows(
                state = state,
                rows = state.rows,
                entered = entered,
                onAction = onAction,
                reduceMotion = reduceMotion,
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
    state: TodayUiState,
    rows: List<TodayRow>,
    entered: Boolean,
    onAction: (TodayAction) -> Unit,
    reduceMotion: Boolean,
) {
    rows.forEachIndexed { index, row ->
        item(key = row.stableId, contentType = row::class.simpleName) {
            TodayRowItem(
                state = state,
                row = row,
                index = index,
                entered = entered,
                onAction = onAction,
                reduceMotion = reduceMotion,
            )
        }
    }
}

@Composable
private fun TodayRowItem(
    state: TodayUiState,
    row: TodayRow,
    index: Int,
    entered: Boolean,
    onAction: (TodayAction) -> Unit,
    reduceMotion: Boolean,
) {
    if (reduceMotion) {
        when (row) {
            is TodayRow.Progress -> ProgressBlock(state = state, row = row)
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
        return
    }

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
            is TodayRow.Progress -> ProgressBlock(state = state, row = row)
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
private fun ProgressBlock(state: TodayUiState, row: TodayRow.Progress) {
    val scheme = MaterialTheme.colorScheme
    val nextAction = remember(state.rows) {
        state.rows.filterIsInstance<TodayRow.Focus>()
            .firstOrNull()?.items?.firstOrNull { !it.done }?.title
    }
    val greetingText = when (state.greeting) {
        com.superflow.core.time.Greeting.MORNING -> stringResource(R.string.good_morning)
        com.superflow.core.time.Greeting.AFTERNOON -> stringResource(R.string.good_afternoon)
        else -> stringResource(R.string.good_evening)
    }
    val dateLabel = try { SfTime.humanDay(state.date) } catch (_: Exception) { state.date.toString() }

    val context = LocalContext.current
    val livingAccentOn = com.superflow.data.Prefs.get(context).livingAccent
    val hour = java.time.LocalTime.now().hour
    val accent = if (livingAccentOn) com.superflow.design.tokens.LivingAccent.shift(scheme.primary, hour) else scheme.primary
    val gradient = Brush.linearGradient(
        colors = listOf(
            accent.copy(alpha = 0.12f),
            scheme.primaryContainer.copy(alpha = 0.18f),
        ),
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SfTheme.shapes.card)
            .background(gradient)
            .padding(Space.BASE.dp)
            .semantics { heading() },
    ) {
        Text(
            text = dateLabel,
            style = MaterialTheme.typography.labelLarge,
            color = scheme.onSurfaceVariant,
        )
        Text(
            text = greetingText,
            style = MaterialTheme.typography.headlineSmall,
            color = scheme.onSurface,
        )
        Spacer(modifier = Modifier.height(Space.MD.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.LG.dp),
        ) {
            SfProgressRing(done = row.done, total = row.total, size = 96, strokeWidth = 8)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (row.total <= 0) "A quiet day" else "${row.done} of ${row.total} done",
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSurface,
                )
                Text(
                    text = row.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
        if (nextAction != null) {
            Spacer(modifier = Modifier.height(Space.MD.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .background(scheme.surface.copy(alpha = 0.72f))
                    .clickable { /* focus card handles the action; this is affordance */ }
                    .padding(horizontal = Space.SM.dp, vertical = Space.SM.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.next_action, nextAction),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.focus),
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.primary,
                )
            }
        }
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
    val accent = habit.colorOverride?.let { androidx.compose.ui.graphics.Color(it) }

    SfHabitCard(
        title = habit.title,
        cue = habit.cueTime.ifBlank { habit.anchorText.ifBlank { null } },
        history = row.history,
        done = item.done,
        skipped = item.skipped,
        missed = item.missed,
        id = habit.id,
        onClick = { onAction(TodayAction.OpenHabit(habit.id)) },
        onCheckIn = { level -> onAction(TodayAction.CheckIn(habit.id, level)) },
        onSkip = { onAction(TodayAction.Skip(habit.id)) },
        onUndo = { onAction(TodayAction.Undo(habit.id)) },
    )
}

@Composable
private fun EmptyBlock(row: TodayRow.Empty, onAction: (TodayAction) -> Unit) {
    SfCard(variant = SfCardVariant.Filled) {
        SfFlowLine(
            modifier = Modifier.fillMaxWidth().height(24.dp),
            progress = 0.52f,
            hasMiss = false,
        )
        Spacer(modifier = Modifier.height(Space.SM.dp))
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
            text = stringResource(R.string.daily_load, row.habits, row.minutes),
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
            text = if (row.habits.size == 1) stringResource(R.string.ready_to_return)
            else stringResource(R.string.ready_to_return_count, row.habits.size),
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
                text = stringResource(R.string.focus),
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
                Text(stringResource(R.string.add))
            }
            TextButton(onClick = { onAction(TodayAction.FocusSuggest) }) {
                Text(stringResource(R.string.suggest))
            }
        }
    }
}

/** Current-checkpoint energy logger (§15). Unlogged state is the default. */
@Composable
private fun EnergyBlock(row: TodayRow.Checkpoints, onAction: (TodayAction) -> Unit) {
    SfCard(variant = SfCardVariant.Filled) {
        Text(
            text = stringResource(R.string.how_is_energy),
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
