package com.superflow.domain

import com.superflow.core.time.DayBucket
import com.superflow.core.time.SfTime
import com.superflow.data.model.CheckIn
import com.superflow.data.model.Habit

/**
 * Advanced analytics pack (Plan B F6): patterns the existing Insights graphs
 * cannot show because they count completions without asking when or with what
 * energy they happened.
 *
 * All functions are pure, work from supplied lists, and disclose their sample
 * sizes. No future predictions, no advice. Honest numbers for honest reflection.
 */
object Analytics {

    data class TimeOfDayPattern(
        val bucket: DayBucket,
        val completions: Int,
        val opportunities: Int,
        val rate: Double,
    ) {
        val hasEnoughData: Boolean get() = opportunities >= 5
    }

    data class RecoveryHistogram(
        val gaps: List<Int>,
        val medianGapDays: Double?,
        val sampleSize: Int,
    )

    data class ConsistencyBand(
        val weekIndex: Int,
        val p25: Double,
        val median: Double,
        val p75: Double,
        val mean: Double,
    )

    fun timeOfDayPatterns(
        habits: List<Habit>,
        checkIns: List<CheckIn>,
        today: java.time.LocalDate,
        days: Int = 30,
    ): List<TimeOfDayPattern> {
        val habitById = habits.associateBy { it.id }
        val checkByKey = checkIns.associateBy { "${it.habitId}:${it.date}" }
        val buckets = DayBucket.values().associateWith { mutableListOf<Boolean>() }

        for (habit in habits) {
            for (offset in 0 until days) {
                val date = today.minusDays(offset.toLong())
                val dateIso = SfTime.format(date)
                val time = SfTime.parseTime(habit.cueTime)
                val bucket = SfTime.bucketOf(time)
                val key = "${habit.id}:$dateIso"
                val ci = checkByKey[key]
                val isDone = ci?.isSuccess == true
                val isOpportunity = ci != null || habitById.containsKey(habit.id)
                buckets[bucket]?.add(isDone)
            }
        }
        return buckets.entries.map { (bucket, doneList) ->
            val completions = doneList.count { it }
            val opportunities = doneList.size
            TimeOfDayPattern(
                bucket = bucket,
                completions = completions,
                opportunities = opportunities,
                rate = if (opportunities == 0) 0.0 else completions.toDouble() / opportunities,
            )
        }.sortedBy { it.bucket.ordinal }
    }

    fun recoveryGaps(
        checkIns: List<CheckIn>,
        habitId: String,
    ): RecoveryHistogram {
        val sorted = checkIns.filter { it.habitId == habitId && it.date.isNotBlank() }
            .sortedBy { it.date }
        val gaps = mutableListOf<Int>()
        var lastMissDate: java.time.LocalDate? = null
        for (ci in sorted) {
            val date = SfTime.parseDate(ci.date) ?: continue
            if (ci.isMiss) {
                lastMissDate = date
            } else if (ci.isSuccess && lastMissDate != null) {
                val gap = java.time.temporal.ChronoUnit.DAYS.between(lastMissDate, date).toInt()
                if (gap in 1..30) gaps.add(gap)
                lastMissDate = null
            }
        }
        val median = if (gaps.isEmpty()) null else gaps.sorted().let { s ->
            val mid = s.size / 2
            if (s.size % 2 == 0) (s[mid - 1] + s[mid]) / 2.0 else s[mid].toDouble()
        }
        return RecoveryHistogram(gaps = gaps, medianGapDays = median, sampleSize = gaps.size)
    }

    fun weeklyBands(dailyCompletion: List<Double>, weeks: Int = 8): List<ConsistencyBand> {
        if (dailyCompletion.isEmpty()) return emptyList()
        val chunked = dailyCompletion.chunked(7).takeLast(weeks)
        return chunked.mapIndexed { idx, week ->
            if (week.isEmpty()) {
                ConsistencyBand(idx, 0.0, 0.0, 0.0, 0.0)
            } else {
                val sorted = week.sorted()
                val p25 = sorted[(sorted.size * 0.25).toInt().coerceIn(0, sorted.lastIndex)]
                val p75 = sorted[(sorted.size * 0.75).toInt().coerceIn(0, sorted.lastIndex)]
                val median = sorted[sorted.size / 2]
                val mean = week.average()
                ConsistencyBand(idx, p25, median, p75, mean)
            }
        }
    }
}
