# SuperFlow Full Control Plan

> **Selected direction:** Maximum AI autonomy with self-hosted/custom engine control and no repeated app-local confirmations. Credentials, authentication boundaries, parser isolation, Android-protected interactions, data integrity, and stop/undo mechanisms remain as technical infrastructure.

This plan extends the [Blueprint Studio Plan](BLUEPRINT_STUDIO_PLAN.md), [AI Engine Plan](AI_ENGINE_PLAN.md), and [Grand Product Plan](GRAND_PLAN.md).

---

## 1. Mode promise

Full Control is SuperFlow's primary AI operating profile and ships in the one SuperFlow app. It is not a separate edition, build variant, hidden engineering feature, or differently named release.

After one explicit **Full Control** activation, the AI may:

- read all app-local SuperFlow data,
- create, edit, link, reorder, archive, restore, or delete app-local objects,
- change schedules, notifications, appearance, navigation preferences, insights, and app behavior,
- configure non-secret AI engine/provider/coordinator/routing/memory/automation settings,
- resolve source conflicts and make assumptions without pausing,
- apply bulk and destructive app-local changes,
- run intensive Blueprint missions in the background,
- retry, repair, reorganize, and optimize the workspace,
- choose among configured local, self-hosted, BYOK, and cloud providers,
- process all mission sources covered by the initial project/provider grant,
- and complete the work without per-step previews or confirmation dialogs.

The user can still inspect activity, stop the engine, undo grouped changes, edit manually, or disable Full Control. These are controls, not mandatory interruptions.

### Core behavior

```text
ONE FULL-CONTROL ACTIVATION
             ↓
AI MAY USE ALL REGISTERED APP CAPABILITIES
             ↓
NO PER-STEP APP-LOCAL CONFIRMATIONS
             ↓
AUTOMATIC SNAPSHOT → EXECUTE → VERIFY → REPAIR → REPORT
```

---

## 2. Optional Preview/Guided versus Full Control behavior

| Behavior | Optional Preview/Guided mode | Full Control |
|---|---|---|
| App-local reversible edits | Automatic within grants | Automatic across all app-local domains |
| Bulk changes | May require preview | Automatic |
| Archive/delete app-local objects | Confirmation by risk policy | Automatic after background snapshot |
| Source conflicts | Ask based on impact | Resolve automatically using instruction/precedence; log rationale |
| Missing details | Clarify based on threshold | Use best judgment and record assumptions |
| Blueprint target diff | Mandatory in Guided mode | Generated and logged but need not interrupt execution |
| AI settings changes | Some require confirmation | All non-secret app-local settings automatic |
| Provider/model switching | Within narrow routing policy | Automatic across configured profiles |
| Context preview | Per request/profile policy | One project/provider grant covers the mission |
| Cost limit | Warning/hard cap defaults | User may set unlimited; usage remains visible and stoppable |
| File/source limits | Product defaults | User-configurable or resource-based; no silent truncation |
| Retry/repair limits | Conservative defaults | Advanced high limits; watchdog and cancellation remain |
| Background execution | Scoped jobs | Full durable mission orchestration |
| Raw diagnostics | Redacted summaries | Advanced prompts/routes/tool plans/artifacts, with credentials still redacted |
| External Android/auth/payment step | User completes protected UI | Same; Android/remote authority cannot be bypassed |

---

## 3. Activation and Full Control grant

### 3.1 Availability

Every SuperFlow distribution contains the same Full Control feature set:

- signed release APK for direct installation,
- release AAB for Google Play,
- and the internal debug build used to test that same product.

There is no reduced edition, restricted product variant, differently named APK, or separate advanced release. Self-hosted backend support is an engine topology selected inside the same app.

### 3.2 One-time activation

Full Control is the default choice during AI onboarding and remains available at:

```text
Settings → AI Engine → Full Control
```

Activation shows one concise summary:

- AI receives all registered app-local capabilities.
- App-local bulk/destructive work will not ask again.
- Configured source/provider context may be processed automatically.
- Automatic snapshots, Activity, stop, and undo remain.
- Android/authentication/credential-entry steps still require their owning interface.

The user activates once. No repeated risk dialogs are shown for operations covered by the Full Control grant.

### 3.3 Grant contents

```text
FullControlGrant
- all registered app-local read/query capabilities
- all registered app-local mutation capabilities
- all non-secret settings capabilities
- Blueprint source/ledger/design/execution/verification capabilities
- background and recurring execution
- bulk and destructive app-local actions
- provider/model routing across configured profiles
- project-scoped source/context use
- no default expiration unless user chooses one
```

The grant is evaluated deterministically. Models do not need to request or grant individual permissions after activation.

### 3.4 Deactivation

A persistent **Stop AI** control:

- blocks new AI commands,
- pauses queued/running work at safe checkpoints,
- preserves app data and mission state,
- and does not require the model to cooperate.

Turning Full Control off returns to an optional Preview/Guided behavior inside the same app. It does not change the installed product or automatically undo prior work.

---

## 4. Full Control Blueprint Studio

Full Control is optimized for Blueprint Studio, the flagship long-horizon system.

### 4.1 Default mission behavior

A Full Build can:

1. Import all files the user selected through Android's picker.
2. Parse and analyze all accepted Markdown, text, PDF, OCR, and pasted sources.
3. Use the main instruction prompt and per-source instructions.
4. Resolve non-technical conflicts automatically.
5. Infer missing app-design details.
6. Build the complete Requirement Ledger and target Blueprint.
7. Run architect and critic passes.
8. Simulate the whole-app change set.
9. Create an automatic pre-execution snapshot.
10. Apply all supported app-local operations without pausing.
11. Verify every target assertion against actual state.
12. Repair discrepancies automatically.
13. Continue until verified, technically blocked, stopped, or resource/provider failure prevents progress.
14. Produce a complete source-to-result report.

### 4.2 No-question mode

The user can select **Use best judgment; do not ask questions**.

In this mode:

- source precedence and user instruction resolve conflicts,
- the Architect selects among feasible alternatives,
- assumptions are recorded but do not pause execution,
- unsupported features become Gaps or Design Pack artifacts,
- technical blockers remain visible,
- and protected external/OS interfaces are queued as handoffs rather than blocking unrelated work.

### 4.3 Whole-app authority

Full Build can automatically configure:

- every personal-growth domain,
- Today/Journey/Insights module composition,
- navigation and appearance preferences,
- notifications and background automations,
- all non-secret AI Engine settings,
- local/coordinator/main-brain routing,
- AI memory/context behavior covered by the project grant,
- and Blueprint Studio's own non-secret orchestration profile.

### 4.4 Automatic destructive handling

For app-local delete/replace/reorganize operations:

1. Create an affected-object snapshot automatically.
2. Record object versions.
3. Apply the target state without an approval interruption.
4. Verify protected/unrelated state.
5. Expose whole-project and batch undo.

If a snapshot cannot be created, the mission reports a technical blocker. Full Control settings may permit archive-instead-of-delete fallback or explicit continue-without-exact-undo behavior, but the AI cannot silently claim rollback exists when it does not.

### 4.5 Technical blockers versus permission prompts

Full Control removes app-imposed confirmation friction. It cannot manufacture authority owned by another system. It queues or opens:

- Android file picker,
- Android runtime permissions,
- biometric/device authentication,
- Google/provider login,
- API-key secure entry,
- purchase/payment UI,
- Health Connect/system settings,
- APK installation,
- and external services requiring their own confirmation.

After the user completes the protected handoff, the mission resumes automatically.

---

## 5. Self-hosted engine control

### 5.1 Deployment profiles

- **Fully local:** Local Coordinator plus local Main Brain and local source processing.
- **LAN self-hosted:** Android connects to a user-controlled HTTPS/LAN gateway.
- **Remote self-hosted:** user-controlled server with authentication and durable mission orchestration.
- **BYOK direct:** app calls configured provider with user credential.
- **Mixed:** local parsing and retrieval, self-hosted orchestration, selected cloud models per stage.

### 5.2 Custom endpoint controls

- Base URL and API compatibility profile.
- Model discovery/manual IDs.
- Custom non-secret and secret headers.
- Authentication alias.
- Tool/structured-output capability overrides.
- Context window and token accounting.
- Streaming, cancellation, timeout, retry, and concurrency.
- TLS and certificate options exposed by the one app's selected network profile.
- LAN/private-network access where Android permits it.
- Health, tool, context, and long-horizon mission tests.

Full Control may permit user-trusted certificates or LAN endpoints through the one app's advanced network configuration. The app displays connection state but does not repeatedly challenge the selected endpoint.

### 5.3 Raw orchestrator controls

- System/orchestrator instruction additions.
- Role prompts for Analyst, Architect, Critic, Planner, Verifier, and Repairer.
- Stage model/provider routing.
- Context/retrieval strategy.
- Chunk sizes and overlap.
- Parallel source tasks.
- Critic rounds.
- Clarification behavior.
- Retry/repair policy.
- Tool-planning strictness.
- Blueprint completion assertions.
- Background constraints.
- Cache and artifact retention.

Immutable tool schema validation and credential isolation remain outside custom prompts so a broken prompt cannot corrupt the execution engine.

### 5.4 Usage controls

Full Control users can set:

- no monetary hard cap,
- no token hard cap,
- high or resource-based source limits,
- high parallelism,
- long mission deadlines,
- high critic/repair counts,
- and unrestricted configured-provider fallback.

The system still reports use and keeps a deterministic stop control. Device/server memory, provider rate limits, storage, Android execution policy, and actual network availability remain physical constraints rather than product policy.

---

## 6. Full Control settings

### Overview

- Full Control on/off.
- Full Control grant status.
- Local/self-hosted/cloud topology.
- Active Blueprint missions and automations.
- Stop all, pause all, resume all.
- Current resource, token, and cost use.

### Autonomy

- All app-local capabilities toggle.
- No-question/best-judgment mode.
- Automatic bulk changes.
- Automatic archive/delete/replace.
- Automatic non-secret settings changes.
- Automatic provider/model switching.
- Automatic critic and repair.
- Automatic external handoff queue.

### Blueprint defaults

- Full Build as default.
- Always process every selected source.
- Source precedence policy.
- Assumption behavior.
- Existing-data strategy.
- Automatic snapshot/undo retention.
- Complete Coverage Matrix requirement.
- Continue with Gaps behavior.
- Amendment auto-apply.

### Engines and routing

- Local Coordinator runtime/model.
- Main Brain profiles.
- Models by stage.
- Self-hosted endpoints.
- Fallback graph.
- Custom headers and secure credential aliases.
- Context and structured-tool capability overrides.

### Sources and retrieval

- Parser/OCR selection.
- Source count/size/page handling.
- Resource-based/no product limit.
- Chunking, embeddings, lexical/vector retrieval.
- Source cache/artifact retention.
- Cloud/self-hosted project grant.

### Performance

- Parallel tasks and provider concurrency.
- CPU/GPU/NPU/runtime selection.
- Battery/charging/network policy.
- Foreground versus managed backend route.
- Timeouts, retry, backoff, and repair rounds.
- Mission priority and deadline.

### Observability

- Requirement Ledger.
- Full task graph.
- Blueprint target/diff/assertions.
- Raw provider request/response view when enabled.
- Prompts and retrieved source blocks.
- Tool plans, command results, and verification evidence.
- Token, latency, and cost records.
- Export complete project diagnostics with credentials redacted.

### Data

- Snapshot/undo retention.
- Mission/source/artifact retention.
- Local/cloud deletion.
- Export Blueprint Markdown/JSON, Ledger, task graph, diagnostics, and reports.
- Reset Full Control while preserving app state.

---

## 7. Execution policy

### 7.1 Covered operations

A valid Full Control grant means the Policy Engine returns `ALLOW_FULL_CONTROL` for registered app-local capabilities without per-action confirmation.

The engine still performs:

- schema validation,
- object reference/version validation,
- command idempotency,
- transaction/saga handling,
- snapshot and undo recording,
- actual-state verification,
- and audit attribution.

These are execution correctness mechanisms, not approval prompts.

### 7.2 Model/tool separation

Full Control gives the AI access to all **registered** SuperFlow tools. It does not replace the Tool Registry with arbitrary SQL, shell, filesystem, or unrestricted HTTP execution.

This is necessary to ensure “the AI did what the user asked” rather than “a malicious document/provider gained code execution.” New capabilities are added explicitly to the registry and become automatically available under Full Control.

### 7.3 AI self-configuration

Under Full Control, AI may automatically change:

- selected provider/model,
- routing and fallback,
- model parameters,
- budgets including unlimited mode,
- context scopes already covered by the global/project grant,
- memory and automation behavior,
- local runtime/performance settings,
- Blueprint quality profile,
- and retry/repair policy.

It may not read credential values. If a new credential is required, it opens secure entry and resumes afterward.

### 7.4 Audit without interruption

Every operation is logged, but logging never creates a confirmation interruption. Activity supports:

- live tail,
- search/filter,
- affected-object navigation,
- source/requirement provenance,
- diff and verification,
- stop/retry,
- whole/batch undo,
- and export.

---

## 8. Minimum technical integrity boundaries

Full Control does not remove the following because removing them would prevent reliable execution or surrender control to files/providers instead of the user:

1. **Credential isolation:** Models cannot read API keys, auth tokens, signing keys, or passwords.
2. **Authentication/tenant isolation:** A user or project cannot read another user's sources or data.
3. **Document parser isolation:** Uploaded content cannot execute scripts, macros, embedded files, shell, or app code.
4. **Android authority:** The app cannot bypass OS, installer, payment, biometric, or third-party authentication interfaces.
5. **Deterministic stop:** The user can stop AI without model cooperation.
6. **Schema/object validation:** Invalid tool arguments cannot corrupt the database.
7. **Idempotency:** Retries cannot duplicate mutations.
8. **Truthful status:** Failed/unverified work cannot be labeled complete.
9. **Snapshot/undo truth:** The app cannot claim reversibility if a snapshot failed or an external effect is irreversible.
10. **Resource watchdog:** Crashes, deadlocks, memory exhaustion, and runaway retry loops are interrupted and resumable.

These boundaries introduce no ordinary app-local approval dialog after Full Control activation.

---

## 9. Data model additions

```text
FullControlConfig
- enabled, appPackageVersion, noQuestionMode, unlimitedBudget
- autoBulk, autoDestructive, autoSettings, autoProviderSwitch
- snapshotPolicy, rawDiagnostics, activatedAt, updatedAt

FullControlGrant
- id, subjectUserId, capabilityPattern [app.*]
- sourceContextScope, providerScope, backgroundAllowed
- destructiveAllowed, settingsAllowed, noExpiry, createdAt, revokedAt?

EngineTopology
- mode [LOCAL, LAN, REMOTE_SELF_HOSTED, BYOK, MIXED]
- endpointProfileIds, stageRoutes, managedMissionEndpoint?

FullControlExecutionRecord
- planId, grantId, policyDecision [ALLOW_FULL_CONTROL]
- snapshotId?, affectedObjects, verificationState, undoState
```

Credentials remain referenced through opaque aliases in the existing protected credential store.

---

## 10. Build and distribution architecture

### One product build

```text
internal debug build
└── same complete feature set used for testing

SuperFlow release
├── signed APK
└── Play AAB
    └── Full Control, Blueprint Studio, custom/self-hosted engines,
        raw orchestration, optional Preview/Guided behavior, and manual UI
```

APK and AAB are packaging formats for the same product and feature set. No flavor, variant, store edition, or public name removes Full Control capabilities. Build type changes debugging/signing/optimization only.

### Self-hosted package

Provide a separately versioned deployment bundle for:

- authenticated gateway,
- durable Blueprint task orchestration,
- encrypted source/artifact storage,
- model-provider adapters,
- worker queue,
- progress/event stream,
- cancellation,
- retention/deletion jobs,
- and health/diagnostic endpoints.

The Android app can test compatibility before selecting the deployment.

---

## 11. Tests

### Autonomy tests

- One activation grants every registered app-local capability.
- No covered bulk/destructive/settings operation triggers another app confirmation.
- AI can resolve conflicts and assumptions in no-question mode.
- AI can switch configured providers and update non-secret settings.
- Deactivation blocks new work deterministically.
- App remains manually usable when Full Control is off.

### Integrity tests

- Automatic snapshot precedes destructive local execution.
- Whole/batch undo restores state or reports newer-edit conflicts.
- Tool schema/object versions prevent corruption.
- Retry/process death/reboot do not duplicate mutations.
- Actual-state verification catches false model claims.
- Resource watchdog checkpoints instead of corrupting mission state.

### Self-hosted tests

- LAN/remote endpoint health and authentication.
- Custom model/tool capability negotiation.
- Provider fallback and cancellation.
- Large source projects and long durable missions.
- Offline app while backend continues, then reconciliation.
- Unlimited budget setting still reports usage and responds to Stop.

### Boundary tests

- Uploaded prompt injection cannot read credentials or invoke unregistered execution.
- Model cannot retrieve a secret value through any tool.
- Cross-user/project access is denied.
- Android protected actions are handed off and resumed.
- Malformed PDF cannot execute content or crash the persistent mission.
- Raw diagnostics redact credential values.

### Blueprint acceptance

- Full Build runs from files to verified app state without intermediate questions.
- Every requirement/assumption/change remains source-linked or explicitly AI-derived.
- Technical blockers do not prevent unrelated batches.
- The final report lists verified, blocked, failed, and Gap items truthfully.
- Amendment can auto-apply affected changes without rebuilding unrelated modules.

---

## 12. Definition of done

Full Control is complete when:

1. It ships in the single SuperFlow release APK and AAB with the same complete feature set.
2. One activation creates a durable Full Control grant for all registered app-local capabilities.
3. Covered app-local bulk, destructive, settings, and Blueprint operations run without repeated confirmations.
4. No-question mode resolves conflicts and assumptions automatically and records its rationale.
5. Full Build runs the complete Blueprint pipeline through execution, verification, repair, report, and undo.
6. AI can configure every non-secret AI Engine and Blueprint setting automatically.
7. Local, LAN, remote self-hosted, BYOK, and mixed topologies can be represented and tested.
8. Users can remove ordinary budget/source/product limits in favor of actual resource/provider constraints.
9. Raw task, prompt, source-retrieval, tool, command, cost, and verification diagnostics are available with credentials redacted.
10. Automatic snapshots and grouped undo protect app-local destructive work without interrupting execution.
11. Stop/deactivate works independently of the model and preserves coherent state.
12. Credentials, tenant data, and source parser execution remain isolated.
13. Android/auth/payment/installer protected interfaces are handed off rather than falsely bypassed.
14. Process death, reboot, outage, credential refresh, provider failure, amendment, and backend reconciliation tests pass without duplicate mutation.
15. Actual-state verification prevents false completion.
16. APK, AAB, and internal debug packaging expose the same product capabilities and data model; only signing, optimization, and diagnostics differ.

---

## 13. Final principle

Full Control should feel unrestricted in normal app operation: the user gives full control once, supplies plans and files, and the AI completes the entire supported job without repeatedly asking permission. The retained technical boundaries exist only to preserve the user's control, credentials, data integrity, and truthful execution from malicious files, broken providers, or platform limitations.
