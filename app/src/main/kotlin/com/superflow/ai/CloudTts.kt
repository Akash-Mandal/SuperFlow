package com.superflow.ai

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import com.superflow.data.Prefs
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cloud text-to-speech over an OpenAI-compatible `/v1/audio/speech` endpoint.
 *
 * System TTS ([SfTextToSpeech]) stays the default: offline, instant, free.
 * This is the upgrade path — a natural voice for coach read-aloud — using
 * the same base URL + API key as the Main Brain unless overridden.
 */
object CloudTts {

    private var player: MediaPlayer? = null
    private val main = Handler(Looper.getMainLooper())

    @Volatile private var generation = 0

    fun speak(context: Context, prefs: Prefs, text: String, onDone: () -> Unit = {}) {
        if (!prefs.ttsEnabled || text.isBlank()) return
        val myGeneration = ++generation
        stopLocked()
        Thread {
            try {
                val file = synthesize(context, prefs, text) ?: run {
                    main.post(onDone)
                    return@Thread
                }
                if (myGeneration != generation) return@Thread
                main.post {
                    if (myGeneration != generation) return@post
                    try {
                        stopLocked()
                        player = MediaPlayer().apply {
                            setDataSource(file.absolutePath)
                            setOnCompletionListener { main.post(onDone) }
                            prepare()
                            start()
                        }
                    } catch (_: Exception) {
                        onDone()
                    }
                }
            } catch (_: Exception) {
                main.post(onDone)
            }
        }.start()
    }

    fun stop() {
        generation++
        main.post { stopLocked() }
    }

    private fun stopLocked() {
        try {
            player?.stop()
            player?.release()
        } catch (_: Exception) { }
        player = null
    }

    private fun synthesize(context: Context, prefs: Prefs, text: String): File? {
        val base = prefs.baseUrl.trim().trimEnd('/')
        if (base.isBlank()) return null
        val urlStr = if (base.contains("/v1")) "$base/audio/speech" else "$base/v1/audio/speech"
        val voice = prefs.ttsVoice.ifBlank { "alloy" }
        val speed = (prefs.ttsSpeechRate / 100.0).coerceIn(0.25, 4.0)
        val payload = JSONObject()
            .put("model", prefs.ttsModel.ifBlank { "tts-1" })
            .put("input", text.take(4000))
            .put("voice", voice)
            .put("speed", speed)
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = prefs.requestTimeoutSec * 1000
            readTimeout = prefs.requestTimeoutSec * 1000 + 30_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer ${prefs.apiKey}")
            if (prefs.organizationId.isNotBlank()) {
                setRequestProperty("OpenAI-Organization", prefs.organizationId)
            }
        }
        conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
        if (conn.responseCode !in 200..299) return null
        val bytes = (if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream).readBytes()
        if (bytes.size < 1024) return null
        val file = File(context.cacheDir, "sf_tts_${System.currentTimeMillis()}.mp3")
        file.writeBytes(bytes)
        return file
    }
}
