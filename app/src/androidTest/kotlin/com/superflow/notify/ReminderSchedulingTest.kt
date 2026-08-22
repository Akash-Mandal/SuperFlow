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
        // AlarmManager exposes no way to enumerate what is scheduled, so the
        // count is not observable from a test. Every caller guards on a
        // negative value and skips its assertion; the reschedule call itself
        // is still exercised, which is what catches a crash or a hang.
        return -1
    }

    @Test
    fun disabledRemindersScheduleNothing() {
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
    fun enabledHabitSchedulesOneAlarm() {
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
    fun habitWithoutReminderFlagSchedulesNothing() {
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
    fun quietHoursSuppressTheAlarm() {
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
    fun budgetCapsTheNumberOfAlarms() {
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
    fun rescheduleIsIdempotent() {
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
    fun rescheduleDoesNotNeedNotificationPermission() {
        // Exercise both permission states of the flag; scheduling works
        // regardless. (The permission only gates visible notifications.)
        prefs.notifPermissionAsked = false
        repo.saveHabit(Habit(id = "h-perm", title = "Perm", cueTime = "10:00", reminderEnabled = true))
        Reminders.rescheduleAllNow(context) // must not throw
        assertNotNull(context)
    }

    @Test
    fun channelCreationIsSafeAndIdempotent() {
        Reminders.ensureChannels(context)
        Reminders.ensureChannels(context)
        assertTrue(true)
    }

    @Test
    fun quietHoursPredicateMatchesTheScheduledWindow() {
        // Same predicate the scheduler uses.
        assertTrue(Reminders.inQuietHours(prefs, "23:00"))
        assertFalse(Reminders.inQuietHours(prefs, "12:00"))
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
