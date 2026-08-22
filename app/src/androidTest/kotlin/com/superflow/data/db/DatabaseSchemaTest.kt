package com.superflow.data.db

import android.content.ContentValues
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

/**
 * Instrumented tests for database creation, migration and data safety.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseSchemaTest {

    private val db = SuperFlowDatabase.get(ApplicationProvider.getApplicationContext())
    private val d = db.db

    private val nameGen = AtomicInteger()

    private fun uniqueDb() = "test_${System.nanoTime()}_${nameGen.incrementAndGet()}"

    @Before
    fun clean() {
        // Drop test leftovers from earlier runs.
        d.query("SELECT name FROM sqlite_master WHERE type='table' AND name LIKE 'test_%'").use { c ->
            val tables = ArrayList<String>()
            while (c.moveToNext()) tables.add(c.getString(0))
            tables.forEach { t -> runCatching { d.execSQL("DROP TABLE $t") } }
        }
    }

    @Test
    fun freshDatabaseHasTheFullSchema() {
        val expected = setOf(
            "identity", "goal", "sys", "habit", "checkin", "focus", "obstacle",
            "scorecard", "flow", "flowstep", "review", "energy", "audit", "aimsg",
            "bp_project", "bp_source", "bp_req", "pause", "profile", "bp_version"
        )
        val actual = mutableSetOf<String>()
        d.query("SELECT name FROM sqlite_master WHERE type='table'").use { c ->
            while (c.moveToNext()) actual.add(c.getString(0))
        }
        assertTrue("missing tables: ${expected - actual}", expected.all { it in actual })
    }

    @Test
    fun productionDatabaseUsesWalJournalMode() {
        d.query("PRAGMA journal_mode").use { c ->
            assertTrue("journal_mode not WAL", c.moveToFirst() && c.getString(0).equals("wal", true))
        }
    }

    @Test
    fun migrationFromV1ConvertsDaysMaskToRecurrenceRule() {
        val name = uniqueDb()
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(
                ApplicationProvider.getApplicationContext()
            ).name(name).callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    // Minimal v1 shape: the pre-recurrence schema.
                    db.execSQL(
                        """CREATE TABLE habit(
                            id TEXT PRIMARY KEY, title TEXT, daysMask INTEGER,
                            status TEXT, createdAt INTEGER)"""
                    )
                    db.execSQL("CREATE TABLE bp_project(id TEXT PRIMARY KEY, name TEXT)")
                    db.execSQL(
                        """INSERT INTO habit(id, title, daysMask, status, createdAt)
                           VALUES ('h1', 'Walk', 42, 'ACTIVE', 1), ('h2', 'Read', 0, 'ACTIVE', 2)"""
                    )
                }
                override fun onUpgrade(
                    db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int
                ) = Schema.upgrade(db, oldVersion, newVersion)
            }).build()
        )
        val v1 = helper.writableDatabase
        // Force the upgrade path by reopening through a helper that claims v3.
        val upgradeHelper = FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(
                ApplicationProvider.getApplicationContext()
            ).name(name).callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(SuperFlowDatabase.VERSION) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    // Never called: the file already exists at v1.
                }
                override fun onUpgrade(
                    db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int
                ) = Schema.upgrade(db, oldVersion, newVersion)
            }).build()
        )
        val v3 = upgradeHelper.writableDatabase

        // 42 = bits 2,3,5,6,7 -> Tue, Wed, Fri, Sat, Sun
        v3.query("SELECT recurrenceRule, scheduleVersion, startDate FROM habit WHERE id='h1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("WEEKLY:2,3,5,6,7", c.getString(0))
            assertEquals(1, c.getInt(1))
        }
        // Zero mask became the every-day default.
        v3.query("SELECT recurrenceRule FROM habit WHERE id='h2'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("WEEKLY:1,2,3,4,5,6,7", c.getString(0))
        }
        // v2/v3 tables exist and original rows survived.
        v3.query("SELECT title FROM habit WHERE id='h1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Walk", c.getString(0))
        }
        val hasPause = v3.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='pause'"
        ).use { it.moveToFirst() }
        val hasBpVersion = v3.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='bp_version'"
        ).use { it.moveToFirst() }
        assertTrue(hasPause)
        assertTrue(hasBpVersion)
        helper.close()
        upgradeHelper.close()
        d.execSQL("DROP TABLE IF EXISTS ${name.replaceFirst("test_", "test_")}") // no-op safety
    }

    @Test
    fun noOpUpgradeOnCurrentVersionKeepsData() {
        val before = d.query("SELECT COUNT(*) FROM habit").use { c -> c.moveToFirst(); c.getInt(0) }
        Schema.upgrade(d, SuperFlowDatabase.VERSION, SuperFlowDatabase.VERSION)
        val after = d.query("SELECT COUNT(*) FROM habit").use { c -> c.moveToFirst(); c.getInt(0) }
        assertEquals(before, after)
    }

    @Test
    fun contentValuesHelperTypesCorrectly() {
        val v = contentValuesOf(
            "s" to "x", "i" to 3, "l" to 9L, "d" to 1.5, "b" to true, "n" to null
        )
        assertEquals("x", v.get("s"))
        assertEquals(3, v.getAsInteger("i"))
        assertEquals(9L, v.getAsLong("l"))
        assertEquals(1.5, v.getAsDouble("d"), 0.001)
        assertEquals(1, v.getAsInteger("b"))
        assertTrue(v.get("missing") == null)
    }
}
