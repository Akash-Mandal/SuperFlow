package com.superflow.ui.engine

import android.content.Intent
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.superflow.R
import com.superflow.ai.Agent
import com.superflow.ai.MainBrain
import com.superflow.ai.Snapshots
import com.superflow.data.Prefs
import com.superflow.domain.Capabilities
import com.superflow.domain.CommandBus
import com.superflow.domain.Risk
import com.superflow.ui.activity.ActivityLogActivity
import com.superflow.ui.common.ScrollActivity
import com.superflow.ui.common.snack
import com.superflow.ui.common.visible
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The AI Engine control center.
 *
 * Providers, models, local coordinator, routing, autonomy and capability
 * permissions, context, memory, budgets, snapshots, diagnostics and privacy.
 */
class AiEngineActivity : ScrollActivity() {

    private val prefs by lazy { Prefs.get(this) }
    private val bus by lazy { CommandBus.get(this) }
    private var diagnostic = ""

    override fun titleText() = getString(R.string.ai_engine)

    override fun buildContent() {
        // Full Control
        content.addView(section("AUTONOMY"))
        val active = prefs.fullControlActive()
        val fc = layoutInflater.inflate(R.layout.item_ai_status, content, false)
        fc.findViewById<TextView>(R.id.status_title).text =
            if (active) "Full Control active" else "Full Control not activated"
        fc.findViewById<TextView>(R.id.status_body).text = if (active)
            "AI can run every registered app-local capability, including bulk, destructive, " +
                    "settings and Blueprint operations, without asking again."
        else
            "Activate once to remove repeated confirmations. SuperFlow keeps taking automatic " +
                    "snapshots, recording every action, and offering grouped undo."
        fc.findViewById<MaterialButton>(R.id.status_action).apply {
            text = if (active) "Deactivate" else "Activate"
            setOnClickListener {
                if (active) { prefs.fullControlActivated = false; rebuild() }
                else MaterialAlertDialogBuilder(this@AiEngineActivity)
                    .setTitle("Activate Full Control?")
                    .setMessage("AI gains unattended control of every SuperFlow capability on " +
                            "this device, including deletion. Snapshots and undo remain available.")
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton("Activate") { _, _ ->
                        prefs.fullControlActivated = true
                        prefs.autonomyProfile = Prefs.PROFILE_FULL
                        rebuild()
                    }.show()
            }
        }
        content.addView(fc)

        val profileChips = ChipGroup(this).apply { isSingleSelection = true }
        listOf(
            Prefs.PROFILE_FULL to "Full Control",
            Prefs.PROFILE_GUIDED to "Guided",
            Prefs.PROFILE_PREVIEW to "Preview"
        ).forEach { (id, label) ->
            profileChips.addView(Chip(this).apply {
                text = label
                isCheckable = true
                isChecked = prefs.autonomyProfile == id
                setEnsureMinTouchTargetSize(false)
                setOnClickListener { prefs.autonomyProfile = id; rebuild() }
            })
        }
        content.addView(profileChips)
        content.addView(textCard("Profile", when (prefs.autonomyProfile) {
            Prefs.PROFILE_FULL -> "Primary profile. After one activation AI runs every registered " +
                    "capability without repeated confirmations."
            Prefs.PROFILE_GUIDED -> "AI acts on low-risk work and asks before destructive changes."
            else -> "AI proposes; nothing runs until you say so."
        }))

        content.addView(section("CAPABILITY PERMISSIONS"))
        content.addView(toggles(listOf(
            Triple("Destructive operations", prefs.allowDestructive) { v: Boolean -> prefs.allowDestructive = v },
            Triple("Change app settings", prefs.allowSettingsChanges) { v: Boolean -> prefs.allowSettingsChanges = v },
            Triple("Background jobs", prefs.allowBackgroundJobs) { v: Boolean -> prefs.allowBackgroundJobs = v },
            Triple("Automatic snapshots", prefs.autoSnapshot) { v: Boolean -> prefs.autoSnapshot = v }
        )))

        // Engine
        content.addView(section("ENGINE"))
        content.addView(toggles(listOf(
            Triple("AI enabled", prefs.aiEnabled) { v: Boolean -> prefs.aiEnabled = v },
            Triple("Local Coordinator only", prefs.localCoordinatorOnly) { v: Boolean ->
                prefs.localCoordinatorOnly = v },
            Triple("Refine Blueprint with cloud", prefs.blueprintCloudRefine) { v: Boolean ->
                prefs.blueprintCloudRefine = v }
        )))
        content.addView(textCard("Local Coordinator",
            "Deterministic, offline and always available. It handles check-ins, focus, planning, " +
                    "creation, queries and undo with no network."))

        // Provider
        content.addView(section("CLOUD MAIN BRAIN"))
        val providerField = field("Provider name", prefs.providerName)
        val baseField = field("Base URL", prefs.baseUrl)
        val modelField = field("Model", prefs.model)
        val keyField = field("API key (leave blank to keep)", "")
        content.addView(textCard("Key status", prefs.maskedKey()))

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(MaterialButton(this).apply {
            text = "Save"
            layoutParams = LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also { it.marginEnd = dpi(8) }
            setOnClickListener { saveProvider(providerField, baseField, modelField, keyField) }
        })
        row.addView(MaterialButton(this, null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Test"
            layoutParams = LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                saveProvider(providerField, baseField, modelField, keyField, quiet = true)
                testConnection()
            }
        })
        content.addView(row)
        if (diagnostic.isNotBlank()) content.addView(textCard("Diagnostics", diagnostic))

        // Generation
        content.addView(section("GENERATION"))
        content.addView(picker("Temperature", listOf(0, 20, 50, 80).map {
            "${it / 100.0}" to it
        }, prefs.temperature) { prefs.temperature = it; rebuild() })
        content.addView(picker("Max tokens", listOf(600, 1200, 2400, 4096).map {
            "$it" to it
        }, prefs.maxTokens) { prefs.maxTokens = it; rebuild() })
        content.addView(picker("Timeout", listOf(30, 60, 120, 300).map {
            "${it}s" to it
        }, prefs.requestTimeoutSec) { prefs.requestTimeoutSec = it; rebuild() })

        // Budget
        content.addView(section("BUDGET"))
        content.addView(toggles(listOf(
            Triple("Unlimited (resource-based)", prefs.unlimitedBudget) { v: Boolean ->
                prefs.unlimitedBudget = v }
        )))
        if (!prefs.unlimitedBudget) {
            val card = layoutInflater.inflate(R.layout.item_habit_stat, content, false)
            card.findViewById<TextView>(R.id.hs_title).text = "Calls this month"
            card.findViewById<TextView>(R.id.hs_percent).text =
                "${prefs.callsThisMonth}/${prefs.monthlyCallBudget}"
            card.findViewById<LinearProgressIndicator>(R.id.hs_bar).setProgressCompat(
                (prefs.callsThisMonth * 100 / prefs.monthlyCallBudget.coerceAtLeast(1))
                    .coerceIn(0, 100), true
            )
            card.findViewById<TextView>(R.id.hs_detail).text = "Tap to reset the counter."
            card.setOnClickListener { prefs.callsThisMonth = 0; rebuild() }
            content.addView(card)
            content.addView(picker("Monthly budget",
                listOf(100, 500, 2000, 10000).map { "$it" to it },
                prefs.monthlyCallBudget) { prefs.monthlyCallBudget = it; rebuild() })
        } else {
            content.addView(textCard("No product cap",
                "Your provider's own limits still apply."))
        }

        // Context
        content.addView(section("CONTEXT AND MEMORY"))
        content.addView(toggles(listOf(
            Triple("Include habits and today", prefs.contextIncludeHabits) { v: Boolean ->
                prefs.contextIncludeHabits = v },
            Triple("Include insights", prefs.contextIncludeInsights) { v: Boolean ->
                prefs.contextIncludeInsights = v },
            Triple("Include personal notes", prefs.contextIncludeMemory) { v: Boolean ->
                prefs.contextIncludeMemory = v }
        )))
        val notes = field("Notes the assistant should remember", prefs.memoryNotes, lines = 3)
        content.addView(MaterialButton(this, null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Save notes"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                prefs.memoryNotes = notes.text?.toString().orEmpty()
                findViewById<View>(R.id.root).snack("Saved")
            }
        })
        content.addView(MaterialButton(this, null,
            com.google.android.material.R.attr.borderlessButtonStyle).apply {
            text = "Show context receipt"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                MaterialAlertDialogBuilder(this@AiEngineActivity)
                    .setTitle("Context receipt")
                    .setMessage(MainBrain.buildContext(bus.repo, prefs).take(4000))
                    .setPositiveButton(R.string.close, null).show()
            }
        })
        content.addView(textCard("Privacy",
            "A context receipt shows exactly what would be sent. API keys are never part of it."))

        // Snapshots
        content.addView(section("SNAPSHOTS"))
        val snaps = Snapshots.list(this)
        content.addView(textCard("Automatic safety copies",
            if (snaps.isEmpty()) "None yet. Taken before multi-step or destructive AI work."
            else snaps.take(6).joinToString("\n") { "· ${Snapshots.label(it)}" }))
        if (snaps.isNotEmpty()) {
            content.addView(MaterialButton(this, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = "Restore most recent"
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setOnClickListener {
                    MaterialAlertDialogBuilder(this@AiEngineActivity)
                        .setTitle("Replace all current data?")
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton("Restore") { _, _ ->
                            val ok = Snapshots.restore(this@AiEngineActivity, snaps.first(), bus)
                            findViewById<View>(R.id.root)
                                .snack(if (ok) "Snapshot restored" else "Restore failed")
                            rebuild()
                        }.show()
                }
            })
        }
        content.addView(MaterialButton(this, null,
            com.google.android.material.R.attr.borderlessButtonStyle).apply {
            text = "Take snapshot now"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                Snapshots.save(this@AiEngineActivity, bus)
                findViewById<View>(R.id.root).snack("Snapshot saved")
                rebuild()
            }
        })

        // Capability catalogue
        content.addView(section("CAPABILITY CATALOG"))
        content.addView(textCard(
            "Version ${Capabilities.CATALOG_VERSION} · ${bus.capabilities.size} capabilities",
            "Manual screens and AI tools call exactly these commands. That is what keeps the " +
                    "two surfaces equal.\n\n" +
                    bus.capabilities.joinToString("\n") {
                        val risk = when (it.risk) {
                            Risk.LOW -> "low"; Risk.MEDIUM -> "medium"; Risk.HIGH -> "high"
                        }
                        "· ${it.name} [$risk]${if (it.destructive) " destructive" else ""}"
                    }
        ))

        // Diagnostics
        content.addView(section("DIAGNOSTICS"))
        content.addView(MaterialButton(this, null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Verify state"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                MaterialAlertDialogBuilder(this@AiEngineActivity)
                    .setTitle("Verification")
                    .setMessage(Agent.get(this@AiEngineActivity).verify())
                    .setPositiveButton(R.string.close, null).show()
            }
        })
        content.addView(MaterialButton(this, null,
            com.google.android.material.R.attr.borderlessButtonStyle).apply {
            text = "Stop AI"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                Agent.get(this@AiEngineActivity).stop()
                findViewById<View>(R.id.root).snack("Stop requested")
            }
        })
        content.addView(MaterialButton(this, null,
            com.google.android.material.R.attr.borderlessButtonStyle).apply {
            text = getString(R.string.activity_trail)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                startActivity(Intent(this@AiEngineActivity, ActivityLogActivity::class.java))
            }
        })
    }

    /* --------------------------------------------------------------- helpers */

    private fun field(hint: String, value: String, lines: Int = 1): TextInputEditText {
        val v = layoutInflater.inflate(R.layout.part_field, content, false)
        v.findViewById<TextInputLayout>(R.id.field_layout).hint = hint
        val edit = v.findViewById<TextInputEditText>(R.id.field_edit)
        edit.setText(value)
        if (lines > 1) {
            edit.isSingleLine = false
            edit.minLines = lines
            edit.inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        content.addView(v)
        return edit
    }

    private fun toggles(items: List<Triple<String, Boolean, (Boolean) -> Unit>>): View {
        val card = layoutInflater.inflate(R.layout.item_setting_group, content, false)
        val holder = card.findViewById<LinearLayout>(R.id.group_container)
        items.forEachIndexed { index, (title, value, onChange) ->
            val row = layoutInflater.inflate(R.layout.item_setting_toggle, holder, false)
            row.findViewById<TextView>(R.id.toggle_title).text = title
            row.findViewById<TextView>(R.id.toggle_sub).visible(false)
            val sw = row.findViewById<MaterialSwitch>(R.id.toggle_switch)
            sw.isChecked = value
            row.setOnClickListener { sw.isChecked = !sw.isChecked; onChange(sw.isChecked) }
            holder.addView(row)
            if (index != items.lastIndex) {
                holder.addView(com.google.android.material.divider.MaterialDivider(this))
            }
        }
        return card
    }

    private fun picker(
        title: String, options: List<Pair<String, Int>>, current: Int, onPick: (Int) -> Unit
    ): View {
        val card = layoutInflater.inflate(R.layout.item_text_card, content, false)
        card.findViewById<TextView>(R.id.text_title).text = title
        card.findViewById<TextView>(R.id.text_body).visible(false)
        val chips = ChipGroup(this).apply { isSingleSelection = true }
        options.forEach { (label, value) ->
            chips.addView(Chip(this).apply {
                text = label
                isCheckable = true
                isChecked = value == current
                setEnsureMinTouchTargetSize(false)
                setOnClickListener { onPick(value) }
            })
        }
        (card.findViewById<TextView>(R.id.text_title).parent as LinearLayout).addView(chips)
        return card
    }

    private fun saveProvider(
        provider: TextInputEditText, base: TextInputEditText,
        model: TextInputEditText, key: TextInputEditText, quiet: Boolean = false
    ) {
        prefs.providerName = provider.text?.toString()?.trim().orEmpty().ifBlank { "Custom" }
        prefs.baseUrl = base.text?.toString()?.trim().orEmpty()
        prefs.model = model.text?.toString()?.trim().orEmpty().ifBlank { "gpt-4o-mini" }
        val k = key.text?.toString()?.trim().orEmpty()
        if (k.isNotBlank()) prefs.apiKey = k
        if (!quiet) {
            findViewById<View>(R.id.root).snack("Saved")
            rebuild()
        }
    }

    private fun testConnection() {
        diagnostic = "Testing…"
        rebuild()
        lifecycleScope.launch {
            val r = withContext(Dispatchers.IO) { MainBrain.testConnection(prefs) }
            diagnostic = if (r.ok) r.text else "Failed: ${r.error}"
            rebuild()
        }
    }

    private fun dpi(v: Int) = (v * resources.displayMetrics.density).toInt()
}
