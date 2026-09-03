package com.superflow.domain

import com.superflow.core.time.SfTime
import com.superflow.data.Repository
import com.superflow.util.Fuzzy

/**
 * Global search across every entity in the workspace.
 *
 * One ranked result list, grouped by type in the UI. Relevance is exact match
 * first, then prefix, then contains, then a fuzzy Levenshtein fallback — the
 * same scoring the plan specifies. Kept free of Android resource references so
 * the logic suites can run it; the UI maps [SearchResult.type] to an icon.
 */
data class SearchResult(
    val type: String,      // "habit", "goal", "identity", "system", "review", "journal", "audit", "obstacle"
    val id: String,
    val title: String,
    val subtitle: String,
    val relevance: Float  // 0.0–1.0
)

object Search {

    fun search(repo: Repository, query: String): List<SearchResult> {
        if (query.isBlank()) return emptyList()
        val q = query.trim().lowercase()
        val results = mutableListOf<SearchResult>()

        repo.habits(true).forEach { h ->
            val score = relevance(q, h.title, h.cueTime, h.cuePlace, h.anchorText, h.benefit)
            if (score > 0f) {
                results.add(SearchResult("habit", h.id, h.title,
                    "${Capabilities.daysLabel(h)} · ${h.cueTime.ifBlank { "Anytime" }}", score))
            }
        }
        repo.identities(true).forEach { i ->
            val score = relevance(q, i.statement)
            if (score > 0f) {
                results.add(SearchResult("identity", i.id, i.statement, i.lifeArea.label, score))
            }
        }
        repo.goals().forEach { g ->
            val score = relevance(q, g.title, g.why)
            if (score > 0f) {
                results.add(SearchResult("goal", g.id, g.title, g.why.ifBlank { "Goal" }, score))
            }
        }
        repo.systems().forEach { s ->
            val score = relevance(q, s.title, s.description)
            if (score > 0f) {
                results.add(SearchResult("system", s.id, s.title, s.description.ifBlank { "System" }, score))
            }
        }
        repo.reviews().forEach { r ->
            val score = relevance(q, r.periodLabel, r.whatWorked, r.whatDidnt, r.systemChange, r.identityEvidence)
            if (score > 0f) {
                results.add(SearchResult("review", r.id, r.periodLabel,
                    "${r.kind.name.lowercase().replaceFirstChar { it.uppercase() }} review · " +
                            r.whatWorked.ifBlank { r.whatDidnt }.take(48), score))
            }
        }
        repo.messages().forEach { m ->
            val score = relevance(q, m.text)
            if (score > 0f) {
                results.add(SearchResult("journal", m.id, m.text.take(72),
                    "${m.role} · ${SfTime.stamp(java.time.Instant.ofEpochMilli(m.createdAt), repo.clock.zone())}", score))
            }
        }
        repo.audit().forEach { a ->
            val score = relevance(q, a.command, a.summary)
            if (score > 0f) {
                results.add(SearchResult("audit", a.id, a.summary.ifBlank { a.command }, a.command, score))
            }
        }
        repo.obstacles().forEach { o ->
            val score = relevance(q, o.ifText, o.thenText)
            if (score > 0f) {
                results.add(SearchResult("obstacle", o.id,
                    "If ${o.ifText}, then ${o.thenText}", "Obstacle plan", score))
            }
        }

        return results.sortedByDescending { it.relevance }
    }

    /** Exact match first, then prefix, then contains, then fuzzy. */
    fun relevance(query: String, vararg fields: String): Float {
        if (fields.isEmpty() || query.isEmpty()) return 0f

        var hasPrefix = false
        var hasContains = false
        var hasQueryContains = false
        var minFuzzy = Int.MAX_VALUE

        for (i in fields.indices) {
            val f = fields[i]
            if (f.isBlank()) continue
            val lowerF = f.lowercase()

            if (lowerF == query) {
                return 1.0f // exact match: early return immediately
            }
            if (lowerF.startsWith(query)) {
                hasPrefix = true
            } else if (!hasPrefix && lowerF.contains(query)) {
                hasContains = true
            } else if (!hasPrefix && !hasContains && query.contains(lowerF) && lowerF.length > 3) {
                hasQueryContains = true
            } else if (!hasPrefix && !hasContains && !hasQueryContains) {
                val dist = Fuzzy.levenshtein(query, lowerF)
                if (dist < minFuzzy) {
                    minFuzzy = dist
                }
            }
        }

        return when {
            hasPrefix -> 0.8f
            hasContains -> 0.5f
            hasQueryContains -> 0.3f
            minFuzzy < 3 -> 0.2f
            else -> 0f
        }
    }
}
