package com.superflow.ai

import android.content.Context
import com.superflow.data.Prefs
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object ModelCatalog {

    data class FetchResult(val models: List<String>, val fromCache: Boolean, val error: String? = null)

    private const val CACHE_FILE = "model_cache.json"
    private const val CACHE_MAX_AGE_MS = 24 * 60 * 60 * 1000L

    fun fetchModels(context: Context, prefs: Prefs): FetchResult {
        val cached = readCache(context)
        if (cached != null && System.currentTimeMillis() - cached.second < CACHE_MAX_AGE_MS && cached.first.isNotEmpty()) {
            if (!prefs.cloudReady()) return FetchResult(cached.first, true)
        }
        if (!prefs.cloudReady()) {
            return FetchResult(cached?.first ?: emptyList(), true, if (cached == null) "No Cloud configured" else null)
        }
        val result = try {
            fetchRemote(prefs)
        } catch (e: Exception) {
            return FetchResult(cached?.first ?: emptyList(), true, e.message ?: "Network error")
        }
        if (result.isNotEmpty()) writeCache(context, result)
        return FetchResult(result, false)
    }

    fun fetchRemote(prefs: Prefs): List<String> {
        val base = prefs.baseUrl.trim().trimEnd('/')
        var urlStr = base
        if (!urlStr.contains("/v1")) urlStr = "$urlStr/v1"
        urlStr = "$urlStr/models"
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = prefs.requestTimeoutSec * 1000
            readTimeout = prefs.requestTimeoutSec * 1000
            setRequestProperty("Authorization", "Bearer ${prefs.apiKey}")
            if (prefs.organizationId.isNotBlank()) setRequestProperty("OpenAI-Organization", prefs.organizationId)
            if (prefs.customHeaders.isNotBlank()) {
                for (line in prefs.customHeaders.lines()) {
                    val parts = line.split(":", limit = 2)
                    if (parts.size == 2) setRequestProperty(parts[0].trim(), parts[1].trim())
                }
            }
        }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
        conn.disconnect()
        if (code !in 200..299) throw RuntimeException("Provider error $code: ${text.take(200)}")
        val root = JSONObject(text)
        val data = root.optJSONArray("data") ?: root.optJSONArray("models") ?: JSONArray()
        val out = mutableListOf<String>()
        for (i in 0 until data.length()) {
            val obj = data.optJSONObject(i) ?: continue
            val id = obj.optString("id").ifBlank { obj.optString("name") }
            if (id.isNotBlank()) out.add(id)
        }
        if (out.isEmpty()) {
            val single = root.optString("id")
            if (single.isNotBlank()) out.add(single)
        }
        return out.sorted()
    }

    private fun readCache(context: Context): Pair<List<String>, Long>? = try {
        val f = java.io.File(context.filesDir, CACHE_FILE)
        if (!f.exists()) return null
        val j = JSONObject(f.readText())
        val arr = j.optJSONArray("models") ?: return null
        val ts = j.optLong("ts", 0)
        val list = mutableListOf<String>()
        for (i in 0 until arr.length()) list.add(arr.getString(i))
        Pair(list, ts)
    } catch (_: Exception) { null }

    private fun writeCache(context: Context, models: List<String>) = try {
        val f = java.io.File(context.filesDir, CACHE_FILE)
        val j = JSONObject().put("models", JSONArray(models)).put("ts", System.currentTimeMillis())
        f.writeText(j.toString())
    } catch (_: Exception) { }
}
