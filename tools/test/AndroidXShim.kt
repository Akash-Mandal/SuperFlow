@file:Suppress("unused", "UNUSED_PARAMETER")

/**
 * Minimal androidx compile shims used ONLY by the desktop JVM test harness.
 *
 * The logic suites (CoreTest, LogicTest, ParseTest, AiTest) compile the app's
 * framework-independent sources — core/, data/, domain/, ai/, blueprint/,
 * util/. A handful of those files import `androidx.sqlite` and
 * `androidx.core.content.ContextCompat` at the top level, so the symbols must
 * resolve for compilation to succeed even though the suites never execute the
 * persistence layer (they drive the pure domain logic directly).
 *
 * Upstream these types come from the pre-exploded AAR set described in
 * docs/BUILD.md. That set is fetched from Google Maven, which is not reachable
 * from every build environment; this file lets the logic suites run anyway.
 * When the real AARs are present they take classpath precedence and this file
 * is redundant — it is never compiled into the APK.
 *
 * These are signature-only stubs. Every member throws if actually invoked, so
 * a test that strays into real database work fails loudly rather than silently
 * exercising a fake.
 */

package androidx.sqlite.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor

private fun shim(): Nothing =
    throw UnsupportedOperationException(
        "androidx.sqlite shim: desktop test harness has no SQLite implementation"
    )

interface SupportSQLiteQuery {
    val sql: String
}

class SimpleSQLiteQuery(private val query: String, val bindArgs: Array<out Any?>? = null) :
    SupportSQLiteQuery {
    override val sql: String get() = query
}

interface SupportSQLiteDatabase {
    fun execSQL(sql: String)
    fun execSQL(sql: String, bindArgs: Array<out Any?>)
    fun query(query: SupportSQLiteQuery): Cursor
    fun query(query: String): Cursor
    fun query(query: String, bindArgs: Array<out Any?>): Cursor
    fun insert(table: String, conflictAlgorithm: Int, values: ContentValues): Long
    fun update(
        table: String,
        conflictAlgorithm: Int,
        values: ContentValues,
        whereClause: String?,
        whereArgs: Array<out Any?>?
    ): Int
    fun delete(table: String, whereClause: String?, whereArgs: Array<out Any?>?): Int
    fun beginTransaction()
    fun setTransactionSuccessful()
    fun endTransaction()
    fun setForeignKeyConstraintsEnabled(enabled: Boolean)
    val version: Int
}

interface SupportSQLiteOpenHelper {
    val writableDatabase: SupportSQLiteDatabase
    val readableDatabase: SupportSQLiteDatabase

    abstract class Callback(@JvmField val version: Int) {
        abstract fun onCreate(db: SupportSQLiteDatabase)
        abstract fun onUpgrade(db: SupportSQLiteDatabase, old: Int, new: Int)
        open fun onDowngrade(db: SupportSQLiteDatabase, old: Int, new: Int) {}
        open fun onConfigure(db: SupportSQLiteDatabase) {}
        open fun onOpen(db: SupportSQLiteDatabase) {}
    }

    class Configuration private constructor(
        @JvmField val context: Context?,
        @JvmField val name: String?,
        @JvmField val callback: Callback?
    ) {
        class Builder internal constructor(private val context: Context?) {
            private var name: String? = null
            private var callback: Callback? = null
            fun name(name: String?): Builder = apply { this.name = name }
            fun callback(callback: Callback): Builder = apply { this.callback = callback }
            fun noBackupDirectory(value: Boolean): Builder = this
            fun allowDataLossOnRecovery(value: Boolean): Builder = this
            fun build(): Configuration = Configuration(context, name, callback)
        }

        companion object {
            @JvmStatic
            fun builder(context: Context): Builder = Builder(context)
        }
    }

    interface Factory {
        fun create(configuration: Configuration): SupportSQLiteOpenHelper
    }
}
