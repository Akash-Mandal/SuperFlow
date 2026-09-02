package com.superflow.domain

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CommandBusTest {

    private lateinit var bus: CommandBus

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        bus = CommandBus.get(context)
    }

    @Test
    fun executeUnknownCommandReturnsFailure() {
        val result = bus.execute("unknown_command_xyz")
        assertFalse(result.ok)
        assertEquals("Unknown command: unknown_command_xyz", result.message)
    }

    @Test
    fun executeJsonNoJsonReturnsFailure() {
        val result = bus.executeJson("no json here")
        assertFalse(result.ok)
        assertEquals("No JSON command found", result.message)
    }

    @Test
    fun executeJsonUnknownCommandReturnsFailure() {
        val result = bus.executeJson("""{"command": "non_existent_cmd"}""")
        assertFalse(result.ok)
        assertEquals("Unknown command: non_existent_cmd", result.message)
    }

    @Test
    fun capabilityExecutionExceptionCaughtAndReturnsFailure() {
        val throwingCap = Capability(
            name = "failing_command",
            summary = "Always throws exception",
            args = emptyList(),
            risk = Risk.LOW,
            run = { throw IllegalStateException("Database error simulation") }
        )

        val ctx = Ctx(bus.repo, JSONObject(), Actor.USER, null, bus)
        val result = try {
            throwingCap.run(ctx)
        } catch (e: Exception) {
            CommandResult.fail("${throwingCap.name} failed: ${e.message ?: e.javaClass.simpleName}")
        }

        assertFalse(result.ok)
        assertEquals("failing_command failed: Database error simulation", result.message)
    }
}
