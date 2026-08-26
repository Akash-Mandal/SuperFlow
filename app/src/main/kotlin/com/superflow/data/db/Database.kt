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
        const val VERSION = 6
        const val TAG = "SuperFlowDb"

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
                id TEXT PRIMARY KEY, statement TEXT, lifeArea TEXT, status TEXT,
                isPrimary INTEGER, evolutionHistory TEXT, createdAt INTEGER)"""
        )
        db.execSQL(
            """CREATE TABLE goal(
                id TEXT PRIMARY KEY, identityId TEXT, title TEXT, why TEXT, outcomeMetric TEXT,
                targetValue REAL, targetDate INTEGER, currentMetricValue REAL, metricUnit TEXT,
                status TEXT, milestones TEXT, createdAt INTEGER)"""
        )
        db.execSQL("CREATE INDEX idx_goal_identity ON goal(identityId)")
        db.execSQL(
            """CREATE TABLE sys(
                id TEXT PRIMARY KEY, goalId TEXT, title TEXT, description TEXT,
                status TEXT, templateId TEXT, reviewFrequency TEXT, createdAt INTEGER)"""
        )
        db.execSQL("CREATE INDEX idx_sys_goal ON sys(goalId)")
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
                colorSeed INTEGER, orderIndex INTEGER, status TEXT,
                graduated INTEGER, graduatedAt INTEGER, createdAt INTEGER)"""
        )
        db.execSQL("CREATE INDEX idx_habit_system ON habit(systemId)")
        db.execSQL("CREATE INDEX idx_habit_identity ON habit(identityId)")
        db.execSQL("CREATE INDEX idx_habit_status ON habit(status)")
        db.execSQL(
            """CREATE TABLE checkin(
                id TEXT PRIMARY KEY, habitId TEXT, date TEXT, result TEXT, level TEXT,
                amount REAL, note TEXT, contextTags TEXT, actualAmount REAL,
                actualDurationMinutes INTEGER, qualityRating INTEGER, difficultyRating INTEGER,
                missReason TEXT, missReasonDetail TEXT, createdAt INTEGER)"""
        )
        db.execSQL("CREATE UNIQUE INDEX idx_checkin_day ON checkin(habitId, date)")
        db.execSQL("CREATE INDEX idx_checkin_date ON checkin(date)")
        db.execSQL("CREATE INDEX idx_checkin_habit ON checkin(habitId)")
        db.execSQL(
            """CREATE TABLE focus(
                id TEXT PRIMARY KEY, date TEXT, habitId TEXT, title TEXT,
                done INTEGER, isPriority INTEGER, goalId TEXT,
                estimatedMinutes INTEGER, carryOverCount INTEGER, orderIndex INTEGER)"""
        )
        db.execSQL("CREATE INDEX idx_focus_date ON focus(date)")
        db.execSQL("CREATE INDEX idx_focus_habit ON focus(habitId)")
        db.execSQL(
            """CREATE TABLE obstacle(
                id TEXT PRIMARY KEY, habitId TEXT, ifText TEXT, thenText TEXT,
                category TEXT, timesUsed INTEGER, lastUsed TEXT, effectiveness INTEGER,
                createdAt INTEGER)"""
        )
        db.execSQL("CREATE INDEX idx_obstacle_habit ON obstacle(habitId)")
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
        db.execSQL("CREATE INDEX idx_flowstep_flow ON flowstep(flowId)")
        db.execSQL("CREATE INDEX idx_flowstep_habit ON flowstep(habitId)")
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
        db.execSQL("CREATE INDEX idx_energy_date ON energy(date)")
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
        db.execSQL("CREATE INDEX idx_bp_source_project ON bp_source(projectId)")
        db.execSQL(
            """CREATE TABLE bp_req(
                id TEXT PRIMARY KEY, projectId TEXT, text TEXT, sourceId TEXT, citation TEXT,
                status TEXT, assumption INTEGER, plannedCommand TEXT, note TEXT, orderIndex INTEGER)"""
        )
        db.execSQL("CREATE INDEX idx_bp_req_project ON bp_req(projectId)")
        db.execSQL(
            """CREATE TABLE pause(
                id TEXT PRIMARY KEY, habitId TEXT, startDate TEXT, endDate TEXT,
                reason TEXT, createdAt INTEGER)"""
        )
        db.execSQL("CREATE INDEX idx_pause_habit ON pause(habitId)")
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
        db.execSQL(
            """CREATE TABLE evidence(
                id TEXT PRIMARY KEY, identityId TEXT, text TEXT, sourceHabitId TEXT,
                date TEXT, createdAt INTEGER)"""
        )

        // ── Functional Plan (PR #8) new tables ──────────────────────────
        db.execSQL(
            """CREATE TABLE growth_plan(
                id TEXT PRIMARY KEY, habit_id TEXT NOT NULL, user_id TEXT DEFAULT 'local',
                phases_json TEXT NOT NULL, current_phase_index INTEGER DEFAULT 0,
                upgrade_policy_json TEXT NOT NULL, weekly_snapshots_json TEXT DEFAULT '[]',
                last_upgrade_date TEXT DEFAULT '', next_review_date TEXT DEFAULT '',
                created_at INTEGER NOT NULL)"""
        )
        db.execSQL("CREATE INDEX idx_growth_plan_habit ON growth_plan(habit_id)")

        db.execSQL(
            """CREATE TABLE milestone(
                id TEXT PRIMARY KEY, habit_id TEXT, type TEXT NOT NULL,
                value INTEGER NOT NULL, label TEXT NOT NULL,
                acknowledged INTEGER DEFAULT 0, achieved_at INTEGER NOT NULL)"""
        )
        db.execSQL("CREATE INDEX idx_milestone_habit ON milestone(habit_id)")

        db.execSQL(
            """CREATE TABLE sprint(
                id TEXT PRIMARY KEY, title TEXT NOT NULL, start_date TEXT NOT NULL,
                end_date TEXT NOT NULL, focus_habits_json TEXT DEFAULT '[]',
                goals_json TEXT DEFAULT '[]', status TEXT DEFAULT 'PLANNED',
                review_notes TEXT DEFAULT '', created_at INTEGER NOT NULL)"""
        )

        db.execSQL(
            """CREATE TABLE journal_entry(
                id TEXT PRIMARY KEY, date TEXT NOT NULL, prompt TEXT DEFAULT '',
                content TEXT NOT NULL, mood INTEGER,
                tags_json TEXT DEFAULT '[]', created_at INTEGER NOT NULL)"""
        )
        db.execSQL("CREATE INDEX idx_journal_date ON journal_entry(date)")

        db.execSQL(
            """CREATE TABLE routine(
                id TEXT PRIMARY KEY, title TEXT NOT NULL, trigger_text TEXT DEFAULT '',
                estimated_minutes INTEGER DEFAULT 0, status TEXT DEFAULT 'ACTIVE',
                created_at INTEGER NOT NULL)"""
        )
        db.execSQL(
            """CREATE TABLE routine_step(
                id TEXT PRIMARY KEY, routine_id TEXT NOT NULL, habit_id TEXT,
                title TEXT NOT NULL, duration_minutes INTEGER DEFAULT 5,
                order_index INTEGER DEFAULT 0, transition_note TEXT DEFAULT '')"""
        )

        db.execSQL(
            """CREATE TABLE environment_design(
                habit_id TEXT PRIMARY KEY, make_obvious_json TEXT DEFAULT '[]',
                make_attractive_json TEXT DEFAULT '[]', make_easy_json TEXT DEFAULT '[]',
                make_satisfying_json TEXT DEFAULT '[]', make_invisible_json TEXT DEFAULT '[]',
                make_unattractive_json TEXT DEFAULT '[]', make_difficult_json TEXT DEFAULT '[]',
                make_unsatisfying_json TEXT DEFAULT '[]')"""
        )

        db.execSQL(
            """CREATE TABLE ai_memory(
                id TEXT PRIMARY KEY, category TEXT NOT NULL, content TEXT NOT NULL,
                importance INTEGER DEFAULT 5, last_accessed INTEGER NOT NULL,
                access_count INTEGER DEFAULT 0, created_at INTEGER NOT NULL)"""
        )
        db.execSQL("CREATE INDEX idx_memory_category ON ai_memory(category)")

        db.execSQL(
            """CREATE TABLE proactive_suggestion(
                id TEXT PRIMARY KEY, type TEXT NOT NULL, text TEXT NOT NULL,
                priority TEXT DEFAULT 'MEDIUM', auto_action_json TEXT DEFAULT '',
                habit_id TEXT, dismissed INTEGER DEFAULT 0, applied INTEGER DEFAULT 0,
                created_at INTEGER NOT NULL)"""
        )

        db.execSQL(
            """CREATE TABLE growth_phase_history(
                id TEXT PRIMARY KEY, growth_plan_id TEXT NOT NULL,
                phase_index INTEGER NOT NULL, action TEXT NOT NULL,
                consistency INTEGER DEFAULT 0, date TEXT NOT NULL, notes TEXT DEFAULT '')"""
        )
        db.execSQL("CREATE INDEX idx_bp_version_project ON bp_version(projectId)")

        db.execSQL(
            """CREATE TABLE blueprint_auto_plan(
                id TEXT PRIMARY KEY, projectId TEXT NOT NULL, phaseIndex INTEGER NOT NULL,
                whatJson TEXT NOT NULL, whenExpr TEXT NOT NULL, whereKind TEXT NOT NULL,
                howOp TEXT NOT NULL, conditionJson TEXT, status TEXT NOT NULL,
                createdAt INTEGER NOT NULL, appliedAt INTEGER)"""
        )
        db.execSQL("CREATE INDEX idx_bap_project ON blueprint_auto_plan(projectId)")

        createCapturedItem(db)
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
        Log.i(SuperFlowDatabase.TAG, "Migrating ${SuperFlowDatabase.NAME} from version $old to $new")
        if (old < 2) migrateToV2(db)
        if (old < 3) migrateToV3(db)
        if (old < 4) migrateToV4(db)
        if (old < 5) migrateToV5(db)
        if (old < 6) migrateToV6(db)
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
            Log.e(SuperFlowDatabase.TAG, "Migration ALTER on $table failed", e)
        }
    }

    private fun guard(db: SupportSQLiteDatabase, step: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            Log.e(SuperFlowDatabase.TAG, "Migration step failed: $step", e)
        }
    }

    /** v4: Core Growth Systems upgrade — new columns across all tables. */
    private fun migrateToV4(db: SupportSQLiteDatabase) {
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
        // Identity evidence journal
        runCatching {
            db.execSQL("""CREATE TABLE IF NOT EXISTS evidence(
                id TEXT PRIMARY KEY, identityId TEXT, text TEXT, sourceHabitId TEXT,
                date TEXT, createdAt INTEGER)""")
        }
        // ── Functional Plan (PR #8) new tables (idempotent) ─────────────
        // ── Alpha2 (PR #6): habit graduation columns ─────────────────
        runCatching { db.execSQL("ALTER TABLE habit ADD COLUMN graduated INTEGER DEFAULT 0") }
        runCatching { db.execSQL("ALTER TABLE habit ADD COLUMN graduatedAt INTEGER") }
        // ── Gap plan (PR #10) Wave 0: foreign-key and query indexes ─────
        listOf(
            "CREATE INDEX IF NOT EXISTS idx_goal_identity ON goal(identityId)",
            "CREATE INDEX IF NOT EXISTS idx_sys_goal ON sys(goalId)",
            "CREATE INDEX IF NOT EXISTS idx_habit_system ON habit(systemId)",
            "CREATE INDEX IF NOT EXISTS idx_habit_identity ON habit(identityId)",
            "CREATE INDEX IF NOT EXISTS idx_habit_status ON habit(status)",
            "CREATE INDEX IF NOT EXISTS idx_checkin_habit ON checkin(habitId)",
            "CREATE INDEX IF NOT EXISTS idx_focus_habit ON focus(habitId)",
            "CREATE INDEX IF NOT EXISTS idx_obstacle_habit ON obstacle(habitId)",
            "CREATE INDEX IF NOT EXISTS idx_flowstep_flow ON flowstep(flowId)",
            "CREATE INDEX IF NOT EXISTS idx_flowstep_habit ON flowstep(habitId)",
            "CREATE INDEX IF NOT EXISTS idx_energy_date ON energy(date)",
            "CREATE INDEX IF NOT EXISTS idx_bp_source_project ON bp_source(projectId)",
            "CREATE INDEX IF NOT EXISTS idx_bp_req_project ON bp_req(projectId)",
            "CREATE INDEX IF NOT EXISTS idx_bp_version_project ON bp_version(projectId)",
            "CREATE INDEX IF NOT EXISTS idx_pause_habit ON pause(habitId)"
        ).forEach { sql -> runCatching { db.execSQL(sql) } }

        runCatching {
            db.execSQL("""CREATE TABLE IF NOT EXISTS growth_plan(
                id TEXT PRIMARY KEY, habit_id TEXT NOT NULL, user_id TEXT DEFAULT 'local',
                phases_json TEXT NOT NULL, current_phase_index INTEGER DEFAULT 0,
                upgrade_policy_json TEXT NOT NULL, weekly_snapshots_json TEXT DEFAULT '[]',
                last_upgrade_date TEXT DEFAULT '', next_review_date TEXT DEFAULT '',
                created_at INTEGER NOT NULL)""")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_growth_plan_habit ON growth_plan(habit_id)")
        }
        runCatching {
            db.execSQL("""CREATE TABLE IF NOT EXISTS milestone(
                id TEXT PRIMARY KEY, habit_id TEXT, type TEXT NOT NULL,
                value INTEGER NOT NULL, label TEXT NOT NULL,
                acknowledged INTEGER DEFAULT 0, achieved_at INTEGER NOT NULL)""")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_milestone_habit ON milestone(habit_id)")
        }
        runCatching {
            db.execSQL("""CREATE TABLE IF NOT EXISTS sprint(
                id TEXT PRIMARY KEY, title TEXT NOT NULL, start_date TEXT NOT NULL,
                end_date TEXT NOT NULL, focus_habits_json TEXT DEFAULT '[]',
                goals_json TEXT DEFAULT '[]', status TEXT DEFAULT 'PLANNED',
                review_notes TEXT DEFAULT '', created_at INTEGER NOT NULL)""")
        }
        runCatching {
            db.execSQL("""CREATE TABLE IF NOT EXISTS journal_entry(
                id TEXT PRIMARY KEY, date TEXT NOT NULL, prompt TEXT DEFAULT '',
                content TEXT NOT NULL, mood INTEGER,
                tags_json TEXT DEFAULT '[]', created_at INTEGER NOT NULL)""")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_journal_date ON journal_entry(date)")
        }
        runCatching {
            db.execSQL("""CREATE TABLE IF NOT EXISTS routine(
                id TEXT PRIMARY KEY, title TEXT NOT NULL, trigger_text TEXT DEFAULT '',
                estimated_minutes INTEGER DEFAULT 0, status TEXT DEFAULT 'ACTIVE',
                created_at INTEGER NOT NULL)""")
            db.execSQL("""CREATE TABLE IF NOT EXISTS routine_step(
                id TEXT PRIMARY KEY, routine_id TEXT NOT NULL, habit_id TEXT,
                title TEXT NOT NULL, duration_minutes INTEGER DEFAULT 5,
                order_index INTEGER DEFAULT 0, transition_note TEXT DEFAULT '')""")
        }
        runCatching {
            db.execSQL("""CREATE TABLE IF NOT EXISTS environment_design(
                habit_id TEXT PRIMARY KEY, make_obvious_json TEXT DEFAULT '[]',
                make_attractive_json TEXT DEFAULT '[]', make_easy_json TEXT DEFAULT '[]',
                make_satisfying_json TEXT DEFAULT '[]', make_invisible_json TEXT DEFAULT '[]',
                make_unattractive_json TEXT DEFAULT '[]', make_difficult_json TEXT DEFAULT '[]',
                make_unsatisfying_json TEXT DEFAULT '[]')""")
        }
        runCatching {
            db.execSQL("""CREATE TABLE IF NOT EXISTS ai_memory(
                id TEXT PRIMARY KEY, category TEXT NOT NULL, content TEXT NOT NULL,
                importance INTEGER DEFAULT 5, last_accessed INTEGER NOT NULL,
                access_count INTEGER DEFAULT 0, created_at INTEGER NOT NULL)""")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_memory_category ON ai_memory(category)")
        }
        runCatching {
            db.execSQL("""CREATE TABLE IF NOT EXISTS proactive_suggestion(
                id TEXT PRIMARY KEY, type TEXT NOT NULL, text TEXT NOT NULL,
                priority TEXT DEFAULT 'MEDIUM', auto_action_json TEXT DEFAULT '',
                habit_id TEXT, dismissed INTEGER DEFAULT 0, applied INTEGER DEFAULT 0,
                created_at INTEGER NOT NULL)""")
        }
        runCatching {
            db.execSQL("""CREATE TABLE IF NOT EXISTS growth_phase_history(
                id TEXT PRIMARY KEY, growth_plan_id TEXT NOT NULL,
                phase_index INTEGER NOT NULL, action TEXT NOT NULL,
                consistency INTEGER DEFAULT 0, date TEXT NOT NULL, notes TEXT DEFAULT '')""")
        }
    }

    private fun migrateToV5(db: SupportSQLiteDatabase) {
        runCatching {
            db.execSQL("""CREATE TABLE IF NOT EXISTS blueprint_auto_plan(
                id TEXT PRIMARY KEY, projectId TEXT NOT NULL, phaseIndex INTEGER NOT NULL,
                whatJson TEXT NOT NULL, whenExpr TEXT NOT NULL, whereKind TEXT NOT NULL,
                howOp TEXT NOT NULL, conditionJson TEXT, status TEXT NOT NULL,
                createdAt INTEGER NOT NULL, appliedAt INTEGER)""")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_bap_project ON blueprint_auto_plan(projectId)")
        }
        runCatching { db.execSQL("ALTER TABLE bp_project ADD COLUMN modelOverride TEXT DEFAULT ''") }
    }

    /**
     * v6: quick capture inbox (Plan B F1). Additive only; nothing existing
     * changes shape, so a plain create-if-not-exists is the whole migration.
     */
    private fun migrateToV6(db: SupportSQLiteDatabase) {
        guard(db, "v6 create captured_item") { createCapturedItem(db) }
    }

    private fun createCapturedItem(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS captured_item(
                id TEXT PRIMARY KEY, text TEXT NOT NULL, kind TEXT DEFAULT 'NOTE',
                source TEXT DEFAULT 'MANUAL', state TEXT DEFAULT 'OPEN',
                converted_to_id TEXT, created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL)"""
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_captured_state ON captured_item(state, created_at)")
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
