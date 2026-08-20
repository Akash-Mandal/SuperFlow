package com.superflow.ui.designer

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.superflow.R
import com.superflow.ai.Coordinator
import com.superflow.data.Repository
import com.superflow.data.model.Habit
import com.superflow.data.model.HabitMode
import com.superflow.data.model.Level
import com.superflow.data.model.TrackType
import com.superflow.domain.Actor
import com.superflow.domain.Capabilities
import com.superflow.core.schedule.Recurrence
import com.superflow.core.time.SfTime
import com.superflow.domain.CommandBus
import com.superflow.notify.Reminders
import com.superflow.ui.common.SfTheme
import com.superflow.ui.common.snack
import com.superflow.ui.common.visible
import com.superflow.util.Dates
import com.superflow.util.jsonOf

/**
 * The Habit Designer.
 *
 * Five short sections — Meaning, Notice, Want, Start, Feel — covering the four
 * laws and their inversions, ending in a plain-language contract.
 */
class HabitDesignerActivity : AppCompatActivity() {

    private lateinit var bus: CommandBus
    private lateinit var repo: Repository
    private lateinit var stepContent: LinearLayout
    private lateinit var progress: LinearProgressIndicator
    private lateinit var btnBack: MaterialButton
    private lateinit var btnNext: MaterialButton
    private lateinit var toolbar: MaterialToolbar

    private var editing: Habit? = null
    private var step = 0

    private val values = HashMap<String, String>()
    private var mode = HabitMode.BUILD
    private var trackType = TrackType.BINARY
    private var recurrence: Recurrence = Recurrence.EVERY_DAY
    private var reminder = false
    private var protectedRoutine = false

    private val steps = listOf("Meaning", "Notice", "Want", "Start", "Feel", "Contract")

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // Theme overlays must be merged before the first inflate.
        SfTheme.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_designer)
        bus = CommandBus.get(this)
        repo = Repository.get(this)

        toolbar = findViewById(R.id.toolbar)
        stepContent = findViewById(R.id.step_content)
        progress = findViewById(R.id.step_progress)
        btnBack = findViewById(R.id.btn_back)
        btnNext = findViewById(R.id.btn_next)

        intent.getStringExtra(EXTRA_HABIT_ID)?.let { loadExisting(it) }

        toolbar.setNavigationOnClickListener { confirmExit() }
        btnBack.setOnClickListener { if (step > 0) { step--; render() } }
        btnNext.setOnClickListener {
            if (step == steps.lastIndex) save() else if (validateStep()) { step++; render() }
        }

        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { v, insets ->
            v.updatePadding(top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top)
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.nav_bar)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.updatePadding(bottom = maxOf(bars.bottom, ime.bottom) +
                    resources.getDimensionPixelSize(R.dimen.space_m))
            insets
        }

        render()
    }

    private fun loadExisting(id: String) {
        val h = repo.habit(id) ?: return
        editing = h
        mode = h.mode
        trackType = h.trackType
        recurrence = Recurrence.decode(h.recurrenceRule)
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

    /* ---------------------------------------------------------------- render */

    private fun render() {
        stepContent.removeAllViews()
        toolbar.title = if (editing == null) getString(R.string.new_habit) else "Edit habit"
        progress.setProgressCompat(((step + 1) * 100) / steps.size, true)
        btnBack.visible(step > 0)
        btnNext.text = if (step == steps.lastIndex) getString(R.string.save) else getString(R.string.next)

        when (step) {
            0 -> meaning()
            1 -> notice()
            2 -> want()
            3 -> start()
            4 -> feel()
            else -> contract()
        }
        findViewById<androidx.core.widget.NestedScrollView>(R.id.scroll).scrollTo(0, 0)
    }

    private fun meaning() {
        header("Step 1 of 6 · Meaning", "What is the habit?",
            "Name it as an action you can start, not an outcome you hope for.")
        field("title", "Habit", "Walk for 10 minutes")

        label("BUILD OR REDUCE")
        chips(listOf("Build a habit" to (mode == HabitMode.BUILD),
            "Reduce a habit" to (mode == HabitMode.REDUCE))) { index ->
            mode = if (index == 0) HabitMode.BUILD else HabitMode.REDUCE
            render()
        }
        if (mode == HabitMode.REDUCE) {
            stepContent.addView(warmCard(
                "Reduce mode inverts the four laws: hide the cue, expose the real cost, add " +
                        "friction, and make the payoff unsatisfying. It also asks for a positive " +
                        "replacement.\n\nIf a behaviour involves dependence, self-harm or danger, " +
                        "please involve a qualified person. An app is not enough on its own."
            ))
        }

        label("HOW IS IT MEASURED?")
        chips(TrackType.values().map {
            it.name.lowercase().replaceFirstChar { c -> c.uppercase() } to (trackType == it)
        }) { index -> trackType = TrackType.values()[index]; render() }

        if (trackType != TrackType.BINARY) {
            field("targetCount", "Target", "10", numeric = true)
            field("unit", "Unit", if (trackType == TrackType.DURATION) "minutes" else "times")
        }
    }

    private fun notice() {
        header("Step 2 of 6 · Notice",
            if (mode == HabitMode.BUILD) "Make it obvious" else "Make it invisible",
            if (mode == HabitMode.BUILD)
                "A stable cue beats motivation. Choose a time and place, or attach it to " +
                        "something you already do reliably."
            else "For a habit you want less of, the first move is removing the cue.")

        timeField()
        field("cuePlace", "In this place", "the kitchen")
        field("anchorText", "Or after this existing routine", "breakfast")
        hint("Habit stacking: \"After [something reliable], I will [this].\"")

        label("HOW OFTEN?")
        val flexible = recurrence as? Recurrence.TimesPerWeek
        chips(listOf(
            "Set days" to (flexible == null),
            "Times a week" to (flexible != null)
        )) { index ->
            recurrence = if (index == 0) Recurrence.EVERY_DAY else Recurrence.TimesPerWeek(3)
            render()
        }

        if (flexible != null) {
            // A weekly quota: any day counts, so a flexible habit never
            // generates a false miss for "the wrong day".
            label("HOW MANY TIMES A WEEK?")
            val counts = (1..7).map { "$it×" to (flexible.times == it) }
            chips(counts) { index -> recurrence = Recurrence.TimesPerWeek(index + 1); render() }
            hint("Any day counts toward the quota. Missing a particular day is not a miss.")
        } else {
            val selected = (recurrence as? Recurrence.Weekly)?.days
                ?: setOf(1, 2, 3, 4, 5, 6, 7)
            val names = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
            val group = ChipGroup(this).apply {
                isSingleSelection = false
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            val chosen = selected.toMutableSet()
            names.forEachIndexed { i, name ->
                val day = i + 1
                group.addView(Chip(this).apply {
                    text = name
                    isCheckable = true
                    isChecked = day in chosen
                    setEnsureMinTouchTargetSize(false)
                    setOnCheckedChangeListener { _, checked ->
                        if (checked) chosen.add(day) else chosen.remove(day)
                        if (chosen.isEmpty()) { chosen.add(day); isChecked = true }
                        recurrence = Recurrence.Weekly(chosen.toSet())
                    }
                })
            }
            stepContent.addView(group)

            val presets = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            listOf(
                "Daily" to Recurrence.EVERY_DAY,
                "Weekdays" to Recurrence.WEEKDAYS,
                "Weekends" to Recurrence.WEEKENDS
            ).forEach { (label, rule) ->
                presets.addView(MaterialButton(
                    this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
                ).apply {
                    text = label
                    layoutParams = LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also { it.marginEnd = dp(6) }
                    setOnClickListener { recurrence = rule; render() }
                })
            }
            stepContent.addView(presets)
        }
    }

    private fun want() {
        header("Step 3 of 6 · Want",
            if (mode == HabitMode.BUILD) "Make it attractive" else "Make it unattractive",
            if (mode == HabitMode.BUILD)
                "Anticipation drives action. What will you genuinely look forward to?"
            else "Name the real cost honestly, without exaggeration or shame.")

        field("benefit",
            if (mode == HabitMode.BUILD) "Immediate benefit" else "The real cost",
            if (mode == HabitMode.BUILD) "I feel awake and clear"
            else "I lose the evening and sleep badly")
        field("temptationBundle", "Pair it with something enjoyable",
            "Only listen to that podcast while walking")
        field("reframe", "A more useful way to see it",
            "I get to move, not I have to exercise", lines = 2)
    }

    private fun start() {
        header("Step 4 of 6 · Start",
            if (mode == HabitMode.BUILD) "Make it easy" else "Make it difficult",
            "The Habit Ladder gives you a version for every kind of day.")

        field("tinyStart", "Tiny start — about two minutes", "Put on my shoes")
        val suggest = MaterialButton(
            this, null, com.google.android.material.R.attr.borderlessButtonStyle
        ).apply {
            text = "Suggest a tiny start"
            setOnClickListener {
                val title = v("title")
                if (title.isBlank()) {
                    findViewById<View>(R.id.root).snack("Name the habit first")
                } else {
                    values["tinyStart"] = Coordinator.defaultTinyStart(title)
                    render()
                }
            }
        }
        stepContent.addView(suggest)

        field("minimumVersion", "Minimum — a real but reduced version", "Walk to the corner")
        field("standardVersion", "Standard — the normal version", "Walk for 10 minutes")
        field("stretchVersion", "Stretch — for a good day", "Walk for 25 minutes")

        label(if (mode == HabitMode.BUILD) "REMOVE FRICTION" else "ADD FRICTION")
        field("frictionPlan", if (mode == HabitMode.BUILD) "Make it easier to start"
        else "Make it harder to start",
            if (mode == HabitMode.BUILD) "Leave my shoes by the door"
            else "Log out and delete the shortcut", lines = 2)
        field("environmentPrep", "Prepare the environment", "Set out my clothes tonight", lines = 2)

        switchRow("Protected routine",
            "Protected routines survive Minimum Mode on low-capacity days.",
            protectedRoutine) { protectedRoutine = it }
    }

    private fun feel() {
        header("Step 5 of 6 · Feel",
            if (mode == HabitMode.BUILD) "Make it satisfying" else "Make it unsatisfying",
            "The payoff has to arrive now. Delayed rewards do not close the loop.")
        field("reward", "Immediate reward", "Tick it off and enjoy my coffee")
        field("recoveryPlan", "Recovery plan if you miss",
            "Return tomorrow with the tiny version. Never miss twice.", lines = 2)
        switchRow("Remind me at the cue time", null, reminder) { reminder = it }
    }

    private fun contract() {
        val h = draft()
        header("Step 6 of 6 · Contract", getString(R.string.your_contract),
            "Read it once. If it sounds unrealistic, shrink it now rather than later.")

        val card = layoutInflater.inflate(R.layout.item_identity, stepContent, false)
        card.findViewById<TextView>(R.id.identity_text).text = h.contract()
        card.findViewById<TextView>(R.id.identity_votes).visible(false)
        (card.findViewById<TextView>(R.id.identity_text).parent as? View)
            ?.findViewWithTag<View>(null)
        stepContent.addView(card)

        val summary = layoutInflater.inflate(R.layout.item_text_card, stepContent, false)
        summary.findViewById<TextView>(R.id.text_title).text = "Summary"
        summary.findViewById<TextView>(R.id.text_body).text = buildString {
            append("Schedule: ${recurrence.label()}\n")
            append("Mode: ${if (mode == HabitMode.BUILD) "Build" else "Reduce"}\n")
            append("Tracking: ${trackType.name.lowercase()}\n")
            append("Reminder: ${if (reminder) "on at ${h.cueTime.ifBlank { "no time set" }}" else "off"}\n")
            append("Protected: ${if (protectedRoutine) "yes" else "no"}\n\n")
            append("Ladder\n")
            Level.values().forEach { append("· ${it.label}: ${h.levelText(it)}\n") }
        }
        stepContent.addView(summary)

        if (h.tinyStart.isBlank()) {
            stepContent.addView(warmCard(
                "No tiny start yet. Habits without one break during a hard week."
            ))
        }
    }

    /* --------------------------------------------------------------- widgets */

    private fun header(kicker: String, title: String, body: String) {
        val v = layoutInflater.inflate(R.layout.part_step_header, stepContent, false)
        v.findViewById<TextView>(R.id.step_kicker).text = kicker
        v.findViewById<TextView>(R.id.step_title).text = title
        v.findViewById<TextView>(R.id.step_body).text = body
        stepContent.addView(v)
    }

    private fun label(text: String) {
        val v = layoutInflater.inflate(R.layout.part_label, stepContent, false)
        (v as TextView).text = text
        stepContent.addView(v)
    }

    private fun hint(text: String) {
        stepContent.addView(TextView(this).apply {
            this.text = text
            setTextAppearance(R.style.Text_SuperFlow_BodyMedium)
            alpha = 0.7f
            setPadding(4, 0, 4, dp(10))
        })
    }

    private fun field(
        key: String, hint: String, placeholder: String,
        lines: Int = 1, numeric: Boolean = false
    ) {
        val v = layoutInflater.inflate(R.layout.part_field, stepContent, false)
        val layout = v.findViewById<TextInputLayout>(R.id.field_layout)
        val edit = v.findViewById<TextInputEditText>(R.id.field_edit)
        layout.hint = hint
        layout.placeholderText = placeholder
        edit.setText(values[key].orEmpty())
        if (numeric) edit.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        if (lines > 1) {
            edit.isSingleLine = false
            edit.minLines = lines
            edit.gravity = android.view.Gravity.TOP
            edit.inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                    android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        edit.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                values[key] = s?.toString().orEmpty()
            }
        })
        stepContent.addView(v)
    }

    private fun timeField() {
        val v = layoutInflater.inflate(R.layout.part_field, stepContent, false)
        val layout = v.findViewById<TextInputLayout>(R.id.field_layout)
        val edit = v.findViewById<TextInputEditText>(R.id.field_edit)
        layout.hint = "At this time"
        layout.placeholderText = "07:30"
        edit.setText(values["cueTime"].orEmpty())
        edit.isFocusable = false
        edit.setOnClickListener {
            val minutes = SfTime.minutesOfDay(values["cueTime"].orEmpty()).coerceAtLeast(7 * 60)
            val picker = MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setHour(minutes / 60).setMinute(minutes % 60)
                .setTitleText("Cue time").build()
            picker.addOnPositiveButtonClickListener {
                val text = String.format("%02d:%02d", picker.hour, picker.minute)
                values["cueTime"] = text
                edit.setText(text)
            }
            picker.show(supportFragmentManager, "cue")
        }
        layout.setEndIconDrawable(R.drawable.ic_calendar)
        layout.endIconMode = TextInputLayout.END_ICON_CUSTOM
        layout.setEndIconOnClickListener { edit.performClick() }
        stepContent.addView(v)
    }

    private fun chips(items: List<Pair<String, Boolean>>, onPick: (Int) -> Unit) {
        val group = layoutInflater.inflate(R.layout.part_chipgroup, stepContent, false) as ChipGroup
        group.isSingleSelection = true
        items.forEachIndexed { index, (label, selected) ->
            group.addView(Chip(this).apply {
                text = label
                isCheckable = true
                isChecked = selected
                setEnsureMinTouchTargetSize(false)
                setOnClickListener { onPick(index) }
            })
        }
        stepContent.addView(group)
    }

    private fun switchRow(title: String, sub: String?, value: Boolean, onChange: (Boolean) -> Unit) {
        val v = layoutInflater.inflate(R.layout.item_setting_toggle, stepContent, false)
        v.findViewById<TextView>(R.id.toggle_title).text = title
        v.findViewById<TextView>(R.id.toggle_sub).apply { visible(sub != null); text = sub }
        val sw = v.findViewById<MaterialSwitch>(R.id.toggle_switch)
        sw.isChecked = value
        v.setOnClickListener {
            sw.isChecked = !sw.isChecked
            onChange(sw.isChecked)
        }
        stepContent.addView(v)
    }

    private fun warmCard(text: String): View {
        val card = layoutInflater.inflate(R.layout.item_text_card, stepContent, false)
        card.findViewById<TextView>(R.id.text_title).text = "Worth knowing"
        card.findViewById<TextView>(R.id.text_body).text = text
        (card as MaterialCardView).setCardBackgroundColor(
            com.google.android.material.color.MaterialColors.getColor(
                card, com.google.android.material.R.attr.colorSecondaryContainer
            )
        )
        card.strokeWidth = 0
        return card
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    /* ----------------------------------------------------------------- state */

    private fun v(key: String) = values[key]?.trim().orEmpty()

    private fun draft(): Habit = (editing ?: Habit(title = v("title"))).copy(
        title = v("title"), mode = mode, trackType = trackType,
        targetCount = v("targetCount").toIntOrNull() ?: 1, unit = v("unit"),
        cueTime = v("cueTime"), cuePlace = v("cuePlace"), anchorText = v("anchorText"),
        benefit = v("benefit"), temptationBundle = v("temptationBundle"), reframe = v("reframe"),
        tinyStart = v("tinyStart"), minimumVersion = v("minimumVersion"),
        standardVersion = v("standardVersion").ifBlank { v("title") },
        stretchVersion = v("stretchVersion"), frictionPlan = v("frictionPlan"),
        environmentPrep = v("environmentPrep"), reward = v("reward"),
        recoveryPlan = v("recoveryPlan"), recurrenceRule = recurrence.encode(),
        reminderEnabled = reminder, protectedRoutine = protectedRoutine
    )

    private fun validateStep(): Boolean {
        if (step == 0 && v("title").isBlank()) {
            findViewById<View>(R.id.root).snack("The habit needs a name")
            return false
        }
        if (step == 1 && v("cueTime").isNotBlank() && !Dates.isValidTime(v("cueTime"))) {
            findViewById<View>(R.id.root).snack("Cue time should look like 07:30")
            return false
        }
        return true
    }

    private fun save() {
        if (v("title").isBlank()) {
            step = 0; render()
            findViewById<View>(R.id.root).snack("The habit needs a name")
            return
        }
        val args = jsonOf(
            "title" to v("title"), "tinyStart" to v("tinyStart"),
            "minimumVersion" to v("minimumVersion"),
            "standardVersion" to v("standardVersion").ifBlank { v("title") },
            "stretchVersion" to v("stretchVersion"), "cueTime" to v("cueTime"),
            "cuePlace" to v("cuePlace"), "anchorText" to v("anchorText"),
            "benefit" to v("benefit"), "temptationBundle" to v("temptationBundle"),
            "reframe" to v("reframe"), "frictionPlan" to v("frictionPlan"),
            "environmentPrep" to v("environmentPrep"), "reward" to v("reward"),
            "recoveryPlan" to v("recoveryPlan"), "unit" to v("unit"),
            "targetCount" to (v("targetCount").toIntOrNull() ?: 1),
            "mode" to mode.name, "trackType" to trackType.name,
            "days" to recurrence.encode(),
            "reminder" to reminder, "protected" to protectedRoutine
        )
        val res = if (editing == null) bus.execute("create_habit", args, Actor.USER)
        else bus.execute("update_habit", args.put("habit", editing!!.id), Actor.USER)

        if (res.ok) {
            Reminders.rescheduleAll(this)
            finish()
        } else findViewById<View>(R.id.root).snack(res.message)
    }

    private fun confirmExit() {
        if (values.values.any { it.isNotBlank() } && editing == null) {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Discard this habit?")
                .setNegativeButton("Keep editing", null)
                .setPositiveButton("Discard") { _, _ -> finish() }
                .show()
        } else finish()
    }

    companion object {
        const val EXTRA_HABIT_ID = "habitId"
    }
}
