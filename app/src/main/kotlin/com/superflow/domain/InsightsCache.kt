package com.superflow.domain

import com.superflow.data.Repository
import com.superflow.data.model.Habit
import com.superflow.data.model.HabitStats
import java.time.LocalDate

/**
 * Short-lived TTL cache for expensive [Insights] computations (#66).
 *
 * [Insights.forHabit]/[allStats] rebuild every habit's opportunity series on
 * each call; the Today list, detail screen, search and suggestions all call
 * them during a single render pass. This cache holds results for [TTL_MS] and
 * invalidates on a repository-revision change, so the same (habit, date) is
 * computed once per frame and edits/imports never serve stale data.
 */
object InsightsCache {

    private const val TTL_MS = 5 * 60_000L

    private data class AllEntry(val stats: List<HabitStats>, val at: Long, val revision: Long)
    private data class HabitEntry(val stat: HabitStats, val at: Long, val revision: Long)

    @Volatile private var all: AllEntry? = null
    private val perHabit = HashMap<String, HabitEntry>()
    private val lock = Any()

    fun allStats(repo: Repository, today: LocalDate): List<HabitStats> {
        val rev = revision(repo)
        val now = System.currentTimeMillis()
        val cached = all
        if (cached != null && cached.revision == rev && now - cached.at < TTL_MS) {
            return cached.stats
        }
        val fresh = repo.habits().map { computeForHabit(repo, it, today) }
        all = AllEntry(fresh, now, rev)
        return fresh
    }

    fun forHabit(repo: Repository, habit: Habit, today: LocalDate): HabitStats {
        val rev = revision(repo)
        val now = System.currentTimeMillis()
        synchronized(lock) {
            val cached = perHabit[habit.id]
            if (cached != null && cached.revision == rev && now - cached.at < TTL_MS) {
                return cached.stat
            }
        }
        val fresh = computeForHabit(repo, habit, today)
        synchronized(lock) { perHabit[habit.id] = HabitEntry(fresh, now, rev) }
        return fresh
    }

    /** Drop every cached value. Cheap to call after an import or restore. */
    fun invalidate() {
        all = null
        synchronized(lock) { perHabit.clear() }
    }

    private fun revision(repo: Repository): Long =
        runCatching { repo.revision.value }.getOrDefault(0L)

    /** Direct (uncached) computation; the single source of truth. */
    private fun computeForHabit(repo: Repository, habit: Habit, today: LocalDate): HabitStats =
        Insights.computeForHabit(repo, habit, today)
}
