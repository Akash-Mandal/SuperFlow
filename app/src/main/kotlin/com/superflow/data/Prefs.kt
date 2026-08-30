package com.superflow.data

import android.content.Context
import android.content.SharedPreferences
import com.superflow.design.Navigation
import com.superflow.design.SoundDesign
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

        /* -------------------------------------------------- appearance */

        /** Colour palettes. Values are persisted, so never renumber them. */
        const val PALETTE_CALM = 0
        const val PALETTE_FOREST = 1
        const val PALETTE_OCEAN = 2
        const val PALETTE_DUSK = 3
        const val PALETTE_MONO = 4
        const val PALETTE_TERRACOTTA = 5
        const val PALETTE_AURORA = 6
        const val PALETTE_COUNT = 7

        const val PERFORMANCE_AUTO = 0
        const val PERFORMANCE_PERFORMANCE = 1
        const val PERFORMANCE_QUALITY = 2

        /** Dark-mode flavours. Only consulted when the app renders dark. */
        const val DARK_WARM = 0
        const val DARK_OLED = 1
        const val DARK_MIDNIGHT = 2

        /** Information density. Governs whitespace, never type size. */
        const val DENSITY_COMPACT = 0
        const val DENSITY_COMFORTABLE = 1
        const val DENSITY_SPACIOUS = 2

        /**
         * Motion level. [MOTION_NONE] is honoured independently of the
         * system "remove animations" setting, which we also respect: either
         * one being set is enough to disable non-essential motion.
         */
        const val MOTION_NONE = 0
        const val MOTION_REDUCED = 1
        const val MOTION_STANDARD = 2
        const val MOTION_EXPRESSIVE = 3

        /** Haptic intensity multiplier steps. */
        const val HAPTICS_OFF = 0
        const val HAPTICS_LIGHT = 1
        const val HAPTICS_MEDIUM = 2
        const val HAPTICS_STRONG = 3

        /** Which surface the app opens on. */
        const val START_TODAY = 0
        const val START_JOURNEY = 1
        const val START_INSIGHTS = 2
        const val START_STUDIO = 3

        /**
         * Launcher icon variants (plan 19.3).
         *
         * Each maps to an activity-alias in the manifest. The ordinals are
         * persisted, so entries may be appended but never reordered.
         */
        const val ICON_DEFAULT = 0
        const val ICON_MINIMAL = 1
        const val ICON_MONO = 2
    }

    private fun bool(key: String, def: Boolean) = p.getBoolean(key, def)
    private fun setBool(key: String, v: Boolean) { p.edit().putBoolean(key, v).apply(); bump() }
    private fun str(key: String, def: String) = p.getString(key, def) ?: def
    private fun setStr(key: String, v: String) { p.edit().putString(key, v).apply(); bump() }
    private fun num(key: String, def: Int) = p.getInt(key, def)
    private fun setNum(key: String, v: Int) { p.edit().putInt(key, v).apply(); bump() }
    private fun floatNum(key: String, def: Float) = p.getFloat(key, def)
    private fun setFloatNum(key: String, v: Float) { p.edit().putFloat(key, v).apply(); bump() }

    init {
        if (p.contains("appLockPinHash") && !secrets.contains("appLockPinHash")) {
            val hash = p.getString("appLockPinHash", "") ?: ""
            if (hash.isNotBlank()) {
                secrets.edit().putString("appLockPinHash", hash).apply()
                p.edit().remove("appLockPinHash").apply()
            }
        }
    }

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

    /* ---------------------------------------------------------- appearance */

    /**
     * Selected colour palette, one of the `PALETTE_*` constants.
     *
     * Out-of-range values coerce to [PALETTE_CALM] rather than throwing: the
     * stored value can outlive a downgrade that removed a palette, and a
     * cosmetic preference is never worth crashing the launch over.
     */
    var palette: Int
        get() = num("palette", PALETTE_CALM).let {
            if (it in 0 until PALETTE_COUNT) it else PALETTE_CALM
        }
        set(v) = setNum("palette", v)

    /** Dark flavour, one of the `DARK_*` constants. Ignored in light mode. */
    var darkVariant: Int
        get() = num("darkVariant", DARK_WARM).coerceIn(DARK_WARM, DARK_MIDNIGHT)
        set(v) = setNum("darkVariant", v)

    /** Information density, one of the `DENSITY_*` constants. */
    var density: Int
        get() = num("density", DENSITY_COMFORTABLE)
            .coerceIn(DENSITY_COMPACT, DENSITY_SPACIOUS)
        set(v) = setNum("density", v)

    var customHue: Int
        get() = num("customHue", -1).coerceIn(-1, 360)
        set(v) = setNum("customHue", v.coerceIn(-1, 360))

    /** Motion level, one of the `MOTION_*` constants. */
    var motionLevel: Int
        get() = num("motionLevel", MOTION_STANDARD)
            .coerceIn(MOTION_NONE, MOTION_EXPRESSIVE)
        set(v) = setNum("motionLevel", v)

    /**
     * Multiplier applied to every animation duration.
     *
     * Derived from [motionLevel] rather than stored, so the two can never
     * disagree. A value of 0 means "snap immediately"; callers must treat it
     * as "skip the animation" rather than running a zero-length one, since a
     * zero-duration animator still posts a frame.
     */
    val motionScale: Float
        get() = when (motionLevel) {
            MOTION_NONE -> 0f
            MOTION_REDUCED -> 0.5f
            MOTION_EXPRESSIVE -> 1.25f
            else -> 1f
        }

    /** True when non-essential motion should be skipped entirely. */
    val motionDisabled: Boolean
        get() = motionLevel == MOTION_NONE

    /** Serif face for identity statements and journal entries. */
    var serifAccents: Boolean
        get() = bool("serifAccents", true)
        set(v) = setBool("serifAccents", v)

    /** Monospaced figures in stats and chart axes. */
    var monoFigures: Boolean
        get() = bool("monoFigures", true)
        set(v) = setBool("monoFigures", v)

    /**
     * Extra contrast: opaque borders on every card and a darker outline.
     * Independent of the system high-contrast setting, which we also honour.
     */
    var highContrast: Boolean
        get() = bool("highContrast", false)
        set(v) = setBool("highContrast", v)

    /** Living accent shifts subtly with time of day (alpha3 §4.3). Off by default. */
    var livingAccent: Boolean
        get() = bool("livingAccent", false)
        set(v) = setBool("livingAccent", v)

    /** Performance tier override: Auto (detect), Performance (force low-end), Quality (force high-end). */
    var performanceMode: Int
        get() = num("performanceMode", PERFORMANCE_AUTO).coerceIn(0, 2)
        set(v) = setNum("performanceMode", v.coerceIn(0, 2))

    /**
     * Counter bumped whenever a preference changes that can only take effect
     * on a fresh Activity.
     *
     * Theme attributes resolve once, at view inflation, so changing a palette
     * or density does nothing to an Activity that is already on screen. An
     * Activity records this value in onCreate and compares it in onResume; if
     * it moved, it recreates itself. That is cheaper and far less error-prone
     * than trying to retint a live view hierarchy, and it is how the settings
     * screen can offer a live preview without every screen subscribing to
     * every appearance preference.
     *
     * Deliberately separate from the general [changes] flow, which fires for
     * any write at all - recreating every Activity because a reminder time
     * changed would be a bug, not a feature.
     */
    val appearanceRevision: Int
        get() = num("appearanceRevision", 0)

    /** Called by the appearance setters; see [appearanceRevision]. */
    private fun bumpAppearance() {
        setNum("appearanceRevision", num("appearanceRevision", 0) + 1)
    }

    /**
     * Applies an appearance change and signals that open Activities must be
     * recreated. Use this rather than assigning the properties directly when
     * the change comes from the settings screen.
     */
    fun setAppearance(
        palette: Int = this.palette,
        darkVariant: Int = this.darkVariant,
        density: Int = this.density,
        highContrast: Boolean = this.highContrast,
        serifAccents: Boolean = this.serifAccents,
        monoFigures: Boolean = this.monoFigures,
        dynamicColor: Boolean = this.dynamicColor,
    ) {
        val changed = palette != this.palette ||
            darkVariant != this.darkVariant ||
            density != this.density ||
            highContrast != this.highContrast ||
            serifAccents != this.serifAccents ||
            monoFigures != this.monoFigures ||
            dynamicColor != this.dynamicColor
        this.palette = palette
        this.darkVariant = darkVariant
        this.density = density
        this.highContrast = highContrast
        this.serifAccents = serifAccents
        this.monoFigures = monoFigures
        this.dynamicColor = dynamicColor
        // Only bump on a real change, so a settings screen that writes back
        // its current state on every bind does not cause a recreate loop.
        if (changed) bumpAppearance()
    }

    /* ---------------------------------------------------------- experience */

    /** Haptic intensity, one of the `HAPTICS_*` constants. */
    var hapticIntensity: Int
        get() {
            // Migration: hapticsEnabled predates this setting. An explicit
            // intensity wins; otherwise fall back to the old boolean so an
            // upgrading user keeps the behaviour they had.
            val stored = num("hapticIntensity", -1)
            if (stored in HAPTICS_OFF..HAPTICS_STRONG) return stored
            return if (hapticsEnabled) HAPTICS_MEDIUM else HAPTICS_OFF
        }
        set(v) {
            setNum("hapticIntensity", v.coerceIn(HAPTICS_OFF, HAPTICS_STRONG))
            // Keep the legacy flag consistent for code still reading it.
            setBool("haptics", v != HAPTICS_OFF)
        }

    /** Amplitude multiplier for waveform haptics, derived from intensity. */
    val hapticScale: Float
        get() = when (hapticIntensity) {
            HAPTICS_OFF -> 0f
            HAPTICS_LIGHT -> 0.6f
            HAPTICS_STRONG -> 1.4f
            else -> 1f
        }

    /** Opt-in sound design. Off by default: silence is the polite default. */
    var soundEnabled: Boolean
        get() = bool("soundEnabled", false)
        set(v) = setBool("soundEnabled", v)

    var soundVolume: Float
        get() = floatNum("soundVolume", 0.5f).coerceIn(0f, 1f)
        set(v) = setFloatNum("soundVolume", v.coerceIn(0f, 1f))

    /**
     * Which individual sound cues are on, as `SoundDesign.Cue` keys.
     *
     * Stored as a comma-joined string rather than a StringSet because
     * SharedPreferences returns the *same mutable set instance* it holds for
     * a StringSet, so a caller who mutates the result silently corrupts the
     * store without a write. A string cannot be mutated behind our back.
     *
     * An absent value means "all of them" — the master switch is the opt-in,
     * and someone who turns sound on should hear the whole vocabulary before
     * being asked to tune it. An empty string means the user switched every
     * cue off individually, which is a different state and is preserved.
     */
    var soundCues: Set<String>
        get() {
            val raw = p.getString("soundCues", null) ?: return SoundDesign.allCues
            return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        }
        set(v) {
            // Unknown keys are dropped: an import from a newer version must
            // not resurrect a cue this build has no sample for.
            val clean = v.filter { it in SoundDesign.allCues }
            setStr("soundCues", clean.joinToString(","))
        }

    /** Whether a single sound cue may play. */
    fun soundCueEnabled(key: String): Boolean = key in soundCues

    fun setSoundCueEnabled(key: String, on: Boolean) {
        val next = soundCues.toMutableSet()
        if (on) next.add(key) else next.remove(key)
        soundCues = next
    }

    /**
     * Colour-vision mode, one of `Accessibility.ColorVision.id`.
     *
     * This does not recolour the whole app — it changes the *hue pairs* used
     * for state, so done and missed never differ by red versus green alone.
     * Shape and glyph carry the same information regardless of this setting;
     * it exists to make the colour layer agree with them rather than to be
     * the only accommodation.
     */
    var colorVision: Int
        get() = num("colorVision", 0)
        set(v) {
            if (v != colorVision) {
                setNum("colorVision", v)
                bumpAppearance()
            }
        }

    /**
     * Tab label style, one of `Navigation.TabLabels` ids.
     *
     * Not an appearance-revision change: the bar re-reads it in onResume,
     * which is cheaper than recreating every activity in the back stack.
     */
    var tabLabels: Int
        get() = num("tabLabels", 0)
        set(v) = setNum("tabLabels", v)

    /**
     * Which gesture shortcuts are armed, as `Navigation.Gesture` keys.
     *
     * Same storage reasoning as [soundCues]. Absent means all of them; every
     * gesture has a visible equivalent, so switching one off costs
     * discoverability rather than capability.
     */
    var gestures: Set<String>
        get() {
            val raw = p.getString("gestures", null) ?: return Navigation.allGestures
            return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        }
        set(v) {
            val clean = v.filter { it in Navigation.allGestures }
            setStr("gestures", clean.joinToString(","))
        }

    fun gestureEnabled(gesture: Navigation.Gesture): Boolean =
        Navigation.gestureEnabled(gesture, gestures, confirmCompletion)

    fun setGestureEnabled(key: String, on: Boolean) {
        val next = gestures.toMutableSet()
        if (on) next.add(key) else next.remove(key)
        gestures = next
    }

    /**
     * Launcher icon variant, one of the `ICON_*` constants.
     *
     * Applied by enabling one activity-alias and disabling the rest, which
     * the launcher notices only on the next refresh — so the setting is
     * stored here and reconciled at startup rather than assumed to have
     * taken effect the moment it was written.
     */
    var appIcon: Int
        get() = num("appIcon", ICON_DEFAULT).coerceIn(ICON_DEFAULT, ICON_MONO)
        set(v) = setNum("appIcon", v.coerceIn(ICON_DEFAULT, ICON_MONO))

    /** Which tab the app opens on, one of the `START_*` constants. */
    var startDestination: Int
        get() = num("startDestination", START_TODAY)
            .coerceIn(START_TODAY, START_STUDIO)
        set(v) = setNum("startDestination", v)

    /** Confirm before marking a habit complete. Off by default. */
    var confirmCompletion: Boolean
        get() = bool("confirmCompletion", false)
        set(v) = setBool("confirmCompletion", v)

    /** Show the weekly history strip on habit cards. */
    var showHistoryStrip: Boolean
        get() = bool("showHistoryStrip", true)
        set(v) = setBool("showHistoryStrip", v)

    /** Swipe gestures on habit cards. Buttons remain available regardless. */
    var swipeActionsEnabled: Boolean
        get() = bool("swipeActions", true)
        set(v) = setBool("swipeActions", v)

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

    /**
     * Per-day-of-week quiet hours override, encoded as 7 pipe-separated
     * "from-to" pairs in ISO order (Mon..Sun). An empty pair means "use the
     * default quietFrom/quietTo for that day"; a "-" pair means "no quiet
     * hours that day". Example:
     * "22:00-07:00|22:00-07:00|...|23:30-09:00|-".
     */
    var quietPerDay: String
        get() = str("quietPerDay", "")
        set(v) = setStr("quietPerDay", v)

    /** Alpha2: separate quiet hours for weekdays and weekends. Empty inherits. */
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

    /* ---- Appearance scheduling ---- */

    /** Dark-mode schedule: "off" (manual), "sunset" (21:00–07:00), or "custom" (from [darkFrom]/[darkTo]). */
    var darkSchedule: String
        get() = str("darkSchedule", "off")
        set(v) = setStr("darkSchedule", v)

    var darkFrom: String
        get() = str("darkFrom", "21:00")
        set(v) = setStr("darkFrom", v)

    var darkTo: String
        get() = str("darkTo", "07:00")
        set(v) = setStr("darkTo", v)

    /** JSON map of reviewId -> (actionId -> done). Backs [com.superflow.domain.ReviewActions]. */
    var reviewActions: String
        get() = str("reviewActions", "{}")
        set(v) = setStr("reviewActions", v)

    var scorecardLastPrompt: String
        get() = str("scorecardLastPrompt", "")
        set(v) = setStr("scorecardLastPrompt", v)

    /* ---- Profiles (#78, lightweight for shared tablets) ---- */

    var activeProfile: String
        get() = str("activeProfile", "Me").ifBlank { "Me" }
        set(v) = setStr("activeProfile", v.ifBlank { "Me" })

    /* ---- App lock ---- */

    var appLockEnabled: Boolean
        get() = bool("appLockEnabled", false)
        set(v) = setBool("appLockEnabled", v)

    /** True when the user has opted to unlock with biometrics (in addition to PIN). */
    var appLockBiometric: Boolean
        get() = bool("appLockBiometric", true)
        set(v) = setBool("appLockBiometric", v)

    /** SHA-256 hash of the PIN, or empty when no PIN is set. Stored in secrets file excluded from backup. */
    var appLockPinHash: String
        get() = secrets.getString("appLockPinHash", "") ?: ""
        set(v) { secrets.edit().putString("appLockPinHash", v).apply(); bump() }

    /** Lock immediately on background, or after a short grace period (seconds). */
    var appLockGraceSeconds: Int
        get() = num("appLockGrace", 30)
        set(v) = setNum("appLockGrace", v.coerceIn(0, 1800))

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

    /** Maximum output tokens. Default 8192. Range 64–131072. */
    var maxTokens: Int
        get() = num("maxTokens", 8192)
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

    /** Max context characters sent in the system prompt. Default 20000. Range 1000–80000. */
    var maxContextChars: Int
        get() = num("maxCtxChars", 20000)
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

    var preferredSttProvider: String
        get() = str("preferredSttProvider", "")
        set(v) = setStr("preferredSttProvider", v)

    var whisperApiKey: String
        get() = secrets.getString("whisperApiKey", "") ?: ""
        set(v) { secrets.edit().putString("whisperApiKey", v).apply(); bump() }

    var proactiveAi: Boolean
        get() = bool("proactiveAi", true)
        set(v) = setBool("proactiveAi", v)

    var proactiveNotifications: Boolean
        get() = bool("proactiveNotif", true)
        set(v) = setBool("proactiveNotif", v)

    var growthPlansEnabled: Boolean
        get() = bool("growthPlans", true)
        set(v) = setBool("growthPlans", v)

    var autoReinforceEnabled: Boolean
        get() = bool("autoReinforce", false)
        set(v) = setBool("autoReinforce", v)

    var autoReinforceMode: String
        get() = str("autoReinforceMode", "propose")
        set(v) = setStr("autoReinforceMode", if (v == "auto") "auto" else "propose")

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

    /* ------------------------------------------------- app-lock extras (PR6) */

    /** "pin", "biometric", or "both". */
    var appLockMethod: String
        get() = str("appLockMethod", "pin")
        set(v) = setStr("appLockMethod", v)

    /** Auto-lock after this many minutes. 0 = lock on app open. */
    var appLockTimeout: Int
        get() = num("appLockTimeout", 0)
        set(v) = setNum("appLockTimeout", v.coerceIn(0, 60))

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
