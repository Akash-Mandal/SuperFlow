package com.superflow.ui

import android.content.Intent
import android.view.View
import android.widget.LinearLayout
import com.superflow.data.*
import com.superflow.domain.Actor
import com.superflow.domain.Capabilities
import com.superflow.domain.Insights
import com.superflow.util.jsonOf

/**
 * Journey: identities, goals, systems, habits, obstacle plans, flows and
 * the supporting design tools. This is where the hierarchy is visible.
 */
class JourneyScreen(private val a: MainActivity) : Screen {

    override fun build(): View = a.scroller {
        setPadding(a.dp(20), a.dp(24), a.dp(20), a.dp(28))

        addView(a.title("Journey"))
        addView(a.spacer(4))
        addView(a.body("Identity shapes the goal. The goal needs a system. The system runs on habits.",
            14f, Palette.INK_FAINT))

        addView(a.spacer(16))
        addView(a.row {
            addView(a.primaryButton("Design a habit") {
                a.startActivity(Intent(a, HabitDesignerActivity::class.java))
            }.apply { layoutParams = lp(0, WRAP, 1f).apply { rightMargin = a.dp(8) } })
            addView(a.ghostButton("Ask AI to build") { a.openAiTab("Build me a morning routine") }
                .apply { layoutParams = lp(0, WRAP, 1f) })
        })

        addView(toolsRow())
        addView(identitiesSection())
        addView(goalsSection())
        addView(systemsSection())
        addView(habitsSection())
        addView(a.spacer(24))
    }

    private fun toolsRow(): View = a.column {
        addView(a.heading("DESIGN TOOLS"))
        addView(a.row {
            addView(a.ghostButton("Scorecard") {
                a.startActivity(Intent(a, ScorecardActivity::class.java))
            }.apply { layoutParams = lp(0, WRAP, 1f).apply { rightMargin = a.dp(8) } })
            addView(a.ghostButton("Flows") {
                a.startActivity(Intent(a, FlowActivity::class.java))
            }.apply { layoutParams = lp(0, WRAP, 1f).apply { rightMargin = a.dp(8) } })
            addView(a.ghostButton("Reviews") {
                a.startActivity(Intent(a, ReviewActivity::class.java))
            }.apply { layoutParams = lp(0, WRAP, 1f) })
        })
    }

    /* ---------------------------------------------------------- identities */

    private fun identitiesSection(): View = a.column {
        addView(a.heading("IDENTITIES"))
        val list = a.repo.identities()
        if (list.isEmpty()) {
            addView(emptyCard("Who are you becoming?",
                "An identity statement gives every habit a reason to exist."))
        }
        for (i in list) {
            val evidence = Insights.identityEvidence(a.repo).firstOrNull { it.first == i.statement }
            addView(a.card {
                addView(a.body(i.statement, 16f, Palette.INK, bold = true))
                addView(a.spacer(4))
                addView(a.body("${i.lifeArea.label} · ${evidence?.second ?: 0} votes · " +
                        "${evidence?.third ?: 0} habits", 13f, Palette.INK_FAINT))
                addView(a.spacer(10))
                addView(a.row {
                    addView(a.ghostButton("Edit") { editIdentity(i) }
                        .apply { layoutParams = lp(0, WRAP, 1f).apply { rightMargin = a.dp(8) } })
                    addView(a.ghostButton("Delete", Palette.DANGER) {
                        confirm("Delete this identity?") {
                            exec("delete_identity", jsonOf("id" to i.id))
                        }
                    }.apply { layoutParams = lp(0, WRAP, 1f) })
                })
            })
        }
        addView(a.ghostButton("+ Add identity") { editIdentity(null) })
    }

    private fun editIdentity(existing: Identity?) {
        val statement = a.field("I am becoming someone who...", existing?.statement ?: "", lines = 2)
        var area = existing?.lifeArea ?: LifeArea.HEALTH
        val areaRow = a.flowRow {}
        fun paintAreas() {
            areaRow.removeAllViews()
            for (la in LifeArea.values()) {
                areaRow.addView(a.chip(la.label, active = la == area) {
                    area = la
                    paintAreas()
                })
            }
        }
        paintAreas()
        val body = a.column(0) {
            addView(a.label("Statement"))
            addView(statement)
            addView(a.label("Life area"))
            addView(areaRow)
        }
        Dialogs.form(a, if (existing == null) "New identity" else "Edit identity", body) {
            val text = statement.text.toString().trim()
            if (text.isBlank()) {
                a.toast("Write a statement first"); return@form false
            }
            if (existing == null) exec("create_identity",
                jsonOf("statement" to text, "lifeArea" to area.name))
            else exec("update_identity",
                jsonOf("id" to existing.id, "statement" to text, "lifeArea" to area.name))
            true
        }
    }

    /* --------------------------------------------------------------- goals */

    private fun goalsSection(): View = a.column {
        addView(a.heading("GOALS"))
        val list = a.repo.goals()
        if (list.isEmpty()) {
            addView(emptyCard("What outcome would matter?",
                "A goal sets direction. Your system does the work."))
        }
        for (g in list) {
            val systems = a.repo.systems().filter { it.goalId == g.id }
            addView(a.card {
                addView(a.body(g.title, 16f, Palette.INK, bold = true))
                if (g.why.isNotBlank()) {
                    addView(a.spacer(4))
                    addView(a.body("Why: ${g.why}", 13f, Palette.INK_SOFT))
                }
                addView(a.spacer(4))
                addView(a.body("${g.status.name.lowercase()} · ${systems.size} systems",
                    13f, Palette.INK_FAINT))
                addView(a.spacer(10))
                addView(a.row {
                    addView(a.ghostButton("Edit") { editGoal(g) }
                        .apply { layoutParams = lp(0, WRAP, 1f).apply { rightMargin = a.dp(8) } })
                    addView(a.ghostButton("Delete", Palette.DANGER) {
                        confirm("Delete this goal?") { exec("delete_goal", jsonOf("id" to g.id)) }
                    }.apply { layoutParams = lp(0, WRAP, 1f) })
                })
            })
        }
        addView(a.ghostButton("+ Add goal") { editGoal(null) })
    }

    private fun editGoal(existing: Goal?) {
        val title = a.field("Goal", existing?.title ?: "")
        val why = a.field("Why does this matter?", existing?.why ?: "", lines = 3)
        val metric = a.field("How will you know? (optional)", existing?.outcomeMetric ?: "")
        val identities = a.repo.identities()
        var identityId = existing?.identityId ?: identities.firstOrNull()?.id
        val idRow = a.flowRow {}
        fun paintIds() {
            idRow.removeAllViews()
            for (i in identities) {
                idRow.addView(a.chip(i.statement.take(26), active = i.id == identityId) {
                    identityId = i.id
                    paintIds()
                })
            }
        }
        paintIds()
        val body = a.column(0) {
            addView(a.label("Goal")); addView(title)
            addView(a.label("Why")); addView(why)
            addView(a.label("Outcome metric")); addView(metric)
            if (identities.isNotEmpty()) {
                addView(a.label("Linked identity")); addView(idRow)
            }
        }
        Dialogs.form(a, if (existing == null) "New goal" else "Edit goal", body) {
            val t = title.text.toString().trim()
            if (t.isBlank()) { a.toast("A title is required"); return@form false }
            val args = jsonOf("title" to t, "why" to why.text.toString(),
                "outcomeMetric" to metric.text.toString(), "identityId" to identityId)
            if (existing == null) exec("create_goal", args)
            else exec("update_goal", args.put("id", existing.id))
            true
        }
    }

    /* ------------------------------------------------------------- systems */

    private fun systemsSection(): View = a.column {
        addView(a.heading("SYSTEMS"))
        val list = a.repo.systems()
        if (list.isEmpty()) {
            addView(emptyCard("How will it actually happen?",
                "A system is the repeatable process behind the goal."))
        }
        for (s in list) {
            val habits = a.repo.habits().filter { it.systemId == s.id }
            addView(a.card {
                addView(a.body(s.title, 16f, Palette.INK, bold = true))
                if (s.description.isNotBlank()) {
                    addView(a.spacer(4))
                    addView(a.body(s.description, 13f, Palette.INK_SOFT))
                }
                addView(a.spacer(4))
                addView(a.body("${habits.size} habits · goal: ${a.repo.goal(s.goalId)?.title ?: "none"}",
                    13f, Palette.INK_FAINT))
                addView(a.spacer(10))
                addView(a.row {
                    addView(a.ghostButton("Edit") { editSystem(s) }
                        .apply { layoutParams = lp(0, WRAP, 1f).apply { rightMargin = a.dp(8) } })
                    addView(a.ghostButton("Delete", Palette.DANGER) {
                        confirm("Delete this system?") { exec("delete_system", jsonOf("id" to s.id)) }
                    }.apply { layoutParams = lp(0, WRAP, 1f) })
                })
            })
        }
        addView(a.ghostButton("+ Add system") { editSystem(null) })
    }

    private fun editSystem(existing: Sys?) {
        val title = a.field("System", existing?.title ?: "")
        val desc = a.field("Describe the process", existing?.description ?: "", lines = 3)
        val goals = a.repo.goals()
        var goalId = existing?.goalId ?: goals.firstOrNull()?.id
        val goalRow = a.flowRow {}
        fun paint() {
            goalRow.removeAllViews()
            for (g in goals) {
                goalRow.addView(a.chip(g.title.take(24), active = g.id == goalId) {
                    goalId = g.id; paint()
                })
            }
        }
        paint()
        val body = a.column(0) {
            addView(a.label("System")); addView(title)
            addView(a.label("Description")); addView(desc)
            if (goals.isNotEmpty()) { addView(a.label("Supports goal")); addView(goalRow) }
        }
        Dialogs.form(a, if (existing == null) "New system" else "Edit system", body) {
            val t = title.text.toString().trim()
            if (t.isBlank()) { a.toast("A title is required"); return@form false }
            val args = jsonOf("title" to t, "description" to desc.text.toString(), "goalId" to goalId)
            if (existing == null) exec("create_system", args)
            else exec("update_system", args.put("id", existing.id))
            true
        }
    }

    /* -------------------------------------------------------------- habits */

    private fun habitsSection(): View = a.column {
        addView(a.heading("HABITS"))
        val list = a.repo.habits()
        if (list.isEmpty()) {
            addView(emptyCard("Pick one small action",
                "Every habit needs a version you can start in two minutes."))
        }
        for (h in list) {
            val stats = Insights.forHabit(a.repo, h)
            addView(a.card {
                addView(a.row {
                    if (h.mode == HabitMode.REDUCE) addView(a.iconDot(Palette.WARM))
                    addView(a.body(h.title, 16f, Palette.INK, bold = true).apply {
                        layoutParams = lp(0, WRAP, 1f)
                    })
                    if (h.protectedRoutine) addView(a.body("protected", 11f, Palette.ACCENT, bold = true))
                })
                addView(a.spacer(4))
                addView(a.body(Capabilities.daysLabel(h.daysMask) +
                        (if (h.cueTime.isNotBlank()) " · ${h.cueTime}" else "") +
                        " · ${stats.repetitions} reps · ${stats.consistency30}%",
                    13f, Palette.INK_FAINT))
                addView(a.spacer(10))
                addView(a.row {
                    addView(a.ghostButton("Open") {
                        a.startActivity(Intent(a, HabitDetailActivity::class.java)
                            .putExtra("habitId", h.id))
                    }.apply { layoutParams = lp(0, WRAP, 1f).apply { rightMargin = a.dp(8) } })
                    addView(a.ghostButton("Edit") {
                        a.startActivity(Intent(a, HabitDesignerActivity::class.java)
                            .putExtra("habitId", h.id))
                    }.apply { layoutParams = lp(0, WRAP, 1f).apply { rightMargin = a.dp(8) } })
                    addView(a.ghostButton("Archive") {
                        exec("archive_habit", jsonOf("habit" to h.id))
                    }.apply { layoutParams = lp(0, WRAP, 1f) })
                })
            })
        }
        val archived = a.repo.habits(true).filter { it.status == Status.ARCHIVED }
        if (archived.isNotEmpty()) {
            addView(a.heading("ARCHIVED"))
            for (h in archived) {
                addView(a.card {
                    addView(a.row {
                        addView(a.body(h.title, 15f, Palette.INK_FAINT).apply {
                            layoutParams = lp(0, WRAP, 1f)
                        })
                        addView(a.ghostButton("Restore") {
                            exec("restore_habit", jsonOf("habit" to h.id))
                        })
                    })
                })
            }
        }
    }

    /* -------------------------------------------------------------- helpers */

    private fun emptyCard(title: String, subtitle: String): View = a.card {
        addView(a.body(title, 15f, Palette.INK, bold = true))
        addView(a.spacer(4))
        addView(a.body(subtitle, 13f, Palette.INK_FAINT))
    }

    private fun confirm(message: String, action: () -> Unit) =
        Dialogs.confirm(a, message, action)

    private fun exec(command: String, args: org.json.JSONObject) {
        val res = a.bus.execute(command, args, Actor.USER)
        if (!res.ok) a.toast(res.message)
    }
}
