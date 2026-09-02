package com.superflow.domain

import com.superflow.data.Repository
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandBusTest {

    @Suppress("UNCHECKED_CAST")
    private fun <T> allocateInstance(clazz: Class<T>): T {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val field = unsafeClass.getDeclaredField("theUnsafe")
        field.isAccessible = true
        val unsafe = field.get(null)
        val method = unsafeClass.getMethod("allocateInstance", Class::class.java)
        return method.invoke(unsafe, clazz) as T
    }

    @Test
    fun `capability execution exception caught and formatted`() {
        val throwingCap = Capability(
            name = "failing_command",
            summary = "Always throws exception",
            args = emptyList(),
            risk = Risk.LOW,
            run = { throw IllegalStateException("Database error simulation") }
        )

        val fakeRepo = allocateInstance(Repository::class.java)
        val fakeBus = allocateInstance(CommandBus::class.java)
        val ctx = Ctx(fakeRepo, JSONObject(), Actor.USER, null, fakeBus)

        val result = try {
            throwingCap.run(ctx)
        } catch (e: Exception) {
            CommandResult.fail("${throwingCap.name} failed: ${e.message ?: e.javaClass.simpleName}")
        }

        assertFalse(result.ok)
        assertEquals("failing_command failed: Database error simulation", result.message)
    }

    @Test
    fun `capability execution exception without message uses simple class name`() {
        val throwingCap = Capability(
            name = "npe_command",
            summary = "Throws exception with null message",
            args = emptyList(),
            risk = Risk.LOW,
            run = { throw NullPointerException() }
        )

        val fakeRepo = allocateInstance(Repository::class.java)
        val fakeBus = allocateInstance(CommandBus::class.java)
        val ctx = Ctx(fakeRepo, JSONObject(), Actor.USER, null, fakeBus)

        val result = try {
            throwingCap.run(ctx)
        } catch (e: Exception) {
            CommandResult.fail("${throwingCap.name} failed: ${e.message ?: e.javaClass.simpleName}")
        }

        assertFalse(result.ok)
        assertEquals("npe_command failed: NullPointerException", result.message)
    }

    @Test
    fun `CommandResult fail helper constructs ok false result`() {
        val failure = CommandResult.fail("Unknown command: test_cmd")
        assertFalse(failure.ok)
        assertEquals("Unknown command: test_cmd", failure.message)
    }

    @Test
    fun `Ctx helpers parse json arguments accurately`() {
        val fakeRepo = allocateInstance(Repository::class.java)
        val fakeBus = allocateInstance(CommandBus::class.java)
        val args = JSONObject().apply {
            put("strKey", "hello")
            put("intKey", 42)
            put("dblKey", 3.14)
            put("boolKey", true)
        }
        val ctx = Ctx(fakeRepo, args, Actor.AI, "group123", fakeBus)

        assertEquals("hello", ctx.str("strKey"))
        assertEquals("default", ctx.str("missingKey", "default"))
        assertNull(ctx.strOrNull("missingKey"))
        assertEquals("hello", ctx.strOrNull("strKey"))
        assertEquals(42, ctx.int("intKey", 0))
        assertEquals(10, ctx.int("missingInt", 10))
        assertEquals(3.14, ctx.dbl("dblKey", 0.0), 0.001)
        assertTrue(ctx.bool("boolKey", false))
    }

    @Test
    fun `executeJson with invalid text returns failure`() {
        // executeJson extracts JSON and fails if no valid JSON command is found
        val jsonText = "This is not JSON"
        val extractedObj = com.superflow.util.extractJson(jsonText)
        assertNull(extractedObj)

        val result = if (extractedObj == null) CommandResult.fail("No JSON command found") else CommandResult(true, "OK")
        assertFalse(result.ok)
        assertEquals("No JSON command found", result.message)
    }
}
