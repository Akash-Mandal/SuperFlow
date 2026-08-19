package com.superflow.ui.scorecard

import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.superflow.R
import com.superflow.data.Repository
import com.superflow.domain.Actor
import com.superflow.domain.CommandBus
import com.superflow.ui.common.ScrollActivity
import com.superflow.ui.common.snack
import com.superflow.ui.sheets.TextInputSheet
import com.superflow.util.jsonOf

/**
 * The Habit Scorecard.
 *
 * Awareness precedes change. A nonjudgmental inventory of what you already do —
 * helpful, neutral or unhelpful — with no scoring and no shame.
 */
class ScorecardActivity : ScrollActivity() {

    private val bus by lazy { CommandBus.get(this) }
    private val repo by lazy { Repository.get(this) }

    override fun titleText() = getString(R.string.habit_scorecard)

    override fun buildContent() {
        content.addView(textCard("Notice, do not judge",
            "List what you already do on a normal day, then mark each one. " +
                    "Noticing is the whole exercise — nothing here needs fixing today."))

        content.addView(MaterialButton(this).apply {
            text = "Add a routine"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dpi(4); it.bottomMargin = dpi(8) }
            setOnClickListener { addRoutine() }
        })

        val entries = repo.scorecard()
        if (entries.isEmpty()) {
            content.addView(textCard("Nothing recorded yet",
                "Try walking through a typical morning: waking, phone, coffee, commute. " +
                        "Aim for honesty, not completeness."))
            return
        }

        for ((verdict, label) in listOf(1 to "HELPFUL", 0 to "NEUTRAL", -1 to "UNHELPFUL")) {
            val list = entries.filter { it.verdict == verdict }
            if (list.isEmpty()) continue
            content.addView(section(label))
            for (e in list) {
                val card = layoutInflater.inflate(R.layout.item_text_card, content, false)
                card.findViewById<TextView>(R.id.text_title).text = e.routine
                card.findViewById<TextView>(R.id.text_body).text = when (verdict) {
                    -1 -> "If you want to change this one, start by removing its cue rather " +
                            "than relying on willpower."
                    1 -> "Worth protecting."
                    else -> "Neutral for now."
                }
                card.setOnLongClickListener {
                    com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                        .setTitle(e.routine)
                        .setItems(arrayOf("Re-score", "Delete")) { _, which ->
                            when (which) {
                                0 -> rescore(e.id)
                                1 -> exec("delete_scorecard_entry", jsonOf("id" to e.id))
                            }
                        }.show(); true
                }
                // Scorecard -> action pipeline (§12)
                val holder = card.findViewById<TextView>(R.id.text_title).parent as LinearLayout
                if (verdict == -1) {
                    holder.addView(MaterialButton(this, null,
                        com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                        text = "Turn into a Reduce habit"
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).also { it.topMargin = dpi(8) }
                        setOnClickListener {
                            exec("convert_scorecard_to_habit",
                                jsonOf("id" to e.id, "mode" to "REDUCE"))
                        }
                    })
                } else if (verdict == 1) {
                    holder.addView(MaterialButton(this, null,
                        com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                        text = "Protect it with a habit"
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).also { it.topMargin = dpi(8) }
                        setOnClickListener {
                            exec("convert_scorecard_to_habit",
                                jsonOf("id" to e.id, "mode" to "BUILD"))
                        }
                    })
                }
                content.addView(card)
            }
        }
        content.addView(textCard("Tip", "Long-press a routine to re-score or remove it."))
    }

    /** Periodic re-score (§12): the verdict can change as routines change. */
    private fun rescore(entryId: String) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Re-score")
            .setItems(arrayOf("Helpful", "Neutral", "Unhelpful")) { _, which ->
                val verdict = listOf(1, 0, -1)[which]
                exec("rescore_scorecard", jsonOf("id" to entryId, "verdict" to verdict))
            }.show()
    }

    private fun addRoutine() {
        TextInputSheet.show(supportFragmentManager, "Add a routine",
            "Check my phone in bed", subtitle = "Then choose how it serves you.") { text ->
            if (text.isBlank()) return@show
            com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("How does it serve you?")
                .setItems(arrayOf("Helpful", "Neutral", "Unhelpful")) { _, which ->
                    val verdict = listOf(1, 0, -1)[which]
                    exec("add_scorecard_entry",
                        jsonOf("routine" to text.trim(), "verdict" to verdict))
                }.show()
        }
    }

    private fun dpi(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun exec(command: String, args: org.json.JSONObject) {
        val res = bus.execute(command, args, Actor.USER)
        if (!res.ok) findViewById<View>(R.id.root).snack(res.message)
        rebuild()
    }
}
