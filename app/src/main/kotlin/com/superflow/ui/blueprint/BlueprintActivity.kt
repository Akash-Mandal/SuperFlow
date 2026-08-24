package com.superflow.ui.blueprint

import android.content.Intent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.superflow.R
import com.superflow.ai.MainBrain
import com.superflow.ai.Snapshots
import com.superflow.blueprint.Compiler
import com.superflow.blueprint.CompilerV2
import com.superflow.blueprint.PdfText
import com.superflow.data.Prefs
import com.superflow.data.Repository
import com.superflow.data.model.*
import com.superflow.domain.Actor
import com.superflow.domain.CommandBus
import com.superflow.ui.common.ScrollActivity
import com.superflow.ui.common.snack
import com.superflow.ui.sheets.TextInputSheet
import com.superflow.util.Dates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Blueprint Studio: the long-horizon Intent Compiler.
 *
 * Sources -> Requirement Ledger -> target design -> execution -> verification
 * -> gap, assumption and undo report. Amendments are versioned and diffable.
 */
class BlueprintActivity : ScrollActivity() {

    private val bus by lazy { CommandBus.get(this) }
    private val repo by lazy { Repository.get(this) }
    private val prefs by lazy { Prefs.get(this) }

    private var projectId: String? = null
    private var report: String = ""
    private var lastGroupId: String? = null
    private var busy = false
    private var showAllLedger = false
    private var collapsedThemes = mutableSetOf<String>()

    private val pickFile = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) importFile(uri) }

    companion object {
        /**
         * Which project to open. Studio's transcript cards deep-link into
         * a specific mission; without this the screen would always open on
         * whatever happens to be newest, which is rarely the one that was
         * tapped.
         */
        const val EXTRA_PROJECT = "project"
    }

    override fun titleText() = getString(R.string.blueprint_studio)

    override fun onResume() {
        super.onResume()
        if (contentReady() && projectId == null) {
            projectId = repo.projects().firstOrNull()?.id
            rebuild()
        }
    }

    override fun buildContent() {
        // A deep link from Studio wins over "most recent", but only if the
        // project still exists — a card can outlive the thing it points at.
        if (projectId == null) {
            projectId = intent?.getStringExtra(EXTRA_PROJECT)
                ?.takeIf { repo.project(it) != null }
        }
        if (projectId == null) projectId = repo.projects().firstOrNull()?.id
        val project = repo.project(projectId)

        if (project == null) {
            content.addView(textCard("Turn documents into a working setup",
                "Add Markdown, text, a PDF or pasted notes plus your instructions. SuperFlow " +
                        "extracts a source-linked Requirement Ledger, designs the workspace, " +
                        "applies it, verifies the real result and reports every gap."))
            content.addView(primary("Create a mission") { newProject() })
            projectList()
            return
        }

        content.addView(textCard(project.name,
            "State: ${project.state.lowercase()} · version ${project.version} · " +
                    "${repo.sources(project.id).size} sources · " +
                    "${repo.requirements(project.id).size} requirements"))

        sourcesSection(project)
        instructionsSection(project)
        ledgerSection(project)
        autoReinforceSection(project)
        runSection(project)
        versionsSection(project)
        if (report.isNotBlank()) {
            content.addView(section("REPORT"))
            content.addView(textCard("Result", report))
            content.addView(outlined("Dismiss") { report = ""; rebuild() })
        }
        projectList()
    }

    /* -------------------------------------------------------------- sections */

    private fun sourcesSection(p: BlueprintProject) {
        content.addView(section("SOURCES"))
        val sources = repo.sources(p.id)
        if (sources.isEmpty()) {
            content.addView(textCard("No sources yet",
                "Paste notes, a plan, a journal export, or import a text/Markdown/PDF file."))
        }
        sources.forEach { s ->
            val card = layoutInflater.inflate(R.layout.item_text_card, content, false)
            card.findViewById<TextView>(R.id.text_title).text = s.name
            card.findViewById<TextView>(R.id.text_body).text =
                "${s.kind} · ${s.lineCount} lines · ${s.content.length} characters" +
                        if (s.instructions.isNotBlank()) "\nNote: ${s.instructions}" else ""
            card.setOnLongClickListener {
                repo.deleteSource(s.id); rebuild(); true
            }
            content.addView(card)
        }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(MaterialButton(this, null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Paste text"
            layoutParams = LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also { it.marginEnd = dpi(8) }
            setOnClickListener { pasteSource(p) }
        })
        row.addView(MaterialButton(this, null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Import file"
            layoutParams = LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                runCatching {
                    pickFile.launch(arrayOf("text/*", "application/pdf", "*/*"))
                }.onFailure { findViewById<View>(R.id.root).snack("No file picker available") }
            }
        })
        content.addView(row)
        content.addView(textCard("Isolation", Compiler.ISOLATION_NOTE))
    }

    private fun instructionsSection(p: BlueprintProject) {
        content.addView(section("YOUR INSTRUCTIONS"))
        val card = layoutInflater.inflate(R.layout.item_text_card, content, false)
        card.findViewById<TextView>(R.id.text_title).text = "Outrank the documents"
        card.findViewById<TextView>(R.id.text_body).text =
            p.instructions.ifBlank { "None yet. Tap to add." }
        card.setOnClickListener {
            TextInputSheet.show(supportFragmentManager, "Instructions",
                "Keep mornings light. No more than four habits.",
                subtitle = "These outrank anything written inside a source.",
                value = p.instructions, lines = 4) { text ->
                repo.saveProject(p.copy(instructions = text))
                rebuild()
            }
        }
        content.addView(card)
    }

    private fun ledgerSection(p: BlueprintProject) {
        val reqs = repo.requirements(p.id)
        content.addView(section("REQUIREMENT LEDGER"))
        if (reqs.isEmpty()) {
            content.addView(textCard("Not compiled yet",
                "Compiling reads every source line, extracts intentions, links each one to its " +
                        "citation, and flags conflicts."))
            return
        }
        content.addView(textCard("Coverage", Compiler.coverage(reqs) + if (reqs.size > 20) "\n\nShowing ${if (showAllLedger) reqs.size else 20} of ${reqs.size} — grouped by theme below." else ""))
        val grouped = reqs.groupBy { themeForRequirement(it) }.toSortedMap()
        grouped.forEach { (theme, items) ->
            val isCollapsed = collapsedThemes.contains(theme)
            val header = layoutInflater.inflate(R.layout.item_text_card, content, false)
            header.findViewById<TextView>(R.id.text_title).text = "$theme · ${items.size} ${if (items.size==1) "item" else "items"} ${if (isCollapsed) "▶" else "▼"}"
            header.findViewById<TextView>(R.id.text_body).text = if (isCollapsed) "Tap to expand" else "Tap to collapse"
            header.setOnClickListener { if (isCollapsed) collapsedThemes.remove(theme) else collapsedThemes.add(theme); rebuild() }
            content.addView(header)
            if (!isCollapsed) {
                val visible = if (showAllLedger || items.size <= 10) items else items.take(10)
                visible.forEach { r ->
                    val card = layoutInflater.inflate(R.layout.item_text_card, content, false)
                    card.findViewById<TextView>(R.id.text_title).text = r.text
                    card.findViewById<TextView>(R.id.text_body).text = buildString {
                        append("${r.citation} · ${r.status.name.lowercase()}")
                        if (r.assumption) append(" · assumption")
                        if (r.note.isNotBlank()) append("\n${r.note}")
                        if (r.plannedCommand.isNotBlank()) {
                            val cmd = runCatching { JSONObject(r.plannedCommand).optString("command") }.getOrDefault("")
                            append("\nPlans to run: $cmd")
                        }
                    }
                    card.setOnClickListener {
                        val next = if (r.status == RequirementStatus.REJECTED) RequirementStatus.ACCEPTED else RequirementStatus.REJECTED
                        repo.saveRequirement(r.copy(status = next)); rebuild()
                    }
                    card.alpha = 0.96f
                    card.setPadding(card.paddingLeft + dpi(8), card.paddingTop, card.paddingRight, card.paddingBottom)
                    content.addView(card)
                }
                if (!showAllLedger && items.size > 10) {
                    content.addView(outlined("Show ${items.size - 10} more in $theme") { showAllLedger = true; rebuild() })
                }
            }
        }
        if (!showAllLedger && reqs.size > 20) {
            content.addView(outlined("Show all ${reqs.size} (ungrouped)") { showAllLedger = true; rebuild() })
        } else if (showAllLedger && reqs.size > 20) {
            content.addView(outlined("Collapse all") { showAllLedger = false; rebuild() })
        }
        content.addView(textCard("Tip", "Grouped by theme (Movement/Mindfulness/...). Tap header to collapse. Large plans are intelligently phased — only phase 0 shows; future phases are Auto Reinforce."))
    }

    private fun themeForRequirement(r: Requirement): String {
        val t = r.text.lowercase()
        return when {
            t.contains("walk") || t.contains("run") || t.contains("exercise") || t.contains("stretch") || t.contains("fitness") -> "Movement"
            t.contains("meditat") || t.contains("mindful") || t.contains("breath") || t.contains("calm") || t.contains("yoga") -> "Mindfulness"
            t.contains("read") || t.contains("book") || t.contains("study") || t.contains("learn") -> "Learning"
            t.contains("eat") || t.contains("nutrition") || t.contains("food") || t.contains("water") || t.contains("protein") -> "Nutrition"
            t.contains("sleep") || t.contains("bed") || t.contains("wind down") -> "Sleep"
            t.contains("work") || t.contains("focus") || t.contains("deep") -> "Focus"
            t.contains("family") || t.contains("friend") || t.contains("partner") -> "Relationships"
            t.contains("save") || t.contains("money") || t.contains("budget") || t.contains("finance") -> "Finance"
            t.contains("write") || t.contains("creative") || t.contains("art") || t.contains("music") -> "Creativity"
            else -> "General"
        }
    }

    private fun autoReinforceSection(p: BlueprintProject) {
        try {
            val db = com.superflow.data.db.SuperFlowDatabase.get(this).db
            val cur = db.query("SELECT id, phaseIndex, whenExpr, whereKind, status FROM blueprint_auto_plan WHERE projectId=? ORDER BY phaseIndex", arrayOf(p.id))
            val rows = mutableListOf<String>()
            cur.use { while (it.moveToNext()) rows.add("Phase ${it.getInt(1)} · ${it.getString(2)} · ${it.getString(3)} · ${it.getString(4)}") }
            content.addView(section("AUTO REINFORCE"))
            val mode = Prefs.get(this).autoReinforceMode
            content.addView(textCard("Mode: $mode (from AI Engine)", if (rows.isEmpty()) "No pending phases — compile a large plan to create them. You can also trigger via Studio chat: \"reinforce now\"." else "${rows.size} pending phase(s) scheduled. Trigger via chat or below."))
            if (rows.isNotEmpty()) {
                rows.forEachIndexed { idx, txt ->
                    val card = layoutInflater.inflate(R.layout.item_text_card, content, false)
                    card.findViewById<TextView>(R.id.text_title).text = "Pending ${idx + 1}"
                    card.findViewById<TextView>(R.id.text_body).text = txt
                    content.addView(card)
                }
                content.addView(outlined("Trigger pending now") {
                    lifecycleScope.launch { com.superflow.domain.CommandBus.get(this@BlueprintActivity).execute("trigger_auto_reinforce", org.json.JSONObject().put("projectId", p.id), com.superflow.domain.Actor.USER); rebuild() }
                })
            }
        } catch (_: Exception) {
            content.addView(section("AUTO REINFORCE"))
            content.addView(textCard("Auto Reinforce", "Trigger via Studio: \"reinforce now\" — applies pending phase when enabled."))
        }
    }

    private fun runSection(p: BlueprintProject) {
        content.addView(section("RUN"))
        content.addView(primary(if (busy) "Working…" else "Compile requirements") {
            if (!busy) compile(p)
        })

        val reqs = repo.requirements(p.id)
        val ready = reqs.count {
            it.status == RequirementStatus.ACCEPTED && it.plannedCommand.isNotBlank()
        }
        content.addView(textCard(
            if (reqs.isEmpty()) "Nothing to apply yet" else "$ready ready to apply",
            if (prefs.fullControlActive())
                "Full Control is on, so building runs without further asks. A snapshot is taken " +
                        "first and every action is individually undoable."
            else "Guided mode: you will be asked to confirm before anything is applied."
        ))
        if (ready > 0) content.addView(primary("Build the workspace ($ready)") { execute(p) })

        lastGroupId?.let { group ->
            content.addView(outlined("Undo this whole build") {
                val res = bus.undoGroup(group)
                findViewById<View>(R.id.root).snack(res.message)
                lastGroupId = null
                rebuild()
            })
        }

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(MaterialButton(this, null,
            androidx.appcompat.R.attr.borderlessButtonStyle).apply {
            text = "Audit setup"
            layoutParams = LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { audit() }
        })
        row.addView(MaterialButton(this, null,
            androidx.appcompat.R.attr.borderlessButtonStyle).apply {
            text = "Design pack"
            layoutParams = LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { exportPack(p) }
        })
        content.addView(row)
        content.addView(MaterialButton(this, null,
            androidx.appcompat.R.attr.borderlessButtonStyle).apply {
            text = "Delete mission"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                MaterialAlertDialogBuilder(this@BlueprintActivity)
                    .setTitle("Delete \"${p.name}\"?")
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.delete) { _, _ ->
                        repo.deleteProject(p.id); projectId = null; report = ""; rebuild()
                    }.show()
            }
        })
    }

    /** Amendment history with a diff against the previous ledger version. */
    private fun versionsSection(p: BlueprintProject) {
        val versions = repo.versions(p.id)
        if (versions.isEmpty()) return
        content.addView(section("AMENDMENT HISTORY"))
        versions.take(6).forEach { v ->
            val card = layoutInflater.inflate(R.layout.item_text_card, content, false)
            card.findViewById<TextView>(R.id.text_title).text = "v${v.version} · ${v.label}"
            card.findViewById<TextView>(R.id.text_body).text =
                "${Dates.stamp(v.createdAt)}  ·  tap to diff against now"
            card.setOnClickListener { showDiff(p, v) }
            content.addView(card)
        }
    }

    private fun showDiff(p: BlueprintProject, version: BlueprintVersion) {
        val previous = runCatching {
            val arr = JSONArray(version.ledgerJson)
            (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
                .map { com.superflow.domain.Serial.requirement(it) }
        }.getOrDefault(emptyList())
        val diff = Compiler.diff(previous, repo.requirements(p.id))
        MaterialAlertDialogBuilder(this)
            .setTitle("Changes since v${version.version}")
            .setMessage(buildString {
                append("Added: ${diff.added.size}\n")
                diff.added.take(6).forEach { append("  + ${it.take(60)}\n") }
                append("\nRemoved: ${diff.removed.size}\n")
                diff.removed.take(6).forEach { append("  − ${it.take(60)}\n") }
                append("\nStatus changes: ${diff.changed.size}\n")
                diff.changed.take(8).forEach { append("  ~ $it\n") }
            })
            .setPositiveButton(R.string.close, null)
            .show()
    }

    private fun projectList() {
        val all = repo.projects()
        if (all.isEmpty()) return
        content.addView(section("MISSIONS"))
        all.forEach { p ->
            val card = layoutInflater.inflate(R.layout.item_text_card, content, false)
            card.findViewById<TextView>(R.id.text_title).text = p.name
            card.findViewById<TextView>(R.id.text_body).text =
                "${p.state.lowercase()} · v${p.version} · ${repo.sources(p.id).size} sources"
            card.setOnClickListener { projectId = p.id; report = ""; rebuild() }
            content.addView(card)
        }
        content.addView(outlined("New mission") { newProject() })
    }

    /* --------------------------------------------------------------- actions */

    private fun newProject() {
        TextInputSheet.show(supportFragmentManager, "New mission", "My 2026 reset") { name ->
            val p = BlueprintProject(
                name = name.trim().ifBlank {
                    "Mission ${com.superflow.core.time.SfTime.shortDay(repo.clock.today())}" }
            )
            repo.saveProject(p)
            projectId = p.id
            rebuild()
        }
    }

    private fun pasteSource(p: BlueprintProject) {
        TextInputSheet.show(supportFragmentManager, "Paste a source",
            "Paste your notes, plan or journal", lines = 8) { text ->
            if (text.isBlank()) return@show
            repo.saveSource(BlueprintSource(
                projectId = p.id,
                name = "pasted-${repo.sources(p.id).size + 1}.md",
                kind = "pasted", content = text, lineCount = text.lines().size
            ))
            rebuild()
        }
    }

    private fun importFile(uri: android.net.Uri) {
        val p = repo.project(projectId) ?: return
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: return@runCatching null
                    if (bytes.size > 2_000_000) return@runCatching "TOOBIG"
                    val isPdf = PdfText.looksLikePdf(bytes)
                    val text = if (isPdf) PdfText.extract(bytes)
                    else String(bytes, Charsets.UTF_8)
                    if (text.isBlank()) return@runCatching "EMPTY"
                    val name = uri.lastPathSegment?.substringAfterLast('/') ?: "imported"
                    repo.saveSource(BlueprintSource(
                        projectId = p.id, name = name,
                        kind = if (isPdf) "pdf" else "text",
                        content = text, lineCount = text.lines().size
                    ))
                    "OK"
                }.getOrNull()
            }
            when (result) {
                "OK" -> rebuild()
                "TOOBIG" -> findViewById<View>(R.id.root)
                    .snack("Larger than 2 MB. Split it or paste the relevant part.")
                "EMPTY" -> findViewById<View>(R.id.root)
                    .snack("No readable text. For scanned PDFs, paste the text instead.")
                else -> findViewById<View>(R.id.root).snack("Could not read that file")
            }
        }
    }

    private fun compile(p: BlueprintProject) {
        val sources = repo.sources(p.id)
        if (sources.isEmpty() && p.instructions.isBlank()) {
            findViewById<View>(R.id.root).snack("Add a source or some instructions first")
            return
        }
        busy = true
        rebuild()
        lifecycleScope.launch {
            // Save the previous ledger as a version before replacing it.
            val previous = repo.requirements(p.id)
            if (previous.isNotEmpty()) {
                val arr = JSONArray()
                previous.forEach { arr.put(com.superflow.domain.Serial.of(it)) }
                repo.saveVersion(BlueprintVersion(
                    projectId = p.id, version = p.version,
                    label = "before recompile", ledgerJson = arr.toString()
                ))
            }

            var reqs = withContext(Dispatchers.IO) {
                repo.clearRequirements(p.id)
                val v1 = Compiler.extractRequirements(p, sources)
                if (v1.size > 20) {
                    val intent = CompilerV2.captureIntent(goal = p.instructions.ifBlank { "Build a habit system" }, dailyTimeMinutes = 30, durationWeeks = 12)
                    val plan = CompilerV2.compileForBlueprint(p, sources, intent)
                    val phase0 = CompilerV2.compilePhase(plan.phases.first(), p.id, 0)
                    phase0.onEach { repo.saveRequirement(it) }
                    val db = com.superflow.data.db.SuperFlowDatabase.get(this@BlueprintActivity).db
                    plan.phases.drop(1).forEachIndexed { idx, ph ->
                        val auto = CompilerV2.compilePhase(ph, p.id, idx + 1)
                        for (req in auto) {
                            try {
                                db.execSQL("INSERT OR REPLACE INTO blueprint_auto_plan VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                                    arrayOf(java.util.UUID.randomUUID().toString(), p.id, idx + 1, req.plannedCommand ?: "{}", "WEEK:${ph.weekStart}", ph.focusArea, "ADD", null, "PENDING", System.currentTimeMillis(), null))
                            } catch (_: Exception) {}
                        }
                    }
                    phase0
                } else {
                    v1.onEach { repo.saveRequirement(it) }
                }
            }

            var refined = 0
            if (prefs.blueprintCloudRefine && prefs.cloudReady()) {
                refined = withContext(Dispatchers.IO) {
                    val reply = MainBrain.chat(
                        prefs, "You refine requirement ledgers. Reply with JSON only.",
                        emptyList(), Compiler.refinementPrompt(p, reqs)
                    )
                    if (reply.ok) Compiler.applyRefinement(repo, reqs, reply.text) else 0
                }
                reqs = repo.requirements(p.id)
            }

            repo.saveProject(p.copy(state = "COMPILED", version = p.version + 1))
            report = "Compiled ${reqs.size} requirements from ${sources.size} sources.\n\n" +
                    Compiler.coverage(reqs) +
                    (if (refined > 0) "\n\nCloud refinement adjusted $refined rows." else "") +
                    "\n\nNothing has been applied yet. Review the ledger, then build."
            busy = false
            rebuild()
        }
    }

    private fun execute(p: BlueprintProject) {
        val run = {
            busy = true
            rebuild()
            lifecycleScope.launch {
                val summary = withContext(Dispatchers.IO) { doExecute(p) }
                report = summary
                busy = false
                rebuild()
            }
            Unit
        }
        if (prefs.fullControlActive()) run()
        else MaterialAlertDialogBuilder(this)
            .setTitle("Apply the accepted requirements?")
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton("Build") { _, _ -> run() }
            .show()
    }

    private fun doExecute(p: BlueprintProject): String {
        Snapshots.save(this, bus)
        val group = newId()
        lastGroupId = group
        var applied = 0
        val failures = ArrayList<String>()

        for (r in repo.requirements(p.id)) {
            if (r.status != RequirementStatus.ACCEPTED || r.plannedCommand.isBlank()) continue
            val obj = runCatching { JSONObject(r.plannedCommand) }.getOrNull() ?: continue
            val res = bus.execute(
                obj.optString("command"), obj.optJSONObject("args") ?: JSONObject(),
                Actor.AI, group
            )
            if (res.ok) {
                applied++
                repo.saveRequirement(r.copy(status = RequirementStatus.IMPLEMENTED))
            } else {
                failures.add("${r.text.take(50)}: ${res.message}")
                repo.saveRequirement(r.copy(status = RequirementStatus.GAP, note = res.message))
            }
        }

        // Verify against real app state, never against model text.
        val after = repo.requirements(p.id)
        val (verified, gaps) = Compiler.verify(repo, after)
        after.filter { it.status == RequirementStatus.IMPLEMENTED && it !in gaps }
            .forEach { repo.saveRequirement(it.copy(status = RequirementStatus.VERIFIED)) }
        gaps.forEach {
            repo.saveRequirement(it.copy(status = RequirementStatus.GAP,
                note = "Planned but not found in the database afterwards"))
        }
        repo.saveProject(p.copy(state = "VERIFIED"))
        try {
            if (repo.flows().isEmpty() && applied >= 2) {
                val db2 = com.superflow.data.db.SuperFlowDatabase.get(this@BlueprintActivity).db
                db2.execSQL("INSERT OR IGNORE INTO proactive_suggestion VALUES (?,?,?,?,?,?,?,?)",
                    arrayOf(java.util.UUID.randomUUID().toString(), "GROWTH", "Blueprint created ${applied} habits — consider generating a Routine/Flow to chain them. Ask Studio: 'create a morning flow with my new habits' or apply via Flows.", "MEDIUM", """{"command":"create_flow","args":{"title":"Morning Flow"}}""", null, 0, 0, System.currentTimeMillis()))
            }
        } catch (_: Exception) {}

        return buildString {
            append("BUILD REPORT\n\n")
            append("Applied: $applied\n")
            append("Verified against the database: $verified\n")
            append("Gaps: ${gaps.size}\n")
            append("Deferred: ${after.count { it.status == RequirementStatus.DEFERRED }}\n")
            append("Rejected: ${after.count { it.status == RequirementStatus.REJECTED }}\n")
            if (failures.isNotEmpty()) {
                append("\nDid not complete:\n")
                failures.forEach { append("· $it\n") }
            }
            if (gaps.isNotEmpty()) {
                append("\nGaps to handle manually:\n")
                gaps.forEach { append("· ${it.text.take(70)} (${it.citation})\n") }
            }
            append("\nA snapshot was taken before this run, and every action can be undone " +
                    "individually or as one group from Activity.")
        }
    }

    private fun audit() {
        report = buildString {
            append("CURRENT SETUP AUDIT\n\n")
            append("Identities: ${repo.identities().size}\n")
            append("Goals: ${repo.goals().size}\n")
            append("Systems: ${repo.systems().size}\n")
            append("Habits: ${repo.habits().size}\n")
            append("Check-ins: ${repo.checkIns().size}\n\n")
            val habits = repo.habits()
            append("Design gaps\n")
            append("· Without a tiny start: ${habits.count { it.tinyStart.isBlank() }}\n")
            append("· Without a cue or anchor: " +
                    "${habits.count { it.cueTime.isBlank() && it.anchorText.isBlank() }}\n")
            append("· Without an immediate reward: ${habits.count { it.reward.isBlank() }}\n")
            append("· Not linked to an identity: ${habits.count { it.identityId == null }}\n")
            if (habits.size > 6) {
                append("\nYou are running ${habits.size} habits. Adding more than about three " +
                        "new behaviours at once usually reduces all of them.\n")
            }
        }
        rebuild()
    }

    private fun exportPack(p: BlueprintProject) {
        val md = buildString {
            append("# SuperFlow Design Pack — ${p.name}\n\n")
            append("_Generated ${com.superflow.core.time.SfTime.humanDay(repo.clock.today())}_\n\n")
            append("## Identities\n")
            repo.identities().forEach { append("- ${it.statement} (${it.lifeArea.label})\n") }
            append("\n## Goals\n")
            repo.goals().forEach {
                append("- ${it.title}${if (it.why.isNotBlank()) " — ${it.why}" else ""}\n")
            }
            append("\n## Systems\n")
            repo.systems().forEach { append("- ${it.title}\n") }
            append("\n## Habits\n")
            repo.habits().forEach { h ->
                append("### ${h.title}\n")
                append("- Contract: ${h.contract()}\n")
                append("- Ladder: tiny=${h.tinyStart} | minimum=${h.minimumVersion} | " +
                        "standard=${h.standardVersion} | stretch=${h.stretchVersion}\n")
                append("- Schedule: ${com.superflow.domain.Capabilities.daysLabel(h)}\n\n")
            }
            append("\n## Requirement ledger\n")
            repo.requirements(p.id).forEach {
                append("- [${it.status.name.lowercase()}] ${it.text} (${it.citation})\n")
            }
        }
        report = md
        rebuild()
        runCatching {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/markdown"
                putExtra(Intent.EXTRA_SUBJECT, "SuperFlow Design Pack")
                putExtra(Intent.EXTRA_TEXT, md)
            }, "Export design pack"))
        }
    }

    /* --------------------------------------------------------------- widgets */

    private fun primary(label: String, onClick: () -> Unit) = MaterialButton(this).apply {
        text = label
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).also { it.topMargin = dpi(4); it.bottomMargin = dpi(4) }
        setOnClickListener { onClick() }
    }

    private fun outlined(label: String, onClick: () -> Unit) = MaterialButton(
        this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
    ).apply {
        text = label
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).also { it.topMargin = dpi(4); it.bottomMargin = dpi(4) }
        setOnClickListener { onClick() }
    }

    private fun dpi(v: Int) = (v * resources.displayMetrics.density).toInt()
}
