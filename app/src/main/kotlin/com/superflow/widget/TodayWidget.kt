package com.superflow.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.superflow.AppBackground
import com.superflow.R
import com.superflow.core.time.SfTime
import com.superflow.data.Repository
import com.superflow.domain.Actor
import com.superflow.domain.CommandBus
import com.superflow.ui.MainActivity
import com.superflow.util.Dates
import com.superflow.util.jsonOf

/**
 * Home-screen widget.
 *
 * Shows today's progress and the single smallest next action, with one tap to
 * complete it. Deliberately minimal: a widget should reduce friction, not
 * become another dashboard to manage.
 *
 * Refresh policy: [refresh] is non-blocking, debounced to at most one render
 * every [MIN_REFRESH_MS] unless [force]d, and performs a single read pass
 * (one habits query + one check-in query) per render.
 */
class TodayWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // One data pass for every widget; identical content on each.
        val data = renderData(context)
        val views = buildViews(context, data)
        appWidgetIds.forEach { manager.updateAppWidget(it, views) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_CHECK -> {
                val habitId = intent.getStringExtra(EXTRA_HABIT_ID)
                if (!habitId.isNullOrBlank()) {
                    // onReceive is the main thread; the check-in is a DB write.
                    AppBackground.launch {
                        try {
                            CommandBus.get(context).execute(
                                "check_in", jsonOf("habit" to habitId, "level" to "TINY"), Actor.USER
                            )
                        } catch (e: Exception) {
                            Log.w(TAG, "Widget check-in failed", e)
                        }
                    }
                }
                refresh(context, force = true)
            }
            ACTION_REFRESH -> refresh(context, force = true)
        }
    }

    companion object {
        private const val TAG = "TodayWidget"
        private const val MIN_REFRESH_MS = 15_000L

        const val ACTION_CHECK = "com.superflow.widget.CHECK"
        const val ACTION_REFRESH = "com.superflow.widget.REFRESH"
        const val EXTRA_HABIT_ID = "habitId"

        @Volatile private var lastRefreshAt = 0L

        /**
         * Refreshes every installed widget, in the background. Cheap to call
         * from any lifecycle callback: without [force], refreshes closer than
         * [MIN_REFRESH_MS] to the previous one are skipped. Safe with zero
         * installed widgets.
         */
        fun refresh(context: Context, force: Boolean = false) {
            val ctx = context.applicationContext
            val now = System.currentTimeMillis()
            if (!force && now - lastRefreshAt < MIN_REFRESH_MS) return
            AppBackground.launch {
                if (!force) {
                    // Lost a race with another forced refresh.
                    if (System.currentTimeMillis() - lastRefreshAt < MIN_REFRESH_MS) return@launch
                }
                lastRefreshAt = System.currentTimeMillis()
                try {
                    renderAll(ctx)
                } catch (e: Exception) {
                    // No widget installed or a transient failure: log, don't crash.
                    Log.w(TAG, "Widget refresh failed", e)
                }
            }
        }

        private fun renderAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, TodayWidget::class.java)) ?: return
            if (ids.isEmpty()) return
            val data = renderData(context)
            val views = buildViews(context, data)
            for (id in ids) manager.updateAppWidget(id, views)
        }

        private fun renderData(context: Context): WidgetData {
            val repo = Repository.get(context)
            val date = repo.clock.today()
            val iso = SfTime.format(date)
            val scheduled = repo.habitsForDay(date)
            val checkIns = repo.checkInsFor(iso).associateBy { it.habitId }
            val done = scheduled.count { checkIns[it.id]?.isSuccess == true }
            val next = scheduled.firstOrNull { checkIns[it.id] == null }
            return WidgetData(
                dayLabel = Dates.humanDay(date),
                progress = if (scheduled.isEmpty()) "Nothing scheduled" else "$done of ${scheduled.size} done",
                total = scheduled.size.coerceAtLeast(1),
                done = done,
                next = next?.let { it.tinyStart.ifBlank { h -> h.title } to it.id }
            )
        }

        private fun buildViews(context: Context, d: WidgetData): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_today)
            views.setTextViewText(R.id.widget_date, d.dayLabel)
            views.setTextViewText(R.id.widget_progress, d.progress)
            views.setProgressBar(R.id.widget_bar, d.total, d.done, false)

            if (d.next == null) {
                views.setTextViewText(R.id.widget_next,
                    if (d.total == 0) "Add a habit to begin" else "Everything is handled")
                views.setViewVisibility(R.id.widget_action, View.GONE)
            } else {
                views.setTextViewText(R.id.widget_next, d.next.first)
                views.setViewVisibility(R.id.widget_action, View.VISIBLE)
                val checkIntent = Intent(context, TodayWidget::class.java).apply {
                    action = ACTION_CHECK
                    putExtra(EXTRA_HABIT_ID, d.next.second)
                }
                views.setOnClickPendingIntent(
                    R.id.widget_action,
                    PendingIntent.getBroadcast(
                        context, d.next.second.hashCode() and 0x7fffffff, checkIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
            }

            views.setOnClickPendingIntent(
                R.id.widget_root,
                PendingIntent.getActivity(
                    context, 0, Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            return views
        }

        /** Immutable widget payload so all IDs render identical, consistent content. */
        private data class WidgetData(
            val dayLabel: String,
            val progress: String,
            val total: Int,
            val done: Int,
            val next: Pair<String, String>?
        )
    }
}
