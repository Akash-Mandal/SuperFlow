package com.superflow.domain

import com.superflow.data.Repository
import com.superflow.data.model.*
import com.superflow.util.jsonOf
import com.superflow.util.objects
import com.superflow.util.string
import com.superflow.util.stringOrNull
import org.json.JSONArray
import org.json.JSONObject

/**
 * Row <-> JSON conversion. One place so undo payloads, snapshots and the
 * user-facing data export all use the same representation.
 */
object Serial {

    const val EXPORT_VERSION = 2

    /* -------------------------------------------------------------- to JSON */

    fun of(i: Identity): JSONObject = jsonOf(
        "table" to "identity", "id" to i.id, "statement" to i.statement,
        "lifeArea" to i.lifeArea.name, "status" to i.status.name, "createdAt" to i.createdAt
    )

    fun of(g: Goal): JSONObject = jsonOf(
        "table" to "goal", "id" to g.id, "identityId" to g.identityId, "title" to g.title,
        "why" to g.why, "outcomeMetric" to g.outcomeMetric, "targetValue" to g.targetValue,
        "targetDate" to g.targetDate, "status" to g.status.name, "createdAt" to g.createdAt
    )

    fun of(s: Sys): JSONObject = jsonOf(
        "table" to "sys", "id" to s.id, "goalId" to s.goalId, "title" to s.title,
        "description" to s.description, "status" to s.status.name, "createdAt" to s.createdAt
    )

    fun of(h: Habit): JSONObject = jsonOf(
        "table" to "habit", "id" to h.id, "systemId" to h.systemId, "identityId" to h.identityId,
        "title" to h.title, "mode" to h.mode.name, "trackType" to h.trackType.name,
        "targetCount" to h.targetCount, "unit" to h.unit, "cueTime" to h.cueTime,
        "cuePlace" to h.cuePlace, "anchorHabitId" to h.anchorHabitId, "anchorText" to h.anchorText,
        "benefit" to h.benefit, "temptationBundle" to h.temptationBundle, "reframe" to h.reframe,
        "tinyStart" to h.tinyStart, "minimumVersion" to h.minimumVersion,
        "standardVersion" to h.standardVersion, "stretchVersion" to h.stretchVersion,
        "frictionPlan" to h.frictionPlan, "environmentPrep" to h.environmentPrep,
        "reward" to h.reward, "recoveryPlan" to h.recoveryPlan,
        "recurrenceRule" to h.recurrenceRule, "scheduleVersion" to h.scheduleVersion,
        "startDate" to h.startDate, "endDate" to h.endDate,
        "reminderEnabled" to h.reminderEnabled, "protectedRoutine" to h.protectedRoutine,
        "colorSeed" to h.colorSeed, "orderIndex" to h.orderIndex,
        "status" to h.status.name, "createdAt" to h.createdAt
    )

    fun of(c: CheckIn): JSONObject = jsonOf(
        "table" to "checkin", "id" to c.id, "habitId" to c.habitId, "date" to c.date,
        "result" to c.result.name, "level" to c.level.name, "amount" to c.amount,
        "note" to c.note, "createdAt" to c.createdAt
    )

    fun of(f: FocusItem): JSONObject = jsonOf(
        "table" to "focus", "id" to f.id, "date" to f.date, "habitId" to f.habitId,
        "title" to f.title, "done" to f.done, "orderIndex" to f.orderIndex
    )

    fun of(o: ObstaclePlan): JSONObject = jsonOf(
        "table" to "obstacle", "id" to o.id, "habitId" to o.habitId, "ifText" to o.ifText,
        "thenText" to o.thenText, "createdAt" to o.createdAt
    )

    fun of(e: ScorecardEntry): JSONObject = jsonOf(
        "table" to "scorecard", "id" to e.id, "routine" to e.routine, "verdict" to e.verdict,
        "note" to e.note, "createdAt" to e.createdAt
    )

    fun of(f: Flow): JSONObject = jsonOf(
        "table" to "flow", "id" to f.id, "title" to f.title, "anchor" to f.anchor,
        "createdAt" to f.createdAt
    )

    fun of(s: FlowStep): JSONObject = jsonOf(
        "table" to "flowstep", "id" to s.id, "flowId" to s.flowId, "habitId" to s.habitId,
        "title" to s.title, "existingBehaviour" to s.existingBehaviour, "orderIndex" to s.orderIndex
    )

    fun of(r: Review): JSONObject = jsonOf(
        "table" to "review", "id" to r.id, "kind" to r.kind.name, "periodLabel" to r.periodLabel,
        "whatWorked" to r.whatWorked, "whatDidnt" to r.whatDidnt, "systemChange" to r.systemChange,
        "identityEvidence" to r.identityEvidence, "createdAt" to r.createdAt
    )

    fun of(e: EnergyLog): JSONObject = jsonOf(
        "table" to "energy", "id" to e.id, "date" to e.date, "checkpoint" to e.checkpoint.name,
        "energy" to e.energy, "note" to e.note, "createdAt" to e.createdAt
    )

    fun of(p: PauseWindow): JSONObject = jsonOf(
        "table" to "pause", "id" to p.id, "habitId" to p.habitId,
        "startDate" to p.startDate, "endDate" to p.endDate,
        "reason" to p.reason, "createdAt" to p.createdAt
    )

    fun of(p: BlueprintProject): JSONObject = jsonOf(
        "table" to "bp_project", "id" to p.id, "name" to p.name, "instructions" to p.instructions,
        "version" to p.version, "state" to p.state, "parentVersionId" to p.parentVersionId,
        "createdAt" to p.createdAt
    )

    fun of(s: BlueprintSource): JSONObject = jsonOf(
        "table" to "bp_source", "id" to s.id, "projectId" to s.projectId, "name" to s.name,
        "kind" to s.kind, "content" to s.content, "instructions" to s.instructions,
        "lineCount" to s.lineCount, "createdAt" to s.createdAt
    )

    fun of(r: Requirement): JSONObject = jsonOf(
        "table" to "bp_req", "id" to r.id, "projectId" to r.projectId, "text" to r.text,
        "sourceId" to r.sourceId, "citation" to r.citation, "status" to r.status.name,
        "assumption" to r.assumption, "plannedCommand" to r.plannedCommand,
        "note" to r.note, "orderIndex" to r.orderIndex
    )

    /* ------------------------------------------------------------ from JSON */

    private fun long(o: JSONObject, k: String, def: Long = System.currentTimeMillis()): Long =
        if (o.isNull(k)) def else o.optLong(k, def)

    fun identity(o: JSONObject) = Identity(
        o.string("id", newId()), o.string("statement"), LifeArea.from(o.string("lifeArea")),
        Status.valueOf(o.string("status", "ACTIVE")), long(o, "createdAt")
    )

    fun goal(o: JSONObject) = Goal(
        o.string("id", newId()), o.stringOrNull("identityId"), o.string("title"), o.string("why"),
        o.string("outcomeMetric"),
        if (o.isNull("targetValue")) null else o.optDouble("targetValue"),
        if (o.isNull("targetDate")) null else o.optLong("targetDate"),
        GoalStatus.valueOf(o.string("status", "ACTIVE")), long(o, "createdAt")
    )

    fun system(o: JSONObject) = Sys(
        o.string("id", newId()), o.stringOrNull("goalId"), o.string("title"),
        o.string("description"), Status.valueOf(o.string("status", "ACTIVE")), long(o, "createdAt")
    )

    fun habit(o: JSONObject) = Habit(
        id = o.string("id", newId()), systemId = o.stringOrNull("systemId"),
        identityId = o.stringOrNull("identityId"), title = o.string("title"),
        mode = HabitMode.valueOf(o.string("mode", "BUILD")),
        trackType = TrackType.valueOf(o.string("trackType", "BINARY")),
        targetCount = o.optInt("targetCount", 1), unit = o.string("unit"),
        cueTime = o.string("cueTime"), cuePlace = o.string("cuePlace"),
        anchorHabitId = o.stringOrNull("anchorHabitId"), anchorText = o.string("anchorText"),
        benefit = o.string("benefit"), temptationBundle = o.string("temptationBundle"),
        reframe = o.string("reframe"), tinyStart = o.string("tinyStart"),
        minimumVersion = o.string("minimumVersion"), standardVersion = o.string("standardVersion"),
        stretchVersion = o.string("stretchVersion"), frictionPlan = o.string("frictionPlan"),
        environmentPrep = o.string("environmentPrep"), reward = o.string("reward"),
        recoveryPlan = o.string("recoveryPlan"),
        recurrenceRule = o.string("recurrenceRule").ifBlank {
            if (o.has("daysMask"))
                com.superflow.core.schedule.Recurrence.fromMask(o.optInt("daysMask", 127)).encode()
            else "WEEKLY:1,2,3,4,5,6,7"
        },
        scheduleVersion = o.optInt("scheduleVersion", 1),
        startDate = o.string("startDate"), endDate = o.stringOrNull("endDate"),
        reminderEnabled = o.optBoolean("reminderEnabled", false),
        protectedRoutine = o.optBoolean("protectedRoutine", false),
        colorSeed = o.optInt("colorSeed", 0),
        orderIndex = o.optInt("orderIndex", 0),
        status = Status.valueOf(o.string("status", "ACTIVE")), createdAt = long(o, "createdAt")
    )

    fun checkIn(o: JSONObject) = CheckIn(
        o.string("id", newId()), o.string("habitId"), o.string("date"),
        CheckInResult.valueOf(o.string("result", "DONE")), Level.from(o.string("level")),
        o.optDouble("amount", 0.0), o.string("note"), long(o, "createdAt")
    )

    fun focus(o: JSONObject) = FocusItem(
        o.string("id", newId()), o.string("date"), o.stringOrNull("habitId"),
        o.string("title"), o.optBoolean("done", false), o.optInt("orderIndex", 0)
    )

    fun obstacle(o: JSONObject) = ObstaclePlan(
        o.string("id", newId()), o.string("habitId"), o.string("ifText"),
        o.string("thenText"), long(o, "createdAt")
    )

    fun scorecard(o: JSONObject) = ScorecardEntry(
        o.string("id", newId()), o.string("routine"), o.optInt("verdict", 0),
        o.string("note"), long(o, "createdAt")
    )

    fun flow(o: JSONObject) = Flow(
        o.string("id", newId()), o.string("title"), o.string("anchor"), long(o, "createdAt")
    )

    fun flowStep(o: JSONObject) = FlowStep(
        o.string("id", newId()), o.string("flowId"), o.stringOrNull("habitId"), o.string("title"),
        o.optBoolean("existingBehaviour", false), o.optInt("orderIndex", 0)
    )

    fun review(o: JSONObject) = Review(
        o.string("id", newId()), ReviewKind.valueOf(o.string("kind", "WEEKLY")),
        o.string("periodLabel"), o.string("whatWorked"), o.string("whatDidnt"),
        o.string("systemChange"), o.string("identityEvidence"), long(o, "createdAt")
    )

    fun energy(o: JSONObject) = EnergyLog(
        o.string("id", newId()), o.string("date"),
        Checkpoint.valueOf(o.string("checkpoint", "MORNING")),
        o.optInt("energy", 3), o.string("note"), long(o, "createdAt")
    )

    fun pause(o: JSONObject) = PauseWindow(
        o.string("id", newId()), o.stringOrNull("habitId"), o.string("startDate"),
        o.string("endDate"), o.string("reason"), long(o, "createdAt")
    )

    fun project(o: JSONObject) = BlueprintProject(
        o.string("id", newId()), o.string("name"), o.string("instructions"),
        o.optInt("version", 1), o.string("state", "DRAFT"),
        o.stringOrNull("parentVersionId"), long(o, "createdAt")
    )

    fun source(o: JSONObject) = BlueprintSource(
        o.string("id", newId()), o.string("projectId"), o.string("name"), o.string("kind", "text"),
        o.string("content"), o.string("instructions"), o.optInt("lineCount", 0), long(o, "createdAt")
    )

    fun requirement(o: JSONObject) = Requirement(
        o.string("id", newId()), o.string("projectId"), o.string("text"), o.stringOrNull("sourceId"),
        o.string("citation"), RequirementStatus.valueOf(o.string("status", "ACCEPTED")),
        o.optBoolean("assumption", false), o.string("plannedCommand"), o.string("note"),
        o.optInt("orderIndex", 0)
    )

    /* ----------------------------------------------------------- whole store */

    fun exportAll(repo: Repository): JSONObject {
        fun arr(items: List<JSONObject>) = JSONArray().also { a -> items.forEach { a.put(it) } }
        val root = JSONObject()
        root.put("app", "SuperFlow")
        root.put("exportVersion", EXPORT_VERSION)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("identities", arr(repo.identities(true).map { of(it) }))
        root.put("goals", arr(repo.goals().map { of(it) }))
        root.put("systems", arr(repo.systems().map { of(it) }))
        root.put("habits", arr(repo.habits(true).map { of(it) }))
        root.put("checkIns", arr(repo.checkIns().map { of(it) }))
        root.put("focus", arr(repo.focusAll().map { of(it) }))
        root.put("obstacles", arr(repo.obstacles().map { of(it) }))
        root.put("scorecard", arr(repo.scorecard().map { of(it) }))
        root.put("flows", arr(repo.flows().map { of(it) }))
        root.put("flowSteps", arr(repo.flows().flatMap { repo.flowSteps(it.id) }.map { of(it) }))
        root.put("reviews", arr(repo.reviews().map { of(it) }))
        root.put("energy", arr(repo.energyLogs().map { of(it) }))
        root.put("pauses", arr(repo.pauses().map { of(it) }))
        val projects = repo.projects()
        root.put("projects", arr(projects.map { of(it) }))
        root.put("sources", arr(projects.flatMap { repo.sources(it.id) }.map { of(it) }))
        root.put("requirements", arr(projects.flatMap { repo.requirements(it.id) }.map { of(it) }))
        return root
    }

    fun importAll(repo: Repository, root: JSONObject) {
        repo.deleteAllData()
        root.optJSONArray("identities")?.objects()?.forEach { repo.saveIdentity(identity(it)) }
        root.optJSONArray("goals")?.objects()?.forEach { repo.saveGoal(goal(it)) }
        root.optJSONArray("systems")?.objects()?.forEach { repo.saveSystem(system(it)) }
        root.optJSONArray("habits")?.objects()?.forEach { repo.saveHabit(habit(it)) }
        root.optJSONArray("checkIns")?.objects()?.forEach { repo.saveCheckIn(checkIn(it)) }
        root.optJSONArray("focus")?.objects()?.forEach { repo.saveFocus(focus(it)) }
        root.optJSONArray("obstacles")?.objects()?.forEach { repo.saveObstacle(obstacle(it)) }
        root.optJSONArray("scorecard")?.objects()?.forEach { repo.saveScorecard(scorecard(it)) }
        root.optJSONArray("flows")?.objects()?.forEach { repo.saveFlow(flow(it)) }
        root.optJSONArray("flowSteps")?.objects()?.forEach { repo.saveFlowStep(flowStep(it)) }
        root.optJSONArray("reviews")?.objects()?.forEach { repo.saveReview(review(it)) }
        root.optJSONArray("energy")?.objects()?.forEach { repo.saveEnergy(energy(it)) }
        root.optJSONArray("pauses")?.objects()?.forEach { repo.savePause(pause(it)) }
        root.optJSONArray("projects")?.objects()?.forEach { repo.saveProject(project(it)) }
        root.optJSONArray("sources")?.objects()?.forEach { repo.saveSource(source(it)) }
        root.optJSONArray("requirements")?.objects()?.forEach { repo.saveRequirement(requirement(it)) }
    }
}
