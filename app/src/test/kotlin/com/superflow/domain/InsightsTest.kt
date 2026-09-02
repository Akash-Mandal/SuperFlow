package com.superflow.domain

import com.superflow.core.schedule.OpportunityStatus
import com.superflow.core.time.FixedClock
import com.superflow.core.time.SuperFlowClock
import com.superflow.data.Repository
import com.superflow.data.Repository.DataSnapshot
import com.superflow.data.model.CheckIn
import com.superflow.data.model.CheckInResult
import com.superflow.data.model.Habit
import com.superflow.data.model.Level
import com.superflow.data.model.PauseWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class InsightsTest {

    private val today = LocalDate.of(2026, 8, 20)

    private fun createTestRepository(clock: SuperFlowClock = FixedClock(Instant.now())): Repository {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val field = unsafeClass.getDeclaredField("theUnsafe")
        field.isAccessible = true
        val unsafe = field.get(null)
        val allocateMethod = unsafeClass.getMethod("allocateInstance", Class::class.java)
        val repo = allocateMethod.invoke(unsafe, Repository::class.java) as Repository
        val clockField = Repository::class.java.getDeclaredField("clock")
        clockField.isAccessible = true
        clockField.set(repo, clock)
        return repo
    }

    private fun habit(id: String = "h1", title: String = "Exercise") = Habit(
        id = id,
        title = title,
        recurrenceRule = "FREQ=DAILY",
        cueTime = "08:00",
        startDate = "2026-08-01"
    )

    @Test
    fun `seriesFor with 0 days returns empty list`() {
        val repo = createTestRepository()
        val h = habit()
        val snap = DataSnapshot(
            identities = emptyList(),
            habits = listOf(h),
            checkIns = emptyList(),
            pauses = emptyList()
        )

        val series = Insights.seriesFor(snap, repo, h, days = 0, today = today)
        assertTrue(series.isEmpty())
    }

    @Test
    fun `seriesFor with negative days returns empty list`() {
        val repo = createTestRepository()
        val h = habit()
        val snap = DataSnapshot(
            identities = emptyList(),
            habits = listOf(h),
            checkIns = emptyList(),
            pauses = emptyList()
        )

        val series = Insights.seriesFor(snap, repo, h, days = -5, today = today)
        assertTrue(series.isEmpty())
    }

    @Test
    fun `seriesFor with 1 day returns single opportunity for today`() {
        val repo = createTestRepository()
        val h = habit()
        val snap = DataSnapshot(
            identities = emptyList(),
            habits = listOf(h),
            checkIns = emptyList(),
            pauses = emptyList()
        )

        val series = Insights.seriesFor(snap, repo, h, days = 1, today = today)
        assertEquals(1, series.size)
        assertEquals(today, series[0].date)
        assertEquals(OpportunityStatus.PENDING, series[0].status)
    }

    @Test
    fun `seriesFor filters pauses correctly for target habit`() {
        val repo = createTestRepository()
        val h1 = habit(id = "h1")
        val globalPause = PauseWindow(
            id = "p1",
            habitId = null,
            startDate = "2026-08-18",
            endDate = "2026-08-18",
            reason = "Vacation"
        )
        val habit1Pause = PauseWindow(
            id = "p2",
            habitId = "h1",
            startDate = "2026-08-19",
            endDate = "2026-08-19",
            reason = "Rest"
        )
        val habit2Pause = PauseWindow(
            id = "p3",
            habitId = "h2",
            startDate = "2026-08-17",
            endDate = "2026-08-17",
            reason = "Injured"
        )

        val snap = DataSnapshot(
            identities = emptyList(),
            habits = listOf(h1),
            checkIns = emptyList(),
            pauses = listOf(globalPause, habit1Pause, habit2Pause)
        )

        val series = Insights.seriesFor(snap, repo, h1, days = 4, today = today)
        val byDate = series.associateBy { it.date }

        assertEquals(OpportunityStatus.MISSED, byDate[LocalDate.of(2026, 8, 17)]?.status)
        assertEquals(OpportunityStatus.PAUSED, byDate[LocalDate.of(2026, 8, 18)]?.status)
        assertEquals(OpportunityStatus.PAUSED, byDate[LocalDate.of(2026, 8, 19)]?.status)
        assertEquals(OpportunityStatus.PENDING, byDate[today]?.status)
    }

    @Test
    fun `seriesFor correctly maps check-ins by habit ID for target habit`() {
        val repo = createTestRepository()
        val h1 = habit(id = "h1")
        val ci1 = CheckIn(
            id = "c1",
            habitId = "h1",
            date = "2026-08-19",
            result = CheckInResult.DONE,
            level = Level.STANDARD
        )
        val ci2 = CheckIn(
            id = "c2",
            habitId = "h2",
            date = "2026-08-18",
            result = CheckInResult.DONE,
            level = Level.STANDARD
        )

        val snap = DataSnapshot(
            identities = emptyList(),
            habits = listOf(h1),
            checkIns = listOf(ci1, ci2),
            pauses = emptyList()
        )

        val series = Insights.seriesFor(snap, repo, h1, days = 3, today = today)
        val byDate = series.associateBy { it.date }

        assertEquals(OpportunityStatus.MISSED, byDate[LocalDate.of(2026, 8, 18)]?.status)
        assertEquals(OpportunityStatus.COMPLETED, byDate[LocalDate.of(2026, 8, 19)]?.status)
        assertEquals(ci1, byDate[LocalDate.of(2026, 8, 19)]?.checkIn)
    }

    @Test
    fun `seriesFor builds correct 7 day window ending on today`() {
        val repo = createTestRepository()
        val h = habit()
        val snap = DataSnapshot(
            identities = emptyList(),
            habits = listOf(h),
            checkIns = emptyList(),
            pauses = emptyList()
        )

        val series = Insights.seriesFor(snap, repo, h, days = 7, today = today)
        assertEquals(7, series.size)
        assertEquals(today.minusDays(6), series.first().date)
        assertEquals(today, series.last().date)
    }
}
