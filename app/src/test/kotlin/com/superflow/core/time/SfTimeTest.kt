package com.superflow.core.time

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Unit tests for the pure time helpers: parsing, daylight-saving resolution,
 * week math and the UI time buckets.
 */
class SfTimeTest {

    /* -------------------------------------------------------------- parsing */

    @Test
    fun `parse and format iso dates`() {
        assertEquals(LocalDate.of(2026, 8, 19), SfTime.parseDate("2026-08-19"))
        assertEquals(LocalDate.of(2026, 8, 19), SfTime.parseDate(" 2026-08-19 "))
        assertNull(SfTime.parseDate("19/08/2026"))
        assertNull(SfTime.parseDate(""))
        assertNull(SfTime.parseDate("not-a-date"))
        assertEquals("2026-08-19", SfTime.format(LocalDate.of(2026, 8, 19)))
    }

    @Test
    fun `parse times strictly`() {
        assertEquals(LocalTime.of(7, 30), SfTime.parseTime("07:30"))
        assertEquals(LocalTime.of(23, 59), SfTime.parseTime("23:59"))
        assertNull(SfTime.parseTime("24:00"))
        assertNull(SfTime.parseTime("07:60"))
        assertNull(SfTime.parseTime("7"))
        assertNull(SfTime.parseTime("07:30:00"))
        assertNull(SfTime.parseTime(""))
    }

    @Test
    fun `minutes of day`() {
        assertEquals(450, SfTime.minutesOfDay("07:30"))
        assertEquals(0, SfTime.minutesOfDay("00:00"))
        assertEquals(-1, SfTime.minutesOfDay("bad"))
        assertTrue(SfTime.isValidTime("12:00"))
        assertFalse(SfTime.isValidTime("12"))
    }

    /* ------------------------------------------------- daylight saving edges */

    @Test
    fun `resolve pushes a gap time forward`() {
        // Europe/Berlin 2026-03-29: clocks jump 02:00 -> 03:00.
        val zone = ZoneId.of("Europe/Berlin")
        val gap = SfTime.resolve(LocalDate.of(2026, 3, 29), LocalTime.of(2, 30), zone)
        // 02:30 does not exist; the result must be at/after 03:00.
        assertTrue(gap.toLocalTime().toSecondOfDay() >= LocalTime.of(3, 0).toSecondOfDay())
    }

    @Test
    fun `resolve picks the earlier offset in an overlap`() {
        // Europe/Berlin 2026-10-25: clocks fall back 03:00 -> 02:00.
        val zone = ZoneId.of("Europe/Berlin")
        val overlap = SfTime.resolve(LocalDate.of(2026, 10, 25), LocalTime.of(2, 30), zone)
        assertEquals("+02:00", overlap.offset.toString())
    }

    @Test
    fun `resolve is identity outside transitions`() {
        val zone = ZoneId.of("Europe/Berlin")
        val zdt = SfTime.resolve(LocalDate.of(2026, 8, 19), LocalTime.of(12, 0), zone)
        assertEquals(LocalTime.of(12, 0), zdt.toLocalTime())
    }

    /* ------------------------------------------------------------ week math */

    @Test
    fun `start of week honours the configured start day`() {
        val wednesday = LocalDate.of(2026, 8, 19)
        assertEquals(LocalDate.of(2026, 8, 17), SfTime.startOfWeek(wednesday, DayOfWeek.MONDAY))
        assertEquals(LocalDate.of(2026, 8, 16), SfTime.startOfWeek(wednesday, DayOfWeek.SUNDAY))
    }

    @Test
    fun `last days is inclusive of the end date`() {
        val end = LocalDate.of(2026, 8, 19)
        val days = SfTime.lastDays(3, end)
        assertEquals(listOf(end.minusDays(2), end.minusDays(1), end), days)
    }

    @Test
    fun `days between is signed`() {
        val a = LocalDate.of(2026, 1, 1)
        val b = LocalDate.of(2026, 1, 11)
        assertEquals(10L, SfTime.daysBetween(a, b))
        assertEquals(-10L, SfTime.daysBetween(b, a))
    }

    @Test
    fun `iso day of week is monday based`() {
        assertEquals(1, SfTime.isoDayOfWeek(LocalDate.of(2026, 8, 17)))
        assertEquals(7, SfTime.isoDayOfWeek(LocalDate.of(2026, 8, 23)))
    }

    /* ------------------------------------------------------------ buckets */

    @Test
    fun `time buckets`() {
        assertEquals(DayBucket.ANYTIME, SfTime.bucketOf(null))
        assertEquals(DayBucket.MORNING, SfTime.bucketOf(LocalTime.of(6, 0)))
        assertEquals(DayBucket.MORNING, SfTime.bucketOf(LocalTime.of(11, 59)))
        assertEquals(DayBucket.DAY, SfTime.bucketOf(LocalTime.of(12, 0)))
        assertEquals(DayBucket.DAY, SfTime.bucketOf(LocalTime.of(16, 59)))
        assertEquals(DayBucket.EVENING, SfTime.bucketOf(LocalTime.of(17, 0)))
    }

    @Test
    fun `greetings`() {
        assertEquals(Greeting.MORNING, SfTime.greetingFor(LocalTime.of(9, 0)))
        assertEquals(Greeting.AFTERNOON, SfTime.greetingFor(LocalTime.of(14, 0)))
        assertEquals(Greeting.EVENING, SfTime.greetingFor(LocalTime.of(20, 0)))
    }

    @Test
    fun `relative timestamps`() {
        val now = Instant.ofEpochMilli(1_700_000_000_000)
        assertEquals("just now", SfTime.relative(now.minusSeconds(30), now))
        assertEquals("5m ago", SfTime.relative(now.minusSeconds(300), now))
        assertEquals("3h ago", SfTime.relative(now.minusSeconds(3 * 3600), now))
        assertEquals("2d ago", SfTime.relative(now.minusSeconds(2 * 86_400), now))
        assertEquals("1w ago", SfTime.relative(now.minusSeconds(604_800 + 3_600), now))
    }

    @Test
    fun `fixed clock advances and reports zone`() {
        val clock = FixedClock(Instant.parse("2026-08-19T00:00:00Z"))
        assertEquals(LocalDate.of(2026, 8, 19), clock.today())
        clock.advance(1, java.time.temporal.ChronoUnit.DAYS)
        assertEquals(LocalDate.of(2026, 8, 20), clock.today())
        clock.setZone(ZoneId.of("Pacific/Kiritimati")) // UTC+14
        assertEquals(LocalDate.of(2026, 8, 20), clock.today()) // +14h same instant
    }
}
