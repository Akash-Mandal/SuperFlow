import com.superflow.util.Fuzzy

var pass = 0
var fail = 0
fun check(n: String, c: Boolean) { if (c) { pass++; println("  ok   $n") } else { fail++; println("  FAIL $n") } }
fun eq(n: String, a: Any?, b: Any?) = check("$n  ($a == $b)", a == b)

fun main() {
    println("Levenshtein distance")
    eq("identical", Fuzzy.levenshtein("walk", "walk"), 0)
    eq("one substitution", Fuzzy.levenshtein("walk", "wolk"), 1)
    eq("one insertion", Fuzzy.levenshtein("walk", "walks"), 1)
    eq("one deletion", Fuzzy.levenshtein("walks", "walk"), 1)
    eq("both empty", Fuzzy.levenshtein("", ""), 0)
    eq("empty vs word", Fuzzy.levenshtein("", "abc"), 3)
    eq("kitten/sitting classic", Fuzzy.levenshtein("kitten", "sitting"), 3)
    eq("raw distance is case-sensitive", Fuzzy.levenshtein("WALK", "walk"), 4)

    println("Similarity")
    eq("identical similarity", Fuzzy.similarity("walk", "walk"), 1.0)
    check("transposition wlak/walk ~0.5", kotlin.math.abs(Fuzzy.similarity("walk", "wlak") - 0.5) < 0.001)
    check("unrelated is zero", Fuzzy.similarity("walk", "zzzzzzz") == 0.0)
    check("empty strings identical", Fuzzy.similarity("", "") == 1.0)
    check("similarity is case-insensitive", Fuzzy.similarity("WALK", "walk") == 1.0)

    println("Best match (typo tolerance)")
    val habits = listOf("Walk", "Journal", "Meditate", "Read")
    eq("exact", Fuzzy.bestMatch("walk", habits) { it.lowercase() }, "Walk")
    eq("transposition -> Walk", Fuzzy.bestMatch("wlak", habits) { it.lowercase() }, "Walk")
    eq("read typo red -> Read", Fuzzy.bestMatch("red", habits) { it.lowercase() }, "Read")
    eq("empty query -> null", Fuzzy.bestMatch("", habits) { it.lowercase() }, null)
    eq("gibberish -> null", Fuzzy.bestMatch("xyzzynothing", habits) { it.lowercase() }, null)
    eq("all zs -> null", Fuzzy.bestMatch("zzz", habits) { it.lowercase() }, null)

    println("Threshold gating")
    val short = listOf("Gym", "Run")
    eq("short unrelated rejected", Fuzzy.bestMatch("zzz", short) { it.lowercase() }, null)
    eq("exact under strict threshold",
        Fuzzy.bestMatch("gym", short, threshold = 0.99) { it.lowercase() }, "Gym")
    // No candidate matches a typo when threshold is pinned to near-identical.
    eq("near-miss rejected when strict",
        Fuzzy.bestMatch("gyn", short, threshold = 0.99) { it.lowercase() }, null)

    println("Ranking picks closest")
    val names = listOf("Meditate", "Medication", "Mediate")
    eq("closest wins", Fuzzy.bestMatch("meditat", names) { it.lowercase() }, "Meditate")

    println()
    println("passed=$pass failed=$fail")
    if (fail > 0) kotlin.system.exitProcess(1)
}
