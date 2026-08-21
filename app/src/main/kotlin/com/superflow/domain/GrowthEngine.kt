package com.superflow.domain

import com.superflow.data.Repository
import com.superflow.data.model.Habit
import com.superflow.data.model.HabitStats
import com.superflow.data.model.Level
import java.time.LocalDate

/**
 * Lightweight, offline growth engine (#4, #8).
 *
 * The engine never auto-edits a habit — that would be surprising. Instead it
 * produces [Suggestion]s to step a habit's ladder up (after sustained high
 * consistency) or down (after repeated struggle), which the user can accept
 * or ignore. All thresholds are conservative and sample-size aware.
 */
object GrowthEngine {

    data class Recommendation(
        val habit: Habit,
        val title: String,
        val body: String,
        val newStandard: String? = null
    )

    fun recommendations(
        repo: Repository,
        today: LocalDate = repo.clock.today()
    ): List<Recommendation> {
        val stats = Insights.allStats(repo, today)
        val out = ArrayList<Recommendation>()

        for (s in stats) {
            val h = s.habit
            if (h.mode == com.superflow.data.model.HabitMode.REDUCE) continue
            if (!s.hasEnoughData) continue

            // Step up: 30-day consistency >= 85% AND a long run.
            if (s.consistency30 >= 85 && s.currentRun >= 14 &&
                h.standardVersion.isNotBlank()) {
                val next = increment(h.standardVersion)
                if (next != null && next != h.standardVersion) {
                    out.add(Recommendation(
                        h,
                        "Ready to grow ${h.title}?",
                        "You have been consistent at ${s.consistency30}% for 30 days with a " +
                                "${s.currentRun}-day run. Consider moving the standard version " +
                                "to \"$next\". Only do this if it still feels easy.",
                        next
                    ))
                }
            }

            // Step down: low consistency with enough opportunities.
            if (s.consistency30 < 45 && s.opportunities30 >= 8) {
                val fallback = h.minimumVersion.ifBlank { h.tinyStart.ifBlank { h.title } }
                if (fallback.isNotBlank() && fallback != h.standardVersion) {
                    out.add(Recommendation(
                        h,
                        "Shrink ${h.title}?",
                        "At ${s.consistency30}% over ${s.opportunities30} opportunities, the standard " +
                                "version is winning more often than you are. Try \"$fallback\" as the " +
                                "standard for a while — smaller that you do beats bigger you skip.",
                        fallback
                    ))
                }
            }
        }
        return out
    }

    /**
     * Best-effort numeric increment ("10 minutes" -> "12 minutes", "2 km" ->
     * "2.5 km"). Returns null when no number is found so callers keep the
     * current value.
     */
    fun increment(text: String): String? {
        val match = Regex("(\\d+(?:[.,]\\d+)?)").find(text) ?: return null
        val number = match.value.replace(",", ".")
        val value = number.toDoubleOrNull() ?: return null
        val next = when {
            value < 1 -> value + 0.5
            value < 5 -> value + 1
            value < 20 -> value + 2
            value < 60 -> value + 5
            else -> (value * 1.15).toInt().toDouble()
        }
        val rounded = if (next % 1.0 == 0.0) next.toInt().toString()
        else String.format("%.1f", next).trimEnd('0').trimEnd('.')
        return text.replaceRange(match.range, rounded)
    }
}
