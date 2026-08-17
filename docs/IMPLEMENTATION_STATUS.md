# SuperFlow — Implementation Status

Built from the product plans in this repository into a working, signed Android
APK. This document records honestly what is implemented, what is partial, and
what is not built, so the plans and the code can be compared without guesswork.

**Build:** `com.superflow` 1.0.0 · minSdk 26 · targetSdk 34 · ~1.1 MB ·
v2+v3 signed · no third-party runtime dependencies · 103 logic tests passing.

---

## Architecture as shipped

```
app/src/main/kotlin/com/superflow/
├── data/          Models, SQLite schema, Repo, Prefs
├── domain/        CommandBus, Capabilities (45), Serial, Insights
├── ai/            Coordinator (local), MainBrain (cloud), Agent, Snapshots
├── blueprint/     Compiler — Intent Compiler and Requirement Ledger
├── notify/        Reminders, checkpoints, boot rescheduling
└── ui/            Design system, 5 tabs, 9 activities
```

The central design decision from the Grand Plan is preserved exactly: **one
shared command bus**. Every mutation in the app — from a button tap, a
notification action, an AI tool call, or a Blueprint execution — goes through
`CommandBus.execute()`, receives the same validation, writes the same audit
entry, and gets the same undo payload. Manual/AI parity is structural, not
aspirational: there is no second code path for the AI to drift from.

---

## Implemented

### Core growth loop
- Identity → Goal → System → Habit → Check-in → Review, fully modelled and editable
- Guest-first onboarding, 8 steps, skippable, no account wall
- Habit Designer: Meaning / Notice / Want / Start / Feel + plain-language contract
- Four laws and their inversions (Build and Reduce modes)
- Habit Ladder: Tiny / Minimum / Standard / Stretch with sensible fallbacks
- Binary, count and duration tracking
- Time, place and anchor cues; habit stacking; day-of-week scheduling
- Tiny Start required and auto-derived when the AI creates a habit

### Today
- Daily Focus, capped at three, with suggest-from-habits
- Timeline bucketed Morning / Day / Evening / Anytime
- Per-card check-in at any ladder level, intentional skip, missed
- **Return today** card driven by the never-miss-twice rule
- Morning/midday/evening checkpoints, energy logging 1–5
- Plan Tomorrow, Minimum Mode with protected-routine exclusions

### Journey
- Full CRUD for identities, goals, systems, habits
- Obstacle Plans (if-then), Habit Scorecard, Flow Builder, Reviews
- Archive/restore preserving history

### Insights
- 7-day bar chart, 30-day totals, per-habit consistency
- Repetitions, current run, best run, recoveries after a miss
- Identity evidence ledger (votes per identity)
- Energy pattern **with an explicit small-sample caveat**
- Redesign suggestions for habits missed twice in a row
- Runs are computed only over scheduled days; intentional skips do not break them

### Recovery
- Return-today cards, Minimum Mode, shrink-to-tiny, recovery plans
- Reduce-mode slip handling with a professional-help pointer

### AI
- **Local Coordinator** — deterministic, offline, no network. Handles check-ins,
  skips, misses, focus, planning, energy, creation, archive/delete, obstacle
  plans, scorecard, queries and help. The app is fully controllable by text
  with every AI provider disabled.
- **Cloud Main Brain** — any OpenAI-compatible endpoint (hosted, LAN, or
  self-hosted). Provider-neutral via `HttpURLConnection`.
- **Routing** — local first, cloud for open-ended work, deterministic fallback
  on any provider failure.
- **Full Control** — one activation, then no repeated app-local confirmations,
  including destructive and multi-step work.
- **Policy Engine** — deterministic code, not the model, decides what may run.
- **Safety** — automatic snapshots before multi-step/destructive runs, full
  Activity trail, per-action undo, grouped undo, deterministic Stop,
  verification against the real database rather than model text.
- **Context Broker** — user-toggled sections, viewable context receipt.
- Budgets (including unlimited), temperature/token/timeout controls, diagnostics.

### Blueprint Studio
- Multi-source projects: pasted text, imported text/Markdown, basic PDF extraction
- Main and per-source instructions; user instructions outrank document content
- Deterministic offline requirement extraction with `file:Lnn` citations
- Requirement Ledger with 8 statuses, accept/reject, duplicate conflict detection
- **Prompt-injection isolation** — sources are data; embedded instructions that
  try to change rules, permissions or safety behaviour are detected and rejected
  as visible ledger rows rather than silently dropped
- Execution as one undoable group, after a snapshot
- **Verification against actual app state**, with gaps reported as gaps
- Coverage report, current-setup audit, Markdown Design Pack export

### Data and privacy
- Local SQLite is the source of truth; works fully offline
- JSON export/import, delete-all-data
- API keys in a separate preference file, excluded from backup, export, prompts,
  logs and context receipts
- Crash reporting off by default
- Notification permission requested only at the reminder step

### Reminders
- Per-habit reminders with Done / Tiny / Skip action buttons
- Quiet hours, total daily reminder budget, checkpoint notifications
- Rescheduled after reboot; suppressed if the habit is already handled

---

## Partial

| Area | State |
|---|---|
| PDF ingestion | Text extraction for digitally generated, Flate-compressed PDFs. Scanned PDFs yield nothing and the UI says so and asks for pasted text. No OCR. |
| Voice control | Not implemented. All AI control is text. |
| Blueprint amendments/branching | Recompiling bumps the version and replaces the ledger. No branch/merge or diff-between-versions UI. |
| Cloud refinement of the ledger | Extraction is deterministic and offline. A configured Main Brain is not yet used to re-rank or enrich requirements. |
| Localization | English only; strings are centralized but not translated. |
| Dark theme | Single warm light theme. |

## Not built

- Optional account, cloud sync, and the managed AI proxy backend
- Home-screen widget, app links, share-sheet accountability summary
- Local on-device model runtime (the Local Coordinator is rules-based, which
  the plan designates as the universal fallback)
- Android instrumentation/UI tests (no emulator was available)
- AAB packaging (requires `bundletool`, unreachable in the build environment)

---

## Test coverage

`tools/run_tests.sh` — 103 assertions, all passing:

- **LogicTest** (45) — dates across month/year boundaries, ISO day-of-week,
  scheduling masks, ladder fallbacks, contract generation, time validation
- **ParseTest** (25) — day-spec parsing, label round-trips, JSON extracted from
  fenced, prose-wrapped, nested and escaped model output
- **AiTest** (33) — 12/24-hour time parsing, anchors, places, day lists,
  injection detection, Blueprint extraction, citations, conflicts, coverage

Not covered by automated tests: Android UI behaviour, SQLite persistence, alarm
delivery, and live provider calls. These need an emulator or device.

---

## Verification

```
$ apksigner verify --verbose build/outputs/superflow-release.apk
Verifies
Verified using v2 scheme (APK Signature Scheme v2): true
Verified using v3 scheme (APK Signature Scheme v3): true

$ aapt2 dump badging build/outputs/superflow-release.apk
package: name='com.superflow' versionCode='1' versionName='1.0.0'
minSdkVersion:'26'  targetSdkVersion:'34'
launchable-activity: name='com.superflow.ui.MainActivity'
```

The APK has not been executed on a device or emulator — none was available in
the build environment. It compiles, dexes, packages and verifies correctly, and
its framework-independent logic is covered by the suites above, but first-run
behaviour on real hardware is unverified.
