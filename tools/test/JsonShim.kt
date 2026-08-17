@file:Suppress("unused")

package org.json

/**
 * Minimal org.json implementation used ONLY by the desktop JVM test harness.
 *
 * The android.jar used for compilation is a stub whose methods throw, so tests
 * that exercise real JSON behaviour need a working implementation. This file
 * is never compiled into the APK, where Android's own org.json is used.
 */

class JSONException(message: String) : RuntimeException(message)

class JSONObject {
    private val map = LinkedHashMap<String, Any?>()

    constructor()

    constructor(source: String) {
        val p = JsonParser(source)
        val v = p.parseValue()
        p.skipWs()
        if (v !is JSONObject) throw JSONException("Not an object")
        map.putAll(v.map)
    }

    internal constructor(m: LinkedHashMap<String, Any?>) {
        map.putAll(m)
    }

    fun put(key: String, value: Any?): JSONObject {
        map[key] = value
        return this
    }

    fun put(key: String, value: Int): JSONObject = put(key, value as Any?)
    fun put(key: String, value: Long): JSONObject = put(key, value as Any?)
    fun put(key: String, value: Double): JSONObject = put(key, value as Any?)
    fun put(key: String, value: Boolean): JSONObject = put(key, value as Any?)

    fun has(key: String): Boolean = map.containsKey(key)
    fun isNull(key: String): Boolean = !map.containsKey(key) || map[key] == null || map[key] === NULL
    fun keys(): Iterator<String> = map.keys.iterator()
    fun length(): Int = map.size
    fun remove(key: String): Any? = map.remove(key)

    fun opt(key: String): Any? = map[key]?.takeIf { it !== NULL }

    @JvmOverloads
    fun optString(key: String, def: String = ""): String = when (val v = opt(key)) {
        null -> def
        is String -> v
        else -> v.toString()
    }

    @JvmOverloads
    fun optInt(key: String, def: Int = 0): Int = when (val v = opt(key)) {
        is Number -> v.toInt()
        is String -> v.toIntOrNull() ?: def
        else -> def
    }

    @JvmOverloads
    fun optLong(key: String, def: Long = 0L): Long = when (val v = opt(key)) {
        is Number -> v.toLong()
        is String -> v.toLongOrNull() ?: def
        else -> def
    }

    @JvmOverloads
    fun optDouble(key: String, def: Double = Double.NaN): Double = when (val v = opt(key)) {
        is Number -> v.toDouble()
        is String -> v.toDoubleOrNull() ?: def
        else -> def
    }

    @JvmOverloads
    fun optBoolean(key: String, def: Boolean = false): Boolean = when (val v = opt(key)) {
        is Boolean -> v
        is String -> v.equals("true", true)
        else -> def
    }

    fun optJSONObject(key: String): JSONObject? = opt(key) as? JSONObject
    fun optJSONArray(key: String): JSONArray? = opt(key) as? JSONArray

    override fun toString(): String = buildString { writeObject(this@JSONObject, this) }
    fun toString(indent: Int): String = toString()

    companion object {
        @JvmField val NULL: Any = object : Any() {
            override fun toString(): String = "null"
            override fun equals(other: Any?): Boolean = other == null || other === this
            override fun hashCode(): Int = 0
        }
    }
}

class JSONArray {
    private val list = ArrayList<Any?>()

    constructor()

    constructor(source: String) {
        val v = JsonParser(source).parseValue()
        if (v !is JSONArray) throw JSONException("Not an array")
        list.addAll(v.list)
    }

    internal constructor(items: List<Any?>) {
        list.addAll(items)
    }

    fun put(value: Any?): JSONArray {
        list.add(value)
        return this
    }

    fun length(): Int = list.size
    fun opt(index: Int): Any? = list.getOrNull(index)?.takeIf { it !== JSONObject.NULL }
    fun optJSONObject(index: Int): JSONObject? = opt(index) as? JSONObject
    fun optJSONArray(index: Int): JSONArray? = opt(index) as? JSONArray

    @JvmOverloads
    fun optString(index: Int, def: String = ""): String = when (val v = opt(index)) {
        null -> def
        is String -> v
        else -> v.toString()
    }

    @JvmOverloads
    fun optInt(index: Int, def: Int = 0): Int = (opt(index) as? Number)?.toInt() ?: def

    override fun toString(): String = buildString { writeArray(this@JSONArray, this) }
}

/* ------------------------------------------------------------------ writer */

private fun writeValue(v: Any?, sb: StringBuilder) {
    when (v) {
        null, JSONObject.NULL -> sb.append("null")
        is JSONObject -> writeObject(v, sb)
        is JSONArray -> writeArray(v, sb)
        is Number, is Boolean -> sb.append(v.toString())
        else -> writeString(v.toString(), sb)
    }
}

private fun writeObject(o: JSONObject, sb: StringBuilder) {
    sb.append('{')
    var first = true
    val it = o.keys()
    while (it.hasNext()) {
        val k = it.next()
        if (!first) sb.append(',')
        first = false
        writeString(k, sb)
        sb.append(':')
        writeValue(o.opt(k), sb)
    }
    sb.append('}')
}

private fun writeArray(a: JSONArray, sb: StringBuilder) {
    sb.append('[')
    for (i in 0 until a.length()) {
        if (i > 0) sb.append(',')
        writeValue(a.opt(i), sb)
    }
    sb.append(']')
}

private fun writeString(s: String, sb: StringBuilder) {
    sb.append('"')
    for (c in s) {
        when (c) {
            '"' -> sb.append("\\\"")
            '\\' -> sb.append("\\\\")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            else -> if (c < ' ') sb.append(String.format("\\u%04x", c.code)) else sb.append(c)
        }
    }
    sb.append('"')
}

/* ------------------------------------------------------------------ parser */

internal class JsonParser(private val src: String) {
    private var i = 0

    fun skipWs() {
        while (i < src.length && src[i].isWhitespace()) i++
    }

    fun parseValue(): Any? {
        skipWs()
        if (i >= src.length) throw JSONException("Unexpected end")
        return when (src[i]) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"' -> parseString()
            't', 'f' -> parseBoolean()
            'n' -> { expect("null"); JSONObject.NULL }
            else -> parseNumber()
        }
    }

    private fun parseObject(): JSONObject {
        expectChar('{')
        val map = LinkedHashMap<String, Any?>()
        skipWs()
        if (i < src.length && src[i] == '}') { i++; return JSONObject(map) }
        while (true) {
            skipWs()
            val key = parseString()
            skipWs()
            expectChar(':')
            map[key] = parseValue()
            skipWs()
            if (i >= src.length) throw JSONException("Unterminated object")
            when (src[i]) {
                ',' -> i++
                '}' -> { i++; return JSONObject(map) }
                else -> throw JSONException("Expected , or } at $i")
            }
        }
    }

    private fun parseArray(): JSONArray {
        expectChar('[')
        val items = ArrayList<Any?>()
        skipWs()
        if (i < src.length && src[i] == ']') { i++; return JSONArray(items) }
        while (true) {
            items.add(parseValue())
            skipWs()
            if (i >= src.length) throw JSONException("Unterminated array")
            when (src[i]) {
                ',' -> i++
                ']' -> { i++; return JSONArray(items) }
                else -> throw JSONException("Expected , or ] at $i")
            }
        }
    }

    private fun parseString(): String {
        expectChar('"')
        val sb = StringBuilder()
        while (i < src.length) {
            val c = src[i++]
            when {
                c == '"' -> return sb.toString()
                c == '\\' -> {
                    if (i >= src.length) throw JSONException("Bad escape")
                    when (val e = src[i++]) {
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'u' -> {
                            val hex = src.substring(i, i + 4)
                            i += 4
                            sb.append(hex.toInt(16).toChar())
                        }
                        else -> sb.append(e)
                    }
                }
                else -> sb.append(c)
            }
        }
        throw JSONException("Unterminated string")
    }

    private fun parseBoolean(): Boolean =
        if (src.startsWith("true", i)) { i += 4; true }
        else { expect("false"); false }

    private fun parseNumber(): Any {
        val start = i
        if (i < src.length && (src[i] == '-' || src[i] == '+')) i++
        var isDouble = false
        while (i < src.length && (src[i].isDigit() || src[i] in ".eE+-")) {
            if (src[i] == '.' || src[i] == 'e' || src[i] == 'E') isDouble = true
            i++
        }
        val text = src.substring(start, i)
        if (text.isEmpty()) throw JSONException("Bad number at $start")
        return if (isDouble) text.toDouble()
        else text.toLongOrNull()?.let { if (it in Int.MIN_VALUE..Int.MAX_VALUE) it.toInt() else it }
            ?: text.toDouble()
    }

    private fun expectChar(c: Char) {
        skipWs()
        if (i >= src.length || src[i] != c) throw JSONException("Expected $c at $i")
        i++
    }

    private fun expect(word: String) {
        if (!src.startsWith(word, i)) throw JSONException("Expected $word at $i")
        i += word.length
    }
}
