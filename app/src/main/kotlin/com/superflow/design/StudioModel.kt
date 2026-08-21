package com.superflow.design

/**
 * The Studio surface, as data (plan 11.4).
 *
 * Studio is the merge of three screens that were never really three things:
 * Coach was a chat, Blueprint was a document pipeline, and the AI Engine was
 * a settings page wearing an activity. All three are the same act — telling
 * the app what to do and watching it happen — so they become one workspace
 * with one transcript.
 *
 * The merge is mostly a question of *ordering and grouping*: which of a
 * hundred rows belong together, what gets a header, what collapses, what an
 * empty Studio should offer instead of a blinking cursor. That is decidable
 * without a screen, so it is decided here and tested.
 *
 * No Android imports.
 */
object StudioModel {

    // ------------------------------------------------------------- transcript

    /** Who produced a turn. */
    enum class Speaker { USER, ASSISTANT, SYSTEM }

    /**
     * How a command turned out. Drives the inline status chip on a turn.
     *
     * [PENDING] is distinct from [RUNNING]: pending means we have parsed a
     * command and are waiting on a confirmation, running means it is
     * actually executing. Showing a spinner for a turn that is really
     * waiting on the user is how people sit staring at a screen.
     */
    enum class RunState { NONE, PENDING, RUNNING, DONE, FAILED, UNDONE }

    /**
     * One entry in the Studio transcript.
     *
     * [actions] are capability names the turn ran. [groupId] ties a reply to
     * the undo group the command bus created for it, which is what makes an
     * "Undo" affordance on the message itself possible rather than making
     * people dig through Activity.
     */
    data class Turn(
        val id: String,
        val speaker: Speaker,
        val text: String,
        val at: Long,
        val meta: String = "",
        val actions: List<String> = emptyList(),
        val groupId: String? = null,
        val state: RunState = RunState.NONE,
        /** Route that produced the turn: "local", "cloud", a provider name. */
        val route: String = "",
    ) {
        /** A turn worth offering an undo on: it changed something, and stuck. */
        val undoable: Boolean get() = groupId != null && state == RunState.DONE

        /** Whether the turn is still in flight, for the typing indicator. */
        val busy: Boolean get() = state == RunState.RUNNING || state == RunState.PENDING
    }

    /**
     * A rendered row. The transcript is not a flat list of turns: it grows
     * date separators, collapses long tool output, and lifts the status pill
     * and quick actions to the top.
     */
    sealed interface Row {
        val key: String

        /** Compact status pill: control mode plus provider. */
        data class Status(
            val title: String,
            val detail: String,
            val actionLabel: String,
            val active: Boolean,
        ) : Row {
            override val key = "status"
        }

        /** Horizontally scrolling quick-action chips. */
        data class QuickActions(val items: List<QuickAction>) : Row {
            override val key = "quick"
        }

        /** Day separator inside the transcript. */
        data class DateBreak(val label: String) : Row {
            override val key = "date:$label"
        }

        /** A conversation turn. */
        data class Message(val turn: Turn, val showAvatar: Boolean) : Row {
            override val key = "turn:${turn.id}"
        }

        /** A blueprint project surfaced as a card inside the transcript. */
        data class Project(
            val id: String,
            val name: String,
            val detail: String,
            val progress: Int,
        ) : Row {
            override val key = "project:$id"
        }

        /** Suggested openers, shown only when there is nothing to read. */
        data class Suggestions(val items: List<String>) : Row {
            override val key = "suggestions"
        }

        /** The coaching card, for an otherwise empty Studio. */
        data class Coach(val text: String) : Row {
            override val key = "coach"
        }

        /** "N earlier messages" — the fold above a long history. */
        data class OlderFold(val hidden: Int) : Row {
            override val key = "fold"
        }
    }

    /** A quick-action chip (11.4). */
    data class QuickAction(val id: String, val label: String, val prompt: String)

    /**
     * The default chip set.
     *
     * Each is a real prompt, not a mode switch: tapping one fills the input
     * with something a person could have typed, so the chip teaches what
     * Studio understands instead of hiding it behind a button.
     */
    val quickActions: List<QuickAction> = listOf(
        QuickAction("blueprint", "Blueprint", "Open Blueprint Studio"),
        QuickAction("audit", "Audit", "Audit my system and tell me what is weak"),
        QuickAction("plan", "Plan", "Plan my week around what I actually did last week"),
        QuickAction("simplify", "Simplify", "What should I drop?"),
        QuickAction("recover", "Recover", "I have missed a few days. Where do I restart?"),
    )

    // ------------------------------------------------------------- assembly

    /** How many turns to show before folding the rest away. */
    const val VISIBLE_TURNS = 40

    /**
     * Builds the Studio row list.
     *
     * Ordering is fixed: status, quick actions, then either the empty-state
     * pair (suggestions plus coach card) or the transcript. Projects sit
     * directly under the quick actions, because a running blueprint is
     * context for whatever you are about to type, not history.
     *
     * @param turns oldest first.
     */
    fun rows(
        status: Row.Status,
        turns: List<Turn>,
        projects: List<Row.Project> = emptyList(),
        suggestions: List<String> = emptyList(),
        coach: String = "",
        dayLabel: (Long) -> String = { "" },
        visibleTurns: Int = VISIBLE_TURNS,
    ): List<Row> {
        val out = ArrayList<Row>()
        out.add(status)
        out.add(Row.QuickActions(quickActions))
        projects.forEach { out.add(it) }

        if (turns.isEmpty()) {
            if (suggestions.isNotEmpty()) out.add(Row.Suggestions(suggestions))
            if (coach.isNotBlank()) out.add(Row.Coach(coach))
            return out
        }

        val limit = visibleTurns.coerceAtLeast(1)
        val hidden = (turns.size - limit).coerceAtLeast(0)
        if (hidden > 0) out.add(Row.OlderFold(hidden))
        val shown = if (hidden > 0) turns.takeLast(limit) else turns

        var lastDay = ""
        var lastSpeaker: Speaker? = null
        shown.forEach { turn ->
            val day = dayLabel(turn.at)
            if (day.isNotBlank() && day != lastDay) {
                out.add(Row.DateBreak(day))
                lastDay = day
                // A separator resets the run, so the first turn after a date
                // break gets its avatar back even if the same speaker
                // continues across midnight.
                lastSpeaker = null
            }
            out.add(Row.Message(turn, showAvatar = turn.speaker != lastSpeaker))
            lastSpeaker = turn.speaker
        }
        return out
    }

    /** Whether the composer should show a typing indicator. */
    fun typing(turns: List<Turn>, sending: Boolean): Boolean =
        sending || turns.lastOrNull()?.busy == true

    // ------------------------------------------------------- message actions

    /** Context-menu actions on a turn (11.4). */
    enum class MessageAction(val key: String, val label: String) {
        COPY("copy", "Copy"),
        UNDO("undo", "Undo"),
        EXPLAIN("explain", "Explain"),
        RETRY("retry", "Try again"),
        ;
    }

    /**
     * Which actions a turn offers.
     *
     * Undo only where there is something to undo; retry only on the user's
     * own text or on a failure, because "try again" on a successful
     * assistant reply would silently run its commands a second time.
     */
    fun actionsFor(turn: Turn): List<MessageAction> {
        val out = ArrayList<MessageAction>()
        if (turn.text.isNotBlank()) out.add(MessageAction.COPY)
        if (turn.undoable) out.add(MessageAction.UNDO)
        if (turn.speaker == Speaker.ASSISTANT && turn.actions.isNotEmpty()) {
            out.add(MessageAction.EXPLAIN)
        }
        if (turn.speaker == Speaker.USER || turn.state == RunState.FAILED) {
            out.add(MessageAction.RETRY)
        }
        return out
    }

    /**
     * The short status chip text for a turn, or null when there is nothing
     * worth saying. Silence is the common case and the right default; a
     * chip on every message turns the transcript into a dashboard.
     */
    fun statusChip(turn: Turn): String? = when (turn.state) {
        RunState.NONE -> null
        RunState.PENDING -> "Waiting for you"
        RunState.RUNNING -> "Running"
        RunState.DONE -> when (turn.actions.size) {
            0 -> null
            1 -> "Ran ${turn.actions.first()}"
            else -> "Ran ${turn.actions.size} steps"
        }
        RunState.FAILED -> "Failed"
        RunState.UNDONE -> "Undone"
    }

    /**
     * Colour role for the status chip, named symbolically so the design
     * layer stays free of Android colour types.
     */
    fun statusRole(state: RunState): String = when (state) {
        RunState.FAILED -> "error"
        RunState.DONE -> "success"
        RunState.RUNNING, RunState.PENDING -> "primary"
        RunState.UNDONE -> "caution"
        RunState.NONE -> "neutral"
    }

    // ------------------------------------------------------------- composer

    /** Maximum characters the composer accepts in one turn. */
    const val MAX_INPUT = 4000

    /** Show the counter only when the limit is close enough to matter. */
    const val COUNTER_AT = 3600

    fun showCounter(length: Int): Boolean = length >= COUNTER_AT

    fun canSend(text: String, busy: Boolean): Boolean =
        !busy && text.isNotBlank() && text.length <= MAX_INPUT

    /**
     * The composer's placeholder.
     *
     * It names the mode, because "Message" tells you nothing about whether
     * this thing can act. When Full Control is off, the placeholder says so
     * rather than letting a person discover the limit by hitting it.
     */
    fun placeholder(fullControl: Boolean, cloud: Boolean): String = when {
        fullControl -> "Tell Studio what to do"
        cloud -> "Ask Studio, or tell it what to change"
        else -> "Ask Studio (local only)"
    }

    // ---------------------------------------------------------- voice input

    /**
     * Waveform bar heights for the live voice visualiser, in 0..1.
     *
     * Real amplitude arrives faster than a person can read it, so the
     * displayed bar is smoothed toward the sample: sudden silence should
     * settle rather than snap, or the waveform reads as broken.
     *
     * @param amplitudes newest last, in 0..1.
     * @param bars how many bars the visualiser draws.
     */
    fun waveform(amplitudes: List<Float>, bars: Int, smoothing: Float = 0.35f): List<Float> {
        if (bars <= 0) return emptyList()
        val recent = amplitudes.takeLast(bars)
        val padded = List(bars - recent.size) { 0f } + recent
        val out = ArrayList<Float>(bars)
        var previous = 0f
        padded.forEach { raw ->
            val v = raw.coerceIn(0f, 1f)
            val smoothed = previous + (v - previous) * (1f - smoothing)
            // A bar of literally zero height disappears, and a waveform with
            // gaps in it reads as dropped audio rather than as quiet.
            previous = smoothed
            out.add(smoothed.coerceIn(MIN_BAR, 1f))
        }
        return out
    }

    const val MIN_BAR = 0.06f
}
