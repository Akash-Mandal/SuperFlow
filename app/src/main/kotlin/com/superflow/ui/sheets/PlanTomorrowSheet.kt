package com.superflow.ui.sheets

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.superflow.R
import com.superflow.data.Repository
import com.superflow.data.model.Habit
import com.superflow.domain.CommandBus
import com.superflow.domain.Insights
import com.superflow.util.jsonArrayOf
import com.superflow.util.jsonOf
import java.time.LocalDate

/**
 * Guided "Plan tomorrow" flow (#23).
 *
 * Lists tomorrow's scheduled habits; the user picks up to three to pre-fill
 * tomorrow's Daily Focus. Tapping "Plan" runs the same `set_daily_focus`
 * capability the AI uses, so there is one code path.
 */
class PlanTomorrowSheet : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.sheet_plan_tomorrow, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val repo = Repository.get(requireContext())
        val bus = CommandBus.get(requireContext())
        val tomorrow = LocalDate.now().plusDays(1)
        val list = view.findViewById<ViewGroup>(R.id.plan_list)
        val save = view.findViewById<MaterialButton>(R.id.btn_save)

        val candidates = repo.habitsForDay(tomorrow)
            .sortedWith(compareByDescending<Habit> { it.protectedRoutine }
                .thenBy { it.orderIndex })
            .take(6)

        if (candidates.isEmpty()) {
            TextView(requireContext()).apply {
                text = "No habits are scheduled for tomorrow yet."
                setPadding(0, resources.getDimensionPixelSize(R.dimen.space_m), 0, 0)
                list.addView(this)
            }
            save.isEnabled = false
            return
        }

        val checks = ArrayList<MaterialCheckBox>()
        candidates.take(3).forEachIndexed { i, h ->
            val row = layoutInflater.inflate(R.layout.item_focus_row, list, false)
            val check = row.findViewById<MaterialCheckBox>(R.id.focus_check)
            row.findViewById<TextView>(R.id.focus_title).text = h.title
            check.isChecked = i < 3
            check.setOnCheckedChangeListener { _, _ -> updateSaveLabel(save, checks) }
            checks.add(check)
            list.addView(row)
        }
        updateSaveLabel(save, checks)

        save.setOnClickListener {
            val chosen = candidates.filterIndexed { i, _ -> checks.getOrNull(i)?.isChecked == true }
                .map { it.title }
            if (chosen.isEmpty()) { dismiss(); return@setOnClickListener }
            bus.execute("set_daily_focus",
                jsonOf("items" to jsonArrayOf(chosen),
                    "date" to com.superflow.core.time.SfTime.format(tomorrow)),
                com.superflow.domain.Actor.USER)
            dismiss()
        }
        view.findViewById<MaterialButton>(R.id.btn_cancel).setOnClickListener { dismiss() }
    }

    private fun updateSaveLabel(save: MaterialButton, checks: List<MaterialCheckBox>) {
        val n = checks.count { it.isChecked }
        save.text = if (n == 0) "Pick at least one" else "Plan $n"
        save.isEnabled = n > 0
    }

    companion object {
        fun show(fm: FragmentManager) = PlanTomorrowSheet().show(fm, "plan_tomorrow")
    }
}
