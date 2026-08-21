package com.superflow.design

/**
 * The opt-in sound layer (plan 9.3).
 *
 * Four sounds, off by default, individually switchable, silent during quiet
 * hours. That last rule is the one that needs code rather than good
 * intentions: a check-in chime at eleven at night, from an app whose whole
 * pitch is calm, is a reason to uninstall.
 *
 * Nothing here plays anything. This decides *whether* a sound plays and how
 * loud, which is the part that can be got wrong silently and the part a test
 * can hold. The ui layer owns the SoundPool.
 *
 * No Android imports.
 */
object SoundDesign {

    /**
     * The vocabulary. Durations are the plan's, and are a design constraint
     * rather than a description: a check-in cue longer than half a second
     * starts to feel like a notification.
     */
    enum class Cue(
        val key: String,
        val label: String,
        val durationMs: Int,
        /** Which sibling haptic this pairs with, so the two land together. */
        val haptic: String,
    ) {
        CHECK_IN("checkIn", "Soft chime", 500, "HEAVY_CLICK"),
        DAY_COMPLETE("dayComplete", "Gentle bell", 1200, "SUCCESS"),
        REVIEW_SAVED("reviewSaved", "Page turn", 300, "CLICK"),
        SWIPE_DISMISS("swipeDismiss", "Soft whoosh", 200, "TICK"),
        ;
    }

    val cues: List<Cue> = Cue.entries.toList()

    /** Every cue on. What "Interface sounds: on" means before tuning. */
    val allCues: Set<String> = cues.map { it.key }.toSet()

    /**
     * The loudest a cue may be played, as a fraction of stream volume.
     *
     * The plan's "under 40dB equivalent" is not something an app can measure
     * — it depends on the device and how loud the user has their phone. What
     * we can do is refuse to be the loudest thing on the device, so the
     * user's volume slider is scaled into a ceiling well below unity.
     */
    const val MAX_GAIN = 0.6f

    /**
     * Per-cue relative loudness.
     *
     * A day-complete bell is the one moment we allow to be noticed; a swipe
     * whoosh should barely register, because it fires on a gesture people
     * make dozens of times a day and anything audible becomes irritating by
     * the second week.
     */
    fun relativeGain(cue: Cue): Float = when (cue) {
        Cue.DAY_COMPLETE -> 1.0f
        Cue.CHECK_IN -> 0.75f
        Cue.REVIEW_SAVED -> 0.6f
        Cue.SWIPE_DISMISS -> 0.35f
    }

    /**
     * The gain to play a cue at, or null to stay silent.
     *
     * Silence wins in every ambiguous case. The order of the checks is the
     * order of the user's intent: the master switch, then the individual
     * cue, then the context they cannot switch off from a settings screen.
     *
     * @param volume the user's 0..1 setting.
     * @param quiet whether we are inside quiet hours.
     * @param muted whether the device is on silent or the app is muted.
     */
    fun gainFor(
        cue: Cue,
        enabled: Boolean,
        cuesOn: Set<String>,
        volume: Float,
        quiet: Boolean,
        muted: Boolean = false,
    ): Float? {
        if (!enabled) return null
        if (cue.key !in cuesOn) return null
        if (quiet || muted) return null
        val v = volume.coerceIn(0f, 1f)
        if (v <= 0f) return null
        val gain = v * MAX_GAIN * relativeGain(cue)
        // Below this the sound is inaudible on a phone speaker but still
        // costs a SoundPool round trip and a wake of the audio path.
        return if (gain < AUDIBLE_FLOOR) null else gain
    }

    const val AUDIBLE_FLOOR = 0.02f

    /**
     * Whether a clock time falls inside quiet hours.
     *
     * Windows wrap midnight — 22:00 to 07:00 is the default and is the
     * normal case, not the edge case — so the comparison cannot be a simple
     * range check. Times are minutes since midnight.
     */
    fun inQuietHours(nowMinutes: Int, fromMinutes: Int, toMinutes: Int): Boolean {
        val now = ((nowMinutes % 1440) + 1440) % 1440
        val from = ((fromMinutes % 1440) + 1440) % 1440
        val to = ((toMinutes % 1440) + 1440) % 1440
        // A window with identical ends means "no quiet hours", not "always".
        if (from == to) return false
        return if (from < to) now in from until to else now >= from || now < to
    }

    /** Parses "HH:mm" into minutes since midnight, or null. */
    fun parseTime(text: String?): Int? {
        val t = text?.trim() ?: return null
        val parts = t.split(":")
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return h * 60 + m
    }

    /**
     * The order the preview button walks the cues in.
     *
     * Quietest first, so the person auditioning them is not startled by the
     * bell before they know what the control does.
     */
    fun previewOrder(): List<Cue> = cues.sortedBy { relativeGain(it) }

    /** Gap between cues in a preview run, in milliseconds. */
    const val PREVIEW_GAP_MS = 700L
}
