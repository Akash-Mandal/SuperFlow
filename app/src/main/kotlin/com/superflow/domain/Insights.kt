package com.superflow.domain

import com.superflow.data.Repository
import com.superflow.data.model.CheckInResult
import com.superflow.data.model.Habit
import com.superflow.data.model.HabitMode
import com.superflow.data.model.HabitStats
import com.superflow.util.Dates

/**
 * Honest progress maths.
 *
 * SuperFlow counts repetitions, recovery and consistency. It never promises a
 * fixed number of days to form a habit and never turns a miss into a verdict.
 */
object Insights {

    fun forHabit(repo: Repository, habit: Habit): HabitStats {
        val all = repo.checkInsOf(habit.id)
        val byDate = all.associateBy { it.date }
        val repetitions = all.count { it.isSuccess }

        // Runs count only over days the habit is actually scheduled.
        var currentRun = 0
        var day = Dates.today()
        var guard = 0
        if (byDate[day] == null) day = Dates.plusDays(day, -1)
        while (guard++ < 400) {
            if (!habit.runsOn(Dates.isoDayOfWeek(day))) { day = Dates.plusDays(day, -1); continue }
            val ci = byDate[day] ?: break
            when {
                ci.isSuccess -> currentRun++
                ci.result == CheckInResult.SKIPPED -> Unit // intentional skip preserves the run
                else -> break
            }
            day = Dates.plusDays(day, -1)
        }

        var bestRun = 0
        var run = 0
        for (d in Dates.lastDays(365).filter { habit.runsOn(Dates.isoDayOfWeek(it)) }) {
            val ci = byDate[d]
            when {
                ci == null -> run = 0
                ci.isSuccess -> { run++; if (run > bestRun) bestRun = run }
                ci.result == CheckInResult.SKIPPED -> Unit
                else -> run = 0
            }
        }
        if (currentRun > bestRun) bestRun = currentRun

        val window = Dates.lastDays(30).filter { habit.runsOn(Dates.isoDayOfWeek(it)) }
        val opportunities = window.count { it <= Dates.today() }
        val hits = window.count { byDate[it]?.isSuccess == true }
        val consistency = if (opportunities == 0) 0 else (hits * 100.0 / opportunities).toInt()

        var recoveries = 0
        val ordered = Dates.lastDays(120)
        for (i in 1 until ordered.size) {
            val prev = byDate[ordered[i - 1]]
            val cur = byDate[ordered[i]]
            if (prev != null && prev.isMiss && cur != null && cur.isSuccess) recoveries++
        }

        var missesInARow = 0
        for (d in Dates.lastDays(30).reversed()) {
            val ci = byDate[d] ?: continue
            if (ci.isMiss) missesInARow++ else break
        }

        val lastDone = all.filter { it.isSuccess }.maxByOrNull { it.date }?.date

        return HabitStats(habit, repetitions, currentRun, bestRun, consistency,
            recoveries, missesInARow, lastDone)
    }

    fun allStats(repo: Repository): List<HabitStats> = repo.habits().map { forHabit(repo, it) }

    fun dayProgress(repo: Repository, date: String = Dates.today()): Pair<Int, Int> {
        val scheduled = repo.habitsForDay(date)
        val checkIns = repo.checkInsFor(date).associateBy { it.habitId }
        val done = scheduled.count { checkIns[it.id]?.isSuccess == true }
        return done to scheduled.size
    }

    fun todaySummary(repo: Repository, date: String = Dates.today()): String {
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
        val checkIns = repo.checkInsFor(date).associateBy { it.habitId }
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

    fun summaryText(repo: Repository, days: Int = 30): String {
        val stats = allStats(repo)
        if (stats.isEmpty()) return "No habits yet. Create one and the insights will fill in."
        val window = Dates.lastDays(days)
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
        val best = stats.maxByOrNull { it.consistency30 }
        if (best != null && best.consistency30 > 0)
            sb.append("Most consistent: ${best.habit.title} (${best.consistency30}%)\n")
        val struggling = stats.filter { it.consistency30 < 40 }.minByOrNull { it.consistency30 }
        if (struggling != null && stats.size > 1)
            sb.append("Worth redesigning: ${struggling.habit.title} " +
                    "(${struggling.consistency30}%) — try shrinking it\n")
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

    fun energyPattern(repo: Repository): String {
        val logs = repo.energyLogs()
        if (logs.size < 6) return "Log energy at a few checkpoints and a pattern will appear here. " +
                "(${logs.size} entries so far — too few to read anything into.)"
        val byCp = logs.groupBy { it.checkpoint }
        val sb = StringBuilder("Average energy by checkpoint (${logs.size} entries):\n")
        for ((cp, list) in byCp) {
            val avg = list.sumOf { it.energy }.toDouble() / list.size
            sb.append("· ${cp.label}: ${"%.1f".format(avg)}/5 from ${list.size} entries\n")
        }
        sb.append("Small samples move a lot. Treat this as a hint, not a rule.")
        return sb.toString()
    }

    /** Successes per day over the last [days] days, for the bar chart. */
    fun dailyCounts(repo: Repository, days: Int = 7): List<Pair<String, Int>> {
        val dates = Dates.lastDays(days)
        val all = repo.checkInsBetween(dates.first(), dates.last())
        return dates.map { d -> d to all.count { it.date == d && it.isSuccess } }
    }

    fun reduceModeProgress(repo: Repository): List<Triple<String, Int, Int>> =
        repo.habits().filter { it.mode == HabitMode.REDUCE }.map { h ->
            val cis = repo.checkInsOf(h.id)
            Triple(h.title, cis.count { it.result == CheckInResult.RESISTED },
                cis.count { it.result == CheckInResult.SLIPPED })
        }
}
