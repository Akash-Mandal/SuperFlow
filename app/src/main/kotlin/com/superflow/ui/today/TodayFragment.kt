package com.superflow.ui.today

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.superflow.R
import com.superflow.data.model.Checkpoint
import com.superflow.data.model.FocusItem
import com.superflow.data.model.Habit
import com.superflow.data.model.Level
import com.superflow.core.time.Greeting
import com.superflow.core.time.SfTime
import com.superflow.design.Navigation
import com.superflow.ui.MainActivity
import com.superflow.data.Prefs
import com.superflow.ui.common.snack
import com.superflow.ui.common.wireRefresh
import com.superflow.ui.designer.HabitDesignerActivity
import com.superflow.ui.detail.HabitDetailActivity
import com.superflow.ui.search.SearchActivity
import com.superflow.ui.settings.SettingsActivity
import com.superflow.ui.sheets.TextInputSheet
import com.superflow.util.Dates
import kotlinx.coroutines.launch
import android.content.Intent
import android.widget.TextView

/**
 * Today: Daily Focus, the timeline, checkpoints and compassionate recovery.
 */
class TodayFragment : Fragment(), TodayAdapter.Callbacks {

    private val model: TodayViewModel by viewModels()
    private lateinit var adapter: TodayAdapter
    private lateinit var list: RecyclerView

    /** Dismisses the refresh spinner once the new rows are on screen. */
    private var pendingRefresh: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_today, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val greeting = view.findViewById<TextView>(R.id.greeting)
        val dateTitle = view.findViewById<TextView>(R.id.date_title)
        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar)
        val fab = view.findViewById<ExtendedFloatingActionButton>(R.id.fab)
        list = view.findViewById(R.id.list)
        // Pull to refresh. The completion callback fires on the next state
        // emission rather than on a timer, so the spinner is honest about
        // when the data actually changed.
        val refresh = view.findViewById<SwipeRefreshLayout>(R.id.refresh)
        refresh.wireRefresh(Prefs.get(requireContext())) { done ->
            pendingRefresh = done
            model.refresh()
        }

        adapter = TodayAdapter(this)
        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = adapter
        (list.itemAnimator as? DefaultItemAnimator)?.supportsChangeAnimations = false

        // Drag-and-drop reorder of habit rows (#12). Long-press a card to drag;
        // only Habit rows are movable, and the new order is persisted on drop.
        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun isLongPressDragEnabled() = true

            override fun onMove(
                rv: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromHabit = (viewHolder as? TodayAdapter.HabitVH)?.habit ?: return false
                val toHabit = (target as? TodayAdapter.HabitVH)?.habit ?: return false
                model.reorderHabitTo(fromHabit.id, toHabit.id)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

            override fun getMovementFlags(
                rv: RecyclerView, viewHolder: RecyclerView.ViewHolder
            ): Int = if (viewHolder is TodayAdapter.HabitVH) {
                makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)
            } else 0
        })
        touchHelper.attachToRecyclerView(list)

        ViewCompat.setOnApplyWindowInsetsListener(view.findViewById(R.id.header)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = bars.top + v.context.resources.getDimensionPixelSize(R.dimen.space_m))
            insets
        }

        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_search -> {
                    startActivity(Intent(requireContext(), SearchActivity::class.java))
                    true
                }
                R.id.action_plan_tomorrow -> {
                    startActivity(Intent(requireContext(), PlanTomorrowActivity::class.java))
                    true
                }
                // Settings is reached from here rather than the tab bar
                // since 10.1 — see Navigation.Tab, which has no entry for it.
                R.id.action_settings -> {
                    startActivity(Intent(requireContext(), SettingsActivity::class.java))
                    true
                }
                R.id.action_refresh -> { model.refresh(); true }
                R.id.action_minimum_mode -> { model.minimumMode(); true }
                R.id.action_complete_all_tiny -> {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.complete_all_tiny)
                        .setMessage("Mark every habit still open today as Tiny? " +
                                "A small win still counts, and you can undo it.")
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.done) { _, _ -> model.completeAllTiny() }
                        .show()
                    true
                }
                R.id.action_undo_today -> {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.undo_today)
                        .setMessage("Revert every check-in recorded today? This can be undone from the Activity trail.")
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.undo) { _, _ -> model.undoToday() }
                        .show()
                    true
                }
                R.id.action_recovery -> {
                    startActivity(Intent(requireContext(),
                        com.superflow.ui.recovery.RecoveryActivity::class.java))
                    true
                }
                else -> false
            }
        }

        fab.setOnClickListener {
            (activity as? MainActivity)?.select(Navigation.Tab.STUDIO)
        }
        list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (dy > 8) fab.shrink() else if (dy < -8) fab.extend()
            }
        })

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    model.state.collect { state ->
                        greeting.text = when (state.greeting) {
                            Greeting.MORNING -> getString(R.string.good_morning)
                            Greeting.AFTERNOON -> getString(R.string.good_afternoon)
                            Greeting.EVENING -> getString(R.string.good_evening)
                        }
                        dateTitle.text = SfTime.humanDay(state.date)
                        adapter.submitList(state.rows) {
                            // submitList's callback runs after the diff has
                            // been applied, which is the first moment the
                            // user can see the new data.
                            pendingRefresh?.invoke()
                            pendingRefresh = null
                        }
                    }
                }
                launch {
                    model.events.collect { message ->
                        if (message != null) {
                            val undoId = model.lastUndoId()
                            if (undoId != null) view.snack(message, "Undo") { model.undoLast() }
                            else view.snack(message)
                            model.consumeEvent()
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        model.refresh()
    }

    /* -------------------------------------------------------------- callbacks */

    override fun onCheck(habit: Habit, level: Level) = model.checkIn(habit, level)
    override fun onSkip(habit: Habit) = model.skip(habit)
    override fun onMissed(habit: Habit) = model.markMissed(habit)
    override fun onClear(habit: Habit) = model.clearCheckIn(habit)

    override fun onOpenHabit(habit: Habit, card: View) {
        startActivity(
            Intent(requireContext(), HabitDetailActivity::class.java)
                .putExtra(HabitDetailActivity.EXTRA_HABIT_ID, habit.id)
        )
    }

    override fun onFocusToggle(item: FocusItem, done: Boolean) = model.toggleFocus(item, done)
    override fun onFocusRemove(item: FocusItem) = model.removeFocus(item)

    override fun onFocusAdd() {
        TextInputSheet.show(
            parentFragmentManager,
            title = "Add a focus action",
            hint = "What deserves emphasis today?",
        ) { text -> if (text.isNotBlank()) model.addFocus(text.trim()) }
    }

    override fun onFocusSuggest() = model.suggestFocus()
    override fun onEnergy(value: Int) = model.logEnergy(value)
    override fun onCheckpoint(cp: Checkpoint) {
        startActivity(
            Intent(requireContext(), CheckpointActivity::class.java)
                .putExtra(CheckpointActivity.EXTRA_CHECKPOINT, cp.name)
        )
    }

    override fun onSuggestionAction(row: TodayRow.Suggestion) {
        when (row.tone) {
            com.superflow.ai.Suggestions.Tone.DESIGN,
            com.superflow.ai.Suggestions.Tone.INSIGHT,
            com.superflow.ai.Suggestions.Tone.ENCOURAGE -> onSuggestionOpen(row)
            else -> model.actOnSuggestion(row)
        }
    }

    override fun onSuggestionOpen(row: TodayRow.Suggestion) {
        val id = row.habitId ?: return
        startActivity(
            Intent(requireContext(), HabitDetailActivity::class.java)
                .putExtra(HabitDetailActivity.EXTRA_HABIT_ID, id)
        )
    }

    override fun onEmptyAction() {
        startActivity(Intent(requireContext(), HabitDesignerActivity::class.java))
    }
}
