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

    fun isLowEnd(context: Context): Boolean {
        cachedLowEnd?.let { return it }
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val lowRam = am?.isLowRamDevice == true
        val oldApi = Build.VERSION.SDK_INT < Build.VERSION_CODES.R
        val fewCores = Runtime.getRuntime().availableProcessors() <= 4
        // Low-end only if low-RAM + (old or few cores). A modern 4-core mid-range
        // is not low-end; a low-RAM Go device is.
        val result = lowRam && (oldApi || fewCores)
        cachedLowEnd = result
        return result
    }

    fun isMidEnd(context: Context): Boolean {
        if (isLowEnd(context)) return false
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        // Mid is low-RAM false but still constrained, or older API.
        return (am?.isLowRamDevice == false && Runtime.getRuntime().availableProcessors() <= 6) ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S
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
