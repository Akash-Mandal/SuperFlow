package com.superflow.domain

import com.superflow.core.schedule.Opportunities
import com.superflow.core.schedule.Opportunity
import com.superflow.core.schedule.OpportunityStatus
import com.superflow.core.schedule.Recurrence
import com.superflow.core.time.SfTime
import com.superflow.data.Repository
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
 */
object Insights {

    /** Builds the opportunity series for one habit over the last [days] days. */
    fun seriesFor(repo: Repository, habit: Habit, days: Int, today: LocalDate): List<Opportunity> {
        val pauses = repo.pauses().filter { it.habitId == null || it.habitId == habit.id }
        return Opportunities.series(
            habit = habit,
            schedule = repo.scheduleOf(habit),
            checkIns = repo.checkInsOf(habit.id).associateBy { LocalDate.parse(it.date) },
            pauses = pauses,
            dates = SfTime.lastDays(days, today),
            today = today
        )
    }

    fun forHabit(repo: Repository, habit: Habit, today: LocalDate = repo.clock.today()): HabitStats {
        val longSeries = seriesFor(repo, habit, 365, today)
        val window = longSeries.filter { !it.date.isBefore(today.minusDays(29)) }
        val recurrence = Recurrence.decode(habit.recurrenceRule)

        val (hits, opportunities) = if (recurrence is Recurrence.TimesPerWeek) {
            Opportunities.quotaAdherence(window, recurrence.times)
        } else {
            Opportunities.adherence(window)
        }
        val consistency = if (opportunities == 0) 0 else (hits * 100) / opportunities

        val allCheckIns = repo.checkInsOf(habit.id)
        return HabitStats(
            habit = habit,
            repetitions = allCheckIns.count { it.isSuccess },
            currentRun = Opportunities.currentRun(longSeries, today),
            bestRun = Opportunities.bestRun(longSeries),
            consistency30 = consistency,
            opportunities30 = opportunities,
            recoveries = Opportunities.recoveries(longSeries),
            missesInARow = Opportunities.missesInARow(longSeries, today),
            needsReturn = Opportunities.needsReturn(longSeries, today),
            lastDone = allCheckIns.filter { it.isSuccess }.maxByOrNull { it.date }?.date
        )
    }

    fun allStats(repo: Repository, today: LocalDate = repo.clock.today()): List<HabitStats> =
        repo.habits().map { forHabit(repo, it, today) }

    /** Done vs scheduled for one day. */
    fun dayProgress(repo: Repository, date: LocalDate = repo.clock.today()): Pair<Int, Int> {
        val scheduled = repo.habitsForDay(date)
        val checkIns = repo.checkInsFor(SfTime.format(date)).associateBy { it.habitId }
        val done = scheduled.count { checkIns[it.id]?.isSuccess == true }
        return done to scheduled.size
    }

    fun todaySummary(repo: Repository, date: LocalDate = repo.clock.today()): String {
        val (done, total) = dayProgress(repo, date)
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
            sb.append(focus.joinToString(", ") { (if (it.done) "[x] " else "[ ] ") + it.title })
            sb.append('.')
        }
        val checkIns = repo.checkInsFor(iso).associateBy { it.habitId }
        val open = repo.habitsForDay(date).filter { checkIns[it.id] == null }
        if (open.isNotEmpty()) {
            sb.append(" Still open: ").append(open.take(4).joinToString(", ") { it.title })
            if (open.size > 4) sb.append(" and ${open.size - 4} more")
            sb.append('.')
        }
        val returning = repo.returnCandidates(date)
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
        val stats = allStats(repo, today)
        if (stats.isEmpty()) return "No habits yet. Create one and the insights will fill in."

        val window = SfTime.lastDays(days, today).map { SfTime.format(it) }.toSet()
        val checkIns = repo.checkIns().filter { it.date in window }
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
        for (i in repo.identities()) {
            val votes = stats.filter { it.habit.identityId == i.id }.sumOf { it.repetitions }
            sb.append("· ${i.statement}: $votes votes\n")
        }
        return sb.toString().trim()
    }

    /** Identity evidence ledger: statement, votes, linked habit count. */
    fun identityEvidence(repo: Repository): List<Triple<String, Int, Int>> =
        repo.identities().map { i ->
            val linked = repo.habits(true).filter { it.identityId == i.id }
            val votes = linked.sumOf { h -> repo.checkInsOf(h.id).count { it.isSuccess } }
            Triple(i.statement, votes, linked.size)
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
        repo.habits().filter { it.mode == HabitMode.REDUCE }.map { h ->
            val cis = repo.checkInsOf(h.id)
            Triple(
                h.title,
                cis.count { it.result == CheckInResult.RESISTED },
                cis.count { it.result == CheckInResult.SLIPPED }
            )
        }

    /** Per-habit day states for the history strip. */
    fun historyStates(
        repo: Repository,
        habit: Habit,
        days: Int = 14,
        today: LocalDate = repo.clock.today()
    ): List<Int> = seriesFor(repo, habit, days, today).map {
        when (it.status) {
            OpportunityStatus.COMPLETED -> 1
            OpportunityStatus.MISSED -> -1
            OpportunityStatus.SKIPPED_PLANNED -> -2
            OpportunityStatus.NOT_SCHEDULED, OpportunityStatus.PAUSED -> -3
            OpportunityStatus.PENDING -> 0
        }
    }

    /* ────────────────────────────────────────────── NEW ANALYSIS METHODS ── */

    /**
     * Time-of-day pattern analysis (Section 9.1).
     *
     * Returns a string showing what % of morning vs evening habits are
     * completed, plus day-of-week patterns.
     */
    fun analyzePatterns(repo: Repository, today: LocalDate = repo.clock.today()): String {
        val allCheckIns = repo.checkInsBetween(
            SfTime.format(today.minusDays(60)),
            SfTime.format(today)
        )
        if (allCheckIns.size < 10) {
            return "Need at least 10 check-ins to detect patterns. " +
                    "(${allCheckIns.size} so far — too few to read anything into.)"
        }

        val habits = repo.habits().associateBy { it.id }

        // Group by time-of-day bucket
        val morningHits = allCheckIns.count { ci ->
            val h = habits[ci.habitId] ?: return@count false
            val mins = SfTime.minutesOfDay(h.cueTime)
            mins in 0..(11 * 60)
        }
        val eveningHits = allCheckIns.count { ci ->
            val h = habits[ci.habitId] ?: return@count false
            val mins = SfTime.minutesOfDay(h.cueTime)
            mins > (17 * 60)
        }
        val totalMorningOpportunities = habits.values.count {
            SfTime.minutesOfDay(it.cueTime) in 0..(11 * 60)
        } * 60
        val totalEveningOpportunities = habits.values.count {
            SfTime.minutesOfDay(it.cueTime) > (17 * 60)
        } * 60

        val sb = StringBuilder()
        sb.append("Time-of-day patterns (last 60 days):\n")
        if (totalMorningOpportunities > 0) {
            sb.append("  Morning habits: ${(morningHits * 100) / totalMorningOpportunities}%\n")
        }
        if (totalEveningOpportunities > 0) {
            sb.append("  Evening habits: ${(eveningHits * 100) / totalEveningOpportunities}%\n")
        }

        // Day-of-week patterns
        val dayHits = mutableMapOf<String, Int>()
        val dayTotal = mutableMapOf<String, Int>()
        java.time.DayOfWeek.values().forEach { dw ->
            dayHits[dw.name] = 0
            dayTotal[dw.name] = 0
        }
        allCheckIns.forEach { ci ->
            val d = java.time.LocalDate.parse(ci.date).dayOfWeek.name
            dayTotal[d] = (dayTotal[d] ?: 0) + 1
            if (ci.isSuccess) dayHits[d] = (dayHits[d] ?: 0) + 1
        }
        sb.append("\nDay-of-week patterns:\n")
        dayHits.forEach { (day, hits) ->
            val total = dayTotal[day] ?: 0
            if (total > 0) sb.append("  ${day.lowercase().replaceFirstChar { it.uppercase() }}: ${(hits * 100) / total}%\n")
        }
        return sb.toString()
    }

    /**
     * Find habit-to-habit correlations (Section 9.1).
     */
    fun analyzeCorrelations(repo: Repository, today: LocalDate = repo.clock.today()): String {
        val checkIns = repo.checkInsBetween(
            SfTime.format(today.minusDays(60)),
            SfTime.format(today)
        )
        if (checkIns.size < 30) {
            return "Need at least 30 check-ins to find correlations. " +
                    "(${checkIns.size} so far.)"
        }

        val byDate = checkIns.groupBy { it.date }
        val habits = repo.habits().take(10) // Top 10 to avoid N² explosion

        val sb = StringBuilder()
        sb.append("Habit correlations (last 60 days):\n")
        var found = 0
        for (i in habits.indices) {
            for (j in (i + 1) until habits.size) {
                val a = habits[i].id
                val b = habits[j].id
                var aOnly = 0; var bOnly = 0; var both = 0; var neither = 0
                byDate.forEach { (_, cis) ->
                    val aSuccess = cis.any { it.habitId == a && it.isSuccess }
                    val bSuccess = cis.any { it.habitId == b && it.isSuccess }
                    when {
                        aSuccess && bSuccess -> both++
                        aSuccess -> aOnly++
                        bSuccess -> bOnly++
                        else -> neither++
                    }
                }
                if (both > 5 && both.toDouble() / (both + aOnly) > 0.7) {
                    sb.append("  When you do "${habits[i].title}", " +
                            "you do "${habits[j].title}" ${(both.toDouble() / (both + aOnly + bOnly) * 100).toInt()}% of the time.\n")
                    found++
                }
            }
            if (found >= 5) break  // Limit output
        }
        if (found == 0) sb.append("  No strong correlations yet.\n")
        return sb.toString()
    }

    /**
     * Predict next week's consistency per habit based on recent trend.
     */
    fun predictConsistency(repo: Repository, today: LocalDate = repo.clock.today()): String {
        val sb = StringBuilder()
        sb.append("Predicted consistency for next 7 days:\n")
        for (habit in repo.habits()) {
            val stats = forHabit(repo, habit, today)
            if (!stats.hasEnoughData) {
                sb.append("  ${habit.title}: not enough data\n")
                continue
            }
            // Simple prediction: average recent consistency
            val recent = repo.checkInsOf(habit.id)
                .filter { LocalDate.parse(it.date).isAfter(today.minusDays(14)) }
            val successes = recent.count { it.isSuccess }
            val total = recent.size
            val predicted = if (total == 0) stats.consistency30 else (successes * 100) / total
            val trend = if (predicted > stats.consistency30) "improving"
                else if (predicted < stats.consistency30 - 5) "declining"
                else "steady"
            sb.append("  ${habit.title}: ~${predicted}% ($trend)\n")
        }
        return sb.toString()
    }

    /**
     * Recovery speed: average time from a miss to the next completion.
     */
    fun recoverySpeed(repo: Repository, today: LocalDate = repo.clock.today()): String {
        val sb = StringBuilder()
        sb.append("Recovery speed:\n")
        var any = false
        for (habit in repo.habits()) {
            val cis = repo.checkInsOf(habit.id)
            if (cis.size < 5) continue
            any = true
            val sorted = cis.sortedBy { it.date }
            var recoveries = 0
            var totalGap = 0
            for (i in 1 until sorted.size) {
                val prev = sorted[i - 1]
                val curr = sorted[i]
                if (prev.isMiss && curr.isSuccess) {
                    recoveries++
                    totalGap += java.time.temporal.ChronoUnit.DAYS.between(
                        java.time.LocalDate.parse(prev.date),
                        java.time.LocalDate.parse(curr.date)
                    )
                }
            }
            val avgGap = if (recoveries > 0) totalGap.toDouble() / recoveries else 0.0
            val trend = if (avgGap < 2.0) "fast" else if (avgGap < 3.5) "moderate" else "slow"
            sb.append("  ${habit.title}: ~${"%.1f".format(avgGap)} days from miss to return ($trend, ${recoveries} recoveries)\n")
        }
        if (!any) sb.append("  Not enough data yet.\n")
        return sb.toString()
    }

    /**
     * Seasonal trend analysis.
     */
    fun seasonalTrends(repo: Repository, today: LocalDate = repo.clock.today()): String {
        val sb = StringBuilder()
        val checkIns = repo.checkInsBetween(
            SfTime.format(today.minusDays(180)),
            SfTime.format(today)
        )
        if (checkIns.size < 30) {
            return "Need at least 30 check-ins over 6 months to find seasonal patterns. " +
                    "(${checkIns.size} so far.)"
        }
        val byMonth = checkIns.groupBy {
            java.time.LocalDate.parse(it.date).month.name
        }
        sb.append("Seasonal trends (last 6 months):\n")
        for (month in java.time.Month.values()) {
            val cis = byMonth[month.name] ?: emptyList()
            if (cis.isEmpty()) continue
            val successes = cis.count { it.isSuccess }
            val pct = (successes * 100) / cis.size
            sb.append("  ${month.name.lowercase().replaceFirstChar { it.uppercase() }}: ${pct}%\n")
        }
        return sb.toString()
    }

    /**
     * Optimal habit ordering — when one habit precedes another, success goes up.
     */
    fun optimalOrdering(repo: Repository, today: LocalDate = repo.clock.today()): String {
        val checkIns = repo.checkInsBetween(
            SfTime.format(today.minusDays(60)),
            SfTime.format(today)
        )
        if (checkIns.size < 30) return "Need more data to detect ordering patterns."

        val byDate = checkIns.groupBy { it.date }
        val habits = repo.habits().take(8)
        val sb = StringBuilder()
        sb.append("Optimal habit ordering hints:\n")

        // Find pairs where habit A done before habit B has higher success rate
        var found = 0
        for (a in habits) {
            for (b in habits) {
                if (a.id == b.id) continue
                var both = 0; var bAfterA = 0; var bWithoutA = 0
                byDate.forEach { (_, cis) ->
                    val aDone = cis.any { it.habitId == a.id && it.isSuccess }
                    val bDone = cis.any { it.habitId == b.id && it.isSuccess }
                    if (aDone && bDone) both++
                    else if (aDone) bAfterA++
                    else if (bDone) bWithoutA++
                }
                if (both >= 3 && (both.toDouble() / (both + bWithoutA)) > 0.6 &&
                    bAfterA > bWithoutA * 1.5) {
                    sb.append("  ${a.title} before ${b.title} helps.\n")
                    found++
                }
                if (found >= 3) break
            }
            if (found >= 3) break
        }
        if (found == 0) sb.append("  No strong ordering signals yet.\n")
        return sb.toString()
    }

    /**
     * Energy-aware scheduling recommendation.
     */
    fun energyAwareSchedule(repo: Repository, today: LocalDate = repo.clock.today()): String {
        if (!repo.profile().toString().contains("""""""")) {
            val energyLogs = repo.energyLogs()
            if (energyLogs.size < 10) {
                return "Need at least 10 energy logs to recommend a schedule. " +
                        "(${energyLogs.size} so far.)"
            }
            val morning = energyLogs.filter { it.checkpoint == com.superflow.data.model.Checkpoint.MORNING }
                .map { it.energy.toDouble() }.average()
            val evening = energyLogs.filter { it.checkpoint == com.superflow.data.model.Checkpoint.EVENING }
                .map { it.energy.toDouble() }.average()
            val sb = StringBuilder()
            sb.append("Energy pattern:\n")
            sb.append("  Morning average: ${"%.1f".format(morning)}/5\n")
            sb.append("  Evening average: ${"%.1f".format(evening)}/5\n")
            sb.append("\n")
            if (morning > evening + 1.0) {
                val eveningHabits = repo.habits().filter {
                    it.cueTime.isNotBlank() && SfTime.minutesOfDay(it.cueTime) > 17 * 60
                }
                sb.append("Recommendation: Your energy is higher in the morning. " +
                        "Consider moving evening habits earlier.\n")
                eveningHabits.take(3).forEach { sb.append("  • ${it.title} (currently ${it.cueTime})\n") }
            } else if (evening > morning + 1.0) {
                sb.append("Recommendation: Your energy is higher in the evening. " +
                        "Stack difficult habits later in the day.\n")
            } else {
                sb.append("Recommendation: Energy is balanced. Trust your schedule.\n")
            }
            return sb.toString()
        }
        return "Energy pattern unavailable."
    }
}
