package com.superflow.work

import android.content.Context

/**
 * Small marker store for background workers.
 *
 * Workers are periodic and idempotent; this SharedPreferences-backed object
 * records which milestones have already been detected (so they are not
 * re-announced) and the label of the last auto-generated weekly review
 * (so a week gets at most one draft).
 *
 * Kept separate from the user-facing [com.superflow.data.Prefs] because these
 * are internal housekeeping values, not settings the user edits.
 */
class WorkPrefs private constructor(context: Context) {

    private val p = context.applicationContext
        .getSharedPreferences("superflow_work", Context.MODE_PRIVATE)

    /** Returns true the first time a given (habit, milestone) pair is seen. */
    fun markMilestone(habitId: String, name: String): Boolean {
        val key = "milestone_$habitId$name"
        if (p.getBoolean(key, false)) return false
        p.edit().putBoolean(key, true).apply()
        return true
    }

    fun lastReviewWeek(): String = p.getString(KEY_REVIEW_WEEK, "").orEmpty()

    fun setLastReviewWeek(label: String) {
        p.edit().putString(KEY_REVIEW_WEEK, label).apply()
    }

    companion object {
        private const val KEY_REVIEW_WEEK = "last_review_week"

        @Volatile private var instance: WorkPrefs? = null
        fun get(context: Context): WorkPrefs =
            instance ?: synchronized(this) {
                instance ?: WorkPrefs(context.applicationContext).also { instance = it }
            }
    }
}
