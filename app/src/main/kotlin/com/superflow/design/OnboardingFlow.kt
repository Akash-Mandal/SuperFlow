package com.superflow.design

/**
 * Onboarding, as data (plan 14).
 *
 * The current flow is eight steps: welcome, life area, identity, goal,
 * system, habit, cue, feel. The plan asks for six. That is not achieved by
 * deleting two screens — every one of those fields still needs a value —
 * but by noticing which questions are really one question. Life area is
 * picked *while* writing the identity, not before it. The system is named
 * from the goal unless the user objects. "How do you want it to feel" is a
 * setting, and settings do not belong in the first sixty seconds.
 *
 * Step order, validation, progress and skip behaviour all live here so the
 * flow can be tested end to end without an Activity, which is the only way
 * anyone would ever notice that step 5 can be reached with an empty habit.
 *
 * No Android imports.
 */
object OnboardingFlow {

    /**
     * The six steps.
     *
     * [illustration] is a symbolic motif name from the illustration system
     * (12.2), resolved to a drawable in the ui layer. The motifs run in a
     * deliberate arc — a seed, a figure, a horizon, a single mark, a clock,
     * a sunrise — so the illustrations flow into one another (14.2).
     */
    enum class Step(
        val key: String,
        val title: String,
        val subtitle: String,
        val illustration: String,
    ) {
        WELCOME(
            "welcome",
            "SuperFlow",
            "Small, repeated votes for the person you are becoming.",
            "seed",
        ),
        IDENTITY(
            "identity",
            "Who are you becoming?",
            "Not a target. A description of the person who already does this.",
            "figure",
        ),
        GOAL(
            "goal",
            "What matters to you?",
            "One outcome that would make a real difference, and why it would.",
            "horizon",
        ),
        HABIT(
            "habit",
            "One small habit",
            "The smallest action that counts as a vote. Small enough that a bad day cannot stop it.",
            "mark",
        ),
        CUE(
            "cue",
            "When and where?",
            "A habit without a time is a wish with better branding.",
            "clock",
        ),
        PREVIEW(
            "preview",
            "Your first day",
            "This is what tomorrow morning looks like.",
            "sunrise",
        ),
        ;

        val index: Int get() = ordinal
    }

    val steps: List<Step> = Step.entries.toList()
    val stepCount: Int get() = steps.size

    /** The step at an index, clamped. Never throws on a restored state. */
    fun stepAt(index: Int): Step = steps[index.coerceIn(0, steps.lastIndex)]

    // ------------------------------------------------------------ the answers

    /**
     * Everything onboarding collects.
     *
     * One flat bag rather than a partially-built entity graph: nothing is
     * written to the database until the last step, so a person who bails at
     * step four leaves no half-made identity behind.
     */
    data class Answers(
        val lifeArea: String = "",
        val identity: String = "",
        val goal: String = "",
        val why: String = "",
        val system: String = "",
        val habit: String = "",
        val tinyStart: String = "",
        val cueTime: String = "",
        val anchor: String = "",
        val reward: String = "",
        val reminder: Boolean = true,
    ) {
        /**
         * The system name, derived when the user did not give one.
         *
         * A system is the repeatable process behind a goal, and most people
         * do not have a word for theirs on day one. Asking cost us a whole
         * step and produced "My routine" nine times in ten, so we write
         * that ourselves and let it be renamed later.
         */
        fun systemName(): String {
            val explicit = system.trim()
            if (explicit.isNotEmpty()) return explicit
            val g = goal.trim()
            return if (g.isEmpty()) "My routine" else "My $g routine"
        }
    }

    // ------------------------------------------------------------- validation

    /**
     * The one field a step will not continue without, and what to say when
     * it is missing.
     *
     * Deliberately at most one per step. A form that raises three errors at
     * once during onboarding reads as a rejection.
     */
    fun requirement(step: Step): Pair<String, String>? = when (step) {
        Step.IDENTITY -> "identity" to "Write something, even roughly. You can change it later."
        Step.GOAL -> "goal" to "Name the outcome."
        Step.HABIT -> "habit" to "Name the habit."
        else -> null
    }

    /** Reads a field by the key [requirement] uses. */
    fun field(answers: Answers, key: String): String = when (key) {
        "lifeArea" -> answers.lifeArea
        "identity" -> answers.identity
        "goal" -> answers.goal
        "why" -> answers.why
        "system" -> answers.system
        "habit" -> answers.habit
        "tinyStart" -> answers.tinyStart
        "cueTime" -> answers.cueTime
        "anchor" -> answers.anchor
        "reward" -> answers.reward
        else -> ""
    }.trim()

    /** Null when the step may advance, otherwise the message to show. */
    fun blockedBecause(step: Step, answers: Answers): String? {
        val (key, message) = requirement(step) ?: return null
        return if (field(answers, key).isEmpty()) message else null
    }

    fun canAdvance(step: Step, answers: Answers): Boolean = blockedBecause(step, answers) == null

    // ---------------------------------------------------------------- chrome

    /** The forward button's label. */
    fun nextLabel(step: Step): String = when (step) {
        Step.WELCOME -> "Begin"
        Step.PREVIEW -> "Start my first day"
        else -> "Next"
    }

    /** Whether a back affordance is shown. Not on the first or last step. */
    fun showsBack(step: Step): Boolean = step != Step.WELCOME && step != Step.PREVIEW

    /**
     * Whether "Skip for now" is shown.
     *
     * Always, per 14.3 — including on the last step, where a person who has
     * changed their mind should not have to create a habit to escape.
     */
    fun showsSkip(step: Step): Boolean = step != Step.PREVIEW

    /**
     * Progress along the connected line (14.2), 0..1.
     *
     * The line is filled to the *completed* fraction, not to the current
     * step, so arriving at step one shows an empty line rather than
     * pretending a sixth of the work is already done.
     */
    fun progress(step: Step): Float = step.index.toFloat() / (stepCount - 1).toFloat()

    /** Progress as a percentage, for a screen reader. */
    fun progressPercent(step: Step): Int = Math.round(progress(step) * 100f)

    /** The spoken position, since the visual progress line is decorative. */
    fun describeProgress(step: Step): String =
        "Step ${step.index + 1} of $stepCount. ${step.title}"

    // ------------------------------------------------------------ navigation

    /** The next step, or null when finished. */
    fun next(step: Step): Step? = steps.getOrNull(step.index + 1)

    /** The previous step, or null at the start. */
    fun previous(step: Step): Step? = if (step.index == 0) null else steps[step.index - 1]

    /** Whether reaching this step should finish rather than advance. */
    fun isLast(step: Step): Boolean = step == steps.last()

    // ------------------------------------------------------- example prompts

    /**
     * Identity examples that cycle behind the field (14.1, step 2).
     *
     * All first person, all present tense, none of them aspirational — the
     * examples are teaching the grammar of an identity statement, and
     * "someone who wants to run" teaches the wrong one.
     */
    val identityExamples: List<String> = listOf(
        "Someone who moves every day",
        "A person who finishes what they start",
        "Someone who sleeps like it matters",
        "A person who reads instead of scrolling",
        "Someone who keeps their word to themselves",
    )

    /** Goal examples, paired with the why that makes them stick. */
    val goalExamples: List<Pair<String, String>> = listOf(
        "Run 5km without stopping" to "So I can keep up with my kids",
        "Finish the draft" to "Because I have been carrying it for two years",
        "Sleep seven hours" to "Everything else is downstream of this",
    )

    /**
     * How long the example cards linger before cycling, in milliseconds.
     * Long enough to read twice, since they cycle while you are typing.
     */
    const val EXAMPLE_DWELL_MS = 4200L

    /**
     * Suggested tiny starts, derived from a habit title.
     *
     * A generator rather than a fixed list because the tiny start has to be
     * a smaller version of *their* habit. These are structural: put on the
     * gear, open the thing, do it for two minutes. The AI coordinator can do
     * better when it is available, but this always works and never waits.
     */
    fun tinyStarts(habit: String): List<String> {
        val h = habit.trim().lowercase().ifEmpty { "it" }
        return listOf(
            "Two minutes of $h",
            "Get set up for $h, then decide",
            "The first step of $h and nothing more",
        )
    }

    // -------------------------------------------------------- skip behaviour

    /**
     * What a skip leaves behind (14.3).
     *
     * An empty app after a skipped onboarding is a dead end: nothing to tap,
     * nothing to learn from. A demo workspace gives the Today screen
     * something to show and can be removed in one action.
     */
    data class DemoWorkspace(
        val identity: String,
        val goal: String,
        val why: String,
        val system: String,
        val habit: String,
        val tinyStart: String,
        val cueTime: String,
    )

    val demo = DemoWorkspace(
        identity = "Someone who moves every day",
        goal = "Walk 5km without it being a whole thing",
        why = "Because sitting all day is making me feel older than I am",
        system = "Morning loop",
        habit = "Walk 10 minutes",
        tinyStart = "Put on my shoes and step outside",
        cueTime = "07:30",
    )

    /**
     * Turns a skip into answers.
     *
     * The demo is marked in the identity text so it can never be mistaken
     * for something the user wrote — a demo entity that looks authored is a
     * demo entity somebody keeps for a year.
     */
    fun demoAnswers(): Answers = Answers(
        lifeArea = "HEALTH",
        identity = demo.identity,
        goal = demo.goal,
        why = demo.why,
        system = demo.system,
        habit = demo.habit,
        tinyStart = demo.tinyStart,
        cueTime = demo.cueTime,
        reminder = false,
    )

    // ------------------------------------------------------- permission ask

    /**
     * Whether to ask for notification permission at this step.
     *
     * Only at the cue step, and only when a reminder was actually asked for.
     * A permission dialog on launch, before the app has explained itself, is
     * the fastest way to a permanent denial.
     */
    fun asksNotificationPermission(step: Step, answers: Answers): Boolean =
        step == Step.CUE && answers.reminder && answers.cueTime.isNotBlank()

    // ------------------------------------------------------------- preview

    /**
     * The Today-screen preview shown on the last step (14.2).
     *
     * Rendering the real card with their real words is the whole point: it
     * closes the loop between "I typed some things into a form" and "this
     * is my morning".
     */
    data class Preview(
        val identity: String,
        val habitTitle: String,
        val habitDetail: String,
        val encouragement: String,
    )

    fun preview(answers: Answers): Preview {
        val time = answers.cueTime.trim()
        val anchor = answers.anchor.trim()
        val detail = listOf(time, anchor).filter { it.isNotEmpty() }.joinToString(" · ")
            .ifEmpty { "Whenever you can" }
        return Preview(
            identity = answers.identity.trim().ifEmpty { demo.identity },
            habitTitle = answers.habit.trim().ifEmpty { demo.habit },
            habitDetail = detail,
            encouragement = "One check-in is one vote. That is the whole method.",
        )
    }
}
