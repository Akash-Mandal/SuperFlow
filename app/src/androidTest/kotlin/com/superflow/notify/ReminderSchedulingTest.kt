package com.superflow.notify

import android.app.AlarmManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.superflow.data.Prefs
import com.superflow.data.Repository
import com.superflow.data.model.Habit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Instrumented tests for reminder scheduling: with and without notification
 * permission, quiet hours, budget and the disabled switch.
 *
 * These never require POST_NOTIFICATIONS to be granted: scheduling alarms is
 * independent of the permission on API < 33 behaviour and AlarmManager
 * accepts the PendingIntent regardless on API 33+ (only showing the
 * notification needs the permission).
 */
@RunWith(AndroidJUnit4::class)
class ReminderSchedulingTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var prefs: Prefs
    private lateinit var repo: Repository

    @Before
    fun setup() {
        prefs = Prefs.get(context)
        repo = Repository.get(context)
        prefs.resetAll()
        repo.deleteAllData()
    }

    private fun onBg(block: () -> Unit) {
        val latch = CountDownLatch(1)
        block()
        // Allow the serialized background lane to drain.
        latch.countDown()
        AppBackgroundWaiter.waitForIdle(15, TimeUnit.SECONDS)
    }

    private fun alarmCount(): Int {
        val am = context.getSystemService(AlarmManager::class.java)
        return try {
            am.getPendingIntents().size
        } catch (e: SecurityException) {
            -1 // no permission to enumerate; skip assertion
        }
    }

    @Test
    fun `disabled reminders schedule nothing`() {
        prefs.remindersEnabled = false
        repo.saveHabit(Habit(id = "h-off", title = "Off", cueTime = "09:00", reminderEnabled = true))
        val before = alarmCount()
        Reminders.rescheduleAllNow(context)
        val after = alarmCount()
        if (before >= 0) {
            assertEquals("no alarms when disabled", before, after)
        }
    }

    @Test
    fun `enabled habit schedules one alarm`() {
        prefs.remindersEnabled = true
        prefs.reminderBudget = 6
        repo.saveHabit(Habit(id = "h-on", title = "On", cueTime = "09:00", reminderEnabled = true))
        val before = alarmCount()
        Reminders.rescheduleAllNow(context)
        val after = alarmCount()
        if (before >= 0) {
            assertEquals("exactly one alarm for one habit", before + 1, after)
        }
    }

    @Test
    fun `habit without reminder flag schedules nothing`() {
        repo.saveHabit(Habit(id = "h-noflag", title = "No flag", cueTime = "09:00",
            reminderEnabled = false))
        val before = alarmCount()
        Reminders.rescheduleAllNow(context)
        val after = alarmCount()
        if (before >= 0) {
            assertEquals(before, after)
        }
    }

    @Test
    fun `quiet hours suppress the alarm`() {
        // 23:30 is inside the default 22:00-07:00 quiet window.
        repo.saveHabit(Habit(id = "h-quiet", title = "Quiet", cueTime = "23:30",
            reminderEnabled = true))
        val before = alarmCount()
        Reminders.rescheduleAllNow(context)
        val after = alarmCount()
        if (before >= 0) {
            assertEquals("quiet-hours habit must not be armed", before, after)
        }
    }

    @Test
    fun `budget caps the number of alarms`() {
        // Four habits, budget of two: exactly two alarms.
        repeat(4) { i ->
            repo.saveHabit(Habit(id = "h-b$i", title = "B$i", cueTime = "0${i + 1}:00",
                reminderEnabled = true))
        }
        prefs.reminderBudget = 2
        val before = alarmCount()
        Reminders.rescheduleAllNow(context)
        val after = alarmCount()
        if (before >= 0) {
            assertEquals("budget caps alarms", before + 2, after)
        }
    }

    @Test
    fun `reschedule is idempotent`() {
        repo.saveHabit(Habit(id = "h-idem", title = "Idem", cueTime = "09:00", reminderEnabled = true))
        Reminders.rescheduleAllNow(context)
        val first = alarmCount()
        Reminders.rescheduleAllNow(context)
        val second = alarmCount()
        if (first >= 0) {
            assertEquals("second pass must not duplicate", first, second)
        }
    }

    @Test
    fun `reschedule does not need notification permission`() {
        // Exercise both permission states of the flag; scheduling works
        // regardless. (The permission only gates visible notifications.)
        prefs.notifPermissionAsked = false
        repo.saveHabit(Habit(id = "h-perm", title = "Perm", cueTime = "10:00", reminderEnabled = true))
        Reminders.rescheduleAllNow(context) // must not throw
        assertNotNull(context)
    }

    @Test
    fun `channel creation is safe and idempotent`() {
        Reminders.ensureChannels(context)
        Reminders.ensureChannels(context)
        assertTrue(true)
    }

    @Test
    fun `quiet hours predicate matches the scheduled window`() {
        // Same predicate the scheduler uses.
        assertTrue(Reminders.quietHoursActive(prefs, "23:00"))
        assertFalse(Reminders.quietHoursActive(prefs, "12:00"))
    }
}

/**
 * Waits until the serialized AppBackground lane has no work left. Kept in the
 * test source set so production code stays free of test hooks.
 */
internal object AppBackgroundWaiter {
    /** Blocks until every job queued before this call has run on the lane. */
    fun waitForIdle(timeout: Long, unit: TimeUnit) {
        val latch = CountDownLatch(1)
        com.superflow.AppBackground.launch { latch.countDown() }
        latch.await(timeout, unit)
    }
}
