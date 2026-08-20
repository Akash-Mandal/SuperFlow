package com.superflow.ai

import com.superflow.data.Prefs
import com.superflow.data.Repository
import com.superflow.domain.Insights
import com.superflow.core.time.SfTime
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
    fun buildContext(repo: Repository, prefs: Prefs): String {
        val maxChars = prefs.maxContextChars
        val sb = StringBuilder()
        val today = repo.clock.today()
        val iso = SfTime.format(today)
        sb.append("Today is ${SfTime.humanDay(today)} ($iso), " +
                "time ${SfTime.formatTime(repo.clock.nowTime())}, zone ${repo.clock.zone().id}.\n")
        if (prefs.contextIncludeHabits) {
            repo.identities().takeIf { it.isNotEmpty() }?.let { list ->
                sb.append("\nIdentities:\n")
                list.forEach { sb.append("- ${it.statement} [id=${it.id}]\n") }
            }
            repo.goals().takeIf { it.isNotEmpty() }?.let { list ->
                sb.append("\nGoals:\n")
                list.forEach { sb.append("- ${it.title} [id=${it.id}]\n") }
            }
            repo.systems().takeIf { it.isNotEmpty() }?.let { list ->
                sb.append("\nSystems:\n")
                list.forEach { sb.append("- ${it.title} [id=${it.id}]\n") }
            }
            repo.habits().takeIf { it.isNotEmpty() }?.let { list ->
                sb.append("\nHabits:\n")
                list.forEach { h ->
                    val ci = repo.checkIn(h.id, iso)
                    sb.append("- ${h.title} [id=${h.id}] tiny=\"${h.tinyStart}\" " +
                            "time=${h.cueTime.ifBlank { "-" }} today=${ci?.result?.name ?: "open"}\n")
                }
            }
            repo.focusFor(iso).takeIf { it.isNotEmpty() }?.let { list ->
                sb.append("\nDaily Focus: ")
                sb.append(list.joinToString(", ") { "${it.title}${if (it.done) " (done)" else ""}" })
                sb.append('\n')
            }
        }
        if (prefs.contextIncludeInsights) {
            sb.append("\nInsights:\n").append(Insights.summaryText(repo, 30)).append('\n')
        }
        if (prefs.contextIncludeReviews) {
            val reviews = repo.reviews().takeLast(3)
            if (reviews.isNotEmpty()) {
                sb.append("\nRecent reviews:\n")
                reviews.forEach { r ->
                    sb.append("- ${r.periodLabel} (${r.kind.name.lowercase()}): ")
                    if (r.systemChange.isNotBlank()) sb.append("changed: ${r.systemChange}; ")
                    if (r.whatWorked.isNotBlank()) sb.append("worked: ${r.whatWorked.take(100)}")
                    sb.append('\n')
                }
            }
        }
        if (prefs.contextIncludeObstacles) {
            val obstacles = repo.habits().flatMap { h ->
                repo.obstacles(h.id).map { o -> "${h.title}: if ${o.ifText} then ${o.thenText}" }
            }
            if (obstacles.isNotEmpty()) {
                sb.append("\nObstacle plans:\n")
                obstacles.take(10).forEach { sb.append("- $it\n") }
            }
        }
        if (prefs.contextIncludeFlows) {
            val flows = repo.flows()
            if (flows.isNotEmpty()) {
                sb.append("\nRoutines/Flows:\n")
                flows.forEach { f ->
                    val steps = repo.flowSteps(f.id).joinToString(" → ") { it.title }
                    sb.append("- ${f.title}: $steps\n")
                }
            }
        }
        if (prefs.contextIncludeMemory) {
            val memories = repo.memories()
                .sortedByDescending { it.importance * it.accessCount }
                .take(10)
            if (memories.isNotEmpty()) {
                sb.append("\nThings you've told me to remember:\n")
                memories.forEach { sb.append("- [${it.category}] ${it.content}\n") }
            }
            if (prefs.memoryNotes.isNotBlank()) {
                sb.append("\nUser notes to remember:\n").append(prefs.memoryNotes).append('\n')
            }
        }
        // Explicit instructions (always included when set)
        if (prefs.aiInstructions.isNotBlank()) {
            sb.append("\nExplicit instructions from the user (highest priority):\n")
                .append(prefs.aiInstructions).append('\n')
        }
        // Local structured memory
        if (prefs.aiLocalMemory.isNotBlank()) {
            sb.append("\nFacts the user wants you to remember:\n")
                .append(prefs.aiLocalMemory).append('\n')
        }
        // Truncate to max context chars
        val result = sb.toString()
        return if (result.length > maxChars) result.take(maxChars) + "\n[truncated]" else result
    }

    fun systemPrompt(prefs: Prefs): String {
        // Custom system prompt override
        if (prefs.customSystemPrompt.isNotBlank()) {
            val base = prefs.customSystemPrompt
            return if (prefs.systemPromptSuffix.isNotBlank()) "$base\n\n${prefs.systemPromptSuffix}" else base
        }

        val autonomy = if (prefs.fullControlActive())
            """
            FULL CONTROL IS ACTIVE. The user has already granted blanket permission for every
            app-local capability, including bulk, destructive and settings operations. Do not ask
            for confirmation and do not ask clarifying questions when a reasonable default exists.
            Execute the work. Every action is snapshotted, audited and individually undoable.
            """.trimIndent()
        else
            "GUIDED MODE. Propose commands, but expect the user to confirm destructive work."

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
        """.trimIndent().let { base ->
            if (prefs.systemPromptSuffix.isNotBlank()) "$base\n\n${prefs.systemPromptSuffix}" else base
        }
    }

    /** Blocking HTTP call. Callers run this off the main thread. */
    fun chat(prefs: Prefs, systemText: String, history: List<Pair<String, String>>, userText: String): Reply {
        if (!prefs.cloudReady()) return Reply(false, "", "No Cloud Main Brain configured")
        if (prefs.budgetRemaining() <= 0)
            return Reply(false, "", "Monthly call budget reached. Raise it in AI Engine settings.")

        val url = buildUrl(prefs.baseUrl)
        val messages = JSONArray()
        messages.put(JSONObject().put("role", "system").put("content", systemText))
        for ((role, content) in history.takeLast(prefs.conversationHistoryLimit)) {
            messages.put(JSONObject().put("role", role).put("content", content))
        }
        messages.put(JSONObject().put("role", "user").put("content", userText))

        val payload = JSONObject()
            .put("model", prefs.model)
            .put("messages", messages)
            .put("temperature", prefs.temperature / 100.0)
            .put("max_tokens", prefs.maxTokens)

        // Top-p (nucleus sampling)
        if (prefs.topP < 100) payload.put("top_p", prefs.topP / 100.0)

        // Frequency and presence penalties
        if (prefs.frequencyPenalty != 0) payload.put("frequency_penalty", prefs.frequencyPenalty / 100.0)
        if (prefs.presencePenalty != 0) payload.put("presence_penalty", prefs.presencePenalty / 100.0)

        // Seed for reproducibility
        if (prefs.seed >= 0) payload.put("seed", prefs.seed)

        // Stop sequences
        if (prefs.stopSequences.isNotBlank()) {
            val stops = prefs.stopSequences.split(",").map { it.trim() }.filter { it.isNotBlank() }
            if (stops.size == 1) payload.put("stop", stops[0])
            else if (stops.size > 1) {
                val arr = JSONArray()
                stops.forEach { arr.put(it) }
                payload.put("stop", arr)
            }
        }

        // Response format
        when (prefs.responseFormat) {
            "json" -> payload.put("response_format", JSONObject().put("type", "json_object"))
            "text" -> payload.put("response_format", JSONObject().put("type", "text"))
        }

        // Streaming
        if (prefs.streamingEnabled) payload.put("stream", true)

        return try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = prefs.requestTimeoutSec * 1000
                readTimeout = prefs.requestTimeoutSec * 1000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer ${prefs.apiKey}")
                // Organization ID (OpenAI-specific but harmless for others)
                if (prefs.organizationId.isNotBlank()) {
                    setRequestProperty("OpenAI-Organization", prefs.organizationId)
                }
                // Custom headers (format: "Header-Name: value\nHeader-Name2: value2")
                if (prefs.customHeaders.isNotBlank()) {
                    for (line in prefs.customHeaders.lines()) {
                        val parts = line.split(":", limit = 2)
                        if (parts.size == 2) {
                            setRequestProperty(parts[0].trim(), parts[1].trim())
                        }
                    }
                }
            }

            // Request logging
            if (prefs.requestLoggingEnabled) {
                android.util.Log.d("SfAI", "→ ${payload.toString().take(2000)}")
            }

            var lastError: Exception? = null
            var code = 0
            var text = ""
            val maxAttempts = prefs.retryCount + 1

            for (attempt in 1..maxAttempts) {
                try {
                    OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload.toString()) }
                    code = conn.responseCode
                    val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                    text = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
                    conn.disconnect()

                    if (code in 200..299) break
                    if (code in listOf(429, 500, 502, 503, 504) && attempt < maxAttempts) {
                        Thread.sleep((attempt * 1000).toLong())  // Exponential backoff
                        continue
                    }
                    break
                } catch (e: Exception) {
                    lastError = e
                    if (attempt < maxAttempts) {
                        Thread.sleep((attempt * 1000).toLong())
                    }
                }
            }

            if (prefs.requestLoggingEnabled) {
                android.util.Log.d("SfAI", "← $code ${text.take(2000)}")
            }

            if (lastError != null && code == 0) {
                return Reply(false, "", "Network error after $maxAttempts attempts: ${lastError.message ?: lastError.javaClass.simpleName}")
            }

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

    fun testConnection(prefs: Prefs): Reply {
        if (prefs.baseUrl.isBlank()) return Reply(false, "", "Set a base URL first")
        if (prefs.apiKey.isBlank()) return Reply(false, "", "Set an API key first")
        val r = chat(prefs, "You are a connection test. Reply with the single word: ok",
            emptyList(), "ping")
        return if (r.ok) Reply(true, "Connected. Model replied: ${r.text.take(60).trim()}") else r
    }
}
