package com.superflow.data

import com.superflow.data.model.*
import com.superflow.domain.Serial
import com.superflow.util.jsonArrayOf
import com.superflow.util.jsonOf
import com.superflow.util.strings
import org.json.JSONArray
import org.json.JSONObject

/**
 * The All-Inclusive Data Policy.
 *
 * Every piece of user data in the app is registered here. When a new feature
 * adds data, it MUST register a [DataCategory] so that export, import, backup,
 * delete, and statistics all cover it automatically.
 *
 * Principles:
 * - Nothing is silently excluded from export/import.
 * - Secrets (API keys) are excluded by design and labelled as such.
 * - Every category has a human-readable name and description.
 * - Categories can be individually toggled for selective export/import.
 * - The policy version bumps when categories are added, so old exports are
 *   recognised and new fields are handled gracefully.
 */
object DataPolicy {

    const val POLICY_VERSION = 2

    /**
     * One registered category of data. Every table, preference group, or
     * file-based store in the app has exactly one entry here.
     */
    data class DataCategory(
        val key: String,               // JSON key in export (e.g. "habits")
        val displayName: String,       // "Habits"
        val description: String,       // "Your habit designs, schedules, and all fields"
        val table: String?,            // SQLite table name, or null for non-DB data
        val isSensitive: Boolean = false,  // True for API keys, etc.
        val isDerived: Boolean = false,    // True for computed data (snapshots)
        val includeByDefault: Boolean = true
    )

    /**
     * Master registry. Every piece of data in the app is listed here.
     * When adding a new feature, add its data category to this list.
     */
    val categories: List<DataCategory> = listOf(
        // Core growth hierarchy
        DataCategory("identities", "Identities",
            "Your identity statements and life areas",
            "identity"),
        DataCategory("goals", "Goals",
            "Your goals, their why, and target metrics",
            "goal"),
        DataCategory("systems", "Systems",
            "Your repeatable systems and routines",
            "sys"),
        DataCategory("habits", "Habits",
            "Habit designs, schedules, Four Laws, ladder, all fields",
            "habit"),

        // Daily activity
        DataCategory("checkIns", "Check-ins",
            "Every check-in, skip, miss, and level chosen",
            "checkin"),
        DataCategory("focus", "Daily Focus",
            "Daily focus items and their completion status",
            "focus"),
        DataCategory("energy", "Energy Logs",
            "Energy ratings logged at checkpoints",
            "energy"),

        // Design tools
        DataCategory("obstacles", "Obstacle Plans",
            "If-then plans for when things go wrong",
            "obstacle"),
        DataCategory("scorecard", "Scorecard",
            "Habit scorecard entries and verdicts",
            "scorecard"),
        DataCategory("flows", "Flows & Routines",
            "Habit stacking flows and their steps",
            "flow"),
        DataCategory("flowSteps", "Flow Steps",
            "Individual steps within each flow",
            "flowstep"),

        // Reflection
        DataCategory("reviews", "Reviews",
            "Weekly, monthly, and quarterly review entries",
            "review"),
        DataCategory("pauses", "Pause Windows",
            "Planned breaks, vacations, and illness pauses",
            "pause"),

        // Blueprint Studio
        DataCategory("projects", "Blueprint Projects",
            "Blueprint Studio project definitions",
            "bp_project"),
        DataCategory("sources", "Blueprint Sources",
            "Documents and text imported into Blueprint Studio",
            "bp_source"),
        DataCategory("requirements", "Blueprint Requirements",
            "Extracted requirement ledger rows",
            "bp_req"),
        DataCategory("blueprintVersions", "Blueprint Versions",
            "Ledger version snapshots for amendment history",
            "bp_version"),

        // AI & conversation
        DataCategory("aiMessages", "AI Conversation",
            "Full conversation history with the AI coach",
            "aimsg"),
        DataCategory("audit", "Activity Trail",
            "Complete audit log of every action taken",
            "audit"),

        // Profile & preferences
        DataCategory("profile", "User Profile",
            "Display name, locale, timezone, week start",
            "profile"),
        DataCategory("preferences", "App Settings",
            "All app preferences: theme, reminders, haptics, AI config, appearance, experience",
            null),  // Stored in SharedPreferences, not SQLite

        // Explicitly excluded (documented for transparency)
        DataCategory("secrets", "API Keys & Secrets",
            "Cloud provider API keys. Excluded from all exports for security. " +
                    "Must be re-entered after import on a new device.",
            null, isSensitive = true, includeByDefault = false),
        DataCategory("snapshots", "Safety Snapshots",
            "Automatic pre-action snapshots. Excluded from export (too large, device-specific). " +
                    "Stored locally in app files directory.",
            null, isDerived = true, includeByDefault = false)
    )

    /** Categories included in a standard export. */
    val exportableCategories: List<DataCategory>
        get() = categories.filter { it.includeByDefault && !it.isSensitive && !it.isDerived }

    /** Categories included in a full (all-inclusive) export. */
    val fullExportCategories: List<DataCategory>
        get() = categories.filter { !it.isSensitive }

    /**
     * Produces a human-readable manifest of what's included in an export.
     */
    fun manifest(repo: Repository): String {
        val sb = StringBuilder()
        sb.append("SuperFlow Data Manifest v$POLICY_VERSION\n\n")
        for (cat in exportableCategories) {
            val count = if (cat.table != null) repo.counts()[cat.table] ?: 0 else countPrefs(repo)
            sb.append("· ${cat.displayName}: $count items\n")
        }
        sb.append("\nExcluded by design:\n")
        for (cat in categories.filter { !it.includeByDefault }) {
            sb.append("· ${cat.displayName}: ${cat.description}\n")
        }
        return sb.toString()
    }

    private fun countPrefs(repo: Repository): Int = 1  // Preferences = 1 blob

    /**
     * Export everything according to the all-inclusion policy.
     * This replaces and extends Serial.exportAll().
     */
    fun exportFull(repo: Repository, prefs: Prefs, includeCategories: Set<String>? = null): JSONObject {
        val cats = includeCategories ?: exportableCategories.map { it.key }.toSet()
        val root = JSONObject()
        root.put("app", "SuperFlow")
        root.put("policyVersion", POLICY_VERSION)
        root.put("exportVersion", Serial.EXPORT_VERSION)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("includedCategories", JSONArray(cats.toList()))

        fun arr(items: List<JSONObject>) = JSONArray().also { a -> items.forEach { a.put(it) } }

        if ("identities" in cats) root.put("identities", arr(repo.identities(true).map { Serial.of(it) }))
        if ("goals" in cats) root.put("goals", arr(repo.goals().map { Serial.of(it) }))
        if ("systems" in cats) root.put("systems", arr(repo.systems().map { Serial.of(it) }))
        if ("habits" in cats) root.put("habits", arr(repo.habits(true).map { Serial.of(it) }))
        if ("checkIns" in cats) root.put("checkIns", arr(repo.checkIns().map { Serial.of(it) }))
        if ("focus" in cats) root.put("focus", arr(repo.focusAll().map { Serial.of(it) }))
        if ("energy" in cats) root.put("energy", arr(repo.energyLogs().map { Serial.of(it) }))
        if ("obstacles" in cats) root.put("obstacles", arr(repo.obstacles().map { Serial.of(it) }))
        if ("scorecard" in cats) root.put("scorecard", arr(repo.scorecard().map { Serial.of(it) }))
        if ("flows" in cats) root.put("flows", arr(repo.flows().map { Serial.of(it) }))
        if ("flowSteps" in cats) root.put("flowSteps", arr(repo.flows().flatMap { repo.flowSteps(it.id) }.map { Serial.of(it) }))
        if ("reviews" in cats) root.put("reviews", arr(repo.reviews().map { Serial.of(it) }))
        if ("pauses" in cats) root.put("pauses", arr(repo.pauses().map { Serial.of(it) }))

        // Blueprint
        if ("projects" in cats) {
            val projects = repo.projects()
            root.put("projects", arr(projects.map { Serial.of(it) }))
            if ("sources" in cats) root.put("sources", arr(projects.flatMap { repo.sources(it.id) }.map { Serial.of(it) }))
            if ("requirements" in cats) root.put("requirements", arr(projects.flatMap { repo.requirements(it.id) }.map { Serial.of(it) }))
        }
        if ("blueprintVersions" in cats) {
            val versions = repo.projects().flatMap { repo.versions(it.id) }
            root.put("blueprintVersions", arr(versions.map { v ->
                jsonOf("table" to "bp_version", "id" to v.id, "projectId" to v.projectId,
                    "version" to v.version, "label" to v.label,
                    "ledgerJson" to v.ledgerJson, "createdAt" to v.createdAt)
            }))
        }

        // AI
        if ("aiMessages" in cats) {
            root.put("aiMessages", arr(repo.messages(10000).map { m ->
                jsonOf("table" to "aimsg", "id" to m.id, "role" to m.role,
                    "text" to m.text, "meta" to m.meta, "createdAt" to m.createdAt)
            }))
        }
        if ("audit" in cats) {
            root.put("audit", arr(repo.audit(100000).map { a ->
                jsonOf("table" to "audit", "id" to a.id, "actor" to a.actor,
                    "command" to a.command, "summary" to a.summary,
                    "payload" to a.payload, "undoPayload" to a.undoPayload,
                    "groupId" to a.groupId, "undone" to a.undone, "createdAt" to a.createdAt)
            }))
        }

        // Profile
        if ("profile" in cats) {
            val p = repo.profile()
            root.put("profile", jsonOf(
                "table" to "profile", "id" to p.id, "displayName" to p.displayName,
                "locale" to p.locale, "zoneId" to p.zoneId, "weekStart" to p.weekStart,
                "createdAt" to p.createdAt, "updatedAt" to p.updatedAt
            ))
        }

        // Preferences (all app settings)
        if ("preferences" in cats) {
            root.put("preferences", exportPreferences(prefs))
        }

        return root
    }

    /**
     * Export all SharedPreferences as a JSON object.
     *
     * All-inclusion: every user-settable preference is covered. The only
     * omissions are deliberate, and each is omitted for a reason rather than
     * by oversight:
     *
     *   * `apiKey` and the rest of the secrets file - credentials do not
     *     belong in a plaintext export.
     *   * `fullControlActivated` - exported for reference but never imported;
     *     granting an app destructive autonomy is a decision that must be
     *     made again on each device.
     *   * `onboarded` - describes this install, not the user.
     *   * `callsThisMonth` / `tokensThisMonth` / `costThisMonthCents` -
     *     per-device usage meters; carrying them across would corrupt budget
     *     accounting on the destination.
     *   * `aiAdvancedMode` - a boolean alias over `aiSetupMode`, which is
     *     exported. Writing both would let them contradict each other.
     */
    fun exportPreferences(prefs: Prefs): JSONObject = jsonOf(
        "themeMode" to prefs.themeMode,
        "dynamicColor" to prefs.dynamicColor,
        // Appearance: palette, dark flavour, density and the type/contrast
        // switches. All-inclusion means a restore should look identical to
        // what the user left, not merely work.
        "palette" to prefs.palette,
        "darkVariant" to prefs.darkVariant,
        "density" to prefs.density,
        "highContrast" to prefs.highContrast,
        "serifAccents" to prefs.serifAccents,
        "monoFigures" to prefs.monoFigures,
        // Experience
        "motionLevel" to prefs.motionLevel,
        "hapticIntensity" to prefs.hapticIntensity,
        "hapticsEnabled" to prefs.hapticsEnabled,
        "soundEnabled" to prefs.soundEnabled,
        "soundVolume" to prefs.soundVolume,
        // Sets go out as arrays rather than the joined string they are
        // stored as: the export format is a contract with other tools, and
        // it should not leak how SharedPreferences happens to hold a set.
        "soundCues" to jsonArrayOf(prefs.soundCues),
        "gestures" to jsonArrayOf(prefs.gestures),
        "colorVision" to prefs.colorVision,
        "tabLabels" to prefs.tabLabels,
        "appIcon" to prefs.appIcon,
        "startDestination" to prefs.startDestination,
        "confirmCompletion" to prefs.confirmCompletion,
        "showHistoryStrip" to prefs.showHistoryStrip,
        "swipeActions" to prefs.swipeActionsEnabled,
        "celebrationsEnabled" to prefs.celebrationsEnabled,
        "remindersEnabled" to prefs.remindersEnabled,
        "quietFrom" to prefs.quietFrom,
        "quietTo" to prefs.quietTo,
        "quietWeekdayFrom" to prefs.quietWeekdayFrom,
        "quietWeekdayTo" to prefs.quietWeekdayTo,
        "quietWeekendFrom" to prefs.quietWeekendFrom,
        "quietWeekendTo" to prefs.quietWeekendTo,
        "weeklySummaryEnabled" to prefs.weeklySummaryEnabled,
        "weeklySummaryDay" to prefs.weeklySummaryDay,
        "weeklySummaryTime" to prefs.weeklySummaryTime,
        "reminderBudget" to prefs.reminderBudget,
        "checkpointsEnabled" to prefs.checkpointsEnabled,
        "morningCheckpoint" to prefs.morningCheckpoint,
        "middayCheckpoint" to prefs.middayCheckpoint,
        "eveningCheckpoint" to prefs.eveningCheckpoint,
        "energyTracking" to prefs.energyTracking,
        "aiEnabled" to prefs.aiEnabled,
        "autonomyProfile" to prefs.autonomyProfile,
        "fullControlActivated" to prefs.fullControlActivated,
        "allowDestructive" to prefs.allowDestructive,
        "allowSettingsChanges" to prefs.allowSettingsChanges,
        "allowBackgroundJobs" to prefs.allowBackgroundJobs,
        "autoSnapshot" to prefs.autoSnapshot,
        "localCoordinatorOnly" to prefs.localCoordinatorOnly,
        "voiceEnabled" to prefs.voiceEnabled,
        "providerName" to prefs.providerName,
        "baseUrl" to prefs.baseUrl,
        "fallbackUrl" to prefs.fallbackUrl,
        "model" to prefs.model,
        "organizationId" to prefs.organizationId,
        "customHeaders" to prefs.customHeaders,
        "temperature" to prefs.temperature,
        "topP" to prefs.topP,
        "maxTokens" to prefs.maxTokens,
        "frequencyPenalty" to prefs.frequencyPenalty,
        "presencePenalty" to prefs.presencePenalty,
        "seed" to prefs.seed,
        "stopSequences" to prefs.stopSequences,
        "responseFormat" to prefs.responseFormat,
        "requestTimeoutSec" to prefs.requestTimeoutSec,
        "retryCount" to prefs.retryCount,
        "conversationHistoryLimit" to prefs.conversationHistoryLimit,
        "maxContextChars" to prefs.maxContextChars,
        "streamingEnabled" to prefs.streamingEnabled,
        "requestLoggingEnabled" to prefs.requestLoggingEnabled,
        "customSystemPrompt" to prefs.customSystemPrompt,
        "systemPromptSuffix" to prefs.systemPromptSuffix,
        "unlimitedBudget" to prefs.unlimitedBudget,
        "monthlyCallBudget" to prefs.monthlyCallBudget,
        "monthlyTokenBudget" to prefs.monthlyTokenBudget,
        "monthlyCostBudgetCents" to prefs.monthlyCostBudgetCents,
        "contextIncludeHabits" to prefs.contextIncludeHabits,
        "contextIncludeCheckIns" to prefs.contextIncludeCheckIns,
        "contextIncludeInsights" to prefs.contextIncludeInsights,
        "contextIncludeReviews" to prefs.contextIncludeReviews,
        "contextIncludeObstacles" to prefs.contextIncludeObstacles,
        "contextIncludeFlows" to prefs.contextIncludeFlows,
        "contextIncludeMemory" to prefs.contextIncludeMemory,
        "memoryNotes" to prefs.memoryNotes,
        "blueprintCloudRefine" to prefs.blueprintCloudRefine,
        "ttsEnabled" to prefs.ttsEnabled,
        "ttsSpeechRate" to prefs.ttsSpeechRate,
        "ttsPitch" to prefs.ttsPitch,
        "sttProvider" to prefs.sttProvider,
        "proactiveAi" to prefs.proactiveAi,
        "proactiveNotifications" to prefs.proactiveNotifications,
        "displayName" to prefs.displayName,
        "crashReporting" to prefs.crashReporting,
        "aiSetupMode" to prefs.aiSetupMode,
        "autoBackupEnabled" to prefs.autoBackupEnabled,
        "autoBackupFrequency" to prefs.autoBackupFrequency,
        "maxBackups" to prefs.maxBackups,
        "aiInstructions" to prefs.aiInstructions,
        "aiLocalMemory" to prefs.aiLocalMemory,
        "appLockEnabled" to prefs.appLockEnabled,
        "appLockMethod" to prefs.appLockMethod,
        "appLockTimeout" to prefs.appLockTimeout,
        "appLockBiometric" to prefs.appLockBiometric,
        "appLockGraceSeconds" to prefs.appLockGraceSeconds,
        "quietPerDay" to prefs.quietPerDay,
        "darkSchedule" to prefs.darkSchedule,
        "darkFrom" to prefs.darkFrom,
        "darkTo" to prefs.darkTo,
        "reviewActions" to prefs.reviewActions,
        "scorecardLastPrompt" to prefs.scorecardLastPrompt,
        "activeProfile" to prefs.activeProfile,
        "growthPlansEnabled" to prefs.growthPlansEnabled,
        "customHue" to prefs.customHue,
        "autoReinforceEnabled" to prefs.autoReinforceEnabled,
        "autoReinforceMode" to prefs.autoReinforceMode,
        "preferredSttProvider" to prefs.preferredSttProvider
        // NOTE: apiKey, the app-lock PIN hash and the Whisper key are
        // deliberately excluded (stored in the secrets file).
    )

    /**
     * Import preferences from an export. Does NOT import secrets.
     */
    fun importPreferences(prefs: Prefs, json: JSONObject) {
        fun str(k: String) = if (json.has(k)) json.optString(k) else null
        fun bool(k: String) = if (json.has(k)) json.optBoolean(k) else null
        fun int(k: String) = if (json.has(k)) json.optInt(k) else null
        fun num(k: String) = if (json.has(k)) json.optDouble(k).toFloat() else null
        fun set(k: String): Set<String>? =
            if (json.has(k)) json.optJSONArray(k)?.strings()?.toSet() ?: emptySet() else null

        int("themeMode")?.let { prefs.themeMode = it }

        // Appearance goes through setAppearance so the revision counter is
        // bumped once for the whole group; assigning the properties one by
        // one would leave open Activities showing the old overlays. Each
        // property still coerces its own value, so a hand-edited or
        // downgraded export cannot produce an out-of-range palette.
        prefs.setAppearance(
            palette = int("palette") ?: prefs.palette,
            darkVariant = int("darkVariant") ?: prefs.darkVariant,
            density = int("density") ?: prefs.density,
            highContrast = bool("highContrast") ?: prefs.highContrast,
            serifAccents = bool("serifAccents") ?: prefs.serifAccents,
            monoFigures = bool("monoFigures") ?: prefs.monoFigures,
            dynamicColor = bool("dynamicColor") ?: prefs.dynamicColor
        )

        int("motionLevel")?.let { prefs.motionLevel = it }
        // hapticIntensity supersedes hapticsEnabled; apply the boolean first
        // so a newer export's explicit intensity wins over the legacy flag.
        bool("hapticsEnabled")?.let { prefs.hapticsEnabled = it }
        int("hapticIntensity")?.let { prefs.hapticIntensity = it }
        bool("soundEnabled")?.let { prefs.soundEnabled = it }
        num("soundVolume")?.let { prefs.soundVolume = it }
        // An empty array is meaningful — every cue switched off by hand —
        // so presence of the key, not emptiness of the list, is the test.
        set("soundCues")?.let { prefs.soundCues = it }
        set("gestures")?.let { prefs.gestures = it }
        int("colorVision")?.let { prefs.colorVision = it }
        int("tabLabels")?.let { prefs.tabLabels = it }
        int("appIcon")?.let { prefs.appIcon = it }
        int("startDestination")?.let { prefs.startDestination = it }
        bool("confirmCompletion")?.let { prefs.confirmCompletion = it }
        bool("showHistoryStrip")?.let { prefs.showHistoryStrip = it }
        bool("swipeActions")?.let { prefs.swipeActionsEnabled = it }
        bool("celebrationsEnabled")?.let { prefs.celebrationsEnabled = it }
        bool("remindersEnabled")?.let { prefs.remindersEnabled = it }
        str("quietFrom")?.let { prefs.quietFrom = it }
        str("quietTo")?.let { prefs.quietTo = it }
        str("quietWeekdayFrom")?.let { prefs.quietWeekdayFrom = it }
        str("quietWeekdayTo")?.let { prefs.quietWeekdayTo = it }
        str("quietWeekendFrom")?.let { prefs.quietWeekendFrom = it }
        str("quietWeekendTo")?.let { prefs.quietWeekendTo = it }
        bool("weeklySummaryEnabled")?.let { prefs.weeklySummaryEnabled = it }
        int("weeklySummaryDay")?.let { prefs.weeklySummaryDay = it }
        str("weeklySummaryTime")?.let { prefs.weeklySummaryTime = it }
        int("reminderBudget")?.let { prefs.reminderBudget = it }
        bool("checkpointsEnabled")?.let { prefs.checkpointsEnabled = it }
        str("morningCheckpoint")?.let { prefs.morningCheckpoint = it }
        str("middayCheckpoint")?.let { prefs.middayCheckpoint = it }
        str("eveningCheckpoint")?.let { prefs.eveningCheckpoint = it }
        bool("energyTracking")?.let { prefs.energyTracking = it }
        bool("aiEnabled")?.let { prefs.aiEnabled = it }
        str("autonomyProfile")?.let { prefs.autonomyProfile = it }
        // fullControlActivated is NOT imported — must be re-activated on new device
        bool("allowDestructive")?.let { prefs.allowDestructive = it }
        bool("allowSettingsChanges")?.let { prefs.allowSettingsChanges = it }
        bool("allowBackgroundJobs")?.let { prefs.allowBackgroundJobs = it }
        bool("autoSnapshot")?.let { prefs.autoSnapshot = it }
        bool("localCoordinatorOnly")?.let { prefs.localCoordinatorOnly = it }
        bool("voiceEnabled")?.let { prefs.voiceEnabled = it }
        str("providerName")?.let { prefs.providerName = it }
        str("baseUrl")?.let { prefs.baseUrl = it }
        str("fallbackUrl")?.let { prefs.fallbackUrl = it }
        str("model")?.let { prefs.model = it }
        str("organizationId")?.let { prefs.organizationId = it }
        str("customHeaders")?.let { prefs.customHeaders = it }
        int("temperature")?.let { prefs.temperature = it }
        int("topP")?.let { prefs.topP = it }
        int("maxTokens")?.let { prefs.maxTokens = it }
        int("frequencyPenalty")?.let { prefs.frequencyPenalty = it }
        int("presencePenalty")?.let { prefs.presencePenalty = it }
        int("seed")?.let { prefs.seed = it }
        str("stopSequences")?.let { prefs.stopSequences = it }
        str("responseFormat")?.let { prefs.responseFormat = it }
        int("requestTimeoutSec")?.let { prefs.requestTimeoutSec = it }
        int("retryCount")?.let { prefs.retryCount = it }
        int("conversationHistoryLimit")?.let { prefs.conversationHistoryLimit = it }
        int("maxContextChars")?.let { prefs.maxContextChars = it }
        bool("streamingEnabled")?.let { prefs.streamingEnabled = it }
        bool("requestLoggingEnabled")?.let { prefs.requestLoggingEnabled = it }
        str("customSystemPrompt")?.let { prefs.customSystemPrompt = it }
        str("systemPromptSuffix")?.let { prefs.systemPromptSuffix = it }
        bool("unlimitedBudget")?.let { prefs.unlimitedBudget = it }
        int("monthlyCallBudget")?.let { prefs.monthlyCallBudget = it }
        int("monthlyTokenBudget")?.let { prefs.monthlyTokenBudget = it }
        int("monthlyCostBudgetCents")?.let { prefs.monthlyCostBudgetCents = it }
        bool("contextIncludeHabits")?.let { prefs.contextIncludeHabits = it }
        bool("contextIncludeCheckIns")?.let { prefs.contextIncludeCheckIns = it }
        bool("contextIncludeInsights")?.let { prefs.contextIncludeInsights = it }
        bool("contextIncludeReviews")?.let { prefs.contextIncludeReviews = it }
        bool("contextIncludeObstacles")?.let { prefs.contextIncludeObstacles = it }
        bool("contextIncludeFlows")?.let { prefs.contextIncludeFlows = it }
        bool("contextIncludeMemory")?.let { prefs.contextIncludeMemory = it }
        str("memoryNotes")?.let { prefs.memoryNotes = it }
        bool("blueprintCloudRefine")?.let { prefs.blueprintCloudRefine = it }
        bool("ttsEnabled")?.let { prefs.ttsEnabled = it }
        int("ttsSpeechRate")?.let { prefs.ttsSpeechRate = it }
        int("ttsPitch")?.let { prefs.ttsPitch = it }
        str("sttProvider")?.let { prefs.sttProvider = it }
        bool("proactiveAi")?.let { prefs.proactiveAi = it }
        bool("proactiveNotifications")?.let { prefs.proactiveNotifications = it }
        str("displayName")?.let { prefs.displayName = it }
        bool("crashReporting")?.let { prefs.crashReporting = it }
        str("aiSetupMode")?.let { prefs.aiSetupMode = it }
        bool("autoBackupEnabled")?.let { prefs.autoBackupEnabled = it }
        str("autoBackupFrequency")?.let { prefs.autoBackupFrequency = it }
        int("maxBackups")?.let { prefs.maxBackups = it }
        str("aiInstructions")?.let { prefs.aiInstructions = it }
        str("aiLocalMemory")?.let { prefs.aiLocalMemory = it }
        bool("appLockEnabled")?.let { prefs.appLockEnabled = it }
        str("appLockMethod")?.let { prefs.appLockMethod = it }
        int("appLockTimeout")?.let { prefs.appLockTimeout = it }
        bool("appLockBiometric")?.let { prefs.appLockBiometric = it }
        int("appLockGraceSeconds")?.let { prefs.appLockGraceSeconds = it }
        str("quietPerDay")?.let { prefs.quietPerDay = it }
        str("darkSchedule")?.let { prefs.darkSchedule = it }
        str("darkFrom")?.let { prefs.darkFrom = it }
        str("darkTo")?.let { prefs.darkTo = it }
        str("reviewActions")?.let { prefs.reviewActions = it }
        str("scorecardLastPrompt")?.let { prefs.scorecardLastPrompt = it }
        str("activeProfile")?.let { prefs.activeProfile = it }
        bool("growthPlansEnabled")?.let { prefs.growthPlansEnabled = it }
        int("customHue")?.let { prefs.customHue = it }
        bool("autoReinforceEnabled")?.let { prefs.autoReinforceEnabled = it }
        str("autoReinforceMode")?.let { prefs.autoReinforceMode = it }
        str("preferredSttProvider")?.let { prefs.preferredSttProvider = it }
        // NOTE: the PIN hash and Whisper key are secrets and are never imported.
    }

    /**
     * Validate an import file before applying it.
     * Returns a list of warnings/issues, or empty if clean. Throws
     * [IllegalArgumentException] for structurally invalid input so the UI can
     * show a specific, actionable error (#67).
     */
    fun validateImport(json: JSONObject): List<String> {
        val warnings = mutableListOf<String>()

        // Must be a JSON object (enforced by the caller's parse).
        if (json.length() == 0) throw IllegalArgumentException("The file is empty.")

        val app = json.optString("app")
        if (app.isBlank() || (app != "SuperFlow" && !json.has("habits"))) {
            throw IllegalArgumentException(
                "This does not look like a SuperFlow export (missing \"app\":\"SuperFlow\")."
            )
        }
        if (app != "SuperFlow") warnings.add("Not a SuperFlow export (app=$app)")

        val version = json.optInt("exportVersion", 0)
        if (version > Serial.EXPORT_VERSION) warnings.add("Export is from a newer version ($version). Some data may not import correctly.")
        if (version in 1 until Serial.EXPORT_VERSION) warnings.add("Export is from an older version ($version). New fields will use defaults.")
        if (version == 0) warnings.add("No export version found; importing as the current format.")

        // Each array must actually be a JSON array of objects.
        val arrayKeys = listOf(
            "identities", "goals", "systems", "habits", "checkIns", "focus",
            "obstacles", "scorecard", "flows", "flowSteps", "reviews", "energy",
            "pauses", "projects", "sources", "requirements"
        )
        for (key in arrayKeys) {
            if (!json.has(key)) continue
            val arr = json.opt(key)
            if (arr !is JSONArray) {
                throw IllegalArgumentException("\"$key\" should be a list but was ${arr?.javaClass?.simpleName ?: "missing"}.")
            }
            // Spot-check the first row is an object.
            if (arr.length() > 0 && !arr.isNull(0) && arr.opt(0) !is JSONObject) {
                throw IllegalArgumentException("\"$key\" contains an entry that is not an object.")
            }
        }

        // Check for expected categories (advisory only).
        val present = json.keys().asSequence().toSet()
        val expected = exportableCategories.map { it.key }
        val missing = expected.filter { it !in present }
        if (missing.isNotEmpty()) warnings.add("Missing categories: ${missing.joinToString(", ")}")

        return warnings
    }
}
