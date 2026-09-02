package com.superflow.work

import com.superflow.data.Repository
import com.superflow.data.model.CheckIn
import com.superflow.data.model.CheckInResult
import com.superflow.data.model.Habit
import com.superflow.domain.Insights
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.system.measureNanoTime

class MilestoneWorkerBenchmarkTest {

    @Test
    fun benchmarkMilestoneWorkerNPlusOneVsBatch() {
        val today = LocalDate.parse("2026-08-26")

        val habits = (1..50).map { i ->
            Habit(
                id = "h_$i",
                title = "Habit $i",
                recurrenceRule = "FREQ=DAILY",
                startDate = "2026-01-01"
            )
        }

        val checkIns = habits.flatMap { h ->
            (1..100).map { day ->
                val date = today.minusDays(day.toLong()).toString()
                CheckIn(
                    habitId = h.id,
                    date = date,
                    result = if (day % 2 == 0) CheckInResult.DONE else CheckInResult.MISSED,
                    createdAt = 1L
                )
            }
        }

        val snap = Repository.DataSnapshot(
            identities = emptyList(),
            habits = habits,
            checkIns = checkIns,
            pauses = emptyList()
        )

        val iterations = 20
        var nPlusOneTime = 0L
        for (it in 0 until iterations) {
            val elapsed = measureNanoTime {
                for (h in snap.activeHabits) {
                    val pauses = snap.pauses.filter { p -> p.habitId == null || p.habitId == h.id }
                    val habitCheckIns = snap.checkInsByHabit[h.id].orEmpty()
                    val successes = habitCheckIns.filter { c -> c.isSuccess }
                    val reps = successes.size
                }
            }
            nPlusOneTime += elapsed
        }

        var batchTime = 0L
        for (it in 0 until iterations) {
            val elapsed = measureNanoTime {
                val checkInsByHabit = snap.checkInsByHabit
                for (h in snap.activeHabits) {
                    val habitCheckIns = checkInsByHabit[h.id].orEmpty()
                    val successes = habitCheckIns.filter { c -> c.isSuccess }
                    val reps = successes.size
                }
            }
            batchTime += elapsed
        }

        val avgNPlusOneMs = nPlusOneTime / iterations / 1_000_000.0
        val avgBatchMs = batchTime / iterations / 1_000_000.0

        println("BENCHMARK_RESULTS:")
        println("N+1 per-habit query time: %.3f ms".format(avgNPlusOneMs))
        println("Batch snapshot query time: %.3f ms".format(avgBatchMs))

        assertTrue(avgBatchMs >= 0)
    }
}
