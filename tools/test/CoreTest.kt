import com.superflow.core.schedule.Opportunities
import com.superflow.core.schedule.OpportunityStatus
import com.superflow.core.schedule.Recurrence
import com.superflow.core.schedule.Schedule
import com.superflow.core.time.FixedClock
import com.superflow.core.time.Greeting
import com.superflow.core.time.SfTime
import com.superflow.data.model.CheckIn
import com.superflow.data.model.CheckInResult
import com.superflow.data.model.Habit
import com.superflow.data.model.Level
import com.superflow.data.model.PauseWindow
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

var pass = 0
var fail = 0
fun check(n: String, c: Boolean) { if (c) { pass++; println("  ok   $n") } else { fail++; println("  FAIL $n") } }
fun eq(n: String, a: Any?, b: Any?) = check("$n  ($a == $b)", a == b)

private val HABIT = Habit(id = "h1", title = "Walk", tinyStart = "Shoes on")

private fun ci(date: String, result: CheckInResult) =
    CheckIn(habitId = "h1", date = date, result = result, level = Level.STANDARD)

private fun series(
    recurrence: Recurrence,
    checkIns: Map<String, CheckInResult>,
    days: Int,
    today: LocalDate,
    pauses: List<PauseWindow> = emptyList(),
    start: LocalDate = today.minusDays(365)
) = Opportunities.series(
    habit = HABIT,
    schedule = Schedule(recurrence = recurrence, startDate = start),
    checkIns = checkIns.mapKeys { LocalDate.parse(it.key) }
        .mapValues { ci(it.key.toString(), it.value) },
    pauses = pauses,
    dates = SfTime.lastDays(days, today),
    today = today
)

fun main() {
    val today = LocalDate.of(2026, 8, 17)   // a Monday

    println("Clock")
    val clock = FixedClock(Instant.parse("2026-08-17T09:30:00Z"), ZoneId.of("UTC"))
    eq("today", clock.today(), today)
    eq("nowTime", clock.nowTime(), LocalTime.of(9, 30))
    clock.advance(2, ChronoUnit.DAYS)
    eq("advance", clock.today(), LocalDate.of(2026, 8, 19))
    clock.setZone(ZoneId.of("Asia/Kolkata"))
    check("zone travel changes wall clock", clock.nowTime() != LocalTime.of(9, 30))

    println("DST correctness")
    val london = ZoneId.of("Europe/London")
    // 2026-03-29 01:00-02:00 does not exist in London.
    val gap = SfTime.resolve(LocalDate.of(2026, 3, 29), LocalTime.of(1, 30), london)
    check("DST gap resolves to a real instant", gap.toInstant() != null)
    // 2026-10-25 01:00-02:00 happens twice.
    val overlap = SfTime.resolve(LocalDate.of(2026, 10, 25), LocalTime.of(1, 30), london)
    check("DST overlap picks earlier offset",
        overlap.offset == london.rules.getValidOffsets(
            LocalDateTime.of(2026, 10, 25, 1, 30)).first())
    println("Leap day")
    eq("2028 is a leap year", LocalDate.of(2028, 2, 29).plusDays(1), LocalDate.of(2028, 3, 1))
    eq("Feb 29 minus a year", LocalDate.of(2028, 2, 29).minusYears(1), LocalDate.of(2027, 2, 28))

    println("Week start")
    eq("US week starts Sunday", SfTime.weekStartFor(java.util.Locale.US), DayOfWeek.SUNDAY)
    eq("FR week starts Monday", SfTime.weekStartFor(java.util.Locale.FRANCE), DayOfWeek.MONDAY)
    eq("startOfWeek Monday", SfTime.startOfWeek(LocalDate.of(2026, 8, 19)), today)
    eq("startOfWeek Sunday-based",
        SfTime.startOfWeek(LocalDate.of(2026, 8, 19), DayOfWeek.SUNDAY), LocalDate.of(2026, 8, 16))

    println("Recurrence")
    eq("daily", Recurrence.parse("daily").encode(), "WEEKLY:1,2,3,4,5,6,7")
    eq("weekdays", Recurrence.parse("weekdays").encode(), "WEEKLY:1,2,3,4,5")
    eq("weekends", Recurrence.parse("weekends").encode(), "WEEKLY:6,7")
    eq("mon,wed,fri", Recurrence.parse("mon,wed,fri").encode(), "WEEKLY:1,3,5")
    eq("3x a week", Recurrence.parse("3x a week").encode(), "TIMES_PER_WEEK:3")
    eq("3 times per week", Recurrence.parse("3 times per week").encode(), "TIMES_PER_WEEK:3")
    eq("every 3 days", Recurrence.parse("every 3 days").encode(), "EVERY_N:3")
    eq("round trip", Recurrence.decode("WEEKLY:2,4").encode(), "WEEKLY:2,4")
    eq("legacy mask", Recurrence.decode("31").encode(), "WEEKLY:1,2,3,4,5")
    eq("label weekdays", Recurrence.parse("weekdays").label(), "Weekdays")
    eq("label quota", Recurrence.parse("4x a week").label(), "4× a week")
    check("quota is flexible", Recurrence.parse("2x a week").isFlexible)
    check("weekly is not flexible", !Recurrence.parse("mon").isFlexible)

    println("EveryNDays anchoring")
    val anchor = LocalDate.of(2026, 8, 1)
    val every3 = Recurrence.EveryNDays(3)
    check("anchor day occurs", every3.occursOn(anchor, anchor))
    check("+3 occurs", every3.occursOn(anchor.plusDays(3), anchor))
    check("+4 does not", !every3.occursOn(anchor.plusDays(4), anchor))

    println("Opportunities: basic adherence")
    val s1 = series(Recurrence.EVERY_DAY, mapOf(
        "2026-08-14" to CheckInResult.DONE,
        "2026-08-15" to CheckInResult.DONE,
        "2026-08-16" to CheckInResult.MISSED
    ), 4, today)
    val (hits, total) = Opportunities.adherence(s1)
    eq("hits", hits, 2)
    eq("opportunities exclude pending today", total, 3)

    println("Planned skips never create misses")
    val s2 = series(Recurrence.EVERY_DAY, mapOf(
        "2026-08-14" to CheckInResult.DONE,
        "2026-08-15" to CheckInResult.SKIPPED,
        "2026-08-16" to CheckInResult.DONE
    ), 4, today)
    eq("skip excluded from denominator", Opportunities.adherence(s2).second, 2)
    eq("skip preserves the run", Opportunities.currentRun(s2, today), 2)

    println("Pauses never create misses")
    val pause = PauseWindow(startDate = "2026-08-15", endDate = "2026-08-16", reason = "holiday")
    val s3 = series(Recurrence.EVERY_DAY, mapOf("2026-08-14" to CheckInResult.DONE),
        4, today, pauses = listOf(pause))
    eq("paused days excluded", Opportunities.adherence(s3).second, 1)
    check("paused day has PAUSED status",
        s3.first { it.date == LocalDate.of(2026, 8, 15) }.status == OpportunityStatus.PAUSED)

    println("Today is never a miss")
    val s4 = series(Recurrence.EVERY_DAY, emptyMap(), 1, today)
    eq("today pending", s4.single().status, OpportunityStatus.PENDING)
    eq("no misses today", Opportunities.missesInARow(s4, today), 0)

    println("Unscheduled days are transparent")
    val s5 = series(Recurrence.WEEKDAYS, mapOf(
        "2026-08-13" to CheckInResult.DONE,   // Thursday
        "2026-08-14" to CheckInResult.DONE    // Friday
    ), 5, today)                              // through Monday, skipping the weekend
    eq("weekend not counted", Opportunities.adherence(s5).second, 2)
    eq("run spans the weekend", Opportunities.currentRun(s5, today), 2)

    println("Runs and recoveries")
    val s6 = series(Recurrence.EVERY_DAY, mapOf(
        "2026-08-11" to CheckInResult.DONE,
        "2026-08-12" to CheckInResult.MISSED,
        "2026-08-13" to CheckInResult.DONE,
        "2026-08-14" to CheckInResult.DONE,
        "2026-08-15" to CheckInResult.DONE,
        "2026-08-16" to CheckInResult.DONE
    ), 7, today)
    eq("current run", Opportunities.currentRun(s6, today), 4)
    eq("best run", Opportunities.bestRun(s6), 4)
    eq("recoveries", Opportunities.recoveries(s6), 1)

    println("Never miss twice")
    val s7 = series(Recurrence.EVERY_DAY, mapOf("2026-08-16" to CheckInResult.MISSED), 2, today)
    check("needsReturn after one miss", Opportunities.needsReturn(s7, today))
    val s8 = series(Recurrence.EVERY_DAY, mapOf(
        "2026-08-16" to CheckInResult.MISSED,
        "2026-08-17" to CheckInResult.DONE
    ), 2, today)
    check("no return once done", !Opportunities.needsReturn(s8, today))

    println("Flexible quota")
    // Week of Mon 10 Aug: three successes against a 3x quota is 100%.
    val s9 = series(Recurrence.TimesPerWeek(3), mapOf(
        "2026-08-10" to CheckInResult.DONE,
        "2026-08-12" to CheckInResult.DONE,
        "2026-08-14" to CheckInResult.DONE
    ), 7, LocalDate.of(2026, 8, 16))
    val (qh, qt) = Opportunities.quotaAdherence(s9, 3)
    eq("quota hits", qh, 3)
    eq("quota target", qt, 3)
    val s10 = series(Recurrence.TimesPerWeek(3), mapOf(
        "2026-08-10" to CheckInResult.DONE
    ), 7, LocalDate.of(2026, 8, 16))
    eq("under quota", Opportunities.quotaAdherence(s10, 3).first, 1)
    check("extra successes never exceed the quota",
        Opportunities.quotaAdherence(series(Recurrence.TimesPerWeek(2), mapOf(
            "2026-08-10" to CheckInResult.DONE,
            "2026-08-11" to CheckInResult.DONE,
            "2026-08-12" to CheckInResult.DONE
        ), 7, LocalDate.of(2026, 8, 16)), 2).first == 2)

    // A 3-day view of a 3x/week habit owes roughly 1, not 3.
    val partial = series(Recurrence.TimesPerWeek(3), mapOf(
        "2026-08-12" to CheckInResult.DONE
    ), 3, LocalDate.of(2026, 8, 12))
    check("partial week pro-rates the quota",
        Opportunities.quotaAdherence(partial, 3).second in 1..2)

    println("Schedule window")
    val bounded = Schedule(
        recurrence = Recurrence.EVERY_DAY,
        startDate = LocalDate.of(2026, 8, 15),
        endDate = LocalDate.of(2026, 8, 16)
    )
    check("before start inactive", !bounded.activeOn(LocalDate.of(2026, 8, 14)))
    check("inside window active", bounded.activeOn(LocalDate.of(2026, 8, 15)))
    check("after end inactive", !bounded.activeOn(LocalDate.of(2026, 8, 17)))

    println("Time parsing")
    eq("valid", SfTime.parseTime("07:30"), LocalTime.of(7, 30))
    eq("invalid hour", SfTime.parseTime("25:00"), null)
    eq("junk", SfTime.parseTime("abc"), null)
    eq("format", SfTime.formatTime(LocalTime.of(7, 5)), "07:05")
    eq("minutesOfDay", SfTime.minutesOfDay("07:30"), 450)

    println("Greeting buckets")
    eq("morning", SfTime.greetingFor(LocalTime.of(8, 0)), Greeting.MORNING)
    eq("afternoon", SfTime.greetingFor(LocalTime.of(13, 0)), Greeting.AFTERNOON)
    eq("evening", SfTime.greetingFor(LocalTime.of(20, 0)), Greeting.EVENING)

    println("Habit ladder still holds")
    val h = HABIT.copy(minimumVersion = "To the corner", standardVersion = "10 minutes")
    eq("tiny", h.levelText(Level.TINY), "Shoes on")
    eq("minimum", h.levelText(Level.MINIMUM), "To the corner")
    eq("stretch falls back to standard", h.levelText(Level.STRETCH), "10 minutes")

    println()
    println("passed=$pass failed=$fail")
    if (fail > 0) kotlin.system.exitProcess(1)
}
