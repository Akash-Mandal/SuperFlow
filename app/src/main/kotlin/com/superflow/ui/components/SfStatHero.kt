package com.superflow.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.superflow.design.Space
import com.superflow.ui.theme.SfTheme

/**
 * A single-number hero stat with its trend (ALPHA3_VISUAL_PLAN §10.13).
 *
 * The delta arrow is decoration; the sentence is the accessibility contract:
 * every StatHero carries a spoken alternative stating direction and
 * comparison plainly ("up 12 percent vs last week"), because "an arrow"
 * means nothing out loud.
 */
@Composable
fun SfStatHero(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    deltaFraction: Float? = null,
    comparisonLabel: String? = null,
    series: List<Float> = emptyList(),
) {
    val motion = SfTheme.motion

    // Entrance roll: the number fades up with the spring used across v3.
    // Under reduced motion it simply appears.
    var entered by remember { mutableStateOf(!motion.enabled) }
    LaunchedEffect(motion.enabled) { entered = true }
    val roll by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = if (motion.enabled) motion.springStandard() else snap(),
        label = "sfStatHeroRoll",
    )

    val direction = when {
        deltaFraction == null -> null
        deltaFraction > 0.005f -> "up"
        deltaFraction < -0.005f -> "down"
        else -> "steady"
    }
    val description = buildString {
        append("$label: $value")
        if (deltaFraction != null && comparisonLabel != null && direction != "steady") {
            append(". $direction ${kotlin.math.abs(deltaFraction * 100).toInt()} percent $comparisonLabel")
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = description },
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.graphicsLayer { alpha = roll },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (deltaFraction != null && direction != null) {
                Text(
                    text = arrow(direction),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (direction == "down") {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
                Spacer(modifier = Modifier.padding(horizontal = Space.XXS.dp))
            }
            Text(
                text = comparisonLabel ?: label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (series.size >= 2) {
            SfSparkline(series = series)
        }
    }
}

private fun arrow(direction: String): String = when (direction) {
    "up" -> "↑"
    "down" -> "↓"
    else -> "→"
}

/**
 * The one-line trend (ALPHA3_VISUAL_PLAN §12).
 *
 * Single accent stroke; no axes, no labels - shape is all it shows, values
 * are the StatHero's job. Draws on over the fast duration; static when
 * motion is off.
 */
@Composable
fun SfSparkline(
    series: List<Float>,
    modifier: Modifier = Modifier,
) {
    if (series.size < 2) return
    val strokeColor = MaterialTheme.colorScheme.primary
    val motion = SfTheme.motion
    var progress by remember { mutableFloatStateOf(if (motion.enabled) 0f else 1f) }
    LaunchedEffect(motion.enabled) {
        if (!motion.enabled || progress >= 1f) return@LaunchedEffect
        var start = 0L
        val target = motion.fast.coerceAtLeast(1).toLong()
        while (progress < 1f) {
            withFrameMillis { frame ->
                if (start == 0L) start = frame
                progress = ((frame - start).toFloat() / target).coerceIn(0f, 1f)
            }
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Space.XXS.dp)
            .height(28.dp),
    ) {
        val min = series.minOrNull() ?: return@Canvas
        val max = series.maxOrNull() ?: return@Canvas
        val range = (max - min).takeIf { it > 0f } ?: 1f
        val stepX = size.width / (series.size - 1)

        // Partial draw by index: the line grows left to right without
        // resampling, so the shape never lies about where data ends.
        val lastVisibleIndex =
            ((series.size - 1) * progress.coerceAtLeast(0.001f)).toInt().coerceIn(1, series.size - 1)

        val path = Path()
        for (i in 0..lastVisibleIndex) {
            val x = i * stepX
            val y = size.height - ((series[i] - min) / range) * size.height
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = strokeColor,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
        )
        if (progress >= 1f && lastVisibleIndex == series.size - 1) {
            val endX = (series.size - 1) * stepX
            val endY = size.height - ((series.last() - min) / range) * size.height
            drawCircle(color = strokeColor, radius = 3.dp.toPx(), center = Offset(endX, endY))
        }
    }
}
