package com.superflow.ui

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.superflow.data.Habit
import com.superflow.data.HabitMode
import com.superflow.data.TrackType
import com.superflow.domain.Actor
import com.superflow.domain.Capabilities
import com.superflow.domain.CommandBus
import com.superflow.util.Dates
import com.superflow.util.jsonOf

/**
 * The Habit Designer.
 *
 * Five short sections - Meaning, Notice, Want, Start, Feel - covering the four
 * laws and their inversions, ending in a plain-language contract.
 */
class HabitDesignerActivity : Activity() {

    private lateinit var bus: CommandBus
    private lateinit var host: FrameLayout
    private var editing: Habit? = null
    private var section = 0

    private var mode = HabitMode.BUILD
    private var trackType = TrackType.BINARY
    private var daysMask = 0b1111111
    private var reminder = false
    private var protectedRoutine = false

    // Section inputs, retained across navigation.
    private val values = HashMap<String, String>()

    private val sections = listOf("Meaning", "Notice", "Want", "Start", "Feel", "Contract")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bus = CommandBus.get(this)
        intent.getStringExtra("habitId")?.let { id ->
            editing = bus.repo.habit(id)
            editing?.let { h ->
                mode = h.mode
                trackType = h.trackType
                daysMask = h.daysMask
                reminder = h.reminderEnabled
                protectedRoutine = h.protectedRoutine
                values["title"] = h.title
                values["tinyStart"] = h.tinyStart
                values["minimumVersion"] = h.minimumVersion
                values["standardVersion"] = h.standardVersion
                values["stretchVersion"] = h.stretchVersion
                values["cueTime"] = h.cueTime
                values["cuePlace"] = h.cuePlace
                values["anchorText"] = h.anchorText
                values["benefit"] = h.benefit
                values["temptationBundle"] = h.temptationBundle
                values["reframe"] = h.reframe
                values["frictionPlan"] = h.frictionPlan
                values["environmentPrep"] = h.environmentPrep
                values["reward"] = h.reward
                values["recoveryPlan"] = h.recoveryPlan
                values["unit"] = h.unit
                values["targetCount"] = h.targetCount.toString()
            }
        }
        host = FrameLayout(this).apply {
            setBackgroundColor(Palette.BG)
            layoutParams = lp(MATCH, MATCH)
        }
        setContentView(host)
        render()
    }

    private fun render() {
        host.removeAllViews()
        host.addView(build(), FrameLayout.LayoutParams(MATCH, MATCH))
    }

    private fun build(): View = scroller {
        setPadding(dp(20), dp(28), dp(20), dp(28))

        addView(body(sections[section].uppercase() + "  ·  ${section + 1}/${sections.size}",
            11f, Palette.ACCENT, bold = true))
        addView(spacer(8))

        when (section) {
            0 -> meaning()
            1 -> notice()
            2 -> want()
            3 -> start()
            4 -> feel()
            else -> contract()
        }

        addView(spacer(20))
        addView(row {
            if (section > 0) {
                addView(ghostButton("Back") { section--; render() }.apply {
                    layoutParams = lp(0, WRAP, 1f).apply { rightMargin = dp(8) }
                })
            }
            addView(primaryButton(if (section == sections.size - 1) "Save habit" else "Next") {
                if (section == sections.size - 1) save() else { section++; render() }
            }.apply { layoutParams = lp(0, WRAP, 1f) })
        })
        addView(ghostButton("Cancel") { finish() })
        addView(spacer(24))
    }

    /* ------------------------------------------------------------ sections */

    private fun LinearLayout.meaning() {
        addView(title(if (editing == null) "What is the habit?" else "Edit habit", 24f))
        addView(spacer(6))
        addView(body("Name it as an action you can start, not an outcome you hope for.",
            14f, Palette.INK_FAINT))
        addView(spacer(16))
        addView(label("Habit"))
        addView(input("title", "Walk for 10 minutes"))

        addView(label("Build or reduce"))
        addView(flowRow {
            addView(chip("Build a habit", active = mode == HabitMode.BUILD) {
                mode = HabitMode.BUILD; render()
            })
            addView(chip("Reduce a habit", active = mode == HabitMode.REDUCE,
                activeColor = Palette.WARM) { mode = HabitMode.REDUCE; render() })
        })
        if (mode == HabitMode.REDUCE) {
            addView(softCard(Palette.WARM_SOFT) {
                addView(body("Reduce mode inverts the four laws: hide the cue, expose the real " +
                        "cost, add friction, and make the payoff unsatisfying. It also asks for a " +
                        "positive replacement.", 13f, Palette.INK))
                addView(spacer(8))
                addView(body("If a behaviour involves dependence, self-harm or danger, please " +
                        "involve a qualified person. An app is not enough on its own.",
                    12f, Palette.WARM))
            })
        }

        addView(label("How is it measured?"))
        addView(flowRow {
            for (t in TrackType.values()) {
                addView(chip(t.name.lowercase().replaceFirstChar { it.uppercase() },
                    active = trackType == t) { trackType = t; render() })
            }
        })
        if (trackType != TrackType.BINARY) {
            addView(row {
                addView(input("targetCount", "10").apply {
                    layoutParams = lp(0, WRAP, 1f).apply { rightMargin = dp(8) }
                })
                addView(input("unit", if (trackType == TrackType.DURATION) "minutes" else "times")
                    .apply { layoutParams = lp(0, WRAP, 1f) })
            })
        }
    }

    private fun LinearLayout.notice() {
        addView(title("Make it obvious", 24f))
        addView(spacer(6))
        addView(body(if (mode == HabitMode.BUILD)
            "A stable cue beats motivation. Choose a time and place, or attach it to something " +
                    "you already do reliably."
        else
            "For a habit you want less of, the first move is removing the cue.",
            14f, Palette.INK_FAINT))
        addView(spacer(16))

        addView(label("At this time"))
        addView(input("cueTime", "07:30"))
        addView(label("In this place"))
        addView(input("cuePlace", "the kitchen"))
        addView(label("Or after this existing routine"))
        addView(input("anchorText", "breakfast"))
        addView(spacer(6))
        addView(body("Habit stacking: \"After [something reliable], I will [this].\"",
            13f, Palette.INK_FAINT))

        addView(label("Which days?"))
        addView(flowRow {
            for ((i, name) in listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").withIndex()) {
                val on = (daysMask shr i) and 1 == 1
                addView(chip(name, active = on) {
                    daysMask = daysMask xor (1 shl i)
                    if (daysMask == 0) daysMask = 1 shl i
                    render()
                })
            }
        })
        addView(row {
            addView(ghostButton("Daily") { daysMask = 0b1111111; render() }
                .apply { layoutParams = lp(0, WRAP, 1f).apply { rightMargin = dp(6) } })
            addView(ghostButton("Weekdays") { daysMask = 0b0011111; render() }
                .apply { layoutParams = lp(0, WRAP, 1f).apply { rightMargin = dp(6) } })
            addView(ghostButton("Weekends") { daysMask = 0b1100000; render() }
                .apply { layoutParams = lp(0, WRAP, 1f) })
        })
    }

    private fun LinearLayout.want() {
        addView(title(if (mode == HabitMode.BUILD) "Make it attractive" else "Make it unattractive", 24f))
        addView(spacer(6))
        addView(body(if (mode == HabitMode.BUILD)
            "Anticipation drives action. What will you genuinely look forward to?"
        else
            "Name the real cost honestly, without exaggeration or shame.",
            14f, Palette.INK_FAINT))
        addView(spacer(16))
        addView(label(if (mode == HabitMode.BUILD) "Immediate benefit" else "The real cost"))
        addView(input("benefit", if (mode == HabitMode.BUILD) "I feel awake and clear"
        else "I lose the evening and sleep badly"))
        addView(label("Pair it with something enjoyable"))
        addView(input("temptationBundle", "Only listen to that podcast while walking"))
        addView(label("A more useful way to see it"))
        addView(input("reframe", "I get to move, not I have to exercise", lines = 2))
    }

    private fun LinearLayout.start() {
        addView(title(if (mode == HabitMode.BUILD) "Make it easy" else "Make it difficult", 24f))
        addView(spacer(6))
        addView(body("The Habit Ladder gives you a version for every kind of day.",
            14f, Palette.INK_FAINT))
        addView(spacer(16))
        addView(label("Tiny start — about two minutes (required)"))
        addView(input("tinyStart", "Put on my shoes"))
        addView(label("Minimum — a real but reduced version"))
        addView(input("minimumVersion", "Walk to the corner"))
        addView(label("Standard — the normal version"))
        addView(input("standardVersion", "Walk for 10 minutes"))
        addView(label("Stretch — for a good day"))
        addView(input("stretchVersion", "Walk for 25 minutes"))
        addView(divider())
        addView(label(if (mode == HabitMode.BUILD) "Remove friction" else "Add friction"))
        addView(input("frictionPlan", if (mode == HabitMode.BUILD)
            "Leave my shoes by the door" else "Log out and delete the shortcut", lines = 2))
        addView(label("Prepare the environment"))
        addView(input("environmentPrep", "Set out my clothes tonight", lines = 2))
        addView(spacer(8))
        addView(row {
            addView(body("Protected routine", 14f, Palette.INK).apply {
                layoutParams = lp(0, WRAP, 1f)
            })
            addView(chip(if (protectedRoutine) "On" else "Off", active = protectedRoutine) {
                protectedRoutine = !protectedRoutine; render()
            })
        })
        addView(body("Protected routines survive Minimum Mode on low-capacity days.",
            12f, Palette.INK_FAINT))
    }

    private fun LinearLayout.feel() {
        addView(title(if (mode == HabitMode.BUILD) "Make it satisfying" else "Make it unsatisfying", 24f))
        addView(spacer(6))
        addView(body("The payoff has to arrive now. Delayed rewards do not close the loop.",
            14f, Palette.INK_FAINT))
        addView(spacer(16))
        addView(label("Immediate reward"))
        addView(input("reward", "Tick it off and enjoy my coffee"))
        addView(label("Recovery plan if you miss"))
        addView(input("recoveryPlan", "Return tomorrow with the tiny version. Never miss twice.",
            lines = 2))
        addView(divider())
        addView(row {
            addView(body("Remind me at the cue time", 14f, Palette.INK).apply {
                layoutParams = lp(0, WRAP, 1f)
            })
            addView(chip(if (reminder) "On" else "Off", active = reminder) {
                reminder = !reminder; render()
            })
        })
    }

    private fun LinearLayout.contract() {
        val h = draft()
        addView(title("Your contract", 24f))
        addView(spacer(6))
        addView(body("Read it once. If it sounds unrealistic, shrink it now rather than later.",
            14f, Palette.INK_FAINT))
        addView(spacer(16))
        addView(softCard(Palette.ACCENT_SOFT) {
            addView(body(h.contract(), 16f, Palette.INK))
        })
        addView(card {
            addView(body("Summary", 14f, Palette.INK, bold = true))
            addView(spacer(8))
            for ((k, v) in listOf(
                "Days" to Capabilities.daysLabel(daysMask),
                "Mode" to if (mode == HabitMode.BUILD) "Build" else "Reduce",
                "Tracking" to trackType.name.lowercase(),
                "Reminder" to if (reminder) "On at ${h.cueTime.ifBlank { "no time set" }}" else "Off",
                "Protected" to if (protectedRoutine) "Yes" else "No"
            )) {
                addView(row {
                    layoutParams = lp(MATCH, WRAP).apply { bottomMargin = dp(6) }
                    addView(body(k, 13f, Palette.INK_FAINT).apply { layoutParams = lp(dp(90), WRAP) })
                    addView(body(v, 13f, Palette.INK_SOFT).apply { layoutParams = lp(0, WRAP, 1f) })
                })
            }
        })
        if (h.tinyStart.isBlank()) {
            addView(softCard(Palette.WARM_SOFT) {
                addView(body("No tiny start yet. Habits without one break during a hard week.",
                    13f, Palette.WARM))
            })
        }
    }

    /* -------------------------------------------------------------- helper */

    private val editors = HashMap<String, EditText>()

    private fun LinearLayout.input(key: String, hint: String, lines: Int = 1): EditText {
        val existing = editors[key]
        val e = field(hint, values[key] ?: "", lines)
        e.setOnFocusChangeListener { _, focused ->
            if (!focused) values[key] = e.text.toString()
        }
        e.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                values[key] = s?.toString() ?: ""
            }
        })
        editors[key] = e
        return e
    }

    private fun v(key: String): String = values[key]?.trim() ?: ""

    private fun draft(): Habit = (editing ?: Habit(title = v("title"))).copy(
        title = v("title"),
        mode = mode,
        trackType = trackType,
        targetCount = v("targetCount").toIntOrNull() ?: 1,
        unit = v("unit"),
        cueTime = v("cueTime"),
        cuePlace = v("cuePlace"),
        anchorText = v("anchorText"),
        benefit = v("benefit"),
        temptationBundle = v("temptationBundle"),
        reframe = v("reframe"),
        tinyStart = v("tinyStart"),
        minimumVersion = v("minimumVersion"),
        standardVersion = v("standardVersion").ifBlank { v("title") },
        stretchVersion = v("stretchVersion"),
        frictionPlan = v("frictionPlan"),
        environmentPrep = v("environmentPrep"),
        reward = v("reward"),
        recoveryPlan = v("recoveryPlan"),
        daysMask = daysMask,
        reminderEnabled = reminder,
        protectedRoutine = protectedRoutine
    )

    private fun save() {
        if (v("title").isBlank()) {
            toast("The habit needs a name")
            section = 0
            render()
            return
        }
        if (v("cueTime").isNotBlank() && !Dates.isValidTime(v("cueTime"))) {
            toast("Cue time should look like 07:30")
            section = 1
            render()
            return
        }
        val args = jsonOf(
            "title" to v("title"),
            "tinyStart" to v("tinyStart"),
            "minimumVersion" to v("minimumVersion"),
            "standardVersion" to v("standardVersion").ifBlank { v("title") },
            "stretchVersion" to v("stretchVersion"),
            "cueTime" to v("cueTime"),
            "cuePlace" to v("cuePlace"),
            "anchorText" to v("anchorText"),
            "benefit" to v("benefit"),
            "temptationBundle" to v("temptationBundle"),
            "reframe" to v("reframe"),
            "frictionPlan" to v("frictionPlan"),
            "environmentPrep" to v("environmentPrep"),
            "reward" to v("reward"),
            "recoveryPlan" to v("recoveryPlan"),
            "unit" to v("unit"),
            "targetCount" to (v("targetCount").toIntOrNull() ?: 1),
            "mode" to mode.name,
            "trackType" to trackType.name,
            "days" to Capabilities.daysLabel(daysMask).lowercase().replace(" ", ""),
            "reminder" to reminder,
            "protected" to protectedRoutine
        )
        val res = if (editing == null) bus.execute("create_habit", args, Actor.USER)
        else bus.execute("update_habit", args.put("habit", editing!!.id), Actor.USER)

        if (res.ok) {
            com.superflow.notify.Reminders.rescheduleAll(this)
            toast(if (editing == null) "Habit created" else "Habit updated")
            finish()
        } else toast(res.message)
    }
}
