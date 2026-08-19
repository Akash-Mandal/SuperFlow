package com.superflow.data.db

import android.content.ContentValues
import android.content.Context
import android.util.Log
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory

/**
 * Persistence built on androidx.sqlite (the same support layer Room generates
 * against). DAOs are written by hand against SupportSQLiteDatabase; the
 * runtime contract is identical to generated code.
 *
 * WAL journal mode is enabled on every open: with rollback-journal mode, a
 * writer holds an exclusive lock that stalls concurrent reads on the main
 * thread; WAL lets readers and a single writer proceed in parallel, which is
 * what keeps check-ins and screen refreshes from stuttering each other. WAL
 * does not change the on-disk data format, so existing user data is
 * untouched.
 */
class SuperFlowDatabase private constructor(context: Context) {

    companion object {
        const val NAME = "superflow.db"
        const val VERSION = 3
        private const val TAG = "SuperFlowDb"

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
                    // Idempotent and cheap; applied before any read/write.
                    try {
                        db.query("PRAGMA journal_mode=WAL").use { c ->
                            if (c.moveToFirst() && c.getInt(0) != 2) {
                                Log.w(TAG, "journal_mode=WAL not applied: ${c.getString(0)}")
                            }
                        }
                        db.execSQL("PRAGMA synchronous=NORMAL")
                    } catch (e: Exception) {
                        // Degraded but safe: default journal mode still works.
                        Log.w(TAG, "Could not enable WAL mode", e)
                    }
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
                recurrenceRule TEXT, scheduleVersion INTEGER, startDate TEXT, endDate TEXT,
                reminderEnabled INTEGER, protectedRoutine INTEGER,
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

    /**
     * Runs the migration chain in ascending order (v1 -> v2 -> v3).
     *
     * A failed step is logged loudly (tag [TAG]) and skipped rather than
     * crashing the app into a startup loop: the read layer tolerates missing
     * columns (see the Cursor helpers), and the user's data stays intact. A
     * migration failure must be visible in logcat, not silent.
     */
    fun upgrade(db: SupportSQLiteDatabase, old: Int, new: Int) {
        Log.i(TAG, "Migrating $NAME from version $old to $new")
        if (old < 2) migrateToV2(db)
        if (old < 3) migrateToV3(db)
    }

    /** v2: colour seeds, blueprint parent versions and the version table. */
    private fun migrateToV2(db: SupportSQLiteDatabase) {
        addColumn(db, "habit", "ALTER TABLE habit ADD COLUMN colorSeed INTEGER DEFAULT 0")
        addColumn(db, "bp_project", "ALTER TABLE bp_project ADD COLUMN parentVersionId TEXT")
        guard(db, "v2 create bp_version") {
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS bp_version(
                    id TEXT PRIMARY KEY, projectId TEXT, version INTEGER, label TEXT,
                    ledgerJson TEXT, createdAt INTEGER)"""
            )
        }
    }

    /** v3: daysMask -> recurrenceRule, schedule versioning, pauses, profile. */
    private fun migrateToV3(db: SupportSQLiteDatabase) {
        addColumn(db, "habit", "ALTER TABLE habit ADD COLUMN recurrenceRule TEXT")
        addColumn(db, "habit", "ALTER TABLE habit ADD COLUMN scheduleVersion INTEGER DEFAULT 1")
        addColumn(db, "habit", "ALTER TABLE habit ADD COLUMN startDate TEXT")
        addColumn(db, "habit", "ALTER TABLE habit ADD COLUMN endDate TEXT")
        guard(db, "v3 daysMask -> recurrenceRule conversion") {
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
        guard(db, "v3 create pause") {
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS pause(
                    id TEXT PRIMARY KEY, habitId TEXT, startDate TEXT, endDate TEXT,
                    reason TEXT, createdAt INTEGER)"""
            )
        }
        guard(db, "v3 create profile") {
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS profile(
                    id TEXT PRIMARY KEY, displayName TEXT, locale TEXT, zoneId TEXT,
                    weekStart INTEGER, createdAt INTEGER, updatedAt INTEGER)"""
            )
        }
    }

    /** ALTER TABLE ADD COLUMN, tolerating (and silently) an already-present column. */
    private fun addColumn(db: SupportSQLiteDatabase, table: String, sql: String) {
        try {
            db.execSQL(sql)
        } catch (e: Exception) {
            if (e.message.orEmpty().contains("duplicate column name", ignoreCase = true)) return
            Log.e(TAG, "Migration ALTER on $table failed", e)
        }
    }

    private fun guard(db: SupportSQLiteDatabase, step: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            Log.e(TAG, "Migration step failed: $step", e)
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
