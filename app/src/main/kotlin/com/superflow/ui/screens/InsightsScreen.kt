package com.superflow.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.superflow.design.ChartGeometry
import com.superflow.design.Periods
import com.superflow.design.Space
import com.superflow.ui.components.SfBar
import com.superflow.ui.components.SfBarChart
import com.superflow.ui.components.SfCard
import com.superflow.ui.components.SfCardVariant
import com.superflow.ui.components.SfChip
import com.superflow.ui.components.SfStatHero
import com.superflow.ui.components.SfChipGroup
import com.superflow.ui.components.SfHeatmap
import com.superflow.ui.components.SfSectionHeader
import com.superflow.ui.components.SfStatCardSkeleton
import com.superflow.ui.theme.SfTheme

/**
 * What the Insights screen needs to draw itself.
 *
 * A flat description rather than a sealed row list, because unlike Today
 * this screen's structure is fixed - the same sections in the same order,
 * with different data in them.
 */
data class InsightsUiState(
    val loading: Boolean = true,
    val periodId: Int = Periods.MONTH_ID,
    /** Daily completion fractions, oldest first, unbucketed. */
    val daily: List<Double> = emptyList(),
    /** Day states for the heatmap, in the HistoryStates encoding. */
    val heatmap: List<Int> = emptyList(),
    val firstWeekday: Int = 0,
    val perHabit: List<HabitConsistency> = emptyList(),
    /** Energy rating paired with that day's completion fraction. */
    val energyPairs: List<Pair<Double, Double>> = emptyList(),
)

data class HabitConsistency(
    val id: String,
    val title: String,
    val percent: Int,
    val samples: Int,
)

/**
 * The Insights screen (§11.3).
 *
 * Every number on this screen is gated by [Periods.MinSamples]. A habit
 * tracker that reports "you are 40% more consistent in the mornings" from
 * four data points is not being helpful, it is generating plausible noise,
 * and the user has no way to tell the difference. So claims carry their
 * sample size, and below the threshold the screen says so plainly instead
 * of showing a number.
 */
@Composable
fun InsightsScreen(
    state: InsightsUiState,
    onPeriodChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val period = Periods.byId(state.periodId)

    if (state.loading) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(Space.BASE.dp),
            verticalArrangement = Arrangement.spacedBy(SfTheme.density.cardGap.dp),
        ) {
            repeat(3) { SfStatCardSkeleton() }
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
        item(key = "period") {
            SfChipGroup(
                chips = Periods.all.map { SfChip(id = it.id.toString(), label = it.label) },
                selected = setOf(state.periodId.toString()),
                singleSelect = true,
                groupLabel = "time period",
                onSelectionChange = { picked ->
                    picked.firstOrNull()?.toIntOrNull()?.let(onPeriodChange)
                },
            )
        }

        item(key = "heroStats") { HeroStatsRow(state = state, period = period) }

        item(key = "consistency") {
            ConsistencyCard(state = state, period = period)
        }

        if (state.heatmap.isNotEmpty()) {
            item(key = "heatmapHeader") { SfSectionHeader(title = "Rhythm") }
            item(key = "heatmap") {
                SfCard(variant = SfCardVariant.Filled) {
                    SfHeatmap(
                        states = state.heatmap.takeLast(period.days),
                        firstWeekday = state.firstWeekday,
                    )
                }
            }
        }

        if (state.energyPairs.isNotEmpty()) {
            item(key = "energyHeader") { SfSectionHeader(title = "Energy") }
            item(key = "energy") { EnergyCard(pairs = state.energyPairs) }
        }

        if (state.perHabit.isNotEmpty()) {
            item(key = "habitsHeader") { SfSectionHeader(title = "Per habit") }
            habitRows(state.perHabit)
        }
        }
    }
}

/** Named habitRows to avoid colliding with Compose's LazyListScope.items. */
private fun LazyListScope.habitRows(habits: List<HabitConsistency>) {
    for (habit in habits) {
        item(key = "habit_${habit.id}", contentType = "HabitConsistency") { HabitConsistencyRow(habit) }
    }
}

/**
 * The completion chart for the selected period.
 *
 * Wrapped in AnimatedContent so switching period cross-fades rather than
 * snapping - the two charts are the same data at different resolutions, and
 * a hard cut makes them look unrelated.
 */
@Composable
private fun ConsistencyCard(state: InsightsUiState, period: Periods.Period) {
    val windowed = Periods.window(state.daily, period)
    val bucketed = Periods.bucket(windowed, period)
    val samples = windowed.size

    SfCard(variant = SfCardVariant.Filled) {
        Text(
            text = "Consistency",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )

        val caveat = Periods.caveatFor(samples, Periods.MinSamples.COMPLETION_RATE)
        if (caveat != null) {
            Text(
                text = caveat,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(Space.MD.dp))

        // An explicit branch rather than an early return: a `return@SfCard`
        // inside a Compose trailing lambda works, but it is easy to
        // misattribute when the lambda is later wrapped in another scope.
        if (!Periods.canClaim(samples, Periods.MinSamples.COMPLETION_RATE)) {
            Text(
                text = "Keep going — a few more days and there will be something to see here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            // transitionSpec is not a @Composable lambda, so the motion specs
            // have to be read here and captured.
            val motion = SfTheme.motion
            AnimatedContent(
                targetState = period.id,
                transitionSpec = {
                    fadeIn(motion.tween(motion.quick)) togetherWith
                        fadeOut(motion.tween(motion.fast))
                },
                label = "periodSwitch",
            ) { _ ->
                SfBarChart(
                    bars = bucketed.mapIndexed { index, value ->
                        SfBar(label = "${index + 1}", value = value * 100)
                    },
                    valueFormat = { "${it.toInt()}%" },
                    label = "Completion over ${period.label}",
                )
            }

            val mean = if (windowed.isEmpty()) 0.0 else windowed.sum() / windowed.size
            Spacer(modifier = Modifier.height(Space.SM.dp))
            Text(
                text = "${ChartGeometry.percent(mean)}% average over $samples days",
                style = SfTheme.type.data,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Energy against completion.
 *
 * Reports a correlation only when [ChartGeometry.correlation] can compute
 * one and the sample clears the threshold, and describes it as a tendency
 * rather than a cause. Energy and completion plausibly drive each other in
 * both directions, and the data cannot distinguish them.
 */
@Composable
private fun EnergyCard(pairs: List<Pair<Double, Double>>) {
    val r = ChartGeometry.correlation(pairs.map { it.first }, pairs.map { it.second })
    val description = ChartGeometry.correlationLabel(r, pairs.size)

    SfCard(variant = SfCardVariant.Filled) {
        Text(
            text = "Energy and completion",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(modifier = Modifier.height(Space.SM.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (r != null && Periods.canClaim(pairs.size, Periods.MinSamples.CORRELATION)) {
            Spacer(modifier = Modifier.height(Space.XS.dp))
            Text(
                // Stated as an association, never as advice. Suggesting the
                // user "should" do anything from a personal correlation of
                // 30 points would be overreach.
                text = "Days you rated higher energy tended to be days you " +
                    "completed more. Based on ${pairs.size} days.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HeroStatsRow(state: InsightsUiState, period: Periods.Period) {
    val windowed = Periods.window(state.daily, period)
    if (!Periods.canClaim(windowed.size, Periods.MinSamples.COMPLETION_RATE)) return
    val mean = windowed.average()
    val series = windowed.map { (it * 100).toFloat() }
    val half = windowed.size / 2
    val firstMean = windowed.take(half).average().takeIf { !it.isNaN() } ?: 0.0
    val secondMean = windowed.drop(half).average().takeIf { !it.isNaN() } ?: 0.0
    val delta: Float? = if (firstMean > 0.01) ((secondMean - firstMean) / firstMean).toFloat() else null

    SfCard(variant = SfCardVariant.Filled) {
        SfStatHero(
            value = "${ChartGeometry.percent(mean)}%",
            label = "Average completion",
            deltaFraction = delta,
            comparisonLabel = if (delta != null) "vs first half of period" else null,
            series = series,
        )
    }
}

@Composable
private fun HabitConsistencyRow(habit: HabitConsistency) {
    val enough = Periods.canClaim(habit.samples, Periods.MinSamples.COMPLETION_RATE)

    SfCard(variant = SfCardVariant.Outlined) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = if (enough) {
                        "${habit.title}, ${habit.percent} percent over ${habit.samples} days"
                    } else {
                        "${habit.title}, not enough data yet"
                    }
                },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = habit.title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (enough) "${habit.percent}%" else "—",
                style = SfTheme.type.data,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
