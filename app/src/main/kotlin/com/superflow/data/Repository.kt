package com.superflow.data

import android.content.Context
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase
import com.superflow.core.schedule.Recurrence
import com.superflow.core.schedule.Schedule
import com.superflow.core.time.SfTime
import com.superflow.core.time.SuperFlowClock
import com.superflow.core.time.SystemClock
import com.superflow.data.db.*
import com.superflow.data.model.*
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single repository over the database.
 *
 * Exposes a [revision] StateFlow that increments on every write, which
 * ViewModels observe to recompute their UI state. The manual UI and the AI
 * tool layer both call these same functions, so behaviour cannot drift.
 */
class Repository private constructor(context: Context, val clock: SuperFlowClock) {

    private val database = SuperFlowDatabase.get(context)
    private val db: SupportSQLiteDatabase get() = database.db

    private val _revision = MutableStateFlow(0L)

    /** Bumped after every mutation. Observers re-query. */
    val revision: StateFlow<Long> = _revision.asStateFlow()

    companion object {
        @Volatile private var instance: Repository? = null

        fun get(context: Context): Repository =
            instance ?: synchronized(this) {
                instance ?: Repository(context.applicationContext, SystemClock()).also {
                    instance = it
                }
            }

        /** Test seam: inject a fixed clock. */
        fun createForTest(context: Context, clock: SuperFlowClock) =
            Repository(context.applicationContext, clock)
    }

    fun invalidate() {
        _revision.value = _revision.value + 1
    }

    private fun query(sql: String, args: Array<Any?>? = null) =
        db.query(SimpleSQLiteQuery(sql, args))

    private fun insert(table: String, values: android.content.ContentValues) {
        db.insert(table, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE, values)
        invalidate()
    }

    fun delete(table: String, where: String, args: Array<Any?>) {
        db.delete(table, where, args)
        invalidate()
    }

    /* ------------------------------------------------------------ identity */

    fun identities(includeArchived: Boolean = false): List<Identity> =
        query(
            "SELECT * FROM identity ${if (includeArchived) "" else "WHERE status<>'ARCHIVED'"} ORDER BY createdAt"
        ).mapAll(Rows::identity)

    fun identity(id: String?): Identity? =
        if (id == null) null
        else query("SELECT * FROM identity WHERE id=?", arrayOf(id)).mapAll(Rows::identity).firstOrNull()

    fun saveIdentity(i: Identity) = insert("identity", contentValuesOf(
        "id" to i.id, "statement" to i.statement, "lifeArea" to i.lifeArea.name,
        "status" to i.status.name, "createdAt" to i.createdAt
    ))

    fun deleteIdentity(id: String) = delete("identity", "id=?", arrayOf(id))

    /* ---------------------------------------------------------------- goal */

    fun goals(): List<Goal> = query("SELECT * FROM goal ORDER BY createdAt").mapAll(Rows::goal)

    fun goal(id: String?): Goal? =
        if (id == null) null
        else query("SELECT * FROM goal WHERE id=?", arrayOf(id)).mapAll(Rows::goal).firstOrNull()

    fun saveGoal(g: Goal) = insert("goal", contentValuesOf(
        "id" to g.id, "identityId" to g.identityId, "title" to g.title, "why" to g.why,
        "outcomeMetric" to g.outcomeMetric, "targetValue" to g.targetValue,
        "targetDate" to g.targetDate, "status" to g.status.name, "createdAt" to g.createdAt
    ))

    fun deleteGoal(id: String) = delete("goal", "id=?", arrayOf(id))

    /* -------------------------------------------------------------- system */

    fun systems(): List<Sys> = query("SELECT * FROM sys ORDER BY createdAt").mapAll(Rows::system)

    fun system(id: String?): Sys? =
        if (id == null) null
        else query("SELECT * FROM sys WHERE id=?", arrayOf(id)).mapAll(Rows::system).firstOrNull()

    fun saveSystem(s: Sys) = insert("sys", contentValuesOf(
        "id" to s.id, "goalId" to s.goalId, "title" to s.title, "description" to s.description,
        "status" to s.status.name, "createdAt" to s.createdAt
    ))

    fun deleteSystem(id: String) = delete("sys", "id=?", arrayOf(id))

    /* --------------------------------------------------------------- habit */

    fun habits(includeArchived: Boolean = false): List<Habit> =
        query(
            "SELECT * FROM habit ${if (includeArchived) "" else "WHERE status<>'ARCHIVED'"} " +
                    "ORDER BY orderIndex, createdAt"
        ).mapAll(Rows::habit)

    fun habit(id: String?): Habit? =
        if (id == null) null
        else query("SELECT * FROM habit WHERE id=?", arrayOf(id)).mapAll(Rows::habit).firstOrNull()

    /** Fuzzy lookup for AI commands and search. */
    fun findHabit(queryText: String): Habit? {
        val q = queryText.trim().lowercase()
        if (q.isEmpty()) return null
        val all = habits(true)
        return all.firstOrNull { it.title.lowercase() == q }
            ?: all.firstOrNull { it.title.lowercase().startsWith(q) }
            ?: all.firstOrNull { it.title.lowercase().contains(q) }
            ?: all.firstOrNull { q.contains(it.title.lowercase()) }
    }

    fun saveHabit(h: Habit) = insert("habit", contentValuesOf(
        "id" to h.id, "systemId" to h.systemId, "identityId" to h.identityId, "title" to h.title,
        "mode" to h.mode.name, "trackType" to h.trackType.name, "targetCount" to h.targetCount,
        "unit" to h.unit, "cueTime" to h.cueTime, "cuePlace" to h.cuePlace,
        "anchorHabitId" to h.anchorHabitId, "anchorText" to h.anchorText,
        "benefit" to h.benefit, "temptationBundle" to h.temptationBundle, "reframe" to h.reframe,
        "tinyStart" to h.tinyStart, "minimumVersion" to h.minimumVersion,
        "standardVersion" to h.standardVersion, "stretchVersion" to h.stretchVersion,
        "frictionPlan" to h.frictionPlan, "environmentPrep" to h.environmentPrep,
        "reward" to h.reward, "recoveryPlan" to h.recoveryPlan,
        "recurrenceRule" to h.recurrenceRule, "scheduleVersion" to h.scheduleVersion,
        "startDate" to h.startDate, "endDate" to h.endDate,
        "reminderEnabled" to h.reminderEnabled,
        "protectedRoutine" to h.protectedRoutine, "colorSeed" to h.colorSeed,
        "orderIndex" to h.orderIndex, "status" to h.status.name, "createdAt" to h.createdAt
    ))

    fun deleteHabit(id: String) {
        db.delete("habit", "id=?", arrayOf(id))
        db.delete("checkin", "habitId=?", arrayOf(id))
        db.delete("obstacle", "habitId=?", arrayOf(id))
        db.delete("focus", "habitId=?", arrayOf(id))
        invalidate()
    }

    fun scheduleOf(habit: Habit): Schedule = Schedule(
        recurrence = Recurrence.decode(habit.recurrenceRule),
        localTime = SfTime.parseTime(habit.cueTime),
        zoneId = clock.zone(),
        startDate = SfTime.parseDate(habit.startDate)
            ?: java.time.Instant.ofEpochMilli(habit.createdAt)
                .atZone(clock.zone()).toLocalDate(),
        endDate = habit.endDate?.let { SfTime.parseDate(it) },
        version = habit.scheduleVersion,
        enabled = habit.status == Status.ACTIVE
    )

    fun habitsForDay(date: LocalDate): List<Habit> =
        habits().filter { it.status == Status.ACTIVE && scheduleOf(it).activeOn(date) }

    /** Today's habits joined with their check-ins, ready for the list adapter. */
    fun todayHabits(date: LocalDate): List<TodayHabit> {
        val iso = SfTime.format(date)
        val checkIns = checkInsFor(iso).associateBy { it.habitId }
        val returning = returnCandidates(date).map { it.id }.toSet()
        return habitsForDay(date).map { h -> TodayHabit(h, checkIns[h.id], h.id in returning) }
    }

    /** Habits missed at their previous real opportunity: the never-miss-twice trigger. */
    fun returnCandidates(date: LocalDate): List<Habit> {
        val allPauses = pauses()
        return habitsForDay(date).filter { h ->
            val series = com.superflow.core.schedule.Opportunities.series(
                habit = h,
                schedule = scheduleOf(h),
                checkIns = checkInsOf(h.id).associateBy { LocalDate.parse(it.date) },
                pauses = allPauses.filter { it.habitId == null || it.habitId == h.id },
                dates = SfTime.lastDays(30, date),
                today = date
            )
            com.superflow.core.schedule.Opportunities.needsReturn(series, date)
        }
    }

    /* ------------------------------------------------------------ check-in */

    fun checkIns(): List<CheckIn> =
        query("SELECT * FROM checkin ORDER BY date DESC").mapAll(Rows::checkIn)

    fun checkInsFor(date: String): List<CheckIn> =
        query("SELECT * FROM checkin WHERE date=?", arrayOf(date)).mapAll(Rows::checkIn)

    fun checkInsBetween(from: String, to: String): List<CheckIn> =
        query("SELECT * FROM checkin WHERE date>=? AND date<=?", arrayOf(from, to)).mapAll(Rows::checkIn)

    fun checkIn(habitId: String, date: String): CheckIn? =
        query("SELECT * FROM checkin WHERE habitId=? AND date=?", arrayOf(habitId, date))
            .mapAll(Rows::checkIn).firstOrNull()

    fun checkInsOf(habitId: String): List<CheckIn> =
        query("SELECT * FROM checkin WHERE habitId=? ORDER BY date DESC", arrayOf(habitId))
            .mapAll(Rows::checkIn)

    fun saveCheckIn(ci: CheckIn) {
        db.delete("checkin", "habitId=? AND date=?", arrayOf(ci.habitId, ci.date))
        insert("checkin", contentValuesOf(
            "id" to ci.id, "habitId" to ci.habitId, "date" to ci.date, "result" to ci.result.name,
            "level" to ci.level.name, "amount" to ci.amount, "note" to ci.note,
            "createdAt" to ci.createdAt
        ))
    }

    fun clearCheckIn(habitId: String, date: String) =
        delete("checkin", "habitId=? AND date=?", arrayOf(habitId, date))

    /* --------------------------------------------------------------- focus */

    fun focusAll(): List<FocusItem> =
        query("SELECT * FROM focus ORDER BY date DESC, orderIndex").mapAll(Rows::focus)

    fun focusFor(date: String): List<FocusItem> =
        query("SELECT * FROM focus WHERE date=? ORDER BY orderIndex", arrayOf(date)).mapAll(Rows::focus)

    fun saveFocus(f: FocusItem) = insert("focus", contentValuesOf(
        "id" to f.id, "date" to f.date, "habitId" to f.habitId, "title" to f.title,
        "done" to f.done, "orderIndex" to f.orderIndex
    ))

    fun deleteFocus(id: String) = delete("focus", "id=?", arrayOf(id))

    fun clearFocus(date: String) = delete("focus", "date=?", arrayOf(date))

    /* ------------------------------------------------------------ obstacle */

    fun obstacles(habitId: String? = null): List<ObstaclePlan> =
        if (habitId == null) query("SELECT * FROM obstacle ORDER BY createdAt").mapAll(Rows::obstacle)
        else query("SELECT * FROM obstacle WHERE habitId=? ORDER BY createdAt", arrayOf(habitId))
            .mapAll(Rows::obstacle)

    fun saveObstacle(o: ObstaclePlan) = insert("obstacle", contentValuesOf(
        "id" to o.id, "habitId" to o.habitId, "ifText" to o.ifText,
        "thenText" to o.thenText, "createdAt" to o.createdAt
    ))

    fun deleteObstacle(id: String) = delete("obstacle", "id=?", arrayOf(id))

    /* ----------------------------------------------------------- scorecard */

    fun scorecard(): List<ScorecardEntry> =
        query("SELECT * FROM scorecard ORDER BY createdAt").mapAll(Rows::scorecard)

    fun saveScorecard(e: ScorecardEntry) = insert("scorecard", contentValuesOf(
        "id" to e.id, "routine" to e.routine, "verdict" to e.verdict,
        "note" to e.note, "createdAt" to e.createdAt
    ))

    fun deleteScorecard(id: String) = delete("scorecard", "id=?", arrayOf(id))

    /* ---------------------------------------------------------------- flow */

    fun flows(): List<Flow> = query("SELECT * FROM flow ORDER BY createdAt").mapAll(Rows::flow)

    fun saveFlow(f: Flow) = insert("flow", contentValuesOf(
        "id" to f.id, "title" to f.title, "anchor" to f.anchor, "createdAt" to f.createdAt
    ))

    fun deleteFlow(id: String) {
        db.delete("flow", "id=?", arrayOf(id))
        db.delete("flowstep", "flowId=?", arrayOf(id))
        invalidate()
    }

    fun flowSteps(flowId: String): List<FlowStep> =
        query("SELECT * FROM flowstep WHERE flowId=? ORDER BY orderIndex", arrayOf(flowId))
            .mapAll(Rows::flowStep)

    fun saveFlowStep(s: FlowStep) = insert("flowstep", contentValuesOf(
        "id" to s.id, "flowId" to s.flowId, "habitId" to s.habitId, "title" to s.title,
        "existingBehaviour" to s.existingBehaviour, "orderIndex" to s.orderIndex
    ))

    fun deleteFlowStep(id: String) = delete("flowstep", "id=?", arrayOf(id))

    /* -------------------------------------------------------------- review */

    fun reviews(): List<Review> =
        query("SELECT * FROM review ORDER BY createdAt DESC").mapAll(Rows::review)

    fun saveReview(r: Review) = insert("review", contentValuesOf(
        "id" to r.id, "kind" to r.kind.name, "periodLabel" to r.periodLabel,
        "whatWorked" to r.whatWorked, "whatDidnt" to r.whatDidnt, "systemChange" to r.systemChange,
        "identityEvidence" to r.identityEvidence, "createdAt" to r.createdAt
    ))

    fun deleteReview(id: String) = delete("review", "id=?", arrayOf(id))

    /* -------------------------------------------------------------- energy */

    fun energyLogs(): List<EnergyLog> =
        query("SELECT * FROM energy ORDER BY date DESC").mapAll(Rows::energy)

    fun energyFor(date: String): List<EnergyLog> =
        query("SELECT * FROM energy WHERE date=?", arrayOf(date)).mapAll(Rows::energy)

    fun saveEnergy(e: EnergyLog) {
        db.delete("energy", "date=? AND checkpoint=?", arrayOf(e.date, e.checkpoint.name))
        insert("energy", contentValuesOf(
            "id" to e.id, "date" to e.date, "checkpoint" to e.checkpoint.name,
            "energy" to e.energy, "note" to e.note, "createdAt" to e.createdAt
        ))
    }

    /* --------------------------------------------------------------- pause */

    fun pauses(): List<PauseWindow> =
        query("SELECT * FROM pause ORDER BY startDate DESC").mapAll(Rows::pause)

    fun savePause(p: PauseWindow) = insert("pause", contentValuesOf(
        "id" to p.id, "habitId" to p.habitId, "startDate" to p.startDate,
        "endDate" to p.endDate, "reason" to p.reason, "createdAt" to p.createdAt
    ))

    fun deletePause(id: String) = delete("pause", "id=?", arrayOf(id))

    /* ------------------------------------------------------------- profile */

    fun profile(): UserProfile =
        query("SELECT * FROM profile WHERE id='local'").mapAll(Rows::profile).firstOrNull()
            ?: UserProfile(
                zoneId = clock.zone().id,
                locale = java.util.Locale.getDefault().toLanguageTag()
            )

    fun saveProfile(p: UserProfile) = insert("profile", contentValuesOf(
        "id" to p.id, "displayName" to p.displayName, "locale" to p.locale,
        "zoneId" to p.zoneId, "weekStart" to p.weekStart,
        "createdAt" to p.createdAt, "updatedAt" to System.currentTimeMillis()
    ))

    /* --------------------------------------------------------------- audit */

    fun audit(limit: Int = 300): List<AuditEntry> =
        query("SELECT * FROM audit ORDER BY createdAt DESC LIMIT $limit").mapAll(Rows::audit)

    fun auditGroup(groupId: String): List<AuditEntry> =
        query("SELECT * FROM audit WHERE groupId=? ORDER BY createdAt DESC", arrayOf(groupId))
            .mapAll(Rows::audit)

    fun saveAudit(a: AuditEntry) = insert("audit", contentValuesOf(
        "id" to a.id, "actor" to a.actor, "command" to a.command, "summary" to a.summary,
        "payload" to a.payload, "undoPayload" to a.undoPayload, "groupId" to a.groupId,
        "undone" to a.undone, "createdAt" to a.createdAt
    ))

    fun markUndone(id: String) {
        db.execSQL("UPDATE audit SET undone=1 WHERE id=?", arrayOf(id))
        invalidate()
    }

    fun clearAudit() = delete("audit", "1", arrayOf())

    /* ------------------------------------------------------------ messages */

    fun messages(limit: Int = 200): List<AiMessage> =
        query("SELECT * FROM aimsg ORDER BY createdAt ASC LIMIT $limit").mapAll(Rows::message)

    fun saveMessage(m: AiMessage) = insert("aimsg", contentValuesOf(
        "id" to m.id, "role" to m.role, "text" to m.text, "meta" to m.meta, "createdAt" to m.createdAt
    ))

    fun clearMessages() = delete("aimsg", "1", arrayOf())

    /* ----------------------------------------------------------- blueprint */

    fun projects(): List<BlueprintProject> =
        query("SELECT * FROM bp_project ORDER BY createdAt DESC").mapAll(Rows::project)

    fun project(id: String?): BlueprintProject? =
        if (id == null) null
        else query("SELECT * FROM bp_project WHERE id=?", arrayOf(id)).mapAll(Rows::project).firstOrNull()

    fun saveProject(p: BlueprintProject) = insert("bp_project", contentValuesOf(
        "id" to p.id, "name" to p.name, "instructions" to p.instructions,
        "version" to p.version, "state" to p.state, "parentVersionId" to p.parentVersionId,
        "createdAt" to p.createdAt
    ))

    fun deleteProject(id: String) {
        db.delete("bp_project", "id=?", arrayOf(id))
        db.delete("bp_source", "projectId=?", arrayOf(id))
        db.delete("bp_req", "projectId=?", arrayOf(id))
        db.delete("bp_version", "projectId=?", arrayOf(id))
        invalidate()
    }

    fun sources(projectId: String): List<BlueprintSource> =
        query("SELECT * FROM bp_source WHERE projectId=? ORDER BY createdAt", arrayOf(projectId))
            .mapAll(Rows::source)

    fun saveSource(s: BlueprintSource) = insert("bp_source", contentValuesOf(
        "id" to s.id, "projectId" to s.projectId, "name" to s.name, "kind" to s.kind,
        "content" to s.content, "instructions" to s.instructions,
        "lineCount" to s.lineCount, "createdAt" to s.createdAt
    ))

    fun deleteSource(id: String) = delete("bp_source", "id=?", arrayOf(id))

    fun requirements(projectId: String): List<Requirement> =
        query("SELECT * FROM bp_req WHERE projectId=? ORDER BY orderIndex", arrayOf(projectId))
            .mapAll(Rows::requirement)

    fun saveRequirement(r: Requirement) = insert("bp_req", contentValuesOf(
        "id" to r.id, "projectId" to r.projectId, "text" to r.text, "sourceId" to r.sourceId,
        "citation" to r.citation, "status" to r.status.name, "assumption" to r.assumption,
        "plannedCommand" to r.plannedCommand, "note" to r.note, "orderIndex" to r.orderIndex
    ))

    fun clearRequirements(projectId: String) = delete("bp_req", "projectId=?", arrayOf(projectId))

    fun versions(projectId: String): List<BlueprintVersion> =
        query("SELECT * FROM bp_version WHERE projectId=? ORDER BY version DESC", arrayOf(projectId))
            .mapAll(Rows::version)

    fun saveVersion(v: BlueprintVersion) = insert("bp_version", contentValuesOf(
        "id" to v.id, "projectId" to v.projectId, "version" to v.version,
        "label" to v.label, "ledgerJson" to v.ledgerJson, "createdAt" to v.createdAt
    ))

    /* ----------------------------------------------------------------- data */

    fun deleteAllData() {
        for (t in listOf("identity", "goal", "sys", "habit", "checkin", "focus", "obstacle",
            "scorecard", "flow", "flowstep", "review", "energy", "audit", "aimsg",
            "bp_project", "bp_source", "bp_req", "bp_version", "pause", "profile")) {
            db.delete(t, null, null)
        }
        invalidate()
    }

    fun counts(): Map<String, Int> {
        val out = LinkedHashMap<String, Int>()
        for (t in listOf("identity", "goal", "sys", "habit", "checkin", "focus", "obstacle",
            "scorecard", "flow", "review", "energy", "audit", "bp_project")) {
            query("SELECT COUNT(*) FROM $t").use { c ->
                out[t] = if (c.moveToFirst()) c.getInt(0) else 0
            }
        }
        return out
    }
}
