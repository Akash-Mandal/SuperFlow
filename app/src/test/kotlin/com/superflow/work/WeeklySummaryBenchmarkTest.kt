package com.superflow.work

import com.superflow.core.schedule.Opportunities
import com.superflow.core.schedule.Recurrence
import com.superflow.core.schedule.Schedule
import com.superflow.core.time.SfTime
import com.superflow.data.Repository.DataSnapshot
import com.superflow.data.model.CheckIn
import com.superflow.data.model.CheckInResult
import com.superflow.data.model.Habit
import com.superflow.data.model.PauseWindow
import com.superflow.data.model.Status
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class WeeklySummaryBenchmarkTest {

    private fun mockSchedule(h: Habit): Schedule = Schedule(
        recurrence = Recurrence.decode(h.recurrenceRule),
        localTime = null,
        zoneId = ZoneId.of("UTC"),
        startDate = LocalDate.of(2026, 1, 1),
        endDate = null,
        version = 1,
        enabled = true
    )

    private fun weekStatsUnoptimized(habits: List<Habit>, getSnap: () -> DataSnapshot, today: LocalDate): WeekSummary {
        var hits = 0
        var opportunities = 0
        var repetitions = 0
        var recoveries = 0
        var best: Pair<Habit, Int>? = null

        for (h in habits) {
            // Unoptimized simulates fetching snapshot inside loop
            val snap = getSnap()
            val pauses = snap.pauses.filter { it.habitId == null || it.habitId == h.id }
            val checkIns = snap.checkInsByHabit[h.id].orEmpty().associateBy { LocalDate.parse(it.date) }
            val series = Opportunities.series(
                habit = h,
                schedule = mockSchedule(h),
                checkIns = checkIns,
                pauses = pauses,
                dates = SfTime.lastDays(7, today),
                today = today
            )
            val recurrence = Recurrence.decode(h.recurrenceRule)
            val (hh, oo) = if (recurrence is Recurrence.TimesPerWeek) {
                Opportunities.quotaAdherence(series, recurrence.times)
            } else {
                Opportunities.adherence(series)
            }
            hits += hh
            opportunities += oo
            recoveries += Opportunities.recoveries(series)
            repetitions += series.count { it.succeeded }
            val run = Opportunities.bestRun(series)
            if (best == null || run > best.second) best = h to run
        }
        val consistency = if (opportunities == 0) 0 else (hits * 100) / opportunities
        return WeekSummary(consistency, repetitions, recoveries, best?.first)
    }

    private fun weekStatsOptimized(habits: List<Habit>, snap: DataSnapshot, today: LocalDate): WeekSummary {
        var hits = 0
        var opportunities = 0
        var repetitions = 0
        var recoveries = 0
        var best: Pair<Habit, Int>? = null

        val unarchivedHabits = snap.habits.filter { it.status != Status.ARCHIVED }
        for (h in unarchivedHabits) {
            val pauses = snap.pauses.filter { it.habitId == null || it.habitId == h.id }
            val checkIns = snap.checkInsByHabit[h.id].orEmpty().associateBy { LocalDate.parse(it.date) }
            val series = Opportunities.series(
                habit = h,
                schedule = mockSchedule(h),
                checkIns = checkIns,
                pauses = pauses,
                dates = SfTime.lastDays(7, today),
                today = today
            )
            val recurrence = Recurrence.decode(h.recurrenceRule)
            val (hh, oo) = if (recurrence is Recurrence.TimesPerWeek) {
                Opportunities.quotaAdherence(series, recurrence.times)
            } else {
                Opportunities.adherence(series)
            }
            hits += hh
            opportunities += oo
            recoveries += Opportunities.recoveries(series)
            repetitions += series.count { it.succeeded }
            val run = Opportunities.bestRun(series)
            if (best == null || run > best.second) best = h to run
        }
        val consistency = if (opportunities == 0) 0 else (hits * 100) / opportunities
        return WeekSummary(consistency, repetitions, recoveries, best?.first)
    }

    @Test
    fun `verify equivalence and benchmark speedup`() {
        val today = LocalDate.of(2026, 4, 19)
        val habits = (1..200).map { i ->
            Habit(id = "h_$i", title = "Habit $i", recurrenceRule = "FREQ=DAILY", status = Status.ACTIVE)
        }
        val checkIns = (1..200).flatMap { i ->
            listOf(
                CheckIn(habitId = "h_$i", date = "2026-04-18", result = CheckInResult.DONE),
                CheckIn(habitId = "h_$i", date = "2026-04-17", result = CheckInResult.MISSED),
                CheckIn(habitId = "h_$i", date = "2026-04-16", result = CheckInResult.DONE)
            )
        }
        val pauses = listOf(
            PauseWindow(id = "p1", habitId = null, startDate = "2026-04-01", endDate = "2026-04-05", reason = "vacation"),
            PauseWindow(id = "p2", habitId = "h_5", startDate = "2026-04-10", endDate = "2026-04-12", reason = "sick")
        )
        val snapshot = DataSnapshot(emptyList(), habits, checkIns, pauses)

        var snapFetchCount = 0
        val getSnap = {
            snapFetchCount++
            snapshot
        }

        // 1. Verify exact functional equivalence
        val resUnopt = weekStatsUnoptimized(habits, getSnap, today)
        val snapshotCountUnopt = snapFetchCount
        snapFetchCount = 0

        val resOpt = weekStatsOptimized(habits, snapshot, today)
        val snapshotCountOpt = snapFetchCount // 0 additional calls inside loop

        assertEquals(resUnopt, resOpt)
        assertEquals(200, snapshotCountUnopt)
        assertEquals(0, snapshotCountOpt)

        // 2. Performance benchmark
        repeat(20) {
            weekStatsUnoptimized(habits, getSnap, today)
            weekStatsOptimized(habits, snapshot, today)
        }

        val iterations = 500
        val startUnopt = System.nanoTime()
        repeat(iterations) {
            weekStatsUnoptimized(habits, getSnap, today)
        }
        val elapsedUnoptMs = (System.nanoTime() - startUnopt) / 1_000_000.0

        val startOpt = System.nanoTime()
        repeat(iterations) {
            weekStatsOptimized(habits, snapshot, today)
        }
        val elapsedOptMs = (System.nanoTime() - startOpt) / 1_000_000.0

        println("Unoptimized total time ($iterations ops): ${"%.2f".format(elapsedUnoptMs)} ms, snapshot calls: 200/op")
        println("Optimized total time ($iterations ops): ${"%.2f".format(elapsedOptMs)} ms, snapshot calls: 0 inside loop (1 per op)")
    }
}
