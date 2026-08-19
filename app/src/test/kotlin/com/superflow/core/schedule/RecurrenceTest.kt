package com.superflow.core.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Unit tests for recurrence-rule decoding, encoding, natural-language parsing
 * and schedule activation — the rules that decide when a habit is owed.
 */
class RecurrenceTest {

    private val monday = LocalDate.of(2026, 8, 17) // a Monday

    /* ------------------------------------------------------------ encode/decode */

    @Test
    fun `decode of blank is every day`() {
        assertEquals(Recurrence.EVERY_DAY, Recurrence.decode(null))
        assertEquals(Recurrence.EVERY_DAY, Recurrence.decode(""))
        assertEquals(Recurrence.EVERY_DAY, Recurrence.decode("   "))
    }

    @Test
    fun `daily encodes and decodes`() {
        assertEquals("DAILY", Recurrence.Daily.encode())
        assertEquals(Recurrence.Daily, Recurrence.decode("DAILY"))
    }

    @Test
    fun `weekly round-trips in sorted order`() {
        val rule = Recurrence.Weekly(setOf(3, 1, 5))
        assertEquals("WEEKLY:1,3,5", rule.encode())
        assertEquals(rule, Recurrence.decode(rule.encode()))
    }

    @Test
    fun `weekly with invalid days falls back to every day`() {
        assertEquals(Recurrence.EVERY_DAY, Recurrence.decode("WEEKLY:9,0,13"))
        assertEquals(Recurrence.EVERY_DAY, Recurrence.decode("WEEKLY:"))
    }

    @Test
    fun `every n days round-trips`() {
        val rule = Recurrence.EveryNDays(3)
        assertEquals("EVERY_N:3", rule.encode())
        assertEquals(rule, Recurrence.decode(rule.encode()))
    }

    @Test
    fun `every n days clamps invalid interval`() {
        assertEquals(Recurrence.EveryNDays(1), Recurrence.decode("EVERY_N:0"))
        assertEquals(Recurrence.EveryNDays(1), Recurrence.decode("EVERY_N:banana"))
    }

    @Test
    fun `times per week is flexible and round-trips`() {
        val rule = Recurrence.TimesPerWeek(3)
        assertTrue(rule.isFlexible)
        assertEquals("TIMES_PER_WEEK:3", rule.encode())
        assertEquals(rule, Recurrence.decode(rule.encode()))
        assertFalse(Recurrence.Weekly(setOf(1)).isFlexible)
    }

    @Test
    fun `times per week is clamped to one through seven`() {
        assertEquals(Recurrence.TimesPerWeek(7), Recurrence.decode("TIMES_PER_WEEK:12"))
        assertEquals(Recurrence.TimesPerWeek(1), Recurrence.decode("TIMES_PER_WEEK:0"))
    }

    @Test
    fun `legacy numeric mask decodes`() {
        // Monday (1) and Wednesday (3) set.
        val mask = (1 shl 0) or (1 shl 2)
        assertEquals(Recurrence.Weekly(setOf(1, 3)), Recurrence.decode(mask.toString()))
        // Zero mask means every day, matching the v3 migration default.
        assertEquals(Recurrence.EVERY_DAY, Recurrence.decode("0"))
        assertEquals(Recurrence.EVERY_DAY, Recurrence.fromMask(0))
        assertEquals(Recurrence.Weekly(setOf(1, 2, 3, 4, 5, 6, 7)), Recurrence.fromMask(127))
    }

    @Test
    fun `unknown text falls back to every day`() {
        assertEquals(Recurrence.EVERY_DAY, Recurrence.decode("nonsense"))
    }

    /* -------------------------------------------------------------- parsing */

    @Test
    fun `parse natural language day words`() {
        assertEquals(Recurrence.EVERY_DAY, Recurrence.parse("daily"))
        assertEquals(Recurrence.EVERY_DAY, Recurrence.parse("Every Day"))
        assertEquals(Recurrence.WEEKDAYS, Recurrence.parse("weekdays"))
        assertEquals(Recurrence.WEEKENDS, Recurrence.parse("weekends"))
    }

    @Test
    fun `parse specific days`() {
        assertEquals(Recurrence.Weekly(setOf(1, 3, 5)), Recurrence.parse("mon, wed, fri"))
        assertEquals(Recurrence.Weekly(setOf(6, 7)), Recurrence.parse("sat and sun"))
    }

    @Test
    fun `parse every n days phrase`() {
        assertEquals(Recurrence.EveryNDays(2), Recurrence.parse("every 2 days"))
        assertEquals(Recurrence.EveryNDays(5), Recurrence.parse("every five days".replace("five", "5")))
    }

    @Test
    fun `parse times per week phrase`() {
        assertEquals(Recurrence.TimesPerWeek(3), Recurrence.parse("3 times a week"))
        assertEquals(Recurrence.TimesPerWeek(2), Recurrence.parse("2x week"))
    }

    @Test
    fun `parse blanks fall back to daily`() {
        assertEquals(Recurrence.EVERY_DAY, Recurrence.parse(""))
        assertEquals(Recurrence.EVERY_DAY, Recurrence.parse("   "))
    }

    /* ------------------------------------------------------------- activation */

    @Test
    fun `schedule respects start and end dates`() {
        val start = monday
        val schedule = Schedule(
            recurrence = Recurrence.Daily,
            startDate = start
        )
        assertFalse(schedule.activeOn(start.minusDays(1)))
        assertTrue(schedule.activeOn(start))
        assertTrue(schedule.activeOn(start.plusDays(30)))

        val bounded = Schedule(
            recurrence = Recurrence.Daily,
            startDate = start,
            endDate = start.plusDays(2)
        )
        assertTrue(bounded.activeOn(start.plusDays(2)))
        assertFalse(bounded.activeOn(start.plusDays(3)))
    }

    @Test
    fun `disabled schedule is never active`() {
        val schedule = Schedule(recurrence = Recurrence.Daily, enabled = false)
        assertFalse(schedule.activeOn(monday))
    }

    @Test
    fun `every n days is anchored to the start date`() {
        val start = monday
        val schedule = Schedule(
            recurrence = Recurrence.EveryNDays(2),
            startDate = start
        )
        assertTrue(schedule.activeOn(start))
        assertFalse(schedule.activeOn(start.plusDays(1)))
        assertTrue(schedule.activeOn(start.plusDays(2)))
        assertFalse(schedule.activeOn(start.plusDays(3)))
        assertTrue(schedule.activeOn(start.plusDays(4)))
    }

    @Test
    fun `times per week is active every day`() {
        val schedule = Schedule(recurrence = Recurrence.TimesPerWeek(3), startDate = monday)
        assertTrue(schedule.activeOn(monday))
        assertTrue(schedule.activeOn(monday.plusDays(3)))
    }

    @Test
    fun `labels are human friendly`() {
        assertEquals("Every day", Recurrence.EVERY_DAY.label())
        assertEquals("Weekdays", Recurrence.WEEKDAYS.label())
        assertEquals("Weekends", Recurrence.WEEKENDS.label())
        assertEquals("3× a week", Recurrence.TimesPerWeek(3).label())
        assertEquals("Every 2 days", Recurrence.EveryNDays(2).label())
    }
}
