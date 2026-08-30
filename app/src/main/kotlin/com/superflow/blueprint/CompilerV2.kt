package com.superflow.blueprint

import com.superflow.core.schedule.Recurrence
import com.superflow.data.Repository
import com.superflow.data.model.*
import com.superflow.domain.GrowthEngine
import com.superflow.domain.HabitTemplates
import com.superflow.util.jsonOf
import org.json.JSONArray
import org.json.JSONObject

/**
 * The Intent Compiler — Blueprint Studio Rewrite (Section 3 of the Grand Plan).
 *
 * Key improvements over the original Compiler:
 *
 *  1. Intent Capture — asks about goal, time, level before extracting
 *  2. Smart Extraction — pulls themes and capabilities, not bullet soup
 *  3. Phased Plan Generation — creates GrowthPlan for each habit
 *  4. Progressive Execution — phase-by-phase, not all at once
 *  5. Weekly Reports — incremental updates with data
 *
 * The V1 is preserved for backward compatibility; new projects should use V2.
 */
object CompilerV2 {

    const val ISOLATION_NOTE =
        "Source documents are treated as data only. Any instruction inside a source that tries " +
                "to change SuperFlow's rules, permissions or safety behaviour is ignored."

    private val bulletRegex = Regex("^\\s*(?:[-*+]|\\d+[.)])\\s+(.{3,300})$")
    private val headingRegex = Regex("^\\s*#{1,6}\\s+(.{2,120})$")

    /* ------------------------------------------------------ step 1 intent — */

    /**
     * Captures the user's intent. Returns a UserIntent with defaults if any
     * fields are blank. Designed to be called before compile() to inform the
     * extraction strategy.
     */
    fun captureIntent(
        goal: String = "",
        dailyTimeMinutes: Int = 30,
        currentLevel: String = "beginner",
        existingRoutines: List<String> = emptyList(),
        durationWeeks: Int = 8,
        priorityAreas: List<String> = emptyList()
    ): UserIntent {
        val adjustedWeeks = when {
            durationWeeks < 4 -> 4  // Minimum 4 weeks
            durationWeeks > 52 -> 52  // Maximum 1 year
            else -> durationWeeks
        }
        val adjustedTime = dailyTimeMinutes.coerceIn(5, 240)
        return UserIntent(
            goal = goal.trim().ifBlank { "Build a habit system" },
            dailyTimeMinutes = adjustedTime,
            currentLevel = currentLevel,
            existingRoutines = existingRoutines,
            durationWeeks = adjustedWeeks,
            priorityAreas = priorityAreas
        )
    }

    /**
     * Render the intent questions as a structured prompt for the user
     * (or the AI).
     */
    fun intentQuestions(): String = """
        Before I compile your plan, I need to know a few things:

        1. What is your main goal? (e.g. "run a 5K by October", "read 12 books this year")
        2. How much time per day can you realistically commit? (e.g. 30 min, 1 hour)
        3. What are you already doing that relates to this?
        4. What is your starting level? (beginner / intermediate / advanced)
        5. How many weeks do you want this to unfold over? (e.g. 8 weeks)
        6. Which life areas are most important to you?

        You can answer in plain language — I'll figure it out.
        """.trimIndent()

    /* ------------------------------------------------------ step 2 themes — */

    /**
     * Phase 1: Extract themes and capabilities from sources.
     *
     * Groups related items by theme rather than treating each bullet as a
     * separate requirement. Themes are prioritised against the user's intent.
     */
    fun extractThemes(
        sources: List<BlueprintSource>,
        intent: UserIntent
    ): List<Theme> {
        // Pull raw items from sources (with section grouping)
        val rawItems = extractRawItems(sources, intent)

        // Group related items into themes by keyword scoring
        val themes = groupIntoThemes(rawItems, intent)

        // Prioritise by the user's stated intent
        return prioritiseThemes(themes, intent)
    }

    private fun extractRawItems(sources: List<BlueprintSource>, intent: UserIntent): List<ExtractedItem> {
        val out = ArrayList<ExtractedItem>()
        for (src in sources) {
            val lines = src.content.lines()
            var section = ""
            for ((idx, rawLine) in lines.withIndex()) {
                val line = rawLine.trimEnd()
                if (line.isBlank()) continue

                val heading = headingRegex.find(line)?.groupValues?.get(1)?.trim()
                if (heading != null) { section = heading; continue }

                val bullet = bulletRegex.find(line)?.groupValues?.get(1)?.trim()
                val candidate = bullet ?: if (looksActionable(line)) line.trim() else null
                    ?: continue

                if (candidate.length < 4) continue

                if (Compiler.isInjectionAttempt(candidate)) {
                    out.add(ExtractedItem(
                        text = "Ignored an instruction embedded in a source document",
                        section = section,
                        sourceId = src.id,
                        citation = "${src.name}:L${idx + 1}",
                        rejected = true
                    ))
                    continue
                }

                out.add(ExtractedItem(
                    text = candidate.take(280),
                    section = section,
                    sourceId = src.id,
                    citation = "${src.name}:L${idx + 1}"
                ))
            }
        }

        // Also add the user's instructions as high-priority themes
        val intentItems = intent.goal.lines().filter { it.trim().length > 3 }
        intentItems.forEach { text ->
            out.add(ExtractedItem(
                text = text.trim(),
                section = "your instructions",
                sourceId = null,
                citation = "your instructions",
                priorityBonus = 10  // User's own words lead
            ))
        }
        return out
    }

    private fun looksActionable(line: String): Boolean {
        val s = line.lowercase().trim()
        if (s.length !in 6..300) return false
        if (s.endsWith("?")) return false
        return listOf(
            "i want", "i need", "i should", "i will", "every day", "daily", "each morning",
            "each evening", "habit", "goal", "routine", "stop ", "start ", "quit ",
            "identity", "becom", "track", "reduce", "practice", "practise",
            "journal", "read ", "write ", "drink ", "meditat", "exercise", "stretch", "walk", "yoga", "sleep"
        ).any { s.contains(it) }
    }

    private fun groupIntoThemes(
        items: List<ExtractedItem>,
        intent: UserIntent
    ): List<Theme> {
        // Map of section -> list of items (sections are typically themes)
        val bySection = items.groupBy { sectionName(it.section) }
        val themes = ArrayList<Theme>()

        // Convert each section into a theme, unless items are sparse
        for ((section, groupItems) in bySection) {
            if (groupItems.size == 1 && section.length < 5) continue  // Skip noise
            val deduped = dedupByJaccard(groupItems.map { it.text })
            themes.add(Theme(
                name = section.ifBlank { groupItems.first().text.take(40) },
                items = deduped,
                estimatedMinutesPerDay = estimateThemeMinutes(groupItems)
            ))
        }
        return themes
    }

    private fun dedupByJaccard(items: List<String>): List<String> {
        val out = mutableListOf<String>()
        for (t in items) {
            val norm = t.lowercase().replace(Regex("[^a-z0-9 ]"), " ").trim().split(Regex("\\s+")).filter { it.length > 2 }.toSet()
            if (out.none { o ->
                val oSet = o.lowercase().replace(Regex("[^a-z0-9 ]"), " ").trim().split(Regex("\\s+")).filter { it.length > 2 }.toSet()
                val inter = norm.intersect(oSet).size.toDouble()
                val union = norm.union(oSet).size.toDouble()
                if (union == 0.0) false else inter / union >= 0.8
            }) out.add(t)
        }
        return out
    }

    private fun prioritiseThemes(themes: List<Theme>, intent: UserIntent): List<Theme> {
        val goal = intent.goal.lowercase()
        return themes.sortedByDescending { theme ->
            var score = theme.items.size  // More items = more coverage
            // Boost themes that mention the user's goal
            theme.items.forEach { item ->
                val l = item.lowercase()
                if (goal.isNotBlank() && l.contains(goal.split(" ").firstOrNull().orEmpty())) score += 5
                // Boost based on priority areas
                intent.priorityAreas.forEach { area ->
                    if (l.contains(area.lowercase())) score += 3
                }
            }
            score
        }
    }

    private fun sectionName(raw: String): String {
        // Map well-known section keywords to canonical theme names
        val s = raw.lowercase().trim()
        return when {
            s.matches(Regex(".*(walk|running|fitness|exercise|stretch|strength|workout).*")) -> "Movement"
            s.matches(Regex(".*(meditat|mindful|breath|calm|yoga|prayer).*")) -> "Mindfulness"
            s.matches(Regex(".*(read|book|study|sparbuch|learn|course).*")) -> "Learning"
            s.matches(Regex(".*(eat|nutrition|food|meal|diet|water|protein).*")) -> "Nutrition"
            s.matches(Regex(".*(sleep|bed|wind down|evening routine).*")) -> "Sleep"
            s.matches(Regex(".*(work|focus|deep|career|business).*")) -> "Focus"
            s.matches(Regex(".*(family|friend|partner|relationship).*")) -> "Relationships"
            s.matches(Regex(".*(save|money|budget|finance).*")) -> "Finance"
            s.matches(Regex(".*(write|creative|art|music|draw|paint).*")) -> "Creativity"
            else -> raw.trim().ifBlank { "General" }
        }
    }

    private fun estimateThemeMinutes(items: List<ExtractedItem>): Int {
        // Guess from the first item's text
        val first = items.firstOrNull()?.text.orEmpty().lowercase()
        return when {
            first.matches(Regex(".*(\\d+)\\s*(hour|hr).*")) -> 60
            first.matches(Regex(".*(\\d+)\\s*min.*")) -> first.filter { it.isDigit() }.toIntOrNull() ?: 10
            first.length > 100 -> 20
            else -> 5
        }
    }

    private data class ExtractedItem(
        val text: String,
        val section: String,
        val sourceId: String?,
        val citation: String,
        val rejected: Boolean = false,
        val priorityBonus: Int = 0
    )

    /* ------------------------------------------------------ step 3 phased — */

    /**
     * Generate a progressive plan from themes + intent.
     *
     * Distributes themes across phases based on total duration. Week 1-2 gets
     * the top 1-2 themes; subsequent phases add 1-2 more or upgrade existing.
     */
    fun generateProgressivePlan(
        themes: List<Theme>,
        intent: UserIntent,
        existingHabits: List<Habit>
    ): ProgressivePlan {
        val totalWeeks = intent.durationWeeks.coerceIn(4, 52)
        val twoWeekPhases = (totalWeeks + 1) / 2  // 1 phase per 2 weeks

        // Order themes by priority (already sorted by extractThemes)
        val ordered = themes.takeIf { it.isNotEmpty() } ?: listOf(
            Theme("Movement", listOf("Move daily"), estimatedMinutesPerDay = 10),
            Theme("Mindfulness", listOf("Take one breath"), estimatedMinutesPerDay = 2)
        )

        // Decide how many habits per phase (cap at intent.dailyTimeMinutes ceiling)
        val maxHabitsPerPhase = (intent.dailyTimeMinutes / 10).coerceAtLeast(1).coerceAtMost(3)
        val phasesNeeded = twoWeekPhases.coerceAtLeast(2)

        val phases = ArrayList<PlanPhase>()
        var phaseThemeIdx = 0

        for (p in 0 until phasesNeeded) {
            val startWeek = p * 2 + 1
            val endWeek = (p + 1) * 2
            val phaseLabel = phaseLabel(p, phasesNeeded)

            // Take 1-3 themes for this phase
            val phaseThemes = ordered.drop(phaseThemeIdx).take(maxHabitsPerPhase)
            val phaseHabits = phaseThemes.flatMap { theme -> themeToHabits(theme, p, intent) }

            // Upgrades for habits from previous phases
            val upgrades = if (p > 0) {
                phases.take(p).flatMap { prev ->
                    prev.newHabits.map { ph ->
                        HabitUpgrade(
                            habitId = "",  // resolved at apply time
                            field = "standardVersion",
                            oldValue = ph.standardVersion,
                            newValue = ph.stretchVersion.ifBlank { ph.standardVersion }
                        )
                    }
                }
            } else emptyList()

            phases.add(PlanPhase(
                weekStart = startWeek,
                weekEnd = endWeek,
                label = phaseLabel,
                newHabits = phaseHabits,
                upgrades = upgrades,
                focusArea = phaseThemes.firstOrNull()?.name ?: "Foundation"
            ))
            phaseThemeIdx += maxHabitsPerPhase
        }

        val totalMinutes = estimateTotalMinutes(phases)
        return ProgressivePlan(
            phases = phases,
            totalWeeks = totalWeeks,
            estimatedDailyTimeMinutes = totalMinutes
        )
    }

    private fun phaseLabel(phase: Int, total: Int): String = when {
        phase == 0 -> "Foundation"
        phase == total - 1 -> "Flourishing"
        phase < total / 2 -> "Building"
        phase < total - 1 -> "Growing"
        else -> "Strengthening"
    }

    private fun themeToHabits(
        theme: Theme,
        phaseIdx: Int,
        intent: UserIntent
    ): List<PlannedHabit> {
        // Try to reuse templates first; if none, generate a habit from the theme text
        val templateCandidates = HabitTemplates.suggestForGoal(theme.name)
        val templates = templateCandidates.take(1).ifEmpty {
            // Fall back to area-based templates by guessing area
            listOf(HabitTemplate(
                title = theme.name,
                tinyStart = "One step",
                standardVersion = theme.items.firstOrNull()?.take(80).orEmpty(),
                minimumVersion = theme.items.firstOrNull()?.take(80).orEmpty(),
                stretchVersion = theme.items.firstOrNull()?.take(80).orEmpty(),
                cueTime = when (phaseIdx) {
                    0 -> "07:00"
                    else -> ""
                },
                recurrenceLabel = "3x a week"
            ))
        }

        return templates.take(2).map { tpl -> PlannedHabit(
            title = tpl.title,
            tinyStart = tpl.tinyStart.ifBlank { "One step" },
            minimumVersion = tpl.minimumVersion.ifBlank { tpl.tinyStart },
            standardVersion = tpl.standardVersion.ifBlank { tpl.tinyStart },
            stretchVersion = tpl.stretchVersion.ifBlank { tpl.standardVersion },
            cueTime = tpl.cueTime,
            cuePlace = "",
            anchorText = tpl.anchorHint,
            daysPerWeek = if (phaseIdx == 0) 3 else (3 + phaseIdx).coerceAtMost(7),
            estimatedMinutes = tpl.let { estimateMinutes(it.standardVersion) },
            lifeArea = tpl.area.name
        ) }
    }

    private fun estimateMinutes(text: String): Int = when {
        text.isBlank() -> 5
        text.contains("hour") || text.contains("hr") -> 60
        Regex("(\\d+)\\s*min").find(text)?.groupValues?.getOrNull(1)?.toIntOrNull() != null ->
            Regex("(\\d+)\\s*min").find(text)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 10
        text.length > 100 -> 20
        text.length > 30 -> 10
        else -> 2
    }

    private fun estimateTotalMinutes(phases: List<PlanPhase>): Int =
        phases.firstOrNull()?.newHabits?.sumOf { it.estimatedMinutes } ?: 10

    /* --------------------------------------------- step 4 progressive exec - */

    /**
     * Compile a single phase to executable requirements — only THIS phase's
     * requirements, not everything at once.
     *
     * Returns a list of Requirement objects to be applied via the command bus
     * one phase at a time.
     */
    fun compilePhase(
        phase: PlanPhase,
        projectId: String,
        phaseIndex: Int
    ): List<Requirement> {
        val out = ArrayList<Requirement>()
        var order = 0

        for (habit in phase.newHabits) {
            // Phase 1 habits are at tiny level; later phases get minimum/standard
            val phaseStandard = when (phaseIndex) {
                0 -> habit.tinyStart
                1 -> habit.minimumVersion.ifBlank { habit.tinyStart }
                else -> habit.standardVersion
            }
            val phaseStandardClean = phaseStandard.ifBlank { habit.tinyStart }

            val habitArgs = jsonOf(
                "title" to habit.title,
                "tinyStart" to habit.tinyStart,
                "minimumVersion" to habit.minimumVersion,
                "standardVersion" to phaseStandardClean,
                "stretchVersion" to habit.stretchVersion,
                "cueTime" to habit.cueTime,
                "anchorText" to habit.anchorText,
                "days" to daysPerWeekToLabel(habit.daysPerWeek)
            )

            out.add(Requirement(
                projectId = projectId,
                text = "Phase ${phase.weekStart}: ${habit.title} (${phase.label})",
                sourceId = null,
                status = RequirementStatus.ACCEPTED,
                plannedCommand = jsonOf(
                    "command" to "create_habit",
                    "args" to habitArgs
                ).toString(),
                orderIndex = order++
            ))
        }

        // Upgrades for existing habits from prior phases
        for (upgrade in phase.upgrades) {
            out.add(Requirement(
                projectId = projectId,
                text = "Phase ${phase.weekStart}: upgrade habit field",
                sourceId = null,
                status = RequirementStatus.ACCEPTED,
                plannedCommand = jsonOf(
                    "command" to "update_habit",
                    "args" to jsonOf(
                        "habit" to upgrade.habitId.ifBlank { error("HabitUpgrade missing habitId for field ${upgrade.field}") },
                        "field" to upgrade.field,
                        "value" to upgrade.newValue
                    )
                ).toString(),
                orderIndex = order++
            ))
        }

        return out
    }

    private fun daysPerWeekToLabel(days: Int): String = when {
        days >= 7 -> "daily"
        days >= 5 -> "weekdays"
        days >= 2 -> "${days}x a week"
        days == 1 -> "weekly"
        else -> "weekdays"
    }

    /* ------------------------------------------------- step 5 weekly report */

    /**
     * Generate a weekly upgrade report for a Blueprint project after a phase
     * has been active. Compares what's complete vs what's planned.
     */
    fun weeklyReport(
        repo: Repository,
        projectId: String,
        phasesCompleted: Int
    ): String {
        val plan = repo.growthPlans().firstOrNull() ?: return "No active growth plan."
        val habit = repo.habit(plan.habitId) ?: return "Habit not found."
        val stats = com.superflow.domain.Insights.forHabit(repo, habit)
        val sb = StringBuilder()
        sb.append("Phase ${plan.currentPhaseIndex + 1} – ${plan.phases[plan.currentPhaseIndex].label}\n")
        sb.append("Consistency this phase: ${stats.consistency30}%\n")
        sb.append("Repetitions: ${stats.repetitions}, Recoveries: ${stats.recoveries}\n\n")
        val lastDecision = plan.weeklySnapshots.lastOrNull()?.decision
        if (stats.consistency30 >= plan.phases[plan.currentPhaseIndex].metrics.minConsistency) {
            sb.append("On track. Next upgrade: ").append(
                if (lastDecision == UpgradeDecision.UPGRADE) "ready to advance" else "evaluate weekly"
            ).append("\n")
        } else {
            sb.append("Phase needs attention. Consider shrinking the standard version.\n")
        }
        sb.append("Phase ${phasesCompleted + 1} plan:\n")
        val nextPhase = plan.phases.getOrNull(plan.currentPhaseIndex + 1)
        nextPhase?.let {
            sb.append("Standard: ${it.standardVersion}; Target: ${it.targetDays} days a week\n")
        }
        return sb.toString()
    }

    /* ------------------------------------------------------ step 2 helpers */

    /**
     * Renders the themes found for the user.
     */
    fun themesSummary(themes: List<Theme>): String {
        if (themes.isEmpty()) return "I could not find any actionable themes in your sources."
        return buildString {
            append("Themes found in your sources:\n")
            themes.forEach { theme ->
                append("\n• ${theme.name} (~${theme.estimatedMinutesPerDay} min/day):\n")
                theme.items.take(3).forEach { append("  - $it\n") }
                if (theme.items.size > 3) append("  ... and ${theme.items.size - 3} more\n")
            }
        }
    }

    /**
     * Compiles with intent-first approach. Used by blueprint UI.
     */
    fun compileForBlueprint(
        project: BlueprintProject,
        sources: List<BlueprintSource>,
        intent: UserIntent = UserIntent()
    ): ProgressivePlan {
        val themes = extractThemes(sources, intent)
        return generateProgressivePlan(themes, intent, emptyList())
    }
}
