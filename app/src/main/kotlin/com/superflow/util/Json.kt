package com.superflow.util

import org.json.JSONArray
import org.json.JSONObject

/** Helpers over org.json, which ships with the Android framework. */

fun jsonOf(vararg pairs: Pair<String, Any?>): JSONObject {
    val o = JSONObject()
    for ((k, v) in pairs) o.put(k, v ?: JSONObject.NULL)
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
