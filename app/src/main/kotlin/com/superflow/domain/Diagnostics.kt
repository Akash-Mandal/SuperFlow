package com.superflow.domain

import com.superflow.data.Repository
import com.superflow.data.model.CheckIn
import com.superflow.data.model.Flow
import com.superflow.data.model.FlowStep
import com.superflow.data.model.FocusItem
import com.superflow.data.model.Goal
import com.superflow.data.model.Habit
import com.superflow.data.model.Identity
import com.superflow.data.model.ObstaclePlan
import com.superflow.data.model.Sys
import org.json.JSONArray

/**
 * Data-integrity diagnostics and repair.
 *
 * The manual "Check data integrity" screen and the AI `fix_integrity` tool
 * share this object, so what is checked and what is fixed cannot drift.
 *
 * Truly orphaned detail rows (check-ins and obstacle plans whose habit was
 * deleted) are removed. Dangling parent links (a goal whose identity was
 * deleted, a habit whose system was deleted, a flow step whose flow was
 * deleted) are nulled out rather than destroying the surviving record, so a
 * fix never deletes user content it does not have to.
 */
object Diagnostics {

    /**
     * Data seam for [Diagnostics] enabling unit testing without Android SQLite context.
     */
    interface DataSource {
        fun habits(includeArchived: Boolean = false): List<Habit>
        fun identities(includeArchived: Boolean = false): List<Identity>
        fun systems(): List<Sys>
        fun flows(): List<Flow>
        fun checkIns(): List<CheckIn>
        fun obstacles(): List<ObstaclePlan>
        fun focusAll(): List<FocusItem>
        fun flowSteps(flowId: String): List<FlowStep>
        fun goals(): List<Goal>

        fun delete(table: String, where: String, args: Array<Any?>)
        fun deleteObstacle(id: String)
        fun saveFocus(f: FocusItem)
        fun deleteFlowStep(id: String)
        fun saveGoal(g: Goal)
        fun saveHabit(h: Habit)
    }

    private fun repoAdapter(repo: Repository): DataSource = object : DataSource {
        override fun habits(includeArchived: Boolean) = repo.habits(includeArchived)
        override fun identities(includeArchived: Boolean) = repo.identities(includeArchived)
        override fun systems() = repo.systems()
        override fun flows() = repo.flows()
        override fun checkIns() = repo.checkIns()
        override fun obstacles() = repo.obstacles()
        override fun focusAll() = repo.focusAll()
        override fun flowSteps(flowId: String) = repo.flowSteps(flowId)
        override fun goals() = repo.goals()

        override fun delete(table: String, where: String, args: Array<Any?>) = repo.delete(table, where, args)
        override fun deleteObstacle(id: String) = repo.deleteObstacle(id)
        override fun saveFocus(f: FocusItem) = repo.saveFocus(f)
        override fun deleteFlowStep(id: String) = repo.deleteFlowStep(id)
        override fun saveGoal(g: Goal) = repo.saveGoal(g)
        override fun saveHabit(h: Habit) = repo.saveHabit(h)
    }

    data class Issue(val message: String)

    fun issues(repo: Repository): List<Issue> = issues(repoAdapter(repo))

    fun issues(ds: DataSource): List<Issue> {
        val out = mutableListOf<Issue>()
        val habitIds = ds.habits(true).map { it.id }.toSet()
        val identityIds = ds.identities(true).map { it.id }.toSet()
        val systemIds = ds.systems().map { it.id }.toSet()
        val flowIds = ds.flows().map { it.id }.toSet()

        val orphanCheckIns = ds.checkIns().filter { it.habitId !in habitIds }
        if (orphanCheckIns.isNotEmpty()) {
            out.add(Issue("${orphanCheckIns.size} check-ins for deleted habits"))
        }

        val orphanObstacles = ds.obstacles().filter { it.habitId !in habitIds }
        if (orphanObstacles.isNotEmpty()) {
            out.add(Issue("${orphanObstacles.size} obstacle plans for deleted habits"))
        }

        val orphanFocus = ds.focusAll().filter { it.habitId != null && it.habitId !in habitIds }
        if (orphanFocus.isNotEmpty()) {
            out.add(Issue("${orphanFocus.size} focus items linked to deleted habits"))
        }

        val orphanFlowSteps = ds.flows().flatMap { ds.flowSteps(it.id) }
            .filter { it.flowId !in flowIds }
        if (orphanFlowSteps.isNotEmpty()) {
            out.add(Issue("${orphanFlowSteps.size} flow steps for deleted flows"))
        }

        val orphanGoals = ds.goals().filter { it.identityId != null && it.identityId !in identityIds }
        if (orphanGoals.isNotEmpty()) {
            out.add(Issue("${orphanGoals.size} goals linked to deleted identities"))
        }

        val orphanHabits = ds.habits().filter { it.systemId != null && it.systemId !in systemIds }
        if (orphanHabits.isNotEmpty()) {
            out.add(Issue("${orphanHabits.size} habits linked to deleted systems"))
        }

        return out
    }

    /** Human-readable report, as shown in AI Engine → Diagnostics. */
    fun checkIntegrity(repo: Repository): String = checkIntegrity(repoAdapter(repo))

    fun checkIntegrity(ds: DataSource): String {
        val found = issues(ds)
        return if (found.isEmpty()) "✓ All data is consistent"
        else "Issues found:\n" + found.joinToString("\n") { "· ${it.message}" }
    }

    /**
     * Cleans up orphaned records. Returns the number of records touched.
     * The caller is responsible for audit/undo recording.
     */
    fun fix(repo: Repository): Int = fix(repoAdapter(repo))

    fun fix(ds: DataSource): Int {
        var touched = 0
        val habitIds = ds.habits(true).map { it.id }.toSet()
        val identityIds = ds.identities(true).map { it.id }.toSet()
        val systemIds = ds.systems().map { it.id }.toSet()
        val flowIds = ds.flows().map { it.id }.toSet()

        ds.checkIns().filter { it.habitId !in habitIds }.forEach {
            ds.delete("checkin", "id=?", arrayOf(it.id)); touched++
        }
        ds.obstacles().filter { it.habitId !in habitIds }.forEach {
            ds.deleteObstacle(it.id); touched++
        }
        ds.focusAll().filter { it.habitId != null && it.habitId !in habitIds }.forEach {
            ds.saveFocus(it.copy(habitId = null)); touched++
        }
        ds.flows().flatMap { ds.flowSteps(it.id) }.filter { it.flowId !in flowIds }.forEach {
            ds.deleteFlowStep(it.id); touched++
        }
        ds.goals().filter { it.identityId != null && it.identityId !in identityIds }.forEach {
            ds.saveGoal(it.copy(identityId = null)); touched++
        }
        ds.habits().filter { it.systemId != null && it.systemId !in systemIds }.forEach {
            ds.saveHabit(it.copy(systemId = null)); touched++
        }
        return touched
    }

    /**
     * Captures the rows [fix] would remove (check-ins and obstacle plans only —
     * the genuinely deleted rows), so an undo can restore them.
     */
    fun captureDeletions(repo: Repository): JSONArray = captureDeletions(repoAdapter(repo))

    fun captureDeletions(ds: DataSource): JSONArray {
        val rows = JSONArray()
        val habitIds = ds.habits(true).map { it.id }.toSet()
        ds.checkIns().filter { it.habitId !in habitIds }.forEach { rows.put(Serial.of(it)) }
        ds.obstacles().filter { it.habitId !in habitIds }.forEach { rows.put(Serial.of(it)) }
        return rows
    }
}
