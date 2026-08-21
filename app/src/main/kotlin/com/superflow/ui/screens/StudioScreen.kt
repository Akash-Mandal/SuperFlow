package com.superflow.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.superflow.R
import com.superflow.design.Space
import com.superflow.design.StudioModel
import com.superflow.ui.components.SfCard
import com.superflow.ui.components.SfCardVariant
import com.superflow.ui.components.SfSkeletonLine
import com.superflow.ui.components.SfTextField
import com.superflow.ui.theme.SfTheme

/** Everything the Studio screen draws, as a plain value. */
data class StudioUiState(
    val loading: Boolean = true,
    val rows: List<StudioModel.Row> = emptyList(),
    val input: String = "",
    val sending: Boolean = false,
    val typing: Boolean = false,
    val listening: Boolean = false,
    /** Normalised 0..1 bars, already smoothed by [StudioModel.waveform]. */
    val levels: List<Float> = emptyList(),
    val placeholder: String = "",
    val canSend: Boolean = false,
    val foldExpanded: Boolean = false,
)

/** Every user action leaves the screen through here. */
sealed interface StudioAction {
    data class Input(val text: String) : StudioAction
    data object Send : StudioAction
    data object Mic : StudioAction
    data object StopListening : StudioAction
    data object ExpandFold : StudioAction
    data object OpenStatus : StudioAction
    data class Quick(val id: String) : StudioAction
    data class Suggestion(val text: String) : StudioAction
    data class Message(val turnId: String, val action: StudioModel.MessageAction) : StudioAction
    data class OpenProject(val id: String) : StudioAction
}

/**
 * Studio (§11.4) — Coach, Blueprint and the AI Engine as one surface.
 *
 * The merge is the point. Three separate screens meant three separate
 * mental models for one capability: people asked the Coach to build a plan
 * and got a pep talk, then opened Blueprint and re-typed the same thing.
 * A single transcript with the tools reachable from it removes the guess.
 *
 * The transcript is a [LazyColumn] over pre-computed
 * [StudioModel.Row]s, so this file only decides what a row *looks* like —
 * ordering, folding, avatar runs and date breaks were all settled in the
 * pure layer and are covered by tests.
 *
 * Deliberately not a chat clone: there is no infinite scroll-to-load. Old
 * turns collapse behind one fold that states its size, because a transcript
 * that silently grows without bound is how people end up scrolling for
 * thirty seconds to find yesterday.
 */
@Composable
fun StudioScreen(
    state: StudioUiState,
    onAction: (StudioAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            if (state.loading) {
                StudioSkeleton()
            } else {
                StudioTranscript(state = state, onAction = onAction)
            }
        }
        StudioComposer(state = state, onAction = onAction)
    }
}

@Composable
private fun StudioTranscript(
    state: StudioUiState,
    onAction: (StudioAction) -> Unit,
) {
    val listState = rememberLazyListState()

    // Follow the tail only when new rows arrive, not on every recomposition,
    // so reading back through history is not yanked away by a status change.
    LaunchedEffect(state.rows.size, state.typing) {
        if (state.rows.isNotEmpty()) {
            listState.animateScrollToItem(state.rows.lastIndex)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Space.MD.dp,
            end = Space.MD.dp,
            top = Space.MD.dp,
            bottom = Space.LG.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(SfTheme.density.cardGap.dp),
    ) {
        state.rows.forEach { row -> studioRow(row, state, onAction) }
        if (state.typing) {
            item(key = "typing") { TypingRow() }
        }
    }
}

private fun LazyListScope.studioRow(
    row: StudioModel.Row,
    state: StudioUiState,
    onAction: (StudioAction) -> Unit,
) {
    when (row) {
        is StudioModel.Row.Status -> item(key = row.key) {
            StatusRow(row, onAction)
        }

        is StudioModel.Row.QuickActions -> item(key = row.key) {
            QuickActionRow(row, onAction)
        }

        is StudioModel.Row.DateBreak -> item(key = row.key) {
            DateBreakRow(row.label)
        }

        is StudioModel.Row.Message -> item(key = row.key) {
            MessageRow(row, onAction)
        }

        is StudioModel.Row.Project -> item(key = row.key) {
            ProjectRow(row, onAction)
        }

        is StudioModel.Row.Suggestions -> item(key = row.key) {
            SuggestionRow(row, onAction)
        }

        is StudioModel.Row.Coach -> item(key = row.key) {
            CoachRow(row.text)
        }

        is StudioModel.Row.OlderFold -> item(key = row.key) {
            FoldRow(row.hidden, state.foldExpanded, onAction)
        }
    }
}

// ------------------------------------------------------------------ rows

/**
 * The control-mode pill.
 *
 * States what the app may do on its own and which brain is answering,
 * because "who just changed my habit" is the single most alarming question
 * an agentic app can leave unanswered.
 */
@Composable
private fun StatusRow(row: StudioModel.Row.Status, onAction: (StudioAction) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    SfCard(
        variant = if (row.active) SfCardVariant.Accent else SfCardVariant.Outlined,
        onClick = { onAction(StudioAction.OpenStatus) },
        modifier = Modifier.semantics {
            contentDescription = "${row.title}. ${row.detail}. ${row.actionLabel}"
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(R.drawable.ic_sparkle),
                contentDescription = null,
                tint = if (row.active) scheme.primary else scheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(Space.SM.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = row.title, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = row.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            Text(
                text = row.actionLabel,
                style = MaterialTheme.typography.labelLarge,
                color = scheme.primary,
            )
        }
    }
}

@Composable
private fun QuickActionRow(
    row: StudioModel.Row.QuickActions,
    onAction: (StudioAction) -> Unit,
) {
    // The one place a horizontal scroll is right: these are shortcuts to
    // things you can also just type, so a chip past the edge costs nothing.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Space.SM.dp),
    ) {
        row.items.forEach { chip ->
            AssistChip(
                onClick = { onAction(StudioAction.Quick(chip.id)) },
                label = { Text(chip.label) },
                colors = AssistChipDefaults.assistChipColors(),
            )
        }
    }
}

@Composable
private fun DateBreakRow(label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Space.XS.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        HairLine(Modifier.weight(1f))
        Text(
            text = label,
            style = SfTheme.type.overline,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(horizontal = Space.SM.dp)
                .semantics { heading() },
        )
        HairLine(Modifier.weight(1f))
    }
}

@Composable
private fun HairLine(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

/**
 * One turn.
 *
 * User turns sit right and tinted; assistant turns sit left on the plain
 * surface. Both are width-capped well short of the screen — a full-bleed
 * line of text on a tablet is unreadable, and the ragged right edge is what
 * makes a transcript scannable at a glance.
 */
@Composable
private fun MessageRow(row: StudioModel.Row.Message, onAction: (StudioAction) -> Unit) {
    val turn = row.turn
    val scheme = MaterialTheme.colorScheme
    val mine = turn.speaker == StudioModel.Speaker.USER
    val system = turn.speaker == StudioModel.Speaker.SYSTEM

    if (system) {
        Text(
            text = turn.text,
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Space.XS.dp),
        )
        return
    }

    val chip = StudioModel.statusChip(turn)
    val actions = StudioModel.actionsFor(turn)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
    ) {
        Surface(
            color = if (mine) scheme.secondaryContainer else scheme.surfaceVariant,
            contentColor = if (mine) scheme.onSecondaryContainer else scheme.onSurface,
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (mine) 18.dp else 4.dp,
                bottomEnd = if (mine) 4.dp else 18.dp,
            ),
            modifier = Modifier.widthIn(max = 460.dp),
        ) {
            Column(modifier = Modifier.padding(SfTheme.density.cardPadding.dp)) {
                Text(text = turn.text, style = MaterialTheme.typography.bodyLarge)
                if (turn.meta.isNotBlank()) {
                    Spacer(Modifier.height(Space.XS.dp))
                    Text(
                        text = turn.meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (chip != null) {
            Text(
                text = chip,
                style = MaterialTheme.typography.labelSmall,
                color = when (turn.state) {
                    StudioModel.RunState.FAILED -> scheme.error
                    StudioModel.RunState.DONE -> scheme.primary
                    else -> scheme.onSurfaceVariant
                },
                modifier = Modifier
                    .padding(top = 2.dp, start = Space.XS.dp, end = Space.XS.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }

        if (actions.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.XS.dp)) {
                actions.forEach { action ->
                    TextButton(onClick = { onAction(StudioAction.Message(turn.id, action)) }) {
                        Text(action.label, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectRow(row: StudioModel.Row.Project, onAction: (StudioAction) -> Unit) {
    SfCard(
        variant = SfCardVariant.Outlined,
        onClick = { onAction(StudioAction.OpenProject(row.id)) },
    ) {
        Column {
            Text(text = row.name, style = MaterialTheme.typography.titleSmall)
            Text(
                text = row.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Space.SM.dp))
            LinearProgressIndicator(
                progress = { row.progress.coerceIn(0, 100) / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
            )
        }
    }
}

@Composable
private fun SuggestionRow(
    row: StudioModel.Row.Suggestions,
    onAction: (StudioAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.XS.dp)) {
        row.items.forEach { text ->
            SfCard(
                variant = SfCardVariant.Outlined,
                onClick = { onAction(StudioAction.Suggestion(text)) },
            ) {
                Text(text = text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun CoachRow(text: String) {
    SfCard(variant = SfCardVariant.Accent) {
        Text(text = text, style = SfTheme.type.identity)
    }
}

@Composable
private fun FoldRow(hidden: Int, expanded: Boolean, onAction: (StudioAction) -> Unit) {
    if (expanded) return
    TextButton(
        onClick = { onAction(StudioAction.ExpandFold) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = if (hidden == 1) "1 earlier message" else "$hidden earlier messages",
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

/**
 * Three dots, breathing.
 *
 * Not a spinner: a spinner says "the app is busy", this says "something is
 * about to say something", which is the truthful signal while a model
 * streams. Respects the motion setting — when animation is off the dots are
 * simply present, which still communicates the wait.
 */
@Composable
private fun TypingRow() {
    val scheme = MaterialTheme.colorScheme
    val animate = SfTheme.motion.enabled
    val transition = rememberInfiniteTransition(label = "typing")
    Row(
        modifier = Modifier
            .padding(start = Space.XS.dp, top = Space.XS.dp)
            .semantics { contentDescription = "Studio is replying" },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, delayMillis = index * 160, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot$index",
            )
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .alpha(if (animate) alpha else 0.7f)
                    .clip(RoundedCornerShape(50))
                    .background(scheme.onSurfaceVariant),
            )
        }
    }
}

// -------------------------------------------------------------- composer

/**
 * The input bar.
 *
 * Pinned above the keyboard, never inside the scrolling list — a composer
 * that scrolls away is the classic chat bug. The counter appears only near
 * the limit ([StudioModel.showCounter]); a character counter visible from
 * the first keystroke reads as a warning and shortens what people write.
 */
@Composable
private fun StudioComposer(
    state: StudioUiState,
    onAction: (StudioAction) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        color = scheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .imePadding()
                .navigationBarsPadding()
                .padding(
                    horizontal = Space.MD.dp,
                    vertical = Space.SM.dp,
                ),
        ) {
            if (state.listening) {
                Waveform(state.levels)
                Spacer(Modifier.height(Space.SM.dp))
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Box(modifier = Modifier.weight(1f)) {
                    SfTextField(
                        value = state.input,
                        onValueChange = { onAction(StudioAction.Input(it)) },
                        placeholder = state.placeholder,
                        singleLine = false,
                        minLines = 1,
                        supportingText = if (StudioModel.showCounter(state.input.length)) {
                            "${state.input.length} / ${StudioModel.MAX_INPUT}"
                        } else {
                            null
                        },
                        enabled = !state.sending,
                    )
                }
                Spacer(Modifier.width(Space.SM.dp))
                IconButton(
                    onClick = {
                        onAction(
                            if (state.listening) StudioAction.StopListening else StudioAction.Mic,
                        )
                    },
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = if (state.listening) scheme.error else scheme.onSurfaceVariant,
                    ),
                ) {
                    Icon(
                        painter = painterResource(
                            if (state.listening) R.drawable.ic_close else R.drawable.ic_mic,
                        ),
                        contentDescription = if (state.listening) "Stop listening" else "Speak",
                    )
                }
                FilledIconButton(
                    onClick = { onAction(StudioAction.Send) },
                    enabled = state.canSend && !state.sending,
                ) {
                    if (state.sending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = scheme.onPrimary,
                        )
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.ic_send),
                            contentDescription = "Send",
                        )
                    }
                }
            }
        }
    }
}

/**
 * Live microphone level.
 *
 * Bars never reach zero ([StudioModel.MIN_BAR]) — a waveform that flatlines
 * during a pause looks like the mic died, and people stop talking and start
 * tapping.
 */
@Composable
private fun Waveform(levels: List<Float>) {
    val color = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .semantics { contentDescription = "Listening" },
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        levels.forEach { level ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height((32f * level.coerceIn(StudioModel.MIN_BAR, 1f)).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color),
            )
        }
    }
}

@Composable
private fun StudioSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Space.MD.dp),
        verticalArrangement = Arrangement.spacedBy(Space.MD.dp),
    ) {
        SfSkeletonLine(widthFraction = 0.6f)
        SfSkeletonLine(widthFraction = 0.9f)
        SfSkeletonLine(widthFraction = 0.45f)
        SfSkeletonLine(widthFraction = 0.8f)
    }
}
