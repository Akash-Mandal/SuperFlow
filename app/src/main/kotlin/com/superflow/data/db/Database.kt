package com.superflow.data.db

import android.content.ContentValues
import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory

/**
 * Persistence built on androidx.sqlite (the same support layer Room generates
 * against). Room's annotation processor is unavailable in this build
 * environment, so DAOs are written by hand against SupportSQLiteDatabase
 * rather than generated - the runtime contract is identical.
 */
class SuperFlowDatabase private constructor(context: Context) {

    companion object {
        const val NAME = "superflow.db"
        const val VERSION = 4

        @Volatile private var instance: SuperFlowDatabase? = null

        fun get(context: Context): SuperFlowDatabase =
            instance ?: synchronized(this) {
                instance ?: SuperFlowDatabase(context.applicationContext).also { instance = it }
            }
    }

    private val helper: SupportSQLiteOpenHelper = FrameworkSQLiteOpenHelperFactory().create(
        SupportSQLiteOpenHelper.Configuration.builder(context.applicationContext)
            .name(NAME)
            .callback(object : SupportSQLiteOpenHelper.Callback(VERSION) {
                override fun onCreate(db: SupportSQLiteDatabase) = Schema.create(db)
                override fun onUpgrade(db: SupportSQLiteDatabase, old: Int, new: Int) =
                    Schema.upgrade(db, old, new)
                override fun onConfigure(db: SupportSQLiteDatabase) {
                    db.setForeignKeyConstraintsEnabled(false)
                }
            })
            .build()
    )

    val db: SupportSQLiteDatabase get() = helper.writableDatabase
}

object Schema {

    fun create(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE identity(
                id TEXT PRIMARY KEY, statement TEXT, lifeArea TEXT, status TEXT,
                isPrimary INTEGER, evolutionHistory TEXT, createdAt INTEGER)"""
        )
        db.execSQL(
            """CREATE TABLE goal(
                id TEXT PRIMARY KEY, identityId TEXT, title TEXT, why TEXT, outcomeMetric TEXT,
                targetValue REAL, targetDate INTEGER, currentMetricValue REAL, metricUnit TEXT,
                status TEXT, milestones TEXT, createdAt INTEGER)"""
        )
        db.execSQL(
            """CREATE TABLE sys(
                id TEXT PRIMARY KEY, goalId TEXT, title TEXT, description TEXT,
                status TEXT, templateId TEXT, reviewFrequency TEXT, createdAt INTEGER)"""
        )
        db.execSQL(
            """CREATE TABLE habit(
                id TEXT PRIMARY KEY, systemId TEXT, identityId TEXT, title TEXT, mode TEXT,
                trackType TEXT, targetCount INTEGER, unit TEXT,
                cueTime TEXT, cuePlace TEXT, anchorHabitId TEXT, anchorText TEXT,
                benefit TEXT, temptationBundle TEXT, reframe TEXT,
                tinyStart TEXT, minimumVersion TEXT, standardVersion TEXT, stretchVersion TEXT,
                frictionPlan TEXT, environmentPrep TEXT, reward TEXT, recoveryPlan TEXT,
                recurrenceRule TEXT, scheduleVersion INTEGER, startDate TEXT, endDate TEXT,
                reminderEnabled INTEGER, protectedRoutine INTEGER,
                rewardSatisfaction INTEGER, rewardLastRated TEXT, reframeHelpful INTEGER,
                bundleEffectiveness INTEGER, frictionPlanActive INTEGER,
                environmentPrepReminderTime TEXT, ladderHistory TEXT,
                lastDifficultyRating INTEGER, stretchCount INTEGER, consecutiveStandards INTEGER,
                estimatedMinutes INTEGER, difficultyRating INTEGER,
                colorSeed INTEGER, orderIndex INTEGER, status TEXT, createdAt INTEGER)"""
        )
        db.execSQL(
            """CREATE TABLE checkin(
                id TEXT PRIMARY KEY, habitId TEXT, date TEXT, result TEXT, level TEXT,
                amount REAL, note TEXT, contextTags TEXT, actualAmount REAL,
                actualDurationMinutes INTEGER, qualityRating INTEGER, difficultyRating INTEGER,
                missReason TEXT, missReasonDetail TEXT, createdAt INTEGER)"""
        )
        db.execSQL("CREATE UNIQUE INDEX idx_checkin_day ON checkin(habitId, date)")
        db.execSQL("CREATE INDEX idx_checkin_date ON checkin(date)")
        db.execSQL(
            """CREATE TABLE focus(
                id TEXT PRIMARY KEY, date TEXT, habitId TEXT, title TEXT,
                done INTEGER, isPriority INTEGER, goalId TEXT,
                estimatedMinutes INTEGER, carryOverCount INTEGER, orderIndex INTEGER)"""
        )
        db.execSQL("CREATE INDEX idx_focus_date ON focus(date)")
        db.execSQL(
            """CREATE TABLE obstacle(
                id TEXT PRIMARY KEY, habitId TEXT, ifText TEXT, thenText TEXT,
                category TEXT, timesUsed INTEGER, lastUsed TEXT, effectiveness INTEGER,
                createdAt INTEGER)"""
        )
        db.execSQL(
            """CREATE TABLE scorecard(
                id TEXT PRIMARY KEY, routine TEXT, verdict INTEGER, note TEXT, createdAt INTEGER)"""
        )
        db.execSQL(
            """CREATE TABLE flow(
                id TEXT PRIMARY KEY, title TEXT, anchor TEXT,
                estimatedMinutes INTEGER, completionCount INTEGER, partialCount INTEGER,
                createdAt INTEGER)"""
        )
        db.execSQL(
            """CREATE TABLE flowstep(
                id TEXT PRIMARY KEY, flowId TEXT, habitId TEXT, title TEXT,
                existingBehaviour INTEGER, durationMinutes INTEGER, isBreakpoint INTEGER,
                orderIndex INTEGER)"""
        )
        db.execSQL(
            """CREATE TABLE review(
                id TEXT PRIMARY KEY, kind TEXT, periodLabel TEXT, whatWorked TEXT, whatDidnt TEXT,
                systemChange TEXT, identityEvidence TEXT, autoGeneratedData TEXT,
                actionItems TEXT, previousReviewId TEXT, createdAt INTEGER)"""
        )
        db.execSQL(
            """CREATE TABLE energy(
                id TEXT PRIMARY KEY, date TEXT, checkpoint TEXT, energy INTEGER,
                note TEXT, createdAt INTEGER)"""
        )
        db.execSQL(
            """CREATE TABLE audit(
                id TEXT PRIMARY KEY, actor TEXT, command TEXT, summary TEXT, payload TEXT,
                undoPayload TEXT, groupId TEXT, undone INTEGER, createdAt INTEGER)"""
        )
        db.execSQL("CREATE INDEX idx_audit_created ON audit(createdAt DESC)")
        db.execSQL(
            """CREATE TABLE aimsg(
                id TEXT PRIMARY KEY, role TEXT, text TEXT, meta TEXT, createdAt INTEGER)"""
        )
        db.execSQL(
            """CREATE TABLE bp_project(
                id TEXT PRIMARY KEY, name TEXT, instructions TEXT, version INTEGER,
                state TEXT, parentVersionId TEXT, createdAt INTEGER)"""
        )
        db.execSQL(
            """CREATE TABLE bp_source(
                id TEXT PRIMARY KEY, projectId TEXT, name TEXT, kind TEXT, content TEXT,
                instructions TEXT, lineCount INTEGER, createdAt INTEGER)"""
        )
        db.execSQL(
            """CREATE TABLE bp_req(
                id TEXT PRIMARY KEY, projectId TEXT, text TEXT, sourceId TEXT, citation TEXT,
                status TEXT, assumption INTEGER, plannedCommand TEXT, note TEXT, orderIndex INTEGER)"""
        )
        db.execSQL(
            """CREATE TABLE pause(
                id TEXT PRIMARY KEY, habitId TEXT, startDate TEXT, endDate TEXT,
                reason TEXT, createdAt INTEGER)"""
        )
        db.execSQL(
            """CREATE TABLE profile(
                id TEXT PRIMARY KEY, displayName TEXT, locale TEXT, zoneId TEXT,
                weekStart INTEGER, createdAt INTEGER, updatedAt INTEGER)"""
        )
        db.execSQL(
            """CREATE TABLE bp_version(
                id TEXT PRIMARY KEY, projectId TEXT, version INTEGER, label TEXT,
                ledgerJson TEXT, createdAt INTEGER)"""
        )
    }

    fun upgrade(db: SupportSQLiteDatabase, old: Int, new: Int) {
        if (old < 3) {
            // daysMask -> recurrenceRule, plus schedule versioning and pauses.
            runCatching { db.execSQL("ALTER TABLE habit ADD COLUMN recurrenceRule TEXT") }
            runCatching { db.execSQL("ALTER TABLE habit ADD COLUMN scheduleVersion INTEGER DEFAULT 1") }
            runCatching { db.execSQL("ALTER TABLE habit ADD COLUMN startDate TEXT") }
            runCatching { db.execSQL("ALTER TABLE habit ADD COLUMN endDate TEXT") }
            runCatching {
                db.query("SELECT id, daysMask FROM habit").use { c ->
                    while (c.moveToNext()) {
                        val id = c.getString(0)
                        val mask = if (c.isNull(1)) 127 else c.getInt(1)
                        val days = (1..7).filter { (mask shr (it - 1)) and 1 == 1 }
                        val rule = if (days.isEmpty()) "WEEKLY:1,2,3,4,5,6,7"
                        else "WEEKLY:" + days.joinToString(",")
                        db.execSQL("UPDATE habit SET recurrenceRule=? WHERE id=?", arrayOf(rule, id))
                    }
                }
            }
            runCatching {
                db.execSQL("""CREATE TABLE IF NOT EXISTS pause(
                    id TEXT PRIMARY KEY, habitId TEXT, startDate TEXT, endDate TEXT,
                    reason TEXT, createdAt INTEGER)""")
            }
            runCatching {
                db.execSQL("""CREATE TABLE IF NOT EXISTS profile(
                    id TEXT PRIMARY KEY, displayName TEXT, locale TEXT, zoneId TEXT,
                    weekStart INTEGER, createdAt INTEGER, updatedAt INTEGER)""")
            }
        }
        if (old < 2) {
            runCatching { db.execSQL("ALTER TABLE habit ADD COLUMN colorSeed INTEGER DEFAULT 0") }
            runCatching { db.execSQL("ALTER TABLE bp_project ADD COLUMN parentVersionId TEXT") }
            runCatching {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS bp_version(
                        id TEXT PRIMARY KEY, projectId TEXT, version INTEGER, label TEXT,
                        ledgerJson TEXT, createdAt INTEGER)"""
                )
            }
        }
        if (old < 4) {
            // v4: Core Growth Systems upgrade — new columns across all tables
            // Identity
            runCatching { db.execSQL("ALTER TABLE identity ADD COLUMN isPrimary INTEGER DEFAULT 1") }
            runCatching { db.execSQL("ALTER TABLE identity ADD COLUMN evolutionHistory TEXT DEFAULT ''") }
            // Goal
            runCatching { db.execSQL("ALTER TABLE goal ADD COLUMN currentMetricValue REAL") }
            runCatching { db.execSQL("ALTER TABLE goal ADD COLUMN metricUnit TEXT DEFAULT ''") }
            runCatching { db.execSQL("ALTER TABLE goal ADD COLUMN milestones TEXT DEFAULT ''") }
            // Sys
            runCatching { db.execSQL("ALTER TABLE sys ADD COLUMN templateId TEXT") }
            runCatching { db.execSQL("ALTER TABLE sys ADD COLUMN reviewFrequency TEXT DEFAULT 'monthly'") }
            // Habit — Four Laws, Ladder, Capacity
            runCatching { db.execSQL("ALTER TABLE habit ADD COLUMN rewardSatisfaction INTEGER") }
            runCatching { db.execSQL("ALTER TABLE habit ADD COLUMN rewardLastRated TEXT") }
            runCatching { db.execSQL("ALTER TABLE habit ADD COLUMN reframeHelpful INTEGER") }
            runCatching { db.execSQL("ALTER TABLE habit ADD COLUMN bundleEffectiveness INTEGER") }
            runCatching { db.execSQL("ALTER TABLE habit ADD COLUMN frictionPlanActive INTEGER DEFAULT 0") }
            runCatching { db.execSQL("ALTER TABLE habit ADD COLUMN environmentPrepReminderTime TEXT") }
            runCatching { db.execSQL("ALTER TABLE habit ADD COLUMN ladderHistory TEXT DEFAULT ''") }
            runCatching { db.execSQL("ALTER TABLE habit ADD COLUMN lastDifficultyRating INTEGER") }
            runCatching { db.execSQL("ALTER TABLE habit ADD COLUMN stretchCount INTEGER DEFAULT 0") }
            runCatching { db.execSQL("ALTER TABLE habit ADD COLUMN consecutiveStandards INTEGER DEFAULT 0") }
            runCatching { db.execSQL("ALTER TABLE habit ADD COLUMN estimatedMinutes INTEGER DEFAULT 5") }
            runCatching { db.execSQL("ALTER TABLE habit ADD COLUMN difficultyRating INTEGER DEFAULT 3") }
            // CheckIn
            runCatching { db.execSQL("ALTER TABLE checkin ADD COLUMN contextTags TEXT DEFAULT ''") }
            runCatching { db.execSQL("ALTER TABLE checkin ADD COLUMN actualAmount REAL") }
            runCatching { db.execSQL("ALTER TABLE checkin ADD COLUMN actualDurationMinutes INTEGER") }
            runCatching { db.execSQL("ALTER TABLE checkin ADD COLUMN qualityRating INTEGER") }
            runCatching { db.execSQL("ALTER TABLE checkin ADD COLUMN difficultyRating INTEGER") }
            runCatching { db.execSQL("ALTER TABLE checkin ADD COLUMN missReason TEXT") }
            runCatching { db.execSQL("ALTER TABLE checkin ADD COLUMN missReasonDetail TEXT") }
            // Focus
            runCatching { db.execSQL("ALTER TABLE focus ADD COLUMN isPriority INTEGER DEFAULT 0") }
            runCatching { db.execSQL("ALTER TABLE focus ADD COLUMN goalId TEXT") }
            runCatching { db.execSQL("ALTER TABLE focus ADD COLUMN estimatedMinutes INTEGER") }
            runCatching { db.execSQL("ALTER TABLE focus ADD COLUMN carryOverCount INTEGER DEFAULT 0") }
            // Obstacle
            runCatching { db.execSQL("ALTER TABLE obstacle ADD COLUMN category TEXT") }
            runCatching { db.execSQL("ALTER TABLE obstacle ADD COLUMN timesUsed INTEGER DEFAULT 0") }
            runCatching { db.execSQL("ALTER TABLE obstacle ADD COLUMN lastUsed TEXT") }
            runCatching { db.execSQL("ALTER TABLE obstacle ADD COLUMN effectiveness INTEGER") }
            // Flow
            runCatching { db.execSQL("ALTER TABLE flow ADD COLUMN estimatedMinutes INTEGER DEFAULT 0") }
            runCatching { db.execSQL("ALTER TABLE flow ADD COLUMN completionCount INTEGER DEFAULT 0") }
            runCatching { db.execSQL("ALTER TABLE flow ADD COLUMN partialCount INTEGER DEFAULT 0") }
            // FlowStep
            runCatching { db.execSQL("ALTER TABLE flowstep ADD COLUMN durationMinutes INTEGER DEFAULT 0") }
            runCatching { db.execSQL("ALTER TABLE flowstep ADD COLUMN isBreakpoint INTEGER DEFAULT 0") }
            // Review
            runCatching { db.execSQL("ALTER TABLE review ADD COLUMN autoGeneratedData TEXT DEFAULT ''") }
            runCatching { db.execSQL("ALTER TABLE review ADD COLUMN actionItems TEXT DEFAULT ''") }
            runCatching { db.execSQL("ALTER TABLE review ADD COLUMN previousReviewId TEXT") }
        }
    }
}

/* ------------------------------------------------------------------ helpers */

fun contentValuesOf(vararg pairs: Pair<String, Any?>): ContentValues {
    val v = ContentValues(pairs.size)
    for ((k, value) in pairs) when (value) {
        null -> v.putNull(k)
        is String -> v.put(k, value)
        is Int -> v.put(k, value)
        is Long -> v.put(k, value)
        is Double -> v.put(k, value)
        is Boolean -> v.put(k, if (value) 1 else 0)
        else -> v.put(k, value.toString())
    }
    return v
}
