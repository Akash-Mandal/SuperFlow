package com.superflow.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.superflow.R
import com.superflow.data.Repository
import com.superflow.domain.Actor
import com.superflow.domain.CommandBus
import com.superflow.domain.Insights
import com.superflow.ui.MainActivity
import com.superflow.util.Dates
import com.superflow.util.jsonOf

/**
 * Home-screen widget.
 *
 * Shows today's progress and the single smallest next action, with one tap to
 * complete it. Deliberately minimal: a widget should reduce friction, not
 * become another dashboard to manage.
 */
class TodayWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { render(context, manager, it) }
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
            ACTION_REFRESH -> refresh(context)
        }
    }

    companion object {
        const val ACTION_CHECK = "com.superflow.widget.CHECK"
        const val ACTION_REFRESH = "com.superflow.widget.REFRESH"
        const val EXTRA_HABIT_ID = "habitId"

        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, TodayWidget::class.java))
            ids?.forEach { render(context, manager, it) }
        }

        private fun render(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val repo = Repository.get(context)
            val date = Dates.today()
            val (done, total) = Insights.dayProgress(repo, date)
            val views = RemoteViews(context.packageName, R.layout.widget_today)

            views.setTextViewText(R.id.widget_date, Dates.humanDay(date))
            views.setTextViewText(
                R.id.widget_progress,
                if (total == 0) "Nothing scheduled" else "$done of $total done"
            )
            views.setProgressBar(R.id.widget_bar, total.coerceAtLeast(1), done, false)

            val checkIns = repo.checkInsFor(date).associateBy { it.habitId }
            val next = repo.habitsForDay(date).firstOrNull { checkIns[it.id] == null }

            if (next == null) {
                views.setTextViewText(R.id.widget_next,
                    if (total == 0) "Add a habit to begin" else "Everything is handled")
                views.setViewVisibility(R.id.widget_action, View.GONE)
            } else {
                views.setTextViewText(R.id.widget_next, next.tinyStart.ifBlank { next.title })
                views.setViewVisibility(R.id.widget_action, View.VISIBLE)
                val checkIntent = Intent(context, TodayWidget::class.java).apply {
                    action = ACTION_CHECK
                    putExtra(EXTRA_HABIT_ID, next.id)
                }
                views.setOnClickPendingIntent(
                    R.id.widget_action,
                    PendingIntent.getBroadcast(
                        context, next.id.hashCode(), checkIntent,
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
            manager.updateAppWidget(widgetId, views)
        }
    }
}
