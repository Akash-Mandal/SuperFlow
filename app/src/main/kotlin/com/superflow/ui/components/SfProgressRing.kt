package com.superflow.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.superflow.design.rememberIsLowEnd
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.superflow.ui.theme.SfTheme
import com.superflow.ui.theme.ringSpring
import kotlin.math.roundToInt

/**
 * The daily progress ring (§12.1).
 *
 * Animates with a spring rather than a tween: progress arriving with a
 * slight settle reads as a physical response to the user's action, where a
 * linear sweep reads as a loading bar. The spring is tuned to 0.75 damping -
 * enough to feel alive, not enough to bounce past the value and imply
 * progress the user has not made.
 *
 * @param done  completed opportunities
 * @param total scheduled opportunities; zero is handled as "nothing
 *              scheduled" rather than as 0%, since 0% implies failure and
 *              an empty day is not a failed day
 */
@Composable
fun SfProgressRing(
    done: Int,
    total: Int,
    modifier: Modifier = Modifier,
    size: Int = 120,
    strokeWidth: Int = 10,
    onClick: (() -> Unit)? = null,
) {
    val motion = SfTheme.motion
    val scheme = MaterialTheme.colorScheme
    val colors = SfTheme.colors

    val target = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)
    val progress by animateFloatAsState(
        targetValue = target,
        animationSpec = ringSpring(motion.enabled),
        label = "ringProgress",
    )

    // Breath Ring idle (§1 "Breath Ring"): subtle 2% radius oscillation
    // over 6s. Disabled when motion is off or on low-end devices — idle
    // motion that runs forever is the single most wasteful animation on a
    // mid/low-end device, and the home feed stays composed offscreen in
    // ViewPager2's cache even when not visible.
    val isLowEnd = rememberIsLowEnd()
    val shouldBreathe = motion.enabled && !isLowEnd && done in 1 until total
    val breathScale = if (shouldBreathe) {
        val breathTransition = rememberInfiniteTransition(label = "breath")
        val breath by breathTransition.animateFloat(
            initialValue = 0.98f,
            targetValue = 1.02f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 6000, easing = androidx.compose.animation.core.LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "breathScale",
        )
        breath
    } else {
        1f
    }

    val percent = (target * 100).roundToInt()
    val description = if (total <= 0) {
        "Nothing scheduled today"
    } else {
        "$done of $total done, $percent percent"
    }

    Box(
        modifier = modifier
            .size(size.dp)
            .then(
                if (breathScale != 1f) Modifier.graphicsLayer {
                    scaleX = breathScale
                    scaleY = breathScale
                } else Modifier
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .semantics {
                contentDescription = description
                // Announced as a progress bar so assistive tech reports the
                // value and its range, not just the label text.
                progressBarRangeInfo = ProgressBarRangeInfo(target, 0f..1f)
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size.dp)) {
            val stroke = strokeWidth.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            val topLeft = Offset(inset, inset)

            drawArc(
                color = scheme.surfaceVariant,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )

            if (progress > 0f) {
                drawArc(
                    // Complete days get the success colour; a partial day
                    // stays in the primary so "done" is a distinct event
                    // rather than the end of a gradient.
                    color = if (target >= 1f) colors.success else scheme.primary,
                    // -90 puts zero at the top, where a clock face starts.
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = if (total <= 0) "—" else "$percent%",
                style = SfTheme.type.dataLarge,
                color = scheme.onSurface,
            )
            if (total > 0) {
                Text(
                    text = "$done/$total",
                    style = SfTheme.type.data,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}
