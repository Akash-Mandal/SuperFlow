package com.superflow.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.superflow.ui.theme.SfTheme

/**
 * The Flow Line motif (§1 "The Flow Line"): a continuous river-like line
 * that bends on misses but never breaks. Used in onboarding, empty states,
 * and the Today hero. Static when motion is off; a gentle path when on.
 * This is the minimal drawable; the animated PathMeasure draw-on will be
 * added when the onboarding hero lands.
 */
@Composable
fun SfFlowLine(
    modifier: Modifier = Modifier,
    progress: Float = 0.72f,
    hasMiss: Boolean = false,
) {
    val accent = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
    val hasMotion = SfTheme.motion.enabled

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp),
    ) {
        val w = size.width
        val h = size.height
        val midY = h / 2f
        val amp = if (hasMiss) h * 0.35f else h * 0.12f

        val trackPath = Path().apply {
            moveTo(0f, midY)
            cubicTo(w * 0.25f, midY - amp, w * 0.55f, midY + amp, w * 0.75f, midY)
            cubicTo(w * 0.88f, midY - amp * 0.5f, w * 0.95f, midY, w, midY)
        }
        drawPath(
            path = trackPath,
            color = track,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
        )

        val accentPath = Path().apply {
            moveTo(0f, midY)
            val endX = w * progress.coerceIn(0f, 1f)
            // Clip the same curve to the progress fraction
            cubicTo(
                (w * 0.25f).coerceAtMost(endX), midY - amp,
                (w * 0.55f).coerceAtMost(endX), midY + amp,
                (w * 0.75f).coerceAtMost(endX), midY,
            )
            if (endX > w * 0.75f) {
                cubicTo(
                    (w * 0.88f).coerceAtMost(endX), midY - amp * 0.5f,
                    (w * 0.95f).coerceAtMost(endX), midY,
                    endX, midY,
                )
            }
        }
        drawPath(
            path = accentPath,
            color = accent,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
        )

        if (hasMiss && hasMotion) {
            val missX = w * 0.52f
            val missY = midY + amp * 0.7f
            drawCircle(color = accent.copy(alpha = 0.18f), radius = 6.dp.toPx(), center = Offset(missX, missY))
        }
    }
}
