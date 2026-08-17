package com.superflow.ui

import android.content.Intent
import android.view.View
import android.widget.LinearLayout
import com.superflow.data.Prefs
import com.superflow.domain.Actor
import com.superflow.domain.Serial
import com.superflow.notify.Reminders
import com.superflow.util.Dates
import com.superflow.util.jsonOf

/**
 * Settings: profile, reminders, checkpoints, data, privacy and the entry point
 * to the detailed AI Engine control center.
 */
class SettingsScreen(private val a: MainActivity) : Screen {

    private val p: Prefs get() = a.prefs

    override fun build(): View = a.scroller {
        setPadding(a.dp(20), a.dp(24), a.dp(20), a.dp(28))

        addView(a.title("Settings"))

        addView(a.heading("AI"))
        addView(a.card {
            addView(a.row {
                addView(a.column {
                    layoutParams = lp(0, WRAP, 1f)
                    addView(a.body("AI Engine", 16f, Palette.INK, bold = true))
                    addView(a.body(
                        if (p.fullControlActive()) "Full Control active"
                        else "Providers, autonomy, memory, budgets, diagnostics",
                        13f, Palette.INK_FAINT))
                })
                addView(a.ghostButton("Open") {
                    a.startActivity(Intent(a, AiEngineActivity::class.java))
                })
            })
            addView(a.divider())
            addView(toggle("Enable AI features", p.aiEnabled) {
                p.aiEnabled = it
                a.refresh()
            })
            addView(a.body("The app stays fully usable with AI, cloud access and local models off.",
                12f, Palette.INK_FAINT))
        })

        addView(a.heading("REMINDERS"))
        addView(a.card {
            addView(toggle("Reminders", p.remindersEnabled) {
                p.remindersEnabled = it
                Reminders.rescheduleAll(a)
                a.refresh()
            })
            addView(a.divider())
            addView(a.label("Quiet hours"))
            addView(a.row {
                val from = a.field("From", p.quietFrom).apply {
                    layoutParams = lp(0, WRAP, 1f).apply { rightMargin = a.dp(8) }
                }
                val to = a.field("To", p.quietTo).apply { layoutParams = lp(0, WRAP, 1f) }
                addView(from)
                addView(to)
                from.setOnFocusChangeListener { _, f -> if (!f) p.quietFrom = from.text.toString() }
                to.setOnFocusChangeListener { _, f -> if (!f) p.quietTo = to.text.toString() }
            })
            addView(a.body("No reminder is delivered inside quiet hours. " +
                    "Total reminder budget: ${p.reminderBudget} per day.", 12f, Palette.INK_FAINT))
            addView(a.spacer(8))
            addView(a.row {
                for (n in listOf(3, 6, 9)) {
                    addView(a.chip("$n/day", active = p.reminderBudget == n) {
                        p.reminderBudget = n
                        Reminders.rescheduleAll(a)
                        a.refresh()
                    })
                }
            })
        })

        addView(a.heading("CHECKPOINTS"))
        addView(a.card {
            addView(toggle("Daily checkpoints", p.checkpointsEnabled) {
                p.checkpointsEnabled = it
                Reminders.rescheduleAll(a)
                a.refresh()
            })
            if (p.checkpointsEnabled) {
                addView(a.divider())
                addView(timeField("Morning", p.morningCheckpoint) { p.morningCheckpoint = it })
                addView(timeField("Midday", p.middayCheckpoint) { p.middayCheckpoint = it })
                addView(timeField("Evening", p.eveningCheckpoint) { p.eveningCheckpoint = it })
            }
            addView(a.divider())
            addView(toggle("Track energy", p.energyTracking) {
                p.energyTracking = it
                a.refresh()
            })
        })

        addView(a.heading("YOUR DATA"))
        addView(a.card {
            val counts = a.repo.counts()
            addView(a.body("Everything is stored locally on this device.", 14f, Palette.INK))
            addView(a.spacer(6))
            addView(a.body(counts.entries.joinToString("  ·  ") { "${it.key} ${it.value}" },
                12f, Palette.INK_FAINT))
            addView(a.spacer(12))
            addView(a.row {
                addView(a.ghostButton("Export") { exportData() }
                    .apply { layoutParams = lp(0, WRAP, 1f).apply { rightMargin = a.dp(8) } })
                addView(a.ghostButton("Import") { importData() }
                    .apply { layoutParams = lp(0, WRAP, 1f) })
            })
            addView(a.spacer(8))
            addView(a.ghostButton("Delete all data", Palette.DANGER) {
                Dialogs.confirm(a, "Delete every identity, goal, habit and check-in? " +
                        "This cannot be undone from here.") {
                    a.bus.execute("delete_all_data", jsonOf("confirm" to true), Actor.USER)
                    a.toast("All data deleted")
                    a.refresh()
                }
            })
        })

        addView(a.heading("PRIVACY"))
        addView(a.card {
            addView(toggle("Crash reporting", p.crashReporting) {
                p.crashReporting = it
                a.refresh()
            })
            addView(a.spacer(6))
            addView(a.body(
                "Off by default. No account is required, nothing is uploaded unless you configure " +
                        "a cloud provider yourself, and API keys are never included in exports, " +
                        "prompts or logs.",
                13f, Palette.INK_SOFT))
        })

        addView(a.heading("ABOUT"))
        addView(a.card {
            addView(a.body("SuperFlow 1.0.0", 15f, Palette.INK, bold = true))
            addView(a.spacer(4))
            addView(a.body("Shape your system. Become your future self, one small action at a time.",
                13f, Palette.INK_SOFT))
            addView(a.spacer(10))
            addView(a.body(
                "SuperFlow is an independent project inspired by widely discussed behaviour-change " +
                        "principles. It is not affiliated with or endorsed by James Clear or the " +
                        "publishers of Atomic Habits.",
                12f, Palette.INK_FAINT))
            addView(a.spacer(10))
            addView(a.body(
                "SuperFlow is not a substitute for medical, mental-health, addiction or other " +
                        "professional care. If a behaviour feels unsafe, please reach out to a " +
                        "qualified person.",
                12f, Palette.INK_FAINT))
            addView(a.spacer(12))
            addView(a.ghostButton("Reset onboarding") {
                Dialogs.confirm(a, "Show the welcome flow again?") {
                    p.onboarded = false
                    a.startActivity(Intent(a, OnboardingActivity::class.java))
                    a.finish()
                }
            })
        })

        addView(a.spacer(24))
    }

    /* -------------------------------------------------------------- pieces */

    private fun toggle(label: String, value: Boolean, onChange: (Boolean) -> Unit): View = a.row {
        layoutParams = lp(MATCH, WRAP).apply { topMargin = a.dp(4); bottomMargin = a.dp(4) }
        addView(a.body(label, 15f, Palette.INK).apply { layoutParams = lp(0, WRAP, 1f) })
        addView(a.chip(if (value) "On" else "Off", active = value) { onChange(!value) })
    }

    private fun timeField(label: String, value: String, onSet: (String) -> Unit): View = a.row {
        layoutParams = lp(MATCH, WRAP).apply { bottomMargin = a.dp(6) }
        addView(a.body(label, 14f, Palette.INK_SOFT).apply { layoutParams = lp(0, WRAP, 1f) })
        addView(a.ghostButton(value) {
            Dialogs.text(a, label, "HH:mm", value) { text ->
                if (Dates.isValidTime(text.trim())) {
                    onSet(text.trim())
                    Reminders.rescheduleAll(a)
                    a.refresh()
                } else a.toast("Use a time like 08:00")
            }
        })
    }

    /* ---------------------------------------------------------------- data */

    private fun exportData() {
        val json = Serial.exportAll(a.repo).toString(2)
        try {
            val file = java.io.File(a.getExternalFilesDir(null) ?: a.filesDir,
                "superflow-export-${Dates.today()}.json")
            file.writeText(json)
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_SUBJECT, "SuperFlow export")
                putExtra(Intent.EXTRA_TEXT, json.take(400_000))
            }
            a.startActivity(Intent.createChooser(share, "Export SuperFlow data"))
            a.toast("Saved to ${file.name}")
        } catch (e: Exception) {
            Dialogs.info(a, "Export", json.take(4000))
        }
    }

    private fun importData() {
        Dialogs.text(a, "Import", "Paste exported JSON") { text ->
            try {
                val root = org.json.JSONObject(text)
                Serial.importAll(a.repo, root)
                a.toast("Import complete")
                a.refresh()
            } catch (e: Exception) {
                a.toast("That did not look like a SuperFlow export")
            }
        }
    }
}
