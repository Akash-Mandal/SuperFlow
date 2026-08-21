package com.superflow.util

import org.json.JSONArray
import org.json.JSONObject

/** Helpers over org.json, which ships with the Android framework. */

/**
 * Converts Kotlin values into their org.json equivalents.
 *
 * [JSONObject.put] stores whatever object it is handed. A Kotlin `List` put
 * straight in stays a `List`, so [JSONObject.optJSONArray] later returns null
 * and every element is silently dropped on the way back — which is what made
 * goal milestones, review action items, check-in context tags, identity
 * evolution and ladder history all round-trip as empty. Wrap collections and
 * maps so they serialise as real JSON arrays and objects.
 */
private fun wrapJson(v: Any?): Any = when (v) {
    null -> JSONObject.NULL
    is JSONObject, is JSONArray, JSONObject.NULL -> v
    is Collection<*> -> JSONArray().also { a -> v.forEach { a.put(wrapJson(it)) } }
    is Array<*> -> JSONArray().also { a -> v.forEach { a.put(wrapJson(it)) } }
    is Map<*, *> -> JSONObject().also { o ->
        v.forEach { (key, value) -> o.put(key.toString(), wrapJson(value)) }
    }
    else -> v
}

fun jsonOf(vararg pairs: Pair<String, Any?>): JSONObject {
    val o = JSONObject()
    for ((k, v) in pairs) o.put(k, wrapJson(v))
    return o
}

fun JSONObject.string(key: String, def: String = ""): String =
    if (isNull(key)) def else optString(key, def)

fun JSONObject.stringOrNull(key: String): String? =
    if (isNull(key)) null else optString(key, "").ifBlank { null }

fun JSONArray.objects(): List<JSONObject> =
    (0 until length()).mapNotNull { optJSONObject(it) }

fun JSONArray.strings(): List<String> =
    (0 until length()).map { optString(it, "") }.filter { it.isNotBlank() }

fun jsonArrayOf(items: Collection<String>): JSONArray {
    val a = JSONArray()
    items.forEach { a.put(it) }
    return a
}

fun jsonArrayOfStrings(items: List<String>): String {
    val a = JSONArray()
    items.forEach { a.put(it) }
    return a.toString()
}

fun jsonArrayFromObjects(items: List<JSONObject>): String {
    val a = JSONArray()
    items.forEach { a.put(it) }
    return a.toString()
}

fun parseObject(text: String): JSONObject? = try {
    JSONObject(text)
} catch (e: Exception) {
    null
}

/**
 * Models sometimes wrap JSON in prose or fenced code blocks. Extract the first
 * balanced top-level JSON object so tool calls survive chatty responses.
 */
fun extractJson(text: String): JSONObject? {
    parseObject(text.trim())?.let { return it }
    Regex("```(?:json)?\\s*([\\s\\S]*?)```").find(text)?.let {
        parseObject(it.groupValues[1].trim())?.let { o -> return o }
    }
    var depth = 0
    var start = -1
    var inStr = false
    var esc = false
    for (i in text.indices) {
        val ch = text[i]
        if (inStr) {
            when {
                esc -> esc = false
                ch == '\\' -> esc = true
                ch == '"' -> inStr = false
            }
            continue
        }
        when (ch) {
            '"' -> inStr = true
            '{' -> { if (depth == 0) start = i; depth++ }
            '}' -> {
                depth--
                if (depth == 0 && start >= 0) {
                    parseObject(text.substring(start, i + 1))?.let { return it }
                    start = -1
                }
            }
        }
    }
    return null
}
