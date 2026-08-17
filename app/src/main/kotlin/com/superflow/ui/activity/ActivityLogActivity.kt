package com.superflow.ui.activity

import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.superflow.R
import com.superflow.data.Repository
import com.superflow.data.model.AuditEntry
import com.superflow.domain.CommandBus
import com.superflow.ui.common.ScrollActivity
import com.superflow.ui.common.snack
import com.superflow.util.Dates

/**
 * The shared Activity trail.
 *
 * Every change is here, whoever made it, with per-action and grouped undo.
 * This is the accountability that lets Full Control skip confirmations.
 */
class ActivityLogActivity : ScrollActivity() {

    private val bus by lazy { CommandBus.get(this) }
    private val repo by lazy { Repository.get(this) }
    private var filter = "ALL"

    override fun titleText() = getString(R.string.activity_trail)

    override fun buildContent() {
        content.addView(textCard("Everything that changed",
            "Actions from you, from AI and from scheduled jobs — each individually undoable."))

        val chips = ChipGroup(this).apply { isSingleSelection = true }
        listOf("ALL", "USER", "AI", "SYSTEM").forEach { f ->
            chips.addView(Chip(this).apply {
                text = f.lowercase().replaceFirstChar { it.uppercase() }
                isCheckable = true
                isChecked = filter == f
                setEnsureMinTouchTargetSize(false)
                setOnClickListener { filter = f; rebuild() }
            })
        }
        content.addView(chips)

        val entries = repo.audit(300).filter { filter == "ALL" || it.actor == filter }
        if (entries.isEmpty()) {
            content.addView(textCard("Nothing recorded yet",
                "Actions appear here the moment anything changes."))
            return
        }

        val groups = LinkedHashMap<String, MutableList<AuditEntry>>()
        entries.forEach { groups.getOrPut(it.groupId ?: it.id) { ArrayList() }.add(it) }

        for ((key, list) in groups) {
            val card = layoutInflater.inflate(R.layout.item_text_card, content, false)
            val title = card.findViewById<TextView>(R.id.text_title)
            val body = card.findViewById<TextView>(R.id.text_body)
            if (list.size > 1) {
                title.text = "Grouped run · ${list.size} actions"
                body.text = list.joinToString("\n") {
                    "· ${it.summary}${if (it.undone) "  (undone)" else ""}"
                } + "\n\n${list.first().actor.lowercase()} · ${Dates.relativeStamp(list.first().createdAt)}"
                if (list.any { !it.undone }) {
                    (title.parent as LinearLayout).addView(MaterialButton(this, null,
                        com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                        text = "Undo all"
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).also { it.topMargin = dpi(12) }
                        setOnClickListener {
                            val res = bus.undoGroup(key)
                            findViewById<View>(R.id.root).snack(res.message)
                            rebuild()
                        }
                    })
                }
            } else {
                val e = list.first()
                title.text = e.summary
                body.text = "${e.actor.lowercase()} · ${Dates.relativeStamp(e.createdAt)}" +
                        if (e.undone) " · undone" else ""
                if (!e.undone && e.undoPayload.isNotBlank()) {
                    (title.parent as LinearLayout).addView(MaterialButton(this, null,
                        com.google.android.material.R.attr.borderlessButtonStyle).apply {
                        text = getString(R.string.undo)
                        setOnClickListener {
                            val res = bus.undo(e)
                            findViewById<View>(R.id.root).snack(res.message)
                            rebuild()
                        }
                    })
                }
            }
            content.addView(card)
        }

        content.addView(MaterialButton(this, null,
            com.google.android.material.R.attr.borderlessButtonStyle).apply {
            text = "Clear trail"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                com.google.android.material.dialog.MaterialAlertDialogBuilder(this@ActivityLogActivity)
                    .setTitle("Clear the activity history?")
                    .setMessage("Undo data will be lost.")
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton("Clear") { _, _ -> repo.clearAudit(); rebuild() }
                    .show()
            }
        })
    }

    private fun dpi(v: Int) = (v * resources.displayMetrics.density).toInt()
}
