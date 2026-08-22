package com.superflow.ui.today

import android.widget.LinearLayout
import com.google.android.material.button.MaterialButton
import com.superflow.R
import com.superflow.data.Repository
import com.superflow.data.model.Checkpoint
import com.superflow.domain.Actor
import com.superflow.domain.CommandBus
import com.superflow.domain.Insights
import com.superflow.ui.common.ScrollActivity
import com.superflow.ui.common.snack
import com.superflow.ui.sheets.TextInputSheet
import com.superflow.util.jsonOf
import kotlinx.coroutines.Dispatchers
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Plan Tomorrow — a guided two-minute flow.
 *
 * Review today → set focus → energy forecast → confirm. Writes nothing until
 * the user acts, and every step goes through the shared command bus.
 */
class PlanTomorrowActivity : ScrollActivity() {

    private val bus by lazy { CommandBus.get(this) }
    private val repo by lazy { Repository.get(this) }
    private val tomorrow: LocalDate by lazy { repo.clock.today().plusDays(1) }
    private val tomorrowIso by lazy { com.superflow.core.time.SfTime.format(tomorrow) }

    private var step = 0
    private val stepCount = 4

    override fun titleText() = "Plan tomorrow"

    override fun buildContent() {
        when (step) {
            0 -> review()
            1 -> focus()
            2 -> energy()
            3 -> confirm()
        }
        footer()
    }

    /* ------------------------------------------------------------ step 1 */

    private fun review() {
        val today = repo.clock.today()
        val (done, total) = Insights.dayProgress(repo, today)
        content.addView(textCard(
            "Review today",
            "You completed $done of $total habit${if (total == 1) "" else "s"} today."
        ))

        // Offer to carry any missed habits into tomorrow's focus.
        val missed = repo.todayHabits(today).filter { it.missed }.map { it.habit }
        if (missed.isEmpty()) {
            content.addView(textCard("Nothing missed", "Tomorrow starts clean."))
        } else {
            missed.forEach { h ->
                content.addView(clickableCard(
                    h.title,
                    "Missed today — tap to carry into tomorrow's focus."
                ) {
                    exec("add_focus_item",
                        jsonOf("title" to h.title, "date" to "tomorrow"), announce = false)
                })
            }
        }
    }

    /* ------------------------------------------------------------ step 2 */

    private fun focus() {
        content.addView(textCard("Set focus",
            "What 1–3 things deserve emphasis tomorrow?"))
        val items = repo.focusFor(tomorrowIso)
        items.forEach { f -> content.addView(planRow("Focus", f.title)) }
        if (items.size < 3) {
            content.addView(button("Add a focus item", outlined = true) {
                TextInputSheet.show(supportFragmentManager, "Focus for tomorrow",
                    "What deserves emphasis?") { text ->
                    if (text.isNotBlank()) {
                        exec("add_focus_item",
                            jsonOf("title" to text.trim(), "date" to "tomorrow"), announce = false)
                    }
                }
            })
        } else {
            content.addView(textCard("Three already", "That is the right amount. More dilutes."))
        }
    }

    /* ------------------------------------------------------------ step 3 */

    private fun energy() {
        val forecast = forecastMorningEnergy()
        content.addView(textCard(
            "Energy forecast",
            when {
                forecast == null -> "Not enough energy logs to forecast tomorrow morning yet. " +
                        "Log energy at the morning checkpoint and a pattern will appear."
                forecast >= 4 -> "Your morning energy has been high (${forecast}/5). " +
                        "Consider the harder habits early."
                forecast >= 3 -> "Tomorrow morning tends to be medium energy (${forecast}/5). " +
                        "Put one meaningful habit first, not everything."
                else -> "Mornings have been low energy (${forecast}/5). " +
                        "Plan a gentle start — a Tiny version still counts."
            }
        ))
        content.addView(button("Put the hardest habit first", outlined = true) {
            val hardest = repo.habitsForDay(tomorrow)
                .maxByOrNull { it.protectedRoutine } ?: repo.habitsForDay(tomorrow).firstOrNull()
            if (hardest == null) {
                findViewById<android.view.View>(R.id.root).snack("Nothing scheduled for tomorrow yet")
            } else {
                exec("add_focus_item",
                    jsonOf("title" to hardest.title, "date" to "tomorrow"), announce = false)
            }
        })
        content.addView(textCard("A nudge, not a rule",
            "This is a hint from your own patterns. You know tomorrow better than a heuristic does."))
    }

    /* ------------------------------------------------------------ step 4 */

    private fun confirm() {
        val habits = repo.habitsForDay(tomorrow)
        val focusItems = repo.focusFor(tomorrowIso)
        val minutes = habits.size * 10
        content.addView(textCard(
            "Tomorrow, ready",
            "${habits.size} habit${if (habits.size == 1) "" else "s"} · " +
                    "${focusItems.size} focus item${if (focusItems.size == 1) "" else "s"} · " +
                    "~$minutes minutes"
        ))
        habits.take(6).forEach { h ->
            content.addView(planRow(h.cueTime.ifBlank { "Anytime" }, h.tinyStart.ifBlank { h.title }))
        }
        if (habits.size > 6) {
            content.addView(textCard("And ${habits.size - 6} more", "The full list is on Today."))
        }
    }

    /* ------------------------------------------------------------ footer */

    private fun footer() {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dpi(16) }
        }
        if (step > 0) {
            row.addView(MaterialButton(this, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = "Back"
                layoutParams = LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also { it.marginEnd = dpi(8) }
                setOnClickListener { step--; rebuild() }
            })
        }
        val label = if (step == stepCount - 1) "Looks good" else "Next"
        row.addView(MaterialButton(this).apply {
            text = label
            layoutParams = LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                if (step == stepCount - 1) {
                    finish()
                } else {
                    step++; rebuild()
                }
            }
        })
        content.addView(row)
    }

    /* ------------------------------------------------------------ helpers */

    private fun forecastMorningEnergy(): Int? {
        val logs = repo.energyLogs().filter { it.checkpoint == Checkpoint.MORNING }
        if (logs.size < 3) return null
        return (logs.sumOf { it.energy } / logs.size)
    }

    private fun planRow(left: String, right: String): android.view.View {
        val card = layoutInflater.inflate(R.layout.item_text_card, content, false)
        card.findViewById<android.widget.TextView>(R.id.text_title).text = left
        card.findViewById<android.widget.TextView>(R.id.text_body).text = right
        return card
    }

    private fun clickableCard(title: String, body: String, onClick: () -> Unit): android.view.View {
        val card = layoutInflater.inflate(R.layout.item_text_card, content, false)
        card.findViewById<android.widget.TextView>(R.id.text_title).text = title
        card.findViewById<android.widget.TextView>(R.id.text_body).text = body
        card.setOnClickListener { onClick() }
        return card
    }

    private fun button(text: String, outlined: Boolean, onClick: () -> Unit): android.view.View =
        MaterialButton(this, null,
            if (outlined) com.google.android.material.R.attr.materialButtonOutlinedStyle
            else com.google.android.material.R.attr.materialButtonStyle).apply {
            this.text = text
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dpi(4) }
            setOnClickListener { onClick() }
        }

    private fun dpi(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun exec(command: String, args: org.json.JSONObject, announce: Boolean = true) {
        lifecycleScope.launch {
            val res = withContext(Dispatchers.IO) { bus.execute(command, args, Actor.USER) }
            if (!res.ok || announce) findViewById<android.view.View>(R.id.root).snack(res.message)
            rebuild()
        }
    }
}
