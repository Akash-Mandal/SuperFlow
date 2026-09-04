package com.superflow

import android.content.Context

/**
 * Dynamic app shortcuts.
 *
 * Delegates to [DynamicShortcuts] to maintain a single, consolidated shortcut manager.
 */
object Shortcuts {

    const val ACTION_CHECK_IN = "com.superflow.intent.CHECK_IN"
    const val EXTRA_HABIT_ID = "habitId"

    fun update(context: Context) {
        DynamicShortcuts.refresh(context)
    }
}
