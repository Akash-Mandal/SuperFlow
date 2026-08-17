package com.superflow.ui.sheets

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.superflow.R
import com.superflow.data.model.LifeArea
import com.superflow.ui.common.visible

/**
 * Two-field editor sheet with an optional single-select chip row.
 *
 * Used for identities, goals and systems so those edits never require leaving
 * the Journey screen.
 */
class EntityEditorSheet : BottomSheetDialogFragment() {

    /** (field1, field2, selectedChipKey) */
    var onSave: ((String, String, String?) -> Unit)? = null
    private var chipKeys: List<String> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.sheet_entity_editor, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val args = requireArguments()
        view.findViewById<TextView>(R.id.sheet_title).text = args.getString(ARG_TITLE)

        val l1 = view.findViewById<TextInputLayout>(R.id.field_1_layout)
        val f1 = view.findViewById<TextInputEditText>(R.id.field_1)
        val l2 = view.findViewById<TextInputLayout>(R.id.field_2_layout)
        val f2 = view.findViewById<TextInputEditText>(R.id.field_2)
        val chipsLabel = view.findViewById<TextView>(R.id.chips_label)
        val chips = view.findViewById<ChipGroup>(R.id.chips)

        l1.hint = args.getString(ARG_HINT1)
        f1.setText(args.getString(ARG_VALUE1).orEmpty())

        val hint2 = args.getString(ARG_HINT2)
        l2.visible(!hint2.isNullOrBlank())
        l2.hint = hint2
        f2.setText(args.getString(ARG_VALUE2).orEmpty())

        val labels = args.getStringArrayList(ARG_CHIP_LABELS).orEmpty()
        chipKeys = args.getStringArrayList(ARG_CHIP_KEYS).orEmpty()
        val selected = args.getString(ARG_CHIP_SELECTED)
        val chipsTitle = args.getString(ARG_CHIP_LABEL)

        chipsLabel.visible(labels.isNotEmpty())
        chips.visible(labels.isNotEmpty())
        chipsLabel.text = chipsTitle

        labels.forEachIndexed { index, label ->
            val chip = Chip(chips.context).apply {
                text = label
                isCheckable = true
                id = View.generateViewId()
                isChecked = chipKeys.getOrNull(index) == selected
                setEnsureMinTouchTargetSize(false)
            }
            chips.addView(chip)
        }

        view.findViewById<MaterialButton>(R.id.btn_cancel).setOnClickListener { dismiss() }
        view.findViewById<MaterialButton>(R.id.btn_save).setOnClickListener {
            val text1 = f1.text?.toString().orEmpty().trim()
            if (text1.isBlank()) {
                l1.error = "Required"
                return@setOnClickListener
            }
            var key: String? = null
            for (i in 0 until chips.childCount) {
                val c = chips.getChildAt(i) as Chip
                if (c.isChecked) key = chipKeys.getOrNull(i)
            }
            onSave?.invoke(text1, f2.text?.toString().orEmpty().trim(), key)
            dismiss()
        }
    }

    companion object {
        private const val ARG_TITLE = "t"
        private const val ARG_HINT1 = "h1"
        private const val ARG_HINT2 = "h2"
        private const val ARG_VALUE1 = "v1"
        private const val ARG_VALUE2 = "v2"
        private const val ARG_CHIP_LABEL = "cl"
        private const val ARG_CHIP_LABELS = "cls"
        private const val ARG_CHIP_KEYS = "cks"
        private const val ARG_CHIP_SELECTED = "csel"

        private fun build(
            title: String, hint1: String, value1: String,
            hint2: String?, value2: String,
            chipLabel: String?, chips: List<Pair<String, String>>, selected: String?,
            onSave: (String, String, String?) -> Unit
        ) = EntityEditorSheet().apply {
            arguments = Bundle().apply {
                putString(ARG_TITLE, title)
                putString(ARG_HINT1, hint1)
                putString(ARG_VALUE1, value1)
                putString(ARG_HINT2, hint2)
                putString(ARG_VALUE2, value2)
                putString(ARG_CHIP_LABEL, chipLabel)
                putStringArrayList(ARG_CHIP_KEYS, ArrayList(chips.map { it.first }))
                putStringArrayList(ARG_CHIP_LABELS, ArrayList(chips.map { it.second }))
                putString(ARG_CHIP_SELECTED, selected)
            }
            this.onSave = onSave
        }

        fun identity(
            fm: FragmentManager, statement: String, area: LifeArea,
            onSave: (String, LifeArea) -> Unit
        ) {
            build(
                title = if (statement.isBlank()) "New identity" else "Edit identity",
                hint1 = "I am becoming someone who…", value1 = statement,
                hint2 = null, value2 = "",
                chipLabel = "Life area",
                chips = LifeArea.values().map { it.name to it.label },
                selected = area.name
            ) { text, _, key -> onSave(text, LifeArea.from(key)) }
                .show(fm, "identity")
        }

        fun goal(
            fm: FragmentManager, title: String, why: String,
            identities: List<Pair<String, String>>, selected: String?,
            onSave: (String, String, String?) -> Unit
        ) {
            build(
                title = if (title.isBlank()) "New goal" else "Edit goal",
                hint1 = "Goal", value1 = title,
                hint2 = "Why does this matter?", value2 = why,
                chipLabel = if (identities.isEmpty()) null else "Linked identity",
                chips = identities.map { it.first to it.second.take(28) },
                selected = selected,
                onSave = onSave
            ).show(fm, "goal")
        }

        fun system(
            fm: FragmentManager, title: String, description: String,
            goals: List<Pair<String, String>>, selected: String?,
            onSave: (String, String, String?) -> Unit
        ) {
            build(
                title = if (title.isBlank()) "New system" else "Edit system",
                hint1 = "System", value1 = title,
                hint2 = "Describe the repeatable process", value2 = description,
                chipLabel = if (goals.isEmpty()) null else "Supports goal",
                chips = goals.map { it.first to it.second.take(28) },
                selected = selected,
                onSave = onSave
            ).show(fm, "system")
        }
    }
}
