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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.superflow.R
import com.superflow.data.model.Checkpoint
import com.superflow.data.model.FocusItem
import com.superflow.data.model.Habit
import com.superflow.data.model.Level
import com.superflow.core.time.Greeting
import com.superflow.core.time.SfTime
import com.superflow.ui.MainActivity
import com.superflow.ui.common.snack
import com.superflow.ui.designer.HabitDesignerActivity
import com.superflow.ui.detail.HabitDetailActivity
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

        adapter = TodayAdapter(this)
        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = adapter
        (list.itemAnimator as? DefaultItemAnimator)?.supportsChangeAnimations = false

        ViewCompat.setOnApplyWindowInsetsListener(view.findViewById(R.id.header)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = bars.top + v.context.resources.getDimensionPixelSize(R.dimen.space_m))
            insets
        }

        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_search -> {
                    startActivity(Intent(requireContext(),
                        com.superflow.ui.search.SearchActivity::class.java))
                    true
                }
                R.id.action_plan_tomorrow -> { model.planTomorrow(); true }
                R.id.action_minimum_mode -> { model.minimumMode(); true }
                R.id.action_recovery -> {
                    startActivity(Intent(requireContext(),
                        com.superflow.ui.recovery.RecoveryActivity::class.java))
                    true
                }
                else -> false
            }
        }

        fab.setOnClickListener {
            (activity as? MainActivity)?.goToTab(3)
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
                        adapter.submitList(state.rows)
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
    override fun onCheckpoint(cp: Checkpoint) = model.runCheckpoint(cp)

    override fun onEmptyAction() {
        startActivity(Intent(requireContext(), HabitDesignerActivity::class.java))
    }
}
