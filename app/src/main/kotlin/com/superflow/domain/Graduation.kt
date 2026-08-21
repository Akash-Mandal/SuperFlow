package com.superflow.domain

import com.superflow.core.schedule.Opportunities
import com.superflow.core.schedule.Recurrence
import com.superflow.core.time.SfTime
import com.superflow.data.Repository
import com.superflow.data.model.Habit
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Habit graduation: the moment a habit stops being effort and starts being
 * identity.
 *
 * Per the plan, a habit becomes eligible when it has been tracked for at
 * least 66 days at 90%+ consistency. Graduated habits move to maintenance —
 * they drop off Today and are checked on weekly instead of daily.
 */
object Graduation {

    const val MIN_DAYS = 66
    const val MIN_CONSISTENCY = 90
    const val MIN_SAMPLE = 5

    /** Pure rule, unit-testable: 66+ tracked days at 90%+ consistency. */
    fun eligible(consistencyPercent: Int, trackedDays: Long, opportunities: Int): Boolean =
        trackedDays >= MIN_DAYS && consistencyPercent >= MIN_CONSISTENCY && opportunities >= MIN_SAMPLE

    data class Status(
        val consistency: Int,
        val opportunities: Int,
        val trackedDays: Long,
        val eligible: Boolean
    ) {
        val hasEnoughData: Boolean get() = opportunities >= MIN_SAMPLE
    }

    /**
     * Computes the real eligibility from the opportunity series over the full
     * tracked window (capped at two years), not just the last 30 days.
     */
    fun status(repo: Repository, habit: Habit): Status {
        val today = repo.clock.today()
        val start = SfTime.parseDate(habit.startDate)
            ?: Instant.ofEpochMilli(habit.createdAt).atZone(repo.clock.zone()).toLocalDate()
        val trackedDays = ChronoUnit.DAYS.between(start, today) + 1
        val window = trackedDays.coerceIn(1, 730).toInt()
        val series = Insights.seriesFor(repo, habit, window, today)
        val recurrence = Recurrence.decode(habit.recurrenceRule)
        val (hits, opportunities) = if (recurrence is Recurrence.TimesPerWeek) {
            Opportunities.quotaAdherence(series, recurrence.times)
        } else {
            Opportunities.adherence(series)
        }
        val consistency = if (opportunities == 0) 0 else (hits * 100) / opportunities
        return Status(consistency, opportunities, trackedDays,
            eligible(consistency, trackedDays, opportunities))
    }

    /** Graduation candidates across all active habits. */
    fun candidates(repo: Repository): List<Pair<Habit, Status>> =
        repo.habits().filter { !it.graduated }.map { it to status(repo, it) }
            .filter { (_, s) -> s.eligible }
}
