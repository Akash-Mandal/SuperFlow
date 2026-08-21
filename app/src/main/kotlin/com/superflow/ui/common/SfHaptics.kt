package com.superflow.ui.common

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import com.superflow.data.Prefs
import com.superflow.design.HapticPattern
import com.superflow.design.Haptics

/**
 * Plays the [Haptics] vocabulary.
 *
 * The patterns themselves are data in the design layer, unit-tested for
 * duration and amplitude invariants. This is the part that talks to the
 * hardware, and it is the only place in the app that should.
 *
 * Three levels of capability are handled, because the difference is very
 * visible to the user:
 *
 *   * A motor with amplitude control: the full waveform, so "complete" can
 *     actually crescendo and "undo" can fall away.
 *   * A motor without amplitude control: the same timings played at full
 *     strength, which keeps the rhythm even though the dynamics are lost.
 *   * No usable vibrator: fall back to View.performHapticFeedback, which at
 *     least produces the system's own click.
 */
object SfHaptics {

    /**
     * Plays [pattern], scaled by the user's haptic intensity.
     *
     * Silently does nothing when the user has haptics off - that is the point
     * of the setting, and callers should not have to check first.
     */
    fun perform(view: View, pattern: HapticPattern, prefs: Prefs? = null) {
        val p = prefs ?: Prefs.get(view.context)
        val scaled = pattern.scaled(p.hapticScale) ?: return
        if (!play(view.context, scaled)) {
            // No vibrator we can drive; approximate with the system feedback
            // so the interaction is not left entirely silent.
            fallback(view, pattern)
        }
    }

    /** Convenience for the common "something was selected" tick. */
    fun select(view: View, prefs: Prefs? = null) = perform(view, Haptics.SELECT, prefs)

    /** Convenience for a completed habit. */
    fun complete(view: View, prefs: Prefs? = null) = perform(view, Haptics.COMPLETE, prefs)

    /**
     * @return false if there is no vibrator to play on, so the caller can
     *         fall back.
     */
    private fun play(context: Context, pattern: HapticPattern): Boolean {
        val vibrator = vibrator(context) ?: return false
        if (!vibrator.hasVibrator()) return false

        val timings = LongArray(pattern.steps.size) { pattern.steps[it].first.toLong() }
        if (timings.isEmpty()) return false

        return try {
            if (vibrator.hasAmplitudeControl()) {
                val amplitudes = IntArray(pattern.steps.size) { i ->
                    // 0 is a genuine pause; anything meant to be felt is at
                    // least 1, since rounding a quiet step to 0 would turn it
                    // into silence and change the rhythm.
                    val a = pattern.steps[i].second
                    if (a <= 0f) 0 else (a * 255f).toInt().coerceIn(1, 255)
                }
                vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                // Without amplitude control the array is read as alternating
                // off/on durations, so a leading 0 is required for the first
                // entry to be an "on" period.
                val alternating = LongArray(timings.size + 1)
                alternating[0] = 0
                System.arraycopy(timings, 0, alternating, 1, timings.size)
                vibrator.vibrate(VibrationEffect.createWaveform(alternating, -1))
            }
            true
        } catch (_: Exception) {
            // Vibration can throw on a device with an unusual motor or when
            // the app is in the background. Never let feedback crash an
            // action the user actually asked for.
            false
        }
    }

    private fun vibrator(context: Context): Vibrator? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                ?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    } catch (_: Exception) {
        null
    }

    /**
     * Maps a pattern onto the nearest system haptic constant.
     *
     * Deliberately coarse: this path only runs on hardware that cannot play a
     * waveform, so the goal is "something appropriate happened", not fidelity.
     */
    private fun fallback(view: View, pattern: HapticPattern) {
        val constant = when (pattern.name) {
            Haptics.COMPLETE.name, Haptics.CONFIRM_DESTRUCTIVE.name ->
                if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM
                else HapticFeedbackConstants.VIRTUAL_KEY
            Haptics.REJECT.name ->
                if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT
                else HapticFeedbackConstants.LONG_PRESS
            Haptics.LIFT.name, Haptics.DROP.name ->
                HapticFeedbackConstants.LONG_PRESS
            else -> HapticFeedbackConstants.VIRTUAL_KEY
        }
        try {
            view.performHapticFeedback(constant)
        } catch (_: Exception) {
            // Nothing more to try.
        }
    }
}
