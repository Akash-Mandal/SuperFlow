package com.superflow.domain

import com.superflow.data.Repository
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

    data class Issue(val message: String)

    fun issues(repo: Repository): List<Issue> {
        val out = mutableListOf<Issue>()
        val habitIds = repo.habits(true).map { it.id }.toSet()
        val identityIds = repo.identities(true).map { it.id }.toSet()
        val systemIds = repo.systems().map { it.id }.toSet()
        val flowIds = repo.flows().map { it.id }.toSet()

        val orphanCheckIns = repo.checkIns().filter { it.habitId !in habitIds }
        if (orphanCheckIns.isNotEmpty()) {
            out.add(Issue("${orphanCheckIns.size} check-ins for deleted habits"))
        }

        val orphanObstacles = repo.obstacles().filter { it.habitId !in habitIds }
        if (orphanObstacles.isNotEmpty()) {
            out.add(Issue("${orphanObstacles.size} obstacle plans for deleted habits"))
        }

        val orphanFocus = repo.focusAll().filter { it.habitId != null && it.habitId !in habitIds }
        if (orphanFocus.isNotEmpty()) {
            out.add(Issue("${orphanFocus.size} focus items linked to deleted habits"))
        }

        val orphanFlowSteps = repo.flows().flatMap { repo.flowSteps(it.id) }
            .filter { it.flowId !in flowIds }
        if (orphanFlowSteps.isNotEmpty()) {
            out.add(Issue("${orphanFlowSteps.size} flow steps for deleted flows"))
        }

        val orphanGoals = repo.goals().filter { it.identityId != null && it.identityId !in identityIds }
        if (orphanGoals.isNotEmpty()) {
            out.add(Issue("${orphanGoals.size} goals linked to deleted identities"))
        }

        val orphanHabits = repo.habits().filter { it.systemId != null && it.systemId !in systemIds }
        if (orphanHabits.isNotEmpty()) {
            out.add(Issue("${orphanHabits.size} habits linked to deleted systems"))
        }

        return out
    }

    /** Human-readable report, as shown in AI Engine → Diagnostics. */
    fun checkIntegrity(repo: Repository): String {
        val found = issues(repo)
        return if (found.isEmpty()) "✓ All data is consistent"
        else "Issues found:\n" + found.joinToString("\n") { "· ${it.message}" }
    }

    /**
     * Cleans up orphaned records. Returns the number of records touched.
     * The caller is responsible for audit/undo recording.
     */
    fun fix(repo: Repository): Int {
        var touched = 0
        val habitIds = repo.habits(true).map { it.id }.toSet()
        val identityIds = repo.identities(true).map { it.id }.toSet()
        val systemIds = repo.systems().map { it.id }.toSet()
        val flowIds = repo.flows().map { it.id }.toSet()

        repo.checkIns().filter { it.habitId !in habitIds }.forEach {
            repo.delete("checkin", "id=?", arrayOf(it.id)); touched++
        }
        repo.obstacles().filter { it.habitId !in habitIds }.forEach {
            repo.deleteObstacle(it.id); touched++
        }
        repo.focusAll().filter { it.habitId != null && it.habitId !in habitIds }.forEach {
            repo.saveFocus(it.copy(habitId = null)); touched++
        }
        repo.flows().flatMap { repo.flowSteps(it.id) }.filter { it.flowId !in flowIds }.forEach {
            repo.deleteFlowStep(it.id); touched++
        }
        repo.goals().filter { it.identityId != null && it.identityId !in identityIds }.forEach {
            repo.saveGoal(it.copy(identityId = null)); touched++
        }
        repo.habits().filter { it.systemId != null && it.systemId !in systemIds }.forEach {
            repo.saveHabit(it.copy(systemId = null)); touched++
        }
        return touched
    }

    /**
     * Captures the rows [fix] would remove (check-ins and obstacle plans only —
     * the genuinely deleted rows), so an undo can restore them.
     */
    fun captureDeletions(repo: Repository): JSONArray {
        val rows = JSONArray()
        val habitIds = repo.habits(true).map { it.id }.toSet()
        repo.checkIns().filter { it.habitId !in habitIds }.forEach { rows.put(Serial.of(it)) }
        repo.obstacles().filter { it.habitId !in habitIds }.forEach { rows.put(Serial.of(it)) }
        return rows
    }
}
