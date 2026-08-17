package com.superflow.ui

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import com.superflow.data.Flow
import com.superflow.domain.Actor
import com.superflow.domain.CommandBus
import com.superflow.util.jsonOf
import org.json.JSONArray

/**
 * Flow Builder: chains of anchored habits.
 *
 *   Wake up -> drink water -> open curtains -> one minute of stretching
 *
 * Existing stable behaviour is shown differently from a new behaviour, and the
 * app recommends no more than three new links at once.
 */
class FlowActivity : Activity() {

    private lateinit var bus: CommandBus
    private lateinit var host: FrameLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bus = CommandBus.get(this)
        host = FrameLayout(this).apply {
            setBackgroundColor(Palette.BG)
            layoutParams = lp(MATCH, MATCH)
        }
        setContentView(host)
        render()
    }

    private fun render() {
        host.removeAllViews()
        host.addView(build(), FrameLayout.LayoutParams(MATCH, MATCH))
    }

    private fun build(): View = scroller {
        setPadding(dp(20), dp(28), dp(20), dp(28))

        addView(title("Flows", 26f))
        addView(spacer(6))
        addView(body("Chain small actions around something you already do reliably. " +
                "Each link becomes the cue for the next.", 14f, Palette.INK_FAINT))

        val flows = bus.repo.flows()
        if (flows.isEmpty()) {
            addView(card {
                addView(body("No flows yet.", 15f, Palette.INK, bold = true))
                addView(spacer(6))
                addView(body("A good first flow has one reliable anchor and no more than three " +
                        "new links.", 13f, Palette.INK_FAINT))
            })
        }

        for (f in flows) {
            val steps = bus.repo.flowSteps(f.id)
            val newLinks = steps.count { !it.existingBehaviour }
            addView(card {
                addView(row {
                    addView(column {
                        layoutParams = lp(0, WRAP, 1f)
                        addView(body(f.title, 16f, Palette.INK, bold = true))
                        if (f.anchor.isNotBlank()) {
                            addView(body("Anchor: ${f.anchor}", 12f, Palette.INK_FAINT))
                        }
                    })
                    addView(ghostButton("Delete", Palette.DANGER) {
                        Dialogs.confirm(this@FlowActivity, "Delete \"${f.title}\"?") {
                            bus.execute("delete_flow", jsonOf("flowId" to f.id), Actor.USER)
                            render()
                        }
                    })
                })
                addView(spacer(12))

                if (steps.isEmpty()) {
                    addView(body("No steps yet.", 13f, Palette.INK_FAINT))
                }
                for ((i, s) in steps.withIndex()) {
                    addView(row {
                        layoutParams = lp(MATCH, WRAP).apply { bottomMargin = dp(8) }
                        addView(body("${i + 1}", 12f, Palette.INK_FAINT).apply {
                            layoutParams = lp(dp(24), WRAP)
                        })
                        addView(iconDot(
                            if (s.existingBehaviour) Palette.INK_FAINT else Palette.ACCENT, 8))
                        addView(body(s.title, 15f,
                            if (s.existingBehaviour) Palette.INK_SOFT else Palette.INK,
                            bold = !s.existingBehaviour).apply { layoutParams = lp(0, WRAP, 1f) })
                        if (s.existingBehaviour) {
                            addView(body("existing", 11f, Palette.INK_FAINT))
                        }
                        addView(ghostButton("✕", Palette.DANGER) {
                            bus.repo.deleteFlowStep(s.id)
                            render()
                        })
                    })
                }

                if (newLinks > 3) {
                    addView(spacer(6))
                    addView(body("$newLinks new behaviours in one flow. Adding more than about " +
                            "three at once usually weakens all of them.", 12f, Palette.WARM))
                }

                addView(spacer(8))
                addView(ghostButton("+ Add step") { addStep(f) })
            })
        }

        addView(spacer(8))
        addView(primaryButton("+ New flow") { newFlow() })
        addView(spacer(12))
        addView(ghostButton("Close") { finish() })
        addView(spacer(24))
    }

    private fun newFlow() {
        val title = field("Morning flow")
        val anchor = field("Waking up")
        val steps = field("drink water\nopen the curtains\none minute of stretching", lines = 4)
        val body = column(0) {
            addView(label("Flow name")); addView(title)
            addView(label("Reliable anchor")); addView(anchor)
            addView(label("Steps, one per line")); addView(steps)
        }
        Dialogs.form(this, "New flow", body) {
            val t = title.text.toString().trim()
            if (t.isBlank()) { toast("Name the flow"); return@form false }
            val arr = JSONArray()
            steps.text.toString().lines().map { it.trim() }.filter { it.isNotBlank() }
                .forEach { arr.put(it) }
            bus.execute("create_flow", jsonOf(
                "title" to t, "anchor" to anchor.text.toString().trim(), "steps" to arr
            ), Actor.USER)
            render()
            true
        }
    }

    private fun addStep(f: Flow) {
        val title = field("drink a glass of water")
        var existing = false
        val toggleRow = row {}
        fun paint() {
            toggleRow.removeAllViews()
            toggleRow.addView(body("Already a stable behaviour", 14f, Palette.INK).apply {
                layoutParams = lp(0, WRAP, 1f)
            })
            toggleRow.addView(chip(if (existing) "Yes" else "No", active = existing) {
                existing = !existing; paint()
            })
        }
        paint()
        val body = column(0) {
            addView(label("Step")); addView(title)
            addView(toggleRow)
        }
        Dialogs.form(this, "Add step", body) {
            val t = title.text.toString().trim()
            if (t.isBlank()) { toast("Describe the step"); return@form false }
            bus.execute("add_flow_step",
                jsonOf("flowId" to f.id, "title" to t, "existing" to existing), Actor.USER)
            render()
            true
        }
    }
}
