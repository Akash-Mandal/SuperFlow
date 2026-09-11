@file:Suppress("LargeClass", "TooManyFunctions", "HardcodedText")

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
import com.superflow.ai.Snapshots
import com.superflow.blueprint.PdfText
import com.superflow.blueprint.Planner
import com.superflow.core.time.SfTime
import com.superflow.data.Repository
import com.superflow.data.model.BlueprintProject
import com.superflow.data.model.BlueprintSource
import com.superflow.data.model.ProgressivePlan
import com.superflow.data.model.RequirementStatus
import com.superflow.data.model.UserIntent
import com.superflow.data.model.newId
import com.superflow.domain.Actor
import com.superflow.domain.CommandBus
import com.superflow.ui.common.ScrollActivity
import com.superflow.ui.common.snack
import com.superflow.ui.sheets.TextInputSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Blueprint Studio 2.0: one dream, five steps, plain words.
 *
 * 1 Dream → 2 Materials → 3 Rules → 4 Your plan → 5 Build & grow.
 * Steps unlock in order; the header always says where you are. The old
 * wall-of-sections is gone, but everything it could do still works: same
 * tables, same AI tools, same undo.
 */
class BlueprintActivity : ScrollActivity() {

    private val bus by lazy { CommandBus.get(this) }
    private val repo by lazy { Repository.get(this) }

    private var projectId: String? = null
    private var dream: UserIntent = UserIntent()
    private var plan: ProgressivePlan? = null
    private var busy = false
    private var lastGroupId: String? = null

    private val pickFile = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) importFile(uri) }

    companion object {
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

    /* ------------------------------------------------------------------ shell */

    override fun buildContent() {
        if (projectId == null) {
            projectId = intent?.getStringExtra(EXTRA_PROJECT)
                ?.takeIf { repo.project(it) != null }
        }
        if (projectId == null) projectId = repo.projects().firstOrNull()?.id
        val project = repo.project(projectId)

        if (project == null) {
            content.addView(textCard("Turn a big dream into a working setup",
                "Say what you want, add your notes and files, set your rules — " +
                    "SuperFlow designs a phased plan, builds the first phase now, " +
                    "and schedules the rest. Everything stays editable and undoable."))
            content.addView(primary("Start a new blueprint") { newProject() })
            projectList(except = null)
            return
        }

        val step = stepOf(project)
        content.addView(textCard(project.name, "Step ${step + 1} of 5 · ${stepName(step)}"))
        content.addView(stepDots(step))
        if (busy) {
            content.addView(textCard("Working…", "Building your plan. One moment."))
            return
        }

        when (step) {
            0 -> dreamStep(project)
            1 -> materialsStep(project)
            2 -> rulesStep(project)
            3 -> planStep(project)
            else -> buildStep(project)
        }

        content.addView(navRow(project, step))
        projectList(except = project.id)
    }

    private fun stepOf(p: BlueprintProject): Int = when (p.state) {
        "DREAM" -> 1
        "MATERIALS" -> 2
        "RULES" -> 3
        "PLAN", "COMPILED", "VERIFIED" -> 4
        else -> 0
    }

    private fun stepName(step: Int): String = when (step) {
        0 -> "Dream"
        1 -> "Materials"
        2 -> "Rules"
        3 -> "Your plan"
        else -> "Build & grow"
    }

    private fun stepDots(step: Int): View {
        val dots = (0..4).joinToString("  ") { if (it <= step) "●" else "○" }
        return textCard(dots, "Dream → Materials → Rules → Your plan → Build & grow")
    }

    private fun goTo(p: BlueprintProject, step: Int) {
        val state = when (step) {
            0 -> "DRAFT"
            1 -> "DREAM"
            2 -> "MATERIALS"
            3 -> "RULES"
            else -> "PLAN"
        }
        repo.saveProject(p.copy(state = state))
        rebuild()
    }

    private fun navRow(p: BlueprintProject, step: Int): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        if (step > 0) {
            row.addView(MaterialButton(this, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = "Back"
                layoutParams = LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also { it.marginEnd = dpi(8) }
                setOnClickListener { goTo(p, step - 1) }
            })
        }
        if (step < 4 && canContinue(p, step)) {
            row.addView(MaterialButton(this).apply {
                text = "Continue"
                layoutParams = LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener { goTo(p, step + 1) }
            })
        }
        if (row.childCount == 0) return View(this).apply { visibility = View.GONE }
        return row
    }

    private fun canContinue(p: BlueprintProject, step: Int): Boolean = when (step) {
        0 -> dream.goal.isNotBlank()
        1 -> repo.sources(p.id).isNotEmpty()
        2 -> true
        3 -> plan != null
        else -> false
    }

    /* ------------------------------------------------------------- 1 dream */

    private fun dreamStep(p: BlueprintProject) {
        content.addView(textCard("What do you want?",
            "One sentence. \"Run a 5K by October\" beats a paragraph — " +
                "the details come from your materials next."))
        content.addView(outlined("Describe your dream (${dream.goal.ifBlank { "not set" }.take(60)})") {
            TextInputSheet.show(supportFragmentManager, "Your dream",
                "e.g. Run a 5K by October", lines = 2) { text ->
                if (text.isNotBlank()) {
                    dream = dream.copy(goal = text.trim())
                    plan = null
                    rebuild()
                }
            }
        })
        content.addView(outlined("Daily minutes: ${dream.dailyTimeMinutes} (tap to change)") {
            TextInputSheet.show(supportFragmentManager, "Minutes per day", "30") { text ->
                text.toIntOrNull()?.let {
                    dream = dream.copy(dailyTimeMinutes = it.coerceIn(5, 240))
                    plan = null
                    rebuild()
                }
            }
        })
        content.addView(outlined("Duration: ${dream.durationWeeks} weeks (tap to change)") {
            TextInputSheet.show(supportFragmentManager, "Weeks", "8") { text ->
                text.toIntOrNull()?.let {
                    dream = dream.copy(durationWeeks = it.coerceIn(4, 52))
                    plan = null
                    rebuild()
                }
            }
        })
        if (dream.goal.isBlank()) {
            content.addView(textCard("Tip", "Set your dream above — Continue unlocks."))
        }
    }

    /* -------------------------------------------------------- 2 materials */

    private fun materialsStep(p: BlueprintProject) {
        content.addView(textCard("What should I read?",
            "Notes, plans, journal exports — text, Markdown or PDF. " +
                "I pull out the themes; your rules (next step) outrank all of it."))
        val sources = repo.sources(p.id)
        if (sources.isEmpty()) {
            content.addView(textCard("Nothing here yet", "Add at least one material to continue."))
        }
        sources.forEach { s ->
            val card = layoutInflater.inflate(R.layout.item_text_card, content, false)
            card.findViewById<TextView>(R.id.text_title).text = s.name
            card.findViewById<TextView>(R.id.text_body).text =
                "${s.kind} · ${s.lineCount} lines"
            val holder = card.findViewById<TextView>(R.id.text_title).parent as? LinearLayout
                ?: card as LinearLayout
            holder.addView(MaterialButton(this, null,
                androidx.appcompat.R.attr.borderlessButtonStyle).apply {
                text = "Remove"
                setOnClickListener {
                    repo.deleteSource(s.id)
                    plan = null
                    rebuild()
                }
            })
            content.addView(card)
        }
        content.addView(primary("Add pasted notes") { pasteSource(p) })
        content.addView(outlined("Import a file") {
            pickFile.launch(arrayOf("text/*", "application/pdf"))
        })
    }

    private fun pasteSource(p: BlueprintProject) {
        TextInputSheet.show(supportFragmentManager, "Paste notes",
            "Paste your notes, plan or journal", lines = 8) { text ->
            if (text.isBlank()) return@show
            repo.saveSource(BlueprintSource(
                projectId = p.id,
                name = "pasted-${repo.sources(p.id).size + 1}.md",
                kind = "pasted", content = text, lineCount = text.lines().size
            ))
            plan = null
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
                "OK" -> { plan = null; rebuild() }
                "TOOBIG" -> findViewById<View>(R.id.root)
                    .snack("Larger than 2 MB. Split it or paste the relevant part.")
                "EMPTY" -> findViewById<View>(R.id.root)
                    .snack("No readable text. For scanned PDFs, paste the text instead.")
                else -> findViewById<View>(R.id.root).snack("Could not read that file")
            }
        }
    }

    /* ------------------------------------------------------------ 3 rules */

    private fun rulesStep(p: BlueprintProject) {
        content.addView(textCard("Your rules outrank everything",
            "What must be built, what to ignore, what may never change. " +
                "If a document disagrees with these, these win."))
        if (p.instructions.isNotBlank()) {
            content.addView(textCard("Current rules", p.instructions.take(500)))
        }
        content.addView(primary("Set my rules") {
            TextInputSheet.show(supportFragmentManager, "Rules",
                "One per line: build this, ignore that, never touch…",
                lines = 5, value = p.instructions) { text ->
                repo.saveProject(p.copy(instructions = text.trim()))
                plan = null
                rebuild()
            }
        })
        content.addView(textCard("Safety note",
            "Instructions hidden inside your files (like \"ignore previous rules\") " +
                "are always ignored. Only rules you type here count."))
    }

    /* ------------------------------------------------------------ 4 plan */

    private fun ensurePlan(p: BlueprintProject): com.superflow.data.model.ProgressivePlan {
        plan?.let { return it }
        val intent = dream.copy(
            goal = dream.goal.ifBlank { p.instructions.ifBlank { p.name } },
        )
        val themes = Planner.readThemes(repo.sources(p.id), intent)
        return Planner.makePlan(themes, intent).also { plan = it }
    }

    private fun planStep(p: BlueprintProject) {
        content.addView(textCard("Your plan, in phases",
            "Phase 1 starts now and stays tiny. Later phases wait their turn — " +
                "nothing lands on you all at once."))
        if (plan == null) {
            content.addView(primary("Make my plan") {
                busy = true
                rebuild()
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { ensurePlan(p) }
                    busy = false
                    rebuild()
                }
            })
            return
        }
        val pl = plan!!
        content.addView(textCard(Planner.describe(pl),
            "From ${repo.sources(p.id).size} material(s)" +
                (if (p.instructions.isNotBlank()) " + your rules" else "")))
        pl.phases.forEachIndexed { idx, phase ->
            val habits = if (phase.newHabits.isEmpty()) "Rest and consolidate."
            else phase.newHabits.joinToString("\n") {
                "· ${it.title} — start: ${it.tinyStart.ifBlank { "one step" }} (~${it.estimatedMinutes} min)"
            }
            content.addView(textCard(
                "Phase ${idx + 1} · ${phase.label} (weeks ${phase.weekStart}–${phase.weekEnd})",
                habits))
        }
        content.addView(outlined("Remake it") {
            plan = null
            rebuild()
        })
    }

    /* ------------------------------------------------------- 5 build&grow */

    private fun buildStep(p: BlueprintProject) {
        val pl = runCatching { ensurePlan(p) }.getOrNull()
        val first = pl?.phases?.firstOrNull()
        val progress = Planner.progressReport(repo, p.id)
        content.addView(textCard("Progress", progress))
        if (first != null && first.newHabits.isNotEmpty()) {
            content.addView(textCard("Phase 1 · ${first.label}",
                first.newHabits.joinToString("\n") { "· ${it.title}" }))
            content.addView(primary("Build phase 1 (${first.newHabits.size} habits)") {
                buildPhase(p, pl!!, 0)
            })
        }
        val later = pl?.phases?.drop(1).orEmpty()
        if (later.isNotEmpty()) {
            content.addView(section("COMING LATER"))
            later.forEachIndexed { idx, phase ->
                content.addView(textCard(
                    "Phase ${idx + 2} · ${phase.label} (week ${phase.weekStart}+)",
                    if (phase.newHabits.isEmpty()) "Rest and consolidate."
                    else phase.newHabits.joinToString("\n") { "· ${it.title}" }))
            }
            content.addView(textCard("Automatic",
                "Later phases are scheduled and arrive on their own — " +
                    "or say \"reinforce now\" in Studio any time."))
        }
        if (lastGroupId != null) {
            content.addView(outlined("Undo the whole build") {
                lastGroupId?.let { gid ->
                    lifecycleScope.launch {
                        val r = withContext(Dispatchers.IO) { bus.undoGroup(gid) }
                        findViewById<View>(R.id.root).snack(r.message)
                        lastGroupId = null
                        rebuild()
                    }
                }
            })
        }
        content.addView(outlined("Share as text") { exportPack(p) })
        content.addView(MaterialButton(this, null,
            androidx.appcompat.R.attr.borderlessButtonStyle).apply {
            text = "Delete this blueprint"
            setOnClickListener {
                MaterialAlertDialogBuilder(this@BlueprintActivity)
                    .setTitle("Delete \"${p.name}\"?")
                    .setMessage("Your habits stay. Only the blueprint and its plan go.")
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.delete) { _, _ ->
                        repo.deleteProject(p.id)
                        projectId = null
                        plan = null
                        rebuild()
                    }
                    .show()
            }
        })
    }

    private fun buildPhase(p: BlueprintProject, pl: com.superflow.data.model.ProgressivePlan, index: Int) {
        val run = {
            busy = true
            rebuild()
            lifecycleScope.launch {
                val summary = withContext(Dispatchers.IO) { doBuild(p, pl, index) }
                busy = false
                findViewById<View>(R.id.root).snack(summary)
                rebuild()
            }
            Unit
        }
        if (com.superflow.data.Prefs.get(this).fullControlActive()) run()
        else MaterialAlertDialogBuilder(this)
            .setTitle("Build phase 1 into your setup?")
            .setMessage("Creates ${pl.phases.firstOrNull()?.newHabits?.size ?: 0} habits. " +
                "One snapshot, one undo for everything.")
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton("Build") { _, _ -> run() }
            .show()
    }

    private fun doBuild(
        p: BlueprintProject,
        pl: com.superflow.data.model.ProgressivePlan,
        index: Int,
    ): String {
        Snapshots.save(this, bus)
        repo.clearRequirements(p.id)
        val reqs = Planner.requirementsFor(pl.phases[index], p.id, index)
        reqs.onEach { repo.saveRequirement(it) }
        // Later phases wait their turn as auto plans.
        val db = com.superflow.data.db.SuperFlowDatabase.get(this).db
        pl.phases.drop(index + 1).forEachIndexed { idx, ph ->
            for (req in Planner.requirementsFor(ph, p.id, index + 1 + idx)) {
                try {
                    db.execSQL(
                        "INSERT OR REPLACE INTO blueprint_auto_plan VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                        arrayOf<Any?>(
                            newId(), p.id, index + 1 + idx, req.plannedCommand,
                            "WEEK:${ph.weekStart}", ph.focusArea, "ADD", null,
                            "PENDING", System.currentTimeMillis(), null,
                        ),
                    )
                } catch (_: Exception) { }
            }
        }
        val group = newId()
        lastGroupId = group
        var applied = 0
        val failed = ArrayList<String>()
        for (r in reqs) {
            if (r.plannedCommand.isBlank()) continue
            val obj = runCatching { JSONObject(r.plannedCommand) }.getOrNull() ?: continue
            val res = bus.execute(
                obj.optString("command"), obj.optJSONObject("args") ?: JSONObject(),
                Actor.AI, group,
            )
            if (res.ok) {
                applied++
                repo.saveRequirement(r.copy(status = RequirementStatus.IMPLEMENTED))
            } else {
                failed.add(r.text.take(60))
                repo.saveRequirement(r.copy(status = RequirementStatus.GAP, note = res.message))
            }
        }
        // Verify against the database, never against claims.
        var verified = 0
        for (r in repo.requirements(p.id)) {
            if (r.status != RequirementStatus.IMPLEMENTED) continue
            val title = runCatching {
                JSONObject(r.plannedCommand).optJSONObject("args")?.optString("title")
            }.getOrNull().orEmpty()
            if (title.isNotBlank() && repo.habits().any { it.title.equals(title, ignoreCase = true) }) {
                repo.saveRequirement(r.copy(status = RequirementStatus.VERIFIED))
                verified++
            } else {
                repo.saveRequirement(r.copy(status = RequirementStatus.GAP,
                    note = "Planned but not found afterwards"))
            }
        }
        repo.saveProject(p.copy(state = "PLAN"))
        return "Built $applied, verified $verified" +
            (if (failed.isNotEmpty()) " — ${failed.size} need a look" else "") +
            ". Later phases scheduled."
    }

    private fun exportPack(p: BlueprintProject) {
        val md = buildString {
            append("# ${p.name}\n\n")
            append("_${SfTime.humanDay(repo.clock.today())}_\n\n")
            append("## Dream\n${dream.goal.ifBlank { p.name }}\n\n")
            if (p.instructions.isNotBlank()) append("## Rules\n${p.instructions}\n\n")
            append("## Habits\n")
            repo.habits().forEach { h ->
                append("- ${h.title} (tiny: ${h.tinyStart.ifBlank { "—" }})\n")
            }
            append("\n## Progress\n${Planner.progressReport(repo, p.id)}\n")
        }
        runCatching {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/markdown"
                putExtra(Intent.EXTRA_SUBJECT, p.name)
                putExtra(Intent.EXTRA_TEXT, md)
            }, "Share blueprint"))
        }
    }

    /* ------------------------------------------------------------ projects */

    private fun projectList(except: String?) {
        val all = repo.projects().filter { it.id != except }
        if (all.isEmpty()) return
        content.addView(section("YOUR BLUEPRINTS"))
        all.forEach { p ->
            val card = layoutInflater.inflate(R.layout.item_text_card, content, false)
            card.findViewById<TextView>(R.id.text_title).text = p.name
            card.findViewById<TextView>(R.id.text_body).text = Planner.progressReport(repo, p.id)
            (card as com.google.android.material.card.MaterialCardView).setOnClickListener {
                projectId = p.id
                plan = null
                rebuild()
            }
            content.addView(card)
        }
    }

    private fun newProject() {
        TextInputSheet.show(supportFragmentManager, "New blueprint", "My 2026 reset") { name ->
            val p = BlueprintProject(
                name = name.trim().ifBlank {
                    "Blueprint ${SfTime.shortDay(repo.clock.today())}" }
            )
            repo.saveProject(p)
            projectId = p.id
            dream = UserIntent()
            plan = null
            rebuild()
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
