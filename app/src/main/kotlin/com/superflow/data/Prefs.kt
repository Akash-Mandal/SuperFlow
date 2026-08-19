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

    /** The POST_NOTIFICATIONS prompt is offered at most once per install. */
    var notifPermissionAsked: Boolean
        get() = bool("notifPermissionAsked", false)
        set(v) = setBool("notifPermissionAsked", v)

    var quietFrom: String
        get() = str("quietFrom", "22:00")
        set(v) = setStr("quietFrom", v)

    var quietTo: String
        get() = str("quietTo", "07:00")
        set(v) = setStr("quietTo", v)

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

    var providerName: String
        get() = str("providerName", "Custom OpenAI-compatible")
        set(v) = setStr("providerName", v)

    var baseUrl: String
        get() = str("baseUrl", "")
        set(v) = setStr("baseUrl", v)

    var model: String
        get() = str("model", "gpt-4o-mini")
        set(v) = setStr("model", v)

    var temperature: Int
        get() = num("temperature", 20)
        set(v) = setNum("temperature", v)

    var maxTokens: Int
        get() = num("maxTokens", 1200)
        set(v) = setNum("maxTokens", v)

    var requestTimeoutSec: Int
        get() = num("timeoutSec", 60)
        set(v) = setNum("timeoutSec", v)

    var unlimitedBudget: Boolean
        get() = bool("unlimitedBudget", false)
        set(v) = setBool("unlimitedBudget", v)

    var monthlyCallBudget: Int
        get() = num("monthlyCallBudget", 500)
        set(v) = setNum("monthlyCallBudget", v)

    var callsThisMonth: Int
        get() = num("callsThisMonth", 0)
        set(v) = setNum("callsThisMonth", v)

    var contextIncludeHabits: Boolean
        get() = bool("ctxHabits", true)
        set(v) = setBool("ctxHabits", v)

    var contextIncludeInsights: Boolean
        get() = bool("ctxInsights", true)
        set(v) = setBool("ctxInsights", v)

    var contextIncludeMemory: Boolean
        get() = bool("ctxMemory", true)
        set(v) = setBool("ctxMemory", v)

    var memoryNotes: String
        get() = str("memoryNotes", "")
        set(v) = setStr("memoryNotes", v)

    /** Let the Cloud Main Brain refine the Blueprint ledger after extraction. */
    var blueprintCloudRefine: Boolean
        get() = bool("bpCloudRefine", true)
        set(v) = setBool("bpCloudRefine", v)

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
