package com.superflow.notify

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.superflow.AppBackground
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
 *
 * [rescheduleAll] is safe to call from any thread and any lifecycle: it hops
 * to a single serialized background lane, coalescing bursts into one run.
 */
object Reminders {

    private const val TAG = "Reminders"

    const val CHANNEL_HABITS = "superflow_habits"
    const val CHANNEL_CHECKPOINTS = "superflow_checkpoints"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_HABITS,
                context.getString(R.string.channel_habits),
                NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.channel_habits_desc)
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_CHECKPOINTS,
                context.getString(R.string.channel_checkpoints),
                NotificationManager.IMPORTANCE_LOW).apply {
                description = context.getString(R.string.channel_checkpoints_desc)
                setShowBadge(false)
            }
        )
    }

    /**
     * Pure quiet-hours predicate (unit-tested): `hhmm` is silent when it
     * falls inside the quiet window, which may wrap midnight (22:00-07:00).
     * Unparseable input is treated as "not quiet".
     */
    fun quietHoursActive(hhmm: String, from: String, to: String): Boolean {
        val t = Dates.minutesOfDay(hhmm)
        val f = Dates.minutesOfDay(from)
        val e = Dates.minutesOfDay(to)
        if (t < 0 || f < 0 || e < 0) return false
        return if (f <= e) t in f..e else (t >= f || t <= e)
    }

    fun inQuietHours(prefs: Prefs, hhmm: String): Boolean =
        quietHoursActive(hhmm, prefs.quietFrom, prefs.quietTo)

    /* ------------------------------------------------- reschedule (async) */

    @Volatile private var inFlight = false
    @Volatile private var pending = false

    /**
     * Non-blocking reschedule from any caller (activities, workers,
     * receivers, boot). Runs on the serialized AppBackground lane; calls made
     * while a run is in flight are coalesced into exactly one follow-up run,
     * so a burst (onCreate + onPause + receiver) cannot stack N alarm passes.
     */
    fun rescheduleAll(context: Context) {
        val ctx = context.applicationContext
        pending = true
        if (inFlight) return
        inFlight = true
        AppBackground.launch {
            try {
                do {
                    pending = false
                    rescheduleAllInternal(ctx)
                } while (pending)
            } finally {
                inFlight = false
            }
        }
    }

    /** Synchronous variant for workers and tests that must observe completion. */
    fun rescheduleAllNow(context: Context) {
        val ctx = context.applicationContext
        AppBackground.await { rescheduleAllInternal(ctx) }
    }

    private fun CoroutineScope.rescheduleAllInternal(context: Context) {
        try {
            ensureChannels(context)
            val prefs = Prefs.get(context)
            val repo = Repository.get(context)
            val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                ?: return

            for (i in 0 until MAX_SLOTS) pendingFor(context, i, null)?.let { am.cancel(it) }
            TodayWidget.refresh(context)
            if (!prefs.remindersEnabled) return

            var budget = prefs.reminderBudget
            var slot = 0

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

            // Environment prep reminders (§4): "Prep for [habit]: [environmentPrep]"
            // fires the evening before a morning habit, or a few hours before otherwise.
            val prepHabits = repo.habits()
                .filter { it.environmentPrep.isNotBlank() && Dates.isValidTime(it.cueTime) }
            for (h in prepHabits) {
                if (budget <= 0) break
                val prepTime = h.environmentPrepReminderTime
                    ?.takeIf { Dates.isValidTime(it) }
                    ?: defaultPrepTime(h.cueTime)
                if (inQuietHours(prefs, prepTime)) continue
                val intent = Intent(context, ReminderReceiver::class.java).apply {
                    putExtra("kind", "prep")
                    putExtra("habitId", h.id)
                    putExtra("title", h.title)
                    putExtra("prep", h.environmentPrep)
                }
                schedule(context, am, slot++, prepTime, intent)
                budget--
            }

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
        } catch (e: Exception) {
            // Reminders are optional: a scheduling failure must never take
            // the app down, but it is not silent either.
            Log.w(TAG, "Reminder reschedule failed", e)
        }
    }

    /**
     * Default prep time (§4): the evening before (21:00) when the cue is before
     * noon, otherwise two hours before the cue.
     */
    fun defaultPrepTime(cueTime: String): String {
        val minutes = Dates.minutesOfDay(cueTime)
        if (minutes < 0) return "21:00"
        val prep = if (minutes < 12 * 60) 21 * 60 else (minutes - 120).coerceAtLeast(0)
        val hh = (prep / 60).toString().padStart(2, '0')
        val mm = (prep % 60).toString().padStart(2, '0')
        return "$hh:$mm"
    }

    private fun schedule(context: Context, am: AlarmManager, slot: Int, hhmm: String, intent: Intent) {
        val minutes = Dates.minutesOfDay(hhmm)
        if (minutes < 0 || slot >= MAX_SLOTS) return
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
        } catch (e: SecurityException) {
            Log.w(TAG, "Not allowed to schedule reminder for slot $slot", e)
        }
    }

    private fun pendingFor(context: Context, slot: Int, intent: Intent?): PendingIntent? {
        val base = intent ?: Intent(context, ReminderReceiver::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return try {
            PendingIntent.getBroadcast(context, BASE_REQUEST + slot, base, flags)
        } catch (e: Exception) {
            Log.w(TAG, "Could not build pending intent for slot $slot", e)
            null
        }
    }

    private const val BASE_REQUEST = 7000
    private const val MAX_SLOTS = 64

    /* ------------------------------------------------------------- notify */

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
        try {
            NotificationManagerCompat.from(context).notify(id, builder.build())
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS denied on Android 13+: the user chose that;
            // reminders simply will not surface. Log for supportability.
            Log.w(TAG, "Not permitted to show notification $id", e)
        }
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

    private companion object {
        private const val TAG = "ReminderReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        // onReceive runs on the main thread; the body does database writes and
        // notification work, so it runs on the serialized background lane.
        val ctx = context.applicationContext
        AppBackground.launch {
            try {
                handle(ctx, intent)
            } catch (e: Exception) {
                Log.w(TAG, "Reminder delivery failed", e)
            }
        }
    }

    private fun handle(context: Context, intent: Intent) {
        val prefs = Prefs.get(context)
        when (intent.getStringExtra("kind")) {
            "habit" -> {
                val habitId = intent.getStringExtra("habitId") ?: return
                val title = intent.getStringExtra("title") ?: "Your habit"
                val tiny = intent.getStringExtra("tiny").orEmpty()
                if (!prefs.remindersEnabled) return
                if (Reminders.quietHoursActive(
                        SfTime.formatTime(java.time.LocalTime.now()),
                        prefs.quietFrom, prefs.quietTo
                    )
                ) return
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

            "prep" -> {
                val habitId = intent.getStringExtra("habitId") ?: return
                val title = intent.getStringExtra("title") ?: "Your habit"
                val prep = intent.getStringExtra("prep").orEmpty()
                if (!prefs.remindersEnabled || Reminders.inQuietHours(prefs, SfTime.formatTime(java.time.LocalTime.now()))) return
                val repo = Repository.get(context)
                if (repo.checkIn(habitId, SfTime.format(repo.clock.today())) != null) return
                val id = (habitId.hashCode() and 0xFFFF) + 4096
                Reminders.notify(context, id, Reminders.CHANNEL_HABITS,
                    "Prep for $title",
                    prep.ifBlank { "A little preparation now saves friction later." })
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
                TodayWidget.refresh(context, force = true)
            }
        }
    }
}

/** Reminders are rescheduled after a reboot. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            Reminders.rescheduleAll(context)
        }
    }
}
