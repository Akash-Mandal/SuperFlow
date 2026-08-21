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
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.superflow.AppBackground
import com.superflow.R
import com.superflow.SuperFlowApp
import com.superflow.data.Prefs
import com.superflow.data.Repository
import com.superflow.domain.Actor
import com.superflow.domain.CommandBus
import com.superflow.domain.Serial
import com.superflow.notify.Reminders
import com.superflow.security.AppLock
import com.superflow.security.PinSetupSheet
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
        val inf = layoutInflater

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

        // Appearance
        val theme = inf.inflate(R.layout.item_theme_picker, container, false)
        val group = theme.findViewById<MaterialButtonToggleGroup>(R.id.theme_group)
        group.check(when (prefs.themeMode) {
            Prefs.THEME_LIGHT -> R.id.theme_light
            Prefs.THEME_DARK -> R.id.theme_dark
            else -> R.id.theme_system
        })
        group.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            prefs.themeMode = when (checkedId) {
                R.id.theme_light -> Prefs.THEME_LIGHT
                R.id.theme_dark -> Prefs.THEME_DARK
                else -> Prefs.THEME_SYSTEM
            }
            SuperFlowApp.applyTheme(prefs.themeMode)
            render()
        }
        container.addView(theme)

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
            action(R.drawable.ic_moon, "Quiet hours",
                "${prefs.quietFrom} – ${prefs.quietTo}") {
                pickTime(prefs.quietFrom) { from ->
                    prefs.quietFrom = from
                    pickTime(prefs.quietTo) { to ->
                        prefs.quietTo = to
                        Reminders.rescheduleAll(requireContext())
                        render()
                    }
                }
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

        // Experience
        container.addView(section("EXPERIENCE"))
        container.addView(group(listOf(
            toggle("Haptics", null, prefs.hapticsEnabled) { prefs.hapticsEnabled = it },
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
                parentFragmentManager.beginTransaction()
                    .replace(R.id.list, DataManagementFragment())
                    .addToBackStack("data_management")
                    .commit()
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
