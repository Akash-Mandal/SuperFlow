package com.superflow.util

/**
 * Fuzzy string matching used by [com.superflow.data.Repository.findHabit]
 * so AI commands and search tolerate small typos ("wlak" -> "Walk").
 */
object Fuzzy {

    private const val DEFAULT_BUFFER_SIZE = 128
    // Thread-local buffers to make Levenshtein calculations allocation-free for typical strings.
    private val bufferThreadLocal = object : ThreadLocal<Pair<IntArray, IntArray>>() {
        override fun initialValue(): Pair<IntArray, IntArray> {
            return IntArray(DEFAULT_BUFFER_SIZE) to IntArray(DEFAULT_BUFFER_SIZE)
        }
    }

    /**
     * Classic Wagner–Fischer Levenshtein distance.
     *
     * Uses the two-row O(min(m,n)) memory variant with thread-local buffer reuse.
     * Returns 0 for equal strings and the edit count otherwise.
     */
    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        // Keep the shorter string in the inner row to use less memory and fewer iterations.
        val s = if (a.length <= b.length) a else b
        val t = if (a.length <= b.length) b else a
        val n = s.length
        val m = t.length

        val (b1, b2) = bufferThreadLocal.get()!!
        var prev = if (n + 1 <= b1.size) b1 else IntArray(n + 1)
        var curr = if (n + 1 <= b2.size) b2 else IntArray(n + 1)

        for (i in 0..n) {
            prev[i] = i
        }

        for (j in 1..m) {
            curr[0] = j
            val tj = t[j - 1]
            for (i in 1..n) {
                val cost = if (s[i - 1] == tj) 0 else 1
                curr[i] = minOf(
                    curr[i - 1] + 1,       // insertion
                    prev[i] + 1,           // deletion
                    prev[i - 1] + cost     // substitution
                )
            }
            val swap = prev; prev = curr; curr = swap
        }
        return prev[n]
    }

    /**
     * Normalised similarity in 0.0..1.0 (1.0 = identical). Useful for
     * ranking candidates and for a confidence threshold.
     */
    fun similarity(a: String, b: String): Double {
        if (a == b) return 1.0
        if (a.equals(b, ignoreCase = true)) return 1.0
        val maxLen = maxOf(a.length, b.length)
        if (maxLen == 0) return 1.0
        val s = a.lowercase()
        val t = b.lowercase()
        if (s == t) return 1.0
        return 1.0 - levenshtein(s, t).toDouble() / maxLen
    }

    /**
     * Pick the best-matching candidate for [query] among [candidates],
     * keyed by [key]. Returns null when no candidate clears a length-aware
     * threshold.
     *
     * The threshold scales with the longer string's length so that a single
     * typo in a short title ("wlak" -> "Walk", 0.5 similarity) still matches,
     * while an unrelated word of the same length stays below it:
     *
     *  - length 3:  ~0.45  (one edit out of three)
     *  - length 4:  ~0.50
     *  - length 5+: 0.60
     *
     * Callers may override [minThreshold] (a hard floor) or pass an explicit
     * fixed [threshold] (which disables the length-aware scaling when >= 0).
     */
    fun <T> bestMatch(
        query: String,
        candidates: List<T>,
        threshold: Double = -1.0,
        minThreshold: Double = 0.45,
        key: (T) -> String
    ): T? {
        val q = query.trim().lowercase()
        if (q.isEmpty() || candidates.isEmpty()) return null
        val fixed = threshold >= 0
        var best: T? = null
        var bestScore = Double.NEGATIVE_INFINITY
        for (c in candidates) {
            val title = key(c).lowercase()
            if (title.isEmpty()) continue
            val score = similarity(q, title)
            val required = if (fixed) threshold
            else maxOf(minThreshold, 1.0 - 2.0 / maxOf(q.length, title.length))
            if (score >= required && score > bestScore) {
                bestScore = score
                best = c
            }
        }
        return best
    }
}
