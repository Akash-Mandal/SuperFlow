package com.superflow.ui.replay

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.superflow.design.Space
import com.superflow.domain.DayReplay
import com.superflow.ui.components.SfTimeline
import com.superflow.ui.components.SfTimelineEntry

@Composable
fun DayReplayScreen(
    dateLabel: String,
    events: List<DayReplay.DayEvent>,
    modifier: Modifier = Modifier,
) {
    val entries = events.map { e ->
        SfTimelineEntry(
            key = "${e.kind.name}_${e.timestampMs}_${e.title.hashCode()}",
            dayLabel = dateLabel,
            title = e.title,
            subtitle = e.subtitle.takeIf { it.isNotBlank() },
            timeLabel = e.timeLabel,
            accent = accentFor(e.kind),
        )
    }
    SfTimeline(
        entries = entries,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Space.SM.dp),
        emptyText = "No events recorded for this day.",
    )
}

@Composable
private fun accentFor(kind: DayReplay.EventKind) = when (kind) {
    DayReplay.EventKind.CHECK_IN -> MaterialTheme.colorScheme.primary
    DayReplay.EventKind.MISS -> MaterialTheme.colorScheme.error
    DayReplay.EventKind.SKIP -> MaterialTheme.colorScheme.outline
    DayReplay.EventKind.FOCUS_DONE -> MaterialTheme.colorScheme.tertiary
    DayReplay.EventKind.ENERGY -> MaterialTheme.colorScheme.secondary
    DayReplay.EventKind.JOURNAL -> MaterialTheme.colorScheme.onSurfaceVariant
}
