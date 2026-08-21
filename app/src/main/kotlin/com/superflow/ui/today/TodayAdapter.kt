package com.superflow.ui.today

import android.content.res.ColorStateList
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.R as MR
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.slider.Slider
import com.superflow.R
import com.superflow.data.model.Checkpoint
import com.superflow.data.model.FocusItem
import com.superflow.data.model.Habit
import com.superflow.data.model.HabitMode
import com.superflow.data.model.Level
import com.superflow.ui.common.*
import com.superflow.util.Dates

/**
 * Today list adapter.
 *
 * A single ListAdapter with DiffUtil drives every card type, so updates
 * animate instead of flickering through notifyDataSetChanged.
 */
class TodayAdapter(
    private val callbacks: Callbacks
) : ListAdapter<TodayRow, RecyclerView.ViewHolder>(DIFF) {

    interface Callbacks {
        fun onCheck(habit: Habit, level: Level)
        fun onSkip(habit: Habit)
        fun onMissed(habit: Habit)
        fun onClear(habit: Habit)
        fun onOpenHabit(habit: Habit, card: View)
        fun onFocusToggle(item: FocusItem, done: Boolean)
        fun onFocusRemove(item: FocusItem)
        fun onFocusAdd()
        fun onFocusSuggest()
        fun onEnergy(value: Int)
        fun onCheckpoint(cp: Checkpoint)
        fun onEmptyAction()
    }

    companion object {
        private const val T_PROGRESS = 0
        private const val T_IDENTITY = 1
        private const val T_RETURN = 2
        private const val T_FOCUS = 3
        private const val T_CHECKPOINT = 4
        private const val T_GROWTH = 5
        private const val T_SECTION = 6
        private const val T_HABIT = 7
        private const val T_EMPTY = 8
        private const val T_LOAD = 9

        private val DIFF = object : DiffUtil.ItemCallback<TodayRow>() {
            override fun areItemsTheSame(a: TodayRow, b: TodayRow) = a.stableId == b.stableId
            override fun areContentsTheSame(a: TodayRow, b: TodayRow) = a == b
        }
    }

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = getItem(position).stableId

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is TodayRow.Progress -> T_PROGRESS
        is TodayRow.Load -> T_LOAD
        is TodayRow.IdentityCard -> T_IDENTITY
        is TodayRow.Returning -> T_RETURN
        is TodayRow.Focus -> T_FOCUS
        is TodayRow.Checkpoints -> T_CHECKPOINT
        is TodayRow.GrowthPlanStatus -> T_GROWTH
        is TodayRow.Section -> T_SECTION
        is TodayRow.HabitRow -> T_HABIT
        is TodayRow.Empty -> T_EMPTY
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            T_PROGRESS -> ProgressVH(inflater.inflate(R.layout.item_progress, parent, false))
            T_LOAD -> LoadVH(inflater.inflate(R.layout.item_text_card, parent, false))
            T_IDENTITY -> IdentityVH(inflater.inflate(R.layout.item_identity, parent, false))
            T_RETURN -> ReturnVH(inflater.inflate(R.layout.item_return, parent, false))
            T_FOCUS -> FocusVH(inflater.inflate(R.layout.item_focus, parent, false))
            T_CHECKPOINT -> CheckpointVH(inflater.inflate(R.layout.item_checkpoint, parent, false))
            T_GROWTH -> GrowthVH(inflater.inflate(R.layout.item_habit_stat, parent, false))
            T_SECTION -> SectionVH(inflater.inflate(R.layout.item_section, parent, false))
            T_HABIT -> HabitVH(inflater.inflate(R.layout.item_habit, parent, false))
            else -> EmptyVH(inflater.inflate(R.layout.item_empty, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is TodayRow.Progress -> (holder as ProgressVH).bind(row)
            is TodayRow.Load -> (holder as LoadVH).bind(row)
            is TodayRow.IdentityCard -> (holder as IdentityVH).bind(row)
            is TodayRow.Returning -> (holder as ReturnVH).bind(row)
            is TodayRow.Focus -> (holder as FocusVH).bind(row)
            is TodayRow.Checkpoints -> (holder as CheckpointVH).bind(row)
            is TodayRow.GrowthPlanStatus -> (holder as GrowthVH).bind(row)
            is TodayRow.Section -> (holder as SectionVH).bind(row)
            is TodayRow.HabitRow -> (holder as HabitVH).bind(row)
            is TodayRow.Empty -> (holder as EmptyVH).bind(row)
        }
    }

    /* ----------------------------------------------------------- view holders */

    inner class ProgressVH(v: View) : RecyclerView.ViewHolder(v) {
        private val ring: ProgressRing = v.findViewById(R.id.ring)
        private val title: TextView = v.findViewById(R.id.progress_title)
        private val sub: TextView = v.findViewById(R.id.progress_sub)

        fun bind(row: TodayRow.Progress) {
            val fraction = if (row.total == 0) 0f else row.done.toFloat() / row.total
            ring.centerLabel = if (row.total == 0) "—" else "${(fraction * 100).toInt()}%"
            ring.centerSub = if (row.total == 0) "" else "${row.done}/${row.total}"
            ring.setProgress(fraction)
            title.text = if (row.total == 0) itemView.context.getString(R.string.nothing_scheduled)
            else "${row.done} of ${row.total} actions"
            sub.text = row.message
        }
    }

    inner class LoadVH(v: View) : RecyclerView.ViewHolder(v) {
        private val title: TextView = v.findViewById(R.id.text_title)
        private val body: TextView = v.findViewById(R.id.text_body)

        fun bind(row: TodayRow.Load) {
            val color = when (row.color) {
                "amber" -> itemView.context.getColor(com.superflow.R.color.sf_amber_50)
                "coral" -> itemView.context.getColor(com.superflow.R.color.sf_error_40)
                else -> itemView.context.getColor(com.superflow.R.color.sf_green_50)
            }
            title.setTextColor(color)
            title.text = "Today's load: ${row.habits} habits · ~${row.minutes} min"
            body.text = when (row.color) {
                "green" -> "Light day. Room to add a stretch if you want one."
                "amber" -> "Moderate day. If it feels heavy, Minimum Mode shrinks everything."
                else -> "Heavy day. Research says 3-5 new behaviours is the practical max. " +
                        "Consider pausing one or using Minimum Mode."
            }
        }
    }

    inner class IdentityVH(v: View) : RecyclerView.ViewHolder(v) {
        private val text: TextView = v.findViewById(R.id.identity_text)
        private val votes: TextView = v.findViewById(R.id.identity_votes)

        fun bind(row: TodayRow.IdentityCard) {
            text.text = row.statement
            votes.text = if (row.votes == 0) "Your first repetition becomes the first piece of evidence."
            else "${row.votes} ${if (row.votes == 1) "vote" else "votes"} so far"
        }
    }

    inner class GrowthVH(v: View) : RecyclerView.ViewHolder(v) {
        private val bar: LinearProgressIndicator = v.findViewById(R.id.hs_bar)
        fun bind(row: TodayRow.GrowthPlanStatus) {
            itemView.findViewById<TextView>(R.id.hs_title).text = "Phase ${row.phaseIndex}/${row.totalPhases} · ${row.phaseLabel}"
            itemView.findViewById<TextView>(R.id.hs_percent).text = row.habitTitle
            itemView.findViewById<TextView>(R.id.hs_detail).text = "Growth plan active"
            val hint = itemView.findViewById<TextView>(R.id.hs_hint)
            hint.visible(true)
            hint.text = "Show up consistently to upgrade to the next phase."
            bar.setProgressCompat((row.phaseIndex * 100) / row.totalPhases, true)
        }
    }

    inner class ReturnVH(v: View) : RecyclerView.ViewHolder(v) {
        private val container: LinearLayout = v.findViewById(R.id.return_items)

        fun bind(row: TodayRow.Returning) {
            container.removeAllViews()
            val inflater = LayoutInflater.from(container.context)
            for (habit in row.habits.take(3)) {
                val item = inflater.inflate(R.layout.item_return_row, container, false)
                item.findViewById<TextView>(R.id.return_title).text =
                    habit.tinyStart.ifBlank { habit.title }
                item.findViewById<MaterialButton>(R.id.return_do).setOnClickListener {
                    it.haptic()
                    callbacks.onCheck(habit, Level.TINY)
                }
                container.addView(item)
            }
        }
    }

    inner class FocusVH(v: View) : RecyclerView.ViewHolder(v) {
        private val count: TextView = v.findViewById(R.id.focus_count)
        private val container: LinearLayout = v.findViewById(R.id.focus_items)
        private val add: MaterialButton = v.findViewById(R.id.focus_add)
        private val suggest: MaterialButton = v.findViewById(R.id.focus_suggest)

        fun bind(row: TodayRow.Focus) {
            count.text = "${row.items.size}/3"
            container.removeAllViews()
            val inflater = LayoutInflater.from(container.context)
            for (item in row.items) {
                val v = inflater.inflate(R.layout.item_focus_row, container, false)
                val check = v.findViewById<MaterialCheckBox>(R.id.focus_check)
                val title = v.findViewById<TextView>(R.id.focus_title)
                title.text = item.title
                check.setOnCheckedChangeListener(null)
                check.isChecked = item.done
                title.paintFlags = if (item.done)
                    title.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                else title.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                title.alpha = if (item.done) 0.55f else 1f
                check.setOnCheckedChangeListener { view, checked ->
                    view.haptic()
                    callbacks.onFocusToggle(item, checked)
                }
                v.findViewById<MaterialButton>(R.id.focus_remove).setOnClickListener {
                    callbacks.onFocusRemove(item)
                }
                container.addView(v)
            }
            add.isEnabled = row.items.size < 3
            suggest.isEnabled = row.items.size < 3
            add.setOnClickListener { callbacks.onFocusAdd() }
            suggest.setOnClickListener { callbacks.onFocusSuggest() }
        }
    }

    inner class CheckpointVH(v: View) : RecyclerView.ViewHolder(v) {
        private val chips: ChipGroup = v.findViewById(R.id.checkpoint_chips)
        private val slider: Slider = v.findViewById(R.id.energy_slider)
        private val label: TextView = v.findViewById(R.id.energy_label)

        fun bind(row: TodayRow.Checkpoints) {
            chips.removeAllViews()
            for (cp in Checkpoint.values()) {
                val chip = Chip(chips.context).apply {
                    text = cp.label
                    isCheckable = false
                    setEnsureMinTouchTargetSize(false)
                    setOnClickListener {
                        it.haptic()
                        callbacks.onCheckpoint(cp)
                    }
                }
                chips.addView(chip)
            }
            slider.clearOnChangeListeners()
            slider.value = (row.energy ?: 3).toFloat().coerceIn(1f, 5f)
            label.text = if (row.energy == null) itemView.context.getString(R.string.energy_now)
            else "${itemView.context.getString(R.string.energy_now)} · ${row.energy}/5"
            slider.addOnChangeListener { _, value, fromUser ->
                if (fromUser) callbacks.onEnergy(value.toInt())
            }
        }
    }

    inner class SectionVH(v: View) : RecyclerView.ViewHolder(v) {
        private val title: TextView = v as TextView
        fun bind(row: TodayRow.Section) { title.text = row.title }
    }

    inner class HabitVH(v: View) : RecyclerView.ViewHolder(v) {
        private val card: MaterialCardView = v.findViewById(R.id.habit_card)
        private val target: View = v.findViewById(R.id.check_target)
        private val circle: ShapeableImageView = v.findViewById(R.id.check_circle)
        private val title: TextView = v.findViewById(R.id.habit_title)
        private val cue: TextView = v.findViewById(R.id.habit_cue)
        private val tiny: TextView = v.findViewById(R.id.habit_tiny)
        private val history: HistoryStrip = v.findViewById(R.id.history)
        private val chips: ChipGroup = v.findViewById(R.id.level_chips)
        private val status: MaterialButton = v.findViewById(R.id.status_chip)

        fun bind(row: TodayRow.HabitRow) {
            val context = itemView.context
            val habit = row.item.habit
            val done = row.item.done
            val skipped = row.item.skipped
            val missed = row.item.missed

            title.text = habit.title
            title.alpha = if (done || skipped) 0.6f else 1f

            val cueText = buildString {
                if (habit.anchorText.isNotBlank()) append("After ${habit.anchorText}")
                else {
                    if (habit.cueTime.isNotBlank()) append(habit.cueTime)
                    if (habit.cuePlace.isNotBlank()) {
                        if (isNotEmpty()) append(" · ")
                        append(habit.cuePlace)
                    }
                }
                if (habit.mode == HabitMode.REDUCE) {
                    if (isNotEmpty()) append(" · ")
                    append("reduce")
                }
            }
            cue.visible(cueText.isNotBlank())
            cue.text = cueText

            tiny.visible(!done && !skipped && habit.tinyStart.isNotBlank())
            tiny.text = "Tiny: ${habit.tinyStart}"

            history.setStates(row.history)

            // Completion circle
            val primary = context.themeColor(MR.attr.colorPrimary)
            val outline = context.themeColor(MR.attr.colorOutline)
            if (done) {
                circle.setBackgroundColor(0)
                circle.backgroundTintList = ColorStateList.valueOf(primary)
                circle.imageTintList = ColorStateList.valueOf(context.themeColor(MR.attr.colorOnPrimary))
                circle.strokeColor = ColorStateList.valueOf(primary)
                circle.alpha = 1f
            } else {
                circle.backgroundTintList =
                    ColorStateList.valueOf(context.themeColor(MR.attr.colorSurface))
                circle.imageTintList = ColorStateList.valueOf(
                    if (skipped || missed) outline else context.themeColor(MR.attr.colorSurfaceVariant)
                )
                circle.strokeColor = ColorStateList.valueOf(outline)
                circle.alpha = 1f
            }

            card.isChecked = done
            card.setCardBackgroundColor(
                if (done) blend(primary, context.themeColor(MR.attr.colorSurface), 0.88f)
                else context.themeColor(MR.attr.colorSurface)
            )

            // Status pill
            val statusText = when {
                done -> row.item.checkIn?.level?.label ?: "Done"
                skipped -> "Skipped"
                missed -> "Missed"
                else -> null
            }
            status.visible(statusText != null)
            statusText?.let { status.text = it }
            status.setOnClickListener { callbacks.onClear(habit) }

            target.setOnClickListener {
                it.confirmHaptic()
                if (done || skipped || missed) callbacks.onClear(habit)
                else callbacks.onCheck(habit, Level.STANDARD)
            }

            card.setOnClickListener { callbacks.onOpenHabit(habit, card) }
            card.transitionName = "habit_${habit.id}"

            // Level chips only while the day is still open
            chips.removeAllViews()
            chips.visible(!done && !skipped)
            if (!done && !skipped) {
                for (level in listOf(Level.TINY, Level.MINIMUM, Level.STRETCH)) {
                    chips.addView(makeChip(level.label) { callbacks.onCheck(habit, level) })
                }
                chips.addView(makeChip("Skip") { callbacks.onSkip(habit) })
                if (!missed) chips.addView(makeChip("Missed") { callbacks.onMissed(habit) })
            }
        }

        private fun makeChip(label: String, onClick: () -> Unit): Chip =
            Chip(chips.context).apply {
                text = label
                isCheckable = false
                isClickable = true
                setEnsureMinTouchTargetSize(false)
                chipMinHeight = context.dpf(32f)
                setOnClickListener {
                    it.haptic()
                    onClick()
                }
            }
    }

    inner class EmptyVH(v: View) : RecyclerView.ViewHolder(v) {
        private val title: TextView = v.findViewById(R.id.empty_title)
        private val body: TextView = v.findViewById(R.id.empty_body)
        private val action: MaterialButton = v.findViewById(R.id.empty_action)
        private val icon: ImageView = v.findViewById(R.id.empty_icon)

        fun bind(row: TodayRow.Empty) {
            title.text = row.title
            body.text = row.body
            icon.setImageResource(R.drawable.ic_sparkle)
            action.visible(row.action != null)
            row.action?.let {
                action.text = it
                action.setOnClickListener { callbacks.onEmptyAction() }
            }
        }
    }
}
