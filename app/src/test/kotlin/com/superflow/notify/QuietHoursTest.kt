package com.superflow.notify

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure quiet-hours predicate used by reminder scheduling
 * and delivery (no Android context required).
 */
class QuietHoursTest {

    // Default product quiet hours: 22:00 -> 07:00 (wraps midnight).
    private val from = "22:00"
    private val to = "07:00"

    @Test
    fun `late night is quiet`() {
        assertTrue(Reminders.quietHoursActive("23:30", from, to))
        assertTrue(Reminders.quietHoursActive("00:00", from, to))
        assertTrue(Reminders.quietHoursActive("06:59", from, to))
    }

    @Test
    fun `boundary handling`() {
        assertTrue(Reminders.quietHoursActive("22:00", from, to)) // from inclusive
        assertTrue(Reminders.quietHoursActive("07:00", from, to)) // to inclusive
        assertFalse(Reminders.quietHoursActive("07:01", from, to))
        assertFalse(Reminders.quietHoursActive("21:59", from, to))
    }

    @Test
    fun `daytime is not quiet`() {
        assertFalse(Reminders.quietHoursActive("08:00", from, to))
        assertFalse(Reminders.quietHoursActive("12:00", from, to))
        assertFalse(Reminders.quietHoursActive("21:59", from, to))
    }

    @Test
    fun `non-wrapping window`() {
        assertTrue(Reminders.quietHoursActive("13:30", "13:00", "14:00"))
        assertFalse(Reminders.quietHoursActive("12:59", "13:00", "14:00"))
        assertFalse(Reminders.quietHoursActive("14:01", "13:00", "14:00"))
    }

    @Test
    fun `zero-length window is only that minute`() {
        assertTrue(Reminders.quietHoursActive("09:00", "09:00", "09:00"))
        assertFalse(Reminders.quietHoursActive("09:01", "09:00", "09:00"))
    }

    @Test
    fun `invalid input is not quiet`() {
        assertFalse(Reminders.quietHoursActive("nope", from, to))
        assertFalse(Reminders.quietHoursActive("25:00", from, to))
        assertFalse(Reminders.quietHoursActive("12:00", "bad", to))
        assertFalse(Reminders.quietHoursActive("12:00", from, "bad"))
    }
}
