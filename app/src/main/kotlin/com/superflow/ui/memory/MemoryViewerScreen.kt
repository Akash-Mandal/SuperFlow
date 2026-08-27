package com.superflow.ui.memory

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.superflow.core.time.SfTime
import com.superflow.data.model.AiMemory
import com.superflow.data.model.MemoryCategory
import com.superflow.design.Space
import com.superflow.ui.components.SfTimeline
import com.superflow.ui.components.SfTimelineEntry
import java.time.Instant
import java.time.ZoneId

@Composable
fun MemoryViewerScreen(
    memories: List<AiMemory>,
    onDelete: (AiMemory) -> Unit,
    modifier: Modifier = Modifier,
) {
    val entries = remember(memories) {
        memories.map { m ->
            val day = SfTime.humanDay(
                java.time.LocalDate.ofInstant(
                    Instant.ofEpochMilli(m.createdAt),
                    ZoneId.systemDefault(),
                ),
            )
            SfTimelineEntry(
                key = m.id,
                dayLabel = categoryLabel(m.category),
                title = m.content.take(120),
                subtitle = "importance ${m.importance} · accessed ${m.accessCount}×",
                timeLabel = SfTime.clockLabel(m.createdAt),
            )
        }
    }
    SfTimeline(
        entries = entries,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Space.SM.dp),
        emptyText = "No memories yet. As you chat with the assistant, it will remember preferences and patterns here — all visible and deletable.",
        onEntryClick = { e -> memories.find { it.id == e.key }?.let(onDelete) },
    )
}

private fun categoryLabel(c: MemoryCategory): String = when (c) {
    MemoryCategory.USER_PREFERENCE -> "Preference"
    MemoryCategory.USER_CONTEXT -> "Context"
    MemoryCategory.HABIT_PATTERN -> "Pattern"
    MemoryCategory.STRUGGLE -> "Struggle"
    MemoryCategory.ACHIEVEMENT -> "Achievement"
    MemoryCategory.GOAL -> "Goal"
    MemoryCategory.LIFE_EVENT -> "Life event"
}
