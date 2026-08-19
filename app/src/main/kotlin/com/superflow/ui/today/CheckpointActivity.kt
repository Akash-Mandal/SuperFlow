package com.superflow.ui.today

import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.superflow.R
import com.superflow.data.Repository
import com.superflow.data.model.Checkpoint
import com.superflow.domain.Actor
import com.superflow.domain.CommandBus
import com.superflow.domain.Insights
import com.superflow.ui.common.ProgressRing
import com.superflow.ui.common.ScrollActivity
import com.superflow.ui.common.snack
import com.superflow.ui.sheets.TextInputSheet
import com.superflow.util.jsonOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Guided checkpoints.
 *
 * Morning, midday and evening become a short guided experience instead of an
 * empty button: an energy rating, the day's plan, an optional focus, and —
 * in the evening — a three-question reflection.
 */
class CheckpointActivity : ScrollActivity() {

    private val bus by lazy { CommandBus.get(this) }
    private val repo by lazy { Repository.get(this) }
    private val checkpoint: Checkpoint by lazy {
        runCatching {
            Checkpoint.valueOf(intent.getStringExtra(EXTRA_CHECKPOINT) ?: "MORNING")
        }.getOrDefault(Checkpoint.MORNING)
    }

    private var energy = 3
    private val reflection = HashMap<String, String>()

    override fun titleText(): String = when (checkpoint) {
        Checkpoint.MORNING -> "Good morning"
        Checkpoint.MIDDAY -> "Midday check-in"
        Checkpoint.EVENING -> "Evening reflection"
    }

    override fun buildContent() {
        when (checkpoint) {
            Checkpoint.MORNING -> morning()
            Checkpoint.MIDDAY -> midday()
            Checkpoint.EVENING -> evening()
        }
    }

    /* -------------------------------------------------------------- morning */

    private fun morning() {
        val today = repo.clock.today()
        val habits = repo.habitsForDay(today)
        val focus = repo.focusFor(com.superflow.core.time.SfTime.format(today))

        content.addView(textCard("Energy right now", "How full is the tank before the day begins?"))
        energySlider()

        content.addView(section("TODAY'S PLAN"))
        if (habits.isEmpty()) {
            content.addView(textCard("Nothing scheduled", "A quiet day is allowed."))
        } else {
            habits.sortedBy { it.cueTime }.forEach { h ->
                content.addView(planRow(
                    h.cueTime.ifBlank { "Anytime" },
                    h.tinyStart.ifBlank { h.title }
                ))
            }
        }

        content.addView(section("FOCUS"))
        content.addView(textCard("Pick up to three",
            "The actions that deserve emphasis today."))
        focus.forEach { f ->
            content.addView(planRow("Focus", f.title))
        }
        if (focus.size < 3) {
            content.addView(outlinedButton("Add a focus action") {
                TextInputSheet.show(supportFragmentManager, "Focus", "What deserves emphasis?") { text ->
                    if (text.isNotBlank()) {
                        exec("add_focus_item", jsonOf("title" to text.trim()), announce = false)
                    }
                }
            })
        }

        content.addView(primaryButton("Start the day") {
            exec("log_energy", jsonOf("energy" to energy, "checkpoint" to checkpoint.name), announce = false)
            exec("run_checkpoint", jsonOf("checkpoint" to checkpoint.name))
            finish()
        })
    }

    /* -------------------------------------------------------------- midday */

    private fun midday() {
        val today = repo.clock.today()
        val (done, total) = Insights.dayProgress(repo, today)

        content.addView(textCard("Energy at the midpoint", "How has the day been so far?"))
        energySlider()

        content.addView(section("PROGRESS"))
        val ring = ProgressRing(this)
        val fraction = if (total == 0) 0f else done.toFloat() / total
        ring.centerLabel = if (total == 0) "—" else "$done/$total"
        ring.centerSub = "done"
        ring.setProgress(fraction, animate = true)
        ring.layoutParams = LinearLayout.LayoutParams(dpi(160), dpi(160)).apply {
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            topMargin = dpi(8)
        }
        content.addView(ring)
        content.addView(textCard(
            if (done == total) "Everything is handled" else "Still ahead of you",
            if (total == 0) "Nothing scheduled — that is allowed."
            else if (done < total) "If the day got away, a Tiny Start still counts."
            else "A clean sweep. Notice how that feels."
        ))

        content.addView(primaryButton("Continue the day") {
            exec("log_energy", jsonOf("energy" to energy, "checkpoint" to checkpoint.name), announce = false)
            finish()
        })
    }

    /* -------------------------------------------------------------- evening */

    private fun evening() {
        val today = repo.clock.today()
        val (done, total) = Insights.dayProgress(repo, today)

        content.addView(textCard("Energy at the end", "Looking back, how was today's energy?"))
        energySlider()

        content.addView(section("TODAY"))
        val ring = ProgressRing(this)
        val fraction = if (total == 0) 0f else done.toFloat() / total
        ring.centerLabel = if (total == 0) "—" else "$done/$total"
        ring.centerSub = "done"
        ring.setProgress(fraction, animate = true)
        ring.layoutParams = LinearLayout.LayoutParams(dpi(160), dpi(160)).apply {
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            topMargin = dpi(8)
        }
        content.addView(ring)
        content.addView(textCard("$done of $total done", "Every finished action was a vote for who you are becoming."))

        content.addView(section("REFLECTION"))
        field("well", "What went well?", "", lines = 3)
        field("hard", "What was hard?", "", lines = 3)
        field("tomorrow", "One thing for tomorrow?", "", lines = 2)

        content.addView(primaryButton("Save reflection") {
            exec("log_energy", jsonOf("energy" to energy, "checkpoint" to checkpoint.name), announce = false)
            val label = "Evening reflection · ${com.superflow.core.time.SfTime.shortDay(today)}"
            exec("create_review", jsonOf(
                "kind" to "WEEKLY",
                "whatWorked" to reflection["well"].orEmpty(),
                "whatDidnt" to reflection["hard"].orEmpty(),
                "systemChange" to reflection["tomorrow"].orEmpty()
            ).put("periodLabel", label))
            finish()
        })
    }

    /* -------------------------------------------------------------- helpers */

    private fun energySlider() {
        val card = layoutInflater.inflate(R.layout.item_checkpoint, content, false)
        card.findViewById<TextView>(R.id.energy_label).text = "Energy · $energy/5"
        card.findViewById<com.google.android.material.chip.ChipGroup>(R.id.checkpoint_chips).visibility =
            android.view.View.GONE
        val slider = card.findViewById<Slider>(R.id.energy_slider)
        slider.value = energy.toFloat()
        slider.addOnChangeListener { _, value, _ ->
            energy = value.toInt().coerceIn(1, 5)
            card.findViewById<TextView>(R.id.energy_label).text = "Energy · $energy/5"
        }
        content.addView(card)
    }

    private fun planRow(left: String, right: String): android.view.View {
        val card = layoutInflater.inflate(R.layout.item_text_card, content, false)
        card.findViewById<TextView>(R.id.text_title).text = left
        card.findViewById<TextView>(R.id.text_body).text = right
        return card
    }

    private fun field(key: String, hint: String, value: String, lines: Int = 1) {
        val v = layoutInflater.inflate(R.layout.part_field, content, false)
        val layout = v.findViewById<TextInputLayout>(R.id.field_layout)
        val edit = v.findViewById<TextInputEditText>(R.id.field_edit)
        layout.hint = hint
        edit.setText(value)
        if (lines > 1) {
            edit.isSingleLine = false
            edit.minLines = lines
            edit.gravity = android.view.Gravity.TOP
            edit.inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        edit.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                reflection[key] = s?.toString().orEmpty()
            }
        })
        content.addView(v)
    }

    private fun primaryButton(text: String, onClick: () -> Unit): android.view.View =
        MaterialButton(this).apply {
            this.text = text
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dpi(12) }
            setOnClickListener { onClick() }
        }

    private fun outlinedButton(text: String, onClick: () -> Unit): android.view.View =
        MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
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

    companion object {
        const val EXTRA_CHECKPOINT = "checkpoint"
    }
}
