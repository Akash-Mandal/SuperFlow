package com.superflow

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.superflow.data.Repository
import com.superflow.domain.Insights
import com.superflow.ui.MainActivity
import com.superflow.ui.blueprint.BlueprintActivity

/**
 * Dynamic launcher shortcuts.
 *
 * Provides shortcuts for:
 * 1. Top active habits (one-tap quick check-in).
 * 2. Blueprint Studio launcher.
 *
 * Recomputed whenever dynamic shortcuts need refreshing, strictly respecting
 * [ShortcutManagerCompat.getMaxShortcutCountPerActivity].
 */
object DynamicShortcuts {

    fun refresh(context: Context) {
        runCatching {
            val maxAllowed = ShortcutManagerCompat.getMaxShortcutCountPerActivity(context)
            if (maxAllowed <= 0) return

            val repo = Repository.get(context)
            val habits = repo.habits()
            val shortcuts = mutableListOf<ShortcutInfoCompat>()

            // Always reserve 1 slot for Blueprint Studio
            val habitLimit = (maxAllowed - 1).coerceAtLeast(0)

            if (habits.isNotEmpty() && habitLimit > 0) {
                val ranked = habits
                    .map { it to Insights.forHabit(repo, it) }
                    .sortedWith(
                        compareByDescending<Pair<com.superflow.data.model.Habit, com.superflow.data.model.HabitStats>> {
                            it.second.repetitions
                        }.thenByDescending { it.second.consistency30 }
                    )
                    .take(habitLimit)

                ranked.forEachIndexed { index, (habit, _) ->
                    val checkInIntent = Intent(context, MainActivity::class.java).apply {
                        action = Shortcuts.ACTION_CHECK_IN
                        putExtra(Shortcuts.EXTRA_HABIT_ID, habit.id)
                    }
                    shortcuts.add(
                        ShortcutInfoCompat.Builder(context, "checkin_${habit.id}")
                            .setShortLabel(habit.title.take(12).ifBlank { "Habit" })
                            .setLongLabel("Check in: ${habit.title.take(30)}")
                            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_check))
                            .setIntent(checkInIntent)
                            .setRank(index)
                            .build()
                    )
                }
            }

            // Blueprint Studio shortcut
            shortcuts.add(
                ShortcutInfoCompat.Builder(context, "blueprint")
                    .setShortLabel(context.getString(R.string.blueprint_studio))
                    .setIcon(IconCompat.createWithResource(context, R.drawable.ic_blueprint))
                    .setIntent(Intent(context, BlueprintActivity::class.java))
                    .setRank(99)
                    .build()
            )

            ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts.take(maxAllowed))
        }
    }
}
