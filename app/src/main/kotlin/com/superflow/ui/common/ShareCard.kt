package com.superflow.ui.common

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.superflow.data.Repository
import com.superflow.domain.Insights
import com.superflow.core.time.SfTime
import java.io.File

/**
 * A shareable progress card, drawn to a bitmap.
 *
 * The layout is a calm 4:5 Instagram-friendly card: today's progress, per-habit
 * consistency bars, a 14-day strip and the identity statement, with a
 * "Shared from SuperFlow" watermark. Pure Canvas drawing — no views — so the
 * card looks identical on every device.
 */
object ShareCard {

    const val WIDTH = 1080
    const val HEIGHT = 1350

    // Warm paper background, grounded green accent — the SuperFlow palette.
    private const val PAPER = 0xFFFBF7EE.toInt()
    private const val INK = 0xFF1C1B18.toInt()
    private const val MUTED = 0xFF79756C.toInt()
    private const val GREEN = 0xFF3A7D5C.toInt()
    private const val GREEN_DEEP = 0xFF17513C.toInt()
    private const val GREEN_SOFT = 0xFFC8E9D8.toInt()
    private const val AMBER = 0xFFF2C6A2.toInt()
    private const val CARD = 0xFFFFFFFF.toInt()
    private const val LINE = 0xFFE7E2D6.toInt()

    fun generate(repo: Repository): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val today = repo.clock.today()
        val (done, total) = Insights.dayProgress(repo, today)
        val stats = Insights.allStats(repo, today)
        val identity = repo.identities().firstOrNull()?.statement

        // Background
        canvas.drawColor(PAPER)
        val accentBar = Paint().apply { color = GREEN }
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), 18f, accentBar)

        var y = 96f
        val title = Paint().apply {
            color = INK; textSize = 68f; isAntiAlias = true
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }
        val body = Paint().apply {
            color = MUTED; textSize = 34f; isAntiAlias = true
        }
        val label = Paint().apply {
            color = MUTED; textSize = 28f; isAntiAlias = true
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }

        canvas.drawText("SuperFlow", 72f, y, title)
        y += 44f
        canvas.drawText("Shape your system. Become your future self.", 72f, y, body)
        y += 96f

        // Today card
        y = drawCard(canvas, y, 360f) { top ->
            canvas.drawText("TODAY", 72f, top + 52f, label)
            val progress = if (total == 0) 0f else done.toFloat() / total
            val big = Paint().apply {
                color = INK; textSize = 88f; isAntiAlias = true
                typeface = Typeface.create("sans-serif", Typeface.BOLD)
            }
            canvas.drawText("$done", 72f, top + 140f, big)
            val of = Paint().apply { color = MUTED; textSize = 40f; isAntiAlias = true }
            val w = big.measureText("$done")
            canvas.drawText("of $total done", 72f + w + 20f, top + 140f, of)
            drawProgressBar(canvas, top + 210f, progress)
            val msg = Paint().apply { color = INK; textSize = 34f; isAntiAlias = true }
            canvas.drawText(
                if (total == 0) "A quiet day is allowed." else "One action at a time.",
                72f, top + 276f, msg
            )
        }
        y += 36f

        // Consistency
        y = drawCard(canvas, y, 260f) { top ->
            canvas.drawText("CONSISTENCY · 30 DAYS", 72f, top + 52f, label)
            var i = 0
            for (s in stats.sortedByDescending { it.consistency30 }.take(4)) {
                val rowY = top + 116f + i * 44f
                val name = Paint().apply {
                    color = INK; textSize = 30f; isAntiAlias = true
                }
                canvas.drawText(s.habit.title, 72f, rowY, name)
                val pct = Paint().apply {
                    color = MUTED; textSize = 30f; isAntiAlias = true
                    typeface = Typeface.create("sans-serif", Typeface.BOLD)
                }
                canvas.drawText("${s.consistency30}%", WIDTH - 160f, rowY, pct)
                drawBar(canvas, rowY + 12f, s.consistency30 / 100f)
                i++
            }
            if (stats.isEmpty()) {
                val none = Paint().apply { color = MUTED; textSize = 30f; isAntiAlias = true }
                canvas.drawText("Check in a few times and bars appear here.", 72f, top + 130f, none)
            }
        }
        y += 36f

        // 14-day strip, aggregated across all habits.
        y = drawCard(canvas, y, 200f) { top ->
            canvas.drawText("LAST 14 DAYS", 72f, top + 52f, label)
            val counts = Insights.dailyCounts(repo, 14, today)
            val cell = (WIDTH - 144f) / counts.size.coerceAtLeast(1)
            counts.forEachIndexed { i, (_, count) ->
                val left = 72f + i * cell
                val color = if (count > 0) GREEN else LINE
                val p = Paint().apply { color = color }
                canvas.drawRoundRect(
                    RectF(left, top + 96f, left + cell - 8f, top + 144f), 12f, 12f, p
                )
            }
        }
        y += 36f

        // Identity
        if (!identity.isNullOrBlank()) {
            y = drawCard(canvas, y, 200f) { top ->
                canvas.drawText("YOU ARE BECOMING", 72f, top + 52f, label)
                val quote = Paint().apply {
                    color = GREEN_DEEP; textSize = 34f; isAntiAlias = true
                    typeface = Typeface.create("sans-serif", Typeface.ITALIC)
                }
                canvas.drawText(wrap(identity, 40), 72f, top + 118f, quote)
            }
            y += 36f
        }

        // Watermark
        val mark = Paint().apply { color = MUTED; textSize = 30f; isAntiAlias = true }
        canvas.drawText("Shared from SuperFlow · ${SfTime.shortDay(today)}", 72f, HEIGHT - 64f, mark)

        return bitmap
    }

    /** Draws a rounded white card and runs [content] for its interior. */
    private fun drawCard(canvas: Canvas, top: Float, height: Float, content: (Float) -> Unit): Float {
        val bg = Paint().apply {
            color = CARD; isAntiAlias = true
            setShadowLayer(12f, 0f, 6f, 0x14000000)
        }
        canvas.drawRoundRect(RectF(56f, top, WIDTH - 56f, top + height), 28f, 28f, bg)
        content(top)
        return top + height
    }

    private fun drawProgressBar(canvas: Canvas, top: Float, fraction: Float) {
        val track = Paint().apply { color = LINE }
        val fill = Paint().apply { color = GREEN; isAntiAlias = true }
        canvas.drawRoundRect(RectF(72f, top, WIDTH - 72f, top + 18f), 9f, 9f, track)
        canvas.drawRoundRect(
            RectF(72f, top, 72f + (WIDTH - 144f) * fraction.coerceIn(0f, 1f), top + 18f),
            9f, 9f, fill
        )
    }

    private fun drawBar(canvas: Canvas, top: Float, fraction: Float) {
        val track = Paint().apply { color = LINE }
        val fill = Paint().apply { color = GREEN_SOFT; isAntiAlias = true }
        canvas.drawRoundRect(RectF(72f, top, WIDTH - 72f, top + 12f), 6f, 6f, track)
        canvas.drawRoundRect(
            RectF(72f, top, 72f + (WIDTH - 144f) * fraction.coerceIn(0f, 1f), top + 12f),
            6f, 6f, fill
        )
    }

    private fun wrap(text: String, max: Int): String {
        val words = text.split(" ")
        val sb = StringBuilder()
        var line = ""
        for (w in words) {
            if (line.length + w.length + 1 > max) {
                if (line.isNotEmpty()) sb.append(line.trim()).append('\n')
                line = w
            } else line = "$line $w"
        }
        if (line.isNotBlank()) sb.append(line.trim())
        return sb.toString().take(3 * max)
    }

    /* ---------------------------------------------------------------- share */

    /** Generates the card and writes a PNG to the cache dir (call off the main thread). */
    fun saveToCache(context: Context, repo: Repository): File {
        val bitmap = generate(repo)
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "superflow-progress.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file
    }

    /**
     * Opens the share sheet for a cached card. The temporary file is provided
     * through a FileProvider so no storage permission is needed. Must run on
     * the main thread (it starts an activity).
     */
    fun shareFile(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "My SuperFlow progress")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share progress"))
    }

    /** Generates and shares in one call, all on the current (main) thread. */
    fun share(context: Context, repo: Repository) {
        shareFile(context, saveToCache(context, repo))
    }

    /** Saves the card to the gallery (MediaStore, API 29+). */
    fun saveToGallery(context: Context, repo: Repository): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        val bitmap = generate(repo)
        return try {
            val resolver = context.contentResolver
            val values = android.content.ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "superflow-${System.currentTimeMillis()}.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SuperFlow")
            }
            val uri: Uri? = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri == null) false
            else {
                resolver.openOutputStream(uri)?.use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                true
            }
        } catch (e: Exception) {
            false
        }
    }
}
