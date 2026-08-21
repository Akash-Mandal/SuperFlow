package com.superflow.ui.detail

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.superflow.AppBackground
import com.superflow.R
import com.superflow.core.time.SfTime
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
import com.superflow.util.jsonOf

/** One habit's full design, ladder, history and obstacle plans. */
class HabitDetailActivity : ScrollActivity() {

    private val bus by lazy { CommandBus.get(this) }
    private val repo by lazy { Repository.get(this) }
    private val habitId by lazy { intent.getStringExtra(EXTRA_HABIT_ID).orEmpty() }
    private val main = Handler(Looper.getMainLooper())

    /** Immutable payload so the DB pass happens off the UI thread. */
    private data class DetailData(
        val habit: Habit?,
        val stats: HabitStats?,
        val todayCheckIn: CheckIn?,
        val history: List<Int>,
        val obstacles: List<ObstaclePlan>
    )

    private var loadGeneration = 0

    override fun titleText() = "Habit"

    override fun onResume() {
        super.onResume()
        if (contentReady()) rebuild()
    }

    override fun buildContent() {
        if (!contentReady()) return
        content.removeAllViews()
        content.addView(textCard("Habit", "Loading…"))
        val gen = ++loadGeneration
        AppBackground.launch {
            val data = loadData()
            main.post {
                if (gen != loadGeneration || isFinishing || isDestroyed) return@post
                content.removeAllViews()
                fill(data)
            }
        }
    }

    private fun loadData(): DetailData {
        val h = repo.habit(habitId)
            ?: return DetailData(null, null, null, emptyList(), emptyList())
        val snap = repo.snapshot()
        val today = repo.clock.today()
        val iso = SfTime.format(today)
        val todayCheckIn = snap.checkIns.firstOrNull { it.habitId == h.id && it.date == iso }
        return DetailData(
            habit = h,
            stats = Insights.forHabit(snap, repo, h, today),
            todayCheckIn = todayCheckIn,
            history = Insights.historyStates(snap, repo, h, 14, today),
            obstacles = repo.obstacles(h.id)
        )
    }

    private fun fill(d: DetailData) {
        val h = d.habit
        if (h == null) {
            content.addView(textCard("Habit not found", "It may have been deleted."))
            return
        }
        val stats = d.stats ?: return
        toolbar.title = h.title
        val today = d.todayCheckIn

        content.addView(textCard(getString(R.string.your_contract), h.contract()))

        // Today
        content.addView(section("TODAY"))
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
            Capabilities.daysLabel(h) + (if (h.cueTime.isNotBlank()) " · ${h.cueTime}" else "")
        val states = Insights.historyStates(repo, h, 14)
        val succeeded14 = states.count { it == 1 }
        histCard.findViewById<TextView>(R.id.text_body).apply {
            visible(true)
            text = "$succeeded14 of ${states.count { it >= 0 }} scheduled days completed"
        }
        val strip = HistoryStrip(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpi(24)
            ).also { it.topMargin = dpi(12) }
        }
        strip.setStates(states)
        (histCard.findViewById<TextView>(R.id.text_title).parent as LinearLayout).addView(strip)
        content.addView(histCard)

        // Ladder
        content.addView(section("THE LADDER"))
        content.addView(textCard("Every kind of day",
            Level.values().joinToString("\n") { "${it.label}: ${h.levelText(it)}" }))
        content.addView(textCard("Ladder advice", Insights.ladderAdvice(repo, h)))

        // Four Laws health (§4)
        content.addView(section("FOUR LAWS HEALTH"))
        val laws = buildString {
            append("Reward: ").append(
                if (h.rewardSatisfaction == null) "not rated yet"
                else "${h.rewardSatisfaction}/5 (rated ${h.rewardLastRated ?: "recently"})")
            append('\n')
            append("Reframe: ").append(
                when (h.reframeHelpful) {
                    null -> "not tested yet"
                    true -> "helped on a hard day"
                    false -> "did not help — consider a new one"
                })
            append('\n')
            append("Temptation bundle: ").append(
                if (h.bundleEffectiveness == null) "not rated yet"
                else "${h.bundleEffectiveness}/5")
            append('\n')
            append("Friction plan: ").append(
                if (h.frictionPlanActive) "active" else "written but not activated")
            if (h.temptationBundle.isNotBlank() || h.reward.isNotBlank()) {
                append('\n')
                append("Capacity: ~${h.estimatedMinutes} min/day, difficulty ${h.difficultyRating}/5")
            }
        }
        content.addView(textCard("Which laws are working?", laws))

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
        if (d.obstacles.isEmpty()) {
            content.addView(textCard("No if-then plans yet",
                "Deciding in advance is what makes a plan survive a bad day."))
        }
        d.obstacles.forEach { o ->
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
            setOnClickListener { exec("archive_habit", jsonOf("habit" to h.id), thenFinish = true) }
        })
        content.addView(MaterialButton(this, null,
            androidx.appcompat.R.attr.borderlessButtonStyle).apply {
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
                        exec("delete_habit", jsonOf("habit" to h.id), thenFinish = true)
                    }.show()
            }
        })
    }

    private fun dpi(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun exec(command: String, args: org.json.JSONObject, thenFinish: Boolean = false) {
        AppBackground.launch {
            val res = bus.execute(command, args, Actor.USER)
            main.post {
                if (isFinishing || isDestroyed) return@post
                if (!res.ok) findViewById<View>(R.id.root).snack(res.message)
                if (thenFinish) finish() else rebuild()
            }
        }
    }

    companion object { const val EXTRA_HABIT_ID = "habitId" }
}
