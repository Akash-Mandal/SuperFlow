package com.superflow.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.superflow.design.Space

/**
 * The vertical timeline rail (ALPHA3_VISUAL_PLAN §10.12).
 *
 * One component for every "what happened, in order" surface: day replay,
 * AI memory, activity log, journey events. Entries arrive pre-sorted; this
 * component groups them under sticky day headers and draws the rail.
 *
 * Deliberately dumb about content: callers shape [SfTimelineEntry]s from
 * whatever they have. The rail is a reading structure, not a data model.
 */
data class SfTimelineEntry(
    val key: String,
    /** Day-group label, e.g. "Today" or "24 Aug". Entries sharing a label group together. */
    val dayLabel: String,
    val title: String,
    val subtitle: String? = null,
    /** Time within the day, e.g. "14:05". Optional. */
    val timeLabel: String? = null,
    /** Leading dot colour; defaults to the palette's primary. */
    val accent: Color? = null,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SfTimeline(
    entries: List<SfTimelineEntry>,
    modifier: Modifier = Modifier,
    onEntryClick: ((SfTimelineEntry) -> Unit)? = null,
    emptyText: String = "Nothing here yet.",
) {
    if (entries.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = emptyText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val groups = entries.groupBy { it.dayLabel }
        // Preserve arrival order of days; LinkedHashMap from groupBy does.
    val railColor = MaterialTheme.colorScheme.surfaceContainerHighest

    LazyColumn(modifier = modifier.fillMaxSize()) {
        groups.forEach { (day, items) ->
            stickyHeader(key = "day_$day") {
                Text(
                    text = day,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(
                            top = Space.SM.dp,
                            bottom = Space.XS.dp,
                            start = Space.BASE.dp + TIMELINE_GUTTER.dp,
                        ),
                )
            }
            items(items, key = { it.key }) { entry ->
                TimelineRow(entry, isLast = entry == items.last(), railColor, onEntryClick)
            }
        }
    }
}

@Composable
private fun TimelineRow(
    entry: SfTimelineEntry,
    isLast: Boolean,
    railColor: Color,
    onEntryClick: ((SfTimelineEntry) -> Unit)?,
) {
    val dotColor = entry.accent ?: MaterialTheme.colorScheme.primary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onEntryClick != null) {
                    Modifier.clickable { onEntryClick(entry) }
                } else {
                    Modifier
                }
            )
            .padding(start = Space.BASE.dp, end = Space.BASE.dp),
    ) {
        // The rail: a dot for this entry and a continuous line beneath it
        // unless this row closes its day group.
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .padding(top = SPACE_ROW_TOP.dp)
                    .size(DOT.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )
            if (!isLast) {
                Spacer(
                    modifier = Modifier
                        .width(RAIL.dp)
                        .height(ROW_MIN_HEIGHT.dp - DOT.dp - SPACE_ROW_TOP.dp)
                        .background(railColor),
                )
            }
        }

        Spacer(modifier = Modifier.width(Space.MD.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = if (isLast) 0.dp else Space.SM.dp)
                .semantics {
                    entry.subtitle?.let {
                        contentDescription = "${entry.title}. $it"
                    } ?: run { contentDescription = entry.title }
                },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (entry.timeLabel != null) {
                    Text(
                        text = entry.timeLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = Space.SM.dp),
                    )
                }
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!entry.subtitle.isNullOrBlank()) {
                Text(
                    text = entry.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Rail geometry, in dp. */
private const val DOT = 8
private const val RAIL = 2
private const val TIMELINE_GUTTER = 4   // aligns headers with dots, not rail edge
private const val ROW_MIN_HEIGHT = 56
private const val SPACE_ROW_TOP = 6
