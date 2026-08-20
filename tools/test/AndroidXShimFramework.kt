@file:Suppress("unused", "UNUSED_PARAMETER")

/**
 * Companion to AndroidXShim.kt — the framework SQLite factory and the
 * `androidx.core` helpers, in their own packages.
 *
 * Desktop test harness only; never compiled into the APK. See AndroidXShim.kt
 * for the full rationale.
 */

package androidx.sqlite.db.framework

import androidx.sqlite.db.SupportSQLiteOpenHelper

class FrameworkSQLiteOpenHelperFactory : SupportSQLiteOpenHelper.Factory {
    override fun create(
        configuration: SupportSQLiteOpenHelper.Configuration
    ): SupportSQLiteOpenHelper =
        throw UnsupportedOperationException(
            "androidx.sqlite shim: desktop test harness has no SQLite implementation"
        )
}
