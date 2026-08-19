package com.superflow.data.db

import android.database.Cursor
import com.superflow.data.model.*

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
        status = Status.valueOf(c.str("status").ifBlank { "ACTIVE" }),
        graduated = c.bool("graduated"), graduatedAt = c.lngOrNull("graduatedAt"),
        createdAt = c.lng("createdAt")
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
}
