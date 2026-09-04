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
import com.superflow.ui.detail.HabitDetailActivity

/**
 * Dynamic launcher shortcuts.
 *
 * Provides shortcuts for:
 * 1. Top active habits (one-tap quick check-in and detail access).
 * 2. Blueprint Studio launcher.
 *
 * Recomputed whenever dynamic shortcuts need refreshing.
 */
object DynamicShortcuts {

    private const val MAX_DYNAMIC_HABITS = 3

    fun refresh(context: Context) {
        runCatching {
            val repo = Repository.get(context)
            val habits = repo.habits()
            val shortcuts = mutableListOf<ShortcutInfoCompat>()

            if (habits.isNotEmpty()) {
                val ranked = habits
                    .map { it to Insights.forHabit(repo, it) }
                    .sortedWith(
                        compareByDescending<Pair<com.superflow.data.model.Habit, com.superflow.data.model.HabitStats>> {
                            it.second.repetitions
                        }.thenByDescending { it.second.consistency30 }
                    )
                    .take(MAX_DYNAMIC_HABITS)

                ranked.forEachIndexed { index, (habit, _) ->
                    // One-tap quick check-in shortcut
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
                            .setRank(index * 2)
                            .build()
                    )

                    // Detail view shortcut
                    val detailIntent = Intent(context, HabitDetailActivity::class.java).apply {
                        action = Intent.ACTION_VIEW
                        putExtra(HabitDetailActivity.EXTRA_HABIT_ID, habit.id)
                    }
                    shortcuts.add(
                        ShortcutInfoCompat.Builder(context, "habit_${habit.id}")
                            .setShortLabel(habit.title.take(20))
                            .setLongLabel(habit.title.take(40))
                            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_bolt))
                            .setIntent(detailIntent)
                            .setRank(index * 2 + 1)
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
                    .setRank(100)
                    .build()
            )

            ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
        }
    }
}
