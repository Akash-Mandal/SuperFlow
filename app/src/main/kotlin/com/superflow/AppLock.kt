package com.superflow

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.superflow.data.Prefs

/**
 * Process-wide app-lock state.
 *
 * Tracks the last successful unlock so the app can re-lock after the
 * configured timeout. `Immediately` locks on every app (re)open; the other
 * values re-lock only after that many minutes away.
 */
object AppLock {

    @Volatile
    private var lastUnlockedAt = 0L

    fun unlock() { lastUnlockedAt = System.currentTimeMillis() }

    fun shouldLock(prefs: Prefs): Boolean {
        if (!prefs.appLockEnabled) return false
        // A PIN method with no PIN configured is not lockable yet.
        if (prefs.appLockMethod == "pin" && !prefs.hasAppLockPin()) return false
        val last = lastUnlockedAt
        if (last == 0L) return true
        val timeoutMin = prefs.appLockTimeout
        if (timeoutMin <= 0) return false
        return System.currentTimeMillis() - last > timeoutMin * 60_000L
    }

    /** Whether the device can run the framework biometric prompt. */
    fun biometricAvailable(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 28) return false
        return if (Build.VERSION.SDK_INT >= 29) {
            val bm = context.getSystemService(android.hardware.biometrics.BiometricManager::class.java)
            bm?.canAuthenticate() == android.hardware.biometrics.BiometricManager.BIOMETRIC_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)
        }
    }
}
