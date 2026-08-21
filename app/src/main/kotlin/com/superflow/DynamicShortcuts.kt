package com.superflow

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.superflow.data.Repository
import com.superflow.domain.Insights
import com.superflow.ui.MainActivity
import com.superflow.ui.detail.HabitDetailActivity

/**
 * Dynamic launcher shortcuts (#63).
 *
 * The manifest ships two static shortcuts (Ask SuperFlow, New habit). Here we
 * add up to [MAX_DYNAMIC] dynamic shortcuts for the user's most-consistent
 * active habits, so a long-press on the launcher offers a one-tap check-in
 * into the habit's detail screen. Recomputed whenever data changes.
 */
object DynamicShortcuts {

    private const val MAX_DYNAMIC = 3

    fun refresh(context: Context) {
        runCatching {
            val repo = Repository.get(context)
            val habits = repo.habits()
            if (habits.isEmpty()) {
                ShortcutManagerCompat.removeAllDynamicShortcuts(context)
                return
            }
            val ranked = habits
                .map { it to Insights.forHabit(repo, it) }
                .sortedWith(
                    compareByDescending<Pair<com.superflow.data.model.Habit, com.superflow.data.model.HabitStats>> {
                        it.second.repetitions
                    }.thenByDescending { it.second.consistency30 }
                )
                .take(MAX_DYNAMIC)

            val shortcuts = ranked.mapIndexed { index, (habit, _) ->
                val intent = Intent(context, HabitDetailActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    putExtra(HabitDetailActivity.EXTRA_HABIT_ID, habit.id)
                }
                ShortcutInfoCompat.Builder(context, "habit_${habit.id}")
                    .setShortLabel(habit.title.take(20))
                    .setLongLabel(habit.title.take(40))
                    .setIcon(IconCompat.createWithResource(context, R.drawable.ic_bolt))
                    .setIntent(intent)
                    .setRank(index)
                    .build()
            }
            ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
        }
    }
}
