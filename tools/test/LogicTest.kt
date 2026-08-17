import com.superflow.data.model.*
import com.superflow.util.Dates

var pass = 0
var fail = 0
fun check(name: String, cond: Boolean) {
    if (cond) { pass++; println("  ok   $name") } else { fail++; println("  FAIL $name") }
}
fun eq(name: String, a: Any?, b: Any?) = check("$name  ($a == $b)", a == b)

fun main() {
    println("Dates")
    eq("isoDayOfWeek Monday", Dates.isoDayOfWeek("2026-08-17"), 1)
    eq("isoDayOfWeek Sunday", Dates.isoDayOfWeek("2026-08-16"), 7)
    eq("plusDays across month", Dates.plusDays("2026-08-31", 1), "2026-09-01")
    eq("plusDays negative", Dates.plusDays("2026-01-01", -1), "2025-12-31")
    eq("lastDays size", Dates.lastDays(7).size, 7)
    eq("lastDays ends today", Dates.lastDays(3, "2026-08-17").last(), "2026-08-17")
    eq("lastDays starts", Dates.lastDays(3, "2026-08-17").first(), "2026-08-15")
    eq("startOfWeek", Dates.startOfWeek("2026-08-19"), "2026-08-17")
    eq("minutesOfDay", Dates.minutesOfDay("07:30"), 450)
    eq("minutesOfDay invalid", Dates.minutesOfDay("25:00"), -1)
    eq("minutesOfDay junk", Dates.minutesOfDay("abc"), -1)
    check("isValidTime ok", Dates.isValidTime("23:59"))
    check("isValidTime bad", !Dates.isValidTime("7:5x"))
    eq("bucket morning", Dates.bucketOf("07:30"), "Morning")
    eq("bucket day", Dates.bucketOf("13:00"), "Day")
    eq("bucket evening", Dates.bucketOf("20:00"), "Evening")
    eq("bucket none", Dates.bucketOf(""), "Anytime")

    println("Habit scheduling")
    val daily = Habit(title = "Walk", daysMask = 0b1111111)
    check("daily runs Monday", daily.runsOn(1))
    check("daily runs Sunday", daily.runsOn(7))
    val weekdays = Habit(title = "Work", daysMask = 0b0011111)
    check("weekdays runs Friday", weekdays.runsOn(5))
    check("weekdays not Saturday", !weekdays.runsOn(6))
    check("weekdays not Sunday", !weekdays.runsOn(7))
    val weekends = Habit(title = "Rest", daysMask = 0b1100000)
    check("weekends runs Sat", weekends.runsOn(6))
    check("weekends not Wed", !weekends.runsOn(3))

    println("Habit ladder")
    val h = Habit(title = "Walk", tinyStart = "Put on shoes", minimumVersion = "Walk to corner",
        standardVersion = "Walk 10 minutes", stretchVersion = "Walk 25 minutes")
    eq("tiny", h.levelText(Level.TINY), "Put on shoes")
    eq("minimum", h.levelText(Level.MINIMUM), "Walk to corner")
    eq("standard", h.levelText(Level.STANDARD), "Walk 10 minutes")
    eq("stretch", h.levelText(Level.STRETCH), "Walk 25 minutes")
    val sparse = Habit(title = "Read")
    eq("fallback tiny -> title", sparse.levelText(Level.TINY), "Read")
    eq("fallback minimum -> title", sparse.levelText(Level.MINIMUM), "Read")
    val partial = Habit(title = "Read", tinyStart = "One page")
    eq("minimum falls back to tiny", partial.levelText(Level.MINIMUM), "One page")

    println("Contract")
    val c1 = Habit(title = "Walk", standardVersion = "Walk 10 minutes", tinyStart = "Put on shoes",
        anchorText = "breakfast", environmentPrep = "Leave shoes by the door",
        reward = "Enjoy my coffee").contract()
    check("contract has anchor", c1.startsWith("After breakfast"))
    check("contract has standard", c1.contains("Walk 10 minutes"))
    check("contract has tiny", c1.contains("Put on shoes"))
    check("contract has prep", c1.contains("Leave shoes by the door"))
    check("contract has reward", c1.contains("Enjoy my coffee"))
    val c2 = Habit(title = "Meditate", cueTime = "07:00", cuePlace = "the study").contract()
    check("contract time+place", c2.startsWith("At 07:00 in the study"))
    val c3 = Habit(title = "Journal").contract()
    check("contract bare", c3.startsWith("Today, I will Journal"))

    println("Level weights")
    check("tiny < standard", Level.TINY.weight < Level.STANDARD.weight)
    check("stretch > standard", Level.STRETCH.weight > Level.STANDARD.weight)

    println("LifeArea parsing")
    eq("from exact", LifeArea.from("HEALTH"), LifeArea.HEALTH)
    eq("from lowercase", LifeArea.from("health"), LifeArea.HEALTH)
    eq("from junk", LifeArea.from("zzz"), LifeArea.CUSTOM)
    eq("from null", LifeArea.from(null), LifeArea.CUSTOM)
    eq("Level.from junk", Level.from("zzz"), Level.STANDARD)

    println()
    println("passed=$pass failed=$fail")
    if (fail > 0) kotlin.system.exitProcess(1)
}
