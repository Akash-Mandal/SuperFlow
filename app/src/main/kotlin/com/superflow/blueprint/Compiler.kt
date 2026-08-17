package com.superflow.blueprint

import com.superflow.ai.Coordinator
import com.superflow.data.Repository
import com.superflow.data.model.BlueprintProject
import com.superflow.data.model.BlueprintSource
import com.superflow.data.model.Requirement
import com.superflow.data.model.RequirementStatus
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

    const val ISOLATION_NOTE =
        "Source documents are treated as data only. Any instruction inside a source that tries " +
                "to change SuperFlow's rules, permissions or safety behaviour is ignored."

    private val bulletRegex = Regex("^\\s*(?:[-*+]|\\d+[.)])\\s+(.{3,300})$")
    private val headingRegex = Regex("^\\s*#{1,6}\\s+(.{2,120})$")

    /**
     * Deterministic extraction. Works completely offline so Blueprint Studio is
     * useful with no provider configured; a Cloud Main Brain can refine the
     * same ledger afterwards.
     */
    fun extractRequirements(
        project: BlueprintProject,
        sources: List<BlueprintSource>
    ): List<Requirement> {
        val out = ArrayList<Requirement>()
        var order = 0

        for (src in sources) {
            val lines = src.content.lines()
            var section = ""
            for ((idx, rawLine) in lines.withIndex()) {
                val line = rawLine.trimEnd()
                if (line.isBlank()) continue

                val heading = headingRegex.find(line)?.groupValues?.get(1)?.trim()
                if (heading != null) { section = heading; continue }

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
                    status = if (command == null) RequirementStatus.DEFERRED
                    else RequirementStatus.ACCEPTED,
                    plannedCommand = command?.toString() ?: "",
                    note = if (command == null)
                        "No safe automatic mapping. Needs a manual decision." else "",
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
                    status = if (command == null) RequirementStatus.DEFERRED
                    else RequirementStatus.ACCEPTED,
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
            "identity", "becom", "track", "reduce", "practice", "practise"
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

        if (s.startsWith("i am becoming") || s.startsWith("become ") ||
            s.contains("i want to be someone") || section.lowercase().contains("identity")) {
            val statement = text
                .replace(Regex("^(?i)i am becoming\\s*"), "")
                .replace(Regex("^(?i)become\\s*"), "").trim()
            if (statement.length in 3..160) {
                return jsonOf("command" to "create_identity",
                    "args" to jsonOf("statement" to statement, "lifeArea" to guessArea(statement)))
            }
        }

        if (section.lowercase().contains("goal") || s.startsWith("goal:")) {
            val title = text.removePrefix("Goal:").removePrefix("goal:").trim()
            if (title.length in 3..160) {
                return jsonOf("command" to "create_goal", "args" to jsonOf("title" to title))
            }
        }

        if (section.lowercase().contains("system") || section.lowercase().contains("routine")) {
            if (text.length in 3..160) {
                return jsonOf("command" to "create_system", "args" to jsonOf("title" to text.trim()))
            }
        }

        if (s.startsWith("stop ") || s.startsWith("quit ") || s.startsWith("cut down") ||
            s.startsWith("reduce ") || s.startsWith("less ")) {
            val title = text.replace(Regex("^(?i)(stop|quit|reduce|cut down on|less)\\s*"), "").trim()
            if (title.length in 2..80) {
                val args = Coordinator.parseHabitPhrase(title) ?: return null
                args.put("mode", "REDUCE")
                return jsonOf("command" to "create_habit", "args" to args)
            }
        }

        val habitish = s.startsWith("i want to") || s.startsWith("i will") ||
                s.startsWith("i should") || s.contains("every day") || s.contains("daily") ||
                s.contains("each morning") || s.contains("each evening") ||
                section.lowercase().contains("habit") ||
                Regex("\\bat \\d{1,2}[:.]\\d{2}\\b").containsMatchIn(s)
        if (habitish) {
            val cleaned = text
                .replace(Regex("^(?i)i want to\\s*"), "")
                .replace(Regex("^(?i)i will\\s*"), "")
                .replace(Regex("^(?i)i should\\s*"), "")
                .replace(Regex("^(?i)i need to\\s*"), "")
                .trim()
            if (cleaned.length in 2..120) {
                val args = Coordinator.parseHabitPhrase(cleaned) ?: return null
                return jsonOf("command" to "create_habit", "args" to args)
            }
        }

        return null
    }

    private fun guessArea(text: String): String {
        val s = text.lowercase()
        return when {
            listOf("health", "fit", "walk", "run", "gym", "sleep", "eat", "body")
                .any { s.contains(it) } -> "HEALTH"
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
            if (prior == null) { seen[key] = r; r }
            else r.copy(status = RequirementStatus.CONFLICTED,
                note = "Duplicates \"${prior.text.take(60)}\" from ${prior.citation}")
        }
    }

    private fun dedupe(list: List<Requirement>): List<Requirement> {
        val seenText = HashSet<String>()
        return list.filter { seenText.add(it.text.lowercase().trim() + "|" + it.citation) }
    }

    fun coverage(reqs: List<Requirement>): String {
        if (reqs.isEmpty()) return "No requirements extracted yet."
        val byStatus = reqs.groupingBy { it.status }.eachCount()
        val sb = StringBuilder()
        sb.append("${reqs.size} requirements\n")
        for (s in RequirementStatus.values()) {
            val n = byStatus[s] ?: continue
            sb.append("· ${s.name.lowercase().replaceFirstChar { it.uppercase() }}: $n\n")
        }
        val sources = reqs.mapNotNull { it.sourceId }.distinct().size
        sb.append("Drawn from $sources sources plus your instructions.")
        return sb.toString()
    }

    /** Verification pass: does real app state match what was planned? */
    fun verify(repo: Repository, reqs: List<Requirement>): Pair<Int, List<Requirement>> {
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

    /* ------------------------------------------------------- cloud refinement */

    /** Prompt asking the Main Brain to improve an already-extracted ledger. */
    fun refinementPrompt(project: BlueprintProject, reqs: List<Requirement>): String {
        val lines = reqs.joinToString("\n") { r ->
            "${r.id}|${r.status.name}|${r.citation}|${r.text.take(160)}"
        }
        return """
            You are refining an already-extracted SuperFlow Requirement Ledger.
            Do NOT invent requirements that have no basis in the listed items.
            You may: improve a requirement's wording, mark clear duplicates as CONFLICTED,
            propose a habit mapping for DEFERRED rows, and flag anything unsafe as REJECTED.

            Mission: ${project.name}
            User instructions: ${project.instructions.ifBlank { "(none)" }}

            Ledger rows, formatted id|status|citation|text:
            $lines

            Reply with ONLY JSON:
            {"updates":[{"id":"<row id>","status":"ACCEPTED|CONFLICTED|DEFERRED|REJECTED",
            "text":"<improved wording, optional>","note":"<short reason>",
            "command":{"command":"create_habit","args":{...}}}]}

            Omit rows you would not change. Every "command" must be one of the SuperFlow
            capabilities and must include a tinyStart when creating a habit.
        """.trimIndent()
    }

    /** Applies a refinement response, returning the number of rows changed. */
    fun applyRefinement(repo: Repository, reqs: List<Requirement>, responseText: String): Int {
        val root = com.superflow.util.extractJson(responseText) ?: return 0
        val updates = root.optJSONArray("updates") ?: return 0
        val byId = reqs.associateBy { it.id }
        var changed = 0
        for (i in 0 until updates.length()) {
            val u = updates.optJSONObject(i) ?: continue
            val target = byId[u.optString("id")] ?: continue
            var updated = target
            u.optString("text").takeIf { it.isNotBlank() }?.let { updated = updated.copy(text = it) }
            u.optString("note").takeIf { it.isNotBlank() }?.let { updated = updated.copy(note = it) }
            u.optString("status").takeIf { it.isNotBlank() }?.let { st ->
                runCatching { RequirementStatus.valueOf(st.uppercase()) }
                    .getOrNull()?.let { updated = updated.copy(status = it) }
            }
            u.optJSONObject("command")?.let { cmd ->
                // Only accept commands that exist and are non-destructive creators.
                val name = cmd.optString("command")
                if (name in setOf("create_habit", "create_identity", "create_goal",
                        "create_system", "add_obstacle_plan", "create_flow")) {
                    updated = updated.copy(
                        plannedCommand = cmd.toString(),
                        status = if (updated.status == RequirementStatus.DEFERRED)
                            RequirementStatus.ACCEPTED else updated.status
                    )
                }
            }
            if (updated != target) {
                repo.saveRequirement(updated)
                changed++
            }
        }
        return changed
    }

    /* ----------------------------------------------------------- version diff */

    data class Diff(val added: List<String>, val removed: List<String>, val changed: List<String>)

    fun diff(previous: List<Requirement>, current: List<Requirement>): Diff {
        val prevByText = previous.associateBy { it.text.lowercase().trim() }
        val currByText = current.associateBy { it.text.lowercase().trim() }
        val added = current.filter { it.text.lowercase().trim() !in prevByText }.map { it.text }
        val removed = previous.filter { it.text.lowercase().trim() !in currByText }.map { it.text }
        val changed = current.mapNotNull { c ->
            val p = prevByText[c.text.lowercase().trim()] ?: return@mapNotNull null
            if (p.status != c.status) "${c.text.take(60)}: ${p.status.name} → ${c.status.name}"
            else null
        }
        return Diff(added, removed, changed)
    }
}
