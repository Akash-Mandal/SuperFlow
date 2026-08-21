package com.superflow.domain

import com.superflow.data.model.LifeArea
import com.superflow.util.Fuzzy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Alpha2 (PR #6) pure logic: Levenshtein distance, global-search relevance
 * scoring, graduation rules and the habit-template catalog. Migrated from the
 * original JVM shim suite into the standard JUnit location.
 */
class Alpha2Test {

    @Test
    fun `levenshtein distance`() {
        assertEquals(0, Fuzzy.levenshtein("walk", "walk"))
        assertEquals(1, Fuzzy.levenshtein("walk", "walkk"))
        assertEquals(3, Fuzzy.levenshtein("kitten", "sitting"))
        assertEquals(3, Fuzzy.levenshtein("", "abc"))
        assertEquals(3, Fuzzy.levenshtein("abc", ""))
        assertEquals(1, Fuzzy.levenshtein("Walk", "walk"))
    }

    private fun close(actual: Float, expected: Float) {
        if (abs(actual - expected) >= 0.001f) {
            throw AssertionError("expected ~$expected, got $actual")
        }
    }

    @Test
    fun `search relevance scoring`() {
        close(Search.relevance("walk", "Walk"), 1.0f)
        close(Search.relevance("wal", "Walk"), 0.8f)
        close(Search.relevance("alk", "Walk"), 0.5f)
        close(Search.relevance("walkk", "Walk"), 0.3f)
        close(Search.relevance("wakk", "Walk"), 0.2f)
        close(Search.relevance("zzzz", "Walk"), 0f)
        close(Search.relevance("walk", "Gym", "Walk"), 1.0f)
        close(Search.relevance("walk", "", "Walk"), 1.0f)
        close(Search.relevance("walk", "", " "), 0f)
    }

    @Test
    fun `graduation rule`() {
        assertTrue(Graduation.eligible(90, 66L, 10))
        assertFalse(Graduation.eligible(90, 65L, 10))
        assertFalse(Graduation.eligible(89, 66L, 10))
        assertFalse(Graduation.eligible(90, 66L, 4))
        assertTrue(Graduation.eligible(100, 200L, 50))
    }

    @Test
    fun `habit template catalog`() {
        assertTrue(Templates.areas().all { Templates.byArea(it).isNotEmpty() })
        assertTrue(Templates.all().size >= 40)
        assertEquals("Morning walk", Templates.find("morning_walk")?.title)
        assertTrue(Templates.find("Read 20 minutes") != null)
        assertEquals(null, Templates.find("zzzz_zzzz"))
        assertTrue(Templates.areas().none { it == LifeArea.CUSTOM })
    }
}
