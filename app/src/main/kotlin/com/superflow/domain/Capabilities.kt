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

    const val CATALOG_VERSION = 2

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
            "evolve_identity", "Record an identity evolution with reason",
            listOf("id" to "string", "newStatement" to "string", "reason" to "string"), Risk.LOW
        ) { c ->
            val old = c.repo.identity(c.str("id")) ?: return@Capability CommandResult.fail("Identity not found")
            val newStatement = c.str("newStatement").trim()
            if (newStatement.isBlank()) return@Capability CommandResult.fail("A new statement is required")
            val allCheckIns = c.repo.checkIns()
            val votes = allCheckIns.count { it.isSuccess }
            val evo = IdentityEvolution(
                previousStatement = old.statement,
                newStatement = newStatement,
                reason = c.str("reason"),
                votesAtEvolution = votes,
                date = c.date()
            )
            val updated = old.copy(
                statement = newStatement,
                evolutionHistory = old.evolutionHistory + evo
            )
            c.repo.saveIdentity(updated)
            val id = c.bus.record(c.actor, "evolve_identity",
                "Evolved identity: " + old.statement + " -> " + newStatement,
                jsonOf("previousStatement" to old.statement, "newStatement" to newStatement),
                undoRestore("identity", Serial.of(old)), c.groupId)
            okResult("Identity evolved. " + newStatement, null, id)
        },

        Capability(
            "add_identity_evidence", "Add qualitative evidence to an identity",
            listOf("identityId" to "string", "text" to "string", "sourceHabitId" to "string"), Risk.LOW
        ) { c ->
            val identityId = c.str("identityId")
            if (c.repo.identity(identityId) == null) return@Capability CommandResult.fail("Identity not found")
            val text = c.str("text").trim()
            if (text.isBlank()) return@Capability CommandResult.fail("Evidence text is required")
            val evidence = IdentityEvidence(
                identityId = identityId,
                text = text,
                sourceHabitId = c.strOrNull("sourceHabitId"),
                date = c.date()
            )
            c.repo.saveEvidence(evidence)
            val id = c.bus.record(c.actor, "add_identity_evidence",
                "Added evidence to identity: $text", Serial.of(evidence),
                undoDelete("evidence", evidence.id), c.groupId)
            okResult("Evidence recorded. $text", jsonOf("id" to evidence.id), id)
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
            "add_goal_milestone", "Add a measurable milestone to a goal",
            listOf("goalId" to "string", "title" to "string"), Risk.LOW
        ) { c ->
            val goal = c.repo.goal(c.str("goalId")) ?: return@Capability CommandResult.fail("Goal not found")
            val title = c.str("title").trim()
            if (title.isBlank()) return@Capability CommandResult.fail("Milestone title is required")
            val milestone = GoalMilestone(title = title)
            val updated = goal.copy(milestones = goal.milestones + milestone)
            c.repo.saveGoal(updated)
            val id = c.bus.record(c.actor, "add_goal_milestone",
                "Added milestone $title to goal ${goal.title}",
                jsonOf("goalId" to goal.id, "milestoneId" to milestone.id),
                undoRestore("goal", Serial.of(goal)), c.groupId)
            okResult("Milestone added to " + goal.title, jsonOf("milestoneId" to milestone.id), id)
        },

        Capability(
            "complete_goal_milestone", "Mark a milestone as achieved",
            listOf("goalId" to "string", "milestoneId" to "string"), Risk.LOW
        ) { c ->
            val goal = c.repo.goal(c.str("goalId")) ?: return@Capability CommandResult.fail("Goal not found")
            val milestoneId = c.str("milestoneId")
            val idx = goal.milestones.indexOfFirst { it.id == milestoneId }
            if (idx < 0) return@Capability CommandResult.fail("Milestone not found")
            val milestones = goal.milestones.toMutableList()
            milestones[idx] = milestones[idx].copy(achieved = true, achievedDate = c.date())
            val updated = goal.copy(milestones = milestones)
            c.repo.saveGoal(updated)
            val id = c.bus.record(c.actor, "complete_goal_milestone",
                "Completed milestone: " + milestones[idx].title,
                null, undoRestore("goal", Serial.of(goal)), c.groupId)
            okResult("Milestone achieved! " + milestones[idx].title, null, id)
        },

        Capability(
            "update_goal_metric", "Update the current metric value for a goal",
            listOf("goalId" to "string", "value" to "double", "unit" to "string"), Risk.LOW
        ) { c ->
            val goal = c.repo.goal(c.str("goalId")) ?: return@Capability CommandResult.fail("Goal not found")
            val value = c.dbl("value", 0.0)
            val unit = c.str("unit", goal.metricUnit)
            val updated = goal.copy(currentMetricValue = value, metricUnit = unit)
            c.repo.saveGoal(updated)
            val id = c.bus.record(c.actor, "update_goal_metric",
                "Updated metric for " + goal.title + ": $value $unit",
                null, undoRestore("goal", Serial.of(goal)), c.groupId)
            okResult("Goal metric updated: $value $unit", null, id)
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
        },

        /* ---------------------------------------------- obstacle surfacing (§10) */

        Capability(
            "activate_obstacle_plan", "Record that an obstacle plan was used",
            listOf("id" to "string", "worked" to "bool"), Risk.LOW
        ) { c ->
            val old = c.repo.obstacles().firstOrNull { it.id == c.str("id") }
                ?: return@Capability CommandResult.fail("Obstacle plan not found")
            val today = c.date()
            val updated = old.copy(timesUsed = old.timesUsed + 1, lastUsed = today,
                effectiveness = if (c.args.isNull("worked")) old.effectiveness
                else c.bool("worked", true).let { if (it) 4 else 2 })
            c.repo.saveObstacle(updated)
            val id = c.bus.record(c.actor, "activate_obstacle_plan",
                "Used obstacle plan: If ${old.ifText}, then ${old.thenText}",
                Serial.of(updated), undoRestore("obstacle", Serial.of(old)), c.groupId)
            okResult("Obstacle plan used (${updated.timesUsed}x total)", null, id)
        },

        Capability(
            "rate_obstacle_plan", "Rate whether an obstacle plan worked (1-5)",
            listOf("id" to "string", "rating" to "1-5"), Risk.LOW
        ) { c ->
            val old = c.repo.obstacles().firstOrNull { it.id == c.str("id") }
                ?: return@Capability CommandResult.fail("Obstacle plan not found")
            val rating = c.int("rating", 3).coerceIn(1, 5)
            val updated = old.copy(effectiveness = rating)
            c.repo.saveObstacle(updated)
            val id = c.bus.record(c.actor, "rate_obstacle_plan",
                "Obstacle plan rated: $rating/5", Serial.of(updated),
                undoRestore("obstacle", Serial.of(old)), c.groupId)
            okResult("Obstacle plan rated: $rating/5", null, id)
        },

        /* -------------------------------------------------- Four Laws living */

        Capability(
            "rate_reward", "Rate a habit reward satisfaction (1-5)",
            listOf("habit" to "id or title", "rating" to "1-5"), Risk.LOW
        ) { c ->
            val h = resolveHabit(c) ?: return@Capability CommandResult.fail("Habit not found")
            val rating = c.int("rating", 3).coerceIn(1, 5)
            val updated = h.copy(rewardSatisfaction = rating, rewardLastRated = c.date())
            c.repo.saveHabit(updated)
            val id = c.bus.record(c.actor, "rate_reward",
                "Rated reward for habit: $rating/5",
                null, undoRestore("habit", Serial.of(h)), c.groupId)
            okResult("Reward satisfaction recorded: $rating/5", null, id)
        },

        Capability(
            "rate_reframe", "Rate whether a reframe helped",
            listOf("habit" to "id or title", "helpful" to "bool"), Risk.LOW
        ) { c ->
            val h = resolveHabit(c) ?: return@Capability CommandResult.fail("Habit not found")
            val helpful = c.bool("helpful", true)
            val updated = h.copy(reframeHelpful = helpful)
            c.repo.saveHabit(updated)
            val tag = if (helpful) "helpful" else "not helpful"
            val id = c.bus.record(c.actor, "rate_reframe",
                "Reframe rated as $tag", null,
                undoRestore("habit", Serial.of(h)), c.groupId)
            okResult("Reframe rated as $tag", null, id)
        },

        Capability(
            "rate_bundle", "Rate temptation bundle effectiveness (1-5)",
            listOf("habit" to "id or title", "rating" to "1-5"), Risk.LOW
        ) { c ->
            val h = resolveHabit(c) ?: return@Capability CommandResult.fail("Habit not found")
            val rating = c.int("rating", 3).coerceIn(1, 5)
            val updated = h.copy(bundleEffectiveness = rating)
            c.repo.saveHabit(updated)
            val id = c.bus.record(c.actor, "rate_bundle",
                "Rated bundle: $rating/5", null,
                undoRestore("habit", Serial.of(h)), c.groupId)
            okResult("Bundle effectiveness recorded: $rating/5", null, id)
        },

        Capability(
            "update_four_laws", "Update any four-laws field post-design",
            listOf("habit" to "id or title", "field" to "string", "value" to "string"), Risk.LOW
        ) { c ->
            val old = resolveHabit(c) ?: return@Capability CommandResult.fail("Habit not found")
            val field = c.str("field").trim()
            val value = c.str("value")
            val updated = when (field.lowercase()) {
                "benefit" -> old.copy(benefit = value)
                "temptationbundle", "bundle" -> old.copy(temptationBundle = value)
                "reframe" -> old.copy(reframe = value)
                "frictionplan", "friction" -> old.copy(frictionPlan = value)
                "environmentprep", "environment", "prep" -> old.copy(environmentPrep = value)
                "reward" -> old.copy(reward = value)
                "rewardSatisfaction" -> old.copy(rewardSatisfaction = value.toIntOrNull()?.coerceIn(1, 5))
                "reframeHelpful" -> old.copy(reframeHelpful = value.equalsTrue())
                "bundleEffectiveness" -> old.copy(bundleEffectiveness = value.toIntOrNull()?.coerceIn(1, 5))
                "frictionplanactive" -> old.copy(frictionPlanActive = value.equalsTrue())
                "environmentprepremindertime" -> old.copy(environmentPrepReminderTime = value)
                else -> return@Capability CommandResult.fail("Unknown four-laws field: $field")
            }
            c.repo.saveHabit(updated)
            val id = c.bus.record(c.actor, "update_four_laws",
                "Updated $field for habit", null,
                undoRestore("habit", Serial.of(old)), c.groupId)
            okResult("Four Laws field updated: $field", null, id)
        },

        /* ----------------------------------------------- Adaptive Ladder */

        Capability(
            "evolve_ladder", "Record a ladder level change with reason",
            listOf("habit" to "id or title", "level" to "TINY|MINIMUM|STANDARD|STRETCH",
                "newText" to "string", "reason" to "string"), Risk.LOW
        ) { c ->
            val old = resolveHabit(c) ?: return@Capability CommandResult.fail("Habit not found")
            val level = Level.from(c.str("level", "STANDARD"))
            val newText = c.str("newText").trim()
            val reason = c.str("reason").trim()
            if (newText.isBlank()) return@Capability CommandResult.fail("New ladder text is required")
            val previousText = old.levelText(level)
            val evo = LadderEvolution(level = level, previousText = previousText,
                newText = newText, reason = reason, date = c.date())
            val updated = old.copy(ladderHistory = old.ladderHistory + evo)
            val finalUpdated = when (level) {
                Level.TINY -> updated.copy(tinyStart = newText)
                Level.MINIMUM -> updated.copy(minimumVersion = newText)
                Level.STANDARD -> updated.copy(standardVersion = newText)
                Level.STRETCH -> updated.copy(stretchVersion = newText)
            }
            c.repo.saveHabit(finalUpdated)
            val id = c.bus.record(c.actor, "evolve_ladder",
                "Evolved ladder for habit: $level $previousText -> $newText",
                jsonOf("level" to level.name, "previousText" to previousText, "newText" to newText),
                undoRestore("habit", Serial.of(old)), c.groupId)
            okResult("Ladder evolved: $level now $newText", null, id)
        },

        /* ------------------------------------------------ Capacity management */

        Capability(
            "set_habit_capacity", "Set estimated minutes and difficulty for a habit",
            listOf("habit" to "id or title", "estimatedMinutes" to "int", "difficulty" to "1-5"),
            Risk.LOW
        ) { c ->
            val old = resolveHabit(c) ?: return@Capability CommandResult.fail("Habit not found")
            val mins = c.int("estimatedMinutes", old.estimatedMinutes).coerceAtLeast(1)
            val diff = c.int("difficulty", old.difficultyRating).coerceIn(1, 5)
            val updated = old.copy(estimatedMinutes = mins, difficultyRating = diff)
            c.repo.saveHabit(updated)
            val id = c.bus.record(c.actor, "set_habit_capacity",
                "Set capacity for habit: ${mins}min, difficulty $diff",
                null, undoRestore("habit", Serial.of(old)), c.groupId)
            okResult("Capacity set: ${mins}min, difficulty $diff", null, id)
        },

        Capability(
            "get_daily_load", "Calculate total cognitive load for a day",
            listOf("date" to "yyyy-MM-dd"), Risk.LOW
        ) { c ->
            val date = c.localDate()
            val habits = c.repo.habitsForDay(date)
            if (habits.isEmpty()) return@Capability okResult("No habits scheduled. Load: 0.")
            val totalMinutes = habits.sumOf { it.estimatedMinutes }
            val avgDiff = habits.map { it.difficultyRating }.average()
            val loadScore = habits.size * avgDiff
            val color = when {
                loadScore < 15 -> "green"
                loadScore < 30 -> "amber"
                else -> "coral"
            }
            okResult("Load: ${habits.size} habits, ~${totalMinutes}min, avg diff %.1f/5 (${color})".format(avgDiff))
        }
    )

    /* -------------------------------------------------------- check-in layer */

    private fun checkInCaps() = listOf(
        Capability("check_in", "Record a completion at Tiny, Minimum, Standard or Stretch",
            listOf("habit" to "id or title", "level" to "TINY|MINIMUM|STANDARD|STRETCH",
                "amount" to "number", "date" to "yyyy-MM-dd", "note" to "string",
                "contextTags" to "array of strings", "quality" to "1-3",
                "difficulty" to "1-5"), Risk.LOW) { c ->
            val h = resolveHabit(c) ?: return@Capability CommandResult.fail("Habit not found")
            val date = c.date()
            val prev = c.repo.checkIn(h.id, date)
            val level = Level.from(c.str("level", "STANDARD"))
            val tags = c.args.optJSONArray("contextTags")?.let { arr ->
                (0 until arr.length()).mapNotNull { arr.optString(it, "").trim().ifBlank { null } }
            } ?: emptyList()
            val quality = if (c.args.isNull("quality")) null else c.int("quality", 0).coerceIn(1, 3)
            val difficulty = if (c.args.isNull("difficulty")) null else c.int("difficulty", 0).coerceIn(1, 5)
            val amount = c.dbl("amount", 0.0)
            val ci = CheckIn(
                habitId = h.id, date = date,
                result = if (h.mode == HabitMode.REDUCE) CheckInResult.RESISTED else CheckInResult.DONE,
                level = level, amount = amount, note = c.str("note"),
                contextTags = tags,
                actualAmount = if (amount > 0 || !c.args.isNull("amount")) amount else null,
                actualDurationMinutes = if (c.args.isNull("duration")) null else c.int("duration", 0),
                qualityRating = quality,
                difficultyRating = difficulty
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
            listOf("habit" to "id or title", "date" to "yyyy-MM-dd", "reason" to "string",
                "detail" to "string"), Risk.LOW) { c ->
            val h = resolveHabit(c) ?: return@Capability CommandResult.fail("Habit not found")
            val date = c.date()
            val prev = c.repo.checkIn(h.id, date)
            val result = if (h.mode == HabitMode.REDUCE) CheckInResult.SLIPPED else CheckInResult.MISSED
            val reason = c.str("reason").trim()
            val detail = c.str("detail").trim()
            val ci = CheckIn(habitId = h.id, date = date, result = result, note = c.str("note"),
                missReason = reason.ifBlank { null },
                missReasonDetail = detail.ifBlank { null })
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
        },

        /* ------------------------------------------- rich check-in data (§7) */

        Capability(
            "rate_checkin_difficulty", "Rate how hard a check-in was (1-5)",
            listOf("habit" to "id or title", "rating" to "1-5", "date" to "yyyy-MM-dd"), Risk.LOW
        ) { c ->
            val h = resolveHabit(c) ?: return@Capability CommandResult.fail("Habit not found")
            val date = c.date()
            val ci = c.repo.checkIn(h.id, date)
                ?: return@Capability CommandResult.fail("No check-in found for this habit on $date")
            val rating = c.int("rating", 3).coerceIn(1, 5)
            val updated = ci.copy(difficultyRating = rating)
            c.repo.saveCheckIn(updated)
            val id = c.bus.record(c.actor, "rate_checkin_difficulty",
                "Difficulty rated: $rating/5", Serial.of(updated), null, c.groupId)
            okResult("Difficulty recorded: $rating/5", null, id)
        },

        Capability(
            "rate_checkin_quality", "Rate session quality (1-3 stars)",
            listOf("habit" to "id or title", "rating" to "1-3", "date" to "yyyy-MM-dd"), Risk.LOW
        ) { c ->
            val h = resolveHabit(c) ?: return@Capability CommandResult.fail("Habit not found")
            val date = c.date()
            val ci = c.repo.checkIn(h.id, date)
                ?: return@Capability CommandResult.fail("No check-in found for this habit on $date")
            val rating = c.int("rating", 2).coerceIn(1, 3)
            val updated = ci.copy(qualityRating = rating)
            c.repo.saveCheckIn(updated)
            val id = c.bus.record(c.actor, "rate_checkin_quality",
                "Quality rated: $rating/3", Serial.of(updated), null, c.groupId)
            okResult("Quality recorded: $rating/3", null, id)
        },

        Capability(
            "record_miss_reason", "Record why a habit was missed",
            listOf("habit" to "id or title", "reason" to "time|energy|forgot|motivation|circumstance|other",
                "date" to "yyyy-MM-dd", "detail" to "string"), Risk.LOW
        ) { c ->
            val h = resolveHabit(c) ?: return@Capability CommandResult.fail("Habit not found")
            val date = c.date()
            val ci = c.repo.checkIn(h.id, date)
                ?: return@Capability CommandResult.fail("No miss recorded for this habit on $date")
            val reason = c.str("reason").trim()
            if (reason.isBlank()) return@Capability CommandResult.fail("A miss reason is required")
            val detail = c.str("detail").trim()
            val updated = ci.copy(missReason = reason,
                missReasonDetail = detail.ifBlank { null })
            c.repo.saveCheckIn(updated)
            val id = c.bus.record(c.actor, "record_miss_reason",
                "Miss reason recorded: $reason", Serial.of(updated), null, c.groupId)
            okResult("Miss reason recorded: $reason", null, id)
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
        },

        /* ------------------------------------------------- linked focus (§6) */

        Capability(
            "set_focus_priority", "Mark a focus item as today's number-one priority",
            listOf("id" to "string", "priority" to "bool"), Risk.LOW
        ) { c ->
            val date = c.date()
            val items = c.repo.focusFor(date)
            val target = items.firstOrNull { it.id == c.str("id") }
                ?: return@Capability CommandResult.fail("Focus action not found")
            val priority = c.bool("priority", true)
            val updated = target.copy(isPriority = priority)
            c.repo.saveFocus(updated)
            val id = c.bus.record(c.actor, "set_focus_priority",
                if (priority) "Starred focus: ${target.title}" else "Unstarred focus: ${target.title}",
                Serial.of(updated), undoRestore("focus", Serial.of(target)), c.groupId)
            okResult(if (priority) "Today's #1: ${target.title}" else "Priority removed", null, id)
        },

        Capability(
            "carry_over_focus", "Move an undone focus item to tomorrow",
            listOf("id" to "string"), Risk.LOW
        ) { c ->
            val today = c.repo.clock.today()
            val iso = SfTime.format(today)
            val target = c.repo.focusFor(iso).firstOrNull { it.id == c.str("id") }
                ?: return@Capability CommandResult.fail("Focus action not found for today")
            if (target.done) return@Capability CommandResult.fail("It is already done — no need to carry it over")
            val tomorrow = SfTime.format(today.plusDays(1))
            val tomorrowItems = c.repo.focusFor(tomorrow)
            if (tomorrowItems.size >= 3) return@Capability CommandResult.fail("Tomorrow's focus is already full")
            val carried = target.copy(
                id = newId(),
                date = tomorrow,
                done = false,
                isPriority = false,
                carryOverCount = target.carryOverCount + 1
            )
            c.repo.saveFocus(carried)
            val id = c.bus.record(c.actor, "carry_over_focus",
                "Carried \"${target.title}\" to tomorrow (skipped ${carried.carryOverCount}x total)",
                Serial.of(carried), undoDelete("focus", carried.id), c.groupId)
            okResult("Carried \"${target.title}\" to tomorrow", null, id)
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

        /* ------------------------------------------- scorecard pipeline (§12) */

        Capability(
            "rescore_scorecard", "Re-evaluate a scorecard entry verdict",
            listOf("id" to "string", "verdict" to "-1|0|1", "note" to "string"), Risk.LOW
        ) { c ->
            val old = c.repo.scorecard().firstOrNull { it.id == c.str("id") }
                ?: return@Capability CommandResult.fail("Entry not found")
            val verdict = c.int("verdict", old.verdict).coerceIn(-1, 1)
            val updated = old.copy(verdict = verdict, note = c.str("note", old.note))
            c.repo.saveScorecard(updated)
            val id = c.bus.record(c.actor, "rescore_scorecard",
                "Re-scored \"${old.routine}\": $verdict", Serial.of(updated),
                undoRestore("scorecard", Serial.of(old)), c.groupId)
            okResult("Scorecard updated", null, id)
        },

        Capability(
            "convert_scorecard_to_habit", "Turn a scorecard entry into a REDUCE or BUILD habit",
            listOf("id" to "string", "mode" to "BUILD|REDUCE", "systemId" to "string"), Risk.LOW
        ) { c ->
            val entry = c.repo.scorecard().firstOrNull { it.id == c.str("id") }
                ?: return@Capability CommandResult.fail("Entry not found")
            val mode = runCatching { HabitMode.valueOf(c.str("mode", "REDUCE").uppercase()) }
                .getOrDefault(HabitMode.REDUCE)
            val existing = c.repo.habits(true)
            val h = Habit(
                systemId = c.strOrNull("systemId") ?: c.repo.systems().firstOrNull()?.id,
                title = entry.routine,
                mode = mode,
                tinyStart = if (mode == HabitMode.BUILD) entry.routine else "",
                standardVersion = entry.routine,
                recurrenceRule = "WEEKLY:1,2,3,4,5,6,7",
                startDate = SfTime.format(c.repo.clock.today()),
                colorSeed = existing.size % 6,
                orderIndex = existing.size
            )
            c.repo.saveHabit(h)
            val id = c.bus.record(c.actor, "convert_scorecard_to_habit",
                "Scorecard \"${entry.routine}\" became a ${mode.name} habit",
                Serial.of(h), undoDelete("habit", h.id), c.groupId)
            okResult("Created ${mode.name.lowercase()} habit: ${entry.routine}",
                jsonOf("id" to h.id), id)
        },

        /* ---------------------------------------------------- flows runnable */

        Capability(
            "run_flow", "Start guided flow execution for today",
            listOf("flowId" to "string"), Risk.LOW
        ) { c ->
            val flow = c.repo.flows().firstOrNull { it.id == c.str("flowId") }
                ?: c.repo.flows().firstOrNull { it.title.equals(c.str("flow"), true) }
                ?: return@Capability CommandResult.fail("Flow not found")
            val steps = c.repo.flowSteps(flow.id)
            if (steps.isEmpty()) return@Capability CommandResult.fail("This flow has no steps yet")
            val date = c.date()
            val done = steps.count { s ->
                s.habitId?.let { c.repo.checkIn(it, date)?.isSuccess == true } == true
            }
            val totalMin = flow.estimatedMinutes
            val stepList = steps.joinToString(" -> ") { it.title }
            val id = c.bus.record(c.actor, "run_flow",
                "Started flow \"${flow.title}\" (${steps.size} steps)", null, null, c.groupId)
            val msg = buildString {
                append("Flow: ").append(flow.title).append(" (").append(steps.size).append(" steps")
                if (totalMin > 0) append(", ~").append(totalMin).append(" min")
                append(")\n").append(stepList)
                if (done > 0) append("\n$done of ").append(steps.size).append(" already done today")
            }
            okResult(msg, jsonOf("flowId" to flow.id, "steps" to steps.size), id)
        },

        Capability(
            "complete_flow", "Mark a full flow as completed",
            listOf("flowId" to "string"), Risk.LOW
        ) { c ->
            val flow = c.repo.flows().firstOrNull { it.id == c.str("flowId") }
                ?: c.repo.flows().firstOrNull { it.title.equals(c.str("flow"), true) }
                ?: return@Capability CommandResult.fail("Flow not found")
            val steps = c.repo.flowSteps(flow.id)
            val date = c.date()
            var completed = 0
            val group = c.groupId ?: newId()
            for (s in steps) {
                val habitId = s.habitId ?: continue
                val prev = c.repo.checkIn(habitId, date)
                c.repo.saveCheckIn(CheckIn(
                    habitId = habitId, date = date, result = CheckInResult.DONE,
                    level = Level.STANDARD, note = "Flow: ${flow.title}"
                ))
                val undo = if (prev == null) jsonOf("kind" to "clearCheckIn", "habitId" to habitId, "date" to date)
                else undoRestore("checkin", Serial.of(prev))
                c.bus.record(c.actor, "complete_flow", "Flow step: ${s.title}", null, undo, group)
                completed++
            }
            val updated = flow.copy(
                completionCount = flow.completionCount + 1,
                estimatedMinutes = if (flow.estimatedMinutes == 0) {
                    steps.sumOf { it.durationMinutes }
                } else flow.estimatedMinutes
            )
            c.repo.saveFlow(updated)
            val id = c.bus.record(c.actor, "complete_flow",
                "Completed flow \"${flow.title}\" ($completed steps)", null, null, group)
            okResult("Flow \"${flow.title}\" complete. $completed steps done.", null, id)
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
                "identityEvidence" to "string", "data" to "string"), Risk.LOW) { c ->
            val kind = runCatching { ReviewKind.valueOf(c.str("kind", "WEEKLY").uppercase()) }
                .getOrDefault(ReviewKind.WEEKLY)
            val label = when (kind) {
                ReviewKind.WEEKLY -> "Week of ${SfTime.shortDay(
                    SfTime.startOfWeek(c.repo.clock.today()))}"
                ReviewKind.MONTHLY -> SfTime.monthLabel(c.repo.clock.today())
                ReviewKind.QUARTERLY ->
                    "Quarter ending ${SfTime.shortDay(c.repo.clock.today())}"
            }
            val previous = c.repo.reviews().firstOrNull()
            val r = Review(kind = kind, periodLabel = label, whatWorked = c.str("whatWorked"),
                whatDidnt = c.str("whatDidnt"), systemChange = c.str("systemChange"),
                identityEvidence = c.str("identityEvidence"),
                autoGeneratedData = c.str("data"), previousReviewId = previous?.id)
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
        },

        /* ------------------------------------------- review actions pipeline (§9) */

        Capability(
            "add_review_action_item", "Add a structured action item to a review",
            listOf("reviewId" to "string", "text" to "string", "linkedCommand" to "string"),
            Risk.LOW
        ) { c ->
            val review = c.repo.reviews().firstOrNull { it.id == c.str("reviewId") }
                ?: return@Capability CommandResult.fail("Review not found")
            val text = c.str("text").trim()
            if (text.isBlank()) return@Capability CommandResult.fail("Action text is required")
            val item = ReviewActionItem(text = text,
                linkedCommand = c.strOrNull("linkedCommand"))
            val updated = review.copy(actionItems = review.actionItems + item)
            c.repo.saveReview(updated)
            val id = c.bus.record(c.actor, "add_review_action_item",
                "Review action added: $text",
                jsonOf("reviewId" to review.id, "itemId" to item.id),
                undoRestore("review", Serial.of(review)), c.groupId)
            okResult("Action item added", jsonOf("itemId" to item.id), id)
        },

        Capability(
            "complete_review_action", "Mark a review action item as done",
            listOf("reviewId" to "string", "itemId" to "string", "outcome" to "string"),
            Risk.LOW
        ) { c ->
            val review = c.repo.reviews().firstOrNull { it.id == c.str("reviewId") }
                ?: return@Capability CommandResult.fail("Review not found")
            val itemId = c.str("itemId")
            val idx = review.actionItems.indexOfFirst { it.id == itemId }
            if (idx < 0) return@Capability CommandResult.fail("Action item not found")
            val items = review.actionItems.toMutableList()
            items[idx] = items[idx].copy(completed = true, completedDate = c.date(),
                outcome = c.strOrNull("outcome"))
            val updated = review.copy(actionItems = items)
            c.repo.saveReview(updated)
            val id = c.bus.record(c.actor, "complete_review_action",
                "Review action done: ${items[idx].text}", Serial.of(updated),
                undoRestore("review", Serial.of(review)), c.groupId)
            okResult("Action item completed", null, id)
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
