package com.superflow.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Preferences and AI Engine configuration.
 *
 * Credentials live in a separate preference file that is excluded from backup
 * and from every export, prompt, log and support bundle.
 */
class Prefs private constructor(context: Context) {

    private val p: SharedPreferences =
        context.applicationContext.getSharedPreferences("superflow_prefs", Context.MODE_PRIVATE)
    private val secrets: SharedPreferences =
        context.applicationContext.getSharedPreferences("superflow_secrets", Context.MODE_PRIVATE)

    companion object {
        @Volatile private var instance: Prefs? = null
        fun get(context: Context): Prefs =
            instance ?: synchronized(this) { instance ?: Prefs(context).also { instance = it } }

        // Autonomy profiles
        const val PROFILE_FULL = "FULL_CONTROL"
        const val PROFILE_GUIDED = "GUIDED"
        const val PROFILE_PREVIEW = "PREVIEW"
    }

    /* ------------------------------------------------------------ general */

    var onboarded: Boolean
        get() = p.getBoolean("onboarded", false)
        set(v) = p.edit().putBoolean("onboarded", v).apply()

    var displayName: String
        get() = p.getString("displayName", "") ?: ""
        set(v) = p.edit().putString("displayName", v).apply()

    var remindersEnabled: Boolean
        get() = p.getBoolean("remindersEnabled", true)
        set(v) = p.edit().putBoolean("remindersEnabled", v).apply()

    var quietFrom: String
        get() = p.getString("quietFrom", "22:00") ?: "22:00"
        set(v) = p.edit().putString("quietFrom", v).apply()

    var quietTo: String
        get() = p.getString("quietTo", "07:00") ?: "07:00"
        set(v) = p.edit().putString("quietTo", v).apply()

    var reminderBudget: Int
        get() = p.getInt("reminderBudget", 6)
        set(v) = p.edit().putInt("reminderBudget", v).apply()

    var checkpointsEnabled: Boolean
        get() = p.getBoolean("checkpointsEnabled", true)
        set(v) = p.edit().putBoolean("checkpointsEnabled", v).apply()

    var morningCheckpoint: String
        get() = p.getString("cpMorning", "08:00") ?: "08:00"
        set(v) = p.edit().putString("cpMorning", v).apply()

    var middayCheckpoint: String
        get() = p.getString("cpMidday", "13:00") ?: "13:00"
        set(v) = p.edit().putString("cpMidday", v).apply()

    var eveningCheckpoint: String
        get() = p.getString("cpEvening", "20:30") ?: "20:30"
        set(v) = p.edit().putString("cpEvening", v).apply()

    var energyTracking: Boolean
        get() = p.getBoolean("energyTracking", true)
        set(v) = p.edit().putBoolean("energyTracking", v).apply()

    var minimumMode: Boolean
        get() = p.getBoolean("minimumMode", false)
        set(v) = p.edit().putBoolean("minimumMode", v).apply()

    var crashReporting: Boolean
        get() = p.getBoolean("crashReporting", false)
        set(v) = p.edit().putBoolean("crashReporting", v).apply()

    /* ----------------------------------------------------------- ai engine */

    var aiEnabled: Boolean
        get() = p.getBoolean("aiEnabled", true)
        set(v) = p.edit().putBoolean("aiEnabled", v).apply()

    /** Full Control is the primary AI profile: one activation, no repeat asks. */
    var autonomyProfile: String
        get() = p.getString("autonomyProfile", PROFILE_FULL) ?: PROFILE_FULL
        set(v) = p.edit().putString("autonomyProfile", v).apply()

    var fullControlActivated: Boolean
        get() = p.getBoolean("fullControlActivated", false)
        set(v) = p.edit().putBoolean("fullControlActivated", v).apply()

    var allowDestructive: Boolean
        get() = p.getBoolean("allowDestructive", true)
        set(v) = p.edit().putBoolean("allowDestructive", v).apply()

    var allowSettingsChanges: Boolean
        get() = p.getBoolean("allowSettingsChanges", true)
        set(v) = p.edit().putBoolean("allowSettingsChanges", v).apply()

    var allowBackgroundJobs: Boolean
        get() = p.getBoolean("allowBackgroundJobs", true)
        set(v) = p.edit().putBoolean("allowBackgroundJobs", v).apply()

    var autoSnapshot: Boolean
        get() = p.getBoolean("autoSnapshot", true)
        set(v) = p.edit().putBoolean("autoSnapshot", v).apply()

    var localCoordinatorOnly: Boolean
        get() = p.getBoolean("localCoordinatorOnly", false)
        set(v) = p.edit().putBoolean("localCoordinatorOnly", v).apply()

    /** Cloud Main Brain configuration. */
    var providerName: String
        get() = p.getString("providerName", "Custom OpenAI-compatible") ?: "Custom OpenAI-compatible"
        set(v) = p.edit().putString("providerName", v).apply()

    var baseUrl: String
        get() = p.getString("baseUrl", "") ?: ""
        set(v) = p.edit().putString("baseUrl", v).apply()

    var model: String
        get() = p.getString("model", "gpt-4o-mini") ?: "gpt-4o-mini"
        set(v) = p.edit().putString("model", v).apply()

    var temperature: Int
        get() = p.getInt("temperature", 20)   // stored as percent
        set(v) = p.edit().putInt("temperature", v).apply()

    var maxTokens: Int
        get() = p.getInt("maxTokens", 1200)
        set(v) = p.edit().putInt("maxTokens", v).apply()

    var requestTimeoutSec: Int
        get() = p.getInt("timeoutSec", 60)
        set(v) = p.edit().putInt("timeoutSec", v).apply()

    var unlimitedBudget: Boolean
        get() = p.getBoolean("unlimitedBudget", false)
        set(v) = p.edit().putBoolean("unlimitedBudget", v).apply()

    var monthlyCallBudget: Int
        get() = p.getInt("monthlyCallBudget", 500)
        set(v) = p.edit().putInt("monthlyCallBudget", v).apply()

    var callsThisMonth: Int
        get() = p.getInt("callsThisMonth", 0)
        set(v) = p.edit().putInt("callsThisMonth", v).apply()

    var contextIncludeHabits: Boolean
        get() = p.getBoolean("ctxHabits", true)
        set(v) = p.edit().putBoolean("ctxHabits", v).apply()

    var contextIncludeInsights: Boolean
        get() = p.getBoolean("ctxInsights", true)
        set(v) = p.edit().putBoolean("ctxInsights", v).apply()

    var contextIncludeMemory: Boolean
        get() = p.getBoolean("ctxMemory", true)
        set(v) = p.edit().putBoolean("ctxMemory", v).apply()

    var memoryNotes: String
        get() = p.getString("memoryNotes", "") ?: ""
        set(v) = p.edit().putString("memoryNotes", v).apply()

    /* ------------------------------------------------------------ secrets */

    /** Stored in the excluded secrets file; never exported or put in a prompt. */
    var apiKey: String
        get() = secrets.getString("apiKey", "") ?: ""
        set(v) = secrets.edit().putString("apiKey", v).apply()

    fun hasApiKey(): Boolean = apiKey.isNotBlank()

    fun maskedKey(): String {
        val k = apiKey
        return when {
            k.isBlank() -> "not set"
            k.length <= 8 -> "set"
            else -> "${k.take(4)}...${k.takeLast(4)}"
        }
    }

    fun clearSecrets() = secrets.edit().clear().apply()

    /* -------------------------------------------------------------- state */

    /** True when AI may execute app-local work without asking each time. */
    fun fullControlActive(): Boolean =
        aiEnabled && autonomyProfile == PROFILE_FULL && fullControlActivated

    /** Cloud calls are possible only with an endpoint and key. */
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
    }
}
