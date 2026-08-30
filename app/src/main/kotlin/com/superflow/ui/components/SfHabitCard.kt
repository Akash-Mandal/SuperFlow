package com.superflow.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.superflow.data.model.Level
import com.superflow.design.Haptics
import com.superflow.design.Space
import com.superflow.design.rememberShouldReduceMotion
import com.superflow.ui.common.SfHaptics
import com.superflow.ui.theme.SfTheme
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The habit card (§11.1, §12.1).
 *
 * Carries the title, cue, level chips, an inline history strip, and a check
 * target. Swiping right checks in, swiping left skips.
 *
 * Two accessibility decisions worth stating. Swipe is an accelerator, never
 * the only route: every swipe action is also a custom accessibility action
 * and a tap target, because a gesture that is the sole way to complete a
 * habit is unusable with a screen reader and hard with a motor impairment.
 * And the card announces its state in words - "done", "rest day" - rather
 * than relying on the strikethrough and fade that convey it visually.
 *
 * @param onCheckIn  called with the level the user committed to
 * @param swipeEnabled mirrors the user's preference; when off the buttons
 *                     remain and only the gesture goes away
 */
@Composable
fun SfHabitCard(
    title: String,
    modifier: Modifier = Modifier,
    cue: String? = null,
    history: List<Int> = emptyList(),
    done: Boolean = false,
    skipped: Boolean = false,
    missed: Boolean = false,
    levels: List<Level> = emptyList(),
    selectedLevel: Level? = null,
    accentColor: Color? = null,
    swipeEnabled: Boolean = true,
    showHistory: Boolean = true,
    id: String = title,
    onClick: (() -> Unit)? = null,
    onCheckIn: ((Level) -> Unit)? = null,
    onSkip: (() -> Unit)? = null,
    onUndo: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val colors = SfTheme.colors
    val motion = SfTheme.motion
    val view = LocalView.current
    val density = LocalDensity.current

    // Swipe travel is capped well short of the card width: the gesture is a
    // shortcut, not a dismissal, and the card must never look like it is
    // about to leave the list.
    val maxTravelPx = with(density) { 88.dp.toPx() }
    val triggerPx = with(density) { 56.dp.toPx() }

    var offsetPx by remember(id) { mutableFloatStateOf(0f) }
    val settledOffset by animateFloatAsState(
        targetValue = offsetPx,
        animationSpec = motion.spring(),
        label = "swipeOffset",
    )

    // Latches when the swipe passes the trigger, so the confirming tick
    // fires once per crossing rather than once per frame.
    var armed by remember(id) { mutableStateOf(false) }

    val settled = done || skipped
    val defaultLevel = selectedLevel ?: levels.firstOrNull() ?: Level.STANDARD

    val stateWord = when {
        done -> "Done"
        skipped -> "Rest day"
        missed -> "Missed"
        else -> "Not yet"
    }

    val actions = buildList {
        if (!settled && onCheckIn != null) {
            add(CustomAccessibilityAction("Check in") { onCheckIn(defaultLevel); true })
        }
        if (!settled && onSkip != null) {
            add(CustomAccessibilityAction("Skip today") { onSkip(); true })
        }
        if (settled && onUndo != null) {
            add(CustomAccessibilityAction("Undo") { onUndo(); true })
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        // The action revealed behind the card as it slides.
        if (offsetPx != 0f) {
            SwipeBackdrop(
                offset = settledOffset,
                trigger = triggerPx,
                checkColor = colors.success,
                skipColor = colors.stateSkipped,
                onSurface = scheme.onSurface,
            )
        }

        SfCard(
            modifier = Modifier
                .offset { IntOffset(settledOffset.roundToInt(), 0) }
                .then(
                    if (swipeEnabled && !settled) {
                        Modifier.pointerInput(title, settled) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    when {
                                        offsetPx >= triggerPx && onCheckIn != null -> {
                                            SfHaptics.perform(view, Haptics.COMPLETE)
                                            onCheckIn(defaultLevel)
                                        }
                                        offsetPx <= -triggerPx && onSkip != null -> {
                                            SfHaptics.perform(view, Haptics.DROP)
                                            onSkip()
                                        }
                                    }
                                    offsetPx = 0f
                                    armed = false
                                },
                                onDragCancel = {
                                    offsetPx = 0f
                                    armed = false
                                },
                            ) { change, dragAmount ->
                                change.consume()
                                val next = (offsetPx + dragAmount)
                                    .coerceIn(-maxTravelPx, maxTravelPx)
                                offsetPx = next
                                // Tick once when the threshold is crossed, so
                                // the user knows the action will fire without
                                // having to watch the screen.
                                val crossed = abs(next) >= triggerPx
                                if (crossed && !armed) {
                                    SfHaptics.perform(view, Haptics.THRESHOLD)
                                    armed = true
                                } else if (!crossed) {
                                    armed = false
                                }
                            }
                        }
                    } else {
                        Modifier
                    }
                )
                .semantics {
                    contentDescription = buildString {
                        append(title)
                        append(", ")
                        append(stateWord)
                        if (cue != null) {
                            append(", ")
                            append(cue)
                        }
                    }
                    if (actions.isNotEmpty()) customActions = actions
                },
            variant = SfCardVariant.Elevated,
            accentColor = accentColor,
            onClick = onClick,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.MD.dp),
            ) {
                SfCheckTarget(
                    checked = done,
                    skipped = skipped,
                    enabled = onCheckIn != null,
                    onToggle = {
                        if (done || skipped) onUndo?.invoke() else onCheckIn?.invoke(defaultLevel)
                    },
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = scheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        // Strikethrough plus fade marks a completed habit,
                        // but never alone - the state is in the description
                        // too, for anyone who cannot see either.
                        textDecoration = if (done) TextDecoration.LineThrough else null,
                        modifier = Modifier.alpha(if (settled) 0.6f else 1f),
                    )
                    if (!cue.isNullOrBlank()) {
                        Text(
                            text = cue,
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            if (showHistory && history.isNotEmpty()) {
                Spacer(modifier = Modifier.height(Space.MD.dp))
                SfHistoryStrip(states = history)
            }

            if (levels.size > 1 && !settled) {
                Spacer(modifier = Modifier.height(Space.MD.dp))
                SfChipGroup(
                    chips = levels.map { SfChip(id = it.name, label = it.label) },
                    selected = setOfNotNull(selectedLevel?.name),
                    singleSelect = true,
                    groupLabel = "commitment level",
                    onSelectionChange = { picked ->
                        val level = levels.firstOrNull { it.name in picked }
                        if (level != null) onCheckIn?.invoke(level)
                    },
                )
            }
        }
    }
}

/**
 * The circular check target.
 *
 * 44dp of touch area around a 24dp circle. The check mark draws itself in
 * with a spring rather than appearing instantly, which is the single most
 * satisfying animation in the app and the one worth spending frames on.
 */
@Composable
private fun SfCheckTarget(
    checked: Boolean,
    skipped: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val colors = SfTheme.colors
    val motion = SfTheme.motion
    val view = LocalView.current

    val fill by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = motion.bouncy(),
        label = "checkFill",
    )
    val ringWidth by animateDpAsState(
        targetValue = if (checked) 0.dp else 2.dp,
        animationSpec = motion.tween(motion.quick),
        label = "checkRing",
    )
    val shouldReduce = rememberShouldReduceMotion()
    val bloom by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = if (shouldReduce) snap() else if (checked) motion.springSnappy() else snap(),
        label = "bloom",
    )

    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .then(
                if (enabled) {
                    Modifier.clickable {
                        SfHaptics.perform(
                            view,
                            if (checked) Haptics.UNDO else Haptics.COMPLETE
                        )
                        onToggle()
                    }
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(26.dp)) {
            val radius = size.minDimension / 2f

            if (bloom > 0.01f && bloom < 1f) {
                drawCircle(
                    color = colors.success.copy(alpha = (1f - bloom) * 0.18f),
                    radius = radius * (1f + bloom * 0.9f),
                )
            }
            if (fill > 0f) {
                drawCircle(color = colors.success, radius = radius * fill)
            }
            if (ringWidth > 0.dp) {
                drawCircle(
                    color = if (skipped) colors.stateSkipped else scheme.outline,
                    radius = radius - ringWidth.toPx() / 2f,
                    style = Stroke(width = ringWidth.toPx()),
                )
            }

            // The tick, drawn as two strokes scaled by the same spring, so
            // it grows out of the filling circle rather than popping on top.
            if (fill > 0.35f) {
                val t = ((fill - 0.35f) / 0.65f).coerceIn(0f, 1f)
                val w = size.width
                val start = Offset(w * 0.28f, w * 0.52f)
                val mid = Offset(w * 0.44f, w * 0.68f)
                val end = Offset(w * 0.74f, w * 0.34f)
                val stroke = Stroke(width = w * 0.1f, cap = StrokeCap.Round)

                drawLine(
                    color = colors.onSuccess,
                    start = start,
                    end = Offset(
                        start.x + (mid.x - start.x) * t.coerceAtMost(0.5f) * 2f,
                        start.y + (mid.y - start.y) * t.coerceAtMost(0.5f) * 2f,
                    ),
                    strokeWidth = stroke.width,
                    cap = StrokeCap.Round,
                )
                if (t > 0.5f) {
                    val t2 = (t - 0.5f) * 2f
                    drawLine(
                        color = colors.onSuccess,
                        start = mid,
                        end = Offset(
                            mid.x + (end.x - mid.x) * t2,
                            mid.y + (end.y - mid.y) * t2,
                        ),
                        strokeWidth = stroke.width,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
    }
}

/** The coloured action revealed as the card slides. */
@Composable
private fun SwipeBackdrop(
    offset: Float,
    trigger: Float,
    checkColor: Color,
    skipColor: Color,
    onSurface: Color,
) {
    val checking = offset > 0
    val progress = (abs(offset) / trigger).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(SfTheme.shapes.card)
            .background((if (checking) checkColor else skipColor).copy(alpha = 0.18f * progress)),
        contentAlignment = if (checking) Alignment.CenterStart else Alignment.CenterEnd,
    ) {
        AnimatedVisibility(
            visible = progress > 0.4f,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Text(
                text = if (checking) "Done" else "Rest",
                style = MaterialTheme.typography.labelLarge,
                color = onSurface,
                modifier = Modifier.padding(horizontal = Space.LG.dp),
            )
        }
    }
}
