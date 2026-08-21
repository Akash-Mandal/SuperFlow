package com.superflow.ai

import com.superflow.core.schedule.OpportunityStatus
import com.superflow.core.schedule.Recurrence
import com.superflow.core.time.SfTime
import com.superflow.data.Prefs
import com.superflow.data.Repository
import com.superflow.data.model.CheckInResult
import com.superflow.data.model.Habit
import com.superflow.data.model.HabitMode
import com.superflow.data.model.HabitStats
import com.superflow.domain.GrowthEngine
import com.superflow.domain.Insights
import com.superflow.domain.ReviewActions
import java.time.LocalDate

/**
 * Proactive, non-pushy suggestions (#2, #8, #63).
 *
 * All suggestions are derived locally from data the app already holds — no
 * network, no notification unless the user opts in via the worker. They are
 * surfaced gently in the Today/Coach UI as a single contextual nudge, ordered
 * by priority. The tone is supportive and specific; it never scolds.
 */
object Suggestions {

    enum class Tone { ENCOURAGE, NUDGE, INSIGHT, DESIGN }

    data class Suggestion(
        val title: String,
        val body: String,
        val tone: Tone,
        val habitId: String? = null,
        val action: String? = null
    )

    /**
     * Returns at most [limit] suggestions for [date]. Cheap enough to call
     * from the main list build; only scans active habits.
     */
    fun forToday(
        repo: Repository,
        date: LocalDate = repo.clock.today(),
        limit: Int = 1
    ): List<Suggestion> {
        val prefs = Prefs(repo.appContext)
        val habits = repo.habits()

        // A pending change from a recent review is the highest-leverage nudge.
        ReviewActions.openActions(repo, prefs).firstOrNull()?.let { (review, action) ->
            return listOf(Suggestion(
                "From your ${review.periodLabel} review",
                action.text,
                Tone.NUDGE
            ))
        }

        if (habits.isEmpty()) {
            return listOf(
                Suggestion(
                    "Start with one tiny action",
                    "A habit you can do in two minutes on your worst day is the seed. " +
                            "Open the Journey tab and design it.",
                    Tone.NUDGE,
                    action = "journey"
                )
            ).take(limit)
        }

        val stats = Insights.allStats(repo, date)
        val out = ArrayList<Suggestion>()

        // 0b. Surface a written if-then plan when a habit is open today (#10).
        repo.habitsForDay(date).firstOrNull { h ->
            repo.obstacles(h.id).isNotEmpty() &&
                    repo.checkIn(h.id, SfTime.format(date)) == null
        }?.let { h ->
            val plan = repo.obstacles(h.id).first()
            out.add(Suggestion(
                "You have a plan for ${h.title}",
                "If ${plan.ifText}, then ${plan.thenText}.",
                Tone.NUDGE, h.id, "check_tiny"
            ))
        }
        if (out.isNotEmpty()) return out.take(limit)

        // 1. Never-miss-twice: return candidates are the strongest signal.
        val returning = repo.returnCandidates(date)
        returning.firstOrNull()?.let { h ->
            val tiny = h.tinyStart.ifBlank { "the smallest version of ${h.title}" }
            out.add(Suggestion(
                "Return today",
                "${h.title} slipped yesterday. That is data, not a verdict. " +
                        "The rule is simple: never miss twice. Can you do $tiny?",
                Tone.NUDGE, h.id, "check_tiny"
            ))
        }

        // 2. Streak at risk: nothing done yet, late in the day, with an active run.
        val hour = repo.clock.nowTime().hour
        if (out.none { it.tone == Tone.NUDGE } && hour >= 17) {
            val (done, total) = Insights.dayProgress(repo, date)
            if (total > 0 && done < total) {
                val activeRun = stats.filter { it.currentRun >= 3 && !it.needsReturn }
                    .maxByOrNull { it.currentRun }
                if (activeRun != null) {
                    out.add(Suggestion(
                        "Protect a ${activeRun.currentRun}-day run",
                        "${activeRun.habit.title} has a run going. A tiny check-in today " +
                                "keeps it alive — and protects the identity you are building.",
                        Tone.ENCOURAGE, activeRun.habit.id, "check_tiny"
                    ))
                } else {
                    out.add(Suggestion(
                        "A small win still counts",
                        "$done of $total done. Even one Tiny version before bed moves the day.",
                        Tone.NUDGE
                    ))
                }
            }
        }

        // 3. Design help: a habit with persistently low consistency.
        if (out.isEmpty()) {
            stats.filter { it.hasEnoughData && it.consistency30 < 50 }
                .minByOrNull { it.consistency30 }?.let { worst ->
                    out.add(redesignSuggestion(worst))
                }
        }

        // 4. Graduation: a long, very consistent habit may be automatic now (#27).
        if (out.isEmpty()) {
            stats.filter {
                it.hasEnoughData && it.consistency30 >= 95 && it.bestRun >= 66
            }.minByOrNull { it.consistency30 }?.let { grad ->
                out.add(Suggestion(
                    "Is \"${grad.habit.title}\" automatic now?",
                    "Over 66+ days at ${grad.consistency30}%, this may no longer need willpower. " +
                            "Consider retiring it from active tracking and freeing attention " +
                            "for the next small habit.",
                    Tone.INSIGHT, grad.habit.id, "edit"
                ))
            }
        }

        // 5. Insight: a habit with high consistency deserves recognition.
        if (out.isEmpty()) {
            stats.filter { it.hasEnoughData && it.consistency30 >= 85 }
                .maxByOrNull { it.consistency30 }?.let { best ->
                    out.add(Suggestion(
                        "${best.consistency30}% on ${best.habit.title}",
                        "Over ${best.opportunities30} opportunities you showed up " +
                                "${best.consistency30}% of the time. That is how an identity forms.",
                        Tone.INSIGHT, best.habit.id
                    ))
                }
        }

        // 6. Reduce-mode: count resistances this week (#83).
        if (out.isEmpty()) {
            habits.filter { it.mode == HabitMode.REDUCE }.forEach { h ->
                val week = repo.checkInsBetween(
                    SfTime.format(date.minusDays(6)), SfTime.format(date))
                    .filter { it.habitId == h.id }
                val resisted = week.count { it.result == CheckInResult.RESISTED }
                if (resisted > 0) {
                    out.add(Suggestion(
                        "$resisted ${if (resisted == 1) "resistance" else "resistances"} this week",
                        "Each time you chose not to do \"${h.title}\", you voted for the person " +
                                "you are becoming.",
                        Tone.ENCOURAGE, h.id
                    ))
                }
            }
        }

        // 7. Energy-aware scheduling hint (#6).
        if (out.isEmpty()) energyHint(repo, date)?.let { out.add(it) }

        // 8. Growth recommendation (#4/#8) — step the ladder up or down.
        if (out.isEmpty()) {
            GrowthEngine.recommendations(repo, date).firstOrNull()?.let { rec ->
                out.add(Suggestion(
                    rec.title,
                    rec.body,
                    Tone.INSIGHT, rec.habit.id, "edit"
                ))
            }
        }

        // 9. Gentle encouragement if everything is done.
        if (out.isEmpty()) {
            val (done, total) = Insights.dayProgress(repo, date)
            if (total > 0 && done == total) {
                out.add(Suggestion(
                    "Today is complete",
                    "Every action was a vote for who you are becoming. " +
                            "Consider prepping one thing for tomorrow.",
                    Tone.ENCOURAGE
                ))
            }
        }

        return out.take(limit)
    }

    /**
     * If the user logs consistently low energy at a checkpoint, suggest
     * moving a non-protected habit scheduled after that point to an earlier,
     * higher-energy window. Needs at least 5 energy logs to be meaningful.
     */
    private fun energyHint(repo: Repository, date: LocalDate): Suggestion? {
        val logs = repo.energyLogs()
        if (logs.size < 5) return null
        val byCp = logs.groupBy { it.checkpoint }
            .mapValues { (_, list) -> list.sumOf { it.energy }.toDouble() / list.size }
        // Find a checkpoint with markedly low average energy (>= 1.0 below best).
        val best = byCp.values.maxOrNull() ?: return null
        val weakCp = byCp.entries.firstOrNull { best - it.value >= 1.0 }?.key ?: return null
        if (weakCp != com.superflow.data.model.Checkpoint.EVENING) return null
        // A habit scheduled after the weak checkpoint (cueTime >= 19:00) is the candidate.
        val eveningHabit = repo.habitsForDay(date).firstOrNull { h ->
            !h.protectedRoutine && com.superflow.util.Dates.minutesOfDay(h.cueTime) >= 19 * 60
        } ?: return null
        return Suggestion(
            "Energy is lower in the ${weakCp.label.lowercase()}",
            "Your ${weakCp.label.lowercase()} energy averages ${"%.1f".format(byCp[weakCp])}/5 vs " +
                    "${"%.1f".format(best)}/5 earlier. Consider moving \"${eveningHabit.title}\" " +
                    "to the morning or making it a tiny version.",
            Tone.INSIGHT, eveningHabit.id, "edit"
        )
    }

    private fun redesignSuggestion(s: HabitStats): Suggestion {
        val h = s.habit
        val reasons = ArrayList<String>()
        if (h.tinyStart.isBlank()) reasons += "it has no tiny start for hard days"
        val recurrence = Recurrence.decode(h.recurrenceRule)
        if (recurrence == Recurrence.EVERY_DAY && s.opportunities30 > 20)
            reasons += "every day may be too ambitious to start"
        if (h.cueTime.isBlank() && h.anchorText.isBlank() && h.cuePlace.isBlank())
            reasons += "it has no obvious cue to trigger it"
        val why = if (reasons.isEmpty()) "shrinking the standard version"
        else reasons.joinToString("; ")
        return Suggestion(
            "Redesign ${h.title}?",
            "At ${s.consistency30}% over ${s.opportunities30} opportunities, this habit is " +
                    "fighting you. The highest-leverage fix is $why. " +
                    "Smaller and more obvious wins more often.",
            Tone.DESIGN, h.id, "edit"
        )
    }
}
