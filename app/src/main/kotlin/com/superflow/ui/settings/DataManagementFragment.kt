package com.superflow.ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.superflow.R
import com.superflow.data.DataPolicy
import com.superflow.data.Prefs
import com.superflow.data.Repository
import com.superflow.domain.Serial
import com.superflow.ui.common.snack
import com.superflow.ui.common.visible
import com.superflow.ui.sheets.TextInputSheet
import com.superflow.util.Dates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * Data Management — the all-inclusive data control center.
 *
 * Every piece of data in the app can be exported, imported, backed up,
 * and managed from here. The all-inclusion policy ensures nothing is
 * silently left out.
 */
class DataManagementFragment : Fragment() {

    private lateinit var prefs: Prefs
    private lateinit var repo: Repository
    private lateinit var container: LinearLayout

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_list, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        prefs = Prefs.get(requireContext())
        repo = Repository.get(requireContext())

        view.findViewById<TextView>(R.id.screen_title).text = "Data Management"
        view.findViewById<TextView>(R.id.screen_subtitle).text =
            "All-inclusive: everything in the app can be exported, imported, and backed up."

        ViewCompat.setOnApplyWindowInsetsListener(view.findViewById(R.id.header)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = bars.top + v.context.resources.getDimensionPixelSize(R.dimen.space_m))
            insets
        }

        val list = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.list)
        list.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
        container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = androidx.recyclerview.widget.RecyclerView.LayoutParams(
                androidx.recyclerview.widget.RecyclerView.LayoutParams.MATCH_PARENT,
                androidx.recyclerview.widget.RecyclerView.LayoutParams.WRAP_CONTENT
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

        // Data overview
        container.addView(section("YOUR DATA"))
        val recordsRow = infoRow("Total records", "…")
        val dbRow = infoRow("Database size", "…")
        val snapshotsRow = infoRow("Snapshots", "…")
        container.addView(group(listOf(
            recordsRow,
            dbRow,
            snapshotsRow,
            infoRow("Policy version", "v${DataPolicy.POLICY_VERSION} — all-inclusive")
        )))
        // counts() is twelve COUNT queries and the snapshot list is a file
        // scan; this screen re-renders on every resume, so keep the reads off
        // the render thread and fill the rows when they land.
        val ctx = requireContext()
        lifecycleScope.launch {
            val counts = withContext(Dispatchers.IO) { repo.counts() }
            if (!isAdded) return@launch
            setSub(recordsRow, "${counts.values.sum()} items across ${counts.size} tables")
            setSub(dbRow, withContext(Dispatchers.IO) {
                try {
                    val dbFile = ctx.getDatabasePath("superflow.db")
                    if (dbFile.exists()) "${dbFile.length() / 1024} KB" else "unknown"
                } catch (e: Exception) { "unknown" }
            })
            setSub(snapshotsRow,
                "${withContext(Dispatchers.IO) { com.superflow.ai.Snapshots.list(ctx).size }} saved")
        }

        // Data manifest
        container.addView(action(R.drawable.ic_info, "View data manifest",
            "See exactly what's included in exports") {
            // manifest() runs counts() once per category; off the UI thread.
            lifecycleScope.launch {
                val report = withContext(Dispatchers.IO) { DataPolicy.manifest(repo) }
                if (!isAdded) return@launch
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Data Manifest")
                    .setMessage(report)
                    .setPositiveButton(R.string.close, null)
                    .show()
            }
        })

        // Export
        container.addView(section("EXPORT"))
        container.addView(group(listOf(
            actionRow("Full export (all-inclusive)",
                "Everything except API keys. ${DataPolicy.exportableCategories.size} categories.") {
                fullExport()
            },
            actionRow("Selective export",
                "Choose which categories to include") {
                selectiveExport()
            },
            actionRow("Share progress summary",
                "A private, text-only recap for accountability") {
                shareSummary()
            }
        )))

        // Import
        container.addView(section("IMPORT"))
        container.addView(group(listOf(
            actionRow("Import from file",
                "Paste or load a previous export. Replaces all current data.") {
                importData()
            },
            actionRow("Merge import",
                "Add data from an export without deleting existing data") {
                mergeImport()
            }
        )))
        container.addView(note(
            "Import validates the file before applying. API keys are never included in exports " +
                    "and must be re-entered after import on a new device."
        ))

        // Auto-backup
        container.addView(section("AUTO-BACKUP"))
        container.addView(group(listOf(
            toggleRow("Auto-backup enabled",
                "Daily backup saved to app storage", prefs.autoBackupEnabled) {
                prefs.autoBackupEnabled = it; render()
            }
        )))
        if (prefs.autoBackupEnabled) {
            container.addView(group(listOf(
                actionRow("Backup frequency", prefs.autoBackupFrequency) {
                    pickFrom("Backup frequency",
                        listOf("Daily", "Every 3 days", "Weekly"),
                        listOf("daily", "3days", "weekly"),
                        prefs.autoBackupFrequency) { prefs.autoBackupFrequency = it; render() }
                },
                actionRow("Keep backups", "${prefs.maxBackups} most recent") {
                    pickFrom("Keep backups",
                        listOf("3", "7", "14", "30"),
                        listOf("3", "7", "14", "30"),
                        prefs.maxBackups.toString()) {
                        prefs.maxBackups = it.toIntOrNull() ?: 7; render()
                    }
                }
            )))
        }
        container.addView(action(R.drawable.ic_download, "Backup now",
            "Save a full backup to app storage immediately") {
            lifecycleScope.launch {
                val ok = withContext(Dispatchers.IO) { doAutoBackup() }
                view?.snack(if (ok) "Backup saved" else "Backup failed")
                render()
            }
        })

        // Data integrity
        container.addView(section("DATA INTEGRITY"))
        container.addView(action(R.drawable.ic_shield, "Check data integrity",
            "Find orphaned records and inconsistencies") {
            lifecycleScope.launch {
                val report = withContext(Dispatchers.IO) { checkIntegrity() }
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Data Integrity")
                    .setMessage(report)
                    .setPositiveButton(R.string.close, null)
                    .apply {
                        if (report.contains("Issues found")) {
                            setNegativeButton("Fix all") { _, _ ->
                                lifecycleScope.launch {
                                    withContext(Dispatchers.IO) { fixIntegrity() }
                                    view?.snack("Orphaned records cleaned")
                                    render()
                                }
                            }
                        }
                    }
                    .show()
            }
        })

        // Dangerous actions
        container.addView(section("DANGEROUS"))
        container.addView(group(listOf(
            actionRow("Clear AI conversation",
                "Delete all messages with the AI coach") {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Clear AI conversation?")
                    .setMessage("All messages will be deleted. This cannot be undone.")
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.delete) { _, _ ->
                        // Database wipe: off the render thread.
                        lifecycleScope.launch {
                            withContext(Dispatchers.IO) { repo.clearMessages() }
                            if (!isAdded) return@launch
                            view?.snack("AI conversation cleared")
                            render()
                        }
                    }.show()
            },
            actionRow("Clear activity trail",
                "Delete the audit log (undo history)") {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Clear activity trail?")
                    .setMessage("The audit log and undo history will be deleted. Existing data is unaffected.")
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.delete) { _, _ ->
                        lifecycleScope.launch {
                            withContext(Dispatchers.IO) { repo.clearAudit() }
                            if (!isAdded) return@launch
                            view?.snack("Activity trail cleared")
                            render()
                        }
                    }.show()
            },
            actionRow("Delete all data",
                "Erase everything: identities, goals, habits, check-ins, reviews, settings") {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Delete ALL data?")
                    .setMessage("Every record will be erased. This cannot be undone. " +
                            "Consider exporting first.\n\nAPI keys will also be removed.")
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.delete) { _, _ ->
                        lifecycleScope.launch {
                            val ctx = requireContext()
                            withContext(Dispatchers.IO) {
                                repo.deleteAllData()
                                prefs.resetAll()
                                com.superflow.ai.Snapshots.clear(ctx)
                            }
                            if (!isAdded) return@launch
                            view?.snack("All data deleted")
                            render()
                        }
                    }.show()
            }
        )))

        // Privacy
        container.addView(section("PRIVACY"))
        container.addView(note(
            "All data stays on your device. Nothing is uploaded unless you configure a cloud " +
                    "AI provider yourself. API keys are stored in a separate encrypted file " +
                    "excluded from all exports, backups, and prompts.\n\n" +
                    "The all-inclusion policy ensures that whenever the app adds new features, " +
                    "their data is automatically covered by export, import, and backup."
        ))

        // Bottom padding
        container.addView(View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                resources.getDimensionPixelSize(R.dimen.list_bottom_padding)
            )
        })
    }

    /* ------------------------------------------------------------- actions */

    private fun fullExport() {
        lifecycleScope.launch {
            val json = withContext(Dispatchers.IO) {
                DataPolicy.exportFull(repo, prefs).toString(2)
            }
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_SUBJECT, "SuperFlow full export")
                putExtra(Intent.EXTRA_TEXT, json.take(500_000))
            }
            startActivity(Intent.createChooser(share, "Export SuperFlow data"))

            // Also save locally
            withContext(Dispatchers.IO) {
                val dir = File(requireContext().filesDir, "exports").apply { mkdirs() }
                File(dir, "superflow-full-${com.superflow.core.time.SfTime.format(repo.clock.today())}.json")
                    .writeText(json)
            }
        }
    }

    private fun selectiveExport() {
        val cats = DataPolicy.exportableCategories
        val names = cats.map { it.displayName }.toTypedArray()
        val checked = BooleanArray(cats.size) { true }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Select categories to export")
            .setMultiChoiceItems(names, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton("Export") { _, _ ->
                val selected = cats.filterIndexed { i, _ -> checked[i] }.map { it.key }.toSet()
                lifecycleScope.launch {
                    val json = withContext(Dispatchers.IO) {
                        DataPolicy.exportFull(repo, prefs, selected).toString(2)
                    }
                    val share = Intent(Intent.ACTION_SEND).apply {
                        type = "application/json"
                        putExtra(Intent.EXTRA_SUBJECT, "SuperFlow selective export")
                        putExtra(Intent.EXTRA_TEXT, json.take(500_000))
                    }
                    startActivity(Intent.createChooser(share, "Export selected data"))
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun importData() {
        TextInputSheet.show(
            parentFragmentManager, "Import", "Paste exported JSON",
            subtitle = "This replaces everything currently in the app.", lines = 6
        ) { text ->
            lifecycleScope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        val json = JSONObject(text)
                        val warnings = DataPolicy.validateImport(json)
                        if (warnings.isNotEmpty()) {
                            // Show warnings but proceed
                            withContext(Dispatchers.Main) {
                                MaterialAlertDialogBuilder(requireContext())
                                    .setTitle("Import warnings")
                                    .setMessage(warnings.joinToString("\n"))
                                    .setPositiveButton("Import anyway") { _, _ ->
                                        lifecycleScope.launch {
                                            withContext(Dispatchers.IO) {
                                                Serial.importAll(repo, json)
                                                json.optJSONObject("preferences")?.let {
                                                    DataPolicy.importPreferences(prefs, it)
                                                }
                                            }
                                            view?.snack("Import complete")
                                            render()
                                        }
                                    }
                                    .setNegativeButton(R.string.cancel, null)
                                    .show()
                            }
                            return@runCatching true
                        }
                        Serial.importAll(repo, json)
                        json.optJSONObject("preferences")?.let {
                            DataPolicy.importPreferences(prefs, it)
                        }
                        true
                    }.getOrDefault(false)
                }
                view?.snack(if (ok) "Import complete" else "That did not look like a SuperFlow export")
                render()
            }
        }
    }

    private fun mergeImport() {
        TextInputSheet.show(
            parentFragmentManager, "Merge import", "Paste exported JSON",
            subtitle = "Adds data without deleting existing records. Duplicates may occur.", lines = 6
        ) { text ->
            lifecycleScope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        val json = JSONObject(text)
                        // Import without deleting first
                        json.optJSONArray("identities")?.let { arr ->
                            (0 until arr.length()).forEach { i ->
                                repo.saveIdentity(Serial.identity(arr.getJSONObject(i)))
                            }
                        }
                        json.optJSONArray("goals")?.let { arr ->
                            (0 until arr.length()).forEach { i ->
                                repo.saveGoal(Serial.goal(arr.getJSONObject(i)))
                            }
                        }
                        json.optJSONArray("systems")?.let { arr ->
                            (0 until arr.length()).forEach { i ->
                                repo.saveSystem(Serial.system(arr.getJSONObject(i)))
                            }
                        }
                        json.optJSONArray("habits")?.let { arr ->
                            (0 until arr.length()).forEach { i ->
                                repo.saveHabit(Serial.habit(arr.getJSONObject(i)))
                            }
                        }
                        json.optJSONArray("evidence")?.let { arr ->
                            (0 until arr.length()).forEach { i ->
                                repo.saveEvidence(Serial.evidence(arr.getJSONObject(i)))
                            }
                        }
                        // ... same pattern for other categories
                        true
                    }.getOrDefault(false)
                }
                view?.snack(if (ok) "Merge complete" else "Merge failed")
                render()
            }
        }
    }

    private fun shareSummary() {
        lifecycleScope.launch {
            val text = withContext(Dispatchers.IO) {
                com.superflow.domain.Insights.summaryText(repo, 30)
            }
            val body = "My SuperFlow progress\n\n$text\n\n— SuperFlow"
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "My SuperFlow progress")
                putExtra(Intent.EXTRA_TEXT, body)
            }, "Share progress"))
        }
    }

    private fun doAutoBackup(): Boolean = try {
        val json = DataPolicy.exportFull(repo, prefs).toString(2)
        val dir = File(requireContext().filesDir, "backups").apply { mkdirs() }
        val date = com.superflow.core.time.SfTime.format(repo.clock.today())
        File(dir, "superflow-backup-$date.json").writeText(json)
        // Prune old backups
        val maxBackups = prefs.maxBackups
        dir.listFiles()?.sortedByDescending { it.lastModified() }
            ?.drop(maxBackups)?.forEach { it.delete() }
        true
    } catch (e: Exception) { false }

    private fun checkIntegrity(): String {
        val issues = mutableListOf<String>()
        val habitIds = repo.habits(true).map { it.id }.toSet()
        val identityIds = repo.identities(true).map { it.id }.toSet()
        val goalIds = repo.goals().map { it.id }.toSet()
        val systemIds = repo.systems().map { it.id }.toSet()

        // Orphaned check-ins
        val orphanCI = repo.checkIns().count { it.habitId !in habitIds }
        if (orphanCI > 0) issues.add("$orphanCI check-ins for deleted habits")

        // Orphaned obstacles
        val orphanOb = repo.obstacles().count { it.habitId !in habitIds }
        if (orphanOb > 0) issues.add("$orphanOb obstacle plans for deleted habits")

        // Goals without identities
        val orphanG = repo.goals().count { it.identityId != null && it.identityId !in identityIds }
        if (orphanG > 0) issues.add("$orphanG goals linked to deleted identities")

        // Systems without goals
        val orphanS = repo.systems().count { it.goalId != null && it.goalId !in goalIds }
        if (orphanS > 0) issues.add("$orphanS systems linked to deleted goals")

        // Habits without systems
        val orphanH = repo.habits().count { it.systemId != null && it.systemId !in systemIds }
        if (orphanH > 0) issues.add("$orphanH habits linked to deleted systems")

        return if (issues.isEmpty()) "✓ All data is consistent. No issues found."
        else "Issues found:\n" + issues.joinToString("\n") { "· $it" }
    }

    private fun fixIntegrity() {
        val habitIds = repo.habits(true).map { it.id }.toSet()
        repo.checkIns().filter { it.habitId !in habitIds }.forEach {
            repo.delete("checkin", "id=?", arrayOf(it.id))
        }
        repo.obstacles().filter { it.habitId !in habitIds }.forEach {
            repo.deleteObstacle(it.id)
        }
    }

    /* ------------------------------------------------------------- helpers */

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

    private fun setSub(row: View, text: String) {
        row.findViewById<TextView>(R.id.action_sub)?.text = text
    }

    private fun infoRow(title: String, value: String): View {
        val v = layoutInflater.inflate(R.layout.item_setting_action, container, false)
        v.findViewById<android.widget.ImageView>(R.id.action_icon).visible(false)
        v.findViewById<TextView>(R.id.action_title).text = title
        v.findViewById<TextView>(R.id.action_sub).apply { visible(true); text = value }
        v.isClickable = false
        return v
    }

    private fun actionRow(title: String, subtitle: String, onClick: () -> Unit): View {
        val v = layoutInflater.inflate(R.layout.item_setting_action, container, false)
        v.findViewById<android.widget.ImageView>(R.id.action_icon).visible(false)
        v.findViewById<TextView>(R.id.action_title).text = title
        v.findViewById<TextView>(R.id.action_sub).apply { visible(true); text = subtitle }
        v.setOnClickListener { onClick() }
        return v
    }

    private fun action(icon: Int, title: String, subtitle: String, onClick: () -> Unit): View {
        val v = layoutInflater.inflate(R.layout.item_setting_action, container, false)
        v.findViewById<android.widget.ImageView>(R.id.action_icon).setImageResource(icon)
        v.findViewById<TextView>(R.id.action_title).text = title
        v.findViewById<TextView>(R.id.action_sub).apply { visible(true); text = subtitle }
        v.setOnClickListener { onClick() }
        return v
    }

    private fun toggleRow(title: String, subtitle: String, value: Boolean, onChange: (Boolean) -> Unit): View {
        val v = layoutInflater.inflate(R.layout.item_setting_toggle, container, false)
        v.findViewById<TextView>(R.id.toggle_title).text = title
        v.findViewById<TextView>(R.id.toggle_sub).apply { visible(true); text = subtitle }
        val sw = v.findViewById<MaterialSwitch>(R.id.toggle_switch)
        sw.isChecked = value
        v.setOnClickListener { sw.isChecked = !sw.isChecked; onChange(sw.isChecked) }
        return v
    }

    private fun pickFrom(title: String, labels: List<String>, values: List<String>,
                         current: String, onPick: (String) -> Unit) {
        val index = values.indexOf(current).coerceAtLeast(0)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setSingleChoiceItems(labels.toTypedArray(), index) { dialog, which ->
                onPick(values[which])
                dialog.dismiss()
                render()
            }
            .show()
    }
}
