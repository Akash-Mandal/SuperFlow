package com.superflow.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.superflow.util.Dates

/**
 * Single repository over the local database. Both the manual UI and the AI
 * tool layer call the same functions, so behaviour cannot drift between them.
 */
class Repo private constructor(context: Context) {

    private val helper = Db(context)
    private val db: SQLiteDatabase get() = helper.writableDatabase

    companion object {
        @Volatile private var instance: Repo? = null
        fun get(context: Context): Repo =
            instance ?: synchronized(this) { instance ?: Repo(context).also { instance = it } }
    }

    /* ----------------------------------------------------------- identity */

    fun identities(includeArchived: Boolean = false): List<Identity> =
        db.rawQuery(
            "SELECT * FROM identity ${if (includeArchived) "" else "WHERE status<>'ARCHIVED'"} ORDER BY createdAt",
            null
        ).mapAll { c ->
            Identity(c.str("id"), c.str("statement"), LifeArea.from(c.str("lifeArea")),
                Status.valueOf(c.str("status").ifBlank { "ACTIVE" }), c.lng("createdAt"))
        }

    fun identity(id: String?): Identity? =
        if (id == null) null else identities(true).firstOrNull { it.id == id }

    fun saveIdentity(i: Identity) {
        db.insertWithOnConflict("identity", null, cv(
            "id" to i.id, "statement" to i.statement, "lifeArea" to i.lifeArea.name,
            "status" to i.status.name, "createdAt" to i.createdAt
        ), SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deleteIdentity(id: String) {
        db.delete("identity", "id=?", arrayOf(id))
    }

    /* --------------------------------------------------------------- goal */

    fun goals(): List<Goal> = db.rawQuery("SELECT * FROM goal ORDER BY createdAt", null).mapAll { c ->
        Goal(c.str("id"), c.strOrNull("identityId"), c.str("title"), c.str("why"),
            c.str("outcomeMetric"), c.dblOrNull("targetValue"), c.lngOrNull("targetDate"),
            GoalStatus.valueOf(c.str("status").ifBlank { "ACTIVE" }), c.lng("createdAt"))
    }

    fun goal(id: String?): Goal? = if (id == null) null else goals().firstOrNull { it.id == id }

    fun saveGoal(g: Goal) {
        db.insertWithOnConflict("goal", null, cv(
            "id" to g.id, "identityId" to g.identityId, "title" to g.title, "why" to g.why,
            "outcomeMetric" to g.outcomeMetric, "targetValue" to g.targetValue,
            "targetDate" to g.targetDate, "status" to g.status.name, "createdAt" to g.createdAt
        ), SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deleteGoal(id: String) {
        db.delete("goal", "id=?", arrayOf(id))
    }

    /* ------------------------------------------------------------- system */

    fun systems(): List<Sys> = db.rawQuery("SELECT * FROM sys ORDER BY createdAt", null).mapAll { c ->
        Sys(c.str("id"), c.strOrNull("goalId"), c.str("title"), c.str("description"),
            Status.valueOf(c.str("status").ifBlank { "ACTIVE" }), c.lng("createdAt"))
    }

    fun system(id: String?): Sys? = if (id == null) null else systems().firstOrNull { it.id == id }

    fun saveSystem(s: Sys) {
        db.insertWithOnConflict("sys", null, cv(
            "id" to s.id, "goalId" to s.goalId, "title" to s.title, "description" to s.description,
            "status" to s.status.name, "createdAt" to s.createdAt
        ), SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deleteSystem(id: String) {
        db.delete("sys", "id=?", arrayOf(id))
    }

    /* -------------------------------------------------------------- habit */

    private fun readHabit(c: android.database.Cursor) = Habit(
        id = c.str("id"), systemId = c.strOrNull("systemId"), identityId = c.strOrNull("identityId"),
        title = c.str("title"), mode = HabitMode.valueOf(c.str("mode").ifBlank { "BUILD" }),
        trackType = TrackType.valueOf(c.str("trackType").ifBlank { "BINARY" }),
        targetCount = c.int("targetCount"), unit = c.str("unit"),
        cueTime = c.str("cueTime"), cuePlace = c.str("cuePlace"),
        anchorHabitId = c.strOrNull("anchorHabitId"), anchorText = c.str("anchorText"),
        benefit = c.str("benefit"), temptationBundle = c.str("temptationBundle"), reframe = c.str("reframe"),
        tinyStart = c.str("tinyStart"), minimumVersion = c.str("minimumVersion"),
        standardVersion = c.str("standardVersion"), stretchVersion = c.str("stretchVersion"),
        frictionPlan = c.str("frictionPlan"), environmentPrep = c.str("environmentPrep"),
        reward = c.str("reward"), recoveryPlan = c.str("recoveryPlan"),
        daysMask = c.int("daysMask"), reminderEnabled = c.bool("reminderEnabled"),
        protectedRoutine = c.bool("protectedRoutine"), orderIndex = c.int("orderIndex"),
        status = Status.valueOf(c.str("status").ifBlank { "ACTIVE" }), createdAt = c.lng("createdAt")
    )

    fun habits(includeArchived: Boolean = false): List<Habit> =
        db.rawQuery(
            "SELECT * FROM habit ${if (includeArchived) "" else "WHERE status<>'ARCHIVED'"} ORDER BY orderIndex, createdAt",
            null
        ).mapAll { readHabit(it) }

    fun habit(id: String?): Habit? =
        if (id == null) null
        else db.rawQuery("SELECT * FROM habit WHERE id=?", arrayOf(id)).mapAll { readHabit(it) }.firstOrNull()

    /** Fuzzy lookup used by AI commands and search: exact, then prefix, then contains. */
    fun findHabit(query: String): Habit? {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return null
        val all = habits(true)
        return all.firstOrNull { it.title.lowercase() == q }
            ?: all.firstOrNull { it.title.lowercase().startsWith(q) }
            ?: all.firstOrNull { it.title.lowercase().contains(q) }
            ?: all.firstOrNull { q.contains(it.title.lowercase()) }
    }

    fun saveHabit(h: Habit) {
        db.insertWithOnConflict("habit", null, cv(
            "id" to h.id, "systemId" to h.systemId, "identityId" to h.identityId, "title" to h.title,
            "mode" to h.mode.name, "trackType" to h.trackType.name, "targetCount" to h.targetCount,
            "unit" to h.unit, "cueTime" to h.cueTime, "cuePlace" to h.cuePlace,
            "anchorHabitId" to h.anchorHabitId, "anchorText" to h.anchorText,
            "benefit" to h.benefit, "temptationBundle" to h.temptationBundle, "reframe" to h.reframe,
            "tinyStart" to h.tinyStart, "minimumVersion" to h.minimumVersion,
            "standardVersion" to h.standardVersion, "stretchVersion" to h.stretchVersion,
            "frictionPlan" to h.frictionPlan, "environmentPrep" to h.environmentPrep,
            "reward" to h.reward, "recoveryPlan" to h.recoveryPlan,
            "daysMask" to h.daysMask, "reminderEnabled" to h.reminderEnabled,
            "protectedRoutine" to h.protectedRoutine, "orderIndex" to h.orderIndex,
            "status" to h.status.name, "createdAt" to h.createdAt
        ), SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deleteHabit(id: String) {
        db.delete("habit", "id=?", arrayOf(id))
        db.delete("checkin", "habitId=?", arrayOf(id))
        db.delete("obstacle", "habitId=?", arrayOf(id))
        db.delete("focus", "habitId=?", arrayOf(id))
    }

    fun habitsForDay(date: String): List<Habit> {
        val dow = Dates.isoDayOfWeek(date)
        return habits().filter { it.status == Status.ACTIVE && it.runsOn(dow) }
    }

    /* ------------------------------------------------------------ checkin */

    private fun readCheckIn(c: android.database.Cursor) = CheckIn(
        c.str("id"), c.str("habitId"), c.str("date"),
        CheckInResult.valueOf(c.str("result").ifBlank { "DONE" }),
        Level.from(c.str("level")), c.dbl("amount"), c.str("note"), c.lng("createdAt")
    )

    fun checkIns(): List<CheckIn> =
        db.rawQuery("SELECT * FROM checkin ORDER BY date DESC", null).mapAll { readCheckIn(it) }

    fun checkInsFor(date: String): List<CheckIn> =
        db.rawQuery("SELECT * FROM checkin WHERE date=?", arrayOf(date)).mapAll { readCheckIn(it) }

    fun checkIn(habitId: String, date: String): CheckIn? =
        db.rawQuery("SELECT * FROM checkin WHERE habitId=? AND date=?", arrayOf(habitId, date))
            .mapAll { readCheckIn(it) }.firstOrNull()

    fun checkInsOf(habitId: String): List<CheckIn> =
        db.rawQuery("SELECT * FROM checkin WHERE habitId=? ORDER BY date DESC", arrayOf(habitId))
            .mapAll { readCheckIn(it) }

    fun saveCheckIn(ci: CheckIn) {
        db.delete("checkin", "habitId=? AND date=?", arrayOf(ci.habitId, ci.date))
        db.insertWithOnConflict("checkin", null, cv(
            "id" to ci.id, "habitId" to ci.habitId, "date" to ci.date, "result" to ci.result.name,
            "level" to ci.level.name, "amount" to ci.amount, "note" to ci.note, "createdAt" to ci.createdAt
        ), SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun clearCheckIn(habitId: String, date: String) {
        db.delete("checkin", "habitId=? AND date=?", arrayOf(habitId, date))
    }

    /* -------------------------------------------------------------- focus */

    fun focusFor(date: String): List<FocusItem> =
        db.rawQuery("SELECT * FROM focus WHERE date=? ORDER BY orderIndex", arrayOf(date)).mapAll { c ->
            FocusItem(c.str("id"), c.str("date"), c.strOrNull("habitId"), c.str("title"),
                c.bool("done"), c.int("orderIndex"))
        }

    fun saveFocus(f: FocusItem) {
        db.insertWithOnConflict("focus", null, cv(
            "id" to f.id, "date" to f.date, "habitId" to f.habitId, "title" to f.title,
            "done" to f.done, "orderIndex" to f.orderIndex
        ), SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deleteFocus(id: String) {
        db.delete("focus", "id=?", arrayOf(id))
    }

    fun clearFocus(date: String) {
        db.delete("focus", "date=?", arrayOf(date))
    }

    /* ----------------------------------------------------------- obstacle */

    fun obstacles(habitId: String? = null): List<ObstaclePlan> {
        val sql = if (habitId == null) "SELECT * FROM obstacle ORDER BY createdAt"
        else "SELECT * FROM obstacle WHERE habitId=? ORDER BY createdAt"
        val args = if (habitId == null) null else arrayOf(habitId)
        return db.rawQuery(sql, args).mapAll { c ->
            ObstaclePlan(c.str("id"), c.str("habitId"), c.str("ifText"), c.str("thenText"), c.lng("createdAt"))
        }
    }

    fun saveObstacle(o: ObstaclePlan) {
        db.insertWithOnConflict("obstacle", null, cv(
            "id" to o.id, "habitId" to o.habitId, "ifText" to o.ifText,
            "thenText" to o.thenText, "createdAt" to o.createdAt
        ), SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deleteObstacle(id: String) {
        db.delete("obstacle", "id=?", arrayOf(id))
    }

    /* ---------------------------------------------------------- scorecard */

    fun scorecard(): List<ScorecardEntry> =
        db.rawQuery("SELECT * FROM scorecard ORDER BY createdAt", null).mapAll { c ->
            ScorecardEntry(c.str("id"), c.str("routine"), c.int("verdict"), c.str("note"), c.lng("createdAt"))
        }

    fun saveScorecard(e: ScorecardEntry) {
        db.insertWithOnConflict("scorecard", null, cv(
            "id" to e.id, "routine" to e.routine, "verdict" to e.verdict,
            "note" to e.note, "createdAt" to e.createdAt
        ), SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deleteScorecard(id: String) {
        db.delete("scorecard", "id=?", arrayOf(id))
    }

    /* --------------------------------------------------------------- flow */

    fun flows(): List<Flow> = db.rawQuery("SELECT * FROM flow ORDER BY createdAt", null).mapAll { c ->
        Flow(c.str("id"), c.str("title"), c.str("anchor"), c.lng("createdAt"))
    }

    fun saveFlow(f: Flow) {
        db.insertWithOnConflict("flow", null, cv(
            "id" to f.id, "title" to f.title, "anchor" to f.anchor, "createdAt" to f.createdAt
        ), SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deleteFlow(id: String) {
        db.delete("flow", "id=?", arrayOf(id))
        db.delete("flowstep", "flowId=?", arrayOf(id))
    }

    fun flowSteps(flowId: String): List<FlowStep> =
        db.rawQuery("SELECT * FROM flowstep WHERE flowId=? ORDER BY orderIndex", arrayOf(flowId)).mapAll { c ->
            FlowStep(c.str("id"), c.str("flowId"), c.strOrNull("habitId"), c.str("title"),
                c.bool("existingBehaviour"), c.int("orderIndex"))
        }

    fun saveFlowStep(s: FlowStep) {
        db.insertWithOnConflict("flowstep", null, cv(
            "id" to s.id, "flowId" to s.flowId, "habitId" to s.habitId, "title" to s.title,
            "existingBehaviour" to s.existingBehaviour, "orderIndex" to s.orderIndex
        ), SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deleteFlowStep(id: String) {
        db.delete("flowstep", "id=?", arrayOf(id))
    }

    /* ------------------------------------------------------------- review */

    fun reviews(): List<Review> =
        db.rawQuery("SELECT * FROM review ORDER BY createdAt DESC", null).mapAll { c ->
            Review(c.str("id"), ReviewKind.valueOf(c.str("kind").ifBlank { "WEEKLY" }), c.str("periodLabel"),
                c.str("whatWorked"), c.str("whatDidnt"), c.str("systemChange"),
                c.str("identityEvidence"), c.lng("createdAt"))
        }

    fun saveReview(r: Review) {
        db.insertWithOnConflict("review", null, cv(
            "id" to r.id, "kind" to r.kind.name, "periodLabel" to r.periodLabel,
            "whatWorked" to r.whatWorked, "whatDidnt" to r.whatDidnt, "systemChange" to r.systemChange,
            "identityEvidence" to r.identityEvidence, "createdAt" to r.createdAt
        ), SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deleteReview(id: String) {
        db.delete("review", "id=?", arrayOf(id))
    }

    /* ------------------------------------------------------------- energy */

    fun energyLogs(): List<EnergyLog> =
        db.rawQuery("SELECT * FROM energy ORDER BY date DESC", null).mapAll { c ->
            EnergyLog(c.str("id"), c.str("date"), Checkpoint.valueOf(c.str("checkpoint").ifBlank { "MORNING" }),
                c.int("energy"), c.str("note"), c.lng("createdAt"))
        }

    fun saveEnergy(e: EnergyLog) {
        db.delete("energy", "date=? AND checkpoint=?", arrayOf(e.date, e.checkpoint.name))
        db.insertWithOnConflict("energy", null, cv(
            "id" to e.id, "date" to e.date, "checkpoint" to e.checkpoint.name,
            "energy" to e.energy, "note" to e.note, "createdAt" to e.createdAt
        ), SQLiteDatabase.CONFLICT_REPLACE)
    }

    /* -------------------------------------------------------------- audit */

    fun audit(limit: Int = 300): List<AuditEntry> =
        db.rawQuery("SELECT * FROM audit ORDER BY createdAt DESC LIMIT $limit", null).mapAll { c ->
            AuditEntry(c.str("id"), c.str("actor"), c.str("command"), c.str("summary"), c.str("payload"),
                c.str("undoPayload"), c.strOrNull("groupId"), c.bool("undone"), c.lng("createdAt"))
        }

    fun saveAudit(a: AuditEntry) {
        db.insertWithOnConflict("audit", null, cv(
            "id" to a.id, "actor" to a.actor, "command" to a.command, "summary" to a.summary,
            "payload" to a.payload, "undoPayload" to a.undoPayload, "groupId" to a.groupId,
            "undone" to a.undone, "createdAt" to a.createdAt
        ), SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun markUndone(id: String) {
        db.execSQL("UPDATE audit SET undone=1 WHERE id=?", arrayOf(id))
    }

    fun clearAudit() {
        db.delete("audit", null, null)
    }

    /* ----------------------------------------------------------- messages */

    fun messages(limit: Int = 200): List<AiMessage> =
        db.rawQuery("SELECT * FROM aimsg ORDER BY createdAt ASC LIMIT $limit", null).mapAll { c ->
            AiMessage(c.str("id"), c.str("role"), c.str("text"), c.str("meta"), c.lng("createdAt"))
        }

    fun saveMessage(m: AiMessage) {
        db.insertWithOnConflict("aimsg", null, cv(
            "id" to m.id, "role" to m.role, "text" to m.text, "meta" to m.meta, "createdAt" to m.createdAt
        ), SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun clearMessages() {
        db.delete("aimsg", null, null)
    }

    /* ---------------------------------------------------------- blueprint */

    fun projects(): List<BlueprintProject> =
        db.rawQuery("SELECT * FROM bp_project ORDER BY createdAt DESC", null).mapAll { c ->
            BlueprintProject(c.str("id"), c.str("name"), c.str("instructions"),
                c.int("version"), c.str("state"), c.lng("createdAt"))
        }

    fun project(id: String?): BlueprintProject? = if (id == null) null else projects().firstOrNull { it.id == id }

    fun saveProject(p: BlueprintProject) {
        db.insertWithOnConflict("bp_project", null, cv(
            "id" to p.id, "name" to p.name, "instructions" to p.instructions,
            "version" to p.version, "state" to p.state, "createdAt" to p.createdAt
        ), SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deleteProject(id: String) {
        db.delete("bp_project", "id=?", arrayOf(id))
        db.delete("bp_source", "projectId=?", arrayOf(id))
        db.delete("bp_req", "projectId=?", arrayOf(id))
    }

    fun sources(projectId: String): List<BlueprintSource> =
        db.rawQuery("SELECT * FROM bp_source WHERE projectId=? ORDER BY createdAt", arrayOf(projectId)).mapAll { c ->
            BlueprintSource(c.str("id"), c.str("projectId"), c.str("name"), c.str("kind"), c.str("content"),
                c.str("instructions"), c.int("lineCount"), c.lng("createdAt"))
        }

    fun saveSource(s: BlueprintSource) {
        db.insertWithOnConflict("bp_source", null, cv(
            "id" to s.id, "projectId" to s.projectId, "name" to s.name, "kind" to s.kind,
            "content" to s.content, "instructions" to s.instructions,
            "lineCount" to s.lineCount, "createdAt" to s.createdAt
        ), SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deleteSource(id: String) {
        db.delete("bp_source", "id=?", arrayOf(id))
    }

    fun requirements(projectId: String): List<Requirement> =
        db.rawQuery("SELECT * FROM bp_req WHERE projectId=? ORDER BY orderIndex", arrayOf(projectId)).mapAll { c ->
            Requirement(c.str("id"), c.str("projectId"), c.str("text"), c.strOrNull("sourceId"),
                c.str("citation"), RequirementStatus.valueOf(c.str("status").ifBlank { "ACCEPTED" }),
                c.bool("assumption"), c.str("plannedCommand"), c.str("note"), c.int("orderIndex"))
        }

    fun saveRequirement(r: Requirement) {
        db.insertWithOnConflict("bp_req", null, cv(
            "id" to r.id, "projectId" to r.projectId, "text" to r.text, "sourceId" to r.sourceId,
            "citation" to r.citation, "status" to r.status.name, "assumption" to r.assumption,
            "plannedCommand" to r.plannedCommand, "note" to r.note, "orderIndex" to r.orderIndex
        ), SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun clearRequirements(projectId: String) {
        db.delete("bp_req", "projectId=?", arrayOf(projectId))
    }

    /* --------------------------------------------------------------- data */

    fun deleteAllData() {
        for (t in listOf("identity", "goal", "sys", "habit", "checkin", "focus", "obstacle",
            "scorecard", "flow", "flowstep", "review", "energy", "audit", "aimsg",
            "bp_project", "bp_source", "bp_req")) {
            db.delete(t, null, null)
        }
    }

    fun counts(): Map<String, Int> {
        val out = LinkedHashMap<String, Int>()
        for (t in listOf("identity", "goal", "sys", "habit", "checkin", "focus", "obstacle",
            "scorecard", "flow", "review", "energy", "audit", "bp_project")) {
            db.rawQuery("SELECT COUNT(*) FROM $t", null).use { c ->
                out[t] = if (c.moveToFirst()) c.getInt(0) else 0
            }
        }
        return out
    }
}
