package com.superflow.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.superflow.R
import com.superflow.design.JourneyTree
import com.superflow.design.Space
import com.superflow.ui.components.SfCard
import com.superflow.ui.components.SfCardVariant
import com.superflow.ui.components.SfEntityRow
import com.superflow.ui.components.SfHabitCardSkeleton
import com.superflow.ui.components.SfSectionHeader
import com.superflow.ui.theme.SfTheme

/** Everything the Journey screen draws, as a plain value. */
data class JourneyUiState(
    val loading: Boolean = true,
    val nodes: List<JourneyTree.Node> = emptyList(),
    val expanded: Set<String> = emptySet(),
)

/** Every user action leaves the screen through here. */
sealed interface JourneyAction {
    data class Toggle(val kind: JourneyTree.Kind, val id: String) : JourneyAction
    data class Open(val kind: JourneyTree.Kind, val id: String) : JourneyAction
    data class Menu(val kind: JourneyTree.Kind, val id: String) : JourneyAction
    data class Add(val kind: JourneyTree.Kind, val parentId: String?) : JourneyAction
    /** 0 scorecard, 1 flows, 2 review — matching the existing fragment. */
    data class Tool(val which: Int) : JourneyAction
}

/**
 * The Journey screen (§11.2).
 *
 * The chain is the content. Where the old screen stacked four independent
 * lists under four headers — which reads as four unrelated features — this
 * draws one tree, so a habit is visibly the thing that a system runs on,
 * which is visibly what a goal needs, which is visibly downstream of an
 * identity. That is the app's entire argument and it was previously
 * invisible on the screen meant to make it.
 *
 * Structure comes from [JourneyTree]; this file only decides what things
 * look like. In particular, the screen never filters: anything the user
 * created is on it somewhere, including entities whose links are broken.
 */
@Composable
fun JourneyScreen(
    state: JourneyUiState,
    onAction: (JourneyAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.loading) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = Space.BASE.dp),
            verticalArrangement = Arrangement.spacedBy(SfTheme.density.cardGap.dp),
        ) {
            Spacer(modifier = Modifier.height(Space.LG.dp))
            repeat(4) { SfHabitCardSkeleton() }
        }
        return
    }

    val tree = remember(state.nodes, state.expanded) {
        JourneyTree.build(state.nodes, state.expanded)
    }
    val gaps = remember(tree) { JourneyTree.gaps(tree) }
    val titles = remember(state.nodes) {
        state.nodes.associateBy({ it.kind.key + ":" + it.id }, { it.title })
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
        item(key = "chain") { ChainHeader(tree.summary) }

        item(key = "tools") { ToolRow(onTool = { onAction(JourneyAction.Tool(it)) }) }

        for (gap in gaps) {
            item(key = "gap_${gap.kind.key}_${gap.nodeId ?: "none"}") {
                GapCard(gap = gap, onAdd = { onAction(JourneyAction.Add(it, gap.nodeId)) })
            }
        }

        if (tree.linked.isNotEmpty()) {
            item(key = "linkedHeader") {
                SfSectionHeader(
                    title = "Your chain",
                    actionLabel = "Add identity",
                    onAction = { onAction(JourneyAction.Add(JourneyTree.Kind.IDENTITY, null)) },
                )
            }
            entityRows(tree.linked, titles, onAction)
        }

        if (tree.unlinked.isNotEmpty()) {
            item(key = "unlinkedHeader") {
                SfSectionHeader(title = "Not linked yet")
            }
            item(key = "unlinkedNote") {
                Text(
                    text = "These exist but nothing above them does. " +
                        "Linking them up is what turns a list into a system.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Space.XS.dp),
                )
            }
            entityRows(tree.unlinked, titles, onAction)
        }

        if (tree.isEmpty) {
            item(key = "empty") { EmptyJourney(onAdd = { onAction(JourneyAction.Add(it, null)) }) }
        }
        }
    }
}

/**
 * Emitted as individual lazy items rather than wrapped in one composable,
 * so the list still recycles: a Column of five hundred rows inside a single
 * item composes all five hundred.
 */
private fun LazyListScope.entityRows(
    rows: List<JourneyTree.Row>,
    titles: Map<String, String>,
    onAction: (JourneyAction) -> Unit,
) {
    for (row in rows) {
        item(key = row.key, contentType = row.node.kind) {
            val kind = row.node.kind
            val id = row.node.id
            val parentKey = kind.parent?.key + ":" + row.node.parentId
            SfEntityRow(
                row = row,
                parentTitle = titles[parentKey],
                onClick = { onAction(JourneyAction.Open(kind, id)) },
                onToggle = if (row.expandable) {
                    { onAction(JourneyAction.Toggle(kind, id)) }
                } else {
                    null
                },
                onMenu = { onAction(JourneyAction.Menu(kind, id)) },
            )
        }
    }
}

/**
 * The four-step breadcrumb, with the reached steps lit.
 *
 * This is a progress indicator for the *structure* of the user's system
 * rather than for their behaviour — a deliberately different thing from a
 * streak. Structure is something you finish once; the header stops
 * insisting about it as soon as the chain is complete.
 */
@Composable
private fun ChainHeader(summary: JourneyTree.Summary) {
    val reached = summary.deepestChain
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Space.SM.dp)
            .semantics {
                contentDescription = if (reached >= JourneyTree.Kind.ordered.size) {
                    "Your chain is complete, from identity through to habits."
                } else {
                    "Chain: identity, goal, system, habit. Reached step $reached of 4."
                }
            },
        horizontalArrangement = Arrangement.spacedBy(Space.XS.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (kind in JourneyTree.Kind.ordered) {
            val lit = kind.rank < reached
            val alpha by animateFloatAsState(
                targetValue = if (lit) 1f else 0.38f,
                animationSpec = SfTheme.motion.tween(
                    SfTheme.motion.normal,
                    delayMs = SfTheme.motion.staggerDelay(kind.rank),
                ),
                label = "chainStep",
            )
            Text(
                text = kind.label,
                style = SfTheme.type.overline,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.alpha(alpha),
            )
            if (kind.child != null) {
                Text(
                    text = "\u2192",
                    style = SfTheme.type.overline,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.alpha(alpha),
                )
            }
        }
    }
}

/** Scorecard, Flows, Review — the design tools, kept above the tree. */
@Composable
private fun ToolRow(onTool: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.SM.dp),
    ) {
        ToolCard(R.drawable.ic_scorecard, "Scorecard", Modifier.weight(1f)) { onTool(0) }
        ToolCard(R.drawable.ic_flow, "Flows", Modifier.weight(1f)) { onTool(1) }
        ToolCard(R.drawable.ic_history, "Review", Modifier.weight(1f)) { onTool(2) }
    }
}

@Composable
private fun ToolCard(icon: Int, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    SfCard(
        modifier = modifier,
        variant = SfCardVariant.Filled,
        onClick = onClick,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.height(Space.SM.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * A prompt for something the hierarchy is missing.
 *
 * Phrased as an invitation with a single obvious next tap. The alternative —
 * a red badge counting problems — turns a personal growth app into a chore
 * list, which is the failure mode this whole design language exists to
 * avoid.
 */
@Composable
private fun GapCard(gap: JourneyTree.Gap, onAdd: (JourneyTree.Kind) -> Unit) {
    val target = gap.kind.parent ?: gap.kind
    SfCard(variant = SfCardVariant.Warm) {
        Text(
            text = gap.title,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(modifier = Modifier.height(Space.XS.dp))
        Text(text = gap.body, style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(Space.SM.dp))
        Box(modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = { onAdd(target) }) {
                Text(text = "Add ${target.label.lowercase()}")
            }
        }
    }
}

/**
 * The first-run state.
 *
 * Offers exactly one action. A blank Journey with four "Add" buttons asks
 * the user to pick an entry point into a model they have not learned yet;
 * the identity is the entry point, and everything else follows from it.
 */
@Composable
private fun EmptyJourney(onAdd: (JourneyTree.Kind) -> Unit) {
    SfCard(variant = SfCardVariant.Accent) {
        Text(
            text = "Start with who you want to be",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(modifier = Modifier.height(Space.SM.dp))
        Text(
            text = "\u201cSomeone who moves every day.\u201d From there, a goal gives it " +
                "direction, a system makes it repeatable, and habits do the work.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(Space.MD.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            TextButton(onClick = { onAdd(JourneyTree.Kind.IDENTITY) }) {
                Text(text = "Write an identity")
            }
            Spacer(modifier = Modifier.width(Space.SM.dp))
            TextButton(onClick = { onAdd(JourneyTree.Kind.HABIT) }) {
                Text(text = "Just add a habit")
            }
        }
    }
}
