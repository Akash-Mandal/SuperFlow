package com.superflow.ai

import com.superflow.data.model.Habit
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the Local Coordinator: habit-phrase parsing and the pure
 * routing logic (no Android context needed — repository access goes through
 * the Lookup seam).
 */
class CoordinatorTest {

    private val habits = listOf(
        Habit(id = "w1", title = "Walk 10 minutes", cueTime = "07:30"),
        Habit(id = "m1", title = "Meditate", cueTime = "08:00"),
        Habit(id = "r1", title = "Read 5 pages", cueTime = "21:00")
    )

    private val lookup = Coordinator.Lookup(
        resolve = { name ->
            val q = name.trim().lowercase()
            if (q.isEmpty()) null
            else habits.firstOrNull { it.title.lowercase() == q }
                ?: habits.firstOrNull { it.title.lowercase().startsWith(q) }
                ?: habits.firstOrNull { it.title.lowercase().contains(q) }
        },
        all = { habits }
    )

    /* ------------------------------------------------------- parseHabitPhrase */

    @Test
    fun `parse title only`() {
        val args = Coordinator.parseHabitPhrase("walk 10 minutes")
        assertNotNull(args)
        assertEquals("Walk 10 minutes", args!!.getString("title"))
        assertEquals("", args.getString("cueTime"))
        assertEquals("", args.getString("days"))
    }

    @Test
    fun `parse time with at`() {
        val args = Coordinator.parseHabitPhrase("meditate at 07:30 daily")
        assertNotNull(args)
        assertEquals("07:30", args!!.getString("cueTime"))
        assertEquals("daily", args.getString("days"))
    }

    @Test
    fun `parse am pm time`() {
        val args = Coordinator.parseHabitPhrase("stretch at 6 am")
        assertNotNull(args)
        assertEquals("06:00", args!!.getString("cueTime"))
    }

    @Test
    fun `parse pm time rolls past noon`() {
        val args = Coordinator.parseHabitPhrase("read at 7 pm")
        assertNotNull(args)
        assertEquals("19:00", args!!.getString("cueTime"))
    }

    @Test
    fun `parse weekdays weekends and day lists`() {
        assertEquals("weekdays", Coordinator.parseHabitPhrase("walk weekdays")!!.getString("days"))
        assertEquals("weekends", Coordinator.parseHabitPhrase("walk weekends")!!.getString("days"))
        assertEquals("mon, wed, fri",
            Coordinator.parseHabitPhrase("walk on mon, wed, fri")!!.getString("days"))
    }

    @Test
    fun `parse place and anchor`() {
        val args = Coordinator.parseHabitPhrase("walk after breakfast in the park")
        assertNotNull(args)
        assertTrue(args!!.getString("cuePlace").contains("park"))
        assertEquals("breakfast", args.getString("anchorText"))
    }

    @Test
    fun `tiny start is always derived`() {
        val args = Coordinator.parseHabitPhrase("walk 10 minutes")
        assertTrue(args!!.getString("tinyStart").isNotBlank())
    }

    @Test
    fun `blank phrase yields nothing`() {
        assertNull(Coordinator.parseHabitPhrase(""))
        assertNull(Coordinator.parseHabitPhrase("   "))
    }

    @Test
    fun `overlong title is rejected`() {
        assertNull(Coordinator.parseHabitPhrase("a".repeat(90)))
    }

    /* -------------------------------------------------------- defaultTinyStart */

    @Test
    fun `default tiny starts are domain specific`() {
        assertTrue(Coordinator.defaultTinyStart("Read 20 pages").contains("one page", true))
        assertTrue(Coordinator.defaultTinyStart("Walk 5 km").contains("shoes", true))
        assertTrue(Coordinator.defaultTinyStart("Meditate 10 min").contains("breaths", true))
        assertTrue(Coordinator.defaultTinyStart("Write a journal entry").contains("one sentence", true))
        assertEquals("Start for two minutes", Coordinator.defaultTinyStart("do the thing"))
    }

    /* -------------------------------------------------------------- routing */

    @Test
    fun `greeting is a noop with a reply`() {
        val plan = Coordinator.interpret("hello", lookup)
        assertNotNull(plan)
        assertEquals("noop", plan!!.command)
        assertNotNull(plan.reply)
    }

    @Test
    fun `help is a noop`() {
        val plan = Coordinator.interpret("what can you do", lookup)
        assertEquals("noop", plan!!.command)
    }

    @Test
    fun `progress and planning phrases route to capabilities`() {
        assertEquals("get_insights", Coordinator.interpret("how am I doing", lookup)!!.command)
        assertEquals("today_summary", Coordinator.interpret("what's today", lookup)!!.command)
        assertEquals("list_habits", Coordinator.interpret("list habits", lookup)!!.command)
        assertEquals("plan_tomorrow", Coordinator.interpret("plan tomorrow", lookup)!!.command)
        assertEquals("enter_minimum_mode", Coordinator.interpret("low energy day", lookup)!!.command)
        assertEquals("run_checkpoint", Coordinator.interpret("morning checkpoint", lookup)!!.command)
    }

    @Test
    fun `energy logging parses the value`() {
        val plan = Coordinator.interpret("my energy is 3", lookup)
        assertNotNull(plan)
        assertEquals("log_energy", plan!!.command)
        assertEquals(3, plan.args.getInt("energy"))
    }

    @Test
    fun `done routes to check in with level parsing`() {
        val plan = Coordinator.interpret("done walk", lookup)
        assertNotNull(plan)
        assertEquals("check_in", plan!!.command)
        assertEquals("w1", plan.args.getString("habit"))
        assertEquals("STANDARD", plan.args.getString("level"))

        // The help card advertises "tiny walk" — level words start the sentence.
        val tiny = Coordinator.interpret("tiny walk", lookup)
        assertNotNull(tiny)
        assertEquals("check_in", tiny!!.command)
        assertEquals("w1", tiny.args.getString("habit"))
        assertEquals("TINY", tiny.args.getString("level"))

        val stretch = Coordinator.interpret("i did stretch meditate", lookup)
        assertNotNull(stretch)
        assertEquals("STRETCH", stretch!!.args.getString("level"))
        assertEquals("m1", stretch.args.getString("habit"))
    }

    @Test
    fun `yesterday checkin targets yesterday`() {
        val plan = Coordinator.interpret("i did my walk yesterday", lookup)
        assertNotNull(plan)
        assertEquals("yesterday", plan!!.args.getString("date"))
    }

    @Test
    fun `skip and missed route to their capabilities`() {
        assertEquals("skip_habit", Coordinator.interpret("skip read", lookup)!!.command)
        assertEquals("mark_missed", Coordinator.interpret("missed meditate", lookup)!!.command)
    }

    @Test
    fun `unresolvable habit yields no plan`() {
        assertNull(Coordinator.interpret("done something nobody tracks", lookup))
    }

    @Test
    fun `focus phrase stores up to three items`() {
        val plan = Coordinator.interpret("focus on write, walk, call mum", lookup)
        assertNotNull(plan)
        assertEquals("set_daily_focus", plan!!.command)
        assertEquals(3, plan.args.getJSONArray("items").length())
    }

    @Test
    fun `more than three focus items are refused`() {
        assertNull(Coordinator.interpret("focus on a, b, c, d", lookup))
    }

    @Test
    fun `create habit phrase goes through the parser`() {
        val plan = Coordinator.interpret("create habit swim at 18:00 daily", lookup)
        assertNotNull(plan)
        assertEquals("create_habit", plan!!.command)
        assertEquals("18:00", plan.args.getString("cueTime"))
        assertEquals("daily", plan.args.getString("days"))
    }

    @Test
    fun `if-then plans attach to the first matching habit`() {
        val plan = Coordinator.interpret("if it rains, then stretch indoors", lookup)
        assertNotNull(plan)
        assertEquals("add_obstacle_plan", plan!!.command)
        assertTrue(plan.args.getString("ifText").contains("it rains", true))
    }

    @Test
    fun `archive and delete route`() {
        assertEquals("archive_habit", Coordinator.interpret("archive walk", lookup)!!.command)
        assertEquals("delete_habit", Coordinator.interpret("delete habit read", lookup)!!.command)
    }

    @Test
    fun `search routes with the query`() {
        val plan = Coordinator.interpret("search walk", lookup)
        assertNotNull(plan)
        assertEquals("search", plan!!.command)
        assertEquals("walk", plan.args.getString("query"))
    }

    @Test
    fun `unknown text yields no plan`() {
        assertNull(Coordinator.interpret("zzz qqq xxx", lookup))
    }
}
