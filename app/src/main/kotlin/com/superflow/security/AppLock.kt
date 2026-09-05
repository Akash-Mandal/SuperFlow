package com.superflow.security

import com.superflow.data.Prefs
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Simple, offline app lock.
 *
 * The PIN is never stored: only a salted SHA-256 hash is kept in
 * SharedPreferences. This keeps the app private on a shared device; it is
 * not intended to defend against a rooted device or a forensic attacker.
 *
 * [graceUntil] records how long the app may stay unlocked after going to the
 * background, so switching away briefly does not force a re-prompt.
 */
object AppLock {

    private const val SALT_PREFIX = "superflow:"
    private const val MIN_PIN = 4
    private const val MAX_PIN = 12

    private var graceUntil: Long = 0L

    fun isEnabled(prefs: Prefs): Boolean = prefs.appLockEnabled && prefs.appLockPinHash.isNotBlank()

    fun validPin(pin: String): Boolean = pin.length in MIN_PIN..MAX_PIN && pin.all { it.isDigit() }

    fun hashPin(pin: String): String {
        val salt = SALT_PREFIX.toByteArray()
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        digest.update(pin.toByteArray())
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun setPin(prefs: Prefs, pin: String): Boolean {
        if (!validPin(pin)) return false
        prefs.appLockPinHash = hashPin(pin)
        prefs.appLockEnabled = true
        return true
    }

    // Dummy hash used for constant-time comparison when no PIN is configured
    private val DUMMY_HASH = "0".repeat(64).toByteArray()

    fun checkPin(prefs: Prefs, pin: String): Boolean {
        val storedHash = prefs.appLockPinHash
        val candidateHash = hashPin(pin).toByteArray()
        val storedBytes = if (storedHash.isNotBlank()) storedHash.toByteArray() else DUMMY_HASH
        val matches = MessageDigest.isEqual(storedBytes, candidateHash)
        return storedHash.isNotBlank() && matches
    }

    fun clearPin(prefs: Prefs) {
        prefs.appLockPinHash = ""
        prefs.appLockEnabled = false
    }

    /** Call when the app leaves the foreground. */
    fun onBackgrounded(prefs: Prefs) {
        if (!isEnabled(prefs)) return
        graceUntil = System.currentTimeMillis() + prefs.appLockGraceSeconds * 1000L
    }

    /** True when the lock screen should be shown on resume. */
    fun shouldLock(prefs: Prefs): Boolean {
        if (!isEnabled(prefs)) return false
        if (graceUntil == 0L) return true
        return System.currentTimeMillis() >= graceUntil
    }

    /** Call after a successful unlock so grace applies next time. */
    fun onUnlocked() {
        graceUntil = 0L
    }

    /** Generate a short random recovery code (unused yet; reserved). */
    fun randomCode(): String {
        val bytes = ByteArray(4)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
