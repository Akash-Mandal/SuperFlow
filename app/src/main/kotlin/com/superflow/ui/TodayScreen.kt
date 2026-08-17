package com.superflow.ui

import android.content.Intent
import android.graphics.Typeface
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.superflow.data.*
import com.superflow.domain.Actor
import com.superflow.domain.Insights
import com.superflow.util.Dates
import com.superflow.util.jsonOf
import org.json.JSONArray

/**
 * Today: Daily Focus, Do Now, the timeline, checkpoints and recovery.
 *
 * A miss never produces red failure theatrics. If the previous opportunity was
 * missed, a "Return today" card sits at the top instead.
 */
class TodayScreen(private val a: MainActivity) : Screen {

    private var date = Dates.today()

    override fun build(): View = a.scroller {
        setPadding(a.dp(20), a.dp(24), a.dp(20), a.dp(28))

        addView(header())
        addView(returnCard())
        addView(progressCard())
        addView(focusCard())
        addView(checkpointCard())
        addView(a.heading("TODAY'S FLOW"))
        addView(timeline())
        addView(quickActions())
        addView(a.spacer(24))
    }

    /* -------------------------------------------------------------- header */

    private fun header(): View = a.column {
        val greet = when (Dates.minutesOfDay(Dates.nowTime())) {
            in 0..(11 * 60 + 59) -> "Good morning"
            in (12 * 60)..(16 * 60 + 59) -> "Good afternoon"
            else -> "Good evening"
        }
        addView(a.body(greet, 14f, Palette.INK_FAINT))
        addView(a.title(Dates.humanDay(date), 25f))

        val identity = a.repo.identities().firstOrNull()
        if (identity != null) {
            addView(a.spacer(12))
            addView(a.softCard(Palette.ACCENT_SOFT) {
                addView(a.body("YOU ARE BECOMING", 11f, Palette.ACCENT, bold = true))
                addView(a.spacer(4))
                addView(a.body(identity.statement, 16f, Palette.INK, bold = true))
            })
        }
    }

    /* ------------------------------------------------- never miss twice */

    private fun returnCard(): View {
        val returning = Insights.returnCards(a.repo, date)
        if (returning.isEmpty()) return a.spacer(0)
        return a.softCard(Palette.WARM_SOFT) {
            addView(a.body("RETURN TODAY", 11f, Palette.WARM, bold = true))
            addView(a.spacer(4))
            addView(a.body("Yesterday slipped by. That happens. The rule is simply: never miss twice.",
                14f, Palette.INK))
            addView(a.spacer(10))
            for (h in returning.take(3)) {
                val tiny = h.tinyStart.ifBlank { h.title }
                addView(a.row {
                    layoutParams = lp(MATCH, WRAP).apply { bottomMargin = a.dp(8) }
                    addView(a.body("$tiny", 14f, Palette.INK, bold = true).apply {
                        layoutParams = lp(0, WRAP, 1f)
                    })
                    addView(a.ghostButton("Do it", Palette.WARM) {
                        run("check_in", jsonOf("habit" to h.id, "level" to "TINY"))
                    })
                })
            }
        }
    }

    /* ------------------------------------------------------------ progress */

    private fun progressCard(): View {
        val (done, total) = Insights.dayProgress(a.repo, date)
        return a.card {
            addView(a.row {
                addView(a.body(if (total == 0) "Nothing scheduled" else "$done of $total actions",
                    17f, Palette.INK, bold = true).apply { layoutParams = lp(0, WRAP, 1f) })
                if (total > 0) {
                    addView(a.body("${if (total == 0) 0 else done * 100 / total}%", 15f, Palette.ACCENT, bold = true))
                }
            })
            if (total > 0) {
                addView(a.progressBar(done.toFloat() / total))
                addView(a.spacer(8))
                addView(a.body(
                    when {
                        done == 0 -> "Start with the smallest version. Showing up is the win."
                        done < total -> "Momentum is real. One more when you are ready."
                        else -> "Every action today was a vote for who you are becoming."
                    }, 13f, Palette.INK_FAINT
                ))
            } else {
                addView(a.spacer(6))
                addView(a.body("A quiet day is allowed. Add a habit whenever you are ready.",
                    13f, Palette.INK_FAINT))
            }
        }
    }

    /* -------------------------------------------------------- daily focus */

    private fun focusCard(): View {
        val focus = a.repo.focusFor(date)
        return a.card {
            addView(a.row {
                addView(a.body("Daily Focus", 16f, Palette.INK, bold = true).apply {
                    layoutParams = lp(0, WRAP, 1f)
                })
                addView(a.body("${focus.size}/3", 13f, Palette.INK_FAINT))
            })
            addView(a.spacer(4))
            addView(a.body("Up to three actions that deserve emphasis today.", 13f, Palette.INK_FAINT))
            addView(a.spacer(10))

            if (focus.isEmpty()) {
                addView(a.body("Nothing chosen yet.", 14f, Palette.INK_FAINT))
            } else {
                for (f in focus) {
                    addView(a.row {
                        layoutParams = lp(MATCH, WRAP).apply { bottomMargin = a.dp(10) }
                        val box = TextView(a).apply {
                            text = if (f.done) "✓" else ""
                            gravity = Gravity.CENTER
                            setTextColor(if (f.done) 0xFFFFFFFF.toInt() else Palette.INK_FAINT)
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                            background = if (f.done) rounded(Palette.ACCENT, a.dp(7))
                            else rounded(Palette.SURFACE, a.dp(7), Palette.LINE, a.dp(2))
                            layoutParams = lp(a.dp(26), a.dp(26)).apply { rightMargin = a.dp(12) }
                            isClickable = true
                            setOnClickListener {
                                run("complete_focus_item", jsonOf("id" to f.id, "done" to !f.done))
                            }
                        }
                        addView(box)
                        addView(a.body(f.title, 15f, if (f.done) Palette.INK_FAINT else Palette.INK).apply {
                            layoutParams = lp(0, WRAP, 1f)
                            if (f.done) paintFlags = paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
                        })
                        addView(TextView(a).apply {
                            text = "✕"
                            setTextColor(Palette.INK_FAINT)
                            setPadding(a.dp(10), 0, 0, 0)
                            isClickable = true
                            setOnClickListener { run("delete_focus_item_ui", jsonOf("id" to f.id)) }
                        })
                    })
                }
            }

            if (focus.size < 3) {
                val input = a.field("Add a focus action")
                addView(input)
                addView(a.row {
                    addView(a.ghostButton("Add") {
                        val text = input.text.toString().trim()
                        if (text.isEmpty()) a.toast("Type an action first")
                        else run("add_focus_item", jsonOf("title" to text, "date" to date))
                    }.apply { layoutParams = lp(0, WRAP, 1f).apply { rightMargin = a.dp(8) } })
                    addView(a.ghostButton("Suggest from habits") { suggestFocus() }
                        .apply { layoutParams = lp(0, WRAP, 1f) })
                })
            }
        }
    }

    private fun suggestFocus() {
        val existing = a.repo.focusFor(date).map { it.title }
        val candidates = a.repo.habitsForDay(date)
            .filter { a.repo.checkIn(it.id, date) == null && it.title !in existing }
            .sortedWith(compareByDescending<Habit> { it.protectedRoutine }.thenBy { it.orderIndex })
            .take(3 - existing.size)
        if (candidates.isEmpty()) {
            a.toast("Nothing left to suggest")
            return
        }
        val items = JSONArray()
        existing.forEach { items.put(it) }
        candidates.forEach { items.put(it.title) }
        run("set_daily_focus", jsonOf("items" to items, "date" to date))
    }

    /* --------------------------------------------------------- checkpoints */

    private fun checkpointCard(): View {
        if (!a.prefs.checkpointsEnabled) return a.spacer(0)
        return a.card {
            addView(a.body("Checkpoints", 16f, Palette.INK, bold = true))
            addView(a.spacer(4))
            addView(a.body("A brief pause to steer the day.", 13f, Palette.INK_FAINT))
            addView(a.spacer(10))
            addView(a.row {
                for (cp in Checkpoint.values()) {
                    addView(a.ghostButton(cp.label) {
                        val res = a.bus.execute("run_checkpoint", jsonOf("checkpoint" to cp.name), Actor.USER)
                        a.toast(res.message)
                    }.apply { layoutParams = lp(0, WRAP, 1f).apply { rightMargin = a.dp(8) } })
                }
            })
            if (a.prefs.energyTracking) {
                addView(a.spacer(12))
                addView(a.body("Energy right now", 13f, Palette.INK_SOFT, bold = true))
                addView(a.spacer(6))
                addView(a.row {
                    val cp = when (Dates.minutesOfDay(Dates.nowTime())) {
                        in 0..(11 * 60 + 59) -> Checkpoint.MORNING
                        in (12 * 60)..(16 * 60 + 59) -> Checkpoint.MIDDAY
                        else -> Checkpoint.EVENING
                    }
                    val logged = a.repo.energyLogs().firstOrNull { it.date == date && it.checkpoint == cp }
                    for (n in 1..5) {
                        addView(a.chip(n.toString(), active = logged?.energy == n) {
                            run("log_energy", jsonOf("energy" to n, "checkpoint" to cp.name, "date" to date))
                        }.apply { layoutParams = lp(0, WRAP, 1f).apply { rightMargin = a.dp(6) } })
                    }
                })
            }
        }
    }

    /* ------------------------------------------------------------ timeline */

    private fun timeline(): View {
        val habits = a.repo.habitsForDay(date)
        if (habits.isEmpty()) {
            return a.card {
                addView(a.body("No habits scheduled for today.", 15f, Palette.INK))
                addView(a.spacer(6))
                addView(a.body("Design one in the Journey tab. Start absurdly small.",
                    13f, Palette.INK_FAINT))
                addView(a.primaryButton("Design a habit") {
                    a.startActivity(Intent(a, HabitDesignerActivity::class.java))
                })
            }
        }
        val container = a.column()
        val buckets = habits.groupBy { Dates.bucketOf(it.cueTime) }
        for (key in listOf("Morning", "Day", "Evening", "Anytime")) {
            val list = buckets[key] ?: continue
            container.addView(a.body(key.uppercase(), 11f, Palette.INK_FAINT, bold = true).apply {
                layoutParams = lp(MATCH, WRAP).apply { topMargin = a.dp(6); bottomMargin = a.dp(8) }
            })
            for (h in list) container.addView(habitCard(h))
        }
        return container
    }

    private fun habitCard(h: Habit): View {
        val ci = a.repo.checkIn(h.id, date)
        val done = ci != null && (ci.result == CheckInResult.DONE || ci.result == CheckInResult.RESISTED)
        val skipped = ci?.result == CheckInResult.SKIPPED
        val missed = ci?.result == CheckInResult.MISSED || ci?.result == CheckInResult.SLIPPED

        return a.card {
            if (done) background = rounded(Palette.ACCENT_SOFT, a.dp(18))
            addView(a.row {
                if (h.mode == HabitMode.REDUCE) addView(a.iconDot(Palette.WARM))
                addView(a.body(h.title, 16f, Palette.INK, bold = true).apply {
                    layoutParams = lp(0, WRAP, 1f)
                    isClickable = true
                    setOnClickListener {
                        a.startActivity(Intent(a, HabitDetailActivity::class.java)
                            .putExtra("habitId", h.id))
                    }
                })
                val statusText = when {
                    done -> ci!!.level.label
                    skipped -> "Skipped"
                    missed -> "Missed"
                    else -> ""
                }
                if (statusText.isNotEmpty()) {
                    addView(a.body(statusText, 12f,
                        if (done) Palette.ACCENT else Palette.INK_FAINT, bold = true))
                }
            })

            val cue = buildString {
                if (h.anchorText.isNotBlank()) append("After ${h.anchorText}")
                else {
                    if (h.cueTime.isNotBlank()) append(h.cueTime)
                    if (h.cuePlace.isNotBlank()) {
                        if (isNotEmpty()) append(" · ")
                        append(h.cuePlace)
                    }
                }
            }
            if (cue.isNotBlank()) {
                addView(a.spacer(4))
                addView(a.body(cue, 13f, Palette.INK_FAINT))
            }
            if (h.tinyStart.isNotBlank() && !done) {
                addView(a.spacer(6))
                addView(a.body("Tiny start: ${h.tinyStart}", 13f, Palette.ACCENT))
            }

            if (!done && !skipped) {
                addView(a.spacer(12))
                addView(a.flowRow {
                    addView(a.chip("Standard", activeColor = Palette.ACCENT) {
                        run("check_in", jsonOf("habit" to h.id, "level" to "STANDARD"))
                    })
                    addView(a.chip("Minimum") { run("check_in", jsonOf("habit" to h.id, "level" to "MINIMUM")) })
                    addView(a.chip("Tiny") { run("check_in", jsonOf("habit" to h.id, "level" to "TINY")) })
                    addView(a.chip("Stretch") { run("check_in", jsonOf("habit" to h.id, "level" to "STRETCH")) })
                    addView(a.chip("Skip") { run("skip_habit", jsonOf("habit" to h.id)) })
                    if (!missed) addView(a.chip("Missed") { run("mark_missed", jsonOf("habit" to h.id)) })
                })
            } else {
                addView(a.spacer(10))
                addView(a.row {
                    addView(a.ghostButton("Undo") {
                        run("clear_check_in", jsonOf("habit" to h.id, "date" to date))
                    })
                    if (done && h.reward.isNotBlank()) {
                        addView(a.body("  ${h.reward}", 13f, Palette.INK_FAINT).apply {
                            layoutParams = lp(0, WRAP, 1f).apply { leftMargin = a.dp(10) }
                        })
                    }
                })
            }
        }
    }

    /* ------------------------------------------------------- quick actions */

    private fun quickActions(): View = a.column {
        addView(a.heading("QUICK ACTIONS"))
        addView(a.row {
            addView(a.ghostButton("Plan tomorrow") { run("plan_tomorrow", jsonOf()) }
                .apply { layoutParams = lp(0, WRAP, 1f).apply { rightMargin = a.dp(8) } })
            addView(a.ghostButton("Minimum Mode") {
                run("enter_minimum_mode", jsonOf("date" to date))
            }.apply { layoutParams = lp(0, WRAP, 1f) })
        })
        addView(a.spacer(8))
        addView(a.row {
            addView(a.ghostButton("Recovery") {
                a.startActivity(Intent(a, RecoveryActivity::class.java))
            }.apply { layoutParams = lp(0, WRAP, 1f).apply { rightMargin = a.dp(8) } })
            addView(a.ghostButton("Ask SuperFlow") { a.openAiTab() }
                .apply { layoutParams = lp(0, WRAP, 1f) })
        })
    }

    /* -------------------------------------------------------------- helper */

    private fun run(command: String, args: org.json.JSONObject) {
        if (command == "delete_focus_item_ui") {
            a.repo.deleteFocus(args.optString("id"))
            a.refresh()
            return
        }
        val res = a.bus.execute(command, args, Actor.USER)
        if (!res.ok) a.toast(res.message)
        else if (command in setOf("plan_tomorrow", "enter_minimum_mode", "set_daily_focus")) a.toast(res.message)
    }
}
