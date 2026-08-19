package com.superflow.domain

import com.superflow.core.schedule.Opportunities
import com.superflow.core.schedule.Opportunity
import com.superflow.core.schedule.OpportunityStatus
import com.superflow.core.schedule.Recurrence
import com.superflow.core.time.SfTime
import com.superflow.data.Repository
import com.superflow.data.Repository.DataSnapshot
import com.superflow.data.model.CheckIn
import com.superflow.data.model.CheckInResult
import com.superflow.data.model.Habit
import com.superflow.data.model.HabitMode
import com.superflow.data.model.HabitStats
import java.time.LocalDate

/**
 * Honest progress maths, derived from opportunities.
 *
 * Per the plan: streaks are never stored, planned skips and pauses never create
 * misses, flexible habits are judged against a weekly quota, and every
 * consistency figure carries its sample size so small samples can be disclosed.
 *
 * All public entry points take either a [Repository] (which takes one
 * [Repository.snapshot] internally) or a preloaded [DataSnapshot], so a
 * screen build reads each table exactly once.
 */
object Insights {

    /** Builds the opportunity series for one habit over the last [days] days. */
    fun seriesFor(repo: Repository, habit: Habit, days: Int, today: LocalDate): List<Opportunity> {
        val snap = repo.snapshot()
        return seriesFor(snap, repo, habit, days, today)
    }

    fun seriesFor(
        snap: DataSnapshot,
        repo: Repository,
        habit: Habit,
        days: Int,
        today: LocalDate
    ): List<Opportunity> {
        val pauses = snap.pauses.filter { it.habitId == null || it.habitId == habit.id }
        val checkIns = snap.checkInsByHabit[habit.id].orEmpty().associateBy { LocalDate.parse(it.date) }
        return Opportunities.series(
            habit = habit,
            schedule = repo.scheduleOf(habit),
            checkIns = checkIns,
            pauses = pauses,
            dates = SfTime.lastDays(days, today),
            today = today
        )
    }

    fun forHabit(repo: Repository, habit: Habit, today: LocalDate = repo.clock.today()): HabitStats =
        forHabit(repo.snapshot(), repo, habit, today)

    fun forHabit(snap: DataSnapshot, repo: Repository, habit: Habit, today: LocalDate): HabitStats {
        val pauses = snap.pauses.filter { it.habitId == null || it.habitId == habit.id }
        val checkIns = snap.checkInsByHabit[habit.id].orEmpty()
        val checkInsByDate = checkIns.associateBy { LocalDate.parse(it.date) }
        val longSeries = Opportunities.series(
            habit = habit,
            schedule = repo.scheduleOf(habit),
            checkIns = checkInsByDate,
            pauses = pauses,
            dates = SfTime.lastDays(365, today),
            today = today
        )
        val window = longSeries.filter { !it.date.isBefore(today.minusDays(29)) }
        val recurrence = Recurrence.decode(habit.recurrenceRule)

        val (hits, opportunities) = if (recurrence is Recurrence.TimesPerWeek) {
            Opportunities.quotaAdherence(window, recurrence.times)
        } else {
            Opportunities.adherence(window)
        }
        val consistency = if (opportunities == 0) 0 else (hits * 100) / opportunities

        val successes = checkIns.filter { it.isSuccess }
        return HabitStats(
            habit = habit,
            repetitions = successes.size,
            currentRun = Opportunities.currentRun(longSeries, today),
            bestRun = Opportunities.bestRun(longSeries),
            consistency30 = consistency,
            opportunities30 = opportunities,
            recoveries = Opportunities.recoveries(longSeries),
            missesInARow = Opportunities.missesInARow(longSeries, today),
            needsReturn = Opportunities.needsReturn(longSeries, today),
            lastDone = successes.maxByOrNull { it.date }?.date
        )
    }

    fun allStats(repo: Repository, today: LocalDate = repo.clock.today()): List<HabitStats> =
        allStats(repo.snapshot(), repo, today)

    fun allStats(snap: DataSnapshot, repo: Repository, today: LocalDate): List<HabitStats> =
        snap.activeHabits.map { forHabit(snap, repo, it, today) }

    /** Done vs scheduled for one day. */
    fun dayProgress(repo: Repository, date: LocalDate = repo.clock.today()): Pair<Int, Int> =
        dayProgress(repo.snapshot(), repo, date)

    fun dayProgress(snap: DataSnapshot, repo: Repository, date: LocalDate): Pair<Int, Int> {
        val scheduled = snap.activeHabits.filter { repo.scheduleOf(it).activeOn(date) }
        val iso = SfTime.format(date)
        val checkIns = snap.checkIns.filter { it.date == iso }.associateBy { it.habitId }
        val done = scheduled.count { checkIns[it.id]?.isSuccess == true }
        return done to scheduled.size
    }

    fun todaySummary(repo: Repository, date: LocalDate = repo.clock.today()): String {
        val snap = repo.snapshot()
        val (done, total) = dayProgress(snap, repo, date)
        if (total == 0) {
            return "Nothing is scheduled for ${SfTime.humanDay(date)}. A quiet day is allowed."
        }
        val iso = SfTime.format(date)
        val focus = repo.focusFor(iso)
        val sb = StringBuilder()
        sb.append("${SfTime.humanDay(date)}: $done of $total actions done.")
        if (focus.isNotEmpty()) {
            val fdone = focus.count { it.done }
            sb.append(" Daily Focus $fdone/${focus.size}: ")
            sb.append(focus.joinToString(", ") { (if (it.done) "[x] " else " [ ] ") + it.title })
            sb.append('.')
        }
        val checkIns = snap.checkIns.filter { it.date == iso }.associateBy { it.habitId }
        val open = snap.activeHabits
            .filter { repo.scheduleOf(it).activeOn(date) }
            .filter { checkIns[it.id] == null }
        if (open.isNotEmpty()) {
            sb.append(" Still open: ").append(open.take(4).joinToString(", ") { it.title })
            if (open.size > 4) sb.append(" and ${open.size - 4} more")
            sb.append('.')
        }
        val returning = returnCandidates(snap, repo, date)
        if (returning.isNotEmpty()) {
            sb.append(" Return today: ").append(returning.joinToString(", ") { it.title }).append('.')
        }
        return sb.toString()
    }

    fun summaryText(
        repo: Repository,
        days: Int = 30,
        today: LocalDate = repo.clock.today()
    ): String {
        val snap = repo.snapshot()
        val stats = allStats(snap, repo, today)
        if (stats.isEmpty()) return "No habits yet. Create one and the insights will fill in."

        val window = SfTime.lastDays(days, today).map { SfTime.format(it) }.toSet()
        val checkIns = snap.checkIns.filter { it.date in window }
        val successes = checkIns.count { it.isSuccess }
        val misses = checkIns.count { it.isMiss }
        val skips = checkIns.count { it.result == CheckInResult.SKIPPED }
        val recoveries = stats.sumOf { it.recoveries }

        val sb = StringBuilder()
        sb.append("Last $days days\n")
        sb.append("Repetitions: $successes\n")
        sb.append("Intentional skips: $skips\n")
        sb.append("Misses: $misses\n")
        sb.append("Recoveries after a miss: $recoveries\n")

        val rated = stats.filter { it.hasEnoughData }
        rated.maxByOrNull { it.consistency30 }?.let {
            sb.append("Most consistent: ${it.habit.title} (${it.consistency30}%")
            sb.append(" of ${it.opportunities30} opportunities)\n")
        }
        rated.filter { it.consistency30 < 40 }.minByOrNull { it.consistency30 }?.let {
            sb.append("Worth redesigning: ${it.habit.title} (${it.consistency30}%) — try shrinking it\n")
        }
        if (rated.size < stats.size) {
            sb.append("(${stats.size - rated.size} habits have too few opportunities to rate yet.)\n")
        }

        sb.append("\nIdentity evidence:\n")
        for (i in snap.identities) {
            val votes = stats.filter { it.habit.identityId == i.id }.sumOf { it.repetitions }
            sb.append("· ${i.statement}: $votes votes\n")
        }
        return sb.toString().trim()
    }

    /** Identity evidence ledger: statement, votes, linked habit count. */
    fun identityEvidence(repo: Repository): List<Triple<String, Int, Int>> =
        identityEvidence(repo.snapshot())

    fun identityEvidence(snap: DataSnapshot): List<Triple<String, Int, Int>> {
        val votesByHabit = snap.checkInsByHabit.mapValues { entry ->
            entry.value.count { it.isSuccess }
        }
        return snap.identities.map { i ->
            val linked = snap.habits.filter { it.identityId == i.id }
            val votes = linked.sumOf { votesByHabit[it.id] ?: 0 }
            Triple(i.statement, votes, linked.size)
        }
    }

    /** Energy pattern, always disclosing the sample size. */
    fun energyPattern(repo: Repository): String {
        val logs = repo.energyLogs()
        if (logs.size < 6) {
            return "Log energy at a few checkpoints and a pattern will appear here. " +
                    "(${logs.size} entries so far — too few to read anything into.)"
        }
        val sb = StringBuilder("Average energy by checkpoint (${logs.size} entries):\n")
        for ((cp, list) in logs.groupBy { it.checkpoint }) {
            val avg = list.sumOf { it.energy }.toDouble() / list.size
            sb.append("· ${cp.label}: ${"%.1f".format(avg)}/5 from ${list.size} entries\n")
        }
        sb.append("Small samples move a lot. Treat this as a hint, not a rule.")
        return sb.toString()
    }

    /** Successes per day for the bar chart. */
    fun dailyCounts(
        repo: Repository,
        days: Int = 7,
        today: LocalDate = repo.clock.today()
    ): List<Pair<LocalDate, Int>> {
        val dates = SfTime.lastDays(days, today)
        val all = repo.checkInsBetween(SfTime.format(dates.first()), SfTime.format(dates.last()))
        return dates.map { d ->
            val iso = SfTime.format(d)
            d to all.count { it.date == iso && it.isSuccess }
        }
    }

    fun reduceModeProgress(repo: Repository): List<Triple<String, Int, Int>> =
        reduceModeProgress(repo.snapshot())

    fun reduceModeProgress(snap: DataSnapshot): List<Triple<String, Int, Int>> {
        val byHabit = snap.checkInsByHabit
        return snap.activeHabits.filter { it.mode == HabitMode.REDUCE }.map { h ->
            val cis = byHabit[h.id].orEmpty()
            Triple(
                h.title,
                cis.count { it.result == CheckInResult.RESISTED },
                cis.count { it.result == CheckInResult.SLIPPED }
            )
        }
    }

    /** Per-habit day states for the history strip. */
    fun historyStates(
        repo: Repository,
        habit: Habit,
        days: Int = 14,
        today: LocalDate = repo.clock.today()
    ): List<Int> = historyStates(repo.snapshot(), repo, habit, days, today)

    fun historyStates(
        snap: DataSnapshot,
        repo: Repository,
        habit: Habit,
        days: Int = 14,
        today: LocalDate
    ): List<Int> = seriesFor(snap, repo, habit, days, today).map {
        when (it.status) {
            OpportunityStatus.COMPLETED -> 1
            OpportunityStatus.MISSED -> -1
            OpportunityStatus.SKIPPED_PLANNED -> -2
            OpportunityStatus.NOT_SCHEDULED, OpportunityStatus.PAUSED -> -3
            OpportunityStatus.PENDING -> 0
        }
    }

    /**
     * Habits missed at their previous real opportunity: the never-miss-twice
     * trigger. Snapshot-based so the Today screen does not re-query check-ins
     * and pauses for every habit.
     */
    fun returnCandidates(
        snap: DataSnapshot,
        repo: Repository,
        date: LocalDate
    ): List<Habit> {
        val dates = SfTime.lastDays(30, date)
        return snap.activeHabits
            .filter { repo.scheduleOf(it).activeOn(date) }
            .filter { h ->
                val pauses = snap.pauses.filter { it.habitId == null || it.habitId == h.id }
                val checkIns = snap.checkInsByHabit[h.id].orEmpty().associateBy { LocalDate.parse(it.date) }
                val series = Opportunities.series(
                    habit = h,
                    schedule = repo.scheduleOf(h),
                    checkIns = checkIns,
                    pauses = pauses,
                    dates = dates,
                    today = date
                )
                Opportunities.needsReturn(series, date)
            }
    }
}
