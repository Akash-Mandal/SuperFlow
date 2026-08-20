package com.superflow.design

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Synthesises the four interface cues (plan 9.3).
 *
 * There are no audio files in this project and there is no way to add any:
 * every sound library worth using is licensed, and shipping four recorded
 * chimes would add more bytes than the entire drawable set. Generating them
 * is not a compromise here — it is better. A synthesised cue is a few dozen
 * lines, is exactly as long as the design says it is, and can be tuned by
 * changing a number instead of re-recording.
 *
 * The character is set by three decisions:
 *
 * - **Sine partials, not square or saw.** Anything with hard harmonics
 *   reads as an alert. These are meant to be noticed and then forgotten.
 * - **Pitches from a pentatonic set.** The major pentatonic has no minor
 *   seconds and no tritone, so any two cues heard together — a check-in
 *   landing while a day-complete bell is still ringing — still consonate.
 * - **Exponential decay with a short attack.** A cue that starts
 *   instantaneously clicks; one that ends abruptly sounds cut off. Both
 *   read as a bug in the app rather than as a sound.
 *
 * Pure: it returns sample arrays. Nothing here opens an audio device.
 */
object ToneSynth {

    /**
     * Output rate.
     *
     * 44.1 kHz rather than 48: every Android device resamples something,
     * and 44.1 is the one rate `AudioTrack` is guaranteed to accept.
     */
    const val SAMPLE_RATE = 44_100

    /**
     * Attack, in milliseconds.
     *
     * Long enough to remove the click of a waveform starting at full
     * amplitude, short enough that the cue still feels instantaneous — this
     * is well under the ~20 ms at which onset becomes perceptible as a
     * fade.
     */
    const val ATTACK_MS = 6

    /** Amplitude at which a decaying tone is treated as finished. */
    const val SILENCE = 0.0005f

    /**
     * A single partial: a frequency, its share of the amplitude, and how
     * fast it dies away relative to the fundamental.
     *
     * Real struck objects lose their upper partials first, which is most of
     * what makes a synthesised bell sound like a bell instead of an organ.
     * [decayScale] above 1 means this partial fades faster.
     */
    data class Partial(
        val hz: Float,
        val amplitude: Float,
        val decayScale: Float = 1f,
        /** Delay before this partial enters, as a fraction of the duration. */
        val delay: Float = 0f,
    )

    /** A complete cue recipe. */
    data class Voice(
        val partials: List<Partial>,
        /** Seconds for the fundamental to fall by a factor of e. */
        val decay: Float,
        /** 0 for a pitched tone; above 0 mixes in filtered noise. */
        val noise: Float = 0f,
    )

    /* ------------------------------------------------------------ pitches */

    // A major pentatonic on A: A4 C#5 E5 F#5 A5. Chosen over a plain major
    // triad because the added sixth keeps a two-note cue from sounding like
    // a doorbell.
    const val A4 = 440.0f
    const val CS5 = 554.37f
    const val E5 = 659.26f
    const val FS5 = 739.99f
    const val A5 = 880.0f
    const val E6 = 1318.51f

    /**
     * The recipe for a cue.
     *
     * Each is deliberately different in *shape*, not only in pitch, because
     * people identify short sounds by envelope long before they identify
     * them by note.
     */
    fun voiceFor(cue: SoundDesign.Cue): Voice = when (cue) {
        // Two partials a fifth apart, the upper one entering fractionally
        // late: reads as a small struck bar rather than a beep.
        SoundDesign.Cue.CHECK_IN -> Voice(
            partials = listOf(
                Partial(E5, 1.0f),
                Partial(A5, 0.45f, decayScale = 1.6f, delay = 0.04f),
                Partial(E6, 0.12f, decayScale = 2.4f),
            ),
            decay = 0.16f,
        )
        // The one cue allowed to ring. A full pentatonic stack with a long
        // tail; it should feel like the end of something.
        SoundDesign.Cue.DAY_COMPLETE -> Voice(
            partials = listOf(
                Partial(A4, 0.9f),
                Partial(CS5, 0.5f, decayScale = 1.2f, delay = 0.06f),
                Partial(E5, 0.55f, decayScale = 1.1f, delay = 0.12f),
                Partial(A5, 0.3f, decayScale = 1.8f, delay = 0.18f),
                Partial(E6, 0.08f, decayScale = 3.0f),
            ),
            decay = 0.42f,
        )
        // Not a pitch at all: a page turn is broadband and short. A little
        // tone underneath stops it reading as static.
        SoundDesign.Cue.REVIEW_SAVED -> Voice(
            partials = listOf(Partial(FS5, 0.35f, decayScale = 2.0f)),
            decay = 0.07f,
            noise = 0.65f,
        )
        // Almost entirely noise, very short. Fires on a gesture people make
        // constantly, so it has to be felt more than heard.
        SoundDesign.Cue.SWIPE_DISMISS -> Voice(
            partials = listOf(Partial(A5, 0.12f, decayScale = 3.0f)),
            decay = 0.045f,
            noise = 0.9f,
        )
    }

    /* ------------------------------------------------------------ envelope */

    /**
     * Amplitude at [t] seconds into a cue of [duration] seconds.
     *
     * Attack is linear, decay is exponential, and the last 15% is faded to
     * zero so the buffer never ends mid-cycle. Without that final taper an
     * otherwise perfect tone ends in a click, which is exactly the artefact
     * this whole file exists to avoid.
     */
    fun envelope(t: Float, duration: Float, decay: Float): Float {
        if (t < 0f || t > duration) return 0f
        val attack = ATTACK_MS / 1000f
        val rise = if (t < attack) t / attack else 1f
        val fall = exp(-t / decay.coerceAtLeast(0.001f))
        val tailStart = duration * 0.85f
        val taper = if (t <= tailStart) 1f
        else 1f - ((t - tailStart) / (duration - tailStart).coerceAtLeast(0.001f))
        return rise * fall * taper.coerceIn(0f, 1f)
    }

    /**
     * Renders a cue to mono float samples in -1..1.
     *
     * @param gain final scaling, from [SoundDesign.gainFor].
     * @param random noise source, injected so the output is reproducible in
     *   a test. Must return values in 0..1.
     */
    fun render(
        cue: SoundDesign.Cue,
        gain: Float = 1f,
        sampleRate: Int = SAMPLE_RATE,
        random: () -> Float = { kotlin.random.Random.nextFloat() },
    ): FloatArray {
        val voice = voiceFor(cue)
        val duration = cue.durationMs / 1000f
        val count = (duration * sampleRate).toInt().coerceAtLeast(1)
        val out = FloatArray(count)

        // One-pole low-pass state for the noise component. A page turn and
        // a whoosh are both dull, and raw white noise is the opposite of
        // dull; without this they sound like interference.
        var filtered = 0f
        val alpha = 0.22f
        val normal = normalisation(voice)

        for (i in 0 until count) {
            val t = i.toFloat() / sampleRate
            var sample = 0f

            for (p in voice.partials) {
                val start = duration * p.delay
                if (t < start) continue
                val local = t - start
                val env = envelope(local, duration - start, voice.decay / p.decayScale)
                sample += p.amplitude * env * sin(2.0 * PI * p.hz * local).toFloat()
            }

            if (voice.noise > 0f) {
                val white = random().coerceIn(0f, 1f) * 2f - 1f
                filtered += alpha * (white - filtered)
                sample += voice.noise * filtered * envelope(t, duration, voice.decay)
            }

            // Clamping rather than normalising after the fact: a cue that
            // quietly rescales itself depending on where its partials
            // happened to align would not be reproducible across devices.
            out[i] = (sample / normal * gain).coerceIn(-1f, 1f)
        }
        return out
    }

    /**
     * The divisor that keeps a voice inside -1..1.
     *
     * The worst case is every partial peaking together, which the delays
     * make unlikely but not impossible, so the sum of amplitudes is the
     * honest bound. Under-driving slightly is the right error: the gain
     * from [SoundDesign] is already well below unity, and a cue that clips
     * sounds broken in a way a quiet one does not.
     */
    fun normalisation(voice: Voice): Float =
        (voice.partials.sumOf { it.amplitude.toDouble() }.toFloat() + voice.noise)
            .coerceAtLeast(1f)

    /**
     * Converts float samples to signed 16-bit little-endian PCM.
     *
     * 32767 rather than 32768 because the positive side of a signed 16-bit
     * range is one short; scaling by 32768 makes a full-amplitude sample
     * wrap to the largest negative value, which is an audible pop at the
     * loudest point of the cue.
     */
    fun toPcm16(samples: FloatArray): ByteArray {
        val out = ByteArray(samples.size * 2)
        for (i in samples.indices) {
            val v = (samples[i].coerceIn(-1f, 1f) * 32767f).toInt()
            out[i * 2] = (v and 0xFF).toByte()
            out[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
        }
        return out
    }

    /** Peak absolute amplitude, for tests and for a level meter. */
    fun peak(samples: FloatArray): Float {
        var max = 0f
        for (s in samples) {
            val a = if (s < 0f) -s else s
            if (a > max) max = a
        }
        return max
    }
}
