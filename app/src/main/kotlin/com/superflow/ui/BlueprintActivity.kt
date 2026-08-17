package com.superflow.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import com.superflow.ai.Snapshots
import com.superflow.blueprint.Compiler
import com.superflow.data.BlueprintProject
import com.superflow.data.BlueprintSource
import com.superflow.data.Requirement
import com.superflow.data.RequirementStatus
import com.superflow.data.newId
import com.superflow.domain.Actor
import com.superflow.domain.CommandBus
import com.superflow.util.Dates
import org.json.JSONObject

/**
 * Blueprint Studio: the long-horizon Intent Compiler.
 *
 * Sources -> Requirement Ledger -> target design -> execution -> verification
 * -> gap, assumption and undo report.
 */
class BlueprintActivity : Activity() {

    private lateinit var bus: CommandBus
    private lateinit var host: FrameLayout
    private var projectId: String? = null
    private var report: String = ""
    private var lastGroupId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bus = CommandBus.get(this)
        projectId = bus.repo.projects().firstOrNull()?.id
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

        addView(title("Blueprint Studio", 26f))
        addView(spacer(6))
        addView(body("Turn documents and instructions into a working SuperFlow setup.",
            14f, Palette.INK_FAINT))

        val project = bus.repo.project(projectId)
        if (project == null) {
            addView(newProjectCard())
            addView(projectList())
            addView(spacer(24))
            return@scroller
        }

        addView(projectHeader(project))
        addView(sourcesSection(project))
        addView(instructionsSection(project))
        addView(ledgerSection(project))
        addView(actionsSection(project))
        if (report.isNotBlank()) addView(reportCard())
        addView(projectList())
        addView(spacer(24))
    }

    /* ------------------------------------------------------------ projects */

    private fun newProjectCard(): View = card {
        addView(body("New mission", 16f, Palette.INK, bold = true))
        addView(spacer(6))
        addView(body("Give the mission a name, add sources, then compile.", 13f, Palette.INK_FAINT))
        addView(spacer(12))
        val name = field("My 2026 reset")
        addView(name)
        addView(primaryButton("Create mission") {
            val n = name.text.toString().trim().ifBlank { "Mission ${Dates.shortDay(Dates.today())}" }
            val p = BlueprintProject(name = n)
            bus.repo.saveProject(p)
            projectId = p.id
            render()
        })
    }

    private fun projectList(): View = column {
        val all = bus.repo.projects()
        if (all.size <= 1 && projectId != null) return@column
        addView(heading("MISSIONS"))
        for (p in all) {
            addView(card {
                addView(row {
                    addView(column {
                        layoutParams = lp(0, WRAP, 1f)
                        addView(body(p.name, 15f, Palette.INK, bold = true))
                        addView(body("${p.state.lowercase()} · v${p.version} · " +
                                "${bus.repo.sources(p.id).size} sources · " +
                                "${bus.repo.requirements(p.id).size} requirements",
                            12f, Palette.INK_FAINT))
                    })
                    addView(ghostButton("Open") { projectId = p.id; report = ""; render() })
                })
            })
        }
        addView(ghostButton("+ New mission") { projectId = null; report = ""; render() })
    }

    private fun projectHeader(p: BlueprintProject): View = softCard(Palette.ACCENT_SOFT) {
        addView(body("MISSION", 11f, Palette.ACCENT, bold = true))
        addView(spacer(4))
        addView(body(p.name, 19f, Palette.INK, bold = true))
        addView(spacer(4))
        addView(body("State: ${p.state.lowercase()} · version ${p.version}", 13f, Palette.INK_SOFT))
    }

    /* ------------------------------------------------------------- sources */

    private fun sourcesSection(p: BlueprintProject): View = column {
        addView(heading("SOURCES"))
        val sources = bus.repo.sources(p.id)
        if (sources.isEmpty()) {
            addView(card {
                addView(body("No sources yet.", 14f, Palette.INK_SOFT))
                addView(spacer(4))
                addView(body("Paste notes, a plan, a journal export, or any Markdown or text.",
                    13f, Palette.INK_FAINT))
            })
        }
        for (s in sources) {
            addView(card {
                addView(row {
                    addView(column {
                        layoutParams = lp(0, WRAP, 1f)
                        addView(body(s.name, 15f, Palette.INK, bold = true))
                        addView(body("${s.kind} · ${s.lineCount} lines · " +
                                "${s.content.length} characters", 12f, Palette.INK_FAINT))
                    })
                    addView(ghostButton("Remove", Palette.DANGER) {
                        bus.repo.deleteSource(s.id)
                        render()
                    })
                })
                if (s.instructions.isNotBlank()) {
                    addView(spacer(6))
                    addView(body("Note: ${s.instructions}", 12f, Palette.INK_SOFT))
                }
            })
        }
        addView(row {
            addView(ghostButton("+ Paste text") { addSource(p, "pasted") }
                .apply { layoutParams = lp(0, WRAP, 1f).apply { rightMargin = dp(8) } })
            addView(ghostButton("+ Import file") { importFile(p) }
                .apply { layoutParams = lp(0, WRAP, 1f) })
        })
        addView(spacer(6))
        addView(body(Compiler.ISOLATION_NOTE, 12f, Palette.INK_FAINT))
    }

    private fun addSource(p: BlueprintProject, kind: String) {
        val name = field("notes.md")
        val content = field("Paste your notes, plan or journal here", lines = 8)
        val note = field("Anything specific about this source (optional)")
        val body = column(0) {
            addView(label("Name")); addView(name)
            addView(label("Content")); addView(content)
            addView(label("Per-source instruction")); addView(note)
        }
        Dialogs.form(this, "Add source", body) {
            val text = content.text.toString()
            if (text.isBlank()) { toast("Paste something first"); return@form false }
            val s = BlueprintSource(
                projectId = p.id,
                name = name.text.toString().trim().ifBlank { "pasted-${bus.repo.sources(p.id).size + 1}" },
                kind = kind,
                content = text,
                instructions = note.text.toString().trim(),
                lineCount = text.lines().size
            )
            bus.repo.saveSource(s)
            render()
            true
        }
    }

    private fun importFile(p: BlueprintProject) {
        try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/*", "text/markdown", "application/pdf"))
            }
            startActivityForResult(intent, REQ_FILE)
        } catch (e: Exception) {
            toast("No file picker available. Use Paste text instead.")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_FILE || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        val p = bus.repo.project(projectId) ?: return
        try {
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "imported"
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return
            if (bytes.size > 2_000_000) {
                toast("That file is larger than 2 MB. Split it or paste the relevant part.")
                return
            }
            val isPdf = bytes.size > 4 && bytes[0] == '%'.code.toByte() && bytes[1] == 'P'.code.toByte()
            val text = if (isPdf) PdfText.extract(bytes) else String(bytes, Charsets.UTF_8)
            if (text.isBlank()) {
                toast("No readable text found. For scanned PDFs, paste the text instead.")
                return
            }
            bus.repo.saveSource(BlueprintSource(
                projectId = p.id, name = name,
                kind = if (isPdf) "pdf" else "text",
                content = text, lineCount = text.lines().size
            ))
            toast("Imported $name")
            render()
        } catch (e: Exception) {
            toast("Could not read that file: ${e.message}")
        }
    }

    /* -------------------------------------------------------- instructions */

    private fun instructionsSection(p: BlueprintProject): View = column {
        addView(heading("YOUR INSTRUCTIONS"))
        addView(card {
            addView(body("These outrank anything written inside a source document.",
                13f, Palette.INK_FAINT))
            addView(spacer(10))
            val text = field("Keep mornings light. No more than four habits. Protect my sleep routine.",
                p.instructions, lines = 4)
            addView(text)
            addView(ghostButton("Save instructions") {
                bus.repo.saveProject(p.copy(instructions = text.text.toString()))
                toast("Saved")
                render()
            })
        })
    }

    /* --------------------------------------------------------- the ledger */

    private fun ledgerSection(p: BlueprintProject): View = column {
        val reqs = bus.repo.requirements(p.id)
        addView(heading("REQUIREMENT LEDGER"))
        if (reqs.isEmpty()) {
            addView(card {
                addView(body("Not compiled yet.", 14f, Palette.INK_SOFT))
                addView(spacer(4))
                addView(body("Compiling reads every source line, extracts intentions, links each " +
                        "one to its citation, and flags conflicts.", 13f, Palette.INK_FAINT))
            })
            return@column
        }
        addView(card {
            addView(body("Coverage", 15f, Palette.INK, bold = true))
            addView(spacer(8))
            addView(body(Compiler.coverage(reqs), 13f, Palette.INK_SOFT))
        })
        for (r in reqs) {
            addView(card {
                addView(row {
                    addView(iconDot(statusColor(r.status), 8))
                    addView(body(r.text, 14f, Palette.INK).apply { layoutParams = lp(0, WRAP, 1f) })
                })
                addView(spacer(6))
                addView(body("${r.citation} · ${r.status.name.lowercase()}" +
                        if (r.assumption) " · assumption" else "", 11f, Palette.INK_FAINT))
                if (r.note.isNotBlank()) {
                    addView(spacer(4))
                    addView(body(r.note, 12f, Palette.WARM))
                }
                if (r.plannedCommand.isNotBlank()) {
                    addView(spacer(4))
                    val cmd = runCatching { JSONObject(r.plannedCommand).optString("command") }
                        .getOrDefault("")
                    addView(body("Plans to run: $cmd", 12f, Palette.ACCENT))
                }
                addView(spacer(8))
                addView(row {
                    if (r.status != RequirementStatus.REJECTED) {
                        addView(ghostButton("Reject") {
                            bus.repo.saveRequirement(r.copy(status = RequirementStatus.REJECTED))
                            render()
                        }.apply { layoutParams = lp(0, WRAP, 1f).apply { rightMargin = dp(8) } })
                    }
                    if (r.status == RequirementStatus.REJECTED ||
                        r.status == RequirementStatus.CONFLICTED) {
                        addView(ghostButton("Accept") {
                            bus.repo.saveRequirement(r.copy(status = RequirementStatus.ACCEPTED))
                            render()
                        }.apply { layoutParams = lp(0, WRAP, 1f) })
                    }
                })
            })
        }
    }

    private fun statusColor(s: RequirementStatus): Int = when (s) {
        RequirementStatus.VERIFIED, RequirementStatus.IMPLEMENTED -> Palette.ACCENT
        RequirementStatus.CONFLICTED, RequirementStatus.GAP -> Palette.WARM
        RequirementStatus.REJECTED -> Palette.DANGER
        else -> Palette.INK_FAINT
    }

    /* ------------------------------------------------------------- actions */

    private fun actionsSection(p: BlueprintProject): View = column {
        addView(heading("RUN"))
        addView(card {
            addView(primaryButton("Compile requirements") { compile(p) })
            addView(spacer(8))
            val reqs = bus.repo.requirements(p.id)
            val ready = reqs.count { it.status == RequirementStatus.ACCEPTED && it.plannedCommand.isNotBlank() }
            addView(body(
                if (reqs.isEmpty()) "Compile first to see what would be built."
                else "$ready requirements are ready to apply.",
                13f, Palette.INK_FAINT
            ))
            if (ready > 0) {
                addView(spacer(10))
                addView(primaryButton("Build the workspace ($ready)") { execute(p) })
                addView(spacer(6))
                addView(body(
                    if (prefsFullControl()) "Full Control is on, so this runs without further asks. " +
                            "A snapshot is taken first and every action is individually undoable."
                    else "Guided mode: you will be asked to confirm before anything is applied.",
                    12f, Palette.INK_FAINT
                ))
            }
            if (lastGroupId != null) {
                addView(spacer(10))
                addView(ghostButton("Undo this whole build", Palette.DANGER) {
                    val res = bus.undoGroup(lastGroupId!!)
                    toast(res.message)
                    lastGroupId = null
                    render()
                })
            }
            addView(spacer(10))
            addView(row {
                addView(ghostButton("Audit current setup") { audit(p) }
                    .apply { layoutParams = lp(0, WRAP, 1f).apply { rightMargin = dp(8) } })
                addView(ghostButton("Export design pack") { exportPack(p) }
                    .apply { layoutParams = lp(0, WRAP, 1f) })
            })
            addView(spacer(8))
            addView(ghostButton("Delete mission", Palette.DANGER) {
                Dialogs.confirm(this@BlueprintActivity, "Delete \"${p.name}\" and its sources?") {
                    bus.repo.deleteProject(p.id)
                    projectId = null
                    report = ""
                    render()
                }
            })
        })
    }

    private fun prefsFullControl(): Boolean =
        com.superflow.data.Prefs.get(this).fullControlActive()

    private fun compile(p: BlueprintProject) {
        val sources = bus.repo.sources(p.id)
        if (sources.isEmpty() && p.instructions.isBlank()) {
            toast("Add a source or some instructions first")
            return
        }
        bus.repo.clearRequirements(p.id)
        val reqs = Compiler.extractRequirements(p, sources)
        reqs.forEach { bus.repo.saveRequirement(it) }
        bus.repo.saveProject(p.copy(state = "COMPILED", version = p.version + 1))
        report = "Compiled ${reqs.size} requirements from ${sources.size} sources.\n\n" +
                Compiler.coverage(reqs) +
                "\n\nNothing has been applied yet. Review the ledger, then build."
        toast("Compiled ${reqs.size} requirements")
        render()
    }

    private fun execute(p: BlueprintProject) {
        val run = {
            Snapshots.save(this, bus)
            val group = newId()
            lastGroupId = group
            val reqs = bus.repo.requirements(p.id)
            var applied = 0
            val failures = ArrayList<String>()

            for (r in reqs) {
                if (r.status != RequirementStatus.ACCEPTED || r.plannedCommand.isBlank()) continue
                val obj = runCatching { JSONObject(r.plannedCommand) }.getOrNull() ?: continue
                val command = obj.optString("command")
                val args = obj.optJSONObject("args") ?: JSONObject()
                val res = bus.execute(command, args, Actor.AI, group)
                if (res.ok) {
                    applied++
                    bus.repo.saveRequirement(r.copy(status = RequirementStatus.IMPLEMENTED))
                } else {
                    failures.add("${r.text.take(50)}: ${res.message}")
                    bus.repo.saveRequirement(r.copy(status = RequirementStatus.GAP, note = res.message))
                }
            }

            // Verify against real app state, never against model text.
            val after = bus.repo.requirements(p.id)
            val (verified, gaps) = Compiler.verify(bus.repo, after)
            after.filter { it.status == RequirementStatus.IMPLEMENTED && it !in gaps }
                .forEach { bus.repo.saveRequirement(it.copy(status = RequirementStatus.VERIFIED)) }
            gaps.forEach {
                bus.repo.saveRequirement(it.copy(status = RequirementStatus.GAP,
                    note = "Planned but not found in the database afterwards"))
            }

            bus.repo.saveProject(p.copy(state = "VERIFIED"))

            val sb = StringBuilder()
            sb.append("BUILD REPORT\n\n")
            sb.append("Applied: $applied\n")
            sb.append("Verified against the database: $verified\n")
            sb.append("Gaps: ${gaps.size}\n")
            sb.append("Deferred (no safe automatic mapping): " +
                    "${after.count { it.status == RequirementStatus.DEFERRED }}\n")
            sb.append("Rejected: ${after.count { it.status == RequirementStatus.REJECTED }}\n")
            if (failures.isNotEmpty()) {
                sb.append("\nDid not complete:\n")
                failures.forEach { sb.append("· $it\n") }
            }
            if (gaps.isNotEmpty()) {
                sb.append("\nGaps to handle manually:\n")
                gaps.forEach { sb.append("· ${it.text.take(70)} (${it.citation})\n") }
            }
            sb.append("\nA snapshot was taken before this run, and every action above can be " +
                    "undone individually or as one group from Activity.")
            report = sb.toString()
            toast("Built $applied items, $verified verified")
            render()
        }

        if (prefsFullControl()) run()
        else Dialogs.confirm(this,
            "Apply the accepted requirements to your workspace?") { run() }
    }

    private fun audit(p: BlueprintProject) {
        val repo = bus.repo
        val sb = StringBuilder("CURRENT SETUP AUDIT\n\n")
        sb.append("Identities: ${repo.identities().size}\n")
        sb.append("Goals: ${repo.goals().size}\n")
        sb.append("Systems: ${repo.systems().size}\n")
        sb.append("Habits: ${repo.habits().size}\n")
        sb.append("Check-ins: ${repo.checkIns().size}\n\n")

        val noTiny = repo.habits().filter { it.tinyStart.isBlank() }
        val noCue = repo.habits().filter { it.cueTime.isBlank() && it.anchorText.isBlank() }
        val noReward = repo.habits().filter { it.reward.isBlank() }
        val orphan = repo.habits().filter { it.identityId == null }

        sb.append("Design gaps\n")
        sb.append("- Without a tiny start: ${noTiny.size}\n")
        noTiny.take(5).forEach { sb.append("    · ${it.title}\n") }
        sb.append("- Without a cue or anchor: ${noCue.size}\n")
        noCue.take(5).forEach { sb.append("    · ${it.title}\n") }
        sb.append("- Without an immediate reward: ${noReward.size}\n")
        sb.append("- Not linked to an identity: ${orphan.size}\n")

        if (repo.habits().size > 6) {
            sb.append("\nYou are running ${repo.habits().size} habits. Adding more than about " +
                    "three new behaviours at once usually reduces all of them.\n")
        }
        report = sb.toString()
        render()
    }

    private fun exportPack(p: BlueprintProject) {
        val repo = bus.repo
        val sb = StringBuilder()
        sb.append("# SuperFlow Design Pack — ${p.name}\n\n")
        sb.append("_Generated ${Dates.humanDay(Dates.today())}_\n\n")
        sb.append("## Identities\n")
        repo.identities().forEach { sb.append("- ${it.statement} (${it.lifeArea.label})\n") }
        sb.append("\n## Goals\n")
        repo.goals().forEach { sb.append("- ${it.title}${if (it.why.isNotBlank()) " — ${it.why}" else ""}\n") }
        sb.append("\n## Systems\n")
        repo.systems().forEach { sb.append("- ${it.title}\n") }
        sb.append("\n## Habits\n")
        repo.habits().forEach { h ->
            sb.append("### ${h.title}\n")
            sb.append("- Contract: ${h.contract()}\n")
            sb.append("- Ladder: tiny=${h.tinyStart} | minimum=${h.minimumVersion} | " +
                    "standard=${h.standardVersion} | stretch=${h.stretchVersion}\n")
            sb.append("- Schedule: ${com.superflow.domain.Capabilities.daysLabel(h.daysMask)}\n\n")
        }
        sb.append("\n## Requirement ledger\n")
        repo.requirements(p.id).forEach {
            sb.append("- [${it.status.name.lowercase()}] ${it.text} (${it.citation})\n")
        }
        report = sb.toString()
        render()
        try {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/markdown"
                putExtra(Intent.EXTRA_SUBJECT, "SuperFlow Design Pack")
                putExtra(Intent.EXTRA_TEXT, sb.toString())
            }, "Export design pack"))
        } catch (e: Exception) {
            // The report card already shows it on screen.
        }
    }

    private fun reportCard(): View = column {
        addView(heading("REPORT"))
        addView(card {
            addView(body(report, 13f, Palette.INK_SOFT))
            addView(spacer(10))
            addView(ghostButton("Dismiss") { report = ""; render() })
        })
    }

    companion object {
        private const val REQ_FILE = 4001
    }
}
