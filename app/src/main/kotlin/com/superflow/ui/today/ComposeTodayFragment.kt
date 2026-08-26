package com.superflow.ui.today

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.superflow.R
import com.superflow.ui.common.sfContent
import com.superflow.ui.common.snack
import com.superflow.ui.designer.HabitDesignerActivity
import com.superflow.ui.detail.HabitDetailActivity
import com.superflow.ui.sheets.TextInputSheet
import com.superflow.ui.screens.TodayAction
import com.superflow.ui.screens.TodayScreen
import kotlinx.coroutines.launch

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
            TodayScreen(state = state, onAction = ::handle)
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
