# SuperFlow Alpha3 — Plan B: Functional Grand Expansion

**Version:** alpha3 · August 2026
**Companion document:** [ALPHA3_VISUAL_PLAN.md](ALPHA3_VISUAL_PLAN.md) — *Plan A: Visual & Experience Redesign*
**Coordination contract:** §13 (Shared Milestones) and §14 (Feature-Surface Matrix) bind the two plans together. They are designed to be executed **simultaneously**: every feature below names the visual surfaces (from Plan A) it must ship with, and every Plan A system has at least one feature consumer defined here. No feature merges without its surface; no surface merges without a consumer.

---

## Table of Contents

1. [Vision: "From Tracker to Coach"](#1-vision-from-tracker-to-coach)
2. [Current Capability Inventory](#2-current-capability-inventory)
3. [F1 · Quick Capture, Inbox & Focus Engine](#3-f1--quick-capture-inbox--focus-engine)
4. [F2 · Blueprint Studio Upgrades & Day Replay](#4-f2--blueprint-studio-upgrades--day-replay)
5. [F3 · Energy-Aware Days & Minimum Mode 2](#5-f3--energy-aware-days--minimum-mode-2)
6. [F4 · AI Memory Viewer & Conversation Continuity](#6-f4--ai-memory-viewer--conversation-continuity)
7. [F5 · Gesture Check-ins, Personalization & Flex Scheduling](#7-f5--gesture-check-ins-personalization--flex-scheduling)
8. [F6 · Advanced Analytics Pack](#8-f6--advanced-analytics-pack)
9. [F7 · Adaptive Coaching Nudges & Smart Notifications 2](#9-f7--adaptive-coaching-nudges--smart-notifications-2)
10. [F8 · Commitment Sprints](#10-f8--commitment-sprints)
11. [F9 · Graduation Ceremony & Identity Milestones](#11-f9--graduation-ceremony--identity-milestones)
12. [F10 · Whole-App Optimization: Every Existing Capability Redesigned](#12-f10--whole-app-optimization-every-existing-capability-redesigned)
13. [Shared Milestones (with Plan A)](#13-shared-milestones-with-plan-a)
14. [Feature-Surface Matrix](#14-feature-surface-matrix)
15. [Data & Schema Changes](#15-data--schema-changes)
16. [AI & Domain Logic Changes](#16-ai--domain-logic-changes)
17. [Privacy, Safety & Full Control Alignment](#17-privacy-safety--full-control-alignment)
18. [Testing Strategy](#18-testing-strategy)
19. [Quality Gates & Acceptance Criteria](#19-quality-gates--acceptance-criteria)

---

## 1. Vision: "From Tracker to Coach"

Alpha2 made SuperFlow a *system shaper*: identities, goals, habits, reviews, obstacle plans, an AI that can run the whole app under Full Control.

**Alpha3's functional thesis:** the app should increasingly answer three questions *before the user asks them*:

1. **"What now?"** → the Focus engine picks the single best next action from everything the user could do.
2. **"How am I really doing?"** → analytics that reveal patterns (time-of-day, energy correlation, recovery curves) instead of raw counts.
3. **"Can I trust myself with this?"** → commitment structures (sprints) and honest closure rituals (graduation) that convert open-ended streaks into finished chapters.

### Functional principles (binding)

| # | Principle | Meaning |
|---|-----------|---------|
| F-P1 | **Suggest, never nag** | Every proactive feature has a frequency budget and a master off switch |
| F-P2 | **Local-first always** | New features work fully offline; AI features degrade gracefully without a model |
| F-P3 | **Same rules for AI and hands** | Every new capability is registered in `CommandBus.kt` so Full Control can execute exactly what the UI can |
| F-P4 | **Undoable by default** | Any bulk or destructive action routes through snapshots (`ai/Snapshots.kt`) |
| F-P5 | **Honest metrics** | Analytics show uncertainty; no vanity extrapolations |

---

## 2. Current Capability Inventory

What exists today (and therefore what alpha3 builds *on*):

- **Core loop:** habits with levels/tiny versions, check-ins (tiny/minimum/standard/stretch/skip), checkpoints, Daily Focus items
- **Structure:** identities + evidence, goals + milestones, systems, obstacle plans, routines, environment design
- **Reflection:** weekly/monthly/quarterly reviews + action items, journal, scorecard, energy logs
- **Growth engine:** growth plans/phases, auto-review, graduation logic, habit templates, milestones
- **AI:** MainBrain coordinator, agent, suggestions, voice input v2, TTS, snapshots, AI memory (`AiMemory`), proactive suggestion model, simulations, progressive plans
- **Blueprint Studio:** source-linked requirement ledger, compiler v2, verification, undo paths
- **Ops:** backups, activity log, audit trail, app lock, dynamic shortcuts, smart notifications, reminders

**Gaps alpha3 closes:** no inbox/capture flow, no time-of-day pattern analysis, no replay of a past day, no memory transparency UI, no sprint/commitment structure, no celebration of habit "graduations", nudges are static reminders rather than adaptive, gestures are limited, charts lack correlation views.

---

## 3. F1 · Quick Capture, Inbox & Focus Engine

The biggest daily-loop upgrade. Ships with Plan A's Command Palette, Focus card, and entity rows (Plan A §10.11, §11.1).

### F1.1 Quick Capture

- Capture from anywhere in the app via Command Palette ("capture …"), share-sheet target (share text/URL into SuperFlow), widget long-press, and voice.
- Captured item types auto-detected: idea, journal snippet, habit candidate, goal candidate, obstacle worry.
- Inbox lives as a slim strip on Today ("3 captured thoughts") opening an Inbox sheet: triage each item → convert to entity / discard / keep as note.
- Items older than 14 days get one gentle nudge, then auto-archive (never deleted).

```kotlin
// data/model addition
data class CapturedItem(
    val id: String,
    val text: String,
    val kind: CaptureKind,          // IDEA, HABIT_CANDIDATE, GOAL_CANDIDATE, WORRY, NOTE
    val createdAt: Long,
    val source: CaptureSource,      // PALETTE, SHARE_SHEET, WIDGET, VOICE
    val state: CaptureState,        // OPEN, CONVERTED, DISCARDED, ARCHIVED
    val convertedToId: String? = null,
)
```

### F1.2 Focus Engine

Replaces the passive Daily Focus list with a ranked single recommendation:

```
score = urgency(dueToday, overdueStreakRisk)
      x momentum(recent completion rate on this habit)
      x capacity(match against today's logged energy)
      x identityWeight(habit-to-identity link strength)
      - fatiguePenalty(already many check-ins today)
```

- The top-ranked item becomes the Today Focus card (Plan A §11.1); ranks 2–4 appear as compact "also suggested" rows.
- Dismissal with reason ("not now", "too much today") feeds back into per-user scoring weights persisted in prefs.
- Fully deterministic offline logic in new `domain/FocusEngine.kt` — AI is optional sugar, never required.

---

## 4. F2 · Blueprint Studio Upgrades & Day Replay

Ships with Studio night aesthetic, blueprint run visuals, and SfTimeline (Plan A §11.4, §10.12).

### F2.1 Blueprint Studio upgrades

- **Resume interrupted runs:** compiler state checkpointed after every phase; a run can resume after process death. New run states: RUNNING, PAUSED, INTERRUPTED, RESUMABLE, FAILED, VERIFIED.
- **Run diff preview:** before execution, show a structured plan diff (entities to create/modify/delete) with per-item undo chips; Full Control still executes without confirmation but the diff is always recorded in the audit trail.
- **Multi-source merge quality:** conflict reconciliation now cites both sources (page/line) inline in the ledger; unresolved conflicts become explicit "decisions" the user can settle later.
- **Blueprint templates:** save a completed workspace design as a reusable template (export/import as a single JSON file).

### F2.2 Day Replay

A scrubbable reconstruction of any past day from existing data (check-ins, journal, energy, focus items, snapshots, activity log):

- New `domain/DayReplay.kt` builds an immutable `DayTimeline` (events with timestamps, kinds, references).
- UI: SfTimeline with a time scrubber; scrubbing highlights the active habit period, energy level, and what was checked in when.
- Purpose: honest reflection ("I keep skipping evening routines") — feeds F6 correlation analytics.
- Entry points: Insights header, Journal day chip, Activity Log.

---

## 5. F3 · Energy-Aware Days & Minimum Mode 2

Ships with energy chip on greeting, gentle-state colors, Essentials rendering (Plan A §11.1, §4.4).

### F3.1 Energy-aware planning

- Energy logs already exist; alpha3 closes the loop: morning Today shows yesterday's energy pattern and suggests today's habit load ("Light day — 3 essentials suggested").
- The Focus Engine consumes live energy (F1.2); routine steps above the user's logged capacity auto-defer to Anytime.
- Weekly review gains an "energy vs completion" mini-chart (F6 chart kit).

### F3.2 Minimum Mode 2

- One-tap entry from Today (and from a notification action); whole app switches to Essentials rendering (Plan A).
- Essentials = habits flagged essential + one recovery suggestion; everything else visually parked, not hidden-shamed.
- Auto-exit next morning with a gentle summary of what was maintained, never what was missed.

---

## 6. F4 · AI Memory Viewer & Conversation Continuity

Ships with SfTimeline + entity detail sheet styling (Plan A §10.12).

- `AiMemory` exists but is invisible to users. Alpha3 adds a Memory viewer: all AI memories listed by category, each with source conversation link, edit/delete, and "forget" per item or per category.
- Conversation continuity: new conversations optionally load relevant memories (top-k by recency + category match); the AI states which memories it used ("Remembering: you prefer morning workouts").
- Memory creation is transparent: every write to memory is logged in the audit trail like any other command.
- Registered in CommandBus so Full Control can manage memory exactly as the UI can.

---

## 7. F5 · Gesture Check-ins, Personalization & Flex Scheduling

Ships with swipe affordances, check-in bloom, LifeArea color rails (Plan A §9.2, §7.3, §4.5).

### F5.1 Gesture layer

- Swipe right on habit row = complete (default check-in level); swipe left = quick editor sheet; long-press = reorder + context menu. All duplicated as buttons (a11y).
- One-time swipe-peek education on first use (Plan A SfSwipeHintRow behavior).

### F5.2 Habit personalization metadata

```kotlin
// added to Habit
val colorOverride: Int? = null,     // LifeArea-derived hue override
val essential: Boolean = false,     // Minimum Mode inclusion
val flexDays: Int = 0,              // weekly flexibility budget
val quietHours: TimeRange? = null,  // no reminders inside this window
```

### F5.3 Flex scheduling

- Each habit gets a weekly flexibility budget (e.g., 5-of-7). The streak engine counts weeks meeting the target instead of unbroken chains — recovery-friendly without abandoning streaks for users who want them.
- Missed-day handling upgrade: first miss offers three paths in one sheet — do tiny now / reschedule within flex budget / intentional rest (logged, no penalty).

---

## 8. F6 · Advanced Analytics Pack

Ships with chart kit v3 (stacked area, radial rings), StatHero (Plan A §12, §10.13).

New analyses in extended `domain/Insights.kt`, all computed locally and cached via `InsightsCache`:

| Analysis | Question it answers | Chart |
|----------|--------------------|-------|
| Time-of-day patterns | When do I actually succeed? | Stacked area by daypart |
| Energy correlation | Do low-energy days actually hurt? | Scatter + trend line, r displayed honestly |
| Recovery curve | How fast do I bounce back after a miss? | Bar histogram of miss→next-check-in gaps |
| Consistency bands | Am I stable or feast-famine? | Weekly band chart (p25–p75 shading) |
| Habit stacking health | Which anchors reliably trigger which habits? | Radial multi-ring |
| Momentum index | Composite weekly trajectory | Sparkline in StatHero |

Rules: minimum data thresholds before an analysis appears (no insights from 3 days of data); every claim includes its sample size; no future predictions beyond simple trend ranges.

---

## 9. F7 · Adaptive Coaching Nudges & Smart Notifications 2

Ships with notification styling and SfNudgeBanner (Plan A §13, §10.16).

- Extends `SmartNotifications.kt`: nudges become state-aware — triggered by context (streak risk given *this* habit's history, overdue goal milestone, unused review, inbox aging), not fixed times.
- Frequency budget: max N nudges/day (default 2), global quiet hours, master off switch, and a visible "why did I get this?" explanation on every nudge.
- In-app counterpart: SfNudgeBanner surfaces the same intelligence inside Today instead of only via notifications.
- All nudge logic is deterministic domain code (`domain/Nudges.kt`); AI may draft copy, never decides timing.

---

## 10. F8 · Commitment Sprints

Ships with sprint board screen, large Breath Ring, countdown numerals, sprint widget (Plan A §10.3, §5.1, §13).

The `Sprint`/`CommitmentSprint` models exist but are unused surface-wise. Alpha3 makes them a first-class feature:

- **Create sprint:** pick 1–3 habits, duration (7/14/21/30 days), daily target, one stake statement written in your own words (serif rendering).
- **Sprint board:** current sprint hero card (large Breath Ring, day X of Y), per-habit progress rails, past sprints list with outcomes.
- **Integrity mechanics:** pausing a sprint is allowed once (with reason); abandoning records an honest outcome — sprints end in COMPLETED, MAINTAINED, or RELEASED (never "failed").
- **Completion:** triggers medium celebration + optional review prompt asking what made it work → offered as evidence toward linked identity.
- Widget: 2×2 sprint countdown with tap-to-check-in.

---

## 11. F9 · Graduation Ceremony & Identity Milestones

Ships with grand celebration (aurora wash), certificate layout, serif voice, chime (Plan A §8.3, §5.2).

- Graduation logic exists (`domain/Graduation.kt`); alpha3 gives it the ritual it deserves:
- **Trigger:** habit reaches sustained mastery criteria (configurable: e.g., 90% consistency over 60+ days) or user declares "this is just who I am now".
- **Ceremony screen:** full-screen ink-wash reveal, identity statement in serif display type, stats recap (days practiced, total reps, longest flow), option to archive the habit into an "Alumni" shelf (visible in Journey, out of Today).
- **Certificate export:** palette-matched shareable image (extends share/ProgressCard).
- **Identity milestones:** identities accumulate graduated habits; Journey shows each identity's "chapters" — graduated habits as closed chapters with dates.

---

## 12. F10 · Whole-App Optimization: Every Existing Capability Redesigned

Alpha3 is not only additive. **Every capability that already exists gets an explicit optimization + redesign pass**, executed in the same milestones as its Plan A visual treatment. Nothing ships in alpha3 untouched.

### F10.1 Core loop engine (check-ins, checkpoints, levels)

- Check-in write path batched and debounced; today-list query rewritten against covering indexes (target: <5ms for 100 habits).
- Checkpoint flow reduced from multi-screen to single-screen with inline level changes; skip reasons become one-tap chips.
- Level progression logic unified into `domain/` (removing UI-side duplication found in TodayAdapter paths) — one source of truth, unit-tested.
- Undo for check-ins within same session via grouped undo (CommandBus), not just edit-overwrite.

### F10.2 Reviews & reflection

- Weekly/monthly/quarterly reviews share one engine with per-kind question sets (currently three near-duplicate flows).
- Review answers prefill from data: auto-generated "highlights of the week" (from F2 Day Replay + F6 analytics) so users reflect on facts, not memory.
- Action items get owners/due states and surface on Today until resolved (closing the review→action gap).

### F10.3 Journal, Scorecard, Energy

- Journal: full-text search via FTS table (currently LIKE scans), entry linking to habits/identities (`[[habit]]` autocomplete), day-chip navigation into Day Replay.
- Scorecard: computed incrementally on check-in rather than full recalculation on open; opens instantly regardless of history size.
- Energy logs: one-tap logging from Today greeting chip (was buried), weekly energy-vs-completion chart added to Insights.

### F10.4 Recovery, Pause & gentle flows

- Recovery flow merges with F5.3 miss-handling sheet (one recovery experience, not two).
- Pause windows gain optional auto-resume summaries ("what was maintained during your pause") instead of silent return.
- All gentle flows copy-reviewed against Evidence-over-Judgment tone guide (no red, no shame language).

### F10.5 Search & navigation infrastructure

- Search moved onto SQLite FTS5 with indexed per-entity tables; results ranked by relevance + recency; sub-50ms on 10k entries.
- Deep links formalized for every entity (`superflow://habit/{id}` etc.) powering shortcuts, widgets, notifications, and AI actions uniformly.
- Dynamic shortcuts regenerated from Focus Engine top items instead of static config.

### F10.6 Routines, Templates & Habit Designer

- Routine builder: drag-reorder with autosave, step timing hints, and "start routine" guided mode with per-step check-off.
- Habit templates: curated starter packs filtered by LifeArea; template preview shows exactly which entities will be created before applying.
- Designer form restructured into cue→action→reward narrative order with inline examples (reduces blank-field paralysis).

### F10.7 Growth Engine & Graduation logic

- Growth plan phases recalculate lazily + cached (InsightsCache pattern extended); upgrade decisions explainable ("phase advanced because…").
- AutoReview frequency made adaptive (based on user responsiveness) instead of fixed cadence.
- Graduation criteria surfaced transparently in Habit Detail ("on track: 84% over last 45 days, needs 90%").

### F10.8 Blueprint Studio reliability

- Compiler v2 hardening: idempotent phase execution (safe retry), deterministic ordering, snapshot before each mutating phase.
- Source extraction: PDF text pipeline cached per document hash; large-document progress reporting.
- Verification pass produces machine-readable result consumed by run UI (Plan A §11.4).

### F10.9 AI coordinator, voice & suggestions

- Coordinator request queue with priority + cancellation; no overlapping agent runs; token/context budget enforced per turn.
- Voice input v2 error states redesigned: partial transcription shown with tap-to-fix; offline fallback message honest about limits.
- Suggestions deduplicated across sources (Suggestions.kt vs GrowthEngine vs new Nudges.kt) through a single suggestion ledger with suppression memory.

### F10.10 Notifications, reminders & shortcuts

- Reminder scheduling migrated to exact-alarm policy-compliant path with graceful inexact fallback + status surfacing in Settings.
- Notification actions (check-in/snooze) execute through CommandBus so they're identical to in-app actions and undoable.
- Smart notification channels consolidated; per-habit reminder inherits quiet hours (F5.2).

### F10.11 Data management, backups & security

- Backups: incremental + integrity checksums; restore preview (what will change) before applying; automatic local backup ring (7 daily).
- Activity log: filterable by command type/entity, entries link to affected entities, retention configurable.
- App lock: biometric-first with PIN fallback (existing), now also guarding Inbox/Memory/Sprint surfaces; privacy veil gesture from Plan A §9.2.
- DataPolicy page rewritten as plain-language data map: what exists, where, how to export/delete each kind.

### F10.12 App-wide engineering optimization

| Area | Optimization | Target |
|------|--------------|--------|
| Startup | Lazy domain init, baseline profile, deferred non-critical work | Cold start ≤900ms |
| Database | Index audit, WAL tuning, query plan checks in CI | p95 query <10ms |
| Memory | Image/bitmap reuse in charts+heatmap, list recycling audit | No growth over 30-min session |
| Battery | JobScheduler batching for nudges/backups/caches | ≤1 wakeup/hour idle |
| APK size | R8 full mode, resource shrinking, font subsetting | Net-neutral or smaller vs alpha2.5 |
| Reliability | Crash-free session target, uncaught-handler with local report | ≥99.7% crash-free |
| Code health | Detekt clean, dead code sweep (VoiceInput v1 etc.), module boundary rules | Zero known-duplicated logic |

Each row lands in M5 (app-wide hardening) with per-module passes distributed through M1–M4 alongside their redesigns.

### F10.13 Experience Hub & Settings Consolidation

- Settings restructured into: Appearance · Experience (motion/sound/haptics/nudges) · AI & Control · Data & Privacy · About — every alpha3 preference finds one home here.
- New-user "Tune your app" flow after onboarding: five quick choices (palette, motion level, nudge budget, density, essential habits) writing real prefs.
- Every setting applies live; search over settings entries; reset-per-section with undo.

---

## 13. Shared Milestones (with Plan A)

Mirror of Plan A §14 — identical milestone table, functional side. F10 optimization passes ride along per module:

| Milestone | Functional (this plan) | Visual (Plan A) | Exit criteria |
|-----------|------------------------|-----------------|---------------|
| **M0 · Foundation** (wk 1–2) | CapturedItem/Sprint schema, Timeline store, memory indexing, CommandBus registrations | Tokens v3, materials, motion specs | Migrations pass; schema tests green |
| **M1 · Core Loop** (wk 3–5) | F1 Quick Capture + Focus Engine, F3 energy loop, F5 gestures/flex; F10.1 core-loop engine + F10.5 search/FTS + deep links | Today redesign, SfHabitCard v3, command palette | Daily loop demoable end-to-end |
| **M2 · Insight** (wk 6–7) | F2 Day Replay, F6 analytics pack; F10.2 reviews engine, F10.3 journal/scorecard/energy | Insights redesign, chart kit v3, SfTimeline | Replay + 6 analyses shipped behind flags |
| **M3 · Intelligence** (wk 8–9) | F2 Blueprint resume/diff/templates, F4 Memory viewer, F7 adaptive nudges; F10.6–F10.10 module passes (routines, growth, blueprint reliability, AI coordinator, notifications) | Studio night theme, nudge banners | Memory transparency complete; nudges state-aware |
| **M4 · Commitment** (wk 8–11) | F8 Sprints lifecycle, F9 Graduation ceremony; F10.4 recovery/pause merge, F10.7 graduation transparency | Sprint board, celebration system | Full sprint + graduation flows usable |
| **M5 · Polish & Ship** (wk 12) | F10.11 data/security passes, F10.12 app-wide hardening, Experience hub wiring, QA, docs | Widgets, splash, icons, audits | Quality gates green; versionName=alpha3 |

Sequencing rule (both plans): features and their named surfaces merge in the same milestone; feature flags allow staged enablement but never split a pair across releases.

---

## 14. Feature-Surface Matrix

Functional side of the contract (mirror of Plan A §15):

| Feature | Requires from Plan A | Ships in |
|---------|----------------------|----------|
| F1 Capture/Inbox/Focus | SfCommandPalette, Focus card hero, SfEntityRow | M1 |
| F2 Blueprint + Day Replay | Studio night theme, SfTimeline, run visuals | M2/M3 |
| F3 Energy + Minimum Mode 2 | Energy chip, gentle colors, Essentials rendering | M1 |
| F4 Memory viewer | SfTimeline, detail sheet styling | M3 |
| F5 Gestures/personalization/flex | Swipe grammar, bloom, color rails | M1 |
| F6 Analytics | Chart kit v3, StatHero, segmented control | M2 |
| F7 Adaptive nudges | Notification styling, SfNudgeBanner | M3 |
| F8 Sprints | Sprint board, large Breath Ring, widget | M4 |
| F9 Graduation | Grand celebration, serif voice, certificate template | M4 |
| F10 Whole-app optimization (F10.1–F10.12) | Every existing surface's Plan A material/motion pass; SfScreenScaffold adoption | M1–M5 (per-module, table in §12) |
| F10.13 Experience hub | Settings live-preview pickers, sound mixer | M5 |

---

## 15. Data & Schema Changes

Backwards-compatible additions (existing databases migrate forward automatically; `DatabaseSchemaTest` extended):

1. `captured_items` table (F1)
2. `day_timeline_events` materialized view/table (F2) — rebuilt lazily from sources
3. Habit columns: colorOverride, essential, flexDays, quietHours (F5)
4. Sprint tables activated + `sprint_outcome` enum extension: RELEASED (F8)
5. Graduation records table: date, criteria snapshot, certificate ref (F9)
6. Nudge ledger: sent nudges with reason codes, for frequency budgets (F7)
7. Prefs additions: motionLevel, soundMix, hapticIntensity, livingAccent, focusWeights, nudgeBudget

All changes ship with: migration test, backup/restore round-trip test, and DataPolicy review (nothing leaves the device).

---

## 16. AI & Domain Logic Changes

- New domain modules (pure Kotlin, unit-testable): `FocusEngine.kt`, `DayReplay.kt`, `Nudges.kt`, `Analytics.kt` (or extensions of Insights.kt)
- `Coordinator.kt`/`Agent.kt`: new intents registered — capture triage assistance, sprint retrospective drafting, graduation reflection prompts. All optional enhancements over deterministic defaults
- `Suggestions.kt` upgraded to consume Focus Engine output rather than its own ad-hoc ranking
- Every new capability registered in `CommandBus.kt` with grouped undo support (F-P3)
- Model catalog unchanged; no new model requirements — alpha3 must work identically with zero AI configured

---

## 17. Privacy, Safety & Full Control Alignment

- No new permissions except share-sheet intent receipt (F1) — no location, no contacts, no account
- All new data local-only; backups already cover exports, extended for new tables
- Nudge explanations are mandatory (visible reasoning), satisfying Calm-is-a-feature principle
- Full Control scope grows to include: capture triage, sprint management, memory management, blueprint resume — each with snapshot + grouped undo
- App lock covers new surfaces (Inbox, Memory viewer, Sprint board)

---

## 18. Testing Strategy

- Unit: FocusEngine scoring (property-based: weights produce stable rankings under permutation), Flex streak math, analytics correctness on fixture datasets, nudge budget enforcement, F10.1 unified level-progression logic, FTS query correctness
- Instrumented (extend existing suite): FirstLaunchFlowTest extended for onboarding capture step; ReminderSchedulingTest extended for adaptive nudges + exact-alarm fallback; DatabaseSchemaTest for all §15 migrations; new SprintLifecycleTest, GraduationFlowTest, DayReplayTest, BackupRestoreRoundTripTest
- Performance tests: startup macrobenchmark, today-list query benchmark, 30-min memory session test (F10.12 targets enforced in CI where emulator allows)
- Manual scripts: gesture discoverability, Minimum Mode round-trip, sprint abandon/release honesty paths, restore-preview flow, biometric lock on new surfaces

---

## 19. Quality Gates & Acceptance Criteria

1. Every feature F1–F9 demoable offline with AI disabled entirely
2. Every feature reachable and operable via Full Control commands (parity check script)
3. Zero nag violations: nudge frequency budgets enforced by unit test
4. All migrations tested up/down against alpha2.5 databases; backup/restore round-trip green for every new table
5. Existing behaviors regress-free: reviews, checkpoints, Blueprint runs, backups — plus each F10.1–F10.13 pass verified by its own before/after check (query timing, flow step count, etc.)
6. Feature-surface matrix (§14) fully checked — no feature ships without its visual surface; **no existing capability ships without its F10 optimization pass**
7. F10.12 engineering budgets met: startup ≤900ms, p95 query <10ms, ≥99.7% crash-free, APK net-neutral or smaller
8. Docs updated (README feature list, BUILD notes) and versionName=alpha3 / versionCode=5

---

*This document is the functional half of alpha3. Read together with [ALPHA3_VISUAL_PLAN.md](ALPHA3_VISUAL_PLAN.md); conflicts resolve in favor of the matrix in §14.*
