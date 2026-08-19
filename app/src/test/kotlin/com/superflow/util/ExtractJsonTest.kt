package com.superflow.util

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the JSON helpers used to survive chatty AI responses and to
 * build command arguments.
 */
class ExtractJsonTest {

    @Test
    fun `plain object parses`() {
        assertEquals(1, extractJson("""{"a": 1}""")?.getInt("a"))
    }

    @Test
    fun `object in prose parses`() {
        val text = "Sure! Here is what I will do: {\"reply\": \"ok\", \"commands\": []} Hope that helps."
        val obj = extractJson(text)
        assertNotNull(obj)
        assertEquals("ok", obj!!.getString("reply"))
    }

    @Test
    fun `fenced code block parses`() {
        val text = """
            I'll run this:
            ```json
            {"reply": "done", "commands": [{"command": "check_in"}]}
            ```
        """.trimIndent()
        val obj = extractJson(text)
        assertNotNull(obj)
        assertEquals(1, obj!!.getJSONArray("commands").length())
    }

    @Test
    fun `nested braces parse as one object`() {
        val text = "noise {\"a\": {\"b\": [1, 2, {\"c\": \"d\"}]}} trailing"
        val obj = extractJson(text)
        assertNotNull(obj)
        assertEquals("d", obj!!.getJSONObject("a").getJSONArray("b").getJSONObject(2).getString("c"))
    }

    @Test
    fun `braces inside strings do not break extraction`() {
        val text = """{"reply": "use {this} and {that}", "n": 2}"""
        val obj = extractJson(text)
        assertNotNull(obj)
        assertEquals(2, obj!!.getInt("n"))
    }

    @Test
    fun `escaped quotes inside strings`() {
        val text = """{"reply": "he said \"hi\""}"""
        val obj = extractJson(text)
        assertNotNull(obj)
        assertTrue(obj!!.getString("reply").contains("\"hi\""))
    }

    @Test
    fun `first balanced object wins with several`() {
        val text = """{"a": 1} and later {"b": 2}"""
        val obj = extractJson(text)
        assertNotNull(obj)
        assertEquals(1, obj!!.getInt("a"))
    }

    @Test
    fun `garbage yields null`() {
        assertNull(extractJson("no json here at all"))
        assertNull(extractJson(""))
        assertNull(extractJson("{\"unterminated\": "))
    }

    @Test
    fun `jsonOf builds objects and nulls`() {
        val o = jsonOf("a" to 1, "b" to null, "c" to "x")
        assertTrue(o.isNull("b"))
        assertEquals(1, o.getInt("a"))
        assertEquals("x", o.getString("c"))
    }

    @Test
    fun `string accessors respect nulls`() {
        val o = JSONObject().apply { put("s", "v"); put("n", JSONObject.NULL) }
        assertEquals("v", o.string("s"))
        assertEquals("d", o.string("n", "d"))
        assertEquals("d", o.string("missing", "d"))
        assertEquals("v", o.stringOrNull("s"))
        assertNull(o.stringOrNull("n"))
        assertNull(o.stringOrNull("missing"))
        assertEquals(null, o.stringOrNull("blank").also { o.put("blank", "") })
    }

    @Test
    fun `array helpers`() {
        val arr = jsonArrayOf(listOf("a", "b", "c"))
        assertEquals(3, arr.length())
        assertEquals(listOf("a", "b", "c"), arr.strings())
        val objects = JSONArray().apply {
            put(JSONObject().put("k", 1))
            put("not-an-object")
            put(JSONObject().put("k", 2))
        }
        assertEquals(2, objects.objects().size)
    }

    @Test
    fun `parseObject is safe`() {
        assertNotNull(parseObject("""{"a": 1}"""))
        assertNull(parseObject("not json"))
    }
}
