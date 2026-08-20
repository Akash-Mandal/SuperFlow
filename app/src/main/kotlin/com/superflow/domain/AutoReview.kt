package com.superflow.domain

import com.superflow.core.time.SfTime
import com.superflow.data.Repository
import com.superflow.data.model.Review
import com.superflow.data.model.ReviewKind
import java.time.DayOfWeek

/**
 * Weekly Auto-Review Generator (Section 6.3 of the Grand Plan).
 *
 * Every Sunday (configurable), the system generates a pre-filled review
 * with data on what worked, what didn't, and suggestions for system changes.
 */
object AutoReview {

    fun generate(repo: Repository, kind: ReviewKind = ReviewKind.WEEKLY): ReviewWithData {
        val days = when (kind) {
            ReviewKind.WEEKLY -> 7
            ReviewKind.MONTHLY -> 30
            ReviewKind.QUARTERLY -> 90
        }
        val today = repo.clock.today()
        val stats = Insights.allStats(repo, today)
        val summary = Insights.summaryText(repo, days, today)

        val whatWorked = buildString {
            val strong = stats.filter { it.hasEnoughData && it.consistency30 >= 80 }
            if (strong.isNotEmpty()) {
                append("Strong this ${kind.name.lowercase()}: ")
                append(strong.joinToString(", ") { "${it.habit.title} (${it.consistency30}%)" })
            }
        }

        val whatDidnt = buildString {
            val weak = stats.filter { it.hasEnoughData && it.consistency30 < 50 }
            if (weak.isNotEmpty()) {
                append("Struggling: ")
                append(weak.joinToString(", ") { "${it.habit.title} (${it.consistency30}%)" })
            }
        }

        val systemChange = buildString {
            val redesign = stats.filter { it.missesInARow >= 2 }
            if (redesign.isNotEmpty()) {
                append("Consider shrinking: ")
                append(redesign.joinToString(", ") { it.habit.title })
            }
        }

        val periodLabel = when (kind) {
            ReviewKind.WEEKLY -> "Week of ${SfTime.shortDay(today.with(DayOfWeek.MONDAY))}"
            ReviewKind.MONTHLY -> SfTime.monthLabel(today)
            ReviewKind.QUARTERLY -> "Quarter ending ${SfTime.shortDay(today)}"
        }

        val review = Review(
            kind = kind,
            periodLabel = periodLabel,
            whatWorked = whatWorked,
            whatDidnt = whatDidnt,
            systemChange = systemChange,
            identityEvidence = ""  // User fills this in
        )

        return ReviewWithData(review, summary)
    }

    /**
     * Creates a shareable accountability report (Section 6.6 of the Grand Plan).
     */
    fun accountabilityReport(repo: Repository, days: Int = 7): String {
        val today = repo.clock.today()
        val stats = Insights.allStats(repo, today)
        val (done, total) = Insights.dayProgress(repo, today)

        return buildString {
            append("My SuperFlow Week\n\n")
            append("Identity: ${repo.identities().firstOrNull()?.statement ?: "—"}\n\n")

            append("Habits this week:\n")
            stats.forEach { s ->
                val bar = "\u2588".repeat(s.consistency30 / 10) +
                    "\u2591".repeat(10 - s.consistency30 / 10)
                append("  ${s.habit.title}: $bar ${s.consistency30}%\n")
            }

            append("\nRecoveries: ${stats.sumOf { it.recoveries }}\n")
            append("Best run: ${stats.maxOfOrNull { it.bestRun } ?: 0} days\n")
            append("\n\u2014 SuperFlow")
        }
    }

    data class ReviewWithData(
        val review: Review,
        val summary: String
    )
}