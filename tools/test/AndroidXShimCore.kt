@file:Suppress("unused", "UNUSED_PARAMETER")

/**
 * Companion to AndroidXShim.kt — `androidx.core.content.ContextCompat`.
 *
 * Desktop test harness only; never compiled into the APK. See AndroidXShim.kt
 * for the full rationale.
 */

package androidx.core.content

import android.content.Context

object ContextCompat {
    const val RECEIVER_NOT_EXPORTED = 4
    const val RECEIVER_EXPORTED = 2

    @JvmStatic
    fun checkSelfPermission(context: Context, permission: String): Int =
        throw UnsupportedOperationException(
            "androidx.core shim: desktop test harness has no permission model"
        )

    @JvmStatic
    fun getColor(context: Context, id: Int): Int =
        throw UnsupportedOperationException("androidx.core shim: no resources on desktop")

    @JvmStatic
    fun getSystemService(context: Context, cls: Class<*>): Any? =
        throw UnsupportedOperationException("androidx.core shim: no system services on desktop")
}
