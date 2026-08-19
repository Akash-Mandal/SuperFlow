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
                    exec("delete_scorecard_entry", jsonOf("id" to e.id)); true
                }
                content.addView(card)
            }
        }
        content.addView(textCard("Tip", "Long-press a routine to remove it."))
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
        runCommand(bus, command, args) { res ->
            if (!res.ok) findViewById<View>(R.id.root).snack(res.message)
            rebuild()
        }
    }
}
