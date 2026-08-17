package com.superflow.core.schedule

import com.superflow.data.model.CheckIn
import com.superflow.data.model.CheckInResult
import com.superflow.data.model.Habit
import com.superflow.data.model.PauseWindow
import java.time.LocalDate

/**
 * Habit opportunities.
 *
 * The plan treats an opportunity as a first-class object: a moment the habit
 * was scheduled and could have happened. Adherence, runs and "misses" are all
 * derived from opportunities plus check-ins, never stored.
 *
 * Two rules from the plan matter most here:
 *   - Planned skips and pause dates do not create misses.
 *   - Flexible habits ("3× a week") are judged against their weekly quota,
 *     not against particular weekdays.
 */
enum class OpportunityStatus { PENDING, COMPLETED, SKIPPED_PLANNED, MISSED, PAUSED, NOT_SCHEDULED }

data class Opportunity(
    val habitId: String,
    val date: LocalDate,
    val status: OpportunityStatus,
    val checkIn: CheckIn?,
    val scheduleVersion: Int
) {
    val counts: Boolean get() = status != OpportunityStatus.NOT_SCHEDULED &&
            status != OpportunityStatus.PAUSED
    val succeeded: Boolean get() = status == OpportunityStatus.COMPLETED
}

object Opportunities {

    /**
     * Builds the opportunity series for one habit over [dates].
     *
     * [today] is needed because a scheduled day that has not finished yet is
     * PENDING, not MISSED — the app must never accuse someone of missing a
     * habit that is still ahead of them.
     */
    fun series(
        habit: Habit,
        schedule: Schedule,
        checkIns: Map<LocalDate, CheckIn>,
        pauses: List<PauseWindow>,
        dates: List<LocalDate>,
        today: LocalDate
    ): List<Opportunity> = dates.map { date ->
        val checkIn = checkIns[date]
        val paused = pauses.any { it.covers(date) }
        val scheduled = schedule.activeOn(date)

        val status = when {
            checkIn != null && checkIn.isSuccess -> OpportunityStatus.COMPLETED
            checkIn != null && checkIn.result == CheckInResult.SKIPPED ->
                OpportunityStatus.SKIPPED_PLANNED
            checkIn != null && checkIn.isMiss -> OpportunityStatus.MISSED
            paused -> OpportunityStatus.PAUSED
            !scheduled -> OpportunityStatus.NOT_SCHEDULED
            date.isAfter(today) -> OpportunityStatus.PENDING
            date == today -> OpportunityStatus.PENDING
            // A past scheduled day with no record is an implicit miss.
            else -> OpportunityStatus.MISSED
        }
        Opportunity(habit.id, date, status, checkIn, schedule.version)
    }

    /**
     * Adherence for a fixed-day habit: successes over real opportunities.
     * Paused days, unscheduled days, planned skips and future days are all
     * excluded from the denominator.
     */
    fun adherence(series: List<Opportunity>): Pair<Int, Int> {
        val relevant = series.filter {
            it.status == OpportunityStatus.COMPLETED || it.status == OpportunityStatus.MISSED
        }
        return relevant.count { it.succeeded } to relevant.size
    }

    /**
     * Adherence for a flexible habit: successes against the weekly quota,
     * summed over the whole window.
     */
    fun quotaAdherence(series: List<Opportunity>, timesPerWeek: Int): Pair<Int, Int> {
        if (series.isEmpty()) return 0 to 0
        val byWeek = series.groupBy {
            it.date.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        }
        var hits = 0
        var target = 0
        for ((_, week) in byWeek) {
            val usable = week.count { it.status != OpportunityStatus.PAUSED }
            if (usable == 0) continue
            // A partial week at either edge of the window only owes a
            // pro-rated share of the quota, so a 10-day view never invents
            // two full weeks of obligation.
            val expected = if (usable >= 7) timesPerWeek
            else Math.round(timesPerWeek * usable / 7.0).toInt().coerceAtLeast(0)
            if (expected == 0) continue
            hits += minOf(week.count { it.succeeded }, expected)
            target += expected
        }
        return hits to target
    }

    /**
     * The current run: consecutive successful opportunities working backwards.
     *
     * An intentional skip preserves the run (it is a choice, not a failure);
     * a paused or unscheduled day is transparent; a miss ends it. Today counts
     * only once it has actually been acted on.
     */
    fun currentRun(series: List<Opportunity>, today: LocalDate): Int {
        var run = 0
        for (op in series.sortedByDescending { it.date }) {
            if (op.date.isAfter(today)) continue
            when (op.status) {
                OpportunityStatus.COMPLETED -> run++
                OpportunityStatus.SKIPPED_PLANNED,
                OpportunityStatus.PAUSED,
                OpportunityStatus.NOT_SCHEDULED -> Unit
                OpportunityStatus.PENDING -> if (op.date != today) return run
                OpportunityStatus.MISSED -> return run
            }
        }
        return run
    }

    fun bestRun(series: List<Opportunity>): Int {
        var best = 0
        var run = 0
        for (op in series.sortedBy { it.date }) {
            when (op.status) {
                OpportunityStatus.COMPLETED -> { run++; if (run > best) best = run }
                OpportunityStatus.SKIPPED_PLANNED,
                OpportunityStatus.PAUSED,
                OpportunityStatus.NOT_SCHEDULED,
                OpportunityStatus.PENDING -> Unit
                OpportunityStatus.MISSED -> run = 0
            }
        }
        return best
    }

    /** A recovery is a success at the first real opportunity after a miss. */
    fun recoveries(series: List<Opportunity>): Int {
        val ordered = series.filter { it.counts }.sortedBy { it.date }
        var count = 0
        var sawMiss = false
        for (op in ordered) {
            when (op.status) {
                OpportunityStatus.MISSED -> sawMiss = true
                OpportunityStatus.COMPLETED -> { if (sawMiss) count++; sawMiss = false }
                else -> Unit
            }
        }
        return count
    }

    /** Consecutive misses at the most recent real opportunities. */
    fun missesInARow(series: List<Opportunity>, today: LocalDate): Int {
        var count = 0
        for (op in series.sortedByDescending { it.date }) {
            if (op.date.isAfter(today)) continue
            when (op.status) {
                OpportunityStatus.MISSED -> count++
                OpportunityStatus.PENDING,
                OpportunityStatus.PAUSED,
                OpportunityStatus.NOT_SCHEDULED -> Unit
                else -> return count
            }
        }
        return count
    }

    /**
     * True when the previous real opportunity was missed and today is still
     * open — the "never miss twice" trigger.
     */
    fun needsReturn(series: List<Opportunity>, today: LocalDate): Boolean {
        val todayOp = series.firstOrNull { it.date == today } ?: return false
        if (todayOp.status != OpportunityStatus.PENDING) return false
        val previous = series
            .filter { it.date.isBefore(today) && it.counts }
            .maxByOrNull { it.date } ?: return false
        return previous.status == OpportunityStatus.MISSED
    }
}
