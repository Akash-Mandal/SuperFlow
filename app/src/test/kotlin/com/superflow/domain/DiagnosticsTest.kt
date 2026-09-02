package com.superflow.domain

import com.superflow.data.model.CheckIn
import com.superflow.data.model.CheckInResult
import com.superflow.data.model.Flow
import com.superflow.data.model.FlowStep
import com.superflow.data.model.FocusItem
import com.superflow.data.model.Goal
import com.superflow.data.model.Habit
import com.superflow.data.model.Identity
import com.superflow.data.model.ObstaclePlan
import com.superflow.data.model.Sys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsTest {

    private class FakeDataSource : Diagnostics.DataSource {
        val habitsList = mutableListOf<Habit>()
        val identitiesList = mutableListOf<Identity>()
        val systemsList = mutableListOf<Sys>()
        val flowsList = mutableListOf<Flow>()
        val checkInsList = mutableListOf<CheckIn>()
        val obstaclesList = mutableListOf<ObstaclePlan>()
        val focusList = mutableListOf<FocusItem>()
        val flowStepsMap = mutableMapOf<String, MutableList<FlowStep>>()
        val goalsList = mutableListOf<Goal>()

        override fun habits(includeArchived: Boolean): List<Habit> = habitsList.toList()
        override fun identities(includeArchived: Boolean): List<Identity> = identitiesList.toList()
        override fun systems(): List<Sys> = systemsList.toList()
        override fun flows(): List<Flow> = flowsList.toList()
        override fun checkIns(): List<CheckIn> = checkInsList.toList()
        override fun obstacles(): List<ObstaclePlan> = obstaclesList.toList()
        override fun focusAll(): List<FocusItem> = focusList.toList()
        override fun flowSteps(flowId: String): List<FlowStep> = flowStepsMap[flowId]?.toList() ?: emptyList()
        override fun goals(): List<Goal> = goalsList.toList()

        override fun delete(table: String, where: String, args: Array<Any?>) {
            if (table == "checkin" && args.isNotEmpty()) {
                val id = args[0] as String
                checkInsList.removeAll { it.id == id }
            }
        }

        override fun deleteObstacle(id: String) {
            obstaclesList.removeAll { it.id == id }
        }

        override fun saveFocus(f: FocusItem) {
            val idx = focusList.indexOfFirst { it.id == f.id }
            if (idx >= 0) focusList[idx] = f else focusList.add(f)
        }

        override fun deleteFlowStep(id: String) {
            flowStepsMap.values.forEach { list -> list.removeAll { it.id == id } }
        }

        override fun saveGoal(g: Goal) {
            val idx = goalsList.indexOfFirst { it.id == g.id }
            if (idx >= 0) goalsList[idx] = g else goalsList.add(g)
        }

        override fun saveHabit(h: Habit) {
            val idx = habitsList.indexOfFirst { it.id == h.id }
            if (idx >= 0) habitsList[idx] = h else habitsList.add(h)
        }
    }

    @Test
    fun `consistent data reports no issues`() {
        val ds = FakeDataSource()
        val sys = Sys(id = "sys-1", title = "Morning Routine")
        val identity = Identity(id = "id-1", statement = "Runner")
        val habit = Habit(id = "h-1", title = "Run", systemId = "sys-1")
        val goal = Goal(id = "g-1", title = "Run 5k", identityId = "id-1")
        val flow = Flow(id = "f-1", title = "Morning Flow")
        val step = FlowStep(id = "s-1", flowId = "f-1", habitId = "h-1", title = "Put shoes on")
        val checkIn = CheckIn(id = "c-1", habitId = "h-1", date = "2026-08-26", result = CheckInResult.DONE)
        val obstacle = ObstaclePlan(id = "o-1", habitId = "h-1", ifText = "Raining", thenText = "Treadmill")
        val focus = FocusItem(id = "focus-1", date = "2026-08-26", habitId = "h-1", title = "Run")

        ds.systemsList.add(sys)
        ds.identitiesList.add(identity)
        ds.habitsList.add(habit)
        ds.goalsList.add(goal)
        ds.flowsList.add(flow)
        ds.flowStepsMap["f-1"] = mutableListOf(step)
        ds.checkInsList.add(checkIn)
        ds.obstaclesList.add(obstacle)
        ds.focusList.add(focus)

        val issues = Diagnostics.issues(ds)
        assertTrue(issues.isEmpty())
        assertEquals("✓ All data is consistent", Diagnostics.checkIntegrity(ds))
        assertEquals(0, Diagnostics.fix(ds))
        assertEquals(0, Diagnostics.captureDeletions(ds).length())
    }

    @Test
    fun `detects orphan check-ins for deleted habits`() {
        val ds = FakeDataSource()
        ds.checkInsList.add(CheckIn(id = "c-orphan", habitId = "deleted-h", date = "2026-08-26", result = CheckInResult.DONE))

        val issues = Diagnostics.issues(ds)
        assertEquals(1, issues.size)
        assertEquals("1 check-ins for deleted habits", issues[0].message)

        val captured = Diagnostics.captureDeletions(ds)
        assertEquals(1, captured.length())
        assertEquals("c-orphan", captured.getJSONObject(0).getString("id"))

        val touched = Diagnostics.fix(ds)
        assertEquals(1, touched)
        assertTrue(ds.checkInsList.isEmpty())
        assertTrue(Diagnostics.issues(ds).isEmpty())
    }

    @Test
    fun `detects orphan obstacle plans for deleted habits`() {
        val ds = FakeDataSource()
        ds.obstaclesList.add(ObstaclePlan(id = "o-orphan", habitId = "deleted-h", ifText = "If rain", thenText = "Indoor"))

        val issues = Diagnostics.issues(ds)
        assertEquals(1, issues.size)
        assertEquals("1 obstacle plans for deleted habits", issues[0].message)

        val captured = Diagnostics.captureDeletions(ds)
        assertEquals(1, captured.length())
        assertEquals("o-orphan", captured.getJSONObject(0).getString("id"))

        val touched = Diagnostics.fix(ds)
        assertEquals(1, touched)
        assertTrue(ds.obstaclesList.isEmpty())
    }

    @Test
    fun `detects orphan focus items linked to deleted habits`() {
        val ds = FakeDataSource()
        ds.focusList.add(FocusItem(id = "f-orphan", date = "2026-08-26", habitId = "deleted-h", title = "Deleted Habit Focus"))

        val issues = Diagnostics.issues(ds)
        assertEquals(1, issues.size)
        assertEquals("1 focus items linked to deleted habits", issues[0].message)

        val touched = Diagnostics.fix(ds)
        assertEquals(1, touched)
        assertEquals(1, ds.focusList.size)
        assertEquals(null, ds.focusList[0].habitId)
        assertEquals("Deleted Habit Focus", ds.focusList[0].title)
    }

    @Test
    fun `detects orphan flow steps for deleted flows`() {
        val ds = FakeDataSource()
        val flow = Flow(id = "f-1", title = "Flow 1")
        val step = FlowStep(id = "s-orphan", flowId = "deleted-flow", habitId = "h-1", title = "Step")
        ds.flowsList.add(flow)
        ds.flowStepsMap["f-1"] = mutableListOf(step)

        val issues = Diagnostics.issues(ds)
        assertEquals(1, issues.size)
        assertEquals("1 flow steps for deleted flows", issues[0].message)

        val touched = Diagnostics.fix(ds)
        assertEquals(1, touched)
        assertTrue(ds.flowStepsMap["f-1"]!!.isEmpty())
    }

    @Test
    fun `detects orphan goals linked to deleted identities`() {
        val ds = FakeDataSource()
        ds.goalsList.add(Goal(id = "g-orphan", title = "Goal", identityId = "deleted-identity"))

        val issues = Diagnostics.issues(ds)
        assertEquals(1, issues.size)
        assertEquals("1 goals linked to deleted identities", issues[0].message)

        val touched = Diagnostics.fix(ds)
        assertEquals(1, touched)
        assertEquals(1, ds.goalsList.size)
        assertEquals(null, ds.goalsList[0].identityId)
    }

    @Test
    fun `detects orphan habits linked to deleted systems`() {
        val ds = FakeDataSource()
        ds.habitsList.add(Habit(id = "h-orphan", title = "Habit", systemId = "deleted-system"))

        val issues = Diagnostics.issues(ds)
        assertEquals(1, issues.size)
        assertEquals("1 habits linked to deleted systems", issues[0].message)

        val touched = Diagnostics.fix(ds)
        assertEquals(1, touched)
        assertEquals(1, ds.habitsList.size)
        assertEquals(null, ds.habitsList[0].systemId)
    }

    @Test
    fun `mixed orphans are all identified, reported, and repaired`() {
        val ds = FakeDataSource()
        ds.checkInsList.add(CheckIn(id = "c1", habitId = "deleted-h", date = "2026-08-26", result = CheckInResult.DONE))
        ds.obstaclesList.add(ObstaclePlan(id = "o1", habitId = "deleted-h", ifText = "If", thenText = "Then"))
        ds.focusList.add(FocusItem(id = "f1", date = "2026-08-26", habitId = "deleted-h", title = "Focus"))

        ds.flowsList.add(Flow(id = "f-existing", title = "Existing Flow"))
        ds.flowStepsMap["f-existing"] = mutableListOf(FlowStep(id = "s1", flowId = "deleted-f", habitId = null, title = "Step"))

        ds.goalsList.add(Goal(id = "g1", title = "Goal", identityId = "deleted-id"))
        ds.habitsList.add(Habit(id = "h1", title = "Habit", systemId = "deleted-sys"))

        val report = Diagnostics.checkIntegrity(ds)
        assertTrue(report.startsWith("Issues found:\n"))
        assertTrue(report.contains("· 1 check-ins for deleted habits"))
        assertTrue(report.contains("· 1 obstacle plans for deleted habits"))
        assertTrue(report.contains("· 1 focus items linked to deleted habits"))
        assertTrue(report.contains("· 1 flow steps for deleted flows"))
        assertTrue(report.contains("· 1 goals linked to deleted identities"))
        assertTrue(report.contains("· 1 habits linked to deleted systems"))

        val capturedDeletions = Diagnostics.captureDeletions(ds)
        assertEquals(2, capturedDeletions.length()) // Check-in and Obstacle Plan only

        val totalTouched = Diagnostics.fix(ds)
        assertEquals(6, totalTouched)

        assertEquals("✓ All data is consistent", Diagnostics.checkIntegrity(ds))
    }
}
