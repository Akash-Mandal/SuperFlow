package com.superflow.domain

@file:Suppress("LargeClass", "TooManyFunctions", "HardcodedText")


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
import com.superflow.data.model.Identity
import com.superflow.data.model.Level
import com.superflow.data.model.Sys
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
        InsightsCache.forHabit(repo, habit, today)

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

    /**
     * Public for [InsightsCache]; other callers should use [forHabit] so they
     * benefit from caching.
     */
    internal fun computeForHabit(repo: Repository, habit: Habit, today: LocalDate): HabitStats {
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
        InsightsCache.allStats(repo, today)

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

        // Recovery celebration (§8): a check-in today after a miss is a comeback.
        val recoveries = repo.habitsForDay(date).filter { isRecovery(repo, it, date) }
        if (recoveries.isNotEmpty()) {
            sb.append(" You came back after a miss: ").append(recoveries.joinToString(", ") { it.title })
                .append(". That is the skill that matters most.")
        }

        // Identity evolution prompt (§1): ~30 days of living an identity.
        val due = identityReviewDue(repo)
        if (due.isNotEmpty()) {
            sb.append(" Review due: ")
            sb.append(due.joinToString(", ") { "\"${it.statement}\"" })
            sb.append(" — is it still who you are becoming?")
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

        val reasons = missReasons(repo, days)
        if (reasons.isNotEmpty()) {
            sb.append("\nTop miss reasons:\n")
            val totalMisses = reasons.sumOf { it.second }
            reasons.take(3).forEach { (reason, count) ->
                val pct = (count * 100) / totalMisses
                sb.append("· $reason ($pct%)\n")
            }
            val top = reasons.firstOrNull()
            if (top != null) {
                val advice = when (top.first) {
                    "time" -> "time-blocking or pairing the habit with an existing routine."
                    "energy" -> "scheduling the habit at your naturally higher-energy time."
                    "forgot" -> "a stronger cue — time, place, or a visual trigger."
                    "motivation" -> "shrinking the habit until it feels almost too easy."
                    else -> "a specific obstacle plan for this pattern."
                }
                sb.append("Consider: $advice\n")
            }
        }

        val corr = energyCorrelation(repo, days)
        if (corr.isNotBlank() && repo.energyLogs().size >= 6) {
            sb.append("\n").append(corr).append('\n')
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

    /* ------------------------------------------- Core Growth Systems analytics */

    /**
     * System health score (§3): average 30-day consistency of the habits under
     * a system, weighted by how many opportunities each habit had.
     */
    fun systemHealth(repo: Repository, system: Sys): Int {
        val habits = repo.habits().filter { it.systemId == system.id }
        if (habits.isEmpty()) return 0
        var weighted = 0
        var totalOpp = 0
        for (h in habits) {
            val stats = forHabit(repo, h)
            weighted += stats.consistency30 * stats.opportunities30
            totalOpp += stats.opportunities30
        }
        return if (totalOpp == 0) 0 else (weighted / totalOpp).coerceIn(0, 100)
    }

    /** All systems with their derived health, sorted best first. */
    fun systemHealthAll(repo: Repository): List<Triple<String, Int, Int>> =
        repo.systems().map { s ->
            Triple(s.title, systemHealth(repo, s), repo.habits().count { it.systemId == s.id })
        }

    /** Daily load (§15): count, estimated minutes, average difficulty, score. */
    fun dailyLoad(repo: Repository, date: LocalDate = repo.clock.today()):
            Triple<Int, Int, Double> {
        val habits = repo.habitsForDay(date)
        if (habits.isEmpty()) return Triple(0, 0, 0.0)
        val minutes = habits.sumOf { it.estimatedMinutes }
        val score = habits.size * habits.map { it.difficultyRating }.average()
        return Triple(habits.size, minutes, score)
    }

    /** Miss reason distribution (§8): reason -> count, for the Insights tab. */
    fun missReasons(repo: Repository, days: Int = 30): List<Pair<String, Int>> {
        val window = SfTime.lastDays(days, repo.clock.today()).map { SfTime.format(it) }.toSet()
        return repo.checkIns().filter { it.date in window && it.isMiss }
            .mapNotNull { it.missReason }
            .groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }
            .map { it.key to it.value }
    }

    /**
     * Day-of-week miss pattern (§8): for a habit, which weekdays have a miss
     * rate above the habit's own average. Returns "MON" -> (misses, opportunities).
     */
    fun weekdayPattern(repo: Repository, habit: Habit, today: LocalDate = repo.clock.today()):
            List<Pair<String, Pair<Int, Int>>> {
        val series = seriesFor(repo, habit, 84, today)  // ~12 weeks
        val byDow = series.groupBy { it.date.dayOfWeek.value }
        return byDow.map { (dow, opps) ->
            val misses = opps.count { it.status == OpportunityStatus.MISSED }
            val total = opps.size
            val label = java.time.DayOfWeek.of(dow).toString().take(3)
            label to (misses to total)
        }.sortedBy { it.first }
    }

    /**
     * Energy-habit correlation (§14): completion % on high-energy days vs
     * low-energy days, using the day's earliest logged energy.
     */
    fun energyCorrelation(repo: Repository, days: Int = 30): String {
        val today = repo.clock.today()
        val window = SfTime.lastDays(days, today).map { SfTime.format(it) }.toSet()
        val energyByDay = repo.energyLogs().filter { it.date in window }
            .groupBy { it.date }.mapValues { (_, logs) -> logs.minOf { it.energy } }
        if (energyByDay.size < 6) {
            return "Log energy at checkpoints for a few days and a correlation will appear here. " +
                    "(${energyByDay.size} days logged so far.)"
        }
        var highDays = 0; var highDone = 0; var highTotal = 0
        var lowDays = 0; var lowDone = 0; var lowTotal = 0
        for ((date, energy) in energyByDay) {
            val day = runCatching { LocalDate.parse(date) }.getOrNull() ?: continue
            val (done, total) = dayProgress(repo, day)
            if (energy >= 4) { highDays++; highDone += done; highTotal += total }
            else if (energy <= 2) { lowDays++; lowDone += done; lowTotal += total }
        }
        val highPct = if (highTotal == 0) null else (highDone * 100) / highTotal
        val lowPct = if (lowTotal == 0) null else (lowDone * 100) / lowTotal
        return buildString {
            append("Energy-habit correlation ($days days, ${energyByDay.size} with energy logged):\n")
            if (highPct != null) append("· High-energy days (>=4): $highPct% of habits done\n")
            if (lowPct != null) append("· Low-energy days (<=2): $lowPct% of habits done\n")
            if (highPct != null && lowPct != null && highPct > lowPct + 10) {
                append("Harder habits may fit better on high-energy days.")
            }
        }
    }

    /**
     * Data-driven review pre-fill (§9): real stats for the period, ready to
     * paste into a review so the user reflects on evidence, not memory.
     */
    fun reviewData(repo: Repository, kind: com.superflow.data.model.ReviewKind): String {
        val today = repo.clock.today()
        val days = when (kind) {
            com.superflow.data.model.ReviewKind.WEEKLY -> 7
            com.superflow.data.model.ReviewKind.MONTHLY -> 30
            com.superflow.data.model.ReviewKind.QUARTERLY -> 90
        }
        val stats = allStats(repo, today)
        if (stats.isEmpty()) return "No habits yet — the first review is just an intention."
        val window = SfTime.lastDays(days, today).map { SfTime.format(it) }.toSet()
        val checkIns = repo.checkIns().filter { it.date in window }
        val successes = checkIns.count { it.isSuccess }
        val misses = checkIns.count { it.isMiss }
        val skips = checkIns.count { it.result == CheckInResult.SKIPPED }
        val recoveries = stats.sumOf { it.recoveries }
        val reasons = missReasons(repo, days)
        val strongest = stats.maxByOrNull { it.consistency30 }
        val struggling = stats.filter { it.hasEnoughData }.minByOrNull { it.consistency30 }
        val sb = StringBuilder()
        sb.append("This period: $successes completions, $skips intentional skips, $misses misses.\n")
        if (stats.isNotEmpty()) {
            val avg = (successes * 100) / (successes + misses + skips).coerceAtLeast(1)
            sb.append("Consistency: $avg% across ${stats.size} habits.\n")
        }
        strongest?.let { sb.append("Strongest: ${it.habit.title} (${it.consistency30}% of ${it.opportunities30}).\n") }
        struggling?.let { sb.append("Struggling: ${it.habit.title} (${it.consistency30}%).\n") }
        if (recoveries > 0) sb.append("Recoveries after a miss: $recoveries.\n")
        if (reasons.isNotEmpty()) {
            sb.append("Miss reasons: ")
            sb.append(reasons.joinToString(", ") { "${it.first} (${it.second})" })
            sb.append(".\n")
        }
        // Previous review's open action items, if any
        val previous = repo.reviews().firstOrNull()
        val openActions = previous?.actionItems?.filter { !it.completed }
        if (openActions != null && openActions.isNotEmpty()) {
            sb.append("Last time you decided to: ")
            sb.append(openActions.joinToString("; ") { it.text })
            sb.append(". How did that go?\n")
        }
        return sb.toString().trim()
    }

    /**
     * Recovery celebration (§8): true if this habit's most recent real
     * opportunity before [date] was a miss — a check-in today is a comeback.
     */
    fun isRecovery(repo: Repository, habit: Habit, date: LocalDate = repo.clock.today()): Boolean {
        val series = seriesFor(repo, habit, 30, date)
        val todayOpp = series.lastOrNull { !it.date.isAfter(date) }
        val prior = series.filter { it.date.isBefore(date) }.lastOrNull()
        return todayOpp?.status == OpportunityStatus.COMPLETED && prior?.status == OpportunityStatus.MISSED
    }

    /** Identities due for their ~30-day evolution check (§1). */
    fun identityReviewDue(repo: Repository): List<Identity> {
        val today = repo.clock.today()
        val cutoff = today.minusDays(30)
        return repo.identities().filter { i ->
            val created = runCatching {
                java.time.Instant.ofEpochMilli(i.createdAt).atZone(repo.clock.zone()).toLocalDate()
            }.getOrNull() ?: return@filter false
            created.isBefore(cutoff)
        }
    }

    /** Preventive nudge (§8): does tomorrow's weekday have a >40% miss history? */
    fun preventiveNudge(repo: Repository, habit: Habit, date: LocalDate = repo.clock.today().plusDays(1)): String? {
        if (!repo.scheduleOf(habit).activeOn(date)) return null
        val pattern = weekdayPattern(repo, habit)
        val dow = date.dayOfWeek.toString().take(3)
        val (_, counts) = pattern.firstOrNull { it.first == dow } ?: return null
        val (misses, total) = counts
        if (total < 3) return null
        val rate = (misses * 100) / total
        if (rate <= 40) return null
        val plan = habit.recoveryPlan.ifBlank {
            repo.obstacles(habit.id).joinToString("; ") { "If ${it.ifText}, then ${it.thenText}" }
        }.ifBlank { habit.tinyStart.ifBlank { habit.title } }
        return "$dow has been hard for ${habit.title} ($rate% of past $total). " +
                "Your plan: $plan"
    }

    /** Ladder suggestion (§5): is this habit due for an upgrade or a shrink? */
    fun ladderAdvice(repo: Repository, habit: Habit): String {
        val stats = forHabit(repo, habit)
        val series = seriesFor(repo, habit, 30, repo.clock.today())
        val consecutive = series.takeLast(14).takeWhile { it.status == OpportunityStatus.COMPLETED }.size
        return when {
            stats.missesInARow >= 3 ->
                "Standard might be too much right now. Shrink it to ${habit.levelText(Level.MINIMUM)} for a week?"
            consecutive >= 14 ->
                "You have done Standard $consecutive days in a row. Ready to upgrade ${habit.levelText(Level.STANDARD)}?"
            habit.stretchCount > 0 && consecutive >= 7 ->
                "Stretch version is waiting: ${habit.levelText(Level.STRETCH)}. Try it once this week?"
            else ->
                "Your ladder is holding. Keep the Standard steady and let the tiny version stay tiny."
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
                    sb.append("  When you do ${habits[i].title}, " +
                            "you do ${habits[j].title} ${(both.toDouble() / (both + aOnly + bOnly) * 100).toInt()}% of the time.\n")
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
                    ).toInt()
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
}
