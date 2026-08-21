package com.superflow.ui.insights

import android.app.Application
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.superflow.R
import com.superflow.data.Prefs
import com.superflow.data.Repository
import com.superflow.data.model.CheckInResult
import com.superflow.domain.Insights
import com.superflow.ui.common.BarChart
import com.superflow.ui.common.HeatmapView
import com.superflow.ui.common.visible
import com.superflow.core.time.SfTime
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Rows rendered by the Insights list. */
sealed class InsightRow {
    abstract val stableId: Long

    data class Chart(val title: String, val sub: String, val bars: List<BarChart.Bar>) : InsightRow() {
        override val stableId = 1L
    }

    data class Stats(
        val title: String, val note: String,
        val v1: String, val l1: String,
        val v2: String, val l2: String,
        val v3: String, val l3: String
    ) : InsightRow() { override val stableId = 2L }

    data class Heatmap(val title: String, val sub: String, val cells: List<Float>) : InsightRow() {
        override val stableId = 3L
    }

    data class Section(val title: String) : InsightRow() {
        override val stableId = ("s$title").hashCode().toLong()
    }

    data class HabitStat(
        val id: String, val title: String, val percent: Int,
        val detail: String, val hint: String?
    ) : InsightRow() { override val stableId = ("hs$id").hashCode().toLong() }

    data class Text(val title: String, val body: String) : InsightRow() {
        override val stableId = ("t$title").hashCode().toLong()
    }

    data class GrowthTrajectory(
        val title: String,
        val phasesCount: Int,
        val currentPhase: Int,
        val consistencies: List<Int>
    ) : InsightRow() { override val stableId = 100L }

    data class Correlation(val text: String) : InsightRow() {
        override val stableId = 101L
    }

    data class RecoveryStats(val title: String, val text: String) : InsightRow() {
        override val stableId = 102L
    }
}

class InsightsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = Repository.get(app)
    val prefs: Prefs = Prefs.get(app)
    private val _rows = MutableStateFlow<List<InsightRow>>(emptyList())
    val rows: StateFlow<List<InsightRow>> = _rows.asStateFlow()

    init { viewModelScope.launch { repo.revision.collect { refresh() } } }

    fun refresh() {
        viewModelScope.launch { _rows.value = withContext(Dispatchers.IO) { build() } }
    }

    private fun build(): List<InsightRow> {
        val rows = ArrayList<InsightRow>()
        val today = repo.clock.today()
        val snap = repo.snapshot()
        val stats = Insights.allStats(snap, repo, today)

        if (stats.isEmpty()) {
            rows.add(InsightRow.Text(
                "Nothing to measure yet",
                "Create a habit and check in once. Insights appear as soon as there is real evidence."
            ))
            return rows
        }

        // Weekly bar chart
        val daily = Insights.dailyCounts(repo, 7, today)
        rows.add(InsightRow.Chart(
            "Last 7 days", "Repetitions per day.",
            daily.map { (date, count) ->
                BarChart.Bar(SfTime.dayLetter(date), count, date == today)
            }
        ))

        // 30-day totals (from the snapshot; no extra queries)
        val window = SfTime.lastDays(30, today).map { SfTime.format(it) }.toSet()
        val checkIns = snap.checkIns.filter { it.date in window }
        val reps = checkIns.count { it.isSuccess }
        val misses = checkIns.count { it.isMiss }
        val skips = checkIns.count { it.result == CheckInResult.SKIPPED }
        val recoveries = stats.sumOf { it.recoveries }
        rows.add(InsightRow.Stats(
            "Last 30 days",
            "Recovery matters more than a perfect record. $skips intentional skips, $misses misses.",
            reps.toString(), "Repetitions",
            stats.maxOfOrNull { it.bestRun }?.toString() ?: "0", "Best run",
            recoveries.toString(), "Recoveries"
        ))

        // Heatmap of overall activity (from the snapshot; no extra queries)
        val cells = ArrayList<Float>()
        val heatDays = SfTime.lastDays(126, today)
        val byDate = snap.checkIns
            .filter { it.isSuccess }
            .groupingBy { it.date }
            .eachCount()
        val peak = (byDate.values.maxOrNull() ?: 1).coerceAtLeast(1)
        for (d in heatDays) cells.add((byDate[SfTime.format(d)] ?: 0).toFloat() / peak)
        rows.add(InsightRow.Heatmap(
            "Consistency map", "The last 18 weeks. Darker means more repetitions that day.", cells
        ))

        // Identity evidence
        val evidence = Insights.identityEvidence(snap)
        if (evidence.isNotEmpty()) {
            rows.add(InsightRow.Section("IDENTITY EVIDENCE"))
            for ((statement, votes, habits) in evidence) {
                rows.add(InsightRow.Text(
                    statement,
                    "$votes ${if (votes == 1) "vote" else "votes"} from $habits " +
                            if (habits == 1) "habit" else "habits"
                ))
            }
        }

        // Per-habit consistency
        rows.add(InsightRow.Section("BY HABIT"))
        for (s in stats.sortedByDescending { it.consistency30 }) {
            rows.add(InsightRow.HabitStat(
                s.habit.id, s.habit.title, s.consistency30,
                "${s.repetitions} reps · run of ${s.currentRun} · best ${s.bestRun}" +
                        (s.lastDone?.let { " · last ${SfTime.parseDate(it)?.let(SfTime::shortDay) ?: it}" } ?: ""),
                if (s.consistency30 in 1..39)
                    "Try shrinking this one. A smaller version you actually do beats a bigger one you skip."
                else null
            ))
        }

        // Redesign candidates
        val struggling = stats.filter { it.missesInARow >= 2 }
        if (struggling.isNotEmpty()) {
            rows.add(InsightRow.Section("WORTH A REDESIGN"))
            rows.add(InsightRow.Text(
                struggling.joinToString(", ") { it.habit.title },
                "Missed more than once in a row. That is a signal about the design, " +
                        "not about your character."
            ))
        }

        // System health (§3)
        val systems = Insights.systemHealthAll(repo)
        if (systems.isNotEmpty()) {
            rows.add(InsightRow.Section("SYSTEMS"))
            for ((title, health, habits) in systems) {
                rows.add(InsightRow.Text(
                    title,
                    if (habits == 0) "No habits under this system yet."
                    else "$health% healthy · $habits habits" +
                            if (health < 40) " · Consider shrinking or rescheduling."
                            else if (health >= 80) " · Working well."
                            else ""
                ))
            }
        }

        // Miss reasons (§8)
        val reasons = Insights.missReasons(repo, 30)
        if (reasons.isNotEmpty()) {
            rows.add(InsightRow.Section("MISS REASONS"))
            val total = reasons.sumOf { it.second }
            rows.add(InsightRow.Text("What got in the way",
                reasons.joinToString("\n") { (r, n) ->
                    "· $r — $n (${(n * 100) / total}%)"
                } + "\n\nPatterns point to the fix: time → time-blocking, energy → " +
                        "schedule at your best hours, forgot → a stronger cue."))
        }

        // Energy
        rows.add(InsightRow.Section("ENERGY"))
        rows.add(InsightRow.Text("Energy pattern", Insights.energyPattern(repo)))
        val corr = Insights.energyCorrelation(repo, 30)
        if (repo.energyLogs().size >= 6) {
            rows.add(InsightRow.Text("Energy and completion", corr))
        }

        // Reduce mode
        val reduce = Insights.reduceModeProgress(snap)
        if (reduce.isNotEmpty()) {
            rows.add(InsightRow.Section("REDUCING"))
            for ((title, resisted, slipped) in reduce) {
                rows.add(InsightRow.Text(title, "$resisted resisted · $slipped slips"))
            }
        }

        // Growth trajectory
        val growthPlans = repo.growthPlans().filter { it.isActive() }
        if (growthPlans.isNotEmpty()) {
            rows.add(InsightRow.Section("GROWTH TRAJECTORY"))
            for (plan in growthPlans) {
                val habit = repo.habit(plan.habitId)
                val consistencies = plan.weeklySnapshots.map { it.consistency }
                if (consistencies.isNotEmpty()) {
                    rows.add(InsightRow.GrowthTrajectory(
                        title = "${habit?.title ?: "Habit"} (Phase ${plan.currentPhaseIndex + 1}/${plan.phases.size})",
                        phasesCount = plan.phases.size,
                        currentPhase = plan.currentPhaseIndex,
                        consistencies = consistencies
                    ))
                }
            }
        }

        // Pattern analysis
        if (stats.size >= 2 && checkIns.size >= 20) {
            rows.add(InsightRow.Section("PATTERNS"))
            rows.add(InsightRow.Correlation(Insights.analyzePatterns(repo)))
            rows.add(InsightRow.Correlation(Insights.analyzeCorrelations(repo)))
        }

        // Recovery speed
        if (stats.any { it.recoveries > 0 }) {
            rows.add(InsightRow.Section("RECOVERY SPEED"))
            rows.add(InsightRow.RecoveryStats("Recovery trend", Insights.recoverySpeed(repo)))
        }

        // Energy-aware schedule
        if (prefs.energyTracking) {
            rows.add(InsightRow.Section("ENERGY & SCHEDULE"))
            rows.add(InsightRow.Text("Energy-aware advice", Insights.energyAwareSchedule(repo)))
        }

        return rows
    }
}

class InsightsFragment : Fragment() {

    private val model: InsightsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_list, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<TextView>(R.id.screen_title).text = getString(R.string.tab_insights)
        view.findViewById<TextView>(R.id.screen_subtitle).text =
            getString(R.string.insights_subtitle)

        ViewCompat.setOnApplyWindowInsetsListener(view.findViewById(R.id.header)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = bars.top + v.context.resources.getDimensionPixelSize(R.dimen.space_m))
            insets
        }

        val list = view.findViewById<RecyclerView>(R.id.list)
        val adapter = InsightsAdapter()
        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.rows.collect { adapter.submitList(it) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        model.refresh()
    }
}

class InsightsAdapter : ListAdapter<InsightRow, RecyclerView.ViewHolder>(DIFF) {

    companion object {
        private const val T_CHART = 0
        private const val T_STATS = 1
        private const val T_HEATMAP = 2
        private const val T_SECTION = 3
        private const val T_HABIT = 4
        private const val T_TEXT = 5

        private val DIFF = object : DiffUtil.ItemCallback<InsightRow>() {
            override fun areItemsTheSame(a: InsightRow, b: InsightRow) = a.stableId == b.stableId
            override fun areContentsTheSame(a: InsightRow, b: InsightRow) = a == b
        }
    }

    init { setHasStableIds(true) }

    override fun getItemId(position: Int) = getItem(position).stableId

    override fun getItemViewType(position: Int) = when (getItem(position)) {
        is InsightRow.Chart -> T_CHART
        is InsightRow.Stats -> T_STATS
        is InsightRow.Heatmap -> T_HEATMAP
        is InsightRow.Section -> T_SECTION
        is InsightRow.HabitStat -> T_HABIT
        is InsightRow.Text -> T_TEXT
        is InsightRow.GrowthTrajectory -> T_CHART
        is InsightRow.Correlation -> T_TEXT
        is InsightRow.RecoveryStats -> T_TEXT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return when (viewType) {
            T_CHART -> ChartVH(inf.inflate(R.layout.item_chart, parent, false))
            T_STATS -> StatsVH(inf.inflate(R.layout.item_stats, parent, false))
            T_HEATMAP -> HeatVH(inf.inflate(R.layout.item_heatmap, parent, false))
            T_SECTION -> SectionVH(inf.inflate(R.layout.item_section, parent, false))
            T_HABIT -> HabitVH(inf.inflate(R.layout.item_habit_stat, parent, false))
            else -> TextVH(inf.inflate(R.layout.item_text_card, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is InsightRow.Chart -> (holder as ChartVH).bind(row)
            is InsightRow.Stats -> (holder as StatsVH).bind(row)
            is InsightRow.Heatmap -> (holder as HeatVH).bind(row)
            is InsightRow.Section -> (holder as SectionVH).bind(row)
            is InsightRow.HabitStat -> (holder as HabitVH).bind(row)
            is InsightRow.Text -> (holder as TextVH).bind(row)
        }
    }

    class ChartVH(v: View) : RecyclerView.ViewHolder(v) {
        private val title: TextView = v.findViewById(R.id.chart_title)
        private val sub: TextView = v.findViewById(R.id.chart_sub)
        private val chart: BarChart = v.findViewById(R.id.chart)
        fun bind(row: InsightRow.Chart) {
            title.text = row.title
            sub.text = row.sub
            chart.setBars(row.bars)
        }
    }

    class StatsVH(v: View) : RecyclerView.ViewHolder(v) {
        fun bind(row: InsightRow.Stats) {
            itemView.findViewById<TextView>(R.id.stats_title).text = row.title
            itemView.findViewById<TextView>(R.id.stats_note).text = row.note
            itemView.findViewById<TextView>(R.id.stat_1_value).text = row.v1
            itemView.findViewById<TextView>(R.id.stat_1_label).text = row.l1
            itemView.findViewById<TextView>(R.id.stat_2_value).text = row.v2
            itemView.findViewById<TextView>(R.id.stat_2_label).text = row.l2
            itemView.findViewById<TextView>(R.id.stat_3_value).text = row.v3
            itemView.findViewById<TextView>(R.id.stat_3_label).text = row.l3
        }
    }

    class HeatVH(v: View) : RecyclerView.ViewHolder(v) {
        private val heat: HeatmapView = v.findViewById(R.id.heatmap)
        fun bind(row: InsightRow.Heatmap) {
            itemView.findViewById<TextView>(R.id.heatmap_title).text = row.title
            itemView.findViewById<TextView>(R.id.heatmap_sub).text = row.sub
            heat.setCells(row.cells)
        }
    }

    class SectionVH(v: View) : RecyclerView.ViewHolder(v) {
        fun bind(row: InsightRow.Section) { (itemView as TextView).text = row.title }
    }

    class HabitVH(v: View) : RecyclerView.ViewHolder(v) {
        private val bar: LinearProgressIndicator = v.findViewById(R.id.hs_bar)
        fun bind(row: InsightRow.HabitStat) {
            itemView.findViewById<TextView>(R.id.hs_title).text = row.title
            itemView.findViewById<TextView>(R.id.hs_percent).text = "${row.percent}%"
            itemView.findViewById<TextView>(R.id.hs_detail).text = row.detail
            val hint = itemView.findViewById<TextView>(R.id.hs_hint)
            hint.visible(row.hint != null)
            row.hint?.let { hint.text = it }
            bar.setProgressCompat(row.percent, true)
        }
    }

    class TextVH(v: View) : RecyclerView.ViewHolder(v) {
        fun bind(row: InsightRow.Text) {
            itemView.findViewById<TextView>(R.id.text_title).text = row.title
            itemView.findViewById<TextView>(R.id.text_body).text = row.body
        }
    }
}
