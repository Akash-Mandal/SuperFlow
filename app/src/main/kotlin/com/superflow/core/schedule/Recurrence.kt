package com.superflow.core.schedule

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Recurrence rules.
 *
 * The plan calls for a `recurrenceRule` on Schedule rather than a bare weekday
 * mask, so a habit can be "every day", "weekdays", specific days, every N days,
 * or N times per week. Schedule edits never rewrite historical opportunities:
 * each opportunity records the schedule version that produced it.
 */
sealed class Recurrence {

    abstract fun occursOn(date: LocalDate, anchor: LocalDate): Boolean

    /** Serialised form stored in the database. */
    abstract fun encode(): String

    /** Every day. */
    object Daily : Recurrence() {
        override fun occursOn(date: LocalDate, anchor: LocalDate) = true
        override fun encode() = "DAILY"
    }

    /** Specific ISO weekdays, Monday = 1 .. Sunday = 7. */
    data class Weekly(val days: Set<Int>) : Recurrence() {
        override fun occursOn(date: LocalDate, anchor: LocalDate) =
            date.dayOfWeek.value in days
        override fun encode() = "WEEKLY:" + days.sorted().joinToString(",")
    }

    /** Every N days counted from the schedule's start date. */
    data class EveryNDays(val interval: Int) : Recurrence() {
        override fun occursOn(date: LocalDate, anchor: LocalDate): Boolean {
            if (interval <= 0) return false
            val delta = java.time.temporal.ChronoUnit.DAYS.between(anchor, date)
            return delta >= 0 && delta % interval == 0L
        }
        override fun encode() = "EVERY_N:$interval"
    }

    /**
     * A weekly quota with no fixed days: "three times a week".
     *
     * Every day is a valid opportunity; adherence is measured against the
     * quota rather than against specific weekdays. This is what stops a
     * flexible habit from generating false misses.
     */
    data class TimesPerWeek(val times: Int) : Recurrence() {
        override fun occursOn(date: LocalDate, anchor: LocalDate) = true
        override fun encode() = "TIMES_PER_WEEK:$times"
    }

    val isFlexible: Boolean get() = this is TimesPerWeek

    /** Human label used across the UI. */
    fun label(): String = when (this) {
        is Daily -> "Every day"
        is Weekly -> when (days.sorted()) {
            listOf(1, 2, 3, 4, 5, 6, 7) -> "Every day"
            listOf(1, 2, 3, 4, 5) -> "Weekdays"
            listOf(6, 7) -> "Weekends"
            else -> {
                val names = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                days.sorted().joinToString(", ") { names[it - 1] }
            }
        }
        is EveryNDays -> if (interval == 1) "Every day" else "Every $interval days"
        is TimesPerWeek -> "$times× a week"
    }

    companion object {
        val EVERY_DAY: Recurrence = Weekly(setOf(1, 2, 3, 4, 5, 6, 7))
        val WEEKDAYS: Recurrence = Weekly(setOf(1, 2, 3, 4, 5))
        val WEEKENDS: Recurrence = Weekly(setOf(6, 7))

        fun decode(text: String?): Recurrence {
            val raw = text?.trim().orEmpty()
            if (raw.isEmpty()) return EVERY_DAY
            return when {
                raw == "DAILY" -> Daily
                raw.startsWith("WEEKLY:") -> {
                    val days = raw.removePrefix("WEEKLY:")
                        .split(",").mapNotNull { it.trim().toIntOrNull() }
                        .filter { it in 1..7 }.toSet()
                    if (days.isEmpty()) EVERY_DAY else Weekly(days)
                }
                raw.startsWith("EVERY_N:") ->
                    EveryNDays(raw.removePrefix("EVERY_N:").toIntOrNull()?.coerceAtLeast(1) ?: 1)
                raw.startsWith("TIMES_PER_WEEK:") ->
                    TimesPerWeek(raw.removePrefix("TIMES_PER_WEEK:").toIntOrNull()?.coerceIn(1, 7) ?: 3)
                // Legacy: a 7-bit weekday mask, Monday = bit 0.
                raw.toIntOrNull() != null -> fromMask(raw.toInt())
                else -> EVERY_DAY
            }
        }

        fun fromMask(mask: Int): Recurrence {
            val days = (1..7).filter { (mask shr (it - 1)) and 1 == 1 }.toSet()
            return if (days.isEmpty()) EVERY_DAY else Weekly(days)
        }

        /** Parses natural language used by the AI coordinator and importer. */
        fun parse(spec: String): Recurrence {
            val s = spec.trim().lowercase()
            if (s.isBlank() || s in setOf("daily", "every day", "everyday")) return EVERY_DAY
            if (s == "weekdays") return WEEKDAYS
            if (s in setOf("weekends", "weekend")) return WEEKENDS

            Regex("""(\d+)\s*(?:x|times)\s*(?:a|per)?\s*week""").find(s)?.let {
                return TimesPerWeek(it.groupValues[1].toInt().coerceIn(1, 7))
            }
            Regex("""every\s+(\d+)\s*days?""").find(s)?.let {
                return EveryNDays(it.groupValues[1].toInt().coerceAtLeast(1))
            }

            val names = listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun")
            val days = HashSet<Int>()
            for (part in s.split(",", " ", "/", "and").map { it.trim() }.filter { it.isNotBlank() }) {
                val idx = names.indexOfFirst { part.startsWith(it) }
                if (idx >= 0) days.add(idx + 1)
            }
            return if (days.isEmpty()) EVERY_DAY else Weekly(days)
        }
    }
}

/**
 * A habit's schedule. Versioned, so editing it never rewrites history.
 */
data class Schedule(
    val recurrence: Recurrence = Recurrence.EVERY_DAY,
    val localTime: LocalTime? = null,
    val zoneId: ZoneId = ZoneId.systemDefault(),
    val startDate: LocalDate = LocalDate.now(),
    val endDate: LocalDate? = null,
    val version: Int = 1,
    val enabled: Boolean = true
) {
    fun activeOn(date: LocalDate): Boolean {
        if (!enabled) return false
        if (date.isBefore(startDate)) return false
        if (endDate != null && date.isAfter(endDate)) return false
        return recurrence.occursOn(date, startDate)
    }
}
