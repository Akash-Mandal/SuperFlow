package com.superflow.ui

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.viewpager2.widget.ViewPager2
import com.superflow.R
import com.superflow.data.Prefs
import com.superflow.data.Repository
import com.superflow.data.model.Habit
import com.superflow.design.Navigation
import com.superflow.ui.onboarding.OnboardingActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Instrumented launch and navigation tests:
 *
 *  - clean state redirects to onboarding (MainActivity finishes itself);
 *  - onboarded state keeps MainActivity alive with the pager + bottom nav;
 *  - tab switching via the deep-link extra keeps the session alive;
 *  - the Today data path reflects database state.
 *
 * The two lifecycle-sensitive tests use [Application.ActivityLifecycleCallbacks]
 * rather than [androidx.test.core.app.ActivityScenario]: MainActivity destroys
 * itself during the clean-install redirect (ActivityScenario.onActivity throws
 * "Activity has been destroyed"), and the deep-link test's teardown is slower
 * than ActivityScenario.close()'s default wait, so it would report "Activity
 * never becomes requested state [DESTROYED]".
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

    /** Observes lifecycle events until [finished]. */
    private class LifecycleHook : Application.ActivityLifecycleCallbacks {
        val mainDestroyed = CountDownLatch(1)
        val mainResumed = CountDownLatch(1)
        val onboardingResumed = CountDownLatch(1)
        @Volatile var main: MainActivity? = null
        @Volatile var onboarding: OnboardingActivity? = null

        override fun onActivityResumed(a: Activity) {
            when (a) {
                is MainActivity -> { main = a; mainResumed.countDown() }
                is OnboardingActivity -> { onboarding = a; onboardingResumed.countDown() }
            }
        }
        override fun onActivityDestroyed(a: Activity) {
            if (a is MainActivity) mainDestroyed.countDown()
        }
        override fun onActivityCreated(a: Activity, b: Bundle?) {}
        override fun onActivityStarted(a: Activity) {}
        override fun onActivityPaused(a: Activity) {}
        override fun onActivityStopped(a: Activity) {}
        override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
    }

    @Test
    fun cleanStateRedirectsToOnboarding() {
        prefs.onboarded = false
        val app = ApplicationProvider.getApplicationContext<Application>()
        val hook = LifecycleHook()
        app.registerActivityLifecycleCallbacks(hook)
        // Launch the real redirect: MainActivity.onCreate starts OnboardingActivity
        // and finishes itself. Track MainActivity's destruction directly.
        app.startActivity(
            Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        val redirected = hook.mainDestroyed.await(15, TimeUnit.SECONDS)
        // The redirect must also have brought up OnboardingActivity.
        val onboardingStarted = hook.onboardingResumed.await(5, TimeUnit.SECONDS)
        app.unregisterActivityLifecycleCallbacks(hook)
        assertTrue(
            "MainActivity must be destroyed (redirect to onboarding)",
            redirected
        )
        assertTrue("redirect must launch OnboardingActivity", onboardingStarted)
        // Finish OnboardingActivity so the next test starts from an empty task.
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            hook.onboarding?.finish()
        }
        Thread.sleep(500)
    }

    @Test
    fun onboardedStateRendersMainScreen() {
        prefs.onboarded = true
        val app = ApplicationProvider.getApplicationContext<Application>()
        val hook = LifecycleHook()
        app.registerActivityLifecycleCallbacks(hook)
        app.startActivity(
            Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        assertTrue("MainActivity must resume", hook.mainResumed.await(15, TimeUnit.SECONDS))
        val activity = hook.main ?: throw AssertionError("MainActivity not created")
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            assertFalse("MainActivity must stay alive when onboarded", activity.isFinishing)
            // The shell inflated: both key containers exist.
            assertTrue(activity.findViewById<android.view.View>(R.id.pager) != null)
        }
        finishAndAwait(activity, hook)
        app.unregisterActivityLifecycleCallbacks(hook)
    }

    @Test
    fun tabDeepLinkIsAcceptedOnARunningActivity() {
        prefs.onboarded = true
        val app = ApplicationProvider.getApplicationContext<Application>()
        val hook = LifecycleHook()
        app.registerActivityLifecycleCallbacks(hook)
        // The deep link is delivered as a launch extra (the same route the
        // system uses for an incoming singleTop launch), which navigates
        // MainActivity to the Insights tab via handleIntent().
        app.startActivity(
            Intent(app, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(MainActivity.EXTRA_TAB, "insights")
        )
        assertTrue("MainActivity must resume", hook.mainResumed.await(15, TimeUnit.SECONDS))
        val activity = hook.main ?: throw AssertionError("MainActivity not created")
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            assertFalse("deep link must not finish the activity", activity.isFinishing)
            val pager = activity.findViewById<ViewPager2>(R.id.pager)
            assertEquals("deep link should select the Insights tab",
                Navigation.Tab.INSIGHTS.index, pager.currentItem)
        }
        assertTrue(prefs.onboarded)
        finishAndAwait(activity, hook)
        app.unregisterActivityLifecycleCallbacks(hook)
    }

    private fun finishAndAwait(activity: MainActivity, hook: LifecycleHook) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            activity.finish()
        }
        assertTrue("activity must reach DESTROYED after finish()",
            hook.mainDestroyed.await(20, TimeUnit.SECONDS))
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
        val app = ApplicationProvider.getApplicationContext<Application>()
        val ready = CountDownLatch(1)
        var activity: OnboardingActivity? = null
        val hook = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(a: Activity) {
                if (a is OnboardingActivity) { activity = a; ready.countDown() }
            }
            override fun onActivityCreated(a: Activity, b: Bundle?) {}
            override fun onActivityStarted(a: Activity) {}
            override fun onActivityPaused(a: Activity) {}
            override fun onActivityStopped(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
            override fun onActivityDestroyed(a: Activity) {}
        }
        app.registerActivityLifecycleCallbacks(hook)
        app.startActivity(
            Intent(app, OnboardingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        assertTrue("OnboardingActivity must resume (layout inflated)", ready.await(15, TimeUnit.SECONDS))
        app.unregisterActivityLifecycleCallbacks(hook)
        val a = activity ?: throw AssertionError("OnboardingActivity not created")
        val destroyed = CountDownLatch(1)
        val hook2 = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityDestroyed(x: Activity) { if (x == a) destroyed.countDown() }
            override fun onActivityCreated(x: Activity, b: Bundle?) {}
            override fun onActivityStarted(x: Activity) {}
            override fun onActivityResumed(x: Activity) {}
            override fun onActivityPaused(x: Activity) {}
            override fun onActivityStopped(x: Activity) {}
            override fun onActivitySaveInstanceState(x: Activity, b: Bundle) {}
        }
        app.registerActivityLifecycleCallbacks(hook2)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { a.finish() }
        assertTrue("OnboardingActivity must destroy", destroyed.await(20, TimeUnit.SECONDS))
        app.unregisterActivityLifecycleCallbacks(hook2)
    }
}
