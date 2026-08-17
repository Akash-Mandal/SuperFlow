package com.superflow.core.time

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * Domain time.
 *
 * The plan requires an injected clock and an explicit ZoneId, so every date
 * calculation is testable and survives reboots, time-zone travel, daylight
 * saving gaps/overlaps, locale week starts and leap days.
 *
 * A `LocalDate` is the domain's notion of "the day the user experienced";
 * instants are only used for ordering and audit.
 */
interface SuperFlowClock {
    fun now(): Instant
    fun zone(): ZoneId

    fun today(): LocalDate = ZonedDateTime.ofInstant(now(), zone()).toLocalDate()
    fun nowTime(): LocalTime = ZonedDateTime.ofInstant(now(), zone()).toLocalTime()
    fun nowDateTime(): LocalDateTime = LocalDateTime.ofInstant(now(), zone())
    fun millis(): Long = now().toEpochMilli()
}

/** Production clock: real time, the device's current zone. */
class SystemClock(private val zoneProvider: () -> ZoneId = { ZoneId.systemDefault() }) :
    SuperFlowClock {
    override fun now(): Instant = Instant.now()
    override fun zone(): ZoneId = zoneProvider()
}

/** Test clock: fixed or manually advanced. */
class FixedClock(
    private var instant: Instant,
    private var zone: ZoneId = ZoneId.of("UTC")
) : SuperFlowClock {
    override fun now(): Instant = instant
    override fun zone(): ZoneId = zone

    fun advance(amount: Long, unit: ChronoUnit) {
        instant = instant.plus(amount, unit)
    }

    fun setTo(dateTime: LocalDateTime) {
        instant = dateTime.atZone(zone).toInstant()
    }

    fun setZone(newZone: ZoneId) {
        zone = newZone
    }
}

/**
 * Date and time helpers built on java.time.
 *
 * All functions are pure: they take the values they need rather than reading
 * a global clock, so they can be exhaustively tested.
 */
object SfTime {

    val ISO_DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun parseDate(text: String): LocalDate? = try {
        LocalDate.parse(text.trim(), ISO_DATE)
    } catch (e: Exception) {
        null
    }

    fun format(date: LocalDate): String = date.format(ISO_DATE)

    fun parseTime(text: String): LocalTime? {
        val parts = text.trim().split(":")
        if (parts.size != 2) return null
        val h = parts[0].trim().toIntOrNull() ?: return null
        val m = parts[1].trim().toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return LocalTime.of(h, m)
    }

    fun formatTime(time: LocalTime): String =
        String.format(Locale.US, "%02d:%02d", time.hour, time.minute)

    fun isValidTime(text: String): Boolean = parseTime(text) != null

    fun minutesOfDay(text: String): Int = parseTime(text)?.let { it.hour * 60 + it.minute } ?: -1

    /**
     * Resolves a local date + wall-clock time to a real instant.
     *
     * During a daylight-saving *gap* the wall time does not exist, so the
     * instant is pushed to the end of the gap. During an *overlap* the earlier
     * offset is chosen, which is what a user setting "07:30" expects.
     */
    fun resolve(date: LocalDate, time: LocalTime, zone: ZoneId): ZonedDateTime {
        val naive = LocalDateTime.of(date, time)
        val rules = zone.rules
        val gap = rules.getTransition(naive)
        return when {
            gap != null && gap.isGap -> naive.atZone(zone).withEarlierOffsetAtOverlap()
            gap != null && gap.isOverlap -> naive.atZone(zone).withEarlierOffsetAtOverlap()
            else -> naive.atZone(zone)
        }
    }

    /** ISO day of week, Monday = 1 .. Sunday = 7. */
    fun isoDayOfWeek(date: LocalDate): Int = date.dayOfWeek.value

    fun startOfWeek(date: LocalDate, weekStart: DayOfWeek = DayOfWeek.MONDAY): LocalDate {
        var d = date
        var guard = 0
        while (d.dayOfWeek != weekStart && guard++ < 8) d = d.minusDays(1)
        return d
    }

    fun weekStartFor(locale: Locale): DayOfWeek = WeekFields.of(locale).firstDayOfWeek

    /** Inclusive list of the [count] dates ending at [end]. */
    fun lastDays(count: Int, end: LocalDate): List<LocalDate> =
        (count - 1 downTo 0).map { end.minusDays(it.toLong()) }

    fun daysBetween(from: LocalDate, to: LocalDate): Long = ChronoUnit.DAYS.between(from, to)

    /* --------------------------------------------------------- presentation */

    fun humanDay(date: LocalDate, locale: Locale = Locale.getDefault()): String =
        date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", locale))

    fun shortDay(date: LocalDate, locale: Locale = Locale.getDefault()): String =
        date.format(DateTimeFormatter.ofPattern("d MMM", locale))

    fun dayLetter(date: LocalDate, locale: Locale = Locale.getDefault()): String =
        date.dayOfWeek.getDisplayName(TextStyle.NARROW, locale)

    fun monthLabel(date: LocalDate, locale: Locale = Locale.getDefault()): String =
        date.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))

    fun stamp(instant: Instant, zone: ZoneId, locale: Locale = Locale.getDefault()): String =
        ZonedDateTime.ofInstant(instant, zone)
            .format(DateTimeFormatter.ofPattern("d MMM, HH:mm", locale))

    fun relative(instant: Instant, now: Instant): String {
        val seconds = ChronoUnit.SECONDS.between(instant, now)
        return when {
            seconds < 60 -> "just now"
            seconds < 3_600 -> "${seconds / 60}m ago"
            seconds < 86_400 -> "${seconds / 3_600}h ago"
            seconds < 604_800 -> "${seconds / 86_400}d ago"
            else -> "${seconds / 604_800}w ago"
        }
    }

    /** Morning / Day / Evening bucket used to group the Today timeline. */
    fun bucketOf(time: LocalTime?): DayBucket = when {
        time == null -> DayBucket.ANYTIME
        time.hour < 12 -> DayBucket.MORNING
        time.hour < 17 -> DayBucket.DAY
        else -> DayBucket.EVENING
    }

    fun greetingFor(time: LocalTime): Greeting = when {
        time.hour < 12 -> Greeting.MORNING
        time.hour < 17 -> Greeting.AFTERNOON
        else -> Greeting.EVENING
    }
}

enum class DayBucket(val label: String) {
    MORNING("Morning"), DAY("Day"), EVENING("Evening"), ANYTIME("Anytime")
}

enum class Greeting { MORNING, AFTERNOON, EVENING }
