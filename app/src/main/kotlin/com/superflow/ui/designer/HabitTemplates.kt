package com.superflow.ui.designer

import com.superflow.core.schedule.Recurrence
import com.superflow.data.model.HabitMode
import com.superflow.data.model.TrackType

/**
 * Starter habit designs shown as the first choice in the Habit Designer.
 *
 * A template pre-fills the Four Laws fields so a beginner does not face a
 * blank form; they can still edit every value and start from scratch by
 * choosing "Blank". Templates deliberately set a tiny two-minute start.
 */
object HabitTemplates {

    data class Template(
        val name: String,
        val title: String,
        val tinyStart: String,
        val minimumVersion: String,
        val standardVersion: String,
        val stretchVersion: String,
        val cueTime: String = "",
        val cuePlace: String = "",
        val anchorText: String = "",
        val benefit: String = "",
        val temptationBundle: String = "",
        val reframe: String = "",
        val frictionPlan: String = "",
        val environmentPrep: String = "",
        val reward: String = "",
        val recoveryPlan: String = "Return with the tiny version. Never miss twice.",
        val recurrence: Recurrence = Recurrence.EVERY_DAY,
        val mode: HabitMode = HabitMode.BUILD,
        val trackType: TrackType = TrackType.BINARY,
        val targetCount: Int = 1,
        val unit: String = "",
        val reminder: Boolean = false,
        val protectedRoutine: Boolean = false
    )

    val all: List<Template> = listOf(
        Template(
            name = "Morning walk",
            title = "Morning walk",
            tinyStart = "Put on my walking shoes",
            minimumVersion = "Walk to the corner",
            standardVersion = "Walk for 10 minutes",
            stretchVersion = "Walk for 25 minutes",
            cueTime = "07:30",
            cuePlace = "outside",
            anchorText = "breakfast",
            benefit = "I feel awake and clear for the day",
            temptationBundle = "Only listen to my favourite podcast while walking",
            reframe = "I get to move, not I have to exercise",
            frictionPlan = "Keep shoes by the door",
            environmentPrep = "Lay out clothes the night before",
            reward = "Enjoy my coffee outside",
            recurrence = Recurrence.WEEKDAYS
        ),
        Template(
            name = "Read",
            title = "Read",
            tinyStart = "Open the book and read one page",
            minimumVersion = "Read for 5 minutes",
            standardVersion = "Read for 20 minutes",
            stretchVersion = "Read for 45 minutes",
            cueTime = "21:00",
            anchorText = "getting into bed",
            benefit = "I learn something and wind down",
            reframe = "One page counts; momentum does the rest",
            frictionPlan = "Keep the book on my pillow",
            environmentPrep = "Charge my phone across the room",
            reward = "Mark the day as a reading day"
        ),
        Template(
            name = "Meditate",
            title = "Meditate",
            tinyStart = "Sit and take three breaths",
            minimumVersion = "Meditate for 3 minutes",
            standardVersion = "Meditate for 10 minutes",
            stretchVersion = "Meditate for 20 minutes",
            cueTime = "07:00",
            cuePlace = "the cushion",
            anchorText = "brushing my teeth in the morning",
            benefit = "I respond instead of reacting",
            reframe = "A wandering mind is the practice, not a failure",
            frictionPlan = "Leave the cushion set up",
            environmentPrep = "Put my phone on do-not-disturb",
            reward = "Ring the bell when I finish",
            protectedRoutine = true
        ),
        Template(
            name = "Journal",
            title = "Journal",
            tinyStart = "Write one sentence",
            minimumVersion = "Three bullet points",
            standardVersion = "Journal for 10 minutes",
            stretchVersion = "Journal for 20 minutes",
            cueTime = "20:30",
            anchorText = "dinner",
            benefit = "I make sense of the day",
            frictionPlan = "Keep the notebook open on the desk",
            environmentPrep = "Put the pen on top",
            reward = "Tick it off and close the notebook"
        ),
        Template(
            name = "Workout",
            title = "Work out",
            tinyStart = "Do five push-ups",
            minimumVersion = "A 10-minute home workout",
            standardVersion = "Train for 30 minutes",
            stretchVersion = "Full 45-minute session",
            cueTime = "18:00",
            cuePlace = "the gym",
            anchorText = "leaving work",
            benefit = "I feel strong and energised",
            temptationBundle = "Only listen to that workout playlist at the gym",
            frictionPlan = "Pack the gym bag the night before",
            environmentPrep = "Leave the bag by the door",
            reward = "A long shower",
            recurrence = Recurrence.parse("mon,wed,fri"),
            protectedRoutine = true
        ),
        Template(
            name = "Drink water",
            title = "Drink water",
            tinyStart = "One glass",
            minimumVersion = "Fill the bottle once",
            standardVersion = "Drink 2 litres",
            stretchVersion = "Drink 3 litres",
            cueTime = "09:00",
            anchorText = "making coffee",
            benefit = "I have more energy and fewer headaches",
            frictionPlan = "Keep a full bottle on the desk",
            reward = "Mark each refill",
            trackType = TrackType.COUNT,
            targetCount = 8,
            unit = "glasses"
        ),
        Template(
            name = "No snooze",
            title = "Get up when the alarm rings",
            tinyStart = "Sit up when it rings",
            minimumVersion = "No snooze once this week",
            standardVersion = "No snooze every weekday",
            stretchVersion = "No snooze every day",
            cueTime = "06:45",
            anchorText = "the alarm",
            benefit = "I start the day on my terms",
            reframe = "Five more minutes never actually helps",
            frictionPlan = "Put the alarm across the room",
            environmentPrep = "Set out gym clothes",
            reward = "A slow coffee",
            recurrence = Recurrence.WEEKDAYS
        ),
        Template(
            name = "Tidy for 10 minutes",
            title = "Tidy for 10 minutes",
            tinyStart = "Put one thing away",
            minimumVersion = "Clear one surface",
            standardVersion = "Tidy for 10 minutes",
            stretchVersion = "Tidy for 20 minutes",
            cueTime = "19:00",
            anchorText = "after dinner",
            benefit = "A calm space makes a calm mind",
            frictionPlan = "Keep a basket for stray things",
            reward = "Sit down to a tidy room"
        )
    )

    val blank: Template? = null
}
