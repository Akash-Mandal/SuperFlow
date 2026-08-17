import com.superflow.core.schedule.Recurrence
import com.superflow.util.extractJson

var pass = 0; var fail = 0
fun check(n: String, c: Boolean) { if (c) { pass++; println("  ok   $n") } else { fail++; println("  FAIL $n") } }
fun eq(n: String, a: Any?, b: Any?) = check("$n  ($a == $b)", a == b)

fun main() {
    println("Day parsing")
    eq("daily", Recurrence.parse("daily").encode(), "WEEKLY:1,2,3,4,5,6,7")
    eq("empty defaults daily", Recurrence.parse("").encode(), "WEEKLY:1,2,3,4,5,6,7")
    eq("everyday", Recurrence.parse("everyday").encode(), "WEEKLY:1,2,3,4,5,6,7")
    eq("weekdays", Recurrence.parse("weekdays").encode(), "WEEKLY:1,2,3,4,5")
    eq("weekends", Recurrence.parse("weekends").encode(), "WEEKLY:6,7")
    eq("mon,wed,fri", Recurrence.parse("mon,wed,fri").encode(), "WEEKLY:1,3,5")
    eq("monday tuesday", Recurrence.parse("monday tuesday").encode(), "WEEKLY:1,2")
    eq("sat and sun", Recurrence.parse("sat and sun").encode(), "WEEKLY:6,7")
    eq("junk defaults daily", Recurrence.parse("blah").encode(), "WEEKLY:1,2,3,4,5,6,7")

    println("Recurrence labels round-trip")
    eq("label daily", Recurrence.parse("daily").label(), "Every day")
    eq("label weekdays", Recurrence.parse("weekdays").label(), "Weekdays")
    eq("label weekends", Recurrence.parse("weekends").label(), "Weekends")
    eq("label mwf", Recurrence.parse("mon,wed,fri").label(), "Mon, Wed, Fri")
    for (spec in listOf("daily", "weekdays", "weekends", "mon,wed,fri", "mon", "3x a week", "every 4 days")) {
        val encoded = Recurrence.parse(spec).encode()
        eq("roundtrip $spec", Recurrence.decode(encoded).encode(), encoded)
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
