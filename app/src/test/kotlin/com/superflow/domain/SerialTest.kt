package com.superflow.domain

import com.superflow.data.model.CheckIn
import com.superflow.data.model.CheckInResult
import com.superflow.data.model.Goal
import com.superflow.data.model.Habit
import com.superflow.data.model.HabitMode
import com.superflow.data.model.Identity
import com.superflow.data.model.LifeArea
import com.superflow.data.model.PauseWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the row <-> JSON serialization used by undo payloads,
 * snapshots and the user-facing export. Field coverage matters here: a
 * dropped field silently loses data on restore.
 */
class SerialTest {

    @Test
    fun `habit round-trips every field`() {
        val h = Habit(
            id = "h-1", systemId = "s-1", identityId = "i-1", title = "Walk",
            mode = HabitMode.REDUCE,
            cueTime = "07:30", cuePlace = "park", anchorText = "breakfast",
            benefit = "health", temptationBundle = "phone", reframe = "movement",
            tinyStart = "step outside", minimumVersion = "1 min",
            standardVersion = "10 min", stretchVersion = "30 min",
            frictionPlan = "shoes by door", environmentPrep = "weather app",
            reward = "coffee", recoveryPlan = "tiny counts",
            recurrenceRule = "WEEKLY:1,3,5", scheduleVersion = 2,
            startDate = "2026-08-01", endDate = "2026-09-01",
            reminderEnabled = true, protectedRoutine = true,
            colorSeed = 3, orderIndex = 7, createdAt = 1234L
        )
        val json = Serial.of(h)
        assertEquals("habit", json.getString("table"))
        assertEquals("h-1", json.getString("id"))
        assertEquals("s-1", json.getString("systemId"))
        assertEquals("i-1", json.getString("identityId"))
        assertEquals("WEEKLY:1,3,5", json.getString("recurrenceRule"))
        assertEquals(2, json.getInt("scheduleVersion"))
        assertEquals("2026-09-01", json.getString("endDate"))
        assertTrue(json.getBoolean("reminderEnabled"))
        assertTrue(json.getBoolean("protectedRoutine"))
        assertEquals(7, json.getInt("orderIndex"))
        assertEquals(1234L, json.getLong("createdAt"))

        val back = Serial.habit(json)
        assertEquals(h, back)
    }

    @Test
    fun `habit nulls survive the round trip`() {
        val h = Habit(id = "h-2", title = "x")
        val json = Serial.of(h)
        val back = Serial.habit(json)
        assertEquals(null, back.systemId)
        assertEquals(null, back.endDate)
        assertFalse(back.reminderEnabled)
    }

    @Test
    fun `identity goal and pause serialize`() {
        val i = Identity(id = "i-1", statement = "a mover", lifeArea = LifeArea.HEALTH)
        assertEquals("a mover", Serial.identity(Serial.of(i)).statement)
        assertEquals(LifeArea.HEALTH, Serial.identity(Serial.of(i)).lifeArea)

        val g = Goal(id = "g-1", title = "Run 10k", identityId = "i-1")
        assertEquals("g-1", Serial.goal(Serial.of(g)).id)
        assertEquals("i-1", Serial.goal(Serial.of(g)).identityId)

        val p = PauseWindow(id = "p-1", habitId = "h-1",
            startDate = "2026-08-01", endDate = "2026-08-05", reason = "travel")
        assertEquals("p-1", Serial.pause(Serial.of(p)).id)
        assertEquals("h-1", Serial.pause(Serial.of(p)).habitId)
    }

    @Test
    fun `checkin serializes level and result`() {
        val c = CheckIn(id = "c-1", habitId = "h-1", date = "2026-08-19",
            result = CheckInResult.RESISTED, amount = 2.5, note = "hard")
        val json = Serial.of(c)
        assertEquals("checkin", json.getString("table"))
        assertEquals("RESISTED", json.getString("result"))
        assertEquals(2.5, json.getDouble("amount"), 0.001)
        val back = Serial.checkIn(json)
        assertEquals(c, back)
    }
}
