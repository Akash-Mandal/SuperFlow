package com.superflow.ai

import android.content.Context
import com.superflow.data.Prefs
import com.superflow.data.model.AiMessage
import com.superflow.data.model.newId
import com.superflow.domain.Actor
import com.superflow.domain.CommandBus
import com.superflow.domain.CommandResult
import com.superflow.util.extractJson
import com.superflow.util.objects
import com.superflow.util.string
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The agent runtime.
 *
 * Routing: Local Coordinator first for deterministic work, Cloud Main Brain for
 * everything else, deterministic rules as the universal fallback. The Policy
 * Engine — not the model — decides whether a command may run automatically.
 */
class Agent private constructor(context: Context) {

    private val app = context.applicationContext
    private val bus = CommandBus.get(app)
    private val prefs = Prefs.get(app)
    private val stopped = AtomicBoolean(false)

    data class Outcome(
        val reply: String,
        val actions: List<String> = emptyList(),
        val groupId: String? = null,
        val route: String = "local",
        val error: String? = null
    )

    companion object {
        @Volatile private var instance: Agent? = null
        fun get(context: Context): Agent =
            instance ?: synchronized(this) {
                instance ?: Agent(context.applicationContext).also { instance = it }
            }
    }

    fun stop() = stopped.set(true)
    fun resume() = stopped.set(false)
    fun isStopped(): Boolean = stopped.get()

    /* --------------------------------------------------------- policy engine */

    /**
     * Deterministic policy. Full Control means no repeated app-local
     * confirmations; anything outside a granted capability is still refused.
     */
    fun mayRun(commandName: String): Pair<Boolean, String> {
        val cap = bus.capability(commandName) ?: return false to "Unknown command"
        if (!prefs.aiEnabled) return false to "AI is switched off in Settings"
        if (cap.destructive && !prefs.allowDestructive)
            return false to "Destructive actions are not permitted for AI"
        if (prefs.fullControlActive()) return true to "Full Control"
        if (cap.destructive) return false to "Needs confirmation in Guided mode"
        return true to "Guided"
    }

    /* ----------------------------------------------------------------- entry */

    suspend fun send(userText: String): Outcome = withContext(Dispatchers.IO) {
        resume()
        bus.repo.saveMessage(AiMessage(role = "user", text = userText))
        val outcome = try {
            handle(userText)
        } catch (e: Exception) {
            Outcome("Something went wrong: ${e.message ?: e.javaClass.simpleName}", error = e.message)
        }
        bus.repo.saveMessage(AiMessage(role = "assistant", text = outcome.reply, meta = outcome.route))
        outcome
    }

    private fun handle(userText: String): Outcome {
        // 1. Local Coordinator: deterministic, offline, instant.
        val plan = Coordinator.interpret(userText, bus.repo)
        if (plan != null && plan.confidence >= 0.8) {
            if (plan.command == "noop") return Outcome(plan.reply ?: "", route = "local")
            return runCommands(listOf(plan.command to plan.args), plan.reply, "local")
        }

        // 2. Cloud Main Brain, when configured and permitted.
        if (prefs.cloudReady() && !stopped.get()) {
            val system = MainBrain.systemPrompt(prefs) + "\n\nCurrent app state:\n" +
                    MainBrain.buildContext(bus.repo, prefs)
            val history = bus.repo.messages(30)
                .filter { it.role == "user" || it.role == "assistant" }
                .takeLast(8)
                .map { it.role to it.text }
            val reply = MainBrain.chat(prefs, system, history, userText)
            if (reply.ok) return interpretCloud(reply.text)
            val fb = fallback(userText, plan)
            return fb.copy(error = reply.error, route = "local-fallback")
        }

        // 3. Deterministic fallback.
        return fallback(userText, plan)
    }

    private fun fallback(userText: String, plan: Coordinator.Plan?): Outcome {
        if (plan != null) {
            if (plan.command == "noop") return Outcome(plan.reply ?: "", route = "local")
            return runCommands(listOf(plan.command to plan.args), plan.reply, "local")
        }
        val hint = if (!prefs.cloudReady())
            "\n\nFor open-ended requests, connect a Cloud Main Brain in Settings › AI Engine. " +
                    "Everything below works offline right now."
        else ""
        return Outcome(
            "I could not map that to an action.\n\n" + Coordinator.coachCard(bus.repo) + hint,
            route = "local"
        )
    }

    private fun interpretCloud(text: String): Outcome {
        val obj = extractJson(text) ?: return Outcome(text.trim(), route = "cloud")
        val reply = obj.string("reply").ifBlank { obj.string("message") }
        val commands = obj.optJSONArray("commands")?.objects()
            ?: obj.optJSONArray("actions")?.objects()
            ?: emptyList()
        if (commands.isEmpty()) return Outcome(reply.ifBlank { text.trim() }, route = "cloud")
        val pairs = commands.mapNotNull { c ->
            val name = c.string("command").ifBlank { c.string("name") }
            if (name.isBlank()) null else name to (c.optJSONObject("args") ?: JSONObject())
        }
        return runCommands(pairs, reply, "cloud")
    }

    /* ------------------------------------------------------------- execution */

    private fun runCommands(
        commands: List<Pair<String, JSONObject>>,
        replyOverride: String?,
        route: String
    ): Outcome {
        if (commands.isEmpty()) return Outcome(replyOverride ?: "Nothing to do.", route = route)

        val group = if (commands.size > 1) newId() else null
        val needsSnapshot = prefs.autoSnapshot && (commands.size > 1 ||
                commands.any { bus.capability(it.first)?.destructive == true })
        if (needsSnapshot) Snapshots.save(app, bus)

        val actions = ArrayList<String>()
        val blocked = ArrayList<String>()
        var lastMessage = ""

        for ((name, args) in commands) {
            if (stopped.get()) { blocked.add("stopped before $name"); break }
            val (allowed, why) = mayRun(name)
            if (!allowed) { blocked.add("$name blocked: $why"); continue }
            val res: CommandResult = bus.execute(name, args, Actor.AI, group)
            if (res.ok) { actions.add(res.message); lastMessage = res.message }
            else blocked.add("$name failed: ${res.message}")
        }

        val sb = StringBuilder()
        if (!replyOverride.isNullOrBlank()) sb.append(replyOverride.trim())
        else if (lastMessage.isNotBlank()) sb.append(lastMessage)

        if (actions.isNotEmpty()) {
            if (sb.isNotEmpty()) sb.append("\n\n")
            sb.append(if (actions.size == 1) "Done: ${actions.first()}"
            else "Done ${actions.size} things:\n" + actions.joinToString("\n") { "· $it" })
        }
        if (blocked.isNotEmpty()) {
            if (sb.isNotEmpty()) sb.append("\n\n")
            sb.append("Not completed:\n" + blocked.joinToString("\n") { "· $it" })
        }
        if (sb.isEmpty()) sb.append("Nothing changed.")

        return Outcome(sb.toString(), actions, group, route)
    }

    /** Verification pass: confirms real app state rather than trusting model text. */
    fun verify(): String {
        val counts = bus.repo.counts()
        return "Verified against the database: " +
                counts.entries.joinToString(", ") { "${it.key}=${it.value}" }
    }
}
