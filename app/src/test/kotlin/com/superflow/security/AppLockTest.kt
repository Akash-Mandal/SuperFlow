package com.superflow.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockTest {

    @Test
    fun testValidPinValidation() {
        assertTrue(AppLock.validPin("1234"))
        assertTrue(AppLock.validPin("123456789012"))
        assertFalse(AppLock.validPin("123")) // Too short
        assertFalse(AppLock.validPin("1234567890123")) // Too long
        assertFalse(AppLock.validPin("123a")) // Non-digits
        assertFalse(AppLock.validPin("")) // Empty
    }

    @Test
    fun testHashPinDeterminismAndDifference() {
        val hash1 = AppLock.hashPin("1234")
        val hash2 = AppLock.hashPin("1234")
        val hash3 = AppLock.hashPin("4321")

        assertEquals(hash1, hash2)
        assertNotEquals(hash1, hash3)
        assertTrue(hash1.length == 64) // SHA-256 hex string length
    }
}
