package com.superflow.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.superflow.data.CheckInResult
import com.superflow.data.Habit
import com.superflow.data.HabitMode
import com.superflow.data.Level
import com.superflow.domain.Actor
import com.superflow.domain.Capabilities
import com.superflow.domain.CommandBus
import com.superflow.domain.Insights
import com.superflow.util.Dates
import com.superflow.util.jsonOf

/** One habit's full design, history, ladder and obstacle plans. */
class HabitDetailActivity : Activity() {

    private lateinit var bus: CommandBus
    private lateinit var host: FrameLayout
    private var habitId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bus = CommandBus.get(this)
        habitId = intent.getStringExtra("habitId") ?: ""
        host = FrameLayout(this).apply {
            setBackgroundColor(Palette.BG)
            layoutParams = lp(MATCH, MATCH)
        }
        setContentView(host)
        render()
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val h = bus.repo.habit(habitId)
        host.removeAllViews()
        if (h == null) {
            host.addView(scroller {
                setPadding(dp(20), dp(40), dp(20), dp(20))
                addView(title("Habit not found"))
                addView(primaryButton("Close") { finish() })
            })
            return
        }
        host.addView(build(h), FrameLayout.LayoutParams(MATCH, MATCH))
    }

    private fun build(h: Habit): View {
        val stats = Insights.forHabit(bus.repo, h)
        return scroller {
            setPadding(dp(20), dp(28), dp(20), dp(28))

            addView(title(h.title, 26f))
            addView(spacer(6))
            addView(body(
                Capabilities.daysLabel(h.daysMask) +
                        (if (h.cueTime.isNotBlank()) " · ${h.cueTime}" else "") +
                        (if (h.mode == HabitMode.REDUCE) " · reduce mode" else ""),
                14f, Palette.INK_FAINT
            ))

            addView(spacer(16))
            addView(softCard(Palette.ACCENT_SOFT) {
                addView(body("YOUR CONTRACT", 11f, Palette.ACCENT, bold = true))
                addView(spacer(6))
                addView(body(h.contract(), 15f, Palette.INK))
            })

            addView(heading("TODAY"))
            addView(card {
                val ci = bus.repo.checkIn(h.id, Dates.today())
                if (ci == null) {
                    addView(body("Not recorded yet.", 14f, Palette.INK_SOFT))
                    addView(spacer(10))
                    addView(flowRow {
                        for (l in Level.values()) {
                            addView(chip(l.label) {
                                exec("check_in", jsonOf("habit" to h.id, "level" to l.name))
                            })
                        }
                        addView(chip("Skip") { exec("skip_habit", jsonOf("habit" to h.id)) })
                        addView(chip("Missed") { exec("mark_missed", jsonOf("habit" to h.id)) })
                    })
                } else {
                    addView(body(
                        when (ci.result) {
                            CheckInResult.DONE, CheckInResult.RESISTED -> "Done at ${ci.level.label}"
                            CheckInResult.SKIPPED -> "Intentionally skipped"
                            else -> "Missed - and that is recoverable"
                        }, 15f, Palette.INK, bold = true
                    ))
                    addView(spacer(10))
                    addView(ghostButton("Clear today") {
                        exec("clear_check_in", jsonOf("habit" to h.id))
                    })
                }
            })

            addView(heading("PROGRESS"))
            addView(card {
                addView(row {
                    addView(stat("Reps", stats.repetitions.toString()))
                    addView(stat("Run", stats.currentRun.toString()))
                    addView(stat("Best", stats.bestRun.toString()))
                    addView(stat("30d", "${stats.consistency30}%"))
                })
                addView(spacer(12))
                addView(progressBar(stats.consistency30 / 100f))
                addView(spacer(10))
                addView(body(
                    if (stats.recoveries > 0)
                        "You have returned after a miss ${stats.recoveries} times. That is the skill that matters."
                    else "Consistency is measured only over the days this habit is scheduled.",
                    13f, Palette.INK_FAINT
                ))
            })

            addView(heading("LAST 14 DAYS"))
            addView(card { addView(historyStrip(h)) })

            addView(heading("THE LADDER"))
            addView(card {
                for (l in Level.values()) {
                    addView(row {
                        layoutParams = lp(MATCH, WRAP).apply { bottomMargin = dp(8) }
                        addView(body(l.label, 13f, Palette.ACCENT, bold = true).apply {
                            layoutParams = lp(dp(80), WRAP)
                        })
                        addView(body(h.levelText(l), 14f, Palette.INK_SOFT).apply {
                            layoutParams = lp(0, WRAP, 1f)
                        })
                    })
                }
            })

            addView(heading("THE DESIGN"))
            addView(card {
                addDesign("Cue", listOfNotNull(
                    h.cueTime.ifBlank { null }, h.cuePlace.ifBlank { null },
                    h.anchorText.ifBlank { null }?.let { "after $it" }
                ).joinToString(" · "))
                addDesign("Benefit", h.benefit)
                addDesign("Paired with", h.temptationBundle)
                addDesign("Reframe", h.reframe)
                addDesign("Friction", h.frictionPlan)
                addDesign("Preparation", h.environmentPrep)
                addDesign("Reward", h.reward)
                addDesign("Recovery", h.recoveryPlan)
            })

            addView(heading("OBSTACLE PLANS"))
            val obstacles = bus.repo.obstacles(h.id)
            if (obstacles.isEmpty()) {
                addView(card {
                    addView(body("No if-then plans yet.", 14f, Palette.INK_SOFT))
                    addView(spacer(6))
                    addView(body("Deciding in advance is what makes a plan survive a bad day.",
                        13f, Palette.INK_FAINT))
                })
            }
            for (o in obstacles) {
                addView(card {
                    addView(body("If ${o.ifText}", 14f, Palette.INK, bold = true))
                    addView(spacer(4))
                    addView(body("then ${o.thenText}", 14f, Palette.INK_SOFT))
                    addView(spacer(8))
                    addView(ghostButton("Remove", Palette.DANGER) {
                        exec("delete_obstacle_plan", jsonOf("id" to o.id))
                    })
                })
            }
            addView(ghostButton("+ Add obstacle plan") { addObstacle(h) })

            addView(heading("MANAGE"))
            addView(row {
                addView(ghostButton("Edit design") {
                    startActivity(Intent(this@HabitDetailActivity, HabitDesignerActivity::class.java)
                        .putExtra("habitId", h.id))
                }.apply { layoutParams = lp(0, WRAP, 1f).apply { rightMargin = dp(8) } })
                addView(ghostButton("Archive") {
                    exec("archive_habit", jsonOf("habit" to h.id)); finish()
                }.apply { layoutParams = lp(0, WRAP, 1f) })
            })
            addView(spacer(8))
            addView(ghostButton("Delete habit", Palette.DANGER) {
                Dialogs.confirm(this@HabitDetailActivity,
                    "Delete \"${h.title}\" and its history? You can undo this from Activity.") {
                    exec("delete_habit", jsonOf("habit" to h.id))
                    finish()
                }
            })
            addView(spacer(8))
            addView(ghostButton("Close") { finish() })
            addView(spacer(24))
        }
    }

    private fun LinearLayout.addDesign(label: String, value: String) {
        if (value.isBlank()) return
        addView(row {
            layoutParams = lp(MATCH, WRAP).apply { bottomMargin = dp(8) }
            addView(body(label, 12f, Palette.INK_FAINT).apply { layoutParams = lp(dp(90), WRAP) })
            addView(body(value, 14f, Palette.INK_SOFT).apply { layoutParams = lp(0, WRAP, 1f) })
        })
    }

    private fun stat(label: String, value: String): View = column {
        layoutParams = lp(0, WRAP, 1f)
        addView(body(value, 20f, Palette.ACCENT, bold = true))
        addView(body(label, 12f, Palette.INK_FAINT))
    }

    private fun historyStrip(h: Habit): View = row {
        for (d in Dates.lastDays(14)) {
            val ci = bus.repo.checkIn(h.id, d)
            val scheduled = h.runsOn(Dates.isoDayOfWeek(d))
            val color = when {
                ci == null && !scheduled -> Palette.SURFACE_ALT
                ci == null -> Palette.LINE
                ci.result == CheckInResult.DONE || ci.result == CheckInResult.RESISTED -> Palette.ACCENT
                ci.result == CheckInResult.SKIPPED -> Palette.ACCENT_SOFT
                else -> Palette.WARM_SOFT
            }
            addView(column {
                layoutParams = lp(0, WRAP, 1f)
                addView(View(this@HabitDetailActivity).apply {
                    background = rounded(color, dp(6))
                    layoutParams = lp(MATCH, dp(34)).apply { rightMargin = dp(3) }
                })
                addView(body(Dates.dayLetter(d), 10f, Palette.INK_FAINT).apply {
                    gravity = android.view.Gravity.CENTER
                })
            })
        }
    }

    private fun addObstacle(h: Habit) {
        val ifField = field("If it rains")
        val thenField = field("then I stretch indoors for five minutes")
        val body = column(0) {
            addView(label("If this happens")); addView(ifField)
            addView(label("Then I will")); addView(thenField)
        }
        Dialogs.form(this, "Obstacle plan", body) {
            val i = ifField.text.toString().trim()
            val t = thenField.text.toString().trim()
            if (i.isBlank() || t.isBlank()) {
                toast("Fill in both halves"); return@form false
            }
            exec("add_obstacle_plan", jsonOf("habit" to h.id, "ifText" to i, "thenText" to t))
            true
        }
    }

    private fun exec(command: String, args: org.json.JSONObject) {
        val res = bus.execute(command, args, Actor.USER)
        if (!res.ok) toast(res.message)
        render()
    }
}
