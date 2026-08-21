package com.superflow.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.FileProvider
import com.superflow.R
import com.superflow.core.time.SfTime
import com.superflow.data.Repository
import com.superflow.domain.Insights
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import kotlin.math.min

/**
 * Renders a simple, dependency-free progress card as a [Bitmap] and shares it
 * via the system share sheet (#18).
 *
 * The card intentionally stays text-and-bars only (no external charting
 * library) so it is robust across OEMs and easy to restyle. It shows the date,
 * today's done/total ring, total repetitions, best run and top habit.
 */
object ProgressCard {

    private const val W = 1080
    private const val H = 1350
    private const val PAD = 64f

    fun render(context: Context, repo: Repository, date: LocalDate = repo.clock.today()): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.parseColor("#FBF8F2")) // warm paper

        val ink = Color.parseColor("#1F2A24")
        val sub = Color.parseColor("#5C6B63")
        val accent = Color.parseColor("#3A7D5C")
        val track = Color.parseColor("#D9DFD8")

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink; textSize = 56f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = sub; textSize = 34f
        }
        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink; textSize = 96f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = sub; textSize = 30f
        }
        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = track; strokeWidth = 36f; style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accent; strokeWidth = 36f; style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }

        var y = PAD + 40f
        canvas.drawText("SuperFlow", PAD, y, titlePaint)
        y += 60f
        canvas.drawText(SfTime.humanDay(date), PAD, y, bodyPaint)
        y += 90f

        // Progress ring + today's count.
        val (done, total) = Insights.dayProgress(repo, date)
        val cx = W / 2f
        val cy = y + 200f
        val radius = 170f
        canvas.drawCircle(cx, cy, radius, trackPaint)
        if (total > 0) {
            val sweep = 360f * done / total
            canvas.drawArc(cx - radius, cy - radius, cx + radius, cy + radius,
                -90f, sweep, false, arcPaint)
        }
        val centerText = "$done/$total"
        val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink; textSize = 80f; textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(centerText, cx, cy + 28f, centerPaint)
        y = cy + radius + 90f

        // Stats grid from all habits.
        val stats = Insights.allStats(repo, date)
        val reps = stats.sumOf { it.repetitions }
        val bestRun = stats.maxOfOrNull { it.bestRun } ?: 0
        val recoveries = stats.sumOf { it.recoveries }
        val top = stats.filter { it.hasEnoughData }.maxByOrNull { it.consistency30 }

        val col1x = PAD
        val col2x = W / 2f
        drawStat(canvas, col1x, y, valuePaint, labelPaint, reps.toString(), "Repetitions")
        drawStat(canvas, col2x, y, valuePaint, labelPaint, bestRun.toString(), "Best run (days)")
        y += 180f
        drawStat(canvas, col1x, y, valuePaint, labelPaint, recoveries.toString(), "Recoveries")
        drawStat(canvas, col2x, y, valuePaint, labelPaint,
            top?.let { "${it.consistency30}%" } ?: "—",
            top?.let { "Top: ${it.habit.title.take(18)}" } ?: "Top habit")
        y += 200f

        // Per-habit bars for up to 5 active habits.
        canvas.drawText("30-day consistency", PAD, y, labelPaint)
        y += 50f
        val ranked = stats.filter { it.hasEnoughData }.sortedByDescending { it.consistency30 }.take(5)
        val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
        val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; textSize = 30f }
        val pctPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = sub; textSize = 28f }
        val barX = PAD + 360f
        val barMax = W - PAD - barX - 120f
        for (s in ranked) {
            canvas.drawText(s.habit.title.take(18), PAD, y + 12f, namePaint)
            canvas.drawRoundRect(barX, y - 10f, barX + barMax, y + 26f, 16f, 16f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = track })
            val fill = barMax * (s.consistency30 / 100f)
            canvas.drawRoundRect(barX, y - 10f, barX + fill, y + 26f, 16f, 16f, barPaint)
            canvas.drawText("${s.consistency30}%", barX + barMax + 20f, y + 12f, pctPaint)
            y += 70f
        }
        if (ranked.isEmpty()) {
            canvas.drawText("Keep going — your consistency will show here.", PAD, y + 12f, bodyPaint)
        }

        // Footer.
        val footer = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = sub; textSize = 28f }
        canvas.drawText("Systems over scoreboards · SuperFlow", PAD, H - PAD, footer)

        // Accent dot.
        canvas.drawCircle(W - PAD - 24f, PAD + 24f, 24f, accentPaint)

        return bmp
    }

    private fun drawStat(
        canvas: Canvas, x: Float, y: Float, value: Paint, label: Paint, big: String, small: String
    ) {
        canvas.drawText(big, x, y, value)
        canvas.drawText(small, x, y + 44f, label)
    }

    /** Render, write to cache, and launch the share sheet. */
    fun share(context: Context, repo: Repository) {
        val bmp = render(context, repo)
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "superflow-progress.png")
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "My SuperFlow progress — systems over scoreboards.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_card)))
    }
}
