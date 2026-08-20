package com.superflow.domain

import com.superflow.core.schedule.Recurrence
import com.superflow.core.time.SfTime
import com.superflow.data.model.*
import com.superflow.util.jsonOf
import com.superflow.util.string
import org.json.JSONArray
import org.json.JSONObject

/**
 * The versioned capability catalogue.
 *
 * Every meaningful operation lives here exactly once. The manual screens call
 * these commands and the AI tool registry exposes the same list, which is what
 * makes manual/AI parity structural rather than aspirational.
 */
object Capabilities {

    const val CATALOG_VERSION = 4  // Phase 1-5: growth engine, templates, journal, routines, memory, what-if, accountability, milestones, sprints, settings, analysis, coaching, blueprint V2, environment, simulation, notification

    fun all(): List<Capability> = buildList {
        addAll(identityCaps())
        addAll(habitCaps())
        addAll(checkInCaps())
        addAll(focusCaps())
        addAll(designCaps())
        addAll(pauseCaps())
        addAll(reviewCaps())
        addAll(queryCaps())
        addAll(dataCaps())
        addAll(growthCaps())
        addAll(templateCaps())
        addAll(journalCaps())
        addAll(routineCaps())
        addAll(memoryCaps())
        addAll(whatIfCaps())
        addAll(accountabilityCaps())
        addAll(milestoneCaps())
        addAll(sprintCaps())
        addAll(settingCaps())
        addAll(analysisCaps())
        addAll(coachingCaps())
        addAll(blueprintCaps())
        addAll(environmentCaps())
        addAll(simulationCaps())
        addAll(notificationCaps())
    }

    /* -------------------------------------------------------- identity layer */

    private fun identityCaps() = listOf(
        Capability(
            "create_identity", "Create an identity statement",
            listOf("statement" to "string", "lifeArea" to "string"), Risk.LOW
        ) { c ->
            val statement = c.str("statement").trim()
            if (statement.isBlank()) return@Capability CommandResult.fail("An identity statement is required")
            val i = Identity(statement = statement, lifeArea = LifeArea.from(c.str("lifeArea")))
            c.repo.saveIdentity(i)
            val id = c.bus.record(c.actor, "create_identity", "Created identity \"$statement\"",
                Serial.of(i), undoDelete("identity", i.id), c.groupId)
            okResult("Identity created", jsonOf("id" to i.id), id)
        },

        Capability(
            "update_identity", "Edit an identity statement",
            listOf("id" to "string", "statement" to "string", "lifeArea" to "string",
                "status" to "string"), Risk.LOW
        ) { c ->
            val old = c.repo.identity(c.str("id")) ?: return@Capability CommandResult.fail("Identity not found")
            val updated = old.copy(
                statement = c.str("statement", old.statement),
                lifeArea = if (c.str("lifeArea").isBlank()) old.lifeArea else LifeArea.from(c.str("lifeArea")),
                status = if (c.str("status").isBlank()) old.status else Status.valueOf(c.str("status").uppercase())
            )
            c.repo.saveIdentity(updated)
            val id = c.bus.record(c.actor, "update_identity", "Updated identity \"${updated.statement}\"",
                Serial.of(updated), undoRestore("identity", Serial.of(old)), c.groupId)
            okResult("Identity updated", jsonOf("id" to updated.id), id)
        },

        Capability(
            "delete_identity", "Delete an identity", listOf("id" to "string"),
            Risk.HIGH, destructive = true
        ) { c ->
            val old = c.repo.identity(c.str("id")) ?: return@Capability CommandResult.fail("Identity not found")
            c.repo.deleteIdentity(old.id)
            val id = c.bus.record(c.actor, "delete_identity", "Deleted identity \"${old.statement}\"",
                Serial.of(old), undoRestore("identity", Serial.of(old)), c.groupId)
            okResult("Identity deleted", null, id)
        },

        Capability(
            "create_goal", "Create a goal linked to an identity",
            listOf("title" to "string", "why" to "string", "identityId" to "string",
                "outcomeMetric" to "string"), Risk.LOW
        ) { c ->
            val title = c.str("title").trim()
            if (title.isBlank()) return@Capability CommandResult.fail("A goal title is required")
            val g = Goal(
                identityId = c.strOrNull("identityId") ?: c.repo.identities().firstOrNull()?.id,
                title = title, why = c.str("why"), outcomeMetric = c.str("outcomeMetric")
            )
            c.repo.saveGoal(g)
            val id = c.bus.record(c.actor, "create_goal", "Created goal \"$title\"",
                Serial.of(g), undoDelete("goal", g.id), c.groupId)
            okResult("Goal created", jsonOf("id" to g.id), id)
        },

        Capability(
            "update_goal", "Edit a goal",
            listOf("id" to "string", "title" to "string", "why" to "string", "status" to "string"),
            Risk.LOW
        ) { c ->
            val old = c.repo.goal(c.str("id")) ?: return@Capability CommandResult.fail("Goal not found")
            val updated = old.copy(
                title = c.str("title", old.title), why = c.str("why", old.why),
                outcomeMetric = c.str("outcomeMetric", old.outcomeMetric),
                status = if (c.str("status").isBlank()) old.status
                else GoalStatus.valueOf(c.str("status").uppercase())
            )
            c.repo.saveGoal(updated)
            val id = c.bus.record(c.actor, "update_goal", "Updated goal \"${updated.title}\"",
                Serial.of(updated), undoRestore("goal", Serial.of(old)), c.groupId)
            okResult("Goal updated", jsonOf("id" to updated.id), id)
        },

        Capability("delete_goal", "Delete a goal", listOf("id" to "string"),
            Risk.HIGH, destructive = true) { c ->
            val old = c.repo.goal(c.str("id")) ?: return@Capability CommandResult.fail("Goal not found")
            c.repo.deleteGoal(old.id)
            val id = c.bus.record(c.actor, "delete_goal", "Deleted goal \"${old.title}\"",
                Serial.of(old), undoRestore("goal", Serial.of(old)), c.groupId)
            okResult("Goal deleted", null, id)
        },

        Capability(
            "create_system", "Create a repeatable system for a goal",
            listOf("title" to "string", "goalId" to "string", "description" to "string"), Risk.LOW
        ) { c ->
            val title = c.str("title").trim()
            if (title.isBlank()) return@Capability CommandResult.fail("A system title is required")
            val s = Sys(
                goalId = c.strOrNull("goalId") ?: c.repo.goals().firstOrNull()?.id,
                title = title, description = c.str("description")
            )
            c.repo.saveSystem(s)
            val id = c.bus.record(c.actor, "create_system", "Created system \"$title\"",
                Serial.of(s), undoDelete("sys", s.id), c.groupId)
            okResult("System created", jsonOf("id" to s.id), id)
        },

        Capability(
            "update_system", "Edit a system",
            listOf("id" to "string", "title" to "string", "description" to "string"), Risk.LOW
        ) { c ->
            val old = c.repo.system(c.str("id")) ?: return@Capability CommandResult.fail("System not found")
            val updated = old.copy(
                title = c.str("title", old.title), description = c.str("description", old.description),
                status = if (c.str("status").isBlank()) old.status
                else Status.valueOf(c.str("status").uppercase())
            )
            c.repo.saveSystem(updated)
            val id = c.bus.record(c.actor, "update_system", "Updated system \"${updated.title}\"",
                Serial.of(updated), undoRestore("sys", Serial.of(old)), c.groupId)
            okResult("System updated", jsonOf("id" to updated.id), id)
        },

        Capability("delete_system", "Delete a system", listOf("id" to "string"),
            Risk.HIGH, destructive = true) { c ->
            val old = c.repo.system(c.str("id")) ?: return@Capability CommandResult.fail("System not found")
            c.repo.deleteSystem(old.id)
            val id = c.bus.record(c.actor, "delete_system", "Deleted system \"${old.title}\"",
                Serial.of(old), undoRestore("sys", Serial.of(old)), c.groupId)
            okResult("System deleted", null, id)
        }
    )

    /* ----------------------------------------------------------- habit layer */

    private fun habitCaps() = listOf(
        Capability(
            "create_habit", "Create a habit with its Tiny Start and cue",
            listOf(
                "title" to "string", "tinyStart" to "string", "cueTime" to "HH:mm",
                "cuePlace" to "string", "anchorText" to "string", "mode" to "BUILD|REDUCE",
                "trackType" to "BINARY|COUNT|DURATION", "targetCount" to "int", "unit" to "string",
                "systemId" to "string", "identityId" to "string",
                "days" to "daily|weekdays|weekends|mon,tue", "reminder" to "bool",
                "protected" to "bool"
            ), Risk.LOW
        ) { c ->
            val title = c.str("title").trim()
            if (title.isBlank()) return@Capability CommandResult.fail("A habit title is required")
            val cueTime = c.str("cueTime").trim()
            if (cueTime.isNotBlank() && !SfTime.isValidTime(cueTime))
                return@Capability CommandResult.fail("Cue time must look like 07:30")
            val existing = c.repo.habits(true)
            val h = Habit(
                systemId = c.strOrNull("systemId") ?: c.repo.systems().firstOrNull()?.id,
                identityId = c.strOrNull("identityId") ?: c.repo.identities().firstOrNull()?.id,
                title = title,
                mode = runCatching { HabitMode.valueOf(c.str("mode", "BUILD").uppercase()) }
                    .getOrDefault(HabitMode.BUILD),
                trackType = runCatching { TrackType.valueOf(c.str("trackType", "BINARY").uppercase()) }
                    .getOrDefault(TrackType.BINARY),
                targetCount = c.int("targetCount", 1).coerceAtLeast(1),
                unit = c.str("unit"),
                cueTime = cueTime, cuePlace = c.str("cuePlace"), anchorText = c.str("anchorText"),
                benefit = c.str("benefit"), temptationBundle = c.str("temptationBundle"),
                reframe = c.str("reframe"),
                tinyStart = c.str("tinyStart"), minimumVersion = c.str("minimumVersion"),
                standardVersion = c.str("standardVersion", title),
                stretchVersion = c.str("stretchVersion"),
                frictionPlan = c.str("frictionPlan"), environmentPrep = c.str("environmentPrep"),
                reward = c.str("reward"), recoveryPlan = c.str("recoveryPlan"),
                recurrenceRule = Recurrence.parse(c.str("days")).encode(),
                startDate = SfTime.format(c.repo.clock.today()),
                reminderEnabled = c.bool("reminder", false),
                protectedRoutine = c.bool("protected", false),
                colorSeed = c.int("colorSeed", existing.size % 6),
                orderIndex = existing.size
            )
            c.repo.saveHabit(h)
            val id = c.bus.record(c.actor, "create_habit", "Created habit \"$title\"",
                Serial.of(h), undoDelete("habit", h.id), c.groupId)
            okResult("Habit created", jsonOf("id" to h.id, "contract" to h.contract()), id)
        },

        Capability(
            "update_habit", "Edit any part of a habit's design",
            listOf("habit" to "id or title", "field" to "string", "value" to "string"), Risk.LOW
        ) { c ->
            val old = resolveHabit(c) ?: return@Capability CommandResult.fail("Habit not found")
            var updated = old
            val field = c.str("field").trim()
            if (field.isNotBlank()) {
                updated = applyField(updated, field, c.str("value"))
                    ?: return@Capability CommandResult.fail("Unknown habit field: $field")
            }
            for ((k, _) in habitFields) {
                if (!c.args.isNull(k) && k != "field" && k != "value") {
                    updated = applyField(updated, k, c.args.string(k)) ?: updated
                }
            }
            if (!c.args.isNull("days")) {
                // A schedule edit bumps the version; history is never rewritten.
                updated = updated.copy(
                    recurrenceRule = Recurrence.parse(c.str("days")).encode(),
                    scheduleVersion = updated.scheduleVersion + 1
                )
            }
            if (updated.cueTime.isNotBlank() && !SfTime.isValidTime(updated.cueTime))
                return@Capability CommandResult.fail("Cue time must look like 07:30")
            c.repo.saveHabit(updated)
            val id = c.bus.record(c.actor, "update_habit", "Updated habit \"${updated.title}\"",
                Serial.of(updated), undoRestore("habit", Serial.of(old)), c.groupId)
            okResult("Habit updated", jsonOf("id" to updated.id, "contract" to updated.contract()), id)
        },

        Capability("archive_habit", "Archive a habit without losing its history",
            listOf("habit" to "id or title"), Risk.MEDIUM) { c ->
            val old = resolveHabit(c) ?: return@Capability CommandResult.fail("Habit not found")
            val updated = old.copy(status = Status.ARCHIVED)
            c.repo.saveHabit(updated)
            val id = c.bus.record(c.actor, "archive_habit", "Archived habit \"${old.title}\"",
                Serial.of(updated), undoRestore("habit", Serial.of(old)), c.groupId)
            okResult("Habit archived", null, id)
        },

        Capability("restore_habit", "Return an archived habit to active",
            listOf("habit" to "id or title"), Risk.LOW) { c ->
            val old = resolveHabit(c) ?: return@Capability CommandResult.fail("Habit not found")
            val updated = old.copy(status = Status.ACTIVE)
            c.repo.saveHabit(updated)
            val id = c.bus.record(c.actor, "restore_habit", "Restored habit \"${old.title}\"",
                Serial.of(updated), undoRestore("habit", Serial.of(old)), c.groupId)
            okResult("Habit restored", null, id)
        },

        Capability("delete_habit", "Permanently delete a habit and its check-ins",
            listOf("habit" to "id or title"), Risk.HIGH, destructive = true) { c ->
            val old = resolveHabit(c) ?: return@Capability CommandResult.fail("Habit not found")
            val rows = JSONArray()
            rows.put(Serial.of(old))
            c.repo.checkInsOf(old.id).forEach { rows.put(Serial.of(it)) }
            c.repo.obstacles(old.id).forEach { rows.put(Serial.of(it)) }
            c.repo.deleteHabit(old.id)
            val undo = jsonOf("kind" to "restoreRows", "table" to "habit", "rows" to rows)
            val id = c.bus.record(c.actor, "delete_habit", "Deleted habit \"${old.title}\"",
                Serial.of(old), undo, c.groupId)
            okResult("Habit deleted", null, id)
        },

        Capability("reorder_habit", "Move a habit up or down the Today timeline",
            listOf("habit" to "id or title", "direction" to "up|down", "toIndex" to "int"),
            Risk.LOW) { c ->
            val target = resolveHabit(c) ?: return@Capability CommandResult.fail("Habit not found")
            val list = c.repo.habits().sortedBy { it.orderIndex }.toMutableList()
            val idx = list.indexOfFirst { it.id == target.id }
            if (idx < 0) return@Capability CommandResult.fail("Habit not found")
            val to = if (!c.args.isNull("toIndex")) c.int("toIndex", idx)
            else if (c.str("direction", "up").lowercase() == "down") idx + 1 else idx - 1
            if (to < 0 || to >= list.size) return@Capability CommandResult.fail("Already at the edge")
            val prevOrder = list.map { it.id to it.orderIndex }
            val moved = list.removeAt(idx)
            list.add(to, moved)
            list.forEachIndexed { i, h -> c.repo.saveHabit(h.copy(orderIndex = i)) }
            val rows = JSONArray()
            prevOrder.forEach { (hid, order) ->
                c.repo.habit(hid)?.let { rows.put(Serial.of(it.copy(orderIndex = order))) }
            }
            val id = c.bus.record(c.actor, "reorder_habit", "Moved \"${target.title}\"", null,
                jsonOf("kind" to "restoreRows", "table" to "habit", "rows" to rows), c.groupId)
            okResult("Order updated", null, id)
        },

        Capability("add_obstacle_plan", "Add an if-then Obstacle Plan to a habit",
            listOf("habit" to "id or title", "ifText" to "string", "thenText" to "string"),
            Risk.LOW) { c ->
            val h = resolveHabit(c) ?: return@Capability CommandResult.fail("Habit not found")
            val ifText = c.str("ifText").trim()
            val thenText = c.str("thenText").trim()
            if (ifText.isBlank() || thenText.isBlank())
                return@Capability CommandResult.fail("Both the if and the then part are required")
            val o = ObstaclePlan(habitId = h.id, ifText = ifText, thenText = thenText)
            c.repo.saveObstacle(o)
            val id = c.bus.record(c.actor, "add_obstacle_plan", "Obstacle plan for \"${h.title}\"",
                Serial.of(o), undoDelete("obstacle", o.id), c.groupId)
            okResult("Obstacle plan added", jsonOf("id" to o.id), id)
        },

        Capability("delete_obstacle_plan", "Remove an Obstacle Plan", listOf("id" to "string"),
            Risk.MEDIUM, destructive = true) { c ->
            val old = c.repo.obstacles().firstOrNull { it.id == c.str("id") }
                ?: return@Capability CommandResult.fail("Obstacle plan not found")
            c.repo.deleteObstacle(old.id)
            val id = c.bus.record(c.actor, "delete_obstacle_plan", "Removed an obstacle plan",
                Serial.of(old), undoRestore("obstacle", Serial.of(old)), c.groupId)
            okResult("Obstacle plan removed", null, id)
        }
    )

    /* -------------------------------------------------------- check-in layer */

    private fun checkInCaps() = listOf(
        Capability("check_in", "Record a completion at Tiny, Minimum, Standard or Stretch",
            listOf("habit" to "id or title", "level" to "TINY|MINIMUM|STANDARD|STRETCH",
                "amount" to "number", "date" to "yyyy-MM-dd", "note" to "string"), Risk.LOW) { c ->
            val h = resolveHabit(c) ?: return@Capability CommandResult.fail("Habit not found")
            val date = c.date()
            val prev = c.repo.checkIn(h.id, date)
            val level = Level.from(c.str("level", "STANDARD"))
            val ci = CheckIn(
                habitId = h.id, date = date,
                result = if (h.mode == HabitMode.REDUCE) CheckInResult.RESISTED else CheckInResult.DONE,
                level = level, amount = c.dbl("amount", 0.0), note = c.str("note")
            )
            c.repo.saveCheckIn(ci)
            val undo = if (prev == null) jsonOf("kind" to "clearCheckIn", "habitId" to h.id, "date" to date)
            else undoRestore("checkin", Serial.of(prev))
            val id = c.bus.record(c.actor, "check_in",
                "${h.title}: ${level.label} on ${SfTime.shortDay(c.localDate())}", Serial.of(ci), undo, c.groupId)
            okResult("${level.label} · ${h.title}", jsonOf("id" to ci.id, "habitId" to h.id), id)
        },

        Capability("skip_habit", "Intentionally skip a habit today, without shame",
            listOf("habit" to "id or title", "date" to "yyyy-MM-dd", "note" to "string"),
            Risk.LOW) { c ->
            val h = resolveHabit(c) ?: return@Capability CommandResult.fail("Habit not found")
            val date = c.date()
            val prev = c.repo.checkIn(h.id, date)
            val ci = CheckIn(habitId = h.id, date = date, result = CheckInResult.SKIPPED,
                note = c.str("note"))
            c.repo.saveCheckIn(ci)
            val undo = if (prev == null) jsonOf("kind" to "clearCheckIn", "habitId" to h.id, "date" to date)
            else undoRestore("checkin", Serial.of(prev))
            val id = c.bus.record(c.actor, "skip_habit", "Skipped \"${h.title}\" intentionally",
                Serial.of(ci), undo, c.groupId)
            okResult("Skipped — that is a choice, not a failure", null, id)
        },

        Capability("mark_missed", "Record that an opportunity passed",
            listOf("habit" to "id or title", "date" to "yyyy-MM-dd"), Risk.LOW) { c ->
            val h = resolveHabit(c) ?: return@Capability CommandResult.fail("Habit not found")
            val date = c.date()
            val prev = c.repo.checkIn(h.id, date)
            val result = if (h.mode == HabitMode.REDUCE) CheckInResult.SLIPPED else CheckInResult.MISSED
            val ci = CheckIn(habitId = h.id, date = date, result = result, note = c.str("note"))
            c.repo.saveCheckIn(ci)
            val undo = if (prev == null) jsonOf("kind" to "clearCheckIn", "habitId" to h.id, "date" to date)
            else undoRestore("checkin", Serial.of(prev))
            val id = c.bus.record(c.actor, "mark_missed", "Recorded a miss for \"${h.title}\"",
                Serial.of(ci), undo, c.groupId)
            okResult("Recorded. One miss is data, not a verdict.", null, id)
        },

        Capability("clear_check_in", "Remove a check-in for a habit",
            listOf("habit" to "id or title", "date" to "yyyy-MM-dd"), Risk.MEDIUM) { c ->
            val h = resolveHabit(c) ?: return@Capability CommandResult.fail("Habit not found")
            val date = c.date()
            val prev = c.repo.checkIn(h.id, date)
                ?: return@Capability CommandResult.fail("No check-in to clear")
            c.repo.clearCheckIn(h.id, date)
            val id = c.bus.record(c.actor, "clear_check_in", "Cleared check-in for \"${h.title}\"",
                null, undoRestore("checkin", Serial.of(prev)), c.groupId)
            okResult("Check-in cleared", null, id)
        },

        Capability("log_energy", "Log energy at a checkpoint (1-5)",
            listOf("energy" to "1-5", "checkpoint" to "MORNING|MIDDAY|EVENING",
                "date" to "yyyy-MM-dd", "note" to "string"), Risk.LOW) { c ->
            val level = c.int("energy", 3).coerceIn(1, 5)
            val cp = runCatching { Checkpoint.valueOf(c.str("checkpoint", "MORNING").uppercase()) }
                .getOrDefault(Checkpoint.MORNING)
            val e = EnergyLog(date = c.date(), checkpoint = cp, energy = level, note = c.str("note"))
            c.repo.saveEnergy(e)
            val id = c.bus.record(c.actor, "log_energy", "${cp.label} energy: $level/5",
                Serial.of(e), null, c.groupId)
            okResult("Energy logged", null, id)
        },

        Capability("start_recovery", "Create a compassionate return plan after a miss",
            listOf("habit" to "id or title"), Risk.LOW) { c ->
            val h = resolveHabit(c) ?: return@Capability CommandResult.fail("Habit not found")
            val tiny = h.tinyStart.ifBlank { "the smallest version of ${h.title}" }
            val plan = h.recoveryPlan.ifBlank { "Return today with $tiny. Never miss twice." }
            val updated = h.copy(recoveryPlan = plan)
            c.repo.saveHabit(updated)
            val id = c.bus.record(c.actor, "start_recovery", "Recovery plan for \"${h.title}\"",
                Serial.of(updated), undoRestore("habit", Serial.of(h)), c.groupId)
            okResult(plan, jsonOf("plan" to plan), id)
        }
    )

    /* ----------------------------------------------------- daily focus layer */

    private fun focusCaps() = listOf(
        Capability("set_daily_focus", "Set up to three Daily Focus actions",
            listOf("items" to "array of strings", "date" to "yyyy-MM-dd"), Risk.MEDIUM) { c ->
            val date = c.date()
            val arr = c.args.optJSONArray("items")
            val titles = ArrayList<String>()
            if (arr != null) for (i in 0 until arr.length()) {
                val s = arr.optString(i, "").trim()
                if (s.isNotBlank()) titles.add(s)
            }
            if (titles.isEmpty()) {
                val single = c.str("title").trim()
                if (single.isNotBlank()) titles.add(single)
            }
            if (titles.isEmpty()) return@Capability CommandResult.fail("Give at least one focus action")
            if (titles.size > 3) return@Capability CommandResult.fail("Daily Focus holds at most three actions")
            val prev = c.repo.focusFor(date)
            val rows = JSONArray()
            prev.forEach { rows.put(Serial.of(it)) }
            c.repo.clearFocus(date)
            titles.forEachIndexed { i, t ->
                val habit = c.repo.findHabit(t)
                c.repo.saveFocus(FocusItem(date = date, habitId = habit?.id, title = t, orderIndex = i))
            }
            val undo = jsonOf("kind" to "restoreRows", "table" to "focus", "rows" to rows)
            val id = c.bus.record(c.actor, "set_daily_focus",
                "Daily Focus for ${SfTime.shortDay(c.localDate())}: ${titles.joinToString("; ")}", null, undo, c.groupId)
            okResult("Daily Focus set", null, id)
        },

        Capability("add_focus_item", "Add one action to the Daily Focus",
            listOf("title" to "string", "date" to "yyyy-MM-dd"), Risk.LOW) { c ->
            val date = c.date()
            val title = c.str("title").trim()
            if (title.isBlank()) return@Capability CommandResult.fail("A title is required")
            val existing = c.repo.focusFor(date)
            if (existing.size >= 3) return@Capability CommandResult.fail("Daily Focus already holds three actions")
            val habit = c.repo.findHabit(title)
            val f = FocusItem(date = date, habitId = habit?.id, title = title, orderIndex = existing.size)
            c.repo.saveFocus(f)
            val id = c.bus.record(c.actor, "add_focus_item", "Added focus \"$title\"",
                Serial.of(f), undoDelete("focus", f.id), c.groupId)
            okResult("Added to Daily Focus", jsonOf("id" to f.id), id)
        },

        Capability("complete_focus_item", "Tick off a Daily Focus action",
            listOf("id" to "string", "title" to "string", "done" to "bool"), Risk.LOW) { c ->
            val date = c.date()
            val items = c.repo.focusFor(date)
            val target = items.firstOrNull { it.id == c.str("id") }
                ?: items.firstOrNull { it.title.equals(c.str("title"), true) }
                ?: items.firstOrNull {
                    c.str("title").isNotBlank() &&
                            it.title.lowercase().contains(c.str("title").lowercase().trim())
                }
                ?: return@Capability CommandResult.fail("Focus action not found")
            val updated = target.copy(done = c.bool("done", true))
            c.repo.saveFocus(updated)
            val id = c.bus.record(c.actor, "complete_focus_item",
                "${if (updated.done) "Completed" else "Reopened"} focus \"${target.title}\"",
                Serial.of(updated), undoRestore("focus", Serial.of(target)), c.groupId)
            okResult(if (updated.done) "A vote for who you are becoming" else "Reopened", null, id)
        },

        Capability("remove_focus_item", "Remove one Daily Focus action",
            listOf("id" to "string"), Risk.LOW) { c ->
            val date = c.date()
            val target = c.repo.focusFor(date).firstOrNull { it.id == c.str("id") }
                ?: return@Capability CommandResult.fail("Focus action not found")
            c.repo.deleteFocus(target.id)
            val id = c.bus.record(c.actor, "remove_focus_item", "Removed focus \"${target.title}\"",
                null, undoRestore("focus", Serial.of(target)), c.groupId)
            okResult("Removed", null, id)
        },

        Capability("clear_daily_focus", "Clear the Daily Focus list for a day",
            listOf("date" to "yyyy-MM-dd"), Risk.MEDIUM, destructive = true) { c ->
            val date = c.date()
            val prev = c.repo.focusFor(date)
            if (prev.isEmpty()) return@Capability CommandResult.fail("Nothing to clear")
            val rows = JSONArray()
            prev.forEach { rows.put(Serial.of(it)) }
            c.repo.clearFocus(date)
            val id = c.bus.record(c.actor, "clear_daily_focus", "Cleared Daily Focus", null,
                jsonOf("kind" to "restoreRows", "table" to "focus", "rows" to rows), c.groupId)
            okResult("Daily Focus cleared", null, id)
        },

        Capability("plan_tomorrow", "Draft tomorrow's Daily Focus from scheduled habits",
            listOf(), Risk.MEDIUM) { c ->
            val tomorrow = c.repo.clock.today().plusDays(1)
            val date = SfTime.format(tomorrow)
            val candidates = c.repo.habitsForDay(tomorrow)
                .sortedWith(compareByDescending<Habit> { it.protectedRoutine }.thenBy { it.orderIndex })
                .take(3)
            if (candidates.isEmpty()) return@Capability CommandResult.fail("No habits are scheduled for tomorrow")
            val prev = c.repo.focusFor(date)
            val rows = JSONArray()
            prev.forEach { rows.put(Serial.of(it)) }
            c.repo.clearFocus(date)
            candidates.forEachIndexed { i, h ->
                c.repo.saveFocus(FocusItem(date = date, habitId = h.id, title = h.title, orderIndex = i))
            }
            val id = c.bus.record(c.actor, "plan_tomorrow",
                "Planned tomorrow: ${candidates.joinToString("; ") { it.title }}", null,
                jsonOf("kind" to "restoreRows", "table" to "focus", "rows" to rows), c.groupId)
            okResult("Tomorrow is planned: ${candidates.joinToString(", ") { it.title }}", null, id)
        }
    )

    /* ------------------------------------------- design and support layer */

    private fun designCaps() = listOf(
        Capability("add_scorecard_entry", "Record a routine as helpful, neutral or unhelpful",
            listOf("routine" to "string", "verdict" to "-1|0|1", "note" to "string"), Risk.LOW) { c ->
            val routine = c.str("routine").trim()
            if (routine.isBlank()) return@Capability CommandResult.fail("Describe the routine")
            val e = ScorecardEntry(routine = routine, verdict = c.int("verdict", 0).coerceIn(-1, 1),
                note = c.str("note"))
            c.repo.saveScorecard(e)
            val id = c.bus.record(c.actor, "add_scorecard_entry", "Scorecard: \"$routine\"",
                Serial.of(e), undoDelete("scorecard", e.id), c.groupId)
            okResult("Added to your Habit Scorecard", jsonOf("id" to e.id), id)
        },

        Capability("delete_scorecard_entry", "Remove a scorecard row", listOf("id" to "string"),
            Risk.MEDIUM, destructive = true) { c ->
            val old = c.repo.scorecard().firstOrNull { it.id == c.str("id") }
                ?: return@Capability CommandResult.fail("Entry not found")
            c.repo.deleteScorecard(old.id)
            val id = c.bus.record(c.actor, "delete_scorecard_entry", "Removed \"${old.routine}\"",
                Serial.of(old), undoRestore("scorecard", Serial.of(old)), c.groupId)
            okResult("Removed", null, id)
        },

        Capability("create_flow", "Create a chain of anchored habits",
            listOf("title" to "string", "anchor" to "string", "steps" to "array of strings"),
            Risk.LOW) { c ->
            val title = c.str("title").trim()
            if (title.isBlank()) return@Capability CommandResult.fail("A flow title is required")
            val f = Flow(title = title, anchor = c.str("anchor"))
            c.repo.saveFlow(f)
            val steps = c.args.optJSONArray("steps")
            if (steps != null) for (i in 0 until steps.length()) {
                val t = steps.optString(i, "").trim()
                if (t.isBlank()) continue
                c.repo.saveFlowStep(FlowStep(flowId = f.id, habitId = c.repo.findHabit(t)?.id,
                    title = t, orderIndex = i))
            }
            val id = c.bus.record(c.actor, "create_flow", "Created flow \"$title\"",
                Serial.of(f), undoDelete("flow", f.id), c.groupId)
            okResult("Flow created", jsonOf("id" to f.id), id)
        },

        Capability("add_flow_step", "Append a step to a flow",
            listOf("flowId" to "string", "title" to "string", "existing" to "bool"), Risk.LOW) { c ->
            val flow = c.repo.flows().firstOrNull { it.id == c.str("flowId") }
                ?: c.repo.flows().firstOrNull { it.title.equals(c.str("flow"), true) }
                ?: return@Capability CommandResult.fail("Flow not found")
            val title = c.str("title").trim()
            if (title.isBlank()) return@Capability CommandResult.fail("A step title is required")
            val steps = c.repo.flowSteps(flow.id)
            val s = FlowStep(flowId = flow.id, habitId = c.repo.findHabit(title)?.id, title = title,
                existingBehaviour = c.bool("existing", false), orderIndex = steps.size)
            c.repo.saveFlowStep(s)
            val id = c.bus.record(c.actor, "add_flow_step", "Added \"$title\" to \"${flow.title}\"",
                Serial.of(s), undoDelete("flowstep", s.id), c.groupId)
            okResult("Step added", jsonOf("id" to s.id), id)
        },

        Capability("delete_flow", "Delete a flow and its steps", listOf("flowId" to "string"),
            Risk.HIGH, destructive = true) { c ->
            val flow = c.repo.flows().firstOrNull { it.id == c.str("flowId") }
                ?: return@Capability CommandResult.fail("Flow not found")
            val rows = JSONArray()
            rows.put(Serial.of(flow))
            c.repo.flowSteps(flow.id).forEach { rows.put(Serial.of(it)) }
            c.repo.deleteFlow(flow.id)
            val id = c.bus.record(c.actor, "delete_flow", "Deleted flow \"${flow.title}\"", null,
                jsonOf("kind" to "restoreRows", "table" to "flow", "rows" to rows), c.groupId)
            okResult("Flow deleted", null, id)
        },

        Capability("enter_minimum_mode", "Drop every non-protected habit to its Minimum for today",
            listOf("date" to "yyyy-MM-dd"), Risk.MEDIUM) { c ->
            val day = c.localDate()
            val date = SfTime.format(day)
            val habits = c.repo.habitsForDay(day).filter { !it.protectedRoutine }
            if (habits.isEmpty()) return@Capability CommandResult.fail("No habits to reduce today")
            val group = c.groupId ?: newId()
            var n = 0
            val rows = JSONArray()
            for (h in habits) {
                c.repo.checkIn(h.id, date)?.let { rows.put(Serial.of(it)) }
                c.repo.saveCheckIn(CheckIn(habitId = h.id, date = date,
                    result = if (h.mode == HabitMode.REDUCE) CheckInResult.RESISTED else CheckInResult.DONE,
                    level = Level.MINIMUM, note = "Minimum Mode"))
                n++
            }
            val id = c.bus.record(c.actor, "enter_minimum_mode",
                "Minimum Mode: $n habits set to Minimum", null,
                jsonOf("kind" to "restoreRows", "table" to "checkin", "rows" to rows), group)
            okResult("Minimum Mode on. $n habits reduced; protected routines untouched.", null, id)
        },

        Capability("run_checkpoint", "Run the morning, midday or evening checkpoint",
            listOf("checkpoint" to "MORNING|MIDDAY|EVENING"), Risk.LOW) { c ->
            val cp = runCatching { Checkpoint.valueOf(c.str("checkpoint", "MORNING").uppercase()) }
                .getOrDefault(Checkpoint.MORNING)
            val day = c.repo.clock.today()
            val habits = c.repo.habitsForDay(day)
            val done = c.repo.checkInsFor(SfTime.format(day)).count { it.isSuccess }
            val focus = c.repo.focusFor(SfTime.format(day))
            val text = when (cp) {
                Checkpoint.MORNING -> "Morning checkpoint. ${habits.size} habits scheduled. " +
                        if (focus.isEmpty()) "Pick up to three Daily Focus actions."
                        else "Focus: ${focus.joinToString(", ") { it.title }}."
                Checkpoint.MIDDAY -> "Midday checkpoint. $done of ${habits.size} done so far. " +
                        "If the day got away, a Tiny Start still counts."
                Checkpoint.EVENING -> "Evening checkpoint. $done of ${habits.size} completed. " +
                        "Prepare one thing for tomorrow."
            }
            val id = c.bus.record(c.actor, "run_checkpoint", "${cp.label} checkpoint", null, null, c.groupId)
            okResult(text, jsonOf("checkpoint" to cp.name), id)
        }
    )

    /* ----------------------------------------------------------- pause layer */

    private fun pauseCaps() = listOf(
        Capability("pause_habits", "Pause habits for a date range, without creating misses",
            listOf("from" to "yyyy-MM-dd", "to" to "yyyy-MM-dd", "habit" to "id or title (optional)",
                "reason" to "string"), Risk.MEDIUM) { c ->
            val today = c.repo.clock.today()
            val from = SfTime.parseDate(c.str("from")) ?: today
            val to = SfTime.parseDate(c.str("to")) ?: from
            if (to.isBefore(from)) return@Capability CommandResult.fail("The end date is before the start")
            val habit = if (c.str("habit").isBlank()) null else resolveHabit(c)
            val p = PauseWindow(
                habitId = habit?.id, startDate = SfTime.format(from),
                endDate = SfTime.format(to), reason = c.str("reason")
            )
            c.repo.savePause(p)
            val scope = habit?.title ?: "all habits"
            val id = c.bus.record(c.actor, "pause_habits",
                "Paused $scope from ${SfTime.shortDay(from)} to ${SfTime.shortDay(to)}",
                Serial.of(p), undoDelete("pause", p.id), c.groupId)
            okResult("Paused $scope. Those days will not count as misses.", jsonOf("id" to p.id), id)
        },

        Capability("resume_habits", "Remove a pause window", listOf("id" to "string"),
            Risk.LOW) { c ->
            val old = c.repo.pauses().firstOrNull { it.id == c.str("id") }
                ?: return@Capability CommandResult.fail("Pause not found")
            c.repo.deletePause(old.id)
            val id = c.bus.record(c.actor, "resume_habits", "Removed a pause window",
                Serial.of(old), undoRestore("pause", Serial.of(old)), c.groupId)
            okResult("Resumed", null, id)
        },

        Capability("set_schedule", "Change when a habit recurs",
            listOf("habit" to "id or title", "days" to "daily|weekdays|3x a week|mon,wed",
                "cueTime" to "HH:mm"), Risk.LOW) { c ->
            val old = resolveHabit(c) ?: return@Capability CommandResult.fail("Habit not found")
            val cueTime = c.str("cueTime").trim()
            if (cueTime.isNotBlank() && !SfTime.isValidTime(cueTime))
                return@Capability CommandResult.fail("Cue time must look like 07:30")
            val recurrence = if (c.str("days").isBlank()) Recurrence.decode(old.recurrenceRule)
            else Recurrence.parse(c.str("days"))
            val updated = old.copy(
                recurrenceRule = recurrence.encode(),
                cueTime = cueTime.ifBlank { old.cueTime },
                scheduleVersion = old.scheduleVersion + 1
            )
            c.repo.saveHabit(updated)
            val id = c.bus.record(c.actor, "set_schedule",
                "\"${updated.title}\" now runs ${recurrence.label().lowercase()}",
                Serial.of(updated), undoRestore("habit", Serial.of(old)), c.groupId)
            okResult("Schedule updated to ${recurrence.label().lowercase()}", null, id)
        }
    )

    /* ---------------------------------------------------------- review layer */

    private fun reviewCaps() = listOf(
        Capability("create_review", "Save a weekly, monthly or quarterly review",
            listOf("kind" to "WEEKLY|MONTHLY|QUARTERLY", "whatWorked" to "string",
                "whatDidnt" to "string", "systemChange" to "string",
                "identityEvidence" to "string"), Risk.LOW) { c ->
            val kind = runCatching { ReviewKind.valueOf(c.str("kind", "WEEKLY").uppercase()) }
                .getOrDefault(ReviewKind.WEEKLY)
            val label = when (kind) {
                ReviewKind.WEEKLY -> "Week of ${SfTime.shortDay(
                    SfTime.startOfWeek(c.repo.clock.today()))}"
                ReviewKind.MONTHLY -> SfTime.monthLabel(c.repo.clock.today())
                ReviewKind.QUARTERLY ->
                    "Quarter ending ${SfTime.shortDay(c.repo.clock.today())}"
            }
            val r = Review(kind = kind, periodLabel = label, whatWorked = c.str("whatWorked"),
                whatDidnt = c.str("whatDidnt"), systemChange = c.str("systemChange"),
                identityEvidence = c.str("identityEvidence"))
            c.repo.saveReview(r)
            val id = c.bus.record(c.actor, "create_review",
                "${kind.name.lowercase().replaceFirstChar { it.uppercase() }} review saved",
                Serial.of(r), undoDelete("review", r.id), c.groupId)
            okResult("Review saved", jsonOf("id" to r.id), id)
        },

        Capability("delete_review", "Delete a review", listOf("id" to "string"),
            Risk.HIGH, destructive = true) { c ->
            val old = c.repo.reviews().firstOrNull { it.id == c.str("id") }
                ?: return@Capability CommandResult.fail("Review not found")
            c.repo.deleteReview(old.id)
            val id = c.bus.record(c.actor, "delete_review", "Deleted ${old.periodLabel} review",
                Serial.of(old), undoRestore("review", Serial.of(old)), c.groupId)
            okResult("Review deleted", null, id)
        }
    )

    /* ----------------------------------------------------------- query layer */

    private fun queryCaps() = listOf(
        Capability("list_habits", "List habits with today's status",
            listOf("date" to "yyyy-MM-dd"), Risk.LOW) { c ->
            val day = c.localDate()
            val date = SfTime.format(day)
            val habits = c.repo.habits()
            if (habits.isEmpty()) return@Capability okResult("You have no habits yet.")
            val today = c.repo.habitsForDay(day).map { it.id }.toSet()
            val text = habits.joinToString("\n") { h ->
                val ci = c.repo.checkIn(h.id, date)
                val mark = when {
                    ci == null && h.id in today -> "open"
                    ci == null -> "not scheduled"
                    ci.isSuccess -> ci.level.label.lowercase()
                    else -> ci.result.name.lowercase()
                }
                "· ${h.title} ($mark)"
            }
            okResult(text)
        },

        Capability("today_summary", "Summarise today", listOf("date" to "yyyy-MM-dd"), Risk.LOW) { c ->
            okResult(Insights.todaySummary(c.repo, c.localDate()))
        },

        Capability("get_insights", "Repetitions, consistency, recovery and identity evidence",
            listOf("days" to "int"), Risk.LOW) { c ->
            okResult(Insights.summaryText(c.repo, c.int("days", 30)))
        },

        Capability("habit_detail", "Show one habit's full design",
            listOf("habit" to "id or title"), Risk.LOW) { c ->
            val h = resolveHabit(c) ?: return@Capability CommandResult.fail("Habit not found")
            val stats = Insights.forHabit(c.repo, h)
            val sb = StringBuilder()
            sb.append(h.title).append('\n').append(h.contract()).append('\n')
            sb.append("Repetitions: ${stats.repetitions}, current run: ${stats.currentRun}, ")
            sb.append("30-day consistency: ${stats.consistency30}%")
            val obstacles = c.repo.obstacles(h.id)
            if (obstacles.isNotEmpty()) {
                sb.append("\nObstacle plans:")
                obstacles.forEach { sb.append("\n  If ${it.ifText}, then ${it.thenText}") }
            }
            okResult(sb.toString())
        },

        Capability("search", "Search habits, goals, identities and systems",
            listOf("query" to "string"), Risk.LOW) { c ->
            val q = c.str("query").trim().lowercase()
            if (q.isBlank()) return@Capability CommandResult.fail("What should I search for?")
            val hits = ArrayList<String>()
            c.repo.identities().filter { it.statement.lowercase().contains(q) }
                .forEach { hits.add("Identity: ${it.statement}") }
            c.repo.goals().filter { it.title.lowercase().contains(q) }
                .forEach { hits.add("Goal: ${it.title}") }
            c.repo.systems().filter { it.title.lowercase().contains(q) }
                .forEach { hits.add("System: ${it.title}") }
            c.repo.habits(true).filter { it.title.lowercase().contains(q) }
                .forEach { hits.add("Habit: ${it.title}") }
            okResult(if (hits.isEmpty()) "No matches for \"$q\"." else hits.joinToString("\n"))
        }
    )

    /* ------------------------------------------------------------ data layer */

    private fun dataCaps() = listOf(
        Capability("export_data", "Export the whole workspace as JSON", listOf(), Risk.LOW) { c ->
            val json = Serial.exportAll(c.repo)
            okResult("Export ready (${json.toString().length} characters)", json)
        },

        Capability("delete_all_data", "Erase every record in the app", listOf("confirm" to "bool"),
            Risk.HIGH, destructive = true) { c ->
            val snapshot = Serial.exportAll(c.repo)
            c.repo.deleteAllData()
            val id = c.bus.record(c.actor, "delete_all_data", "Deleted all app data", null,
                jsonOf("kind" to "noop"), c.groupId)
            okResult("All data deleted", snapshot, id)
        },

        Capability("clear_ai_history", "Clear the AI conversation", listOf(),
            Risk.MEDIUM, destructive = true) { c ->
            c.repo.clearMessages()
            okResult("AI history cleared")
        }
    )


    /* ---------------------------------------------------- growth caps */

    private fun growthCaps() = listOf(
        Capability("create_growth_plan", "Create a progressive growth plan for a habit",
            listOf("habit" to "id or title", "weeks" to "int"), Risk.LOW) { c ->
            val h = resolveHabit(c) ?: return@Capability CommandResult.fail("Habit not found")
            val weeks = c.int("weeks", 8).coerceIn(4, 52)
            val existing = c.repo.growthPlansForHabit(h.id)
            if (existing.isNotEmpty()) return@Capability CommandResult.fail("A growth plan already exists for this habit")
            val plan = GrowthEngine.generateGrowthPlan(h, weeks)
            c.repo.saveGrowthPlan(plan)
            val id = c.bus.record(c.actor, "create_growth_plan",
                "Created growth plan for \"${h.title}\" (${weeks} weeks)",
                null, null, c.groupId)
            okResult("Growth plan created for \"${h.title}\". It will auto-evaluate weekly.", jsonOf("id" to plan.id), id)
        },

        Capability("list_growth_plans", "Show all active growth plans with status",
            listOf(), Risk.LOW) { c ->
            val plans = c.repo.growthPlans().filter { it.isActive() }
            if (plans.isEmpty()) return@Capability okResult("No active growth plans.")
            val text = plans.joinToString("\n") { p ->
                val habit = c.repo.habit(p.habitId)
                val phase = p.phases.getOrNull(p.currentPhaseIndex)
                "\u2022 ${habit?.title ?: "Unknown"}: Phase ${p.currentPhaseIndex + 1}/${p.phases.size} " +
                        "\u2014 ${phase?.label ?: ""} (${p.weeksSinceStart()} weeks)"
            }
            okResult(text)
        },

        Capability("upgrade_phase", "Manually advance to next growth phase",
            listOf("habit" to "id or title"), Risk.LOW) { c ->
            val h = resolveHabit(c) ?: return@Capability CommandResult.fail("Habit not found")
            val plan = c.repo.growthPlansForHabit(h.id).firstOrNull { it.isActive() }
                ?: return@Capability CommandResult.fail("No active growth plan for this habit")
            if (plan.currentPhaseIndex >= plan.phases.lastIndex)
                return@Capability CommandResult.fail("Already at the highest phase")
            GrowthEngine.applyUpgrade(plan, c.repo)
            val id = c.bus.record(c.actor, "upgrade_phase",
                "Upgraded \"${h.title}\" to phase ${plan.currentPhaseIndex + 2}",
                null, null, c.groupId)
            okResult("Upgraded to phase ${plan.currentPhaseIndex + 2}", null, id)
        },

        Capability("downgrade_phase", "Manually step back a growth phase",
            listOf("habit" to "id or title"), Risk.LOW) { c ->
            val h = resolveHabit(c) ?: return@Capability CommandResult.fail("Habit not found")
            val plan = c.repo.growthPlansForHabit(h.id).firstOrNull { it.isActive() }
                ?: return@Capability CommandResult.fail("No active growth plan for this habit")
            if (plan.currentPhaseIndex <= 0) return@Capability CommandResult.fail("Already at the first phase")
            GrowthEngine.applyDowngrade(plan, c.repo)
            val id = c.bus.record(c.actor, "downgrade_phase",
                "Downgraded \"${h.title}\" to phase ${plan.currentPhaseIndex}",
                null, null, c.groupId)
            okResult("Downgraded to phase ${plan.currentPhaseIndex}", null, id)
        },

        Capability("difficulty_assessment", "Rate a habit\'s difficulty based on its design",
            listOf("habit" to "id or title"), Risk.LOW) { c ->
            val h = resolveHabit(c) ?: return@Capability CommandResult.fail("Habit not found")
            val rating = GrowthEngine.estimateDifficulty(h)
            okResult("${h.title}: ${rating.level.name} (score ${rating.score}/5)\n${rating.advice}")
        }
    )

    /* ---------------------------------------------------- template caps */

    private fun templateCaps() = listOf(
        Capability("suggest_templates", "Get habit templates for a goal",
            listOf("goal" to "string", "area" to "string"), Risk.LOW) { c ->
            val goal = c.str("goal")
            val area = c.str("area")
            val templates = if (area.isNotBlank())
                HabitTemplates.forArea(com.superflow.data.model.LifeArea.from(area))
            else HabitTemplates.suggestForGoal(goal)
            if (templates.isEmpty()) return@Capability okResult("No templates found for that goal.")
            val text = templates.joinToString("\n") { t ->
                "\u2022 ${t.title}: ${t.standardVersion} (${t.recurrenceLabel})"
            }
            okResult("Found ${templates.size} templates:\n$text")
        },

        Capability("apply_template", "Create a habit from a template",
            listOf("templateId" to "string", "title" to "string"), Risk.LOW) { c ->
            val title = c.str("title").lowercase()
            val template = HabitTemplates.allTemplates().firstOrNull {
                it.title.lowercase().contains(title) || it.id == c.str("templateId")
            } ?: return@Capability CommandResult.fail("Template not found")
            val habit = Habit(
                title = template.title,
                tinyStart = template.tinyStart,
                minimumVersion = template.minimumVersion,
                standardVersion = template.standardVersion,
                stretchVersion = template.stretchVersion,
                cueTime = template.cueTime,
                anchorText = template.anchorHint,
                benefit = template.benefit,
                recurrenceRule = com.superflow.core.schedule.Recurrence.parse(template.recurrenceLabel).encode()
            )
            c.repo.saveHabit(habit)
            val id = c.bus.record(c.actor, "apply_template",
                "Created \"${habit.title}\" from template",
                null, undoDelete("habit", habit.id), c.groupId)
            okResult("\"${habit.title}\" created from template", jsonOf("id" to habit.id), id)
        },

        Capability("list_templates", "Browse all habit templates by area",
            listOf("area" to "string"), Risk.LOW) { c ->
            val area = com.superflow.data.model.LifeArea.from(c.str("area"))
            val templates = HabitTemplates.forArea(area)
            if (templates.isEmpty()) return@Capability okResult("No templates in that area.")
            val text = templates.joinToString("\n") { t ->
                "\u2022 ${t.title}: ${t.standardVersion}"
            }
            okResult("${area.label} templates (${templates.size}):\n$text")
        }
    )

    /* ---------------------------------------------------- journal caps */

    private fun journalCaps() = listOf(
        Capability("create_journal_entry", "Write a journal entry",
            listOf("content" to "string", "date" to "yyyy-MM-dd", "mood" to "1-5",
                "prompt" to "string", "tags" to "string"), Risk.LOW) { c ->
            val content = c.str("content").trim()
            if (content.isBlank()) return@Capability CommandResult.fail("Journal content is required")
            val date = c.date()
            val entry = JournalEntry(
                date = date, prompt = c.str("prompt"), content = content,
                mood = c.int("mood", 0).let { if (it == 0) null else it.coerceIn(1, 5) },
                tags = c.str("tags").split(",").map { it.trim() }.filter { it.isNotBlank() }
            )
            c.repo.saveJournalEntry(entry)
            val id = c.bus.record(c.actor, "create_journal_entry",
                "Journal entry for ${SfTime.shortDay(c.localDate())}",
                null, undoDelete("journal_entry", entry.id), c.groupId)
            okResult("Journal entry saved", jsonOf("id" to entry.id), id)
        },

        Capability("suggest_journal_prompt", "Get a guided journal prompt",
            listOf(), Risk.LOW) { c ->
            val prompts = listOf(
                "What worked today that you want to remember?",
                "What would you tell your past self about today?",
                "What evidence did you collect about who you are becoming?",
                "What was the best moment today and why?",
                "If tomorrow were perfect, what would it look like?",
                "What is one thing you are grateful for right now?",
                "What did you learn about yourself this week?",
                "Describe a challenge you faced and how you handled it."
            )
            val prompt = prompts.random()
            okResult(prompt, jsonOf("prompt" to prompt))
        }
    )

    /* ---------------------------------------------------- routine caps (upgrade from flows) */

    private fun routineCaps() = listOf(
        Capability("create_routine", "Create a habit stacking routine",
            listOf("title" to "string", "trigger" to "string", "estimatedMinutes" to "int"),
            Risk.LOW) { c ->
            val title = c.str("title").trim()
            if (title.isBlank()) return@Capability CommandResult.fail("A routine title is required")
            val r = Routine(title = title, trigger = c.str("trigger"),
                estimatedMinutes = c.int("estimatedMinutes", 30))
            c.repo.saveRoutine(r)
            val id = c.bus.record(c.actor, "create_routine", "Created routine \"$title\"",
                null, undoDelete("routine", r.id), c.groupId)
            okResult("Routine created", jsonOf("id" to r.id), id)
        },

        Capability("add_routine_step", "Add a step to a routine",
            listOf("routineId" to "string", "title" to "string", "durationMinutes" to "int",
                "habit" to "id or title"), Risk.LOW) { c ->
            val rId = c.str("routineId")
            val routine = c.repo.routine(rId)
                ?: return@Capability CommandResult.fail("Routine not found")
            val title = c.str("title").trim()
            if (title.isBlank()) return@Capability CommandResult.fail("A step title is required")
            val habitId = resolveHabit(c)?.id
            val steps = c.repo.routineSteps(routine.id)
            val step = RoutineStep(routineId = routine.id, habitId = habitId, title = title,
                durationMinutes = c.int("durationMinutes", 5), orderIndex = steps.size)
            c.repo.saveRoutineStep(step)
            val id = c.bus.record(c.actor, "add_routine_step",
                "Added \"$title\" to \"${routine.title}\"",
                null, undoDelete("routine_step", step.id), c.groupId)
            okResult("Step added", jsonOf("id" to step.id), id)
        },

        Capability("delete_routine", "Delete a routine and its steps",
            listOf("routineId" to "string"), Risk.MEDIUM, destructive = true) { c ->
            val r = c.repo.routine(c.str("routineId"))
                ?: return@Capability CommandResult.fail("Routine not found")
            c.repo.deleteRoutine(r.id)
            val id = c.bus.record(c.actor, "delete_routine", "Deleted routine \"${r.title}\"",
                null, null, c.groupId)
            okResult("Routine deleted", null, id)
        }
    )

    /* ---------------------------------------------------- memory caps */

    private fun memoryCaps() = listOf(
        Capability("remember", "Store a structured memory for the AI",
            listOf("content" to "string", "category" to "string", "importance" to "1-10"),
            Risk.LOW) { c ->
            val content = c.str("content").trim()
            if (content.isBlank()) return@Capability CommandResult.fail("What should I remember?")
            val category = runCatching {
                MemoryCategory.valueOf(c.str("category").uppercase())
            }.getOrDefault(MemoryCategory.USER_PREFERENCE)
            val mem = AiMemory(category = category, content = content,
                importance = c.int("importance", 5).coerceIn(1, 10))
            c.repo.saveMemory(mem)
            val id = c.bus.record(c.actor, "remember", "Remembered: ${content.take(100)",
                null, undoDelete("ai_memory", mem.id), c.groupId)
            okResult("I will remember that.", jsonOf("id" to mem.id), id)
        },

        Capability("forget", "Remove a stored memory",
            listOf("id" to "string"), Risk.MEDIUM, destructive = true) { c ->
            val old = c.repo.memories.firstOrNull { it.id == c.str("id") }
                ?: return@Capability CommandResult.fail("Memory not found")
            c.repo.deleteMemory(old.id)
            val id = c.bus.record(c.actor, "forget", "Forgot: ${old.content.take(60)}...",
                null, null, c.groupId)
            okResult("Memory removed", null, id)
        },

        Capability("list_memories", "Show what the AI remembers about you",
            listOf(), Risk.LOW) { c ->
            val memories = c.repo.memories()
            if (memories.isEmpty()) return@Capability okResult("Nothing stored for me to remember.")
            val text = memories.sortedByDescending { it.importance }.joinToString("\n") { m ->
                "\u2022 [${m.category.name}] ${m.content} (importance ${m.importance})"
            }
            okResult("I remember:\n$text")
        }
    )

    /* ---------------------------------------------------- what-if caps */

    private fun whatIfCaps() = listOf(
        Capability("simulate_add_habit", "Preview the impact of adding a new habit",
            listOf("title" to "string", "standardVersion" to "string", "tinyStart" to "string"),
            Risk.LOW) { c ->
            val mock = Habit(
                title = c.str("title").ifBlank { "Mock habit" },
                standardVersion = c.str("standardVersion"),
                tinyStart = c.str("tinyStart"),
                cueTime = c.str("cueTime"),
                recurrenceRule = com.superflow.core.schedule.Recurrence.parse(c.str("days")).encode()
            )
            val sim = GrowthEngine.simulateAddition(c.repo, mock)
            okResult("Current: ${sim.currentHabits} habits, ~${sim.currentMinutes} min/day. " +
                    "With new habit: ${sim.newHabits} habits, ~${sim.newMinutes} min/day. " +
                    "Risk: ${sim.riskLevel.name}.\n${sim.advice}")
        },

        Capability("simulate_remove_habit", "Preview the impact of removing a habit",
            listOf("habit" to "id or title"), Risk.LOW) { c ->
            val h = resolveHabit(c) ?: return@Capability CommandResult.fail("Habit not found")
            val currentDaily = c.repo.habitsForDay(c.repo.clock.today()).size
            okResult("Removing \"${h.title}\" would leave you with ${currentDaily - 1} habits scheduled today.")
        }
    )

    /* ---------------------------------------------------- accountability caps */

    private fun accountabilityCaps() = listOf(
        Capability("generate_report", "Create a shareable progress report",
            listOf("days" to "int"), Risk.LOW) { c ->
            val report = AutoReview.accountabilityReport(c.repo, c.int("days", 7))
            okResult(report)
        },

        Capability("export_weekly_summary", "Export the week\'s data as text",
            listOf(), Risk.LOW) { c ->
            val summary = Insights.summaryText(c.repo, 7)
            okResult(summary)
        }
    )

    /* ---------------------------------------------------- milestone caps */

    private fun milestoneCaps() = listOf(
        Capability("list_milestones", "Show all achieved milestones",
            listOf("habit" to "id or title"), Risk.LOW) { c ->
            val milestones = if (c.str("habit").isNotBlank()) {
                val h = resolveHabit(c) ?: return@Capability CommandResult.fail("Habit not found")
                c.repo.milestonesForHabit(h.id)
            } else c.repo.milestones()
            if (milestones.isEmpty()) return@Capability okResult("No milestones achieved yet.")
            val text = milestones.joinToString("\n") { m ->
                "\u2022 ${m.label} (${m.type.name})"
            }
            okResult("Milestones:\n$text")
        }
    )

    /* ---------------------------------------------------- sprint caps */

    private fun sprintCaps() = listOf(
        Capability("create_sprint", "Create a time-boxed commitment sprint",
            listOf("title" to "string", "startDate" to "yyyy-MM-dd", "endDate" to "yyyy-MM-dd",
                "focusHabits" to "string", "goals" to "string"), Risk.LOW) { c ->
            val title = c.str("title").trim()
            if (title.isBlank()) return@Capability CommandResult.fail("A sprint title is required")
            val startDate = SfTime.parseDate(c.str("startDate")) ?: c.repo.clock.today()
            val endDate = SfTime.parseDate(c.str("endDate")) ?: startDate.plusDays(14)
            if (endDate.isBefore(startDate)) return@Capability CommandResult.fail("End date is before start date")
            val sprint = Sprint(
                title = title, startDate = SfTime.format(startDate),
                endDate = SfTime.format(endDate),
                focusHabits = c.str("focusHabits").split(",").map { it.trim() }.filter { it.isNotBlank() },
                goals = c.str("goals").split(",").map { it.trim() }.filter { it.isNotBlank() }
            )
            c.repo.saveSprint(sprint)
            val id = c.bus.record(c.actor, "create_sprint", "Created sprint \"$title\"",
                null, undoDelete("sprint", sprint.id), c.groupId)
            okResult("Sprint created: ${SfTime.shortDay(startDate)} to ${SfTime.shortDay(endDate)}",
                jsonOf("id" to sprint.id), id)
        },

        Capability("complete_sprint", "Mark a sprint as complete with review",
            listOf("id" to "string", "reviewNotes" to "string"), Risk.LOW) { c ->
            val sprint = c.repo.sprint(c.str("id"))
                ?: return@Capability CommandResult.fail("Sprint not found")
            c.repo.saveSprint(sprint.copy(status = SprintStatus.COMPLETED,
                reviewNotes = c.str("reviewNotes")))
            val id = c.bus.record(c.actor, "complete_sprint",
                "Completed sprint \"${sprint.title}\"",
                null, null, c.groupId)
            okResult("Sprint \"${sprint.title}\" completed!", null, id)
        },

        Capability("abandon_sprint", "End a sprint early with a compassionate note",
            listOf("id" to "string", "note" to "string"), Risk.LOW) { c ->
            val sprint = c.repo.sprint(c.str("id"))
                ?: return@Capability CommandResult.fail("Sprint not found")
            c.repo.saveSprint(sprint.copy(status = SprintStatus.ABANDONED,
                reviewNotes = c.str("note")))
            val id = c.bus.record(c.actor, "abandon_sprint",
                "Abandoned sprint \"${sprint.title}\"",
                null, null, c.groupId)
            okResult("Sprint ended. Some experiments don't work out — that's data, not failure.", null, id)
        }
    )

    /* ---------------------------------------------------- setting caps */

    private fun settingCaps() = listOf(
        Capability("set_theme", "Change the app theme",
            listOf("theme" to "system|light|dark"), Risk.LOW) { c ->
            val theme = c.str("theme", "system").lowercase()
            val mode = when (theme) {
                "light" -> com.superflow.data.Prefs.THEME_LIGHT
                "dark" -> com.superflow.data.Prefs.THEME_DARK
                else -> com.superflow.data.Prefs.THEME_SYSTEM
            }
            val prefs = com.superflow.data.Prefs.get(c.bus.context())
            prefs.themeMode = mode
            val id = c.bus.record(c.actor, "set_theme", "Changed theme to $theme",
                null, null, c.groupId)
            okResult("Theme changed to $theme", null, id)
        },

        Capability("set_quiet_hours", "Update quiet hours for notifications",
            listOf("from" to "HH:mm", "to" to "HH:mm"), Risk.LOW) { c ->
            val prefs = com.superflow.data.Prefs.get(c.bus.context())
            val from = c.str("from").ifBlank { prefs.quietFrom }
            val to = c.str("to").ifBlank { prefs.quietTo }
            prefs.quietFrom = from
            prefs.quietTo = to
            val id = c.bus.record(c.actor, "set_quiet_hours",
                "Quiet hours: $from to $to", null, null, c.groupId)
            okResult("Quiet hours set to $from - $to", null, id)
        },

        Capability("set_haptics", "Configure haptic feedback",
            listOf("enabled" to "bool"), Risk.LOW) { c ->
            val prefs = com.superflow.data.Prefs.get(c.bus.context())
            prefs.hapticsEnabled = c.bool("enabled", false)
            val id = c.bus.record(c.actor, "set_haptics", "Haptics ${if (prefs.hapticsEnabled) "enabled" else "disabled"}",
                null, null, c.groupId)
            okResult("Haptics updated", null, id)
        }
    )


    /* ---------------------------------------------------- analysis caps */

    private fun analysisCaps() = listOf(
        Capability("analyze_patterns", "Detect time-of-day and day-of-week patterns",
            listOf(), Risk.LOW) { c ->
            okResult(Insights.analyzePatterns(c.repo))
        },

        Capability("analyze_correlations", "Find habit-to-habit correlations",
            listOf(), Risk.LOW) { c ->
            okResult(Insights.analyzeCorrelations(c.repo))
        },

        Capability("predict_consistency", "Predict next week's consistency per habit",
            listOf(), Risk.LOW) { c ->
            okResult(Insights.predictConsistency(c.repo))
        },

        Capability("time_audit", "Estimate daily time commitment",
            listOf(), Risk.LOW) { c ->
            val habits = c.repo.habits()
            val total = habits.sumOf { com.superflow.domain.GrowthEngine.let { /* see */ }.run {
                estimateMinutes(it.standardVersion)
            } }
            val byHabit = habits.joinToString("\n") { h ->
                "  ${h.title}: ~${estimateMinutes(h.standardVersion)} min/day"
            }
            okResult("Total daily time: ~$total min\n$byHabit")
        },

        Capability("obstacle_plan_progress", "Surface obstacle plans for a struggling habit",
            listOf("habit" to "id or title"), Risk.LOW) { c ->
            val h = resolveHabit(c) ?: return@Capability CommandResult.fail("Habit not found")
            val obstacles = c.repo.obstacles(h.id)
            if (obstacles.isEmpty()) return@Capability okResult("No obstacle plans set for \"${h.title}\" yet.")
            val stats = Insights.forHabit(c.repo, h)
            if (stats.missesInARow < 2) {
                okResult("Your obstacle plans are ready when you need them:\n" +
                        obstacles.joinToString("\n") { "  If ${it.ifText}, then ${it.thenText}" })
            } else {
                okResult("\"${h.title}\" has missed ${stats.missesInARow} times. Your plans:\n" +
                        obstacles.joinToString("\n") { "  → If ${it.ifText}, then ${it.thenText}" })
            }
        }
    )

    /* ---------------------------------------------------- coaching caps */

    private fun coachingCaps() = listOf(
        Capability("weekly_coaching_report", "Generate a comprehensive weekly report",
            listOf(), Risk.LOW) { c ->
            okResult(com.superflow.domain.AutoReview.generate(c.repo).review.run {
                "${periodLabel}\n\nWhat worked: $whatWorked\nWhat didn\'t: $whatDidnt\nSystem change: $systemChange"
            })
        },

        Capability("morning_briefing", "Today\'s plan with energy-aware ordering",
            listOf("energy" to "1-5"), Risk.LOW) { c ->
            val today = c.repo.clock.today()
            val habits = c.repo.habitsForDay(today)
            if (habits.isEmpty()) return@Capability okResult("Nothing scheduled today. A quiet day is allowed.")
            val energy = c.int("energy", 3).coerceIn(1, 5)
            val ordered = habits.sortedByDescending { habitScore(it, energy) }
            val text = ordered.joinToString("\n") { h ->
                val cue = if (h.cueTime.isNotBlank()) " at ${h.cueTime}" else ""
                "  • ${h.title}${cue}"
            }
            okResult("Today\'s plan (energy: $energy/5):\n$text\n\nFocus on the first 3; tiny versions for the rest.")
        },

        Capability("evening_reflection", "End-of-day summary with prompt",
            listOf(), Risk.LOW) { c ->
            val today = c.repo.clock.today()
            val (done, total) = Insights.dayProgress(c.repo, today)
            val summary = Insights.todaySummary(c.repo, today)
            val prompts = listOf(
                "What worked today that you want to remember?",
                "What would you tell your past self about today?",
                "What evidence did you collect about who you are becoming?"
            )
            okResult("$summary\n\n$done of $total done.\n\nEvening prompt: ${prompts.random()}")
        },

        Capability("diagnose_struggle", "Analyze why a habit is struggling and suggest fixes",
            listOf("habit" to "id or title"), Risk.LOW) { c ->
            val h = resolveHabit(c) ?: return@Capability CommandResult.fail("Habit not found")
            val stats = Insights.forHabit(c.repo, h)
            val rating = GrowthEngine.estimateDifficulty(h)
            val sb = StringBuilder()
            sb.append("\"${h.title}\" diagnosis:\n\n")
            sb.append("Consistency: ${stats.consistency30}%, Misses in a row: ${stats.missesInARow}\n")
            sb.append("Difficulty: ${rating.level.name} (${rating.score}/5)\n")
            sb.append("Factors:\n")
            rating.factors.forEach { sb.append("  • $it\n") }
            sb.append("\nSuggestions:\n")
            when {
                stats.missesInARow >= 3 -> sb.append("  → Shrink to tiny version for a week. Recovery is the real skill.\n")
                rating.score >= 4 -> sb.append("  → Make the tiny version genuinely tiny (2 min).\n")
                stats.consistency30 < 50 -> sb.append("  → Reduce target days.\n")
                else -> sb.append("  → Keep going. The variance is normal.\n")
            }
            okResult(sb.toString())
        },

        Capability("reflect", "Start a guided journaling reflection",
            listOf(), Risk.LOW) { c ->
            val prompts = listOf(
                "What evidence did you collect today about who you are becoming?",
                "What is one thing you are grateful for?",
                "What was the best moment today and why?"
            )
            okResult(prompts.random(), jsonOf("prompt" to prompts.random()))
        }
    )

    /* ---------------------------------------------------- blueprint V2 caps */

    private fun blueprintCaps() = listOf(
        Capability("create_progressive_blueprint", "Compile a Blueprint with phased execution",
            listOf("projectId" to "string", "goal" to "string", "durationWeeks" to "int",
                "dailyTimeMinutes" to "int"), Risk.LOW) { c ->
            val project = c.repo.project(c.str("projectId"))
                ?: return@Capability CommandResult.fail("Project not found")
            val sources = c.repo.sources(project.id)
            val intent = com.superflow.blueprint.CompilerV2.captureIntent(
                goal = c.str("goal", project.name),
                dailyTimeMinutes = c.int("dailyTimeMinutes", 30),
                durationWeeks = c.int("durationWeeks", 8)
            )
            val plan = com.superflow.blueprint.CompilerV2.compileForBlueprint(project, sources, intent)
            val text = "Progressive plan: ${plan.phases.size} phases over ${plan.totalWeeks} weeks (~${plan.estimatedDailyTimeMinutes} min/day)\n\n" +
                    plan.phases.joinToString("\n") { p ->
                        "Phase ${p.weekStart}-${p.weekEnd} (${p.label}): ${p.newHabits.size} new habits in ${p.focusArea}"
                    }
            okResult(text)
        },

        Capability("evaluate_blueprint_phase", "Check if current phase is complete",
            listOf("projectId" to "string"), Risk.LOW) { c ->
            val project = c.repo.project(c.str("projectId"))
                ?: return@Capability CommandResult.fail("Project not found")
            val reqs = c.repo.requirements(project.id)
            val implemented = reqs.count { it.status == RequirementStatus.IMPLEMENTED }
            val total = reqs.size
            if (total == 0) return@Capability okResult("No requirements in this blueprint yet.")
            val pct = (implemented * 100) / total
            okResult("Blueprint phase: $implemented / $total implemented (${pct}%).")
        },

        Capability("advance_blueprint_phase", "Move to the next blueprint phase",
            listOf("projectId" to "string"), Risk.LOW) { c ->
            val project = c.repo.project(c.str("projectId"))
                ?: return@Capability CommandResult.fail("Project not found")
            // Save current state, then increment version
            val newProject = project.copy(version = project.version + 1)
            c.repo.saveProject(newProject)
            val id = c.bus.record(c.actor, "advance_blueprint_phase",
                "Advanced to blueprint v${newProject.version}",
                null, null, c.groupId)
            okResult("Advanced to blueprint v${newProject.version}", null, id)
        }
    )

    /* ---------------------------------------------------- environment caps */

    private fun environmentCaps() = listOf(
        Capability("set_environment_design", "Set environment design for a habit",
            listOf("habit" to "id or title", "makeObvious" to "string", "makeAttractive" to "string",
                "makeEasy" to "string", "makeSatisfying" to "string"), Risk.LOW) { c ->
            val h = resolveHabit(c) ?: return@Capability CommandResult.fail("Habit not found")
            val existing = c.repo.environmentDesign(h.id) ?: EnvironmentDesign(habitId = h.id)
            val updated = existing.copy(
                makeObvious = c.str("makeObvious").split("|").map { it.trim() }.filter { it.isNotBlank() }
                    .ifEmpty { existing.makeObvious },
                makeAttractive = c.str("makeAttractive").split("|").map { it.trim() }.filter { it.isNotBlank() }
                    .ifEmpty { existing.makeAttractive },
                makeEasy = c.str("makeEasy").split("|").map { it.trim() }.filter { it.isNotBlank() }
                    .ifEmpty { existing.makeEasy },
                makeSatisfying = c.str("makeSatisfying").split("|").map { it.trim() }.filter { it.isNotBlank() }
                    .ifEmpty { existing.makeSatisfying }
            )
            c.repo.saveEnvironmentDesign(updated)
            val id = c.bus.record(c.actor, "set_environment_design",
                "Environment design set for \"${h.title}\"", null, null, c.groupId)
            okResult("Environment set for \"${h.title}\".", jsonOf("habitId" to h.id), id)
        },

        Capability("suggest_environment", "Suggest environment changes for a habit",
            listOf("habit" to "id or title"), Risk.LOW) { c ->
            val h = resolveHabit(c) ?: return@Capability CommandResult.fail("Habit not found")
            val suggestions = buildList {
                add("Make obvious: Put ${h.title.lowercase()} cues where you can see them")
                add("Make attractive: Pair ${h.title} with something you enjoy")
                add("Make easy: Reduce friction to start in 30 seconds")
                add("Make satisfying: Track on a visible streak counter")
            }
            okResult("Environment suggestions for \"${h.title}\":\n" +
                    suggestions.joinToString("\n") { "  • $it" })
        }
    )

    /* ---------------------------------------------------- simulation caps */

    private fun simulationCaps() = listOf(
        Capability("simulate_reschedule", "Preview the impact of changing a schedule",
            listOf("habit" to "id or title", "cueTime" to "HH:mm", "days" to "string"),
            Risk.LOW) { c ->
            val h = resolveHabit(c) ?: return@Capability CommandResult.fail("Habit not found")
            val newTime = c.str("cueTime").ifBlank { h.cueTime }
            val newDays = c.str("days").ifBlank { "daily" }
            okResult("Changing \"${h.title}\" to $newTime on $newDays. " +
                    "This will only affect new check-ins; history is preserved.")
        }
    )

    /* ---------------------------------------------------- notification caps */

    private fun notificationCaps() = listOf(
        Capability("set_reminders_enabled", "Toggle habit reminders on or off",
            listOf("enabled" to "bool"), Risk.LOW) { c ->
            val prefs = com.superflow.data.Prefs.get(c.bus.context())
            prefs.remindersEnabled = c.bool("enabled", true)
            com.superflow.notify.Reminders.rescheduleAll(c.bus.context())
            val id = c.bus.record(c.actor, "set_reminders_enabled",
                "Reminders ${if (prefs.remindersEnabled) "enabled" else "disabled"}",
                null, null, c.groupId)
            okResult("Reminders ${if (prefs.remindersEnabled) "on" else "off"}", null, id)
        },

        Capability("set_growth_plans_enabled", "Toggle the growth plan system",
            listOf("enabled" to "bool"), Risk.LOW) { c ->
            val prefs = com.superflow.data.Prefs.get(c.bus.context())
            prefs.growthPlansEnabled = c.bool("enabled", true)
            val id = c.bus.record(c.actor, "set_growth_plans_enabled",
                "Growth plans ${if (prefs.growthPlansEnabled) "enabled" else "disabled"}",
                null, null, c.groupId)
            okResult("Growth plans ${if (prefs.growthPlansEnabled) "on" else "off"}", null, id)
        }
    )

    /* ---------------------------------------------------------------- helpers */

    private val habitFields: List<Pair<String, String>> = listOf(
        "title" to "", "tinyStart" to "", "minimumVersion" to "", "standardVersion" to "",
        "stretchVersion" to "", "cueTime" to "", "cuePlace" to "", "anchorText" to "",
        "benefit" to "", "temptationBundle" to "", "reframe" to "", "frictionPlan" to "",
        "environmentPrep" to "", "reward" to "", "recoveryPlan" to "", "unit" to "",
        "targetCount" to "", "mode" to "", "trackType" to "", "reminder" to "",
        "protected" to "", "status" to ""
    )

    private fun applyField(h: Habit, field: String, value: String): Habit? = when (field.lowercase()) {
        "title" -> h.copy(title = value)
        "tinystart", "tiny" -> h.copy(tinyStart = value)
        "minimumversion", "minimum" -> h.copy(minimumVersion = value)
        "standardversion", "standard" -> h.copy(standardVersion = value)
        "stretchversion", "stretch" -> h.copy(stretchVersion = value)
        "cuetime", "time" -> h.copy(cueTime = value)
        "cueplace", "place" -> h.copy(cuePlace = value)
        "anchortext", "anchor" -> h.copy(anchorText = value)
        "benefit" -> h.copy(benefit = value)
        "temptationbundle", "bundle" -> h.copy(temptationBundle = value)
        "reframe" -> h.copy(reframe = value)
        "frictionplan", "friction" -> h.copy(frictionPlan = value)
        "environmentprep", "prep", "environment" -> h.copy(environmentPrep = value)
        "reward" -> h.copy(reward = value)
        "recoveryplan", "recovery" -> h.copy(recoveryPlan = value)
        "unit" -> h.copy(unit = value)
        "targetcount", "target" -> h.copy(targetCount = value.toIntOrNull() ?: h.targetCount)
        "mode" -> runCatching { h.copy(mode = HabitMode.valueOf(value.uppercase())) }.getOrDefault(h)
        "tracktype", "track" -> runCatching { h.copy(trackType = TrackType.valueOf(value.uppercase())) }
            .getOrDefault(h)
        "reminder", "reminderenabled" -> h.copy(reminderEnabled = value.equalsTrue())
        "protected", "protectedroutine" -> h.copy(protectedRoutine = value.equalsTrue())
        "status" -> runCatching { h.copy(status = Status.valueOf(value.uppercase())) }.getOrDefault(h)
        else -> null
    }

    private fun String.equalsTrue(): Boolean =
        equals("true", true) || equals("yes", true) || equals("on", true) || this == "1"

    fun resolveHabit(c: Ctx): Habit? {
        val key = listOf("habitId", "habit", "id", "title").firstNotNullOfOrNull { k ->
            c.str(k).trim().ifBlank { null }
        } ?: return null
        return c.repo.habit(key) ?: c.repo.findHabit(key)
    }

    /** Natural-language schedule spec -> recurrence rule. */
    fun parseDays(spec: String): Recurrence = Recurrence.parse(spec)

    fun daysLabel(habit: Habit): String = Recurrence.decode(habit.recurrenceRule).label()

    fun daysLabel(rule: String): String = Recurrence.decode(rule).label()
}
