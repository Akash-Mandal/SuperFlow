package com.superflow.domain

import android.content.Context
import com.superflow.data.Repository
import com.superflow.data.model.AuditEntry
import com.superflow.data.model.newId
import com.superflow.util.Dates
import com.superflow.util.extractJson
import com.superflow.util.jsonOf
import com.superflow.util.string
import com.superflow.util.stringOrNull
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import org.json.JSONObject

/**
 * The shared command bus.
 *
 * Manual UI actions, notification actions, AI tool calls and Blueprint
 * execution all create the same typed command, receive the same validation,
 * write the same audit entry and share the same undo behaviour. Nothing may
 * mutate domain state except through here.
 */

enum class Actor { USER, AI, SYSTEM }

enum class Risk { LOW, MEDIUM, HIGH }

data class CommandResult(
    val ok: Boolean,
    val message: String,
    val auditId: String? = null,
    val data: JSONObject? = null
) {
    companion object {
        fun fail(message: String) = CommandResult(false, message)
    }
}

/** One registered capability: name, schema, risk, and how to run it. */
class Capability(
    val name: String,
    val summary: String,
    val args: List<Pair<String, String>>,
    val risk: Risk,
    val destructive: Boolean = false,
    val run: (Ctx) -> CommandResult
)

class Ctx(
    val repo: Repository,
    val args: JSONObject,
    val actor: Actor,
    val groupId: String?,
    val bus: CommandBus
) {
    fun str(key: String, def: String = ""): String = args.string(key, def)
    fun strOrNull(key: String): String? = args.stringOrNull(key)
    fun int(key: String, def: Int): Int = if (args.isNull(key)) def else args.optInt(key, def)
    fun dbl(key: String, def: Double): Double = if (args.isNull(key)) def else args.optDouble(key, def)
    fun bool(key: String, def: Boolean): Boolean = if (args.isNull(key)) def else args.optBoolean(key, def)
}

/** Emitted after every successful command so the UI can react (toasts, haptics). */
data class CommandEvent(val command: String, val actor: Actor, val result: CommandResult)

class CommandBus private constructor(context: Context) {

    val repo: Repository = Repository.get(context)
    private val appContext = context.applicationContext

    private val _events = MutableSharedFlow<CommandEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<CommandEvent> = _events

    companion object {
        @Volatile private var instance: CommandBus? = null
        fun get(context: Context): CommandBus =
            instance ?: synchronized(this) {
                instance ?: CommandBus(context.applicationContext).also { instance = it }
            }
    }

    fun context(): Context = appContext

    /* -------------------------------------------------------------- registry */

    val capabilities: List<Capability> by lazy { Capabilities.all() }

    fun capability(name: String): Capability? =
        capabilities.firstOrNull { it.name.equals(name, ignoreCase = true) }

    fun manifest(): String = capabilities.joinToString("\n") { c ->
        "- ${c.name}(${c.args.joinToString(", ") { it.first }}): ${c.summary}"
    }

    /* ------------------------------------------------------------- execution */

    fun execute(
        name: String,
        args: JSONObject = JSONObject(),
        actor: Actor = Actor.USER,
        groupId: String? = null
    ): CommandResult {
        val cap = capability(name) ?: return CommandResult.fail("Unknown command: $name")
        val ctx = Ctx(repo, args, actor, groupId, this)
        val result = try {
            cap.run(ctx)
        } catch (e: Exception) {
            CommandResult.fail("${cap.name} failed: ${e.message ?: e.javaClass.simpleName}")
        }
        if (result.ok) _events.tryEmit(CommandEvent(name, actor, result))
        return result
    }

    fun executeJson(text: String, actor: Actor = Actor.AI, groupId: String? = null): CommandResult {
        val obj = extractJson(text) ?: return CommandResult.fail("No JSON command found")
        val name = obj.string("command").ifBlank { obj.string("name") }
        val args = obj.optJSONObject("args") ?: JSONObject()
        return execute(name, args, actor, groupId)
    }

    fun record(
        actor: Actor,
        command: String,
        summary: String,
        payload: JSONObject? = null,
        undo: JSONObject? = null,
        groupId: String? = null
    ): String {
        val entry = AuditEntry(
            actor = actor.name,
            command = command,
            summary = summary,
            payload = payload?.toString() ?: "",
            undoPayload = undo?.toString() ?: "",
            groupId = groupId
        )
        repo.saveAudit(entry)
        return entry.id
    }

    /* ------------------------------------------------------------------ undo */

    fun undo(entry: AuditEntry): CommandResult {
        if (entry.undone) return CommandResult.fail("Already undone")
        if (entry.undoPayload.isBlank()) return CommandResult.fail("This action cannot be undone")
        val undo = extractJson(entry.undoPayload) ?: return CommandResult.fail("Undo data unreadable")
        val res = applyUndo(undo)
        if (res.ok) {
            repo.markUndone(entry.id)
            record(Actor.USER, "undo", "Undid: ${entry.summary}")
            _events.tryEmit(CommandEvent("undo", Actor.USER, res))
        }
        return res
    }

    fun undoGroup(groupId: String): CommandResult {
        val entries = repo.auditGroup(groupId).filter { !it.undone }
        if (entries.isEmpty()) return CommandResult.fail("Nothing to undo in this group")
        var done = 0
        for (e in entries) if (undo(e).ok) done++
        return CommandResult(done > 0, "Undid $done of ${entries.size} actions")
    }

    private fun applyUndo(undo: JSONObject): CommandResult {
        val kind = undo.string("kind")
        val table = undo.string("table")
        val id = undo.string("id")
        return when (kind) {
            "deleteRow" -> { deleteRow(table, id); CommandResult(true, "Reverted") }
            "restoreRow" -> {
                val row = undo.optJSONObject("row") ?: return CommandResult.fail("Missing row")
                restoreRow(table, row)
                CommandResult(true, "Restored")
            }
            "restoreRows" -> {
                val arr = undo.optJSONArray("rows") ?: return CommandResult.fail("Missing rows")
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { restoreRow(it.string("table", table), it) }
                }
                CommandResult(true, "Restored ${arr.length()} items")
            }
            "clearCheckIn" -> {
                repo.clearCheckIn(undo.string("habitId"), undo.string("date"))
                CommandResult(true, "Check-in cleared")
            }
            "noop" -> CommandResult(true, "Nothing to revert")
            else -> CommandResult.fail("Unsupported undo type")
        }
    }

    private fun deleteRow(table: String, id: String) {
        when (table) {
            "identity" -> repo.deleteIdentity(id)
            "goal" -> repo.deleteGoal(id)
            "sys" -> repo.deleteSystem(id)
            "habit" -> repo.deleteHabit(id)
            "focus" -> repo.deleteFocus(id)
            "obstacle" -> repo.deleteObstacle(id)
            "scorecard" -> repo.deleteScorecard(id)
            "flow" -> repo.deleteFlow(id)
            "flowstep" -> repo.deleteFlowStep(id)
            "review" -> repo.deleteReview(id)
            "bp_project" -> repo.deleteProject(id)
            "bp_source" -> repo.deleteSource(id)
        }
    }

    private fun restoreRow(table: String, row: JSONObject) {
        when (table) {
            "identity" -> repo.saveIdentity(Serial.identity(row))
            "goal" -> repo.saveGoal(Serial.goal(row))
            "sys" -> repo.saveSystem(Serial.system(row))
            "habit" -> repo.saveHabit(Serial.habit(row))
            "checkin" -> repo.saveCheckIn(Serial.checkIn(row))
            "focus" -> repo.saveFocus(Serial.focus(row))
            "obstacle" -> repo.saveObstacle(Serial.obstacle(row))
            "scorecard" -> repo.saveScorecard(Serial.scorecard(row))
            "flow" -> repo.saveFlow(Serial.flow(row))
            "flowstep" -> repo.saveFlowStep(Serial.flowStep(row))
            "review" -> repo.saveReview(Serial.review(row))
            "bp_project" -> repo.saveProject(Serial.project(row))
            "bp_source" -> repo.saveSource(Serial.source(row))
        }
    }

    fun snapshot(): JSONObject = Serial.exportAll(repo)

    fun restoreSnapshot(json: JSONObject): CommandResult = try {
        Serial.importAll(repo, json)
        CommandResult(true, "Snapshot restored")
    } catch (e: Exception) {
        CommandResult.fail("Restore failed: ${e.message}")
    }
}

/* ------------------------------------------------------------------ helpers */

fun Ctx.date(key: String = "date"): String {
    val raw = str(key).trim().lowercase()
    return when (raw) {
        "", "today" -> Dates.today()
        "yesterday" -> Dates.yesterday()
        "tomorrow" -> Dates.tomorrow()
        else -> raw
    }
}

fun okResult(message: String, data: JSONObject? = null, auditId: String? = null) =
    CommandResult(true, message, auditId, data)

fun undoDelete(table: String, id: String): JSONObject =
    jsonOf("kind" to "deleteRow", "table" to table, "id" to id)

fun undoRestore(table: String, row: JSONObject): JSONObject =
    jsonOf("kind" to "restoreRow", "table" to table, "row" to row)
