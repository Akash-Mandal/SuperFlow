package com.superflow.domain

import com.superflow.data.model.CheckIn
import com.superflow.data.model.CheckInResult
import com.superflow.data.model.Checkpoint
import com.superflow.data.model.EnergyLog
import com.superflow.data.model.FocusItem
import com.superflow.data.model.JournalEntry
import com.superflow.data.model.Level
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DayReplayTest {

    private fun checkIn(
        habitId: String,
        result: CheckInResult,
        createdAt: Long,
        note: String = "",
    ) = CheckIn(habitId = habitId, date = "2026-08-26", result = result,
        level = Level.STANDARD, note = note, createdAt = createdAt)

    /* ------------------------------------------------------------- ordering */

    @Test
    fun `events sort by time within the day`() {
        val replay = DayReplay.build(
            checkIns = listOf(
                checkIn("a", CheckInResult.DONE, createdAt = 9_000L),
                checkIn("b", CheckInResult.DONE, createdAt = 8_000L),
            ),
            journal = emptyList(),
            focus = emptyList(),
            energy = emptyList(),
        )
        assertTrue(replay.first().timestampMs <= replay.last().timestampMs)
        assertEquals(2, replay.size)
    }

    @Test
    fun `timeless focus completions open the day`() {
        val replay = DayReplay.build(
            checkIns = listOf(checkIn("a", CheckInResult.DONE, createdAt = 5_000L)),
            journal = emptyList(),
            focus = listOf(FocusItem(date = "2026-08-26", habitId = null, title = "Plan week", done = true)),
            energy = emptyList(),
        )
        assertEquals(DayReplay.EventKind.FOCUS_DONE, replay.first().kind)
        assertEquals("Plan week", replay.first().title)
    }

    @Test
    fun `undone focus pins do not appear`() {
        val replay = DayReplay.build(
            checkIns = emptyList(),
            journal = emptyList(),
            focus = listOf(FocusItem(date = "2026-08-26", habitId = null, title = "Someday", done = false)),
            energy = emptyList(),
        )
        assertTrue(replay.isEmpty())
    }

    /* ------------------------------------------------------- kind mapping */

    @Test
    fun `results map to honest event kinds`() {
        val replay = DayReplay.build(
            checkIns = listOf(
                checkIn("done", CheckInResult.DONE, 1_000),
                checkIn("res", CheckInResult.RESISTED, 2_000),
                checkIn("miss", CheckInResult.MISSED, 3_000),
                checkIn("slip", CheckInResult.SLIPPED, 4_000),
                checkIn("skip", CheckInResult.SKIPPED, 5_000),
            ),
            journal = emptyList(),
            focus = emptyList(),
            energy = emptyList(),
        )
        assertEquals(DayReplay.EventKind.CHECK_IN, replay[0].kind)
        assertEquals(DayReplay.EventKind.CHECK_IN, replay[1].kind)
        assertEquals(DayReplay.EventKind.MISS, replay[2].kind)
        assertEquals(DayReplay.EventKind.MISS, replay[3].kind)
        assertEquals(DayReplay.EventKind.SKIP, replay[4].kind)
    }

    /* -------------------------------------------------------- composition */

    @Test
    fun `journal closes the day after same-millisecond actions`() {
        val t = 10_000L
        val replay = DayReplay.build(
            checkIns = listOf(checkIn("a", CheckInResult.DONE, t)),
            journal = listOf(JournalEntry(date = "2026-08-26", content = "Reflection.", createdAt = t)),
            focus = emptyList(),
            energy = emptyList(),
        )
        // Tie-break: actions before reflections.
        assertEquals(DayReplay.EventKind.CHECK_IN, replay[0].kind)
        assertEquals(DayReplay.EventKind.JOURNAL, replay[1].kind)
    }

    @Test
    fun `energy logs order by checkpoint when clock times are absent`() {
        // All three written with identical timestamps: checkpoint order decides.
        val t = 7_000L
        val replay = DayReplay.build(
            checkIns = emptyList(),
            journal = emptyList(),
            focus = emptyList(),
            energy = listOf(
                EnergyLog(date = "2026-08-26", checkpoint = Checkpoint.EVENING, energy = 3, createdAt = t),
                EnergyLog(date = "2026-08-26", checkpoint = Checkpoint.MORNING, energy = 4, createdAt = t),
            ),
        )
        assertTrue(replay[0].title.contains("4/5"))
        assertTrue(replay[1].title.contains("3/5"))
    }

    @Test
    fun `habit titles resolve through the provided lookup`() {
        val replay = DayReplay.build(
            checkIns = listOf(checkIn("h-12345678", CheckInResult.DONE, 1_000)),
            journal = emptyList(),
            focus = emptyList(),
            energy = emptyList(),
            habitTitle = { if (it == "h-12345678") "Morning walk" else it },
        )
        assertTrue(replay.single().title.contains("Morning walk"))
    }

    /* ---------------------------------------------------------- emptiness */

    @Test
    fun `an empty day is an empty replay - never fabricated content`() {
        val replay = DayReplay.build(
            checkIns = emptyList(), journal = emptyList(),
            focus = emptyList(), energy = emptyList(),
        )
        assertTrue(replay.isEmpty())
    }
}
