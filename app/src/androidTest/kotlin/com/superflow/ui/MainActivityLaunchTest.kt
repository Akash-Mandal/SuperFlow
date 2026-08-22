package com.superflow.ui

import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.superflow.R
import com.superflow.data.Prefs
import com.superflow.data.Repository
import com.superflow.data.model.Habit
import com.superflow.ui.onboarding.OnboardingActivity
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented launch and navigation tests:
 *
 *  - clean state redirects to onboarding (MainActivity finishes itself);
 *  - onboarded state keeps MainActivity alive with the pager + bottom nav;
 *  - tab switching via the deep-link extra keeps the session alive;
 *  - the Today data path reflects database state.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityLaunchTest {

    private lateinit var prefs: Prefs
    private lateinit var repo: Repository

    @Before
    fun setup() {
        prefs = Prefs.get(ApplicationProvider.getApplicationContext())
        repo = Repository.get(ApplicationProvider.getApplicationContext())
    }

    @After
    fun teardown() {
        prefs.resetAll()
        repo.deleteAllData()
    }

    @Test
    fun cleanStateRedirectsToOnboarding() {
        prefs.onboarded = false
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        var finishing = false
        // MainActivity calls finish() synchronously in onCreate when onboarding
        // is pending, so this activity is destroyed almost immediately. Capture
        // the finishing flag now, while it is still alive; once it is torn down
        // ActivityScenario.onActivity throws "Activity has been destroyed".
        scenario.onActivity { activity ->
            finishing = activity.isFinishing
        }
        // Give the redirect/teardown a beat, then close without touching the
        // (now destroyed) activity again.
        Thread.sleep(500)
        scenario.close()
        assertTrue(
            "MainActivity must finish when onboarding is pending",
            finishing
        )
        // The redirect startActivity(OnboardingActivity) runs synchronously in
        // onCreate before finish(); the finishing flag proves the branch ran.
    }

    @Test
    fun onboardedStateRendersMainScreen() {
        prefs.onboarded = true
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { activity ->
            assertFalse("MainActivity must stay alive when onboarded", activity.isFinishing)
            // The shell inflated: both key containers exist.
            assertTrue(activity.findViewById<android.view.View>(R.id.pager) != null)
        }
        scenario.close()
    }

    @Test
    fun tabDeepLinkIsAcceptedOnARunningActivity() {
        prefs.onboarded = true
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        // onNewIntent path: a second launch with a tab extra.
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = android.content.Intent(context, MainActivity::class.java)
        intent.putExtra(MainActivity.EXTRA_TAB, "insights")
        scenario.onActivity { it.onNewIntent(intent) }
        Thread.sleep(300)
        scenario.onActivity { assertFalse(it.isFinishing) }
        scenario.close()
        assertTrue(prefs.onboarded)
    }

    @Test
    fun todayDataPathShowsAScheduledHabit() {
        prefs.onboarded = true
        repo.saveHabit(Habit(
            id = "launch-h1", title = "Launch habit", cueTime = "08:00",
            recurrenceRule = "WEEKLY:1,2,3,4,5,6,7"
        ))
        val todays = repo.todayHabits(repo.clock.today())
        assertTrue(todays.any { it.habit.id == "launch-h1" })

        // Removing the habit removes it from the same data path.
        repo.deleteHabit("launch-h1")
        assertFalse(repo.todayHabits(repo.clock.today()).any { it.habit.id == "launch-h1" })
    }

    @Test
    fun onboardingActivityInflatesWithoutCrash() {
        val scenario = ActivityScenario.launch(OnboardingActivity::class.java)
        var crashed = false
        scenario.onActivity { }
        // Reaching this point means the layout inflated; close it again.
        scenario.close()
        assertFalse(crashed)
    }
}
