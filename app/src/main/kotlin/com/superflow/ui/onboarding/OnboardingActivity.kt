package com.superflow.ui.onboarding

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.button.MaterialButton
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
import com.superflow.data.Prefs
import com.superflow.data.model.LifeArea
import com.superflow.domain.Actor
import com.superflow.domain.CommandBus
import com.superflow.notify.Reminders
import com.superflow.ui.MainActivity
import com.superflow.ui.common.snack
import com.superflow.ui.common.visible
import com.superflow.util.jsonOf

/**
 * Onboarding: from aspiration to first action in under five minutes.
 *
 * Skippable, no account wall, no name required. Notification permission is
 * requested only at the reminder step, with the reason stated.
 */
class OnboardingActivity : AppCompatActivity() {

    private lateinit var bus: CommandBus
    private lateinit var prefs: Prefs
    private lateinit var contentView: LinearLayout
    private lateinit var progress: LinearProgressIndicator
    private lateinit var back: MaterialButton
    private lateinit var next: MaterialButton

    private var step = 0
    private var area = LifeArea.HEALTH
    private val values = HashMap<String, String>()
    private var wantsReminder = false

    private val totalSteps = 9

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)
        bus = CommandBus.get(this)
        prefs = Prefs.get(this)

        contentView = findViewById(R.id.onb_content)
        progress = findViewById(R.id.onb_progress)
        back = findViewById(R.id.onb_back)
        next = findViewById(R.id.onb_next)

        back.setOnClickListener { if (step > 0) { step--; render() } }
        next.setOnClickListener { advance() }

        ViewCompat.setOnApplyWindowInsetsListener(progress) { v, insets ->
            v.updatePadding(top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top)
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.onb_nav)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.updatePadding(bottom = maxOf(bars.bottom, ime.bottom) +
                    resources.getDimensionPixelSize(R.dimen.space_m))
            insets
        }

        render()
    }

    private fun render() {
        contentView.removeAllViews()
        progress.setProgressCompat(((step + 1) * 100) / totalSteps, true)
        back.visible(step in 1 until totalSteps - 1)
        next.text = when (step) {
            0 -> "Begin"
            totalSteps - 1 -> "Create my system"
            else -> getString(R.string.next)
        }

        when (step) {
            0 -> welcome()
            1 -> pickArea()
            2 -> identity()
            3 -> goal()
            4 -> system()
            5 -> habit()
            6 -> cue()
            7 -> feel()
            else -> preview()
        }
        findViewById<androidx.core.widget.NestedScrollView>(R.id.onb_scroll).scrollTo(0, 0)
    }

    /* ---------------------------------------------------------------- steps */

    private fun welcome() {
        contentView.addView(TextView(this).apply {
            text = getString(R.string.app_name)
            setTextAppearance(R.style.Text_SuperFlow_DisplaySmall)
            setPadding(0, dpi(48), 0, dpi(8))
        })
        contentView.addView(TextView(this).apply {
            text = getString(R.string.tagline)
            setTextAppearance(R.style.Text_SuperFlow_BodyLarge)
            alpha = 0.8f
            setPadding(0, 0, 0, dpi(24))
        })
        contentView.addView(card("A promise",
            "Everything stays on your device. No account, no ads, no streak guilt, nothing " +
                    "uploaded unless you connect a provider yourself.", accent = true))
        contentView.addView(card("How it works", buildString {
            append("Identity — who you are becoming\n")
            append("Goal — the outcome that would matter\n")
            append("System — the repeatable process\n")
            append("Habit — the smallest useful action\n")
            append("Review — improve the system, not blame yourself")
        }))
        contentView.addView(MaterialButton(this).apply {
            text = "I don't know what to track"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dpi(12) }
            setOnClickListener { guideDiscovery() }
        })
        contentView.addView(MaterialButton(this, null,
            com.google.android.material.R.attr.borderlessButtonStyle).apply {
            text = "Skip for now"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener { complete(skipped = true) }
        })
    }

    /**
     * Lightweight, offline goal discovery (#76): asks "how do you want to
     * feel?" and suggests a starter habit from the templates based on the
     * chosen life area. No AI required.
     */
    private fun guideDiscovery() {
        val options = arrayOf("More energised", "Calmer", "More focused",
            "Healthier", "More present with people")
        MaterialAlertDialogBuilder(this)
            .setTitle("How do you want to feel?")
            .setItems(options) { _, which ->
                val pick = when (which) {
                    0 -> "Morning walk"
                    1 -> "Meditate"
                    2 -> "Read"
                    3 -> "Work out"
                    else -> "Journal"
                }
                val t = com.superflow.ui.designer.HabitTemplates.all
                    .firstOrNull { it.name == pick }
                if (t != null) {
                    values["identity"] = "someone who ${t.title.lowercase()}s"
                    values["habit"] = t.title
                    values["tiny"] = t.tinyStart
                    values["system"] = t.benefit
                }
                step = 5 // jump to the habit step, pre-filled
                render()
            }.show()
    }

    private fun pickArea() {
        header("Where do you want to grow?", "One area is enough to start.")
        val chips = ChipGroup(this).apply { isSingleSelection = true }
        LifeArea.values().forEach { la ->
            chips.addView(Chip(this).apply {
                text = la.label
                isCheckable = true
                isChecked = la == area
                setEnsureMinTouchTargetSize(false)
                setOnClickListener { area = la }
            })
        }
        contentView.addView(chips)
    }

    private fun identity() {
        header("Who are you becoming?",
            "Habits stick when they say something true about you.")
        field("identity", "I am becoming someone who…", "someone who moves every day", lines = 2)
        contentView.addView(card("Examples",
            "someone who takes care of their body · a person who writes daily · " +
                    "someone who is present with their family"))
    }

    private fun goal() {
        header("What outcome would matter?",
            "A goal gives direction. Your system does the work.")
        field("goal", "Goal", "Comfortably walk 5 km")
        field("why", "Why does this matter to you?", "", lines = 3)
    }

    private fun system() {
        header("What process could produce it?",
            "Describe the repeatable routine, not the result.")
        field("system", "System", "Move after breakfast on weekdays", lines = 2)
    }

    private fun habit() {
        header("Pick one habit",
            "And the smallest version you could do on your worst day.")
        // Template quick-fill (#74).
        contentView.addView(TextView(this).apply {
            text = "OR START FROM A TEMPLATE"
            setTextAppearance(R.style.Text_SuperFlow_Overline)
            setPadding(0, dpi(8), 0, dpi(4))
        })
        val chips = ChipGroup(this)
        listOf("Morning walk", "Read", "Meditate", "Journal", "Work out").forEach { name ->
            chips.addView(Chip(this).apply {
                text = name
                isCheckable = false
                setEnsureMinTouchTargetSize(false)
                setOnClickListener {
                    val t = com.superflow.ui.designer.HabitTemplates.all
                        .firstOrNull { it.name == name }
                    if (t != null) {
                        values["habit"] = t.title
                        values["tiny"] = t.tinyStart
                        render()
                    }
                }
            })
        }
        contentView.addView(chips)
        field("habit", "Habit", "Walk for 10 minutes")
        field("tiny", "Tiny start — about two minutes", "Put on my shoes and step outside")
        contentView.addView(MaterialButton(this, null,
            com.google.android.material.R.attr.borderlessButtonStyle).apply {
            text = "Suggest a tiny start"
            setOnClickListener {
                val title = v("habit")
                if (title.isBlank()) findViewById<View>(R.id.root).snack("Name the habit first")
                else { values["tiny"] = Coordinator.defaultTinyStart(title); render() }
            }
        })
        contentView.addView(card("Why so small?",
            "The tiny version is not a lesser version. It is the one that survives a bad week."))
    }

    private fun cue() {
        header("What will make you notice it?",
            "A time and place, or an existing routine to attach to.")
        timeField()
        field("anchor", "Or after this existing routine", "breakfast")
    }

    private fun feel() {
        header("What makes it worth it?",
            "An immediate, honest payoff right after the action.")
        field("reward", "Reward", "Listen to my favourite playlist")

        val row = layoutInflater.inflate(R.layout.item_setting_toggle, contentView, false)
        row.findViewById<TextView>(R.id.toggle_title).text = "Remind me at my cue time"
        row.findViewById<TextView>(R.id.toggle_sub).apply {
            visible(true)
            text = "Android will ask for notification permission so SuperFlow can send that one " +
                    "reminder. Nothing else uses it."
        }
        val sw = row.findViewById<MaterialSwitch>(R.id.toggle_switch)
        sw.isChecked = wantsReminder
        row.setOnClickListener { sw.isChecked = !sw.isChecked; wantsReminder = sw.isChecked }
        contentView.addView(row)
    }

    /** Preview of what the user's Today will look like (#75). */
    private fun preview() {
        header("Here's what your Today will look like",
            "One tiny habit, an obvious cue, and an immediate reward. You can change any of it later.")
        val previewBody = buildString {
            append("Tiny: ")
            append(v("tiny").ifBlank { Coordinator.defaultTinyStart(v("habit")) })
            if (v("cueTime").isNotBlank() || v("anchor").isNotBlank()) {
                append("\nCue: ")
                append(listOf(v("cueTime"), v("anchor")).filter { it.isNotBlank() }.joinToString(" after "))
            }
            if (v("reward").isNotBlank()) append("\nReward: ${v("reward")}")
            append("\nReminder: ${if (wantsReminder) "on" else "off"}")
        }
        contentView.addView(card(v("habit").ifBlank { "Your habit" }, previewBody, accent = true))
    }

    /* -------------------------------------------------------------- widgets */

    private fun header(title: String, body: String) {
        contentView.addView(TextView(this).apply {
            text = "STEP ${step + 1} OF $totalSteps"
            setTextAppearance(R.style.Text_SuperFlow_Overline)
            setPadding(0, dpi(24), 0, dpi(8))
        })
        contentView.addView(TextView(this).apply {
            text = title
            setTextAppearance(R.style.Text_SuperFlow_HeadlineSmall)
            setPadding(0, 0, 0, dpi(6))
        })
        contentView.addView(TextView(this).apply {
            text = body
            setTextAppearance(R.style.Text_SuperFlow_BodyMedium)
            alpha = 0.75f
            setPadding(0, 0, 0, dpi(20))
        })
    }

    private fun card(title: String, body: String, accent: Boolean = false): View {
        val v = layoutInflater.inflate(
            if (accent) R.layout.item_identity else R.layout.item_text_card, contentView, false
        )
        if (accent) {
            v.findViewById<TextView>(R.id.identity_text).text = title
            v.findViewById<TextView>(R.id.identity_votes).text = body
        } else {
            v.findViewById<TextView>(R.id.text_title).text = title
            v.findViewById<TextView>(R.id.text_body).text = body
        }
        return v
    }

    private fun field(key: String, hint: String, placeholder: String, lines: Int = 1) {
        val v = layoutInflater.inflate(R.layout.part_field, contentView, false)
        val layout = v.findViewById<TextInputLayout>(R.id.field_layout)
        val edit = v.findViewById<TextInputEditText>(R.id.field_edit)
        layout.hint = hint
        if (placeholder.isNotBlank()) layout.placeholderText = placeholder
        edit.setText(values[key].orEmpty())
        if (lines > 1) {
            edit.isSingleLine = false
            edit.minLines = lines
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
        contentView.addView(v)
    }

    private fun timeField() {
        val v = layoutInflater.inflate(R.layout.part_field, contentView, false)
        val layout = v.findViewById<TextInputLayout>(R.id.field_layout)
        val edit = v.findViewById<TextInputEditText>(R.id.field_edit)
        layout.hint = "At this time"
        layout.placeholderText = "07:30"
        edit.setText(values["cueTime"].orEmpty())
        edit.isFocusable = false
        edit.setOnClickListener {
            val picker = MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H).setHour(7).setMinute(30).build()
            picker.addOnPositiveButtonClickListener {
                val text = String.format("%02d:%02d", picker.hour, picker.minute)
                values["cueTime"] = text
                edit.setText(text)
            }
            picker.show(supportFragmentManager, "cue")
        }
        contentView.addView(v)
    }

    private fun dpi(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun v(key: String) = values[key]?.trim().orEmpty()

    /* --------------------------------------------------------------- finish */

    private fun advance() {
        val required = when (step) {
            2 -> "identity" to "Write something, even roughly"
            3 -> "goal" to "Name the outcome"
            5 -> "habit" to "Name the habit"
            else -> null
        }
        if (required != null && v(required.first).isBlank()) {
            findViewById<View>(R.id.root).snack(required.second)
            return
        }
        if (step == totalSteps - 1) complete(skipped = false) else { step++; render() }
    }

    private fun complete(skipped: Boolean) {
        if (!skipped) {
            val identityId = bus.execute("create_identity",
                jsonOf("statement" to v("identity"), "lifeArea" to area.name), Actor.USER)
                .data?.optString("id")

            val goalId = bus.execute("create_goal",
                jsonOf("title" to v("goal"), "why" to v("why"), "identityId" to identityId),
                Actor.USER).data?.optString("id")

            val systemId = bus.execute("create_system", jsonOf(
                "title" to v("system").ifBlank { "My ${v("goal")} routine" }, "goalId" to goalId
            ), Actor.USER).data?.optString("id")

            bus.execute("create_habit", jsonOf(
                "title" to v("habit"),
                "tinyStart" to v("tiny").ifBlank { Coordinator.defaultTinyStart(v("habit")) },
                "standardVersion" to v("habit"),
                "cueTime" to v("cueTime"),
                "anchorText" to v("anchor"),
                "reward" to v("reward"),
                "systemId" to systemId,
                "identityId" to identityId,
                "reminder" to wantsReminder,
                "days" to "daily"
            ), Actor.USER)

            if (wantsReminder && android.os.Build.VERSION.SDK_INT >= 33) {
                val perm = android.Manifest.permission.POST_NOTIFICATIONS
                if (checkSelfPermission(perm) !=
                    android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    notificationPermission.launch(perm)
                }
            }
        }
        prefs.onboarded = true
        Reminders.rescheduleAll(this)
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
