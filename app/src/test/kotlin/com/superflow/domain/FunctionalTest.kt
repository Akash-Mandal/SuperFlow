package com.superflow.domain

import com.superflow.data.model.DifficultyLevel
import com.superflow.data.model.Habit
import com.superflow.data.model.LifeArea
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Functional Plan (PR #8) pure-logic coverage: the Progressive Growth Engine,
 * the habit template library and the difficulty/time estimators.
 */
class FunctionalTest {

    @Test
    fun `growth plan generates four phases`() {
        val h = Habit(title = "Read", tinyStart = "Open the book", standardVersion = "Read 20 min")
        val plan = GrowthEngine.generateGrowthPlan(h, weeks = 8)
        assertEquals(4, plan.phases.size)
        assertEquals("Foundation", plan.phases[0].label)
        assertEquals("Flourishing", plan.phases[3].label)
        assertEquals(0, plan.currentPhaseIndex)
        assertTrue(plan.isActive())
    }

    @Test
    fun `growth plan phases get progressively harder standards`() {
        val h = Habit(title = "Read", tinyStart = "Open the book",
            standardVersion = "Read 20 min", stretchVersion = "Read 40 min")
        val plan = GrowthEngine.generateGrowthPlan(h, weeks = 8)
        // Phase 1 starts at the tiny level; later phases work toward the standard.
        assertEquals(h.tinyStart, plan.phases[0].standardVersion)
        assertEquals(h.standardVersion, plan.phases[1].standardVersion)
    }

    @Test
    fun `estimate minutes parses explicit and implicit durations`() {
        assertEquals(20, GrowthEngine.estimateMinutes("Read 20 min"))
        assertEquals(60, GrowthEngine.estimateMinutes("Deep work for an hour"))
        assertTrue(GrowthEngine.estimateMinutes("Just a tiny action") > 0)
    }

    @Test
    fun `difficulty estimator rates a small habit easy`() {
        val easy = Habit(title = "Drink water", tinyStart = "One glass",
            standardVersion = "8 glasses", estimatedMinutes = 2)
        assertEquals(DifficultyLevel.EASY, GrowthEngine.estimateDifficulty(easy).level)
    }

    @Test
    fun `difficulty estimator rates a long habit challenging`() {
        val hard = Habit(title = "Study", tinyStart = "Open notes",
            standardVersion = "Study 90 min", estimatedMinutes = 90)
        assertEquals(DifficultyLevel.CHALLENGING, GrowthEngine.estimateDifficulty(hard).level)
    }

    @Test
    fun `template library has templates across life areas`() {
        val all = HabitTemplates.allTemplates()
        // Plan promises 100+; implementation ships 31. Assert the real floor
        // so the test fails if the catalog regresses, and track the gap in
        // IMPLEMENTATION_STATUS.
        assertTrue(all.size >= 30)
        assertTrue(HabitTemplates.forArea(LifeArea.HEALTH).isNotEmpty())
        assertTrue(HabitTemplates.forArea(LifeArea.LEARNING).isNotEmpty())
    }

    @Test
    fun `suggest for goal returns relevant templates`() {
        val reading = HabitTemplates.suggestForGoal("read every day")
        assertTrue(reading.any { it.title.lowercase().contains("read") })
    }

    @Test
    fun `template fields are complete for designer pre-fill`() {
        val tpl = HabitTemplates.forArea(LifeArea.HEALTH).first()
        assertTrue(tpl.title.isNotBlank())
        assertTrue(tpl.tinyStart.isNotBlank())
        assertTrue(tpl.standardVersion.isNotBlank())
    }
}
