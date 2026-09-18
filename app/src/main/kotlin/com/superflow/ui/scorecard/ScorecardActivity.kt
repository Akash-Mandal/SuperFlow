package com.superflow.ui.scorecard

import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.superflow.R
import com.superflow.data.Prefs
import com.superflow.data.Repository
import com.superflow.domain.Actor
import com.superflow.domain.CommandBus
import java.time.LocalDate
import java.time.temporal.ChronoUnit
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
    private val prefs by lazy { Prefs.get(this) }

    /** Set until the first off-main read lands (#49); [buildContent] spins. */
    private var loading = true
    /** Entries read once per load, instead of twice inside every build. */
    private var entries: List<com.superflow.data.model.ScorecardEntry> = emptyList()

    override fun titleText() = getString(R.string.habit_scorecard)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        load()
    }

    /**
     * One background read serves the whole screen (#49): buildContent used to
     * call repo.scorecard() twice on the main thread — once directly and once
     * from the monthly-prompt check — recomputing everything on every open
     * and on every rebuild.
     */
    private fun load() {
        loading = true
        rebuild()
        com.superflow.AppBackground.launch {
            val loaded = repo.scorecard()
            mainHandler.post {
                if (isFinishing || isDestroyed) return@post
                entries = loaded
                loading = false
                rebuild()
                maybePromptRescore(loaded)
            }
        }
    }

    override fun buildContent() {
        if (loading) {
            content.addView(loadingRow())
            return
        }

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

    /**
     * Once a month, nudge the user to re-score their routines (#25). The
     * marker stores the last ISO month the prompt was shown; "re-scoring"
     * means reviewing existing entries and removing routines that no longer
     * fit, then adding new ones. Runs from [load]'s completion, with the
     * entries already read.
     */
    private fun maybePromptRescore(loaded: List<com.superflow.data.model.ScorecardEntry>) {
        if (loaded.isEmpty()) return
        val now = repo.clock.today()
        val thisMonth = "%d-%02d".format(now.year, now.monthValue)
        if (prefs.scorecardLastPrompt == thisMonth) return
        prefs.scorecardLastPrompt = thisMonth
        val lastEntry = loaded.maxByOrNull { it.createdAt }?.createdAt ?: 0L
        val daysOld = ChronoUnit.DAYS.between(
            java.time.Instant.ofEpochMilli(lastEntry).atZone(java.time.ZoneId.systemDefault()).toLocalDate(),
            now
        )
        if (daysOld < 14) return
        MaterialAlertDialogBuilder(this)
            .setTitle("Re-score your routines?")
            .setMessage("It has been $daysOld days since you updated your scorecard. " +
                    "Routines shift — a quick re-score keeps it honest.")
            .setPositiveButton("Review") { _, _ -> }
            .setNegativeButton("Later", null)
            .show()
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

    /** The #49 loading row, shown until the first background read lands. */
    private fun loadingRow(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, dpi(24), 0, dpi(24))
        }
        row.addView(android.widget.ProgressBar(this).apply {
            layoutParams = LinearLayout.LayoutParams(dpi(24), dpi(24)).also {
                it.marginEnd = dpi(12)
            }
            indeterminateTintList = android.content.res.ColorStateList.valueOf(
                com.google.android.material.color.MaterialColors.getColor(
                    row, com.google.android.material.R.attr.colorPrimary
                )
            )
        })
        row.addView(TextView(this).apply {
            text = "Loading your routines…"
            setTextAppearance(R.style.Text_SuperFlow_BodyMedium)
        })
        return row
    }

    private fun exec(command: String, args: org.json.JSONObject) {
        runCommand(bus, command, args) { res ->
            if (!res.ok) findViewById<View>(R.id.root).snack(res.message)
            load()
        }
    }
}
