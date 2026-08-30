@file:Suppress("LargeClass", "TooManyFunctions", "HardcodedText")

package com.superflow.ui.settings


import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.superflow.AppBackground
import com.superflow.R
import com.superflow.data.Prefs
import com.superflow.data.Repository
import com.superflow.design.Catalog
import com.superflow.domain.Actor
import com.superflow.domain.CommandBus
import com.superflow.domain.Serial
import com.superflow.notify.Reminders
import com.superflow.security.AppLock
import com.superflow.security.PinSetupSheet
import com.superflow.ui.common.SfTheme
import com.superflow.ui.common.snack
import com.superflow.ui.common.visible
import com.superflow.ui.engine.AiEngineActivity
import com.superflow.ui.onboarding.OnboardingActivity
import com.superflow.ui.pause.PauseActivity
import com.superflow.ui.sheets.TextInputSheet
import com.superflow.util.Dates
import com.superflow.util.jsonOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Settings: appearance, reminders, checkpoints, data, privacy and the entry
 * point to the AI Engine control center.
 */
class SettingsFragment : Fragment() {

    private lateinit var prefs: Prefs
    private lateinit var repo: Repository
    private lateinit var bus: CommandBus
    private lateinit var container: LinearLayout

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_list, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        prefs = Prefs.get(requireContext())
        repo = Repository.get(requireContext())
        bus = CommandBus.get(requireContext())

        view.findViewById<TextView>(R.id.screen_title).text = getString(R.string.tab_settings)
        view.findViewById<TextView>(R.id.screen_subtitle).text =
            "Everything is optional. The app works fully offline."

        ViewCompat.setOnApplyWindowInsetsListener(view.findViewById(R.id.header)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = bars.top + v.context.resources.getDimensionPixelSize(R.dimen.space_m))
            insets
        }

        // A single scrolling column is simpler here than a typed adapter.
        val list = view.findViewById<RecyclerView>(R.id.list)
        list.layoutManager = LinearLayoutManager(requireContext())
        container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT
            )
        }
        list.adapter = SingleViewAdapter(container)
        render()
    }

    override fun onResume() {
        super.onResume()
        if (::container.isInitialized) render()
    }

    private fun render() {
        container.removeAllViews()

        // Active profile (#78) — handy on a shared tablet.
        container.addView(action(R.drawable.ic_identity, "Profile",
            prefs.activeProfile) {
            val names = listOf("Me", "Partner", "Family member")
            val current = names.indexOf(prefs.activeProfile).coerceAtLeast(0)
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Who is using the app?")
                .setSingleChoiceItems(names.toTypedArray(), current) { d, which ->
                    if (which < names.size - 1) {
                        prefs.activeProfile = names[which]
                        d.dismiss(); render()
                    } else {
                        d.dismiss()
                        com.superflow.ui.sheets.TextInputSheet.show(
                            parentFragmentManager, "Profile name", "A name for this profile"
                        ) { name ->
                            if (name.isNotBlank()) { prefs.activeProfile = name.trim(); render() }
                        }
                    }
                }.show()
        })

        // Appearance & Experience
        //
        // The full surface (palettes, dark styles, density, motion, haptics,
        // sound, start screen, list behaviour) lives on its own screen; this
        // row summarises the current state so the common case of "what am I
        // set to?" is answered without a tap.
        container.addView(section(getString(R.string.appearance)))
        container.addView(group(listOf(
            action(R.drawable.ic_palette, getString(R.string.appearance_experience),
                appearanceSummary()) {
                push(AppearanceFragment(), "appearance")
            }
        )))

        // Dark-mode schedule (#77), only relevant when following system/theme.
        if (prefs.themeMode == Prefs.THEME_SYSTEM) {
            container.addView(action(R.drawable.ic_moon, "Dark mode schedule",
                when (prefs.darkSchedule) {
                    "sunset" -> "Sunset to sunrise (21:00–07:00)"
                    "custom" -> "${prefs.darkFrom} – ${prefs.darkTo}"
                    else -> "Off"
                }) {
                val options = arrayOf("Off", "Sunset to sunrise", "Custom hours")
                val values = arrayOf("off", "sunset", "custom")
                val current = values.indexOf(prefs.darkSchedule).coerceAtLeast(0)
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Dark mode schedule")
                    .setSingleChoiceItems(options, current) { d, which ->
                        prefs.darkSchedule = values[which]
                        if (values[which] == "custom") {
                            pickTime(prefs.darkFrom) { from ->
                                prefs.darkFrom = from
                                pickTime(prefs.darkTo) { to ->
                                    prefs.darkTo = to; d.dismiss(); render()
                                }
                            }
                        } else { d.dismiss(); render() }
                    }.show()
            })
        }

        // AI
        container.addView(section("AI"))
        container.addView(group(listOf(
            action(R.drawable.ic_sparkle, getString(R.string.ai_engine),
                if (prefs.fullControlActive()) "Full Control active"
                else "Providers, autonomy, memory, budgets") {
                startActivity(Intent(requireContext(), AiEngineActivity::class.java))
            },
            toggle("Enable AI features",
                "The app stays fully usable with AI off.", prefs.aiEnabled) {
                prefs.aiEnabled = it
            },
            toggle("Voice control", "Speak instead of typing.", prefs.voiceEnabled) {
                prefs.voiceEnabled = it
            }
        )))

        // Reminders
        container.addView(section("REMINDERS"))
        container.addView(group(listOf(
            toggle("Reminders", null, prefs.remindersEnabled) {
                prefs.remindersEnabled = it
                Reminders.rescheduleAll(requireContext())
                render()
            },
            action(R.drawable.ic_notification, "Daily reminder budget",
                "${prefs.reminderBudget} per day") {
                val options = arrayOf("3 per day", "6 per day", "9 per day")
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Reminder budget")
                    .setItems(options) { _, which ->
                        prefs.reminderBudget = listOf(3, 6, 9)[which]
                        Reminders.rescheduleAll(requireContext())
                        render()
                    }.show()
            },
            action(R.drawable.ic_moon, "Quiet hours by day",
                if (prefs.quietPerDay.isBlank()) "Same every day"
                else "Custom schedule") {
                editPerDayQuietHours()
            },
            action(R.drawable.ic_pause, getString(R.string.pause_mode), pauseSubtitle()) {
                startActivity(Intent(requireContext(), PauseActivity::class.java))
            }
        )))

        // Checkpoints
        container.addView(section("CHECKPOINTS"))
        container.addView(group(buildList {
            add(toggle("Daily checkpoints", null, prefs.checkpointsEnabled) {
                prefs.checkpointsEnabled = it
                Reminders.rescheduleAll(requireContext())
                render()
            })
            if (prefs.checkpointsEnabled) {
                add(action(R.drawable.ic_sun, "Morning", prefs.morningCheckpoint) {
                    pickTime(prefs.morningCheckpoint) { t ->
                        prefs.morningCheckpoint = t
                        Reminders.rescheduleAll(requireContext()); render()
                    }
                })
                add(action(R.drawable.ic_today, "Midday", prefs.middayCheckpoint) {
                    pickTime(prefs.middayCheckpoint) { t ->
                        prefs.middayCheckpoint = t
                        Reminders.rescheduleAll(requireContext()); render()
                    }
                })
                add(action(R.drawable.ic_moon, "Evening", prefs.eveningCheckpoint) {
                    pickTime(prefs.eveningCheckpoint) { t ->
                        prefs.eveningCheckpoint = t
                        Reminders.rescheduleAll(requireContext()); render()
                    }
                })
            }
            add(toggle("Track energy", "Optional, with sample-size caveats.", prefs.energyTracking) {
                prefs.energyTracking = it
            })
        }))

        // Weekly summary
        container.addView(section("WEEKLY SUMMARY"))
        container.addView(group(buildList {
            add(toggle("Weekly report", "A quiet Sunday-evening recap.", prefs.weeklySummaryEnabled) {
                prefs.weeklySummaryEnabled = it
            })
            if (prefs.weeklySummaryEnabled) {
                val dayNames = listOf("Monday", "Tuesday", "Wednesday", "Thursday",
                    "Friday", "Saturday", "Sunday")
                add(action(R.drawable.ic_calendar, "Report day",
                    dayNames[(prefs.weeklySummaryDay - 1).coerceIn(0, 6)]) {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Weekly report day")
                        .setItems(dayNames.toTypedArray()) { _, which ->
                            prefs.weeklySummaryDay = which + 1
                            render()
                        }.show()
                })
                add(action(R.drawable.ic_notification, "Report time", prefs.weeklySummaryTime) {
                    pickTime(prefs.weeklySummaryTime) { t ->
                        prefs.weeklySummaryTime = t
                        render()
                    }
                })
            }
        }))

        // Pause / Vacation
        container.addView(section("PAUSE / VACATION"))
        container.addView(group(buildList {
            add(action(R.drawable.ic_pause, "Pause habits",
                "Take a break without creating misses") { startPauseFlow() })
            val pauses = repo.pauses()
            if (pauses.isEmpty()) {
                add(note("No active pauses. Paused days never count as misses."))
            } else {
                pauses.forEach { p ->
                    val scope = p.habitId?.let { id -> repo.habit(id)?.title } ?: "All habits"
                    add(action(R.drawable.ic_play, "Resume: $scope",
                        "${p.startDate} → ${p.endDate}" +
                                (if (p.reason.isBlank()) "" else " · ${p.reason}")) {
                        bus.execute("resume_habits", jsonOf("id" to p.id), Actor.USER)
                        view?.snack("Resumed")
                        render()
                    })
                }
            }
        }))

        // Experience
        //
        // Haptics moved to Appearance & Experience, where it is a three-way
        // intensity rather than a switch. Leaving a duplicate boolean here
        // would let the two controls disagree.
        container.addView(section("EXPERIENCE"))
        container.addView(group(listOf(
            toggle("Celebrations", "A brief animation when you complete the day.",
                prefs.celebrationsEnabled) { prefs.celebrationsEnabled = it }
        )))

        // Security
        container.addView(section("SECURITY"))
        container.addView(group(buildList {
            val locked = AppLock.isEnabled(prefs)
            add(toggle("App lock", "Require a PIN to open SuperFlow", locked) { enable ->
                if (enable) {
                    PinSetupSheet().apply {
                        onSaved = { render() }
                    }.show(parentFragmentManager, "pin")
                } else {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.remove_lock)
                        .setMessage("Remove the PIN and app lock?")
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.delete) { _, _ ->
                            AppLock.clearPin(prefs); render()
                        }.show()
                }
            })
            if (locked) {
                add(action(R.drawable.ic_lock, getString(R.string.change_pin), null) {
                    PinSetupSheet().apply { onSaved = { view?.snack("PIN updated") } }
                        .show(parentFragmentManager, "pin")
                })
            }
        }))

        // Data Management — all-inclusive
        container.addView(section("YOUR DATA"))
        container.addView(group(listOf(
            action(R.drawable.ic_upload, "Data Management",
                "Export, import, backup, integrity — all-inclusive data control") {
                push(DataManagementFragment(), "data_management")
            }
        )))
        container.addView(note(
            "The all-inclusion policy ensures every piece of data in the app — identities, goals, " +
                    "habits, check-ins, reviews, AI conversation, settings, and more — is covered " +
                    "by export, import, and backup. Nothing is silently left out."
        ))


        // Privacy
        container.addView(section("PRIVACY"))
        container.addView(group(listOf(
            toggle("Crash reporting", "Off by default.", prefs.crashReporting) {
                prefs.crashReporting = it
            }
        )))
        container.addView(note(
            "No account is required, nothing is uploaded unless you configure a cloud provider " +
                    "yourself, and API keys are never included in exports, prompts or logs."
        ))

        // About
        container.addView(section("ABOUT"))
        container.addView(group(listOf(
            action(R.drawable.ic_info, "SuperFlow 2.0.0",
                "Shape your system. Become your future self.") { },
            action(R.drawable.ic_refresh, "Replay onboarding", null) {
                prefs.onboarded = false
                startActivity(Intent(requireContext(), OnboardingActivity::class.java))
                requireActivity().finish()
            }
        )))
        container.addView(note(getString(R.string.disclaimer_attribution)))
        container.addView(note(getString(R.string.disclaimer_care)))
        container.addView(View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                resources.getDimensionPixelSize(R.dimen.list_bottom_padding)
            )
        })
    }

    /**
     * Opens a settings sub-screen.
     *
     * Settings is hosted by [SettingsActivity] now that it is a route rather
     * than a tab, so sub-screens go on that activity's own back stack. The
     * previous code replaced R.id.list — the RecyclerView inside this very
     * fragment's layout — which worked only because the ids happened not to
     * collide, and left back behaviour depending on which tab was underneath.
     */
    private fun push(fragment: Fragment, tag: String) {
        (activity as? SettingsActivity)?.push(fragment, tag)
    }

    /* ------------------------------------------------------------- builders */

    private fun pauseSubtitle(): String {
        val today = java.time.LocalDate.now()
        val active = repo.pauses().count {
            val end = runCatching { java.time.LocalDate.parse(it.endDate) }.getOrNull()
            end != null && !end.isBefore(today)
        }
        return if (active == 0) "Pause habits for a holiday or break"
        else "$active active pause${if (active == 1) "" else "s"}"
    }

    private fun section(title: String): View =
        layoutInflater.inflate(R.layout.item_section, container, false).also {
            (it as TextView).text = title
        }

    private fun note(text: String): View =
        TextView(requireContext()).apply {
            this.text = text
            setTextAppearance(R.style.Text_SuperFlow_BodyMedium)
            alpha = 0.75f
            setPadding(4, 8, 4, 16)
        }

    private fun group(children: List<View>): View {
        val card = layoutInflater.inflate(R.layout.item_setting_group, container, false)
        val holder = card.findViewById<LinearLayout>(R.id.group_container)
        children.forEachIndexed { index, child ->
            holder.addView(child)
            if (index != children.lastIndex) {
                holder.addView(com.google.android.material.divider.MaterialDivider(requireContext()))
            }
        }
        return card
    }

    private fun toggle(
        title: String, subtitle: String?, value: Boolean, onChange: (Boolean) -> Unit
    ): View {
        val v = layoutInflater.inflate(R.layout.item_setting_toggle, container, false)
        v.findViewById<TextView>(R.id.toggle_title).text = title
        v.findViewById<TextView>(R.id.toggle_sub).apply {
            visible(subtitle != null)
            text = subtitle
        }
        val sw = v.findViewById<MaterialSwitch>(R.id.toggle_switch)
        sw.isChecked = value
        v.setOnClickListener {
            sw.isChecked = !sw.isChecked
            onChange(sw.isChecked)
        }
        return v
    }

    /**
     * One line describing the current appearance, for the settings row.
     *
     * Reads as "Calm - System - Comfortable". Dynamic colour replaces the
     * palette name, because when it is on the palette choice is inert and
     * naming it would be misleading.
     */
    private fun appearanceSummary(): String {
        val palette =
            if (prefs.dynamicColor && SfTheme.dynamicColorSupported()) getString(R.string.dynamic_color)
            else Catalog.labelOf(Catalog.palettes, prefs.palette)
        val mode = when (prefs.themeMode) {
            Prefs.THEME_LIGHT -> getString(R.string.theme_light)
            Prefs.THEME_DARK -> getString(R.string.theme_dark)
            else -> getString(R.string.theme_system)
        }
        val density = Catalog.labelOf(Catalog.densities, prefs.density)
        return "$palette \u00b7 $mode \u00b7 $density"
    }

    private fun action(icon: Int, title: String, subtitle: String?, onClick: () -> Unit): View {
        val v = layoutInflater.inflate(R.layout.item_setting_action, container, false)
        v.findViewById<ImageView>(R.id.action_icon).setImageResource(icon)
        v.findViewById<TextView>(R.id.action_title).text = title
        v.findViewById<TextView>(R.id.action_sub).apply {
            visible(subtitle != null)
            text = subtitle
        }
        v.setOnClickListener { onClick() }
        return v
    }

    /**
     * Per-day-of-week quiet hours (#70). Each day can use the global default,
     * its own from/to window, or be disabled entirely. The result is encoded
     * into [Prefs.quietPerDay] and consumed by [Reminders.inQuietHours].
     */
    private fun editPerDayQuietHours() {
        val names = arrayOf("Monday", "Tuesday", "Wednesday", "Thursday",
            "Friday", "Saturday", "Sunday")
        // Parse current overrides into per-day choices: 0=default, 1=custom, 2=off.
        val current = prefs.quietPerDay.split("|")
            .map { it.trim() }
            .map {
                when {
                    it == "-" -> 2
                    it.contains("-") && it.substringBefore("-").isNotBlank() -> 1
                    else -> 0
                }
            }.toMutableList()
        while (current.size < 7) current.add(0)
        val choices = arrayOf("Use default (${prefs.quietFrom}–${prefs.quietTo})",
            "Custom hours", "No quiet hours")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Quiet hours by day")
            .setSingleChoiceItems(names.map { name ->
                val c = choices[current[names.indexOf(name)]]
                "$name — $c"
            }.toTypedArray(), -1) { dialog, which ->
                val dayIndex = which
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(names[which])
                    .setSingleChoiceItems(choices, current[dayIndex]) { inner, choice ->
                        when (choice) {
                            0 -> setQuietOverride(dayIndex, null)
                            2 -> setQuietOverride(dayIndex, "-")
                            1 -> pickQuietWindow(dayIndex)
                        }
                        inner.dismiss(); dialog.dismiss(); render()
                    }.show()
            }
            .setPositiveButton(R.string.close, null)
            .show()
    }

    private fun setQuietOverride(dayIndex: Int, value: String?) {
        val parts = prefs.quietPerDay.split("|").toMutableList()
        while (parts.size < 7) parts.add("")
        parts[dayIndex] = value ?: ""
        prefs.quietPerDay = parts.joinToString("|")
        Reminders.rescheduleAll(requireContext())
    }

    private fun pickQuietWindow(dayIndex: Int) {
        pickTime(prefs.quietFrom) { from ->
            pickTime(prefs.quietTo) { to ->
                setQuietOverride(dayIndex, "$from-$to")
                render()
            }
        }
    }

    private fun pickTime(current: String, onPicked: (String) -> Unit) {
        val minutes = Dates.minutesOfDay(current).coerceAtLeast(0)
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(minutes / 60)
            .setMinute(minutes % 60)
            .build()
        picker.addOnPositiveButtonClickListener {
            onPicked(String.format("%02d:%02d", picker.hour, picker.minute))
        }
        picker.show(parentFragmentManager, "time")
    }

    /** Guided pause/vacation flow: start date → end date → optional reason. */
    private fun startPauseFlow() {
        val today = com.superflow.core.time.SfTime.format(repo.clock.today())
        TextInputSheet.show(
            parentFragmentManager, "Pause habits", "Start date (yyyy-MM-dd)",
            subtitle = "Paused days never count as misses.", value = today
        ) { from ->
            val fromIso = from.trim().ifBlank { today }
            TextInputSheet.show(
                parentFragmentManager, "Pause habits", "End date (yyyy-MM-dd)",
                subtitle = "The break ends after this day.", value = fromIso
            ) { to ->
                val toIso = to.trim().ifBlank { fromIso }
                TextInputSheet.show(
                    parentFragmentManager, "Pause habits", "Reason (optional)",
                    subtitle = "Vacation, illness, travel, or anything else."
                ) { reason ->
                    val res = bus.execute(
                        "pause_habits",
                        jsonOf("from" to fromIso, "to" to toIso, "reason" to reason.trim()),
                        Actor.USER
                    )
                    view?.snack(res.message)
                    render()
                }
            }
        }
    }

    /* ------------------------------------------------------------------ data */

    private fun exportData() {
        lifecycleScope.launch {
            val json = withContext(Dispatchers.IO) { Serial.exportAll(repo).toString(2) }
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = File(requireContext().cacheDir, "exports").apply { mkdirs() }
                    File(dir, "superflow-${com.superflow.core.time.SfTime.format(repo.clock.today())}.json")
                        .writeText(json)
                }.isSuccess
            }
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_SUBJECT, "SuperFlow export")
                putExtra(Intent.EXTRA_TEXT, json.take(400_000))
            }
            startActivity(Intent.createChooser(share, "Export SuperFlow data"))
            if (!ok) view?.snack("Shared, but the local copy could not be written")
        }
    }

    private fun importData() {
        TextInputSheet.show(
            parentFragmentManager, "Import", "Paste exported JSON",
            subtitle = "This replaces everything currently in the app.", lines = 6
        ) { text ->
            lifecycleScope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching { Serial.importAll(repo, org.json.JSONObject(text)) }.isSuccess
                }
                view?.snack(if (ok) "Import complete" else "That did not look like a SuperFlow export")
                render()
            }
        }
    }

    /** Shareable private accountability summary. */
    private fun shareSummary() {
        lifecycleScope.launch {
            val text = withContext(Dispatchers.IO) {
                com.superflow.domain.Insights.summaryText(repo, 30)
            }
            val body = "My SuperFlow progress\n\n$text\n\n" +
                    "Shared from SuperFlow. Systems over scoreboards."
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "My SuperFlow progress")
                putExtra(Intent.EXTRA_TEXT, body)
            }, "Share progress"))
        }
    }
}

/** Hosts one pre-built view inside a RecyclerView so it scrolls with insets. */
class SingleViewAdapter(private val content: View) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        object : RecyclerView.ViewHolder(content) {}

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) = Unit
    override fun getItemCount() = 1
}
