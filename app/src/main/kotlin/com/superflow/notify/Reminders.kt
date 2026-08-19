package com.superflow.notify

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.superflow.R
import com.superflow.data.Prefs
import com.superflow.data.Repository
import com.superflow.data.model.Checkpoint
import com.superflow.domain.Actor
import com.superflow.domain.CommandBus
import com.superflow.ui.MainActivity
import com.superflow.core.time.SfTime
import com.superflow.util.Dates
import com.superflow.util.jsonOf
import com.superflow.widget.TodayWidget
import java.util.Calendar

/**
 * Local reminders.
 *
 * Quiet hours, a total daily budget, action buttons and no guilt language.
 * A reminder invites; it never scolds.
 */
object Reminders {

    const val CHANNEL_HABITS = "superflow_habits"
    const val CHANNEL_CHECKPOINTS = "superflow_checkpoints"
    const val CHANNEL_REVIEWS = "superflow_reviews"
    const val CHANNEL_MILESTONES = "superflow_milestones"
    const val CHANNEL_AI_SUGGESTIONS = "superflow_ai_suggestions"
    const val CHANNEL_WEEKLY_SUMMARY = "superflow_weekly_summary"
    const val CHANNEL_BACKUP = "superflow_backup"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return

        fun channel(id: String, name: Int, desc: Int, importance: Int, vibrate: Boolean) {
            nm.createNotificationChannel(
                NotificationChannel(id, context.getString(name), importance).apply {
                    description = context.getString(desc)
                    setShowBadge(false)
                    enableVibration(vibrate)
                }
            )
        }

        channel(CHANNEL_HABITS, R.string.channel_habits, R.string.channel_habits_desc,
            NotificationManager.IMPORTANCE_DEFAULT, true)
        channel(CHANNEL_CHECKPOINTS, R.string.channel_checkpoints, R.string.channel_checkpoints_desc,
            NotificationManager.IMPORTANCE_DEFAULT, false)
        channel(CHANNEL_REVIEWS, R.string.channel_reviews, R.string.channel_reviews_desc,
            NotificationManager.IMPORTANCE_DEFAULT, false)
        channel(CHANNEL_MILESTONES, R.string.channel_milestones, R.string.channel_milestones_desc,
            NotificationManager.IMPORTANCE_MIN, false)
        channel(CHANNEL_AI_SUGGESTIONS, R.string.channel_ai_suggestions,
            R.string.channel_ai_suggestions_desc, NotificationManager.IMPORTANCE_MIN, false)
        channel(CHANNEL_WEEKLY_SUMMARY, R.string.channel_weekly_summary,
            R.string.channel_weekly_summary_desc, NotificationManager.IMPORTANCE_DEFAULT, false)
        channel(CHANNEL_BACKUP, R.string.channel_backup, R.string.channel_backup_desc,
            NotificationManager.IMPORTANCE_MIN, false)
    }

    /**
     * The quiet window in effect for a given day of week. Weekdays and
     * weekends can each have their own hours; an unset per-day window falls
     * back to the single legacy window.
     */
    fun quietBounds(prefs: Prefs, dayOfWeek: java.time.DayOfWeek): Pair<String, String> {
        val weekend = dayOfWeek == java.time.DayOfWeek.SATURDAY ||
                dayOfWeek == java.time.DayOfWeek.SUNDAY
        val from = if (weekend) prefs.quietWeekendFrom.ifBlank { prefs.quietFrom }
        else prefs.quietWeekdayFrom.ifBlank { prefs.quietFrom }
        val to = if (weekend) prefs.quietWeekendTo.ifBlank { prefs.quietTo }
        else prefs.quietWeekdayTo.ifBlank { prefs.quietTo }
        return from to to
    }

    fun inQuietHours(prefs: Prefs, hhmm: String, dayOfWeek: java.time.DayOfWeek): Boolean {
        val t = Dates.minutesOfDay(hhmm)
        val (fromText, toText) = quietBounds(prefs, dayOfWeek)
        val from = Dates.minutesOfDay(fromText)
        val to = Dates.minutesOfDay(toText)
        if (t < 0 || from < 0 || to < 0) return false
        return if (from <= to) t in from..to else (t >= from || t <= to)
    }

    fun inQuietHours(prefs: Prefs, hhmm: String): Boolean =
        inQuietHours(prefs, hhmm, java.time.LocalDate.now().dayOfWeek)

    fun rescheduleAll(context: Context) {
        ensureChannels(context)
        val prefs = Prefs.get(context)
        val repo = Repository.get(context)
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        for (i in 0 until 64) pendingFor(context, i, null)?.let { am.cancel(it) }
        TodayWidget.refresh(context)
        if (!prefs.remindersEnabled) return

        var budget = prefs.reminderBudget
        var slot = 0

        // Quiet hours are enforced at fire time (the receiver re-checks the
        // actual day of week), not at schedule time, so a reminder whose cue
        // falls inside a weekend-only quiet window still fires on weekdays.
        val habits = repo.habits()
            .filter { it.reminderEnabled && it.cueTime.isNotBlank() && Dates.isValidTime(it.cueTime) }
            .sortedBy { Dates.minutesOfDay(it.cueTime) }
        for (h in habits) {
            if (budget <= 0) break
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                putExtra("kind", "habit")
                putExtra("habitId", h.id)
                putExtra("title", h.title)
                putExtra("tiny", h.tinyStart)
            }
            schedule(context, am, slot++, h.cueTime, intent)
            budget--
        }

        if (prefs.checkpointsEnabled) {
            val cps = listOf(
                Checkpoint.MORNING to prefs.morningCheckpoint,
                Checkpoint.MIDDAY to prefs.middayCheckpoint,
                Checkpoint.EVENING to prefs.eveningCheckpoint
            )
            for ((cp, time) in cps) {
                if (!Dates.isValidTime(time)) continue
                val intent = Intent(context, ReminderReceiver::class.java).apply {
                    putExtra("kind", "checkpoint")
                    putExtra("checkpoint", cp.name)
                }
                schedule(context, am, slot++, time, intent)
            }
        }
    }

    private fun schedule(context: Context, am: AlarmManager, slot: Int, hhmm: String, intent: Intent) {
        val minutes = Dates.minutesOfDay(hhmm)
        if (minutes < 0 || slot >= 64) return
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, minutes / 60)
            set(Calendar.MINUTE, minutes % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        val pi = pendingFor(context, slot, intent) ?: return
        runCatching {
            am.setInexactRepeating(AlarmManager.RTC_WAKEUP, cal.timeInMillis,
                AlarmManager.INTERVAL_DAY, pi)
        }
    }

    private fun pendingFor(context: Context, slot: Int, intent: Intent?): PendingIntent? {
        val base = intent ?: Intent(context, ReminderReceiver::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return runCatching {
            PendingIntent.getBroadcast(context, 7000 + slot, base, flags)
        }.getOrNull()
    }

    fun notify(
        context: Context,
        id: Int,
        channel: String,
        title: String,
        text: String,
        actions: List<NotificationCompat.Action> = emptyList()
    ) {
        ensureChannels(context)
        val open = PendingIntent.getActivity(
            context, id, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(context, channel)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(R.drawable.ic_bolt)
            .setColor(0xFF3A7D5C.toInt())
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(open)
        actions.forEach { builder.addAction(it) }
        runCatching { NotificationManagerCompat.from(context).notify(id, builder.build()) }
    }

    fun action(
        context: Context,
        icon: Int,
        label: String,
        extras: Map<String, String>,
        requestCode: Int
    ): NotificationCompat.Action {
        val intent = Intent(context, ReminderReceiver::class.java)
        extras.forEach { (k, v) -> intent.putExtra(k, v) }
        val pi = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action.Builder(icon, label, pi).build()
    }
}

/** Delivers reminders and handles their action buttons. */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val prefs = Prefs.get(context)
        when (intent.getStringExtra("kind")) {
            "habit" -> {
                val habitId = intent.getStringExtra("habitId") ?: return
                val title = intent.getStringExtra("title") ?: "Your habit"
                val tiny = intent.getStringExtra("tiny").orEmpty()
                if (!prefs.remindersEnabled || Reminders.inQuietHours(prefs, SfTime.formatTime(java.time.LocalTime.now()))) return
                val repo = Repository.get(context)
                if (repo.checkIn(habitId, SfTime.format(repo.clock.today())) != null) return
                val id = habitId.hashCode() and 0xFFFF
                Reminders.notify(
                    context, id, Reminders.CHANNEL_HABITS, title,
                    if (tiny.isNotBlank()) "Tiny start: $tiny" else "A small version counts.",
                    listOf(
                        Reminders.action(context, R.drawable.ic_check, "Done",
                            mapOf("kind" to "action", "action" to "check_in", "habitId" to habitId),
                            id * 3),
                        Reminders.action(context, R.drawable.ic_bolt, "Tiny",
                            mapOf("kind" to "action", "action" to "tiny", "habitId" to habitId),
                            id * 3 + 1),
                        Reminders.action(context, R.drawable.ic_close, "Skip",
                            mapOf("kind" to "action", "action" to "skip", "habitId" to habitId),
                            id * 3 + 2)
                    )
                )
            }

            "checkpoint" -> {
                if (!prefs.checkpointsEnabled) return
                if (Reminders.inQuietHours(prefs, SfTime.formatTime(java.time.LocalTime.now()))) return
                val cp = intent.getStringExtra("checkpoint") ?: return
                val res = CommandBus.get(context)
                    .execute("run_checkpoint", jsonOf("checkpoint" to cp), Actor.SYSTEM)
                Reminders.notify(context, 9000 + (cp.hashCode() and 0xFF),
                    Reminders.CHANNEL_CHECKPOINTS,
                    "${cp.lowercase().replaceFirstChar { it.uppercase() }} checkpoint",
                    res.message)
            }

            "action" -> {
                val habitId = intent.getStringExtra("habitId") ?: return
                val bus = CommandBus.get(context)
                when (intent.getStringExtra("action")) {
                    "check_in" -> bus.execute("check_in",
                        jsonOf("habit" to habitId, "level" to "STANDARD"), Actor.USER)
                    "tiny" -> bus.execute("check_in",
                        jsonOf("habit" to habitId, "level" to "TINY"), Actor.USER)
                    "skip" -> bus.execute("skip_habit", jsonOf("habit" to habitId), Actor.USER)
                }
                NotificationManagerCompat.from(context).cancel(habitId.hashCode() and 0xFFFF)
                TodayWidget.refresh(context)
            }
        }
    }
}

/** Reminders are rescheduled after a reboot. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Reminders.rescheduleAll(context)
        }
    }
}
