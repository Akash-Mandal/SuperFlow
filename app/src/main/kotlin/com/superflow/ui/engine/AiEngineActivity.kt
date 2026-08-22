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
import com.superflow.ui.common.InfoButton
import com.superflow.ui.common.AiParameterInfo
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

        // The preset chips are built after the fields they populate, but they
        // belong above them on screen, so remember the slot and insert there.
        val presetIndex = content.childCount

        val providerField = field("Provider name", prefs.providerName)
        val baseField = field("Base URL", prefs.baseUrl)
        val fallbackField = field("Fallback URL (optional)", prefs.fallbackUrl)
        val modelField = field("Model", prefs.model)
        val orgField = field("Organization ID (optional)", prefs.organizationId)
        val keyField = field("API key (leave blank to keep)", "")
        val headersField = field("Custom headers (one per line: Name: Value)", prefs.customHeaders, lines = 2)

        // Provider presets
        val presetChips = ChipGroup(this).apply { isSingleSelection = false }
        listOf(
            "OpenAI" to Pair("https://api.openai.com", "gpt-4o"),
            "Anthropic" to Pair("https://api.anthropic.com", "claude-sonnet-4-20250514"),
            "Groq" to Pair("https://api.groq.com/openai", "llama-3.3-70b-versatile"),
            "Ollama (local)" to Pair("http://localhost:11434", "llama3.1"),
            "OpenRouter" to Pair("https://openrouter.ai/api", "openai/gpt-4o"),
            "Together AI" to Pair("https://api.together.xyz", "meta-llama/Meta-Llama-3.1-70B-Instruct-Turbo")
        ).forEach { (name, pair) ->
            presetChips.addView(Chip(this).apply {
                text = name
                isCheckable = false
                setEnsureMinTouchTargetSize(false)
                setOnClickListener {
                    val pf = providerField; val bf = baseField; val mf = modelField
                    pf.setText(name); bf.setText(pair.first); mf.setText(pair.second)
                    findViewById<View>(R.id.root).snack("Preset loaded — edit and save")
                }
            })
        }
        content.addView(presetChips, presetIndex)
        content.addView(textCard("Key status", prefs.maskedKey()))

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(MaterialButton(this).apply {
            text = "Save"
            layoutParams = LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also { it.marginEnd = dpi(8) }
            setOnClickListener { saveProvider(providerField, baseField, modelField, keyField,
                fallbackField, orgField, headersField) }
        })
        row.addView(MaterialButton(this, null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Test"
            layoutParams = LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                saveProvider(providerField, baseField, modelField, keyField,
                    fallbackField, orgField, headersField, quiet = true)
                testConnection()
            }
        })
        content.addView(row)
        if (diagnostic.isNotBlank()) content.addView(textCard("Diagnostics", diagnostic))

        // Mode toggle: Default / Intermediate / Advanced
        content.addView(section("SETUP MODE"))
        val modeCard = layoutInflater.inflate(R.layout.item_text_card, content, false)
        val modeTitle = modeCard.findViewById<TextView>(R.id.text_title)
        val modeBody = modeCard.findViewById<TextView>(R.id.text_body)
        when (prefs.aiSetupMode) {
            "default" -> {
                modeTitle.text = "Default Mode"
                modeBody.text = "Simple presets for common use cases. Everything works well out of the box."
            }
            "intermediate" -> {
                modeTitle.text = "Intermediate Mode"
                modeBody.text = "Curated presets plus commonly useful options. The right balance for most users."
            }
            else -> {
                modeTitle.text = "Advanced Mode"
                modeBody.text = "Every parameter with full customization. For users who want complete control."
            }
        }
        val modeHolder = modeCard.findViewById<TextView>(R.id.text_title).parent as? LinearLayout ?: modeCard as LinearLayout
        val modeChips = ChipGroup(this).apply { isSingleSelection = true }
        listOf("default" to "Default", "intermediate" to "Intermediate", "advanced" to "Advanced")
            .forEach { (value, label) ->
                modeChips.addView(Chip(this).apply {
                    text = label
                    isCheckable = true
                    isChecked = prefs.aiSetupMode == value
                    setEnsureMinTouchTargetSize(false)
                    setOnClickListener {
                        prefs.aiSetupMode = value
                        rebuild()
                    }
                })
            }
        (modeHolder as? LinearLayout)?.addView(modeChips)
        content.addView(modeCard)

        // Generation — content depends on mode
        content.addView(section("GENERATION"))

        // === DEFAULT MODE ===
        if (prefs.aiSetupMode == "default") {
            content.addView(picker("Creativity level", listOf(
                "Precise (0.3)" to 30,
                "Balanced (0.7)" to 70,
                "Creative (1.0)" to 100,
                "Very creative (1.5)" to 150
            ), prefs.temperature) { prefs.temperature = it; rebuild() })
            infoViewWithShort("temperature")?.let { content.addView(it) }

            content.addView(picker("Response length", listOf(
                "Short (1024)" to 1024,
                "Medium (4096)" to 4096,
                "Long (8192)" to 8192,
                "Very long (16384)" to 16384
            ), prefs.maxTokens) { prefs.maxTokens = it; rebuild() })
            infoViewWithShort("max_tokens")?.let { content.addView(it) }

            content.addView(picker("Wait time", listOf(
                "Quick (60s)" to 60,
                "Normal (120s)" to 120,
                "Patient (300s)" to 300,
                "Very patient (600s)" to 600
            ), prefs.requestTimeoutSec) { prefs.requestTimeoutSec = it; rebuild() })
            infoViewWithShort("timeout")?.let { content.addView(it) }
        }

        // === INTERMEDIATE MODE ===
        if (prefs.aiSetupMode == "intermediate") {
            // Same curated pickers as Default
            content.addView(picker("Creativity level", listOf(
                "Precise (0.3)" to 30,
                "Balanced (0.7)" to 70,
                "Creative (1.0)" to 100,
                "Very creative (1.5)" to 150
            ), prefs.temperature) { prefs.temperature = it; rebuild() })
            infoView("temperature")?.let { content.addView(it) }

            content.addView(picker("Response length", listOf(
                "Short (1024)" to 1024,
                "Medium (4096)" to 4096,
                "Long (8192)" to 8192,
                "Very long (16384)" to 16384,
                "Maximum (32768)" to 32768
            ), prefs.maxTokens) { prefs.maxTokens = it; rebuild() })
            infoView("max_tokens")?.let { content.addView(it) }

            content.addView(picker("Wait time", listOf(
                "Quick (60s)" to 60,
                "Normal (120s)" to 120,
                "Patient (300s)" to 300,
                "Very patient (600s)" to 600
            ), prefs.requestTimeoutSec) { prefs.requestTimeoutSec = it; rebuild() })
            infoView("timeout")?.let { content.addView(it) }

            // Response format — useful for Blueprint Studio
            val fmtChipsInt = ChipGroup(this).apply { isSingleSelection = true }
            listOf("auto" to "Auto", "json" to "JSON mode", "text" to "Plain text").forEach { (v, l) ->
                fmtChipsInt.addView(Chip(this).apply {
                    text = l; isCheckable = true
                    isChecked = prefs.responseFormat == v
                    setEnsureMinTouchTargetSize(false)
                    setOnClickListener { prefs.responseFormat = v; rebuild() }
                })
            }
            val fmtCardInt = layoutInflater.inflate(R.layout.item_text_card, content, false)
            fmtCardInt.findViewById<TextView>(R.id.text_title).text = "Response format"
            fmtCardInt.findViewById<TextView>(R.id.text_body).text =
                "How the AI structures its output. Auto is best for most tasks."
            (fmtCardInt.findViewById<TextView>(R.id.text_title).parent as? LinearLayout)?.addView(fmtChipsInt)
            content.addView(fmtCardInt)
            infoView("response_format")?.let { content.addView(it) }

            // Retries — practical for reliability
            content.addView(picker("Retries on failure", listOf(
                "None (0)" to 0,
                "Standard (2)" to 2,
                "Persistent (3)" to 3,
                "Aggressive (5)" to 5
            ), prefs.retryCount) { prefs.retryCount = it; rebuild() })
            infoView("retries")?.let { content.addView(it) }

            // Conversation history — affects cost and quality
            content.addView(picker("Conversation history", listOf(
                "Short (10 messages)" to 10,
                "Normal (20 messages)" to 20,
                "Long (40 messages)" to 40,
                "Very long (80 messages)" to 80
            ), prefs.conversationHistoryLimit) { prefs.conversationHistoryLimit = it; rebuild() })
            infoView("history_limit")?.let { content.addView(it) }

            // Streaming toggle
            content.addView(toggles(listOf(
                Triple("Streaming (show responses as they generate)", prefs.streamingEnabled) { v: Boolean ->
                    prefs.streamingEnabled = v }
            )))
            infoView("streaming")?.let { content.addView(it) }
        }

        // === ADVANCED MODE ===
        if (prefs.aiSetupMode == "advanced") {
            content.addView(sliderParam("Temperature", 0, 200, prefs.temperature,
                hint = "0 = deterministic, 70 = balanced, 200 = creative") {
                prefs.temperature = it
            })

            content.addView(sliderParam("Top-p (nucleus)", 0, 100, prefs.topP,
                hint = "100 = disabled. Lower values focus on likely tokens.") {
                prefs.topP = it
            })
            infoView("top_p")?.let { content.addView(it) }

            content.addView(numberParam("Max tokens", prefs.maxTokens, 64, 131_072,
                hint = "Output length limit. Default 4096. GPT-4o supports up to 16384, Claude up to 8192.") {
                prefs.maxTokens = it
            })

            content.addView(sliderParam("Frequency penalty", -200, 200, prefs.frequencyPenalty,
                hint = "Penalizes tokens by how often they've appeared. 0 = off.") {
                prefs.frequencyPenalty = it
            })
            infoView("frequency_penalty")?.let { content.addView(it) }

            content.addView(sliderParam("Presence penalty", -200, 200, prefs.presencePenalty,
                hint = "Encourages new topics at positive values. 0 = off.") {
                prefs.presencePenalty = it
            })
            infoView("presence_penalty")?.let { content.addView(it) }

            content.addView(numberParam("Seed (-1 = random)", prefs.seed, -1, 2_147_483_647,
                hint = "Set a fixed seed for reproducible outputs. -1 for random.") {
                prefs.seed = it
            })
            infoView("seed")?.let { content.addView(it) }

            field("Stop sequences (comma-separated)", prefs.stopSequences)
            val stopCard = layoutInflater.inflate(R.layout.item_text_card, content, false)
            stopCard.findViewById<TextView>(R.id.text_title).text = "Stop sequences"
            stopCard.findViewById<TextView>(R.id.text_body).text = prefs.stopSequences.ifBlank { "None" }
            content.addView(stopCard)
            infoView("stop_sequences")?.let { content.addView(it) }

            // Response format
            val fmtChips = ChipGroup(this).apply { isSingleSelection = true }
            listOf("auto" to "Auto", "json" to "JSON mode", "text" to "Plain text").forEach { (v, l) ->
                fmtChips.addView(Chip(this).apply {
                    text = l; isCheckable = true
                    isChecked = prefs.responseFormat == v
                    setEnsureMinTouchTargetSize(false)
                    setOnClickListener { prefs.responseFormat = v; rebuild() }
                })
            }
            val fmtCard = layoutInflater.inflate(R.layout.item_text_card, content, false)
            fmtCard.findViewById<TextView>(R.id.text_title).text = "Response format"
            fmtCard.findViewById<TextView>(R.id.text_body).visible(false)
            (fmtCard.findViewById<TextView>(R.id.text_title).parent as? LinearLayout)?.addView(fmtChips)
            content.addView(fmtCard)
            infoView("response_format")?.let { content.addView(it) }

            content.addView(section("ADVANCED"))

            content.addView(numberParam("Timeout (seconds)", prefs.requestTimeoutSec, 5, 900,
                hint = "How long to wait for a response. Default 120s. Complex tasks may need 300+.") {
                prefs.requestTimeoutSec = it
            })

            content.addView(numberParam("Retries on failure", prefs.retryCount, 0, 5,
                hint = "Automatic retries for transient errors (429, 500, 502, 503, 504).") {
                prefs.retryCount = it
            })

            content.addView(numberParam("Conversation history", prefs.conversationHistoryLimit, 2, 100,
                hint = "Number of previous messages sent to the model. More = better context, higher cost.") {
                prefs.conversationHistoryLimit = it
            })

            content.addView(numberParam("Max context characters", prefs.maxContextChars, 1_000, 80_000,
                hint = "Maximum size of the app state sent in the system prompt. Default 12000.") {
                prefs.maxContextChars = it
            })

            content.addView(toggles(listOf(
                Triple("Streaming (experimental)", prefs.streamingEnabled) { v: Boolean ->
                    prefs.streamingEnabled = v },
                Triple("Request/response logging", prefs.requestLoggingEnabled) { v: Boolean ->
                    prefs.requestLoggingEnabled = v }
            )))
            infoView("logging")?.let { content.addView(it) }
        } // end advanced mode block

        // AI Instructions & Memory — always visible, both modes
        content.addView(section("INSTRUCTIONS & MEMORY"))
        content.addView(textCard("Explicit Instructions",
            if (prefs.aiInstructions.isNotBlank())
                "${prefs.aiInstructions.length} characters of custom instructions active"
            else "None set. Add rules the AI must always follow."))
        infoView("ai_instructions")?.let { content.addView(it) }

        val instrField = field("Rules the AI must follow (one per line)", prefs.aiInstructions, lines = 4)
        content.addView(MaterialButton(this, null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Save instructions"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                prefs.aiInstructions = instrField.text?.toString().orEmpty()
                findViewById<View>(R.id.root).snack("Instructions saved")
            }
        })

        content.addView(textCard("Local Memory",
            if (prefs.aiLocalMemory.isNotBlank())
                "${prefs.aiLocalMemory.lines().size} facts stored"
            else "Empty. Add facts about yourself the AI should remember."))
        infoView("local_memory")?.let { content.addView(it) }

        val memField = field("Facts the AI should remember (one per line)", prefs.aiLocalMemory, lines = 4)
        content.addView(MaterialButton(this, null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Save memory"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                prefs.aiLocalMemory = memField.text?.toString().orEmpty()
                findViewById<View>(R.id.root).snack("Memory saved")
            }
        })

        // Also keep the existing memory notes field
        content.addView(section("CONTEXT AND MEMORY"))
        content.addView(textCard("Override or extend",
            if (prefs.customSystemPrompt.isNotBlank()) "Custom prompt active (${prefs.customSystemPrompt.length} chars)"
            else "Using the built-in system prompt."))

        val suffixField = field("Extra instructions (appended to every prompt)",
            prefs.systemPromptSuffix, lines = 3)
        content.addView(MaterialButton(this, null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Save extra instructions"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                prefs.systemPromptSuffix = suffixField.text?.toString().orEmpty()
                findViewById<View>(R.id.root).snack("Saved")
            }
        })

        val customPromptField = field("Full custom system prompt (overrides built-in)",
            prefs.customSystemPrompt, lines = 5)
        content.addView(MaterialButton(this, null,
            androidx.appcompat.R.attr.borderlessButtonStyle).apply {
            text = "Save custom prompt"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                prefs.customSystemPrompt = customPromptField.text?.toString().orEmpty()
                findViewById<View>(R.id.root).snack(
                    if (prefs.customSystemPrompt.isBlank()) "Reverted to built-in prompt"
                    else "Custom prompt saved"
                )
                rebuild()
            }
        })
        content.addView(MaterialButton(this, null,
            androidx.appcompat.R.attr.borderlessButtonStyle).apply {
            text = "View built-in prompt"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                // Temporarily clear custom prompt to see the built-in one
                val saved = prefs.customSystemPrompt
                prefs.customSystemPrompt = ""
                val prompt = MainBrain.systemPrompt(prefs)
                prefs.customSystemPrompt = saved
                MaterialAlertDialogBuilder(this@AiEngineActivity)
                    .setTitle("Built-in system prompt")
                    .setMessage(prompt.take(4000))
                    .setPositiveButton(R.string.close, null).show()
            }
        })

        // Budget
        content.addView(section("BUDGET"))
        content.addView(toggles(listOf(
            Triple("Unlimited (no SuperFlow cap)", prefs.unlimitedBudget) { v: Boolean ->
                prefs.unlimitedBudget = v }
        )))
        if (!prefs.unlimitedBudget) {
            // Calls budget
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

            content.addView(numberParam("Monthly call budget", prefs.monthlyCallBudget, 1, 1_000_000,
                hint = "Hard cap on API calls per month. Default 5000.") {
                prefs.monthlyCallBudget = it
            })

            // Token budget
            if (prefs.monthlyTokenBudget > 0 || prefs.tokensThisMonth > 0) {
                val tokenCard = layoutInflater.inflate(R.layout.item_habit_stat, content, false)
                tokenCard.findViewById<TextView>(R.id.hs_title).text = "Tokens this month"
                val budget = prefs.monthlyTokenBudget
                tokenCard.findViewById<TextView>(R.id.hs_percent).text =
                    if (budget > 0) "${prefs.tokensThisMonth}/${budget}"
                    else "${prefs.tokensThisMonth} (no cap)"
                if (budget > 0) {
                    tokenCard.findViewById<LinearProgressIndicator>(R.id.hs_bar).setProgressCompat(
                        (prefs.tokensThisMonth * 100 / budget.coerceAtLeast(1)).coerceIn(0, 100), true
                    )
                }
                tokenCard.findViewById<TextView>(R.id.hs_detail).text = "Tap to reset."
                tokenCard.setOnClickListener { prefs.tokensThisMonth = 0; rebuild() }
                content.addView(tokenCard)
            }

            content.addView(numberParam("Monthly token budget (0 = unlimited)",
                prefs.monthlyTokenBudget, 0, 1_000_000_000,
                hint = "Cap on total tokens (input + output). 0 = no cap.") {
                prefs.monthlyTokenBudget = it
            })

            // Cost budget
            content.addView(numberParam("Monthly cost budget in cents (0 = unlimited)",
                prefs.monthlyCostBudgetCents, 0, 100_000_000,
                hint = "Approximate cost tracking. 0 = no cap. E.g. 5000 = \$50.") {
                prefs.monthlyCostBudgetCents = it
            })
        } else {
            content.addView(textCard("No SuperFlow cap",
                "Your provider's own rate limits and billing still apply. " +
                        "Calls this month: ${prefs.callsThisMonth}."))
            content.addView(MaterialButton(this, null,
                androidx.appcompat.R.attr.borderlessButtonStyle).apply {
                text = "Reset call counter"
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setOnClickListener { prefs.callsThisMonth = 0; rebuild() }
            })
        }

        // Context
        content.addView(section("CONTEXT AND MEMORY"))
        content.addView(toggles(listOf(
            Triple("Include identities, goals, systems, habits", prefs.contextIncludeHabits) { v: Boolean ->
                prefs.contextIncludeHabits = v },
            Triple("Include today's check-ins", prefs.contextIncludeCheckIns) { v: Boolean ->
                prefs.contextIncludeCheckIns = v },
            Triple("Include insights and stats", prefs.contextIncludeInsights) { v: Boolean ->
                prefs.contextIncludeInsights = v },
            Triple("Include reviews", prefs.contextIncludeReviews) { v: Boolean ->
                prefs.contextIncludeReviews = v },
            Triple("Include obstacle plans", prefs.contextIncludeObstacles) { v: Boolean ->
                prefs.contextIncludeObstacles = v },
            Triple("Include flows/routines", prefs.contextIncludeFlows) { v: Boolean ->
                prefs.contextIncludeFlows = v },
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
            androidx.appcompat.R.attr.borderlessButtonStyle).apply {
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

        // Voice / TTS / STT
        content.addView(section("VOICE"))
        content.addView(toggles(listOf(
            Triple("Voice input (STT)", prefs.voiceEnabled) { v: Boolean ->
                prefs.voiceEnabled = v },
            Triple("Voice output (TTS)", prefs.ttsEnabled) { v: Boolean ->
                prefs.ttsEnabled = v },
            Triple("Proactive AI suggestions", prefs.proactiveAi) { v: Boolean ->
                prefs.proactiveAi = v },
            Triple("Proactive notifications", prefs.proactiveNotifications) { v: Boolean ->
                prefs.proactiveNotifications = v }
        )))

        content.addView(sliderParam("TTS speech rate", 50, 200, prefs.ttsSpeechRate,
            hint = "100 = normal speed. 50 = half speed, 200 = double speed.") {
            prefs.ttsSpeechRate = it
        })

        content.addView(sliderParam("TTS pitch", 50, 200, prefs.ttsPitch,
            hint = "100 = normal pitch.") {
            prefs.ttsPitch = it
        })

        // STT provider
        val sttChips = ChipGroup(this).apply { isSingleSelection = true }
        listOf("platform" to "Android (Google)", "whisper_api" to "Whisper API",
            "whisper_local" to "Whisper local", "vosk" to "Vosk offline").forEach { (v, l) ->
            sttChips.addView(Chip(this).apply {
                text = l; isCheckable = true
                isChecked = prefs.sttProvider == v
                setEnsureMinTouchTargetSize(false)
                setOnClickListener { prefs.sttProvider = v; rebuild() }
            })
        }
        val sttCard = layoutInflater.inflate(R.layout.item_text_card, content, false)
        sttCard.findViewById<TextView>(R.id.text_title).text = "Speech-to-text provider"
        sttCard.findViewById<TextView>(R.id.text_body).visible(false)
        (sttCard.findViewById<TextView>(R.id.text_title).parent as? LinearLayout)?.addView(sttChips)
        content.addView(sttCard)

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
            androidx.appcompat.R.attr.borderlessButtonStyle).apply {
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
            com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Check data integrity"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                val issues = com.superflow.domain.Diagnostics.issues(bus.repo)
                val message = if (issues.isEmpty())
                    "All references are intact. No orphaned records found."
                else buildString {
                    append("Found ${issues.size} issue")
                    if (issues.size != 1) append("s")
                    append(":\n\n")
                    issues.forEach { append("· ${it.message}\n") }
                    append("\nThese rows reference records that no longer exist. " +
                            "Deleting a parent cascades its children, so orphans usually " +
                            "come from an import or an older app version.")
                }
                MaterialAlertDialogBuilder(this@AiEngineActivity)
                    .setTitle("Data integrity")
                    .setMessage(message)
                    .setPositiveButton(R.string.close, null)
                    .show()
            }
        })
        content.addView(MaterialButton(this, null,
            androidx.appcompat.R.attr.borderlessButtonStyle).apply {
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
            androidx.appcompat.R.attr.borderlessButtonStyle).apply {
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
        (card.findViewById<TextView>(R.id.text_title).parent as? LinearLayout)?.addView(chips)
        return card
    }

    private fun saveProvider(
        provider: TextInputEditText, base: TextInputEditText,
        model: TextInputEditText, key: TextInputEditText,
        fallback: TextInputEditText? = null,
        orgId: TextInputEditText? = null,
        headers: TextInputEditText? = null,
        quiet: Boolean = false
    ) {
        prefs.providerName = provider.text?.toString()?.trim().orEmpty().ifBlank { "Custom" }
        prefs.baseUrl = base.text?.toString()?.trim().orEmpty()
        prefs.model = model.text?.toString()?.trim().orEmpty().ifBlank { "gpt-4o" }
        val k = key.text?.toString()?.trim().orEmpty()
        if (k.isNotBlank()) prefs.apiKey = k
        fallback?.let { prefs.fallbackUrl = it.text?.toString()?.trim().orEmpty() }
        orgId?.let { prefs.organizationId = it.text?.toString()?.trim().orEmpty() }
        headers?.let { prefs.customHeaders = it.text?.toString()?.trim().orEmpty() }
        if (!quiet) {
            findViewById<View>(R.id.root).snack("Saved")
            rebuild()
        }
    }

    /** Slider with live value display for continuous parameters. */
    private fun sliderParam(
        title: String, min: Int, max: Int, current: Int,
        hint: String = "", onChange: (Int) -> Unit
    ): View {
        val card = layoutInflater.inflate(R.layout.item_text_card, content, false)
        val titleView = card.findViewById<TextView>(R.id.text_title)
        val bodyView = card.findViewById<TextView>(R.id.text_body)
        val holder = titleView.parent as? LinearLayout ?: card as LinearLayout

        fun displayValue(v: Int): String {
            // Show as decimal for parameters stored as ×100
            return when (title) {
                "Temperature" -> "%.2f".format(v / 100.0)
                "Top-p (nucleus)" -> "%.2f".format(v / 100.0)
                "Frequency penalty" -> "%.2f".format(v / 100.0)
                "Presence penalty" -> "%.2f".format(v / 100.0)
                "TTS speech rate" -> "%.2f×".format(v / 100.0)
                "TTS pitch" -> "%.2f×".format(v / 100.0)
                else -> v.toString()
            }
        }

        titleView.text = "$title: ${displayValue(current)}"
        bodyView.text = hint

        val slider = com.google.android.material.slider.Slider(this).apply {
            valueFrom = min.toFloat()
            valueTo = max.toFloat()
            stepSize = 1f
            value = current.toFloat().coerceIn(min.toFloat(), max.toFloat())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            addOnChangeListener { _, value, fromUser ->
                if (fromUser) {
                    val intVal = value.toInt()
                    titleView.text = "$title: ${displayValue(intVal)}"
                    onChange(intVal)
                }
            }
        }
        holder.addView(slider)
        return card
    }

    /** Free-text number input with validation and hint. */
    private fun numberParam(
        title: String, current: Int, min: Int, max: Int,
        hint: String = "", onChange: (Int) -> Unit
    ): View {
        val card = layoutInflater.inflate(R.layout.item_text_card, content, false)
        card.findViewById<TextView>(R.id.text_title).text = title
        card.findViewById<TextView>(R.id.text_body).text = hint
        val holder = card.findViewById<TextView>(R.id.text_title).parent as? LinearLayout ?: card as LinearLayout

        val input = com.google.android.material.textfield.TextInputLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setBoxCornerRadii(
                dpi(16).toFloat(), dpi(16).toFloat(),
                dpi(16).toFloat(), dpi(16).toFloat()
            )
            suffixText = if (max > 100_000) "" else "($min–$max)"
        }
        val edit = TextInputEditText(this).apply {
            setText(current.toString())
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                    android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
            isSingleLine = true
            setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    val v = text?.toString()?.toIntOrNull() ?: current
                    val clamped = v.coerceIn(min, max)
                    if (clamped != v) setText(clamped.toString())
                    onChange(clamped)
                }
            }
            setOnEditorActionListener { _, _, _ ->
                val v = text?.toString()?.toIntOrNull() ?: current
                val clamped = v.coerceIn(min, max)
                setText(clamped.toString())
                onChange(clamped)
                true
            }
        }
        input.addView(edit)
        holder.addView(input)
        return card
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

    private fun infoView(key: String): android.view.View? {
        val p = AiParameterInfo.parameters[key] ?: return null
        return InfoButton.create(this, p.title, p.longDesc)
    }
    private fun infoViewWithShort(key: String): android.view.View? {
        val p = AiParameterInfo.parameters[key] ?: return null
        return InfoButton.create(this, p.title, p.shortDesc + "\n\n" + p.longDesc)
    }
    private fun dpi(v: Int) = (v * resources.displayMetrics.density).toInt()
}
