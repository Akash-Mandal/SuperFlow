package com.superflow.ui.detail

import android.content.Intent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.superflow.R
import com.superflow.data.Repository
import com.superflow.data.model.*
import com.superflow.domain.Actor
import com.superflow.domain.Capabilities
import com.superflow.domain.CommandBus
import com.superflow.domain.Insights
import com.superflow.ui.common.HistoryStrip
import com.superflow.ui.common.ScrollActivity
import com.superflow.ui.common.snack
import com.superflow.ui.common.visible
import com.superflow.ui.designer.HabitDesignerActivity
import com.superflow.ui.sheets.ObstacleSheet
import com.superflow.util.Dates
import com.superflow.util.jsonOf

/** One habit's full design, ladder, history and obstacle plans. */
class HabitDetailActivity : ScrollActivity() {

    private val bus by lazy { CommandBus.get(this) }
    private val repo by lazy { Repository.get(this) }
    private val habitId by lazy { intent.getStringExtra(EXTRA_HABIT_ID).orEmpty() }

    override fun titleText() = "Habit"

    override fun onResume() {
        super.onResume()
        if (contentReady()) rebuild()
    }

    override fun buildContent() {
        val h = repo.habit(habitId)
        if (h == null) {
            content.addView(textCard("Habit not found", "It may have been deleted."))
            return
        }
        toolbar.title = h.title
        val stats = Insights.forHabit(repo, h)

        content.addView(textCard(getString(R.string.your_contract), h.contract()))

        // Today
        content.addView(section("TODAY"))
        val today = repo.checkIn(h.id, Dates.today())
        val todayCard = layoutInflater.inflate(R.layout.item_text_card, content, false)
        todayCard.findViewById<TextView>(R.id.text_title).text = when {
            today == null -> "Not recorded yet"
            today.isSuccess -> "Done at ${today.level.label}"
            today.result == CheckInResult.SKIPPED -> "Intentionally skipped"
            else -> "Missed — and that is recoverable"
        }
        val body = todayCard.findViewById<TextView>(R.id.text_body)
        if (today == null) {
            body.visible(false)
            val chips = ChipGroup(this)
            Level.values().forEach { level ->
                chips.addView(Chip(this).apply {
                    text = level.label
                    isCheckable = false
                    setEnsureMinTouchTargetSize(false)
                    setOnClickListener { exec("check_in", jsonOf("habit" to h.id, "level" to level.name)) }
                })
            }
            chips.addView(Chip(this).apply {
                text = "Skip"; isCheckable = false; setEnsureMinTouchTargetSize(false)
                setOnClickListener { exec("skip_habit", jsonOf("habit" to h.id)) }
            })
            chips.addView(Chip(this).apply {
                text = "Missed"; isCheckable = false; setEnsureMinTouchTargetSize(false)
                setOnClickListener { exec("mark_missed", jsonOf("habit" to h.id)) }
            })
            (todayCard.findViewById<TextView>(R.id.text_title).parent as LinearLayout).addView(chips)
        } else {
            body.text = "Tap to clear and record something else."
            todayCard.setOnClickListener { exec("clear_check_in", jsonOf("habit" to h.id)) }
        }
        content.addView(todayCard)

        // Progress
        content.addView(section("PROGRESS"))
        val prog = layoutInflater.inflate(R.layout.item_habit_stat, content, false)
        prog.findViewById<TextView>(R.id.hs_title).text = "30-day consistency"
        prog.findViewById<TextView>(R.id.hs_percent).text = "${stats.consistency30}%"
        prog.findViewById<LinearProgressIndicator>(R.id.hs_bar)
            .setProgressCompat(stats.consistency30, true)
        prog.findViewById<TextView>(R.id.hs_detail).text =
            "${stats.repetitions} reps · run of ${stats.currentRun} · best ${stats.bestRun} · " +
                    "${stats.recoveries} recoveries"
        val hint = prog.findViewById<TextView>(R.id.hs_hint)
        hint.visible(true)
        hint.text = if (stats.recoveries > 0)
            "You have returned after a miss ${stats.recoveries} times. That is the skill that matters."
        else "Consistency counts only the days this habit is scheduled."
        content.addView(prog)

        // History
        content.addView(section("LAST 14 DAYS"))
        val histCard = layoutInflater.inflate(R.layout.item_text_card, content, false)
        histCard.findViewById<TextView>(R.id.text_title).text =
            Capabilities.daysLabel(h.daysMask) + (if (h.cueTime.isNotBlank()) " · ${h.cueTime}" else "")
        histCard.findViewById<TextView>(R.id.text_body).visible(false)
        val strip = HistoryStrip(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpi(24)
            ).also { it.topMargin = dpi(12) }
        }
        val checkIns = repo.checkInsOf(h.id).associateBy { it.date }
        strip.setStates(Dates.lastDays(14).map { d ->
            when {
                !h.runsOn(Dates.isoDayOfWeek(d)) -> -3
                checkIns[d]?.isSuccess == true -> 1
                checkIns[d]?.result == CheckInResult.SKIPPED -> -2
                checkIns[d]?.isMiss == true -> -1
                else -> 0
            }
        })
        (histCard.findViewById<TextView>(R.id.text_title).parent as LinearLayout).addView(strip)
        content.addView(histCard)

        // Ladder
        content.addView(section("THE LADDER"))
        content.addView(textCard("Every kind of day",
            Level.values().joinToString("\n") { "${it.label}: ${h.levelText(it)}" }))

        // Design
        content.addView(section("THE DESIGN"))
        val design = buildString {
            fun add(k: String, v: String) { if (v.isNotBlank()) append("$k: $v\n") }
            add("Cue", listOfNotNull(
                h.cueTime.ifBlank { null }, h.cuePlace.ifBlank { null },
                h.anchorText.ifBlank { null }?.let { "after $it" }
            ).joinToString(" · "))
            add("Benefit", h.benefit)
            add("Paired with", h.temptationBundle)
            add("Reframe", h.reframe)
            add("Friction", h.frictionPlan)
            add("Preparation", h.environmentPrep)
            add("Reward", h.reward)
            add("Recovery", h.recoveryPlan)
        }.trim()
        content.addView(textCard(
            if (h.mode == HabitMode.REDUCE) "Reduce design" else "Four laws",
            design.ifBlank { "Nothing filled in yet. Edit the design to add cues and rewards." }
        ))

        // Obstacles
        content.addView(section(getString(R.string.obstacle_plans)))
        val obstacles = repo.obstacles(h.id)
        if (obstacles.isEmpty()) {
            content.addView(textCard("No if-then plans yet",
                "Deciding in advance is what makes a plan survive a bad day."))
        }
        obstacles.forEach { o ->
            val card = layoutInflater.inflate(R.layout.item_text_card, content, false)
            card.findViewById<TextView>(R.id.text_title).text = "If ${o.ifText}"
            card.findViewById<TextView>(R.id.text_body).text = "then ${o.thenText}"
            card.setOnLongClickListener {
                exec("delete_obstacle_plan", jsonOf("id" to o.id)); true
            }
            content.addView(card)
        }
        content.addView(MaterialButton(this, null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = getString(R.string.add_obstacle)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dpi(4) }
            setOnClickListener {
                ObstacleSheet.show(supportFragmentManager) { ifText, thenText ->
                    exec("add_obstacle_plan",
                        jsonOf("habit" to h.id, "ifText" to ifText, "thenText" to thenText))
                }
            }
        })

        // Manage
        content.addView(section("MANAGE"))
        content.addView(MaterialButton(this).apply {
            text = "Edit design"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                startActivity(Intent(this@HabitDetailActivity, HabitDesignerActivity::class.java)
                    .putExtra(HabitDesignerActivity.EXTRA_HABIT_ID, h.id))
            }
        })
        content.addView(MaterialButton(this, null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Archive"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dpi(8) }
            setOnClickListener { exec("archive_habit", jsonOf("habit" to h.id)); finish() }
        })
        content.addView(MaterialButton(this, null,
            com.google.android.material.R.attr.borderlessButtonStyle).apply {
            text = "Delete habit"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                MaterialAlertDialogBuilder(this@HabitDetailActivity)
                    .setTitle("Delete \"${h.title}\"?")
                    .setMessage("Its history goes too. You can undo this from Activity.")
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.delete) { _, _ ->
                        exec("delete_habit", jsonOf("habit" to h.id)); finish()
                    }.show()
            }
        })
    }

    private fun dpi(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun exec(command: String, args: org.json.JSONObject) {
        val res = bus.execute(command, args, Actor.USER)
        if (!res.ok) findViewById<View>(R.id.root).snack(res.message)
        rebuild()
    }

    companion object { const val EXTRA_HABIT_ID = "habitId" }
}
