package com.superflow.util

import com.superflow.domain.Search
import org.junit.Assert.assertEquals
import org.junit.Test

class FuzzyAndSearchBenchmarkTest {

    @Test
    fun `benchmark relevance matching performance`() {
        val candidates = (1..1000).map { i ->
            arrayOf("Habit $i", "Morning routine $i", "Cue $i", "Anchor $i", "Benefit $i")
        }

        val queries = listOf("morning", "habit 500", "rutine", "cue 123", "benfit", "xyz999")

        // Warmup
        repeat(10) {
            for (query in queries) {
                for (fields in candidates) {
                    Search.relevance(query, *fields)
                }
            }
        }

        // Measure
        val iterations = 100
        val start = System.nanoTime()
        repeat(iterations) {
            for (query in queries) {
                for (fields in candidates) {
                    Search.relevance(query, *fields)
                }
            }
        }
        val elapsedMs = (System.nanoTime() - start) / 1_000_000.0
        val totalOps = iterations * queries.size * candidates.size
        println("Processed $totalOps Search.relevance evaluations in ${"%.2f".format(elapsedMs)} ms (${"%.2f".format((elapsedMs * 1_000_000) / totalOps)} ns/op)")
    }

    @Test
    fun `benchmark bestMatch performance`() {
        val habits = (1..1000).map { "Habit Number $it" }
        val queries = listOf("habit number 500", "hbt nmbr 250", "habit number 999", "nonexistent")

        // Warmup
        repeat(10) {
            for (q in queries) {
                Fuzzy.bestMatch(q, habits) { it }
            }
        }

        // Measure
        val iterations = 200
        val start = System.nanoTime()
        repeat(iterations) {
            for (q in queries) {
                Fuzzy.bestMatch(q, habits) { it }
            }
        }
        val elapsedMs = (System.nanoTime() - start) / 1_000_000.0
        val totalOps = iterations * queries.size
        println("Processed $totalOps Fuzzy.bestMatch calls over 1000 candidates in ${"%.2f".format(elapsedMs)} ms (${"%.2f".format(elapsedMs / totalOps)} ms/call)")
    }
}
