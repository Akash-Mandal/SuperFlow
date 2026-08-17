package com.superflow.ai

import com.superflow.data.Prefs
import com.superflow.data.Repo
import com.superflow.domain.Insights
import com.superflow.util.Dates
import com.superflow.util.extractJson
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cloud Main Brain adapter.
 *
 * Provider-neutral: any OpenAI-compatible chat completions endpoint works,
 * including local (llama.cpp, Ollama, LM Studio), LAN, or remote self-hosted
 * servers. The API key never enters a prompt, a log or an export.
 */
object MainBrain {

    data class Reply(val ok: Boolean, val text: String, val error: String? = null)

    /** Context Broker: assembles only the sections the user has permitted. */
    fun buildContext(repo: Repo, prefs: Prefs): String {
        val sb = StringBuilder()
        sb.append("Today is ${Dates.humanDay(Dates.today())} (${Dates.today()}), time ${Dates.nowTime()}.\n")
        if (prefs.contextIncludeHabits) {
            val identities = repo.identities()
            if (identities.isNotEmpty()) {
                sb.append("\nIdentities:\n")
                identities.forEach { sb.append("- ${it.statement} [id=${it.id}]\n") }
            }
            val goals = repo.goals()
            if (goals.isNotEmpty()) {
                sb.append("\nGoals:\n")
                goals.forEach { sb.append("- ${it.title} [id=${it.id}]\n") }
            }
            val systems = repo.systems()
            if (systems.isNotEmpty()) {
                sb.append("\nSystems:\n")
                systems.forEach { sb.append("- ${it.title} [id=${it.id}]\n") }
            }
            val habits = repo.habits()
            if (habits.isNotEmpty()) {
                sb.append("\nHabits:\n")
                habits.forEach { h ->
                    val ci = repo.checkIn(h.id, Dates.today())
                    sb.append("- ${h.title} [id=${h.id}] tiny=\"${h.tinyStart}\" " +
                            "time=${h.cueTime.ifBlank { "-" }} today=${ci?.result?.name ?: "open"}\n")
                }
            }
            val focus = repo.focusFor(Dates.today())
            if (focus.isNotEmpty()) {
                sb.append("\nDaily Focus: ")
                sb.append(focus.joinToString(", ") { "${it.title}${if (it.done) " (done)" else ""}" })
                sb.append('\n')
            }
        }
        if (prefs.contextIncludeInsights) {
            sb.append("\nInsights:\n").append(Insights.summaryText(repo, 30)).append('\n')
        }
        if (prefs.contextIncludeMemory && prefs.memoryNotes.isNotBlank()) {
            sb.append("\nUser notes to remember:\n").append(prefs.memoryNotes).append('\n')
        }
        return sb.toString()
    }

    fun systemPrompt(prefs: Prefs): String {
        val autonomy = if (prefs.fullControlActive())
            """
            FULL CONTROL IS ACTIVE. The user has already granted blanket permission for every
            app-local capability, including bulk, destructive and settings operations. Do not ask
            for confirmation and do not ask clarifying questions when a reasonable default exists.
            Execute the work. Every action is snapshotted, audited and individually undoable.
            """.trimIndent()
        else
            """
            GUIDED MODE. Propose commands, but expect the user to confirm destructive work.
            """.trimIndent()

        return """
            You are SuperFlow's assistant. SuperFlow is a calm personal-growth app built on
            identity-based habit change: identity -> goal -> system -> habit -> check-in -> review.

            Principles you must respect:
            - Systems over scoreboards. Never promise a fixed number of days to form a habit.
            - Every habit needs a Tiny Start that takes about two minutes.
            - A miss is data, not a moral failing. Recovery beats perfection; never miss twice.
            - Be warm, brief and concrete. No hype, no guilt, no urgency, no casino feedback.
            - Never claim you did something you did not do.

            $autonomy

            You control the app by emitting tool calls. To act, reply with ONLY a JSON object:
            {"reply": "<one short sentence for the user>", "commands": [{"command": "<name>", "args": {...}}]}

            If no action is needed, reply with:
            {"reply": "<your answer>", "commands": []}

            Available commands:
            ${Coordinator.toolCatalog()}

            Rules for commands:
            - Use habit ids from the context when you have them; otherwise pass the title in "habit".
            - Levels are TINY, MINIMUM, STANDARD or STRETCH.
            - "days" accepts daily, weekdays, weekends, or a list like "mon,wed,fri".
            - Daily Focus holds at most three actions.
            - When creating a habit always include a tinyStart.
            - You may emit several commands to complete a multi-step job in one turn.
        """.trimIndent()
    }

    /** Blocking HTTP call. Callers run this off the main thread. */
    fun chat(prefs: Prefs, systemText: String, history: List<Pair<String, String>>, userText: String): Reply {
        if (!prefs.cloudReady()) {
            return Reply(false, "", "No Cloud Main Brain configured")
        }
        if (prefs.budgetRemaining() <= 0) {
            return Reply(false, "", "Monthly call budget reached. Raise it in AI Engine settings.")
        }

        val url = buildUrl(prefs.baseUrl)
        val messages = JSONArray()
        messages.put(JSONObject().put("role", "system").put("content", systemText))
        for ((role, content) in history.takeLast(10)) {
            messages.put(JSONObject().put("role", role).put("content", content))
        }
        messages.put(JSONObject().put("role", "user").put("content", userText))

        val payload = JSONObject()
            .put("model", prefs.model)
            .put("messages", messages)
            .put("temperature", prefs.temperature / 100.0)
            .put("max_tokens", prefs.maxTokens)

        return try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = prefs.requestTimeoutSec * 1000
                readTimeout = prefs.requestTimeoutSec * 1000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer ${prefs.apiKey}")
            }
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload.toString()) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
            conn.disconnect()

            if (code !in 200..299) {
                val msg = extractJson(text)?.optJSONObject("error")?.optString("message")
                    ?: text.take(200)
                return Reply(false, "", "Provider error $code: $msg")
            }
            prefs.noteCall()
            val content = parseContent(text)
            if (content.isNullOrBlank()) Reply(false, "", "Empty response from provider")
            else Reply(true, content)
        } catch (e: Exception) {
            Reply(false, "", "Network error: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun parseContent(body: String): String? {
        val root = extractJson(body) ?: return null
        root.optJSONArray("choices")?.optJSONObject(0)?.let { choice ->
            choice.optJSONObject("message")?.optString("content")?.let { if (it.isNotBlank()) return it }
            choice.optString("text").let { if (it.isNotBlank()) return it }
        }
        // Some providers return {"content":[{"text":...}]}
        root.optJSONArray("content")?.optJSONObject(0)?.optString("text")?.let {
            if (it.isNotBlank()) return it
        }
        return null
    }

    private fun buildUrl(base: String): String {
        var b = base.trim().trimEnd('/')
        if (b.endsWith("/chat/completions")) return b
        if (!b.contains("/v1")) b = "$b/v1"
        return "$b/chat/completions"
    }

    /** Connection test used by AI Engine diagnostics. */
    fun testConnection(prefs: Prefs): Reply {
        if (prefs.baseUrl.isBlank()) return Reply(false, "", "Set a base URL first")
        if (prefs.apiKey.isBlank()) return Reply(false, "", "Set an API key first")
        val r = chat(prefs, "You are a connection test. Reply with the single word: ok",
            emptyList(), "ping")
        return if (r.ok) Reply(true, "Connected. Model replied: ${r.text.take(60).trim()}")
        else r
    }
}
