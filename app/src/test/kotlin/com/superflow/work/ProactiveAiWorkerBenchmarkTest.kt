package com.superflow.work

import com.superflow.data.model.Milestone
import com.superflow.data.model.MilestoneType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Benchmark test for [ProactiveAiWorker] milestone detection query patterns.
 */
class ProactiveAiWorkerBenchmarkTest {

    data class MockHabit(val id: String, val title: String, val repetitions: Int, val recoveries: Int, val currentRun: Int)

    class MockRepository {
        val milestoneDb = mutableListOf<Milestone>()
        val queryCount = AtomicInteger(0)

        fun milestones(): List<Milestone> {
            queryCount.incrementAndGet()
            return milestoneDb.toList()
        }

        fun saveMilestone(milestone: Milestone) {
            milestoneDb.add(milestone)
        }
    }

    // Legacy implementation with N+1 database queries inside checkAndCreateMilestone
    private fun detectMilestonesLegacy(repo: MockRepository, habits: List<MockHabit>) {
        for (habit in habits) {
            checkAndCreateMilestoneLegacy(repo, habit.id, MilestoneType.FIRST_CHECKIN, habit.repetitions, 1, "\"${habit.title}\": first step taken")
            checkAndCreateMilestoneLegacy(repo, habit.id, MilestoneType.REPS_7, habit.repetitions, 7, "\"${habit.title}\": 7 repetitions")
            checkAndCreateMilestoneLegacy(repo, habit.id, MilestoneType.REPS_21, habit.repetitions, 21, "\"${habit.title}\": 21 repetitions")
            checkAndCreateMilestoneLegacy(repo, habit.id, MilestoneType.REPS_66, habit.repetitions, 66, "\"${habit.title}\": 66 repetitions")
            checkAndCreateMilestoneLegacy(repo, habit.id, MilestoneType.REPS_100, habit.repetitions, 100, "\"${habit.title}\": 100 repetitions")
            checkAndCreateMilestoneLegacy(repo, habit.id, MilestoneType.RECOVERY_3, habit.recoveries, 3, "\"${habit.title}\": returned 3 times")
            checkAndCreateMilestoneLegacy(repo, habit.id, MilestoneType.STREAK_7, habit.currentRun, 7, "\"${habit.title}\": 7 in a row")
            checkAndCreateMilestoneLegacy(repo, habit.id, MilestoneType.STREAK_30, habit.currentRun, 30, "\"${habit.title}\": 30 in a row")
        }
    }

    private fun checkAndCreateMilestoneLegacy(
        repo: MockRepository,
        habitId: String?,
        type: MilestoneType,
        actualValue: Int,
        threshold: Int,
        label: String
    ) {
        if (actualValue >= threshold) {
            val existing = repo.milestones().any {
                it.habitId == habitId && it.type == type
            }
            if (!existing) {
                repo.saveMilestone(Milestone(
                    habitId = habitId,
                    type = type,
                    value = actualValue,
                    label = label
                ))
            }
        }
    }

    // Optimized implementation with pre-fetched batch query into a HashSet
    private fun detectMilestonesOptimized(repo: MockRepository, habits: List<MockHabit>) {
        val existingMilestones = repo.milestones().mapTo(HashSet()) { Pair(it.habitId, it.type) }

        for (habit in habits) {
            checkAndCreateMilestoneOptimized(repo, existingMilestones, habit.id, MilestoneType.FIRST_CHECKIN, habit.repetitions, 1, "\"${habit.title}\": first step taken")
            checkAndCreateMilestoneOptimized(repo, existingMilestones, habit.id, MilestoneType.REPS_7, habit.repetitions, 7, "\"${habit.title}\": 7 repetitions")
            checkAndCreateMilestoneOptimized(repo, existingMilestones, habit.id, MilestoneType.REPS_21, habit.repetitions, 21, "\"${habit.title}\": 21 repetitions")
            checkAndCreateMilestoneOptimized(repo, existingMilestones, habit.id, MilestoneType.REPS_66, habit.repetitions, 66, "\"${habit.title}\": 66 repetitions")
            checkAndCreateMilestoneOptimized(repo, existingMilestones, habit.id, MilestoneType.REPS_100, habit.repetitions, 100, "\"${habit.title}\": 100 repetitions")
            checkAndCreateMilestoneOptimized(repo, existingMilestones, habit.id, MilestoneType.RECOVERY_3, habit.recoveries, 3, "\"${habit.title}\": returned 3 times")
            checkAndCreateMilestoneOptimized(repo, existingMilestones, habit.id, MilestoneType.STREAK_7, habit.currentRun, 7, "\"${habit.title}\": 7 in a row")
            checkAndCreateMilestoneOptimized(repo, existingMilestones, habit.id, MilestoneType.STREAK_30, habit.currentRun, 30, "\"${habit.title}\": 30 in a row")
        }
    }

    private fun checkAndCreateMilestoneOptimized(
        repo: MockRepository,
        existingMilestones: MutableSet<Pair<String?, MilestoneType>>,
        habitId: String?,
        type: MilestoneType,
        actualValue: Int,
        threshold: Int,
        label: String
    ) {
        if (actualValue >= threshold) {
            val key = Pair(habitId, type)
            if (!existingMilestones.contains(key)) {
                repo.saveMilestone(Milestone(
                    habitId = habitId,
                    type = type,
                    value = actualValue,
                    label = label
                ))
                existingMilestones.add(key)
            }
        }
    }

    @Test
    fun `verify optimized milestone detection produces identical results with O(1) queries`() {
        val habits = (1..50).map { i ->
            MockHabit(
                id = "habit_$i",
                title = "Habit $i",
                repetitions = if (i % 2 == 0) 25 else 5,
                recoveries = if (i % 3 == 0) 4 else 1,
                currentRun = if (i % 5 == 0) 10 else 2
            )
        }

        val legacyRepo = MockRepository()
        detectMilestonesLegacy(legacyRepo, habits)
        val legacyQueryCount = legacyRepo.queryCount.get()
        val legacyMilestones = legacyRepo.milestoneDb.toList()

        val optRepo = MockRepository()
        detectMilestonesOptimized(optRepo, habits)
        val optQueryCount = optRepo.queryCount.get()
        val optMilestones = optRepo.milestoneDb.toList()

        // 1. Verify exact functional equivalence
        assertEquals(legacyMilestones.size, optMilestones.size)
        assertEquals(
            legacyMilestones.map { Triple(it.habitId, it.type, it.value) },
            optMilestones.map { Triple(it.habitId, it.type, it.value) }
        )

        // 2. Verify query count reduction: Legacy does N+1 queries (100+ queries for 50 habits), Optimized does exactly 1 query
        assertEquals(1, optQueryCount)
        assertTrue(legacyQueryCount > 100)
        println("Legacy queries: $legacyQueryCount vs Optimized queries: $optQueryCount")
    }
}
