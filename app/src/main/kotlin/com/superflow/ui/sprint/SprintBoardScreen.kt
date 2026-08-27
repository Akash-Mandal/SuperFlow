package com.superflow.ui.sprint

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.superflow.core.time.SfTime
import com.superflow.data.model.Sprint
import com.superflow.data.model.SprintStatus
import com.superflow.design.Space
import com.superflow.ui.components.SfCard
import com.superflow.ui.components.SfCardVariant
import com.superflow.ui.components.SfProgressRing
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Composable
fun SprintBoardScreen(
    sprints: List<Sprint>,
    onSelect: (Sprint) -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = sprints.firstOrNull { it.status == SprintStatus.ACTIVE }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Space.BASE.dp),
        verticalArrangement = Arrangement.spacedBy(Space.MD.dp),
    ) {
        if (active != null) {
            ActiveSprintHero(sprint = active, onSelect = { onSelect(active) })
        } else {
            SfCard(variant = SfCardVariant.Filled) {
                Text(
                    text = "No active sprint",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = "Start a 7–30 day commitment to focus on a few habits together.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (sprints.isNotEmpty()) {
            Text(
                text = "All sprints",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
        }

        sprints.forEach { sprint ->
            SfCard(
                variant = if (sprint.status == SprintStatus.ACTIVE) SfCardVariant.Elevated else SfCardVariant.Outlined,
                onClick = { onSelect(sprint) },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = sprint.title,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "${sprint.startDate} → ${sprint.endDate} · ${sprint.status.name.lowercase()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (sprint.status == SprintStatus.ACTIVE) {
                        val total = daysBetween(sprint.startDate, sprint.endDate).coerceAtLeast(1)
                        val elapsed = daysBetween(sprint.startDate, SfTime.format(java.time.LocalDate.now())).coerceIn(0, total)
                        Text(
                            text = "${total - elapsed}d left",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.semantics { contentDescription = "${total - elapsed} days remaining" },
                        )
                    }
                }
                if (sprint.focusHabits.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(Space.XS.dp))
                    Text(
                        text = sprint.focusHabits.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ActiveSprintHero(sprint: Sprint, onSelect: () -> Unit) {
    val total = daysBetween(sprint.startDate, sprint.endDate).coerceAtLeast(1)
    val elapsed = daysBetween(sprint.startDate, SfTime.format(LocalDate.now())).coerceIn(0, total)
    val fraction = elapsed.toFloat() / total

    SfCard(variant = SfCardVariant.Elevated, onClick = onSelect) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.LG.dp),
        ) {
            SfProgressRing(done = elapsed, total = total)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = sprint.title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = "Day ${elapsed + 1} of $total",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${(fraction * 100).toInt()}% elapsed",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

private fun daysBetween(fromIso: String, toIso: String): Int {
    val from = SfTime.parseDate(fromIso) ?: return 0
    val to = SfTime.parseDate(toIso) ?: return 0
    return ChronoUnit.DAYS.between(from, to).toInt().coerceAtLeast(0)
}
