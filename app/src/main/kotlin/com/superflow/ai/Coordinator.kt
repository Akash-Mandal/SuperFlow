package com.superflow.ai

import com.superflow.core.time.SfTime
import com.superflow.data.Repository
import com.superflow.data.model.Habit
import com.superflow.data.model.Level
import com.superflow.domain.Capabilities
import com.superflow.util.jsonOf
import org.json.JSONArray
import org.json.JSONObject

/**
 * The Local Coordinator Mini-AI.
 *
 * Deterministic, offline, no network. It handles the common commands directly
 * so the app is fully controllable by text with AI providers disabled, and it
 * is the universal fallback whenever the Cloud Main Brain is unavailable.
 */
object Coordinator {

    data class Plan(
        val command: String,
        val args: JSONObject,
        val confidence: Double,
        val reply: String? = null
    )

    /**
     * The only repository surface [interpret] needs. Seam so the pure
     * parsing/routing logic is unit-testable without an Android context.
     */
    class Lookup(
        val resolve: (String) -> Habit?,
        val all: () -> List<Habit>
    ) {
        companion object {
            fun from(repo: Repository) = Lookup({ repo.findHabit(it) }, { repo.habits() })
        }
    }

    fun interpret(text: String, repo: Repository): Plan? =
        interpret(text, Lookup.from(repo)) ?: interpretWithRepo(text, repo)

    fun interpret(text: String, lookup: Lookup): Plan? {
        val raw = text.trim()
        val s = raw.lowercase()
        if (s.isBlank()) return null

        if (Regex("reinforce|apply.*next phase|next phase|auto.*upgrade").containsMatchIn(s)) {
            return Plan("trigger_auto_reinforce", JSONObject(), 0.92,
                "Triggering Auto Reinforce — applying pending phase.")
        }

        if (s in setOf("hi", "hello", "hey", "yo")) {
            return Plan("noop", JSONObject(), 1.0,
                "Hello. Tell me what to change and I will do it — check in a habit, plan tomorrow, " +
                        "design something new, or ask how you are doing.")
        }
        if (s.contains("what can you do") || (s.contains("help") && s.length < 24)) {
            return Plan("noop", JSONObject(), 1.0, helpText())
        }

        if (matches(s, "how am i doing", "how is it going", "my progress", "show progress", "insights"))
            return Plan("get_insights", jsonOf("days" to 30), 0.95)

        if (matches(s, "what's today", "whats today", "today", "summary",
                "what do i have today", "today summary", "what is on today"))
            return Plan("today_summary", JSONObject(), 0.9)

        if (matches(s, "list habits", "show habits", "my habits", "what habits"))
            return Plan("list_habits", JSONObject(), 0.95)

        if (s.startsWith("search ") || s.startsWith("find "))
            return Plan("search", jsonOf("query" to raw.substringAfter(' ').trim()), 0.9)

        if (matches(s, "plan tomorrow", "plan for tomorrow", "prepare tomorrow"))
            return Plan("plan_tomorrow", JSONObject(), 0.95)

        if (matches(s, "minimum mode", "low energy day", "hard day", "bad day", "reduce today"))
            return Plan("enter_minimum_mode", JSONObject(), 0.9)

        if (matches(s, "morning checkpoint")) return Plan("run_checkpoint", jsonOf("checkpoint" to "MORNING"), 0.95)
        if (matches(s, "midday checkpoint")) return Plan("run_checkpoint", jsonOf("checkpoint" to "MIDDAY"), 0.95)
        if (matches(s, "evening checkpoint")) return Plan("run_checkpoint", jsonOf("checkpoint" to "EVENING"), 0.95)

        Regex("(?:my )?energy (?:is |= )?([1-5])").find(s)?.let {
            return Plan("log_energy", jsonOf("energy" to it.groupValues[1].toInt()), 0.9)
        }

        val level = when {
            s.contains("tiny") -> Level.TINY
            s.contains("minimum") || s.contains("minimal") -> Level.MINIMUM
            s.contains("stretch") -> Level.STRETCH
            else -> Level.STANDARD
        }
        val dateWord = if (s.contains("yesterday")) "yesterday" else "today"

        if (startsAny(s, "skip ", "skipping ")) {
            val name = stripLeading(raw, listOf("skip", "skipping"))
            lookup.resolve(name)?.let {
                return Plan("skip_habit", jsonOf("habit" to it.id, "date" to dateWord), 0.85)
            }
        }

        if (startsAny(s, "missed ", "i missed ", "didn't do ", "did not do ")) {
            val name = stripLeading(raw, listOf("i missed", "missed", "didn't do", "did not do"))
            // Reason-aware miss: "missed walk because I was busy" -> reason captured.
            val withReason = s.contains("because") || s.contains(" due to ")
            val reason = if (withReason) {
                when {
                    s.contains("time") || s.contains("busy") || s.contains("schedule") -> "time"
                    s.contains("tired") || s.contains("exhausted") || s.contains("low energy") -> "energy"
                    s.contains("forgot") -> "forgot"
                    s.contains("motivat") || s.contains("didn't want") || s.contains("lazy") -> "motivation"
                    s.contains("circumstance") || s.contains("unexpected") -> "circumstance"
                    else -> "other"
                }
            } else null
            lookup.resolve(name)?.let {
                val args = jsonOf("habit" to it.id, "date" to dateWord)
                if (reason != null) args.put("reason", reason)
                return Plan("mark_missed", args, 0.85)
            }
        }

        // Level words are valid sentence starters too: the help card
        // advertises "tiny walk", so "tiny walk" must check in at Tiny.
        if (startsAny(s, "done ", "did ", "i did ", "completed ", "finished ", "check in ",
            "checkin ", "check off ", "mark ", "log ", "i completed ",
            "tiny ", "minimum ", "minimal ", "stretch ")) {
            val name = stripLeading(raw, listOf("i completed", "check in", "checkin", "check off",
                "completed", "finished", "i did", "done", "did", "mark", "log",
                "tiny", "minimum", "minimal", "stretch"))
                .removePrefix("my ").removeSuffix(" done").trim()
            val cleaned = name
                .replace(Regex("\\b(tiny|minimum|minimal|standard|stretch|today|yesterday)\\b"), "")
                .trim().trim(',', '.', '-')
            lookup.resolve(cleaned.ifBlank { name })?.let {
                return Plan("check_in",
                    jsonOf("habit" to it.id, "level" to level.name, "date" to dateWord), 0.85)
            }
        }

        if (startsAny(s, "focus on ", "my focus is ", "set focus ", "focus:")) {
            val rest = stripLeading(raw, listOf("focus on", "my focus is", "set focus", "focus:"))
            val items = rest.split(",", " and ", ";").map { it.trim() }.filter { it.isNotBlank() }
            if (items.isNotEmpty() && items.size <= 3) {
                val arr = JSONArray()
                items.forEach { arr.put(it) }
                return Plan("set_daily_focus", jsonOf("items" to arr), 0.85)
            }
        }

        if (startsAny(s, "create habit ", "add habit ", "new habit ", "track ")) {
            val rest = stripLeading(raw, listOf("create habit", "add habit", "new habit", "track"))
            parseHabitPhrase(rest)?.let { return Plan("create_habit", it, 0.8) }
        }

        if (startsAny(s, "i want to ", "help me ", "start ")) {
            val rest = stripLeading(raw, listOf("i want to", "help me", "start"))
            if (rest.length in 3..80 && !rest.contains("?")) {
                parseHabitPhrase(rest)?.let { return Plan("create_habit", it, 0.6) }
            }
        }

        if (startsAny(s, "archive ", "pause ")) {
            val name = stripLeading(raw, listOf("archive", "pause"))
            lookup.resolve(name)?.let { return Plan("archive_habit", jsonOf("habit" to it.id), 0.85) }
        }
        if (startsAny(s, "delete habit ", "remove habit ")) {
            val name = stripLeading(raw, listOf("delete habit", "remove habit"))
            lookup.resolve(name)?.let { return Plan("delete_habit", jsonOf("habit" to it.id), 0.85) }
        }

        Regex("if (.+?),? then (.+)").find(raw)?.let { m ->
            val ifText = m.groupValues[1].trim()
            val thenText = m.groupValues[2].trim()
            val habit = lookup.all().firstOrNull { ifText.lowercase().contains(it.title.lowercase()) }
                ?: lookup.all().firstOrNull()
            if (habit != null) {
                return Plan("add_obstacle_plan",
                    jsonOf("habit" to habit.id, "ifText" to ifText, "thenText" to thenText), 0.7)
            }
        }

        if (startsAny(s, "scorecard ")) {
            val rest = stripLeading(raw, listOf("scorecard"))
            val verdict = when {
                rest.lowercase().contains("unhelpful") || rest.lowercase().contains("bad") -> -1
                rest.lowercase().contains("helpful") || rest.lowercase().contains("good") -> 1
                else -> 0
            }
            return Plan("add_scorecard_entry", jsonOf("routine" to rest, "verdict" to verdict), 0.75)
        }

        /* ------------------------------------------- Core Growth Systems NLP */

        if (matches(s, "today's load", "daily load", "my load", "how much can i handle")) {
            return Plan("get_daily_load", JSONObject(), 0.9)
        }

        if (matches(s, "system health", "health of my systems", "how are my systems")) {
            return Plan("get_system_health", JSONObject(), 0.9)
        }

        if (matches(s, "energy correlation", "energy and habits", "does energy matter")) {
            return Plan("get_energy_correlation", JSONObject(), 0.9)
        }

        if (matches(s, "miss patterns", "when do i miss", "weekday pattern", "why do i miss")) {
            return Plan("get_miss_patterns", JSONObject(), 0.9)
        }

        return null
    }

    /** Core Growth Systems NLP that needs the repository, not just habit lookup. */
    private fun interpretWithRepo(text: String, repo: Repository): Plan? {
        val raw = text.trim()
        val s = raw.lowercase()
        if (s.contains("rate the reward") || s.contains("reward satisfaction")) {
            Regex("(?:for |on )?([a-z ]+?)(?: as |: )?([1-5])(?:/5)?$").find(raw)?.let { m ->
                val name = m.groupValues[1].trim()
                repo.findHabit(name)?.let {
                    return Plan("rate_reward",
                        jsonOf("habit" to it.id, "rating" to m.groupValues[2].toInt()), 0.8)
                }
            }
        }

        if (startsAny(s, "evolve identity ", "i am now ", "identity evolution ")) {
            val rest = stripLeading(raw, listOf("evolve identity", "i am now", "identity evolution"))
            val newStatement = rest.trim().trim('"', '\'', '.', '!')
            if (newStatement.length in 3..100) {
                val id = repo.identities().firstOrNull()?.id
                if (id != null) {
                    return Plan("evolve_identity",
                        jsonOf("id" to id, "newStatement" to newStatement,
                            "reason" to "spoken evolution"), 0.75)
                }
            }
        }

        if (startsAny(s, "carry ", "carry over ")) {
            val rest = stripLeading(raw, listOf("carry over", "carry"))
            repo.focusFor(SfTime.format(repo.clock.today())).firstOrNull {
                it.title.lowercase().contains(rest.lowercase()) && !it.done
            }?.let {
                return Plan("carry_over_focus", jsonOf("id" to it.id), 0.85)
            }
        }

        if (startsAny(s, "star ", "priority ")) {
            val rest = stripLeading(raw, listOf("star", "priority"))
            repo.focusFor(SfTime.format(repo.clock.today())).firstOrNull {
                it.title.lowercase().contains(rest.lowercase())
            }?.let {
                return Plan("set_focus_priority", jsonOf("id" to it.id, "priority" to true), 0.85)
            }
        }

        if (startsAny(s, "milestone for ", "add milestone ")) {
            val rest = stripLeading(raw, listOf("add milestone", "milestone for"))
            val goal = repo.goals().firstOrNull()
            if (goal != null && rest.isNotBlank()) {
                return Plan("add_goal_milestone",
                    jsonOf("goalId" to goal.id, "title" to rest), 0.8)
            }
        }

        if (matches(s, "update goal metric", "goal progress", "goal metric")) {
            val goal = repo.goals().firstOrNull() ?: return null
            Regex("(\\d+(?:\\.\\d+)?)\\s*([a-z]*)$").find(raw)?.let { m ->
                return Plan("update_goal_metric",
                    jsonOf("goalId" to goal.id, "value" to m.groupValues[1].toDouble(),
                        "unit" to m.groupValues[2]), 0.75)
            }
        }

        if (startsAny(s, "run flow ", "start flow ")) {
            val rest = stripLeading(raw, listOf("run flow", "start flow"))
            val flow = repo.flows().firstOrNull {
                it.title.lowercase().contains(rest.lowercase())
            } ?: repo.flows().firstOrNull()
            if (flow != null) {
                return Plan("run_flow", jsonOf("flowId" to flow.id), 0.85)
            }
        }

        if (startsAny(s, "complete flow ", "finished flow ")) {
            val rest = stripLeading(raw, listOf("complete flow", "finished flow"))
            val flow = repo.flows().firstOrNull {
                it.title.lowercase().contains(rest.lowercase())
            } ?: repo.flows().firstOrNull()
            if (flow != null) {
                return Plan("complete_flow", jsonOf("flowId" to flow.id), 0.85)
            }
        }

        return null
    }

    /** Deterministic offline coach card when no model is configured. */
    fun coachCard(repo: Repository): String {
        val stats = com.superflow.domain.Insights.allStats(repo)
        val date = repo.clock.today()
        val checkIns = repo.checkInsFor(
            com.superflow.core.time.SfTime.format(date)).associateBy { it.habitId }
        val open = repo.habitsForDay(date).filter { checkIns[it.id] == null }
        return when {
            stats.isEmpty() ->
                "Start with one habit you could do in two minutes even on your worst day. " +
                        "The size is the point: small enough to survive a bad week."
            open.isNotEmpty() -> {
                val h = open.first()
                val tiny = h.tinyStart.ifBlank { h.title }
                "Smallest next step: $tiny. If that is all you do today, the system still held."
            }
            stats.any { it.missesInARow >= 2 } -> {
                val s = stats.first { it.missesInARow >= 2 }
                "\"${s.habit.title}\" has slipped a few times. Shrink it until it feels almost too " +
                        "easy, then let it grow back on its own."
            }
            else ->
                "Everything scheduled is handled. Consider preparing one thing for tomorrow " +
                        "so the future version of you starts with less friction."
        }
    }

    fun suggestions(repo: Repository): List<String> {
        val out = ArrayList<String>()
        val date = repo.clock.today()
        val checkIns = repo.checkInsFor(
            com.superflow.core.time.SfTime.format(date)).associateBy { it.habitId }
        val open = repo.habitsForDay(date).filter { checkIns[it.id] == null }
        if (open.isNotEmpty()) out.add("Done ${open.first().title}")
        out.add("How am I doing?")
        if (repo.focusFor(com.superflow.core.time.SfTime.format(date)).isEmpty())
            out.add("Plan tomorrow")
        out.add("Minimum mode")
        out.add("List habits")
        return out.take(5)
    }

    private fun helpText(): String = """
        I can run anything the buttons can, because we share the same commands.

        Try:
        · "done meditation" or "tiny walk"
        · "skip reading" / "missed journaling because I was busy"
        · "focus on write, walk, call mum"
        · "plan tomorrow" / "minimum mode"
        · "energy 2" / "today's load"
        · "create habit walk 10 minutes at 07:30 daily"
        · "if it rains, then stretch indoors"
        · "carry walk to tomorrow" / "star the dentist call"
        · "rate the reward for walk 4"
        · "i am now someone who trains daily"
        · "how am I doing" / "list habits"

        With a Cloud Main Brain configured I can also handle open-ended requests
        and build a whole workspace from your documents in Blueprint Studio.
    """.trimIndent()

    /* -------------------------------------------------------------- parsing */

    private fun matches(s: String, vararg phrases: String): Boolean =
        phrases.any { s == it || s.startsWith("$it ") || s.contains(it) }

    private fun startsAny(s: String, vararg prefixes: String): Boolean =
        prefixes.any { s.startsWith(it) }

    private fun stripLeading(raw: String, prefixes: List<String>): String {
        var out = raw.trim()
        for (p in prefixes.sortedByDescending { it.length }) {
            if (out.lowercase().startsWith(p.lowercase())) {
                out = out.substring(p.length).trim()
                break
            }
        }
        return out.trim().trim(':', '-', ',').trim()
    }

    /** "walk 10 minutes at 07:30 in the park daily" -> create_habit args. */
    fun parseHabitPhrase(phrase: String): JSONObject? {
        var text = phrase.trim()
        if (text.isBlank()) return null

        var cueTime = ""
        Regex("\\bat (\\d{1,2}[:.]\\d{2})\\b").find(text)?.let {
            cueTime = it.groupValues[1].replace('.', ':')
            if (cueTime.length == 4) cueTime = "0$cueTime"
            text = text.replace(it.value, "").trim()
        }
        if (cueTime.isBlank()) {
            Regex("\\bat (\\d{1,2})\\s?(am|pm)\\b", RegexOption.IGNORE_CASE).find(text)?.let {
                var hour = it.groupValues[1].toInt()
                if (it.groupValues[2].lowercase() == "pm" && hour < 12) hour += 12
                if (it.groupValues[2].lowercase() == "am" && hour == 12) hour = 0
                cueTime = String.format("%02d:00", hour)
                text = text.replace(it.value, "").trim()
            }
        }

        var days = ""
        for (word in listOf("every day", "everyday", "daily", "weekdays", "weekends")) {
            if (text.lowercase().contains(word)) {
                days = if (word == "every day" || word == "everyday") "daily" else word
                text = Regex(word, RegexOption.IGNORE_CASE).replace(text, "").trim()
            }
        }
        Regex("\\bon ((?:mon|tue|wed|thu|fri|sat|sun)[a-z]*(?:\\s*,?\\s*(?:and\\s*)?(?:mon|tue|wed|thu|fri|sat|sun)[a-z]*)*)",
            RegexOption.IGNORE_CASE).find(text)?.let {
            days = it.groupValues[1]
            text = text.replace(it.value, "").trim()
        }

        var place = ""
        Regex("\\bin (?:the )?([a-z ]{3,20})$", RegexOption.IGNORE_CASE).find(text.trim())?.let {
            place = it.groupValues[1].trim()
            text = text.replace(it.value, "").trim()
        }

        var anchor = ""
        Regex("\\bafter ([a-z ]{3,30})", RegexOption.IGNORE_CASE).find(text)?.let {
            anchor = it.groupValues[1].trim()
            text = text.replace(it.value, "").trim()
        }

        val title = text.trim().trim(',', '.', '-').ifBlank { return null }
        if (title.length > 70) return null

        val tiny = defaultTinyStart(title)

        return jsonOf(
            "title" to title.replaceFirstChar { it.uppercase() },
            "tinyStart" to tiny,
            "cueTime" to cueTime,
            "cuePlace" to place,
            "anchorText" to anchor,
            "days" to days,
            "standardVersion" to title.replaceFirstChar { it.uppercase() }
        )
    }

    /** Every habit needs a two-minute version; derive a sensible default. */
    fun defaultTinyStart(title: String): String {
        val t = title.lowercase()
        return when {
            t.startsWith("read") || t.contains("book") -> "Open the book and read one page"
            t.contains("walk") -> "Put on your shoes and step outside"
            t.contains("run") || t.contains("jog") -> "Put on your running shoes"
            t.contains("meditat") || t.contains("breath") -> "Sit down and take three breaths"
            t.contains("write") || t.contains("journal") -> "Write one sentence"
            t.contains("water") || t.contains("hydrat") -> "Fill the glass"
            t.contains("stretch") || t.contains("yoga") -> "Do one stretch"
            t.contains("gym") || t.contains("workout") || t.contains("exercis") -> "Pack the bag"
            t.contains("study") || t.contains("learn") -> "Open the material and read one line"
            t.contains("tidy") || t.contains("clean") -> "Put one thing away"
            t.contains("call") || t.contains("message") -> "Open the contact"
            t.contains("guitar") || t.contains("piano") || t.contains("practice") -> "Pick up the instrument"
            else -> "Start for two minutes"
        }
    }

    fun toolCatalog(): String = Capabilities.all().joinToString("\n") { c ->
        "· ${c.name}(${c.args.joinToString(", ") { "${it.first}: ${it.second}" }}) — ${c.summary}" +
                if (c.destructive) " [destructive]" else ""
    }
}
