package com.superflow.ui.journey

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
import com.superflow.data.model.LifeArea
import com.superflow.design.JourneyTree
import com.superflow.ui.common.sfContent
import com.superflow.ui.common.snack
import com.superflow.ui.designer.HabitDesignerActivity
import com.superflow.ui.detail.HabitDetailActivity
import com.superflow.ui.flows.FlowActivity
import com.superflow.ui.review.ReviewActivity
import com.superflow.ui.scorecard.ScorecardActivity
import com.superflow.ui.screens.JourneyAction
import com.superflow.ui.screens.JourneyScreen
import com.superflow.ui.screens.JourneyUiState
import com.superflow.ui.sheets.EntityEditorSheet
import kotlinx.coroutines.launch

/**
 * Compose host for Journey (plan 11.2).
 *
 * Shares [JourneyViewModel] with the View fragment, so both draw the same
 * hierarchy from the same [JourneyTree] nodes and neither can develop its
 * own idea of what a link means. `design.Rendering` decides which is live.
 *
 * The editors stay as bottom sheets rather than becoming Compose dialogs.
 * They are `DialogFragment`s reused by four other screens, and forking them
 * to serve one Compose host would leave two editors to keep in step for no
 * gain a user could see.
 */
class ComposeJourneyFragment : Fragment() {

    private val model: JourneyViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflated rather than constructed: see fragment_compose_tab.xml for
        // why a code-built ComposeView breaks inside ViewPager2.
        val host = inflater.inflate(R.layout.fragment_compose_tab, container, false) as ComposeView
        return host.sfContent {
            val rows by model.rows.collectAsState()
            JourneyScreen(state = stateFrom(rows), onAction = ::handle)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.events.collect { message ->
                    if (message != null) {
                        view.snack(message, "Undo") { model.undoLast() }
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

    /**
     * The ViewModel emits adapter rows; the Compose screen wants nodes.
     *
     * Rather than have the ViewModel publish two shapes, the nodes are
     * recovered from the rows it already built - they are carried whole on
     * each [JourneyRow.Entity] precisely so this is a projection rather
     * than a second query. Expansion comes back the same way: a row is in
     * the set exactly when the tree said it was expanded.
     *
     * `loading` is inferred from emptiness rather than tracked. The first
     * emission always contains at least the tools row, so an empty list
     * means the first build has not landed yet.
     */
    private fun stateFrom(rows: List<JourneyRow>): JourneyUiState {
        if (rows.isEmpty()) return JourneyUiState(loading = true)
        val entities = rows.filterIsInstance<JourneyRow.Entity>()
        return JourneyUiState(
            loading = false,
            nodes = entities.map { it.row.node },
            expanded = entities.filter { it.row.expanded }.map { it.row.key }.toSet(),
        )
    }

    private fun handle(action: JourneyAction) {
        when (action) {
            is JourneyAction.Toggle -> model.toggle(action.kind, action.id)
            is JourneyAction.Open -> open(action.kind, action.id)
            is JourneyAction.Menu -> open(action.kind, action.id)
            is JourneyAction.Add -> add(action.kind, action.parentId)
            is JourneyAction.Tool -> tool(action.which)
        }
    }

    private fun open(kind: JourneyTree.Kind, id: String) {
        when (kind) {
            JourneyTree.Kind.HABIT -> startActivity(
                Intent(requireContext(), HabitDetailActivity::class.java)
                    .putExtra(HabitDetailActivity.EXTRA_HABIT_ID, id)
            )
            JourneyTree.Kind.IDENTITY ->
                model.identity(id)?.let { editIdentity(it.id, it.statement, it.lifeArea) }
            JourneyTree.Kind.GOAL ->
                model.goal(id)?.let { editGoal(it.id, it.title, it.why, it.identityId) }
            JourneyTree.Kind.SYSTEM ->
                model.system(id)?.let { editSystem(it.id, it.title, it.description, it.goalId) }
        }
    }

    /**
     * Adding from inside the tree pre-links to the node it was added under.
     *
     * This is the whole reason the Compose screen passes a `parentId`. Add
     * a system from the header and it starts unlinked; add it from under a
     * goal and it belongs to that goal, which is the difference between a
     * hierarchy the user has to assemble and one that assembles itself.
     */
    private fun add(kind: JourneyTree.Kind, parentId: String?) {
        when (kind) {
            JourneyTree.Kind.IDENTITY -> editIdentity(null, "", LifeArea.HEALTH)
            JourneyTree.Kind.GOAL ->
                editGoal(null, "", "", parentId ?: model.identities().firstOrNull()?.id)
            JourneyTree.Kind.SYSTEM ->
                editSystem(null, "", "", parentId ?: model.goals().firstOrNull()?.id)
            JourneyTree.Kind.HABIT -> {
                val intent = Intent(requireContext(), HabitDesignerActivity::class.java)
                if (parentId != null) {
                    intent.putExtra(HabitDesignerActivity.EXTRA_SYSTEM_ID, parentId)
                    model.system(parentId)?.goalId?.let { gid ->
                        model.goal(gid)?.identityId?.let { iid ->
                            intent.putExtra(HabitDesignerActivity.EXTRA_IDENTITY_ID, iid)
                        }
                    }
                }
                startActivity(intent)
            }
        }
    }

    private fun tool(which: Int) {
        val target = when (which) {
            0 -> ScorecardActivity::class.java
            1 -> FlowActivity::class.java
            else -> ReviewActivity::class.java
        }
        startActivity(Intent(requireContext(), target))
    }

    private fun editIdentity(id: String?, statement: String, area: LifeArea) {
        EntityEditorSheet.identity(parentFragmentManager, statement, area) { text, picked ->
            model.saveIdentity(id, text, picked)
        }
    }

    private fun editGoal(id: String?, title: String, why: String, identityId: String?) {
        EntityEditorSheet.goal(
            parentFragmentManager, title, why,
            model.identities().map { it.id to it.statement }, identityId
        ) { t, w, linked -> model.saveGoal(id, t, w, linked) }
    }

    private fun editSystem(id: String?, title: String, description: String, goalId: String?) {
        EntityEditorSheet.system(
            parentFragmentManager, title, description,
            model.goals().map { it.id to it.title }, goalId
        ) { t, d, linked -> model.saveSystem(id, t, d, linked) }
    }
}
