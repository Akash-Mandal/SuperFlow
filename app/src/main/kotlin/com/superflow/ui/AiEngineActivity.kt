package com.superflow.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.superflow.ai.Agent
import com.superflow.ai.MainBrain
import com.superflow.ai.Snapshots
import com.superflow.data.Prefs
import com.superflow.domain.Capabilities
import com.superflow.domain.CommandBus
import com.superflow.domain.Risk
import java.util.concurrent.Executors

/**
 * The AI Engine control center.
 *
 * Providers, models, local coordinator, routing, autonomy and capability
 * permissions, context, memory, budgets, snapshots, diagnostics and privacy.
 */
class AiEngineActivity : Activity() {

    private lateinit var p: Prefs
    private lateinit var bus: CommandBus
    private lateinit var host: FrameLayout
    private val pool = Executors.newSingleThreadExecutor()
    private var diagnostic = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        p = Prefs.get(this)
        bus = CommandBus.get(this)
        host = FrameLayout(this).apply {
            setBackgroundColor(Palette.BG)
            layoutParams = lp(MATCH, MATCH)
        }
        setContentView(host)
        render()
    }

    private fun render() {
        host.removeAllViews()
        host.addView(build(), FrameLayout.LayoutParams(MATCH, MATCH))
    }

    private fun build(): View = scroller {
        setPadding(dp(20), dp(28), dp(20), dp(28))

        addView(title("AI Engine", 26f))
        addView(spacer(6))
        addView(body("Two control surfaces, one set of commands. Everything here is optional.",
            14f, Palette.INK_FAINT))

        /* ------------------------------------------------------ full control */
        addView(heading("AUTONOMY"))
        addView(fullControlCard())

        addView(card {
            addView(body("Profile", 15f, Palette.INK, bold = true))
            addView(spacer(8))
            addView(flowRow {
                for ((id, label) in listOf(
                    Prefs.PROFILE_FULL to "Full Control",
                    Prefs.PROFILE_GUIDED to "Guided",
                    Prefs.PROFILE_PREVIEW to "Preview"
                )) {
                    addView(chip(label, active = p.autonomyProfile == id) {
                        p.autonomyProfile = id
                        render()
                    })
                }
            })
            addView(spacer(6))
            addView(body(when (p.autonomyProfile) {
                Prefs.PROFILE_FULL -> "Primary profile. After one activation AI runs every registered " +
                        "capability without repeated confirmations. Snapshots, Activity and undo stay on."
                Prefs.PROFILE_GUIDED -> "AI acts on low-risk work and asks before destructive changes."
                else -> "AI proposes; nothing runs until you say so."
            }, 13f, Palette.INK_SOFT))
        })

        addView(card {
            addView(body("Capability permissions", 15f, Palette.INK, bold = true))
            addView(spacer(10))
            addView(toggle("Destructive operations", p.allowDestructive) { p.allowDestructive = it; render() })
            addView(toggle("Change app settings", p.allowSettingsChanges) { p.allowSettingsChanges = it; render() })
            addView(toggle("Background jobs", p.allowBackgroundJobs) { p.allowBackgroundJobs = it; render() })
            addView(toggle("Automatic snapshots", p.autoSnapshot) { p.autoSnapshot = it; render() })
        })

        /* ----------------------------------------------------------- engine */
        addView(heading("ENGINE"))
        addView(card {
            addView(toggle("AI enabled", p.aiEnabled) { p.aiEnabled = it; render() })
            addView(divider())
            addView(toggle("Local Coordinator only", p.localCoordinatorOnly) {
                p.localCoordinatorOnly = it; render()
            })
            addView(body("The Local Coordinator is deterministic, offline and always available. " +
                    "It handles check-ins, focus, planning, creation, queries and undo with no network.",
                13f, Palette.INK_FAINT))
        })

        addView(card {
            addView(body("Cloud Main Brain", 15f, Palette.INK, bold = true))
            addView(spacer(4))
            addView(body("Any OpenAI-compatible endpoint: hosted, LAN, or self-hosted " +
                    "(llama.cpp, Ollama, LM Studio, vLLM).", 13f, Palette.INK_FAINT))
            addView(spacer(12))

            addView(label("Provider name"))
            val provider = field("Custom OpenAI-compatible", p.providerName)
            addView(provider)

            addView(label("Base URL"))
            val base = field("https://api.example.com/v1", p.baseUrl)
            addView(base)

            addView(label("Model"))
            val model = field("gpt-4o-mini", p.model)
            addView(model)

            addView(label("API key"))
            val key = field("Stored outside backups and exports", "")
            addView(key)
            addView(body("Current: ${p.maskedKey()}", 12f, Palette.INK_FAINT))

            addView(spacer(12))
            addView(row {
                addView(ghostButton("Save") {
                    p.providerName = provider.text.toString().trim().ifBlank { "Custom" }
                    p.baseUrl = base.text.toString().trim()
                    p.model = model.text.toString().trim().ifBlank { "gpt-4o-mini" }
                    val k = key.text.toString().trim()
                    if (k.isNotBlank()) p.apiKey = k
                    toast("Saved")
                    render()
                }.apply { layoutParams = lp(0, WRAP, 1f).apply { rightMargin = dp(8) } })
                addView(ghostButton("Test") {
                    p.providerName = provider.text.toString().trim().ifBlank { "Custom" }
                    p.baseUrl = base.text.toString().trim()
                    p.model = model.text.toString().trim().ifBlank { "gpt-4o-mini" }
                    val k = key.text.toString().trim()
                    if (k.isNotBlank()) p.apiKey = k
                    testConnection()
                }.apply { layoutParams = lp(0, WRAP, 1f).apply { rightMargin = dp(8) } })
                addView(ghostButton("Clear key", Palette.DANGER) {
                    p.clearSecrets(); toast("Key cleared"); render()
                }.apply { layoutParams = lp(0, WRAP, 1f) })
            })
            if (diagnostic.isNotBlank()) {
                addView(spacer(10))
                addView(softCard(Palette.SURFACE_ALT) {
                    addView(body(diagnostic, 13f, Palette.INK_SOFT))
                })
            }
        })

        addView(card {
            addView(body("Generation", 15f, Palette.INK, bold = true))
            addView(spacer(10))
            addView(label("Temperature: ${p.temperature / 100.0}"))
            addView(flowRow {
                for (t in listOf(0, 20, 50, 80)) {
                    addView(chip("${t / 100.0}", active = p.temperature == t) {
                        p.temperature = t; render()
                    })
                }
            })
            addView(label("Max tokens: ${p.maxTokens}"))
            addView(flowRow {
                for (t in listOf(600, 1200, 2400, 4096)) {
                    addView(chip("$t", active = p.maxTokens == t) { p.maxTokens = t; render() })
                }
            })
            addView(label("Timeout: ${p.requestTimeoutSec}s"))
            addView(flowRow {
                for (t in listOf(30, 60, 120, 300)) {
                    addView(chip("${t}s", active = p.requestTimeoutSec == t) {
                        p.requestTimeoutSec = t; render()
                    })
                }
            })
        })

        /* ---------------------------------------------------------- budgets */
        addView(heading("BUDGET"))
        addView(card {
            addView(toggle("Unlimited (resource-based)", p.unlimitedBudget) {
                p.unlimitedBudget = it; render()
            })
            if (!p.unlimitedBudget) {
                addView(divider())
                addView(body("Used ${p.callsThisMonth} of ${p.monthlyCallBudget} calls",
                    14f, Palette.INK_SOFT))
                addView(progressBar(
                    (p.callsThisMonth.toFloat() / p.monthlyCallBudget.coerceAtLeast(1)).coerceIn(0f, 1f)
                ))
                addView(spacer(10))
                addView(flowRow {
                    for (n in listOf(100, 500, 2000, 10000)) {
                        addView(chip("$n", active = p.monthlyCallBudget == n) {
                            p.monthlyCallBudget = n; render()
                        })
                    }
                })
                addView(ghostButton("Reset counter") { p.callsThisMonth = 0; render() })
            } else {
                addView(body("No product-imposed cap. Your provider's own limits still apply.",
                    13f, Palette.INK_FAINT))
            }
        })

        /* ---------------------------------------------------------- context */
        addView(heading("CONTEXT AND MEMORY"))
        addView(card {
            addView(toggle("Include habits and today", p.contextIncludeHabits) {
                p.contextIncludeHabits = it; render()
            })
            addView(toggle("Include insights", p.contextIncludeInsights) {
                p.contextIncludeInsights = it; render()
            })
            addView(toggle("Include personal notes", p.contextIncludeMemory) {
                p.contextIncludeMemory = it; render()
            })
            addView(divider())
            addView(label("Notes the assistant should remember"))
            val notes = field("I work night shifts on Tuesdays", p.memoryNotes, lines = 3)
            addView(notes)
            addView(ghostButton("Save notes") {
                p.memoryNotes = notes.text.toString()
                toast("Saved")
            })
            addView(spacer(10))
            addView(ghostButton("Show context receipt") {
                Dialogs.info(this@AiEngineActivity, "Context receipt",
                    MainBrain.buildContext(bus.repo, p).take(4000))
            })
            addView(body("A context receipt shows exactly what would be sent. API keys are never " +
                    "part of it.", 12f, Palette.INK_FAINT))
        })

        /* -------------------------------------------------------- snapshots */
        addView(heading("SNAPSHOTS"))
        addView(card {
            val snaps = Snapshots.list(this@AiEngineActivity)
            addView(body("Taken automatically before multi-step or destructive AI work.",
                13f, Palette.INK_FAINT))
            addView(spacer(10))
            if (snaps.isEmpty()) addView(body("No snapshots yet.", 14f, Palette.INK_SOFT))
            for (f in snaps.take(6)) {
                addView(row {
                    layoutParams = lp(MATCH, WRAP).apply { bottomMargin = dp(8) }
                    addView(body(Snapshots.label(f), 13f, Palette.INK_SOFT).apply {
                        layoutParams = lp(0, WRAP, 1f)
                    })
                    addView(ghostButton("Restore") {
                        Dialogs.confirm(this@AiEngineActivity,
                            "Replace all current data with this snapshot?") {
                            if (Snapshots.restore(this@AiEngineActivity, f, bus))
                                toast("Snapshot restored") else toast("Restore failed")
                        }
                    })
                })
            }
            addView(spacer(6))
            addView(row {
                addView(ghostButton("Take snapshot now") {
                    Snapshots.save(this@AiEngineActivity, bus)
                    toast("Snapshot saved"); render()
                }.apply { layoutParams = lp(0, WRAP, 1f).apply { rightMargin = dp(8) } })
                addView(ghostButton("Clear all", Palette.DANGER) {
                    Snapshots.clear(this@AiEngineActivity); render()
                }.apply { layoutParams = lp(0, WRAP, 1f) })
            })
        })

        /* ----------------------------------------------------- capabilities */
        addView(heading("CAPABILITY CATALOG"))
        addView(card {
            addView(body("Version ${Capabilities.CATALOG_VERSION} · " +
                    "${Capabilities.all().size} registered capabilities", 14f, Palette.INK, bold = true))
            addView(spacer(4))
            addView(body("Manual screens and AI tools call exactly these commands. That is what " +
                    "keeps the two surfaces equal.", 13f, Palette.INK_FAINT))
            addView(spacer(10))
            for (c in bus.capabilities) {
                addView(row {
                    layoutParams = lp(MATCH, WRAP).apply { bottomMargin = dp(6) }
                    addView(iconDot(when (c.risk) {
                        Risk.LOW -> Palette.ACCENT
                        Risk.MEDIUM -> Palette.WARM
                        Risk.HIGH -> Palette.DANGER
                    }, 8))
                    addView(column {
                        layoutParams = lp(0, WRAP, 1f)
                        addView(body(c.name, 13f, Palette.INK, bold = true))
                        addView(body(c.summary, 12f, Palette.INK_FAINT))
                    })
                })
            }
        })

        /* ------------------------------------------------------ diagnostics */
        addView(heading("DIAGNOSTICS"))
        addView(card {
            addView(row {
                addView(ghostButton("Verify state") {
                    Dialogs.info(this@AiEngineActivity, "Verification", Agent.get(this@AiEngineActivity).verify())
                }.apply { layoutParams = lp(0, WRAP, 1f).apply { rightMargin = dp(8) } })
                addView(ghostButton("Stop AI") {
                    Agent.get(this@AiEngineActivity).stop()
                    toast("Stop requested. In-flight work halts at the next step.")
                }.apply { layoutParams = lp(0, WRAP, 1f) })
            })
            addView(spacer(8))
            addView(ghostButton("Activity trail") {
                startActivity(Intent(this@AiEngineActivity, ActivityLogActivity::class.java))
            })
        })

        addView(spacer(12))
        addView(ghostButton("Close") { finish() })
        addView(spacer(24))
    }

    private fun fullControlCard(): View {
        val active = p.fullControlActive()
        return softCard(if (active) Palette.ACCENT_SOFT else Palette.WARM_SOFT) {
            addView(body(if (active) "FULL CONTROL ACTIVE" else "FULL CONTROL NOT ACTIVATED",
                11f, if (active) Palette.ACCENT else Palette.WARM, bold = true))
            addView(spacer(8))
            addView(body(
                if (active)
                    "AI can run every registered app-local capability, including bulk, destructive, " +
                            "settings and Blueprint operations, without asking again."
                else
                    "Activate once to remove repeated confirmations. SuperFlow will keep taking " +
                            "automatic snapshots, recording every action in Activity, and offering " +
                            "grouped undo. You can revoke this at any time.",
                14f, Palette.INK
            ))
            addView(spacer(12))
            addView(ghostButton(if (active) "Deactivate" else "Activate Full Control",
                if (active) Palette.DANGER else Palette.ACCENT) {
                if (active) {
                    p.fullControlActivated = false
                    toast("Full Control deactivated")
                    render()
                } else {
                    Dialogs.confirm(this@AiEngineActivity,
                        "Grant AI unattended control of every SuperFlow capability on this device, " +
                                "including deletion? Snapshots and undo remain available.") {
                        p.fullControlActivated = true
                        p.autonomyProfile = Prefs.PROFILE_FULL
                        toast("Full Control activated")
                        render()
                    }
                }
            })
        }
    }

    private fun toggle(label: String, value: Boolean, onChange: (Boolean) -> Unit): View = row {
        layoutParams = lp(MATCH, WRAP).apply { topMargin = dp(4); bottomMargin = dp(4) }
        addView(body(label, 15f, Palette.INK).apply { layoutParams = lp(0, WRAP, 1f) })
        addView(chip(if (value) "On" else "Off", active = value) { onChange(!value) })
    }

    private fun testConnection() {
        diagnostic = "Testing..."
        render()
        pool.execute {
            val r = MainBrain.testConnection(p)
            runOnUiThread {
                diagnostic = if (r.ok) r.text else "Failed: ${r.error}"
                render()
            }
        }
    }
}
