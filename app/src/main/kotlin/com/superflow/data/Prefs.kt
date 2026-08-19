package com.superflow.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Preferences and AI Engine configuration.
 *
 * Credentials live in a separate preference file excluded from backup and from
 * every export, prompt, log and support bundle.
 */
class Prefs private constructor(context: Context) {

    private val p: SharedPreferences =
        context.applicationContext.getSharedPreferences("superflow_prefs", Context.MODE_PRIVATE)
    private val secrets: SharedPreferences =
        context.applicationContext.getSharedPreferences("superflow_secrets", Context.MODE_PRIVATE)

    private val _changes = MutableStateFlow(0L)
    val changes: StateFlow<Long> = _changes.asStateFlow()

    private fun bump() { _changes.value = _changes.value + 1 }

    companion object {
        @Volatile private var instance: Prefs? = null
        fun get(context: Context): Prefs =
            instance ?: synchronized(this) {
                instance ?: Prefs(context.applicationContext).also { instance = it }
            }

        const val PROFILE_FULL = "FULL_CONTROL"
        const val PROFILE_GUIDED = "GUIDED"
        const val PROFILE_PREVIEW = "PREVIEW"

        const val THEME_SYSTEM = 0
        const val THEME_LIGHT = 1
        const val THEME_DARK = 2
    }

    private fun bool(key: String, def: Boolean) = p.getBoolean(key, def)
    private fun setBool(key: String, v: Boolean) { p.edit().putBoolean(key, v).apply(); bump() }
    private fun str(key: String, def: String) = p.getString(key, def) ?: def
    private fun setStr(key: String, v: String) { p.edit().putString(key, v).apply(); bump() }
    private fun num(key: String, def: Int) = p.getInt(key, def)
    private fun setNum(key: String, v: Int) { p.edit().putInt(key, v).apply(); bump() }
    private fun floatNum(key: String, def: Float) = p.getFloat(key, def)
    private fun setFloatNum(key: String, v: Float) { p.edit().putFloat(key, v).apply(); bump() }

    /* ------------------------------------------------------------- general */

    var onboarded: Boolean
        get() = bool("onboarded", false)
        set(v) = setBool("onboarded", v)

    var displayName: String
        get() = str("displayName", "")
        set(v) = setStr("displayName", v)

    var themeMode: Int
        get() = num("themeMode", THEME_SYSTEM)
        set(v) = setNum("themeMode", v)

    var dynamicColor: Boolean
        get() = bool("dynamicColor", true)
        set(v) = setBool("dynamicColor", v)

    var hapticsEnabled: Boolean
        get() = bool("haptics", true)
        set(v) = setBool("haptics", v)

    var celebrationsEnabled: Boolean
        get() = bool("celebrations", true)
        set(v) = setBool("celebrations", v)

    /* ----------------------------------------------------------- reminders */

    var remindersEnabled: Boolean
        get() = bool("remindersEnabled", true)
        set(v) = setBool("remindersEnabled", v)

    var quietFrom: String
        get() = str("quietFrom", "22:00")
        set(v) = setStr("quietFrom", v)

    var quietTo: String
        get() = str("quietTo", "07:00")
        set(v) = setStr("quietTo", v)

    /**
     * Alpha2: separate quiet hours for weekdays and weekends. Empty means
     * "inherit the single quietFrom/quietTo window above", so users who set
     * one window keep it until they configure per-day hours.
     */
    var quietWeekdayFrom: String
        get() = str("quietWeekdayFrom", "")
        set(v) = setStr("quietWeekdayFrom", v)

    var quietWeekdayTo: String
        get() = str("quietWeekdayTo", "")
        set(v) = setStr("quietWeekdayTo", v)

    var quietWeekendFrom: String
        get() = str("quietWeekendFrom", "")
        set(v) = setStr("quietWeekendFrom", v)

    var quietWeekendTo: String
        get() = str("quietWeekendTo", "")
        set(v) = setStr("quietWeekendTo", v)

    var reminderBudget: Int
        get() = num("reminderBudget", 6)
        set(v) = setNum("reminderBudget", v)

    var checkpointsEnabled: Boolean
        get() = bool("checkpointsEnabled", true)
        set(v) = setBool("checkpointsEnabled", v)

    var morningCheckpoint: String
        get() = str("cpMorning", "08:00")
        set(v) = setStr("cpMorning", v)

    var middayCheckpoint: String
        get() = str("cpMidday", "13:00")
        set(v) = setStr("cpMidday", v)

    var eveningCheckpoint: String
        get() = str("cpEvening", "20:30")
        set(v) = setStr("cpEvening", v)

    var energyTracking: Boolean
        get() = bool("energyTracking", true)
        set(v) = setBool("energyTracking", v)

    /* ------------------------------------------------------ weekly summary */

    var weeklySummaryEnabled: Boolean
        get() = bool("weeklySummaryEnabled", true)
        set(v) = setBool("weeklySummaryEnabled", v)

    /** ISO day of week (Monday = 1 .. Sunday = 7) for the weekly report. */
    var weeklySummaryDay: Int
        get() = num("weeklySummaryDay", 7)
        set(v) = setNum("weeklySummaryDay", v.coerceIn(1, 7))

    var weeklySummaryTime: String
        get() = str("weeklySummaryTime", "18:00")
        set(v) = setStr("weeklySummaryTime", v)

    var crashReporting: Boolean
        get() = bool("crashReporting", false)
        set(v) = setBool("crashReporting", v)

    /* ----------------------------------------------------------- ai engine */

    var aiEnabled: Boolean
        get() = bool("aiEnabled", true)
        set(v) = setBool("aiEnabled", v)

    var autonomyProfile: String
        get() = str("autonomyProfile", PROFILE_FULL)
        set(v) = setStr("autonomyProfile", v)

    var fullControlActivated: Boolean
        get() = bool("fullControlActivated", false)
        set(v) = setBool("fullControlActivated", v)

    var allowDestructive: Boolean
        get() = bool("allowDestructive", true)
        set(v) = setBool("allowDestructive", v)

    var allowSettingsChanges: Boolean
        get() = bool("allowSettingsChanges", true)
        set(v) = setBool("allowSettingsChanges", v)

    var allowBackgroundJobs: Boolean
        get() = bool("allowBackgroundJobs", true)
        set(v) = setBool("allowBackgroundJobs", v)

    var autoSnapshot: Boolean
        get() = bool("autoSnapshot", true)
        set(v) = setBool("autoSnapshot", v)

    var localCoordinatorOnly: Boolean
        get() = bool("localCoordinatorOnly", false)
        set(v) = setBool("localCoordinatorOnly", v)

    var voiceEnabled: Boolean
        get() = bool("voiceEnabled", true)
        set(v) = setBool("voiceEnabled", v)

    /* ---- Provider ---- */

    var providerName: String
        get() = str("providerName", "Custom OpenAI-compatible")
        set(v) = setStr("providerName", v)

    var baseUrl: String
        get() = str("baseUrl", "")
        set(v) = setStr("baseUrl", v)

    var fallbackUrl: String
        get() = str("fallbackUrl", "")
        set(v) = setStr("fallbackUrl", v)

    var model: String
        get() = str("model", "gpt-4o")
        set(v) = setStr("model", v)

    var organizationId: String
        get() = str("organizationId", "")
        set(v) = setStr("organizationId", v)

    var customHeaders: String
        get() = str("customHeaders", "")
        set(v) = setStr("customHeaders", v)

    /* ---- Generation parameters (all freely customizable) ---- */

    /** Temperature × 100 (so 70 = 0.70). Default 0.70. Range 0–200. */
    var temperature: Int
        get() = num("temperature", 70)
        set(v) = setNum("temperature", v.coerceIn(0, 200))

    /** Top-p (nucleus sampling) × 100. Default 100 = 1.0 (disabled). Range 0–100. */
    var topP: Int
        get() = num("topP", 100)
        set(v) = setNum("topP", v.coerceIn(0, 100))

    /** Maximum output tokens. Default 4096. Range 64–131072. */
    var maxTokens: Int
        get() = num("maxTokens", 4096)
        set(v) = setNum("maxTokens", v.coerceIn(64, 131_072))

    /** Frequency penalty × 100. Default 0. Range -200 to 200. */
    var frequencyPenalty: Int
        get() = num("freqPenalty", 0)
        set(v) = setNum("freqPenalty", v.coerceIn(-200, 200))

    /** Presence penalty × 100. Default 0. Range -200 to 200. */
    var presencePenalty: Int
        get() = num("presPenalty", 0)
        set(v) = setNum("presPenalty", v.coerceIn(-200, 200))

    /** Seed for reproducible outputs. -1 = random (default). */
    var seed: Int
        get() = num("seed", -1)
        set(v) = setNum("seed", v)

    /** Stop sequences, comma-separated. Empty = none. */
    var stopSequences: String
        get() = str("stopSequences", "")
        set(v) = setStr("stopSequences", v)

    /** Response format: "auto", "json", "text". */
    var responseFormat: String
        get() = str("responseFormat", "auto")
        set(v) = setStr("responseFormat", v)

    /** Request timeout in seconds. Default 120. Range 5–900. */
    var requestTimeoutSec: Int
        get() = num("timeoutSec", 120)
        set(v) = setNum("timeoutSec", v.coerceIn(5, 900))

    /** Number of retries on transient failure. Default 2. Range 0–5. */
    var retryCount: Int
        get() = num("retryCount", 2)
        set(v) = setNum("retryCount", v.coerceIn(0, 5))

    /** Max conversation history messages sent to the model. Default 20. Range 2–100. */
    var conversationHistoryLimit: Int
        get() = num("convHistoryLimit", 20)
        set(v) = setNum("convHistoryLimit", v.coerceIn(2, 100))

    /** Max context characters sent in the system prompt. Default 12000. Range 1000–80000. */
    var maxContextChars: Int
        get() = num("maxCtxChars", 12000)
        set(v) = setNum("maxCtxChars", v.coerceIn(1_000, 80_000))

    /** Enable streaming responses (where supported). */
    var streamingEnabled: Boolean
        get() = bool("streaming", false)
        set(v) = setBool("streaming", v)

    /** Log requests and responses for debugging. */
    var requestLoggingEnabled: Boolean
        get() = bool("reqLogging", false)
        set(v) = setBool("reqLogging", v)

    /** Custom system prompt override. Empty = use the built-in prompt. */
    var customSystemPrompt: String
        get() = str("customSysPrompt", "")
        set(v) = setStr("customSysPrompt", v)

    /** Append extra instructions to every system prompt. */
    var systemPromptSuffix: String
        get() = str("sysPromptSuffix", "")
        set(v) = setStr("sysPromptSuffix", v)

    /* ---- Budget ---- */

    var unlimitedBudget: Boolean
        get() = bool("unlimitedBudget", false)
        set(v) = setBool("unlimitedBudget", v)

    var monthlyCallBudget: Int
        get() = num("monthlyCallBudget", 5000)
        set(v) = setNum("monthlyCallBudget", v.coerceAtLeast(1))

    var callsThisMonth: Int
        get() = num("callsThisMonth", 0)
        set(v) = setNum("callsThisMonth", v)

    /** Monthly token budget (input + output). 0 = unlimited. */
    var monthlyTokenBudget: Int
        get() = num("monthlyTokenBudget", 0)
        set(v) = setNum("monthlyTokenBudget", v.coerceAtLeast(0))

    var tokensThisMonth: Int
        get() = num("tokensThisMonth", 0)
        set(v) = setNum("tokensThisMonth", v)

    /** Monthly cost budget in cents. 0 = unlimited. */
    var monthlyCostBudgetCents: Int
        get() = num("monthlyCostCents", 0)
        set(v) = setNum("monthlyCostCents", v.coerceAtLeast(0))

    var costThisMonthCents: Int
        get() = num("costThisMonthCents", 0)
        set(v) = setNum("costThisMonthCents", v)

    /* ---- Context ---- */

    var contextIncludeHabits: Boolean
        get() = bool("ctxHabits", true)
        set(v) = setBool("ctxHabits", v)

    var contextIncludeInsights: Boolean
        get() = bool("ctxInsights", true)
        set(v) = setBool("ctxInsights", v)

    var contextIncludeMemory: Boolean
        get() = bool("ctxMemory", true)
        set(v) = setBool("ctxMemory", v)

    var contextIncludeCheckIns: Boolean
        get() = bool("ctxCheckIns", true)
        set(v) = setBool("ctxCheckIns", v)

    var contextIncludeReviews: Boolean
        get() = bool("ctxReviews", false)
        set(v) = setBool("ctxReviews", v)

    var contextIncludeObstacles: Boolean
        get() = bool("ctxObstacles", false)
        set(v) = setBool("ctxObstacles", v)

    var contextIncludeFlows: Boolean
        get() = bool("ctxFlows", false)
        set(v) = setBool("ctxFlows", v)

    var memoryNotes: String
        get() = str("memoryNotes", "")
        set(v) = setStr("memoryNotes", v)

    /** Let the Cloud Main Brain refine the Blueprint ledger after extraction. */
    var blueprintCloudRefine: Boolean
        get() = bool("bpCloudRefine", true)
        set(v) = setBool("bpCloudRefine", v)

    /* ---- Voice / TTS / STT ---- */

    var ttsEnabled: Boolean
        get() = bool("ttsEnabled", false)
        set(v) = setBool("ttsEnabled", v)

    var ttsSpeechRate: Int
        get() = num("ttsSpeechRate", 90)     // ×100, so 90 = 0.9×
        set(v) = setNum("ttsSpeechRate", v.coerceIn(50, 200))

    var ttsPitch: Int
        get() = num("ttsPitch", 100)         // ×100
        set(v) = setNum("ttsPitch", v.coerceIn(50, 200))

    var sttProvider: String
        get() = str("sttProvider", "platform")
        set(v) = setStr("sttProvider", v)

    var proactiveAi: Boolean
        get() = bool("proactiveAi", true)
        set(v) = setBool("proactiveAi", v)

    var proactiveNotifications: Boolean
        get() = bool("proactiveNotif", true)
        set(v) = setBool("proactiveNotif", v)

    /* ---- Data management ---- */

    var autoBackupEnabled: Boolean
        get() = bool("autoBackup", false)
        set(v) = setBool("autoBackup", v)

    var autoBackupFrequency: String
        get() = str("autoBackupFreq", "daily")
        set(v) = setStr("autoBackupFreq", v)

    var maxBackups: Int
        get() = num("maxBackups", 7)
        set(v) = setNum("maxBackups", v.coerceIn(1, 30))

    /* ---- AI mode ---- */

    /** AI setup complexity: "default", "intermediate", or "advanced". */
    var aiSetupMode: String
        get() = str("aiSetupMode", "default")
        set(v) = setStr("aiSetupMode", v)

    /** Legacy compatibility — maps old boolean to new tri-state. */
    var aiAdvancedMode: Boolean
        get() = aiSetupMode == "advanced"
        set(v) { aiSetupMode = if (v) "advanced" else "default" }

    /* ---- AI instructions & memory ---- */

    var aiInstructions: String
        get() = str("aiInstructions", "")
        set(v) = setStr("aiInstructions", v)

    var aiLocalMemory: String
        get() = str("aiLocalMemory", "")
        set(v) = setStr("aiLocalMemory", v)

    /* ------------------------------------------------------------- secrets */

    var apiKey: String
        get() = secrets.getString("apiKey", "") ?: ""
        set(v) { secrets.edit().putString("apiKey", v).apply(); bump() }

    fun hasApiKey(): Boolean = apiKey.isNotBlank()

    fun maskedKey(): String {
        val k = apiKey
        return when {
            k.isBlank() -> "not set"
            k.length <= 8 -> "set"
            else -> "${k.take(4)}…${k.takeLast(4)}"
        }
    }

    fun clearSecrets() { secrets.edit().clear().apply(); bump() }

    /* --------------------------------------------------------------- state */

    fun fullControlActive(): Boolean =
        aiEnabled && autonomyProfile == PROFILE_FULL && fullControlActivated

    fun cloudReady(): Boolean =
        aiEnabled && !localCoordinatorOnly && baseUrl.isNotBlank() && apiKey.isNotBlank()

    fun budgetRemaining(): Int =
        if (unlimitedBudget) Int.MAX_VALUE else (monthlyCallBudget - callsThisMonth).coerceAtLeast(0)

    fun noteCall() {
        if (!unlimitedBudget) callsThisMonth += 1
    }

    fun resetAll() {
        p.edit().clear().apply()
        secrets.edit().clear().apply()
        bump()
    }
}
