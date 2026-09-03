package com.superflow.ai

import android.content.Context
import android.content.Intent

/**
 * Voice control facade delegating to [VoiceInputV2].
 *
 * Speech is transcribed to text through [VoiceInputV2]'s multi-provider engine
 * (Platform, Whisper API, Local, Vosk) and then handled by the exact same
 * command path as typing.
 */
class VoiceInput(private val context: Context) {

    interface Callbacks : VoiceInputV2.Callbacks

    private var engine: VoiceEngine? = null

    companion object {
        fun isAvailable(context: Context): Boolean =
            VoiceInputV2.availableProviders(context).isNotEmpty()

        fun hasPermission(context: Context): Boolean =
            VoiceInputV2.hasPermission(context)

        /**
         * An intent that opens the system voice-input settings.
         */
        fun settingsIntent(): Intent = Intent(android.provider.Settings.ACTION_VOICE_INPUT_SETTINGS)

        const val PERMISSION = VoiceInputV2.PERMISSION
    }

    fun isListening(): Boolean = engine?.isListening() ?: false

    fun start(callbacks: VoiceInputV2.Callbacks) {
        stop()
        runCatching {
            val vEngine = VoiceInputV2.create(context)
            engine = vEngine
            vEngine.start(callbacks)
        }.onFailure { e ->
            callbacks.onError(e.message ?: "Failed to start voice engine")
        }
    }

    fun stop() {
        runCatching { engine?.stop() }
        engine = null
    }
}
