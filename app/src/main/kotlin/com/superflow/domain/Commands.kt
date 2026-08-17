package com.superflow.domain

import android.content.Context
import com.superflow.data.*
import com.superflow.util.Dates
import com.superflow.util.extractJson
import com.superflow.util.jsonOf
import com.superflow.util.string
import com.superflow.util.stringOrNull
import org.json.JSONObject

/**
 * The shared command bus.
 *
 * Manual UI actions and AI tool calls both create the same typed command,
 * receive the same validation, write the same audit entry and share the same
 * undo behaviour. Nothing may mutate domain state except through here.
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

/** Execution context handed to a capability. */
class Ctx(
    val repo: Repo,
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

class CommandBus private constructor(context: Context) {

    val repo: Repo = Repo.get(context)
    private val appContext = context.applicationContext
    private val listeners = ArrayList<() -> Unit>()

    companion object {
        @Volatile private var instance: CommandBus? = null
        fun get(context: Context): CommandBus =
            instance ?: synchronized(this) { instance ?: CommandBus(context).also { instance = it } }
    }

    fun addListener(l: () -> Unit) { listeners.add(l) }
    fun removeListener(l: () -> Unit) { listeners.remove(l) }
    private fun notifyChanged() { for (l in ArrayList(listeners)) runCatching { l() } }

    /* -------------------------------------------------------------- registry */

    val capabilities: List<Capability> by lazy { Capabilities.all() }

    fun capability(name: String): Capability? =
        capabilities.firstOrNull { it.name.equals(name, ignoreCase = true) }

    /** Machine-readable manifest, used by the AI prompt and Settings. */
    fun manifest(): String {
        val sb = StringBuilder()
        for (c in capabilities) {
            sb.append("- ").append(c.name).append("(")
            sb.append(c.args.joinToString(", ") { it.first })
            sb.append("): ").append(c.summary).append('\n')
        }
        return sb.toString()
    }

    /* ------------------------------------------------------------- execution */

    fun execute(
        name: String,
        args: JSONObject = JSONObject(),
        actor: Actor = Actor.USER,
        groupId: String? = null
    ): CommandResult {
        val cap = capability(name)
            ?: return CommandResult.fail("Unknown command: $name")
        val ctx = Ctx(repo, args, actor, groupId, this)
        val result = try {
            cap.run(ctx)
        } catch (e: Exception) {
            CommandResult.fail("${cap.name} failed: ${e.message ?: e.javaClass.simpleName}")
        }
        if (result.ok) notifyChanged()
        return result
    }

    fun executeJson(text: String, actor: Actor = Actor.AI, groupId: String? = null): CommandResult {
        val obj = extractJson(text) ?: return CommandResult.fail("No JSON command found")
        val name = obj.string("command").ifBlank { obj.string("name") }
        val args = obj.optJSONObject("args") ?: JSONObject()
        return execute(name, args, actor, groupId)
    }

    /** Records an entry in the shared Activity trail and returns its id. */
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

    /** Reverses one recorded action. Undo entries are themselves not undoable. */
    fun undo(entry: AuditEntry): CommandResult {
        if (entry.undone) return CommandResult.fail("Already undone")
        if (entry.undoPayload.isBlank()) return CommandResult.fail("This action cannot be undone")
        val undo = extractJson(entry.undoPayload) ?: return CommandResult.fail("Undo data unreadable")
        val res = applyUndo(undo)
        if (res.ok) {
            repo.markUndone(entry.id)
            record(Actor.USER, "undo", "Undid: ${entry.summary}")
            notifyChanged()
        }
        return res
    }

    /** Reverses every action in a group, newest first. */
    fun undoGroup(groupId: String): CommandResult {
        val entries = repo.audit(1000).filter { it.groupId == groupId && !it.undone }
        if (entries.isEmpty()) return CommandResult.fail("Nothing to undo in this group")
        var done = 0
        for (e in entries) if (undo(e).ok) done++
        notifyChanged()
        return CommandResult(done > 0, "Undid $done of ${entries.size} actions")
    }

    private fun applyUndo(undo: JSONObject): CommandResult {
        val kind = undo.string("kind")
        val table = undo.string("table")
        val id = undo.string("id")
        return when (kind) {
            "deleteRow" -> {
                deleteRow(table, id)
                CommandResult(true, "Reverted")
            }
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

    /** Snapshot of everything, used for Full Control safety snapshots + export. */
    fun snapshot(): JSONObject = Serial.exportAll(repo)

    fun restoreSnapshot(json: JSONObject): CommandResult {
        return try {
            Serial.importAll(repo, json)
            notifyChanged()
            CommandResult(true, "Snapshot restored")
        } catch (e: Exception) {
            CommandResult.fail("Restore failed: ${e.message}")
        }
    }

    fun context(): Context = appContext
}

/** Convenience for capabilities that need a "today" default. */
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

fun undoDelete(table: String, id: String): JSONObject = jsonOf("kind" to "deleteRow", "table" to table, "id" to id)

fun undoRestore(table: String, row: JSONObject): JSONObject =
    jsonOf("kind" to "restoreRow", "table" to table, "row" to row)
