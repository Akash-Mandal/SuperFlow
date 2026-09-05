package com.superflow.data.db

import android.database.Cursor
import android.util.Log
import com.superflow.data.model.*
import com.superflow.util.objects
import com.superflow.util.string
import com.superflow.util.stringOrNull
import org.json.JSONArray
import org.json.JSONObject

/** Cursor read helpers plus row mappers, kept in one place. */

fun Cursor.str(name: String): String =
    getColumnIndex(name).let { if (it < 0 || isNull(it)) "" else getString(it) }

fun Cursor.strOrNull(name: String): String? =
    getColumnIndex(name).let { if (it < 0 || isNull(it)) null else getString(it) }

fun Cursor.int(name: String): Int =
    getColumnIndex(name).let { if (it < 0 || isNull(it)) 0 else getInt(it) }

fun Cursor.intOrNull(name: String): Int? =
    getColumnIndex(name).let { if (it < 0 || isNull(it)) null else getInt(it) }

fun Cursor.boolOrNull(name: String): Boolean? =
    getColumnIndex(name).let { if (it < 0 || isNull(it)) null else getInt(it) == 1 }

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
        Status.valueOf(c.str("status").ifBlank { "ACTIVE" }),
        c.bool("isPrimary"),
        parseEvolutionHistory(c.str("evolutionHistory")),
        c.lng("createdAt")
    )

    fun goal(c: Cursor) = Goal(
        c.str("id"), c.strOrNull("identityId"), c.str("title"), c.str("why"),
        c.str("outcomeMetric"), c.dblOrNull("targetValue"), c.lngOrNull("targetDate"),
        c.dblOrNull("currentMetricValue"), c.str("metricUnit"),
        GoalStatus.valueOf(c.str("status").ifBlank { "ACTIVE" }),
        parseGoalMilestones(c.str("milestones")),
        c.lng("createdAt")
    )

    fun system(c: Cursor) = Sys(
        c.str("id"), c.strOrNull("goalId"), c.str("title"), c.str("description"),
        Status.valueOf(c.str("status").ifBlank { "ACTIVE" }),
        c.strOrNull("templateId"), c.str("reviewFrequency").ifBlank { "monthly" },
        c.lng("createdAt")
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
        rewardSatisfaction = c.intOrNull("rewardSatisfaction"),
        rewardLastRated = c.strOrNull("rewardLastRated"),
        reframeHelpful = c.boolOrNull("reframeHelpful"),
        bundleEffectiveness = c.intOrNull("bundleEffectiveness"),
        frictionPlanActive = c.bool("frictionPlanActive"),
        environmentPrepReminderTime = c.strOrNull("environmentPrepReminderTime"),
        ladderHistory = parseLadderEvolution(c.str("ladderHistory")),
        lastDifficultyRating = c.intOrNull("lastDifficultyRating"),
        stretchCount = c.int("stretchCount"), consecutiveStandards = c.int("consecutiveStandards"),
        estimatedMinutes = c.int("estimatedMinutes").coerceAtLeast(1),
        difficultyRating = c.int("difficultyRating").coerceIn(1, 5),
        colorSeed = c.int("colorSeed"),
        colorOverride = try { c.intOrNull("colorOverride") } catch (_: Exception) { null },
        essential = try { c.bool("essential") } catch (_: Exception) { false },
        flexDays = try { c.int("flexDays") } catch (_: Exception) { 0 },
        quietHours = try { c.strOrNull("quietHours") } catch (_: Exception) { null },
        orderIndex = c.int("orderIndex"),
        status = Status.valueOf(c.str("status").ifBlank { "ACTIVE" }),
        graduated = c.bool("graduated"), graduatedAt = c.lngOrNull("graduatedAt"),
        createdAt = c.lng("createdAt")
    )

    fun checkIn(c: Cursor) = CheckIn(
        c.str("id"), c.str("habitId"), c.str("date"),
        CheckInResult.valueOf(c.str("result").ifBlank { "DONE" }),
        Level.from(c.str("level")), c.dbl("amount"), c.str("note"),
        parseContextTags(c.str("contextTags")),
        c.dblOrNull("actualAmount"),
        c.intOrNull("actualDurationMinutes"),
        c.intOrNull("qualityRating"),
        c.intOrNull("difficultyRating"),
        c.strOrNull("missReason"), c.strOrNull("missReasonDetail"),
        c.lng("createdAt")
    )

    fun focus(c: Cursor) = FocusItem(
        c.str("id"), c.str("date"), c.strOrNull("habitId"), c.str("title"),
        c.bool("done"), c.bool("isPriority"), c.strOrNull("goalId"),
        c.intOrNull("estimatedMinutes"),
        c.int("carryOverCount"), c.int("orderIndex")
    )

    fun obstacle(c: Cursor) = ObstaclePlan(
        c.str("id"), c.str("habitId"), c.str("ifText"), c.str("thenText"),
        c.strOrNull("category"), c.int("timesUsed"), c.strOrNull("lastUsed"),
        c.intOrNull("effectiveness"),
        c.lng("createdAt")
    )

    fun scorecard(c: Cursor) = ScorecardEntry(
        c.str("id"), c.str("routine"), c.int("verdict"), c.str("note"), c.lng("createdAt")
    )

    fun flow(c: Cursor) = Flow(
        c.str("id"), c.str("title"), c.str("anchor"),
        c.int("estimatedMinutes"), c.int("completionCount"), c.int("partialCount"),
        c.lng("createdAt")
    )

    fun flowStep(c: Cursor) = FlowStep(
        c.str("id"), c.str("flowId"), c.strOrNull("habitId"), c.str("title"),
        c.bool("existingBehaviour"), c.int("durationMinutes"), c.bool("isBreakpoint"),
        c.int("orderIndex")
    )

    fun review(c: Cursor) = Review(
        c.str("id"), ReviewKind.valueOf(c.str("kind").ifBlank { "WEEKLY" }), c.str("periodLabel"),
        c.str("whatWorked"), c.str("whatDidnt"), c.str("systemChange"),
        c.str("identityEvidence"), c.str("autoGeneratedData"),
        parseReviewActionItems(c.str("actionItems")), c.strOrNull("previousReviewId"),
        c.lng("createdAt")
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

    fun evidence(c: Cursor) = IdentityEvidence(
        c.str("id"), c.str("identityId"), c.str("text"), c.strOrNull("sourceHabitId"),
        c.str("date"), c.lng("createdAt")
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

    /** Quick-capture inbox item (Plan B F1). */
    fun capturedItem(c: Cursor) = CapturedItem(
        id = c.str("id"),
        text = c.str("text"),
        kind = CaptureKind.valueOf(c.str("kind").ifBlank { "NOTE" }),
        source = CaptureSource.valueOf(c.str("source").ifBlank { "MANUAL" }),
        state = CaptureState.valueOf(c.str("state").ifBlank { "OPEN" }),
        convertedToId = c.strOrNull("converted_to_id"),
        createdAt = c.lng("created_at"),
        updatedAt = c.lngOrNull("updated_at") ?: c.lng("created_at"),
    )

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

/* --------------------------------------------------------------- JSON parsers */

private fun parseEvolutionHistory(raw: String): List<IdentityEvolution> {
    if (raw.isBlank()) return emptyList()
    return try {
        JSONArray(raw).objects().map { o ->
            IdentityEvolution(
                previousStatement = o.string("previousStatement"),
                newStatement = o.string("newStatement"),
                reason = o.string("reason"),
                votesAtEvolution = o.optInt("votesAtEvolution", 0),
                date = o.string("date")
            )
        }
    } catch (e: Exception) {
            Log.w("Cursors", "Failed to parse IdentityEvolutions: ${e.message}")
            emptyList()
        }
}

private fun parseGoalMilestones(raw: String): List<GoalMilestone> {
    if (raw.isBlank()) return emptyList()
    return try {
        JSONArray(raw).objects().map { o ->
            val linkedHabitIds = o.optJSONArray("linkedHabitIds")?.let { arr ->
                (0 until arr.length()).map { arr.optString(it, "") }.filter { it.isNotBlank() }
            } ?: emptyList()
            GoalMilestone(
                id = o.string("id"),
                title = o.string("title"),
                achieved = o.optBoolean("achieved", false),
                achievedDate = o.stringOrNull("achievedDate"),
                linkedHabitIds = linkedHabitIds
            )
        }
    } catch (e: Exception) {
            Log.w("Cursors", "Failed to parse GoalMilestones: ${e.message}")
            emptyList()
        }
}

private fun parseLadderEvolution(raw: String): List<LadderEvolution> {
    if (raw.isBlank()) return emptyList()
    return try {
        JSONArray(raw).objects().map { o ->
            LadderEvolution(
                level = Level.from(o.optString("level")),
                previousText = o.string("previousText"),
                newText = o.string("newText"),
                reason = o.string("reason"),
                date = o.string("date")
            )
        }
    } catch (e: Exception) {
            Log.w("Cursors", "Failed to parse LadderEvolutions: ${e.message}")
            emptyList()
        }
}

private fun parseContextTags(raw: String): List<String> {
    if (raw.isBlank()) return emptyList()
    return try {
        val arr = JSONArray(raw)
        (0 until arr.length()).mapNotNull { i ->
            val s = arr.optString(i, "")
            if (s.isNotBlank()) s
            else arr.optJSONObject(i)?.optString("tag", "")?.takeIf { it.isNotBlank() }
        }
    } catch (e: Exception) {
        Log.w("Cursors", "Failed to parse ContextTags, falling back to CSV: ${e.message}")
        raw.split(",").map { it.trim() }.filter { it.isNotBlank() }
    }
}

private fun parseReviewActionItems(raw: String): List<ReviewActionItem> {
    if (raw.isBlank()) return emptyList()
    return try {
        JSONArray(raw).objects().map { o ->
            ReviewActionItem(
                id = o.string("id"),
                text = o.string("text"),
                completed = o.optBoolean("completed", false),
                completedDate = o.stringOrNull("completedDate"),
                linkedCommand = o.stringOrNull("linkedCommand"),
                outcome = o.stringOrNull("outcome")
            )
        }
    } catch (e: Exception) {
            Log.w("Cursors", "Failed to parse ReviewActionItems: ${e.message}")
            emptyList()
        }
}

private fun JSONArray.strings(): List<String> =
    (0 until length()).map { optString(it, "") }.filter { it.isNotBlank() }
