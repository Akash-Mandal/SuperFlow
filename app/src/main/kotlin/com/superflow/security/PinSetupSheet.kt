package com.superflow.security

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.superflow.R
import com.superflow.data.Prefs

/**
 * Set or change the app-lock PIN.
 *
 * The entry step asks for a new PIN; the confirmation step asks for it
 * again; only matching, valid PINs are stored via [AppLock]. The sheet never
 * echoes the PIN back beyond the field's own masking.
 */
class PinSetupSheet : BottomSheetDialogFragment() {

    var onSaved: (() -> Unit)? = null

    private var step = STEP_ENTRY
    private var first = ""

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        BottomSheetDialog(requireContext())

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: android.view.ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.sheet_pin_setup, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val title = view.findViewById<TextView>(R.id.pin_title)
        val body = view.findViewById<TextView>(R.id.pin_body)
        val layout = view.findViewById<TextInputLayout>(R.id.pin_layout)
        val field = view.findViewById<TextInputEditText>(R.id.pin_field)
        val save = view.findViewById<MaterialButton>(R.id.btn_save)
        val cancel = view.findViewById<MaterialButton>(R.id.btn_cancel)

        fun render() {
            if (step == STEP_ENTRY) {
                title.setText(R.string.set_pin)
                body.text = "Choose a 4–12 digit PIN. It is stored only as a salted hash on this device."
                layout.hint = "New PIN"
                save.setText(R.string.next)
            } else {
                title.setText(R.string.change_pin)
                body.text = "Enter it once more to confirm."
                layout.hint = "Confirm PIN"
                save.setText(R.string.save)
            }
            field.text?.clear()
        }
        render()

        save.setOnClickListener {
            val pin = field.text?.toString().orEmpty()
            if (step == STEP_ENTRY) {
                if (!AppLock.validPin(pin)) {
                    layout.error = "PIN must be 4–12 digits"
                    return@setOnClickListener
                }
                first = pin
                step = STEP_CONFIRM
                render()
            } else {
                if (pin != first) {
                    layout.error = "PINs do not match"
                    return@setOnClickListener
                }
                AppLock.setPin(Prefs.get(requireContext()), pin)
                onSaved?.invoke()
                dismiss()
            }
        }
        cancel.setOnClickListener { dismiss() }
    }

    companion object {
        private const val STEP_ENTRY = 0
        private const val STEP_CONFIRM = 1
    }
}
