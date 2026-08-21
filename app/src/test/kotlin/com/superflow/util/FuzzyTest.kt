package com.superflow.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Levenshtein / similarity / typo-tolerant best-match (migrated from the
 * original JVM shim suite into the standard JUnit location).
 */
class FuzzyTest {

    @Test
    fun `levenshtein distance`() {
        assertEquals(0, Fuzzy.levenshtein("walk", "walk"))
        assertEquals(1, Fuzzy.levenshtein("walk", "wolk"))
        assertEquals(1, Fuzzy.levenshtein("walk", "walks"))
        assertEquals(1, Fuzzy.levenshtein("walks", "walk"))
        assertEquals(0, Fuzzy.levenshtein("", ""))
        assertEquals(3, Fuzzy.levenshtein("", "abc"))
        assertEquals(3, Fuzzy.levenshtein("kitten", "sitting"))
        assertEquals(4, Fuzzy.levenshtein("WALK", "walk"))
    }

    @Test
    fun `similarity`() {
        assertEquals(1.0, Fuzzy.similarity("walk", "walk"))
        assertTrue(abs(Fuzzy.similarity("walk", "wlak") - 0.5) < 0.001)
        assertEquals(0.0, Fuzzy.similarity("walk", "zzzzzzz"))
        assertEquals(1.0, Fuzzy.similarity("", ""))
        assertEquals(1.0, Fuzzy.similarity("WALK", "walk"))
    }

    @Test
    fun `best match with typo tolerance`() {
        val habits = listOf("Walk", "Journal", "Meditate", "Read")
        assertEquals("Walk", Fuzzy.bestMatch("walk", habits) { it.lowercase() })
        assertEquals("Walk", Fuzzy.bestMatch("wlak", habits) { it.lowercase() })
        assertEquals("Read", Fuzzy.bestMatch("red", habits) { it.lowercase() })
        assertNull(Fuzzy.bestMatch("", habits) { it.lowercase() })
        assertNull(Fuzzy.bestMatch("xyzzynothing", habits) { it.lowercase() })
        assertNull(Fuzzy.bestMatch("zzz", habits) { it.lowercase() })
    }

    @Test
    fun `threshold gating`() {
        val short = listOf("Gym", "Run")
        assertNull(Fuzzy.bestMatch("zzz", short) { it.lowercase() })
        assertEquals("Gym", Fuzzy.bestMatch("gym", short, threshold = 0.99) { it.lowercase() })
        assertNull(Fuzzy.bestMatch("gyn", short, threshold = 0.99) { it.lowercase() })
    }

    @Test
    fun `ranking picks closest`() {
        val names = listOf("Meditate", "Medication", "Mediate")
        assertEquals("Meditate", Fuzzy.bestMatch("meditat", names) { it.lowercase() })
    }
}
