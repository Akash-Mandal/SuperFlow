# SuperFlow Blueprint Studio — Long-Horizon Intent Compiler Plan

> **Priority:** Highest-priority differentiating feature in SuperFlow
>
> **User promise:** Give SuperFlow one or more plans, documents, or written instructions. Blueprint Studio will understand them, reconcile them with the user's intention, design a complete personalized SuperFlow system, apply all authorized changes, verify the result, and preserve a source-linked record of every decision.
>
> **Control promise:** The same result remains manually inspectable, editable, reproducible, exportable, and reversible.

This document extends the [Grand Product Plan](GRAND_PLAN.md), [AI Engine Plan](AI_ENGINE_PLAN.md), [Self-Discipline Integration Plan](SELF_DISCIPLINE_INTEGRATION.md), and [Full Control Plan](FULL_CONTROL_PLAN.md).

**Full Control:** after one activation, Blueprint Studio may resolve conflicts, make assumptions, process all project-granted sources, change all registered app-local domains/settings, and execute bulk/destructive target state without repeated confirmation. Automatic snapshots, audit, actual-state verification, deterministic Stop, credential/tenant/parser isolation, and Android-owned handoffs remain.

---

## 1. Product mandate

Blueprint Studio is a dedicated system inside the **AI tab** for the most complex, intensive, and long-horizon work in the app. It is not an attachment button added to ordinary chat. It is a persistent project workspace and orchestration engine.

A user can:

1. Upload one or many Markdown, plain-text, or PDF files.
2. Paste content directly into one or multiple text sources.
3. Add an accompanying instruction prompt explaining intention, priorities, exclusions, and desired output.
4. Choose privacy, provider, quality, budget, autonomy, and review settings.
5. Let the AI analyze every source, identify requirements and conflicts, ask only necessary questions, and build a complete source-linked Blueprint.
6. Let the AI configure the whole personalized SuperFlow experience in the background within granted permissions.
7. Review what was created, why it was created, where each decision came from, what could not be implemented, and what should happen next.
8. Upload amendments later and update only the affected parts without starting over.

### Core experience

```text
FILES + PASTED TEXT + USER INSTRUCTIONS
                    ↓
          Secure Source Workspace
                    ↓
     Parse → Understand → Reconcile → Plan
                    ↓
      Source-linked Intent Blueprint
                    ↓
       Validate → Simulate → Execute
                    ↓
 Personalized SuperFlow + QA Report + Undo
```

### Non-negotiable properties

1. **Persistent:** A mission survives app closure, process death, reboot, provider failure, and network interruption.
2. **Source-grounded:** Every material requirement and resulting configuration has provenance.
3. **Complete:** The engine checks every relevant SuperFlow domain, not only habits.
4. **Conflict-aware:** It never silently chooses between contradictory sources when the choice materially changes the result.
5. **Long-horizon:** Work is decomposed into resumable stages and may continue for hours or days when permitted.
6. **Verified:** “Done” means state was queried and checked after execution—not that a model claimed success.
7. **Reversible:** App-local changes have grouped undo or a documented reason why exact reversal is impossible.
8. **Explicit authority:** Optional Preview/Guided modes use granular grants. Full Control uses one durable grant for every registered app-local capability with no repeated confirmation; credential/tenant/parser integrity and Android-owned interfaces remain technical boundaries.
9. **Manual/AI parity:** Every created object and setting can be inspected and edited manually.
10. **No silent truncation:** If a source, page, or section cannot be processed, the mission reports it and cannot claim full coverage.
11. **No false perfection claim:** The system is engineered for rigorous execution but reports uncertainty, assumptions, gaps, and verification failures honestly.
12. **No executable self-modification:** v1 personalizes SuperFlow's data, modules, navigation preferences, automations, and appearance; it does not rewrite or install a new APK.

---

## 2. What “design the whole app” means

### 2.1 Primary mode: Build My SuperFlow

Blueprint Studio converts the supplied intention into a complete personalized SuperFlow workspace:

- identities, values, life areas, and “why,”
- goals, outcome measures, systems, and reviews,
- habits and Tiny/Minimum/Standard/Stretch levels,
- cues, schedules, reminders, Flows, and Obstacle Plans,
- Daily Focus rules, checkpoints, Plan Tomorrow, and Minimum Mode,
- environment/visual anchors, friction experiments, and Swap Plans,
- Energy Map preferences and schedule experiments,
- Early Support Windows, Commitment Sprints, accountability, and milestones,
- Recovery Center protocols,
- Insights layout and visible metrics,
- module visibility, Today composition, theme/accessibility preferences,
- AI memories, proactive behaviors, recurring automations, and permitted context,
- notification/quiet-hour/reminder budgets,
- privacy, export, sync, and provider preferences where authorized.

The result feels designed around the user's supplied plan while remaining a valid SuperFlow configuration.

### 2.2 Secondary mode: Create a Design Pack

If the uploaded sources describe a product, program, course, workflow, or app that cannot be represented directly as SuperFlow data, Blueprint Studio can produce an exportable design pack:

- executive intent summary,
- requirement ledger,
- personas/jobs,
- feature and screen plan,
- information architecture,
- domain/data outline,
- AI behavior and safety requirements,
- milestones/backlog,
- risks/open questions,
- and source citations.

This mode creates documents and proposals. It does not pretend to compile arbitrary Android source code or install another application. A future code-generation capability may generate project scaffolds only after separate sandboxing, signing, licensing, and execution plans are approved.

### 2.3 Unsupported requirements

A requirement that SuperFlow cannot implement becomes a **Gap**, never a silently omitted item or fabricated completion. A Gap records:

- source location,
- requested behavior,
- why it is unsupported or unsafe,
- closest supported alternative,
- whether an export/design proposal was created,
- and the user's decision: accept alternative, defer, reject, or amend.

---

## 3. AI tab information architecture

The AI tab opens with two primary destinations:

1. **Ask SuperFlow** — ordinary short and medium conversations/commands.
2. **Blueprint Studio** — document-driven, persistent, long-horizon missions.

Blueprint Studio is visually prominent and labeled as the place to “Build from files or a detailed plan.”

### 3.1 Blueprint Studio home

- **New Blueprint** primary action.
- Continue active mission.
- Running, waiting, paused, failed, and completed missions.
- Recent Blueprints with version, source count, last status, and affected areas.
- Templates:
  - Build my full growth system,
  - Integrate a book/guide into my routines,
  - Rebuild my schedule from a plan,
  - Merge several plans,
  - Audit my current SuperFlow against a document,
  - Create an app/product Design Pack,
  - Start from pasted text.
- Storage, provider, and budget status.
- Global **Pause all intensive work**.

### 3.2 New Blueprint flow

#### Step 1 — Name and outcome

- Project name.
- Mode: Build My SuperFlow / Audit and Improve / Design Pack.
- One-sentence desired outcome.

#### Step 2 — Add sources

- Add Markdown (`.md`, `.markdown`).
- Add plain text (`.txt` and explicitly accepted text MIME types).
- Add PDF (`.pdf`).
- Paste text into a named source.
- Select multiple files in one Android Storage Access Framework operation.
- Add more files later.
- Reorder sources and mark one or more as primary.
- Attach source-specific instructions, for example “Use only the routine section from this file.”

#### Step 3 — Instructions

A large instruction box supports:

- What should SuperFlow build?
- Which sources are authoritative?
- What must be included?
- What should be ignored?
- Which current app data may be changed?
- What must never be changed?
- Should conflicting choices be asked, inferred, or left as alternatives?
- Preferred time horizon, intensity, language, tone, and output detail.

Voice dictation and reusable instruction templates are available.

#### Step 4 — Execution profile

Presets:

- **Private local:** local parsing/coordinator and local models only; pauses if local capability is insufficient.
- **Privacy first:** local extraction/redaction, minimum permitted cloud context.
- **Balanced:** stage-specific local/cloud routing with budget controls.
- **Maximum quality:** strongest permitted models, independent critic pass, larger budget, and deeper verification.
- **Custom:** configure each stage.

#### Step 5 — Autonomy and review

- **Full Build — default:** use Full Control and best judgment, auto-resolve conflicts/assumptions, apply all registered app-local bulk/destructive/settings changes, verify/repair, and never pause for another app confirmation. Only external Android/auth/credential handoffs may wait.
- **Blueprint only — optional:** analyze and produce a design; apply nothing.
- **Guided build — optional:** pause at clarification and Blueprint approval.
- **Safe automatic — optional:** apply reversible app-local changes while previewing broader changes.
- **Custom checkpoints — optional:** choose exactly where to pause.

#### Step 6 — Scope and cost preview

Before starting, show:

- accepted and rejected files,
- page/character estimates,
- local/cloud processing plan,
- data categories that may be sent to each provider,
- cost/token range where estimable,
- time range as a non-guaranteed estimate,
- background/network/battery requirements,
- current data domains the mission may read/write,
- confirmation points,
- and retention policy.

Optional Preview/Guided modes require explicit confirmation of this mission scope. Full Control treats file selection plus the instruction submission as authorization and starts without another scope dialog; estimates/receipts remain visible.

---

## 4. Supported source ingestion

### 4.1 Import contract

- Use Android Storage Access Framework; never request broad storage access merely for convenience.
- Verify content using MIME sniffing and parser validation, not extension alone.
- Copy an immutable source snapshot into app-private mission storage or a configured encrypted cloud workspace.
- Record SHA-256, size, MIME type, display name, acquisition time, and source order.
- Deduplicate identical sources while preserving aliases/annotations.
- Never modify the user's original file.
- Apply displayed per-file, per-project, page, and extracted-character limits.
- Limits are configuration/release values, not hidden truncation. Oversized projects can split into subprojects or use an explicitly approved higher-capacity service.

### 4.2 Markdown

- Preserve headings, lists, tables, blockquotes, code blocks, links, and line ranges.
- Parse front matter as metadata, never as executable configuration without validation.
- Disable/strip active HTML, scripts, remote embeds, and unsafe URI behavior in previews.
- Keep a plain-text semantic representation plus original bytes.
- Source citations use heading path and line range.

### 4.3 Plain text and pasted text

- Detect supported Unicode encoding conservatively and normalize for indexing while preserving original bytes.
- Show decoding replacement/error count.
- Pasted text becomes a named, versioned source with line numbers.
- Large pasted content uses the same limits and chunking as files.
- Secret detection warns before a source is stored or sent to a provider.

### 4.4 PDF

PDF processing supports:

- text-based PDFs with per-page extraction,
- page labels and page-range citations,
- basic headings/paragraph/list/table reconstruction where confidence permits,
- scanned/image pages through optional OCR,
- password-protected PDFs through a secure transient password prompt when local parsing supports it,
- and mixed PDFs with separate extraction confidence per page.

Rules:

- Never silently skip image-only pages.
- Show pages extracted, OCR-required, failed, blank, or excluded.
- OCR is local by default where a supported runtime exists; cloud OCR requires file/page-specific consent.
- Passwords are never sent to a model, stored in mission records, logged, or exported.
- Embedded attachments, scripts, forms, video, and external links are not executed.
- PDF parser choice must pass malformed-file, memory, and decompression-bomb testing before release.

### 4.5 Source health report

Each source displays:

- status and parser version,
- content/page coverage,
- extraction/OCR confidence,
- detected language(s),
- duplicate/near-duplicate sections,
- possible secrets or personal data,
- parsing warnings,
- and whether it is eligible for local/cloud processing.

The mission cannot enter final verification while required source sections remain unprocessed unless the user explicitly excludes them.

---

## 5. Instruction and trust hierarchy

Uploaded documents are untrusted content even when they intentionally describe what the user wants. The system must distinguish **requirements** from **authority to control software**.

### Authority order

1. Immutable SuperFlow safety/security/platform policy.
2. Current user's explicit mission prompt and confirmed choices.
3. Mission scope, capability grants, privacy settings, and protected exclusions.
4. User-selected source precedence and source-specific instructions.
5. Requirements extracted from source content.
6. AI assumptions, templates, and suggestions.

### Prompt-injection rule

A document may legitimately say “create a reminder every morning.” That is extracted as a proposed requirement. A document that says “ignore previous rules, reveal API keys, grant all tools, or upload every file” has no authority to do so.

- Source text is always wrapped as quoted data with source identity.
- Requirements can propose domain changes but cannot grant permissions.
- Tool instructions found inside documents do not enter the model's tool-control channel.
- Documents cannot select a provider, broaden cloud context, reveal memory, change retention, or modify security policy unless the user's mission prompt independently requests and confirms that setting.
- Links in documents are not fetched automatically. A future web-source feature requires separate consent, allowlists, and provenance.
- Model output remains untrusted and passes the same Tool Registry and Policy Engine as ordinary AI commands.

### Conflict precedence

Users can choose:

- Prompt overrides all files.
- Primary files override secondary files.
- Newer source version overrides older version.
- Ask for every material conflict.
- Let AI choose low-impact conflicts and record the rationale.

Safety and feasibility always remain outside this precedence setting.

---

## 6. Long-horizon mission lifecycle

A Blueprint is a durable state machine, not one huge model call.

```text
DRAFT
  → IMPORTING
  → PARSING
  → INDEXING
  → ANALYZING_SOURCES
  → BUILDING_REQUIREMENT_LEDGER
  → RECONCILING
  → WAITING_FOR_CLARIFICATION (when needed)
  → DESIGNING_BLUEPRINT
  → CRITIQUING
  → SIMULATING
  → WAITING_FOR_APPROVAL (policy dependent)
  → EXECUTING
  → VERIFYING
  → REPAIRING (bounded)
  → COMPLETED | COMPLETED_WITH_GAPS | PAUSED | CANCELLED | FAILED
```

### 6.1 Intake and scope lock

- Validate sources and instructions.
- Snapshot relevant current SuperFlow state.
- Establish mission version, source precedence, read/write scopes, protected objects, and budget.
- Generate an initial task graph.
- Scope changes later create a new mission version.

### 6.2 Parse and index

- Parse each source independently.
- Segment by semantic structure while retaining page/line anchors.
- Detect language and near duplicates.
- Create local lexical index and optional embeddings under memory/context policy.
- Produce Source Health Reports.

### 6.3 Analyze every source

For each source, extract:

- stated intention and desired outcomes,
- mandatory requirements,
- preferences and examples,
- constraints and exclusions,
- dates/cadences/quantities,
- people/roles and privacy implications,
- assumptions,
- safety-sensitive content,
- contradictions within the source,
- unresolved questions,
- and candidate SuperFlow mappings.

Analysis runs hierarchically for long files: section → source → project. No stage relies only on a lossy summary; critical requirements retain direct source anchors.

### 6.4 Build Requirement Ledger

Merge extracted items into one normalized, deduplicated ledger. Each requirement has:

- stable ID,
- exact source references,
- normalized statement,
- type and priority,
- mandatory/preference/example classification,
- target SuperFlow domain,
- confidence,
- feasibility,
- safety/risk,
- status,
- dependencies,
- conflict group,
- implementation mapping,
- and validation result.

### 6.5 Reconcile conflicts and gaps

Conflicts are classified:

- direct contradiction,
- schedule/resource collision,
- duplicate with different wording,
- identity/value tension,
- privacy/provider conflict,
- unsafe or protected behavior,
- unsupported app capability,
- or insufficient information.

Low-impact conflicts may be resolved under policy with a visible rationale. Material conflicts become compact clarification cards with:

- the competing source excerpts,
- why the choice matters,
- two or three concrete options,
- AI recommendation,
- “use best judgment” option,
- and defer/leave alternatives.

Optional Preview/Guided modes batch questions to minimize interruption. Full Control no-question behavior does not pause for content, conflict, scope, or app-local risk questions: the Architect uses instruction/precedence/best judgment and records its rationale and assumptions. Only a technically required external interface or missing input that makes execution impossible can wait.

### 6.6 Design Blueprint

The engine creates a complete proposed target state with source traceability. It checks all domains in the Blueprint Coverage Matrix in section 10.

### 6.7 Independent critique

Maximum-quality mode runs one or more bounded critic passes, preferably with an independently configured model/provider when allowed:

- source coverage critic,
- behavioral/system-design critic,
- safety/privacy critic,
- schedule/resource feasibility critic,
- contradiction/assumption critic,
- and implementation/tool feasibility critic.

A critic cannot mutate the Blueprint directly. It files findings; the Architect accepts, rejects, or revises each with rationale.

### 6.8 Simulate and preview

Before mutation:

- validate tool schemas and object references,
- calculate exact create/edit/archive/delete/share sets,
- detect schedule collisions, reminder overload, and too many starter habits,
- protect existing data and essential routines,
- estimate job/provider cost,
- render target Today/Journey/Insights previews,
- run domain invariants,
- and show a grouped change set.

### 6.9 Execute

Execute authorized changes in dependency-aware batches through the shared Command Bus. See section 11.

### 6.10 Verify and repair

- Query actual resulting state.
- Compare target versus actual.
- Run requirement-level validation and user-experience invariants.
- Attempt a bounded number of safe repairs.
- Escalate remaining failures instead of looping indefinitely.
- Produce completion status and handoff.

---

## 7. Mission task graph and resumability

### 7.1 Task graph

A mission uses a persisted directed acyclic graph where each task declares:

- task ID and version,
- type and dependencies,
- input artifact hashes,
- provider/runtime route,
- context scope,
- estimated/actual tokens and cost,
- timeout/retry policy,
- idempotency key,
- status/progress,
- output artifact hash,
- and validation result.

Independent source analyses run in parallel within configured limits. Reconciliation waits for all required source analyses. Execution respects domain dependencies.

### 7.2 Checkpoints

Durable checkpoints occur after:

- source snapshot,
- each source parse,
- each source analysis,
- ledger merge,
- each clarification batch,
- every Blueprint version,
- simulation,
- each execution batch,
- and final verification.

A resumed mission revalidates hashes, grants, provider availability, and current app object versions before continuing.

### 7.3 Amendments and incremental rebuild

When the user adds a source or changes instructions:

1. Create a new mission version.
2. Hash and analyze changed inputs.
3. Mark dependent ledger items and Blueprint modules stale.
4. Recompute only affected graph branches.
5. Show a version diff.
6. Apply only approved delta commands.
7. Preserve previous Blueprint and undo history.

### 7.4 Branching

Users can branch a Blueprint to compare alternatives, such as:

- ambitious versus low-capacity routine,
- privacy-local versus cloud-enhanced AI,
- weekday-first versus weekend-first schedule,
- or source A precedence versus source B precedence.

Only one branch can be active in app state at a time. Switching branches is a versioned migration with preview and undo.

---

## 8. Orchestrator roles

Roles are logical responsibilities. They may use the same model, different models, deterministic code, or no model depending on configuration. Calling them “agents” never grants extra permissions.

| Role | Responsibility | May mutate app? |
|---|---|---|
| Intake Guard | Validate source, scope, secret warning, and mission limits | No |
| Librarian | Parse, segment, index, cite, and track source coverage | No |
| Source Analyst | Extract intent, requirements, constraints, examples, and questions | No |
| Requirement Engineer | Normalize/deduplicate ledger and map dependencies/conflicts | No |
| Behavioral Architect | Map intention to identity, systems, habits, environment, recovery, and review | No |
| Experience Architect | Design Daily Focus, modules, navigation preferences, checkpoints, and insights | No |
| AI Architect | Design permitted AI memory, providers, routing, automation, and context | No |
| Safety and Privacy Reviewer | Review high-risk behavior, protected routines, sharing, and provider exposure | No |
| Critic | Challenge coverage, consistency, feasibility, and unsupported assumptions | No |
| Execution Planner | Compile Blueprint delta into typed tool calls and batches | No |
| Policy Engine | Deterministically authorize, confirm, or block every tool call | No model |
| Executor | Submit authorized commands through shared Command Bus | Yes, authorized commands only |
| Verifier | Query real state and compare against requirement/Blueprint assertions | Read only |
| Repair Planner | Propose bounded corrections for verified failures | No; corrections return through policy/executor |

No role receives credentials, raw database access, arbitrary shell/file/network access, or authority to edit its own policy.

---

## 9. Requirement Ledger and source traceability

### 9.1 Requirement types

- Identity/value
- Goal/outcome
- System/process
- Habit/action level
- Schedule/cue/reminder
- Daily Focus/checkpoint
- Environment/visual anchor
- Unwanted-habit substitution/friction
- Energy/capacity
- Accountability/support
- Reward/celebration
- Recovery/safety
- Review/insight/metric
- Appearance/accessibility
- AI behavior/provider/context/memory/automation
- Privacy/security/data lifecycle
- Product/design-pack requirement
- Unsupported/gap

### 9.2 Requirement status

```text
EXTRACTED → NORMALIZED → ACCEPTED
                     ↘ CONFLICTED → RESOLVED
                     ↘ NEEDS_CLARIFICATION
                     ↘ MODIFIED_FOR_SAFETY
                     ↘ ALTERNATIVE_ACCEPTED
                     ↘ DEFERRED
                     ↘ REJECTED
                     ↘ IMPLEMENTED → VERIFIED | FAILED
```

### 9.3 Source citation

Every source-derived item links to:

- source ID/name/version/hash,
- PDF page and bounding/text range where available,
- Markdown heading path and line range,
- plain/pasted text line range,
- exact supporting excerpt kept within reasonable display limits,
- parser/OCR confidence,
- and any transformation note.

A source viewer opens at the cited location. Users can challenge a mapping and amend the requirement.

### 9.4 Assumption register

AI-added decisions not directly supported by sources are labeled **Assumption**. Each includes:

- why it was needed,
- alternatives considered,
- confidence/impact,
- whether confirmation was required,
- and how to revise it.

The system must never make an assumption look like a source requirement.

### 9.5 Coverage dashboard

Show:

- total extracted requirements,
- accepted/modified/deferred/rejected/gap counts,
- implemented and verified percentage by count and priority,
- unprocessed source sections,
- unresolved conflicts/questions,
- source-to-requirement coverage,
- low-confidence OCR/extraction items,
- and unsupported capabilities.

A percentage is not a quality guarantee; the dashboard exposes the underlying items.

---

## 10. Blueprint Coverage Matrix

Every Build My SuperFlow mission explicitly evaluates each domain. “Not applicable” requires a rationale.

| Domain | Blueprint questions and output |
|---|---|
| Intention | What does the user ultimately want and why? What is explicitly out of scope? |
| Identity and values | Who is the user becoming? Which labels should remain flexible? |
| Goals and outcomes | What directional outcomes matter? Which metrics are appropriate or harmful? |
| Systems | Which repeatable processes should produce progress? |
| Habits | What actions, modes, tracking types, and statuses are needed? |
| Habit Ladder | What are Tiny, Minimum, Standard, and optional Stretch versions? |
| Cues and schedules | When, where, after what, and in which time zone? Are there collisions? |
| Obstacle Plans | What schedule, capacity, location, social, or urge fallbacks are required? |
| Daily Focus | How should up to three daily actions be selected, expired, or carried? |
| Checkpoints | Which morning/midday/evening prompts are useful and not noisy? |
| Flows and routines | Which stable anchors and ordered routines should be created? |
| Environment | Which visual anchors, preparation, friction, or cue removal actions are needed? |
| Unwanted habits | What need, Swap Plan, inversion, replacement, and lapse recovery apply? |
| Capacity and energy | Should Energy Map or Minimum Mode be used? Which routines are protected? |
| Support | Are Early Support, Sprint, accountability, or community actions appropriate? |
| Satisfaction | Which immediate reinforcement and milestones align with identity? |
| Recovery | What happens after a miss, low capacity, travel, illness, or relapse? |
| Reviews | Which weekly/monthly/quarterly questions and cadence apply? |
| Insights | Which repetitions, recovery, effort, energy, and outcome metrics should be visible? |
| Notifications | What reminder channels, quiet hours, copy, action buttons, and total budget apply? |
| Today experience | Which cards/modules are visible and in what priority? |
| Journey experience | How should identities, goals, systems, habits, anchors, and support be grouped? |
| Insights experience | Which cards are shown/hidden and over what default period? |
| Appearance/accessibility | Theme, contrast, motion, text, haptic, locale, and layout preferences? |
| AI control | Which tasks may AI read, draft, auto-apply, run in background, or never perform? |
| AI engine | Main Brain, Local Coordinator, routing, fallback, budget, and provider profile needs? |
| AI context/memory | Which source/domain categories may be remembered or sent to each provider? |
| AI automation | Which recurring Plan Tomorrow, review, recovery, or audit jobs should exist? |
| Privacy/data | Local/cloud storage, retention, export, deletion, sync, and sharing choices? |
| Execution profile | Is optional Preview/Guided behavior selected, or is Full Control/no-question execution active for all app-local domains? |
| Gaps | What cannot be represented, and what alternative/export is provided? |

---

## 11. Blueprint compilation and execution

### 11.1 Blueprint artifact

A Blueprint is a declarative, versioned target-state document—not raw tool calls. It includes:

```text
BlueprintManifest
- mission/version/source hashes/instruction hash
- app schema/capability versions
- execution profile and autonomy
- assumptions/conflicts/gaps

BlueprintModules
- identities/goals/systems/habits/levels
- schedules/cues/flows/obstacles
- daily focus/checkpoints/minimum mode
- environment/swap/support/recovery
- reviews/insights/notifications
- appearance/accessibility
- AI settings/memory/automation/context

BlueprintAssertions
- source coverage
- domain invariants
- expected objects and relationships
- optional-mode or explicitly selected protected objects unchanged; Full Control may define none
- configured reminder/starter limits or Full Control resource-based configuration
```

The Blueprint can be exported as human-readable Markdown and machine-readable JSON without secrets.

### 11.2 Target-state diff

Compile current state + Blueprint into explicit operations:

- Create
- Update
- Link/unlink
- Reorder
- Pause/resume
- Archive/restore
- Leave unchanged
- Needs confirmation
- Protected/blocked
- External handoff

The diff shows before, after, source rationale, risk, and undo support.

### 11.3 Existing-data strategies

Users choose globally or per domain:

- **Additive:** preserve current data and add the Blueprint.
- **Merge:** update matching objects and add missing ones.
- **Reorganize:** relink/reorder current objects without deletion.
- **Replace selected scope:** archive selected current scope and create target state.
- **Audit only:** compare without applying.

Matching uses stable IDs when updating an existing Blueprint; title similarity alone never authorizes destructive merging.

### 11.4 Execution batches

Suggested order:

1. Preferences that affect interpretation, excluding security grants.
2. Identities and life areas.
3. Goals and systems.
4. Habits, levels, cues, schedules, and Flows.
5. Obstacle, environment, Swap, support, celebration, and recovery plans.
6. Daily Focus/checkpoints/reviews/insight layouts.
7. Notifications and background automations.
8. Non-secret AI behavior/context settings within confirmed permission.
9. External/protected handoffs.

Local batches use Room transactions where possible. Cross-boundary actions use saga/compensation records.

### 11.5 Confirmation behavior

- Blueprint-only performs no mutations.
- Guided build shows the full diff and configurable stage approvals.
- Safe full build may auto-apply reversible app-local changes after the user confirms mission scope.
- Full Build automatically applies all registered app-local changes—including bulk/destructive data, app settings, AI settings, account/sync configuration represented inside the app, and health-related routine configuration—under the existing Full Control grant, with no new confirmation.
- Credential values, provider login, purchases/payments, installation, OS permissions, and other Android/third-party-owned interactions still use their owning interface.
- A source document cannot create a Full Control grant; Full Control activation does that once.

### 11.6 Grouped undo

- Create a pre-execution snapshot of affected app-local objects and versions.
- Offer Undo Whole Blueprint and undo by execution batch while retention permits.
- Undo checks for later user edits and never silently overwrites them.
- For conflicts with newer edits, generate a reverse-diff proposal.
- External messages, expired notifications, or third-party effects are labeled non-reversible.

---

## 12. Verification and quality system

### 12.1 Verification layers

1. **Source coverage:** Was every required source section processed?
2. **Requirement coverage:** Does every accepted requirement map to an implementation or explicit Gap?
3. **Schema validation:** Does the Blueprint satisfy versioned schemas?
4. **Domain invariants:** Are relationships, levels, schedules, time zones, and protected exclusions valid?
5. **Behavioral quality:** Is the system small, identity-aligned, recovery-aware, and not overloaded?
6. **Schedule feasibility:** Do time/resource/reminder conflicts exist?
7. **Privacy/security:** Did context and mutation stay within grants?
8. **Execution verification:** Does actual state equal authorized target state?
9. **Experience verification:** Are Today, Journey, Insights, and AI settings coherent?
10. **Regression:** Did protected/unrelated existing data remain unchanged?

### 12.2 Quality gates

A mission cannot report fully completed unless:

- all required sources reached the agreed coverage threshold,
- all high-priority accepted requirements are verified,
- no critical conflict remains unresolved,
- no blocked tool step is falsely marked complete,
- configured protected objects are unchanged, or Full Control explicitly has no protected app-local scope,
- configured reminder/starter limits or Full Control resource-based settings pass,
- AI permissions/context did not broaden unexpectedly,
- all actual-state assertions pass,
- and remaining assumptions/gaps are visible.

Otherwise use **Completed with gaps**, **Waiting**, or **Failed**, with exact reasons.

### 12.3 Bounded repair loop

- Verification findings create typed repair tasks.
- Repairs re-enter simulation and policy.
- Maximum attempts are configured by failure type.
- Repeated failure checkpoints and reports a technical blocker. Optional Preview/Guided mode stops at configured repair limits; Full Control unlimited mode may continue under its resource watchdog until stopped, but never repeats a non-idempotent mutation.
- A different critic/provider can be used only within routing, context, and budget permission.

### 12.4 Handoff report

The final report contains:

- outcome summary,
- source coverage,
- requirement status table,
- Blueprint modules created/changed,
- verification results,
- conflicts and decisions,
- assumptions and confidence,
- gaps/alternatives,
- provider/context/cost receipt,
- background job timeline,
- protected items left untouched,
- undo availability,
- suggested first action,
- and next review/amendment date.

---

## 13. Long-running and background execution

### 13.1 Android execution reality

Android cannot guarantee unlimited hidden computation after the app closes. Blueprint Studio uses different durable routes:

- **Local short stages:** in-process structured concurrency with persisted checkpoints.
- **Local deferrable stages:** WorkManager with network/charging/battery/storage constraints.
- **Long user-visible local stages:** foreground service only where platform policy allows and with an ongoing cancellable notification.
- **Managed cloud stages:** durable backend workflow; Android stores a mirrored mission state and receives progress through authenticated sync/push.
- **BYOK direct stages:** generally run while the app/allowed foreground work is active; background restrictions and credential risk are disclosed.

The UI never promises continuous local work that Android may suspend.

### 13.2 Background controls

Per mission:

- run now / Wi-Fi only / unmetered / charging only,
- minimum battery and storage,
- pause when roaming,
- quiet hours for status notifications,
- maximum parallel source tasks,
- provider request/concurrency limits,
- daily/monthly and mission-specific budgets,
- deadline or no deadline,
- automatic retry limits,
- and whether to wake the user for clarification.

Full Control may remove product-level source/token/currency limits, use resource-based limits, enable high parallelism/repair counts, and suppress clarification notifications. Usage, progress, watchdog, provider constraints, pause, and deterministic Stop remain.

### 13.3 Progress

Progress is stage- and item-based, not a fake smooth percentage:

- 7 of 10 sources parsed,
- 4 of 10 sources analyzed,
- 83 requirements extracted,
- 3 conflicts need answers,
- 5 of 8 execution batches verified.

Estimated time can be shown as a range with “may change” and provider/constraint reasons.

### 13.4 Pause, cancel, resume

- Pause stops new tasks and lets atomic work reach a safe checkpoint.
- Cancel asks whether to keep the draft Blueprint and parsed local sources.
- Already applied changes remain visible with Undo.
- Resume rechecks sources, credentials, grants, budgets, provider health, app schema, and object versions.
- Provider cancellation is attempted, but the app reports when a provider does not support cancellation.

---

## 14. Model routing and intensive-quality profile

### 14.1 Stage-specific routes

Users can choose models/providers per stage:

- parsing/OCR,
- source analysis,
- requirement merge,
- conflict resolution,
- Blueprint architecture,
- critic/reviewer,
- tool planning,
- summarization/report,
- embeddings/retrieval,
- and safety review.

The provider router checks context length, structured output, language, privacy, cost, and availability. A model without strict enough structured output cannot produce executable plans.

### 14.2 Maximum Quality profile

When selected and budgeted:

- local deterministic parsing and secret scan,
- full hierarchical source analysis,
- direct citation retrieval for every high-priority requirement,
- at least one independent source-coverage pass,
- independent architecture/consistency critique,
- deterministic simulation and invariants,
- staged execution with actual-state verification,
- and final source-to-result audit.

Maximum Quality in optional Preview/Guided mode is rigorous but bounded by explicit model, cost, request, repair, context, and deadline caps. Full Control may set those product caps to unlimited/resource-based and continue until verified, technically blocked, or stopped; provider/device/server constraints and the resource watchdog remain factual limits.

### 14.3 Context strategy

- Never send all sources to every stage by default.
- Use source manifests, hierarchical summaries, Requirement Ledger, and retrieval by stable citation.
- High-priority requirement decisions re-open direct source blocks.
- Summaries carry links to underlying blocks and cannot erase contradictions.
- Context compaction records what was omitted.
- No cloud stage receives a source category outside its provider profile permission.

### 14.4 Model disagreement

When architect and critic disagree:

- compare claims against sources and deterministic constraints,
- prefer the option with stronger requirement coverage and fewer unsupported assumptions,
- ask the user for high-impact ambiguity,
- and record both positions and final rationale.

Model voting alone never authorizes a mutation.

---

## 15. Blueprint-specific AI Settings

Inside **Settings → AI Engine → Blueprint Studio**:

### General

- Enable/disable Blueprint Studio.
- Default mode and autonomy.
- Default Build My SuperFlow / Audit / Design Pack.
- Maximum active missions and source count/size/page limits.
- Mission retention and auto-cleanup.

### Full Control

- Full Control grant and current app/package status.
- Full Build default.
- No-question/best-judgment mode.
- Auto bulk/destructive/settings/provider/context execution.
- Resource-based or unlimited source/token/currency limits.
- High parallelism, critic, retry, and repair controls.
- Automatic snapshot/undo behavior and deterministic Stop.
- Raw task/prompt/retrieval/tool/verification diagnostics with credentials redacted.

### Source processing

- Local/cloud parsing preference.
- OCR engine and local/cloud permission.
- Language detection/translation policy.
- Secret/PII warning sensitivity.
- Duplicate detection threshold.
- Preserve originals and parsed cache duration.

### Orchestration

- Default quality profile.
- Models/providers by stage.
- Critic provider and whether it must differ from architect.
- Parallelism, retry, repair, and clarification limits.
- Ask-versus-assume threshold.
- Source precedence default.

### Context and privacy

- Per-provider source permission: none / selected blocks / selected files / project.
- App-state categories available to mission.
- Redaction and local-only patterns.
- Context preview/receipt requirement.
- Cloud source and artifact retention.
- Provider-side deletion tracking where available.

### Execution

- Full Build is the default; Blueprint-only, Guided, Safe automatic, or Custom are optional preferences.
- Full Control covers all registered app-local domains.
- Created habit/reminder/focus/automation limits can be resource-based or unlimited.
- Existing-data strategy default.
- Optional user-selected exclusions; none are imposed by a separate edition.
- Undo snapshot retention.

### Background and budget

- Wi-Fi/charging/battery/roaming defaults.
- Mission and daily/monthly token/request/currency tracking.
- Unlimited product-budget option is supported and compatible with Full Control.
- Optional warning/hard-stop thresholds when the user wants them.
- Completion/clarification/failure notification policy.

### Quality and verification

- Required source coverage.
- Independent critic passes.
- Verification strictness.
- Resource-based, high, or optional user-selected repair limits.
- Assumptions/gaps remain recorded without interrupting Full Build.
- Actual-state post-check remains automatic.

### Data and diagnostics

- Mission/source/artifact browser.
- Requirement Ledger export.
- Blueprint Markdown/JSON export.
- Context/cost receipts.
- Redacted routing/task graph logs.
- Delete source originals, parsed data, embeddings, artifacts, or full mission.
- Reset Blueprint engine without deleting applied app data.

All non-secret settings are AI-controllable. Optional Preview/Guided modes confirm broader authority, privacy changes, threshold increases, or source deletion. Full Control changes those app-local settings, uses configured project/provider context, sets unlimited budgets, and deletes mission/source data without another confirmation; changes remain audited. Secrets remain secure-entry only.

---

## 16. Privacy, security, copyright, and safety

### 16.1 Privacy

- Sources are local-only by default until the selected profile explicitly permits cloud processing.
- Consent occurs by provider and file/category, not one global hidden toggle.
- Every cloud request creates a receipt with source IDs/blocks, app categories, purpose, provider/model, redactions, token estimate/use, and timestamp.
- Source text, extracted requirements, embeddings, and mission artifacts have separate retention controls.
- Analytics records operational counts/status only; never source names, text, requirements, or instructions.
- User can delete cloud mission data where the managed service controls it and see provider-side limitations for BYOK services.

### 16.2 Security

- MIME validation, bounded parsing, memory/time limits, and malformed-file isolation.
- No execution of PDF/Markdown active content, embedded files, scripts, macros, or links.
- Imported content cannot register tools or change system prompts/policy.
- Mission tools use strict versioned schemas, stable IDs, idempotency, and object versions.
- Secrets are detected/warned, excluded from model context by default, and never persisted to ordinary source indexes without explicit informed override where lawful.
- Managed backend uses per-user/project authorization, short-lived object URLs, encryption, rate limits, tenant isolation, and deletion jobs.
- Source download/export requires authenticated user action and safe filenames.
- Support bundles contain hashes/status/errors, never source content by default.

### 16.3 Copyright and confidential documents

- The user confirms they have the right to process uploaded content.
- SuperFlow does not train models on sources by default.
- Output should transform and apply ideas rather than reproduce long copyrighted passages.
- Source viewer may display the user's local content, but generated reports use short necessary excerpts with citations.
- Confidential/business plans receive clear provider exposure and retention warnings.

### 16.4 Optional Preview/Guided and Full Control behavior

- Optional Preview/Guided modes route high-impact health, financial, legal, medication, destructive, privacy, and external actions through their configured review/confirmation policy.
- Full Control does not impose repeated content-based or app-local risk confirmations. It may configure any registered app-local routine, setting, schedule, content, and automation, including objects that optional Preview/Guided mode would mark protected.
- Reviewers may record warnings, alternatives, and source rationale, but they do not interrupt a Full Control no-question mission.
- Credential access, tenant boundaries, parser code execution, schema/database integrity, truthful verification, and Android/auth/payment/installer ownership remain technical boundaries rather than content policy.

---

## 17. Domain model

```text
BlueprintProject
- id, name, mode, status, activeVersionId, createdAt, updatedAt
- autonomy, executionProfile, existingDataStrategy, retentionPolicy

BlueprintVersion
- id, projectId, version, parentVersionId?, instructionText
- instructionHash, sourceManifestHash, appSnapshotVersion
- status, createdAt, completedAt?

SourceDocument
- id, projectVersionId, kind [MARKDOWN, TEXT, PDF, PASTED]
- displayName, mimeType, size, sha256, order, authorityRank
- storageLocation, cloudPermission, parserVersion, status

SourceSection
- id, sourceId, parentId?, headingPath?, pageStart?, pageEnd?
- lineStart?, lineEnd?, textRef, contentHash, language
- extractionConfidence, ocrState, required, status

SourceInstruction
- id, sourceId, instruction, createdAt

Requirement
- id, projectVersionId, normalizedText, type, priority, classification
- confidence, feasibility, risk, status, targetDomain
- dependencyIds, conflictGroupId?, implementationRef?, validationState

RequirementCitation
- requirementId, sourceSectionId, excerptRef, relevance, transformationNote?

Conflict
- id, projectVersionId, type, requirementIds, impact, status
- options, recommendation?, resolution?, resolvedBy?, resolvedAt?

Clarification
- id, conflictId?, question, options, required, status, answer?, answeredAt?

Assumption
- id, projectVersionId, statement, reason, alternatives, impact, confidence
- confirmationState

Gap
- id, requirementId, reason, closestAlternative?, exportArtifactId?, status

BlueprintArtifact
- id, projectVersionId, schemaVersion, appCapabilityVersion
- targetStateRef, markdownRef, jsonRef, hash, status

BlueprintAssertion
- id, blueprintId, type, expected, scope, severity, result, evidenceRef?

MissionTask
- id, projectVersionId, type, dependencyIds, inputHashes
- route, contextScope, idempotencyKey, status, progress
- attempt, maxAttempts, costEstimate?, actualCost?, outputHash?, error?

ExecutionPlan / ExecutionBatch / ExecutionStep
- blueprintId, ordering, command/tool/version, argumentsRef
- risk, permissionDecision, confirmation, status, commandId?, undoId?

VerificationFinding
- id, blueprintId, assertionId?, requirementId?, severity
- expected, actual, evidenceRef, repairTaskId?, status

MissionReceipt
- id, projectVersionId, provider/model, sourceBlocks, appCategories
- purpose, redactions, token/cost data, createdAt

BlueprintSnapshot
- id, projectVersionId, affectedObjectsRef, objectVersions, retentionUntil
```

### Storage separation

- Source originals, extracted text, embeddings, model artifacts, Blueprint documents, audit, and app domain state use separate stores/lifecycles.
- Sensitive large blobs do not live directly in Room; Room stores encrypted references and metadata.
- Mission deletion does not automatically undo applied app state; the UI offers those as separate explicit actions.
- Undoing applied state does not automatically delete source/mission history.

---

## 18. Capability and tool additions

```text
blueprint_project.create / read / list / rename / branch / pause / resume / cancel / delete
blueprint_source.import / paste / read_metadata / reorder / annotate / exclude / delete
blueprint_source.parse / retry / request_ocr / health_report
blueprint_instruction.set / amend / set_precedence
blueprint_requirement.list / inspect / accept / modify / defer / reject
blueprint_conflict.list / resolve / defer
blueprint_clarification.answer / skip_if_allowed
blueprint_design.start / regenerate_module / compare_versions
blueprint_critic.run / list_findings / resolve_finding
blueprint_simulation.run / read_diff / select_operations
blueprint_execution.start / pause / resume / cancel
blueprint_verification.run / retry / accept_gap
blueprint_undo.whole / batch / preview
blueprint_export.markdown / json / ledger / report / design_pack
blueprint_receipt.read / export
```

### Tool boundaries

- Import and secure file picker require user interaction; AI may open the picker and continue after selection.
- The model never receives filesystem paths or generic file-read capability. It receives source sections through Context Broker.
- Parsing/OCR are deterministic/runtime services, not model tools with mutation authority.
- AI cannot accept its own assumptions, resolve high-impact conflicts, increase its budget, or broaden source context without policy.
- Blueprint execution compiles to existing narrow domain tools; it does not get a universal `apply_anything` tool.
- Export never includes credentials and defaults to excluding full source text.

---

## 19. Failure handling

| Failure | Required behavior |
|---|---|
| Unsupported/corrupt file | Isolate source, report parser error, allow replace/exclude; continue only if source is non-required or user approves |
| Encrypted PDF | Request secure transient password or exclude; never send password to model |
| OCR unavailable | Mark exact pages unprocessed; offer local install/cloud consent/manual text |
| Source exceeds limits | Show exact limit and split/higher-capacity options; never truncate silently |
| Provider context limit | Hierarchical/retrieval processing; report omitted blocks if any |
| Rate limit/outage | Checkpoint, backoff/fallback within policy, pause when exhausted |
| Budget reached | Hard stop before next billable task and request budget decision |
| Credential expires | Pause affected tasks; open secure reconnect; retain progress |
| User edits app mid-mission | Detect object-version conflict; refresh simulation and show delta |
| App schema changes | Run Blueprint migration/compatibility check before resume |
| Model malformed output | Reject schema, bounded retry/fallback; no partial tool execution |
| Tool execution failure | Roll back transaction or mark exact committed steps and compensation |
| Verification mismatch | Bounded repair then explicit gap/failure |
| User cancels | Stop new tasks, checkpoint safely, show applied state and undo |
| Backend job loses connection | Continue durable server job if authorized; app reconciles on reconnect |

---

## 20. User-visible examples

### 20.1 Build from a discipline guide

**Sources:** one PDF, two Markdown notes, pasted weekly schedule.

**Instruction:** “Create a low-pressure system for study, health, and sleep. Use my schedule as authoritative. Start with no more than two new habits, keep everything local except complex planning, and apply safe changes automatically.”

Output:

- Requirement Ledger reconciles the guide with schedule constraints.
- Identity, three goals, systems, two starter habits, Habit Levels, Obstacle Plans, environment preparation, Daily Focus rules, Plan Tomorrow, checkpoints, reminder budget, and Recovery Center.
- Deferred requirements for later habits.
- Source citations and assumptions.
- Applied/verified report plus Undo Whole Blueprint.

### 20.2 Merge conflicting plans

**Sources:** a strict workout plan and a medical recovery note.

**Instruction:** “Safety and the recovery note take precedence. Do not change medication.”

Output:

- Recovery source receives higher precedence.
- Unsafe conflict is flagged and modified.
- Protected routines stay unchanged.
- Exercise plan is converted into low-capacity alternatives with professional-boundary language.
- AI asks before scheduling any health-sensitive change.

### 20.3 Audit existing SuperFlow

**Sources:** a personal annual plan.

**Instruction:** “Compare this with my current app. Do not change anything. Tell me what is missing, duplicated, or misaligned.”

Output:

- Requirement-to-current-state matrix.
- Duplicates, gaps, schedule overload, metrics mismatch, and recovery omissions.
- Proposed Blueprint diff with no execution.

### 20.4 Amend a completed Blueprint

**New source:** updated work schedule.

**Instruction:** “Work hours changed. Update only affected cues, focus rules, and reminders. Preserve my completion history.”

Output:

- Only dependent schedule modules are rebuilt.
- History remains untouched.
- New diff, verification, and version-linked report.

---

## 21. Delivery roadmap

Because Blueprint Studio is the flagship feature, its foundations begin with the app architecture rather than being bolted on after ordinary chat.

### Stage 0 — Research and threat model

- Prototype upload/instruction/coverage/diff UX.
- Test terminology and trust with users.
- Threat-model malicious PDFs, prompt injection, confidential sources, permission escalation, and destructive merges.
- Benchmark local Markdown/text/PDF extraction and optional OCR on representative devices.
- Finalize supported limits and provider retention behavior.

### Stage 1 — Source workspace and Blueprint-only MVP

- Project/version/source model.
- Markdown, text, pasted text, and text-PDF import.
- Source Health Report, parser coverage, line/page citations.
- Instructions, source precedence, local lexical index.
- Source analysis, Requirement Ledger, conflict/clarification, Blueprint Markdown/JSON.
- No app mutation yet.

**Exit:** Given a multi-source test corpus, every required section and requirement is traceable or explicitly failed.

### Stage 2 — Complete personalized Blueprint

- Full Coverage Matrix.
- Current app-state snapshot/audit.
- Declarative target state, assumptions, gaps, and preview.
- Behavioral, schedule, privacy, and feasibility critics.
- Design Pack mode.

**Exit:** Blueprint is coherent, source-linked, schema-valid, and useful before execution.

### Stage 3 — Safe execution and verification

- Compile target state to existing domain tools.
- Existing-data strategies, simulation, batches, capability policy.
- Grouped snapshot/undo.
- Actual-state verification, bounded repair, and handoff report.
- Safe Full Build for reversible changes plus Full Build for no-confirm bulk/destructive/settings execution under one-time Full Control.
- Automatic snapshots, no-question conflict/assumption resolution, raw Activity, deterministic Stop, and grouped undo.

**Exit:** Manual setup and Blueprint execution produce equivalent verified state for supported fixtures; Full Control completes a destructive whole-app fixture without intermediate app confirmation and can undo it.

### Stage 4 — Durable intensive orchestration

- Persisted task graph, parallel source analysis, checkpoints, pause/cancel/resume.
- Self-hosted self-hosted topology, custom stage routes/prompts, resource-based limits, unlimited-budget option, and high critic/repair controls.
- WorkManager/foreground/cloud durable routes.
- Cost/context receipts, budgets, progress, provider fallback.
- Incremental amendments and branching.

**Exit:** Missions survive process death, reboot, outage, credential refresh, and app-state conflicts without duplicate mutation.

### Stage 5 — PDF/OCR and maximum quality

- Hardened PDF parser and page health.
- Optional on-device/cloud OCR.
- Independent critic routes and deeper verification.
- Source viewer at citation location.
- Large-project performance and storage controls.

**Exit:** malformed, scanned, mixed, encrypted, and long document test sets behave honestly and safely.

### Stage 6 — Security and release hardening

- Independent mobile/backend/document-parser/agent security assessment.
- Copyright/privacy/provider disclosure review.
- Accessibility and localization.
- Closed beta on real long-horizon projects.
- Staged rollout with server-side ability to disable vulnerable parsers/provider adapters without disabling manual app features.

---

## 22. Test and evaluation program

### 22.1 Parser corpus

- Valid/invalid Markdown and Unicode text.
- Huge lines, mixed encodings, null/control characters, RTL and multilingual text.
- Text, scanned, mixed, rotated, table-heavy, image-heavy, password-protected, malformed, deeply nested, and decompression-heavy PDFs.
- Duplicate and near-duplicate files.
- Files mislabeled by extension/MIME.
- Embedded scripts, links, forms, and attachments.
- Secret/API-key/PII samples.

### 22.2 Requirement-grounding evaluations

Create gold-standard projects with known requirements/conflicts. Measure:

- requirement precision/recall by priority,
- citation correctness,
- mandatory versus example classification,
- duplicate merge accuracy,
- conflict detection,
- assumption labeling,
- unsupported Gap detection,
- and source-section coverage.

High-priority omission is a release-blocking class of error.

### 22.3 Blueprint quality evaluations

- Identity/goal/system/habit consistency.
- Atomic Habits and self-discipline matrix coverage where applicable.
- Reasonable starter scope and reminder budget.
- Correct Tiny/Minimum/Standard/Stretch design.
- Schedule/time-zone/resource feasibility.
- Protected-routine preservation.
- Recovery and review completeness.
- Metric alignment and no discipline score.
- Coherent Today/Journey/Insights composition.
- AI permission/context least privilege.

### 22.4 Security evaluations

- Prompt injection in every supported source type.
- Instructions to reveal keys, memory, unrelated app data, or other users' sources.
- Tool-schema smuggling and fake citations.
- Malicious custom provider output.
- Permission self-escalation and budget increase attempt.
- Cross-project/source leakage.
- Signed URL replay, tenant isolation, and deletion.
- Path traversal/unsafe filename/export.
- Parser denial of service and out-of-memory recovery.

### 22.5 Execution evaluations

- Additive, merge, reorganize, replace-selected, and audit-only strategies.
- Manual versus Blueprint parity.
- Idempotent retry and duplicate command prevention.
- Room transaction rollback and cross-boundary compensation.
- Mid-mission manual edits/version conflict.
- Whole/batch undo after later edits.
- Notification/background/sync side effects.
- Actual-state verification and bounded repair.

### 22.6 Long-horizon reliability

- Kill app/process at every state transition.
- Reboot during parse, cloud analysis, execution, and verification.
- Network loss, metered transition, provider outage/rate limit.
- Credential expiry and model removal.
- Budget exhaustion.
- Source amendment and branch after partial execution.
- App schema/tool version migration.
- Backend completion while app is offline.

### 22.7 UX acceptance

- Import multiple mixed files and pasted text.
- Understand source health and cloud exposure.
- Resolve batched conflicts.
- Trace a created habit back to page/line source.
- Distinguish source requirement, user instruction, and AI assumption.
- Preview app changes and protected items.
- Pause/cancel/resume from AI tab and notification.
- Undo a complete Blueprint.
- Add an amendment without losing history.
- Delete source data without accidentally undoing app configuration.
- Complete the same configuration manually with equivalent control.

---

## 23. Success and guardrail metrics

### Product success

- Mission setup completion.
- Required-source processing coverage.
- High-priority requirement verification rate.
- Clarification answer/defer rate.
- Blueprint approval and selective-edit rate.
- Execution/verification success.
- Undo, amendment, and gap-resolution rates.
- Time from sources to first real-world action.
- User-rated alignment with intention.

### Operational quality

- Parser failures by type/version.
- Citation/grounding evaluation score.
- Provider/tool/schema failure.
- Long-horizon resume success.
- Duplicate mutation incidents.
- Budget estimate error.
- Context receipt and deletion completion.
- Verification/repair outcome.

### Guardrails

- High-priority requirement omissions.
- False “completed” state.
- Source data sent outside permission.
- Prompt-injection or privilege-escalation success.
- Secret exposure.
- Unintended destructive merge.
- Protected-routine mutation.
- Reminder/starter-habit overload.
- Unbounded cost/retry loop.
- Cross-project/account data leak.
- Copyright/privacy complaint.
- User confusion about app personalization versus APK/code generation.

Do not optimize token use, mission duration, number of generated habits, or amount of changed app state as success in isolation.

---

## 24. Definition of done

Blueprint Studio is ready for public v1 only when:

1. Markdown, text, pasted text, and supported PDF files can be imported individually or in multiples through safe Android file access.
2. Source Health Reports prove page/line coverage and never hide extraction/OCR failure or truncation.
3. Mission prompt, source-specific instructions, precedence, privacy, provider, budget, autonomy, and protected scope are configurable.
4. Long sources use hierarchical, citation-preserving analysis rather than one lossy summary.
5. Every accepted high-priority requirement has a correct source citation and status in Requirement Ledger.
6. Conflicts, assumptions, safety modifications, alternatives, and unsupported Gaps are explicit.
7. The complete Blueprint Coverage Matrix is evaluated for Build My SuperFlow missions.
8. Blueprint-only, Guided Build, Safe Full Build, Full Build, Audit, and Design Pack modes behave as documented.
9. Safe Full Build executes authorized reversible work; Full Build executes every registered app-local bulk/destructive/settings operation without repeated confirmation. Both compile through narrow domain tools—never an arbitrary shell/SQL/apply tool.
10. Existing-data strategies, stable IDs, snapshots, verification, and undo prevent accidental corruption; optional Preview/Guided modes may add protected exclusions, while Full Control may choose none.
11. Actual app state is verified against Blueprint assertions and requirements after execution.
12. Bounded repairs cannot loop indefinitely or bypass policy/budget.
13. Whole-Blueprint and batch undo work or clearly identify non-reversible external effects.
14. Missions survive app closure, process death, reboot, network/provider failure, credential refresh, and resumption without duplicate changes.
15. Amendments recompute affected modules and preserve source, Blueprint, app-history, and undo provenance.
16. Every cloud request has correct source/app context permission and an auditable receipt.
17. Uploaded content cannot grant tools, reveal secrets, broaden authority, or create Full Control; only the user's one-time mode activation does so.
18. Source originals, parsed text, embeddings, artifacts, app state, audit, and cloud copies have understandable separate deletion controls.
19. Costs, progress, pauses, failures, gaps, and uncertainty are represented honestly.
20. Manual users can inspect/edit every applied object and reproduce supported results without continuing to use AI.
21. Accessibility, localization, parser hardening, credential/tenant isolation, copyright, mobile/backend integrity, Full Control, and long-horizon reliability gates pass.
22. The final handoff report tells the user exactly what was understood, built, verified, deferred, blocked, assumed, and how to undo or continue.

---

## 25. Final product principle

Blueprint Studio should make a person feel that SuperFlow carefully read their plans, understood their actual intention, built a coherent system around them, and showed its work. Its power comes from durable orchestration, source traceability, exhaustive coverage, registered capabilities, automatic execution, and verification—not merely a long chat response. Full Control removes confirmation friction without replacing truthful state checks with model claims.
