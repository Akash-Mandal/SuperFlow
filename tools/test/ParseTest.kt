import com.superflow.domain.Capabilities
import com.superflow.util.extractJson

var pass = 0; var fail = 0
fun check(n: String, c: Boolean) { if (c) { pass++; println("  ok   $n") } else { fail++; println("  FAIL $n") } }
fun eq(n: String, a: Any?, b: Any?) = check("$n  ($a == $b)", a == b)

fun main() {
    println("Day parsing")
    eq("daily", Capabilities.parseDays("daily"), 0b1111111)
    eq("empty defaults daily", Capabilities.parseDays(""), 0b1111111)
    eq("everyday", Capabilities.parseDays("everyday"), 0b1111111)
    eq("weekdays", Capabilities.parseDays("weekdays"), 0b0011111)
    eq("weekends", Capabilities.parseDays("weekends"), 0b1100000)
    eq("mon,wed,fri", Capabilities.parseDays("mon,wed,fri"), 0b0010101)
    eq("monday tuesday", Capabilities.parseDays("monday tuesday"), 0b0000011)
    eq("sat and sun", Capabilities.parseDays("sat and sun"), 0b1100000)
    eq("junk defaults daily", Capabilities.parseDays("blah"), 0b1111111)

    println("Day labels round-trip")
    eq("label daily", Capabilities.daysLabel(0b1111111), "Every day")
    eq("label weekdays", Capabilities.daysLabel(0b0011111), "Weekdays")
    eq("label weekends", Capabilities.daysLabel(0b1100000), "Weekends")
    eq("label mwf", Capabilities.daysLabel(0b0010101), "Mon, Wed, Fri")
    // round trip through the lowercase/no-space form the designer saves
    for (m in listOf(0b1111111, 0b0011111, 0b1100000, 0b0010101, 0b0000001)) {
        val label = Capabilities.daysLabel(m).lowercase().replace(" ", "")
        eq("roundtrip $m", Capabilities.parseDays(label), m)
    }

    println("JSON extraction")
    check("plain", extractJson("""{"a":1}""")?.optInt("a",0) == 1)
    check("fenced", extractJson("```json\n{\"a\":2}\n```")?.optInt("a",0) == 2)
    check("prose wrapped", extractJson("Sure! {\"a\":3} done.")?.optInt("a",0) == 3)
    check("nested braces", extractJson("x {\"a\":{\"b\":4}} y")?.optJSONObject("a")?.optInt("b",0) == 4)
    check("brace in string", extractJson("""{"a":"}{","b":5}""")?.optInt("b",0) == 5)
    check("escaped quote", extractJson("""{"a":"say \"hi\"","b":6}""")?.optInt("b",0) == 6)
    check("no json", extractJson("just words") == null)

    println()
    println("passed=$pass failed=$fail")
    if (fail > 0) kotlin.system.exitProcess(1)
}
