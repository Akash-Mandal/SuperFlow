package com.superflow.ui.inbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.superflow.data.model.CaptureKind
import com.superflow.data.model.CapturedItem
import com.superflow.design.Space

/**
 * The capture inbox (Plan B F1.1): triage for thoughts captured from the
 * command palette, the share sheet, or voice.
 *
 * One item, one decision at a time. Conversions are deliberately narrow -
 * journal entry or today's focus - because the point of triage is that it is
 * fast. "Design habit" hands the text to the Habit Designer and leaves the
 * item open until the user decides there; nothing converts itself.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxSheet(
    items: List<CapturedItem>,
    onDismiss: () -> Unit,
    onToJournal: (CapturedItem) -> Unit,
    onPinToToday: (CapturedItem) -> Unit,
    onDesignHabit: (CapturedItem) -> Unit,
    onDiscard: (CapturedItem) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = Space.BASE.dp)) {
            Text(
                text = if (items.size == 1) "1 captured thought"
                else "${items.size} captured thoughts",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = "Decide what each becomes. Nothing is deleted.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(Space.SM.dp))

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Space.MD.dp),
            ) {
                items(items, key = { it.id }) { item ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            KindLabel(item.kind)
                        }
                        Spacer(modifier = Modifier.height(Space.XXS.dp))
                        Text(
                            text = item.text,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.height(Space.XS.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.XS.dp)) {
                            FilledTonalButton(onClick = { onToJournal(item) }) {
                                Text("Journal")
                            }
                            FilledTonalButton(onClick = { onPinToToday(item) }) {
                                Text("Pin to today")
                            }
                            TextButton(onClick = { onDesignHabit(item) }) {
                                Text("Design habit")
                            }
                            TextButton(onClick = { onDiscard(item) }) {
                                Text("Discard")
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(Space.XL.dp))
        }
    }
}

@Composable
private fun KindLabel(kind: CaptureKind) {
    val label = when (kind) {
        CaptureKind.IDEA -> "Idea"
        CaptureKind.HABIT_CANDIDATE -> "Habit candidate"
        CaptureKind.GOAL_CANDIDATE -> "Goal candidate"
        CaptureKind.WORRY -> "Worry"
        CaptureKind.NOTE -> "Note"
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}
