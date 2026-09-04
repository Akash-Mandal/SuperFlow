package com.superflow.ai

import android.content.Context
import com.superflow.data.Prefs

/**
 * Legacy wrapper over Text-To-Speech engine.
 *
 * Delegates to the unified [SfTextToSpeech] singleton to prevent duplicate
 * TTS engine initialization, speech rate conflicts, and resource leaks.
 */
class Speech(context: Context) {

    private val ttsEngine = SfTextToSpeech.get(context)

    fun init() {
        // SfTextToSpeech initializes singleton asynchronously upon access
    }

    fun isSpeaking(): Boolean = ttsEngine.isSpeaking()

    fun speak(prefs: Prefs, text: String) {
        if (!prefs.ttsEnabled) return
        ttsEngine.speak(text)
    }

    fun stop() {
        ttsEngine.stop()
    }

    fun shutdown() {
        ttsEngine.stop()
    }
}
