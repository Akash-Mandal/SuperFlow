package com.superflow.ui.journey

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.superflow.data.Repository
import com.superflow.data.model.Goal
import com.superflow.data.model.Habit
import com.superflow.data.model.HabitMode
import com.superflow.data.model.Identity
import com.superflow.data.model.LifeArea
import com.superflow.data.model.Status
import com.superflow.data.model.Sys
import com.superflow.design.JourneyTree
import com.superflow.design.Periods
import com.superflow.domain.Actor
import com.superflow.domain.Capabilities
import com.superflow.domain.CommandBus
import com.superflow.domain.Insights
import com.superflow.domain.JourneyMapper
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

    /**
     * The counts strip: how much of the chain actually exists.
     *
     * Not decoration. `deepestChain == 4` is the single number that says
     * whether the app's premise has taken hold for this user, and it was
     * not on the screen before.
     */
    data class Summary(val summary: JourneyTree.Summary) : JourneyRow() {
        override val stableId = 2L
    }

    data class Header(val title: String, val addLabel: String?, val kind: String) : JourneyRow() {
        override val stableId = ("h$kind").hashCode().toLong()
    }

    /**
     * One entity in the tree.
     *
     * Carries the whole [JourneyTree.Row] rather than pre-flattened fields
     * so the adapter can draw depth, connectors and counts without the
     * ViewModel having to decide what a connector looks like.
     */
    data class Entity(
        val row: JourneyTree.Row,
        val subtitle: String,
        val icon: Int,
    ) : JourneyRow() {
        val id: String get() = row.node.id
        val kind: String get() = row.node.kind.key
        val title: String get() = row.node.title
        val archived: Boolean get() = row.node.archived
        override val stableId = (row.key).hashCode().toLong()
    }

    /** A place the chain is broken, phrased as an invitation. */
    data class Gap(val gap: JourneyTree.Gap) : JourneyRow() {
        override val stableId = ("g" + gap.kind.key + (gap.nodeId ?: "")).hashCode().toLong()
    }

    data class Empty(val title: String, val body: String, val kind: String) : JourneyRow() {
        override val stableId = ("e$kind").hashCode().toLong()
    }
}

/**
 * Journey's state (plan 11.2).
 *
 * The screen draws one hierarchy rather than four stacked lists, and every
 * decision about *what* that hierarchy contains is made outside this class:
 * [JourneyMapper] turns the four tables into nodes, [JourneyTree] places
 * them, counts them and decides what is dormant, orphaned or missing. What
 * is left here is the repository access, the coroutine plumbing and the
 * expansion set — the three things that need a Context, a scope or a
 * lifetime, and so cannot be pure.
 *
 * That split is why the tree is testable. `JourneyTree` and `JourneyMapper`
 * carry the assertions; this file carries none, because there is nothing
 * left in it to get wrong.
 */
class JourneyViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = Repository.get(app)
    private val bus = CommandBus.get(app)

    private val _rows = MutableStateFlow<List<JourneyRow>>(emptyList())
    val rows: StateFlow<List<JourneyRow>> = _rows.asStateFlow()

    private val _events = MutableStateFlow<String?>(null)
    val events: StateFlow<String?> = _events.asStateFlow()

    /**
     * Which nodes are open.
     *
     * Held in the ViewModel, not in Prefs: expansion is a reading position,
     * and restoring yesterday's open branches on a fresh launch is more
     * often wrong than right. It does survive rotation and tab switches,
     * which is the case that actually annoys people.
     *
     * `null` means "not yet decided" — the first build picks a default from
     * the tree's own size rather than opening nothing.
     */
    private var expanded: Set<String>? = null

    init {
        viewModelScope.launch { repo.revision.collect { refresh() } }
    }

    fun consumeEvent() { _events.value = null }

    fun refresh() {
        viewModelScope.launch {
            _rows.value = withContext(Dispatchers.IO) { build() }
        }
    }

    /** Open or close one node, then rebuild. Cheap: the tree is small. */
    fun toggle(kind: JourneyTree.Kind, id: String) {
        val current = expanded ?: emptySet()
        expanded = JourneyTree.toggle(current, kind, id)
        refresh()
    }

    /**
     * Open every ancestor of a node, so a deep link does not land on a
     * collapsed branch with nothing visible.
     */
    fun reveal(kind: JourneyTree.Kind, id: String) {
        val nodes = nodes()
        expanded = (expanded ?: JourneyTree.defaultExpansion(nodes)) +
            JourneyTree.revealPath(nodes, kind, id) +
            JourneyTree.expansionKey(kind, id)
        refresh()
    }

    /* ----------------------------------------------------------- building */

    private fun nodes(): List<JourneyTree.Node> = JourneyMapper.nodes(
        identities = repo.identities(),
        goals = repo.goals(),
        systems = repo.systems(),
        // The archived ones are wanted here: the tree draws them dimmed
        // rather than hiding them, so a system whose habits were all
        // archived does not look empty for no reason.
        habits = repo.habits(true),
        identityDetail = { identityDetail(it) },
        habitDetail = { habitDetail(it) },
    )

    private fun build(): List<JourneyRow> {
        val nodes = nodes()
        val open = expanded ?: JourneyTree.defaultExpansion(nodes).also { expanded = it }
        val tree = JourneyTree.build(nodes, open)

        val out = ArrayList<JourneyRow>(tree.rows.size + 8)
        out.add(JourneyRow.Tools)

        if (tree.isEmpty) {
            out.add(
                JourneyRow.Empty(
                    "Who are you becoming?",
                    "An identity statement gives every habit a reason to exist. " +
                        "Start there and the rest hangs off it.",
                    JourneyTree.Kind.IDENTITY.key,
                )
            )
            return out
        }

        out.add(JourneyRow.Summary(tree.summary))

        if (tree.linked.isNotEmpty()) {
            out.add(
                JourneyRow.Header(
                    "Your chain", JourneyTree.Kind.IDENTITY.label, JourneyTree.Kind.IDENTITY.key
                )
            )
            tree.linked.forEach { out.add(entityRow(it)) }
        }

        if (tree.unlinked.isNotEmpty()) {
            // Named for what it is from the user's side. "Unlinked" is our
            // word for it; "not connected yet" is what it means to them,
            // and it reads as a to-do rather than an error.
            out.add(JourneyRow.Header("Not connected yet", null, "unlinked"))
            tree.unlinked.forEach { out.add(entityRow(it)) }
        }

        JourneyTree.gaps(nodes).forEach { out.add(JourneyRow.Gap(it)) }

        return out
    }

    private fun entityRow(row: JourneyTree.Row): JourneyRow.Entity =
        JourneyRow.Entity(
            row = row,
            subtitle = row.node.detail,
            icon = iconFor(row),
        )

    private fun iconFor(row: JourneyTree.Row): Int = when (row.node.kind) {
        JourneyTree.Kind.IDENTITY -> com.superflow.R.drawable.ic_identity
        JourneyTree.Kind.GOAL -> com.superflow.R.drawable.ic_goal
        JourneyTree.Kind.SYSTEM -> com.superflow.R.drawable.ic_system
        JourneyTree.Kind.HABIT -> when {
            row.node.archived -> com.superflow.R.drawable.ic_archive
            habitMode(row.node.id) == HabitMode.REDUCE -> com.superflow.R.drawable.ic_shield
            else -> com.superflow.R.drawable.ic_bolt
        }
    }

    private fun habitMode(id: String): HabitMode? = repo.habit(id)?.mode

    private fun identityDetail(identity: Identity): String {
        val evidence = evidenceCache().get(identity.statement)
        return buildString {
            append(identity.lifeArea.label)
            val votes = evidence?.first ?: 0
            if (votes > 0) {
                append(" \u00b7 ")
                append(votes)
                append(if (votes == 1) " vote" else " votes")
            }
        }
    }

    /**
     * Identity evidence is one scan of every check-in, so it is computed
     * once per build rather than once per identity.
     */
    private var evidenceAt = -1L
    private var evidence: Map<String, Pair<Int, Int>> = emptyMap()

    private fun evidenceCache(): Map<String, Pair<Int, Int>> {
        val revision = repo.revision.value
        if (revision != evidenceAt) {
            evidence = Insights.identityEvidence(repo)
                .associate { it.first to (it.second to it.third) }
            evidenceAt = revision
        }
        return evidence
    }

    private fun habitDetail(habit: Habit): String {
        if (habit.status == Status.ARCHIVED) return "archived"
        val stats = Insights.forHabit(repo, habit)
        return JourneyMapper.habitDetail(
            habit = habit,
            daysLabel = Capabilities.daysLabel(habit),
            repetitions = stats.repetitions,
            consistency = stats.consistency30,
            // Below the sample floor a consistency percentage is noise
            // dressed as a measurement; the same threshold Insights uses.
            hasEnoughData = stats.repetitions >= Periods.MinSamples.COMPLETION_RATE,
        )
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

    fun undoLast() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repo.audit(1).firstOrNull()?.let { bus.undo(it) }
            }
        }
    }
}
