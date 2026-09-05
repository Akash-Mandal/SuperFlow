package com.superflow.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockTest {

    @Test
    fun validPinRequirements() {
        assertFalse(AppLock.validPin("123")) // too short
        assertTrue(AppLock.validPin("1234")) // min valid
        assertTrue(AppLock.validPin("12345678")) // mid valid
        assertTrue(AppLock.validPin("123456789012")) // max valid (12)
        assertFalse(AppLock.validPin("1234567890123")) // too long
        assertFalse(AppLock.validPin("123a")) // non-digit
    }

    @Test
    fun hashPinDeterministic() {
        val hash1 = AppLock.hashPin("1234")
        val hash2 = AppLock.hashPin("1234")
        val hash3 = AppLock.hashPin("4321")

        assertEquals(hash1, hash2)
        assertFalse(hash1 == hash3)
        assertEquals(64, hash1.length) // SHA-256 hex string
    }

    @Test
    fun randomCodeLengthAndHex() {
        val code1 = AppLock.randomCode()
        val code2 = AppLock.randomCode()

        assertEquals(8, code1.length)
        assertEquals(8, code2.length)
        assertTrue(code1.all { it in "0123456789abcdef" })
    }
}
