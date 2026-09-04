package com.superflow.ai

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * Legacy voice control entry point.
 *
 * Delegates to [VoiceInputV2] to support configurable STT providers
 * (Platform SpeechRecognizer, Whisper API, local Whisper, Vosk) seamlessly across all screens.
 */
class VoiceInput(private val context: Context) {

    interface Callbacks {
        fun onReady() {}
        fun onPartial(text: String) {}
        fun onResult(text: String)
        fun onError(message: String)
        fun onVolume(rms: Float) {}
        fun onEnd() {}
    }

    private var engine: VoiceEngine? = null

    companion object {
        fun isAvailable(context: Context): Boolean =
            VoiceInputV2.availableProviders(context).isNotEmpty()

        fun hasPermission(context: Context): Boolean =
            VoiceInputV2.hasPermission(context)

        fun settingsIntent(): Intent = Intent(android.provider.Settings.ACTION_VOICE_INPUT_SETTINGS)

        const val PERMISSION = Manifest.permission.RECORD_AUDIO
    }

    fun isListening(): Boolean = engine?.isListening() == true

    fun start(callbacks: Callbacks) {
        stop()
        if (!isAvailable(context)) {
            callbacks.onError(
                "No speech recogniser found. On de-Googled devices, install or enable a " +
                        "voice-input provider (e.g. a system speech engine), then grant " +
                        "microphone permission."
            )
            return
        }
        if (!hasPermission(context)) {
            callbacks.onError("Microphone permission is required")
            return
        }

        try {
            val v2Callbacks = object : VoiceInputV2.Callbacks {
                override fun onReady() = callbacks.onReady()
                override fun onPartial(text: String) = callbacks.onPartial(text)
                override fun onResult(text: String) = callbacks.onResult(text)
                override fun onError(message: String) = callbacks.onError(message)
                override fun onVolume(rms: Float) = callbacks.onVolume(rms)
                override fun onEnd() = callbacks.onEnd()
            }
            engine = VoiceInputV2.create(context).also { it.start(v2Callbacks) }
        } catch (e: Exception) {
            callbacks.onError(e.message ?: "Failed to start voice input")
        }
    }

    fun stop() {
        engine?.stop()
        engine = null
    }
}
