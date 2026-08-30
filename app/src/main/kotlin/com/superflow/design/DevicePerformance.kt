package com.superflow.design

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Device performance tiering — no benchmark, just static signals that are
 * cheap to read and stable for the process lifetime. The goal is not to
 * label a device as "good" or "bad" but to choose animation budgets that
 * keep the home feed usable on the devices people actually carry.
 *
 * Low-end is deliberately narrow: only devices that are low-RAM per the
 * platform, or very old, or with few cores. Everything else is "standard"
 * and gets the full motion language. This keeps the aesthetic intact on
 * mid-range while protecting the bottom quartile from jank.
 */
object DevicePerformance {

    @Volatile private var cachedLowEnd: Boolean? = null
    @Volatile private var cachedMidEnd: Boolean? = null

    fun isLowEnd(context: Context): Boolean {
        // Manual override wins over auto detection
        val mode = com.superflow.data.Prefs.get(context).performanceMode
        when (mode) {
            com.superflow.data.Prefs.PERFORMANCE_PERFORMANCE -> return true
            com.superflow.data.Prefs.PERFORMANCE_QUALITY -> return false
        }
        cachedLowEnd?.let { return it }
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val lowRam = am?.isLowRamDevice == true
        val oldApi = Build.VERSION.SDK_INT < Build.VERSION_CODES.R
        val fewCores = Runtime.getRuntime().availableProcessors() <= 4
        val result = lowRam && (oldApi || fewCores)
        cachedLowEnd = result
        return result
    }

    fun isMidEnd(context: Context): Boolean {
        val mode = com.superflow.data.Prefs.get(context).performanceMode
        when (mode) {
            com.superflow.data.Prefs.PERFORMANCE_QUALITY -> return false
            com.superflow.data.Prefs.PERFORMANCE_PERFORMANCE -> return true
        }
        if (isLowEnd(context)) return false
        cachedMidEnd?.let { return it }
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val result = (am?.isLowRamDevice == false && Runtime.getRuntime().availableProcessors() <= 6) ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S
        cachedMidEnd = result
        return result
    }

    /** True when motion should be reduced for performance, even on mid/high. */
    fun shouldReduceMotion(context: Context): Boolean {
        val mode = com.superflow.data.Prefs.get(context).performanceMode
        when (mode) {
            com.superflow.data.Prefs.PERFORMANCE_QUALITY -> return false
            com.superflow.data.Prefs.PERFORMANCE_PERFORMANCE -> return true
        }
        return isLowEnd(context) || isMidEnd(context)
    }

    /** For tests / previews where no Context is available. */
    fun isLowEndForTest(lowRam: Boolean, sdk: Int, cores: Int): Boolean {
        val oldApi = sdk < Build.VERSION_CODES.R
        val fewCores = cores <= 4
        return lowRam && (oldApi || fewCores)
    }
}

@Composable
fun rememberIsLowEnd(): Boolean {
    val ctx = LocalContext.current
    return remember(ctx) { DevicePerformance.isLowEnd(ctx) }
}

@Composable
fun rememberIsMidEnd(): Boolean {
    val ctx = LocalContext.current
    return remember(ctx) { DevicePerformance.isMidEnd(ctx) }
}

@Composable
fun rememberShouldReduceMotion(): Boolean {
    val ctx = LocalContext.current
    // Prefs can change at runtime via the toggle; re-read on each composition
    // rather than caching, so switching modes takes effect without killing the process.
    return DevicePerformance.shouldReduceMotion(ctx)
}
