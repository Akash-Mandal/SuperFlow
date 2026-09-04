package com.superflow.domain

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import com.superflow.core.time.FixedClock
import com.superflow.core.time.SuperFlowClock
import com.superflow.data.Repository
import com.superflow.data.db.SuperFlowDatabase
import com.superflow.data.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Core Growth Systems: nested-field serialization round-trips, ladder text,
 * model defaults and the system-template catalog. Migrated from the original
 * JVM shim suite into the standard JUnit location.
 */
class GrowthTest {

    @Test
    fun `ladder evolution serialization round-trips`() {
        val ladder = listOf(
            LadderEvolution(level = Level.STANDARD, previousText = "Walk 10 min",
                newText = "Walk 15 min", reason = "14 consecutive standards", date = "2026-07-01")
        )
        val h = Habit(title = "Walk", ladderHistory = ladder, stretchCount = 3,
            consecutiveStandards = 5, estimatedMinutes = 12, difficultyRating = 4,
            rewardSatisfaction = 4, rewardLastRated = "2026-07-10",
            reframeHelpful = true, bundleEffectiveness = 3, frictionPlanActive = true,
            environmentPrepReminderTime = "21:00", lastDifficultyRating = 2)
        val hBack = Serial.habit(Serial.of(h))
        assertEquals(1, hBack.ladderHistory.size)
        assertEquals(Level.STANDARD, hBack.ladderHistory.first().level)
        assertEquals("Walk 15 min", hBack.ladderHistory.first().newText)
        assertEquals("14 consecutive standards", hBack.ladderHistory.first().reason)
        assertEquals(3, hBack.stretchCount)
        assertEquals(5, hBack.consecutiveStandards)
        assertEquals(12, hBack.estimatedMinutes)
        assertEquals(4, hBack.difficultyRating)
        assertEquals(4, hBack.rewardSatisfaction)
        assertEquals("2026-07-10", hBack.rewardLastRated)
        assertEquals(true, hBack.reframeHelpful)
        assertEquals(3, hBack.bundleEffectiveness)
        assertEquals(true, hBack.frictionPlanActive)
        assertEquals("21:00", hBack.environmentPrepReminderTime)
        assertEquals(2, hBack.lastDifficultyRating)
    }

    @Test
    fun `check-in rich data serialization round-trips`() {
        val ci = CheckIn(habitId = "h1", date = "2026-07-01", result = CheckInResult.DONE,
            level = Level.STRETCH, contextTags = listOf("good sleep", "high energy"),
            actualAmount = 12.0, actualDurationMinutes = 45, qualityRating = 3,
            difficultyRating = 2, missReason = "time", missReasonDetail = "meeting ran long")
        val ciBack = Serial.checkIn(Serial.of(ci))
        assertEquals(listOf("good sleep", "high energy"), ciBack.contextTags)
        assertEquals(12.0, ciBack.actualAmount)
        assertEquals(45, ciBack.actualDurationMinutes)
        assertEquals(3, ciBack.qualityRating)
        assertEquals(2, ciBack.difficultyRating)
        assertEquals("time", ciBack.missReason)
        assertEquals("meeting ran long", ciBack.missReasonDetail)
    }

    @Test
    fun `goal milestones serialization round-trips`() {
        val goal = Goal(title = "Run a 5K", outcomeMetric = "km",
            currentMetricValue = 3.0, metricUnit = "km",
            milestones = listOf(
                GoalMilestone(title = "Walk 1km", achieved = true, achievedDate = "2026-06-01"),
                GoalMilestone(title = "Walk 3km"),
                GoalMilestone(title = "Run 5km", linkedHabitIds = listOf("h1", "h2"))
            ))
        val gBack = Serial.goal(Serial.of(goal))
        assertEquals(3, gBack.milestones.size)
        assertEquals(true, gBack.milestones[0].achieved)
        assertEquals("2026-06-01", gBack.milestones[0].achievedDate)
        assertEquals(listOf("h1", "h2"), gBack.milestones[2].linkedHabitIds)
        assertEquals(3.0, gBack.currentMetricValue)
        assertEquals("km", gBack.metricUnit)
    }

    @Test
    fun `identity evolution serialization round-trips`() {
        val identity = Identity(statement = "Someone who moves daily",
            evolutionHistory = listOf(
                IdentityEvolution(previousStatement = "Someone who wants to move",
                    newStatement = "Someone who moves daily",
                    reason = "100 votes", votesAtEvolution = 100, date = "2026-05-01")
            ), isPrimary = true)
        val iBack = Serial.identity(Serial.of(identity))
        assertEquals(1, iBack.evolutionHistory.size)
        assertEquals("Someone who wants to move", iBack.evolutionHistory[0].previousStatement)
        assertEquals(100, iBack.evolutionHistory[0].votesAtEvolution)
        assertEquals(true, iBack.isPrimary)
    }

    @Test
    fun `review action items serialization round-trips`() {
        val review = Review(kind = ReviewKind.WEEKLY, periodLabel = "Week of Aug 17",
            autoGeneratedData = "87% consistency, strongest: Walk",
            actionItems = listOf(
                ReviewActionItem(text = "Shrink Journal to 5 min",
                    linkedCommand = "{\"command\":\"update_habit\"}"),
                ReviewActionItem(text = "Move Meditate to morning", completed = true,
                    completedDate = "2026-08-18", outcome = "Did it, worked")
            ), previousReviewId = "rev-1")
        val rBack = Serial.review(Serial.of(review))
        assertEquals(2, rBack.actionItems.size)
        assertEquals("87% consistency, strongest: Walk", rBack.autoGeneratedData)
        assertEquals("Shrink Journal to 5 min", rBack.actionItems[0].text)
        assertEquals("{\"command\":\"update_habit\"}", rBack.actionItems[0].linkedCommand)
        assertEquals(true, rBack.actionItems[1].completed)
        assertEquals("Did it, worked", rBack.actionItems[1].outcome)
        assertEquals("rev-1", rBack.previousReviewId)
    }

    @Test
    fun `focus item serialization round-trips`() {
        val focus = FocusItem(date = "2026-08-19", habitId = "h1", title = "Walk",
            done = false, isPriority = true, goalId = "g1", estimatedMinutes = 30,
            carryOverCount = 2)
        val fBack = Serial.focus(Serial.of(focus))
        assertEquals(true, fBack.isPriority)
        assertEquals("g1", fBack.goalId)
        assertEquals(30, fBack.estimatedMinutes)
        assertEquals(2, fBack.carryOverCount)
    }

    @Test
    fun `obstacle plan serialization round-trips`() {
        val obs = ObstaclePlan(habitId = "h1", ifText = "It rains", thenText = "Stretch indoors",
            category = "weather", timesUsed = 3, lastUsed = "2026-08-18", effectiveness = 4)
        val oBack = Serial.obstacle(Serial.of(obs))
        assertEquals("weather", oBack.category)
        assertEquals(3, oBack.timesUsed)
        assertEquals("2026-08-18", oBack.lastUsed)
        assertEquals(4, oBack.effectiveness)
    }

    @Test
    fun `flow and flow step serialization round-trip`() {
        val flow = Flow(title = "Morning", anchor = "Wake up", estimatedMinutes = 25,
            completionCount = 4, partialCount = 2)
        val fBack = Serial.flow(Serial.of(flow))
        assertEquals(25, fBack.estimatedMinutes)
        assertEquals(4, fBack.completionCount)
        assertEquals(2, fBack.partialCount)

        val step = FlowStep(flowId = "f1", habitId = "h1", title = "Walk",
            durationMinutes = 10, isBreakpoint = true)
        val sBack = Serial.flowStep(Serial.of(step))
        assertEquals(10, sBack.durationMinutes)
        assertEquals(true, sBack.isBreakpoint)
    }

    @Test
    fun `system template fields round-trip`() {
        val sys = Sys(title = "Morning Movement", templateId = "morning_routine",
            reviewFrequency = "weekly")
        val syBack = Serial.system(Serial.of(sys))
        assertEquals("morning_routine", syBack.templateId)
        assertEquals("weekly", syBack.reviewFrequency)
    }

    @Test
    fun `ladder level text respects evolved ladder`() {
        val evolved = Habit(title = "Walk", tinyStart = "Shoes on",
            standardVersion = "Walk 10 min")
        assertEquals("Walk 10 min", evolved.levelText(Level.STANDARD))
        assertEquals("Shoes on", evolved.levelText(Level.TINY))
        assertEquals("Walk 10 min", evolved.levelText(Level.STRETCH))
    }

    @Test
    fun `model defaults for growth fields`() {
        val fresh = Habit(title = "Walk")
        assertEquals(5, fresh.estimatedMinutes)
        assertEquals(3, fresh.difficultyRating)
        assertEquals(false, fresh.frictionPlanActive)
        assertEquals(0, fresh.ladderHistory.size)

        val freshCi = CheckIn(habitId = "h", date = "2026-01-01", result = CheckInResult.DONE)
        assertEquals(0, freshCi.contextTags.size)
        assertEquals(null, freshCi.missReason)

        val freshFocus = FocusItem(date = "2026-01-01", habitId = "h", title = "t")
        assertEquals(false, freshFocus.isPriority)
        assertEquals(0, freshFocus.carryOverCount)

        val freshSys = Sys(title = "S")
        assertEquals("monthly", freshSys.reviewFrequency)
        assertEquals(null, freshSys.templateId)

        val freshGoal = Goal(title = "G")
        assertEquals(0, freshGoal.milestones.size)

        val freshIdentity = Identity(statement = "s")
        assertEquals(true, freshIdentity.isPrimary)
        assertEquals(0, freshIdentity.evolutionHistory.size)
    }

    @Test
    fun `system templates catalog`() {
        val templates = Capabilities.systemTemplates()
        assertEquals(5, templates.size)
        assertTrue(templates.any { it.first == "morning_routine" })
        assertTrue(templates.any { it.first == "evening_wind_down" })
    }

    @Test
    fun evaluateWeeklyCalculatesAverageEnergyFromEnergyLogsInPast7Days() {
        val fixedDate = LocalDate.of(2026, 8, 26)
        val habit = Habit(id = "h1", title = "Exercise", tinyStart = "1 pushup", standardVersion = "20 pushups")
        val energyLogs = listOf(
            EnergyLog(date = "2026-08-25", checkpoint = Checkpoint.MORNING, energy = 4),
            EnergyLog(date = "2026-08-26", checkpoint = Checkpoint.EVENING, energy = 2),
            EnergyLog(date = "2026-08-10", checkpoint = Checkpoint.MORNING, energy = 5)
        )
        val repo = createFakeRepository(
            clock = FixedClock(fixedDate.atStartOfDay(ZoneId.of("UTC")).toInstant(), ZoneId.of("UTC")),
            habits = listOf(habit),
            energyLogs = energyLogs
        )

        val plan = GrowthEngine.generateGrowthPlan(habit)
        val snapshot = GrowthEngine.evaluateWeekly(plan, repo, fixedDate)
        assertEquals(3.0, snapshot.averageEnergy!!, 0.001)
    }

    @Test
    fun evaluateWeeklyReturnsNullAverageEnergyWhenNoEnergyLogsExistInPast7Days() {
        val fixedDate = LocalDate.of(2026, 8, 26)
        val habit = Habit(id = "h1", title = "Exercise", tinyStart = "1 pushup", standardVersion = "20 pushups")
        val energyLogs = listOf(
            EnergyLog(date = "2026-08-10", checkpoint = Checkpoint.MORNING, energy = 5)
        )
        val repo = createFakeRepository(
            clock = FixedClock(fixedDate.atStartOfDay(ZoneId.of("UTC")).toInstant(), ZoneId.of("UTC")),
            habits = listOf(habit),
            energyLogs = energyLogs
        )

        val plan = GrowthEngine.generateGrowthPlan(habit)
        val snapshot = GrowthEngine.evaluateWeekly(plan, repo, fixedDate)
        assertNull(snapshot.averageEnergy)
    }

    /* ---------------------------------------------------- Repository helper */

    private fun createFakeRepository(
        clock: SuperFlowClock = FixedClock(Instant.parse("2026-08-26T10:00:00Z")),
        habits: List<Habit> = emptyList(),
        energyLogs: List<EnergyLog> = emptyList()
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

        val writeLockField = Repository::class.java.getDeclaredField("writeLock")
        writeLockField.isAccessible = true
        writeLockField.set(repo, java.util.concurrent.locks.ReentrantLock())

        val revisionField = Repository::class.java.getDeclaredField("_revision")
        revisionField.isAccessible = true
        revisionField.set(repo, kotlinx.coroutines.flow.MutableStateFlow(0L))

        val dbProxy = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method, args ->
            when (method.name) {
                "query" -> {
                    val queryObj = args[0]
                    val sqlField = queryObj.javaClass.getDeclaredField("query")
                    sqlField.isAccessible = true
                    val sql = sqlField.get(queryObj) as String

                    when {
                        sql.contains("FROM habit") -> createHabitCursor(habits)
                        sql.contains("FROM energy") -> createEnergyCursor(energyLogs)
                        else -> createEmptyCursor()
                    }
                }
                "beginTransaction", "setTransactionSuccessful", "endTransaction" -> null
                "insert" -> 1L
                "delete" -> 1
                "execSQL" -> null
                else -> null
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

    private fun createEnergyCursor(energyLogs: List<EnergyLog>): Cursor {
        val rows = energyLogs.map { e ->
            mapOf(
                "id" to e.id, "date" to e.date, "checkpoint" to e.checkpoint.name,
                "energy" to e.energy, "note" to e.note, "createdAt" to e.createdAt
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
