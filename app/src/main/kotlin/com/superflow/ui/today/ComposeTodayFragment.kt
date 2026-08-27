package com.superflow.ui.today

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.superflow.R
import com.superflow.data.Repository
import com.superflow.data.model.CapturedItem
import com.superflow.data.model.JournalEntry
import com.superflow.data.model.FocusItem
import com.superflow.core.time.SfTime
import com.superflow.domain.Search
import com.superflow.domain.SearchResult
import com.superflow.ui.MainActivity
import com.superflow.ui.common.sfContent
import com.superflow.ui.common.snack
import com.superflow.ui.components.SfCommandPalette
import com.superflow.ui.designer.HabitDesignerActivity
import com.superflow.ui.detail.HabitDetailActivity
import com.superflow.ui.inbox.InboxSheet
import com.superflow.ui.sheets.TextInputSheet
import com.superflow.ui.screens.TodayAction
import com.superflow.ui.screens.TodayScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Compose host for Today (plan 11.1).
 *
 * Deliberately thin. [TodayViewModel] is the same one the View fragment
 * uses and [TodayScreen] renders the same [TodayUiState], so this class
 * exists only to turn [TodayAction]s back into the ViewModel calls the
 * older fragment made through its `Callbacks` interface. Which of the two
 * fragments is live is decided by `design.Rendering`.
 *
 * Sharing the ViewModel is the point. It means the Compose screen is not a
 * second implementation of Today's behaviour - only of its appearance - so
 * the two cannot disagree about what a check-in does, and switching between
 * them cannot change the app's semantics.
 *
 * The header the View version draws in XML (greeting, date, toolbar) is
 * drawn inside [TodayScreen] instead, which is why there is no layout file
 * here and no toolbar wiring.
 */
class ComposeTodayFragment : Fragment() {

    private val model: TodayViewModel by viewModels()
    private val repo by lazy { Repository.get(requireContext()) }

    // Shell-level UI state, owned here rather than in the shared ViewModel:
    // the palette and inbox are Compose-host features of this fragment, not
    // part of the Today row model both renderers read.
    private var paletteOpen = mutableStateOf(false)
    private var paletteQuery = mutableStateOf("")
    private var paletteResults = mutableStateOf<List<SearchResult>>(emptyList())
    private var inboxOpen = mutableStateOf(false)
    private var inboxItems = mutableStateOf<List<CapturedItem>>(emptyList())

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

            Column {
                TodayTopBar(
                    greeting = state.greeting,
                    openCaptures = inboxItems.value.count { it.state == com.superflow.data.model.CaptureState.OPEN },
                    onSearch = { paletteOpen.value = true },
                    onInbox = { refreshInbox(); inboxOpen.value = true },
                )
                TodayScreen(state = state, onAction = ::handle)
            }

            if (paletteOpen.value) {
                SfCommandPalette(
                    onDismiss = { paletteOpen.value = false },
                    query = paletteQuery.value,
                    onQueryChange = { newQuery ->
                        paletteQuery.value = newQuery
                        runSearch(newQuery)
                    },
                    results = paletteResults.value,
                    resultKey = { it.type + it.id },
                    resultContent = { result -> PaletteResultRow(result) { openResult(it) } },
                    quickActions = {
                        PaletteAction("Capture a thought") {
                            paletteOpen.value = false
                            showCaptureSheet()
                        }
                        PaletteAction("Design a habit") {
                            paletteOpen.value = false
                            startActivity(
                                Intent(requireContext(), HabitDesignerActivity::class.java)
                            )
                        }
                        PaletteAction("Review captures") {
                            paletteOpen.value = false
                            refreshInbox()
                            inboxOpen.value = true
                        }
                        PaletteAction("Replay today") {
                            paletteOpen.value = false
                            startActivity(
                                Intent(requireContext(), com.superflow.ui.replay.DayReplayActivity::class.java)
                                    .putExtra(
                                        com.superflow.ui.replay.DayReplayActivity.EXTRA_DATE,
                                        SfTime.format(repo.clock.today()),
                                    )
                            )
                        }
                        PaletteAction("AI Memory") {
                            paletteOpen.value = false
                            startActivity(
                                Intent(requireContext(), com.superflow.ui.memory.MemoryViewerActivity::class.java)
                            )
                        }
                        PaletteAction("Sprints") {
                            paletteOpen.value = false
                            startActivity(
                                Intent(requireContext(), com.superflow.ui.sprint.SprintBoardActivity::class.java)
                            )
                        }
                    },
                )
            }

            if (inboxOpen.value) {
                InboxSheet(
                    items = inboxItems.value,
                    onDismiss = { inboxOpen.value = false; model.refresh() },
                    onToJournal = ::convertToJournal,
                    onPinToToday = ::pinToToday,
                    onDesignHabit = ::designHabitFrom,
                    onDiscard = ::discardCapture,
                )
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.events.collect { message ->
                    if (message != null) {
                        // Undo stays a snackbar rather than becoming part of
                        // the composition: it belongs to the window, it must
                        // outlive a recomposition, and the View screens use
                        // the same one.
                        val undoId = model.lastUndoId()
                        if (undoId != null) view.snack(message, "Undo") { model.undoLast() }
                        else view.snack(message)
                        model.consumeEvent()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        model.refresh()
        refreshInbox()
    }

    /* ------------------------------------------------------- command palette */

    private fun runSearch(queryText: String) {
        if (queryText.isBlank()) {
            paletteResults.value = emptyList()
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val results = withContext(Dispatchers.IO) { Search.search(repo, queryText) }
            // Guard against a stale response overwriting a newer query.
            if (paletteQuery.value == queryText) paletteResults.value = results
        }
    }

    private fun openResult(result: SearchResult) {
        paletteOpen.value = false
        paletteQuery.value = ""
        paletteResults.value = emptyList()
        when (result.type) {
            "habit" -> startActivity(
                Intent(requireContext(), HabitDetailActivity::class.java)
                    .putExtra(HabitDetailActivity.EXTRA_HABIT_ID, result.id)
            )
            // Everything else lives on one of the tabs; route there and let
            // the tab's own screens open the entity. Cheap, honest, and no
            // dead ends.
            else -> {
                val tab = when (result.type) {
                    "identity", "goal", "system", "obstacle" -> "journey"
                    "review", "journal" -> "insights"
                    "audit" -> "insights"
                    else -> null
                }
                if (tab != null) {
                    startActivity(
                        Intent(requireContext(), MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            .putExtra(MainActivity.EXTRA_TAB, tab)
                    )
                }
            }
        }
    }

    /* --------------------------------------------------------- capture inbox */

    private fun showCaptureSheet() {
        TextInputSheet.show(
            parentFragmentManager,
            title = "Capture a thought",
            hint = "Anything - it can stay messy",
        ) { text ->
            if (text.isNotBlank()) saveCapture(
                CapturedItem(text = text.trim(), source = com.superflow.data.model.CaptureSource.PALETTE)
            )
        }
    }

    private fun saveCapture(item: CapturedItem) {
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) { repo.saveCapturedItem(item) }
            refreshInbox()
            view?.snack("Captured. It will wait in your inbox.")
        }
    }

    private fun refreshInbox() {
        viewLifecycleOwner.lifecycleScope.launch {
            inboxItems.value = withContext(Dispatchers.IO) { repo.capturedItems() }
        }
    }

    private fun updateItem(item: CapturedItem, state: com.superflow.data.model.CaptureState, convertedTo: String? = null) {
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                repo.saveCapturedItem(
                    item.copy(state = state, convertedToId = convertedTo ?: item.convertedToId)
                )
            }
            refreshInbox()
        }
    }

    private fun convertToJournal(item: CapturedItem) {
        viewLifecycleOwner.lifecycleScope.launch {
            val entry = JournalEntry(date = SfTime.format(repo.clock.today()), content = item.text)
            withContext(Dispatchers.IO) {
                repo.saveJournalEntry(entry)
                repo.saveCapturedItem(
                    item.copy(
                        state = com.superflow.data.model.CaptureState.CONVERTED,
                        convertedToId = entry.id,
                    )
                )
            }
            refreshInbox()
            view?.snack("Saved to your journal.")
        }
    }

    private fun pinToToday(item: CapturedItem) {
        viewLifecycleOwner.lifecycleScope.launch {
            val focus = FocusItem(
                date = SfTime.format(repo.clock.today()),
                habitId = null,
                title = item.text.take(80),
            )
            withContext(Dispatchers.IO) {
                repo.saveFocus(focus)
                repo.saveCapturedItem(
                    item.copy(
                        state = com.superflow.data.model.CaptureState.CONVERTED,
                        convertedToId = focus.id,
                    )
                )
            }
            refreshInbox()
            model.refresh()
            view?.snack("Pinned to today's focus.")
        }
    }

    private fun designHabitFrom(item: CapturedItem) {
        updateItem(item, com.superflow.data.model.CaptureState.ARCHIVED)
        startActivity(Intent(requireContext(), HabitDesignerActivity::class.java))
    }

    private fun discardCapture(item: CapturedItem) {
        updateItem(item, com.superflow.data.model.CaptureState.DISCARDED)
    }

    private fun handle(action: TodayAction) {
        when (action) {
            is TodayAction.CheckIn -> model.checkIn(action.habitId, action.level)
            is TodayAction.Skip -> model.skip(action.habitId)
            is TodayAction.Undo -> model.clearCheckIn(action.habitId)
            is TodayAction.OpenHabit -> startActivity(
                Intent(requireContext(), HabitDetailActivity::class.java)
                    .putExtra(HabitDetailActivity.EXTRA_HABIT_ID, action.habitId)
            )
            is TodayAction.ToggleFocus -> model.toggleFocus(action.focusId, action.done)
            is TodayAction.RemoveFocus -> model.removeFocus(action.focusId)
            TodayAction.FocusAdd -> TextInputSheet.show(
                parentFragmentManager,
                title = "Add a focus action",
                hint = "What deserves emphasis today?",
            ) { text -> if (text.isNotBlank()) model.addFocus(text.trim()) }
            TodayAction.FocusSuggest -> model.suggestFocus()
            is TodayAction.SuggestionAction -> when (action.row.tone) {
                com.superflow.ai.Suggestions.Tone.DESIGN,
                com.superflow.ai.Suggestions.Tone.INSIGHT,
                com.superflow.ai.Suggestions.Tone.ENCOURAGE ->
                    action.row.habitId?.let { id ->
                        startActivity(
                            Intent(requireContext(), HabitDetailActivity::class.java)
                                .putExtra(HabitDetailActivity.EXTRA_HABIT_ID, id)
                        )
                    }
                else -> model.actOnSuggestion(action.row)
            }
            is TodayAction.LogEnergy -> model.logEnergy(action.value)
            TodayAction.AddHabit ->
                startActivity(Intent(requireContext(), HabitDesignerActivity::class.java))
            TodayAction.Refresh -> model.refresh()
        }
    }
}
