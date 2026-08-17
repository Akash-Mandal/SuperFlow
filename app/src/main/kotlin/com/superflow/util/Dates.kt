package com.superflow.util

import com.superflow.core.time.SfTime
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * Presentation-only date helpers.
 *
 * Domain date logic lives in [com.superflow.core.time.SfTime] and the injected
 * clock; this facade only formats values the UI already holds, so no screen
 * reaches for the system clock to make a decision.
 */
object Dates {

    fun humanDay(date: LocalDate): String = SfTime.humanDay(date)
    fun humanDay(iso: String): String =
        SfTime.parseDate(iso)?.let { SfTime.humanDay(it) } ?: iso

    fun shortDay(date: LocalDate): String = SfTime.shortDay(date)
    fun shortDay(iso: String): String =
        SfTime.parseDate(iso)?.let { SfTime.shortDay(it) } ?: iso

    fun dayLetter(date: LocalDate): String = SfTime.dayLetter(date)

    fun stamp(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        SfTime.stamp(Instant.ofEpochMilli(millis), zone)

    fun relativeStamp(millis: Long, now: Long = System.currentTimeMillis()): String =
        SfTime.relative(Instant.ofEpochMilli(millis), Instant.ofEpochMilli(now))

    fun isValidTime(text: String): Boolean = SfTime.isValidTime(text)
    fun minutesOfDay(text: String): Int = SfTime.minutesOfDay(text)
    fun format(date: LocalDate): String = SfTime.format(date)
}
