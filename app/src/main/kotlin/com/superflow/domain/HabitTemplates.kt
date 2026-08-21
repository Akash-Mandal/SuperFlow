package com.superflow.domain

import com.superflow.data.model.DifficultyLevel
import com.superflow.data.model.HabitTemplate
import com.superflow.data.model.LifeArea

/**
 * Pre-built habit templates organized by life area (Section 5.1 of the Grand Plan).
 *
 * Users face blank-page paralysis; these templates provide a starting point
 * for common goals. The AI can also suggest templates based on user intent.
 */
object HabitTemplates {

    fun forArea(area: LifeArea): List<HabitTemplate> = when (area) {
        LifeArea.HEALTH -> healthTemplates()
        LifeArea.LEARNING -> learningTemplates()
        LifeArea.RELATIONSHIPS -> relationshipsTemplates()
        LifeArea.WORK -> workTemplates()
        LifeArea.CREATIVITY -> creativityTemplates()
        LifeArea.FINANCE -> financeTemplates()
        LifeArea.MINDFULNESS -> mindfulnessTemplates()
        LifeArea.HOME -> homeTemplates()
        LifeArea.CUSTOM -> allTemplates()
    }

    fun allTemplates(): List<HabitTemplate> = buildList {
        addAll(healthTemplates())
        addAll(learningTemplates())
        addAll(relationshipsTemplates())
        addAll(workTemplates())
        addAll(creativityTemplates())
        addAll(financeTemplates())
        addAll(mindfulnessTemplates())
        addAll(homeTemplates())
    }

    fun suggestForGoal(goalTitle: String): List<HabitTemplate> {
        val g = goalTitle.lowercase()
        return when {
            g.contains("run") || g.contains("5k") || g.contains("marathon") -> runningPlan()
            g.contains("read") || g.contains("book") -> readingPlan()
            g.contains("write") || g.contains("novel") || g.contains("blog") -> writingPlan()
            g.contains("meditat") || g.contains("mindful") || g.contains("calm") -> mindfulnessPlan()
            g.contains("weight") || g.contains("fit") || g.contains("strong") -> fitnessPlan()
            g.contains("sleep") -> sleepPlan()
            g.contains("learn") || g.contains("study") -> learningPlan()
            g.contains("save") || g.contains("money") || g.contains("budget") -> financePlan()
            else -> generalWellbeing()
        }
    }

    /* ------------------------------------------------------------- HEALTH */

    private fun healthTemplates() = listOf(
        HabitTemplate("Morning walk", "Put on shoes", "Walk 5 min", "Walk 20 min", "Walk 30 min",
            "07:00", "daily", "After waking up", "Fresh air and gentle movement",
            LifeArea.HEALTH, DifficultyLevel.EASY, listOf("movement", "outdoor", "morning")),
        HabitTemplate("Drink water", "One glass", "Fill your water bottle", "8 glasses", "10 glasses",
            "", "daily", "", "Hydration is the foundation of energy",
            LifeArea.HEALTH, DifficultyLevel.EASY, listOf("hydration", "wellness")),
        HabitTemplate("Stretch", "One stretch", "Stretch for 2 min", "Stretch 10 min", "Stretch 20 min",
            "07:00", "daily", "After waking up", "Flexibility and body awareness",
            LifeArea.HEALTH, DifficultyLevel.EASY, listOf("flexibility", "morning")),
        HabitTemplate("Strength training", "One push-up", "5 min bodyweight", "20 min strength", "40 min strength",
            "12:00", "3x a week", "", "Build functional strength",
            LifeArea.HEALTH, DifficultyLevel.MODERATE, listOf("strength", "exercise")),
        HabitTemplate("Healthy breakfast", "One piece of fruit", "Eat protein", "Balanced breakfast", "Meal-prep breakfast",
            "08:00", "daily", "After waking up", "Fuel your morning",
            LifeArea.HEALTH, DifficultyLevel.EASY, listOf("nutrition", "morning")),
        HabitTemplate("Evening walk", "Step outside", "5 min walk", "20 min walk", "30 min walk",
            "19:00", "daily", "After dinner", "Wind down and digest",
            LifeArea.HEALTH, DifficultyLevel.EASY, listOf("movement", "evening")),
        HabitTemplate("Sleep schedule", "Go to bed 5 min earlier", "Bed by 23:00", "Bed by 22:30", "Bed by 22:00",
            "22:00", "daily", "", "Consistent sleep is the foundation of health",
            LifeArea.HEALTH, DifficultyLevel.MODERATE, listOf("sleep", "routine")),
    )

    /* ----------------------------------------------------------- LEARNING */

    private fun learningTemplates() = listOf(
        HabitTemplate("Read", "Open the book", "Read 1 page", "Read 20 min", "Read 30 min",
            "21:00", "daily", "Before bed", "Expand your mind",
            LifeArea.LEARNING, DifficultyLevel.EASY, listOf("reading", "evening")),
        HabitTemplate("Laguage practice", "Open the app", "1 lesson", "15 min", "30 min",
            "08:00", "weekdays", "After breakfast", "Consistent practice compounds",
            LifeArea.LEARNING, DifficultyLevel.MODERATE, listOf("languages", "morning")),
        HabitTemplate("Online course", "Open the course", "Watch 5 min", "Watch 30 min", "Watch 1 hour",
            "20:00", "3x a week", "", "Invest in your skills",
            LifeArea.LEARNING, DifficultyLevel.MODERATE, listOf("skills", "evening")),
        HabitTemplate("Write a summary", "One sentence", "Write for 2 min", "Write for 15 min", "Write for 30 min",
            "20:30", "daily", "After reading", "Solidify what you learned",
            LifeArea.LEARNING, DifficultyLevel.MODERATE, listOf("reflection", "writing")),
    )

    /* ------------------------------------------------------- RELATIONSHIPS */

    private fun relationshipsTemplates() = listOf(
        HabitTemplate("Call a friend", "Send a text", "5 min call", "15 min call", "30 min call",
            "12:00", "weekly", "", "Connection is a practice",
            LifeArea.RELATIONSHIPS, DifficultyLevel.EASY, listOf("connection", "social")),
        HabitTemplate("Quality time", "Sit together", "10 min without phones", "30 min", "1 hour",
            "19:00", "daily", "", "Presence over productivity",
            LifeArea.RELATIONSHIPS, DifficultyLevel.EASY, listOf("family", "partner")),
        HabitTemplate("Listen actively", "Put down your phone", "Listen for 2 min", "Listen 10 min", "Listen 30 min",
            "", "daily", "", "The best gift you can give",
            LifeArea.RELATIONSHIPS, DifficultyLevel.EASY, listOf("communication")),
        HabitTemplate("Express gratitude", "One word", "One sentence", "A paragraph", "A letter",
            "21:00", "daily", "", "Gratitude rewires the brain",
            LifeArea.RELATIONSHIPS, DifficultyLevel.EASY, listOf("gratitude", "evening")),
    )

    /* --------------------------------------------------------------- WORK */

    private fun workTemplates() = listOf(
        HabitTemplate("Deep work session", "Open your task", "10 min focused", "60 min focused", "120 min focused",
            "09:00", "weekdays", "After morning routine", "Your most important work",
            LifeArea.WORK, DifficultyLevel.CHALLENGING, listOf("focus", "productivity")),
        HabitTemplate("Plan the day", "Write 3 tasks", "5 min planning", "10 min planning", "15 min planning",
            "08:00", "weekdays", "Before starting work", "A plan beats willpower",
            LifeArea.WORK, DifficultyLevel.EASY, listOf("planning", "morning")),
        HabitTemplate("Review the day", "One sentence", "2 min review", "10 min review", "15 min review",
            "17:00", "weekdays", "Before finishing work", "Close the day intentionally",
            LifeArea.WORK, DifficultyLevel.EASY, listOf("review", "evening")),
    )

    /* --------------------------------------------------------- CREATIVITY */

    private fun creativityTemplates() = listOf(
        HabitTemplate("Creative practice", "Open your tools", "5 min", "30 min", "60 min",
            "07:00", "daily", "", "Creativity is a habit, not a gift",
            LifeArea.CREATIVITY, DifficultyLevel.MODERATE, listOf("art", "practice")),
        HabitTemplate("Free writing", "One sentence", "Write 5 min", "Write 20 min", "Write 45 min",
            "07:30", "daily", "", "Clear your mind, find your voice",
            LifeArea.CREATIVITY, DifficultyLevel.EASY, listOf("writing", "morning")),
        HabitTemplate("Brainstorm", "One idea", "3 ideas", "10 ideas", "20 ideas",
            "15:00", "3x a week", "", "Quantity leads to quality",
            LifeArea.CREATIVITY, DifficultyLevel.EASY, listOf("ideas", "afternoon")),
    )

    /* ------------------------------------------------------------  FINANCE */

    private fun financeTemplates() = listOf(
        HabitTemplate("Track spending", "Open the budget app", "Log 1 expense", "Log all expenses", "Review the week",
            "20:00", "daily", "", "What gets measured gets managed",
            LifeArea.FINANCE, DifficultyLevel.MODERATE, listOf("budget", "tracking")),
        HabitTemplate("Save money", "Save $1", "Save $5", "Save percentage", "Max savings",
            "09:00", "weekly", "After payday", "Pay yourself first",
            LifeArea.FINANCE, DifficultyLevel.EASY, listOf("saving", "money")),
        HabitTemplate("Review subscriptions", "List your subs", "Find 1 to cancel", "Cancel unused", "Negotiate bills",
            "", "monthly", "", "Small leaks sink ships",
            LifeArea.FINANCE, DifficultyLevel.MODERATE, listOf("finance", "minimalism")),
    )

    /* ------------------------------------------------------- MINDFULNESS */

    private fun mindfulnessTemplates() = listOf(
        HabitTemplate("Meditate", "One breath", "2 min", "10 min", "20 min",
            "07:00", "daily", "", "The pause that refreshes",
            LifeArea.MINDFULNESS, DifficultyLevel.EASY, listOf("meditation", "calm")),
        HabitTemplate("Gratitude journal", "One thing", "List 3 things", "Write 5 min", "Write 15 min",
            "21:00", "daily", "Before bed", "Train the gratitude muscle",
            LifeArea.MINDFULNESS, DifficultyLevel.EASY, listOf("gratitude", "evening")),
        HabitTemplate("Breathing exercise", "One breath", "1 min breathing", "5 min breathing", "10 min breathing",
            "12:00", "daily", "", "Your anchor in any storm",
            LifeArea.MINDFULNESS, DifficultyLevel.EASY, listOf("breath", "midday")),
        HabitTemplate("Body scan", "Notice your feet", "2 min scan", "10 min scan", "20 min scan",
            "21:30", "daily", "Before sleep", "Connect with your body",
            LifeArea.MINDFULNESS, DifficultyLevel.EASY, listOf("body", "evening")),
    )

    /* --------------------------------------------------------------- HOME */

    private fun homeTemplates() = listOf(
        HabitTemplate("Tidy up", "Tidy 1 thing", "5 min tidy", "15 min tidy", "30 min tidy",
            "08:00", "daily", "", "Your environment shapes you",
            LifeArea.HOME, DifficultyLevel.EASY, listOf("cleaning", "morning")),
        HabitTemplate("Declutter", "Remove 1 item", "3 items", "10 items", "One bag to donate",
            "10:00", "weekly", "", "Space for what matters",
            LifeArea.HOME, DifficultyLevel.MODERATE, listOf("minimalism", "weekend")),
        HabitTemplate("Meal prep", "Prep 1 ingredient", "10 min prep", "30 min prep", "Full week prep",
            "10:00", "weekly", "After groceries", "Health starts in the kitchen",
            LifeArea.HOME, DifficultyLevel.MODERATE, listOf("cooking", "mealplanning")),
    )

    /* --------------------------------------------------- themed plans ---- */

    private fun runningPlan() = listOf(
        HabitTemplate("Walk before run", "Put on shoes", "Walk 5 min", "Walk 20 min", "Walk 30 min",
            "07:00", "daily", "", "Build the base",
            LifeArea.HEALTH, DifficultyLevel.EASY, listOf("running", "beginner")),
        HabitTemplate("Run intervals", "Jog 1 min", "Run-walk 10 min", "20 min intervals", "30 min intervals",
            "07:00", "3x a week", "", "Gradual running build-up",
            LifeArea.HEALTH, DifficultyLevel.MODERATE, listOf("running", "cardio")),
        HabitTemplate("Strength for runners", "One squat", "5 min bodyweight", "15 min strength", "30 min strength",
            "17:00", "2x a week", "", "Prevent injury, run stronger",
            LifeArea.HEALTH, DifficultyLevel.MODERATE, listOf("strength", "running")),
    )

    private fun readingPlan() = listOf(
        HabitTemplate("Read daily", "One page", "Read 5 min", "Read 20 min", "Read 45 min",
            "21:00", "daily", "Before bed", "Consistent reading compounds",
            LifeArea.LEARNING, DifficultyLevel.EASY, listOf("reading", "habit")),
        HabitTemplate("Book journal", "One sentence", "Write 2 min", "Write 10 min", "Write 20 min",
            "21:30", "daily", "After reading", "Solidify what you read",
            LifeArea.LEARNING, DifficultyLevel.EASY, listOf("journal", "reflection")),
    )

    private fun writingPlan() = listOf(
        HabitTemplate("Write daily", "One word", "Write 5 min", "Write 25 min", "Write 60 min",
            "07:00", "daily", "", "Show up, the words will follow",
            LifeArea.CREATIVITY, DifficultyLevel.MODERATE, listOf("writing", "creative")),
        HabitTemplate("Read like a writer", "One page", "Read 5 min", "Read 15 min", "Read 30 min",
            "20:00", "daily", "", "Study the craft",
            LifeArea.LEARNING, DifficultyLevel.EASY, listOf("reading", "writing")),
    )

    private fun mindfulnessPlan() = listOf(
        HabitTemplate("Meditate", "One breath", "2 min", "10 min", "20 min",
            "07:00", "daily", "", "The pause that refreshes",
            LifeArea.MINDFULNESS, DifficultyLevel.EASY, listOf("meditation")),
        HabitTemplate("Mindful check-in", "Notice your breath", "1 min pause", "3 pauses", "5 pauses",
            "12:00", "daily", "", "Moments of presence",
            LifeArea.MINDFULNESS, DifficultyLevel.EASY, listOf("mindfulness", "midday")),
        HabitTemplate("Compassion practice", "One kind thought", "Write 2 min", "10 min loving-kindness", "20 min",
            "21:00", "daily", "", "Kindness starts with yourself",
            LifeArea.MINDFULNESS, DifficultyLevel.EASY, listOf("compassion", "evening")),
    )

    private fun fitnessPlan() = listOf(
        HabitTemplate("Morning movement", "One stretch", "5 min movement", "20 min workout", "45 min workout",
            "07:00", "daily", "", "Move first, everything else second",
            LifeArea.HEALTH, DifficultyLevel.MODERATE, listOf("fitness", "morning")),
        HabitTemplate("Track nutrition", "Log one meal", "Log all meals", "Meal prep", "Perfect nutrition week",
            "08:00", "daily", "", "You are what you eat",
            LifeArea.HEALTH, DifficultyLevel.MODERATE, listOf("nutrition", "tracking")),
    )

    private fun sleepPlan() = listOf(
        HabitTemplate("Wind down routine", "No screens 5 min", "No screens 30 min", "1 hour wind down", "2 hour wind down",
            "21:00", "daily", "", "Your sleep ritual",
            LifeArea.HEALTH, DifficultyLevel.MODERATE, listOf("sleep", "evening")),
        HabitTemplate("Consistent bed time", "Bed 5 min earlier", "Same bedtime", "Same wake time", "Perfect sleep schedule",
            "22:00", "daily", "", "Regularity beats duration",
            LifeArea.HEALTH, DifficultyLevel.MODERATE, listOf("sleep", "routine")),
        HabitTemplate("Morning sunlight", "Open the curtains", "1 min outside", "10 min outside", "30 min outside",
            "07:00", "daily", "", "Set your circadian rhythm",
            LifeArea.HEALTH, DifficultyLevel.EASY, listOf("sleep", "morning")),
    )

    private fun learningPlan() = listOf(
        HabitTemplate("Daily learning", "One fact", "5 min study", "30 min study", "60 min study",
            "08:00", "daily", "", "Be 1% better every day",
            LifeArea.LEARNING, DifficultyLevel.MODERATE, listOf("learning", "education")),
        HabitTemplate("Teach someone", "Share one thing", "Explain briefly", "Write a summary", "Make a tutorial",
            "17:00", "weekly", "", "Teaching is the best way to learn",
            LifeArea.LEARNING, DifficultyLevel.MODERATE, listOf("teaching", "sharing")),
    )

    private fun financePlan() = listOf(
        HabitTemplate("Daily expense tracking", "Log one expense", "Log all expenses", "Categorize", "Review weekly trends",
            "20:00", "daily", "", "Know where your money goes",
            LifeArea.FINANCE, DifficultyLevel.MODERATE, listOf("budget", "tracking")),
        HabitTemplate("Weekly budget review", "Open banking app", "5 min review", "15 min review", "30 min review",
            "10:00", "weekly", "Sunday morning", "Stay in control",
            LifeArea.FINANCE, DifficultyLevel.MODERATE, listOf("finance", "planning")),
    )

    private fun generalWellbeing() = listOf(
        HabitTemplate("Move your body", "One stretch", "5 min walk", "20 min exercise", "45 min exercise",
            "07:00", "daily", "", "Your body is your home",
            LifeArea.HEALTH, DifficultyLevel.EASY, listOf("movement", "wellness")),
        HabitTemplate("Connect with someone", "Send one text", "5 min chat", "30 min catch-up", "Quality time",
            "12:00", "daily", "", "Connection is a human need",
            LifeArea.RELATIONSHIPS, DifficultyLevel.EASY, listOf("social", "connection")),
        HabitTemplate("Learn something new", "One curiosity", "5 min explore", "30 min learn", "60 min deep dive",
            "19:00", "daily", "", "Stay curious",
            LifeArea.LEARNING, DifficultyLevel.EASY, listOf("learning", "growth")),
        HabitTemplate("Rest and recover", "One deep breath", "5 min pause", "30 min guilt-free rest", "Full rest day",
            "14:00", "daily", "", "Rest is productive",
            LifeArea.MINDFULNESS, DifficultyLevel.EASY, listOf("rest", "recovery")),
    )
}