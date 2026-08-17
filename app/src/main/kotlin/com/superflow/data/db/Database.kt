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
        const val VERSION = 2

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
                id TEXT PRIMARY KEY, statement TEXT, lifeArea TEXT, status TEXT, createdAt INTEGER)"""
        )
        db.execSQL(
            """CREATE TABLE goal(
                id TEXT PRIMARY KEY, identityId TEXT, title TEXT, why TEXT, outcomeMetric TEXT,
                targetValue REAL, targetDate INTEGER, status TEXT, createdAt INTEGER)"""
        )
        db.execSQL(
            """CREATE TABLE sys(
                id TEXT PRIMARY KEY, goalId TEXT, title TEXT, description TEXT,
                status TEXT, createdAt INTEGER)"""
        )
        db.execSQL(
            """CREATE TABLE habit(
                id TEXT PRIMARY KEY, systemId TEXT, identityId TEXT, title TEXT, mode TEXT,
                trackType TEXT, targetCount INTEGER, unit TEXT,
                cueTime TEXT, cuePlace TEXT, anchorHabitId TEXT, anchorText TEXT,
                benefit TEXT, temptationBundle TEXT, reframe TEXT,
                tinyStart TEXT, minimumVersion TEXT, standardVersion TEXT, stretchVersion TEXT,
                frictionPlan TEXT, environmentPrep TEXT, reward TEXT, recoveryPlan TEXT,
                daysMask INTEGER, reminderEnabled INTEGER, protectedRoutine INTEGER,
                colorSeed INTEGER, orderIndex INTEGER, status TEXT, createdAt INTEGER)"""
        )
        db.execSQL(
            """CREATE TABLE checkin(
                id TEXT PRIMARY KEY, habitId TEXT, date TEXT, result TEXT, level TEXT,
                amount REAL, note TEXT, createdAt INTEGER)"""
        )
        db.execSQL("CREATE UNIQUE INDEX idx_checkin_day ON checkin(habitId, date)")
        db.execSQL("CREATE INDEX idx_checkin_date ON checkin(date)")
        db.execSQL(
            """CREATE TABLE focus(
                id TEXT PRIMARY KEY, date TEXT, habitId TEXT, title TEXT,
                done INTEGER, orderIndex INTEGER)"""
        )
        db.execSQL("CREATE INDEX idx_focus_date ON focus(date)")
        db.execSQL(
            """CREATE TABLE obstacle(
                id TEXT PRIMARY KEY, habitId TEXT, ifText TEXT, thenText TEXT, createdAt INTEGER)"""
        )
        db.execSQL(
            """CREATE TABLE scorecard(
                id TEXT PRIMARY KEY, routine TEXT, verdict INTEGER, note TEXT, createdAt INTEGER)"""
        )
        db.execSQL("CREATE TABLE flow(id TEXT PRIMARY KEY, title TEXT, anchor TEXT, createdAt INTEGER)")
        db.execSQL(
            """CREATE TABLE flowstep(
                id TEXT PRIMARY KEY, flowId TEXT, habitId TEXT, title TEXT,
                existingBehaviour INTEGER, orderIndex INTEGER)"""
        )
        db.execSQL(
            """CREATE TABLE review(
                id TEXT PRIMARY KEY, kind TEXT, periodLabel TEXT, whatWorked TEXT, whatDidnt TEXT,
                systemChange TEXT, identityEvidence TEXT, createdAt INTEGER)"""
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
            """CREATE TABLE bp_version(
                id TEXT PRIMARY KEY, projectId TEXT, version INTEGER, label TEXT,
                ledgerJson TEXT, createdAt INTEGER)"""
        )
    }

    fun upgrade(db: SupportSQLiteDatabase, old: Int, new: Int) {
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
