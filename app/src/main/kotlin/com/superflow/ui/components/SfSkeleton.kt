package com.superflow.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.superflow.design.Radius
import com.superflow.design.Space
import com.superflow.ui.theme.SfTheme

/**
 * Content-aware loading placeholders (§18.1).
 *
 * The point of a skeleton is not to entertain during the wait - it is to
 * make the wait feel shorter by showing the shape of what is coming, so the
 * eye has somewhere to settle and the layout does not jump when data
 * arrives. A skeleton that does not match the real content's shape makes
 * things worse, which is why these are per-component rather than one
 * generic grey box.
 *
 * The shimmer respects the motion preference: at the None level the
 * placeholder is a static fill. A user who disabled animations should not be
 * given a permanently animating screen simply because it is "loading".
 */

@Composable
private fun shimmerBrush(): Brush {
    val colors = SfTheme.colors
    val motion = SfTheme.motion

    if (!motion.enabled) {
        // Static fill: still communicates "content goes here", without the
        // sweep.
        return Brush.linearGradient(listOf(colors.skeletonBase, colors.skeletonBase))
    }

    val transition = rememberInfiniteTransition(label = "skeleton")
    val offset by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            // Slow and low-contrast on purpose. A fast, bright shimmer reads
            // as a progress indicator and makes the wait feel longer.
            animation = tween(durationMillis = 1400, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerOffset",
    )

    return Brush.linearGradient(
        colors = listOf(colors.skeletonBase, colors.skeletonHighlight, colors.skeletonBase),
        start = Offset(offset - 400f, 0f),
        end = Offset(offset, 0f),
    )
}

/**
 * A single shimmering block.
 *
 * @param cornerRadius a [Radius] token. [Radius.FULL] is the "fully rounded"
 *                     sentinel rather than a dp value, so it is translated
 *                     to a percentage shape instead of a negative corner.
 */
@Composable
fun SfSkeletonBlock(
    modifier: Modifier = Modifier,
    cornerRadius: Int = Radius.XS,
) {
    val shape = if (cornerRadius == Radius.FULL) {
        RoundedCornerShape(percent = 50)
    } else {
        RoundedCornerShape(cornerRadius.dp)
    }
    Spacer(
        modifier = modifier
            .clip(shape)
            .background(shimmerBrush())
    )
}

/** A line of text. Width is a fraction so lines can be ragged like real text. */
@Composable
fun SfSkeletonLine(
    modifier: Modifier = Modifier,
    widthFraction: Float = 1f,
    height: Int = 14,
) {
    SfSkeletonBlock(
        modifier = modifier
            .fillMaxWidth(widthFraction.coerceIn(0.05f, 1f))
            .height(height.dp),
        cornerRadius = Radius.XXS,
    )
}

/**
 * The habit card skeleton.
 *
 * Mirrors SfHabitCard's layout: a check target, two lines of text, and a
 * history strip. Matching the real geometry is the whole point - when the
 * data lands, nothing moves.
 */
@Composable
fun SfHabitCardSkeleton(modifier: Modifier = Modifier) {
    SfCard(
        modifier = modifier.semantics { contentDescription = "Loading habit" },
        variant = SfCardVariant.Elevated,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.MD.dp),
        ) {
            SfSkeletonBlock(
                modifier = Modifier.size(40.dp),
                cornerRadius = Radius.FULL,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Space.SM.dp),
            ) {
                SfSkeletonLine(widthFraction = 0.62f, height = 16)
                SfSkeletonLine(widthFraction = 0.38f, height = 12)
            }
        }
        Spacer(modifier = Modifier.height(Space.MD.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.XS.dp)) {
            repeat(7) {
                SfSkeletonBlock(
                    modifier = Modifier.size(width = 20.dp, height = 20.dp),
                    cornerRadius = Radius.XXS,
                )
            }
        }
    }
}

/** A stat card skeleton: a label and a big number. */
@Composable
fun SfStatCardSkeleton(modifier: Modifier = Modifier) {
    SfCard(modifier = modifier, variant = SfCardVariant.Filled) {
        SfSkeletonLine(widthFraction = 0.45f, height = 11)
        Spacer(modifier = Modifier.height(Space.SM.dp))
        SfSkeletonBlock(
            modifier = Modifier
                .width(72.dp)
                .height(28.dp),
            cornerRadius = Radius.XXS,
        )
    }
}

/**
 * A list of habit skeletons.
 *
 * Three is deliberate: enough to read as a list, few enough that the screen
 * does not look full of content that is about to be replaced.
 */
@Composable
fun SfTodaySkeleton(modifier: Modifier = Modifier, count: Int = 3) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SfTheme.density.cardGap.dp),
    ) {
        repeat(count) { SfHabitCardSkeleton() }
    }
}
