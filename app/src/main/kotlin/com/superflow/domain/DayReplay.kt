package com.superflow.domain

import com.superflow.core.time.SfTime
import com.superflow.data.model.CheckIn
import com.superflow.data.model.EnergyLog
import com.superflow.data.model.FocusItem
import com.superflow.data.model.JournalEntry

/**
 * Day Replay (Plan B F2.2): reconstructs a past day from what was already
 * recorded, in one ordered stream.
 *
 * The purpose is honest reflection - "I keep skipping evening routines" is
 * visible in a replayed day in a way no aggregate chart shows. Everything is
 * derived from existing records; nothing new is stored. Pure function, no
 * Android imports, so fixtures test it directly.
 */
object DayReplay {

    /** What kind of thing happened at this point of the day. */
    enum class EventKind { CHECK_IN, MISS, SKIP, FOCUS_DONE, ENERGY, JOURNAL }

    /**
     * One moment in the replayed day.
     *
     * @param timestampMs wall-clock ordering key; events without a time
     *                    (focus pins) sort before everything that has one.
     */
    data class DayEvent(
        val kind: EventKind,
        val title: String,
        val subtitle: String = "",
        val timeLabel: String? = null,
        val timestampMs: Long,
    )

    /**
     * @param checkIns   all check-ins for the date (any result)
     * @param journal    journal entries written that date
     * @param focus      focus items pinned to that date
     * @param energy     energy logs recorded at that date's checkpoints
     * @param habitTitle resolves a habit id into its display title
     */
    fun build(
        checkIns: List<CheckIn>,
        journal: List<JournalEntry>,
        focus: List<FocusItem>,
        energy: List<EnergyLog>,
        habitTitle: (String) -> String = { it },
    ): List<DayEvent> {
        val events = ArrayList<DayEvent>()

        for (f in focus) {
            if (!f.done) continue
            // Focus items carry no clock time; they open the day's story.
            events.add(
                DayEvent(
                    kind = EventKind.FOCUS_DONE,
                    title = f.title,
                    subtitle = "Focus",
                    timeLabel = null,
                    timestampMs = 0L,
                )
            )
        }

        for (e in energy) {
            events.add(
                DayEvent(
                    kind = EventKind.ENERGY,
                    title = "Energy ${e.energy}/5",
                    subtitle = e.checkpoint.label + " checkpoint",
                    timeLabel = null,
                    timestampMs = checkpointOrder(e.checkpoint.name) * 1_000_000L,
                )
            )
        }

        for (c in checkIns) {
            val kind = when {
                c.isSuccess -> EventKind.CHECK_IN
                c.isMiss -> EventKind.MISS
                else -> EventKind.SKIP
            }
            val verb = when (kind) {
                EventKind.CHECK_IN ->
                    if (c.level == com.superflow.data.model.Level.TINY) "Showed up tiny"
                    else "Checked in"
                EventKind.MISS -> "Missed"
                else -> "Skipped"
            }
            events.add(
                DayEvent(
                    kind = kind,
                    title = "$verb · ${habitTitle(c.habitId)}",
                    subtitle = c.note.ifBlank { "" },
                    timeLabel = SfTime.clockLabel(c.createdAt),
                    timestampMs = c.createdAt,
                )
            )
        }

        for (j in journal) {
            events.add(
                DayEvent(
                    kind = EventKind.JOURNAL,
                    title = "Journal entry",
                    subtitle = j.content.lineSequence().firstOrNull()?.take(80) ?: "",
                    timeLabel = SfTime.clockLabel(j.createdAt),
                    timestampMs = j.createdAt,
                )
            )
        }

        return events.sortedWith(
            compareBy({ it.timestampMs }, { rank(it.kind) })
        )
    }

    /** Checkpoints have a natural day order even without a clock time. */
    private fun checkpointOrder(name: String): Int = when (name) {
        "MORNING" -> 1
        "MIDDAY", "AFTERNOON" -> 2
        else -> 3
    }

    /** Tie-break within the same millisecond: actions before reflections. */
    private fun rank(kind: EventKind): Int = when (kind) {
        EventKind.CHECK_IN, EventKind.MISS, EventKind.SKIP -> 0
        EventKind.FOCUS_DONE -> 1
        EventKind.ENERGY -> 2
        EventKind.JOURNAL -> 3
    }
}
