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
import com.superflow.util.jsonArrayOfStrings
import com.superflow.util.jsonArrayFromObjects
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

    /**
     * Immutable read snapshot: one query per table instead of one per habit.
     *
     * Screen builds and insight computations read the same handful of tables
     * many times over (per-habit check-ins, pauses, habits); taking one
     * snapshot per pass turns O(N) queries into O(1). Callers that must see
     * concurrent writes simply take a fresh snapshot — the revision flow
     * guarantees a rebuild after every mutation.
     */
    data class DataSnapshot(
        val identities: List<Identity>,
        /** All habits, archived included. */
        val habits: List<Habit>,
        /** Every check-in, newest first. */
        val checkIns: List<CheckIn>,
        val pauses: List<PauseWindow>
    ) {
        val activeHabits: List<Habit>
            get() = habits.filter { it.status == Status.ACTIVE }

        val checkInsByHabit: Map<String, List<CheckIn>>
            get() = checkIns.groupBy { it.habitId }
    }

    fun snapshot(): DataSnapshot = DataSnapshot(
        identities = identities(true),
        habits = habits(true),
        checkIns = checkIns(),
        pauses = pauses()
    )

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
        "status" to i.status.name, "isPrimary" to i.isPrimary,
        "evolutionHistory" to jsonArrayFromObjects(i.evolutionHistory.map { ev ->
            org.json.JSONObject().apply {
                put("previousStatement", ev.previousStatement)
                put("newStatement", ev.newStatement)
                put("reason", ev.reason)
                put("votesAtEvolution", ev.votesAtEvolution)
                put("date", ev.date)
            }
        }),
        "createdAt" to i.createdAt
    ))

    fun deleteIdentity(id: String) {
        // Cascade: identity -> goals -> systems -> habits -> their children.
        transaction {
            goals().filter { it.identityId == id }.forEach { deleteGoalInternal(it.id) }
            db.delete("evidence", "identityId=?", arrayOf(id))
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
        "targetDate" to g.targetDate, "currentMetricValue" to g.currentMetricValue,
        "metricUnit" to g.metricUnit, "status" to g.status.name,
        "milestones" to jsonArrayFromObjects(g.milestones.map { m ->
            org.json.JSONObject().apply {
                put("id", m.id)
                put("title", m.title)
                put("achieved", m.achieved)
                put("achievedDate", m.achievedDate)
                put("linkedHabitIds", org.json.JSONArray().apply {
                    m.linkedHabitIds.forEach { put(it) }
                })
            }
        }),
        "createdAt" to g.createdAt
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
        "status" to s.status.name, "templateId" to s.templateId,
        "reviewFrequency" to s.reviewFrequency, "createdAt" to s.createdAt
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
        "protectedRoutine" to h.protectedRoutine,
        "rewardSatisfaction" to h.rewardSatisfaction, "rewardLastRated" to h.rewardLastRated,
        "reframeHelpful" to h.reframeHelpful, "bundleEffectiveness" to h.bundleEffectiveness,
        "frictionPlanActive" to h.frictionPlanActive,
        "environmentPrepReminderTime" to h.environmentPrepReminderTime,
        "ladderHistory" to jsonArrayFromObjects(h.ladderHistory.map { l ->
            org.json.JSONObject().apply {
                put("level", l.level.name)
                put("previousText", l.previousText)
                put("newText", l.newText)
                put("reason", l.reason)
                put("date", l.date)
            }
        }),
        "lastDifficultyRating" to h.lastDifficultyRating,
        "stretchCount" to h.stretchCount, "consecutiveStandards" to h.consecutiveStandards,
        "estimatedMinutes" to h.estimatedMinutes, "difficultyRating" to h.difficultyRating,
        "colorSeed" to h.colorSeed, "orderIndex" to h.orderIndex,
        "status" to h.status.name,
        "graduated" to h.graduated, "graduatedAt" to h.graduatedAt,
        "createdAt" to h.createdAt
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
        db.delete("evidence", "sourceHabitId=?", arrayOf(id))
        // Flow steps reference habits by id but survive the habit's deletion;
        // they are simply detached so a flow can outlive an edited habit.
        db.execSQL("UPDATE flowstep SET habitId=NULL WHERE habitId=?", arrayOf(id))
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
        habits().filter {
            it.status == Status.ACTIVE && !it.graduated && scheduleOf(it).activeOn(date)
        }

    fun habitsForDay(snap: DataSnapshot, date: LocalDate): List<Habit> =
        snap.activeHabits.filter { scheduleOf(it).activeOn(date) }

    /** Today's habits joined with their check-ins, ready for the list adapter. */
    fun todayHabits(date: LocalDate): List<TodayHabit> = todayHabits(snapshot(), date)

    fun todayHabits(snap: DataSnapshot, date: LocalDate): List<TodayHabit> {
        val iso = SfTime.format(date)
        val dayCheckIns = snap.checkIns.filter { it.date == iso }.associateBy { it.habitId }
        val returning = com.superflow.domain.Insights.returnCandidates(snap, this, date).map { it.id }.toSet()
        return habitsForDay(snap, date).map { h -> TodayHabit(h, dayCheckIns[h.id], h.id in returning) }
    }

    /** Habits missed at their previous real opportunity: the never-miss-twice trigger. */
    fun returnCandidates(date: LocalDate): List<Habit> =
        com.superflow.domain.Insights.returnCandidates(snapshot(), this, date)

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
            "contextTags" to jsonArrayOfStrings(ci.contextTags),
            "actualAmount" to ci.actualAmount,
            "actualDurationMinutes" to ci.actualDurationMinutes,
            "qualityRating" to ci.qualityRating,
            "difficultyRating" to ci.difficultyRating,
            "missReason" to ci.missReason, "missReasonDetail" to ci.missReasonDetail,
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
        "done" to f.done, "isPriority" to f.isPriority, "goalId" to f.goalId,
        "estimatedMinutes" to f.estimatedMinutes, "carryOverCount" to f.carryOverCount,
        "orderIndex" to f.orderIndex
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
        "thenText" to o.thenText, "category" to o.category,
        "timesUsed" to o.timesUsed, "lastUsed" to o.lastUsed,
        "effectiveness" to o.effectiveness, "createdAt" to o.createdAt
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
        "id" to f.id, "title" to f.title, "anchor" to f.anchor,
        "estimatedMinutes" to f.estimatedMinutes, "completionCount" to f.completionCount,
        "partialCount" to f.partialCount, "createdAt" to f.createdAt
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
        "existingBehaviour" to s.existingBehaviour, "durationMinutes" to s.durationMinutes,
        "isBreakpoint" to s.isBreakpoint, "orderIndex" to s.orderIndex
    ))

    fun deleteFlowStep(id: String) = delete("flowstep", "id=?", arrayOf(id))

    /* -------------------------------------------------------------- review */

    fun reviews(): List<Review> =
        query("SELECT * FROM review ORDER BY createdAt DESC").mapAll(Rows::review)

    fun saveReview(r: Review) = insert("review", contentValuesOf(
        "id" to r.id, "kind" to r.kind.name, "periodLabel" to r.periodLabel,
        "whatWorked" to r.whatWorked, "whatDidnt" to r.whatDidnt, "systemChange" to r.systemChange,
        "identityEvidence" to r.identityEvidence, "autoGeneratedData" to r.autoGeneratedData,
        "actionItems" to jsonArrayFromObjects(r.actionItems.map { ai ->
            org.json.JSONObject().apply {
                put("id", ai.id)
                put("text", ai.text)
                put("completed", ai.completed)
                put("completedDate", ai.completedDate)
                put("linkedCommand", ai.linkedCommand)
                put("outcome", ai.outcome)
            }
        }),
        "previousReviewId" to r.previousReviewId,
        "createdAt" to r.createdAt
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

    /* ------------------------------------------------------------ evidence */

    fun evidence(identityId: String? = null): List<IdentityEvidence> =
        if (identityId == null) query("SELECT * FROM evidence ORDER BY date DESC").mapAll(Rows::evidence)
        else query("SELECT * FROM evidence WHERE identityId=? ORDER BY date DESC", arrayOf(identityId))
            .mapAll(Rows::evidence)

    fun saveEvidence(e: IdentityEvidence) = insert("evidence", contentValuesOf(
        "id" to e.id, "identityId" to e.identityId, "text" to e.text,
        "sourceHabitId" to e.sourceHabitId, "date" to e.date, "createdAt" to e.createdAt
    ))

    fun deleteEvidence(id: String) = delete("evidence", "id=?", arrayOf(id))

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

    /* ────────────────────────────────────────────────────── NEW ENTITIES ── */

    /* -------------------------------------------------------- growth plans */

    fun growthPlans(): List<GrowthPlan> =
        query("SELECT * FROM growth_plan ORDER BY created_at").mapAll(Rows::growthPlan)

    fun growthPlan(id: String?): GrowthPlan? =
        if (id == null) null
        else query("SELECT * FROM growth_plan WHERE id=?", arrayOf(id)).mapAll(Rows::growthPlan).firstOrNull()

    fun growthPlansForHabit(habitId: String): List<GrowthPlan> =
        query("SELECT * FROM growth_plan WHERE habit_id=? ORDER BY created_at", arrayOf(habitId))
            .mapAll(Rows::growthPlan)

    fun saveGrowthPlan(g: GrowthPlan) = insert("growth_plan", contentValuesOf(
        "id" to g.id, "habit_id" to g.habitId, "user_id" to g.userId,
        "phases_json" to phasesToJson(g.phases),
        "current_phase_index" to g.currentPhaseIndex,
        "upgrade_policy_json" to upgradePolicyToJson(g.upgradePolicy),
        "weekly_snapshots_json" to snapshotsToJson(g.weeklySnapshots),
        "last_upgrade_date" to g.lastUpgradeDate,
        "next_review_date" to g.nextReviewDate,
        "created_at" to g.createdAt
    ))

    fun deleteGrowthPlan(id: String) {
        delete("growth_plan", "id=?", arrayOf(id))
        delete("growth_phase_history", "growth_plan_id=?", arrayOf(id))
    }

    /* ----------------------------------------------------------- milestones */

    fun milestones(): List<Milestone> =
        query("SELECT * FROM milestone ORDER BY achieved_at DESC").mapAll(Rows::milestone)

    fun milestonesForHabit(habitId: String): List<Milestone> =
        query("SELECT * FROM milestone WHERE habit_id=? ORDER BY achieved_at DESC", arrayOf(habitId))
            .mapAll(Rows::milestone)

    fun saveMilestone(m: Milestone) = insert("milestone", contentValuesOf(
        "id" to m.id, "habit_id" to m.habitId, "type" to m.type.name,
        "value" to m.value, "label" to m.label, "acknowledged" to m.acknowledged,
        "achieved_at" to m.achievedAt
    ))

    fun deleteMilestone(id: String) = delete("milestone", "id=?", arrayOf(id))

    /* -------------------------------------------------------------- sprints */

    fun sprints(): List<Sprint> =
        query("SELECT * FROM sprint ORDER BY created_at DESC").mapAll(Rows::sprint)

    fun sprint(id: String?): Sprint? =
        if (id == null) null
        else query("SELECT * FROM sprint WHERE id=?", arrayOf(id)).mapAll(Rows::sprint).firstOrNull()

    fun saveSprint(s: Sprint) = insert("sprint", contentValuesOf(
        "id" to s.id, "title" to s.title, "start_date" to s.startDate,
        "end_date" to s.endDate, "focus_habits_json" to strListToJson(s.focusHabits),
        "goals_json" to strListToJson(s.goals), "status" to s.status.name,
        "review_notes" to s.reviewNotes, "created_at" to s.createdAt
    ))

    fun deleteSprint(id: String) = delete("sprint", "id=?", arrayOf(id))

    /* --------------------------------------------------------- journal */

    fun journalEntries(): List<JournalEntry> =
        query("SELECT * FROM journal_entry ORDER BY created_at DESC").mapAll(Rows::journalEntry)

    fun journalEntriesFor(date: String): List<JournalEntry> =
        query("SELECT * FROM journal_entry WHERE date=? ORDER BY created_at", arrayOf(date))
            .mapAll(Rows::journalEntry)

    fun journalEntry(id: String?): JournalEntry? =
        if (id == null) null
        else query("SELECT * FROM journal_entry WHERE id=?", arrayOf(id)).mapAll(Rows::journalEntry).firstOrNull()

    fun saveJournalEntry(e: JournalEntry) = insert("journal_entry", contentValuesOf(
        "id" to e.id, "date" to e.date, "prompt" to e.prompt, "content" to e.content,
        "mood" to e.mood, "tags_json" to strListToJson(e.tags), "created_at" to e.createdAt
    ))

    fun deleteJournalEntry(id: String) = delete("journal_entry", "id=?", arrayOf(id))

    /* --------------------------------------------- quick capture inbox (alpha3) */

    /** Open items first (newest last, so triage reads chronologically), then everything else. */
    fun capturedItems(): List<CapturedItem> =
        query(
            """SELECT * FROM captured_item
               ORDER BY CASE state WHEN 'OPEN' THEN 0 ELSE 1 END, created_at DESC"""
        ).mapAll(Rows::capturedItem)

    fun capturedItems(state: CaptureState): List<CapturedItem> =
        query("SELECT * FROM captured_item WHERE state=? ORDER BY created_at DESC", arrayOf(state.name))
            .mapAll(Rows::capturedItem)

    fun openCaptureCount(): Int =
        query("SELECT COUNT(*) FROM captured_item WHERE state='OPEN'")
            .mapAll { it.getInt(0) }.firstOrNull() ?: 0

    fun capturedItem(id: String?): CapturedItem? =
        if (id == null) null
        else query("SELECT * FROM captured_item WHERE id=?", arrayOf(id))
            .mapAll(Rows::capturedItem).firstOrNull()

    fun saveCapturedItem(item: CapturedItem) = insert("captured_item", contentValuesOf(
        "id" to item.id, "text" to item.text, "kind" to item.kind.name,
        "source" to item.source.name, "state" to item.state.name,
        "converted_to_id" to item.convertedToId,
        "created_at" to item.createdAt, "updated_at" to item.updatedAt,
    ))

    fun deleteCapturedItem(id: String) = delete("captured_item", "id=?", arrayOf(id))

    /* ------------------------------------------------------------- routines */

    fun routines(): List<Routine> =
        query("SELECT * FROM routine ORDER BY created_at").mapAll(Rows::routine)

    fun routine(id: String?): Routine? =
        if (id == null) null
        else query("SELECT * FROM routine WHERE id=?", arrayOf(id)).mapAll(Rows::routine).firstOrNull()

    fun saveRoutine(r: Routine) = insert("routine", contentValuesOf(
        "id" to r.id, "title" to r.title, "trigger_text" to r.trigger,
        "estimated_minutes" to r.estimatedMinutes, "status" to r.status.name,
        "created_at" to r.createdAt
    ))

    fun deleteRoutine(id: String) {
        delete("routine", "id=?", arrayOf(id))
        delete("routine_step", "routine_id=?", arrayOf(id))
    }

    fun routineSteps(routineId: String): List<RoutineStep> =
        query("SELECT * FROM routine_step WHERE routine_id=? ORDER BY order_index", arrayOf(routineId))
            .mapAll(Rows::routineStep)

    fun saveRoutineStep(s: RoutineStep) = insert("routine_step", contentValuesOf(
        "id" to s.id, "routine_id" to s.routineId, "habit_id" to s.habitId,
        "title" to s.title, "duration_minutes" to s.durationMinutes,
        "order_index" to s.orderIndex, "transition_note" to s.transitionNote
    ))

    fun deleteRoutineStep(id: String) = delete("routine_step", "id=?", arrayOf(id))

    /* ---------------------------------------------------- environment design */

    fun environmentDesign(habitId: String): EnvironmentDesign? =
        query("SELECT * FROM environment_design WHERE habit_id=?", arrayOf(habitId))
            .mapAll(Rows::environmentDesign).firstOrNull()

    fun saveEnvironmentDesign(e: EnvironmentDesign) = insert("environment_design", contentValuesOf(
        "habit_id" to e.habitId,
        "make_obvious_json" to strListToJson(e.makeObvious),
        "make_attractive_json" to strListToJson(e.makeAttractive),
        "make_easy_json" to strListToJson(e.makeEasy),
        "make_satisfying_json" to strListToJson(e.makeSatisfying),
        "make_invisible_json" to strListToJson(e.makeInvisible),
        "make_unattractive_json" to strListToJson(e.makeUnattractive),
        "make_difficult_json" to strListToJson(e.makeDifficult),
        "make_unsatisfying_json" to strListToJson(e.makeUnsatisfying)
    ))

    fun deleteEnvironmentDesign(habitId: String) =
        delete("environment_design", "habit_id=?", arrayOf(habitId))

    /* ------------------------------------------------------------- ai memory */

    fun memories(category: MemoryCategory? = null): List<AiMemory> =
        if (category == null)
            query("SELECT * FROM ai_memory ORDER BY created_at DESC").mapAll(Rows::aiMemory)
        else
            query("SELECT * FROM ai_memory WHERE category=? ORDER BY created_at DESC", arrayOf(category.name))
                .mapAll(Rows::aiMemory)

    fun saveMemory(m: AiMemory) = insert("ai_memory", contentValuesOf(
        "id" to m.id, "category" to m.category.name, "content" to m.content,
        "importance" to m.importance, "last_accessed" to m.lastAccessed,
        "access_count" to m.accessCount, "created_at" to m.createdAt
    ))

    fun deleteMemory(id: String) = delete("ai_memory", "id=?", arrayOf(id))

    fun touchMemory(id: String) {
        db.execSQL("UPDATE ai_memory SET last_accessed=?, access_count=access_count+1 WHERE id=?",
            arrayOf(System.currentTimeMillis(), id))
        invalidate()
    }

    /* --------------------------------------------------- proactive suggestions */

    fun proactiveSuggestions(includeDismissed: Boolean = false): List<ProactiveSuggestion> =
        query(
            "SELECT * FROM proactive_suggestion ${if (includeDismissed) "" else "WHERE dismissed=0"} ORDER BY created_at DESC"
        ).mapAll(Rows::proactiveSuggestion)

    fun saveProactiveSuggestion(s: ProactiveSuggestion) = insert("proactive_suggestion", contentValuesOf(
        "id" to s.id, "type" to s.type.name, "text" to s.text,
        "priority" to s.priority.name, "auto_action_json" to s.autoActionJson,
        "habit_id" to s.habitId, "dismissed" to s.dismissed,
        "applied" to s.applied, "created_at" to s.createdAt
    ))

    fun dismissProactiveSuggestion(id: String) {
        db.execSQL("UPDATE proactive_suggestion SET dismissed=1 WHERE id=?", arrayOf(id))
        invalidate()
    }

    fun applyProactiveSuggestion(id: String) {
        db.execSQL("UPDATE proactive_suggestion SET applied=1 WHERE id=?", arrayOf(id))
        invalidate()
    }

    /* ------------------------------------------------- growth phase history */

    fun growthPhaseHistories(growthPlanId: String): List<GrowthPhaseHistory> =
        query("SELECT * FROM growth_phase_history WHERE growth_plan_id=? ORDER BY created_at",
            arrayOf(growthPlanId)).mapAll(Rows::growthPhaseHistory)

    fun saveGrowthPhaseHistory(h: GrowthPhaseHistory) = insert("growth_phase_history", contentValuesOf(
        "id" to h.id, "growth_plan_id" to h.growthPlanId, "phase_index" to h.phaseIndex,
        "action" to h.action, "consistency" to h.consistency,
        "date" to h.date, "notes" to h.notes
    ))

    /* ----------------------------------------------------------------- data */

    /** Every table in the schema, in child-before-parent delete order. */
    private val allTables = listOf(
        "checkin", "focus", "obstacle", "scorecard", "flowstep", "flow", "review",
        "energy", "evidence", "growth_phase_history", "routine_step", "routine",
        "journal_entry", "milestone", "growth_plan", "sprint",
        "proactive_suggestion", "ai_memory", "environment_design",
        "bp_req", "bp_source", "bp_version", "bp_project", "pause",
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

    /* ───────────────────────────────────────────────────── JSON helpers ── */

    private fun phasesToJson(phases: List<GrowthPhase>): String {
        val arr = org.json.JSONArray()
        for (p in phases) {
            val metrics = org.json.JSONObject().apply {
                put("minConsistency", p.metrics.minConsistency)
                put("minRecoveries", p.metrics.minRecoveries)
                put("maxMissesInARow", p.metrics.maxMissesInARow)
                put("minEnergy", p.metrics.minEnergy)
            }
            arr.put(org.json.JSONObject().apply {
                put("weekNumber", p.weekNumber)
                put("label", p.label)
                put("tinyStart", p.tinyStart)
                put("minimumVersion", p.minimumVersion)
                put("standardVersion", p.standardVersion)
                put("stretchVersion", p.stretchVersion)
                put("targetDays", p.targetDays)
                put("notes", p.notes)
                put("metrics", metrics)
            })
        }
        return arr.toString()
    }

    private fun upgradePolicyToJson(p: UpgradePolicy): String =
        org.json.JSONObject().apply {
            put("autoUpgrade", p.autoUpgrade)
            put("upgradeDay", p.upgradeDay)
            put("minWeeksInPhase", p.minWeeksInPhase)
            put("maxWeeksInPhase", p.maxWeeksInPhase)
            put("downgradeOnStruggle", p.downgradeOnStruggle)
            put("struggleThreshold", p.struggleThreshold)
        }.toString()

    private fun snapshotsToJson(snapshots: List<WeeklySnapshot>): String {
        val arr = org.json.JSONArray()
        for (s in snapshots) {
            arr.put(org.json.JSONObject().apply {
                put("weekNumber", s.weekNumber)
                put("phaseIndex", s.phaseIndex)
                put("consistency", s.consistency)
                put("repetitions", s.repetitions)
                put("misses", s.misses)
                put("recoveries", s.recoveries)
                put("averageEnergy", s.averageEnergy)
                put("decision", s.decision.name)
                put("date", s.date)
            })
        }
        return arr.toString()
    }

    private fun strListToJson(list: List<String>): String {
        val arr = org.json.JSONArray()
        list.forEach { arr.put(it) }
        return arr.toString()
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
