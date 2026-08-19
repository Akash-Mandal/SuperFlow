package com.superflow.ui.today

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.superflow.data.Prefs
import com.superflow.data.Repository
import com.superflow.data.model.*
import com.superflow.domain.Actor
import com.superflow.domain.CommandBus
import com.superflow.domain.CommandResult
import com.superflow.domain.Insights
import com.superflow.core.time.DayBucket
import com.superflow.core.time.Greeting
import com.superflow.core.time.SfTime
import java.time.LocalDate
import java.time.LocalTime
import com.superflow.util.jsonArrayOf
import com.superflow.util.jsonOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Rows rendered by the Today list. */
sealed class TodayRow {
    abstract val stableId: Long

    data class Progress(val done: Int, val total: Int, val message: String) : TodayRow() {
        override val stableId = 1L
    }

    data class Load(val habits: Int, val minutes: Int, val score: Double, val color: String) : TodayRow() {
        override val stableId = 11L
    }

    data class IdentityCard(val statement: String, val votes: Int) : TodayRow() {
        override val stableId = 2L
    }

    data class Returning(val habits: List<Habit>) : TodayRow() {
        override val stableId = 3L
    }

    data class Focus(val items: List<FocusItem>) : TodayRow() {
        override val stableId = 4L
    }

    data class Checkpoints(val energy: Int?) : TodayRow() {
        override val stableId = 5L
    }

    data class Section(val title: String) : TodayRow() {
        override val stableId = ("section$title").hashCode().toLong()
    }

    data class HabitRow(val item: TodayHabit, val history: List<Int>) : TodayRow() {
        override val stableId = item.habit.id.hashCode().toLong()
    }

    data class Empty(val title: String, val body: String, val action: String?) : TodayRow() {
        override val stableId = 9L
    }
}

data class TodayUiState(
    val date: LocalDate = LocalDate.now(),
    val greeting: Greeting = Greeting.MORNING,
    val rows: List<TodayRow> = emptyList(),
    val loading: Boolean = true
)

class TodayViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = Repository.get(app)
    private val bus = CommandBus.get(app)
    val prefs: Prefs = Prefs.get(app)

    private val _state = MutableStateFlow(TodayUiState())
    val state: StateFlow<TodayUiState> = _state.asStateFlow()

    private val _events = MutableStateFlow<String?>(null)
    val events: StateFlow<String?> = _events.asStateFlow()

    private var lastAuditId: String? = null

    init {
        viewModelScope.launch {
            repo.revision.collect { refresh() }
        }
    }

    fun consumeEvent() { _events.value = null }

    fun lastUndoId(): String? = lastAuditId

    fun refresh() {
        viewModelScope.launch {
            val built = withContext(Dispatchers.IO) { build() }
            _state.value = built
        }
    }

    private fun build(): TodayUiState {
        val date = repo.clock.today()
        val iso = SfTime.format(date)
        val rows = ArrayList<TodayRow>()

        val (done, total) = Insights.dayProgress(repo, date)
        rows.add(TodayRow.Progress(done, total, progressMessage(done, total)))

        // Daily load indicator (§15)
        val (habitsCount, minutes, score) = Insights.dailyLoad(repo, date)
        if (habitsCount > 0) {
            val color = when {
                score < 15 -> "green"
                score < 30 -> "amber"
                else -> "coral"
            }
            rows.add(TodayRow.Load(habitsCount, minutes, score, color))
        }

        // Primary identity first, then others (§1)
        val identities = repo.identities().sortedByDescending { it.isPrimary }
        identities.firstOrNull()?.let { identity ->
            val votes = Insights.identityEvidence(repo)
                .firstOrNull { it.first == identity.statement }?.second ?: 0
            rows.add(TodayRow.IdentityCard(identity.statement, votes))
        }

        val returning = repo.returnCandidates(date)
        if (returning.isNotEmpty()) rows.add(TodayRow.Returning(returning))

        rows.add(TodayRow.Focus(repo.focusFor(iso)))

        if (prefs.checkpointsEnabled) {
            val cp = currentCheckpoint()
            val energy = repo.energyFor(iso).firstOrNull { it.checkpoint == cp }?.energy
            rows.add(TodayRow.Checkpoints(energy))
        }

        val todayHabits = repo.todayHabits(date)
        if (todayHabits.isEmpty()) {
            rows.add(TodayRow.Empty(
                "No habits scheduled",
                "Design one in the Journey tab. Start absurdly small — a version you could do on your worst day.",
                "Design a habit"
            ))
        } else {
            val buckets = todayHabits.groupBy {
                SfTime.bucketOf(SfTime.parseTime(it.habit.cueTime))
            }
            for (key in DayBucket.values()) {
                val list = buckets[key] ?: continue
                rows.add(TodayRow.Section(key.label))
                list.forEach { rows.add(TodayRow.HabitRow(it, historyFor(it.habit))) }
            }
        }

        return TodayUiState(
            date = date,
            greeting = SfTime.greetingFor(repo.clock.nowTime()),
            rows = rows,
            loading = false
        )
    }

    private fun progressMessage(done: Int, total: Int): String = when {
        total == 0 -> "A quiet day is allowed. Add a habit whenever you are ready."
        done == 0 -> "Start with the smallest version. Showing up is the win."
        done < total -> "Momentum is real. One more when you are ready."
        else -> "Every action today was a vote for who you are becoming."
    }

    /** 14-day state strip, derived from the opportunity series. */
    private fun historyFor(habit: Habit): List<Int> = Insights.historyStates(repo, habit, 14)

    private fun currentCheckpoint(): Checkpoint =
        when (SfTime.greetingFor(repo.clock.nowTime())) {
            Greeting.MORNING -> Checkpoint.MORNING
            Greeting.AFTERNOON -> Checkpoint.MIDDAY
            Greeting.EVENING -> Checkpoint.EVENING
        }

    /* --------------------------------------------------------------- actions */

    private fun run(command: String, args: JSONObject, announce: Boolean = true) {
        viewModelScope.launch {
            val res: CommandResult = withContext(Dispatchers.IO) {
                bus.execute(command, args, Actor.USER)
            }
            lastAuditId = res.auditId
            if (announce || !res.ok) _events.value = res.message
        }
    }

    fun checkIn(habit: Habit, level: Level) =
        run("check_in", jsonOf("habit" to habit.id, "level" to level.name))

    fun skip(habit: Habit) = run("skip_habit", jsonOf("habit" to habit.id))

    fun markMissed(habit: Habit) = run("mark_missed", jsonOf("habit" to habit.id))

    fun clearCheckIn(habit: Habit) =
        run("clear_check_in", jsonOf("habit" to habit.id), announce = false)

    fun toggleFocus(item: FocusItem, done: Boolean) =
        run("complete_focus_item", jsonOf("id" to item.id, "done" to done), announce = false)

    fun removeFocus(item: FocusItem) =
        run("remove_focus_item", jsonOf("id" to item.id), announce = false)

    fun addFocus(title: String) = run("add_focus_item", jsonOf("title" to title), announce = false)

    fun suggestFocus() {
        viewModelScope.launch {
            val date = repo.clock.today()
            val iso = SfTime.format(date)
            val existing = repo.focusFor(iso).map { it.title }
            val checkIns = repo.checkInsFor(iso).associateBy { it.habitId }
            val candidates = repo.habitsForDay(date)
                .filter { checkIns[it.id] == null && it.title !in existing }
                .sortedWith(compareByDescending<Habit> { it.protectedRoutine }.thenBy { it.orderIndex })
                .take(3 - existing.size)
            if (candidates.isEmpty()) {
                _events.value = "Nothing left to suggest"
                return@launch
            }
            run("set_daily_focus",
                jsonOf("items" to jsonArrayOf(existing + candidates.map { it.title })))
        }
    }

    fun logEnergy(value: Int) =
        run("log_energy",
            jsonOf("energy" to value, "checkpoint" to currentCheckpoint().name), announce = false)

    fun runCheckpoint(cp: Checkpoint) = run("run_checkpoint", jsonOf("checkpoint" to cp.name))

    fun planTomorrow() = run("plan_tomorrow", JSONObject())

    fun minimumMode() = run("enter_minimum_mode", JSONObject())

    fun undoLast() {
        val id = lastAuditId ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repo.audit(50).firstOrNull { it.id == id }?.let { bus.undo(it) }
            }
            lastAuditId = null
        }
    }
}
