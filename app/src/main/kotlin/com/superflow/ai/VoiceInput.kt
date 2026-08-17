package com.superflow.ai

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat

/**
 * Voice control via the platform SpeechRecognizer.
 *
 * Speech is transcribed to text and then handled by exactly the same command
 * path as typing, so voice never gains capabilities the keyboard lacks.
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

    private var recognizer: SpeechRecognizer? = null
    private var listening = false

    companion object {
        fun isAvailable(context: Context): Boolean =
            SpeechRecognizer.isRecognitionAvailable(context)

        fun hasPermission(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED

        const val PERMISSION = Manifest.permission.RECORD_AUDIO
    }

    fun isListening(): Boolean = listening

    fun start(callbacks: Callbacks) {
        if (listening) return
        if (!isAvailable(context)) {
            callbacks.onError("Speech recognition is not available on this device")
            return
        }
        if (!hasPermission(context)) {
            callbacks.onError("Microphone permission is required")
            return
        }
        stop()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    listening = true
                    callbacks.onReady()
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) = callbacks.onVolume(rmsdB)
                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    listening = false
                    callbacks.onEnd()
                }

                override fun onError(error: Int) {
                    listening = false
                    callbacks.onError(describe(error))
                    callbacks.onEnd()
                }

                override fun onResults(results: Bundle?) {
                    listening = false
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                    if (text.isBlank()) callbacks.onError("Nothing was heard")
                    else callbacks.onResult(text)
                    callbacks.onEnd()
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.let { if (it.isNotBlank()) callbacks.onPartial(it) }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        runCatching { recognizer?.startListening(intent) }
            .onFailure { callbacks.onError("Could not start listening") }
    }

    fun stop() {
        listening = false
        runCatching {
            recognizer?.stopListening()
            recognizer?.destroy()
        }
        recognizer = null
    }

    private fun describe(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "Audio recording problem"
        SpeechRecognizer.ERROR_CLIENT -> "Recognition client error"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required"
        SpeechRecognizer.ERROR_NETWORK -> "Network problem"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timed out"
        SpeechRecognizer.ERROR_NO_MATCH -> "I did not catch that"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recogniser is busy"
        SpeechRecognizer.ERROR_SERVER -> "Recognition server error"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech heard"
        else -> "Speech recognition failed"
    }
}
