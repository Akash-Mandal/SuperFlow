package com.superflow.ui

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import com.superflow.data.ReviewKind
import com.superflow.domain.Actor
import com.superflow.domain.CommandBus
import com.superflow.domain.Insights
import com.superflow.util.Dates
import com.superflow.util.jsonOf

/**
 * The review system.
 *
 * A review is not a report card. It exists to change the system: keep, shrink,
 * expand, reschedule, redesign the environment, or retire.
 */
class ReviewActivity : Activity() {

    private lateinit var bus: CommandBus
    private lateinit var host: FrameLayout
    private var kind = ReviewKind.WEEKLY

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

        addView(title("Review", 26f))
        addView(spacer(6))
        addView(body("Look at the system, not at yourself.", 14f, Palette.INK_FAINT))

        addView(spacer(14))
        addView(flowRow {
            for (k in ReviewKind.values()) {
                addView(chip(k.name.lowercase().replaceFirstChar { it.uppercase() },
                    active = kind == k) { kind = k; render() })
            }
        })

        addView(heading("WHAT THE DATA SAYS"))
        addView(card {
            val days = when (kind) {
                ReviewKind.WEEKLY -> 7
                ReviewKind.MONTHLY -> 30
                ReviewKind.QUARTERLY -> 90
            }
            addView(body(Insights.summaryText(bus.repo, days), 13f, Palette.INK_SOFT))
        })

        addView(heading("SUGGESTIONS"))
        addView(card {
            val stats = Insights.allStats(bus.repo)
            if (stats.isEmpty()) {
                addView(body("Nothing to review yet.", 14f, Palette.INK_SOFT))
            } else {
                val weak = stats.filter { it.consistency30 < 50 }
                val strong = stats.filter { it.consistency30 >= 80 }
                if (weak.isNotEmpty()) {
                    addView(body("Consider shrinking", 14f, Palette.WARM, bold = true))
                    addView(spacer(4))
                    weak.take(3).forEach {
                        addView(body("· ${it.habit.title} at ${it.consistency30}% — try making the " +
                                "standard version match the tiny one for a while.",
                            13f, Palette.INK_SOFT))
                    }
                    addView(spacer(10))
                }
                if (strong.isNotEmpty()) {
                    addView(body("Steady enough to grow", 14f, Palette.ACCENT, bold = true))
                    addView(spacer(4))
                    strong.take(3).forEach {
                        addView(body("· ${it.habit.title} at ${it.consistency30}% — you could add a " +
                                "little, but only if it still feels easy.", 13f, Palette.INK_SOFT))
                    }
                }
                if (weak.isEmpty() && strong.isEmpty()) {
                    addView(body("Everything is in the middle. That is a fine place to be. " +
                            "Change one thing at most.", 13f, Palette.INK_SOFT))
                }
            }
        })

        addView(heading("YOUR REFLECTION"))
        val worked = field("What actually worked?", lines = 3)
        val didnt = field("What got in the way?", lines = 3)
        val change = field("One change to the system", lines = 2)
        val evidence = field("Evidence about who you are becoming", lines = 2)
        addView(card {
            addView(label("What worked")); addView(worked)
            addView(label("What got in the way")); addView(didnt)
            addView(label("One system change")); addView(change)
            addView(label("Identity evidence")); addView(evidence)
            addView(primaryButton("Save review") {
                val res = bus.execute("create_review", jsonOf(
                    "kind" to kind.name,
                    "whatWorked" to worked.text.toString(),
                    "whatDidnt" to didnt.text.toString(),
                    "systemChange" to change.text.toString(),
                    "identityEvidence" to evidence.text.toString()
                ), Actor.USER)
                toast(res.message)
                if (res.ok) render()
            })
        })

        addView(heading("PAST REVIEWS"))
        val past = bus.repo.reviews()
        if (past.isEmpty()) addView(card { addView(body("None yet.", 14f, Palette.INK_SOFT)) })
        for (r in past) {
            addView(card {
                addView(row {
                    addView(body("${r.periodLabel} · ${r.kind.name.lowercase()}",
                        14f, Palette.INK, bold = true).apply { layoutParams = lp(0, WRAP, 1f) })
                    addView(ghostButton("Delete", Palette.DANGER) {
                        bus.execute("delete_review", jsonOf("id" to r.id), Actor.USER)
                        render()
                    })
                })
                if (r.whatWorked.isNotBlank()) {
                    addView(spacer(6)); addView(body("Worked: ${r.whatWorked}", 13f, Palette.INK_SOFT))
                }
                if (r.whatDidnt.isNotBlank()) {
                    addView(spacer(4)); addView(body("In the way: ${r.whatDidnt}", 13f, Palette.INK_SOFT))
                }
                if (r.systemChange.isNotBlank()) {
                    addView(spacer(4)); addView(body("Changed: ${r.systemChange}", 13f, Palette.ACCENT))
                }
                addView(spacer(4))
                addView(body(Dates.stamp(r.createdAt), 11f, Palette.INK_FAINT))
            })
        }

        addView(spacer(12))
        addView(ghostButton("Close") { finish() })
        addView(spacer(24))
    }
}
