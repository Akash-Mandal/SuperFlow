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
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.appbar.MaterialToolbar
import com.superflow.R
import com.superflow.data.model.LifeArea
import com.superflow.design.JourneyTree
import com.superflow.ui.common.dp
import com.superflow.ui.common.themeColor
import com.superflow.ui.common.visible
import com.superflow.data.Prefs
import com.superflow.ui.common.snack
import com.superflow.ui.common.wireRefresh
import com.superflow.util.onDebouncedClick
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

    /** Dismisses the refresh spinner once the new rows are on screen. */
    private var pendingRefresh: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_list, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<TextView>(R.id.screen_title).text = getString(R.string.tab_journey)
        view.findViewById<TextView>(R.id.screen_subtitle).text =
            "One chain, top to bottom. Tap a level to open what hangs off it."

        val list = view.findViewById<RecyclerView>(R.id.list)
        val refresh = view.findViewById<SwipeRefreshLayout>(R.id.refresh)
        // The visible equivalent of the pull gesture, for users who have
        // switched it off or cannot perform it.
        view.findViewById<MaterialToolbar>(R.id.toolbar).apply {
            inflateMenu(R.menu.list_menu)
            setOnMenuItemClickListener { item ->
                if (item.itemId == R.id.action_refresh) { model.refresh(); true } else false
            }
        }
        refresh.wireRefresh(Prefs.get(requireContext())) { done ->
            pendingRefresh = done
            model.refresh()
        }
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
            onToggle = { model.toggle(it.row.node.kind, it.id) },
            onAdd = ::addFor,
            onTool = ::openTool
        )
        list.layoutManager = LinearLayoutManager(requireContext())
        list.setHasFixedSize(true)
        list.setItemViewCacheSize(12)
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
                    return if (adapter.isDraggable(pos)) makeMovementFlags(
                        androidx.recyclerview.widget.ItemTouchHelper.UP or
                            androidx.recyclerview.widget.ItemTouchHelper.DOWN, 0
                    ) else 0
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
                launch {
                    model.rows.collect { rows ->
                        adapter.submitList(rows) {
                            pendingRefresh?.invoke()
                            pendingRefresh = null
                        }
                    }
                }
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

    private fun addFor(kind: String, parentId: String? = null) {
        when (kind) {
            "identity" -> editIdentity(null, "", LifeArea.HEALTH)
            "goal" -> editGoal(null, "", "", parentId ?: model.identities().firstOrNull()?.id)
            "system" -> editSystem(null, "", "", parentId ?: model.goals().firstOrNull()?.id)
            "habit" -> {
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
        when (row.kind) {
            "identity" -> menu.menu.add("Add goal to this identity").setOnMenuItemClickListener { addFor("goal", row.id); true }
            "goal" -> menu.menu.add("Add system to this goal").setOnMenuItemClickListener { addFor("system", row.id); true }
            "system" -> menu.menu.add("Add habit to this system").setOnMenuItemClickListener { addFor("habit", row.id); true }
            else -> Unit
        }
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

/**
 * Draws the Journey hierarchy.
 *
 * Holds no logic about the hierarchy itself: depth, connectors, counts,
 * dormancy and orphan state all arrive on the [JourneyTree.Row] inside each
 * item. What this class decides is only how those facts look.
 */
class JourneyAdapter(
    private val onOpen: (JourneyRow.Entity) -> Unit,
    private val onMenu: (JourneyRow.Entity, View) -> Unit,
    private val onToggle: (JourneyRow.Entity) -> Unit,
    private val onAdd: (String, String?) -> Unit,
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
        private const val T_SUMMARY = 4
        private const val T_GAP = 5

        /** Indent per level. Four levels deep must still leave room to read. */
        private const val INDENT_DP = 16

        /** Dormant rows stay legible; they just stop competing for attention. */
        private const val DORMANT_ALPHA = 0.55f

        private val DIFF = object : DiffUtil.ItemCallback<JourneyRow>() {
            override fun areItemsTheSame(a: JourneyRow, b: JourneyRow) = a.stableId == b.stableId
            override fun areContentsTheSame(a: JourneyRow, b: JourneyRow) = a == b
        }
    }

    init { setHasStableIds(true) }

    override fun getItemId(position: Int) = getItem(position).stableId

    override fun getItemViewType(position: Int) = when (getItem(position)) {
        is JourneyRow.Tools -> T_TOOLS
        is JourneyRow.Summary -> T_SUMMARY
        is JourneyRow.Header -> T_HEADER
        is JourneyRow.Entity -> T_ENTITY
        is JourneyRow.Gap -> T_GAP
        is JourneyRow.Empty -> T_EMPTY
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return when (viewType) {
            T_TOOLS -> ToolsVH(inf.inflate(R.layout.item_tools, parent, false))
            T_SUMMARY -> SummaryVH(inf.inflate(R.layout.item_journey_summary, parent, false))
            T_HEADER -> HeaderVH(inf.inflate(R.layout.item_section_action, parent, false))
            T_ENTITY -> EntityVH(inf.inflate(R.layout.item_tree_entity, parent, false))
            T_GAP -> GapVH(inf.inflate(R.layout.item_empty, parent, false))
            else -> EmptyVH(inf.inflate(R.layout.item_empty, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is JourneyRow.Tools -> (holder as ToolsVH).bind()
            is JourneyRow.Summary -> (holder as SummaryVH).bind(row)
            is JourneyRow.Header -> (holder as HeaderVH).bind(row)
            is JourneyRow.Entity -> (holder as EntityVH).bind(row)
            is JourneyRow.Gap -> (holder as GapVH).bind(row)
            is JourneyRow.Empty -> (holder as EmptyVH).bind(row)
        }
    }

    inner class ToolsVH(v: View) : RecyclerView.ViewHolder(v) {
        private val icons = listOf(
            v.findViewById<ImageView>(R.id.tool_1_icon),
            v.findViewById<ImageView>(R.id.tool_2_icon),
            v.findViewById<ImageView>(R.id.tool_3_icon)
        )
        private val labels = listOf(
            v.findViewById<TextView>(R.id.tool_1_label),
            v.findViewById<TextView>(R.id.tool_2_label),
            v.findViewById<TextView>(R.id.tool_3_label)
        )
        private val cards = listOf(
            v.findViewById<MaterialCardView>(R.id.tool_1),
            v.findViewById<MaterialCardView>(R.id.tool_2),
            v.findViewById<MaterialCardView>(R.id.tool_3)
        )
        fun bind() {
            val specs = listOf(
                Triple(R.drawable.ic_scorecard, "Scorecard", 0),
                Triple(R.drawable.ic_flow, "Flows", 1),
                Triple(R.drawable.ic_history, "Reviews", 2)
            )
            specs.forEachIndexed { index, (icon, label, tool) ->
                icons[index].setImageResource(icon)
                labels[index].text = label
                cards[index].onDebouncedClick { onTool(tool) }
            }
        }
    }

    /**
     * The counts strip.
     *
     * The note underneath is the point of the card: four numbers on their
     * own are a dashboard, and this app does not want a dashboard. The
     * sentence says what the numbers mean for the chain.
     */
    inner class SummaryVH(v: View) : RecyclerView.ViewHolder(v) {
        private val identities: TextView = v.findViewById(R.id.chain_identities_value)
        private val goals: TextView = v.findViewById(R.id.chain_goals_value)
        private val systems: TextView = v.findViewById(R.id.chain_systems_value)
        private val habits: TextView = v.findViewById(R.id.chain_habits_value)
        private val note: TextView = v.findViewById(R.id.chain_note)

        fun bind(row: JourneyRow.Summary) {
            val s = row.summary
            identities.text = s.identities.toString()
            goals.text = s.goals.toString()
            systems.text = s.systems.toString()
            habits.text = s.habits.toString()
            note.text = chainNote(s)
        }

        private fun chainNote(s: JourneyTree.Summary): String = when {
            s.deepestChain >= 4 ->
                "At least one habit traces all the way back to an identity. " +
                    "That is the whole idea working."
            s.unlinked > 0 ->
                "${s.unlinked} " +
                    (if (s.unlinked == 1) "thing is" else "things are") +
                    " not connected to anything above them yet."
            s.deepestChain == 3 -> "One more link and a habit reaches an identity."
            s.deepestChain == 2 -> "Your goals have systems. Habits are what run them."
            else -> "Identity shapes the goal. The goal needs a system. " +
                "The system runs on habits."
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
                action.onDebouncedClick { onAdd(row.kind, null) }
            }
        }
    }

    inner class EntityVH(v: View) : RecyclerView.ViewHolder(v) {
        private val indent: View = v.findViewById(R.id.tree_indent)
        private val rail: View = v.findViewById(R.id.tree_rail)
        private val card: MaterialCardView = v.findViewById(R.id.entity_card)
        private val accent: View = v.findViewById(R.id.entity_accent)
        private val icon: ImageView = v.findViewById(R.id.entity_icon)
        private val title: TextView = v.findViewById(R.id.entity_title)
        private val sub: TextView = v.findViewById(R.id.entity_sub)
        private val count: TextView = v.findViewById(R.id.entity_count)
        private val add: MaterialButton = v.findViewById(R.id.entity_add)
        private val expand: MaterialButton = v.findViewById(R.id.entity_expand)
        private val menu: MaterialButton = v.findViewById(R.id.entity_menu)

        fun bind(row: JourneyRow.Entity) {
            val tree = row.row
            indent.layoutParams = indent.layoutParams.apply {
                width = itemView.context.dp(tree.depth * INDENT_DP)
            }
            // A top-level node has nothing above it, so a rail would be a
            // line pointing at nothing.
            rail.visible(tree.depth > 0)

            icon.setImageResource(row.icon)
            title.text = row.title
            sub.text = row.subtitle
            sub.visible(row.subtitle.isNotBlank())

            val accentColor = when (tree.node.kind) {
                JourneyTree.Kind.IDENTITY -> itemView.context.themeColor(androidx.appcompat.R.attr.colorPrimary)
                JourneyTree.Kind.GOAL -> itemView.context.themeColor(com.google.android.material.R.attr.colorSecondary)
                JourneyTree.Kind.SYSTEM -> itemView.context.themeColor(com.google.android.material.R.attr.colorTertiary)
                else -> itemView.context.themeColor(com.google.android.material.R.attr.colorSurfaceVariant)
            }
            accent.setBackgroundColor(accentColor)

            card.alpha = if (tree.dormant || row.archived) DORMANT_ALPHA else 1f
            // Orphans get the outline treatment: visibly not attached,
            // without the red that would make a normal state look like a
            // failure.
            card.strokeWidth =
                if (tree.orphan) itemView.context.resources
                    .getDimensionPixelSize(R.dimen.stroke_emphasis)
                else itemView.context.resources
                    .getDimensionPixelSize(R.dimen.stroke_hairline)

            if (tree.expandable && !tree.expanded) {
                count.visibility = View.VISIBLE
                count.text = tree.descendantCount.toString()
            } else {
                count.visibility = View.GONE
            }

            if (tree.expandable) {
                expand.visibility = View.VISIBLE
                expand.setIconResource(
                    if (tree.expanded) R.drawable.ic_chevron_down else R.drawable.ic_chevron_right
                )
                expand.contentDescription =
                    if (tree.expanded) "Collapse ${row.title}" else "Expand ${row.title}"
                expand.minimumWidth = itemView.context.dp(48)
                expand.minimumHeight = itemView.context.dp(48)
                expand.onDebouncedClick { onToggle(row) }
                expand.post {
                    (expand.parent as? View)?.let { parent ->
                        val rect = android.graphics.Rect()
                        expand.getHitRect(rect)
                        rect.inset(-expand.context.dp(8), -expand.context.dp(8))
                        parent.touchDelegate = android.view.TouchDelegate(rect, expand)
                    }
                }
            } else {
                expand.visibility = View.GONE
                expand.setOnClickListener(null)
            }

            val child = tree.node.kind.child
            if (child != null) {
                add.visibility = View.VISIBLE
                add.contentDescription = "Add ${child.label} to ${row.title}"
                add.onDebouncedClick { onAdd(child.key, row.id) }
            } else {
                add.visibility = View.GONE
                add.setOnClickListener(null)
            }

            card.contentDescription = describe(row)
            card.onDebouncedClick { onOpen(row) }
            menu.contentDescription = "More options for ${row.title}"
            menu.onDebouncedClick { onMenu(row, it) }
        }

        /**
         * What a screen reader hears.
         *
         * Indentation and connector lines carry the hierarchy visually and
         * carry nothing at all to TalkBack, so the level is spoken. Without
         * this the whole tree reads as a flat list of titles, which is
         * exactly the screen this rewrite was replacing.
         */
        private fun describe(row: JourneyRow.Entity): String = buildString {
            append(row.row.node.kind.label)
            append(", ")
            append(row.title)
            if (row.subtitle.isNotBlank()) {
                append(", ")
                append(row.subtitle)
            }
            if (row.row.depth > 0) {
                append(", level ")
                append(row.row.depth + 1)
            }
            if (row.row.orphan) append(", not connected to anything above it")
            if (row.row.expandable && !row.row.expanded) {
                append(", ")
                append(row.row.descendantCount)
                append(" hidden below")
            }
        }
    }

    /**
     * A gap in the chain, drawn as an invitation rather than a warning.
     *
     * Reuses the empty-state card deliberately: a missing link and an empty
     * level are the same thing to a user - somewhere the app is asking for
     * one more piece - and giving them two different treatments would imply
     * a distinction that does not exist.
     */
    inner class GapVH(v: View) : RecyclerView.ViewHolder(v) {
        private val title: TextView = v.findViewById(R.id.empty_title)
        private val body: TextView = v.findViewById(R.id.empty_body)
        private val action: MaterialButton = v.findViewById(R.id.empty_action)
        fun bind(row: JourneyRow.Gap) {
            title.text = row.gap.title
            body.text = row.gap.body
            action.visibility = View.VISIBLE
            action.text = "Add ${row.gap.kind.label.lowercase()}"
            action.onDebouncedClick { onAdd(row.gap.kind.key, row.gap.nodeId) }
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
            action.onDebouncedClick { onAdd(row.kind, null) }
        }
    }
}
