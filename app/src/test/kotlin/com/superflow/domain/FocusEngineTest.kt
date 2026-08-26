package com.superflow.domain

import com.superflow.data.model.CheckIn
import com.superflow.data.model.CheckInResult
import com.superflow.data.model.Habit
import com.superflow.data.model.HabitStats
import com.superflow.data.model.TodayHabit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusEngineTest {

    private fun habit(
        id: String = "h1",
        minutes: Int = 10,
        identityId: String? = null,
    ) = Habit(id = id, title = "Habit $id", estimatedMinutes = minutes, identityId = identityId)

    private fun open(h: Habit, checkIn: CheckIn? = null) =
        TodayHabit(habit = h, checkIn = checkIn)

    private fun done(h: Habit) = TodayHabit(
        habit = h,
        checkIn = CheckIn(habitId = h.id, date = "2026-08-26", result = CheckInResult.DONE),
    )

    private fun stats(
        id: String,
        misses: Int = 0,
        consistency: Int = 50,
        opportunities: Int = 30,
        run: Int = 0,
    ) = HabitStats(
        habit = habit(id), repetitions = 0, currentRun = run, bestRun = run,
        consistency30 = consistency, opportunities30 = opportunities,
        recoveries = 0, missesInARow = misses, needsReturn = misses > 0, lastDone = null,
    )

    /* ------------------------------------------------------- basic filtering */

    @Test
    fun `done and skipped habits never rank`() {
        val a = habit("a")
        val ranked = FocusEngine.rank(
            today = listOf(done(a)),
            statsOf = { null },
        )
        assertTrue(ranked.isEmpty())
        assertNull(FocusEngine.focus(listOf(done(a)), { null }))
    }

    @Test
    fun `empty day has no focus`() {
        assertNull(FocusEngine.focus(emptyList(), { null }))
    }

    /* ------------------------------------------------------------- ordering */

    @Test
    fun `missed habits outrank neutral ones`() {
        val risky = habit("risky")
        val calm = habit("calm")
        val ranked = FocusEngine.rank(
            today = listOf(open(calm), open(risky)),
            statsOf = { if (it == "risky") stats("risky", misses = 2) else null },
        )
        assertEquals("risky", ranked.first().habit.id)
        assertTrue(ranked.first().reasons.isNotEmpty())
    }

    @Test
    fun `identity-linked habits get the identity weight`() {
        val linked = habit("linked", identityId = "i1")
        val plain = habit("plain")
        val ranked = FocusEngine.rank(listOf(open(plain), open(linked)), { null })
        assertEquals("linked", ranked.first().habit.id)
    }

    @Test
    fun `momentum uses consistency only when there is enough data`() {
        val thin = stats("thin", consistency = 100, opportunities = 2)
        val rich = stats("rich", consistency = 20, opportunities = 30)
        val ranked = FocusEngine.rank(
            today = listOf(open(habit("thin")), open(habit("rich"))),
            statsOf = { if (it == "thin") thin else rich },
        )
        // Thin data must stay at momentum 1.0; rich low consistency drops below it.
        assertEquals("thin", ranked.first().habit.id)
    }

    /* -------------------------------------------------------------- capacity */

    @Test
    fun `unlogged energy is neutral`() {
        assertEquals(1f, FocusEngine.capacityFactor(30, energy = null))
    }

    @Test
    fun `tiny actions shine on low-energy days`() {
        val tiny = FocusEngine.capacityFactor(5, energy = 1)
        val heavy = FocusEngine.capacityFactor(45, energy = 1)
        assertTrue(tiny > 1f && heavy < 1f)
    }

    @Test
    fun `energy factors stay bounded around one`() {
        for (minutes in listOf(1, 5, 10, 15, 20, 45)) {
            for (energy in 1..5) {
                val f = FocusEngine.capacityFactor(minutes, energy)
                assertTrue("$minutes/$energy -> $f", f in 0.8f..1.2f)
            }
        }
    }

    /* -------------------------------------------------- fatigue & dismissal */

    @Test
    fun `fatigue lowers all scores but cannot reorder equal candidates`() {
        val a = habit("a")
        val base = FocusEngine.rank(listOf(open(a)), { null }, checkedSoFar = 0)
        val tired = FocusEngine.rank(listOf(open(a)), { null }, checkedSoFar = 4)
        assertTrue(tired.single().score < base.single().score)
    }

    @Test
    fun `twice-dismissed candidate is dropped from focus but still listed`() {
        val a = habit("a")
        val b = habit("b")
        val dismissals = mapOf("a" to 2)
        val ranked = FocusEngine.rank(listOf(open(a), open(b)), { null }, dismissals = dismissals)
        val focus = FocusEngine.focus(listOf(open(a), open(b)), { null }, dismissals = dismissals)
        assertEquals("b", focus?.habit?.id)
        assertEquals("a", ranked.firstOrNull { it.habit.id == "a" }?.habit?.id) // still visible in list
    }

    /* ---------------------------------------------------------- explainability */

    @Test
    fun `every score above neutral carries at least one reason`() {
        val riskyLinked = habit("x", identityId = "i1")
        val c = FocusEngine.rank(
            listOf(open(riskyLinked)),
            { stats("x", misses = 1, run = 3) },
        ).single()
        assertTrue(c.score > FocusEngine.NEUTRAL_SCORE)
        assertTrue(c.reasons.isNotEmpty())
    }

    /* ------------------------------------------- permutation stability (F-P5) */

    @Test
    fun `ranking is stable under input permutation`() {
        val a = open(habit("a"))
        val b = open(habit("b"), )
        val c = open(habit("c"))
        val s = { id: String ->
            when (id) {
                "a" -> stats("a", misses = 1)
                "b" -> stats("b", consistency = 80)
                else -> null
            }
        }
        val r1 = FocusEngine.rank(listOf(a, b, c), s).map { it.habit.id }
        val r2 = FocusEngine.rank(listOf(c, a, b), s).map { it.habit.id }
        val r3 = FocusEngine.rank(listOf(b, c, a), s).map { it.habit.id }
        assertEquals(r1, r2)
        assertEquals(r2, r3)
    }
}
