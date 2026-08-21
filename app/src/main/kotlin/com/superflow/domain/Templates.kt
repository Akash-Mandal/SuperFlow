package com.superflow.domain

import com.superflow.data.model.LifeArea

/**
 * The habit templates library.
 *
 * Pre-built, research-backed starting points organised by life area. Each
 * template carries the same fields the Habit Designer collects, so applying
 * one is a head start, not a finished product — the user still reads and owns
 * the contract.
 */
data class HabitTemplate(
    val id: String,          // stable slug, used by apply_template
    val title: String,
    val area: LifeArea,
    val benefit: String = "",
    val cueTime: String = "",
    val cuePlace: String = "",
    val anchorText: String = "",
    val tinyStart: String = "",
    val minimumVersion: String = "",
    val standardVersion: String = "",
    val stretchVersion: String = "",
    val frictionPlan: String = "",
    val environmentPrep: String = "",
    val reward: String = "",
    val recoveryPlan: String = "Return tomorrow with the tiny version. Never miss twice.",
    val difficulty: Int = 3,          // 1 (easiest) .. 5 (hardest)
    val estimatedMinutes: Int = 10,
    val schedule: String = "daily",   // parseable by Recurrence.parse
    val obstacles: List<Pair<String, String>> = emptyList()
)

object Templates {

    fun all(): List<HabitTemplate> = HEALTH + LEARNING + RELATIONSHIPS + WORK +
            CREATIVITY + FINANCE + MINDFULNESS + HOME

    fun byArea(area: LifeArea): List<HabitTemplate> =
        all().filter { it.area == area }

    fun areas(): List<LifeArea> = LifeArea.values().filter { it != LifeArea.CUSTOM }

    fun find(idOrTitle: String): HabitTemplate? {
        val q = idOrTitle.trim()
        if (q.isBlank()) return null
        return all().firstOrNull { it.id.equals(q, true) }
            ?: all().firstOrNull { it.title.equals(q, true) }
            ?: all().firstOrNull { it.title.lowercase().contains(q.lowercase()) }
    }

    /* ------------------------------------------------------------- health */

    private val HEALTH = listOf(
        template("morning_walk", "Morning walk", LifeArea.HEALTH,
            benefit = "I feel awake and clear",
            cueTime = "07:30", cuePlace = "around the block",
            tinyStart = "Put on my shoes", minimumVersion = "Walk to the corner",
            standardVersion = "Walk for 10 minutes", stretchVersion = "Walk for 25 minutes",
            frictionPlan = "Leave my shoes by the door", environmentPrep = "Lay out clothes tonight",
            reward = "Enjoy my coffee afterwards", estimatedMinutes = 10,
            obstacles = listOf("It is raining" to "Walk the stairs inside for five minutes",
                "I overslept" to "Do a one-minute loop instead of skipping")),
        template("drink_water", "Drink water", LifeArea.HEALTH,
            benefit = "More energy, fewer headaches",
            cuePlace = "at my desk", anchorText = "each meal",
            tinyStart = "One glass", minimumVersion = "Three glasses",
            standardVersion = "Eight glasses", stretchVersion = "Two litres",
            frictionPlan = "Keep a filled bottle on the desk", environmentPrep = "Fill it the night before",
            reward = "Check it off", difficulty = 2, estimatedMinutes = 1,
            obstacles = listOf("I forget" to "Set the bottle where I always look")),
        template("stretch_daily", "Stretch", LifeArea.HEALTH,
            benefit = "Less stiffness and tension",
            cueTime = "20:00", cuePlace = "in the living room",
            tinyStart = "One stretch", minimumVersion = "Five minutes",
            standardVersion = "Ten minutes", stretchVersion = "Fifteen minutes plus foam rolling",
            frictionPlan = "Keep the mat unrolled", environmentPrep = "Clear a floor spot",
            reward = "Wind-down tea", difficulty = 2, estimatedMinutes = 10),
        template("sleep_by_10", "Sleep by 10pm", LifeArea.HEALTH,
            benefit = "Waking up without the fog",
            cueTime = "21:30",
            tinyStart = "Screens off at 21:45", minimumVersion = "In bed by 22:15",
            standardVersion = "Asleep by 22:00", stretchVersion = "Asleep by 21:30",
            frictionPlan = "Charge my phone outside the bedroom",
            environmentPrep = "Dim the lights at 21:00",
            reward = "A calm, dark room", difficulty = 4, estimatedMinutes = 30,
            obstacles = listOf("I get sucked into a show" to "Start the wind-down before the show")),
        template("take_vitamins", "Take vitamins", LifeArea.HEALTH,
            benefit = "Filling the gaps in my diet",
            anchorText = "breakfast",
            tinyStart = "One vitamin", minimumVersion = "The daily multivitamin",
            standardVersion = "Full stack", stretchVersion = "Plus a fish-oil capsule",
            frictionPlan = "Keep them beside my breakfast bowl",
            environmentPrep = "Refill the weekly box on Sunday",
            reward = "Check it off", difficulty = 1, estimatedMinutes = 1),
        template("ten_k_steps", "10,000 steps", LifeArea.HEALTH,
            benefit = "Steadier energy all day",
            cueTime = "12:30",
            tinyStart = "A five-minute walk", minimumVersion = "6,000 steps",
            standardVersion = "10,000 steps", stretchVersion = "12,000 steps",
            frictionPlan = "Park further away and take the stairs",
            environmentPrep = "Charge the tracker overnight",
            reward = "A podcast while I walk", difficulty = 3, estimatedMinutes = 60,
            obstacles = listOf("A desk-bound day" to "Walk during calls and after lunch"))
    )

    /* ------------------------------------------------------------ learning */

    private val LEARNING = listOf(
        template("read_20", "Read 20 minutes", LifeArea.LEARNING,
            benefit = "I am becoming someone who reads",
            cueTime = "21:00", cuePlace = "in my chair",
            tinyStart = "Read one page", minimumVersion = "Read five minutes",
            standardVersion = "Read for 20 minutes", stretchVersion = "Read for 45 minutes",
            frictionPlan = "Leave the book on the pillow", environmentPrep = "Put the phone elsewhere",
            reward = "Highlight one idea", estimatedMinutes = 20,
            obstacles = listOf("Too tired" to "Read one page only — it still counts")),
        template("language_practice", "Language practice", LifeArea.LEARNING,
            benefit = "Progress without cramming",
            anchorText = "breakfast",
            tinyStart = "Five new words", minimumVersion = "Ten minutes of practice",
            standardVersion = "Twenty minutes of practice", stretchVersion = "A conversation lesson",
            frictionPlan = "Keep the app on my home screen",
            environmentPrep = "Queue tomorrow's lesson tonight",
            reward = "A streak badge", difficulty = 3, estimatedMinutes = 20),
        template("online_course", "Online course", LifeArea.LEARNING,
            benefit = "A real skill, one lesson at a time",
            cueTime = "19:00",
            tinyStart = "Open the course", minimumVersion = "One short lesson",
            standardVersion = "One lesson plus notes", stretchVersion = "Two lessons",
            frictionPlan = "Log in and park on the next lesson",
            environmentPrep = "Close other tabs before starting",
            reward = "Close the laptop with intent", difficulty = 3, estimatedMinutes = 30),
        template("take_notes", "Take notes", LifeArea.LEARNING,
            benefit = "Remembering more of what I read",
            anchorText = "reading",
            tinyStart = "One line", minimumVersion = "Three bullet points",
            standardVersion = "A short summary", stretchVersion = "A summary plus one action",
            frictionPlan = "Keep a notebook beside the book", environmentPrep = "Date the next page",
            reward = "Reviewing the shelf of notes", difficulty = 2, estimatedMinutes = 5),
        template("review_flashcards", "Review flashcards", LifeArea.LEARNING,
            benefit = "Spaced repetition that sticks",
            cueTime = "18:30",
            tinyStart = "Five cards", minimumVersion = "The daily due cards",
            standardVersion = "Due cards plus ten new", stretchVersion = "A full review session",
            frictionPlan = "Keep the deck on the home screen",
            environmentPrep = "Prepare the deck the night before",
            reward = "Watch the review count drop", difficulty = 2, estimatedMinutes = 10),
        template("journal_learn", "Learning journal", LifeArea.LEARNING,
            benefit = "Turning experience into insight",
            cueTime = "21:30",
            tinyStart = "One sentence", minimumVersion = "Three lines",
            standardVersion = "One paragraph", stretchVersion = "A page",
            frictionPlan = "Leave the journal open on the desk",
            environmentPrep = "Keep a pen beside it",
            reward = "A calmer mind", difficulty = 2, estimatedMinutes = 10)
    )

    /* -------------------------------------------------------- relationships */

    private val RELATIONSHIPS = listOf(
        template("call_a_friend", "Call a friend", LifeArea.RELATIONSHIPS,
            benefit = "Connection is a need, not a luxury",
            anchorText = "the evening walk", schedule = "weekly",
            tinyStart = "Send one text", minimumVersion = "A five-minute call",
            standardVersion = "A real conversation", stretchVersion = "A long catch-up",
            frictionPlan = "Keep their number one tap away",
            environmentPrep = "Pick who to call this week",
            reward = "Feeling closer", difficulty = 2, estimatedMinutes = 15,
            obstacles = listOf("It feels awkward" to "Start with a text, then call")),
        template("family_dinner", "Family dinner", LifeArea.RELATIONSHIPS,
            benefit = "A shared table is a shared life",
            cueTime = "18:30", schedule = "weekdays",
            tinyStart = "Eat together once this week", minimumVersion = "Three dinners together",
            standardVersion = "Dinner together most nights", stretchVersion = "Dinner plus a game",
            frictionPlan = "Agree the time in advance", environmentPrep = "Prep while the kettle boils",
            reward = "Stories from everyone's day", difficulty = 3, estimatedMinutes = 45),
        template("express_gratitude", "Express gratitude", LifeArea.RELATIONSHIPS,
            benefit = "People feel seen",
            cueTime = "20:00",
            tinyStart = "One thank-you text", minimumVersion = "One specific thank-you",
            standardVersion = "Thank someone for something specific", stretchVersion = "A short written note",
            frictionPlan = "Keep a list of people to thank", environmentPrep = "Note what I noticed today",
            reward = "Their reply", difficulty = 1, estimatedMinutes = 5),
        template("active_listening", "Active listening", LifeArea.RELATIONSHIPS,
            benefit = "People remember how I made them feel",
            anchorText = "every conversation",
            tinyStart = "Put the phone face-down", minimumVersion = "No interrupting",
            standardVersion = "Reflect back what I heard", stretchVersion = "Ask one deeper question",
            frictionPlan = "Phone goes in the pocket", environmentPrep = "Choose the conversation",
            reward = "A deeper connection", difficulty = 3, estimatedMinutes = 15),
        template("no_phone_meals", "No phone during meals", LifeArea.RELATIONSHIPS,
            benefit = "Undivided attention",
            anchorText = "every meal",
            tinyStart = "One phone-free meal a week", minimumVersion = "Phone-free dinners",
            standardVersion = "All shared meals phone-free", stretchVersion = "Meals plus screen-free mornings",
            frictionPlan = "Charge the phone in another room",
            environmentPrep = "Set a bowl for phones by the door",
            reward = "A calmer table", difficulty = 3, estimatedMinutes = 30),
        template("check_in_partner", "Check in with my partner", LifeArea.RELATIONSHIPS,
            benefit = "Small signals prevent big drift",
            cueTime = "21:00",
            tinyStart = "One real question", minimumVersion = "Five undistracted minutes",
            standardVersion = "A real how-was-your-day", stretchVersion = "A weekly deeper check-in",
            frictionPlan = "Ask before we both get tired",
            environmentPrep = "Screens down by 20:45",
            reward = "Feeling known", difficulty = 2, estimatedMinutes = 10)
    )

    /* ---------------------------------------------------------------- work */

    private val WORK = listOf(
        template("plan_tomorrow_tonight", "Plan tomorrow tonight", LifeArea.WORK,
            benefit = "Starting the day already decided",
            cueTime = "17:30", schedule = "weekdays",
            tinyStart = "List one task", minimumVersion = "Pick the top three",
            standardVersion = "Schedule the top three", stretchVersion = "Time-block the whole morning",
            frictionPlan = "Keep the planner open on the desk",
            environmentPrep = "Close the day with a shutdown ritual",
            reward = "A clear head", difficulty = 2, estimatedMinutes = 10,
            obstacles = listOf("Too tired" to "Just write tomorrow's first task")),
        template("deep_work_block", "Deep work block", LifeArea.WORK,
            benefit = "The hard thing done before noon",
            cueTime = "09:00", schedule = "weekdays",
            tinyStart = "Ten focused minutes", minimumVersion = "One 25-minute block",
            standardVersion = "One 90-minute block", stretchVersion = "Two deep blocks",
            frictionPlan = "Phone in another room, notifications off",
            environmentPrep = "Choose the task the night before",
            reward = "Lunch guilt-free", difficulty = 4, estimatedMinutes = 90,
            obstacles = listOf("Interruptions" to "Block the calendar and close the door")),
        template("inbox_zero", "Inbox zero", LifeArea.WORK,
            benefit = "Email stops owning my attention",
            cueTime = "16:00", schedule = "weekdays",
            tinyStart = "Clear five emails", minimumVersion = "Process the new mail",
            standardVersion = "Reach inbox zero", stretchVersion = "Zero plus a weekly review of folders",
            frictionPlan = "Turn off push notifications",
            environmentPrep = "Batch email into one window",
            reward = "Close the tab", difficulty = 3, estimatedMinutes = 20),
        template("standup_prep", "Stand-up prep", LifeArea.WORK,
            benefit = "Walking in with a clear update",
            cueTime = "08:45", schedule = "weekdays",
            tinyStart = "Three bullet points", minimumVersion = "Yesterday, today, blockers",
            standardVersion = "A crisp written update", stretchVersion = "Plus one question for the team",
            frictionPlan = "Template pinned in notes", environmentPrep = "Skim yesterday's update",
            reward = "A confident start", difficulty = 2, estimatedMinutes = 5),
        template("end_of_day_shutdown", "End-of-day shutdown", LifeArea.WORK,
            benefit = "The day actually ends",
            cueTime = "17:45", schedule = "weekdays",
            tinyStart = "Close the tabs", minimumVersion = "Write tomorrow's first task",
            standardVersion = "Shutdown: close, review, plan", stretchVersion = "Full weekly review",
            frictionPlan = "A literal shutdown checklist", environmentPrep = "Keep the checklist visible",
            reward = "Leave on time", difficulty = 2, estimatedMinutes = 10),
        template("skill_hour", "Skill hour", LifeArea.WORK,
            benefit = "Compound growth in my craft",
            cueTime = "11:00", schedule = "weekdays",
            tinyStart = "Ten minutes", minimumVersion = "One focused session",
            standardVersion = "One hour of deliberate practice", stretchVersion = "Practice plus a write-up",
            frictionPlan = "Calendar it as a meeting",
            environmentPrep = "Gather materials beforehand",
            reward = "Tracked progress", difficulty = 4, estimatedMinutes = 60)
    )

    /* ---------------------------------------------------------- creativity */

    private val CREATIVITY = listOf(
        template("write_500", "Write 500 words", LifeArea.CREATIVITY,
            benefit = "Showing up beats waiting for inspiration",
            cueTime = "06:30",
            tinyStart = "Write one sentence", minimumVersion = "Write 100 words",
            standardVersion = "Write 500 words", stretchVersion = "Write 1,000 words",
            frictionPlan = "Open the draft before bed",
            environmentPrep = "Clear the desk the night before",
            reward = "Watch the word count grow", difficulty = 3, estimatedMinutes = 25,
            obstacles = listOf("Blank page" to "Start with a bad sentence on purpose")),
        template("sketch_15", "Sketch for 15 minutes", LifeArea.CREATIVITY,
            benefit = "Drawing is seeing",
            cueTime = "19:00",
            tinyStart = "One line drawing", minimumVersion = "A quick sketch",
            standardVersion = "Fifteen minutes of sketching", stretchVersion = "A finished study",
            frictionPlan = "Keep the sketchbook out", environmentPrep = "Pencil sharpened",
            reward = "Date the page", difficulty = 2, estimatedMinutes = 15),
        template("practice_instrument", "Practice instrument", LifeArea.CREATIVITY,
            benefit = "Small sessions beat long rare ones",
            cueTime = "18:00",
            tinyStart = "Pick it up", minimumVersion = "Ten minutes",
            standardVersion = "Twenty minutes", stretchVersion = "A full practice session",
            frictionPlan = "Leave it out of the case",
            environmentPrep = "Tune it the night before",
            reward = "A smoother run-through", difficulty = 3, estimatedMinutes = 20),
        template("brainstorm_ideas", "Brainstorm ideas", LifeArea.CREATIVITY,
            benefit = "Quantity first, quality later",
            cueTime = "12:00", schedule = "weekdays",
            tinyStart = "One idea", minimumVersion = "Five ideas",
            standardVersion = "Ten ideas", stretchVersion = "Twenty ideas, then pick one",
            frictionPlan = "A running ideas note", environmentPrep = "Date the next page",
            reward = "The best idea circled", difficulty = 2, estimatedMinutes = 10),
        template("create_something", "Create something", LifeArea.CREATIVITY,
            benefit = "Making things is the point",
            cueTime = "10:00", schedule = "weekends",
            tinyStart = "Gather materials", minimumVersion = "Fifteen minutes of making",
            standardVersion = "One finished piece", stretchVersion = "A piece I am proud of",
            frictionPlan = "Pre-stage the materials",
            environmentPrep = "Clear a work surface",
            reward = "Photograph the result", difficulty = 3, estimatedMinutes = 45)
    )

    /* ------------------------------------------------------------- finance */

    private val FINANCE = listOf(
        template("track_spending", "Track spending", LifeArea.FINANCE,
            benefit = "Awareness before budgets",
            cueTime = "20:00",
            tinyStart = "Log one purchase", minimumVersion = "Log today's spending",
            standardVersion = "Log every purchase", stretchVersion = "Log and categorise weekly",
            frictionPlan = "App on the home screen",
            environmentPrep = "Notifications for receipts",
            reward = "A clean weekly total", difficulty = 2, estimatedMinutes = 5,
            obstacles = listOf("Small purchases feel minor" to "Log them anyway — they add up")),
        template("no_spend_day", "No-spend day", LifeArea.FINANCE,
            benefit = "Breaking the tap-to-buy reflex",
            schedule = "weekly",
            tinyStart = "One no-spend morning", minimumVersion = "One no-spend day",
            standardVersion = "A no-spend day each week", stretchVersion = "A no-spend weekend",
            frictionPlan = "Delete saved cards from quick checkout",
            environmentPrep = "Plan meals and errands in advance",
            reward = "The money stays put", difficulty = 3, estimatedMinutes = 0),
        template("review_budget", "Review budget", LifeArea.FINANCE,
            benefit = "Deciding instead of drifting",
            cueTime = "10:00", schedule = "weekly",
            tinyStart = "Open the app", minimumVersion = "Check the balances",
            standardVersion = "Review the week's spending", stretchVersion = "Set next week's plan",
            frictionPlan = "A recurring calendar slot", environmentPrep = "Gather statements",
            reward = "A number I trust", difficulty = 2, estimatedMinutes = 15),
        template("save_auto_transfer", "Automatic savings transfer", LifeArea.FINANCE,
            benefit = "Pay myself first",
            schedule = "weekly",
            tinyStart = "Set up the transfer", minimumVersion = "One small automatic transfer",
            standardVersion = "The planned amount, automatically", stretchVersion = "Raise it when income rises",
            frictionPlan = "Automate so it is not a decision",
            environmentPrep = "Choose the account once",
            reward = "Watch it compound", difficulty = 1, estimatedMinutes = 5),
        template("invest_research", "Investment research", LifeArea.FINANCE,
            benefit = "Understanding before buying",
            cueTime = "09:00", schedule = "weekends",
            tinyStart = "Read one page", minimumVersion = "One article",
            standardVersion = "Thirty minutes of research", stretchVersion = "Write up a thesis",
            frictionPlan = "Keep a research note",
            environmentPrep = "Queue one article",
            reward = "A written note", difficulty = 3, estimatedMinutes = 30)
    )

    /* --------------------------------------------------------- mindfulness */

    private val MINDFULNESS = listOf(
        template("meditate_10", "Meditate 10 minutes", LifeArea.MINDFULNESS,
            benefit = "A quieter, less reactive mind",
            cueTime = "07:00", cuePlace = "on the cushion",
            tinyStart = "Three slow breaths", minimumVersion = "Five minutes",
            standardVersion = "Ten minutes", stretchVersion = "Twenty minutes",
            frictionPlan = "Leave the cushion out",
            environmentPrep = "Set the timer the night before",
            reward = "Starting the day on my terms", difficulty = 2, estimatedMinutes = 10,
            obstacles = listOf("Restless mind" to "The noticing is the practice")),
        template("gratitude_journal", "Gratitude journal", LifeArea.MINDFULNESS,
            benefit = "Noticing what is already good",
            cueTime = "21:00",
            tinyStart = "One thing", minimumVersion = "Three things",
            standardVersion = "Three things plus why", stretchVersion = "A full gratitude page",
            frictionPlan = "Journal and pen on the pillow",
            environmentPrep = "Open to today's page",
            reward = "Sleeping a little lighter", difficulty = 1, estimatedMinutes = 5),
        template("body_scan", "Body scan", LifeArea.MINDFULNESS,
            benefit = "Dropping tension I did not know I held",
            cueTime = "21:30",
            tinyStart = "Notice my shoulders", minimumVersion = "A three-minute scan",
            standardVersion = "A ten-minute scan", stretchVersion = "A full guided scan",
            frictionPlan = "A saved guided track", environmentPrep = "Lights low, phone away",
            reward = "A relaxed jaw and shoulders", difficulty = 2, estimatedMinutes = 10),
        template("digital_sunset", "Digital sunset", LifeArea.MINDFULNESS,
            benefit = "Sleep and presence after dark",
            cueTime = "21:00",
            tinyStart = "Ten minutes screen-free", minimumVersion = "Screens off by 21:30",
            standardVersion = "No screens after 21:00", stretchVersion = "A whole screen-free evening",
            frictionPlan = "Charge devices in another room",
            environmentPrep = "A book and lamp ready",
            reward = "Waking up clearer", difficulty = 3, estimatedMinutes = 60,
            obstacles = listOf("The scroll wins" to "Turn the phone off, not just silent")),
        template("breathing_exercise", "Breathing exercise", LifeArea.MINDFULNESS,
            benefit = "A reset button for stress",
            anchorText = "any tense moment",
            tinyStart = "One deep breath", minimumVersion = "Ten slow breaths",
            standardVersion = "A two-minute box-breathing", stretchVersion = "Five minutes of practice",
            frictionPlan = "A reminder on the watch", environmentPrep = "Remember the pattern: 4-4-4-4",
            reward = "A slower heartbeat", difficulty = 1, estimatedMinutes = 2)
    )

    /* ---------------------------------------------------------------- home */

    private val HOME = listOf(
        template("make_bed", "Make the bed", LifeArea.HOME,
            benefit = "One win before breakfast",
            anchorText = "getting up",
            tinyStart = "Straighten the duvet", minimumVersion = "Pull up the covers",
            standardVersion = "Make the bed properly", stretchVersion = "Bed plus tidy the bedside",
            frictionPlan = "Nothing stacked on the bed",
            environmentPrep = "Choose easy bedding",
            reward = "A calm room to return to", difficulty = 1, estimatedMinutes = 2),
        template("tidy_10", "Tidy for 10 minutes", LifeArea.HOME,
            benefit = "A home that resets itself",
            cueTime = "19:30",
            tinyStart = "Clear one surface", minimumVersion = "Five minutes",
            standardVersion = "Ten minutes", stretchVersion = "Ten minutes plus a weekly reset",
            frictionPlan = "A timer on the fridge", environmentPrep = "A box for things out of place",
            reward = "A clear room", difficulty = 1, estimatedMinutes = 10),
        template("meal_prep", "Meal prep", LifeArea.HOME,
            benefit = "Deciding dinner once, not every night",
            cueTime = "16:00", schedule = "weekends",
            tinyStart = "Plan three meals", minimumVersion = "Prep one meal",
            standardVersion = "Prep the week's lunches", stretchVersion = "Full weekly prep",
            frictionPlan = "A standing grocery list",
            environmentPrep = "Clear the counters first",
            reward = "Easy weeknights", difficulty = 3, estimatedMinutes = 60),
        template("laundry", "Laundry routine", LifeArea.HOME,
            benefit = "Never running out of clean clothes",
            schedule = "weekly",
            tinyStart = "Sort one load", minimumVersion = "Wash and dry one load",
            standardVersion = "Wash, dry, fold, put away", stretchVersion = "Plus ironing and stain checks",
            frictionPlan = "A fixed laundry day",
            environmentPrep = "Sort as clothes come off",
            reward = "A clear basket", difficulty = 2, estimatedMinutes = 30),
        template("water_plants", "Water the plants", LifeArea.HOME,
            benefit = "Living things that thrive",
            schedule = "weekly",
            tinyStart = "Check one plant", minimumVersion = "Water the thirsty ones",
            standardVersion = "Water all plants", stretchVersion = "Water plus prune and check",
            frictionPlan = "Keep the can where I see it",
            environmentPrep = "Group plants by water need",
            reward = "New growth", difficulty = 1, estimatedMinutes = 10)
    )

    /* -------------------------------------------------------------- helper */

    private fun template(
        id: String, title: String, area: LifeArea,
        benefit: String = "", cueTime: String = "", cuePlace: String = "", anchorText: String = "",
        tinyStart: String = "", minimumVersion: String = "", standardVersion: String = "",
        stretchVersion: String = "", frictionPlan: String = "", environmentPrep: String = "",
        reward: String = "", recoveryPlan: String = "Return tomorrow with the tiny version. Never miss twice.",
        difficulty: Int = 3, estimatedMinutes: Int = 10, schedule: String = "daily",
        obstacles: List<Pair<String, String>> = emptyList()
    ) = HabitTemplate(
        id = id, title = title, area = area, benefit = benefit,
        cueTime = cueTime, cuePlace = cuePlace, anchorText = anchorText,
        tinyStart = tinyStart, minimumVersion = minimumVersion,
        standardVersion = standardVersion, stretchVersion = stretchVersion,
        frictionPlan = frictionPlan, environmentPrep = environmentPrep,
        reward = reward, recoveryPlan = recoveryPlan, difficulty = difficulty,
        estimatedMinutes = estimatedMinutes, schedule = schedule, obstacles = obstacles
    )
}
