package com.superflow.domain

import com.superflow.data.model.AiMessage
import com.superflow.data.model.AuditEntry
import com.superflow.data.model.BlueprintProject
import com.superflow.data.model.Requirement
import com.superflow.data.model.RequirementStatus
import com.superflow.design.StudioModel

/**
 * Turns the stored conversation into the transcript [StudioModel] renders.
 *
 * The persisted shape is thin on purpose — an [AiMessage] is a role, some
 * text and a meta string — because that is all the agent needs to replay a
 * conversation to a model. The Studio screen needs rather more: which turns
 * changed something, whether the change stuck, what to offer an undo on.
 * All of that lives in the audit log, keyed by group.
 *
 * Correlating the two is the whole job of this file, and it is done here,
 * purely, rather than in a ViewModel, so the rules are visible and tested:
 *
 * - An assistant turn is matched to the audit entries written *for it*,
 *   within a short window after it was saved. Time is the only link the two
 *   tables share; the agent does not write its group id back onto the
 *   message. The window is deliberately tight, because attributing an
 *   unrelated later change to an earlier reply would offer an undo that
 *   destroys something the user never associated with that message.
 * - A group that has been entirely undone reads as [RunState.UNDONE], not
 *   as done-with-nothing-to-undo. Losing that distinction is how people end
 *   up pressing undo twice.
 */
object StudioMapper {

    /**
     * How long after an assistant message an audit entry may still be
     * considered part of that turn.
     *
     * Commands run synchronously inside `Agent.send`, so in practice the
     * gap is milliseconds; the allowance is for a slow device and a cloud
     * round trip finishing just after the reply was persisted. Fifteen
     * seconds is far longer than any real gap and far shorter than the
     * interval between two things a person typed.
     */
    const val ATTRIBUTION_WINDOW_MS = 15_000L

    /**
     * Builds the transcript.
     *
     * @param messages oldest first, as [com.superflow.data.Repository.messages]
     *   returns them.
     * @param audit newest first, as the repository returns it. Only entries
     *   carrying a group id can be attributed, since an ungrouped entry has
     *   no undo target.
     * @param pending id of a message currently in flight, if any.
     */
    fun turns(
        messages: List<AiMessage>,
        audit: List<AuditEntry> = emptyList(),
        pending: String? = null,
    ): List<StudioModel.Turn> {
        val grouped = audit
            .filter { it.groupId != null }
            .sortedBy { it.createdAt }

        return messages.map { m ->
            val speaker = speakerOf(m.role)
            if (speaker != StudioModel.Speaker.ASSISTANT) {
                return@map StudioModel.Turn(
                    id = m.id,
                    speaker = speaker,
                    text = m.text,
                    at = m.createdAt,
                    meta = "",
                    state = if (m.id == pending) StudioModel.RunState.RUNNING
                    else StudioModel.RunState.NONE,
                )
            }

            val window = grouped.filter {
                it.createdAt >= m.createdAt &&
                    it.createdAt - m.createdAt <= ATTRIBUTION_WINDOW_MS
            }
            // An assistant reply owns the *first* group written after it.
            // Taking every entry in the window would merge two replies'
            // work whenever a follow-up landed inside the same 15 seconds.
            val groupId = window.firstOrNull()?.groupId
            val entries = if (groupId == null) emptyList()
            else window.filter { it.groupId == groupId }

            StudioModel.Turn(
                id = m.id,
                speaker = speaker,
                text = m.text,
                at = m.createdAt,
                meta = routeLabel(m.meta),
                actions = entries.map { it.command }.distinct(),
                groupId = groupId,
                state = stateOf(entries, m.id == pending),
                route = m.meta,
            )
        }
    }

    /** Maps a stored role string onto a speaker, tolerating unknown values. */
    fun speakerOf(role: String): StudioModel.Speaker = when (role.lowercase()) {
        "user" -> StudioModel.Speaker.USER
        "assistant" -> StudioModel.Speaker.ASSISTANT
        else -> StudioModel.Speaker.SYSTEM
    }

    /**
     * The run state implied by a turn's audit entries.
     *
     * Partially undone counts as done: something the user reverted by hand,
     * one step at a time, still leaves the rest of the change in place, and
     * claiming otherwise would hide it.
     */
    fun stateOf(entries: List<AuditEntry>, running: Boolean): StudioModel.RunState = when {
        running -> StudioModel.RunState.RUNNING
        entries.isEmpty() -> StudioModel.RunState.NONE
        entries.all { it.undone } -> StudioModel.RunState.UNDONE
        else -> StudioModel.RunState.DONE
    }

    /**
     * Humanises the `meta` field, which the agent fills with a route name.
     *
     * "local-fallback" is the one worth spelling out: it means the cloud
     * call failed and the deterministic coordinator answered instead. A
     * person who does not know that reads a worse answer as the model
     * getting worse.
     */
    fun routeLabel(meta: String): String = when (meta.trim().lowercase()) {
        "" -> ""
        "local" -> "on device"
        "local-fallback" -> "on device — cloud unavailable"
        "cloud" -> "cloud"
        else -> meta
    }

    // ---------------------------------------------------------- status row

    /**
     * The status pill at the top of Studio.
     *
     * It answers one question — can this thing act, and through what — in
     * the two lines a person will actually read before typing.
     */
    fun status(
        fullControl: Boolean,
        localOnly: Boolean,
        cloudReady: Boolean,
        providerLabel: String,
        capabilityCount: Int,
    ): StudioModel.Row.Status {
        val engine = when {
            localOnly -> "Local coordinator only"
            cloudReady -> providerLabel
            else -> "Local coordinator — no cloud configured"
        }
        return StudioModel.Row.Status(
            title = if (fullControl) "Full Control active" else "Guided mode",
            detail = "$engine · $capabilityCount capabilities",
            actionLabel = if (fullControl) "Manage" else "Activate",
            active = fullControl,
        )
    }

    // -------------------------------------------------------- project rows

    /**
     * Blueprint projects, as transcript cards.
     *
     * Only unfinished work surfaces here. A shipped blueprint is history,
     * and history belongs in Blueprint Studio, not at the top of the thing
     * you are about to type into.
     */
    fun projects(
        projects: List<BlueprintProject>,
        limit: Int = MAX_PROJECT_CARDS,
        requirements: (String) -> List<Requirement>,
    ): List<StudioModel.Row.Project> =
        projects
            .filter { it.state != "ARCHIVED" }
            .take(limit.coerceAtLeast(0))
            .map { p ->
                val reqs = requirements(p.id)
                val done = reqs.count { done(it.status) }
                StudioModel.Row.Project(
                    id = p.id,
                    name = p.name,
                    detail = projectDetail(p, reqs.size, done),
                    progress = percent(done, reqs.size),
                )
            }

    const val MAX_PROJECT_CARDS = 2

    /** Whether a requirement counts as delivered for the progress ring. */
    fun done(status: RequirementStatus): Boolean =
        status == RequirementStatus.IMPLEMENTED || status == RequirementStatus.VERIFIED

    fun projectDetail(project: BlueprintProject, total: Int, done: Int): String = when {
        total == 0 -> "v${project.version} · no requirements yet"
        done == total -> "v${project.version} · all $total delivered"
        else -> "v${project.version} · $done of $total delivered"
    }

    /**
     * Integer percentage, rounded down, with the two ends reserved.
     *
     * Zero of anything is 0 and everything is 100; a value in between never
     * rounds to either, because a ring that reads 100% next to "3 of 4" is
     * the kind of small inconsistency that makes people stop trusting the
     * number.
     */
    fun percent(done: Int, total: Int): Int {
        if (total <= 0 || done <= 0) return 0
        if (done >= total) return 100
        return (done * 100 / total).coerceIn(1, 99)
    }
}
