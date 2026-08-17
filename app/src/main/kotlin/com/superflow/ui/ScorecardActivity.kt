package com.superflow.ui

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import com.superflow.domain.Actor
import com.superflow.domain.CommandBus
import com.superflow.util.jsonOf

/**
 * The Habit Scorecard.
 *
 * Awareness precedes change. This is a nonjudgmental inventory of what you
 * already do - helpful, neutral or unhelpful - with no scoring and no shame.
 */
class ScorecardActivity : Activity() {

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

        addView(title("Habit Scorecard", 26f))
        addView(spacer(6))
        addView(body("List what you already do on a normal day, then mark each one. " +
                "Noticing is the whole exercise - nothing here needs fixing today.",
            14f, Palette.INK_FAINT))

        addView(heading("ADD A ROUTINE"))
        addView(card {
            val routine = field("Check my phone in bed")
            addView(routine)
            addView(row {
                for ((label, verdict, color) in listOf(
                    Triple("Helpful", 1, Palette.ACCENT),
                    Triple("Neutral", 0, Palette.INK_FAINT),
                    Triple("Unhelpful", -1, Palette.WARM)
                )) {
                    addView(ghostButton(label, color) {
                        val text = routine.text.toString().trim()
                        if (text.isBlank()) toast("Describe the routine first")
                        else {
                            bus.execute("add_scorecard_entry",
                                jsonOf("routine" to text, "verdict" to verdict), Actor.USER)
                            render()
                        }
                    }.apply { layoutParams = lp(0, WRAP, 1f).apply { rightMargin = dp(6) } })
                }
            })
        })

        val entries = bus.repo.scorecard()
        addView(heading("YOUR DAY"))
        if (entries.isEmpty()) {
            addView(card {
                addView(body("Nothing recorded yet.", 14f, Palette.INK_SOFT))
                addView(spacer(4))
                addView(body("Try walking through a typical morning: waking, phone, coffee, " +
                        "commute. Aim for honesty, not completeness.", 13f, Palette.INK_FAINT))
            })
        }

        for (group in listOf(1, 0, -1)) {
            val list = entries.filter { it.verdict == group }
            if (list.isEmpty()) continue
            addView(body(
                when (group) { 1 -> "HELPFUL"; 0 -> "NEUTRAL"; else -> "UNHELPFUL" },
                11f,
                when (group) { 1 -> Palette.ACCENT; 0 -> Palette.INK_FAINT; else -> Palette.WARM },
                bold = true
            ).apply { (layoutParams as? android.widget.LinearLayout.LayoutParams)?.topMargin = dp(14) })
            addView(spacer(6))
            for (e in list) {
                addView(card {
                    addView(row {
                        addView(iconDot(when (e.verdict) {
                            1 -> Palette.ACCENT
                            0 -> Palette.INK_FAINT
                            else -> Palette.WARM
                        }, 8))
                        addView(body(e.routine, 15f, Palette.INK).apply {
                            layoutParams = lp(0, WRAP, 1f)
                        })
                        addView(ghostButton("Remove", Palette.DANGER) {
                            bus.execute("delete_scorecard_entry", jsonOf("id" to e.id), Actor.USER)
                            render()
                        })
                    })
                    if (e.verdict == -1) {
                        addView(spacer(8))
                        addView(body("If you want to change this one, start by removing its cue " +
                                "rather than relying on willpower.", 12f, Palette.INK_FAINT))
                    }
                })
            }
        }

        addView(spacer(12))
        addView(ghostButton("Close") { finish() })
        addView(spacer(24))
    }
}
