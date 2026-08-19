package com.superflow.core.schedule

import com.superflow.data.model.CheckIn
import com.superflow.data.model.CheckInResult
import com.superflow.data.model.Habit
import com.superflow.data.model.Level
import com.superflow.data.model.PauseWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/**
 * Unit tests for the opportunity model: adherence, runs, recoveries and the
 * "never miss twice" trigger. These rules must never invent misses for
 * paused days, planned skips or flexible (N-times-per-week) habits.
 */
class OpportunitiesTest {

    private val today = LocalDate.of(2026, 8, 19) // a Wednesday
    private val start = today.minusDays(30)

    private fun habit(recurrence: Recurrence = Recurrence.Daily) = Habit(
        id = "h1",
        title = "Walk",
        recurrenceRule = recurrence.encode(),
        cueTime = "07:30",
        startDate = SfIso(start)
    )

    private fun schedule(habit: Habit = habit()) = Schedule(
        recurrence = Recurrence.decode(habit.recurrenceRule),
        localTime = LocalTime.of(7, 30),
        startDate = start,
        enabled = true
    )

    private fun checkIn(date: LocalDate, result: CheckInResult = CheckInResult.DONE) = CheckIn(
        habitId = "h1",
        date = date.format(Iso),
        result = result,
        level = Level.STANDARD
    )

    private val Iso = java.time.format.DateTimeFormatter.ISO_LOCAL_DATE

    private fun series(
        checkIns: List<CheckIn> = emptyList(),
        pauses: List<PauseWindow> = emptyList(),
        habit: Habit = habit(),
        days: Int = 30
    ): List<Opportunity> {
        val h = habit
        return Opportunities.series(
            habit = h,
            schedule = schedule(h),
            checkIns = checkIns.associateBy { LocalDate.parse(it.date) },
            pauses = pauses,
            dates = com.superflow.core.time.SfTime.lastDays(days, today),
            today = today
        )
    }

    /* ------------------------------------------------------------- statuses */

    @Test
    fun `completed checkin marks the day completed`() {
        val s = series(listOf(checkIn(today)))
        val op = s.first { it.date == today }
        assertEquals(OpportunityStatus.COMPLETED, op.status)
        assertTrue(op.succeeded)
    }

    @Test
    fun `planned skip is not a miss`() {
        val s = series(listOf(checkIn(today, CheckInResult.SKIPPED)))
        val op = s.first { it.date == today }
        assertEquals(OpportunityStatus.SKIPPED_PLANNED, op.status)
        assertFalse(op.succeeded)
    }

    @Test
    fun `resisted counts as success for reduce habits`() {
        val s = series(listOf(checkIn(today, CheckInResult.RESISTED)))
        val op = s.first { it.date == today }
        assertEquals(OpportunityStatus.COMPLETED, op.status)
    }

    @Test
    fun `slipped counts as a miss`() {
        val s = series(listOf(checkIn(today, CheckInResult.SLIPPED)))
        val op = s.first { it.date == today }
        assertEquals(OpportunityStatus.MISSED, op.status)
    }

    @Test
    fun `past day with no record is an implicit miss`() {
        val s = series()
        val op = s.first { it.date == today.minusDays(2) }
        assertEquals(OpportunityStatus.MISSED, op.status)
    }

    @Test
    fun `today is pending until acted on`() {
        val s = series()
        val op = s.first { it.date == today }
        assertEquals(OpportunityStatus.PENDING, op.status)
        assertFalse(op.succeeded)
    }

    @Test
    fun `paused days are paused not missed`() {
        val pause = PauseWindow(
            habitId = null,
            startDate = today.minusDays(2).format(Iso),
            endDate = today.minusDays(1).format(Iso),
            reason = "holiday"
        )
        val s = series(pauses = listOf(pause))
        assertEquals(OpportunityStatus.PAUSED, s.first { it.date == today.minusDays(2) }.status)
        // An outside-the-window day is unaffected.
        assertEquals(OpportunityStatus.MISSED, s.first { it.date == today.minusDays(5) }.status)
    }

    @Test
    fun `weekly habit days that are not scheduled are excluded`() {
        val h = habit(Recurrence.Weekly(setOf(1))) // Mondays only
        val s = series(habit = h)
        val wed = s.first { it.date == today }
        assertEquals(OpportunityStatus.NOT_SCHEDULED, wed.status)
        val mon = s.first { it.date == today.minusDays(2) }
        assertEquals(OpportunityStatus.MISSED, mon.status)
    }

    /* ------------------------------------------------------------ adherence */

    @Test
    fun `adherence excludes paused and pending days`() {
        // Wednesday-only habit over the 30-day window:
        //  Wed 7/22 -> implicit miss
        //  Wed 7/29 -> paused (no record, inside the pause window)
        //  Wed 8/5  -> missed (explicit SLIPPED)
        //  Wed 8/12 -> done
        //  Wed 8/19 (today) -> pending
        val h = habit(Recurrence.Weekly(setOf(3)))
        val checkIns = listOf(
            checkIn(today.minusDays(7)),                       // 8/12 done
            checkIn(today.minusDays(14), CheckInResult.SLIPPED) // 8/5 missed
        )
        val pause = PauseWindow(habitId = null,
            startDate = today.minusDays(21).format(Iso), // 7/29
            endDate = today.minusDays(21).format(Iso))
        val s = series(checkIns = checkIns, pauses = listOf(pause), habit = h)
        val (hits, total) = Opportunities.adherence(s)
        assertEquals(1, hits)
        assertEquals(3, total) // done + 2 real misses; paused and pending excluded
    }

    @Test
    fun `quota adherence for a full week with no checkins`() {
        // One aligned week (Mon 8/10 .. Sun 8/16), 3 times a week, nothing done.
        val h = habit(Recurrence.TimesPerWeek(3))
        val weekEnd = LocalDate.of(2026, 8, 16) // a Sunday
        val s = Opportunities.series(
            habit = h,
            schedule = schedule(h),
            checkIns = emptyMap(),
            pauses = emptyList(),
            dates = com.superflow.core.time.SfTime.lastDays(7, weekEnd),
            today = weekEnd
        )
        val (hits, target) = Opportunities.quotaAdherence(s, 3)
        assertEquals(0, hits)
        assertEquals(3, target)
    }

    @Test
    fun `quota adherence caps hits at the quota per week`() {
        // One aligned week, all 7 days done, but the habit only owes 2.
        val h = habit(Recurrence.TimesPerWeek(2))
        val weekEnd = LocalDate.of(2026, 8, 16) // a Sunday
        val checkIns = (0 until 7).map { checkIn(weekEnd.minusDays((6 - it).toLong())) }
        val s = Opportunities.series(
            habit = h,
            schedule = schedule(h),
            checkIns = checkIns.associateBy { LocalDate.parse(it.date) },
            pauses = emptyList(),
            dates = com.superflow.core.time.SfTime.lastDays(7, weekEnd),
            today = weekEnd
        )
        val (hits, target) = Opportunities.quotaAdherence(s, 2)
        assertEquals(2, hits)
        assertEquals(2, target)
    }

    /* ---------------------------------------------------------------- runs */

    @Test
    fun `current run stops at a miss and ignores skips`() {
        val checkIns = listOf(
            checkIn(today),
            checkIn(today.minusDays(1)),
            checkIn(today.minusDays(2), CheckInResult.SKIPPED),
            checkIn(today.minusDays(3)),
            checkIn(today.minusDays(4), CheckInResult.MISSED)
        )
        val s = series(checkIns = checkIns)
        assertEquals(3, Opportunities.currentRun(s, today))
    }

    @Test
    fun `pending today does not extend but does not break the run`() {
        // Today is still open: it cannot extend the run, but the completed
        // days before it still form the current run.
        val checkIns = listOf(
            checkIn(today.minusDays(1)),
            checkIn(today.minusDays(2))
        )
        val s = series(checkIns = checkIns)
        assertEquals(2, Opportunities.currentRun(s, today))

        // A miss before the open today ends the run.
        val missed = listOf(
            checkIn(today.minusDays(1)),
            checkIn(today.minusDays(2), CheckInResult.MISSED)
        )
        assertEquals(1, Opportunities.currentRun(series(checkIns = missed), today))
    }

    @Test
    fun `best run finds the longest streak`() {
        val checkIns = (0 until 4).map { checkIn(today.minusDays((it + 6).toLong())) } +
                (0 until 2).map { checkIn(today.minusDays((it + 1).toLong())) }
        val s = series(checkIns = checkIns)
        assertEquals(4, Opportunities.bestRun(s))
    }

    @Test
    fun `recovery is a success right after a miss`() {
        val checkIns = listOf(
            checkIn(today.minusDays(2), CheckInResult.MISSED),
            checkIn(today.minusDays(1)),
            checkIn(today)
        )
        val s = series(checkIns = checkIns)
        assertEquals(1, Opportunities.recoveries(s))
    }

    @Test
    fun `misses in a row counts the tail of real misses`() {
        val checkIns = listOf(
            checkIn(today.minusDays(1), CheckInResult.SLIPPED),
            checkIn(today.minusDays(2), CheckInResult.MISSED),
            checkIn(today.minusDays(3))
        )
        val s = series(checkIns = checkIns)
        assertEquals(2, Opportunities.missesInARow(s, today))
    }

    /* --------------------------------------------------------- never miss twice */

    @Test
    fun `needsReturn fires only after a real miss with today still open`() {
        val missed = listOf(checkIn(today.minusDays(1), CheckInResult.MISSED))
        assertTrue(Opportunities.needsReturn(series(checkIns = missed), today))
    }

    @Test
    fun `needsReturn does not fire when yesterday was done`() {
        val done = listOf(checkIn(today.minusDays(1)))
        assertFalse(Opportunities.needsReturn(series(checkIns = done), today))
    }

    @Test
    fun `needsReturn does not fire when today is already handled`() {
        val both = listOf(
            checkIn(today.minusDays(1), CheckInResult.MISSED),
            checkIn(today)
        )
        assertFalse(Opportunities.needsReturn(series(checkIns = both), today))
    }

    @Test
    fun `needsReturn does not fire when the previous day was not scheduled`() {
        val h = habit(Recurrence.Weekly(setOf(3))) // Wednesdays; today is a Wednesday
        val s = Opportunities.series(
            habit = h,
            schedule = schedule(h),
            checkIns = emptyMap(),
            pauses = emptyList(),
            dates = com.superflow.core.time.SfTime.lastDays(10, today),
            today = today
        )
        // Last Wednesday (7 days ago) is implicitly missed, but the previous
        // real opportunity is that one — so the trigger should fire.
        assertTrue(Opportunities.needsReturn(s, today))
    }

    @Test
    fun `paused day before today is transparent to the trigger`() {
        val pause = PauseWindow(habitId = null,
            startDate = today.minusDays(1).format(Iso),
            endDate = today.minusDays(1).format(Iso))
        val missedTwoAgo = listOf(checkIn(today.minusDays(2), CheckInResult.MISSED))
        val s = series(checkIns = missedTwoAgo, pauses = listOf(pause))
        // The day between the miss and today is paused, so it does not count
        // as the "previous real opportunity" — the trigger still sees the miss.
        assertTrue(Opportunities.needsReturn(s, today))
    }

    /* --------------------------------------------------------------- helpers */

    private fun SfIso(d: LocalDate): String = d.format(Iso)
}
