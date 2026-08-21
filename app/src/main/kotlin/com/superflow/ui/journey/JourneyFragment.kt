package com.superflow.ui.journey

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.superflow.R
import com.superflow.data.model.LifeArea
import com.superflow.ui.common.snack
import com.superflow.ui.designer.HabitDesignerActivity
import com.superflow.ui.detail.HabitDetailActivity
import com.superflow.ui.flows.FlowActivity
import com.superflow.ui.review.ReviewActivity
import com.superflow.ui.scorecard.ScorecardActivity
import com.superflow.ui.search.SearchActivity
import com.superflow.ui.sheets.EntityEditorSheet
import kotlinx.coroutines.launch

/**
 * Journey: identities, goals, systems, habits and the design tools.
 * This is where the growth hierarchy is visible and editable.
 */
class JourneyFragment : Fragment() {

    private val model: JourneyViewModel by viewModels()
    private lateinit var adapter: JourneyAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_list, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<TextView>(R.id.screen_title).text = getString(R.string.tab_journey)
        view.findViewById<TextView>(R.id.screen_subtitle).text =
            "Identity shapes the goal. The goal needs a system. The system runs on habits."

        val list = view.findViewById<RecyclerView>(R.id.list)
        val fab = view.findViewById<ExtendedFloatingActionButton>(R.id.fab)
        fab.visibility = View.VISIBLE
        fab.text = "Design habit"
        fab.setOnClickListener {
            startActivity(Intent(requireContext(), HabitDesignerActivity::class.java))
        }

        view.findViewById<Toolbar>(R.id.toolbar).apply {
            menu.clear()
            inflateMenu(R.menu.journey_menu)
            setOnMenuItemClickListener { item: MenuItem ->
                when (item.itemId) {
                    R.id.action_search -> {
                        startActivity(Intent(requireContext(), SearchActivity::class.java))
                        true
                    }
                    else -> false
                }
            }
        }
        list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (dy > 8) fab.shrink() else if (dy < -8) fab.extend()
            }
        })

        ViewCompat.setOnApplyWindowInsetsListener(view.findViewById(R.id.header)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = bars.top + v.context.resources.getDimensionPixelSize(R.dimen.space_m))
            insets
        }

        adapter = JourneyAdapter(
            onOpen = ::openEntity,
            onMenu = ::showMenu,
            onAdd = ::addFor,
            onTool = ::openTool
        )
        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = adapter

        // Long-press and drag to reorder habits. Only habit rows are draggable;
        // headers, tools, identities, goals and systems stay put.
        val touchHelper = androidx.recyclerview.widget.ItemTouchHelper(
            object : androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(
                androidx.recyclerview.widget.ItemTouchHelper.UP or
                        androidx.recyclerview.widget.ItemTouchHelper.DOWN, 0
            ) {
                override fun onMove(
                    rv: RecyclerView,
                    from: RecyclerView.ViewHolder,
                    to: RecyclerView.ViewHolder
                ): Boolean {
                    val fromPos = from.bindingAdapterPosition
                    val toPos = to.bindingAdapterPosition
                    if (fromPos == RecyclerView.NO_POSITION || toPos == RecyclerView.NO_POSITION) return false
                    if (!adapter.isDraggable(toPos)) return false
                    adapter.moveItem(fromPos, toPos)
                    return true
                }

                override fun onSwiped(vh: RecyclerView.ViewHolder, dir: Int) {}

                override fun isLongPressDragEnabled() = true

                override fun getMovementFlags(
                    rv: RecyclerView, vh: RecyclerView.ViewHolder
                ): Int {
                    val pos = vh.bindingAdapterPosition
                    return if (adapter.isDraggable(pos)) makeMovementFlags(UP or DOWN, 0) else 0
                }

                override fun clearView(rv: RecyclerView, vh: RecyclerView.ViewHolder) {
                    super.clearView(rv, vh)
                    val order = adapter.orderedHabitIds()
                    if (order.size > 1) model.reorderHabits(order)
                }
            }
        )
        touchHelper.attachToRecyclerView(list)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { model.rows.collect { adapter.submitList(it) } }
                launch {
                    model.events.collect {
                        if (it != null) {
                            view.snack(it, "Undo") { model.undoLast() }
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

    /* ----------------------------------------------------------------- flows */

    private fun openTool(which: Int) {
        val target = when (which) {
            0 -> ScorecardActivity::class.java
            1 -> FlowActivity::class.java
            else -> ReviewActivity::class.java
        }
        startActivity(Intent(requireContext(), target))
    }

    private fun openEntity(row: JourneyRow.Entity) {
        when (row.kind) {
            "habit" -> startActivity(
                Intent(requireContext(), HabitDetailActivity::class.java)
                    .putExtra(HabitDetailActivity.EXTRA_HABIT_ID, row.id)
            )
            "identity" -> model.identity(row.id)?.let { editIdentity(it.id, it.statement, it.lifeArea) }
            "goal" -> model.goal(row.id)?.let { editGoal(it.id, it.title, it.why, it.identityId) }
            "system" -> model.system(row.id)?.let { editSystem(it.id, it.title, it.description, it.goalId) }
        }
    }

    private fun addFor(kind: String) {
        when (kind) {
            "identity" -> editIdentity(null, "", LifeArea.HEALTH)
            "goal" -> editGoal(null, "", "", model.identities().firstOrNull()?.id)
            "system" -> editSystem(null, "", "", model.goals().firstOrNull()?.id)
            "habit" -> startActivity(Intent(requireContext(), HabitDesignerActivity::class.java))
        }
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

    private fun showMenu(row: JourneyRow.Entity, anchor: View) {
        val menu = PopupMenu(requireContext(), anchor)
        if (row.archived) {
            menu.menu.add("Restore").setOnMenuItemClickListener {
                model.restoreHabit(row.id); true
            }
        } else {
            menu.menu.add(if (row.kind == "habit") "Open" else "Edit")
                .setOnMenuItemClickListener { openEntity(row); true }
            if (row.kind == "habit") {
                menu.menu.add("Edit design").setOnMenuItemClickListener {
                    startActivity(
                        Intent(requireContext(), HabitDesignerActivity::class.java)
                            .putExtra(HabitDesignerActivity.EXTRA_HABIT_ID, row.id)
                    )
                    true
                }
                menu.menu.add("Duplicate").setOnMenuItemClickListener {
                    model.duplicateHabit(row.id); true
                }
                val order = menu.menu.addSubMenu("Reorder")
                order.add("Move up").setOnMenuItemClickListener {
                    model.moveHabit(row.id, "up"); true
                }
                order.add("Move down").setOnMenuItemClickListener {
                    model.moveHabit(row.id, "down"); true
                }
                if (!row.archived) {
                    menu.menu.add("Archive").setOnMenuItemClickListener {
                        model.archiveHabit(row.id); true
                    }
                }
            }
        }
        menu.menu.add("Delete").setOnMenuItemClickListener {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete \"${row.title.take(40)}\"?")
                .setMessage("You can undo this from the Activity trail.")
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete) { _, _ -> model.delete(row.kind, row.id) }
                .show()
            true
        }
        menu.show()
    }
}

/* ------------------------------------------------------------------ adapter */

class JourneyAdapter(
    private val onOpen: (JourneyRow.Entity) -> Unit,
    private val onMenu: (JourneyRow.Entity, View) -> Unit,
    private val onAdd: (String) -> Unit,
    private val onTool: (Int) -> Unit
) : ListAdapter<JourneyRow, RecyclerView.ViewHolder>(DIFF) {

    /** Mutable mirror of the submitted list, used for drag reordering. */
    private var backing: MutableList<JourneyRow> = mutableListOf()

    override fun submitList(list: List<JourneyRow>?) {
        backing = (list ?: emptyList()).toMutableList()
        super.submitList(list)
    }

    fun itemAt(position: Int): JourneyRow = backing.getOrNull(position) ?: JourneyRow.Tools

    /** Moves a row and re-diffs, so the internal list never goes stale. */
    fun moveItem(from: Int, to: Int) {
        if (from !in backing.indices || to !in backing.indices || from == to) return
        val item = backing.removeAt(from)
        backing.add(to, item)
        submitList(backing.toList())
    }

    /** Returns the current habit order after a drag. */
    fun orderedHabitIds(): List<String> =
        backing.filter { it is JourneyRow.Entity && it.kind == "habit" && !it.archived && !it.graduated }
            .map { (it as JourneyRow.Entity).id }

    fun isDraggable(position: Int): Boolean {
        val row = backing.getOrNull(position) ?: return false
        return row is JourneyRow.Entity && row.kind == "habit" && !row.archived && !row.graduated
    }

    companion object {
        private const val T_TOOLS = 0
        private const val T_HEADER = 1
        private const val T_ENTITY = 2
        private const val T_EMPTY = 3

        private val DIFF = object : DiffUtil.ItemCallback<JourneyRow>() {
            override fun areItemsTheSame(a: JourneyRow, b: JourneyRow) = a.stableId == b.stableId
            override fun areContentsTheSame(a: JourneyRow, b: JourneyRow) = a == b
        }
    }

    init { setHasStableIds(true) }

    override fun getItemId(position: Int) = getItem(position).stableId

    override fun getItemViewType(position: Int) = when (getItem(position)) {
        is JourneyRow.Tools -> T_TOOLS
        is JourneyRow.Header -> T_HEADER
        is JourneyRow.Entity -> T_ENTITY
        is JourneyRow.Empty -> T_EMPTY
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return when (viewType) {
            T_TOOLS -> ToolsVH(inf.inflate(R.layout.item_tools, parent, false))
            T_HEADER -> HeaderVH(inf.inflate(R.layout.item_section_action, parent, false))
            T_ENTITY -> EntityVH(inf.inflate(R.layout.item_entity, parent, false))
            else -> EmptyVH(inf.inflate(R.layout.item_empty, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is JourneyRow.Tools -> (holder as ToolsVH).bind()
            is JourneyRow.Header -> (holder as HeaderVH).bind(row)
            is JourneyRow.Entity -> (holder as EntityVH).bind(row)
            is JourneyRow.Empty -> (holder as EmptyVH).bind(row)
        }
    }

    inner class ToolsVH(v: View) : RecyclerView.ViewHolder(v) {
        fun bind() {
            val specs = listOf(
                Triple(R.id.tool_1, R.drawable.ic_scorecard, "Scorecard"),
                Triple(R.id.tool_2, R.drawable.ic_flow, "Flows"),
                Triple(R.id.tool_3, R.drawable.ic_history, "Reviews")
            )
            val icons = listOf(R.id.tool_1_icon, R.id.tool_2_icon, R.id.tool_3_icon)
            val labels = listOf(R.id.tool_1_label, R.id.tool_2_label, R.id.tool_3_label)
            specs.forEachIndexed { index, (cardId, icon, label) ->
                itemView.findViewById<ImageView>(icons[index]).setImageResource(icon)
                itemView.findViewById<TextView>(labels[index]).text = label
                itemView.findViewById<MaterialCardView>(cardId).setOnClickListener { onTool(index) }
            }
        }
    }

    inner class HeaderVH(v: View) : RecyclerView.ViewHolder(v) {
        private val title: TextView = v.findViewById(R.id.section_title)
        private val action: MaterialButton = v.findViewById(R.id.section_action)
        fun bind(row: JourneyRow.Header) {
            title.text = row.title
            if (row.addLabel == null) {
                action.visibility = View.GONE
            } else {
                action.visibility = View.VISIBLE
                action.text = row.addLabel
                action.setOnClickListener { onAdd(row.kind) }
            }
        }
    }

    inner class EntityVH(v: View) : RecyclerView.ViewHolder(v) {
        private val card: MaterialCardView = v.findViewById(R.id.entity_card)
        private val icon: ImageView = v.findViewById(R.id.entity_icon)
        private val title: TextView = v.findViewById(R.id.entity_title)
        private val sub: TextView = v.findViewById(R.id.entity_sub)
        private val menu: MaterialButton = v.findViewById(R.id.entity_menu)

        fun bind(row: JourneyRow.Entity) {
            icon.setImageResource(row.icon)
            title.text = row.title
            sub.text = row.subtitle
            card.alpha = if (row.archived) 0.6f else 1f
            card.setOnClickListener { onOpen(row) }
            menu.setOnClickListener { onMenu(row, it) }
        }
    }

    inner class EmptyVH(v: View) : RecyclerView.ViewHolder(v) {
        private val title: TextView = v.findViewById(R.id.empty_title)
        private val body: TextView = v.findViewById(R.id.empty_body)
        private val action: MaterialButton = v.findViewById(R.id.empty_action)
        fun bind(row: JourneyRow.Empty) {
            title.text = row.title
            body.text = row.body
            action.visibility = View.VISIBLE
            action.text = "Add"
            action.setOnClickListener { onAdd(row.kind) }
        }
    }
}
