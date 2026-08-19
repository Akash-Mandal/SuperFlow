package com.superflow.ui.review

import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.superflow.R
import com.superflow.data.Repository
import com.superflow.data.model.ReviewKind
import com.superflow.domain.Actor
import com.superflow.domain.CommandBus
import com.superflow.domain.Insights
import com.superflow.ui.common.ScrollActivity
import com.superflow.ui.common.snack
import com.superflow.util.Dates
import com.superflow.util.jsonOf

/**
 * The review system.
 *
 * A review is not a report card. It exists to change the system: keep, shrink,
 * expand, reschedule, redesign the environment, or retire.
 */
class ReviewActivity : ScrollActivity() {

    private val bus by lazy { CommandBus.get(this) }
    private val repo by lazy { Repository.get(this) }
    private var kind = ReviewKind.WEEKLY
    private val answers = HashMap<String, String>()

    override fun titleText() = getString(R.string.review)

    override fun buildContent() {
        content.addView(textCard("Look at the system, not at yourself",
            "The goal of a review is one concrete change, not a verdict."))

        val chips = ChipGroup(this).apply { isSingleSelection = true }
        ReviewKind.values().forEach { k ->
            chips.addView(Chip(this).apply {
                text = k.name.lowercase().replaceFirstChar { it.uppercase() }
                isCheckable = true
                isChecked = kind == k
                setEnsureMinTouchTargetSize(false)
                setOnClickListener { kind = k; rebuild() }
            })
        }
        content.addView(chips)

        val days = when (kind) {
            ReviewKind.WEEKLY -> 7
            ReviewKind.MONTHLY -> 30
            ReviewKind.QUARTERLY -> 90
        }
        content.addView(section("WHAT THE DATA SAYS"))
        content.addView(textCard("Last $days days", Insights.summaryText(repo, days)))

        content.addView(section("SUGGESTIONS"))
        val stats = Insights.allStats(repo)
        val weak = stats.filter { it.consistency30 < 50 }
        val strong = stats.filter { it.consistency30 >= 80 }
        content.addView(textCard("Where to change one thing", buildString {
            if (stats.isEmpty()) append("Nothing to review yet.")
            if (weak.isNotEmpty()) {
                append("Consider shrinking:\n")
                weak.take(3).forEach {
                    append("· ${it.habit.title} at ${it.consistency30}% — make the standard " +
                            "version match the tiny one for a while.\n")
                }
            }
            if (strong.isNotEmpty()) {
                if (isNotEmpty()) append('\n')
                append("Steady enough to grow:\n")
                strong.take(3).forEach {
                    append("· ${it.habit.title} at ${it.consistency30}% — you could add a " +
                            "little, but only if it still feels easy.\n")
                }
            }
            if (weak.isEmpty() && strong.isEmpty() && stats.isNotEmpty()) {
                append("Everything is in the middle. That is a fine place to be. " +
                        "Change one thing at most.")
            }
        }.trim()))

        content.addView(section("YOUR REFLECTION"))
        field("whatWorked", "What actually worked?")
        field("whatDidnt", "What got in the way?")
        field("systemChange", "One change to the system")
        field("identityEvidence", "Evidence about who you are becoming")

        content.addView(MaterialButton(this).apply {
            text = "Save review"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dpi(8) }
            setOnClickListener {
                runCommand(bus, "create_review", jsonOf(
                    "kind" to kind.name,
                    "whatWorked" to answers["whatWorked"].orEmpty(),
                    "whatDidnt" to answers["whatDidnt"].orEmpty(),
                    "systemChange" to answers["systemChange"].orEmpty(),
                    "identityEvidence" to answers["identityEvidence"].orEmpty()
                )) { res ->
                    findViewById<View>(R.id.root).snack(res.message)
                    if (res.ok) { answers.clear(); rebuild() }
                }
            }
        })

        content.addView(section("PAST REVIEWS"))
        val past = repo.reviews()
        if (past.isEmpty()) content.addView(textCard("None yet", "Reviews you save appear here."))
        past.forEach { r ->
            val card = layoutInflater.inflate(R.layout.item_text_card, content, false)
            card.findViewById<TextView>(R.id.text_title).text =
                "${r.periodLabel} · ${r.kind.name.lowercase()}"
            card.findViewById<TextView>(R.id.text_body).text = buildString {
                if (r.whatWorked.isNotBlank()) append("Worked: ${r.whatWorked}\n")
                if (r.whatDidnt.isNotBlank()) append("In the way: ${r.whatDidnt}\n")
                if (r.systemChange.isNotBlank()) append("Changed: ${r.systemChange}\n")
                if (r.identityEvidence.isNotBlank()) append("Evidence: ${r.identityEvidence}\n")
                append(Dates.stamp(r.createdAt))
            }
            card.setOnLongClickListener {
                bus.execute("delete_review", jsonOf("id" to r.id), Actor.USER); rebuild(); true
            }
            content.addView(card)
        }
    }

    private fun field(key: String, hint: String) {
        val v = layoutInflater.inflate(R.layout.part_field, content, false)
        val layout = v.findViewById<TextInputLayout>(R.id.field_layout)
        val edit = v.findViewById<TextInputEditText>(R.id.field_edit)
        layout.hint = hint
        edit.setText(answers[key].orEmpty())
        edit.isSingleLine = false
        edit.minLines = 2
        edit.inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        edit.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                answers[key] = s?.toString().orEmpty()
            }
        })
        content.addView(v)
    }

    private fun dpi(v: Int) = (v * resources.displayMetrics.density).toInt()
}
