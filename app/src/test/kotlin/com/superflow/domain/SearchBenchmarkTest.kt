package com.superflow.domain

import com.superflow.util.Fuzzy
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureNanoTime

class SearchBenchmarkTest {

    @Test
    fun benchmarkSearchRelevanceExecution() {
        val queries = listOf("walk", "meditation", "gym", "routine", "exercise", "journal", "read", "water", "sleep", "workout")
        val sampleFields = (1..500).flatMap { i ->
            listOf(
                "Morning Walk $i",
                "Daily meditation routine and mindfulness",
                "Gym strength training session",
                "Evening reading for 30 minutes",
                "Drink 2 liters of water daily",
                "Journal reflections and progress notes"
            )
        }

        // Warmup
        for (q in queries) {
            for (i in 0 until 100) {
                Search.relevance(q, sampleFields[i], sampleFields[i + 1])
            }
        }

        val iterations = 50
        val totalTimeNanos = measureNanoTime {
            for (it in 0 until iterations) {
                for (q in queries) {
                    for (fIdx in 0 until sampleFields.size - 2 step 3) {
                        Search.relevance(
                            q,
                            sampleFields[fIdx],
                            sampleFields[fIdx + 1],
                            sampleFields[fIdx + 2]
                        )
                    }
                }
            }
        }

        val totalMs = totalTimeNanos / 1_000_000.0
        val avgMsPerIteration = totalMs / iterations

        println("BENCHMARK_RESULTS:")
        println("Search relevance benchmarking across ${sampleFields.size} fields:")
        println("Total time for $iterations iterations: %.3f ms".format(totalMs))
        println("Avg time per search pass: %.3f ms".format(avgMsPerIteration))

        assertTrue(totalMs >= 0)
    }

    @Test
    fun benchmarkFuzzySimilarityExecution() {
        val candidates = (1..1000).map { "Habit Candidate Name Number $it" }
        val query = "habit candidate name number 500"

        // Warmup
        for (i in 0 until 100) {
            Fuzzy.similarity(query, candidates[i])
        }

        val iterations = 100
        val elapsedNanos = measureNanoTime {
            for (it in 0 until iterations) {
                for (candidate in candidates) {
                    Fuzzy.similarity(query, candidate)
                }
            }
        }

        val totalMs = elapsedNanos / 1_000_000.0
        val avgMsPerIteration = totalMs / iterations

        println("BENCHMARK_RESULTS:")
        println("Fuzzy similarity benchmarking across 1,000 candidates:")
        println("Total time for $iterations iterations: %.3f ms".format(totalMs))
        println("Avg time per 1,000 similarity checks: %.3f ms".format(avgMsPerIteration))

        assertTrue(totalMs >= 0)
    }
}
