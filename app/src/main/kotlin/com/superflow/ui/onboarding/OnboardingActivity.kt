package com.superflow.ui.onboarding

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.superflow.ai.Coordinator
import com.superflow.components.SfChip
import com.superflow.data.Prefs
import com.superflow.data.model.LifeArea
import com.superflow.design.OnboardingFlow
import com.superflow.design.Navigation
import com.superflow.domain.Actor
import com.superflow.domain.CommandBus
import com.superflow.notify.Reminders
import com.superflow.ui.MainActivity
import com.superflow.ui.screens.OnboardingAction
import com.superflow.ui.screens.OnboardingScreen
import com.superflow.ui.screens.OnboardingUiState
import com.superflow.ui.theme.SfThemeFromPrefs
import com.superflow.util.jsonOf
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Onboarding (§14): six illustrated steps, from aspiration to first action.
 *
 * The flow itself — order, validation, labels, progress, what a skip leaves
 * behind — is [OnboardingFlow], which is pure and tested. The screen is
 * [OnboardingScreen], which is Compose. This class owns only the three
 * things neither of those can: the example timer, the platform time picker,
 * and the write to the database at the end.
 *
 * Three rules survive from the old eight-step version because they were the
 * good parts:
 *
 * - **Nothing is written until the last step.** Someone who abandons at
 *   step four leaves no orphaned identity behind. The whole flow is one
 *   [OnboardingFlow.Answers] value until [complete].
 * - **Notification permission is asked at the cue step**, and only if a
 *   reminder was actually requested. Asking on launch, before the app has
 *   said what it is for, is the fastest route to a permanent denial.
 * - **Skip is always available and never punished.** It lands on Today with
 *   an empty workspace, which is a legitimate way to use this app.
 *
 * What changed: the life-area question no longer owns a screen. It was a
 * gate in front of a question that already implied the answer, so the chips
 * moved under the identity field where they help instead of blocking.
 */
class OnboardingActivity : AppCompatActivity() {

    private lateinit var bus: CommandBus
    private lateinit var prefs: Prefs

    private val state = MutableStateFlow(OnboardingUiState())
    private var exampleTimer: Job? = null

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        bus = CommandBus.get(this)
        prefs = Prefs.get(this)

        state.update {
            it.copy(
                step = OnboardingFlow.stepAt(savedInstanceState?.getInt(KEY_STEP) ?: 0),
                lifeAreas = lifeAreaChips(),
                widthClass = widthClass(),
            )
        }

        setContent {
            SfThemeFromPrefs {
                val ui by state.collectAsState()
                OnboardingScreen(state = ui, onAction = ::onAction)
            }
        }

        onBackPressedDispatcher.addCallback(this) {
            // Back walks the flow, not out of it. Leaving from step five
            // by reflex, losing five answers, is a worse outcome than
            // making someone press back twice.
            val previous = OnboardingFlow.previous(state.value.step)
            if (previous == null) finish() else goTo(previous)
        }

        startExampleTimer()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_STEP, state.value.step.index)
    }

    override fun onDestroy() {
        super.onDestroy()
        exampleTimer?.cancel()
    }

    /* ------------------------------------------------------------- actions */

    private fun onAction(action: OnboardingAction) {
        when (action) {
            OnboardingAction.Next -> advance()
            OnboardingAction.Back -> OnboardingFlow.previous(state.value.step)?.let(::goTo)
            OnboardingAction.Skip -> complete(skipped = true)
            is OnboardingAction.Edit -> edit(action.field, action.value)
            is OnboardingAction.Reminder -> state.update {
                it.copy(answers = it.answers.copy(reminder = action.enabled))
            }
            OnboardingAction.PickTime -> pickTime()
        }
    }

    private fun edit(field: String, value: String) = state.update { s ->
        val a = s.answers
        val next = when (field) {
            "lifeArea" -> a.copy(lifeArea = value)
            "identity" -> a.copy(identity = value)
            "goal" -> a.copy(goal = value)
            "why" -> a.copy(why = value)
            "system" -> a.copy(system = value)
            "habit" -> a.copy(habit = value)
            "tinyStart" -> a.copy(tinyStart = value)
            "cueTime" -> a.copy(cueTime = value)
            "anchor" -> a.copy(anchor = value)
            "reward" -> a.copy(reward = value)
            else -> a
        }
        // Editing clears the blocker: the message was about the field being
        // empty, and leaving it up while someone types reads as the app
        // still refusing them.
        s.copy(answers = next, blocker = null)
    }

    private fun advance() {
        val current = state.value
        val blocker = OnboardingFlow.blockedBecause(current.step, current.answers)
        if (blocker != null) {
            state.update { it.copy(blocker = blocker) }
            return
        }
        if (OnboardingFlow.asksNotificationPermission(current.step, current.answers)) {
            requestNotifications()
        }
        val next = OnboardingFlow.next(current.step)
        if (next == null) complete(skipped = false) else goTo(next)
    }

    private fun goTo(step: OnboardingFlow.Step) {
        state.update { it.copy(step = step, blocker = null, exampleIndex = 0) }
        startExampleTimer()
    }

    /**
     * Cycles the worked examples on the identity and goal steps.
     *
     * A static example gets read once and ignored; a rotating one shows the
     * *range* of what belongs in the field, which is the actual difficulty.
     * The timer only runs on the steps that have examples, so it is not
     * quietly waking the CPU on the welcome screen.
     */
    private fun startExampleTimer() {
        exampleTimer?.cancel()
        val step = state.value.step
        val cycles = step == OnboardingFlow.Step.IDENTITY || step == OnboardingFlow.Step.GOAL
        if (!cycles) return
        exampleTimer = lifecycleScope.launch {
            while (true) {
                delay(OnboardingFlow.EXAMPLE_DWELL_MS)
                state.update { it.copy(exampleIndex = it.exampleIndex + 1) }
            }
        }
    }

    private fun pickTime() {
        val existing = state.value.answers.cueTime.split(":")
        val hour = existing.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: DEFAULT_HOUR
        val minute = existing.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: DEFAULT_MINUTE
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(if (android.text.format.DateFormat.is24HourFormat(this))
                TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H)
            .setHour(hour)
            .setMinute(minute)
            .build()
        picker.addOnPositiveButtonClickListener {
            // Stored as 24-hour regardless of what the picker displayed:
            // the presentation format is a device setting, the stored value
            // is data, and mixing the two is how you get 7:30 PM parsed as
            // half past seven in the morning.
            edit("cueTime", String.format("%02d:%02d", picker.hour, picker.minute))
        }
        picker.show(supportFragmentManager, "cue")
    }

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val permission = Manifest.permission.POST_NOTIFICATIONS
        if (checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) return
        notificationPermission.launch(permission)
    }

    /* -------------------------------------------------------------- finish */

    /**
     * Writes the workspace and leaves.
     *
     * Everything goes through [CommandBus] as [Actor.USER] rather than
     * straight into the repository, so onboarding lands in Activity like
     * any other change and is undoable. Someone who regrets their first
     * identity statement at 9am on day one should be able to remove it the
     * same way they would remove anything else.
     */
    private fun complete(skipped: Boolean) {
        if (state.value.busy) return
        state.update { it.copy(busy = true) }

        if (!skipped) {
            val a = state.value.answers
            val identityId = bus.execute(
                "create_identity",
                jsonOf(
                    "statement" to a.identity.trim(),
                    "lifeArea" to (LifeArea.from(a.lifeArea).name),
                ),
                Actor.USER,
            ).data?.optString("id")

            val goalId = bus.execute(
                "create_goal",
                jsonOf(
                    "title" to a.goal.trim(),
                    "why" to a.why.trim(),
                    "identityId" to identityId,
                ),
                Actor.USER,
            ).data?.optString("id")

            val systemId = bus.execute(
                "create_system",
                jsonOf("title" to a.systemName(), "goalId" to goalId),
                Actor.USER,
            ).data?.optString("id")

            bus.execute(
                "create_habit",
                jsonOf(
                    "title" to a.habit.trim(),
                    "tinyStart" to a.tinyStart.trim()
                        .ifBlank { Coordinator.defaultTinyStart(a.habit.trim()) },
                    "standardVersion" to a.habit.trim(),
                    "cueTime" to a.cueTime,
                    "anchorText" to a.anchor.trim(),
                    "reward" to a.reward.trim(),
                    "systemId" to systemId,
                    "identityId" to identityId,
                    "reminder" to a.reminder,
                    "days" to "daily",
                ),
                Actor.USER,
            )
        }

        prefs.onboarded = true
        Reminders.rescheduleAll(this)
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    /* --------------------------------------------------------------- setup */

    /**
     * Life-area chips, in the order the enum declares them minus CUSTOM.
     *
     * "Custom" as a chip is a dead end during onboarding — it names no
     * area and offers no field to name one — so it is left to the designer
     * screens where an area can actually be typed.
     */
    private fun lifeAreaChips(): List<SfChip> =
        LifeArea.entries
            .filter { it != LifeArea.CUSTOM }
            .map { SfChip(id = it.name, label = it.label) }

    private fun widthClass(): Navigation.WidthClass {
        val metrics = resources.displayMetrics
        return Navigation.widthClass((metrics.widthPixels / metrics.density).toInt())
    }

    private companion object {
        const val KEY_STEP = "step"
        const val DEFAULT_HOUR = 7
        const val DEFAULT_MINUTE = 30
    }
}
