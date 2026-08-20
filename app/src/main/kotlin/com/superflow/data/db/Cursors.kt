package com.superflow.data.db

import android.database.Cursor
import com.superflow.data.model.*
import org.json.JSONArray
import org.json.JSONObject

/** Cursor read helpers plus row mappers, kept in one place. */

fun Cursor.str(name: String): String =
    getColumnIndex(name).let { if (it < 0 || isNull(it)) "" else getString(it) }

fun Cursor.strOrNull(name: String): String? =
    getColumnIndex(name).let { if (it < 0 || isNull(it)) null else getString(it) }

fun Cursor.int(name: String): Int =
    getColumnIndex(name).let { if (it < 0 || isNull(it)) 0 else getInt(it) }

fun Cursor.lng(name: String): Long =
    getColumnIndex(name).let { if (it < 0 || isNull(it)) 0L else getLong(it) }

fun Cursor.lngOrNull(name: String): Long? =
    getColumnIndex(name).let { if (it < 0 || isNull(it)) null else getLong(it) }

fun Cursor.dbl(name: String): Double =
    getColumnIndex(name).let { if (it < 0 || isNull(it)) 0.0 else getDouble(it) }

fun Cursor.dblOrNull(name: String): Double? =
    getColumnIndex(name).let { if (it < 0 || isNull(it)) null else getDouble(it) }

fun Cursor.bool(name: String): Boolean = int(name) == 1

inline fun <T> Cursor.mapAll(block: (Cursor) -> T): List<T> {
    val out = ArrayList<T>()
    use { c -> while (c.moveToNext()) out.add(block(c)) }
    return out
}

/* ------------------------------------------------------------------ mappers */

object Rows {

    fun identity(c: Cursor) = Identity(
        c.str("id"), c.str("statement"), LifeArea.from(c.str("lifeArea")),
        Status.valueOf(c.str("status").ifBlank { "ACTIVE" }), c.lng("createdAt")
    )

    fun goal(c: Cursor) = Goal(
        c.str("id"), c.strOrNull("identityId"), c.str("title"), c.str("why"),
        c.str("outcomeMetric"), c.dblOrNull("targetValue"), c.lngOrNull("targetDate"),
        GoalStatus.valueOf(c.str("status").ifBlank { "ACTIVE" }), c.lng("createdAt")
    )

    fun system(c: Cursor) = Sys(
        c.str("id"), c.strOrNull("goalId"), c.str("title"), c.str("description"),
        Status.valueOf(c.str("status").ifBlank { "ACTIVE" }), c.lng("createdAt")
    )

    fun habit(c: Cursor) = Habit(
        id = c.str("id"), systemId = c.strOrNull("systemId"), identityId = c.strOrNull("identityId"),
        title = c.str("title"), mode = HabitMode.valueOf(c.str("mode").ifBlank { "BUILD" }),
        trackType = TrackType.valueOf(c.str("trackType").ifBlank { "BINARY" }),
        targetCount = c.int("targetCount"), unit = c.str("unit"),
        cueTime = c.str("cueTime"), cuePlace = c.str("cuePlace"),
        anchorHabitId = c.strOrNull("anchorHabitId"), anchorText = c.str("anchorText"),
        benefit = c.str("benefit"), temptationBundle = c.str("temptationBundle"),
        reframe = c.str("reframe"), tinyStart = c.str("tinyStart"),
        minimumVersion = c.str("minimumVersion"), standardVersion = c.str("standardVersion"),
        stretchVersion = c.str("stretchVersion"), frictionPlan = c.str("frictionPlan"),
        environmentPrep = c.str("environmentPrep"), reward = c.str("reward"),
        recoveryPlan = c.str("recoveryPlan"),
        recurrenceRule = c.str("recurrenceRule").ifBlank { "WEEKLY:1,2,3,4,5,6,7" },
        scheduleVersion = c.int("scheduleVersion").coerceAtLeast(1),
        startDate = c.str("startDate"), endDate = c.strOrNull("endDate"),
        reminderEnabled = c.bool("reminderEnabled"), protectedRoutine = c.bool("protectedRoutine"),
        colorSeed = c.int("colorSeed"), orderIndex = c.int("orderIndex"),
        status = Status.valueOf(c.str("status").ifBlank { "ACTIVE" }), createdAt = c.lng("createdAt")
    )

    fun checkIn(c: Cursor) = CheckIn(
        c.str("id"), c.str("habitId"), c.str("date"),
        CheckInResult.valueOf(c.str("result").ifBlank { "DONE" }),
        Level.from(c.str("level")), c.dbl("amount"), c.str("note"), c.lng("createdAt")
    )

    fun focus(c: Cursor) = FocusItem(
        c.str("id"), c.str("date"), c.strOrNull("habitId"), c.str("title"),
        c.bool("done"), c.int("orderIndex")
    )

    fun obstacle(c: Cursor) = ObstaclePlan(
        c.str("id"), c.str("habitId"), c.str("ifText"), c.str("thenText"), c.lng("createdAt")
    )

    fun scorecard(c: Cursor) = ScorecardEntry(
        c.str("id"), c.str("routine"), c.int("verdict"), c.str("note"), c.lng("createdAt")
    )

    fun flow(c: Cursor) = Flow(c.str("id"), c.str("title"), c.str("anchor"), c.lng("createdAt"))

    fun flowStep(c: Cursor) = FlowStep(
        c.str("id"), c.str("flowId"), c.strOrNull("habitId"), c.str("title"),
        c.bool("existingBehaviour"), c.int("orderIndex")
    )

    fun review(c: Cursor) = Review(
        c.str("id"), ReviewKind.valueOf(c.str("kind").ifBlank { "WEEKLY" }), c.str("periodLabel"),
        c.str("whatWorked"), c.str("whatDidnt"), c.str("systemChange"),
        c.str("identityEvidence"), c.lng("createdAt")
    )

    fun energy(c: Cursor) = EnergyLog(
        c.str("id"), c.str("date"), Checkpoint.valueOf(c.str("checkpoint").ifBlank { "MORNING" }),
        c.int("energy"), c.str("note"), c.lng("createdAt")
    )

    fun audit(c: Cursor) = AuditEntry(
        c.str("id"), c.str("actor"), c.str("command"), c.str("summary"), c.str("payload"),
        c.str("undoPayload"), c.strOrNull("groupId"), c.bool("undone"), c.lng("createdAt")
    )

    fun message(c: Cursor) = AiMessage(
        c.str("id"), c.str("role"), c.str("text"), c.str("meta"), c.lng("createdAt")
    )

    fun project(c: Cursor) = BlueprintProject(
        c.str("id"), c.str("name"), c.str("instructions"), c.int("version"),
        c.str("state"), c.strOrNull("parentVersionId"), c.lng("createdAt")
    )

    fun source(c: Cursor) = BlueprintSource(
        c.str("id"), c.str("projectId"), c.str("name"), c.str("kind"), c.str("content"),
        c.str("instructions"), c.int("lineCount"), c.lng("createdAt")
    )

    fun requirement(c: Cursor) = Requirement(
        c.str("id"), c.str("projectId"), c.str("text"), c.strOrNull("sourceId"),
        c.str("citation"), RequirementStatus.valueOf(c.str("status").ifBlank { "ACCEPTED" }),
        c.bool("assumption"), c.str("plannedCommand"), c.str("note"), c.int("orderIndex")
    )

    fun pause(c: Cursor) = PauseWindow(
        c.str("id"), c.strOrNull("habitId"), c.str("startDate"), c.str("endDate"),
        c.str("reason"), c.lng("createdAt")
    )

    fun profile(c: Cursor) = UserProfile(
        c.str("id"), c.str("displayName"), c.str("locale"), c.str("zoneId"),
        c.int("weekStart").let { if (it in 1..7) it else 1 }, c.lng("createdAt"), c.lng("updatedAt")
    )

    fun version(c: Cursor) = BlueprintVersion(
        c.str("id"), c.str("projectId"), c.int("version"), c.str("label"),
        c.str("ledgerJson"), c.lng("createdAt")
    )

    /* ───────────────────────────────────────────────────── Phase 1 mappers */

    fun growthPlan(c: Cursor): GrowthPlan {
        val phases = parseJsonArray(c.str("phases_json"))?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { obj -> growthPhaseFromJson(obj) }
            }
        } ?: emptyList()
        val snapshots = parseJsonArray(c.str("weekly_snapshots_json"))?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { obj -> weeklySnapshotFromJson(obj) }
            }
        } ?: emptyList()
        return GrowthPlan(
            id = c.str("id"), habitId = c.str("habit_id"), userId = c.str("user_id"),
            createdAt = c.lng("created_at"), phases = phases,
            currentPhaseIndex = c.int("current_phase_index"),
            upgradePolicy = upgradePolicyFromJson(c.str("upgrade_policy_json")) ?: UpgradePolicy(),
            weeklySnapshots = snapshots,
            lastUpgradeDate = c.str("last_upgrade_date"),
            nextReviewDate = c.str("next_review_date")
        )
    }

    private fun growthPhaseFromJson(obj: JSONObject) = GrowthPhase(
        weekNumber = obj.optInt("weekNumber"), label = obj.optString("label"),
        tinyStart = obj.optString("tinyStart"), minimumVersion = obj.optString("minimumVersion"),
        standardVersion = obj.optString("standardVersion"), stretchVersion = obj.optString("stretchVersion"),
        targetDays = obj.optInt("targetDays"), notes = obj.optString("notes"),
        metrics = obj.optJSONObject("metrics")?.let { phaseMetricsFromJson(it) } ?: PhaseMetrics()
    )

    private fun phaseMetricsFromJson(obj: JSONObject) = PhaseMetrics(
        minConsistency = obj.optInt("minConsistency", 60),
        minRecoveries = obj.optInt("minRecoveries", 0),
        maxMissesInARow = obj.optInt("maxMissesInARow", 2),
        minEnergy = obj.optInt("minEnergy", 0)
    )

    private fun upgradePolicyFromJson(json: String): UpgradePolicy? {
        val obj = parseObject(json) ?: return null
        return UpgradePolicy(
            autoUpgrade = obj.optBoolean("autoUpgrade", true),
            upgradeDay = obj.optInt("upgradeDay", 1),
            minWeeksInPhase = obj.optInt("minWeeksInPhase", 1),
            maxWeeksInPhase = obj.optInt("maxWeeksInPhase", 4),
            downgradeOnStruggle = obj.optBoolean("downgradeOnStruggle", true),
            struggleThreshold = obj.optInt("struggleThreshold", 3)
        )
    }

    private fun weeklySnapshotFromJson(obj: JSONObject) = WeeklySnapshot(
        weekNumber = obj.optInt("weekNumber"), phaseIndex = obj.optInt("phaseIndex"),
        consistency = obj.optInt("consistency"), repetitions = obj.optInt("repetitions"),
        misses = obj.optInt("misses"), recoveries = obj.optInt("recoveries"),
        averageEnergy = if (obj.has("averageEnergy") && !obj.isNull("averageEnergy"))
            obj.optDouble("averageEnergy") else null,
        decision = UpgradeDecision.valueOf(obj.optString("decision", "HOLD")),
        date = obj.optString("date")
    )

    private fun parseObject(text: String): JSONObject? = try {
        JSONObject(text)
    } catch (e: Exception) { null }

    private fun parseJsonArray(text: String): JSONArray? = try {
        JSONArray(text)
    } catch (e: Exception) { null }

    fun milestone(c: Cursor) = Milestone(
        c.str("id"), c.strOrNull("habit_id"),
        MilestoneType.valueOf(c.str("type").ifBlank { "FIRST_CHECKIN" }),
        c.int("value"), c.str("label"), c.lng("achieved_at"), c.bool("acknowledged")
    )

    fun sprint(c: Cursor): Sprint {
        val focusHabits = parseJsonArray(c.str("focus_habits_json"))?.strings() ?: emptyList()
        val goals = parseJsonArray(c.str("goals_json"))?.strings() ?: emptyList()
        return Sprint(
            id = c.str("id"), title = c.str("title"),
            startDate = c.str("start_date"), endDate = c.str("end_date"),
            focusHabits = focusHabits, goals = goals,
            status = SprintStatus.valueOf(c.str("status").ifBlank { "PLANNED" }),
            reviewNotes = c.str("review_notes"), createdAt = c.lng("created_at")
        )
    }

    fun journalEntry(c: Cursor): JournalEntry {
        val tags = parseJsonArray(c.str("tags_json"))?.strings() ?: emptyList()
        return JournalEntry(
            id = c.str("id"), date = c.str("date"), prompt = c.str("prompt"),
            content = c.str("content"), mood = c.int("mood").let { if (it == 0) null else it },
            tags = tags, createdAt = c.lng("created_at")
        )
    }

    fun routine(c: Cursor) = Routine(
        id = c.str("id"), title = c.str("title"), trigger = c.str("trigger_text"),
        estimatedMinutes = c.int("estimated_minutes"),
        status = Status.valueOf(c.str("status").ifBlank { "ACTIVE" }),
        createdAt = c.lng("created_at")
    )

    fun routineStep(c: Cursor) = RoutineStep(
        id = c.str("id"), routineId = c.str("routine_id"), habitId = c.strOrNull("habit_id"),
        title = c.str("title"), durationMinutes = c.int("duration_minutes"),
        orderIndex = c.int("order_index"), transitionNote = c.str("transition_note")
    )

    fun environmentDesign(c: Cursor) = EnvironmentDesign(
        habitId = c.str("habit_id"),
        makeObvious = parseJsonArray(c.str("make_obvious_json"))?.strings() ?: emptyList(),
        makeAttractive = parseJsonArray(c.str("make_attractive_json"))?.strings() ?: emptyList(),
        makeEasy = parseJsonArray(c.str("make_easy_json"))?.strings() ?: emptyList(),
        makeSatisfying = parseJsonArray(c.str("make_satisfying_json"))?.strings() ?: emptyList(),
        makeInvisible = parseJsonArray(c.str("make_invisible_json"))?.strings() ?: emptyList(),
        makeUnattractive = parseJsonArray(c.str("make_unattractive_json"))?.strings() ?: emptyList(),
        makeDifficult = parseJsonArray(c.str("make_difficult_json"))?.strings() ?: emptyList(),
        makeUnsatisfying = parseJsonArray(c.str("make_unsatisfying_json"))?.strings() ?: emptyList()
    )

    fun aiMemory(c: Cursor) = AiMemory(
        id = c.str("id"),
        category = MemoryCategory.valueOf(c.str("category").ifBlank { "USER_PREFERENCE" }),
        content = c.str("content"), importance = c.int("importance"),
        lastAccessed = c.lng("last_accessed"), accessCount = c.int("access_count"),
        createdAt = c.lng("created_at")
    )

    fun proactiveSuggestion(c: Cursor) = ProactiveSuggestion(
        id = c.str("id"),
        type = SuggestionType.valueOf(c.str("type").ifBlank { "FOCUS" }),
        text = c.str("text"),
        priority = Priority.valueOf(c.str("priority").ifBlank { "MEDIUM" }),
        autoActionJson = c.str("auto_action_json"),
        habitId = c.strOrNull("habit_id"),
        createdAt = c.lng("created_at"),
        dismissed = c.bool("dismissed"),
        applied = c.bool("applied")
    )

    fun growthPhaseHistory(c: Cursor) = GrowthPhaseHistory(
        id = c.str("id"), growthPlanId = c.str("growth_plan_id"),
        phaseIndex = c.int("phase_index"), action = c.str("action"),
        consistency = c.int("consistency"), date = c.str("date"), notes = c.str("notes")
    )
}

private fun JSONArray.strings(): List<String> =
    (0 until length()).map { optString(it, "") }.filter { it.isNotBlank() }