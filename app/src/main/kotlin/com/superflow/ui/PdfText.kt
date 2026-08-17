package com.superflow.ui

import java.io.ByteArrayOutputStream
import java.util.zip.Inflater

/**
 * Minimal, bounded PDF text extraction.
 *
 * Handles the common case of digitally generated PDFs with Flate-compressed
 * content streams. Scanned documents produce no text; the UI then asks the
 * user to paste the relevant part instead of silently importing nothing.
 */
object PdfText {

    private const val MAX_OUTPUT = 400_000

    fun extract(bytes: ByteArray): String {
        val out = StringBuilder()
        try {
            for (stream in contentStreams(bytes)) {
                if (out.length > MAX_OUTPUT) break
                out.append(textFromStream(stream))
                out.append('\n')
            }
        } catch (e: Exception) {
            return out.toString().trim()
        }
        return out.toString()
            .replace(Regex("[ \\t]{2,}"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    /** Finds `stream ... endstream` blocks and inflates the deflated ones. */
    private fun contentStreams(bytes: ByteArray): List<String> {
        val results = ArrayList<String>()
        val marker = "stream".toByteArray(Charsets.ISO_8859_1)
        val endMarker = "endstream".toByteArray(Charsets.ISO_8859_1)
        var i = 0
        var guard = 0
        while (i < bytes.size && guard++ < 5000) {
            val start = indexOf(bytes, marker, i)
            if (start < 0) break
            var dataStart = start + marker.size
            // Skip EOL after the keyword.
            if (dataStart < bytes.size && bytes[dataStart] == '\r'.code.toByte()) dataStart++
            if (dataStart < bytes.size && bytes[dataStart] == '\n'.code.toByte()) dataStart++
            val end = indexOf(bytes, endMarker, dataStart)
            if (end < 0) break
            val slice = bytes.copyOfRange(dataStart, end.coerceAtLeast(dataStart))
            val text = inflate(slice) ?: String(slice, Charsets.ISO_8859_1)
            if (text.contains("Tj") || text.contains("TJ")) results.add(text)
            i = end + endMarker.size
            if (results.size > 400) break
        }
        return results
    }

    private fun inflate(data: ByteArray): String? = try {
        val inflater = Inflater()
        inflater.setInput(data)
        val buffer = ByteArray(16384)
        val sink = ByteArrayOutputStream()
        var guard = 0
        while (!inflater.finished() && guard++ < 4000) {
            val n = inflater.inflate(buffer)
            if (n == 0) break
            sink.write(buffer, 0, n)
            if (sink.size() > MAX_OUTPUT) break
        }
        inflater.end()
        if (sink.size() == 0) null else String(sink.toByteArray(), Charsets.ISO_8859_1)
    } catch (e: Exception) {
        null
    }

    /** Pulls the string literals out of PDF text-showing operators. */
    private fun textFromStream(stream: String): String {
        val sb = StringBuilder()
        var i = 0
        var inString = false
        var depth = 0
        val current = StringBuilder()
        while (i < stream.length) {
            val c = stream[i]
            if (inString) {
                when {
                    c == '\\' && i + 1 < stream.length -> {
                        val next = stream[i + 1]
                        current.append(when (next) {
                            'n' -> '\n'; 'r' -> '\r'; 't' -> '\t'
                            '(' -> '('; ')' -> ')'; '\\' -> '\\'
                            else -> next
                        })
                        i += 2
                        continue
                    }
                    c == '(' -> { depth++; current.append(c) }
                    c == ')' -> {
                        depth--
                        if (depth < 0) {
                            inString = false
                            sb.append(current)
                            // Heuristic: operators that end a line.
                            val after = stream.substring(i, minOf(i + 6, stream.length))
                            if (after.contains("TJ") || after.contains("Tj") || after.contains("'")) {
                                sb.append(' ')
                            }
                            current.setLength(0)
                        } else current.append(c)
                    }
                    else -> current.append(c)
                }
            } else {
                if (c == '(') { inString = true; depth = 0; current.setLength(0) }
                else if (c == 'T' && i + 1 < stream.length &&
                    (stream[i + 1] == 'd' || stream[i + 1] == 'D' || stream[i + 1] == '*')) {
                    sb.append('\n')
                }
            }
            i++
        }
        return sb.toString()
    }

    private fun indexOf(haystack: ByteArray, needle: ByteArray, from: Int): Int {
        if (needle.isEmpty()) return -1
        outer@ for (i in from..haystack.size - needle.size) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) continue@outer
            }
            return i
        }
        return -1
    }
}
