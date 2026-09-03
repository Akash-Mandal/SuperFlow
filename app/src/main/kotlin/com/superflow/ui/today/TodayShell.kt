package com.superflow.ui.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.superflow.R
import com.superflow.core.time.Greeting
import com.superflow.design.Space
import com.superflow.domain.SearchResult

/**
 * Shell-level UI for the Compose Today host (Plan A §9.1, §10.11): the slim
 * top bar - greeting, search, inbox - plus the command palette's result and
 * action rows. These belong to the fragment's composition, not the shared
 * [TodayRow] model, because the View renderer has its own header.
 */

@Composable
fun TodayTopBar(
    greeting: Greeting,
    openCaptures: Int,
    onSearch: () -> Unit,
    onInbox: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val greetingText = when (greeting) {
        Greeting.MORNING -> "Good morning"
        Greeting.AFTERNOON -> "Good afternoon"
        Greeting.EVENING -> "Good evening"
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Space.BASE.dp, vertical = Space.SM.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = greetingText,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        IconButton(onClick = onInbox) {
            Box {
                Icon(
                    painter = painterResource(R.drawable.ic_inbox),
                    contentDescription = "Captured thoughts",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (openCaptures > 0) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .semantics {
                                contentDescription = "$openCaptures captured thoughts"
                            },
                    ) { Text("$openCaptures") }
                }
            }
        }
        IconButton(onClick = onSearch) {
            Icon(
                painter = painterResource(R.drawable.ic_search),
                contentDescription = "Search",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** One tappable quick action row inside the palette. */
@Composable
fun PaletteAction(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = Space.SM.dp, vertical = Space.MD.dp),
    )
}

/** One search result row; the whole row opens its destination. */
@Composable
fun PaletteResultRow(
    result: SearchResult,
    onOpen: (SearchResult) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen(result) }
            .padding(horizontal = Space.MD.dp, vertical = Space.SM.dp),
    ) {
        Text(
            text = result.title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (result.subtitle.isNotBlank()) {
            Text(
                text = "${typeLabel(result.type)} · ${result.subtitle}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = typeLabel(result.type),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun typeLabel(type: String): String = when (type) {
    "habit" -> "Habit"
    "identity" -> "Identity"
    "goal" -> "Goal"
    "system" -> "System"
    "review" -> "Review"
    "journal" -> "Journal"
    "audit" -> "Activity"
    "obstacle" -> "Obstacle plan"
    else -> type.replaceFirstChar { it.uppercase() }
}
