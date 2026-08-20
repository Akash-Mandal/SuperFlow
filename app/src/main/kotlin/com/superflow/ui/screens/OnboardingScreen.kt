package com.superflow.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.superflow.R
import com.superflow.design.Accessibility
import com.superflow.design.Navigation
import com.superflow.design.OnboardingFlow
import com.superflow.design.Space
import com.superflow.ui.components.SfCard
import com.superflow.ui.components.SfCardVariant
import com.superflow.ui.components.SfChip
import com.superflow.ui.components.SfChipGroup
import com.superflow.ui.components.SfSectionHeader
import com.superflow.ui.components.SfTextField
import com.superflow.ui.theme.SfTheme

/** Everything the onboarding screen draws, as a plain value. */
data class OnboardingUiState(
    val step: OnboardingFlow.Step = OnboardingFlow.Step.WELCOME,
    val answers: OnboardingFlow.Answers = OnboardingFlow.Answers(),
    /** Non-null when the user tried to advance without the required field. */
    val blocker: String? = null,
    val busy: Boolean = false,
    /** Cycled example index, advanced by the host on a timer. */
    val exampleIndex: Int = 0,
    /** Life-area chips, id = enum name. */
    val lifeAreas: List<SfChip> = emptyList(),
    val widthClass: Navigation.WidthClass = Navigation.WidthClass.COMPACT,
)

/** Every user action leaves the screen through here. */
sealed interface OnboardingAction {
    data object Next : OnboardingAction
    data object Back : OnboardingAction
    data object Skip : OnboardingAction
    data class Edit(val field: String, val value: String) : OnboardingAction
    data class Reminder(val enabled: Boolean) : OnboardingAction
    data object PickTime : OnboardingAction
}

/**
 * Onboarding (§14) — six illustrated steps.
 *
 * Every decision about *what* the flow is — order, validation, labels,
 * progress, what a skip leaves behind — is in [OnboardingFlow] and tested.
 * This file only renders it, which is why the screen is mostly a `when`.
 *
 * The two rules that shaped it: nothing is written to storage until the last
 * step, and no question is asked twice in different words. The old flow
 * asked for a life area on its own screen and then asked for an identity
 * that implied it; that screen is gone and the chips live under the identity
 * field, where they are an aid rather than a gate.
 */
@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    onAction: (OnboardingAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val step = state.step
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .imePadding(),
    ) {
        ProgressLine(step)

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter,
        ) {
            val animate = SfTheme.motion.enabled
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    if (!animate) {
                        fadeIn(animationSpec = SfTheme.motion.tween(0)) togetherWith
                            fadeOut(animationSpec = SfTheme.motion.tween(0))
                    } else {
                        val forward = targetState.index >= initialState.index
                        val offset = if (forward) 1 else -1
                        (
                            slideInHorizontally { full -> offset * full / 6 } +
                                fadeIn(animationSpec = SfTheme.motion.tween(220))
                            ) togetherWith (
                            slideOutHorizontally { full -> -offset * full / 6 } +
                                fadeOut(animationSpec = SfTheme.motion.tween(160))
                            )
                    }
                },
                label = "step",
            ) { current ->
                StepBody(
                    step = current,
                    state = state,
                    onAction = onAction,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Space.LG.dp),
                )
            }
        }

        Footer(state = state, onAction = onAction)
    }
}

// ------------------------------------------------------------------ chrome

/**
 * The connected progress line (§14.2).
 *
 * One continuous line rather than six dots: dots read as "six things to
 * fill in", a line reads as "you are part of the way through something".
 * Decorative — the position is announced from the heading instead, so a
 * screen reader is not read a progress bar on every step.
 */
@Composable
private fun ProgressLine(step: OnboardingFlow.Step) {
    val target = OnboardingFlow.progress(step)
    val fraction by animateFloatAsState(
        targetValue = target,
        animationSpec = SfTheme.motion.tween(420),
        label = "progress",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.LG.dp, vertical = Space.MD.dp)
            .height(3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clearAndSetSemantics { },
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

@Composable
private fun Footer(state: OnboardingUiState, onAction: (OnboardingAction) -> Unit) {
    val step = state.step
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = Space.LG.dp, vertical = Space.MD.dp),
    ) {
        if (state.blocker != null) {
            Text(
                text = state.blocker,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = Space.SM.dp),
            )
        }
        Button(
            onClick = { onAction(OnboardingAction.Next) },
            enabled = !state.busy,
            modifier = Modifier
                .fillMaxWidth()
                .height(Accessibility.MIN_TARGET_DP.dp + 8.dp),
        ) {
            Text(OnboardingFlow.nextLabel(step))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Space.XS.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (OnboardingFlow.showsBack(step)) {
                TextButton(onClick = { onAction(OnboardingAction.Back) }) { Text("Back") }
            } else {
                Spacer(Modifier.width(1.dp))
            }
            if (OnboardingFlow.showsSkip(step)) {
                TextButton(onClick = { onAction(OnboardingAction.Skip) }) {
                    Text("Skip for now")
                }
            } else {
                Spacer(Modifier.width(1.dp))
            }
        }
    }
}

// ------------------------------------------------------------------- steps

@Composable
private fun StepBody(
    step: OnboardingFlow.Step,
    state: OnboardingUiState,
    onAction: (OnboardingAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val answers = state.answers
    Column(
        modifier = modifier.widthIn(max = Navigation.MAX_CONTENT_WIDTH.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Illustration(step)
        Spacer(Modifier.height(Space.LG.dp))
        Text(
            text = step.title,
            style = if (step == OnboardingFlow.Step.WELCOME) {
                SfTheme.type.display
            } else {
                MaterialTheme.typography.headlineSmall
            },
            modifier = Modifier.semantics {
                heading()
                contentDescription = OnboardingFlow.describeProgress(step)
            },
        )
        Spacer(Modifier.height(Space.XS.dp))
        Text(
            text = step.subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Space.XL.dp))

        when (step) {
            OnboardingFlow.Step.WELCOME -> WelcomeStep()
            OnboardingFlow.Step.IDENTITY -> IdentityStep(state, onAction)
            OnboardingFlow.Step.GOAL -> GoalStep(state, onAction)
            OnboardingFlow.Step.HABIT -> HabitStep(state, onAction)
            OnboardingFlow.Step.CUE -> CueStep(state, onAction)
            OnboardingFlow.Step.PREVIEW -> PreviewStep(answers)
        }

        Spacer(Modifier.height(Space.XXL.dp))
    }
}

/**
 * The step motif.
 *
 * Symbolic names resolve to vectors here and nowhere else, so the pure
 * layer never learns what an `R` is. An unmapped motif draws nothing rather
 * than crashing — a missing illustration must never cost someone their
 * first run.
 */
@Composable
private fun Illustration(step: OnboardingFlow.Step) {
    val res = when (step.illustration) {
        "seed" -> R.drawable.ic_sparkle
        "figure" -> R.drawable.ic_identity
        "horizon" -> R.drawable.ic_goal
        "mark" -> R.drawable.ic_check
        "clock" -> R.drawable.ic_notification
        "sunrise" -> R.drawable.ic_sun
        else -> null
    } ?: return
    Box(
        modifier = Modifier
            .padding(top = Space.XL.dp)
            .size(96.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(res),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(44.dp),
        )
    }
}

@Composable
private fun WelcomeStep() {
    Column(verticalArrangement = Arrangement.spacedBy(Space.MD.dp)) {
        Promise("Identity first", "Habits are votes. The point is who they make you.")
        Promise("Small by design", "A habit you can do on your worst day is the only one that survives.")
        Promise("Yours alone", "Everything is on this device unless you say otherwise.")
    }
}

@Composable
private fun Promise(title: String, detail: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            painter = painterResource(R.drawable.ic_check),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(18.dp),
        )
        Spacer(Modifier.width(Space.SM.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun IdentityStep(state: OnboardingUiState, onAction: (OnboardingAction) -> Unit) {
    val examples = OnboardingFlow.identityExamples
    val example = examples[state.exampleIndex % examples.size]
    Column {
        SfTextField(
            value = state.answers.identity,
            onValueChange = { onAction(OnboardingAction.Edit("identity", it)) },
            label = "I am…",
            placeholder = example,
            singleLine = false,
            minLines = 2,
            maxLength = 120,
        )
        Spacer(Modifier.height(Space.MD.dp))
        // The life area rides along with the identity instead of owning a
        // screen. Optional: an identity with no area is still an identity.
        SfSectionHeader(title = "Area (optional)")
        SfChipGroup(
            chips = state.lifeAreas,
            selected = setOfNotNull(state.answers.lifeArea.ifBlank { null }),
            onSelectionChange = { picked ->
                onAction(OnboardingAction.Edit("lifeArea", picked.firstOrNull().orEmpty()))
            },
            singleSelect = true,
            groupLabel = "Life area",
        )
    }
}

@Composable
private fun GoalStep(state: OnboardingUiState, onAction: (OnboardingAction) -> Unit) {
    val pair = OnboardingFlow.goalExamples[state.exampleIndex % OnboardingFlow.goalExamples.size]
    Column {
        SfTextField(
            value = state.answers.goal,
            onValueChange = { onAction(OnboardingAction.Edit("goal", it)) },
            label = "The outcome",
            placeholder = pair.first,
            maxLength = 120,
        )
        Spacer(Modifier.height(Space.MD.dp))
        // The why is optional but asked for on the same screen, because a
        // goal without one is the first thing abandoned in week three.
        SfTextField(
            value = state.answers.why,
            onValueChange = { onAction(OnboardingAction.Edit("why", it)) },
            label = "Why it matters (optional)",
            placeholder = pair.second,
            singleLine = false,
            minLines = 2,
            maxLength = 240,
        )
    }
}

@Composable
private fun HabitStep(state: OnboardingUiState, onAction: (OnboardingAction) -> Unit) {
    val tiny = OnboardingFlow.tinyStarts(state.answers.habit)
    Column {
        SfTextField(
            value = state.answers.habit,
            onValueChange = { onAction(OnboardingAction.Edit("habit", it)) },
            label = "The habit",
            placeholder = "Walk 10 minutes",
            maxLength = 120,
        )
        Spacer(Modifier.height(Space.MD.dp))
        SfSectionHeader(title = "Bad-day version")
        Text(
            text = "The version you do when everything has gone wrong. This is what keeps the streak honest.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Space.SM.dp))
        SfTextField(
            value = state.answers.tinyStart,
            onValueChange = { onAction(OnboardingAction.Edit("tinyStart", it)) },
            label = "Tiny start (optional)",
            placeholder = tiny.first(),
            maxLength = 120,
        )
        Spacer(Modifier.height(Space.SM.dp))
        SfChipGroup(
            chips = tiny.map { SfChip(id = it, label = it) },
            selected = setOfNotNull(state.answers.tinyStart.ifBlank { null }),
            onSelectionChange = { picked ->
                onAction(OnboardingAction.Edit("tinyStart", picked.firstOrNull().orEmpty()))
            },
            singleSelect = true,
            groupLabel = "Suggested tiny starts",
        )
    }
}

@Composable
private fun CueStep(state: OnboardingUiState, onAction: (OnboardingAction) -> Unit) {
    val answers = state.answers
    Column {
        SfCard(
            variant = SfCardVariant.Outlined,
            onClick = { onAction(OnboardingAction.PickTime) },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Time", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = answers.cueTime.ifBlank { "Not set" },
                        style = SfTheme.type.data,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(Space.MD.dp))
        SfTextField(
            value = answers.anchor,
            onValueChange = { onAction(OnboardingAction.Edit("anchor", it)) },
            label = "After I… (optional)",
            placeholder = "make coffee",
            maxLength = 120,
        )
        Spacer(Modifier.height(Space.MD.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Remind me", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "One notification at that time. Nothing else, ever.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = answers.reminder,
                onCheckedChange = { onAction(OnboardingAction.Reminder(it)) },
            )
        }
    }
}

/**
 * The last step renders their words as the card they will actually see.
 *
 * Not a mock: same shape, same type, same wording as the Today card. The
 * gap between "I filled in a form" and "this is my morning" is closed by
 * showing the morning.
 */
@Composable
private fun PreviewStep(answers: OnboardingFlow.Answers) {
    val preview = OnboardingFlow.preview(answers)
    Column {
        Text(
            text = preview.identity,
            style = SfTheme.type.identity,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Space.MD.dp))
        SfCard(variant = SfCardVariant.Elevated) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
                Spacer(Modifier.width(Space.MD.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(preview.habitTitle, style = MaterialTheme.typography.titleMedium)
                    Text(
                        preview.habitDetail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(Space.LG.dp))
        Text(
            text = preview.encouragement,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
