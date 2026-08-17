package com.superflow.ui

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import com.superflow.data.HabitMode
import com.superflow.domain.Actor
import com.superflow.domain.CommandBus
import com.superflow.domain.Insights
import com.superflow.util.Dates
import com.superflow.util.jsonOf

/**
 * The Recovery Center.
 *
 * Missing once is normal. The only thing that needs protecting is the second
 * miss. Nothing here scolds, ranks or scores the user.
 */
class RecoveryActivity : Activity() {

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

        addView(title("Recovery", 26f))
        addView(spacer(6))
        addView(body("A missed day is information about the design, not a verdict about you.",
            14f, Palette.INK_FAINT))

        addView(spacer(16))
        addView(softCard(Palette.ACCENT_SOFT) {
            addView(body("THE ONE RULE", 11f, Palette.ACCENT, bold = true))
            addView(spacer(6))
            addView(body("Never miss twice. The first miss is an accident. The second is the " +
                    "start of a new pattern - and that is the one worth interrupting.",
                15f, Palette.INK))
        })

        /* -------------------------------------------------- return today */
        val returning = Insights.returnCards(bus.repo, Dates.today())
        addView(heading("RETURN TODAY"))
        if (returning.isEmpty()) {
            addView(card {
                addView(body("Nothing needs rescuing right now.", 15f, Palette.INK))
                addView(spacer(4))
                addView(body("If a habit slips, it will appear here with its smallest version.",
                    13f, Palette.INK_FAINT))
            })
        }
        for (h in returning) {
            addView(card {
                addView(body(h.title, 16f, Palette.INK, bold = true))
                addView(spacer(6))
                addView(body("Smallest way back: ${h.tinyStart.ifBlank { "start for two minutes" }}",
                    14f, Palette.ACCENT))
                if (h.recoveryPlan.isNotBlank()) {
                    addView(spacer(4))
                    addView(body(h.recoveryPlan, 13f, Palette.INK_SOFT))
                }
                addView(spacer(10))
                addView(row {
                    addView(ghostButton("Do the tiny version") {
                        bus.execute("check_in", jsonOf("habit" to h.id, "level" to "TINY"), Actor.USER)
                        toast("You are back. That is the whole win.")
                        render()
                    }.apply { layoutParams = lp(0, WRAP, 1f).apply { rightMargin = dp(8) } })
                    addView(ghostButton("Not today") {
                        bus.execute("skip_habit", jsonOf("habit" to h.id), Actor.USER)
                        render()
                    }.apply { layoutParams = lp(0, WRAP, 1f) })
                })
            })
        }

        /* ------------------------------------------------ low capacity */
        addView(heading("LOW-CAPACITY DAY"))
        addView(card {
            addView(body("Some days there is genuinely less to give.", 15f, Palette.INK))
            addView(spacer(6))
            addView(body("Minimum Mode drops every non-protected habit to its Minimum version " +
                    "for today. Protected routines are left alone.", 13f, Palette.INK_FAINT))
            addView(spacer(12))
            addView(primaryButton("Enter Minimum Mode") {
                val res = bus.execute("enter_minimum_mode", jsonOf(), Actor.USER)
                toast(res.message)
                render()
            })
        })

        /* -------------------------------------------- struggling habits */
        val struggling = Insights.allStats(bus.repo).filter { it.missesInARow >= 2 }
        if (struggling.isNotEmpty()) {
            addView(heading("REDESIGN CANDIDATES"))
            for (s in struggling) {
                addView(card {
                    addView(body(s.habit.title, 15f, Palette.INK, bold = true))
                    addView(spacer(4))
                    addView(body("${s.missesInARow} missed in a row · ${s.consistency30}% over 30 days",
                        12f, Palette.INK_FAINT))
                    addView(spacer(8))
                    addView(body("A habit that keeps slipping is usually too big, badly timed, or " +
                            "missing a clear cue. Try shrinking it to the tiny version for a week.",
                        13f, Palette.INK_SOFT))
                    addView(spacer(10))
                    addView(row {
                        addView(ghostButton("Shrink to tiny") {
                            val tiny = s.habit.tinyStart.ifBlank { "Start for two minutes" }
                            bus.execute("update_habit", jsonOf(
                                "habit" to s.habit.id, "field" to "standardVersion", "value" to tiny
                            ), Actor.USER)
                            toast("Standard version is now the tiny one")
                            render()
                        }.apply { layoutParams = lp(0, WRAP, 1f).apply { rightMargin = dp(8) } })
                        addView(ghostButton("Recovery plan") {
                            val res = bus.execute("start_recovery",
                                jsonOf("habit" to s.habit.id), Actor.USER)
                            Dialogs.info(this@RecoveryActivity, "Recovery plan", res.message)
                            render()
                        }.apply { layoutParams = lp(0, WRAP, 1f) })
                    })
                })
            }
        }

        /* ----------------------------------------------- reduce mode slip */
        val reduce = bus.repo.habits().filter { it.mode == HabitMode.REDUCE }
        if (reduce.isNotEmpty()) {
            addView(heading("AFTER A SLIP"))
            addView(card {
                addView(body("Returning to an unwanted behaviour is part of most real attempts. " +
                        "What matters is what happens in the next hour, not the next month.",
                    14f, Palette.INK_SOFT))
                addView(spacer(10))
                for (h in reduce) {
                    addView(row {
                        layoutParams = lp(MATCH, WRAP).apply { bottomMargin = dp(8) }
                        addView(body(h.title, 14f, Palette.INK).apply { layoutParams = lp(0, WRAP, 1f) })
                        addView(ghostButton("Resisted") {
                            bus.execute("check_in", jsonOf("habit" to h.id, "level" to "STANDARD"),
                                Actor.USER)
                            render()
                        })
                    })
                }
                addView(spacer(6))
                addView(body("If this involves dependence, self-harm or anything unsafe, please " +
                        "talk to a qualified person. SuperFlow is not a substitute for care.",
                    12f, Palette.WARM))
            })
        }

        addView(spacer(12))
        addView(ghostButton("Close") { finish() })
        addView(spacer(24))
    }
}
