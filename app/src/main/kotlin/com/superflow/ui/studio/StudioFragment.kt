package com.superflow.ui.studio

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.superflow.R
import com.superflow.ai.Agent
import com.superflow.ai.Coordinator
import com.superflow.ai.VoiceInput
import com.superflow.data.Prefs
import com.superflow.data.Repository
import com.superflow.design.SoundDesign
import com.superflow.design.StudioModel
import com.superflow.domain.Capabilities
import com.superflow.domain.CommandBus
import com.superflow.domain.StudioMapper
import com.superflow.ui.activity.ActivityLogActivity
import com.superflow.ui.blueprint.BlueprintActivity
import com.superflow.ui.common.SfSound
import com.superflow.ui.common.snack
import com.superflow.ui.common.sfContent
import com.superflow.ui.engine.AiEngineActivity
import com.superflow.ui.screens.StudioAction
import com.superflow.ui.screens.StudioScreen
import com.superflow.ui.screens.StudioUiState
import com.superflow.util.Dates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Studio — the merged Coach, Blueprint and AI Engine surface (11.4).
 *
 * Three tabs became one because they were one capability wearing three
 * hats. Asking the Coach to build something produced encouragement; the
 * plan had to be re-typed into Blueprint; the settings that decided whether
 * either could act lived in a third place people never found. The merge
 * keeps all three as one transcript with the heavy tools one tap away in
 * the overflow, so the conversation is the entry point and the editors are
 * where you go when the conversation is not enough.
 *
 * The body is Compose. This class is the plumbing: it owns the ViewModel,
 * the toolbar, voice input and the permission dance, and translates
 * [StudioAction]s into calls on the agent. No layout decisions are made
 * here and no transcript assembly happens here — that is
 * [com.superflow.domain.StudioMapper] and [StudioModel], both of which are
 * pure and tested.
 */
class StudioFragment : Fragment() {

    private val model: StudioViewModel by viewModels()
    private var voice: VoiceInput? = null

    private val micPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startVoice()
            else view?.snack(getString(R.string.mic_permission_needed))
        }

    private val pickFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        handleFile(uri)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_studio, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar)
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { v, insets ->
            v.updatePadding(top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top)
            insets
        }

        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_blueprint -> { openBlueprint(); true }
                R.id.action_engine -> { openEngine(); true }
                R.id.action_activity -> {
                    startActivity(Intent(requireContext(), ActivityLogActivity::class.java))
                    true
                }
                R.id.action_clear -> { confirmClear(); true }
                else -> false
            }
        }

        val host = view.findViewById<ComposeView>(R.id.compose)
        ViewCompat.setOnApplyWindowInsetsListener(host) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val bottomNav = requireActivity().findViewById<View>(R.id.bottom_nav)
            val navHeight = if (bottomNav != null && bottomNav.visibility == View.VISIBLE) {
                if (bottomNav.height > 0) bottomNav.height else (56 * v.resources.displayMetrics.density).toInt()
            } else 0
            v.updatePadding(bottom = navHeight + bars.bottom)
            insets
        }
        host.sfContent {
            val state by model.state.collectAsState()
            StudioScreen(state = state, onAction = ::onAction)
        }
    }

    /** Single sink for everything the screen can do. */
    private fun onAction(action: StudioAction) {
        when (action) {
            is StudioAction.Input -> model.input(action.text)
            StudioAction.Send -> send()
            StudioAction.Mic -> requestVoice()
            StudioAction.StopListening -> stopVoice()
            StudioAction.ExpandFold -> model.expandFold()
            StudioAction.OpenStatus -> openEngine()
            is StudioAction.Quick -> quick(action.id)
            is StudioAction.Suggestion -> model.send(action.text)
            is StudioAction.Message -> message(action.turnId, action.action)
            is StudioAction.OpenProject -> openBlueprint(action.id)
            StudioAction.Attach -> pickFile.launch(arrayOf("text/*", "application/pdf", "image/*", "*/*"))
        }
    }

    private fun handleFile(uri: android.net.Uri) {
        try {
            val bytes = requireContext().contentResolver.openInputStream(uri)?.readBytes() ?: return
            if (bytes.size > 2_000_000) { view?.snack("File too large (>2MB). Split it."); return }
            val isPdf = com.superflow.blueprint.PdfText.looksLikePdf(bytes)
            val text = if (isPdf) com.superflow.blueprint.PdfText.extract(bytes) else String(bytes, Charsets.UTF_8)
            if (text.isBlank()) { view?.snack("No readable text. For scanned PDFs, paste instead."); return }
            val name = uri.lastPathSegment?.substringAfterLast("/") ?: "file"
            model.input("Attached $name:\n\n${text.take(6000)}")
            view?.snack("Attached $name — edit and send")
        } catch (_: Exception) { view?.snack("Could not read that file") }
    }

    private fun send() {
        val text = model.state.value.input.trim()
        if (text.isEmpty()) return
        model.input("")
        model.send(text)
    }

    private fun quick(id: String) {
        val action = StudioModel.quickActions.firstOrNull { it.id == id } ?: return
        // Blueprint is the one chip that opens a tool rather than typing:
        // "Open Blueprint Studio" is a navigation, and routing it through
        // the model would make the user watch it be parsed.
        if (action.id == "blueprint") { openBlueprint(); return }
        model.input(action.prompt)
    }

    private fun message(turnId: String, action: StudioModel.MessageAction) {
        val turn = model.turnOf(turnId) ?: return
        when (action) {
            StudioModel.MessageAction.COPY -> {
                val clip = requireContext()
                    .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clip.setPrimaryClip(ClipData.newPlainText("SuperFlow", turn.text))
                // Android 13+ shows its own copy confirmation; a second
                // toast on top of it is noise.
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    view?.snack(getString(R.string.copied))
                }
            }
            StudioModel.MessageAction.UNDO -> model.undo(turn) { view?.snack(it) }
            StudioModel.MessageAction.EXPLAIN -> explain(turn)
            StudioModel.MessageAction.RETRY -> model.send(retryTextFor(turn))
        }
    }

    /**
     * What "try again" re-sends.
     *
     * On the user's own turn it is that text. On a failed assistant turn it
     * is the message that provoked it, because re-sending the failure back
     * to the model asks it to explain its own error rather than to retry.
     */
    private fun retryTextFor(turn: StudioModel.Turn): String =
        if (turn.speaker == StudioModel.Speaker.USER) turn.text
        else model.previousUserText(turn.id).orEmpty().ifBlank { turn.text }

    private fun explain(turn: StudioModel.Turn) {
        val body = buildString {
            appendLine("Studio ran ${turn.actions.size} step(s) for this reply:")
            appendLine()
            turn.actions.forEach { name ->
                val cap = Capabilities.all().firstOrNull { it.name == name }
                appendLine("• ${cap?.summary ?: name}")
            }
            if (turn.route.isNotBlank()) {
                appendLine()
                appendLine("Answered ${StudioMapper.routeLabel(turn.route)}.")
            }
            if (turn.groupId != null) {
                appendLine()
                appendLine("Every step is in Activity and can be undone.")
            }
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.explain_title)
            .setMessage(body.trim())
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun confirmClear() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.clear_conversation)
            .setMessage(R.string.clear_conversation_body)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.clear_conversation) { _, _ ->
                model.clear()
                view?.snack(getString(R.string.conversation_cleared))
            }
            .show()
    }

    /* ------------------------------------------------------------- voice */

    private fun requestVoice() {
        if (!VoiceInput.isAvailable(requireContext())) {
            view?.snack(getString(R.string.voice_unavailable))
            return
        }
        if (VoiceInput.hasPermission(requireContext())) startVoice()
        else micPermission.launch(VoiceInput.PERMISSION)
    }

    private fun startVoice() {
        voice?.stop()
        voice = VoiceInput(requireContext()).also { v ->
            model.startedListening()
            v.start(object : VoiceInput.Callbacks {
                override fun onPartial(text: String) = model.input(text)
                override fun onResult(text: String) {
                    model.input("")
                    model.stoppedListening()
                    if (text.isNotBlank()) model.send(text)
                }
                override fun onVolume(rms: Float) = model.level(rms)
                override fun onError(message: String) {
                    model.stoppedListening()
                    view?.snack(message)
                }
                override fun onEnd() = model.stoppedListening()
            })
        }
    }

    private fun stopVoice() {
        voice?.stop()
        model.stoppedListening()
    }

    override fun onPause() {
        super.onPause()
        // A recogniser left running in the background is a live microphone
        // the user cannot see. It always stops with the screen.
        stopVoice()
    }

    override fun onResume() {
        super.onResume()
        model.refresh()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        voice?.stop()
        voice = null
    }

    private fun openBlueprint(projectId: String? = null) {
        startActivity(Intent(requireContext(), BlueprintActivity::class.java).apply {
            if (projectId != null) putExtra(BlueprintActivity.EXTRA_PROJECT, projectId)
        })
    }

    private fun openEngine() =
        startActivity(Intent(requireContext(), AiEngineActivity::class.java))
}

/**
 * Studio's state holder.
 *
 * It owns exactly two things the pure layer cannot: the in-flight send, and
 * the live microphone amplitudes. Everything else is recomputed from the
 * repository, so an edit made in Blueprint or a command run from a widget
 * shows up here without this class knowing those exist.
 */
class StudioViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = Repository.get(app)
    private val agent = Agent.get(app)
    private val bus = CommandBus.get(app)
    private val prefs = Prefs.get(app)

    private val _state = MutableStateFlow(StudioUiState())
    val state: StateFlow<StudioUiState> = _state.asStateFlow()

    private var turns: List<StudioModel.Turn> = emptyList()
    private var amplitudes: List<Float> = emptyList()

    init {
        viewModelScope.launch { repo.revision.collect { refresh() } }
        viewModelScope.launch { prefs.changes.collect { refresh() } }
        refresh()
    }

    fun input(text: String) = _state.update {
        // Clamp rather than reject: silently dropping keystrokes at the
        // limit reads as the field being broken.
        val clamped = text.take(StudioModel.MAX_INPUT)
        it.copy(input = clamped, canSend = StudioModel.canSend(clamped, it.sending))
    }

    fun expandFold() = _state.update { it.copy(foldExpanded = true) }

    fun turnOf(id: String): StudioModel.Turn? = turns.firstOrNull { it.id == id }

    /** The user message immediately before [id], for retrying a failure. */
    fun previousUserText(id: String): String? {
        val index = turns.indexOfFirst { it.id == id }
        if (index <= 0) return null
        return turns.take(index)
            .lastOrNull { it.speaker == StudioModel.Speaker.USER }
            ?.text
    }

    fun refresh() {
        viewModelScope.launch {
            val rendered = withContext(Dispatchers.IO) { build() }
            _state.update {
                it.copy(
                    loading = false,
                    rows = rendered,
                    typing = StudioModel.typing(turns, it.sending),
                    placeholder = StudioModel.placeholder(
                        prefs.fullControlActive(),
                        prefs.cloudReady(),
                    ),
                    canSend = StudioModel.canSend(it.input, it.sending),
                )
            }
        }
    }

    private fun build(): List<StudioModel.Row> {
        val messages = repo.messages(200)
        turns = StudioMapper.turns(messages, repo.audit(200))
        val visible = if (_state.value.foldExpanded) messages.size else StudioModel.VISIBLE_TURNS
        return StudioModel.rows(
            status = StudioMapper.status(
                fullControl = prefs.fullControlActive(),
                localOnly = prefs.localCoordinatorOnly,
                cloudReady = prefs.cloudReady(),
                providerLabel = "${prefs.providerName} · ${prefs.model}",
                capabilityCount = Capabilities.all().size,
            ),
            turns = turns,
            projects = StudioMapper.projects(repo.projects()) { repo.requirements(it) },
            suggestions = if (turns.isEmpty()) Coordinator.suggestions(repo) else emptyList(),
            coach = if (turns.isEmpty()) Coordinator.coachCard(repo) else "",
            // Through the repository's clock, not the system default, so a
            // date break lands where the user's day did rather than where
            // UTC thinks it did.
            dayLabel = { Dates.humanDay(java.time.Instant.ofEpochMilli(it).atZone(repo.clock.zone()).toLocalDate()) },
            visibleTurns = visible.coerceAtLeast(1),
        )
    }

    fun send(text: String) {
        if (_state.value.sending || text.isBlank()) return
        _state.update { it.copy(sending = true, typing = true, canSend = false) }
        viewModelScope.launch {
            val outcome = agent.send(text)
            _state.update { it.copy(sending = false) }
            refresh()
            if (outcome.error == null && outcome.actions.isNotEmpty()) {
                SfSound.play(getApplication(), SoundDesign.Cue.CHECK_IN)
            }
        }
    }

    fun undo(turn: StudioModel.Turn, notify: (String) -> Unit) {
        val group = turn.groupId ?: return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { bus.undoGroup(group) }
            notify(result.message)
            refresh()
        }
    }

    fun clear() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.clearMessages() }
            _state.update { it.copy(foldExpanded = false) }
            refresh()
        }
    }

    /* ------------------------------------------------------------- voice */

    fun startedListening() {
        amplitudes = emptyList()
        _state.update { it.copy(listening = true, levels = emptyList()) }
    }

    fun stoppedListening() {
        amplitudes = emptyList()
        _state.update { it.copy(listening = false, levels = emptyList()) }
    }

    /**
     * Feeds one RMS sample to the waveform.
     *
     * `SpeechRecognizer` reports roughly -2..10 dB with no documented
     * bounds, so the value is normalised here rather than in the design
     * layer: the range is a property of this particular API, not of
     * waveforms.
     */
    fun level(rms: Float) {
        if (!_state.value.listening) return
        val normalised = ((rms + 2f) / 12f).coerceIn(0f, 1f)
        amplitudes = (amplitudes + normalised).takeLast(WAVEFORM_BARS)
        _state.update {
            it.copy(levels = StudioModel.waveform(amplitudes, WAVEFORM_BARS))
        }
    }

    private companion object {
        const val WAVEFORM_BARS = 28
    }
}
