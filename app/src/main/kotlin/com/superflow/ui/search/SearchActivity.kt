package com.superflow.ui.search

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.superflow.R
import com.superflow.data.Repository
import com.superflow.data.model.Habit
import com.superflow.domain.Insights
import com.superflow.ui.detail.HabitDetailActivity

/**
 * Global search across identities, goals, systems and habits.
 *
 * A single text field filters every entity by case-insensitive substring,
 * with habit results showing their consistency. Tapping a habit opens its
 * detail screen; tapping an identity/goal/system opens the Journey tab.
 */
class SearchActivity : AppCompatActivity() {

    private lateinit var repo: Repository
    private lateinit var adapter: SearchAdapter
    private lateinit var empty: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        repo = Repository.get(this)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.search_root)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }

        val field = findViewById<TextInputEditText>(R.id.search_field)
        val list = findViewById<RecyclerView>(R.id.results)
        empty = findViewById(R.id.empty_hint)

        adapter = SearchAdapter { openResult(it) }
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        field.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                runQuery(s?.toString().orEmpty())
            }
        })

        // Show everything (grouped) on open so the screen is never blank.
        runQuery("")
    }

    private fun runQuery(raw: String) {
        val q = raw.trim().lowercase()
        val results = ArrayList<SearchResult>()
        if (q.length >= 2) {
            // Fuzzy-resolve a habit for short / typo queries too.
            val exactHabitIds = HashSet<String>()
            repo.identities(true).filter { it.statement.lowercase().contains(q) }.forEach {
                results.add(SearchResult("identity", it.id, it.statement,
                    "Identity · ${it.lifeArea.label}", R.drawable.ic_identity))
            }
            repo.goals().filter { it.title.lowercase().contains(q) }.forEach {
                results.add(SearchResult("goal", it.id, it.title,
                    "Goal" + (it.why.takeIf { w -> w.isNotBlank() }?.let { w -> " · ${w.take(60)}" } ?: ""),
                    R.drawable.ic_goal))
            }
            repo.systems().filter { it.title.lowercase().contains(q) }.forEach {
                results.add(SearchResult("system", it.id, it.title,
                    "System" + (it.description.takeIf { d -> d.isNotBlank() }?.let { d -> " · ${d.take(60)}" } ?: ""),
                    R.drawable.ic_system))
            }
            repo.habits(true).filter { it.title.lowercase().contains(q) }.forEach {
                exactHabitIds.add(it.id)
                results.add(habitResult(it))
            }
            // One fuzzy match (if it isn't already an exact/substring hit).
            if (exactHabitIds.isEmpty()) {
                repo.findHabit(q)?.takeIf { it.id !in exactHabitIds }?.let {
                    results.add(habitResult(it, fuzzy = true))
                }
            }
        } else if (q.isEmpty()) {
            // Landing state: recent habits, so search is useful from the first tap.
            repo.habits().take(8).forEach { results.add(habitResult(it)) }
        }

        adapter.submitList(results)
        empty.visibility = if (results.isEmpty()) View.VISIBLE else View.GONE
        empty.text = if (q.length < 2 && q.isNotEmpty())
            "Type at least two characters to search."
        else "No matches for \"$raw\"."
    }

    private fun habitResult(h: Habit, fuzzy: Boolean = false): SearchResult {
        val stats = Insights.forHabit(repo, h)
        val sub = buildString {
            if (fuzzy) append("Did you mean: ")
            append("Habit · ${stats.repetitions} reps")
            if (stats.hasEnoughData) append(" · ${stats.consistency30}%")
            if (h.status != com.superflow.data.model.Status.ACTIVE) append(" · ${h.status.name.lowercase()}")
        }
        return SearchResult("habit", h.id, h.title, sub,
            if (h.mode == com.superflow.data.model.HabitMode.REDUCE) R.drawable.ic_shield
            else R.drawable.ic_bolt)
    }

    private fun openResult(r: SearchResult) {
        when (r.kind) {
            "habit" -> startActivity(
                Intent(this, HabitDetailActivity::class.java)
                    .putExtra(HabitDetailActivity.EXTRA_HABIT_ID, r.id)
            )
            else -> {
                // Identities/goals/systems live under Journey; jump to that tab.
                setResult(RESULT_OK, Intent().putExtra(EXTRA_JUMP_TAB, "journey"))
                finish()
            }
        }
    }

    companion object {
        const val EXTRA_JUMP_TAB = "jumpTab"
    }
}

/* ------------------------------------------------------------------ model */

data class SearchResult(
    val kind: String,
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: Int
)

/* ---------------------------------------------------------------- adapter */

class SearchAdapter(
    private val onClick: (SearchResult) -> Unit
) : ListAdapter<SearchResult, SearchAdapter.VH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<SearchResult>() {
            override fun areItemsTheSame(a: SearchResult, b: SearchResult) = a.kind == b.kind && a.id == b.id
            override fun areContentsTheSame(a: SearchResult, b: SearchResult) = a == b
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_entity, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        private val card: MaterialCardView = v.findViewById(R.id.entity_card)
        private val icon: ImageView = v.findViewById(R.id.entity_icon)
        private val title: TextView = v.findViewById(R.id.entity_title)
        private val sub: TextView = v.findViewById(R.id.entity_sub)
        private val menu: View = v.findViewById(R.id.entity_menu)
        fun bind(r: SearchResult) {
            icon.setImageResource(r.icon)
            title.text = r.title
            sub.text = r.subtitle
            menu.visibility = View.GONE
            card.setOnClickListener { onClick(r) }
        }
    }
}
