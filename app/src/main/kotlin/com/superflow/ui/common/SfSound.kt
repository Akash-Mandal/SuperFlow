package com.superflow.ui.common

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import com.superflow.data.Prefs
import com.superflow.design.SoundDesign
import com.superflow.design.ToneSynth
import java.util.concurrent.Executors

/**
 * Plays the interface cues (plan 9.3).
 *
 * The decision of *whether* to make a sound belongs to [SoundDesign] and is
 * tested there; the *shape* of the sound belongs to [ToneSynth] and is
 * tested there. This object is what remains: an `AudioTrack`, a cache, and
 * the handful of Android behaviours that neither pure layer can know about.
 *
 * Three of those matter enough to spell out.
 *
 * **The device's ringer wins.** A cue is played on the *notification*
 * stream, and is suppressed entirely when the phone is on silent or
 * vibrate. `USAGE_ASSISTANCE_SONIFICATION` would technically be the more
 * accurate usage tag, but it ignores the ringer switch, and a habit tracker
 * that chirps in a meeting because it classified itself as a UI sound has
 * failed at the only thing this feature was for.
 *
 * **Never on the main thread.** Rendering a 1.2-second bell is a few
 * hundred thousand multiplications. That is nothing on a background thread
 * and a dropped frame on the UI one, which would make the "calm" cue the
 * only stutter in the app.
 *
 * **Cache by cue, not by gain.** Volume is applied by `AudioTrack` at
 * playback, so one rendered buffer per cue serves every volume — otherwise
 * dragging the volume slider would re-synthesise on every pixel.
 */
object SfSound {

    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "sf-sound").apply {
            isDaemon = true
            // Below the default so a cue can never delay real work; audio
            // latency of a few milliseconds is imperceptible for a sound
            // that is a reaction to something the user already saw.
            priority = Thread.MIN_PRIORITY + 2
        }
    }

    private val cache = HashMap<SoundDesign.Cue, ByteArray>()

    /**
     * Plays [cue] if the user's settings and the device's state allow it.
     *
     * Safe to call from anywhere, including from a background thread, and
     * safe to call for a cue the user has switched off — the whole point is
     * that callers describe what happened rather than deciding whether it
     * should be audible.
     */
    fun play(context: Context, cue: SoundDesign.Cue, prefs: Prefs? = null) {
        val app = context.applicationContext
        val p = prefs ?: Prefs.get(app)

        // Cheap checks first, on the calling thread: the common case is
        // that sound is off, and that should not cost a thread hand-off.
        if (!p.soundEnabled) return
        if (cue.key !in p.soundCues) return

        val gain = SoundDesign.gainFor(
            cue = cue,
            enabled = p.soundEnabled,
            cuesOn = p.soundCues,
            volume = p.soundVolume,
            quiet = inQuietHours(p),
            muted = muted(app),
        ) ?: return

        executor.execute { render(cue, gain) }
    }

    /**
     * Plays every cue in turn, quietest first, for the settings preview.
     *
     * Sequential rather than simultaneous, with a gap: the point is to let
     * someone recognise each sound, and four bells at once is a chord, not
     * a demonstration.
     */
    fun preview(context: Context, prefs: Prefs? = null) {
        val app = context.applicationContext
        val p = prefs ?: Prefs.get(app)
        executor.execute {
            SoundDesign.previewOrder().forEach { cue ->
                if (cue.key !in p.soundCues) return@forEach
                val gain = SoundDesign.gainFor(
                    cue, p.soundEnabled, p.soundCues, p.soundVolume,
                    quiet = false, // A preview is explicitly requested.
                    muted = muted(app),
                ) ?: return@forEach
                render(cue, gain)
                Thread.sleep(SoundDesign.PREVIEW_GAP_MS)
            }
        }
    }

    /** Drops the cached buffers. Called when nothing is likely to play soon. */
    fun release() {
        executor.execute { cache.clear() }
    }

    /* ------------------------------------------------------------ internals */

    private fun render(cue: SoundDesign.Cue, gain: Float) {
        try {
            val pcm = cache.getOrPut(cue) {
                ToneSynth.toPcm16(ToneSynth.render(cue, gain = 1f))
            }
            val track = build(pcm.size) ?: return
            track.setVolume(gain.coerceIn(0f, 1f))
            track.write(pcm, 0, pcm.size)
            track.play()
            // A static-mode track holds its buffer, so it can be released
            // as soon as the audio has drained. Sleeping for the cue's own
            // duration is exact, and this thread has nothing else to do.
            Thread.sleep(cue.durationMs.toLong() + TAIL_MS)
            track.release()
        } catch (_: Exception) {
            // Audio is decoration. A device that refuses to give us a track
            // - an emulator without an audio HAL, a phone with every stream
            // in use - must not take a check-in down with it.
        }
    }

    private fun build(bytes: Int): AudioTrack? {
        if (bytes <= 0) return null
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(ToneSynth.SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        return AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(format)
            .setBufferSizeInBytes(bytes)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
            .takeIf { it.state == AudioTrack.STATE_NO_STATIC_DATA }
    }

    /**
     * Whether the device is telling us to be quiet.
     *
     * Both the ringer mode and a zeroed notification stream count. The
     * second happens more than people expect — plenty of users leave the
     * ringer on and the notification volume at zero — and honouring only
     * the first would make this app the exception that still makes noise.
     */
    private fun muted(context: Context): Boolean {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return true
        if (audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return true
        if (audio.getStreamVolume(AudioManager.STREAM_NOTIFICATION) <= 0) return true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            audio.isStreamMute(AudioManager.STREAM_NOTIFICATION)
        ) return true
        return false
    }

    /**
     * Whether now falls inside the user's quiet hours.
     *
     * There is no separate on/off switch: the window is always honoured,
     * and setting both ends to the same time disables it. That is one
     * fewer control, and it removes the state where quiet hours are
     * configured but silently not applied.
     */
    private fun inQuietHours(prefs: Prefs): Boolean {
        val from = SoundDesign.parseTime(prefs.quietFrom) ?: return false
        val to = SoundDesign.parseTime(prefs.quietTo) ?: return false
        val now = java.time.LocalTime.now()
        return SoundDesign.inQuietHours(now.hour * 60 + now.minute, from, to)
    }

    /** Slack after a cue's nominal length, to let the hardware drain. */
    private const val TAIL_MS = 60L
}
