package com.superflow.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.superflow.R
import com.superflow.design.JourneyTree
import com.superflow.design.Space
import com.superflow.ui.theme.SfTheme

/**
 * One node of the Journey tree (§11.2).
 *
 * The row is a card with three parts: a connector gutter that draws the
 * line back to the parent, the card itself with its entity accent, and an
 * expand control when there is something underneath.
 *
 * The connector is drawn rather than faked with indentation because the
 * plan's whole point for this screen is that the chain is *visible* -
 * identity to goal to system to habit. Indentation alone conveys nesting to
 * a sighted user who is already looking for it; a drawn line says it.
 *
 * That line is decorative, so it is excluded from accessibility entirely
 * and the relationship is carried in words instead: the row announces its
 * kind and its parent, which is information the line only implies.
 */
@Composable
fun SfEntityRow(
    row: JourneyTree.Row,
    modifier: Modifier = Modifier,
    parentTitle: String? = null,
    onClick: (() -> Unit)? = null,
    onToggle: (() -> Unit)? = null,
    onMenu: (() -> Unit)? = null,
    /** Trailing content inside the card: badges, counts, a chevron. */
    trailing: (@Composable () -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val accent = accentFor(row.node.kind)
    val connector = scheme.outlineVariant

    // Dormant entities stay fully legible but recede: they are context, not
    // the thing the user came here to act on. Fading them below about 0.6
    // starts failing contrast, so this is the floor rather than a taste
    // choice, and high contrast removes the fade altogether.
    val dim = if (row.dormant && !SfTheme.highContrast) 0.62f else 1f

    // IntrinsicSize.Min lets the connector gutter measure itself against the
    // card beside it. Without it the line is drawn at a guessed height and
    // stops short of, or overshoots, a card whose title wrapped to two lines.
    Row(
        modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min),
        verticalAlignment = Alignment.Top,
    ) {
        if (row.depth > 0) {
            TreeConnector(
                depth = row.depth,
                last = row.last,
                color = connector,
            )
        }

        Box(modifier = Modifier.weight(1f).alpha(dim)) {
            SfCard(
                variant = if (row.orphan) SfCardVariant.Outlined else SfCardVariant.Elevated,
                accentColor = accent,
                onClick = onClick,
                modifier = Modifier.semantics {
                    contentDescription = describe(row, parentTitle)
                    if (row.expandable) {
                        stateDescription = if (row.expanded) "Expanded" else "Collapsed"
                    }
                },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(iconFor(row.node.kind)),
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(Space.MD.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = row.node.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (row.node.detail.isNotBlank()) {
                            Text(
                                text = row.node.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (trailing != null) trailing()
                    if (onMenu != null) {
                        IconButton(onClick = onMenu) {
                            Icon(
                                painter = painterResource(R.drawable.ic_more),
                                contentDescription = "More actions for ${row.node.title}",
                                tint = scheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                val connections = JourneyTree.connectionLabel(row)
                if (connections.isNotEmpty() || row.orphan) {
                    Spacer(modifier = Modifier.height(Space.SM.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Space.SM.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (connections.isNotEmpty()) {
                            Text(
                                text = connections,
                                style = SfTheme.type.data,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                        if (row.orphan) {
                            // Stated, not hidden: an entity that is attached
                            // to nothing is the single most common reason a
                            // user's system quietly stops working.
                            Text(
                                text = orphanLabel(row.node.kind),
                                style = MaterialTheme.typography.bodySmall,
                                fontStyle = FontStyle.Italic,
                                color = SfTheme.colors.caution,
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        if (row.expandable && onToggle != null) {
                            ExpandToggle(
                                expanded = row.expanded,
                                count = row.descendantCount,
                                onToggle = onToggle,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The elbow that ties a row to its parent.
 *
 * One indent step per level, then a vertical stem and a horizontal arm. The
 * stem stops halfway down on the last child so the group visibly closes
 * instead of trailing off into the next section.
 */
@Composable
private fun TreeConnector(depth: Int, last: Boolean, color: Color) {
    val step = SfTheme.density.cardPadding.dp
    Box(
        modifier = Modifier
            .width(step * depth)
            .fillMaxHeight()
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
            val stroke = 1.5f * density
            // Only the innermost level draws an elbow; the outer levels
            // draw pass-through stems for the ancestors still open below.
            val x = size.width - step.toPx() / 2f
            val midY = size.height / 2f
            drawLine(
                color = color,
                start = Offset(x, 0f),
                end = Offset(x, if (last) midY else size.height),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = color,
                start = Offset(x, midY),
                end = Offset(size.width, midY),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

/**
 * The expand affordance.
 *
 * Shows the hidden count while collapsed, because "3 below" is a reason to
 * tap and a bare chevron is not.
 */
@Composable
private fun ExpandToggle(expanded: Boolean, count: Int, onToggle: () -> Unit) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = SfTheme.motion.tween(SfTheme.motion.quick),
        label = "expandChevron",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .semantics { contentDescription = if (expanded) "Collapse" else "Show $count more" },
    ) {
        AnimatedVisibility(
            visible = !expanded && count > 0,
            enter = fadeIn(SfTheme.motion.tween(SfTheme.motion.fast)),
            exit = fadeOut(SfTheme.motion.tween(SfTheme.motion.instant)),
        ) {
            Text(
                text = "$count",
                style = SfTheme.type.data,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(
            onClick = onToggle,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_chevron_down),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rotate(rotation),
            )
        }
    }
}

/** §6.5: each entity type carries its own accent. */
@Composable
fun accentFor(kind: JourneyTree.Kind): Color {
    val colors = SfTheme.colors
    return when (kind) {
        JourneyTree.Kind.IDENTITY -> colors.accentIdentity
        JourneyTree.Kind.GOAL -> colors.accentGoal
        JourneyTree.Kind.SYSTEM -> colors.accentSystem
        JourneyTree.Kind.HABIT -> colors.accentHabit
    }
}

private fun iconFor(kind: JourneyTree.Kind): Int = when (kind) {
    JourneyTree.Kind.IDENTITY -> R.drawable.ic_identity
    JourneyTree.Kind.GOAL -> R.drawable.ic_goal
    JourneyTree.Kind.SYSTEM -> R.drawable.ic_system
    JourneyTree.Kind.HABIT -> R.drawable.ic_bolt
}

private fun orphanLabel(kind: JourneyTree.Kind): String {
    val above = kind.parent ?: return ""
    return "No ${above.label.lowercase()}"
}

/**
 * What a screen reader hears.
 *
 * Kind first, because "Goal, Walk 5km" tells the user where they are in the
 * hierarchy immediately; the drawn connector conveys that visually and this
 * is its spoken equivalent.
 */
private fun describe(row: JourneyTree.Row, parentTitle: String?): String = buildString {
    append(row.node.kind.label)
    append(". ")
    append(row.node.title)
    if (row.node.detail.isNotBlank()) {
        append(". ")
        append(row.node.detail)
    }
    if (parentTitle != null) {
        append(". Under ")
        append(parentTitle)
    }
    val connections = JourneyTree.connectionLabel(row)
    if (connections.isNotEmpty()) {
        append(". ")
        append(connections)
    }
    if (row.orphan) {
        append(". Not linked to ")
        append(row.node.kind.parent?.let { "a ${it.label.lowercase()}" } ?: "anything")
    }
    if (row.dormant) append(". Dormant")
}
