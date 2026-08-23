package com.superflow.ui.insights

import android.app.Application
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
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
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.appbar.MaterialToolbar
import com.superflow.R
import com.superflow.data.Prefs
import com.superflow.data.Repository
import com.superflow.data.model.CheckInResult
import com.superflow.domain.Insights
import com.superflow.ui.common.BarChart
import com.superflow.ui.common.HeatmapView
import com.superflow.ui.common.snack
import com.superflow.ui.common.visible
import com.superflow.ui.common.wireRefresh
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
}

class InsightsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = Repository.get(app)
    private val _period = MutableStateFlow(30)
    val period: StateFlow<Int> = _period.asStateFlow()
    private val _rows = MutableStateFlow<List<InsightRow>>(emptyList())
    val rows: StateFlow<List<InsightRow>> = _rows.asStateFlow()

    init {
        viewModelScope.launch {
            repo.revision.collect { refresh() }
        }
        viewModelScope.launch {
            period.collect { refresh() }
        }
    }

    fun setPeriod(days: Int) {
        _period.value = days
    }

    fun refresh() {
        viewModelScope.launch { _rows.value = withContext(Dispatchers.IO) { build() } }
    }

    private fun build(): List<InsightRow> {
        val rows = ArrayList<InsightRow>()
        val days = _period.value
        val today = repo.clock.today()
        val all = Insights.allStats(repo)
        if (all.isEmpty()) {
            rows.add(InsightRow.Text(
                "Nothing to measure yet",
                "Create a habit and check in once. Insights appear as soon as there is real evidence."
            ))
            return rows
        }

        // Repetitions chart for the selected period (bars for 7d; weekly buckets otherwise).
        val daily = Insights.dailyCounts(repo, days.coerceAtMost(90))
        rows.add(InsightRow.Chart(
            "Last $days days", "Repetitions per day.",
            daily.map { (date, count) ->
                BarChart.Bar(SfTime.dayLetter(date), count, date == today)
            }
        ))

        // Delta vs previous equal-length period (#56).
        val cur = countWindow(repo, today, days)
        val prev = countWindow(repo, today.minusDays(days.toLong()), days)
        val change = if (prev.successes == 0) 0 else
            ((cur.successes - prev.successes).toDouble() / prev.successes * 100).toInt()
        val arrow = when {
            change > 0 -> "↑$change%"
            change < 0 -> "↓${-change}%"
            else -> "—"
        }
        rows.add(InsightRow.Stats(
            "Last $days days",
            "$arrow vs the previous $days days. Recovery matters more than a perfect record. " +
                    "${cur.skips} intentional skips, ${cur.misses} misses.",
            cur.successes.toString(), "Repetitions",
            all.maxOfOrNull { it.bestRun }?.toString() ?: "0", "Best run",
            all.sumOf { it.recoveries }.toString(), "Recoveries"
        ))

        // Expanded stat strip (#60).
        val stretch = all.sumOf { s ->
            val cis = repo.checkInsOf(s.habit.id).filter { it.date in SfTime.lastDays(days, today).map(SfTime::format) }
            cis.count { it.level.name == "STRETCH" }
        }
        val avgCons = all.filter { it.hasEnoughData }.map { it.consistency30 }.average().toInt()
        rows.add(InsightRow.Stats(
            "At a glance",
            "Averaged across habits with enough data.",
            "${all.size}", "Habits",
            "$avgCons%", "Avg consistency",
            stretch.toString(), "Stretch reps"
        ))

        // Day-of-week pattern (#52) over the selected window.
        if (days >= 14) {
            val dayCounts = IntArray(7)
            val dayOpp = IntArray(7)
            for (s in all) {
                val series = Insights.seriesFor(repo, s.habit, days, today)
                for (op in series) {
                    if (op.counts) {
                        val dow = op.date.dayOfWeek.value - 1
                        dayOpp[dow]++
                        if (op.succeeded) dayCounts[dow]++
                    }
                }
            }
            val names = listOf("M", "T", "W", "T", "F", "S", "S")
            val bars = (0..6).map { dow ->
                val pct = if (dayOpp[dow] == 0) 0
                else (dayCounts[dow] * 100 / dayOpp[dow])
                BarChart.Bar(names[dow], pct, dow == today.dayOfWeek.value - 1)
            }
            val best = bars.indices.maxByOrNull { bars[it].value }
            val worst = bars.indices.minByOrNull { bars[it].value }
            if (best != null && worst != null && dayOpp.sum() >= 7) {
                rows.add(InsightRow.Text(
                    "Weekly rhythm",
                    "Best: ${fullDow(best)} (${bars[best].value}%) · " +
                            "Hardest: ${fullDow(worst)} (${bars[worst].value}%). " +
                            "Treat these as hints, not rules."
                ))
            }
        }

        // Heatmap over 18 weeks.
        val cells = ArrayList<Float>()
        val heatDays = SfTime.lastDays(126, today)
        val byDate = repo.checkInsBetween(
            SfTime.format(heatDays.first()), SfTime.format(heatDays.last()))
            .filter { it.isSuccess }
            .groupingBy { it.date }.eachCount()
        val peak = (byDate.values.maxOrNull() ?: 1).coerceAtLeast(1)
        for (d in heatDays) cells.add((byDate[SfTime.format(d)] ?: 0).toFloat() / peak)
        rows.add(InsightRow.Heatmap(
            "Consistency map", "The last 18 weeks. Darker means more repetitions that day.", cells
        ))

        // Milestone timeline (#54).
        val milestones = buildMilestones(repo, all)
        if (milestones.isNotEmpty()) {
            rows.add(InsightRow.Section("MILESTONES"))
            milestones.take(6).forEach { rows.add(InsightRow.Text(it.first, it.second)) }
        }

        // Growth trajectory (Functional Plan §2)
        val growthPlans = repo.growthPlans().filter { it.isActive() }
        if (growthPlans.isNotEmpty()) {
            rows.add(InsightRow.Section("GROWTH TRAJECTORY"))
            for (plan in growthPlans) {
                val habit = repo.habit(plan.habitId)
                val consistencies = plan.weeklySnapshots.map { it.consistency }
                if (consistencies.isNotEmpty()) {
                    rows.add(InsightRow.Text(
                        "${habit?.title ?: "Habit"} (Phase ${plan.currentPhaseIndex + 1}/${plan.phases.size})",
                        "Weekly consistency: ${consistencies.joinToString("%, ")}"
                    ))
                }
            }
        }

        // Pattern analysis (Functional Plan §9)
        if (all.size >= 2) {
            rows.add(InsightRow.Section("PATTERNS"))
            rows.add(InsightRow.Text("Patterns", Insights.analyzePatterns(repo)))
            rows.add(InsightRow.Text("Correlations", Insights.analyzeCorrelations(repo)))
        }

        // Recovery speed (Functional Plan §9)
        if (all.any { it.recoveries > 0 }) {
            rows.add(InsightRow.Section("RECOVERY SPEED"))
            rows.add(InsightRow.Text("Recovery trend", Insights.recoverySpeed(repo)))
        }

        // Identity evidence
        val evidence = Insights.identityEvidence(repo)
        if (evidence.isNotEmpty()) {
            rows.add(InsightRow.Section("IDENTITY EVIDENCE"))
            for ((statement, votes, n) in evidence) {
                rows.add(InsightRow.Text(
                    statement,
                    "$votes ${if (votes == 1) "vote" else "votes"} from $n " +
                            if (n == 1) "habit" else "habits"
                ))
            }
        }

        // Per-habit consistency
        rows.add(InsightRow.Section("BY HABIT"))
        for (s in all.sortedByDescending { it.consistency30 }) {
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
        val struggling = all.filter { it.missesInARow >= 2 }
        if (struggling.isNotEmpty()) {
            rows.add(InsightRow.Section("WORTH A REDESIGN"))
            rows.add(InsightRow.Text(
                struggling.joinToString(", ") { it.habit.title },
                "Missed more than once in a row. That is a signal about the design, " +
                        "not about your character."
            ))
        }

        // Energy
        rows.add(InsightRow.Section("ENERGY"))
        rows.add(InsightRow.Text("Energy pattern", Insights.energyPattern(repo)))

        // Reduce mode
        val reduce = Insights.reduceModeProgress(repo)
        if (reduce.isNotEmpty()) {
            rows.add(InsightRow.Section("REDUCING"))
            for ((title, resisted, slipped) in reduce) {
                rows.add(InsightRow.Text(title, "$resisted resisted · $slipped slips"))
            }
        }

        return rows
    }

    private data class Window(val successes: Int, val misses: Int, val skips: Int)

    private fun countWindow(repo: Repository, end: LocalDate, days: Int): Window {
        val start = end.minusDays(days.toLong() - 1)
        val cis = repo.checkInsBetween(SfTime.format(start), SfTime.format(end))
        return Window(
            cis.count { it.isSuccess },
            cis.count { it.isMiss },
            cis.count { it.result == CheckInResult.SKIPPED }
        )
    }

    private fun fullDow(index: Int): String =
        listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")[index]

    /** Build a chronological list of (title, detail) milestone markers. */
    private fun buildMilestones(
        repo: Repository, stats: List<com.superflow.data.model.HabitStats>
    ): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        for (s in stats) {
            val first = s.habit.createdAt
            val firstLabel = SfTime.shortDay(
                java.time.Instant.ofEpochMilli(first)
                    .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            )
            out.add("Started ${s.habit.title}" to firstLabel)
            if (s.bestRun >= 7) out.add("7-day run: ${s.habit.title}" to "${s.bestRun} days best")
            if (s.repetitions >= 21) out.add("21 reps: ${s.habit.title}" to "${s.repetitions} total")
            if (s.repetitions >= 100) out.add("100 reps: ${s.habit.title}" to "${s.repetitions} total")
        }
        return out.sortedBy { it.second }
    }
}

class InsightsFragment : Fragment() {

    private val model: InsightsViewModel by viewModels()

    /** Dismisses the refresh spinner once the new rows are on screen. */
    private var pendingRefresh: (() -> Unit)? = null

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

        // Period switcher (#47): 7 / 30 / 90 days / year.
        val margin = resources.getDimensionPixelSize(R.dimen.screen_margin)
        val periodGroup = ChipGroup(requireContext()).apply {
            isSingleSelection = true
            isSelectionRequired = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = resources.getDimensionPixelSize(R.dimen.space_s) }
            setPadding(margin, 0, margin, 0)
        }
        listOf(7 to "7d", 30 to "30d", 90 to "90d", 365 to "Year").forEach { (days, label) ->
            periodGroup.addView(Chip(requireContext()).apply {
                text = label
                isCheckable = true
                isChecked = days == model.period.value
                setEnsureMinTouchTargetSize(false)
                setOnClickListener { model.setPeriod(days) }
            })
        }
        val header = view.findViewById<ViewGroup>(R.id.header)
        header.addView(periodGroup)

        val toolbar = view.findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        toolbar.inflateMenu(R.menu.insights_menu)
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_share_card -> { shareCard(); true }
                else -> false
            }
        }

        val list = view.findViewById<RecyclerView>(R.id.list)
        // The visible equivalent of the pull gesture, for users who have
        // switched it off or cannot perform it.
        view.findViewById<MaterialToolbar>(R.id.toolbar).apply {
            inflateMenu(R.menu.list_menu)
            setOnMenuItemClickListener { item ->
                if (item.itemId == R.id.action_refresh) { model.refresh(); true } else false
            }
        }
        view.findViewById<SwipeRefreshLayout>(R.id.refresh)
            .wireRefresh(Prefs.get(requireContext())) { done ->
                pendingRefresh = done
                model.refresh()
            }
        val adapter = InsightsAdapter()
        list.layoutManager = LinearLayoutManager(requireContext())
        list.setHasFixedSize(true)
        list.setItemViewCacheSize(6)
        list.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.rows.collect { rows ->
                    adapter.submitList(rows) {
                        pendingRefresh?.invoke()
                        pendingRefresh = null
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        model.refresh()
    }

    private fun shareCard() {
        val repo = com.superflow.data.Repository.get(requireContext())
        val ctx = requireContext()
        lifecycleScope.launch {
            val file = runCatching {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.superflow.ui.common.ShareCard.saveToCache(ctx, repo)
                }
            }.getOrNull()
            if (file == null) requireView().snack("Could not prepare the card")
            else runCatching { com.superflow.ui.common.ShareCard.shareFile(ctx, file) }
        }
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
