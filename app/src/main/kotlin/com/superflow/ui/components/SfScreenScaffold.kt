package com.superflow.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.superflow.design.Space
import com.superflow.ui.theme.SfTheme

/**
 * The shared screen anatomy (ALPHA3_VISUAL_PLAN §3.1, §7.3).
 *
 * Every screen composes its header from this scaffold so that title,
 * subtitle, actions and hero content land in the same place with the same
 * rhythm everywhere - which is what makes the app feel like one product
 * rather than a collection of screens.
 *
 * Choreography: the header fades in and rises 12dp on first composition;
 * [hero] and [content] render immediately (they carry their own entrance
 * treatments where a screen wants them). Under reduced motion the header
 * simply appears.
 *
 * The scaffold is inset-aware: callers inside an edge-to-edge activity pass
 * nothing and get status bar padding; the content slot receives the padding
 * it must consume via [contentPadding] rather than padding itself, so lazy
 * lists keep their own scroll state.
 */
@Composable
fun SfScreenScaffold(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    hero: (@Composable () -> Unit)? = null,
    contentPadding: PaddingValues =
        PaddingValues(horizontal = Space.BASE.dp, vertical = Space.MD.dp),
    content: @Composable () -> Unit,
) {
    val motion = SfTheme.motion

    // One-shot header choreography. `animateFloatAsState` over two states:
    // we start hidden-offset and settle immediately after composition. When
    // motion is disabled the spec snaps, which is the documented "no motion"
    // path - no separate branch needed at call sites.
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(motion.enabled) { entered = true }
    val progress by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = if (motion.enabled) {
            motion.springStandard()
        } else {
            snap()
        },
        label = "sfScaffoldHeaderEntrance",
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = progress
                    translationY = (1f - progress) * HEADER_SLIDE_DP
                }
                .padding(horizontal = Space.BASE.dp, vertical = Space.SM.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions,
                )
            }
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = Space.XXS.dp),
                )
            }
        }

        if (hero != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.BASE.dp, vertical = Space.SM.dp),
            ) {
                hero()
            }
        }

        // Content owns scrolling; the scaffold never scrolls itself, so a
        // screen's scroll state survives navigation round-trips.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            content()
        }
    }
}

/** Slide distance for the header entrance, in dp. */
private const val HEADER_SLIDE_DP = 12f

/**
 * List-hosting variant of the scaffold for screens whose whole body is one
 * scrollable list (the common case during the v3 migration). Header, actions
 * and hero behave identically; the list gets the remaining space and a
 * bottom inset sized to clear the navigation dock.
 */
@Composable
fun SfScreenScaffoldList(
    title: String,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    hero: (@Composable () -> Unit)? = null,
    dockClearance: Boolean = true,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    // Implemented by delegation: identical header block, list body. Kept as
    // a sibling API rather than a sealed variant so both read naturally at
    // call sites without mode flags.
    val motion = SfTheme.motion
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(motion.enabled) { entered = true }
    val progress by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = if (motion.enabled) motion.springStandard() else snap(),
        label = "sfScaffoldListHeaderEntrance",
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = progress
                    translationY = (1f - progress) * HEADER_SLIDE_DP
                }
                .padding(horizontal = Space.BASE.dp, vertical = Space.SM.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Row(verticalAlignment = Alignment.CenterVertically, content = actions)
            }
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = Space.XXS.dp),
                )
            }
        }

        if (hero != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.BASE.dp, vertical = Space.SM.dp),
            ) { hero() }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Space.BASE.dp,
                end = Space.BASE.dp,
                top = Space.SM.dp,
                bottom = (if (dockClearance) Space.XL else Space.BASE).dp,
            ),
            verticalArrangement = Arrangement.spacedBy(Space.SM.dp),
            content = content,
        )
    }
}
