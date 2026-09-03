package com.superflow.security

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import com.superflow.data.Prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockTest {

    private class MemorySharedPreferences : SharedPreferences {
        val map = mutableMapOf<String, Any?>()

        override fun getAll(): Map<String, *> = map
        override fun getString(key: String, defValue: String?): String? =
            map[key] as? String ?: defValue
        override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? =
            @Suppress("UNCHECKED_CAST") (map[key] as? Set<String> ?: defValues)
        override fun getInt(key: String, defValue: Int): Int =
            map[key] as? Int ?: defValue
        override fun getLong(key: String, defValue: Long): Long =
            map[key] as? Long ?: defValue
        override fun getFloat(key: String, defValue: Float): Float =
            map[key] as? Float ?: defValue
        override fun getBoolean(key: String, defValue: Boolean): Boolean =
            map[key] as? Boolean ?: defValue
        override fun contains(key: String): Boolean = map.containsKey(key)
        override fun edit(): SharedPreferences.Editor = Editor()
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        inner class Editor : SharedPreferences.Editor {
            override fun putString(key: String, value: String?): SharedPreferences.Editor { map[key] = value; return this }
            override fun putStringSet(key: String, values: Set<String>?): SharedPreferences.Editor { map[key] = values; return this }
            override fun putInt(key: String, value: Int): SharedPreferences.Editor { map[key] = value; return this }
            override fun putLong(key: String, value: Long): SharedPreferences.Editor { map[key] = value; return this }
            override fun putFloat(key: String, value: Float): SharedPreferences.Editor { map[key] = value; return this }
            override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor { map[key] = value; return this }
            override fun remove(key: String): SharedPreferences.Editor { map.remove(key); return this }
            override fun clear(): SharedPreferences.Editor { map.clear(); return this }
            override fun apply() {}
            override fun commit(): Boolean = true
        }
    }

    private fun createTestPrefs(): Prefs {
        val prefsSp = MemorySharedPreferences()
        val secretsSp = MemorySharedPreferences()

        val context = object : ContextWrapper(null) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences {
                return if (name == "superflow_secrets") secretsSp else prefsSp
            }
        }

        val constructor = Prefs::class.java.getDeclaredConstructor(Context::class.java)
        constructor.isAccessible = true
        return constructor.newInstance(context)
    }

    @Test
    fun testValidPin() {
        assertTrue(AppLock.validPin("1234"))
        assertTrue(AppLock.validPin("123456"))
        assertTrue(AppLock.validPin("123456789012"))

        assertFalse(AppLock.validPin("123"))
        assertFalse(AppLock.validPin("1234567890123"))
        assertFalse(AppLock.validPin(""))

        assertFalse(AppLock.validPin("abcd"))
        assertFalse(AppLock.validPin("123a"))
        assertFalse(AppLock.validPin("1234 "))
        assertFalse(AppLock.validPin(" 1234"))
        assertFalse(AppLock.validPin("12-34"))
    }

    @Test
    fun testHashPin() {
        val pin = "1234"
        val hash1 = AppLock.hashPin(pin)
        val hash2 = AppLock.hashPin(pin)

        assertEquals(hash1, hash2)
        assertEquals(64, hash1.length)

        val hashDiff = AppLock.hashPin("4321")
        assertNotEquals(hash1, hashDiff)
    }

    @Test
    fun testSetAndCheckPin() {
        val prefs = createTestPrefs()
        assertFalse(AppLock.isEnabled(prefs))

        // Reject invalid PIN
        assertFalse(AppLock.setPin(prefs, "123"))
        assertFalse(AppLock.isEnabled(prefs))

        // Set valid PIN
        assertTrue(AppLock.setPin(prefs, "1234"))
        assertTrue(AppLock.isEnabled(prefs))

        // Check PIN with matching, wrong, and invalid PIN inputs
        assertTrue(AppLock.checkPin(prefs, "1234"))
        assertFalse(AppLock.checkPin(prefs, "4321"))
        assertFalse(AppLock.checkPin(prefs, "123"))
        assertFalse(AppLock.checkPin(prefs, "1234567890123"))
        assertFalse(AppLock.checkPin(prefs, "abcd"))

        // Clear PIN
        AppLock.clearPin(prefs)
        assertFalse(AppLock.isEnabled(prefs))
        assertFalse(AppLock.checkPin(prefs, "1234"))
    }

    @Test
    fun testLockAndGracePeriod() {
        val prefs = createTestPrefs()
        AppLock.setPin(prefs, "1234")

        // Lock state when grace empty
        AppLock.onUnlocked()
        assertTrue(AppLock.shouldLock(prefs))

        // Backgrounded sets grace period
        prefs.appLockGraceSeconds = 10
        AppLock.onBackgrounded(prefs)
        assertFalse(AppLock.shouldLock(prefs))
    }

    @Test
    fun testRandomCode() {
        val code1 = AppLock.randomCode()
        val code2 = AppLock.randomCode()

        assertEquals(8, code1.length)
        assertEquals(8, code2.length)
    }
}
