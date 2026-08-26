package com.superflow.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.superflow.design.Space
import kotlinx.coroutines.delay

/**
 * The Command Palette (ALPHA3_VISUAL_PLAN §10.11).
 *
 * One surface for "where is it" and "do it": a search field over every
 * entity plus quick actions. Deliberately dependency-free - callers hand in
 * already-computed [results] and render them via [result], so the palette
 * never needs to know what an entity is or how one opens.
 *
 * Presentation: GlassMat-style - a 92% surface over the dimmed window.
 * A real backdrop blur needs a window-level RenderEffect; the near-opaque
 * surface reads almost identically and works on every API level.
 *
 * Typing is debounced upstream: recomposition from [query] changes is cheap,
 * but searching is the caller's job per keystroke or per debounce as suits.
 */
@Composable
fun <T> SfCommandPalette(
    onDismiss: () -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    results: List<T>,
    resultKey: (T) -> Any,
    resultContent: @Composable (T) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search or type a command",
    quickActions: @Composable ColumnScope.() -> Unit = {},
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = Space.MD.dp, vertical = Space.XL.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            tonalElevation = 3.dp,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = Space.BASE.dp, end = Space.SM.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(com.superflow.R.drawable.ic_search),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val requester = remember { FocusRequester() }
                    TextField(
                        value = query,
                        onValueChange = onQueryChange,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(requester)
                            .semantics { contentDescription = "Command palette search" },
                        placeholder = { Text(placeholder) },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            painter = painterResource(com.superflow.R.drawable.ic_close),
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // Focus lands in the field as the dialog attaches, so
                    // typing starts immediately - the point of a pull-down
                    // palette.
                    LaunchedEffect(Unit) {
                        delay(80) // let the window attach first
                        requester.requestFocus()
                    }
                }

                if (query.isBlank()) {
                    Column(
                        modifier = Modifier.padding(horizontal = Space.BASE.dp),
                    ) {
                        Text(
                            text = "Quick actions",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(Space.SM.dp))
                        quickActions()
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        horizontal = Space.BASE.dp,
                        vertical = Space.SM.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(Space.XXS.dp),
                ) {
                    items(results, key = resultKey) { item ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        ) {
                            resultContent(item)
                        }
                    }
                }
            }
        }
    }
}
