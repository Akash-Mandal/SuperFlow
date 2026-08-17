package com.superflow.ui.sheets

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.superflow.R
import com.superflow.ui.common.visible
import android.widget.TextView

/**
 * Reusable single-field bottom sheet.
 *
 * Bottom sheets keep quick edits in place rather than pushing the user through
 * a full screen for one line of text.
 */
class TextInputSheet : BottomSheetDialogFragment() {

    var onSave: ((String) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.sheet_text_input, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val args = requireArguments()
        val titleView = view.findViewById<TextView>(R.id.sheet_title)
        val subtitleView = view.findViewById<TextView>(R.id.sheet_subtitle)
        val layout = view.findViewById<TextInputLayout>(R.id.input_layout)
        val input = view.findViewById<TextInputEditText>(R.id.input)
        val save = view.findViewById<MaterialButton>(R.id.btn_save)
        val cancel = view.findViewById<MaterialButton>(R.id.btn_cancel)

        titleView.text = args.getString(ARG_TITLE)
        val subtitle = args.getString(ARG_SUBTITLE)
        subtitleView.visible(!subtitle.isNullOrBlank())
        subtitleView.text = subtitle

        layout.hint = args.getString(ARG_HINT)
        input.setText(args.getString(ARG_VALUE).orEmpty())
        val lines = args.getInt(ARG_LINES, 1)
        if (lines > 1) {
            input.isSingleLine = false
            input.minLines = lines
            input.maxLines = lines + 3
        }
        input.requestFocus()

        save.setOnClickListener {
            onSave?.invoke(input.text?.toString().orEmpty())
            dismiss()
        }
        cancel.setOnClickListener { dismiss() }
    }

    companion object {
        private const val ARG_TITLE = "title"
        private const val ARG_SUBTITLE = "subtitle"
        private const val ARG_HINT = "hint"
        private const val ARG_VALUE = "value"
        private const val ARG_LINES = "lines"
        private const val TAG = "TextInputSheet"

        fun show(
            fm: FragmentManager,
            title: String,
            hint: String,
            subtitle: String? = null,
            value: String = "",
            lines: Int = 1,
            onSave: (String) -> Unit
        ) {
            val sheet = TextInputSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_TITLE, title)
                    putString(ARG_HINT, hint)
                    putString(ARG_SUBTITLE, subtitle)
                    putString(ARG_VALUE, value)
                    putInt(ARG_LINES, lines)
                }
                this.onSave = onSave
            }
            sheet.show(fm, TAG)
        }
    }
}
