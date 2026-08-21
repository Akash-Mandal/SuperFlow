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
import com.superflow.ai.Speech
import com.superflow.ai.VoiceInput
import com.superflow.data.Prefs
import com.superflow.data.Repository
import com.superflow.domain.Capabilities
import com.superflow.ui.activity.ActivityLogActivity
import com.superflow.ui.blueprint.BlueprintActivity
import com.superflow.ui.common.snack
import com.superflow.ui.common.visible
import com.superflow.ui.engine.AiEngineActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class CoachRow {
    abstract val stableId: Long

    data class Status(val title: String, val body: String, val action: String, val active: Boolean) :
        CoachRow() { override val stableId = 1L }

    data class Blueprint(val subtitle: String) : CoachRow() { override val stableId = 2L }

    data class Suggestions(val items: List<String>) : CoachRow() { override val stableId = 3L }

    data class Message(val id: String, val text: String, val fromUser: Boolean, val meta: String) :
        CoachRow() { override val stableId = id.hashCode().toLong() }

    data class Coach(val text: String) : CoachRow() { override val stableId = 5L }
}

class CoachViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = Repository.get(app)
    private val agent = Agent.get(app)
    val prefs: Prefs = Prefs.get(app)

    private val _rows = MutableStateFlow<List<CoachRow>>(emptyList())
    val rows: StateFlow<List<CoachRow>> = _rows.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _errors = MutableStateFlow<String?>(null)
    val errors: StateFlow<String?> = _errors.asStateFlow()

    /** Emits the latest assistant reply so the view can optionally speak it. */
    private val _lastReply = MutableStateFlow<String?>(null)
    val lastReply: StateFlow<String?> = _lastReply.asStateFlow()

    init {
        viewModelScope.launch { repo.revision.collect { refresh() } }
        viewModelScope.launch { prefs.changes.collect { refresh() } }
    }

    fun consumeError() { _errors.value = null }

    fun refresh() {
        viewModelScope.launch { _rows.value = withContext(Dispatchers.IO) { build() } }
    }

    private fun build(): List<CoachRow> {
        val rows = ArrayList<CoachRow>()
        val full = prefs.fullControlActive()
        rows.add(CoachRow.Status(
            if (full) "Full Control active" else "Full Control not activated",
            if (full)
                "AI can run every registered capability without asking again. Snapshots, " +
                        "Activity and undo stay on."
            else
                "Activate once to let AI complete multi-step and destructive work without " +
                        "repeated confirmations.",
            if (full) "Manage" else "Activate",
            full
        ))

        val engine = when {
            prefs.localCoordinatorOnly -> "Local Coordinator only"
            prefs.cloudReady() -> "${prefs.providerName} · ${prefs.model}"
            else -> "Local Coordinator (no cloud configured)"
        }
        rows.add(CoachRow.Blueprint("$engine · ${Capabilities.all().size} capabilities"))

        val messages = repo.messages(60)
        if (messages.isEmpty()) {
            rows.add(CoachRow.Suggestions(Coordinator.suggestions(repo)))
            rows.add(CoachRow.Coach(Coordinator.coachCard(repo)))
        } else {
            messages.forEach {
                rows.add(CoachRow.Message(it.id, it.text, it.role == "user", it.meta))
            }
        }
        return rows
    }

    fun send(text: String) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            val outcome = agent.send(text)
            _busy.value = false
            if (outcome.error != null) _errors.value = outcome.error
            else if (outcome.reply.isNotBlank()) _lastReply.value = outcome.reply
            refresh()
        }
    }

    fun clear() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.clearMessages() }
            refresh()
        }
    }
}

/**
 * Coach: Ask SuperFlow by text or voice, Blueprint Studio, Full Control status.
 */
class CoachFragment : Fragment() {

    private val model: CoachViewModel by viewModels()
    private lateinit var adapter: CoachAdapter
    private lateinit var list: RecyclerView
    private var voice: VoiceInput? = null
    private val speech by lazy { Speech(requireContext()) }

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
        // Keep the last message visible above the keyboard on small screens (#64).
        ViewCompat.setOnApplyWindowInsetsListener(list) { v, insets ->
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.updatePadding(bottom = if (ime.bottom > 0) v.context.dpPx(8) else 0)
            if (ime.bottom > 0 && adapter.itemCount > 0) {
                list.post { list.scrollToPosition(adapter.itemCount - 1) }
            }
            insets
        }

        adapter = CoachAdapter(
            onSuggestion = { model.send(it) },
            onStatusAction = { openEngine() },
            onBlueprint = { openBlueprint() }
        )
        list.layoutManager = LinearLayoutManager(requireContext()).apply { stackFromEnd = false }
        list.adapter = adapter

        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_search -> {
                    startActivity(Intent(requireContext(),
                        com.superflow.ui.search.SearchActivity::class.java)); true
                }
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

        mic.visible(VoiceInput.isAvailable(requireContext()) && model.prefs.voiceEnabled)
        mic.setOnClickListener {
            if (voice?.isListening() == true) { voice?.stop(); return@setOnClickListener }
            if (VoiceInput.hasPermission(requireContext())) startVoice()
            else micPermission.launch(VoiceInput.PERMISSION)
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
                        thinking.visible(it)
                        send.isEnabled = !it
                    }
                }
                launch {
                    model.errors.collect {
                        if (it != null) { view.snack(it); model.consumeError() }
                    }
                }
                launch {
                    model.lastReply.collect { reply ->
                        if (!reply.isNullOrBlank() && model.prefs.ttsEnabled) {
                            speech.speak(model.prefs, reply)
                        }
                    }
                }
            }
        }
    }

    private fun startVoice() {
        val input = view?.findViewById<TextInputEditText>(R.id.input) ?: return
        val mic = view?.findViewById<MaterialButton>(R.id.btn_mic) ?: return
        voice = VoiceInput(requireContext())
        voice?.start(object : VoiceInput.Callbacks {
            override fun onReady() {
                mic.setIconResource(R.drawable.ic_pause)
                input.hint = getString(R.string.listening)
            }
            override fun onPartial(text: String) = input.setText(text)
            override fun onResult(text: String) {
                input.setText("")
                model.send(text)
            }
            override fun onError(message: String) { view?.snack(message) }
            override fun onEnd() {
                mic.setIconResource(R.drawable.ic_mic)
                input.hint = getString(R.string.message_hint)
            }
        })
    }

    override fun onPause() {
        super.onPause()
        voice?.stop()
        if (::speech.isLazyInitialized) speech.stop()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (::speech.isLazyInitialized) speech.shutdown()
    }

    override fun onResume() {
        super.onResume()
        model.refresh()
    }

    private fun openBlueprint() =
        startActivity(Intent(requireContext(), BlueprintActivity::class.java))

    private fun openEngine() =
        startActivity(Intent(requireContext(), AiEngineActivity::class.java))
}

private fun android.content.Context.dpPx(v: Int): Int =
    (v * resources.displayMetrics.density).toInt()

class CoachAdapter(
    private val onSuggestion: (String) -> Unit,
    private val onStatusAction: () -> Unit,
    private val onBlueprint: () -> Unit
) : ListAdapter<CoachRow, RecyclerView.ViewHolder>(DIFF) {

    companion object {
        private const val T_STATUS = 0
        private const val T_BLUEPRINT = 1
        private const val T_SUGGEST = 2
        private const val T_MESSAGE = 3
        private const val T_COACH = 4

        private val DIFF = object : DiffUtil.ItemCallback<CoachRow>() {
            override fun areItemsTheSame(a: CoachRow, b: CoachRow) = a.stableId == b.stableId
            override fun areContentsTheSame(a: CoachRow, b: CoachRow) = a == b
        }
    }

    init { setHasStableIds(true) }

    override fun getItemId(position: Int) = getItem(position).stableId

    override fun getItemViewType(position: Int) = when (getItem(position)) {
        is CoachRow.Status -> T_STATUS
        is CoachRow.Blueprint -> T_BLUEPRINT
        is CoachRow.Suggestions -> T_SUGGEST
        is CoachRow.Message -> T_MESSAGE
        is CoachRow.Coach -> T_COACH
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return when (viewType) {
            T_STATUS -> StatusVH(inf.inflate(R.layout.item_ai_status, parent, false))
            T_BLUEPRINT -> BlueprintVH(inf.inflate(R.layout.item_text_card, parent, false))
            T_SUGGEST -> SuggestVH(inf.inflate(R.layout.item_suggestions, parent, false))
            T_MESSAGE -> MessageVH(inf.inflate(R.layout.item_message, parent, false))
            else -> CoachVH(inf.inflate(R.layout.item_text_card, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is CoachRow.Status -> (holder as StatusVH).bind(row)
            is CoachRow.Blueprint -> (holder as BlueprintVH).bind(row)
            is CoachRow.Suggestions -> (holder as SuggestVH).bind(row)
            is CoachRow.Message -> (holder as MessageVH).bind(row)
            is CoachRow.Coach -> (holder as CoachVH).bind(row)
        }
    }

    inner class StatusVH(v: View) : RecyclerView.ViewHolder(v) {
        fun bind(row: CoachRow.Status) {
            itemView.findViewById<TextView>(R.id.status_title).text = row.title
            itemView.findViewById<TextView>(R.id.status_body).text = row.body
            itemView.findViewById<MaterialButton>(R.id.status_action).apply {
                text = row.action
                setOnClickListener { onStatusAction() }
            }
        }
    }

    inner class BlueprintVH(v: View) : RecyclerView.ViewHolder(v) {
        fun bind(row: CoachRow.Blueprint) {
            itemView.findViewById<TextView>(R.id.text_title).text = "Blueprint Studio"
            itemView.findViewById<TextView>(R.id.text_body).text =
                "Compile documents and instructions into a whole workspace.\n\n${row.subtitle}"
            itemView.findViewById<MaterialCardView>(R.id.text_card).setOnClickListener {
                onBlueprint()
            }
        }
    }

    inner class SuggestVH(v: View) : RecyclerView.ViewHolder(v) {
        private val chips: ChipGroup = v.findViewById(R.id.suggestion_chips)
        fun bind(row: CoachRow.Suggestions) {
            chips.removeAllViews()
            for (s in row.items) {
                chips.addView(Chip(chips.context).apply {
                    text = s
                    isCheckable = false
                    setEnsureMinTouchTargetSize(false)
                    setOnClickListener { onSuggestion(s) }
                })
            }
        }
    }

    inner class MessageVH(v: View) : RecyclerView.ViewHolder(v) {
        private val row: LinearLayout = v.findViewById(R.id.msg_row)
        private val card: MaterialCardView = v.findViewById(R.id.msg_card)
        private val text: TextView = v.findViewById(R.id.msg_text)
        private val meta: TextView = v.findViewById(R.id.msg_meta)

        fun bind(m: CoachRow.Message) {
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

    inner class CoachVH(v: View) : RecyclerView.ViewHolder(v) {
        fun bind(row: CoachRow.Coach) {
            itemView.findViewById<TextView>(R.id.text_title).text = "Offline coach"
            itemView.findViewById<TextView>(R.id.text_body).text = row.text
        }
    }
}
