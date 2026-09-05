package com.superflow.ui.common

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.superflow.R

/**
 * A small "ⓘ" info button that shows a description dialog when tapped.
 *
 * Used throughout the app to explain parameters, settings, and options
 * without cluttering the UI. Every setting that isn't self-explanatory
 * should have one of these next to it.
 *
 * Usage in code:
 *   InfoButton.show(activity, "Temperature", "Controls randomness. 0 = deterministic, 2 = very creative.")
 *
 * Usage as a view:
 *   val btn = InfoButton(context).apply {
 *       title = "Temperature"
 *       description = "Controls randomness..."
 *   }
 *   parent.addView(btn)
 */
class InfoButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : LinearLayout(context, attrs, defStyle) {

    var title: String = ""
        set(value) {
            field = value
            updateAccessibilityLabel()
        }
    var description: String = ""

    private val icon: ImageView

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        isClickable = true
        isFocusable = true

        icon = ImageView(context).apply {
            setImageResource(R.drawable.ic_info)
            val size = (20 * resources.displayMetrics.density).toInt()
            layoutParams = LayoutParams(size, size).apply {
                marginStart = (4 * resources.displayMetrics.density).toInt()
            }
            alpha = 0.6f
        }
        addView(icon)

        updateAccessibilityLabel()
        setOnClickListener { show() }
    }

    private fun updateAccessibilityLabel() {
        val label = if (title.isNotBlank()) "Info: $title" else "Info"
        contentDescription = label
        icon.contentDescription = label
    }

    fun show() {
        val activity = context as? AppCompatActivity ?: return
        MaterialAlertDialogBuilder(context)
            .setTitle(title.ifBlank { "Info" })
            .setMessage(description.ifBlank { "No description available." })
            .setPositiveButton(R.string.close, null)
            .show()
    }

    companion object {
        /**
         * Convenience: create and show an info dialog immediately.
         */
        fun show(activity: AppCompatActivity, title: String, description: String) {
            MaterialAlertDialogBuilder(activity)
                .setTitle(title)
                .setMessage(description)
                .setPositiveButton(R.string.close, null)
                .show()
        }

        /**
         * Convenience: create an InfoButton view ready to add to a parent.
         */
        fun create(context: Context, title: String, description: String): InfoButton {
            return InfoButton(context).apply {
                this.title = title
                this.description = description
            }
        }
    }
}

/**
 * Info descriptions for all AI Engine parameters.
 * Used by the Default/Advanced mode UI to show explanations.
 */
object AiParameterInfo {

    data class ParamInfo(
        val title: String,
        val shortDesc: String,     // One line for Default mode
        val longDesc: String       // Full explanation for Advanced mode info button
    )

    val parameters = mapOf(
        "temperature" to ParamInfo(
            "Temperature",
            "How creative vs predictable the AI is",
            "Controls the randomness of the AI's responses. Lower values (0.0-0.3) make the AI more " +
                    "deterministic and focused — good for structured tasks like creating habits. " +
                    "Higher values (0.7-1.0) make it more creative and varied — good for brainstorming " +
                    "and open-ended coaching. Values above 1.0 are rarely useful.\n\n" +
                    "Recommended: 0.7 for general use, 0.3 for Blueprint Studio, 1.0 for creative brainstorming."
        ),
        "top_p" to ParamInfo(
            "Top-p (Nucleus Sampling)",
            "Limits the AI to the most likely words",
            "An alternative to temperature. Instead of sampling from all possible words, the AI only " +
                    "considers the top P% of likely words. 1.0 = consider all words (disabled). " +
                    "0.5 = only consider the top 50% most likely words.\n\n" +
                    "Usually leave at 1.0. Lower values make output more focused but can reduce quality. " +
                    "Don't change both temperature and top_p at the same time — pick one."
        ),
        "max_tokens" to ParamInfo(
            "Max Tokens",
            "Maximum length of AI responses",
            "The maximum number of tokens (roughly words) the AI can generate in one response. " +
                    "One token ≈ 4 characters or 0.75 words.\n\n" +
                    "4,096 tokens ≈ 3,000 words — enough for most tasks.\n" +
                    "16,384 tokens ≈ 12,000 words — needed for Blueprint Studio compilation.\n" +
                    "Higher values cost more and take longer. Set based on your model's maximum.\n\n" +
                    "If responses are cut off mid-sentence, increase this value."
        ),
        "frequency_penalty" to ParamInfo(
            "Frequency Penalty",
            "Reduces repetition of words",
            "Penalizes words that have already appeared in the response. Positive values (0.1-0.5) " +
                    "reduce repetition. Negative values encourage repetition.\n\n" +
                    "Leave at 0 unless you notice the AI repeating itself excessively."
        ),
        "presence_penalty" to ParamInfo(
            "Presence Penalty",
            "Encourages new topics",
            "Encourages the AI to introduce new topics rather than staying on the current one. " +
                    "Positive values encourage topic diversity. Negative values keep the AI focused.\n\n" +
                    "Leave at 0 for most use cases. Set to 0.3-0.5 if you want more varied suggestions."
        ),
        "seed" to ParamInfo(
            "Seed",
            "For reproducible outputs",
            "When set to a specific number, the AI will produce the same output for the same input " +
                    "every time (if the model supports it). -1 means random (different each time).\n\n" +
                    "Useful for testing and debugging. Leave at -1 for normal use."
        ),
        "stop_sequences" to ParamInfo(
            "Stop Sequences",
            "Words that end the AI's response",
            "Comma-separated list of words/phrases that, when generated, immediately stop the AI's " +
                    "response. Useful for preventing the AI from generating beyond a certain point.\n\n" +
                    "Leave empty for normal use. Example: \"END,STOP\" would stop the AI when it " +
                    "generates either word."
        ),
        "response_format" to ParamInfo(
            "Response Format",
            "How the AI structures its output",
            "Auto: The AI decides the best format (default).\n" +
                    "JSON mode: Forces the AI to always respond with valid JSON. Required for some " +
                    "structured operations but may reduce response quality.\n" +
                    "Plain text: Forces plain text output.\n\n" +
                    "Leave on Auto unless you have a specific reason to change it."
        ),
        "timeout" to ParamInfo(
            "Timeout",
            "How long to wait for a response",
            "Maximum seconds to wait for the AI provider to respond before giving up.\n\n" +
                    "30-60s: Fine for simple queries.\n" +
                    "120s: Good default for most tasks.\n" +
                    "300s+: Needed for Blueprint Studio compilation with large documents.\n\n" +
                    "If you get timeout errors, increase this value."
        ),
        "retries" to ParamInfo(
            "Retries",
            "Automatic retry on failure",
            "Number of times to retry a failed request before giving up. Retries only happen for " +
                    "transient errors (server busy, network timeout) with exponential backoff.\n\n" +
                    "2 retries (default) handles most temporary issues. Set to 0 to disable."
        ),
        "history_limit" to ParamInfo(
            "Conversation History",
            "How much context the AI remembers",
            "Number of previous messages sent to the AI with each request. More messages = better " +
                    "context but higher cost and slower responses.\n\n" +
                    "10-20: Good balance of context and cost.\n" +
                    "50+: Maximum context for complex multi-step tasks.\n\n" +
                    "Each message costs tokens. Reduce if you're hitting budget limits."
        ),
        "context_chars" to ParamInfo(
            "Max Context Characters",
            "How much of your data the AI sees",
            "Maximum size of the app state (habits, goals, insights, etc.) included in the AI's " +
                    "system prompt. Larger values give the AI more information but cost more tokens.\n\n" +
                    "12,000 (default): Includes most data.\n" +
                    "30,000+: For power users with many habits and detailed data.\n\n" +
                    "Reduce if you're hitting your model's context window limit."
        ),
        "streaming" to ParamInfo(
            "Streaming",
            "Show responses as they're generated",
            "When enabled, the AI's response appears word-by-word as it's generated, rather than " +
                    "waiting for the complete response. This feels faster but may not work with all " +
                    "providers.\n\n" +
                    "Experimental. Leave off unless your provider supports it."
        ),
        "logging" to ParamInfo(
            "Request Logging",
            "Log AI requests for debugging",
            "When enabled, every request sent to and response received from the AI provider is " +
                    "logged to Android's logcat. Useful for debugging connection issues.\n\n" +
                    "Turn off for normal use. Logs may contain your data."
        ),
        "call_budget" to ParamInfo(
            "Monthly Call Budget",
            "Maximum API calls per month",
            "Hard cap on the number of API calls SuperFlow can make to your AI provider each month. " +
                    "Prevents unexpected costs.\n\n" +
                    "5,000 (default): Enough for daily use with proactive AI.\n" +
                    "50,000+: For heavy Blueprint Studio users.\n" +
                    "Set to 0 or enable Unlimited to remove the cap."
        ),
        "token_budget" to ParamInfo(
            "Monthly Token Budget",
            "Maximum tokens per month",
            "Cap on total tokens (input + output) per month. More precise than call count for " +
                    "cost control, since longer conversations use more tokens.\n\n" +
                    "0 = no cap (default). Set based on your provider's pricing."
        ),
        "custom_headers" to ParamInfo(
            "Custom Headers",
            "Extra HTTP headers for your provider",
            "Additional HTTP headers sent with every API request. One per line in the format:\n" +
                    "Header-Name: value\n\n" +
                    "Used for some providers that require special authentication or routing headers. " +
                    "Leave empty for standard providers (OpenAI, Anthropic, Groq, etc.)."
        ),
        "system_prompt" to ParamInfo(
            "System Prompt",
            "The AI's core instructions",
            "The system prompt defines the AI's personality, rules, and capabilities. SuperFlow " +
                    "includes a carefully designed built-in prompt that follows the app's principles " +
                    "(calm, evidence-based, no gamification).\n\n" +
                    "You can:\n" +
                    "- Add extra instructions (appended to the built-in prompt)\n" +
                    "- Replace the entire prompt (overrides the built-in one)\n\n" +
                    "Be careful: a poorly written prompt can make the AI ignore safety rules or " +
                    "produce unhelpful responses."
        ),
        "memory_notes" to ParamInfo(
            "Memory Notes",
            "Things the AI should always remember",
            "Free-text notes that are included in every AI conversation. Use this to tell the AI " +
                    "important context about yourself that it should always keep in mind.\n\n" +
                    "Examples:\n" +
                    "- \"I have two kids and limited time in the mornings\"\n" +
                    "- \"I'm recovering from a knee injury — no running\"\n" +
                    "- \"I prefer evening workouts\"\n" +
                    "- \"I'm training for a 5K in October\""
        ),
        "ai_instructions" to ParamInfo(
            "Explicit Instructions",
            "Rules the AI must follow",
            "Specific instructions that override the AI's default behavior. These are included in " +
                    "every conversation and take priority over the built-in system prompt.\n\n" +
                    "Examples:\n" +
                    "- \"Never suggest more than 3 habits at once\"\n" +
                    "- \"Always suggest the tiny version first\"\n" +
                    "- \"Be more direct and less wordy\"\n" +
                    "- \"Always ask before making changes to my schedule\""
        ),
        "local_memory" to ParamInfo(
            "Local Memory",
            "Structured facts the AI remembers",
            "A structured memory store that persists across conversations. Unlike free-text notes, " +
                    "these are organized as facts that the AI can reference specifically.\n\n" +
                    "The AI can also add to this memory when you say things like \"remember that I...\"\n\n" +
                    "Format: one fact per line.\n" +
                    "Example:\n" +
                    "- Morning energy is usually high\n" +
                    "- Wednesday is my busiest work day\n" +
                    "- I prefer outdoor activities\n" +
                    "- My birthday is March 15"
        )
    )
}
