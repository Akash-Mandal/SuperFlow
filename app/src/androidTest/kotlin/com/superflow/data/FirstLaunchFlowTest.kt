package com.superflow.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.superflow.data.model.Level
import com.superflow.domain.Actor
import com.superflow.domain.CommandBus
import com.superflow.domain.Insights
import com.superflow.widget.TodayWidget
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Instrumented tests for the clean-install and already-onboarded paths:
 * preference state, first-launch data creation, empty-database rendering
 * inputs and widget safety with zero installed widgets.
 */
@RunWith(AndroidJUnit4::class)
class FirstLaunchFlowTest {

    private lateinit var prefs: Prefs

    @Before
    fun reset() {
        prefs = Prefs.get(ApplicationProvider.getApplicationContext())
        prefs.resetAll()
    }

    private fun onBg(block: () -> Unit) {
        val latch = CountDownLatch(1)
        getInstrumentation().runOnMainSync {
            block()
            latch.countDown()
        }
        assertTrue("timed out", latch.await(30, TimeUnit.SECONDS))
    }

    /* ------------------------------------------------------- preference state */

    @Test
    fun cleanStateHasOnboardingPendingAndSaneDefaults() {
        prefs.resetAll()
        assertFalse(prefs.onboarded)
        assertEquals(Prefs.THEME_SYSTEM, prefs.themeMode)
        assertTrue(prefs.remindersEnabled)
        assertFalse(prefs.notifPermissionAsked)
        assertEquals("22:00", prefs.quietFrom)
        assertEquals("07:00", prefs.quietTo)
        assertFalse(prefs.hasApiKey())
    }

    @Test
    fun onboardedStateSurvivesAndThemesApply() {
        prefs.onboarded = true
        prefs.themeMode = Prefs.THEME_DARK
        assertEquals(Prefs.THEME_DARK, prefs.themeMode)
        assertTrue(prefs.onboarded)
        prefs.resetAll()
        assertFalse(prefs.onboarded)
    }

    /* --------------------------------------------------- first launch creation */

    @Test
    fun onboardingCreationWritesIdentityGoalSystemHabit() {
        val bus = CommandBus.get(ApplicationProvider.getApplicationContext())
        val ids = arrayOfNulls<String>(4)

        onBg {
            ids[0] = bus.execute("create_identity",
                JSONObject("""{"statement":"someone who moves","lifeArea":"HEALTH"}"""),
                Actor.USER).data?.optString("id")
            ids[1] = bus.execute("create_goal", JSONObject(
                """{"title":"Walk daily","why":"health","identityId":"${'$'}{ids[0]}"}"""),
                Actor.USER).data?.optString("id")
            ids[2] = bus.execute("create_system", JSONObject(
                """{"title":"Morning walk","goalId":"${'$'}{ids[1]}"}"""),
                Actor.USER).data?.optString("id")
            ids[3] = bus.execute("create_habit", JSONObject(
                """{"title":"Walk 10 minutes","tinyStart":"Step outside","cueTime":"07:30",
                    "systemId":"${'$'}{ids[2]}","identityId":"${'$'}{ids[0]}","days":"daily"}"""),
                Actor.USER).data?.optString("id")
        }

        ids.forEach { assertNotNull(it) }

        val repo = Repository.get(ApplicationProvider.getApplicationContext())
        val habit = repo.habit(ids[3])
        assertNotNull(habit)
        assertEquals("Walk 10 minutes", habit!!.title)
        assertEquals("Step outside", habit.tinyStart)
        assertEquals("07:30", habit.cueTime)
        assertEquals(ids[2], habit.systemId)
        // "daily" encodes as every weekday.
        assertEquals("WEEKLY:1,2,3,4,5,6,7", habit.recurrenceRule)

        // The habit is scheduled for today and shows in the Today list.
        val today = repo.clock.today()
        val todays = repo.todayHabits(today)
        assertTrue("habit missing from today list", todays.any { it.habit.id == habit.id })
    }

    @Test
    fun emptyDatabaseRendersWithoutExceptions() {
        val repo = Repository.get(ApplicationProvider.getApplicationContext())
        // Wipe to simulate a clean install.
        repo.deleteAllData()
        onBg {
            val today = repo.clock.today()
            val habits = repo.todayHabits(today)
            assertTrue(habits.isEmpty())
            val (done, total) = Insights.dayProgress(repo, today)
            assertEquals(0, done)
            assertEquals(0, total)
            val summary = Insights.todaySummary(repo, today)
            assertTrue(summary.contains("Nothing is scheduled"))
        }
    }

    @Test
    fun checkInAndUndoRoundTrip() {
        val bus = CommandBus.get(ApplicationProvider.getApplicationContext())
        val repo = Repository.get(ApplicationProvider.getApplicationContext())
        repo.deleteAllData()
        val habitId = onBgReturn {
            bus.execute("create_habit",
                org.json.JSONObject("""{"title":"Undo test habit","days":"daily"}"""), Actor.USER)
                .data?.optString("id")
        }
        assertNotNull(habitId)
        val date = repo.clock.today().toString()

        onBg {
            val res = bus.execute("check_in",
                org.json.JSONObject("""{"habit":"$habitId","level":"TINY"}"""), Actor.USER)
            assertTrue(res.ok)
        }
        val ci = repo.checkIn(habitId!!, date)
        assertNotNull(ci)
        assertEquals(Level.TINY, ci!!.level)

        // Undo the last command (the check-in).
        val audit = repo.audit(5).first()
        val undoRes = onBgReturn { bus.undo(audit) }
        assertTrue(undoRes.ok)
        assertEquals(null, repo.checkIn(habitId!!, date))
    }

    /* ----------------------------------------------------------------- widget */

    @Test
    fun widgetRefreshWithZeroIdsIsSafe() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        // No widget is installed in the test environment; this must be a no-op,
        // not a crash. Run several times back to back (debounce + force).
        repeat(3) {
            TodayWidget.refresh(context)
            TodayWidget.refresh(context)
        }
        // If it had thrown, the test fails here.
    }

    /* --------------------------------------------------------------- helpers */

    private fun <T> onBgReturn(block: () -> T): T {
        var result: T? = null
        val latch = CountDownLatch(1)
        getInstrumentation().runOnMainSync {
            result = block()
            latch.countDown()
        }
        assertTrue("timed out", latch.await(30, TimeUnit.SECONDS))
        @Suppress("UNCHECKED_CAST")
        return result as T
    }
}
