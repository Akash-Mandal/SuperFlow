package com.superflow

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.superflow.data.Repository
import com.superflow.ui.MainActivity
import com.superflow.ui.blueprint.BlueprintActivity

/**
 * Dynamic app shortcuts.
 *
 * The top three habits get a one-tap quick check-in shortcut, so the most
 * important actions are reachable straight from the launcher. The list is
 * rebuilt on app start; stale shortcuts for deleted habits are dropped
 * automatically by [ShortcutManagerCompat.setDynamicShortcuts].
 */
object Shortcuts {

    const val ACTION_CHECK_IN = "com.superflow.intent.CHECK_IN"
    const val EXTRA_HABIT_ID = "habitId"

    fun update(context: Context) {
        val repo = Repository.get(context)
        val habits = repo.habits().sortedBy { it.orderIndex }.take(3)

        val shortcuts = mutableListOf<ShortcutInfoCompat>()
        habits.forEach { h ->
            shortcuts.add(
                ShortcutInfoCompat.Builder(context, "checkin_${h.id}")
                    .setShortLabel(h.title.take(12).ifBlank { "Habit" })
                    .setLongLabel("Check in: ${h.title}")
                    .setIcon(IconCompat.createWithResource(context, R.drawable.ic_check))
                    .setIntent(Intent(context, MainActivity::class.java).apply {
                        action = ACTION_CHECK_IN
                        putExtra(EXTRA_HABIT_ID, h.id)
                    })
                    .build()
            )
        }

        shortcuts.add(
            ShortcutInfoCompat.Builder(context, "blueprint")
                .setShortLabel(context.getString(R.string.blueprint_studio))
                .setIcon(IconCompat.createWithResource(context, R.drawable.ic_blueprint))
                .setIntent(Intent(context, BlueprintActivity::class.java))
                .build()
        )

        runCatching { ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts) }
    }
}
