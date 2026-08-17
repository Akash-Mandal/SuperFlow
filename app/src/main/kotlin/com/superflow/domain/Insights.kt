package com.superflow.domain

import com.superflow.data.CheckIn
import com.superflow.data.CheckInResult
import com.superflow.data.Habit
import com.superflow.data.HabitMode
import com.superflow.data.Repo
import com.superflow.util.Dates

/**
 * Honest progress maths.
 *
 * SuperFlow counts repetitions, recovery and consistency. It never promises a
 * fixed number of days to form a habit and never turns a miss into a verdict.
 */
object Insights {

    data class HabitStats(
        val habit: Habit,
        val repetitions: Int,
        val currentRun: Int,
        val bestRun: Int,
        val consistency30: Int,
        val recoveries: Int,
        val missesInARow: Int,
        val lastDone: String?
    )

    private fun isSuccess(ci: CheckIn): Boolean =
        ci.result == CheckInResult.DONE || ci.result == CheckInResult.RESISTED

    private fun isMiss(ci: CheckIn): Boolean =
        ci.result == CheckInResult.MISSED || ci.result == CheckInResult.SLIPPED

    fun forHabit(repo: Repo, habit: Habit): HabitStats {
        val all = repo.checkInsOf(habit.id)
        val byDate = all.associateBy { it.date }
        val repetitions = all.count { isSuccess(it) }

        // Runs are counted over the days the habit is actually scheduled.
        var currentRun = 0
        var day = Dates.today()
        var guard = 0
        // A day that has not been acted on yet should not break the run.
        if (byDate[day] == null) day = Dates.plusDays(day, -1)
        while (guard++ < 400) {
            if (!habit.runsOn(Dates.isoDayOfWeek(day))) { day = Dates.plusDays(day, -1); continue }
            val ci = byDate[day] ?: break
            if (isSuccess(ci)) currentRun++
            else if (ci.result == CheckInResult.SKIPPED) { /* intentional skip preserves the run */ }
            else break
            day = Dates.plusDays(day, -1)
        }

        var bestRun = 0
        var run = 0
        for (d in Dates.lastDays(365).filter { habit.runsOn(Dates.isoDayOfWeek(it)) }) {
            val ci = byDate[d]
            when {
                ci == null -> run = 0
                isSuccess(ci) -> { run++; if (run > bestRun) bestRun = run }
                ci.result == CheckInResult.SKIPPED -> Unit
                else -> run = 0
            }
        }
        if (currentRun > bestRun) bestRun = currentRun

        val window = Dates.lastDays(30).filter { habit.runsOn(Dates.isoDayOfWeek(it)) }
        val opportunities = window.count { it <= Dates.today() }
        val hits = window.count { byDate[it]?.let(::isSuccess) == true }
        val consistency = if (opportunities == 0) 0 else (hits * 100.0 / opportunities).toInt()

        // A recovery is a success on the day right after a miss.
        var recoveries = 0
        val ordered = Dates.lastDays(120)
        for (i in 1 until ordered.size) {
            val prev = byDate[ordered[i - 1]]
            val cur = byDate[ordered[i]]
            if (prev != null && isMiss(prev) && cur != null && isSuccess(cur)) recoveries++
        }

        var missesInARow = 0
        for (d in Dates.lastDays(30).reversed()) {
            val ci = byDate[d] ?: continue
            if (isMiss(ci)) missesInARow++ else break
        }

        val lastDone = all.filter { isSuccess(it) }.maxByOrNull { it.date }?.date

        return HabitStats(habit, repetitions, currentRun, bestRun, consistency, recoveries, missesInARow, lastDone)
    }

    fun allStats(repo: Repo): List<HabitStats> = repo.habits().map { forHabit(repo, it) }

    /** Percentage of today's scheduled opportunities that have been acted on. */
    fun dayProgress(repo: Repo, date: String = Dates.today()): Pair<Int, Int> {
        val scheduled = repo.habitsForDay(date)
        val done = scheduled.count { h ->
            repo.checkIn(h.id, date)?.let { isSuccess(it) } == true
        }
        return done to scheduled.size
    }

    fun todaySummary(repo: Repo, date: String = Dates.today()): String {
        val (done, total) = dayProgress(repo, date)
        if (total == 0) return "Nothing is scheduled for ${Dates.humanDay(date)}. A quiet day is allowed."
        val focus = repo.focusFor(date)
        val sb = StringBuilder()
        sb.append("${Dates.humanDay(date)}: $done of $total actions done.")
        if (focus.isNotEmpty()) {
            val fdone = focus.count { it.done }
            sb.append(" Daily Focus $fdone/${focus.size}: ")
            sb.append(focus.joinToString(", ") { (if (it.done) "[x] " else "[ ] ") + it.title })
            sb.append('.')
        }
        val open = repo.habitsForDay(date).filter { repo.checkIn(it.id, date) == null }
        if (open.isNotEmpty()) {
            sb.append(" Still open: ").append(open.take(4).joinToString(", ") { it.title })
            if (open.size > 4) sb.append(" and ${open.size - 4} more")
            sb.append('.')
        }
        val returning = returnCards(repo, date)
        if (returning.isNotEmpty()) {
            sb.append(" Return today: ").append(returning.joinToString(", ") { it.title }).append('.')
        }
        return sb.toString()
    }

    /** Habits missed at the previous opportunity - the "never miss twice" rule. */
    fun returnCards(repo: Repo, date: String = Dates.today()): List<Habit> =
        repo.habitsForDay(date).filter { h ->
            if (repo.checkIn(h.id, date) != null) return@filter false
            var prev = Dates.plusDays(date, -1)
            var guard = 0
            while (guard++ < 14 && !h.runsOn(Dates.isoDayOfWeek(prev))) prev = Dates.plusDays(prev, -1)
            repo.checkIn(h.id, prev)?.let { isMiss(it) } == true
        }

    fun summaryText(repo: Repo, days: Int = 30): String {
        val stats = allStats(repo)
        if (stats.isEmpty()) return "No habits yet. Create one and the insights will fill in."
        val window = Dates.lastDays(days)
        val checkIns = repo.checkIns().filter { it.date in window }
        val successes = checkIns.count { isSuccess(it) }
        val misses = checkIns.count { isMiss(it) }
        val skips = checkIns.count { it.result == CheckInResult.SKIPPED }
        val recoveries = stats.sumOf { it.recoveries }
        val sb = StringBuilder()
        sb.append("Last $days days\n")
        sb.append("Repetitions: $successes\n")
        sb.append("Intentional skips: $skips\n")
        sb.append("Misses: $misses\n")
        sb.append("Recoveries after a miss: $recoveries\n")
        val best = stats.maxByOrNull { it.consistency30 }
        if (best != null && best.consistency30 > 0)
            sb.append("Most consistent: ${best.habit.title} (${best.consistency30}%)\n")
        val struggling = stats.filter { it.consistency30 < 40 && it.repetitions >= 0 }
            .sortedBy { it.consistency30 }.firstOrNull()
        if (struggling != null && stats.size > 1)
            sb.append("Worth redesigning: ${struggling.habit.title} (${struggling.consistency30}%) - try shrinking it\n")
        sb.append("\nIdentity evidence:\n")
        for (i in repo.identities()) {
            val linked = stats.filter { it.habit.identityId == i.id }
            val votes = linked.sumOf { it.repetitions }
            sb.append("- ${i.statement}: $votes votes\n")
        }
        return sb.toString().trim()
    }

    /** Identity evidence ledger: repetitions that support each identity. */
    fun identityEvidence(repo: Repo): List<Triple<String, Int, Int>> =
        repo.identities().map { i ->
            val linked = repo.habits(true).filter { it.identityId == i.id }
            val votes = linked.sumOf { h -> repo.checkInsOf(h.id).count { isSuccess(it) } }
            Triple(i.statement, votes, linked.size)
        }

    /** Simple energy pattern with an explicit sample-size caveat. */
    fun energyPattern(repo: Repo): String {
        val logs = repo.energyLogs()
        if (logs.size < 6) return "Log energy at a few checkpoints and a pattern will appear here. " +
                "(${logs.size} entries so far - too few to read anything into.)"
        val byCp = logs.groupBy { it.checkpoint }
        val sb = StringBuilder("Average energy by checkpoint (${logs.size} entries):\n")
        for ((cp, list) in byCp) {
            val avg = list.sumOf { it.energy }.toDouble() / list.size
            sb.append("- ${cp.label}: ${"%.1f".format(avg)}/5 from ${list.size} entries\n")
        }
        sb.append("Small samples move a lot. Treat this as a hint, not a rule.")
        return sb.toString()
    }

    /** Weekly bar data: successes per day over the last 7 days. */
    fun weekBars(repo: Repo): List<Pair<String, Int>> =
        Dates.lastDays(7).map { d ->
            Dates.dayLetter(d) to repo.checkInsFor(d).count { isSuccess(it) }
        }

    fun reduceModeProgress(repo: Repo): String {
        val reduce = repo.habits().filter { it.mode == HabitMode.REDUCE }
        if (reduce.isEmpty()) return ""
        val sb = StringBuilder("Reduce mode\n")
        for (h in reduce) {
            val cis = repo.checkInsOf(h.id)
            val resisted = cis.count { it.result == CheckInResult.RESISTED }
            val slipped = cis.count { it.result == CheckInResult.SLIPPED }
            sb.append("- ${h.title}: $resisted resisted, $slipped slips\n")
        }
        return sb.toString().trim()
    }
}
