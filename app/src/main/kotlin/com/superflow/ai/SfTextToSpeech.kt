package com.superflow.ai

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.superflow.data.Prefs
import java.util.Locale

/**
 * SuperFlow's central text-to-speech engine.
 *
 * Uses Android's built-in TTS API with proper lifecycle management. Speaks
 * coach AI responses, check-in confirmations, and blueprint report summaries.
 * Slightly slower speech rate for a calm feel.
 *
 * Critical fix for Bug #1: "TTS not implemented — no TTS code exists anywhere"
 */
class SfTextToSpeech(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var initialized = false
    private var pendingText: String? = null
    private var pendingCallback: (() -> Unit)? = null

    private val prefs: Prefs get() = Prefs.get(context)

    init {
        tts = TextToSpeech(context) { status ->
            initialized = status == TextToSpeech.SUCCESS
            if (initialized) {
                tts?.language = Locale.getDefault()
                tts?.setSpeechRate((prefs.ttsSpeechRate / 100f).coerceIn(0.5f, 2.0f))
                tts?.setPitch((prefs.ttsPitch / 100f).coerceIn(0.5f, 2.0f))

                // Listen for utterance completion
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        android.os.Handler(context.mainLooper).post {
                            pendingCallback?.invoke()
                            pendingCallback = null
                        }
                    }
                    @Deprecated("Deprecated in Java", replaceWith = ReplaceWith("onError"))
                    override fun onError(utteranceId: String?) {}
                })

                pendingText?.let { speak(it); pendingText = null }
            }
        }
    }

    /**
     * Speak the given text. Designed for short utterances (AI replies, confirmations).
     */
    fun speak(text: String) {
        if (!prefs.ttsEnabled) return
        if (text.isBlank()) return
        if (!initialized) {
            pendingText = text
            return
        }
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "sf_${System.currentTimeMillis()}")
    }

    /**
     * Speak and optionally run a callback when done.
     */
    fun speak(text: String, onDone: () -> Unit) {
        pendingCallback = onDone
        speak(text)
    }

    /**
     * Stop any ongoing speech.
     */
    fun stop() {
        tts?.stop()
        pendingText = null
        pendingCallback = null
    }

    /**
     * Check if TTS is currently speaking.
     */
    fun isSpeaking(): Boolean = tts?.isSpeaking ?: false

    /**
     * Release TTS resources. Call from Activity.onDestroy() or Fragment.onDestroyView().
     */
    fun destroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        initialized = false
    }

    companion object {
        @Volatile private var instance: SfTextToSpeech? = null

        fun get(context: Context): SfTextToSpeech =
            instance ?: synchronized(this) {
                instance ?: SfTextToSpeech(context.applicationContext).also { instance = it }
            }

        fun reset() {
            instance?.destroy()
            instance = null
        }
    }
}