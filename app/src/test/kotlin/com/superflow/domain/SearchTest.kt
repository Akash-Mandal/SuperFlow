package com.superflow.domain

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import com.superflow.core.time.FixedClock
import com.superflow.core.time.SuperFlowClock
import com.superflow.data.Repository
import com.superflow.data.db.SuperFlowDatabase
import com.superflow.data.model.AiMessage
import com.superflow.data.model.AuditEntry
import com.superflow.data.model.Goal
import com.superflow.data.model.Habit
import com.superflow.data.model.Identity
import com.superflow.data.model.LifeArea
import com.superflow.data.model.ObstaclePlan
import com.superflow.data.model.Review
import com.superflow.data.model.ReviewKind
import com.superflow.data.model.Status
import com.superflow.data.model.Sys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy
import java.time.Instant
import java.time.ZoneId

class SearchTest {

    /* -------------------------------------------------- Search.relevance tests */

    @Test
    fun `relevance returns exact match score 1`() {
        assertEquals(1.0f, Search.relevance("walk", "Walk"), 0.001f)
        assertEquals(1.0f, Search.relevance("walk", "Gym", "Walk"), 0.001f)
    }

    @Test
    fun `relevance returns prefix match score 0_8`() {
        assertEquals(0.8f, Search.relevance("wal", "Walk"), 0.001f)
    }

    @Test
    fun `relevance returns contains match score 0_5`() {
        assertEquals(0.5f, Search.relevance("alk", "Walk"), 0.001f)
    }

    @Test
    fun `relevance returns query-contains match score 0_3`() {
        assertEquals(0.3f, Search.relevance("walkk", "Walk"), 0.001f)
    }

    @Test
    fun `relevance skips query-contains match when length is 3 or less`() {
        assertEquals(0.2f, Search.relevance("runn", "Run"), 0.001f)
    }

    @Test
    fun `relevance returns fuzzy match score 0_2`() {
        assertEquals(0.2f, Search.relevance("wakk", "Walk"), 0.001f)
    }

    @Test
    fun `relevance returns 0 when no match`() {
        assertEquals(0f, Search.relevance("zzzz", "Walk"), 0.001f)
    }

    @Test
    fun `relevance handles blank fields and empty list`() {
        assertEquals(0f, Search.relevance("walk", "", "   "), 0.001f)
        assertEquals(0f, Search.relevance("walk"), 0.001f)
    }

    /* ---------------------------------------------------- Search.search tests */

    @Test
    fun `blank query returns empty list`() {
        val repo = createFakeRepository()
        assertTrue(Search.search(repo, "").isEmpty())
        assertTrue(Search.search(repo, "   ").isEmpty())
    }

    @Test
    fun `search finds habits with title, cue, place, anchor, benefit`() {
        val repo = createFakeRepository(
            habits = listOf(
                Habit(
                    id = "h1",
                    title = "Morning Meditation",
                    cueTime = "07:00",
                    cuePlace = "Living room",
                    anchorText = "After coffee",
                    benefit = "Peace of mind"
                ),
                Habit(
                    id = "h2",
                    title = "Evening Read",
                    cueTime = "",
                    cuePlace = "Bed",
                    anchorText = "",
                    benefit = ""
                )
            )
        )

        // Exact match on title
        val r1 = Search.search(repo, "morning meditation")
        assertEquals(1, r1.size)
        assertEquals("habit", r1[0].type)
        assertEquals("h1", r1[0].id)
        assertEquals("Morning Meditation", r1[0].title)
        assertTrue(r1[0].subtitle.contains("07:00"))
        assertEquals(1.0f, r1[0].relevance, 0.001f)

        // Test fallback subtitle when cueTime is blank
        val r2 = Search.search(repo, "evening read")
        assertEquals(1, r2.size)
        assertTrue(r2[0].subtitle.contains("Anytime"))

        // Search by cuePlace / benefit
        val r3 = Search.search(repo, "peace")
        assertEquals(1, r3.size)
        assertEquals("h1", r3[0].id)
    }

    @Test
    fun `search finds identities and maps statement and lifeArea label`() {
        val repo = createFakeRepository(
            identities = listOf(
                Identity(
                    id = "i1",
                    statement = "I am a disciplined writer",
                    lifeArea = LifeArea.CREATIVITY,
                    status = Status.ACTIVE
                )
            )
        )

        val results = Search.search(repo, "disciplined")
        assertEquals(1, results.size)
        val res = results[0]
        assertEquals("identity", res.type)
        assertEquals("i1", res.id)
        assertEquals("I am a disciplined writer", res.title)
        assertEquals(LifeArea.CREATIVITY.label, res.subtitle)
        assertEquals(0.5f, res.relevance, 0.001f)
    }

    @Test
    fun `search finds goals and handles fallback why`() {
        val repo = createFakeRepository(
            goals = listOf(
                Goal(id = "g1", title = "Run a marathon", why = "Health and endurance"),
                Goal(id = "g2", title = "Write a novel", why = "")
            )
        )

        val r1 = Search.search(repo, "marathon")
        assertEquals(1, r1.size)
        assertEquals("goal", r1[0].type)
        assertEquals("Run a marathon", r1[0].title)
        assertEquals("Health and endurance", r1[0].subtitle)

        val r2 = Search.search(repo, "novel")
        assertEquals(1, r2.size)
        assertEquals("Goal", r2[0].subtitle)
    }

    @Test
    fun `search finds systems and handles fallback description`() {
        val repo = createFakeRepository(
            systems = listOf(
                Sys(id = "s1", title = "Daily Exercise Routine", description = "30 mins daily workout"),
                Sys(id = "s2", title = "Weekly Cleanup", description = "")
            )
        )

        val r1 = Search.search(repo, "exercise")
        assertEquals(1, r1.size)
        assertEquals("system", r1[0].type)
        assertEquals("30 mins daily workout", r1[0].subtitle)

        val r2 = Search.search(repo, "cleanup")
        assertEquals(1, r2.size)
        assertEquals("System", r2[0].subtitle)
    }

    @Test
    fun `search finds reviews and formats period, kind, and text truncation`() {
        val repo = createFakeRepository(
            reviews = listOf(
                Review(
                    id = "r1",
                    kind = ReviewKind.WEEKLY,
                    periodLabel = "Week 34 2026",
                    whatWorked = "Consistent wake up time and morning routine went great",
                    whatDidnt = "Late night phone usage",
                    systemChange = "",
                    identityEvidence = ""
                ),
                Review(
                    id = "r2",
                    kind = ReviewKind.MONTHLY,
                    periodLabel = "August 2026",
                    whatWorked = "",
                    whatDidnt = "Skipped weekend workouts due to travel",
                    systemChange = "",
                    identityEvidence = ""
                )
            )
        )

        val results = Search.search(repo, "2026")
        assertEquals(2, results.size)

        val weekly = results.first { it.id == "r1" }
        assertEquals("review", weekly.type)
        assertEquals("Week 34 2026", weekly.title)
        assertTrue(weekly.subtitle.startsWith("Weekly review · "))

        val monthly = results.first { it.id == "r2" }
        assertEquals("August 2026", monthly.title)
        assertTrue(monthly.subtitle.startsWith("Monthly review · Skipped weekend workouts"))
    }

    @Test
    fun `search finds messages as journal and formats timestamp`() {
        val epochMs = 1700000000000L
        val repo = createFakeRepository(
            clock = FixedClock(Instant.ofEpochMilli(epochMs), ZoneId.of("UTC")),
            messages = listOf(
                AiMessage(
                    id = "m1",
                    role = "user",
                    text = "Had a great journaling session today about personal growth and progress",
                    createdAt = epochMs
                )
            )
        )

        val results = Search.search(repo, "journaling")
        assertEquals(1, results.size)
        val res = results[0]
        assertEquals("journal", res.type)
        assertEquals("m1", res.id)
        assertEquals("Had a great journaling session today about personal growth and progress", res.title)
        assertTrue(res.subtitle.startsWith("user · "))
    }

    @Test
    fun `search finds audit entries and handles summary fallback`() {
        val repo = createFakeRepository(
            audit = listOf(
                AuditEntry(
                    id = "a1",
                    actor = "user",
                    command = "check_in_habit",
                    summary = "Checked in Morning Walk",
                    payload = "",
                    undoPayload = ""
                ),
                AuditEntry(
                    id = "a2",
                    actor = "ai",
                    command = "archive_habit",
                    summary = "",
                    payload = "",
                    undoPayload = ""
                )
            )
        )

        val r1 = Search.search(repo, "Checked in")
        assertEquals(1, r1.size)
        assertEquals("audit", r1[0].type)
        assertEquals("Checked in Morning Walk", r1[0].title)
        assertEquals("check_in_habit", r1[0].subtitle)

        val r2 = Search.search(repo, "archive_habit")
        assertEquals(1, r2.size)
        assertEquals("archive_habit", r2[0].title) // fallback when summary is blank
        assertEquals("archive_habit", r2[0].subtitle)
    }

    @Test
    fun `search finds obstacles and formats if-then subtitle`() {
        val repo = createFakeRepository(
            obstacles = listOf(
                ObstaclePlan(
                    id = "o1",
                    habitId = "h1",
                    ifText = "it rains outside",
                    thenText = "do indoor cardio"
                )
            )
        )

        val results = Search.search(repo, "rains")
        assertEquals(1, results.size)
        val res = results[0]
        assertEquals("obstacle", res.type)
        assertEquals("If it rains outside, then do indoor cardio", res.title)
        assertEquals("Obstacle plan", res.subtitle)
    }

    @Test
    fun `search results are sorted by descending relevance`() {
        val repo = createFakeRepository(
            habits = listOf(
                Habit(id = "h1", title = "Walk"),          // Exact match = 1.0
                Habit(id = "h2", title = "Walking"),       // Prefix match = 0.8
                Habit(id = "h3", title = "Morning Walk"), // Contains match = 0.5
                Habit(id = "h4", title = "Wakk")           // Fuzzy match = 0.2
            )
        )

        val results = Search.search(repo, "walk")
        assertEquals(4, results.size)
        assertEquals("h1", results[0].id)
        assertEquals(1.0f, results[0].relevance, 0.001f)
        assertEquals("h2", results[1].id)
        assertEquals(0.8f, results[1].relevance, 0.001f)
        assertEquals("h3", results[2].id)
        assertEquals(0.5f, results[2].relevance, 0.001f)
        assertEquals("h4", results[3].id)
        assertEquals(0.2f, results[3].relevance, 0.001f)
    }

    /* ---------------------------------------------------- Repository helper */

    private fun createFakeRepository(
        clock: SuperFlowClock = FixedClock(Instant.parse("2026-08-26T10:00:00Z")),
        habits: List<Habit> = emptyList(),
        identities: List<Identity> = emptyList(),
        goals: List<Goal> = emptyList(),
        systems: List<Sys> = emptyList(),
        reviews: List<Review> = emptyList(),
        messages: List<AiMessage> = emptyList(),
        audit: List<AuditEntry> = emptyList(),
        obstacles: List<ObstaclePlan> = emptyList()
    ): Repository {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val field = unsafeClass.getDeclaredField("theUnsafe")
        field.isAccessible = true
        val unsafe = field.get(null)
        val allocateMethod = unsafeClass.getMethod("allocateInstance", Class::class.java)
        val repo = allocateMethod.invoke(unsafe, Repository::class.java) as Repository

        val clockField = Repository::class.java.getDeclaredField("clock")
        clockField.isAccessible = true
        clockField.set(repo, clock)

        val dbProxy = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method, args ->
            if (method.name == "query") {
                val queryObj = args[0]
                val sqlField = queryObj.javaClass.getDeclaredField("query")
                sqlField.isAccessible = true
                val sql = sqlField.get(queryObj) as String

                val cursor = when {
                    sql.contains("FROM habit") -> createHabitCursor(habits)
                    sql.contains("FROM identity") -> createIdentityCursor(identities)
                    sql.contains("FROM goal") -> createGoalCursor(goals)
                    sql.contains("FROM sys") -> createSysCursor(systems)
                    sql.contains("FROM review") -> createReviewCursor(reviews)
                    sql.contains("FROM aimsg") -> createMessageCursor(messages)
                    sql.contains("FROM audit") -> createAuditCursor(audit)
                    sql.contains("FROM obstacle") -> createObstacleCursor(obstacles)
                    else -> createEmptyCursor()
                }
                cursor
            } else {
                null
            }
        } as SupportSQLiteDatabase

        val superFlowDb = createSuperFlowDatabase(dbProxy)

        val dbField = Repository::class.java.getDeclaredField("database")
        dbField.isAccessible = true
        dbField.set(repo, superFlowDb)

        return repo
    }

    private fun createSuperFlowDatabase(dbProxy: SupportSQLiteDatabase): SuperFlowDatabase {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val field = unsafeClass.getDeclaredField("theUnsafe")
        field.isAccessible = true
        val unsafe = field.get(null)
        val allocateMethod = unsafeClass.getMethod("allocateInstance", Class::class.java)
        val sfDb = allocateMethod.invoke(unsafe, SuperFlowDatabase::class.java) as SuperFlowDatabase

        // In SuperFlowDatabase, val db: SupportSQLiteDatabase get() = helper.writableDatabase
        // So we can set helper field to a proxy returning dbProxy
        val helperClass = Class.forName("androidx.sqlite.db.SupportSQLiteOpenHelper")
        val helperProxy = Proxy.newProxyInstance(
            helperClass.classLoader,
            arrayOf(helperClass)
        ) { _, method, _ ->
            if (method.name == "getWritableDatabase" || method.name == "getReadableDatabase") {
                dbProxy
            } else null
        }

        val helperField = SuperFlowDatabase::class.java.getDeclaredField("helper")
        helperField.isAccessible = true
        helperField.set(sfDb, helperProxy)

        return sfDb
    }

    private fun createEmptyCursor(): Cursor = createMapCursor(emptyList())

    private fun createHabitCursor(habits: List<Habit>): Cursor {
        val rows = habits.map { h ->
            mapOf(
                "id" to h.id, "systemId" to h.systemId, "identityId" to h.identityId,
                "title" to h.title, "mode" to h.mode.name, "trackType" to h.trackType.name,
                "targetCount" to h.targetCount, "unit" to h.unit,
                "cueTime" to h.cueTime, "cuePlace" to h.cuePlace,
                "anchorHabitId" to h.anchorHabitId, "anchorText" to h.anchorText,
                "benefit" to h.benefit, "temptationBundle" to h.temptationBundle,
                "reframe" to h.reframe, "tinyStart" to h.tinyStart,
                "minimumVersion" to h.minimumVersion, "standardVersion" to h.standardVersion,
                "stretchVersion" to h.stretchVersion, "frictionPlan" to h.frictionPlan,
                "environmentPrep" to h.environmentPrep, "reward" to h.reward,
                "recoveryPlan" to h.recoveryPlan, "recurrenceRule" to h.recurrenceRule,
                "scheduleVersion" to h.scheduleVersion, "startDate" to h.startDate,
                "endDate" to h.endDate, "reminderEnabled" to h.reminderEnabled,
                "protectedRoutine" to h.protectedRoutine, "rewardSatisfaction" to h.rewardSatisfaction,
                "rewardLastRated" to h.rewardLastRated, "reframeHelpful" to h.reframeHelpful,
                "bundleEffectiveness" to h.bundleEffectiveness, "frictionPlanActive" to h.frictionPlanActive,
                "environmentPrepReminderTime" to h.environmentPrepReminderTime,
                "ladderHistory" to "", "lastDifficultyRating" to h.lastDifficultyRating,
                "stretchCount" to h.stretchCount, "consecutiveStandards" to h.consecutiveStandards,
                "estimatedMinutes" to h.estimatedMinutes, "difficultyRating" to h.difficultyRating,
                "colorSeed" to h.colorSeed, "colorOverride" to h.colorOverride,
                "essential" to h.essential, "flexDays" to h.flexDays,
                "quietHours" to h.quietHours, "orderIndex" to h.orderIndex,
                "status" to h.status.name, "graduated" to h.graduated,
                "graduatedAt" to h.graduatedAt, "createdAt" to h.createdAt
            )
        }
        return createMapCursor(rows)
    }

    private fun createIdentityCursor(identities: List<Identity>): Cursor {
        val rows = identities.map { i ->
            mapOf(
                "id" to i.id, "statement" to i.statement, "lifeArea" to i.lifeArea.name,
                "status" to i.status.name, "isPrimary" to i.isPrimary,
                "evolutionHistory" to "", "createdAt" to i.createdAt
            )
        }
        return createMapCursor(rows)
    }

    private fun createGoalCursor(goals: List<Goal>): Cursor {
        val rows = goals.map { g ->
            mapOf(
                "id" to g.id, "identityId" to g.identityId, "title" to g.title, "why" to g.why,
                "outcomeMetric" to g.outcomeMetric, "targetValue" to g.targetValue,
                "targetDate" to g.targetDate, "currentMetricValue" to g.currentMetricValue,
                "metricUnit" to g.metricUnit, "status" to g.status.name,
                "milestones" to "", "createdAt" to g.createdAt
            )
        }
        return createMapCursor(rows)
    }

    private fun createSysCursor(systems: List<Sys>): Cursor {
        val rows = systems.map { s ->
            mapOf(
                "id" to s.id, "goalId" to s.goalId, "title" to s.title,
                "description" to s.description, "status" to s.status.name,
                "templateId" to s.templateId, "reviewFrequency" to s.reviewFrequency,
                "createdAt" to s.createdAt
            )
        }
        return createMapCursor(rows)
    }

    private fun createReviewCursor(reviews: List<Review>): Cursor {
        val rows = reviews.map { r ->
            mapOf(
                "id" to r.id, "kind" to r.kind.name, "periodLabel" to r.periodLabel,
                "whatWorked" to r.whatWorked, "whatDidnt" to r.whatDidnt,
                "systemChange" to r.systemChange, "identityEvidence" to r.identityEvidence,
                "autoGeneratedData" to r.autoGeneratedData, "actionItems" to "",
                "previousReviewId" to r.previousReviewId, "createdAt" to r.createdAt
            )
        }
        return createMapCursor(rows)
    }

    private fun createMessageCursor(messages: List<AiMessage>): Cursor {
        val rows = messages.map { m ->
            mapOf(
                "id" to m.id, "role" to m.role, "text" to m.text,
                "meta" to m.meta, "createdAt" to m.createdAt
            )
        }
        return createMapCursor(rows)
    }

    private fun createAuditCursor(audit: List<AuditEntry>): Cursor {
        val rows = audit.map { a ->
            mapOf(
                "id" to a.id, "actor" to a.actor, "command" to a.command,
                "summary" to a.summary, "payload" to a.payload,
                "undoPayload" to a.undoPayload, "groupId" to a.groupId,
                "undone" to a.undone, "createdAt" to a.createdAt
            )
        }
        return createMapCursor(rows)
    }

    private fun createObstacleCursor(obstacles: List<ObstaclePlan>): Cursor {
        val rows = obstacles.map { o ->
            mapOf(
                "id" to o.id, "habitId" to o.habitId, "ifText" to o.ifText,
                "thenText" to o.thenText, "category" to o.category,
                "timesUsed" to o.timesUsed, "lastUsed" to o.lastUsed,
                "effectiveness" to o.effectiveness, "createdAt" to o.createdAt
            )
        }
        return createMapCursor(rows)
    }

    private fun createMapCursor(rows: List<Map<String, Any?>>): Cursor {
        var rowIndex = -1
        val columns = if (rows.isNotEmpty()) rows[0].keys.toList() else emptyList()

        return Proxy.newProxyInstance(
            Cursor::class.java.classLoader,
            arrayOf(Cursor::class.java)
        ) { _, method, args ->
            when (method.name) {
                "moveToNext" -> {
                    rowIndex++
                    rowIndex < rows.size
                }
                "getColumnIndex" -> {
                    val name = args[0] as String
                    columns.indexOf(name)
                }
                "isNull" -> {
                    val idx = args[0] as Int
                    if (idx < 0 || idx >= columns.size) true
                    else rows[rowIndex][columns[idx]] == null
                }
                "getString" -> {
                    val idx = args[0] as Int
                    if (idx < 0 || idx >= columns.size) ""
                    else rows[rowIndex][columns[idx]]?.toString() ?: ""
                }
                "getInt" -> {
                    val idx = args[0] as Int
                    if (idx < 0 || idx >= columns.size) 0
                    else {
                        val v = rows[rowIndex][columns[idx]]
                        when (v) {
                            is Boolean -> if (v) 1 else 0
                            is Number -> v.toInt()
                            else -> 0
                        }
                    }
                }
                "getLong" -> {
                    val idx = args[0] as Int
                    if (idx < 0 || idx >= columns.size) 0L
                    else {
                        val v = rows[rowIndex][columns[idx]]
                        when (v) {
                            is Boolean -> if (v) 1L else 0L
                            is Number -> v.toLong()
                            else -> 0L
                        }
                    }
                }
                "getDouble" -> {
                    val idx = args[0] as Int
                    if (idx < 0 || idx >= columns.size) 0.0
                    else {
                        val v = rows[rowIndex][columns[idx]]
                        (v as? Number)?.toDouble() ?: 0.0
                    }
                }
                "close" -> null
                else -> null
            }
        } as Cursor
    }
}
