package com.superflow.util

/**
 * String-distance helpers shared by fuzzy habit lookup and global search.
 *
 * Kept free of Android references so the logic suites can exercise them on
 * the desktop JVM.
 */

/**
 * Classic Levenshtein edit distance: the minimum number of single-character
 * insertions, deletions or substitutions needed to turn [a] into [b].
 * Used for typo-tolerant matching (e.g. "walkk" still finds "Walk").
 */
fun levenshtein(a: String, b: String): Int {
    if (a == b) return 0
    if (a.isEmpty()) return b.length
    if (b.isEmpty()) return a.length
    val matrix = Array(a.length + 1) { IntArray(b.length + 1) }
    for (i in 0..a.length) matrix[i][0] = i
    for (j in 0..b.length) matrix[0][j] = j
    for (i in 1..a.length) {
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            matrix[i][j] = minOf(
                matrix[i - 1][j] + 1,      // deletion
                matrix[i][j - 1] + 1,      // insertion
                matrix[i - 1][j - 1] + cost // substitution
            )
        }
    }
    return matrix[a.length][b.length]
}
