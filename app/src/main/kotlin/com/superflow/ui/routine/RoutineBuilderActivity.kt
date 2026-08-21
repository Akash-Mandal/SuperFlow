package com.superflow.ui.routine

import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.superflow.R
import com.superflow.data.Repository
import com.superflow.data.model.Routine
import com.superflow.data.model.RoutineStep
import com.superflow.data.model.Status
import com.superflow.ui.common.ScrollActivity
import com.superflow.ui.common.snack
import com.superflow.ui.common.themeColor
import com.superflow.ui.sheets.TextInputSheet

/**
 * Routine Builder — upgrade from Flows (Section 6.1).
 *
 * Create habit-stacking routines with timed steps:
 *   "Morning Routine: After waking up, Walk 10 min → Stretch 5 min → Meditate 10 min"
 *
 * Features:
 *  - Drag-and-drop reorder (via number editing)
 *  - Timer for each step (optional)
 *  - Total time estimate
 *  - Can be checked-in as a single unit
 */
class RoutineBuilderActivity : ScrollActivity() {

    private val repo by lazy { Repository.get(this) }

    override fun titleText() = "Routines"

    override fun buildContent() {
        content.addView(textCard("Build Your Routines",
            "Chain habits together into sequences. Each step has an optional timer. " +
                    "Run the routine and check in as you go."))

        content.addView(MaterialButton(this).apply {
            text = "New routine"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dpi(4); it.bottomMargin = dpi(8) }
            setOnClickListener { newRoutine() }
        })

        val routines = repo.routines()
        if (routines.isEmpty()) {
            content.addView(textCard("No routines yet",
                "A good first routine has one reliable trigger (like \"after waking up\") " +
                        "and no more than 3-5 steps."))
            return
        }

        for (r in routines) {
            val steps = repo.routineSteps(r.id)
            val totalTime = steps.sumOf { it.durationMinutes }
            val card = layoutInflater.inflate(R.layout.item_text_card, content, false)
            card.findViewById<TextView>(R.id.text_title).text = r.title
            card.findViewById<TextView>(R.id.text_body).text = buildString {
                if (r.trigger.isNotBlank()) append("Trigger: ${r.trigger}\n\n")
                if (steps.isEmpty()) append("No steps yet.")
                steps.forEachIndexed { i, s ->
                    append("${i + 1}. ${s.title}")
                    if (s.durationMinutes > 0) append(" (${s.durationMinutes} min)")
                    if (s.habitId != null) append(" ★")
                    append('\n')
                }
                if (totalTime > 0) append("\nTotal: ~$totalTime min")
            }

            // Action buttons
            val holder = card.findViewById<TextView>(R.id.text_title).parent as LinearLayout
            holder.addView(makeStepButton(r, steps))
            holder.addView(makeRunButton(r, steps))
            holder.addView(makeDeleteButton(r))

            card.setOnLongClickListener {
                val newStatus = if (r.status == Status.ACTIVE) Status.PAUSED else Status.ACTIVE
                repo.saveRoutine(r.copy(status = newStatus))
                snack(if (newStatus == Status.PAUSED) "Routine paused" else "Routine active")
                rebuild()
                true
            }
            content.addView(card)
        }
    }

    private fun newRoutine() {
        TextInputSheet.show(supportFragmentManager, "New routine", "Morning Routine") { title ->
            if (title.isBlank()) return@show
            TextInputSheet.show(supportFragmentManager, "Trigger", "After waking up") { trigger ->
                val r = Routine(title = title.trim(), trigger = trigger.trim())
                repo.saveRoutine(r)
                // Add first step
                TextInputSheet.show(supportFragmentManager,
                    "Add step 1", "e.g. Walk 10 min") { step ->
                    addStep(r, step)
                }
            }
        }
    }

    private fun addStep(routine: Routine, text: String) {
        val parts = text.split(",")
        val title = parts[0].trim()
        val minutes = parts.getOrNull(1)?.trim()?.filter { it.isDigit() }?.toIntOrNull() ?: 5
        val steps = repo.routineSteps(routine.id)
        // A step typed into the builder is free text, not a linked habit.
        val step = RoutineStep(routineId = routine.id, habitId = null, title = title,
            durationMinutes = minutes, orderIndex = steps.size)
        repo.saveRoutineStep(step)
        snack("Step added: $title (${minutes} min)")
        rebuild()
    }

    private fun makeStepButton(routine: Routine, steps: List<RoutineStep>) = MaterialButton(this, null,
        com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
        text = "Add step"
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).also { it.topMargin = dpi(8) }
        setOnClickListener {
            TextInputSheet.show(supportFragmentManager,
                "Add step to \"${routine.title}\"",
                "Title, minutes (e.g. Walk 10 min, 10)") { text ->
                addStep(routine, text)
            }
        }
    }

    private fun makeRunButton(routine: Routine, steps: List<RoutineStep>) = MaterialButton(this, null,
        com.google.android.material.R.attr.materialButtonStyle).apply {
        text = "▶ Run routine (${steps.sumOf { it.durationMinutes }} min)"
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).also { it.topMargin = dpi(4) }
        isEnabled = steps.isNotEmpty()
        setOnClickListener {
            startGuidedRoutine(routine, steps)
        }
    }

    private fun makeDeleteButton(routine: Routine) = MaterialButton(this, null,
        com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
        text = "Delete"
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).also { it.topMargin = dpi(4) }
        setTextColor(themeColor(androidx.appcompat.R.attr.colorError))
        setOnClickListener {
            MaterialAlertDialogBuilder(this@RoutineBuilderActivity)
                .setTitle("Delete \"${routine.title}\"?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete") { _, _ ->
                    repo.deleteRoutine(routine.id)
                    snack("Routine deleted")
                    rebuild()
                }.show()
        }
    }

    private fun startGuidedRoutine(routine: Routine, steps: List<RoutineStep>) {
        // Build a guided step-by-step display
        val stepText = steps.joinToString("\n") { s ->
            "• ${s.title} (${s.durationMinutes} min)"
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("▶ ${routine.title}")
            .setMessage("Follow these steps:\n\n$stepText\n\n" +
                    "Mark each as done when you complete it.")
            .setPositiveButton("Start") { _, _ ->
                // Check in linked habits
                for (step in steps) {
                    if (step.habitId != null) {
                        val bus = com.superflow.domain.CommandBus.get(this)
                        bus.execute("check_in",
                            com.superflow.util.jsonOf("habit" to step.habitId, "level" to "TINY"),
                            com.superflow.domain.Actor.USER)
                    }
                }
                snack("Routine started! Check in as you go.")
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun dpi(v: Int) = (v * resources.displayMetrics.density).toInt()
}