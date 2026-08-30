package com.superflow.ui.insights

import android.app.Application
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.superflow.R
import com.superflow.core.time.SfTime
import com.superflow.data.Repository
import com.superflow.design.HistoryStates
import com.superflow.design.Periods
import com.superflow.domain.Analytics
import com.superflow.domain.Insights
import com.superflow.ui.common.sfContent
import com.superflow.ui.screens.HabitConsistency
import com.superflow.ui.screens.InsightsScreen
import com.superflow.ui.screens.InsightsUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Compose host for Insights (plan 11.3).
 *
 * Unlike Today and Journey, this one does not share the View screen's
 * ViewModel. [InsightsViewModel] emits pre-rendered adapter rows with the
 * copy already baked into them; the Compose screen wants the numbers and
 * decides its own copy from [Periods] and [HistoryStates]. Reshaping one
 * into the other would mean parsing strings back into data, which is worse
 * than computing them twice.
 *
 * `design.Rendering` decides which of the two is live.
 */
class ComposeInsightsFragment : Fragment() {

    private val model: ComposeInsightsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflated rather than constructed: see fragment_compose_tab.xml for
        // why a code-built ComposeView breaks inside ViewPager2.
        val host = inflater.inflate(R.layout.fragment_compose_tab, container, false)
            .findViewById<ComposeView>(R.id.compose_host)
        return host.sfContent {
            val state by model.state.collectAsState()
            InsightsScreen(state = state, onPeriodChange = model::setPeriod)
        }
    }

    override fun onResume() {
        super.onResume()
        model.refresh()
    }
}

/**
 * Insights state for the Compose screen.
 *
 * Everything here is a measurement or a raw series. Not one field is a
 * sentence: what a number means is decided on the screen, next to the
 * sample-size gate that decides whether to say it at all.
 */
class ComposeInsightsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = Repository.get(app)

    private val _state = MutableStateFlow(InsightsUiState())
    val state: StateFlow<InsightsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { repo.revision.collect { refresh() } }
    }

    fun setPeriod(periodId: Int) {
        if (periodId == _state.value.periodId) return
        _state.value = _state.value.copy(periodId = periodId)
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val built = withContext(Dispatchers.IO) { build(_state.value.periodId) }
            _state.value = built
        }
    }

    private fun build(periodId: Int): InsightsUiState {
        val period = Periods.byId(periodId)
        val today = repo.clock.today()
        val habits = repo.habits()

        if (habits.isEmpty()) {
            return InsightsUiState(loading = false, periodId = periodId)
        }

        val days = SfTime.lastDays(period.days, today)

        // One query for the whole window rather than one per day: a year
        // view would otherwise be 365 round trips through SQLite.
        val checkIns = repo.checkInsBetween(
            SfTime.format(days.first()), SfTime.format(days.last())
        )
        val successByDate = checkIns.filter { it.isSuccess }
            .groupingBy { it.date }.eachCount()

        // Scheduled counts have to be recomputed per day: a habit's schedule
        // can change, and using today's count for every day in the window
        // would silently rewrite history.
        val daily = days.map { date ->
            val scheduled = repo.habitsForDay(date).size
            if (scheduled == 0) 0.0
            else (successByDate[SfTime.format(date)] ?: 0).toDouble() / scheduled
        }

        return InsightsUiState(
            loading = false,
            periodId = periodId,
            daily = daily,
            heatmap = days.map { heatState(it, today, successByDate) },
            firstWeekday = days.firstOrNull()?.dayOfWeek?.value?.rem(7) ?: 0,
            perHabit = habits.map { habit ->
                val stats = Insights.forHabit(repo, habit, today)
                HabitConsistency(
                    id = habit.id,
                    title = habit.title,
                    percent = stats.consistency30,
                    samples = stats.repetitions,
                )
            }.sortedByDescending { it.percent },
            energyPairs = energyPairs(days, daily),
            timeOfDay = Analytics.timeOfDayPatterns(habits, checkIns, today),
        )
    }

    /**
     * One day's state in the [HistoryStates] encoding.
     *
     * A day with nothing scheduled is INACTIVE rather than MISSED. Marking
     * a rest day as a miss is the single most demoralising thing a habit
     * tracker can do, and it is also just wrong.
     */
    private fun heatState(
        date: LocalDate,
        today: LocalDate,
        successByDate: Map<String, Int>,
    ): Int {
        val scheduled = repo.habitsForDay(date).size
        if (scheduled == 0) return HistoryStates.INACTIVE
        val done = successByDate[SfTime.format(date)] ?: 0
        return when {
            done >= scheduled -> HistoryStates.COMPLETED
            // Today is not a failure until it is over.
            date == today -> HistoryStates.PENDING
            done > 0 -> HistoryStates.COMPLETED
            else -> HistoryStates.MISSED
        }
    }

    /**
     * Energy against completion, one point per day that has both.
     *
     * Days without an energy rating are dropped rather than imputed. An
     * imputed point is an invention, and this pair feeds a correlation the
     * screen may go on to describe in words.
     */
    private fun energyPairs(days: List<LocalDate>, daily: List<Double>): List<Pair<Double, Double>> {
        val out = ArrayList<Pair<Double, Double>>()
        days.forEachIndexed { index, date ->
            // A day can carry several checkpoint ratings; the day's energy
            // is their mean, not the last one logged, so an evening slump
            // does not erase a good morning.
            val logs = repo.energyFor(SfTime.format(date))
            if (logs.isEmpty()) return@forEachIndexed
            out.add(logs.map { it.energy }.average() to daily[index])
        }
        return out
    }
}
