package com.superflow.util

import com.superflow.core.time.SfTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class DatesTest {

    @Test
    fun `humanDay formats LocalDate correctly`() {
        val date = LocalDate.of(2026, 8, 19)
        assertEquals(SfTime.humanDay(date), Dates.humanDay(date))
    }

    @Test
    fun `humanDay formats valid ISO date string and returns raw string for invalid ISO`() {
        val isoDate = "2026-08-19"
        val invalidIso = "invalid-date"

        val expectedValid = SfTime.humanDay(LocalDate.of(2026, 8, 19))
        assertEquals(expectedValid, Dates.humanDay(isoDate))
        assertEquals(invalidIso, Dates.humanDay(invalidIso))
    }

    @Test
    fun `shortDay formats LocalDate correctly`() {
        val date = LocalDate.of(2026, 8, 19)
        assertEquals(SfTime.shortDay(date), Dates.shortDay(date))
    }

    @Test
    fun `shortDay formats valid ISO date string and returns raw string for invalid ISO`() {
        val isoDate = "2026-08-19"
        val invalidIso = "invalid-date"

        val expectedValid = SfTime.shortDay(LocalDate.of(2026, 8, 19))
        assertEquals(expectedValid, Dates.shortDay(isoDate))
        assertEquals(invalidIso, Dates.shortDay(invalidIso))
    }

    @Test
    fun `dayLetter delegates to SfTime`() {
        val date = LocalDate.of(2026, 8, 19)
        assertEquals(SfTime.dayLetter(date), Dates.dayLetter(date))
    }

    @Test
    fun `stamp formats epoch millis into zone date time string`() {
        val zone = ZoneId.of("UTC")
        val instant = Instant.parse("2026-08-19T10:15:00Z")
        val millis = instant.toEpochMilli()

        val expected = SfTime.stamp(instant, zone)
        assertEquals(expected, Dates.stamp(millis, zone))
    }

    @Test
    fun `relativeStamp formats difference between past millis and now`() {
        val nowMillis = 1_700_000_000_000L
        val pastMillis = nowMillis - (5 * 60 * 1000L) // 5 minutes ago

        assertEquals("5m ago", Dates.relativeStamp(pastMillis, nowMillis))
    }

    @Test
    fun `isValidTime validates time strings`() {
        assertTrue(Dates.isValidTime("07:30"))
        assertTrue(Dates.isValidTime("23:59"))
        assertFalse(Dates.isValidTime("24:00"))
        assertFalse(Dates.isValidTime("invalid"))
    }

    @Test
    fun `minutesOfDay calculates total minutes`() {
        assertEquals(450, Dates.minutesOfDay("07:30"))
        assertEquals(0, Dates.minutesOfDay("00:00"))
        assertEquals(-1, Dates.minutesOfDay("invalid"))
    }

    @Test
    fun `format formats LocalDate to ISO string`() {
        val date = LocalDate.of(2026, 8, 19)
        assertEquals("2026-08-19", Dates.format(date))
    }
}
