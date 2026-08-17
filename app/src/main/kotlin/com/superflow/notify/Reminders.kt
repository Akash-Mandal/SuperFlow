package com.superflow.notify

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.superflow.data.Checkpoint
import com.superflow.data.Prefs
import com.superflow.data.Repo
import com.superflow.domain.Actor
import com.superflow.domain.CommandBus
import com.superflow.ui.MainActivity
import com.superflow.util.Dates
import com.superflow.util.jsonOf
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

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_HABITS, "Habit reminders", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Gentle nudges for the habits you scheduled" }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_CHECKPOINTS, "Checkpoints", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "Morning, midday and evening check-ins" }
        )
    }

    /** True when [hhmm] falls inside the configured quiet window. */
    fun inQuietHours(prefs: Prefs, hhmm: String): Boolean {
        val t = Dates.minutesOfDay(hhmm)
        val from = Dates.minutesOfDay(prefs.quietFrom)
        val to = Dates.minutesOfDay(prefs.quietTo)
        if (t < 0 || from < 0 || to < 0) return false
        return if (from <= to) t in from..to else (t >= from || t <= to)
    }

    fun rescheduleAll(context: Context) {
        ensureChannels(context)
        val prefs = Prefs.get(context)
        val repo = Repo.get(context)
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        // Clear previously scheduled slots.
        for (i in 0 until 64) {
            pendingFor(context, i, null)?.let { am.cancel(it) }
        }
        if (!prefs.remindersEnabled) return

        var budget = prefs.reminderBudget
        var slot = 0

        // Habit reminders, in schedule order, capped by the daily budget.
        val habits = repo.habits()
            .filter { it.reminderEnabled && it.cueTime.isNotBlank() && Dates.isValidTime(it.cueTime) }
            .sortedBy { Dates.minutesOfDay(it.cueTime) }
        for (h in habits) {
            if (budget <= 0) break
            if (inQuietHours(prefs, h.cueTime)) continue
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                putExtra("kind", "habit")
                putExtra("habitId", h.id)
                putExtra("title", h.title)
                putExtra("tiny", h.tinyStart)
            }
            schedule(context, am, slot++, h.cueTime, intent)
            budget--
        }

        // Checkpoints.
        if (prefs.checkpointsEnabled) {
            val cps = listOf(
                Checkpoint.MORNING to prefs.morningCheckpoint,
                Checkpoint.MIDDAY to prefs.middayCheckpoint,
                Checkpoint.EVENING to prefs.eveningCheckpoint
            )
            for ((cp, time) in cps) {
                if (!Dates.isValidTime(time) || inQuietHours(prefs, time)) continue
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
        try {
            am.setInexactRepeating(AlarmManager.RTC_WAKEUP, cal.timeInMillis,
                AlarmManager.INTERVAL_DAY, pi)
        } catch (e: Exception) {
            // Scheduling is best effort; the app never depends on it.
        }
    }

    private fun pendingFor(context: Context, slot: Int, intent: Intent?): PendingIntent? {
        val base = intent ?: Intent(context, ReminderReceiver::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        return try {
            PendingIntent.getBroadcast(context, 7000 + slot, base, flags)
        } catch (e: Exception) {
            null
        }
    }

    fun notify(context: Context, id: Int, channel: String, title: String, text: String,
               actions: List<Notification.Action> = emptyList()) {
        ensureChannels(context)
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val open = PendingIntent.getActivity(
            context, id, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        )
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(context, channel)
        else @Suppress("DEPRECATION") Notification.Builder(context)
        builder.setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
            .setAutoCancel(true)
            .setContentIntent(open)
        actions.forEach { builder.addAction(it) }
        nm.notify(id, builder.build())
    }

    fun action(context: Context, label: String, extras: Map<String, String>, requestCode: Int): Notification.Action {
        val intent = Intent(context, ReminderReceiver::class.java)
        extras.forEach { (k, v) -> intent.putExtra(k, v) }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        val pi = PendingIntent.getBroadcast(context, requestCode, intent, flags)
        return Notification.Action.Builder(null as android.graphics.drawable.Icon?, label, pi).build()
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
                if (!prefs.remindersEnabled || Reminders.inQuietHours(prefs, Dates.nowTime())) return
                val repo = Repo.get(context)
                if (repo.checkIn(habitId, Dates.today()) != null) return  // already handled
                val id = habitId.hashCode() and 0xFFFF
                Reminders.notify(
                    context, id, Reminders.CHANNEL_HABITS, title,
                    if (tiny.isNotBlank()) "Tiny start: $tiny" else "A small version counts.",
                    listOf(
                        Reminders.action(context, "Done",
                            mapOf("kind" to "action", "action" to "check_in", "habitId" to habitId), id * 3),
                        Reminders.action(context, "Tiny",
                            mapOf("kind" to "action", "action" to "tiny", "habitId" to habitId), id * 3 + 1),
                        Reminders.action(context, "Skip",
                            mapOf("kind" to "action", "action" to "skip", "habitId" to habitId), id * 3 + 2)
                    )
                )
            }

            "checkpoint" -> {
                if (!prefs.checkpointsEnabled) return
                if (Reminders.inQuietHours(prefs, Dates.nowTime())) return
                val cp = intent.getStringExtra("checkpoint") ?: return
                val bus = CommandBus.get(context)
                val res = bus.execute("run_checkpoint", jsonOf("checkpoint" to cp), Actor.SYSTEM)
                Reminders.notify(context, 9000 + cp.hashCode().and(0xFF),
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
                val nm = context.getSystemService(NotificationManager::class.java)
                nm?.cancel(habitId.hashCode() and 0xFFFF)
            }
        }
    }
}

/** Reminders are rescheduled after a reboot. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Reminders.rescheduleAll(context)
        }
    }
}
