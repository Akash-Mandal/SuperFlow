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
import com.superflow.util.Fuzzy
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
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

    internal val appContext = context.applicationContext
    private val database = SuperFlowDatabase.get(context)
    private val db: SupportSQLiteDatabase get() = database.db

    /**
     * Serialises writes from the UI thread, AI tool calls and WorkManager
     * jobs. SQLite itself serialises writes, but without this guard a
     * read-modify-write (e.g. check-in upsert, reorder) could interleave
     * with another writer and lose an update. Reentrant so a transaction
     * can call the individual [insert]/[delete] helpers.
     */
    private val writeLock = ReentrantLock()

    private val _revision = MutableStateFlow(0L)

    /** Bumped after every mutation. Observers re-query. */
    val revision: StateFlow<Long> = _revision.asStateFlow()

    /**
     * Runs [block] inside a single SQLite transaction under [writeLock].
     *
     * Multi-step mutations (cascading deletes, reorder, rollover, import)
     * go through here so observers only see a consistent state and a failure
     * midway never leaves half-written rows behind.
     */
    private inline fun <T> transaction(block: () -> T): T = writeLock.withLock {
        db.beginTransaction()
        try {
            val result = block()
            db.setTransactionSuccessful()
            result
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Public, lock-and-transaction wrapper for bulk/atomic work performed by
     * other layers (e.g. JSON import, grouped Blueprint execution). The whole
     * block commits or rolls back together, and the revision counter is bumped
     * exactly once on success.
     */
    fun <T> runInTransaction(block: () -> T): T {
        val result = transaction(block)
        invalidate()
        return result
    }

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

    private fun insert(table: String, values: android.content.ContentValues) = writeLock.withLock {
        db.insert(table, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE, values)
        invalidate()
    }

    fun delete(table: String, where: String, args: Array<Any?>) = writeLock.withLock {
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

    fun deleteIdentity(id: String) {
        // Cascade: identity -> goals -> systems -> habits -> their children.
        transaction {
            goals().filter { it.identityId == id }.forEach { deleteGoalInternal(it.id) }
            db.delete("identity", "id=?", arrayOf(id))
        }
        invalidate()
    }

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

    fun deleteGoal(id: String) {
        transaction { deleteGoalInternal(id) }
        invalidate()
    }

    /** Must be called inside a [transaction]. Cascades to systems. */
    private fun deleteGoalInternal(id: String) {
        systems().filter { it.goalId == id }.forEach { deleteSystemInternal(it.id) }
        db.delete("goal", "id=?", arrayOf(id))
    }

    /* -------------------------------------------------------------- system */

    fun systems(): List<Sys> = query("SELECT * FROM sys ORDER BY createdAt").mapAll(Rows::system)

    fun system(id: String?): Sys? =
        if (id == null) null
        else query("SELECT * FROM sys WHERE id=?", arrayOf(id)).mapAll(Rows::system).firstOrNull()

    fun saveSystem(s: Sys) = insert("sys", contentValuesOf(
        "id" to s.id, "goalId" to s.goalId, "title" to s.title, "description" to s.description,
        "status" to s.status.name, "createdAt" to s.createdAt
    ))

    fun deleteSystem(id: String) {
        transaction { deleteSystemInternal(id) }
        invalidate()
    }

    /** Must be called inside a [transaction]. Cascades to habits. */
    private fun deleteSystemInternal(id: String) {
        habits(true).filter { it.systemId == id }.forEach { deleteHabitInternal(it.id) }
        db.delete("sys", "id=?", arrayOf(id))
    }

    /* --------------------------------------------------------------- habit */

    fun habits(includeArchived: Boolean = false): List<Habit> =
        query(
            "SELECT * FROM habit ${if (includeArchived) "" else "WHERE status<>'ARCHIVED'"} " +
                    "ORDER BY orderIndex, createdAt"
        ).mapAll(Rows::habit)

    fun habit(id: String?): Habit? =
        if (id == null) null
        else query("SELECT * FROM habit WHERE id=?", arrayOf(id)).mapAll(Rows::habit).firstOrNull()

    /**
     * Fuzzy lookup for AI commands and search.
     *
     * Tries exact, prefix, substring and containment matches first, then
     * falls back to Levenshtein distance so a one-character typo
     * ("wlak", "jornaling") still resolves. The fuzzy threshold rejects
     * unrelated words, and short titles (<3 chars) skip fuzzy matching to
     * avoid spurious hits.
     */
    fun findHabit(queryText: String): Habit? {
        val q = queryText.trim().lowercase()
        if (q.isEmpty()) return null
        val all = habits(true)
        all.firstOrNull { it.title.lowercase() == q }?.let { return it }
        all.firstOrNull { it.title.lowercase().startsWith(q) }?.let { return it }
        all.firstOrNull { it.title.lowercase().contains(q) }?.let { return it }
        all.firstOrNull { q.contains(it.title.lowercase()) && it.title.length >= 3 }?.let { return it }
        // Typo tolerance: only consider candidates long enough to be meaningful.
        val candidates = all.filter { it.title.length >= 3 }
        return Fuzzy.bestMatch(q, candidates) { it.title.lowercase() }
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
        transaction { deleteHabitInternal(id) }
        invalidate()
    }

    /** Must be called inside a [transaction]. */
    private fun deleteHabitInternal(id: String) {
        db.delete("habit", "id=?", arrayOf(id))
        db.delete("checkin", "habitId=?", arrayOf(id))
        db.delete("obstacle", "habitId=?", arrayOf(id))
        db.delete("focus", "habitId=?", arrayOf(id))
        // Flow steps reference habits by id but survive the habit's deletion;
        // they are simply detached so a flow can outlive an edited habit.
        db.execSQL("UPDATE flowstep SET habitId=NULL WHERE habitId=?", arrayOf(id))
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

    fun saveCheckIn(ci: CheckIn) = transaction {
        db.delete("checkin", "habitId=? AND date=?", arrayOf(ci.habitId, ci.date))
        db.insert("checkin", android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE, contentValuesOf(
            "id" to ci.id, "habitId" to ci.habitId, "date" to ci.date, "result" to ci.result.name,
            "level" to ci.level.name, "amount" to ci.amount, "note" to ci.note,
            "createdAt" to ci.createdAt
        ))
        invalidate()
    }

    fun clearCheckIn(habitId: String, date: String) =
        delete("checkin", "habitId=? AND date=?", arrayOf(habitId, date))

    /** Clears every check-in for one date (used by undo_today). */
    fun clearCheckInsForDate(date: String) = writeLock.withLock {
        db.delete("checkin", "date=?", arrayOf(date))
        invalidate()
    }

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

    fun deleteFlow(id: String) = transaction {
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

    fun saveEnergy(e: EnergyLog) = transaction {
        db.delete("energy", "date=? AND checkpoint=?", arrayOf(e.date, e.checkpoint.name))
        db.insert("energy", android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE, contentValuesOf(
            "id" to e.id, "date" to e.date, "checkpoint" to e.checkpoint.name,
            "energy" to e.energy, "note" to e.note, "createdAt" to e.createdAt
        ))
        invalidate()
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

    fun audit(limit: Int = 300, offset: Int = 0): List<AuditEntry> =
        query(
            "SELECT * FROM audit ORDER BY createdAt DESC LIMIT $limit OFFSET $offset"
        ).mapAll(Rows::audit)

    fun auditGroup(groupId: String): List<AuditEntry> =
        query("SELECT * FROM audit WHERE groupId=? ORDER BY createdAt DESC", arrayOf(groupId))
            .mapAll(Rows::audit)

    fun saveAudit(a: AuditEntry) = insert("audit", contentValuesOf(
        "id" to a.id, "actor" to a.actor, "command" to a.command, "summary" to a.summary,
        "payload" to a.payload, "undoPayload" to a.undoPayload, "groupId" to a.groupId,
        "undone" to a.undone, "createdAt" to a.createdAt
    ))

    fun markUndone(id: String) = writeLock.withLock {
        db.execSQL("UPDATE audit SET undone=1 WHERE id=?", arrayOf(id))
        invalidate()
    }

    fun clearAudit() = delete("audit", "1", arrayOf())

    /* ------------------------------------------------------------ messages */

    fun messages(limit: Int = 200, offset: Int = 0): List<AiMessage> =
        query(
            "SELECT * FROM aimsg ORDER BY createdAt ASC LIMIT $limit OFFSET $offset"
        ).mapAll(Rows::message)

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

    fun deleteProject(id: String) = transaction {
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

    /** Every table in the schema, in child-before-parent delete order. */
    private val allTables = listOf(
        "checkin", "focus", "obstacle", "scorecard", "flowstep", "flow", "review",
        "energy", "bp_req", "bp_source", "bp_version", "bp_project", "pause",
        "aimsg", "audit", "habit", "sys", "goal", "identity", "profile"
    )

    fun deleteAllData() = transaction {
        for (t in allTables) db.delete(t, null, null)
        invalidate()
    }

    fun counts(): Map<String, Int> {
        val out = LinkedHashMap<String, Int>()
        for (t in allTables) {
            query("SELECT COUNT(*) FROM $t").use { c ->
                out[t] = if (c.moveToFirst()) c.getInt(0) else 0
            }
        }
        return out
    }

    /* ---------------------------------------------------- aggregation (#38) */

    /**
     * Aggregate check-in counts for one habit via SQL GROUP BY, avoiding a
     * full in-memory scan. Returns a map of [CheckInResult] name -> count.
     */
    fun checkInCounts(habitId: String): Map<String, Int> {
        val out = LinkedHashMap<String, Int>()
        query(
            "SELECT result, COUNT(*) FROM checkin WHERE habitId=? GROUP BY result",
            arrayOf(habitId)
        ).use { c ->
            while (c.moveToNext()) out[c.getString(0)] = c.getInt(1)
        }
        return out
    }

    /** Repetitions (DONE + RESISTED) for a habit, counted in SQL. */
    fun repetitions(habitId: String): Int = checkInCounts(habitId)
        .filterKeys { it == CheckInResult.DONE.name || it == CheckInResult.RESISTED.name }
        .values.sum()

    /* ---------------------------------------------- data integrity (#36) */

    /** One orphaned-record finding from [integrityReport]. */
    data class IntegrityIssue(val table: String, val count: Int, val detail: String)

    /**
     * Finds records that point at parents which no longer exist. Used by the
     * AI Engine diagnostics screen. Each query is a LEFT JOIN / NOT EXISTS
     * scan over the (indexed) foreign-key columns.
     */
    fun integrityReport(): List<IntegrityIssue> {
        val issues = ArrayList<IntegrityIssue>()

        fun count(sql: String, args: Array<Any?>? = null): Int =
            query(sql, args).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }

        // goals with no identity (identityId set but missing)
        count(
            "SELECT COUNT(*) FROM goal g WHERE g.identityId IS NOT NULL AND " +
                    "g.identityId<>'' AND NOT EXISTS (SELECT 1 FROM identity i WHERE i.id=g.identityId)"
        ).let { if (it > 0) issues.add(IntegrityIssue("goal", it, "goals linked to a missing identity")) }

        // systems with no goal
        count(
            "SELECT COUNT(*) FROM sys s WHERE s.goalId IS NOT NULL AND s.goalId<>'' AND " +
                    "NOT EXISTS (SELECT 1 FROM goal g WHERE g.id=s.goalId)"
        ).let { if (it > 0) issues.add(IntegrityIssue("sys", it, "systems linked to a missing goal")) }

        // habits with no system
        count(
            "SELECT COUNT(*) FROM habit h WHERE h.systemId IS NOT NULL AND h.systemId<>'' AND " +
                    "NOT EXISTS (SELECT 1 FROM sys s WHERE s.id=h.systemId)"
        ).let { if (it > 0) issues.add(IntegrityIssue("habit", it, "habits linked to a missing system")) }

        // habits with no identity
        count(
            "SELECT COUNT(*) FROM habit h WHERE h.identityId IS NOT NULL AND h.identityId<>'' AND " +
                    "NOT EXISTS (SELECT 1 FROM identity i WHERE i.id=h.identityId)"
        ).let { if (it > 0) issues.add(IntegrityIssue("habit", it, "habits linked to a missing identity")) }

        // check-ins with no habit
        count(
            "SELECT COUNT(*) FROM checkin c WHERE NOT EXISTS " +
                    "(SELECT 1 FROM habit h WHERE h.id=c.habitId)"
        ).let { if (it > 0) issues.add(IntegrityIssue("checkin", it, "check-ins for a missing habit")) }

        // obstacles with no habit
        count(
            "SELECT COUNT(*) FROM obstacle o WHERE NOT EXISTS " +
                    "(SELECT 1 FROM habit h WHERE h.id=o.habitId)"
        ).let { if (it > 0) issues.add(IntegrityIssue("obstacle", it, "obstacle plans for a missing habit")) }

        // focus items with no habit (only when linked)
        count(
            "SELECT COUNT(*) FROM focus f WHERE f.habitId IS NOT NULL AND f.habitId<>'' AND " +
                    "NOT EXISTS (SELECT 1 FROM habit h WHERE h.id=f.habitId)"
        ).let { if (it > 0) issues.add(IntegrityIssue("focus", it, "focus items for a missing habit")) }

        // flow steps with no flow
        count(
            "SELECT COUNT(*) FROM flowstep fs WHERE NOT EXISTS " +
                    "(SELECT 1 FROM flow f WHERE f.id=fs.flowId)"
        ).let { if (it > 0) issues.add(IntegrityIssue("flowstep", it, "flow steps for a missing flow")) }

        // blueprint rows with no project
        count(
            "SELECT COUNT(*) FROM bp_source s WHERE NOT EXISTS " +
                    "(SELECT 1 FROM bp_project p WHERE p.id=s.projectId)"
        ).let { if (it > 0) issues.add(IntegrityIssue("bp_source", it, "sources for a missing project")) }
        count(
            "SELECT COUNT(*) FROM bp_req r WHERE NOT EXISTS " +
                    "(SELECT 1 FROM bp_project p WHERE p.id=r.projectId)"
        ).let { if (it > 0) issues.add(IntegrityIssue("bp_req", it, "requirements for a missing project")) }
        count(
            "SELECT COUNT(*) FROM bp_version v WHERE NOT EXISTS " +
                    "(SELECT 1 FROM bp_project p WHERE p.id=v.projectId)"
        ).let { if (it > 0) issues.add(IntegrityIssue("bp_version", it, "versions for a missing project")) }

        return issues
    }
}
