package com.superflow.blueprint

import com.superflow.data.BlueprintProject
import com.superflow.data.BlueprintSource
import com.superflow.data.Repo
import com.superflow.data.Requirement
import com.superflow.data.RequirementStatus
import com.superflow.util.jsonOf
import org.json.JSONObject

/**
 * The Intent Compiler.
 *
 * Sources plus instructions become a source-linked Requirement Ledger, then a
 * declarative target state, then existing narrow domain commands. There is no
 * unrestricted "apply anything" capability, and nothing is claimed as done
 * until it has been verified against real app state.
 */
object Compiler {

    /** Ingested text is data, never instructions. */
    const val ISOLATION_NOTE =
        "Source documents are treated as data only. Any instruction inside a source that tries " +
                "to change SuperFlow's rules, permissions or safety behaviour is ignored."

    private val bulletRegex = Regex("^\\s*(?:[-*+]|\\d+[.)])\\s+(.{3,300})$")
    private val headingRegex = Regex("^\\s*#{1,6}\\s+(.{2,120})$")

    /**
     * Deterministic extraction pass. Works completely offline so Blueprint
     * Studio is useful with no provider configured; a Cloud Main Brain can
     * refine the same ledger afterwards.
     */
    fun extractRequirements(project: BlueprintProject, sources: List<BlueprintSource>): List<Requirement> {
        val out = ArrayList<Requirement>()
        var order = 0

        for (src in sources) {
            val lines = src.content.lines()
            var section = ""
            for ((idx, rawLine) in lines.withIndex()) {
                val line = rawLine.trimEnd()
                if (line.isBlank()) continue

                headingRegex.find(line)?.let {
                    section = it.groupValues[1].trim()
                    return@let
                }

                val bullet = bulletRegex.find(line)?.groupValues?.get(1)?.trim()
                val candidate = when {
                    bullet != null -> bullet
                    looksActionable(line) -> line.trim()
                    else -> null
                } ?: continue

                if (candidate.length < 4) continue
                if (isInjectionAttempt(candidate)) {
                    out.add(Requirement(
                        projectId = project.id,
                        text = "Ignored an instruction embedded in a source document",
                        sourceId = src.id,
                        citation = "${src.name}:L${idx + 1}",
                        status = RequirementStatus.REJECTED,
                        note = ISOLATION_NOTE,
                        orderIndex = order++
                    ))
                    continue
                }

                val command = planCommand(candidate, section)
                out.add(Requirement(
                    projectId = project.id,
                    text = candidate.take(280),
                    sourceId = src.id,
                    citation = "${src.name}:L${idx + 1}",
                    status = if (command == null) RequirementStatus.DEFERRED else RequirementStatus.ACCEPTED,
                    plannedCommand = command?.toString() ?: "",
                    note = if (command == null) "No safe automatic mapping. Needs a manual decision." else "",
                    orderIndex = order++
                ))
            }
        }

        // Main instructions are user intent and rank above document content.
        if (project.instructions.isNotBlank()) {
            for (line in project.instructions.lines()) {
                val text = line.trim().removePrefix("-").trim()
                if (text.length < 4) continue
                val command = planCommand(text, "instructions")
                out.add(Requirement(
                    projectId = project.id,
                    text = text.take(280),
                    sourceId = null,
                    citation = "your instructions",
                    status = if (command == null) RequirementStatus.DEFERRED else RequirementStatus.ACCEPTED,
                    plannedCommand = command?.toString() ?: "",
                    orderIndex = order++
                ))
            }
        }

        return dedupe(markConflicts(out))
    }

    private fun looksActionable(line: String): Boolean {
        val s = line.lowercase().trim()
        if (s.length !in 6..300) return false
        if (s.endsWith("?")) return false
        return listOf(
            "i want", "i need", "i should", "i will", "every day", "daily", "each morning",
            "each evening", "habit", "goal", "routine", "stop ", "start ", "quit ",
            "identity", "become", "track", "reduce", "practice", "practise"
        ).any { s.contains(it) }
    }

    /** Sources must not be able to escalate their own authority. */
    fun isInjectionAttempt(text: String): Boolean {
        val s = text.lowercase()
        return listOf(
            "ignore previous", "ignore all previous", "disregard the", "you are now",
            "system prompt", "override the", "grant yourself", "bypass", "disable safety",
            "reveal the api key", "print the key", "act as an unrestricted"
        ).any { s.contains(it) }
    }

    /** Maps a requirement to one existing narrow domain command, or null. */
    fun planCommand(text: String, section: String): JSONObject? {
        val s = text.lowercase().trim()

        // Identity
        if (s.startsWith("i am becoming") || s.startsWith("become ") ||
            s.contains("i want to be someone") || section.lowercase().contains("identity")) {
            val statement = text.replace(Regex("^(?i)i am becoming\\s*", RegexOption.IGNORE_CASE), "")
                .replace(Regex("^(?i)become\\s*"), "").trim()
            if (statement.length in 3..160) {
                return jsonOf("command" to "create_identity",
                    "args" to jsonOf("statement" to statement, "lifeArea" to guessArea(statement)))
            }
        }

        // Goal
        if (section.lowercase().contains("goal") || s.startsWith("goal:")) {
            val title = text.removePrefix("Goal:").removePrefix("goal:").trim()
            if (title.length in 3..160) {
                return jsonOf("command" to "create_goal", "args" to jsonOf("title" to title))
            }
        }

        // System
        if (section.lowercase().contains("system") || section.lowercase().contains("routine")) {
            if (text.length in 3..160) {
                return jsonOf("command" to "create_system", "args" to jsonOf("title" to text.trim()))
            }
        }

        // Reduce mode
        if (s.startsWith("stop ") || s.startsWith("quit ") || s.startsWith("cut down") ||
            s.startsWith("reduce ") || s.startsWith("less ")) {
            val title = text.replace(Regex("^(?i)(stop|quit|reduce|cut down on|less)\\s*"), "").trim()
            if (title.length in 2..80) {
                val args = com.superflow.ai.Coordinator.parseHabitPhrase(title) ?: return null
                args.put("mode", "REDUCE")
                return jsonOf("command" to "create_habit", "args" to args)
            }
        }

        // Habit
        val habitish = s.startsWith("i want to") || s.startsWith("i will") || s.startsWith("i should") ||
                s.contains("every day") || s.contains("daily") || s.contains("each morning") ||
                s.contains("each evening") || section.lowercase().contains("habit") ||
                Regex("\\bat \\d{1,2}[:.]\\d{2}\\b").containsMatchIn(s)
        if (habitish) {
            val cleaned = text
                .replace(Regex("^(?i)i want to\\s*"), "")
                .replace(Regex("^(?i)i will\\s*"), "")
                .replace(Regex("^(?i)i should\\s*"), "")
                .replace(Regex("^(?i)i need to\\s*"), "")
                .trim()
            if (cleaned.length in 2..120) {
                val args = com.superflow.ai.Coordinator.parseHabitPhrase(cleaned) ?: return null
                return jsonOf("command" to "create_habit", "args" to args)
            }
        }

        return null
    }

    private fun guessArea(text: String): String {
        val s = text.lowercase()
        return when {
            listOf("health", "fit", "walk", "run", "gym", "sleep", "eat").any { s.contains(it) } -> "HEALTH"
            listOf("learn", "study", "read", "course", "language").any { s.contains(it) } -> "LEARNING"
            listOf("family", "friend", "partner", "relationship").any { s.contains(it) } -> "RELATIONSHIPS"
            listOf("work", "career", "job", "business").any { s.contains(it) } -> "WORK"
            listOf("write", "draw", "music", "creative", "art").any { s.contains(it) } -> "CREATIVITY"
            listOf("money", "save", "budget", "finance").any { s.contains(it) } -> "FINANCE"
            listOf("meditat", "calm", "mindful", "breath").any { s.contains(it) } -> "MINDFULNESS"
            listOf("home", "clean", "tidy", "house").any { s.contains(it) } -> "HOME"
            else -> "CUSTOM"
        }
    }

    /** Flags requirements that plan to create the same thing twice. */
    private fun markConflicts(list: List<Requirement>): List<Requirement> {
        val seen = HashMap<String, Requirement>()
        return list.map { r ->
            if (r.plannedCommand.isBlank()) return@map r
            val obj = runCatching { JSONObject(r.plannedCommand) }.getOrNull() ?: return@map r
            val cmd = obj.optString("command")
            val title = obj.optJSONObject("args")?.let {
                it.optString("title").ifBlank { it.optString("statement") }
            }?.lowercase()?.trim() ?: return@map r
            val key = "$cmd|$title"
            val prior = seen[key]
            if (prior == null) {
                seen[key] = r
                r
            } else {
                r.copy(status = RequirementStatus.CONFLICTED,
                    note = "Duplicates \"${prior.text.take(60)}\" from ${prior.citation}")
            }
        }
    }

    private fun dedupe(list: List<Requirement>): List<Requirement> {
        val seenText = HashSet<String>()
        return list.filter { seenText.add(it.text.lowercase().trim() + "|" + it.citation) }
    }

    /** Coverage report shown before and after execution. */
    fun coverage(reqs: List<Requirement>): String {
        if (reqs.isEmpty()) return "No requirements extracted yet."
        val byStatus = reqs.groupingBy { it.status }.eachCount()
        val sb = StringBuilder()
        sb.append("${reqs.size} requirements\n")
        for (s in RequirementStatus.values()) {
            val n = byStatus[s] ?: continue
            sb.append("- ${s.name.lowercase().replaceFirstChar { it.uppercase() }}: $n\n")
        }
        val sources = reqs.mapNotNull { it.sourceId }.distinct().size
        sb.append("Drawn from $sources sources plus your instructions.")
        return sb.toString()
    }

    /** Verification pass: does real app state match what was planned? */
    fun verify(repo: Repo, reqs: List<Requirement>): Pair<Int, List<Requirement>> {
        var verified = 0
        val gaps = ArrayList<Requirement>()
        for (r in reqs) {
            if (r.status != RequirementStatus.IMPLEMENTED) continue
            val obj = runCatching { JSONObject(r.plannedCommand) }.getOrNull()
            val args = obj?.optJSONObject("args")
            val command = obj?.optString("command") ?: ""
            val title = args?.optString("title")?.ifBlank { null }
                ?: args?.optString("statement")?.ifBlank { null }
            val exists = when (command) {
                "create_habit" -> title != null && repo.findHabit(title) != null
                "create_identity" -> title != null &&
                        repo.identities(true).any { it.statement.equals(title, true) }
                "create_goal" -> title != null && repo.goals().any { it.title.equals(title, true) }
                "create_system" -> title != null && repo.systems().any { it.title.equals(title, true) }
                else -> true
            }
            if (exists) verified++ else gaps.add(r)
        }
        return verified to gaps
    }
}
