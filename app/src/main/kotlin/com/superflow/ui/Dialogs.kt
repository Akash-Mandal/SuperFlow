package com.superflow.ui

import android.app.Activity
import android.app.AlertDialog
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView

/** Plain, calm dialogs. No urgency, no dark patterns. */
object Dialogs {

    /** Form dialog. The body callback returns true to dismiss. */
    fun form(activity: Activity, title: String, body: View, onSave: () -> Boolean) {
        val scroll = ScrollView(activity).apply {
            setPadding(activity.dp(22), activity.dp(8), activity.dp(22), activity.dp(8))
            addView(body, LinearLayout.LayoutParams(MATCH, WRAP))
        }
        val dialog = AlertDialog.Builder(activity)
            .setTitle(title)
            .setView(scroll)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (onSave()) dialog.dismiss()
            }
        }
        dialog.show()
    }

    fun confirm(activity: Activity, message: String, onYes: () -> Unit) {
        AlertDialog.Builder(activity)
            .setMessage(message)
            .setPositiveButton("Yes") { _, _ -> onYes() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun info(activity: Activity, title: String, message: String) {
        AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Close", null)
            .show()
    }

    fun choose(activity: Activity, title: String, options: List<String>, onPick: (Int) -> Unit) {
        AlertDialog.Builder(activity)
            .setTitle(title)
            .setItems(options.toTypedArray()) { _, which -> onPick(which) }
            .show()
    }

    fun text(activity: Activity, title: String, hint: String, initial: String = "", onSave: (String) -> Unit) {
        val input = activity.field(hint, initial, lines = 3)
        val wrap = activity.column(0) { addView(input) }
        form(activity, title, wrap) {
            onSave(input.text.toString())
            true
        }
    }
}
