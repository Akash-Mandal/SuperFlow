import com.superflow.data.model.*
import com.superflow.core.time.SfTime
import java.time.LocalDate

var pass = 0
var fail = 0
fun check(name: String, cond: Boolean) {
    if (cond) { pass++; println("  ok   $name") } else { fail++; println("  FAIL $name") }
}
fun eq(name: String, a: Any?, b: Any?) = check("$name  ($a == $b)", a == b)

fun main() {
    println("Ladder and contract focus (dates and scheduling live in CoreTest)")

    println("Habit ladder")
    val h = Habit(title = "Walk", tinyStart = "Put on shoes", minimumVersion = "Walk to corner",
        standardVersion = "Walk 10 minutes", stretchVersion = "Walk 25 minutes")
    eq("tiny", h.levelText(Level.TINY), "Put on shoes")
    eq("minimum", h.levelText(Level.MINIMUM), "Walk to corner")
    eq("standard", h.levelText(Level.STANDARD), "Walk 10 minutes")
    eq("stretch", h.levelText(Level.STRETCH), "Walk 25 minutes")
    val sparse = Habit(title = "Read")
    eq("fallback tiny -> title", sparse.levelText(Level.TINY), "Read")
    eq("fallback minimum -> title", sparse.levelText(Level.MINIMUM), "Read")
    val partial = Habit(title = "Read", tinyStart = "One page")
    eq("minimum falls back to tiny", partial.levelText(Level.MINIMUM), "One page")

    println("Contract")
    val c1 = Habit(title = "Walk", standardVersion = "Walk 10 minutes", tinyStart = "Put on shoes",
        anchorText = "breakfast", environmentPrep = "Leave shoes by the door",
        reward = "Enjoy my coffee").contract()
    check("contract has anchor", c1.startsWith("After breakfast"))
    check("contract has standard", c1.contains("Walk 10 minutes"))
    check("contract has tiny", c1.contains("Put on shoes"))
    check("contract has prep", c1.contains("Leave shoes by the door"))
    check("contract has reward", c1.contains("Enjoy my coffee"))
    val c2 = Habit(title = "Meditate", cueTime = "07:00", cuePlace = "the study").contract()
    check("contract time+place", c2.startsWith("At 07:00 in the study"))
    val c3 = Habit(title = "Journal").contract()
    check("contract bare", c3.startsWith("Today, I will Journal"))

    println("Level weights")
    check("tiny < standard", Level.TINY.weight < Level.STANDARD.weight)
    check("stretch > standard", Level.STRETCH.weight > Level.STANDARD.weight)

    println("LifeArea parsing")
    eq("from exact", LifeArea.from("HEALTH"), LifeArea.HEALTH)
    eq("from lowercase", LifeArea.from("health"), LifeArea.HEALTH)
    eq("from junk", LifeArea.from("zzz"), LifeArea.CUSTOM)
    eq("from null", LifeArea.from(null), LifeArea.CUSTOM)
    eq("Level.from junk", Level.from("zzz"), Level.STANDARD)


    println("Journey mapper turns four tables into one tree")
    run {
        val id1 = com.superflow.data.model.Identity(id = "i1", statement = "Someone who moves")
        val g1 = com.superflow.data.model.Goal(id = "g1", identityId = "i1", title = "Walk 5km", why = "Keep up with my kids")
        val s1 = com.superflow.data.model.Sys(id = "s1", goalId = "g1", title = "Morning loop")
        val h1 = com.superflow.data.model.Habit(id = "h1", systemId = "s1", title = "Walk 10 minutes")

        val nodes = com.superflow.domain.JourneyMapper.nodes(
            listOf(id1), listOf(g1), listOf(s1), listOf(h1)
        )
        eq("four nodes", nodes.size, 4)
        eq("kinds in hierarchy order", nodes.map { it.kind },
            com.superflow.design.JourneyTree.Kind.ordered)
        eq("identity has no parent", nodes[0].parentId, null)
        eq("goal points at the identity", nodes[1].parentId, "i1")
        eq("system points at the goal", nodes[2].parentId, "g1")
        eq("habit points at the system", nodes[3].parentId, "s1")
        eq("identity title is the statement", nodes[0].title, "Someone who moves")

        val tree = com.superflow.design.JourneyTree.build(nodes)
        eq("the chain is complete", tree.summary.deepestChain, 4)
        eq("nothing is unlinked", tree.summary.unlinked, 0)
    }
    println()

    println("Journey mapper: a habit linked only to an identity stays honest")
    run {
        // The designer allows a habit to name an identity and skip the
        // system layer. Reparenting it under the identity would draw a
        // hierarchy the user did not build.
        val id1 = com.superflow.data.model.Identity(id = "i1", statement = "Someone who reads")
        val h = com.superflow.data.model.Habit(id = "h1", identityId = "i1", systemId = null, title = "Read")
        val nodes = com.superflow.domain.JourneyMapper.nodes(listOf(id1), emptyList(), emptyList(), listOf(h))
        eq("habit has no parent", nodes.last().parentId, null)
        val tree = com.superflow.design.JourneyTree.build(nodes)
        eq("reported as unlinked", tree.summary.unlinked, 1)
        check("still on the screen", tree.rows.any { it.node.id == "h1" })
    }
    println()

    println("Journey mapper: status maps to activity, not to visibility")
    run {
        val active = com.superflow.data.model.Habit(id = "a", title = "A", status = Status.ACTIVE)
        val paused = com.superflow.data.model.Habit(id = "p", title = "P", status = Status.PAUSED)
        val gone = com.superflow.data.model.Habit(id = "z", title = "Z", status = Status.ARCHIVED)
        val nodes = com.superflow.domain.JourneyMapper.nodes(
            emptyList(), emptyList(), emptyList(), listOf(active, paused, gone)
        )
        eq("all three kept", nodes.size, 3)
        eq("active is active", nodes[0].active, true)
        eq("paused is not", nodes[1].active, false)
        eq("archived is flagged", nodes[2].archived, true)
        eq("archived is not active", nodes[2].active, false)

        val trimmed = com.superflow.domain.JourneyMapper.nodes(
            emptyList(), emptyList(), emptyList(), listOf(active, paused, gone), includeArchived = false
        )
        eq("archived dropped on request", trimmed.size, 2)
        check("paused survives the drop", trimmed.any { it.id == "p" })

        // Maintaining a goal is work; it must not read as dormant.
        val maintaining = com.superflow.data.model.Goal(id = "g", title = "G", status = GoalStatus.MAINTAINING)
        val closed = com.superflow.data.model.Goal(id = "c", title = "C", status = GoalStatus.CLOSED)
        val achieved = com.superflow.data.model.Goal(id = "d", title = "D", status = GoalStatus.ACHIEVED)
        val gn = com.superflow.domain.JourneyMapper.nodes(
            emptyList(), listOf(maintaining, closed, achieved), emptyList(), emptyList()
        )
        eq("maintaining counts as active", gn[0].active, true)
        eq("closed does not", gn[1].active, false)
        eq("closed is archived", gn[1].archived, true)
        eq("achieved is inactive but not archived", gn[2].active to gn[2].archived, false to false)
    }
    println()

    println("Journey mapper detail lines")
    run {
        val short = com.superflow.data.model.Goal(id = "g", title = "T", why = "Short reason")
        eq("why is appended", com.superflow.domain.JourneyMapper.defaultGoalDetail(short),
            "active \u00b7 Short reason")
        val bare = com.superflow.data.model.Goal(id = "g", title = "T")
        eq("no why, no separator", com.superflow.domain.JourneyMapper.defaultGoalDetail(bare), "active")
        val long = com.superflow.data.model.Goal(id = "g", title = "T", why = "x".repeat(200))
        val detail = com.superflow.domain.JourneyMapper.defaultGoalDetail(long)
        check("long why is truncated", detail.length < 100)
        check("truncation is visible", detail.endsWith("\u2026"))
        val exact = com.superflow.data.model.Goal(id = "g", title = "T",
            why = "x".repeat(com.superflow.domain.JourneyMapper.WHY_PREVIEW))
        check("an exactly-fitting why is not marked truncated",
            !com.superflow.domain.JourneyMapper.defaultGoalDetail(exact).endsWith("\u2026"))

        val habit = com.superflow.data.model.Habit(id = "h", title = "Walk", cueTime = "07:00")
        val full = com.superflow.domain.JourneyMapper.habitDetail(habit, "Daily", 12, 80, true)
        eq("full detail", full, "Daily \u00b7 07:00 \u00b7 12 reps \u00b7 80%")
        val thin = com.superflow.domain.JourneyMapper.habitDetail(habit, "Daily", 2, 80, false)
        check("percentage withheld below the threshold", !thin.contains("%"))
        check("reps still shown", thin.contains("2 reps"))
        eq("one rep is singular",
            com.superflow.domain.JourneyMapper.habitDetail(habit, "Daily", 1, 0, false),
            "Daily \u00b7 07:00 \u00b7 1 rep")
        val fresh = com.superflow.data.model.Habit(id = "h", title = "Walk")
        eq("a brand new habit says only its schedule",
            com.superflow.domain.JourneyMapper.habitDetail(fresh, "Weekdays", 0, 0, false), "Weekdays")
        val reduce = com.superflow.data.model.Habit(id = "h", title = "Scroll less", mode = HabitMode.REDUCE)
        check("reducing habits say so",
            com.superflow.domain.JourneyMapper.habitDetail(reduce, "Daily", 0, 0, false).contains("reducing"))
    }
    println()

    println("Journey mapper preserves every entity")
    run {
        // Same invariant as the tree, checked at the boundary where the
        // repository's four lists become one: nothing gets dropped in the
        // translation, whatever state it is in.
        val ids = (1..3).map { com.superflow.data.model.Identity(id = "i$it", statement = "I$it") }
        val goals = (1..4).map { com.superflow.data.model.Goal(id = "g$it", title = "G$it", identityId = if (it > 2) null else "i1") }
        val syss = (1..2).map { com.superflow.data.model.Sys(id = "s$it", title = "S$it", goalId = "g$it") }
        val habits = (1..5).map { com.superflow.data.model.Habit(id = "h$it", title = "H$it", systemId = if (it > 3) null else "s1") }
        val nodes = com.superflow.domain.JourneyMapper.nodes(ids, goals, syss, habits)
        eq("14 in, 14 out", nodes.size, 14)
        val open = nodes.map { com.superflow.design.JourneyTree.expansionKey(it.kind, it.id) }.toSet()
        val tree = com.superflow.design.JourneyTree.build(nodes, open)
        eq("14 rows drawn", tree.rows.size, 14)
        eq("unlinked counted", tree.summary.unlinked, 2 + 2)
        eq("active habits", tree.summary.activeHabits, 5)
    }

    println("Studio mapper correlates the transcript with the audit log")
    run {
        val M = com.superflow.domain.StudioMapper
        val ask = AiMessage(id = "m1", role = "user", text = "Add a walking habit", createdAt = 1_000L)
        val reply = AiMessage(id = "m2", role = "assistant", text = "Added it.", meta = "local", createdAt = 2_000L)
        val entry = AuditEntry(id = "a1", actor = "AI", command = "habit.create",
            summary = "Walk", groupId = "g1", createdAt = 2_050L)

        val turns = M.turns(listOf(ask, reply), listOf(entry))
        eq("both messages survive", turns.size, 2)
        eq("user turn", turns[0].speaker, com.superflow.design.StudioModel.Speaker.USER)
        eq("assistant turn", turns[1].speaker, com.superflow.design.StudioModel.Speaker.ASSISTANT)
        eq("the change is attributed", turns[1].groupId, "g1")
        eq("and named", turns[1].actions, listOf("habit.create"))
        eq("done", turns[1].state, com.superflow.design.StudioModel.RunState.DONE)
        check("so it can be undone", turns[1].undoable)
        check("undo is offered",
            M.turns(listOf(ask, reply), listOf(entry)).last().let {
                com.superflow.design.StudioModel.actionsFor(it)
                    .contains(com.superflow.design.StudioModel.MessageAction.UNDO)
            })

        // The window is the only link between the two tables, so a change
        // made a minute later must not be blamed on this reply.
        val late = entry.copy(id = "a2", createdAt = 2_000L + M.ATTRIBUTION_WINDOW_MS + 1)
        eq("a late change is not attributed", M.turns(listOf(reply), listOf(late)).first().groupId, null)
        eq("and the turn stays quiet", M.turns(listOf(reply), listOf(late)).first().state,
            com.superflow.design.StudioModel.RunState.NONE)

        // Two replies close together each keep their own work.
        val reply2 = AiMessage(id = "m3", role = "assistant", text = "And that.", meta = "local", createdAt = 3_000L)
        val entry2 = AuditEntry(id = "a3", actor = "AI", command = "goal.create",
            summary = "5km", groupId = "g2", createdAt = 3_020L)
        val two = M.turns(listOf(reply, reply2), listOf(entry, entry2))
        eq("first reply keeps its group", two[0].groupId, "g1")
        eq("second reply keeps its own", two[1].groupId, "g2")
        eq("no cross-contamination", two[0].actions, listOf("habit.create"))

        eq("a fully undone group reads as undone",
            M.stateOf(listOf(entry.copy(undone = true)), false),
            com.superflow.design.StudioModel.RunState.UNDONE)
        eq("a partly undone group is still done",
            M.stateOf(listOf(entry.copy(undone = true), entry.copy(id = "a4")), false),
            com.superflow.design.StudioModel.RunState.DONE)
        eq("in flight beats everything",
            M.stateOf(listOf(entry), true), com.superflow.design.StudioModel.RunState.RUNNING)
        eq("an ungrouped entry cannot be attributed",
            M.turns(listOf(reply), listOf(entry.copy(groupId = null))).first().groupId, null)

        eq("unknown roles are system", M.speakerOf("tool"), com.superflow.design.StudioModel.Speaker.SYSTEM)
        eq("roles are case-insensitive", M.speakerOf("USER"), com.superflow.design.StudioModel.Speaker.USER)
        check("a fallback says why", M.routeLabel("local-fallback").contains("cloud unavailable"))
        eq("an empty route stays empty", M.routeLabel(""), "")
        eq("an unknown route passes through", M.routeLabel("ollama"), "ollama")
    }
    println()

    println("Studio status and project cards")
    run {
        val M = com.superflow.domain.StudioMapper
        val guided = M.status(false, false, true, "OpenAI \u00b7 gpt-4o", 42)
        eq("guided title", guided.title, "Guided mode")
        eq("guided invites activation", guided.actionLabel, "Activate")
        check("guided is not active", !guided.active)
        check("the engine is named", guided.detail.contains("OpenAI"))
        check("capabilities are counted", guided.detail.contains("42"))
        val local = M.status(true, true, true, "OpenAI", 42)
        check("local-only overrides a configured cloud", local.detail.startsWith("Local coordinator only"))
        val bare = M.status(false, false, false, "", 3)
        check("no cloud says so", bare.detail.contains("no cloud configured"))

        val p = BlueprintProject(id = "p1", name = "Marathon", version = 2)
        val reqs = listOf(
            Requirement(projectId = "p1", text = "a", sourceId = null, status = RequirementStatus.IMPLEMENTED),
            Requirement(projectId = "p1", text = "b", sourceId = null, status = RequirementStatus.VERIFIED),
            Requirement(projectId = "p1", text = "c", sourceId = null, status = RequirementStatus.ACCEPTED),
            Requirement(projectId = "p1", text = "d", sourceId = null, status = RequirementStatus.GAP),
        )
        val cards = M.projects(listOf(p)) { reqs }
        eq("one card", cards.size, 1)
        eq("named", cards[0].name, "Marathon")
        eq("progress", cards[0].progress, 50)
        check("the detail counts", cards[0].detail.contains("2 of 4"))
        check("and versions", cards[0].detail.contains("v2"))
        eq("archived projects do not surface",
            M.projects(listOf(p.copy(state = "ARCHIVED"))) { reqs }.size, 0)
        eq("the transcript is not a project list",
            M.projects((1..6).map { p.copy(id = "p$it") }) { emptyList() }.size, M.MAX_PROJECT_CARDS)
        check("an empty project says so",
            M.projects(listOf(p)) { emptyList() }[0].detail.contains("no requirements"))

        eq("nothing is 0%", M.percent(0, 4), 0)
        eq("everything is 100%", M.percent(4, 4), 100)
        eq("nothing of nothing is 0%", M.percent(0, 0), 0)
        eq("almost done never rounds to 100", M.percent(199, 200), 99)
        eq("barely started never rounds to 0", M.percent(1, 200), 1)
    }
    println()
    println()
    println("passed=$pass failed=$fail")
    if (fail > 0) kotlin.system.exitProcess(1)
}
