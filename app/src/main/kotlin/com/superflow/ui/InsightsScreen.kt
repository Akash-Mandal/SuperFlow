package com.superflow.ui

import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.superflow.data.CheckInResult
import com.superflow.data.HabitMode
import com.superflow.domain.Insights
import com.superflow.util.Dates

/**
 * Insights without obsession.
 *
 * Repetitions, consistency, recovery and identity evidence - never a
 * leaderboard, never a discipline score, always with the sample size shown.
 */
class InsightsScreen(private val a: MainActivity) : Screen {

    override fun build(): View = a.scroller {
        setPadding(a.dp(20), a.dp(24), a.dp(20), a.dp(28))

        addView(a.title("Insights"))
        addView(a.spacer(4))
        addView(a.body("Evidence about your system, not a verdict about you.", 14f, Palette.INK_FAINT))

        addView(weekCard())
        addView(totalsCard())
        addView(identityCard())
        addView(habitsCard())
        addView(recoveryCard())
        addView(energyCard())
        addView(reduceCard())
        addView(a.spacer(24))
    }

    /* ------------------------------------------------------------ the week */

    private fun weekCard(): View {
        val bars = Insights.weekBars(a.repo)
        val max = (bars.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
        return a.card {
            addView(a.body("Last 7 days", 16f, Palette.INK, bold = true))
            addView(a.spacer(4))
            addView(a.body("Repetitions per day.", 13f, Palette.INK_FAINT))
            addView(a.spacer(14))
            addView(LinearLayout(a).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.BOTTOM
                layoutParams = lp(MATCH, a.dp(110))
                for ((letter, count) in bars) {
                    val col = LinearLayout(a).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                        layoutParams = LinearLayout.LayoutParams(0, MATCH, 1f)
                    }
                    col.addView(TextView(a).apply {
                        text = if (count > 0) count.toString() else ""
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                        setTextColor(Palette.INK_FAINT)
                        gravity = Gravity.CENTER
                        layoutParams = lp(MATCH, WRAP)
                    })
                    val h = a.dp(6 + (70 * count / max))
                    col.addView(View(a).apply {
                        background = rounded(
                            if (count > 0) Palette.ACCENT else Palette.SURFACE_ALT, a.dp(6)
                        )
                        layoutParams = lp(a.dp(22), h).apply { topMargin = a.dp(4) }
                    })
                    col.addView(TextView(a).apply {
                        text = letter
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                        setTextColor(Palette.INK_FAINT)
                        gravity = Gravity.CENTER
                        layoutParams = lp(MATCH, WRAP).apply { topMargin = a.dp(6) }
                    })
                    addView(col)
                }
            })
        }
    }

    /* -------------------------------------------------------------- totals */

    private fun totalsCard(): View {
        val window = Dates.lastDays(30)
        val checkIns = a.repo.checkIns().filter { it.date in window }
        val reps = checkIns.count { it.result == CheckInResult.DONE || it.result == CheckInResult.RESISTED }
        val skips = checkIns.count { it.result == CheckInResult.SKIPPED }
        val misses = checkIns.count { it.result == CheckInResult.MISSED || it.result == CheckInResult.SLIPPED }
        val recoveries = Insights.allStats(a.repo).sumOf { it.recoveries }
        return a.card {
            addView(a.body("Last 30 days", 16f, Palette.INK, bold = true))
            addView(a.spacer(12))
            addView(statRow("Repetitions", reps.toString(), Palette.ACCENT))
            addView(statRow("Intentional skips", skips.toString(), Palette.INK_SOFT))
            addView(statRow("Misses", misses.toString(), Palette.INK_SOFT))
            addView(statRow("Recoveries after a miss", recoveries.toString(), Palette.WARM))
            addView(a.spacer(8))
            addView(a.body("Recovery matters more than a perfect record.", 13f, Palette.INK_FAINT))
        }
    }

    private fun statRow(label: String, value: String, color: Int): View = a.row {
        layoutParams = lp(MATCH, WRAP).apply { bottomMargin = a.dp(10) }
        addView(a.body(label, 14f, Palette.INK_SOFT).apply { layoutParams = lp(0, WRAP, 1f) })
        addView(TextView(a).apply {
            text = value
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTextColor(color)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        })
    }

    /* ---------------------------------------------------- identity evidence */

    private fun identityCard(): View {
        val evidence = Insights.identityEvidence(a.repo)
        if (evidence.isEmpty()) return a.spacer(0)
        return a.card {
            addView(a.body("Identity evidence", 16f, Palette.INK, bold = true))
            addView(a.spacer(4))
            addView(a.body("Each repetition is a small vote for who you are becoming.",
                13f, Palette.INK_FAINT))
            addView(a.spacer(12))
            for ((statement, votes, habits) in evidence) {
                addView(a.row {
                    layoutParams = lp(MATCH, WRAP).apply { bottomMargin = a.dp(10) }
                    addView(a.column {
                        layoutParams = lp(0, WRAP, 1f)
                        addView(a.body(statement, 14f, Palette.INK, bold = true))
                        addView(a.body("$habits habits", 12f, Palette.INK_FAINT))
                    })
                    addView(TextView(a).apply {
                        text = votes.toString()
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                        setTextColor(Palette.ACCENT)
                        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    })
                })
            }
        }
    }

    /* ----------------------------------------------------------- per habit */

    private fun habitsCard(): View {
        val stats = Insights.allStats(a.repo)
        if (stats.isEmpty()) {
            return a.card {
                addView(a.body("No habits yet", 15f, Palette.INK, bold = true))
                addView(a.spacer(4))
                addView(a.body("Insights appear once you have something to measure.",
                    13f, Palette.INK_FAINT))
            }
        }
        return a.card {
            addView(a.body("By habit", 16f, Palette.INK, bold = true))
            addView(a.spacer(12))
            for (s in stats.sortedByDescending { it.consistency30 }) {
                addView(a.column {
                    layoutParams = lp(MATCH, WRAP).apply { bottomMargin = a.dp(14) }
                    addView(a.row {
                        addView(a.body(s.habit.title, 14f, Palette.INK, bold = true).apply {
                            layoutParams = lp(0, WRAP, 1f)
                        })
                        addView(a.body("${s.consistency30}%", 13f, Palette.ACCENT, bold = true))
                    })
                    addView(a.progressBar(s.consistency30 / 100f))
                    addView(a.spacer(6))
                    addView(a.body(
                        "${s.repetitions} reps · run of ${s.currentRun} · best ${s.bestRun}" +
                                (s.lastDone?.let { " · last ${Dates.shortDay(it)}" } ?: ""),
                        12f, Palette.INK_FAINT
                    ))
                    if (s.consistency30 in 1..39) {
                        addView(a.spacer(4))
                        addView(a.body("Try shrinking this one. A smaller version you actually do " +
                                "beats a bigger one you skip.", 12f, Palette.WARM))
                    }
                })
            }
        }
    }

    /* ------------------------------------------------------------ recovery */

    private fun recoveryCard(): View {
        val struggling = Insights.allStats(a.repo).filter { it.missesInARow >= 2 }
        if (struggling.isEmpty()) return a.spacer(0)
        return a.softCard(Palette.WARM_SOFT) {
            addView(a.body("Worth a redesign", 15f, Palette.WARM, bold = true))
            addView(a.spacer(6))
            addView(a.body("These have been missed more than once in a row. That is a signal about " +
                    "the design, not about your character.", 13f, Palette.INK))
            addView(a.spacer(10))
            for (s in struggling) {
                addView(a.body("· ${s.habit.title} (${s.missesInARow} in a row)", 14f, Palette.INK))
            }
        }
    }

    /* -------------------------------------------------------------- energy */

    private fun energyCard(): View {
        if (!a.prefs.energyTracking) return a.spacer(0)
        return a.card {
            addView(a.body("Energy pattern", 16f, Palette.INK, bold = true))
            addView(a.spacer(8))
            addView(a.body(Insights.energyPattern(a.repo), 13f, Palette.INK_SOFT))
        }
    }

    /* -------------------------------------------------------------- reduce */

    private fun reduceCard(): View {
        val text = Insights.reduceModeProgress(a.repo)
        if (text.isBlank()) return a.spacer(0)
        return a.card {
            addView(a.body("Reducing", 16f, Palette.INK, bold = true))
            addView(a.spacer(8))
            addView(a.body(text, 13f, Palette.INK_SOFT))
        }
    }
}
