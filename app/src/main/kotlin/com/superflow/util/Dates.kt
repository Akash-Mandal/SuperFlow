package com.superflow.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Date helpers. Domain dates are local `yyyy-MM-dd` strings so a check-in
 * always belongs to the day the user experienced, not a UTC instant.
 */
object Dates {

    private fun fmt() = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun today(): String = fmt().format(Date())

    fun cal(date: String): Calendar {
        val c = Calendar.getInstance()
        try {
            c.time = fmt().parse(date) ?: Date()
        } catch (e: Exception) {
            c.time = Date()
        }
        return c
    }

    fun of(c: Calendar): String = fmt().format(c.time)

    fun plusDays(date: String, days: Int): String {
        val c = cal(date)
        c.add(Calendar.DAY_OF_YEAR, days)
        return of(c)
    }

    fun yesterday(): String = plusDays(today(), -1)
    fun tomorrow(): String = plusDays(today(), 1)

    /** ISO day of week, Monday = 1 .. Sunday = 7. */
    fun isoDayOfWeek(date: String): Int {
        val dow = cal(date).get(Calendar.DAY_OF_WEEK)
        return if (dow == Calendar.SUNDAY) 7 else dow - 1
    }

    fun humanDay(date: String): String =
        SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(cal(date).time)

    fun shortDay(date: String): String =
        SimpleDateFormat("d MMM", Locale.getDefault()).format(cal(date).time)

    fun dayLetter(date: String): String = when (isoDayOfWeek(date)) {
        1 -> "M"; 2 -> "T"; 3 -> "W"; 4 -> "T"; 5 -> "F"; 6 -> "S"; else -> "S"
    }

    fun dayNumber(date: String): String =
        SimpleDateFormat("d", Locale.getDefault()).format(cal(date).time)

    /** Inclusive list of the last [n] dates ending at [endDate]. */
    fun lastDays(n: Int, endDate: String = today()): List<String> {
        val out = ArrayList<String>(n)
        for (i in n - 1 downTo 0) out.add(plusDays(endDate, -i))
        return out
    }

    fun startOfWeek(date: String = today()): String {
        var d = date
        var guard = 0
        while (isoDayOfWeek(d) != 1 && guard++ < 10) d = plusDays(d, -1)
        return d
    }

    fun weekLabel(date: String = today()): String = "Week of ${shortDay(startOfWeek(date))}"

    fun monthLabel(date: String = today()): String =
        SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(cal(date).time)

    fun nowTime(): String = SimpleDateFormat("HH:mm", Locale.US).format(Date())

    fun stamp(millis: Long): String =
        SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(millis))

    fun relativeStamp(millis: Long): String {
        val diff = System.currentTimeMillis() - millis
        return when {
            diff < 60_000 -> "just now"
            diff < 3_600_000 -> "${diff / 60_000}m ago"
            diff < 86_400_000 -> "${diff / 3_600_000}h ago"
            diff < 604_800_000 -> "${diff / 86_400_000}d ago"
            else -> stamp(millis)
        }
    }

    fun minutesOfDay(hhmm: String): Int {
        val parts = hhmm.split(":")
        if (parts.size != 2) return -1
        val h = parts[0].trim().toIntOrNull() ?: return -1
        val m = parts[1].trim().toIntOrNull() ?: return -1
        if (h !in 0..23 || m !in 0..59) return -1
        return h * 60 + m
    }

    fun isValidTime(hhmm: String): Boolean = minutesOfDay(hhmm) >= 0

    fun formatTime(minutes: Int): String = String.format(Locale.US, "%02d:%02d", minutes / 60, minutes % 60)

    /** Morning / Day / Evening bucket used to group the Today timeline. */
    fun bucketOf(hhmm: String): String {
        val m = minutesOfDay(hhmm)
        return when {
            m < 0 -> "Anytime"
            m < 12 * 60 -> "Morning"
            m < 17 * 60 -> "Day"
            else -> "Evening"
        }
    }

    fun greeting(): String = when (minutesOfDay(nowTime())) {
        in 0..(11 * 60 + 59) -> "Good morning"
        in (12 * 60)..(16 * 60 + 59) -> "Good afternoon"
        else -> "Good evening"
    }
}
