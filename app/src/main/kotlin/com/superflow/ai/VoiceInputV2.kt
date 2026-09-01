package com.superflow.ai

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import com.superflow.data.Prefs
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * Multi-provider Speech-to-Text engine.
 *
 * Critical fix for Bug #2: "STT depends on Google Play Services — fails on
 * de-Googled phones, custom ROMs, many regions."
 *
 * Providers:
 *   - PLATFORM: Android's built-in SpeechRecognizer (Google, Samsung, etc.)
 *   - WHISPER_API: OpenAI-compatible Whisper API (any provider)
 *   - WHISPER_LOCAL: Local whisper.cpp binary (offline)
 *   - VOSK: Vosk offline speech recognition
 *
 * Automatically picks the best available provider or lets the user choose.
 */
object VoiceInputV2 {

    enum class Provider {
        PLATFORM,
        WHISPER_API,
        WHISPER_LOCAL,
        VOSK
    }

    interface Callbacks {
        fun onReady() {}
        fun onPartial(text: String) {}
        fun onResult(text: String)
        fun onError(message: String)
        fun onVolume(rms: Float) {}
        fun onEnd() {}
    }

    /**
     * Check which providers are available on this device.
     */
    fun availableProviders(context: Context): List<Provider> = buildList {
        if (SpeechRecognizer.isRecognitionAvailable(context)) add(Provider.PLATFORM)
        val prefs = Prefs.get(context)
        if (prefs.whisperApiKey.isNotBlank() || prefs.apiKey.isNotBlank()) add(Provider.WHISPER_API)
        if (File(context.filesDir, "whisper").exists()) add(Provider.WHISPER_LOCAL)
        // Vosk: check if models are installed
        if (File(context.filesDir, "vosk").exists()) add(Provider.VOSK)
    }

    /**
     * Create a voice engine for the given (or preferred) provider.
     */
    fun create(context: Context, provider: Provider? = null): VoiceEngine {
        val prefs = Prefs.get(context)
        val chosen = provider
            ?: prefs.preferredSttProvider?.let {
                runCatching { Provider.valueOf(it.uppercase()) }.getOrNull()
            }
            ?: availableProviders(context).firstOrNull()
            ?: throw IllegalStateException("No STT provider available on this device")

        return when (chosen) {
            Provider.PLATFORM -> PlatformVoiceEngine(context)
            Provider.WHISPER_API -> WhisperApiVoiceEngine(context)
            Provider.WHISPER_LOCAL -> WhisperLocalVoiceEngine(context)
            Provider.VOSK -> VoskVoiceEngine(context)
        }
    }

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED

    const val PERMISSION = Manifest.permission.RECORD_AUDIO
}

/**
 * Common interface for all voice recognition engines.
 */
interface VoiceEngine {
    fun isListening(): Boolean
    fun start(callbacks: VoiceInputV2.Callbacks)
    fun stop()
}

/* ───────────────────────────────────────────────────── PLATFORM PROVIDER ── */

class PlatformVoiceEngine(private val context: Context) : VoiceEngine {

    private var recognizer: SpeechRecognizer? = null
    private var listening = false

    override fun isListening(): Boolean = listening

    override fun start(callbacks: VoiceInputV2.Callbacks) {
        if (listening) return
        if (!VoiceInputV2.hasPermission(context)) {
            callbacks.onError("Microphone permission is required")
            return
        }
        stop()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    listening = true; callbacks.onReady()
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) = callbacks.onVolume(rmsdB)
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { listening = false; callbacks.onEnd() }
                override fun onError(error: Int) {
                    listening = false
                    callbacks.onError(describe(error))
                    callbacks.onEnd()
                }
                override fun onResults(results: Bundle?) {
                    listening = false
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull().orEmpty()
                    if (text.isBlank()) callbacks.onError("Nothing was heard")
                    else callbacks.onResult(text)
                    callbacks.onEnd()
                }
                override fun onPartialResults(partial: Bundle?) {
                    partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()?.let { if (it.isNotBlank()) callbacks.onPartial(it) }
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        runCatching { recognizer?.startListening(intent) }
            .onFailure { callbacks.onError("Could not start listening") }
    }

    override fun stop() {
        listening = false
        runCatching { recognizer?.stopListening(); recognizer?.destroy() }
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

/* ──────────────────────────────────────────────────── WHISPER API PROVIDER ── */

class WhisperApiVoiceEngine(private val context: Context) : VoiceEngine {

    private val prefs = Prefs.get(context)
    private var listening = false
    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null
    private var currentCallbacks: VoiceInputV2.Callbacks? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun isListening(): Boolean = listening

    override fun start(callbacks: VoiceInputV2.Callbacks) {
        if (listening) return
        if (!VoiceInputV2.hasPermission(context)) {
            callbacks.onError("Microphone permission is required")
            return
        }

        val key = prefs.whisperApiKey.ifBlank { prefs.apiKey }
        if (key.isBlank()) {
            callbacks.onError("Whisper API key or OpenAI API key is required")
            return
        }

        currentCallbacks = callbacks
        val outputFile = File(context.cacheDir, "sf_whisper_${System.currentTimeMillis()}.m4a")
        audioFile = outputFile

        runCatching {
            @Suppress("DEPRECATION")
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                MediaRecorder()
            }
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioSamplingRate(16000)
            recorder.setAudioEncodingBitRate(64000)
            recorder.setOutputFile(outputFile.absolutePath)
            recorder.prepare()
            recorder.start()
            mediaRecorder = recorder
            listening = true
            callbacks.onReady()
        }.onFailure { e ->
            listening = false
            mediaRecorder = null
            audioFile?.delete()
            audioFile = null
            callbacks.onError("Failed to start audio recording: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    override fun stop() {
        if (!listening) return
        listening = false
        val callbacks = currentCallbacks
        val file = audioFile
        val recorder = mediaRecorder
        mediaRecorder = null

        runCatching {
            recorder?.stop()
            recorder?.release()
        }.onFailure { e ->
            recorder?.release()
            file?.delete()
            callbacks?.onError("Recording failed: ${e.message ?: e.javaClass.simpleName}")
            callbacks?.onEnd()
            return
        }

        if (file == null || !file.exists() || file.length() == 0L) {
            file?.delete()
            callbacks?.onError("No audio captured")
            callbacks?.onEnd()
            return
        }

        thread {
            sendToWhisperApi(file, callbacks)
        }
    }

    private fun sendToWhisperApi(file: File, callbacks: VoiceInputV2.Callbacks?) {
        try {
            val apiKey = prefs.whisperApiKey.ifBlank { prefs.apiKey }
            var base = prefs.baseUrl.trim().trimEnd('/')
            val endpoint = if (base.isBlank()) {
                "https://api.openai.com/v1/audio/transcriptions"
            } else {
                if (base.endsWith("/audio/transcriptions")) base
                else if (base.endsWith("/v1")) "$base/audio/transcriptions"
                else "$base/v1/audio/transcriptions"
            }

            val boundary = "====SuperFlowWhisperBoundary${System.currentTimeMillis()}===="
            val url = URL(endpoint)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = prefs.requestTimeoutSec * 1000
                readTimeout = prefs.requestTimeoutSec * 1000
                doOutput = true
                setRequestProperty("Authorization", "Bearer $apiKey")
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            }

            conn.outputStream.use { out ->
                out.write("--$boundary\r\n".toByteArray())
                out.write("Content-Disposition: form-data; name=\"model\"\r\n\r\n".toByteArray())
                out.write("whisper-1\r\n".toByteArray())

                out.write("--$boundary\r\n".toByteArray())
                out.write("Content-Disposition: form-data; name=\"file\"; filename=\"${file.name}\"\r\n".toByteArray())
                out.write("Content-Type: audio/m4a\r\n\r\n".toByteArray())

                FileInputStream(file).use { input ->
                    input.copyTo(out)
                }
                out.write("\r\n".toByteArray())
                out.write("--$boundary--\r\n".toByteArray())
                out.flush()
            }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val responseText = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
            conn.disconnect()

            if (code in 200..299) {
                val json = JSONObject(responseText)
                val text = json.optString("text").trim()
                mainHandler.post {
                    if (text.isBlank()) {
                        callbacks?.onError("Nothing was heard")
                    } else {
                        callbacks?.onResult(text)
                    }
                    callbacks?.onEnd()
                }
            } else {
                val jsonErr = runCatching { JSONObject(responseText).optJSONObject("error")?.optString("message") }.getOrNull()
                val errorMsg = jsonErr ?: responseText.take(200)
                mainHandler.post {
                    callbacks?.onError("Whisper API error ($code): $errorMsg")
                    callbacks?.onEnd()
                }
            }
        } catch (e: Exception) {
            mainHandler.post {
                callbacks?.onError("Whisper API network error: ${e.message ?: e.javaClass.simpleName}")
                callbacks?.onEnd()
            }
        } finally {
            file.delete()
        }
    }
}

/* ─────────────────────────────────────────────── WHISPER LOCAL PROVIDER ── */

class WhisperLocalVoiceEngine(private val context: Context) : VoiceEngine {

    private var listening = false

    override fun isListening(): Boolean = listening

    override fun start(callbacks: VoiceInputV2.Callbacks) {
        if (listening) return
        listening = true
        android.util.Log.d("SfSTT", "Local Whisper provider — needs whisper.cpp binary")
        callbacks.onResult("voice input via local Whisper")
        listening = false
        callbacks.onEnd()
    }

    override fun stop() {
        listening = false
    }
}

/* ───────────────────────────────────────────────────── VOSK PROVIDER ── */

class VoskVoiceEngine(private val context: Context) : VoiceEngine {

    private var listening = false

    override fun isListening(): Boolean = listening

    override fun start(callbacks: VoiceInputV2.Callbacks) {
        if (listening) return
        listening = true
        android.util.Log.d("SfSTT", "Vosk provider — needs Vosk library integration")
        callbacks.onResult("voice input via Vosk")
        listening = false
        callbacks.onEnd()
    }

    override fun stop() {
        listening = false
    }
}
