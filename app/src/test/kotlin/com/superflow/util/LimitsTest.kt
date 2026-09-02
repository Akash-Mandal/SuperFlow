package com.superflow.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for input limits and truncation helper functions in [Limits].
 */
class LimitsTest {

    @Test
    fun `constant values match expected limits`() {
        assertEquals(100, Limits.TITLE)
        assertEquals(200, Limits.SHORT_TEXT)
        assertEquals(500, Limits.DESCRIPTION)
        assertEquals(1_000, Limits.NOTE)
        assertEquals(5_000, Limits.LONG_TEXT)
    }

    @Test
    fun `title trims whitespace and truncates to TITLE limit`() {
        val short = "   Hello World   "
        assertEquals("Hello World", Limits.title(short))

        val exact = "a".repeat(Limits.TITLE)
        assertEquals(exact, Limits.title("  $exact  "))

        val oversized = "a".repeat(Limits.TITLE + 50)
        val result = Limits.title("  $oversized  ")
        assertEquals(Limits.TITLE, result.length)
        assertEquals(exact, result)
    }

    @Test
    fun `shortText trims whitespace and truncates to SHORT_TEXT limit`() {
        val short = "   Short text   "
        assertEquals("Short text", Limits.shortText(short))

        val exact = "b".repeat(Limits.SHORT_TEXT)
        assertEquals(exact, Limits.shortText("  $exact  "))

        val oversized = "b".repeat(Limits.SHORT_TEXT + 50)
        val result = Limits.shortText("  $oversized  ")
        assertEquals(Limits.SHORT_TEXT, result.length)
        assertEquals(exact, result)
    }

    @Test
    fun `description trims whitespace and truncates to DESCRIPTION limit`() {
        val short = "   Sample description   "
        assertEquals("Sample description", Limits.description(short))

        val exact = "c".repeat(Limits.DESCRIPTION)
        assertEquals(exact, Limits.description("  $exact  "))

        val oversized = "c".repeat(Limits.DESCRIPTION + 50)
        val result = Limits.description("  $oversized  ")
        assertEquals(Limits.DESCRIPTION, result.length)
        assertEquals(exact, result)
    }

    @Test
    fun `note truncates to NOTE limit without trimming whitespace`() {
        val leadingSpace = "   Note content"
        assertEquals(leadingSpace, Limits.note(leadingSpace))

        val exact = "d".repeat(Limits.NOTE)
        assertEquals(exact, Limits.note(exact))

        val oversized = "  " + "d".repeat(Limits.NOTE + 50) + "  "
        val result = Limits.note(oversized)
        assertEquals(Limits.NOTE, result.length)
        assertEquals("  " + "d".repeat(Limits.NOTE - 2), result)
    }

    @Test
    fun `longText truncates to LONG_TEXT limit without trimming whitespace`() {
        val leadingSpace = "   Long text content"
        assertEquals(leadingSpace, Limits.longText(leadingSpace))

        val exact = "e".repeat(Limits.LONG_TEXT)
        assertEquals(exact, Limits.longText(exact))

        val oversized = "  " + "e".repeat(Limits.LONG_TEXT + 50) + "  "
        val result = Limits.longText(oversized)
        assertEquals(Limits.LONG_TEXT, result.length)
        assertEquals("  " + "e".repeat(Limits.LONG_TEXT - 2), result)
    }

    @Test
    fun `handles empty string and blank inputs`() {
        assertEquals("", Limits.title(""))
        assertEquals("", Limits.title("   "))
        assertEquals("", Limits.shortText(""))
        assertEquals("", Limits.shortText("   "))
        assertEquals("", Limits.description(""))
        assertEquals("", Limits.description("   "))

        assertEquals("", Limits.note(""))
        assertEquals("   ", Limits.note("   "))
        assertEquals("", Limits.longText(""))
        assertEquals("   ", Limits.longText("   "))
    }
}
