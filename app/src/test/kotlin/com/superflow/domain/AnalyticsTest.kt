package com.superflow.domain

import com.superflow.core.time.DayBucket
import com.superflow.data.model.CheckIn
import com.superflow.data.model.CheckInResult
import com.superflow.data.model.Habit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AnalyticsTest {

    private fun habit(id: String, cue: String) = Habit(id = id, title = "H $id", cueTime = cue)

    private fun done(habitId: String, date: String) =
        CheckIn(habitId = habitId, date = date, result = CheckInResult.DONE, createdAt = 1L)

    private fun missed(habitId: String, date: String) =
        CheckIn(habitId = habitId, date = date, result = CheckInResult.MISSED, createdAt = 1L)

    @Test
    fun `time-of-day groups by cue time`() {
        val today = LocalDate.parse("2026-08-26")
        val habits = listOf(
            habit("m", "07:30"),
            habit("e", "19:00"),
        )
        val checkIns = listOf(done("m", "2026-08-26"), done("e", "2026-08-25"))
        val patterns = Analytics.timeOfDayPatterns(habits, checkIns, today, days = 2)
        assertEquals(DayBucket.values().size, patterns.size)
        val morning = patterns.first { it.bucket == DayBucket.MORNING }
        assertTrue(morning.opportunities >= 1)
    }

    @Test
    fun `recovery median is null without recoveries`() {
        val hist = Analytics.recoveryGaps(
            listOf(done("h", "2026-08-20"), done("h", "2026-08-21")), "h"
        )
        assertEquals(null, hist.medianGapDays)
        assertEquals(0, hist.sampleSize)
    }

    @Test
    fun `recovery gaps measure miss to next success`() {
        val hist = Analytics.recoveryGaps(
            listOf(
                missed("h", "2026-08-20"),
                done("h", "2026-08-21"),
                missed("h", "2026-08-22"),
                done("h", "2026-08-24"),
            ), "h"
        )
        assertEquals(listOf(1, 2), hist.gaps)
        assertEquals(1.5, hist.medianGapDays!!, 0.01)
    }

    @Test
    fun `weekly bands handle empty and flat series`() {
        assertTrue(Analytics.weeklyBands(emptyList()).isEmpty())
        val flat = List(14) { 0.5 }
        val bands = Analytics.weeklyBands(flat, weeks = 2)
        assertEquals(2, bands.size)
        assertEquals(0.5, bands[0].median, 0.001)
    }

    @Test
    fun `time-of-day never crashes on empty habits`() {
        val patterns = Analytics.timeOfDayPatterns(emptyList(), emptyList(), LocalDate.now())
        assertEquals(DayBucket.values().size, patterns.size)
        patterns.forEach { assertEquals(0.0, it.rate, 0.001) }
    }
}
