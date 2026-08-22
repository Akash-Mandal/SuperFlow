package com.superflow.ui.pause

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.superflow.R
import com.superflow.core.time.SfTime
import com.superflow.data.Repository
import com.superflow.data.model.PauseWindow
import com.superflow.domain.Actor
import com.superflow.domain.CommandBus
import com.superflow.ui.common.ScrollActivity
import com.superflow.ui.common.snack
import com.superflow.util.jsonOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Pause / vacation mode.
 *
 * "Going on break?" lets the user pause every habit (or one habit) for a date
 * range without creating misses — the same [pause_habits] capability used by
 * the AI and by notifications. Active pauses are listed with a resume action.
 */
class PauseActivity : ScrollActivity() {

    private lateinit var repo: Repository
    private lateinit var bus: CommandBus

    private var fromDate: LocalDate = LocalDate.now()
    private var toDate: LocalDate = LocalDate.now()
    private var habitId: String? = null

    override fun titleText() = getString(R.string.pause_mode)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = Repository.get(this)
        bus = CommandBus.get(this)
    }

    override fun buildContent() {
        content.addView(section("GOING ON A BREAK?"))
        content.addView(textCard(
            "Pause protects your run",
            "Paused days never count as misses. Your streak, consistency and " +
                    "\"never miss twice\" logic simply look past them."
        ))

        // Range chips
        content.addView(label("How long?"))
        val rangeChips = ChipGroup(this).apply { isSingleSelection = true }
        val today = LocalDate.now()
        val ranges = listOf(
            "Today" to (today to today),
            "This weekend" to run {
                val sat = today.with(java.time.DayOfWeek.SATURDAY)
                val sun = today.with(java.time.DayOfWeek.SUNDAY)
                sat to sun
            },
            "One week" to (today to today.plusDays(6)),
            "Two weeks" to (today to today.plusDays(13))
        )
        ranges.forEachIndexed { i, (label, range) ->
            rangeChips.addView(Chip(this).apply {
                text = label
                isCheckable = true
                isChecked = i == 0
                setEnsureMinTouchTargetSize(false)
                setOnClickListener {
                    fromDate = range.first
                    toDate = range.second
                    refreshDateLabels()
                }
            })
        }
        content.addView(rangeChips)

        // Custom date range
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val fromBtn = dateButton("From") { fromDate = it; refreshDateLabels() }
        val toBtn = dateButton("To") { toDate = it; refreshDateLabels() }
        fromBtn.layoutParams = LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also { it.marginEnd = dp(6) }
        toBtn.layoutParams = LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also { it.marginStart = dp(6) }
        row.addView(fromBtn); row.addView(toBtn)
        content.addView(row)
        fromButton = fromBtn; toButton = toBtn
        refreshDateLabels()

        // Scope: all habits, or one
        content.addView(label("What is paused?"))
        val scopeChips = ChipGroup(this).apply { isSingleSelection = true }
        scopeChips.addView(Chip(this).apply {
            text = "All habits"; isCheckable = true; isChecked = true
            setEnsureMinTouchTargetSize(false)
            setOnClickListener { habitId = null; rebuildHabitPicker() }
        })
        val habits = repo.habits()
        if (habits.isNotEmpty()) {
            habits.forEach { h ->
                scopeChips.addView(Chip(this).apply {
                    text = h.title; isCheckable = true
                    setEnsureMinTouchTargetSize(false)
                    setOnClickListener { habitId = h.id; rebuildHabitPicker() }
                })
            }
        }
        content.addView(scopeChips)

        // Reason
        val reasonLayout = layoutInflater.inflate(R.layout.part_field, content, false)
            as TextInputLayout
        reasonLayout.hint = "Reason (optional)"
        val reasonEdit = reasonLayout.findViewById<TextInputEditText>(R.id.field_edit)
        reasonEdit.minLines = 2
        reasonEdit.isSingleLine = false
        reasonEdit.inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
        reasonEdit.maxLines = 3
        content.addView(reasonLayout)

        val save = MaterialButton(this).apply {
            text = "Pause"; isEnabled = true
            setOnClickListener {
                if (toDate.isBefore(fromDate)) {
                    snack("The end date is before the start")
                    return@setOnClickListener
                }
                val args = jsonOf(
                    "from" to SfTime.format(fromDate),
                    "to" to SfTime.format(toDate),
                    "reason" to reasonEdit.text?.toString().orEmpty().trim()
                )
                habitId?.let { args.put("habit", it) }
                lifecycleScope.launch {
                    val res = withContext(Dispatchers.IO) {
                        bus.execute("pause_habits", args, Actor.USER)
                    }
                    snack(res.message)
                    if (res.ok) finish()
                }
            }
        }
        content.addView(save)

        // Active pauses
        val active = activePauses()
        if (active.isNotEmpty()) {
            content.addView(section("ACTIVE PAUSES"))
            for (p in active) {
                val card = layoutInflater.inflate(R.layout.item_text_card, content, false)
                val habitName = p.habitId?.let { id -> repo.habit(id)?.title } ?: "All habits"
                card.findViewById<TextView>(R.id.text_title).text = habitName
                card.findViewById<TextView>(R.id.text_body).text = buildString {
                    append(SfTime.shortDay(java.time.LocalDate.parse(p.startDate)))
                    append(" – ")
                    append(SfTime.shortDay(java.time.LocalDate.parse(p.endDate)))
                    if (p.reason.isNotBlank()) append(" · ${p.reason}")
                }
                val parent = card.findViewById<TextView>(R.id.text_title).parent as LinearLayout
                parent.addView(MaterialButton(
                    this, null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle
                ).apply {
                    text = "Resume"
                    setOnClickListener { resume(p.id) }
                })
                content.addView(card)
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(content) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.updatePadding(bottom = maxOf(bars.bottom, ime.bottom) + resources.getDimensionPixelSize(R.dimen.space_xl))
            insets
        }
    }

    private var fromButton: MaterialButton? = null
    private var toButton: MaterialButton? = null

    private fun rebuildHabitPicker() { /* selection is stored in habitId */ }

    private fun activePauses(): List<PauseWindow> {
        val today = LocalDate.now()
        return repo.pauses().filter {
            val end = runCatching { LocalDate.parse(it.endDate) }.getOrNull() ?: return@filter false
            !end.isBefore(today)
        }.sortedBy { it.startDate }
    }

    private fun resume(id: String) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                bus.execute("resume_habits", jsonOf("id" to id), Actor.USER)
            }
            snack("Resumed")
            rebuild()
        }
    }

    private fun dateButton(label: String, onPick: (LocalDate) -> Unit): MaterialButton {
        val btn = MaterialButton(
            this, null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            text = label
            setOnClickListener { pickDate(onPick) }
        }
        return btn
    }

    private fun pickDate(onPick: (LocalDate) -> Unit) {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Choose date")
            .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
            .build()
        picker.addOnPositiveButtonClickListener { millis ->
            val date = java.time.Instant.ofEpochMilli(millis)
                .atZone(ZoneOffset.UTC).toLocalDate()
            onPick(date)
        }
        picker.show(supportFragmentManager, "date")
    }

    private fun refreshDateLabels() {
        fromButton?.text = "From: ${SfTime.shortDay(fromDate)}"
        toButton?.text = "To: ${SfTime.shortDay(toDate)}"
    }

    private fun label(text: String): TextView =
        TextView(this).apply {
            this.text = text
            setTextAppearance(R.style.Text_SuperFlow_TitleMedium)
            setPadding(dp(4), dp(16), dp(4), dp(6))
        }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
