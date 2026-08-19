package com.superflow.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Unit tests for the domain models: the habit contract, ladder levels,
 * check-in semantics and pause windows.
 */
class ModelsTest {

    /* --------------------------------------------------------- habit contract */

    @Test
    fun `contract uses the anchor when present`() {
        val h = Habit(title = "Walk 10 minutes", anchorText = "breakfast",
            standardVersion = "Walk 10 minutes", tinyStart = "Step outside")
        val c = h.contract()
        assertTrue(c.startsWith("After breakfast, I will Walk 10 minutes."))
        assertTrue(c.contains("On a hard day I can stop after Step outside."))
    }

    @Test
    fun `contract uses time and place when no anchor`() {
        val h = Habit(title = "Meditate", cueTime = "07:00", cuePlace = "bedroom",
            standardVersion = "Meditate for 5 minutes")
        val c = h.contract()
        assertTrue(c.startsWith("At 07:00 in bedroom, I will Meditate for 5 minutes."))
    }

    @Test
    fun `contract includes prep and reward`() {
        val h = Habit(title = "Study", standardVersion = "Study one page",
            environmentPrep = "Clear the desk", reward = "Coffee afterwards")
        val c = h.contract()
        assertTrue(c.contains("Beforehand: Clear the desk."))
        assertTrue(c.contains("Afterward: Coffee afterwards."))
    }

    @Test
    fun `contract strips the word after from anchors`() {
        val h = Habit(title = "Run", anchorText = "After breakfast")
        assertTrue(h.contract().startsWith("After breakfast, I will"))
    }

    /* -------------------------------------------------------------- ladder */

    @Test
    fun `level text falls back down the ladder`() {
        val h = Habit(title = "Write", standardVersion = "Write a paragraph",
            tinyStart = "Write one word")
        assertEquals("Write one word", h.levelText(Level.TINY))
        assertEquals("Write one word", h.levelText(Level.MINIMUM)) // no minimum set
        assertEquals("Write a paragraph", h.levelText(Level.STANDARD))
        assertEquals("Write a paragraph", h.levelText(Level.STRETCH)) // no stretch set
    }

    @Test
    fun `level weights order`() {
        assertTrue(Level.TINY.weight < Level.MINIMUM.weight)
        assertTrue(Level.MINIMUM.weight < Level.STANDARD.weight)
        assertTrue(Level.STANDARD.weight < Level.STRETCH.weight)
        assertEquals(Level.STANDARD, Level.from("standard"))
        assertEquals(Level.STANDARD, Level.from("STANDARD"))
        assertEquals(Level.STANDARD, Level.from("nope"))
    }

    /* ------------------------------------------------------------- check-ins */

    @Test
    fun `checkin success semantics`() {
        assertTrue(CheckIn(habitId = "h", date = "d", result = CheckInResult.DONE).isSuccess)
        assertTrue(CheckIn(habitId = "h", date = "d", result = CheckInResult.RESISTED).isSuccess)
        assertFalse(CheckIn(habitId = "h", date = "d", result = CheckInResult.SKIPPED).isSuccess)
        assertTrue(CheckIn(habitId = "h", date = "d", result = CheckInResult.MISSED).isMiss)
        assertTrue(CheckIn(habitId = "h", date = "d", result = CheckInResult.SLIPPED).isMiss)
        assertFalse(CheckIn(habitId = "h", date = "d", result = CheckInResult.SKIPPED).isMiss)
    }

    @Test
    fun `today habit derived flags`() {
        val h = Habit(title = "h")
        assertTrue(TodayHabit(h, null).open)
        assertFalse(TodayHabit(h, null).done)
        val done = CheckIn(habitId = "h", date = "d", result = CheckInResult.DONE)
        val t = TodayHabit(h, done, isReturning = true)
        assertTrue(t.done)
        assertTrue(t.isReturning)
        val skipped = CheckIn(habitId = "h", date = "d", result = CheckInResult.SKIPPED)
        assertTrue(TodayHabit(h, skipped).skipped)
        val missed = CheckIn(habitId = "h", date = "d", result = CheckInResult.MISSED)
        assertTrue(TodayHabit(h, missed).missed)
    }

    /* ---------------------------------------------------------------- pauses */

    @Test
    fun `pause window covers its range inclusive`() {
        val p = PauseWindow(habitId = null,
            startDate = "2026-08-10", endDate = "2026-08-12")
        assertTrue(p.covers(LocalDate.of(2026, 8, 10)))
        assertTrue(p.covers(LocalDate.of(2026, 8, 11)))
        assertTrue(p.covers(LocalDate.of(2026, 8, 12)))
        assertFalse(p.covers(LocalDate.of(2026, 8, 9)))
        assertFalse(p.covers(LocalDate.of(2026, 8, 13)))
    }

    @Test
    fun `pause window with bad dates covers nothing`() {
        val p = PauseWindow(habitId = null, startDate = "nope", endDate = "2026-08-12")
        assertFalse(p.covers(LocalDate.of(2026, 8, 11)))
    }

    /* ------------------------------------------------------------- life areas */

    @Test
    fun `life area lookup is case insensitive and safe`() {
        assertEquals(LifeArea.HEALTH, LifeArea.from("health"))
        assertEquals(LifeArea.HEALTH, LifeArea.from("HEALTH"))
        assertEquals(LifeArea.CUSTOM, LifeArea.from("unknown"))
        assertEquals(LifeArea.CUSTOM, LifeArea.from(null))
    }

    @Test
    fun `status parsing helpers default safely`() {
        assertEquals(Status.ACTIVE, Status.valueOf("ACTIVE".ifBlank { "ACTIVE" }))
    }
}
