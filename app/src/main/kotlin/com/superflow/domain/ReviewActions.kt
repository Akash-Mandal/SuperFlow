package com.superflow.domain

import com.superflow.data.Prefs
import com.superflow.data.Repository
import com.superflow.data.model.Review
import org.json.JSONObject

/**
 * Follow-through on reviews (#9).
 *
 * A review's "one change to the system" is broken into actionable bullet
 * items and tracked until done, so reviews change behaviour instead of being
 * written and forgotten. Completion state lives in [Prefs] as a compact JSON
 * map keyed by review id; parsing is tolerant (splits on newlines/bullets).
 */
object ReviewActions {

    data class Action(val id: String, val text: String, val done: Boolean)

    /** Extract bullet/numbered action items from free-text. */
    fun parse(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        return text.lines()
            .map { line ->
                line.trim()
                    .removePrefix("•")
                    .removePrefix("-")
                    .removePrefix("*")
                    .replace(Regex("^\\d+[.)]\\s*"), "")
                    .trim()
            }
            .filter { it.length in 3..200 }
    }

    fun actionsFor(repo: Repository, prefs: Prefs, review: Review): List<Action> {
        val done = doneMap(prefs, review.id)
        return parse(review.systemChange).mapIndexed { i, text ->
            val id = "$i:${text.hashCode()}"
            Action(id, text, done[id] == true)
        }
    }

    /** Open actions across recent reviews, newest first — used by Today/Coach. */
    fun openActions(repo: Repository, prefs: Prefs): List<Pair<Review, Action>> =
        repo.reviews().take(6).flatMap { r ->
            actionsFor(repo, prefs, r).filterNot { it.done }.map { r to it }
        }

    fun toggleDone(prefs: Prefs, reviewId: String, actionId: String, done: Boolean) {
        val all = JSONObject(prefs.reviewActions)
        val perReview = all.optJSONObject(reviewId) ?: JSONObject()
        perReview.put(actionId, done)
        all.put(reviewId, perReview)
        prefs.reviewActions = all.toString()
    }

    private fun doneMap(prefs: Prefs, reviewId: String): Map<String, Boolean> {
        val all = runCatching { JSONObject(prefs.reviewActions) }.getOrNull() ?: return emptyMap()
        val obj = all.optJSONObject(reviewId) ?: return emptyMap()
        return obj.keys().asSequence().associateWith { obj.optBoolean(it) }
    }
}
