package com.superflow.ui.journey

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.superflow.data.Repository
import com.superflow.data.model.*
import com.superflow.domain.Actor
import com.superflow.domain.Capabilities
import com.superflow.domain.CommandBus
import com.superflow.domain.Insights
import com.superflow.util.jsonOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Rows rendered by the Journey list. */
sealed class JourneyRow {
    abstract val stableId: Long

    object Tools : JourneyRow() { override val stableId = 1L }

    data class Header(val title: String, val addLabel: String?, val kind: String) : JourneyRow() {
        override val stableId = ("h$kind").hashCode().toLong()
    }

    data class Entity(
        val id: String,
        val kind: String,
        val title: String,
        val subtitle: String,
        val icon: Int,
        val archived: Boolean = false,
        val graduated: Boolean = false
    ) : JourneyRow() {
        override val stableId = (kind + id).hashCode().toLong()
    }

    data class Empty(val title: String, val body: String, val kind: String) : JourneyRow() {
        override val stableId = ("e$kind").hashCode().toLong()
    }
}

class JourneyViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = Repository.get(app)
    private val bus = CommandBus.get(app)

    private val _rows = MutableStateFlow<List<JourneyRow>>(emptyList())
    val rows: StateFlow<List<JourneyRow>> = _rows.asStateFlow()

    private val _events = MutableStateFlow<String?>(null)
    val events: StateFlow<String?> = _events.asStateFlow()

    init {
        viewModelScope.launch { repo.revision.collect { refresh() } }
    }

    fun consumeEvent() { _events.value = null }

    fun refresh() {
        viewModelScope.launch {
            _rows.value = withContext(Dispatchers.IO) { build() }
        }
    }

    private fun build(): List<JourneyRow> {
        val rows = ArrayList<JourneyRow>()
        rows.add(JourneyRow.Tools)

        // Identities
        rows.add(JourneyRow.Header("Identities", "Add", "identity"))
        val identities = repo.identities()
        val evidence = Insights.identityEvidence(repo).associateBy { it.first }
        if (identities.isEmpty()) {
            rows.add(JourneyRow.Empty("Who are you becoming?",
                "An identity statement gives every habit a reason to exist.", "identity"))
        }
        for (i in identities) {
            val ev = evidence[i.statement]
            rows.add(JourneyRow.Entity(
                i.id, "identity", i.statement,
                "${i.lifeArea.label} · ${ev?.second ?: 0} votes · ${ev?.third ?: 0} habits",
                com.superflow.R.drawable.ic_identity
            ))
        }

        // Goals
        rows.add(JourneyRow.Header("Goals", "Add", "goal"))
        val goals = repo.goals()
        if (goals.isEmpty()) {
            rows.add(JourneyRow.Empty("What outcome would matter?",
                "A goal sets direction. Your system does the work.", "goal"))
        }
        for (g in goals) {
            val systems = repo.systems().count { it.goalId == g.id }
            rows.add(JourneyRow.Entity(
                g.id, "goal", g.title,
                buildString {
                    append(g.status.name.lowercase())
                    append(" · $systems systems")
                    if (g.why.isNotBlank()) append(" · ${g.why.take(48)}")
                },
                com.superflow.R.drawable.ic_goal
            ))
        }

        // Systems
        rows.add(JourneyRow.Header("Systems", "Add", "system"))
        val systems = repo.systems()
        if (systems.isEmpty()) {
            rows.add(JourneyRow.Empty("How will it actually happen?",
                "A system is the repeatable process behind the goal.", "system"))
        }
        for (s in systems) {
            val habits = repo.habits().count { it.systemId == s.id }
            rows.add(JourneyRow.Entity(
                s.id, "system", s.title,
                "$habits habits · goal: ${repo.goal(s.goalId)?.title ?: "none"}",
                com.superflow.R.drawable.ic_system
            ))
        }

        // Habits
        rows.add(JourneyRow.Header("Habits", "Design", "habit"))
        val habits = repo.habits().filter { !it.graduated }
        if (habits.isEmpty()) {
            rows.add(JourneyRow.Empty("Pick one small action",
                "Every habit needs a version you can start in two minutes.", "habit"))
        }
        for (h in habits) {
            val stats = Insights.forHabit(repo, h)
            rows.add(JourneyRow.Entity(
                h.id, "habit", h.title,
                Capabilities.daysLabel(h) +
                        (if (h.cueTime.isNotBlank()) " · ${h.cueTime}" else "") +
                        " · ${stats.repetitions} reps · ${stats.consistency30}%",
                if (h.mode == HabitMode.REDUCE) com.superflow.R.drawable.ic_shield
                else com.superflow.R.drawable.ic_bolt
            ))
        }

        // Graduated habits live in maintenance, off Today, checked weekly.
        val graduated = repo.habits().filter { it.graduated }
        if (graduated.isNotEmpty()) {
            rows.add(JourneyRow.Header("Maintenance", null, "maintenance"))
            for (h in graduated) {
                val stats = Insights.forHabit(repo, h)
                rows.add(JourneyRow.Entity(
                    h.id, "habit", h.title,
                    "Automatic · weekly check-in · ${stats.repetitions} reps",
                    com.superflow.R.drawable.ic_star, graduated = true
                ))
            }
        }

        val archived = repo.habits(true).filter { it.status == Status.ARCHIVED }
        if (archived.isNotEmpty()) {
            rows.add(JourneyRow.Header("Archived", null, "archived"))
            for (h in archived) {
                rows.add(JourneyRow.Entity(
                    h.id, "habit", h.title, "archived",
                    com.superflow.R.drawable.ic_archive, archived = true
                ))
            }
        }

        return rows
    }

    /* --------------------------------------------------------------- actions */

    private fun run(command: String, args: JSONObject) {
        viewModelScope.launch {
            val res = withContext(Dispatchers.IO) { bus.execute(command, args, Actor.USER) }
            _events.value = res.message
        }
    }

    fun identity(id: String): Identity? = repo.identity(id)
    fun goal(id: String): Goal? = repo.goal(id)
    fun system(id: String): Sys? = repo.system(id)
    fun habit(id: String): Habit? = repo.habit(id)
    fun identities(): List<Identity> = repo.identities()
    fun goals(): List<Goal> = repo.goals()

    fun saveIdentity(id: String?, statement: String, area: LifeArea) {
        if (id == null) run("create_identity", jsonOf("statement" to statement, "lifeArea" to area.name))
        else run("update_identity", jsonOf("id" to id, "statement" to statement, "lifeArea" to area.name))
    }

    fun saveGoal(id: String?, title: String, why: String, identityId: String?) {
        val args = jsonOf("title" to title, "why" to why, "identityId" to identityId)
        if (id == null) run("create_goal", args) else run("update_goal", args.put("id", id))
    }

    fun saveSystem(id: String?, title: String, description: String, goalId: String?) {
        val args = jsonOf("title" to title, "description" to description, "goalId" to goalId)
        if (id == null) run("create_system", args) else run("update_system", args.put("id", id))
    }

    fun delete(kind: String, id: String) = when (kind) {
        "identity" -> run("delete_identity", jsonOf("id" to id))
        "goal" -> run("delete_goal", jsonOf("id" to id))
        "system" -> run("delete_system", jsonOf("id" to id))
        "habit" -> run("delete_habit", jsonOf("habit" to id))
        else -> Unit
    }

    fun archiveHabit(id: String) = run("archive_habit", jsonOf("habit" to id))
    fun restoreHabit(id: String) = run("restore_habit", jsonOf("habit" to id))

    /** Persists the drag-and-drop order for the active habits. */
    fun reorderHabits(ids: List<String>) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val arr = org.json.JSONArray()
                ids.forEach { arr.put(it) }
                bus.execute("reorder_habits", jsonOf("ids" to arr), Actor.USER)
            }
        }
    }

    fun undoLast() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repo.audit(1).firstOrNull()?.let { bus.undo(it) }
            }
        }
    }
}
