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

    private val fmt: SimpleDateFormat get() = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val human: SimpleDateFormat get() = SimpleDateFormat("EEEE, d MMMM", Locale.US)
    private val humanShort: SimpleDateFormat get() = SimpleDateFormat("d MMM", Locale.US)
    private val timeFmt: SimpleDateFormat get() = SimpleDateFormat("HH:mm", Locale.US)
    private val stampFmt: SimpleDateFormat get() = SimpleDateFormat("d MMM, HH:mm", Locale.US)

    fun today(): String = fmt.format(Date())

    fun cal(date: String): Calendar {
        val c = Calendar.getInstance()
        try {
            c.time = fmt.parse(date) ?: Date()
        } catch (e: Exception) {
            c.time = Date()
        }
        return c
    }

    fun of(c: Calendar): String = fmt.format(c.time)

    fun plusDays(date: String, days: Int): String {
        val c = cal(date)
        c.add(Calendar.DAY_OF_YEAR, days)
        return of(c)
    }

    fun yesterday(): String = plusDays(today(), -1)
    fun tomorrow(): String = plusDays(today(), 1)

    /** ISO day of week, Monday = 1 .. Sunday = 7. */
    fun isoDayOfWeek(date: String): Int {
        val dow = cal(date).get(Calendar.DAY_OF_WEEK) // Sunday = 1
        return if (dow == Calendar.SUNDAY) 7 else dow - 1
    }

    fun humanDay(date: String): String = human.format(cal(date).time)
    fun shortDay(date: String): String = humanShort.format(cal(date).time)

    fun dayLetter(date: String): String =
        when (isoDayOfWeek(date)) {
            1 -> "M"; 2 -> "T"; 3 -> "W"; 4 -> "T"; 5 -> "F"; 6 -> "S"; else -> "S"
        }

    /** Inclusive list of the last [n] dates ending today. */
    fun lastDays(n: Int, endDate: String = today()): List<String> {
        val out = ArrayList<String>(n)
        for (i in n - 1 downTo 0) out.add(plusDays(endDate, -i))
        return out
    }

    fun startOfWeek(date: String = today()): String {
        var d = date
        while (isoDayOfWeek(d) != 1) d = plusDays(d, -1)
        return d
    }

    fun weekLabel(date: String = today()): String {
        val s = startOfWeek(date)
        return "Week of ${shortDay(s)}"
    }

    fun monthLabel(date: String = today()): String =
        SimpleDateFormat("MMMM yyyy", Locale.US).format(cal(date).time)

    fun nowTime(): String = timeFmt.format(Date())

    fun stamp(millis: Long): String = stampFmt.format(Date(millis))

    fun minutesOfDay(hhmm: String): Int {
        val parts = hhmm.split(":")
        if (parts.size != 2) return -1
        val h = parts[0].trim().toIntOrNull() ?: return -1
        val m = parts[1].trim().toIntOrNull() ?: return -1
        if (h !in 0..23 || m !in 0..59) return -1
        return h * 60 + m
    }

    fun isValidTime(hhmm: String): Boolean = minutesOfDay(hhmm) >= 0

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
}
