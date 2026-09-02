package com.superflow.domain

import android.content.Context
import android.content.ContextWrapper
import com.superflow.core.time.FixedClock
import com.superflow.data.Repository
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class CommandBusTest {

    private fun testContext(): Context = object : ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
    }

    private val fixedDate: LocalDate = LocalDate.of(2026, 8, 26)
    private val fixedClock: FixedClock = FixedClock(
        fixedDate.atStartOfDay(ZoneId.of("UTC")).toInstant(),
        ZoneId.of("UTC")
    )

    @Test
    fun `CommandResult fail creates failed result`() {
        val result = CommandResult.fail("Something went wrong")
        assertFalse(result.ok)
        assertEquals("Something went wrong", result.message)
        assertNull(result.auditId)
        assertNull(result.data)
    }

    @Test
    fun `CommandResult properties hold passed values`() {
        val dataObj = JSONObject().apply { put("key", "value") }
        val result = CommandResult(
            ok = true,
            message = "Success",
            auditId = "audit-123",
            data = dataObj
        )
        assertTrue(result.ok)
        assertEquals("Success", result.message)
        assertEquals("audit-123", result.auditId)
        assertEquals(dataObj, result.data)
    }

    @Test
    fun `Capability retains constructor properties`() {
        val capability = Capability(
            name = "test_cmd",
            summary = "Test command summary",
            args = listOf("param1" to "string", "param2" to "int"),
            risk = Risk.MEDIUM,
            destructive = true,
            run = { ctx -> CommandResult(true, "Executed " + ctx.str("param1")) }
        )

        assertEquals("test_cmd", capability.name)
        assertEquals("Test command summary", capability.summary)
        assertEquals(2, capability.args.size)
        assertEquals("param1", capability.args[0].first)
        assertEquals(Risk.MEDIUM, capability.risk)
        assertTrue(capability.destructive)
    }

    @Test
    fun `Ctx argument helper methods resolve values and defaults`() {
        val repo = Repository.createForTest(testContext(), fixedClock)
        val bus = CommandBus.get(testContext())

        val json = JSONObject().apply {
            put("strKey", "hello")
            put("intKey", 42)
            put("dblKey", 3.14)
            put("boolKey", true)
            put("nullKey", JSONObject.NULL)
        }

        val ctx = Ctx(
            repo = repo,
            args = json,
            actor = Actor.USER,
            groupId = "group-1",
            bus = bus
        )

        assertEquals("hello", ctx.str("strKey"))
        assertEquals("default", ctx.str("missingKey", "default"))

        assertEquals("hello", ctx.strOrNull("strKey"))
        assertNull(ctx.strOrNull("missingKey"))
        assertNull(ctx.strOrNull("nullKey"))

        assertEquals(42, ctx.int("intKey", 0))
        assertEquals(10, ctx.int("missingKey", 10))
        assertEquals(10, ctx.int("nullKey", 10))

        assertEquals(3.14, ctx.dbl("dblKey", 0.0), 0.0001)
        assertEquals(1.0, ctx.dbl("missingKey", 1.0), 0.0001)
        assertEquals(1.0, ctx.dbl("nullKey", 1.0), 0.0001)

        assertTrue(ctx.bool("boolKey", false))
        assertFalse(ctx.bool("missingKey", false))
        assertTrue(ctx.bool("nullKey", true))
    }

    @Test
    fun `Ctx localDate resolves relative and explicit dates correctly`() {
        val repo = Repository.createForTest(testContext(), fixedClock)
        val bus = CommandBus.get(testContext())

        val json = JSONObject().apply {
            put("date", "today")
            put("yesterdayKey", "yesterday")
            put("tomorrowKey", "tomorrow")
            put("explicitKey", "2026-05-15")
            put("invalidKey", "not-a-date")
            put("emptyKey", "   ")
        }

        val ctx = Ctx(repo = repo, args = json, actor = Actor.USER, groupId = null, bus = bus)

        // "today" or default key "date"
        assertEquals(fixedDate, ctx.localDate("date"))
        assertEquals("2026-08-26", ctx.date("date"))

        // "yesterday"
        assertEquals(fixedDate.minusDays(1), ctx.localDate("yesterdayKey"))

        // "tomorrow"
        assertEquals(fixedDate.plusDays(1), ctx.localDate("tomorrowKey"))

        // explicit date
        assertEquals(LocalDate.of(2026, 5, 15), ctx.localDate("explicitKey"))
        assertEquals("2026-05-15", ctx.date("explicitKey"))

        // invalid date falls back to today
        assertEquals(fixedDate, ctx.localDate("invalidKey"))

        // empty date string falls back to today
        assertEquals(fixedDate, ctx.localDate("emptyKey"))

        // missing key falls back to today
        assertEquals(fixedDate, ctx.localDate("missingKey"))
    }

    @Test
    fun `CommandBus capability lookup and manifest`() {
        val bus = CommandBus.get(testContext())

        val caps = bus.capabilities
        assertTrue(caps.isNotEmpty())

        val firstCapName = caps.first().name
        val foundCap = bus.capability(firstCapName.uppercase())
        assertNotNull(foundCap)
        assertEquals(firstCapName.lowercase(), foundCap?.name?.lowercase())

        assertNull(bus.capability("non_existent_capability_12345"))

        val manifestStr = bus.manifest()
        assertTrue(manifestStr.contains(firstCapName))
    }

    @Test
    fun `CommandBus execute handles unknown capabilities`() {
        val bus = CommandBus.get(testContext())
        val result = bus.execute("unknown_command_xyz")
        assertFalse(result.ok)
        assertTrue(result.message.contains("Unknown command"))
    }

    @Test
    fun `CommandBus executeJson parses json and handles invalid input`() {
        val bus = CommandBus.get(testContext())

        val invalidResult = bus.executeJson("no json content here")
        assertFalse(invalidResult.ok)
        assertEquals("No JSON command found", invalidResult.message)

        val unknownJsonResult = bus.executeJson("""{"command": "non_existent_command"}""")
        assertFalse(unknownJsonResult.ok)
        assertTrue(unknownJsonResult.message.contains("Unknown command"))
    }

    @Test
    fun `okResult creates successful CommandResult`() {
        val dataObj = JSONObject().apply { put("status", "ok") }
        val res = okResult("Done", dataObj, "audit-99")
        assertTrue(res.ok)
        assertEquals("Done", res.message)
        assertEquals(dataObj, res.data)
        assertEquals("audit-99", res.auditId)
    }

    @Test
    fun `undoDelete creates expected JSON object`() {
        val undo = undoDelete("habit", "h-123")
        assertEquals("deleteRow", undo.getString("kind"))
        assertEquals("habit", undo.getString("table"))
        assertEquals("h-123", undo.getString("id"))
    }

    @Test
    fun `undoRestore creates expected JSON object`() {
        val row = JSONObject().apply { put("id", "g-456"); put("title", "Goal") }
        val undo = undoRestore("goal", row)
        assertEquals("restoreRow", undo.getString("kind"))
        assertEquals("goal", undo.getString("table"))
        assertEquals("g-456", undo.getJSONObject("row").getString("id"))
    }

    @Test
    fun `Actor and Risk enums contain expected values`() {
        val actors = Actor.values()
        assertEquals(3, actors.size)
        assertTrue(actors.contains(Actor.USER))
        assertTrue(actors.contains(Actor.AI))
        assertTrue(actors.contains(Actor.SYSTEM))

        val risks = Risk.values()
        assertEquals(3, risks.size)
        assertTrue(risks.contains(Risk.LOW))
        assertTrue(risks.contains(Risk.MEDIUM))
        assertTrue(risks.contains(Risk.HIGH))
    }

    @Test
    fun `CommandEvent retains emitted command result details`() {
        val res = CommandResult(true, "Success")
        val event = CommandEvent("create_habit", Actor.AI, res)
        assertEquals("create_habit", event.command)
        assertEquals(Actor.AI, event.actor)
        assertEquals(res, event.result)
    }
}
