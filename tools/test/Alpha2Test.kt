import com.superflow.domain.Graduation
import com.superflow.domain.Search
import com.superflow.util.levenshtein

var pass = 0; var fail = 0
fun check(n: String, c: Boolean) { if (c) { pass++; println("  ok   $n") } else { fail++; println("  FAIL $n") } }
fun eq(n: String, a: Any?, b: Any?) = check("$n  ($a == $b)", a == b)
fun close(n: String, a: Float, b: Float) = check("$n  ($a ~ $b)", kotlin.math.abs(a - b) < 0.001f)

fun main() {
    println("Alpha2: fuzzy matching, search relevance, graduation rules")

    println("Levenshtein distance")
    eq("identical", levenshtein("walk", "walk"), 0)
    eq("insertion", levenshtein("walk", "walkk"), 1)
    eq("substitution", levenshtein("kitten", "sitting"), 3)
    eq("empty left", levenshtein("", "abc"), 3)
    eq("empty right", levenshtein("abc", ""), 3)
    eq("case sensitive distance", levenshtein("Walk", "walk"), 1)

    println("Search relevance")
    close("exact", Search.relevance("walk", "Walk"), 1.0f)
    close("prefix", Search.relevance("wal", "Walk"), 0.8f)
    close("contains", Search.relevance("alk", "Walk"), 0.5f)
    close("superset contains", Search.relevance("walkk", "Walk"), 0.3f)
    close("fuzzy typo", Search.relevance("wakk", "Walk"), 0.2f)
    close("no match", Search.relevance("zzzz", "Walk"), 0f)
    close("best field wins", Search.relevance("walk", "Gym", "Walk"), 1.0f)
    close("blank field ignored", Search.relevance("walk", "", "Walk"), 1.0f)
    check("all-blank fields are zero", Search.relevance("walk", "", " ") == 0f)

    println("Graduation rule")
    check("66 days at 90% graduates", Graduation.eligible(90, 66L, 10))
    check("65 days does not", !Graduation.eligible(90, 65L, 10))
    check("89% does not", !Graduation.eligible(89, 66L, 10))
    check("tiny sample does not", !Graduation.eligible(90, 66L, 4))
    check("plenty over the bar", Graduation.eligible(100, 200L, 50))

    println()
    println("passed=$pass failed=$fail")
    if (fail > 0) kotlin.system.exitProcess(1)
}
