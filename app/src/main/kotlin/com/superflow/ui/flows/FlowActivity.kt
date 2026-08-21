package com.superflow.ui.flows

import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.superflow.R
import com.superflow.data.Repository
import com.superflow.domain.Actor
import com.superflow.domain.CommandBus
import com.superflow.ui.common.ScrollActivity
import com.superflow.ui.common.snack
import com.superflow.ui.sheets.TextInputSheet
import com.superflow.util.jsonArrayOf
import com.superflow.util.jsonOf

/**
 * Flow Builder: chains of anchored habits.
 *
 *   Wake up -> drink water -> open curtains -> one minute of stretching
 *
 * Existing stable behaviour is shown differently from a new behaviour, and the
 * app warns past three new links at once.
 */
class FlowActivity : ScrollActivity() {

    private val bus by lazy { CommandBus.get(this) }
    private val repo by lazy { Repository.get(this) }

    override fun titleText() = getString(R.string.flows)

    override fun buildContent() {
        content.addView(textCard("Chain small actions",
            "Attach new behaviour to something you already do reliably. " +
                    "Each link becomes the cue for the next."))

        content.addView(MaterialButton(this).apply {
            text = "New flow"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dpi(4); it.bottomMargin = dpi(8) }
            setOnClickListener { newFlow() }
        })

        val flows = repo.flows()
        if (flows.isEmpty()) {
            content.addView(textCard("No flows yet",
                "A good first flow has one reliable anchor and no more than three new links."))
            return
        }

        for (f in flows) {
            val steps = repo.flowSteps(f.id)
            val newLinks = steps.count { !it.existingBehaviour }
            val card = layoutInflater.inflate(R.layout.item_text_card, content, false)
            card.findViewById<TextView>(R.id.text_title).text = f.title
            card.findViewById<TextView>(R.id.text_body).text = buildString {
                if (f.anchor.isNotBlank()) append("Anchor: ${f.anchor}\n\n")
                if (steps.isEmpty()) append("No steps yet.")
                steps.forEachIndexed { i, s ->
                    append("${i + 1}. ${s.title}")
                    if (s.existingBehaviour) append("  (existing)")
                    if (s.durationMinutes > 0) append("  (~${s.durationMinutes} min)")
                    append('\n')
                }
                if (f.estimatedMinutes > 0) append("\nTotal: ~${f.estimatedMinutes} min")
                if (f.completionCount > 0) append(" · completed ${f.completionCount} times")
                if (newLinks > 3) {
                    append("\n$newLinks new behaviours in one flow. Adding more than about three " +
                            "at once usually weakens all of them.")
                }
            }.trim()
            val holder = card.findViewById<TextView>(R.id.text_title).parent as LinearLayout
            if (steps.isNotEmpty()) {
                holder.addView(MaterialButton(this, null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                    text = "Run flow"
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                    ).also { it.topMargin = dpi(12) }
                    setOnClickListener {
                        exec("run_flow", jsonOf("flowId" to f.id))
                        com.google.android.material.dialog.MaterialAlertDialogBuilder(this@FlowActivity)
                            .setTitle("Running: ${f.title}")
                            .setMessage(repo.flowSteps(f.id).joinToString("\n") { s ->
                                "${if (s.durationMinutes > 0) "⏱ ${s.durationMinutes} min — " else ""}${s.title}"
                            })
                            .setPositiveButton("Mark all done") { _, _ ->
                                exec("complete_flow", jsonOf("flowId" to f.id))
                            }
                            .setNegativeButton(R.string.cancel, null)
                            .show()
                    }
                })
            }
            holder.addView(MaterialButton(this, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = "Add step"
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.topMargin = dpi(12) }
                setOnClickListener {
                    TextInputSheet.show(supportFragmentManager, "Add step",
                        "drink a glass of water") { t ->
                        if (t.isNotBlank()) exec("add_flow_step",
                            jsonOf("flowId" to f.id, "title" to t.trim()))
                    }
                }
            })
            card.setOnLongClickListener {
                com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle("Delete \"${f.title}\"?")
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.delete) { _, _ ->
                        exec("delete_flow", jsonOf("flowId" to f.id))
                    }.show()
                true
            }
            content.addView(card)
        }
    }

    private fun newFlow() {
        TextInputSheet.show(supportFragmentManager, "New flow", "Morning flow",
            subtitle = "Name it first, then add the anchor and steps.") { title ->
            if (title.isBlank()) return@show
            TextInputSheet.show(supportFragmentManager, "Reliable anchor",
                "Waking up") { anchor ->
                TextInputSheet.show(supportFragmentManager, "Steps",
                    "One per line", lines = 4) { stepsText ->
                    val steps = stepsText.lines().map { it.trim() }.filter { it.isNotBlank() }
                    exec("create_flow", jsonOf(
                        "title" to title.trim(), "anchor" to anchor.trim(),
                        "steps" to jsonArrayOf(steps)
                    ))
                }
            }
        }
    }

    private fun dpi(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun exec(command: String, args: org.json.JSONObject) {
        runCommand(bus, command, args) { res ->
            if (!res.ok) findViewById<View>(R.id.root).snack(res.message)
            rebuild()
        }
    }
}
