package com.superflow.ui.search

import android.content.Intent
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.superflow.R
import com.superflow.data.Repository
import com.superflow.domain.Search
import com.superflow.domain.SearchResult
import com.superflow.ui.MainActivity
import com.superflow.ui.activity.ActivityLogActivity
import com.superflow.ui.common.ScrollActivity
import com.superflow.ui.detail.HabitDetailActivity
import com.superflow.ui.review.ReviewActivity

/**
 * Global search across every entity.
 *
 * One field, ranked results grouped by type, tapping navigates straight to the
 * entity. The search itself lives in [Search] so the AI tool and this screen
 * never disagree about what a query matches.
 */
class SearchActivity : ScrollActivity() {

    private val repo by lazy { Repository.get(this) }
    private lateinit var results: LinearLayout
    private var query = ""

    override fun titleText() = "Search"

    override fun buildContent() {
        val field = layoutInflater.inflate(R.layout.part_field, content, false)
        val layout = field.findViewById<TextInputLayout>(R.id.field_layout)
        val edit = field.findViewById<TextInputEditText>(R.id.field_edit)
        layout.hint = "Search everything"
        layout.placeholderText = "Habits, goals, reviews, journal, activity…"
        edit.isSingleLine = true
        edit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {
                query = s?.toString().orEmpty()
                renderResults()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        content.addView(field)

        results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(results)
        edit.requestFocus()
        renderResults()
    }

    private fun renderResults() {
        results.removeAllViews()
        if (query.isBlank()) {
            results.addView(textCard("Find anything", "Your whole workspace is searchable — " +
                    "habits, identities, goals, systems, reviews, journal entries, activity " +
                    "and obstacle plans."))
            return
        }
        val found = Search.search(repo, query)
        if (found.isEmpty()) {
            results.addView(textCard("No matches", "Nothing for \"$query\". " +
                    "Try a shorter word or a different spelling."))
            return
        }
        found.take(40).forEach { r -> results.addView(row(r)) }
    }

    private fun row(r: SearchResult): View {
        val card = layoutInflater.inflate(R.layout.item_entity, results, false)
        card.findViewById<ImageView>(R.id.entity_icon).setImageResource(iconFor(r.type))
        card.findViewById<TextView>(R.id.entity_title).text = r.title
        val sub = card.findViewById<TextView>(R.id.entity_sub)
        sub.text = if (r.subtitle.isBlank()) r.type else "${r.type} · ${r.subtitle}"
        card.findViewById<MaterialButton>(R.id.entity_menu).visibility = View.GONE
        card.setOnClickListener { open(r) }
        return card
    }

    private fun iconFor(type: String): Int = when (type) {
        "habit" -> R.drawable.ic_bolt
        "identity" -> R.drawable.ic_identity
        "goal" -> R.drawable.ic_goal
        "system" -> R.drawable.ic_system
        "review" -> R.drawable.ic_scorecard
        "journal" -> R.drawable.ic_history
        "audit" -> R.drawable.ic_info
        "obstacle" -> R.drawable.ic_warning
        else -> R.drawable.ic_search
    }

    private fun open(r: SearchResult) {
        when (r.type) {
            "habit" -> startActivity(Intent(this, HabitDetailActivity::class.java)
                .putExtra(HabitDetailActivity.EXTRA_HABIT_ID, r.id))
            "identity", "goal", "system" ->
                startActivity(Intent(this, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_TAB, "journey"))
            "review" -> startActivity(Intent(this, ReviewActivity::class.java))
            "audit" -> startActivity(Intent(this, ActivityLogActivity::class.java))
            "journal" -> startActivity(Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_TAB, "coach"))
            "obstacle" -> {
                val habitId = repo.obstacles().firstOrNull { it.id == r.id }?.habitId
                if (habitId != null) {
                    startActivity(Intent(this, HabitDetailActivity::class.java)
                        .putExtra(HabitDetailActivity.EXTRA_HABIT_ID, habitId))
                } else {
                    startActivity(Intent(this, MainActivity::class.java)
                        .putExtra(MainActivity.EXTRA_TAB, "journey"))
                }
            }
            else -> Unit
        }
    }
}
