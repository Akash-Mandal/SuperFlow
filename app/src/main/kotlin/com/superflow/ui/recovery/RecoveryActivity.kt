package com.superflow.ui.recovery

import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.superflow.R
import com.superflow.data.Repository
import com.superflow.data.model.Habit
import com.superflow.data.model.HabitMode
import com.superflow.data.model.HabitStats
import com.superflow.data.model.Level
import com.superflow.domain.CommandBus
import com.superflow.domain.Insights
import com.superflow.ui.common.ScrollActivity
import com.superflow.ui.common.snack
import com.superflow.util.jsonOf

/**
 * The Recovery Center.
 *
 * Missing once is normal. The only thing worth protecting is the second miss.
 * Nothing here scolds, ranks or scores the user.
 *
 * The stats pass (per-habit 365-day opportunity series) runs on the
 * background lane; the screen shows a placeholder until it lands.
 */
class RecoveryActivity : ScrollActivity() {

    private val bus by lazy { CommandBus.get(this) }
    private val repo by lazy { Repository.get(this) }

    private data class RecoveryData(
        val returning: List<Habit>,
        val struggling: List<HabitStats>,
        val reduceHabits: List<Habit>
    )

    private var loadGeneration = 0

    override fun titleText() = getString(R.string.recovery_center)

    override fun buildContent() {
        if (!contentReady()) return
        content.removeAllViews()
        content.addView(textCard(getString(R.string.recovery_center), "Loading…"))
        val gen = ++loadGeneration
        com.superflow.AppBackground.launch {
            val data = loadData()
            mainHandler.post {
                if (gen != loadGeneration || isFinishing || isDestroyed) return@post
                content.removeAllViews()
                fill(data)
            }
        }
    }

    private fun loadData(): RecoveryData {
        val today = repo.clock.today()
        val snap = repo.snapshot()
        return RecoveryData(
            returning = Insights.returnCandidates(snap, repo, today),
            struggling = Insights.allStats(snap, repo, today).filter { it.missesInARow >= 2 },
            reduceHabits = snap.activeHabits.filter { it.mode == HabitMode.REDUCE }
        )
    }

    private fun fill(d: RecoveryData) {
        content.addView(textCard("The one rule",
            "Never miss twice. The first miss is an accident. The second is the start of a " +
                    "new pattern — and that is the one worth interrupting."))

        content.addView(section("RETURN TODAY"))
        if (d.returning.isEmpty()) {
            content.addView(textCard("Nothing needs rescuing",
                "If a habit slips, it will appear here with its smallest version."))
        }
        for (h in d.returning) {
            val card = layoutInflater.inflate(R.layout.item_text_card, content, false)
            card.findViewById<TextView>(R.id.text_title).text = h.title
            card.findViewById<TextView>(R.id.text_body).text =
                "Smallest way back: ${h.tinyStart.ifBlank { "start for two minutes" }}" +
                        if (h.recoveryPlan.isNotBlank()) "\n\n${h.recoveryPlan}" else ""
            val holder = card.findViewById<TextView>(R.id.text_title).parent as LinearLayout
            holder.addView(MaterialButton(this).apply {
                text = "Do the tiny version"
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.topMargin = dpi(12) }
                setOnClickListener {
                    exec("check_in", jsonOf("habit" to h.id, "level" to Level.TINY.name))
                    findViewById<View>(R.id.root).snack("You are back. That is the whole win.")
                }
            })
            content.addView(card)
        }

        content.addView(section("LOW-CAPACITY DAY"))
        val minCard = layoutInflater.inflate(R.layout.item_text_card, content, false)
        minCard.findViewById<TextView>(R.id.text_title).text = "Some days there is less to give"
        minCard.findViewById<TextView>(R.id.text_body).text =
            "Minimum Mode drops every non-protected habit to its Minimum version for today. " +
                    "Protected routines are left alone."
        (minCard.findViewById<TextView>(R.id.text_title).parent as LinearLayout).addView(
            MaterialButton(this).apply {
                text = getString(R.string.minimum_mode)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.topMargin = dpi(12) }
                setOnClickListener { exec("enter_minimum_mode", jsonOf()) }
            })
        content.addView(minCard)

        if (d.struggling.isNotEmpty()) {
            content.addView(section("REDESIGN CANDIDATES"))
            for (s in d.struggling) {
                val card = layoutInflater.inflate(R.layout.item_text_card, content, false)
                card.findViewById<TextView>(R.id.text_title).text = s.habit.title
                card.findViewById<TextView>(R.id.text_body).text =
                    "${s.missesInARow} missed in a row · ${s.consistency30}% over 30 days\n\n" +
                            "A habit that keeps slipping is usually too big, badly timed, or " +
                            "missing a clear cue. Try shrinking it for a week."
                (card.findViewById<TextView>(R.id.text_title).parent as LinearLayout).addView(
                    MaterialButton(this, null,
                        com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                        text = "Shrink to tiny"
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).also { it.topMargin = dpi(12) }
                        setOnClickListener {
                            exec("update_habit", jsonOf(
                                "habit" to s.habit.id, "field" to "standardVersion",
                                "value" to s.habit.tinyStart.ifBlank { "Start for two minutes" }
                            ))
                        }
                    })
                content.addView(card)
            }
        }

        if (d.reduceHabits.isNotEmpty()) {
            content.addView(section("AFTER A SLIP"))
            content.addView(textCard("Returning is part of most real attempts",
                "What matters is the next hour, not the next month.\n\n" +
                        "If this involves dependence, self-harm or anything unsafe, please talk " +
                        "to a qualified person. SuperFlow is not a substitute for care."))
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
