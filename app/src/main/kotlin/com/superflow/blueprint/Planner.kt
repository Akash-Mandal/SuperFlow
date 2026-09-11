package com.superflow.blueprint

import com.superflow.data.Repository
import com.superflow.data.model.BlueprintSource
import com.superflow.data.model.Habit
import com.superflow.data.model.HabitTemplate
import com.superflow.data.model.HabitUpgrade
import com.superflow.data.model.PlanPhase
import com.superflow.data.model.PlannedHabit
import com.superflow.data.model.ProgressivePlan
import com.superflow.data.model.Requirement
import com.superflow.data.model.RequirementStatus
import com.superflow.data.model.Theme
import com.superflow.data.model.UserIntent
import com.superflow.domain.HabitTemplates
import com.superflow.util.jsonOf

/**
 * Blueprint Studio 2.0 planner.
 *
 * One job: turn a dream + materials + rules into a phased habit plan.
 * Dream (goal, minutes, weeks) → read materials for themes → drop
 * duplicates → deal themes into 2-week phases (2–3 habits now, rest later)
 * → emit one executable requirement per habit.
 *
 * Sources are data, never orders: embedded instructions are rejected, and
 * the user's own rules always outrank documents.
 */
object Planner {

    const val ISOLATION_NOTE =
        "Source documents are treated as data only. Any instruction inside a source that tries " +
            "to change SuperFlow's rules, permissions or safety behaviour is ignored."

    private val bulletRegex = Regex("^\\s*(?:[-*+]|\\d+[.)])\\s+(.{3,300})$")
    private val headingRegex = Regex("^\\s*#{1,6}\\s+(.{2,120})$")

    /* ------------------------------------------------------------ dream — */

    fun dreamOf(
        goal: String = "",
        dailyMinutes: Int = 30,
        weeks: Int = 8,
    ): UserIntent = UserIntent(
        goal = goal.trim().ifBlank { "Build a habit system" },
        dailyTimeMinutes = dailyMinutes.coerceIn(5, 240),
        durationWeeks = weeks.coerceIn(4, 52),
    )

    /* --------------------------------------------------------- materials — */

    fun readThemes(sources: List<BlueprintSource>, intent: UserIntent): List<Theme> {
        val items = extractItems(sources, intent)
        return prioritise(groupIntoThemes(items), intent)
    }

    private data class Item(
        val text: String,
        val section: String,
        val citation: String,
        val rejected: Boolean = false,
    )

    private fun extractItems(sources: List<BlueprintSource>, intent: UserIntent): List<Item> {
        val out = ArrayList<Item>()
        for (src in sources) {
            var section = ""
            for ((idx, rawLine) in src.content.lines().withIndex()) {
                val line = rawLine.trimEnd()
                if (line.isBlank()) continue
                headingRegex.find(line)?.groupValues?.get(1)?.trim()?.let {
                    section = it
                    continue
                }
                val bullet = bulletRegex.find(line)?.groupValues?.get(1)?.trim()
                val candidate = bullet
                    ?: if (looksActionable(line)) line.trim() else null
                    ?: continue
                if (candidate.length < 4) continue
                if (isInjectionAttempt(candidate)) {
                    out.add(Item("Ignored an instruction embedded in a source", section, "${src.name}:L${idx + 1}", rejected = true))
                    continue
                }
                out.add(Item(candidate.take(280), section, "${src.name}:L${idx + 1}"))
            }
        }
        intent.goal.lines().filter { it.trim().length > 3 }.forEach {
            out.add(Item(it.trim(), "your rules", "your rules"))
        }
        return out
    }

    private fun looksActionable(line: String): Boolean {
        val s = line.lowercase().trim()
        if (s.length !in 6..300 || s.endsWith("?")) return false
        return listOf(
            "i want", "i need", "i should", "i will", "every day", "daily", "each morning",
            "each evening", "habit", "goal", "routine", "stop ", "start ", "quit ",
            "identity", "becom", "track", "reduce", "practice", "practise",
            "journal", "read ", "write ", "drink ", "meditat", "exercise", "stretch", "walk", "yoga", "sleep"
        ).any { s.contains(it) }
    }

    fun isInjectionAttempt(text: String): Boolean {
        val s = text.lowercase()
        return listOf(
            "ignore previous", "ignore all previous", "disregard the", "you are now",
            "system prompt", "override the", "grant yourself", "bypass", "disable safety",
            "reveal the api key", "print the key", "act as an unrestricted"
        ).any { s.contains(it) }
    }

    private fun groupIntoThemes(items: List<Item>): List<Theme> {
        val themes = ArrayList<Theme>()
        for ((section, group) in items.groupBy { themeName(it.section) }) {
            if (group.size == 1 && section.length < 5) continue
            themes.add(Theme(
                name = section.ifBlank { group.first().text.take(40) },
                items = dedup(group.map { it.text }),
                estimatedMinutesPerDay = guessMinutes(group),
            ))
        }
        return themes
    }

    private fun dedup(items: List<String>): List<String> {
        val out = mutableListOf<String>()
        for (t in items) {
            val norm = words(t)
            if (out.none { o ->
                val oSet = words(o)
                val union = norm.union(oSet).size.toDouble()
                union != 0.0 && norm.intersect(oSet).size / union >= 0.8
            }) out.add(t)
        }
        return out
    }

    private fun words(t: String): Set<String> =
        t.lowercase().replace(Regex("[^a-z0-9 ]"), " ").trim()
            .split(Regex("\\s+")).filter { it.length > 2 }.toSet()

    private fun prioritise(themes: List<Theme>, intent: UserIntent): List<Theme> {
        val goalFirst = intent.goal.lowercase().split(" ").firstOrNull().orEmpty()
        return themes.sortedByDescending { theme ->
            var score = theme.items.size
            theme.items.forEach { item ->
                val l = item.lowercase()
                if (goalFirst.isNotBlank() && l.contains(goalFirst)) score += 5
                intent.priorityAreas.forEach { area ->
                    if (l.contains(area.lowercase())) score += 3
                }
            }
            score
        }
    }

    private fun themeName(raw: String): String {
        val s = raw.lowercase().trim()
        return when {
            s.matches(Regex(".*(walk|running|fitness|exercise|stretch|strength|workout).*")) -> "Movement"
            s.matches(Regex(".*(meditat|mindful|breath|calm|yoga|prayer).*")) -> "Mindfulness"
            s.matches(Regex(".*(read|book|study|learn|course).*")) -> "Learning"
            s.matches(Regex(".*(eat|nutrition|food|meal|diet|water|protein).*")) -> "Nutrition"
            s.matches(Regex(".*(sleep|bed|wind down|evening routine).*")) -> "Sleep"
            s.matches(Regex(".*(work|focus|deep|career|business).*")) -> "Focus"
            s.matches(Regex(".*(family|friend|partner|relationship).*")) -> "Relationships"
            s.matches(Regex(".*(save|money|budget|finance).*")) -> "Finance"
            s.matches(Regex(".*(write|creative|art|music|draw|paint).*")) -> "Creativity"
            else -> raw.trim().ifBlank { "General" }
        }
    }

    private fun guessMinutes(group: List<Item>): Int {
        val first = group.firstOrNull()?.text.orEmpty().lowercase()
        return when {
            first.matches(Regex(".*(\\d+)\\s*(hour|hr).*")) -> 60
            first.matches(Regex(".*(\\d+)\\s*min.*")) -> first.filter { it.isDigit() }.toIntOrNull() ?: 10
            first.length > 100 -> 20
            else -> 5
        }
    }

    /* ------------------------------------------------------------- plan — */

    fun makePlan(
        themes: List<Theme>,
        intent: UserIntent,
        @Suppress("UNUSED_PARAMETER") existingHabits: List<Habit> = emptyList(),
    ): ProgressivePlan {
        val totalWeeks = intent.durationWeeks.coerceIn(4, 52)
        val phaseCount = ((totalWeeks + 1) / 2).coerceAtLeast(2)
        val ordered = themes.takeIf { it.isNotEmpty() } ?: listOf(
            Theme("Movement", listOf("Move daily"), estimatedMinutesPerDay = 10),
            Theme("Mindfulness", listOf("Take one breath"), estimatedMinutesPerDay = 2),
        )
        val perPhase = (intent.dailyTimeMinutes / 10).coerceAtLeast(1).coerceAtMost(3)
        val phases = ArrayList<PlanPhase>()
        var themeIdx = 0
        for (p in 0 until phaseCount) {
            val phaseThemes = ordered.drop(themeIdx).take(perPhase)
            val habits = phaseThemes.flatMap { themeToHabits(it, p) }
            val upgrades = if (p == 0) emptyList() else phases.take(p).flatMap { prev ->
                prev.newHabits.map { ph ->
                    HabitUpgrade("", "standardVersion", ph.standardVersion,
                        ph.stretchVersion.ifBlank { ph.standardVersion })
                }
            }
            phases.add(PlanPhase(
                weekStart = p * 2 + 1,
                weekEnd = (p + 1) * 2,
                label = phaseLabel(p, phaseCount),
                newHabits = habits,
                upgrades = upgrades,
                focusArea = phaseThemes.firstOrNull()?.name ?: "Foundation",
            ))
            themeIdx += perPhase
        }
        return ProgressivePlan(
            phases = phases,
            totalWeeks = totalWeeks,
            estimatedDailyTimeMinutes = phases.firstOrNull()?.newHabits?.sumOf { it.estimatedMinutes } ?: 10,
        )
    }

    private fun phaseLabel(phase: Int, total: Int): String = when {
        phase == 0 -> "Foundation"
        phase == total - 1 -> "Flourishing"
        phase < total / 2 -> "Building"
        phase < total - 1 -> "Growing"
        else -> "Strengthening"
    }

    private fun themeToHabits(theme: Theme, phaseIdx: Int): List<PlannedHabit> {
        val templates = HabitTemplates.suggestForGoal(theme.name).take(1).ifEmpty {
            listOf(HabitTemplate(
                title = theme.name,
                tinyStart = "One step",
                minimumVersion = theme.items.firstOrNull()?.take(80).orEmpty(),
                standardVersion = theme.items.firstOrNull()?.take(80).orEmpty(),
                stretchVersion = theme.items.firstOrNull()?.take(80).orEmpty(),
                cueTime = if (phaseIdx == 0) "07:00" else "",
                recurrenceLabel = "3x a week",
            ))
        }
        return templates.take(2).map { tpl ->
            PlannedHabit(
                title = tpl.title,
                tinyStart = tpl.tinyStart.ifBlank { "One step" },
                minimumVersion = tpl.minimumVersion.ifBlank { tpl.tinyStart },
                standardVersion = tpl.standardVersion.ifBlank { tpl.tinyStart },
                stretchVersion = tpl.stretchVersion.ifBlank { tpl.standardVersion },
                cueTime = tpl.cueTime,
                anchorText = tpl.anchorHint,
                daysPerWeek = if (phaseIdx == 0) 3 else (3 + phaseIdx).coerceAtMost(7),
                estimatedMinutes = guessHabitMinutes(tpl.standardVersion),
                lifeArea = tpl.area.name,
            )
        }
    }

    private fun guessHabitMinutes(text: String): Int = when {
        text.isBlank() -> 5
        text.contains("hour") || text.contains("hr") -> 60
        Regex("(\\d+)\\s*min").find(text)?.groupValues?.getOrNull(1)?.toIntOrNull() != null ->
            Regex("(\\d+)\\s*min").find(text)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 10
        text.length > 100 -> 20
        text.length > 30 -> 10
        else -> 2
    }

    /** Plain-words summary for step 4 — no jargon. */
    fun describe(plan: ProgressivePlan): String = buildString {
        val first = plan.phases.firstOrNull()
        val nowCount = first?.newHabits?.size ?: 0
        append("Starts with $nowCount habit${if (nowCount == 1) "" else "s"} " +
            "(~${plan.estimatedDailyTimeMinutes} min a day), " +
            "then grows over ${plan.totalWeeks} weeks in ${plan.phases.size} phases.")
    }

    /* ------------------------------------------------------------ build — */

    fun requirementsFor(phase: PlanPhase, projectId: String, phaseIndex: Int): List<Requirement> {
        val out = ArrayList<Requirement>()
        var order = 0
        for (habit in phase.newHabits) {
            val standard = when (phaseIndex) {
                0 -> habit.tinyStart
                1 -> habit.minimumVersion.ifBlank { habit.tinyStart }
                else -> habit.standardVersion
            }.ifBlank { habit.tinyStart }
            out.add(Requirement(
                projectId = projectId,
                text = "Week ${phase.weekStart}: ${habit.title} (${phase.label})",
                sourceId = null,
                status = RequirementStatus.ACCEPTED,
                plannedCommand = jsonOf(
                    "command" to "create_habit",
                    "args" to jsonOf(
                        "title" to habit.title,
                        "tinyStart" to habit.tinyStart,
                        "minimumVersion" to habit.minimumVersion,
                        "standardVersion" to standard,
                        "stretchVersion" to habit.stretchVersion,
                        "cueTime" to habit.cueTime,
                        "anchorText" to habit.anchorText,
                        "days" to daysLabel(habit.daysPerWeek),
                    ),
                ).toString(),
                orderIndex = order++,
            ))
        }
        return out
    }

    private fun daysLabel(days: Int): String = when {
        days >= 7 -> "daily"
        days >= 5 -> "weekdays"
        days >= 2 -> "${days}x a week"
        days == 1 -> "weekly"
        else -> "weekdays"
    }

    fun progressReport(repo: Repository, projectId: String): String {
        val done = repo.requirements(projectId).count {
            it.status == RequirementStatus.IMPLEMENTED || it.status == RequirementStatus.VERIFIED
        }
        val total = repo.requirements(projectId).size
        if (total == 0) return "Nothing built yet — finish step 4 and press Build."
        return "$done of $total built."
    }
}
