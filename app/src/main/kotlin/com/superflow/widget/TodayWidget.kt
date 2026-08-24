package com.superflow.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.superflow.R
import com.superflow.data.Repository
import com.superflow.data.model.FocusItem
import com.superflow.data.model.Habit
import com.superflow.design.Navigation
import com.superflow.design.WidgetLayout
import com.superflow.domain.Actor
import com.superflow.domain.CommandBus
import com.superflow.domain.Insights
import com.superflow.ui.MainActivity
import com.superflow.util.jsonOf

/**
 * Home-screen widget (plan 16).
 *
 * Four sizes, chosen by the launcher's reported cell size and resolved by
 * [WidgetLayout], which owns every decision that can be stated as a function
 * of (width, height, how much there is to show). Colour resolution and the
 * bitmaps live in [WidgetChrome]. What remains here is the part that can only
 * be done with a `Context`: reading the day, binding it into `RemoteViews`,
 * and wiring the intents.
 *
 * The plan asks for Jetpack Glance. Glance is a Compose-for-RemoteViews
 * runtime and is not in the dependency set, so this stays RemoteViews. The
 * split above is deliberately the one Glance would impose anyway: were Glance
 * to arrive, [WidgetLayout] and [WidgetChrome] would be reused verbatim and
 * only this file would be rewritten.
 *
 * Two behaviours worth knowing before editing.
 *
 * **Every widget id is measured separately.** A user may have a 2x2 on the
 * home screen and a 4x4 on a second page; `getAppWidgetOptions` is per-id, so
 * [render] resolves a size per id rather than once per update.
 *
 * **A check-in from the widget must not open the app.** `ACTION_CHECK` is a
 * broadcast handled here, which runs on the main thread of whatever process
 * receives it. The database write is small and synchronous by design — a
 * `goAsync` round trip costs more than the write, and the user is looking at
 * the widget waiting for the tick.
 */
class TodayWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { render(context, manager, it) }
    }

    /**
     * Re-render just the resized widget.
     *
     * Without this, a widget dragged from 4x2 to 4x4 keeps the medium layout
     * until the next 30-minute tick — the single most visible widget bug
     * there is, and the reason the framework added this callback.
     */
    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?
    ) {
        super.onAppWidgetOptionsChanged(context, manager, appWidgetId, newOptions)
        render(context, manager, appWidgetId)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_CHECK -> {
                val habitId = intent.getStringExtra(EXTRA_HABIT_ID)
                if (!habitId.isNullOrBlank()) {
                    CommandBus.get(context).execute(
                        "check_in", jsonOf("habit" to habitId, "level" to "TINY"), Actor.USER
                    )
                }
                refresh(context)
            }
            ACTION_FOCUS -> {
                val focusId = intent.getStringExtra(EXTRA_FOCUS_ID)
                val done = intent.getBooleanExtra(EXTRA_DONE, true)
                if (!focusId.isNullOrBlank()) {
                    CommandBus.get(context).execute(
                        "complete_focus_item",
                        jsonOf("id" to focusId, "done" to done),
                        Actor.USER
                    )
                }
                refresh(context)
            }
            ACTION_REFRESH -> refresh(context)
        }
    }

    companion object {
        const val ACTION_CHECK = "com.superflow.widget.CHECK"
        const val ACTION_FOCUS = "com.superflow.widget.FOCUS"
        const val ACTION_REFRESH = "com.superflow.widget.REFRESH"
        const val EXTRA_HABIT_ID = "habitId"
        const val EXTRA_FOCUS_ID = "focusId"
        const val EXTRA_DONE = "done"

        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, TodayWidget::class.java))
            ids?.forEach { render(context, manager, it) }
        }

        // ------------------------------------------------------------ render

        private fun render(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val size = sizeOf(manager, widgetId)
            val chrome = WidgetChrome.chromeFor(context)
            val repo = Repository.get(context)
            val date = repo.clock.today()
            val iso = com.superflow.core.time.SfTime.format(date)
            val (done, total) = Insights.dayProgress(repo, date)

            val checkIns = repo.checkInsFor(iso).associateBy { it.habitId }
            val scheduled = repo.habitsForDay(date)
            val pending = scheduled.filter { checkIns[it.id]?.isSuccess != true }
            val next = pending.firstOrNull()

            val autoPending = try {
                val db = com.superflow.data.db.SuperFlowDatabase.get(context).db
                db.query("SELECT COUNT(*) FROM blueprint_auto_plan WHERE status='PENDING'").use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
            } catch (_: Exception) { 0 }
            val content = WidgetLayout.content(
                done = done,
                total = total,
                nextHabit = next?.let { it.tinyStart.ifBlank { it.title } },
                timeOfDay = WidgetLayout.timeOfDay(repo.clock.nowTime().hour),
            ).let { if (autoPending > 0) it.copy(subhead = it.subhead + " · $autoPending Auto Reinforce pending") else it }

            val views = when (size) {
                WidgetLayout.Size.SMALL -> small(context, chrome, content, done, total)
                WidgetLayout.Size.MEDIUM -> medium(context, chrome, content, next)
                WidgetLayout.Size.WIDE ->
                    wide(context, manager, widgetId, chrome, content, repo, iso)
                WidgetLayout.Size.LARGE ->
                    large(context, chrome, content, size, pending, scheduled, checkIns)
            }

            views.setInt(R.id.widget_root, "setBackgroundResource", chrome.backgroundRes)
            views.setContentDescription(R.id.widget_root, WidgetLayout.describe(content))
            views.setOnClickPendingIntent(R.id.widget_root, openApp(context))
            manager.updateAppWidget(widgetId, views)
        }

        /**
         * The size the launcher has given this widget.
         *
         * `OPTION_APPWIDGET_MIN_WIDTH`/`MAX_HEIGHT` is the pair that
         * describes the portrait cell: min width and max height are both
         * measured in the *current* orientation's grid, and mixing them is
         * how widgets end up sized for a phone that is lying on its side.
         * A missing bundle means the launcher never reported — fall back to
         * the provider's declared minimum, which is the Medium size.
         */
        private fun sizeOf(manager: AppWidgetManager, widgetId: Int): WidgetLayout.Size {
            val options = try {
                manager.getAppWidgetOptions(widgetId)
            } catch (e: Exception) {
                null
            } ?: return WidgetLayout.Size.MEDIUM
            val w = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
            val h = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
            if (w <= 0 || h <= 0) return WidgetLayout.Size.MEDIUM
            return WidgetLayout.sizeFor(w, h)
        }

        private fun widthDpOf(manager: AppWidgetManager, widgetId: Int): Int {
            val options = try {
                manager.getAppWidgetOptions(widgetId)
            } catch (e: Exception) {
                null
            } ?: return WidgetLayout.Size.WIDE.minWidthDp
            val w = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
            return if (w > 0) w else WidgetLayout.Size.WIDE.minWidthDp
        }

        // ------------------------------------------------------------- sizes

        private fun small(
            context: Context,
            chrome: WidgetChrome.Chrome,
            content: WidgetLayout.Content,
            done: Int,
            total: Int,
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_small)
            views.setImageViewBitmap(
                R.id.widget_ring,
                WidgetChrome.ring(
                    context, content.percent, chrome,
                    WidgetChrome.ringDp(WidgetLayout.Size.SMALL)
                )
            )
            // A percentage on an empty day is a zero the user did not earn;
            // show a dash instead, matching the "Nothing scheduled" wording
            // of the larger sizes.
            views.setTextViewText(
                R.id.widget_percent,
                if (total == 0) "\u2014" else "${content.percent}%"
            )
            views.setTextColor(R.id.widget_percent, chrome.onSurface)
            views.setTextViewText(
                R.id.widget_count,
                if (total == 0) "no habits" else "$done/$total"
            )
            views.setTextColor(R.id.widget_count, chrome.muted)
            return views
        }

        private fun medium(
            context: Context,
            chrome: WidgetChrome.Chrome,
            content: WidgetLayout.Content,
            next: Habit?,
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_medium)
            views.setImageViewBitmap(
                R.id.widget_ring,
                WidgetChrome.ring(
                    context, content.percent, chrome,
                    WidgetChrome.ringDp(WidgetLayout.Size.MEDIUM)
                )
            )
            headline(views, chrome, content)

            if (content.showsAction && next != null) {
                views.setViewVisibility(R.id.widget_action, View.VISIBLE)
                views.setTextViewText(R.id.widget_action, content.actionLabel)
                views.setTextColor(R.id.widget_action, chrome.accent)
                views.setContentDescription(
                    R.id.widget_action,
                    "${content.actionLabel}: ${next.title}"
                )
                views.setOnClickPendingIntent(R.id.widget_action, checkIntent(context, next.id))
            } else {
                views.setViewVisibility(R.id.widget_action, View.GONE)
            }
            return views
        }

        private fun wide(
            context: Context,
            manager: AppWidgetManager,
            widgetId: Int,
            chrome: WidgetChrome.Chrome,
            content: WidgetLayout.Content,
            repo: Repository,
            iso: String,
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_wide)
            headline(views, chrome, content)
            views.setTextViewText(R.id.widget_percent, "${content.percent}%")
            views.setTextColor(R.id.widget_percent, chrome.muted)
            views.setImageViewBitmap(
                R.id.widget_bar,
                WidgetChrome.bar(
                    context, content.percent, chrome,
                    // Minus the layout's own 14dp padding on each side.
                    widthDpOf(manager, widgetId) - 28
                )
            )

            views.removeAllViews(R.id.widget_rows)
            val focus = repo.focusFor(iso)
            val rows = WidgetLayout.habitRows(WidgetLayout.Size.WIDE, focus.size)
            focus.take(rows).forEach { item ->
                views.addView(R.id.widget_rows, focusRow(context, chrome, item))
            }
            // With no focus list there is nothing to fill the space, so the
            // subhead carries the next habit instead of being repeated.
            views.setViewVisibility(
                R.id.widget_subhead,
                if (rows == 0) View.VISIBLE else View.GONE
            )
            return views
        }

        private fun large(
            context: Context,
            chrome: WidgetChrome.Chrome,
            content: WidgetLayout.Content,
            size: WidgetLayout.Size,
            pending: List<Habit>,
            scheduled: List<Habit>,
            checkIns: Map<String, com.superflow.data.model.CheckIn>,
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_large)
            views.setImageViewBitmap(
                R.id.widget_ring,
                WidgetChrome.ring(context, content.percent, chrome, WidgetChrome.ringDp(size))
            )
            headline(views, chrome, content)

            views.removeAllViews(R.id.widget_rows)
            // Unfinished first, then the completed ones, so the row a user
            // reaches for is always at the top and the list does not
            // reorder under their finger as they tick things off. Within
            // each group the day's own order is preserved.
            val ordered = pending + scheduled.filter { checkIns[it.id]?.isSuccess == true }
            val rows = WidgetLayout.habitRows(size, ordered.size)
            ordered.take(rows).forEach { habit ->
                val done = checkIns[habit.id]?.isSuccess == true
                views.addView(R.id.widget_rows, habitRow(context, chrome, habit, done))
            }
            val hidden = ordered.size - rows
            if (hidden > 0) {
                views.addView(R.id.widget_rows, moreRow(context, chrome, hidden))
            }
            return views
        }

        // -------------------------------------------------------------- rows

        private fun habitRow(
            context: Context,
            chrome: WidgetChrome.Chrome,
            habit: Habit,
            done: Boolean,
        ): RemoteViews {
            val row = RemoteViews(context.packageName, R.layout.widget_row)
            row.setImageViewBitmap(R.id.row_box, WidgetChrome.box(context, done, chrome))
            row.setTextViewText(R.id.row_title, habit.title)
            row.setTextColor(R.id.row_title, if (done) chrome.muted else chrome.onSurface)
            row.setContentDescription(
                R.id.row_root,
                if (done) "${habit.title}, done" else "${habit.title}, not yet. Tap to check in."
            )
            if (!done) {
                row.setOnClickPendingIntent(R.id.row_root, checkIntent(context, habit.id))
            }
            return row
        }

        private fun focusRow(
            context: Context,
            chrome: WidgetChrome.Chrome,
            item: FocusItem,
        ): RemoteViews {
            val row = RemoteViews(context.packageName, R.layout.widget_row)
            row.setImageViewBitmap(R.id.row_box, WidgetChrome.box(context, item.done, chrome))
            row.setTextViewText(R.id.row_title, item.title)
            row.setTextColor(R.id.row_title, if (item.done) chrome.muted else chrome.onSurface)
            row.setContentDescription(
                R.id.row_root,
                if (item.done) "${item.title}, done. Tap to reopen."
                else "${item.title}, not yet. Tap to complete."
            )
            row.setOnClickPendingIntent(
                R.id.row_root, focusIntent(context, item.id, !item.done)
            )
            return row
        }

        /** "and 3 more" — a count, not a scroll hint, because it cannot scroll. */
        private fun moreRow(
            context: Context,
            chrome: WidgetChrome.Chrome,
            hidden: Int,
        ): RemoteViews {
            val row = RemoteViews(context.packageName, R.layout.widget_row)
            row.setViewVisibility(R.id.row_box, View.INVISIBLE)
            row.setTextViewText(R.id.row_title, "and $hidden more")
            row.setTextColor(R.id.row_title, chrome.muted)
            row.setContentDescription(R.id.row_root, "$hidden more, open the app to see them")
            row.setOnClickPendingIntent(R.id.row_root, openApp(context))
            return row
        }

        private fun headline(
            views: RemoteViews,
            chrome: WidgetChrome.Chrome,
            content: WidgetLayout.Content,
        ) {
            views.setTextViewText(R.id.widget_headline, content.headline)
            views.setTextColor(R.id.widget_headline, chrome.onSurface)
            views.setTextViewText(R.id.widget_subhead, content.subhead)
            views.setTextColor(R.id.widget_subhead, chrome.muted)
        }

        // ----------------------------------------------------------- intents

        /**
         * Request codes.
         *
         * `PendingIntent` equality ignores extras, so two check-in intents
         * for different habits collide unless their request codes differ —
         * the classic widget bug where every row checks off the same habit.
         * The id's hash provides the spread; the offsets keep the three
         * intent families from colliding with each other.
         */
        private const val RC_OPEN = 0
        private const val RC_CHECK = 1 shl 20
        private const val RC_FOCUS = 1 shl 21

        private fun checkIntent(context: Context, habitId: String): PendingIntent {
            val intent = Intent(context, TodayWidget::class.java).apply {
                action = ACTION_CHECK
                putExtra(EXTRA_HABIT_ID, habitId)
                // Extras are ignored for equality but the data URI is not:
                // belt and braces, so a launcher that caches aggressively
                // still gets distinct intents.
                data = android.net.Uri.parse("superflow://check/$habitId")
            }
            return PendingIntent.getBroadcast(
                context, RC_CHECK + (habitId.hashCode() and 0xFFFF), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun focusIntent(context: Context, focusId: String, done: Boolean): PendingIntent {
            val intent = Intent(context, TodayWidget::class.java).apply {
                action = ACTION_FOCUS
                putExtra(EXTRA_FOCUS_ID, focusId)
                putExtra(EXTRA_DONE, done)
                data = android.net.Uri.parse("superflow://focus/$focusId/$done")
            }
            return PendingIntent.getBroadcast(
                context, RC_FOCUS + (focusId.hashCode() and 0xFFFF), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun openApp(context: Context): PendingIntent =
            PendingIntent.getActivity(
                context, RC_OPEN,
                Intent(context, MainActivity::class.java).apply {
                    // A widget tap should land on Today whatever the user's
                    // start-screen preference is: they tapped a view of
                    // today, and being taken to Insights instead reads as
                    // the wrong widget having been pressed.
                    putExtra(MainActivity.EXTRA_TAB, Navigation.Tab.TODAY.key)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
    }
}
