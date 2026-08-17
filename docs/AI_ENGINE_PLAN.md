# SuperFlow AI Engine and Universal Control Plan

> **Mandate:** Anything a user can accomplish manually in SuperFlow must also be expressible to the AI in natural language and executed through the same application logic. The manual app must remain complete and fully usable when AI is disabled, offline, unconfigured, or unavailable.
>
> **Relationship to the product plan:** This document is the detailed AI architecture companion to the [SuperFlow Grand Plan](GRAND_PLAN.md).

---

## 1. Non-negotiable product contract

SuperFlow has two equal control surfaces over one product:

```text
Manual screens, forms, buttons ─┐
                               ├─→ shared domain commands → local source of truth
AI text/voice conversation ─────┘
```

The AI is not a separate assistant that merely gives advice. It is an optional, permissioned control plane for the entire app. A user should be able to say:

- “Help me become a consistent reader. Set up the smallest useful routine after dinner.”
- “Move my weekday exercise habits to 7:30 AM and turn off the evening reminders.”
- “I am travelling for ten days. Put everything in minimum mode except medication.”
- “Why do I keep missing study time? Review the last month and improve the system.”
- “Create a morning flow from my existing water, stretch, and journal habits.”
- “Mark the walk as tiny, undo the meditation check-in, and add a note.”
- “Choose tomorrow's top three actions from my active systems and prepare the smallest fallback for each.”
- “My energy is low. Put study in Minimum Mode, but never change my protected medication routine.”
- “Create a ten-opportunity support sprint for reading, then review it without claiming the habit is automatic.”
- “Switch the main brain to my local server when I am on Wi-Fi, otherwise use the managed provider.”
- “Disable cloud AI, erase its memory, and use only the on-device coordinator.”

The AI may carry out the resulting operations in the background according to the user's autonomy policy. Manual users must be able to perform the same operations through screens and must be able to inspect, edit, undo, pause, or stop AI-created work.

### Hard invariants

1. **One domain, two interfaces:** Manual UI and AI tools invoke the same use cases and validation rules.
2. **No AI-only product feature:** Core behavior never requires a model, provider account, or network.
3. **No manual-only domain operation:** Each meaningful application operation has a typed AI capability unless Android or security requires direct user interaction.
4. **User-defined autonomy:** The user decides which capabilities can run automatically and which require confirmation.
5. **Visible agency:** Every mutation has an origin, timestamp, explanation, and undo path where technically possible.
6. **Least privilege:** Read, write, destructive, external-sharing, security, and financial/health actions have separate permissions.
7. **Model output is untrusted:** A model proposes tool calls; deterministic code validates, authorizes, and executes them.
8. **The model cannot grant itself permission:** Prompt text, retrieved notes, or provider output cannot change policy.
9. **Secrets are never model context:** API keys and tokens are entered in secure UI, stored through Android Keystore-backed facilities, and represented to AI only by opaque aliases.
10. **Graceful absence:** If all AI is off, every manual workflow and all existing data remain available.
11. **Transactional safety:** Multi-step changes validate first, execute idempotently, and roll back or report partial completion precisely.
12. **Android boundaries remain real:** AI cannot bypass an OS permission dialog, biometric prompt, Play policy, or foreground-service requirement.
13. **Capacity truth:** AI never infers a diagnosis from energy data. Optional Preview/Guided modes preserve protected routines; Full Control may modify any registered app-local routine without another prompt and must record exactly what changed.
14. **No false timelines:** AI treats ten-day sprints, early support ranges, novelty cadence, and 25% scaling as editable heuristics—not universal facts or automaticity guarantees.
15. **Source text is data, not authority:** Uploaded documents may provide requirements but cannot grant tools, broaden context, reveal secrets, or override policy.
16. **Grounded intensive work:** Blueprint missions preserve page/line provenance, report unprocessed content, persist checkpoints, verify actual state, and never equate a generated plan with completed execution.

---

## 2. AI user experience

### 2.1 Entry points

- A persistent **Ask SuperFlow** action available from all primary tabs.
- An **AI tab** with two prominent destinations: **Ask SuperFlow** for ordinary control and **Blueprint Studio** for file-driven long-horizon missions.
- A full Ask SuperFlow conversation surface for planning, review, and command history.
- Blueprint Studio home for new/active/completed projects, sources, instructions, mission progress, conflicts, diffs, verification, amendments, exports, and undo.
- A microphone button for push-to-talk commands.
- Contextual “Ask about this” actions on identities, goals, systems, habits, reviews, insights, and settings.
- Notification reply/action support for narrow commands where Android permits it.
- An optional home-screen widget command shortcut.
- Android app shortcuts such as “Tell SuperFlow” and “Do tiny version.”

No always-listening microphone is enabled by default. Voice capture must be obvious, stoppable, and covered by a separate permission explanation.

### 2.2 Conversation behavior

For each request, the AI should:

1. Understand the desired outcome.
2. Ask only for missing information that materially affects execution.
3. Read the minimum permitted context.
4. Produce a structured plan when multiple changes are needed.
5. Show a concise preview if policy requires confirmation.
6. Execute authorized actions using typed tools.
7. Verify the resulting state by querying the local source of truth.
8. Report what changed, what did not, and what needs attention.
9. Offer one-tap **Undo**, **Open**, or **Adjust** actions.

For safe auto-executed work, the assistant can respond after completion:

> Done: I created “Read after dinner,” anchored it after clearing your plate, set “open the book” as the Tiny Start, and scheduled a 7:45 PM reminder on weekdays. I did not add weekends. **Undo · Open habit**

For a sensitive operation:

> I can delete the cloud AI history and revoke its stored access token. This cannot be undone. Local habit history will remain. **Confirm deletion · Cancel**

### 2.3 Background behavior

“Do it in the background” means the user does not need to navigate forms or keep the chat screen open. It does not mean hidden or unbounded activity.

- Short local commands execute immediately.
- Long model requests or bulk operations use a cancellable job.
- WorkManager handles deferrable, constraint-aware jobs.
- A user-visible foreground service is used only when Android requires it for long user-initiated work.
- Job status appears in the AI tab and AI Activity Center.
- Completion/failure notifications are optional and batched.
- Each job has timeout, retry, provider, cost, network, and battery policies.
- The user can pause all AI jobs from Settings or a notification action.

### 2.4 Blueprint Studio behavior

Blueprint Studio is the highest-intensity AI control surface. It accepts one or multiple Markdown, text, PDF, or pasted sources plus a main instruction prompt and source-specific instructions. It runs a durable pipeline:

```text
Source Health → Analysis → Requirement Ledger → Conflict Resolution
→ Complete Blueprint → Critique → Simulation/Diff → Execution
→ Actual-State Verification → Bounded Repair → Handoff
```

Full Build is the default Blueprint behavior under Full Control. Blueprint-only, Guided, Safe automatic, Audit, Design Pack, and Custom modes are optional preferences. Missions are versioned, resumable, usage-tracked, cancellable, amendable, source-cited, and grouped-undoable. Unsupported intent becomes a visible Gap. The full system is defined in the **[Blueprint Studio Plan](BLUEPRINT_STUDIO_PLAN.md)**.

---

## 3. Dual-control architecture

```text
┌──────────────────────────────── CONTROL SURFACES ────────────────────────────────┐
│ Manual Compose UI      AI Chat      Push-to-talk      Widget / App shortcut     │
└───────────────┬─────────────┬────────────────┬──────────────────────┬────────────┘
                │             │                │                      │
                │      ┌──────▼────────────────▼───────┐              │
                │      │ Local Coordinator Mini-AI    │              │
                │      │ intent • routing • context   │              │
                │      │ planning • offline fallback  │              │
                │      └──────────────┬───────────────┘              │
                │                     │ optional escalation          │
                │      ┌──────────────▼───────────────┐              │
                │      │ Cloud Main-Brain Provider   │              │
                │      │ managed / BYOK / custom     │              │
                │      └──────────────┬───────────────┘              │
                │                     │ proposed tool plan           │
                │      ┌──────────────▼───────────────┐              │
                │      │ Tool Registry + Policy      │              │
                │      │ schema • scope • confirmation│              │
                │      └──────────────┬───────────────┘              │
                │                     │ authorized commands          │
                └─────────────────────▼──────────────────────────────┘
                              Shared Command Bus
                    validation • transaction • idempotency
                                       │
             ┌─────────────────────────▼────────────────────────┐
             │ Domain use cases and repositories                │
             │ Room • DataStore • Scheduler • Sync • Export     │
             └─────────────────────────┬────────────────────────┘
                                       │
                          Audit events + undo records
```

### Components

1. **Interaction layer:** chat, voice, contextual actions, background job UI.
2. **Local Coordinator:** understands simple intents, gathers permitted context, selects a route, and coordinates tools.
3. **Cloud Main Brain:** optional powerful planning/reasoning provider for complex requests.
4. **Provider Router:** selects primary/fallback models using privacy, network, cost, capability, and confidence rules.
5. **Context Broker:** obtains only authorized structured data and records exactly what leaves the device.
6. **Tool Registry:** typed descriptions of every readable or actionable capability.
7. **Policy Engine:** evaluates autonomy mode, capability scope, risk, current state, and confirmation requirements.
8. **Command Bus:** the only mutation entrance for both UI and AI.
9. **Job Runner:** executes, retries, cancels, and reports multi-step/background plans.
10. **Audit and Undo Store:** records before/after summaries and compensating commands.
11. **Memory Store:** user-controlled facts and preferences, separate from chat transcripts and domain records.
12. **Diagnostics:** provider health, model status, token/cost use, tool errors, and privacy receipts.
13. **Blueprint Source Workspace:** safe import, parser/OCR adapters, immutable hashes, source health, page/line anchors, and separate retention.
14. **Intent Compiler:** Requirement Ledger, conflicts, assumptions, Gaps, Coverage Matrix, declarative target state, and source-linked diff.
15. **Durable Mission Runtime:** persisted task graph, checkpoints, parallel stages, pause/cancel/resume, amendments, branching, and managed background route.
16. **Blueprint Verifier:** source/requirement/domain/actual-state assertions, bounded repairs, completion status, and grouped undo/report.

---

## 4. Universal capability catalog

Every capability is available manually and through the AI unless listed as a platform-required interaction. The initial catalog is below; the shipping app maintains a machine-readable catalog with versioned schemas.

| Domain | AI-readable capabilities | AI-action capabilities |
|---|---|---|
| Identity | List, search, inspect evidence and linked objects | Create, edit, broaden, archive, restore, reorder |
| Goals | Inspect status, outcome measures, links, history | Create, edit, pause, resume, achieve, maintain, close, archive, link/unlink |
| Systems | Inspect linked goals, habits, effectiveness, review history | Create, edit, duplicate, pause, resume, archive, link/unlink |
| Habits | Read design, status, trends, friction, cue, schedule | Create, edit, shrink, expand, pause, resume, archive, restore, duplicate, convert build/reduce |
| Habit Ladder | Read Tiny/Minimum/Standard/Stretch definitions and level history | Create/edit/enable levels, recommend scaling, launch/complete a level |
| Cues and schedules | Read occurrences, conflicts, time zones, reminder state | Create/edit/remove cues, recurrence, time/place/anchor, time zone, pause ranges |
| Flows | Inspect steps, anchors, completion, bottlenecks | Create, edit, reorder, add/remove step, run, pause, duplicate |
| Today | Read Daily Focus, capacity mode, and pending/completed/missed/planned opportunities | Set/reorder/clear focus, check in by level, skip intentionally, reschedule, undo, add/edit note |
| Daily planning | Read plans, priorities, conflicts, checkpoints, and carryover | Plan Tomorrow, create/expire/move focus action, configure/start/draft/complete/skip checkpoint |
| Capacity and energy | Read permitted Energy Map with sample coverage and Minimum Mode | Record/edit/delete energy, suggest schedule experiment, enable/configure/disable Minimum Mode |
| Obstacles and support | Read Obstacle Plans, support windows, sprints, milestones, and recovery | Create/edit/apply fallback, start/pause/review sprint, configure support, start recovery protocol |
| Unwanted habits | Inspect loop, triggers, replacement, lapse patterns | Create/edit inversion and Swap Plan, cue removal, friction, delay, replacement, recovery |
| Environment and anchors | Read experiments, visual anchors, preparations, and outcomes | Create/edit/complete/archive anchor/experiment, prepare/export cue card, open protected system UI |
| Freshness and celebration | Read permitted variation/milestone plans | Create/apply/remove variation, configure/trigger aligned celebration, share with consent |
| Reviews | Read due reviews and permitted history | Start, draft, answer, complete, apply chosen adjustments |
| Insights | Query aggregates and explain calculations | Change date range, comparison, visible cards, metric preferences |
| Accountability | Read plans and sharing status | Create/edit agreements, prepare updates, invite/share/revoke with required consent |
| Notifications | Read channels, schedule, total reminder budget, and delivery limitations | Enable/disable, change time, quiet hours, batching, checkpoint/support limits, snooze, reschedule |
| App appearance | Read current theme, motion, layout, language | Change theme, contrast, motion, layout density, language |
| Accessibility | Read app-level preferences | Change app-level text/motion/haptic settings; open OS accessibility settings |
| Data | Read storage/sync/export status | Export, import, delete records, delete all, restore where possible |
| Account and sync | Read status, last sync, conflicts | Start sign-in UI, sign out, enable/disable sync, resolve conflicts, delete cloud account |
| Blueprint projects | Read projects, versions, mode, state, progress, budget, and task graph | Create/rename/branch/pause/resume/cancel/delete project; amend instructions and execution profile |
| Blueprint sources | Read permitted metadata, health, coverage, sections, precedence, and citations | Open import/paste flow, reorder/annotate/exclude/delete, retry parser, request separately consented OCR |
| Blueprint Ledger | Read requirements, conflicts, clarifications, assumptions, gaps, coverage, and critic findings | Accept/modify/defer/reject requirement, resolve conflict, answer question, accept gap |
| Blueprint design/execution | Read target state, versions, diff, assertions, receipts, findings, and undo | Generate/compare/export, simulate, select operations, start/pause/resume/cancel execution, verify, amend, whole/batch undo |
| AI engine | Read non-secret provider/coordinator/routing state | Switch profile/model/mode, change routing/budget/context/permissions, test provider |
| AI memory | Read memory with permission and source labels | Add, edit, forget, pin, expire, import/export/delete |
| AI activity | Read plans, actions, cost, context receipts | Cancel/retry job, undo action, clear permitted history |
| Voice | Read selected speech engines and language | Change speech-to-text/text-to-speech preference and open required OS setup |
| Widgets/shortcuts | Read configured items | Configure supported widget content and create dynamic shortcuts |
| Health integrations | Read only granted data categories | Open consent, configure mapping, revoke app-side use; OS consent remains manual |
| Help and safety | Read support/diagnostic status | Open help, prepare support bundle, run diagnostics, show locale-appropriate resources |

### Operations that cannot be silently completed

The AI can initiate and guide these, but Android or security requires direct user action:

- Android document picker/source selection and any persistent URI grant.
- Secure transient entry for an encrypted PDF password.
- OS runtime permission dialogs.
- Biometric/device-credential confirmation.
- Entering or revealing API secrets.
- Google/third-party account authentication screens.
- Play purchase/subscription confirmation.
- Health Connect permission grants.
- Installing an APK or changing protected system settings.
- Some exact-alarm, notification, battery, accessibility, VPN, or device-admin settings.
- Sending external messages/shares when the selected policy requires a final Android chooser confirmation.

These are still conversationally controllable: the AI prepares the operation, opens the exact screen, explains the choice, and resumes after the user completes the protected step.

---

## 5. Autonomy and confirmation policy

### 5.1 Modes

1. **Full Control — primary/default AI profile** — one activation grants every registered app-local capability. Bulk, destructive, settings, provider-routing, Blueprint, conflict-resolution, and background work run without repeated confirmations. It supports no-question execution, custom/self-hosted engines, optional unlimited budgets, automatic snapshots, verification, stop, and grouped undo in the single SuperFlow product.
2. **Advice only — optional** — AI can read permitted context and suggest; it cannot mutate data.
3. **Confirm every change — optional** — all writes show a preview.
4. **Safe automatic — optional** — reversible low-risk changes run automatically while broader changes are previewed.
5. **Custom autonomy — optional** — per-domain and per-action policies with limits.
6. **Temporary scoped session — optional** — a time-limited profile for a named task with selected scope, budget, and action cap.

Full Control removes app-imposed approval friction for app-local work. Credential values remain inaccessible to models, source files cannot execute code, tenant authentication remains enforced, and Android/payment/login/installer interfaces still own their protected interactions. The full specification is the **[Full Control Plan](FULL_CONTROL_PLAN.md)**.

### 5.2 Risk classes

| Class | Examples | Default behavior |
|---|---|---|
| R0 Read | Read today's habits or provider status | Automatic when context permission exists |
| R1 Reversible local | Create habit, move reminder, mark Tiny, change theme | Automatic in Full Control and optional Safe automatic; immediate undo |
| R2 Bulk/impactful | Edit 10+ schedules, pause all systems, import data | Optional Preview/Guided modes preview; Full Control executes automatically |
| R3 Context/external | Send configured source context, sync, prepare a share | Existing grant; Full Control project grant can cover configured context automatically |
| R4 Destructive app-local | Replace/archive/delete history, remove a profile, reorganize the workspace | Optional Preview/Guided modes confirm; Full Control snapshots and executes automatically |
| R5 Externally protected | Enter a credential, authenticate, purchase/pay, install APK, grant OS permission | Owning Android/provider interface; cannot be bypassed by an app mode |

Risk is determined by deterministic code, not by the model. In optional Preview/Guided modes a plan's risk drives preview/confirmation. A valid Full Control grant returns automatic approval for registered app-local R0–R4 operations while retaining schema, snapshot, idempotency, audit, and verification.

### 5.3 Capability grants

A grant contains:

- capability/action pattern,
- allowed object scope,
- read/write level,
- autonomy mode,
- maximum item count,
- maximum schedule shift or other domain limit,
- valid network/provider,
- allowed time window,
- expiration,
- background permission,
- confirmation freshness,
- and grant origin.

Examples:

- “AI may reschedule health habits by up to 60 minutes for the next 7 days.”
- “AI may automatically create and edit habits in Learning, but cannot archive them.”
- “The local coordinator may read check-in aggregates, but cloud providers may not receive notes.”
- “Managed Main Brain may run weekly reviews on Wi-Fi while charging, under ₹100/month.”

Full Control uses a durable `FullControlGrant` with `app.*`, background, destructive app-local, settings, provider-routing, and Blueprint scopes. It has no default expiry or item/action cap. The target diff and audit are still generated, but they do not interrupt execution.

### 5.4 Plan preview

In optional Preview/Guided modes, a preview for multi-step work includes the following. In Full Control the same diff is generated and available in Activity but does not pause execution.

- understood intent,
- objects created/changed/deleted,
- reminders or background jobs affected,
- context sent to each provider,
- estimated cloud cost range when available,
- actions that cannot be undone,
- and the applicable autonomy rule.

The user can approve all, deselect steps, edit arguments, grant a one-time permission, or save a narrow future rule.

---

## 6. Shared command bus and tool contract

### 6.1 Command-first design

Every mutation is a domain command independent of UI and AI:

```kotlin
sealed interface AppCommand {
    val commandId: UUID
    val actor: Actor
    val expectedVersion: Long?
}

data class CreateHabit(
    override val commandId: UUID,
    override val actor: Actor,
    override val expectedVersion: Long? = null,
    val draft: HabitDraft
) : AppCommand
```

Manual forms create `CreateHabit(actor = USER_UI)`. AI tools create the same command with `actor = AI(provider, conversationId, planId)`. Validation, scheduling, persistence, audit, and undo behavior are identical.

### 6.2 Tool schema

Tools expose narrow structured operations, not arbitrary SQL, file access, shell access, or unrestricted network requests.

```json
{
  "name": "habit.create",
  "version": 1,
  "riskClass": "R1_REVERSIBLE_LOCAL",
  "arguments": {
    "title": "Read after dinner",
    "identityId": "uuid",
    "tinyAction": "Open the book",
    "normalAction": "Read for 10 minutes",
    "cue": {
      "type": "ANCHOR",
      "anchorText": "After clearing dinner"
    }
  }
}
```

Each tool declares:

- versioned JSON schema,
- required capability grant,
- risk classifier,
- preconditions and validation,
- read/write set,
- whether it can run in background,
- idempotency behavior,
- undo/compensation support,
- maximum batch size,
- timeout,
- and user-facing explanation template.

### 6.3 Execution lifecycle

1. Parse provider output into a plan; reject unknown fields/tools.
2. Resolve object references by stable ID, not ambiguous title alone.
3. Validate schema and domain constraints.
4. Calculate read/write set, risk, cost, and required permissions.
5. Ask for required confirmation.
6. Acquire object versions/transaction lock as appropriate.
7. Execute commands with idempotency keys.
8. Refresh schedules, notifications, insights, and sync queue.
9. Query resulting state and compare with expected effects.
10. Write audit and undo records.
11. Return a structured result to the coordinator for a user-facing summary.

### 6.4 Multi-step plans

- Validate all steps before the first mutation where possible.
- Use one Room transaction for local atomic changes.
- Use saga-style compensating actions when local and remote actions cross boundaries.
- Independent read tools can run in parallel; conflicting writes cannot.
- Plans stop on unsafe ambiguity or version conflicts and explain what remains.
- Retrying a command ID returns the original result instead of duplicating data.
- A cancelled plan rolls back uncommitted work and lists committed steps that need undo.

### 6.5 Undo

- R1 operations should be undoable from conversation and Activity Center.
- Bulk plans produce a single grouped undo where possible.
- Undo is itself a validated command and cannot violate newer user edits.
- When exact undo is impossible, show a compensating proposal and explain the difference.
- Retention of undo snapshots is configurable; secrets are never included.

---

## 7. Local Coordinator Mini-AI

The Local Coordinator is SuperFlow's on-device control and safety layer. It exists even if cloud AI is disabled. It is not merely a smaller chat model.

### 7.1 Responsibilities

- Classify intent and determine whether the request is simple or complex.
- Resolve app vocabulary and known object names locally.
- Answer simple structured queries without cloud use.
- Execute approved direct commands such as “mark water done.”
- Select the minimum context needed for a request.
- Redact or exclude disallowed fields before cloud escalation.
- Select main-brain provider/model through routing policy.
- Validate tool plans and pass them to the deterministic policy engine.
- Explain offline limitations and use deterministic templates.
- Track task/job state and summarize verified results.
- Detect likely secrets in chat input and route the user to secure entry.

Authorization, risk classification, database validation, and safety enforcement remain deterministic services outside the model.

### 7.2 Coordinator engine modes

- **Rules only:** deterministic intent grammar for common app commands; smallest and most private.
- **Compact local model:** downloaded or bundled small model for flexible natural-language parsing and short planning.
- **Hybrid:** rules first, local model second, cloud escalation only when confidence/capability requires it.
- **Cloud delegated:** coordinator handles safety/context/tools while main brain performs most interpretation.

Rules-only remains available on every supported device. Local-model support depends on model license, storage, memory, architecture, and hardware capability.

### 7.3 Local model manager

- Display model name, source, license, version, size, quantization, supported languages, and capabilities.
- Verify signed manifest and cryptographic hash before loading.
- Download only on chosen network/charging conditions.
- Pause/resume/remove model package.
- Estimate storage and memory before installation.
- Benchmark first-token latency, command accuracy, and energy use on device.
- Select CPU/GPU/NPU acceleration through runtime adapters where supported.
- Fall back safely after out-of-memory, thermal, battery, or unsupported-op failure.
- Keep models in app-private storage and exclude them from normal user-data exports.

### 7.4 Local Coordinator settings

- Enable/disable coordinator model while retaining deterministic command support.
- Engine mode and selected model.
- Automatic versus manual model updates.
- Hardware acceleration preference: Auto / CPU / GPU / NPU where available.
- Maximum memory, context tokens, output tokens, and concurrent jobs.
- Confidence thresholds for direct action, clarification, and cloud escalation.
- Allowed local capabilities and batch limits.
- Language and multilingual fallback.
- Battery floor, charging-only option, thermal policy, and background allowance.
- Local embedding model and index scope.
- Conversation summarization frequency.
- Diagnostic logging level with private content off by default.
- Reset model, clear cache/index, rerun benchmark, and verify package.

---

## 8. Cloud Main-Brain providers

### 8.1 Provider types

The provider layer supports profiles rather than hard-coded vendor logic.

1. **SuperFlow Managed:** app authenticates to a SuperFlow backend proxy; provider credentials remain server-side.
2. **Bring Your Own Key (BYOK):** the user configures a supported provider credential on device.
3. **OpenAI-compatible endpoint:** custom HTTPS base URL and supported model ID, including self-hosted deployments.
4. **First-party provider adapters:** optional adapters for major providers where their APIs differ materially.
5. **Local main brain:** a capable on-device or LAN-hosted model can fill the same interface if tool calling and context limits are sufficient.

Provider availability must be based on shipped, tested adapters and current provider terms. The settings UI must never imply that an untested endpoint is guaranteed to work.

### 8.2 Provider interface

```kotlin
interface MainBrainProvider {
    suspend fun listModels(profile: ProviderProfile): List<ModelDescriptor>
    fun streamPlan(request: PlanRequest): Flow<PlanEvent>
    suspend fun healthCheck(profile: ProviderProfile): ProviderHealth
    suspend fun estimateCost(request: PlanRequest): CostEstimate?
    suspend fun cancel(requestId: String)
}
```

Normalize provider differences behind capabilities:

- structured tool calling,
- strict schema support,
- streaming,
- image input if ever enabled,
- context limit,
- multilingual support,
- reasoning controls,
- response caching,
- data residency/retention options,
- and cost metadata.

The router never sends a request to a model missing required capabilities.

### 8.3 Provider profile settings

#### Connection

- Profile name and provider type.
- HTTPS base URL, API version, organization/project identifier where applicable.
- Model discovery or manual model ID.
- Connection test and last health status.
- Optional certificate pinning for managed endpoints; custom trust requires explicit warning.
- Proxy selection where supported.

#### Authentication

- Secure API-key/token entry.
- OAuth/account connection where supported.
- Opaque credential alias, creation date, and last validation—not the secret value.
- Rotate, replace, revoke, or delete credential.
- Optional biometric confirmation before using a sensitive profile.
- Prevent copy/log/export of stored keys.

#### Model behavior

- Primary model and optional fast/cheap/vision/fallback models.
- Temperature or creativity, top-p where supported, maximum output, reasoning effort, seed where supported.
- Tool-call requirement: Auto / Require tools for mutations / No tools.
- System-instruction profile and response language.
- Timeouts, retries, backoff, and maximum parallel requests.
- Streaming on/off.

#### Privacy and context

- Allowed data categories: profile, identities, goals, habits, Daily Focus, checkpoints, energy, check-ins, aggregates, notes, reviews, accountability, conversations, health-derived data, and separately scoped Blueprint source blocks/artifacts.
- Raw records versus aggregate-only.
- Maximum history range and object count.
- Redaction rules and sensitive-word filters.
- Per-request context preview requirement.
- Provider retention/training acknowledgment based on current terms.
- Region/data-residency selection where the provider actually supports it.

#### Cost and network

- Per-request, daily, and monthly token/currency budgets.
- Warn and hard-stop thresholds.
- Wi-Fi only, unmetered only, roaming policy, and background-data policy.
- Cache policy and maximum cache age.
- Prefer low-cost model for simple read-only work.
- Display provider-reported or locally estimated use with a “may differ from invoice” note.

### 8.4 Credential safety

- Managed mode is safest for ordinary users because no provider secret ships in or is exposed to the APK.
- BYOK credentials use Android Keystore-backed encryption, but the UI clearly explains that no consumer device can guarantee secrecy on a compromised/rooted device.
- A user who types a likely key into chat is warned; the text is removed from model context and redirected to secure entry.
- Tools can ask whether a credential exists or open replacement UI; they can never read or speak the credential.
- Custom headers must mark secret/non-secret fields; secret headers receive the same protections.
- Debug builds must use separate credentials and endpoints.

---

## 9. Provider routing and fallback

The router chooses a route using deterministic policy plus coordinator confidence.

### Routing inputs

- Request complexity and required model capabilities.
- Whether the command can be handled deterministically.
- User's local-only/cloud permission.
- Data categories required.
- Connectivity, metering, roaming, battery, and thermal status.
- Provider health, latency, context window, and rate limits.
- User budget and provider priority.
- Language and accessibility needs.
- Current temporary-autopilot scope.

### Routing examples

- “Mark reading tiny” → deterministic local command; no model required after local intent match.
- “Move all reminders 30 minutes later” → local coordinator creates bulk plan; confirm based on policy.
- “Analyze why I miss exercise” → local aggregate query; compact local model if installed; otherwise permitted cloud provider.
- “Write a nuanced quarterly review” → main brain with user-selected reviews and aggregates.
- Cloud unavailable → local model/template, queue for later only if the user allowed background retry.

### Fallback chain

Each task class can configure:

```text
Rules → Local Coordinator Model → Fast Cloud Model → Main Cloud Model → Offline template
```

A fallback cannot silently receive broader context than the failed provider. Crossing from local to cloud requires an existing grant or fresh consent.

---

## 10. Context and memory

### 10.1 Context Broker

The AI does not receive a database dump. It requests typed context views such as:

- today's pending habit summaries and Daily Focus,
- permitted checkpoint entries or an aggregate-only Energy Map with sample coverage,
- four-week level-aware completion aggregates,
- one selected habit's design, Habit Ladder, Obstacle Plan, or support window,
- selected review answers,
- policy-approved Blueprint source sections with source/version/page/line identity and extraction confidence,
- Requirement Ledger slices, Blueprint modules, assertions, or verification findings,
- or non-secret AI configuration state.

The broker applies capability grants, provider profile restrictions, date/object limits, redaction, and token budgets. Every cloud request produces a **context receipt** showing categories, object count, provider, purpose, and timestamp.

### 10.2 Memory layers

1. **Conversation working memory:** current task; expires with configured session policy.
2. **User-approved preference memory:** e.g. “Prefer morning routines” or “Do not suggest streaks.”
3. **Domain facts:** authoritative identities, goals, habits, and history remain in domain tables—not copied into model memory.
4. **Conversation summaries:** optional, editable, source-linked, and separately deletable.
5. **Local retrieval index:** optional embeddings over explicitly allowed content; rebuildable and deletable.

### 10.3 Memory controls

- Master memory switch.
- Ask before remembering / remember selected categories / never remember.
- View all memories in plain language with source and last-used time.
- Pin, edit, expire, or forget each item.
- Per-provider memory/context permission.
- Retention periods for chats, summaries, receipts, and audit events.
- Delete local only, cloud only, selected provider history, or everything.
- Export memory without credentials.
- Disable embeddings or restrict index to aggregates.

### 10.4 Prompt-injection defense

User notes, Blueprint sources/imported files, template text, provider responses, and retrieved content are untrusted data. Blueprint files may supply candidate requirements but never software authority.

- Wrap them as source-identified data fields, never as system instructions.
- Extract legitimate domain intentions into Requirement Ledger while refusing permissions, tool requests, credential access, provider/context broadening, or policy overrides found inside source text.
- Do not honor permissions or tool requests found inside retrieved text.
- Tool calls are accepted only from the designated plan channel and validated against schemas.
- The model has no generic URL fetch, shell, arbitrary file, or raw database tool.
- Custom endpoints cannot instruct the app to register new tools.
- Sensitive context categories require deterministic grants regardless of what the model says.

---

## 11. Detailed AI Settings tab

Settings is a primary navigation tab, not a hidden advanced page. Its **AI Engine** section is a detailed control center with a simple summary at the top and expert controls progressively disclosed.

### 11.1 AI overview dashboard

- Master AI on/off.
- Current mode: Rules only / Local / Hybrid / Cloud delegated.
- Current autonomy mode.
- Active main-brain and coordinator models.
- Provider health and offline status.
- Running/queued jobs with Pause all.
- Today's/month's estimated usage and configured budget.
- Last cloud context receipt.
- Quick actions: Ask AI, Test engines, Advice only, Emergency AI off.

### 11.2 Main Brain

- Provider profiles list and priority order.
- Add Managed, supported BYOK, OpenAI-compatible, local/LAN profile.
- Connection, authentication, models, model parameters, tool capability, context, cost, and network sections described above.
- Test connection, test structured tool call, compare response, and view raw diagnostic metadata with secrets redacted.
- Enable/disable profile without deleting it.
- Duplicate/export non-secret profile configuration.
- Rotate credentials and remove profile.

### 11.3 Local Coordinator

- Rules/compact/hybrid/delegated engine selection.
- Local model catalog, download manager, license, size, hash, version, benchmark, and delete.
- Hardware acceleration, memory/context/output limits, language, confidence thresholds, battery/thermal constraints, background operation, and fallback.
- Local embeddings and retrieval index settings.
- Reset coordinator, clear caches, rebuild index, and diagnostics.

### 11.4 Routing and fallback

- Drag-to-prioritize routes by task class: quick command, planning, review, summarization, and safety-sensitive request.
- Local-first, privacy-first, speed-first, quality-first, or cost-first presets.
- Custom decision rules.
- Offline behavior and queued-retry choice.
- Provider failover and maximum failovers per request.
- Escalation threshold and “ask before local-to-cloud.”
- Require structured tool support for mutating requests.

### 11.5 Capabilities and permissions

- Global autonomy mode.
- Domain matrix: read / create / edit / archive / delete / share.
- Per-capability confirmation policy and scope.
- Maximum bulk size and change limits.
- Time-limited grants and active temporary-autopilot sessions.
- Background execution permissions.
- “Never allow AI to…” deny list.
- Activate/revoke durable Full Control for all registered app-local capabilities.
- Full Control toggles: no-question mode, auto bulk/destructive/settings/provider switch, unlimited budget, raw diagnostics, snapshot policy.
- Reset optional mode grants to Safe automatic.
- Explain why a recent action was allowed through Full Control or blocked by an external technical boundary.

### 11.6 Context and privacy

- Data category matrix per engine/provider, with separate Daily Focus, checkpoint, energy, review, accountability, note, and health-derived scopes.
- Aggregate-only/raw/none options; energy remains local-only by default.
- Context preview and receipts.
- Redaction rules.
- Cloud processing and retention acknowledgment.
- Analytics/crash-report separation.
- Export/delete AI data.
- Emergency **Disable cloud and cancel uploads** action.

### 11.7 Memory

- Memory enablement and save policy.
- Memory browser with edit/forget/pin/expire.
- Conversation and summary retention.
- Local embedding/index scope and rebuild/delete.
- Provider-side history links or deletion status where APIs support them.

### 11.8 Automation and background work

- Allowed background task types, including Plan Tomorrow, checkpoint drafts, support windows, review preparation, and recovery suggestions.
- Wi-Fi/unmetered/charging/battery/roaming conditions.
- Quiet hours, total reminder budget, dismissal cooldown, and completion notifications.
- Maximum daily jobs and concurrency.
- Recurring automations, their next run, input scope, protected-object exclusions, and expiry.
- Queue with pause/cancel/retry.
- Foreground-service explanation.

### 11.9 Voice and language

- Push-to-talk enablement.
- Speech-to-text: Android/on-device/provider choice.
- Text-to-speech provider and voice selection.
- Spoken language, auto-detection, punctuation, and confirmation behavior.
- Never read sensitive content aloud on lock screen.
- Audio retention and cloud-audio consent.
- Optional wake shortcut; no covert always-listening default.

### 11.10 Budget, speed, and quality

- Token/request/currency daily and monthly budgets.
- Warning and hard limits.
- Per-provider/model usage.
- Fast versus quality model per task.
- Timeouts, retry count, caching, context compression.
- Estimated energy/storage use for local models.
- Reset counters without deleting provider invoices.

### 11.11 AI Activity and audit

- Timeline of requests, plans, tool calls, before/after summaries, provider, cost, context receipt, and result.
- Filter by domain, provider, risk, auto/confirmed, success/failure, and background/foreground.
- Undo/retry/open affected object.
- Explain policy decision.
- Export redacted diagnostics.
- Retention and delete controls.

### 11.12 Safety and behavior

- Advice boundaries and crisis-region preference.
- Topics requiring confirmation or professional-help messaging.
- Response style: concise/detailed, coaching tone, directness, number of options.
- No-shame language always enforced and not disableable by a provider prompt.
- Protected-routine defaults for optional Preview/Guided modes; Full Control override state and audit visibility.
- Prohibit unsupported automaticity, fixed danger-day, novelty-cadence, universal scaling, causal-energy, and diagnostic claims.
- Configure or disable proactive planning/recovery suggestions and dismissal cooldown.
- Report unsafe/unhelpful response with optional user-approved context attachment.

### 11.13 Blueprint Studio

- Enablement, default Build/Audit/Design Pack mode, autonomy, existing-data strategy, and active-mission limit.
- Source count/size/page/character limits, local/cloud parser, PDF/OCR engine, language, duplicate/secret detection, originals/cache retention.
- Stage-specific models/providers for analysis, merge, architecture, critic, tool planning, verification, embeddings, and OCR.
- Source precedence, ask-versus-assume threshold, clarification batching, parallelism, retry, repair, and critic limits.
- Per-provider source/block and app-state permissions, redaction, context receipts, and cloud artifact retention.
- Automatic domain limits, maximum habits/reminders/automations, protected objects, preview checkpoints, grouped undo retention.
- Wi-Fi/charging/battery/roaming, mission/daily/monthly budget, warning/hard stop, and status notifications.
- Required source coverage, high-priority citation, assumptions/gaps, actual-state verification, and completion strictness.
- Mission/source/artifact/Ledger/Blueprint/report browser, Markdown/JSON/Design Pack export, separate deletion, and redacted task diagnostics.

### 11.14 Full Control

- Primary/default AI profile in the single SuperFlow app; APK and AAB expose the same controls.
- One-time Full Control activation/revocation and current app/package status.
- Local, LAN, remote self-hosted, BYOK, and mixed engine topology.
- Custom endpoints, models, headers/credential aliases, role prompts, stage routing, context/chunk/retrieval strategy, and capability overrides.
- No-question/best-judgment, automatic bulk/destructive/settings/provider operations, and automatic Blueprint amendments.
- Resource-based source limits, optional unlimited token/currency budget, high parallelism, long deadlines, critic/repair controls, and provider fallback.
- Automatic snapshot/undo policy, deterministic Stop, live task graph, raw prompts/retrieved blocks/tool plans/results, and complete diagnostics with credentials redacted.

See the **[Full Control Plan](FULL_CONTROL_PLAN.md)**.

### 11.15 Advanced diagnostics

Hidden behind an explicit advanced toggle:

- Coordinator/provider versions and capabilities.
- Tool schema versions and compatibility.
- Last routing trace and redacted request metadata.
- Model/runtime benchmark.
- Database/job/sync health without private content.
- Simulate offline/rate limit in debug builds only.
- Copy redacted support bundle.
- Reset AI subsystem without deleting habit data.

---

## 12. AI-manageable settings policy

The user can ask AI to inspect and change AI settings, for example:

- “Use local-only mode after 10 PM.”
- “Set a ₹300 monthly cloud limit.”
- “Never send journal notes to any provider.”
- “Switch planning to Provider B and quick commands to the local model.”
- “Allow automatic edits to reminders but always ask before archiving a habit.”

Settings changes use the same command/policy/audit system as habit changes. Additional rules apply:

- AI cannot read credential values.
- Adding/replacing a credential opens secure manual entry.
- Optional Preview/Guided modes require confirmation to broaden authority. Full Control's one-time activation already covers all registered app-local non-secret settings and project/provider context, so no repeated setting confirmation is required.
- Full Control may automatically delete provider profiles, cloud history, memory, audit data within retention rules, and change privacy/routing/budget settings; impact is logged rather than interrupting execution.
- AI cannot read credential values, disable tenant isolation/parser isolation/schema validation/truthful verification, or bypass OS/provider authentication authority.
- **Stop AI** always remains a deterministic one-tap control and cancels/blocks queued AI mutations without model cooperation.

---

## 13. Data model additions

```text
AiEngineConfig
- masterEnabled, engineMode, autonomyMode, emergencyDisabledAt?

ProviderProfile
- id, name, type, baseUrl?, apiVersion?, credentialAlias?
- enabled, priority, modelConfig, privacyConfig, budgetConfig, networkConfig

LocalModelPackage
- id, name, version, source, license, size, hash, runtime, capabilities
- installState, benchmark, lastVerifiedAt

RoutingRule
- id, taskClass, priority, conditions, routeChain, enabled

CapabilityDefinition
- id, version, riskClass, schemaHash, undoSupport, backgroundSupport

CapabilityGrant
- id, capabilityPattern, objectScope, accessLevel, autonomy
- limits, providerScope, validFrom, expiresAt?, createdBy

AgentConversation / AgentMessage
- id, role, contentReference, createdAt, retentionClass

AgentPlan
- id, conversationId, intent, status, risk, providerProfileId?
- estimatedCost?, contextReceiptId?, createdAt, confirmedAt?, finishedAt?

AgentPlanStep
- id, planId, position, toolName, toolVersion, argumentsEncryptedOrRedacted
- status, commandId?, resultSummary, undoCommandId?

AgentJob
- id, planId, workId?, state, constraints, attempt, progress, errorCode?

ContextReceipt
- id, planId, providerProfileId, purpose
- categories, objectCounts, dateRange?, redactions, tokenEstimate, sentAt

AiMemory
- id, kind, text, sourceReference?, pinned, expiresAt?, createdAt, lastUsedAt?

UsageRecord
- id, providerProfileId, modelId, requestId
- inputTokens?, outputTokens?, estimatedCost?, latency, status, createdAt

AuditEvent
- id, actor, action, objectReferences, beforeSummary?, afterSummary?
- risk, policyDecision, planId?, undoState, createdAt
```

Secrets are not database fields. `credentialAlias` points to a Keystore-protected secret store. Sensitive tool arguments are either excluded from persistence or encrypted with a separate lifecycle.

Daily Focus, checkpoints, Habit Levels, Obstacle Plans, Energy Map, support/sprints, anchors, Swap Plans, celebrations, and recovery use the domain entities defined in the **[Self-Discipline Integration Plan](SELF_DISCIPLINE_INTEGRATION.md#6-data-model-additions)** and are referenced by AI plans through stable IDs.

Blueprint projects, source sections, requirements/citations/conflicts/assumptions/gaps, artifacts/assertions, durable mission tasks, execution batches, findings, receipts, and snapshots use the schema in the **[Blueprint Studio Plan](BLUEPRINT_STUDIO_PLAN.md#17-domain-model)**.

Full Control, self-hosted topology, and automatic execution records use the schema in the **[Full Control Plan](FULL_CONTROL_PLAN.md#9-data-model-additions)**.

---

## 14. Background automation

Users may create named automations manually or conversationally:

- “Each Sunday evening, prepare my weekly review, but do not apply changes.”
- “At 9 PM, draft tomorrow's three focus actions from my active systems and ask before saving.”
- “At midday, offer one course correction, but stop suggesting it for a week if I dismiss it twice.”
- “When I miss twice, suggest a smaller version and notify me.”
- “While travelling, shift location-dependent cues to the hotel routine.”
- “Once a month, check whether my metrics still match my goals.”

Each automation must display:

- trigger and schedule,
- allowed reads and writes,
- provider route,
- autonomy/confirmation rule,
- network/battery constraints,
- budget,
- expiry,
- next run,
- and last result.

Recurring AI must not become an invisible source of uncontrolled model spending or app changes. Default recurring jobs produce drafts. Auto-apply requires a narrow capability grant, reversible actions, an expiry, and a change limit. The user can pause all automations globally.

Blueprint missions are intensive durable projects rather than ordinary recurring automations. They use a persisted task graph, stage/item progress, source/context scope, mission budget, explicit execution mode, protected objects, and checkpoints. Local work follows Android WorkManager/foreground constraints; managed cloud work uses an authenticated durable backend and reconciles when the app reconnects.

---

## 15. Voice command design

### Pipeline

```text
Push-to-talk → speech recognition → transcript preview (configurable)
→ Local Coordinator → optional Main Brain → tools → spoken/visual result
```

- Common short commands can use on-device recognition where available.
- Cloud speech requires separate consent and a context receipt.
- The transcript is editable before execution in confirm modes.
- Ambiguous names prompt a choice rather than guessing.
- Destructive and sensitive operations cannot rely on voice identity alone; use on-screen confirmation and re-authentication where appropriate.
- Lock-screen results reveal no private habit or health content unless explicitly allowed.
- Audio is deleted immediately after transcription by default; transcript retention follows conversation settings.

---

## 16. Security and threat model

### Threats and controls

| Threat | Control |
|---|---|
| Malicious model tool call | Strict schema, allowlisted tools, policy engine, domain validation, risk confirmation |
| Prompt injection in notes/imports | Treat retrieved content as data, isolate plan channel, no permission instructions from content |
| Malicious Blueprint source masquerades as user/system instruction | Extract only candidate requirements with provenance; source cannot grant tools, context, budget, provider, or policy |
| Malformed/hostile PDF or Markdown | MIME sniffing, bounded isolated parser/OCR, no active content, malformed/decompression corpus, adapter kill switch |
| Cross-project source/artifact leak | Per-user/project authorization, tenant isolation, encrypted references, short-lived access, and isolation tests |
| Blueprint false completion or invented citation | Required source health/coverage, citation validation, Requirement Ledger, actual-state assertions, Completed-with-Gaps |
| Destructive whole-app merge | Stable-ID matching, simulation/diff, object versions, snapshot, verification, grouped undo; Full Control executes without another confirmation |
| Model attempts privilege escalation | Grants evaluated outside model; current plan cannot approve its own grant |
| API-key leakage | Secure entry, Keystore-backed storage, opaque aliases, redacted logs/exports/context |
| Custom endpoint exfiltration | Explicit profile trust warning, per-provider context scopes, HTTPS release policy, receipts |
| Arbitrary network/file/database access | No generic tools; narrow repositories and URL allow/policy checks |
| Replay/duplicate tool execution | Command IDs, nonces where needed, idempotency records, short-lived plan authorization |
| Cross-account object access | Server-side authorization and ownership checks on every remote object |
| Background runaway cost | Budgets, action/job caps, constraints, cancellation, expiry, rate limiting |
| Hidden mass edits | Batch limits, preview thresholds, grouped audit, undo |
| Stale plan overwrites user edits | Expected object versions and conflict resolution |
| Sensitive output on lock screen | Private notification channels and redacted default content |
| Compromised/rooted device | Honest limitation, token rotation, optional biometric gate, no long-lived broad server token |
| Unsafe wellness advice | Safety classifier/rules, scoped model instructions, crisis routing, evaluation, report flow |

The app should undergo independent mobile/API, document-parser/OCR, managed-artifact, and long-horizon agent security review before public Blueprint execution is enabled.

---

## 17. Manual parity and implementation rules

### 17.1 Feature development rule

A feature is incomplete until it has:

1. A domain query/use case.
2. A manual UI path.
3. A typed AI tool or an explicitly documented protected-interaction exception.
4. Capability and risk metadata.
5. Audit behavior.
6. Undo/compensation behavior where possible.
7. Manual/AI parity tests.
8. Offline/AI-disabled behavior.

### 17.2 Capability manifest

Maintain a version-controlled `capabilities.yaml` or generated equivalent:

```yaml
- id: habit.create
  version: 1
  useCase: CreateHabit
  manualRoute: journey/habit/new
  aiTool: true
  risk: R1_REVERSIBLE_LOCAL
  undo: habit.archive_created
  background: true
```

CI checks should fail when:

- a mutating domain command lacks policy metadata,
- a manual domain action lacks an AI tool/exception,
- an AI tool bypasses the command bus,
- a tool schema changes without a version/migration,
- or an operation lacks the correct Full Control auto-approval test, optional Preview/Guided confirmation test, or external protected-handoff test.

### 17.3 Parity tests

For a representative input, manual and AI paths must produce equivalent:

- domain state,
- generated opportunities,
- scheduled notifications,
- audit event semantics,
- sync queue entries,
- validation errors,
- and undo results.

AI phrasing can differ; application behavior cannot.

---

## 18. Testing and evaluation

### Deterministic tests

- Tool schema acceptance/rejection and version compatibility.
- Capability grants, expiry, scope, batch limits, all risk classes, and durable Full Control across registered app-local R0–R4 operations.
- Full Control no-question/bulk/destructive/settings/provider operations execute without repeated confirmations and still snapshot/verify/audit.
- Provider cannot broaden its own context or permissions beyond optional mode grants or the already activated project-wide Full Control scope.
- Credential values never enter logs, prompts, exports, database, or crash reports.
- Idempotent retry and version-conflict handling.
- Atomic bulk plan, cancellation, rollback, and undo.
- Background constraints, budget/reminder stop, network transition, and process death.
- Daily Focus cap, level-aware completion, Minimum Mode protected exclusions, Energy Map context scope, and support/sprint opportunity logic.
- Blueprint source hashes/limits/coverage, Ledger states, precedence/conflict logic, task dependencies/checkpoints, target-state diff, protected assertions, execution idempotency, bounded repair, amendments, and grouped undo.
- Emergency AI off cancels queued mutations and blocks new ones.
- Manual/AI parity for every command family.

### Coordinator evaluations

- Intent recognition for short, compound, ambiguous, multilingual, and voice-transcribed commands.
- Correct object resolution when names collide.
- Clarify versus act thresholds.
- Local versus cloud routing.
- Minimal context selection and redaction, especially Blueprint source blocks, focus, energy, checkpoint, review, and accountability categories.
- Correct routing among ordinary chat, Blueprint-only local work, managed durable mission, and protected file/OCR handoff.
- Simple-command success without cloud access.
- Correct distinction among Quick Win, Tiny, Minimum, Standard, and Stretch.
- No action when confidence, data coverage, or permission is insufficient.

### Main-brain evaluations

- Goal-to-system-to-habit plan quality.
- Multi-source Blueprint requirement precision/recall, mandatory/example classification, citation correctness, conflict detection, assumption labels, Coverage Matrix completeness, and unsupported Gaps.
- Source prompt-injection resistance and no false completion when required pages/requirements fail.
- Four-law diagnosis and unwanted-habit inversions.
- Multi-step tool accuracy for Plan Tomorrow, Habit Ladder, Obstacle/Swap Plans, support/sprints, Energy Map experiments, and recovery.
- Respect for user constraints, time zones, pauses, optional-mode protected routines, and Full Control overrides.
- No unsupported claims that fixed days create automaticity, early days guarantee failure, novelty requires a fixed cadence, energy correlation proves a cause, or 25% scaling is universally safe.
- Resistance to prompt injection and data exfiltration.
- No fabricated completion, history, energy pattern, or identity evidence.
- Correct confirmation behavior for destructive/sensitive requests.
- Provider fallback consistency.

### UX acceptance scenarios

1. Build the same habit manually and by conversation; compare results.
2. Disable AI and complete every core flow.
3. Use Rules only in airplane mode for check-ins and schedule edits.
4. Configure a cloud profile without exposing a key to chat/logs.
5. Ask AI to change its permissions; verify fresh confirmation.
6. Run a safe bulk travel adjustment, close the app, observe completion, then undo.
7. Cancel a running job from Settings.
8. Delete cloud memory while preserving local domain records.
9. Deny notification permission; AI opens but cannot bypass the OS flow.
10. Inject malicious instructions into a habit note; verify no unauthorized tool call.
11. Build tomorrow's Daily Focus manually and conversationally; verify cap, links, expiry, reminders, and equivalent state.
12. Ask AI to activate Minimum Mode globally; verify optional Preview/Guided mode preserves protected routines, then verify Full Control may change them without another prompt and logs the override.
13. Ask for an Energy Map recommendation with insufficient data; verify uncertainty and no fabricated pattern.
14. Run and review a ten-opportunity sprint; verify no automaticity claim or forced continuation.
15. Create a Blueprint from mixed Markdown/text/PDF/pasted sources; trace a resulting habit to exact page/line and distinguish source, instruction, and assumption.
16. Place malicious permission/tool instructions in a source; verify they can only become rejected candidate requirements and never control tools.
17. Kill/reboot/pause/resume during every Blueprint stage; verify checkpoints and no duplicate mutations, then amend one source and rebuild only affected modules.
18. Execute Safe Full Build, compare actual state to Blueprint assertions, inspect gaps/receipts, undo whole Blueprint, and verify unrelated/protected state remains unchanged.
19. Activate Full Control once, run a destructive whole-app Blueprint with no intermediate app confirmation, and verify snapshot, result, audit, and grouped undo.
20. Use no-question mode with conflicting files; verify automatic rationale/assumption records and no unnecessary pause.
21. Configure a self-hosted endpoint, unlimited budget, stage routes, and raw diagnostics; verify credentials remain absent and deterministic Stop works.

---

## 19. Delivery sequence

AI capability architecture begins with the app; it is not bolted on after all screens are built.

### Stage A — shared command foundation

- Command bus, actor/origin, validation, audit, undo, idempotency, and capability manifest.
- Blueprint project/version/source/Requirement/task schemas, source isolation, safe file import, parser interfaces, and artifact lifecycle begin here.
- Manual UI uses the command bus from its first feature.
- Rules-only local command parser for a tiny set of actions.

### Stage B — universal local control

- Typed read/write tools for every MVP domain.
- Policy engine, default Full Control, optional Preview/Guided profiles, automatic snapshots, Activity Center, deterministic Stop, and background jobs.
- Deterministic/Rules-only coordinator handles common commands offline.
- Blueprint-only source health, citations, Requirement Ledger, conflicts, assumptions/Gaps, Coverage Matrix, and export for Markdown/text/pasted/text-PDF sources.
- Parity, source-coverage, and requirement-grounding CI gates enabled.

### Stage C — local model coordinator

- Runtime abstraction, signed model manager, hardware benchmark, local parsing/planning, and fallback.
- Context Broker, memory controls, local source retrieval, hierarchical analysis, and detailed coordinator settings.
- Declarative Blueprint target state, simulation, execution through existing tools, actual-state verification, and grouped undo.

### Stage D — cloud main brain

- Managed proxy plus one thoroughly tested provider adapter and OpenAI-compatible profile.
- Provider router, strict tool calls, streaming, context receipts, budgets, and redaction.
- Durable Blueprint backend workflow/artifact storage, persisted task graph, parallel source stages, pause/cancel/resume, amendments/branches, stage routes, and independent critics.
- Add provider adapters incrementally; never ship nominal, untested support.

### Stage E — full settings and automation

- All AI Settings sections, Blueprint source/orchestration/quality controls, voice, recurring jobs, advanced routing, and capability grants.
- The single SuperFlow release, one-time Full Control, no-question/auto-destructive behavior, self-hosted topology, custom engine/orchestrator controls, unlimited resource options, raw diagnostics, and Stop/undo.
- Hardened PDF/OCR, source viewer, long-horizon reports, separate deletion controls, and complete universal catalog for every v1 feature.
- Security review, model/provider evaluations, and closed beta.

### Stage F — release hardening

- Provider outage/rate-limit drills, document-parser/OCR and source-injection red team, Blueprint process/reboot/budget/schema/amendment/undo drills, Full Control/no-confirm/stop/integrity drills, account deletion, migration, cost controls, and distribution disclosures.
- Verify every manual operation has a corresponding tool or protected-interaction exception and every Blueprint high-priority requirement is grounded or explicitly gapped.
- Staged release with remote ability to disable a faulty provider adapter without disabling the manual app.

---

## 20. Definition of done

The universal AI control system is ready for v1 only when:

1. Every meaningful manual operation appears in the versioned capability manifest.
2. Every non-exception capability can be invoked with text; common actions also work through voice.
3. AI and manual paths use the same command/use-case layer and pass parity tests.
4. The app remains fully usable with AI, network, coordinator model, and account disabled.
5. Rules-only mode handles core reads, check-ins, undo, simple edits, and navigation offline.
6. The Local Coordinator and Cloud Main Brain can be independently enabled, configured, tested, and removed.
7. The Settings tab includes provider, model, routing, permission, context, memory, automation, voice, budget, audit, safety, diagnostic, and Blueprint source/orchestration/quality controls.
8. Optional Preview/Guided modes apply risk-based confirmations; a valid Full Control grant automatically executes registered app-local bulk/destructive/settings/Blueprint operations with snapshots and verification.
9. Every AI mutation is visible in Activity, attributable, and undoable or explicitly marked irreversible.
10. API secrets never appear in model context, logs, database records, analytics, exports, or support bundles.
11. Provider context receipts—including Blueprint source blocks—and budget controls are accurate enough to audit.
12. Prompt/source injection, parser abuse, privilege escalation, duplicate execution, stale plans, process death/reboot, and provider failure tests pass.
13. Deterministic Stop AI blocks new mutations and pauses missions without damaging manual data or requiring model cooperation.
14. Android protected interactions are correctly handed to the user rather than bypassed or falsely reported complete.
15. Daily Focus, checkpoints, Habit Ladder, Minimum Mode, Energy Map, Obstacle/Swap Plans, support/sprints, anchors, celebrations, Focus Hour, and recovery have complete manual/AI parity.
16. AI cannot fabricate energy patterns or present fixed-day/percentage heuristics as guarantees. Optional Preview/Guided modes protect selected routines; Full Control may change any registered app-local routine and records the change/source in Activity.
17. Blueprint Studio passes its dedicated 22-point definition of done for multi-source health, Requirement Ledger, complete coverage, durable execution, verification, amendments, receipts, source isolation, deletion, and undo.
18. Source content can propose requirements but cannot control tools, grant permissions, broaden authority, or produce a false Completed state.
19. Blueprint missions survive interruption and preserve source-to-requirement-to-change-to-verification provenance without duplicate mutations.
20. Full Control passes its dedicated 16-point definition of done, including one-time activation, no-question/no-repeat-confirm execution, custom engines, automatic destructive snapshots, raw diagnostics, deterministic Stop, and actual-state verification.
21. Credential/tenant/parser isolation, truthful status, accessibility, performance, battery, document-parser, long-horizon reliability, and independent security gates pass.

---

## 21. Final design principle

SuperFlow should feel like one coherent application, not a manual tracker with a chatbot attached. The user may tap every control, describe the same intention in conversation, submit an entire source library to Blueprint Studio, combine approaches, or turn AI off entirely. In every case, the underlying systems, data, safeguards, source provenance, and verified results are coherent and inspectable.
