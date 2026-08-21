package com.superflow.ai

import android.content.Context
import android.speech.tts.TextToSpeech
import com.superflow.data.Prefs
import java.util.Locale

/**
 * Thin wrapper over the platform [TextToSpeech] engine (#1 — AI responses
 * read aloud).
 *
 * Initialisation is lazy and asynchronous; [speak] queues utterances once the
 * engine is ready and silently drops them if TTS is unavailable or disabled.
 * Rate and pitch are read live from [Prefs] (stored ×100), so changes in
 * Settings apply to the next utterance.
 */
class Speech(context: Context) {

    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private var ready = false

    fun init() {
        if (tts != null) return
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
                ready = true
            }
        }
    }

    fun isSpeaking(): Boolean = tts?.isSpeaking == true

    fun speak(prefs: Prefs, text: String) {
        if (!prefs.ttsEnabled) return
        init()
        val engine = tts ?: return
        engine.setSpeechRate(prefs.ttsSpeechRate / 100f)
        engine.setPitch(prefs.ttsPitch / 100f)
        if (ready) {
            engine.speak(text, TextToSpeech.QUEUE_ADD, null, "superflow-${System.nanoTime()}")
        }
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
    }
}
