package com.superflow.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.superflow.R
import com.superflow.data.Prefs
import com.superflow.design.ColorRoles
import com.superflow.design.SurfaceRoles
import com.superflow.design.ThemeSelection
import com.superflow.design.WidgetLayout

/**
 * Colour and bitmap work for the home-screen widget (plan 16.2).
 *
 * A widget is not a window into the app; it is a set of instructions posted
 * to another process, and almost everything the app can do to a view is
 * unavailable. Three consequences shape this file.
 *
 * **A widget cannot read the app's theme.** `RemoteViews` inflates in the
 * launcher, against the launcher's configuration, so `?attr/colorPrimary`
 * resolves to the launcher's idea of primary — which is usually the
 * framework default. The palette has to be resolved here, from [Prefs], and
 * pushed across as literal ARGB values. That is why this file talks to
 * [ColorRoles] and [SurfaceRoles] directly rather than to a theme.
 *
 * **A widget cannot tint most things below API 31.** `setColorStateList` —
 * the call that would tint a `ProgressBar` — arrived in S, and this app ships
 * to API 26. Rather than have the widget look palette-aware on new phones and
 * default-blue on old ones, every coloured surface that is not text is drawn
 * here as a bitmap: the ring, and the bar. Bitmaps are tinted by definition.
 *
 * **A widget cannot receive an arbitrary background.** Drawables cannot be
 * sent through `RemoteViews`, only resource ids, so the rounded card behind
 * the widget is one of four pre-declared shapes chosen by dark flavour. The
 * background is a surface, not an accent, so it varies with the dark variant
 * and not with the palette — which is exactly the set of shapes declared.
 *
 * Bitmap budget is the other constraint worth stating. The system caps the
 * bitmap memory a single `RemoteViews` may carry at roughly 1.5x the screen,
 * and a widget that exceeds it is silently replaced by an error view. The
 * ring is drawn at a fixed small size and the bar at its real width but only
 * a few pixels tall, so the whole payload stays under about 200 KB even on a
 * 3x display.
 */
internal object WidgetChrome {

    /**
     * The colours a widget needs, already resolved to ARGB.
     *
     * Deliberately smaller than a full scheme. Anything a widget cannot
     * express is not worth resolving.
     */
    data class Chrome(
        val backgroundRes: Int,
        val onSurface: Int,
        val muted: Int,
        val accent: Int,
        val onAccent: Int,
        val track: Int,
        val outline: Int,
        val success: Int,
        /** High contrast thickens the ring rather than recolouring it. */
        val emphatic: Boolean,
    )

    fun chromeFor(context: Context): Chrome {
        val prefs = Prefs.get(context)
        val dark = isDark(context, prefs)
        val scheme = ColorRoles.schemeFor(prefs.palette, dark)
        val surfaces = SurfaceRoles.surfacesFor(dark, prefs.darkVariant)
        val contrast = prefs.highContrast
        return Chrome(
            backgroundRes = backgroundFor(dark, prefs.darkVariant),
            onSurface = surfaces.onSurface,
            muted = if (contrast) surfaces.onSurface else surfaces.onSurfaceVariant,
            accent = scheme.primary,
            onAccent = scheme.onPrimary,
            track = surfaces.surfaceVariant,
            outline = if (contrast) surfaces.outline else surfaces.outlineVariant,
            success = scheme.success,
            emphatic = contrast,
        )
    }

    /**
     * Whether the widget should render dark.
     *
     * The app's own theme preference wins over the launcher's, because a user
     * who has forced the app light and sees a black widget on their home
     * screen has been given two answers to one question. Only THEME_SYSTEM
     * defers to the configuration — and the configuration here is the
     * launcher's, which is the right one to ask, since that is where the
     * widget is drawn.
     */
    private fun isDark(context: Context, prefs: Prefs): Boolean = when (prefs.themeMode) {
        Prefs.THEME_LIGHT -> false
        Prefs.THEME_DARK -> true
        else -> (context.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    private fun backgroundFor(dark: Boolean, darkVariant: Int): Int = when {
        !dark -> R.drawable.widget_bg
        darkVariant == ThemeSelection.DARK_OLED_ID -> R.drawable.widget_bg_oled
        darkVariant == ThemeSelection.DARK_MIDNIGHT_ID -> R.drawable.widget_bg_midnight
        else -> R.drawable.widget_bg_warm
    }

    // ------------------------------------------------------------- bitmaps

    /** Ring diameter in dp, per widget size. Small has room to be generous. */
    fun ringDp(size: WidgetLayout.Size): Int = when (size) {
        WidgetLayout.Size.SMALL -> 76
        WidgetLayout.Size.LARGE -> 52
        else -> 56
    }

    /**
     * The progress ring.
     *
     * Drawn from twelve o'clock, clockwise, with round caps so that a single
     * completed habit out of twenty still reads as a mark rather than a
     * hairline. A zero-progress ring draws the track only: a round cap at 0
     * degrees would put a dot at the top of an untouched day, which looks
     * like a bug.
     */
    fun ring(context: Context, percent: Int, chrome: Chrome, sizeDp: Int): Bitmap {
        val px = dp(context, sizeDp).coerceAtLeast(24)
        val stroke = dp(context, if (chrome.emphatic) 9 else 7).toFloat()
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val inset = stroke / 2f + 1f
        val box = RectF(inset, inset, px - inset, px - inset)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            strokeCap = Paint.Cap.ROUND
        }

        paint.color = chrome.track
        canvas.drawArc(box, 0f, 360f, false, paint)

        val clamped = percent.coerceIn(0, 100)
        if (clamped > 0) {
            paint.color = if (clamped >= 100) chrome.success else chrome.accent
            // A full ring drawn as a 360-degree arc with round caps overlaps
            // itself at the top and leaves a visible seam; draw it as a
            // circle instead.
            if (clamped >= 100) {
                canvas.drawArc(box, 0f, 360f, false, paint)
            } else {
                canvas.drawArc(box, -90f, clamped * 3.6f, false, paint)
            }
        }
        return bmp
    }

    /**
     * The horizontal progress bar used by the Wide size.
     *
     * Drawn at the widget's real width so that the rounded ends are not
     * stretched by `FIT_XY`. Height is fixed at 8dp, so even a 500dp-wide
     * tablet widget costs about 48 KB.
     */
    fun bar(context: Context, percent: Int, chrome: Chrome, widthDp: Int): Bitmap {
        val w = dp(context, widthDp.coerceIn(80, 640))
        val h = dp(context, 8).coerceAtLeast(4)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val r = h / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.color = chrome.track
        canvas.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), r, r, paint)

        val clamped = percent.coerceIn(0, 100)
        if (clamped > 0) {
            // Never shorter than its own cap radius, or a 1% day draws a
            // lens-shaped sliver instead of a dot.
            val filled = maxOf(h.toFloat(), w * clamped / 100f)
            paint.color = if (clamped >= 100) chrome.success else chrome.accent
            canvas.drawRoundRect(RectF(0f, 0f, filled, h.toFloat()), r, r, paint)
        }
        return bmp
    }

    /**
     * The check box drawn beside a row.
     *
     * Also a bitmap, for the same tinting reason as the ring, and drawn
     * rather than composed from two vectors because a widget row needs
     * exactly one ImageView per state rather than a stack of them.
     *
     * The tick is stroked as two line segments instead of a filled path so
     * that it stays crisp at 22dp on an mdpi screen, where a filled
     * Material tick collapses into a blob.
     */
    fun box(context: Context, checked: Boolean, chrome: Chrome, sizeDp: Int = 22): Bitmap {
        val px = dp(context, sizeDp).coerceAtLeast(12)
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val stroke = dp(context, if (chrome.emphatic) 2 else 2).toFloat().coerceAtLeast(2f)
        val r = px / 2f - stroke / 2f

        if (checked) {
            paint.style = Paint.Style.FILL
            paint.color = chrome.success
            canvas.drawCircle(px / 2f, px / 2f, r, paint)

            paint.style = Paint.Style.STROKE
            paint.color = chrome.onAccent
            paint.strokeWidth = stroke
            paint.strokeCap = Paint.Cap.ROUND
            val a = px * 0.30f
            val b = px * 0.47f
            val c = px * 0.70f
            canvas.drawLine(a, px * 0.52f, b, px * 0.68f, paint)
            canvas.drawLine(b, px * 0.68f, c, px * 0.34f, paint)
        } else {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = stroke
            paint.color = chrome.outline
            canvas.drawCircle(px / 2f, px / 2f, r, paint)
        }
        return bmp
    }

    private fun dp(context: Context, value: Int): Int =
        Math.round(value * context.resources.displayMetrics.density)
}
