package com.superflow.ui.coach

import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
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
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.superflow.R
import com.superflow.ai.Agent
import com.superflow.ai.Coordinator
import com.superflow.ai.VoiceInputV2
import com.superflow.data.Prefs
import com.superflow.data.Repository
import com.superflow.data.model.ProactiveSuggestion
import com.superflow.data.model.SuggestionType
import com.superflow.domain.Capabilities
import com.superflow.domain.GrowthEngine
import com.superflow.domain.Insights
import com.superflow.ui.activity.ActivityLogActivity
import com.superflow.ui.blueprint.BlueprintActivity
import com.superflow.ui.common.dpPx
import com.superflow.ui.common.snack
import com.superflow.ui.common.visible
import com.superflow.ui.engine.AiEngineActivity
import com.superflow.ui.journey.JourneyViewModel
import com.superflow.core.time.SfTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Studio — the unified AI workspace (Section 4.5 of the Grand Plan).
 *
 * Replaces the old Coach tab with:
 *  - Status bar (AI mode, provider, capability count)
 *  - Quick action chips (Plan, Analyze, Review, Reflect, Blueprint, Diagnose)
 *  - Proactive AI suggestions
 *  - Conversation with voice input
 */
class StudioFragment : Fragment() {

    private val model: StudioViewModel by viewModels()
    private lateinit var adapter: StudioAdapter
    private lateinit var list: RecyclerView
    private var voice: VoiceInputV2.PlatformVoiceEngine? = null

    private val micPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startVoice() else view?.snack("Microphone permission is required")
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_coach, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar)
        val input = view.findViewById<TextInputEditText>(R.id.input)
        val send = view.findViewById<MaterialButton>(R.id.btn_send)
        val mic = view.findViewById<MaterialButton>(R.id.btn_mic)
        val thinking = view.findViewById<LinearProgressIndicator>(R.id.thinking)
        list = view.findViewById(R.id.list)

        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = bars.top)
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(view.findViewById(R.id.composer)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.updatePadding(bottom = maxOf(bars.bottom, ime.bottom) + v.context.dpPx(76))
            insets
        }

        adapter = StudioAdapter(
            onQuickAction = { action -> model.send(action) },
            onSuggestionApply = { suggestion -> applyProactiveSuggestion(suggestion) },
            onSuggestionDismiss = { id -> model.dismissSuggestion(id) },
            onStatusAction = { openEngine() },
            onBlueprint = { openBlueprint() }
        )
        list.layoutManager = LinearLayoutManager(requireContext()).apply { stackFromEnd = false }
        list.adapter = adapter

        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_blueprint -> { openBlueprint(); true }
                R.id.action_engine -> { openEngine(); true }
                R.id.action_activity -> {
                    startActivity(Intent(requireContext(), ActivityLogActivity::class.java)); true
                }
                R.id.action_clear -> { model.clear(); true }
                else -> false
            }
        }

        fun submit() {
            val text = input.text?.toString()?.trim().orEmpty()
            if (text.isEmpty()) return
            input.setText("")
            model.send(text)
        }
        send.setOnClickListener { submit() }
        input.setOnEditorActionListener { _, _, _ -> submit(); true }

        mic.visible(
            VoiceInputV2.availableProviders(requireContext()).isNotEmpty()
                && model.prefs.voiceEnabled
        )
        mic.setOnClickListener {
            if (voice?.isListening() == true) { voice?.stop(); return@setOnClickListener }
            if (VoiceInputV2.hasPermission(requireContext())) startVoice()
            else micPermission.launch(VoiceInputV2.PERMISSION)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    model.rows.collect {
                        adapter.submitList(it) {
                            if (it.isNotEmpty()) list.scrollToPosition(adapter.itemCount - 1)
                        }
                    }
                }
                launch {
                    model.busy.collect {
                        thinking.visible(it); send.isEnabled = !it
                    }
                }
                launch {
                    model.errors.collect {
                        if (it != null) { view?.snack(it); model.consumeError() }
                    }
                }
            }
        }
    }

    private fun startVoice() {
        val input = view?.findViewById<TextInputEditText>(R.id.input) ?: return
        val mic = view?.findViewById<MaterialButton>(R.id.btn_mic) ?: return
        try {
            val engine = VoiceInputV2.create(requireContext(), VoiceInputV2.Provider.PLATFORM)
            voice = engine as? VoiceInputV2.PlatformVoiceEngine
            engine.start(object : VoiceInputV2.Callbacks {
                override fun onReady() {
                    mic.setIconResource(R.drawable.ic_pause)
                    input.hint = "Listening..."
                }
                override fun onPartial(text: String) { input.setText(text) }
                override fun onResult(text: String) {
                    input.setText(""); model.send(text)
                }
                override fun onError(message: String) { view?.snack(message) }
                override fun onEnd() {
                    mic.setIconResource(R.drawable.ic_mic)
                    input.hint = "Message Studio..."
                }
            })
        } catch (e: Exception) {
            view?.snack(e.message ?: "Voice not available")
        }
    }

    override fun onPause() { super.onPause(); voice?.stop() }
    override fun onResume() { super.onResume(); model.refresh() }

    private fun openBlueprint() =
        startActivity(Intent(requireContext(), BlueprintActivity::class.java))
    private fun openEngine() =
        startActivity(Intent(requireContext(), AiEngineActivity::class.java))
    private fun applyProactiveSuggestion(suggestion: ProactiveSuggestion) {
        model.applySuggestion(suggestion)
    }
}

/* ───────────────────────────────────────────────────────────────── view model ── */

class StudioViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = Repository.get(app)
    private val agent = Agent.get(app)
    val prefs: Prefs = Prefs.get(app)

    private val _rows = MutableStateFlow<List<StudioRow>>(emptyList())
    val rows: StateFlow<List<StudioRow>> = _rows.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _errors = MutableStateFlow<String?>(null)
    val errors: StateFlow<String?> = _errors.asStateFlow()

    init {
        viewModelScope.launch { repo.revision.collect { refresh() } }
        viewModelScope.launch { prefs.changes.collect { refresh() } }
    }

    fun consumeError() { _errors.value = null }
    fun refresh() {
        viewModelScope.launch { _rows.value = withContext(Dispatchers.IO) { build() } }
    }

    private fun build(): List<StudioRow> {
        val rows = ArrayList<StudioRow>()
        val full = prefs.fullControlActive()
        val engine = when {
            prefs.localCoordinatorOnly -> "Local Coordinator"
            prefs.cloudReady() -> "${prefs.providerName} · ${prefs.model}"
            else -> "Local (no cloud)"
        }

        // Status bar
        rows.add(StudioRow.Status(
            title = if (full) "⚡ Full Control" else "🔍 Guided",
            subtitle = "$engine · ${Capabilities.all().size} caps",
            action = if (full) "Manage" else "Activate",
            active = full
        ))

        // Quick action chips (Section 4.6)
        rows.add(StudioRow.QuickActions(listOf(
            "📋 Plan", "📊 Analyze", "🔄 Review", "💡 Ideas",
            "🔧 Diagnose", "📝 Reflect", "🎯 Blueprint", "⬆ Upgrade"
        )))

        // Proactive AI suggestions
        val suggestions = repo.proactiveSuggestions()
        if (suggestions.isNotEmpty()) {
            rows.add(StudioRow.Section("SUGGESTIONS"))
            suggestions.take(3).forEach { rows.add(StudioRow.Suggestion(it)) }
        }

        // Growth plan status
        val activePlans = repo.growthPlans().filter { it.isActive() }
        if (activePlans.isNotEmpty()) {
            rows.add(StudioRow.Section("GROWTH"))
            activePlans.forEach { plan ->
                val habit = repo.habit(plan.habitId)
                val phase = plan.phases.getOrNull(plan.currentPhaseIndex)
                rows.add(StudioRow.GrowthStatus(
                    habit = habit?.title ?: "Unknown",
                    phase = phase?.label ?: "",
                    progress = (plan.currentPhaseIndex.toFloat() / plan.phases.size * 100).toInt()
                ))
            }
        }

        // Conversation messages
        val messages = repo.messages(60)
        if (messages.isEmpty()) {
            rows.add(StudioRow.Section("GETTING STARTED"))
            rows.add(StudioRow.Suggestions(Coordinator.suggestions(repo)))
            rows.add(StudioRow.CoachCard(Coordinator.coachCard(repo)))
        } else {
            messages.forEach { msg ->
                rows.add(StudioRow.Message(it, msg.text, msg.role == "user", msg.meta))
            }
        }

        // Today's progress mini-card
        val (done, total) = Insights.dayProgress(repo)
        rows.add(StudioRow.Section("TODAY"))
        rows.add(StudioRow.DayProgress(done, total))

        return rows
    }

    fun send(text: String) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            val outcome = agent.send(text)
            _busy.value = false
            if (outcome.error != null) _errors.value = outcome.error
            refresh()
        }
    }

    fun clear() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.clearMessages() }
            refresh()
        }
    }

    fun dismissSuggestion(id: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.dismissProactiveSuggestion(id) }
            refresh()
        }
    }

    fun applySuggestion(suggestion: ProactiveSuggestion) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repo.applyProactiveSuggestion(suggestion.id)
                if (suggestion.autoActionJson.isNotBlank()) {
                    val bus = com.superflow.domain.CommandBus.get(getApplication())
                    val obj = com.superflow.util.parseObject(suggestion.autoActionJson)
                    if (obj != null) {
                        bus.executeJson(obj.toString(), com.superflow.domain.Actor.AI)
                    }
                }
            }
            refresh()
        }
    }
}

/* ─────────────────────────────────────────────────────────────── row types ── */

sealed class StudioRow {
    abstract val stableId: Long

    data class Status(val title: String, val subtitle: String, val action: String, val active: Boolean) :
        StudioRow() { override val stableId = 1L }

    data class QuickActions(val actions: List<String>) : StudioRow() {
        override val stableId = 2L
    }

    data class Section(val title: String) : StudioRow() {
        override val stableId = ("s$title").hashCode().toLong()
    }

    data class Suggestion(val suggestion: ProactiveSuggestion) : StudioRow() {
        override val stableId = suggestion.id.hashCode().toLong()
    }

    data class GrowthStatus(val habit: String, val phase: String, val progress: Int) :
        StudioRow() { override val stableId = ("g$habit").hashCode().toLong() }

    data class Suggestions(val items: List<String>) : StudioRow() {
        override val stableId = 3L
    }

    data class CoachCard(val text: String) : StudioRow() {
        override val stableId = 10L
    }

    data class DayProgress(val done: Int, val total: Int) : StudioRow() {
        override val stableId = 11L
    }

    data class Message(val id: String, val text: String, val fromUser: Boolean, val meta: String) :
        StudioRow() { override val stableId = id.hashCode().toLong() }
}

/* ─────────────────────────────────────────────────────────────── adapter ── */

class StudioAdapter(
    private val onQuickAction: (String) -> Unit,
    private val onSuggestionApply: (ProactiveSuggestion) -> Unit,
    private val onSuggestionDismiss: (String) -> Unit,
    private val onStatusAction: () -> Unit,
    private val onBlueprint: () -> Unit
) : ListAdapter<StudioRow, RecyclerView.ViewHolder>(DIFF) {

    companion object {
        private const val T_STATUS = 0; private const val T_ACTIONS = 1
        private const val T_SECTION = 2; private const val T_SUGGESTION = 3
        private const val T_GROWTH = 4; private const val T_SUGGEST = 5
        private const val T_COACH = 6; private const val T_PROGRESS = 7
        private const val T_MESSAGE = 8

        private val DIFF = object : DiffUtil.ItemCallback<StudioRow>() {
            override fun areItemsTheSame(a: StudioRow, b: StudioRow) = a.stableId == b.stableId
            override fun areContentsTheSame(a: StudioRow, b: StudioRow) = a == b
        }
    }

    init { setHasStableIds(true) }

    override fun getItemId(position: Int) = getItem(position).stableId

    override fun getItemViewType(position: Int) = when (getItem(position)) {
        is StudioRow.Status -> T_STATUS; is StudioRow.QuickActions -> T_ACTIONS
        is StudioRow.Section -> T_SECTION; is StudioRow.Suggestion -> T_SUGGESTION
        is StudioRow.GrowthStatus -> T_GROWTH; is StudioRow.Suggestions -> T_SUGGEST
        is StudioRow.CoachCard -> T_COACH; is StudioRow.DayProgress -> T_PROGRESS
        is StudioRow.Message -> T_MESSAGE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return when (viewType) {
            T_STATUS -> StatusVH(inf.inflate(R.layout.item_ai_status, parent, false))
            T_ACTIONS -> ActionsVH(inf.inflate(R.layout.item_suggestions, parent, false))
            T_SECTION -> SectionVH(inf.inflate(R.layout.item_section, parent, false))
            T_SUGGESTION -> SuggestionVH(inf.inflate(R.layout.item_text_card, parent, false))
            T_GROWTH -> GrowthVH(inf.inflate(R.layout.item_habit_stat, parent, false))
            T_SUGGEST -> SuggestVH(inf.inflate(R.layout.item_suggestions, parent, false))
            T_COACH -> CoachVH(inf.inflate(R.layout.item_text_card, parent, false))
            T_PROGRESS -> ProgressVH(inf.inflate(R.layout.item_progress, parent, false))
            T_MESSAGE -> MessageVH(inf.inflate(R.layout.item_message, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is StudioRow.Status -> (holder as StatusVH).bind(row)
            is StudioRow.QuickActions -> (holder as ActionsVH).bind(row)
            is StudioRow.Section -> (holder as SectionVH).bind(row)
            is StudioRow.Suggestion -> (holder as SuggestionVH).bind(row)
            is StudioRow.GrowthStatus -> (holder as GrowthVH).bind(row)
            is StudioRow.Suggestions -> (holder as SuggestVH).bind(row)
            is StudioRow.CoachCard -> (holder as CoachVH).bind(row)
            is StudioRow.DayProgress -> (holder as ProgressVH).bind(row)
            is StudioRow.Message -> (holder as MessageVH).bind(row)
        }
    }

    /* ── View Holders ── */

    inner class StatusVH(v: View) : RecyclerView.ViewHolder(v) {
        fun bind(row: StudioRow.Status) {
            itemView.findViewById<TextView>(R.id.status_title).text = row.title
            itemView.findViewById<TextView>(R.id.status_body).text = row.subtitle
            itemView.findViewById<MaterialButton>(R.id.status_action).apply {
                text = row.action; setOnClickListener { onStatusAction() }
            }
        }
    }

    inner class ActionsVH(v: View) : RecyclerView.ViewHolder(v) {
        private val chips: ChipGroup = v.findViewById(R.id.suggestion_chips)
        fun bind(row: StudioRow.QuickActions) {
            chips.removeAllViews()
            for (action in row.actions) {
                chips.addView(Chip(chips.context).apply {
                    text = action
                    isCheckable = false
                    isClickable = true
                    setEnsureMinTouchTargetSize(false)
                    setOnClickListener { onQuickAction(action) }
                })
            }
        }
    }

    inner class SectionVH(v: View) : RecyclerView.ViewHolder(v) {
        fun bind(row: StudioRow.Section) { (itemView as TextView).text = row.title }
    }

    inner class SuggestionVH(v: View) : RecyclerView.ViewHolder(v) {
        fun bind(row: StudioRow.Suggestion) {
            val t = row.suggestion.type.name.lowercase().replaceFirstChar { it.uppercase() }
            itemView.findViewById<TextView>(R.id.text_title).text = "$t suggestion"
            itemView.findViewById<TextView>(R.id.text_body).text = row.suggestion.text
            itemView.setOnClickListener { onSuggestionApply(row.suggestion) }
            itemView.setOnLongClickListener {
                onSuggestionDismiss(row.suggestion.id); true
            }
        }
    }

    inner class GrowthVH(v: View) : RecyclerView.ViewHolder(v) {
        fun bind(row: StudioRow.GrowthStatus) {
            itemView.findViewById<TextView>(R.id.hs_title).text = row.habit
            itemView.findViewById<TextView>(R.id.hs_percent).text = "${row.progress}%"
            itemView.findViewById<TextView>(R.id.hs_detail).text = row.phase
            itemView.findViewById<LinearProgressIndicator>(R.id.hs_bar)
                .setProgressCompat(row.progress, true)
        }
    }

    inner class SuggestVH(v: View) : RecyclerView.ViewHolder(v) {
        private val chips: ChipGroup = v.findViewById(R.id.suggestion_chips)
        fun bind(row: StudioRow.Suggestions) {
            chips.removeAllViews()
            for (s in row.items) {
                chips.addView(Chip(chips.context).apply {
                    text = s; isCheckable = false; setEnsureMinTouchTargetSize(false)
                    setOnClickListener { onQuickAction(s) }
                })
            }
        }
    }

    inner class CoachVH(v: View) : RecyclerView.ViewHolder(v) {
        fun bind(row: StudioRow.CoachCard) {
            itemView.findViewById<TextView>(R.id.text_title).text = "Offline coach"
            itemView.findViewById<TextView>(R.id.text_body).text = row.text
        }
    }

    inner class ProgressVH(v: View) : RecyclerView.ViewHolder(v) {
        fun bind(row: StudioRow.DayProgress) {
            val fraction = if (row.total == 0) 0f else row.done.toFloat() / row.total
            itemView.findViewById<com.superflow.ui.common.ProgressRing>(R.id.ring).apply {
                centerLabel = if (row.total == 0) "—" else "${(fraction * 100).toInt()}%"
                centerSub = if (row.total == 0) "" else "${row.done}/${row.total}"
                setProgress(fraction)
            }
            itemView.findViewById<TextView>(R.id.progress_title).text =
                "${row.done} of ${row.total} actions"
        }
    }

    inner class MessageVH(v: View) : RecyclerView.ViewHolder(v) {
        private val row: LinearLayout = v.findViewById(R.id.msg_row)
        private val card: MaterialCardView = v.findViewById(R.id.msg_card)
        private val text: TextView = v.findViewById(R.id.msg_text)
        private val meta: TextView = v.findViewById(R.id.msg_meta)

        fun bind(m: StudioRow.Message) {
            text.text = m.text
            row.gravity = if (m.fromUser) android.view.Gravity.END else android.view.Gravity.START
            val ctx = itemView.context
            card.setCardBackgroundColor(
                com.google.android.material.color.MaterialColors.getColor(
                    card,
                    if (m.fromUser) com.google.android.material.R.attr.colorPrimaryContainer
                    else com.google.android.material.R.attr.colorSurface
                )
            )
            card.strokeWidth = if (m.fromUser) 0 else ctx.dpPx(1)
            val lp = card.layoutParams as LinearLayout.LayoutParams
            lp.marginStart = if (m.fromUser) ctx.dpPx(48) else 0
            lp.marginEnd = if (m.fromUser) 0 else ctx.dpPx(48)
            card.layoutParams = lp
            meta.visible(!m.fromUser && m.meta.isNotBlank())
            meta.text = "via ${m.meta}"
        }
    }
}