# SuperFlow — Implementation Status

A full AndroidX / Material 3 Android application built from the product plans in
this repository. This document records honestly what is implemented, what is
partial, and what is not built.

**Build:** `com.superflow` 2.0.0 · minSdk 26 · targetSdk 34 · ~7.8 MB ·
v2+v3 signed · 76 AndroidX/Material libraries · 343 app classes ·
capability catalog unified across PRs #6–#10 (see "Catalogue" below) ·
logic assertions verified on a desktop JVM (JDK 17 + Kotlin 2.4.10).

---

## Architecture

```
app/src/main/kotlin/com/superflow/
├── core/
│   ├── time/        Injected clock, java.time helpers, DST-safe resolution
│   └── schedule/    Recurrence rules, Schedule, Opportunity engine
├── work/            WorkManager jobs: daily rollover, reminder refresh
├── data/
│   ├── model/       Domain models
│   ├── db/          androidx.sqlite schema, DAOs, row mappers
│   ├── Repository   Reactive repo exposing a StateFlow revision
│   └── Prefs        Settings + AI config; secrets in a separate excluded file
├── domain/          CommandBus, 45 Capabilities, Serial, Insights
├── ai/              Coordinator (local), MainBrain (cloud), Agent, Snapshots, VoiceInput
├── blueprint/       Intent Compiler, Requirement Ledger, PdfText
├── notify/          Reminders, checkpoints, boot rescheduling
├── widget/          Home-screen widget
└── ui/
    ├── common/      Design helpers, ProgressRing, BarChart, HeatmapView, HistoryStrip
    ├── today/ journey/ insights/ coach/ settings/    (five tabs, MVVM)
    ├── designer/ detail/ recovery/ scorecard/ flows/ review/
    ├── blueprint/ engine/ activity/ onboarding/
    └── sheets/      Material bottom-sheet editors
```

**Real Android architecture:** `AppCompatActivity` + `Fragment` + `ViewPager2`,
`ViewModel` per screen, `StateFlow` state, `RecyclerView` + `ListAdapter` +
`DiffUtil` for every list, coroutines for all IO, `androidx.sqlite` persistence,
`AlarmManager`/`BroadcastReceiver` for reminders, `AppWidgetProvider` for the
widget, `SpeechRecognizer` for voice.

**The core is rebuilt to the plan's domain rules.** Scheduling is a
`Recurrence` rule (daily / weekdays / specific days / every N days / N times a
week), not a weekday bitmask. Adherence, runs, recoveries and misses are all
derived from an **Opportunity series** — never stored — so the plan's rules hold
by construction:

| Plan rule | How it holds |
|---|---|
| Streaks are derived, never authoritative stored state | `Opportunities.currentRun` computes from the series each time |
| Planned skips and pause dates do not create misses | `SKIPPED_PLANNED` and `PAUSED` are excluded from the denominator and preserve runs |
| Schedule edits do not rewrite historical opportunities | Each edit bumps `scheduleVersion`; opportunities record the version that produced them |
| A habit can have no clock time | `localTime` is nullable; anchor-only habits bucket to "Anytime" |
| Date maths survives reboot, travel, DST, locale, leap days | `java.time` throughout, an injected `SuperFlowClock`, explicit `ZoneId`, DST gap/overlap resolution |
| Insights disclose sample size | `HabitStats.opportunities30` and `hasEnoughData` gate every rating |

**The spine is the shared command bus.** A button tap, a notification action, an
AI tool call and a Blueprint execution all flow through `CommandBus.execute()` —
one validation path, one audit entry, one undo mechanism. There is no second
code path for AI to drift from.

---

## UI and UX

- **Material 3 throughout** — tonal colour system, `MaterialCardView`,
  `BottomNavigationView` with active indicator, `ExtendedFloatingActionButton`
  that shrinks on scroll, chips, `MaterialSwitch`, `MaterialTimePicker`,
  `MaterialAlertDialog`, bottom sheets, `LinearProgressIndicator`, Snackbars
  with Undo.
- **Full dark theme** plus system/light/dark switching at runtime.
- **Edge-to-edge** with correct system-bar and IME inset handling.
- **Collapsing toolbars** with parallax headers and lift-on-scroll.
- **Custom animated charts** — an animated progress ring, a rounded bar chart,
  an 18-week consistency heatmap, and a compact 14-day history strip per habit.
- **Motion** — staggered list entry animations, DiffUtil item animations,
  animated chart reveals.
- **Haptics** on check-in and completion, user-disableable.
- Warm, paper-like palette; a miss uses amber, never a punitive red.

---

## Implemented

### Core growth loop
Identity → Goal → System → Habit → Check-in → Review, fully modelled and
editable; guest-first 8-step onboarding; Habit Designer with six sections
(Meaning / Notice / Want / Start / Feel / Contract) covering the four laws and
their inversions; Tiny/Minimum/Standard/Stretch ladder; binary, count and
duration tracking; time, place and anchor cues; habit stacking; day-of-week
scheduling; a required Tiny Start with automatic suggestion.

### Today
Animated progress ring; identity card with vote count; **Return today** cards
driven by the never-miss-twice rule; Daily Focus capped at three with
suggest-from-habits; per-habit check-in at any ladder level plus skip and
missed; 14-day history strip on each card; morning/midday/evening checkpoints;
energy slider; Plan Tomorrow; Minimum Mode with protected-routine exclusions.

### Journey
Full CRUD for identities, goals, systems and habits via bottom-sheet editors
with chip-based linking; Obstacle Plans; Habit Scorecard; Flow Builder;
Reviews; archive/restore preserving history.

### Insights
7-day bar chart; 30-day totals; 18-week heatmap; per-habit consistency bars;
identity evidence ledger; recoveries-after-a-miss; redesign candidates; energy
pattern **with an explicit small-sample caveat**; reduce-mode progress.

### AI
- **Local Coordinator** — deterministic, offline. The app is fully controllable
  by text with every provider disabled.
- **Cloud Main Brain** — any OpenAI-compatible endpoint (hosted, LAN, self-hosted).
- **Voice control** via `SpeechRecognizer`, routed through the same commands.
- **Full Control** — one activation, then no repeated app-local confirmations.
- **Policy Engine** — deterministic code, not the model, decides what may run.
- **Safety** — automatic snapshots, full Activity trail, per-action and grouped
  undo, deterministic Stop, verification against the real database.
- Context Broker with a viewable receipt; budgets including unlimited.

### Blueprint Studio
Multi-source ingestion (paste, text/Markdown import, PDF text extraction);
deterministic offline requirement extraction with `file:Lnn` citations;
**prompt-injection isolation** surfacing embedded instructions as visible
rejected rows; **cloud refinement** of the ledger when a provider is configured;
execution as one undoable group after a snapshot; verification against actual
app state; **amendment history with version diffing**; coverage report; setup
audit; Markdown Design Pack export.

### Background work
WorkManager runs a periodic **daily rollover** that closes out finished days
(materialising misses only where they are genuinely earned — never for paused
days, planned skips, or flexible-quota habits) and a **reminder refresh** that
re-arms alarms lost to reboot or doze. AlarmManager is kept only for exact
user-facing reminders, as the plan specifies.

### Platform integration
Home-screen widget with one-tap tiny check-in; notification actions
(Done / Tiny / Skip); quiet hours and a daily reminder budget; boot
rescheduling; app shortcuts; `superflow://` deep link; share-sheet export and
progress summary.

### Data and privacy
Local SQLite is the source of truth; JSON export/import; delete-all-data;
API keys in a separate preference file excluded from backup, export, prompts and
logs; crash reporting off by default; notification permission requested only at
the reminder step.

---

## Previously omitted, now implemented

| Feature | Status |
|---|---|
| Voice control | Done — `SpeechRecognizer`, same command path as typing |
| Home-screen widget | Done — progress, next tiny action, one-tap check-in |
| Dark theme | Done — full M3 dark palette + runtime switching |
| Share-sheet summary | Done — private text recap |
| Blueprint amendments / branching | Done — versioned ledger snapshots with diff |
| Cloud refinement of the ledger | Done — bounded, only known safe commands accepted |
| App shortcuts / deep links | Done |
| Real Material components | Done — this was the headline gap |

### Wave 0 — Data Integrity (GAP_ANALYSIS_AND_FIXES issues #30–40)

| Gap | Resolution |
|---|---|
| #31 No batch operations / transactions | Private `transaction{}` plus public `Repository.runInTransaction{}`; cascading deletes, check-in/energy upserts, reorder, Minimum Mode, focus replace, plan-tomorrow, JSON import and project delete all commit as one unit |
| #32 No indexes on foreign keys | 15 indexes added in `onCreate` and a v3→v4 migration: `goal.identityId`, `sys.goalId`, `habit.systemId/identityId/status`, `checkin.habitId`, `focus.habitId`, `obstacle.habitId`, `flowstep.flowId/habitId`, `energy.date`, `bp_source/bp_req/bp_version.projectId`, `pause.habitId` |
| #33 No orphan cleanup | `deleteIdentity`/`deleteGoal`/`deleteSystem` cascade identity→goal→system→habit→children; deleting a habit detaches referencing flow steps rather than stranding them |
| #34 `counts()` missing tables | `counts()` now reports every table via one shared `allTables` list |
| #35 `deleteAllData()` missing tables | Clears every table in child-before-parent order, atomically |
| #36 No data integrity check tool | `Repository.integrityReport()` finds orphaned references; "Check data integrity" button added to AI Engine → Diagnostics |
| #37 No DB version bump | `Schema.VERSION` 3→4 with an idempotent (`CREATE INDEX IF NOT EXISTS`) migration |
| #38 No aggregation in SQL | `checkInCounts(habitId)` (GROUP BY) and `repetitions(habitId)` aggregate in SQL |
| #39 No pagination for audit/messages | `audit(limit, offset)` and `messages(limit, offset)` accept offsets (backward-compatible defaults) |
| #40 No concurrent write protection | A reentrant `writeLock` serialises every write; multi-step writes run inside transactions |
| #30 `findHabit()` fuzzy matching | Levenshtein-based final-resort matching with a length-aware threshold (`util/Fuzzy.kt`), covered by the TextTest suite |

### Wave 1 — Search & Navigation (issues #11–16, 26)

| Gap | Resolution |
|---|---|
| #11 No search anywhere | New `SearchActivity` with a live field filtering identities, goals, systems and habits (substring + one fuzzy habit match); reachable via a search icon in both the Today and Journey toolbars. Tapping a habit opens its detail; other entities jump to Journey. |
| #12 No habit reordering | Drag-and-drop on the Today list via `ItemTouchHelper` (long-press a habit card), plus a Move up/down submenu on Journey habit rows — both routed through the existing transactional `reorder_habit` capability. |
| #13 No duplicate habit | New `duplicate_habit` capability deep-copies every design field (with a fresh id, reset order/status, and carried-over obstacle plans); "Duplicate" added to the Journey habit context menu. |
| #14 No complete-all-remaining | New `complete_all_tiny` capability marks every still-open habit today as Tiny (evening wrap-up), with a confirm dialog and grouped undo; added to the Today toolbar. |
| #15 No undo-all-today | New `undo_today` capability reverts every check-in recorded today in one transaction; added to the Today toolbar with a confirm dialog. `Repository.clearCheckInsForDate` and a `clearCheckInsForDate` undo type back it. |
| #16 No habit templates in Designer | Designer opens with a "Template" step (new habits only) offering eight pre-filled Four-Laws designs plus "Blank"; all fields remain editable. |
| #26 No pause/vacation UI | New `PauseActivity` ("Pause / vacation" in Settings → Reminders) with range chips (today/weekend/1–2 weeks), a Material date-range picker, all-habits-or-one scope, a reason, and a list of active pauses with Resume. Uses the existing `pause_habits`/`resume_habits` capabilities. |

Catalog bumped to v3 (52 capabilities).

### Wave 2 — Background Workers (issues #41–45)

| Gap | Resolution |
|---|---|
| #41 Only 2 workers exist | Added `MilestoneWorker`, `ReviewWorker`, `SnapshotCleanupWorker`, `WidgetRefreshWorker` to the existing `DailyRolloverWorker`/`ReminderRefreshWorker`; all registered in `BackgroundWork.schedule` and `cancel`. |
| #42 No snapshot cleanup | `SnapshotCleanupWorker` deletes snapshots older than 30 days and keeps at most 20 most recent, daily. |
| #43 No milestone detection worker | `MilestoneWorker` (every 12h) checks first rep, 7/21-day runs, 21/100 reps and 90% consistency per habit, notifying once per threshold (idempotent via new `WorkPrefs` marker store) on a new "Milestones" channel. |
| #44 No review generation worker | `ReviewWorker` (daily, acts on Sundays) pre-generates one weekly review draft per ISO week from the same `Insights` numbers, guarded by a per-week marker. |
| #45 Widget only refreshes on app pause | `WidgetRefreshWorker` refreshes the widget every 30 minutes (WorkManager minimum) as a backstop to the existing app-pause/check-in refreshes. |

### Wave 3 — Notifications & engagement (issues #17–21, 70–73)

| Gap | Resolution |
|---|---|
| #17 Weekly summary notification | `ReviewWorker` posts a Sunday-evening "Your week is ready to review" notification (reps + recoveries) on the new Reviews channel. |
| #18 Shareable progress card | New `share.ProgressCard` renders a warm PNG card (ring, repetitions, best run, recoveries, top habit, per-habit consistency bars) on a dependency-free Canvas and shares it via a FileProvider; "Share progress card" added to Data Management. |
| #19 App lock | New `security.AppLock` (salted SHA-256 PIN hash, never stored plaintext), `LockActivity`, `PinSetupSheet`, and an ActivityLifecycleCallbacks hook in `SuperFlowApp`; "App lock" toggle + Change PIN under Settings → Security. |
| #20 Backup to file / scheduled auto-backup | New `data.Backups` writes full JSON snapshots to app-private storage with timestamp, rotation to a cap and restore through the transactional import; `BackupWorker` runs daily / every 3 days / weekly; Data Management gains Backup now, frequency and retention controls that re-schedule the worker. |
| #21 Notification action buttons | Already present — habit reminders carry Done / Tiny / Skip action buttons (see `ReminderReceiver`). |
| #70 Quiet hours per day of week | `Prefs.quietPerDay` encoded override (Mon–Sun: default / custom window / off); `Reminders.inQuietHours` resolves the per-day window; "Quiet hours by day" picker added under Settings → Reminders. |
| #71 Notification channel for AI/proactive | Added `CHANNEL_AI` ("AI suggestions") channel. |
| #72 Notification channel for milestones | Added `CHANNEL_MILESTONES` (Wave 2) used by `MilestoneWorker`. |
| #73 Notification channel for reviews | Added `CHANNEL_REVIEWS` used by the weekly summary. |

### Polish (issues #28, #29, #65)

| Gap | Resolution |
|---|---|
| #28 Max length validation | New `util.Limits` (title 100, short text 200, description 500, note 1k, long text 5k) applied to every capability that accepts free text (identity/goal/system/habit titles, obstacle if/then, routines, notes, review fields); the Designer's `field()` adds an `InputFilter.LengthFilter` and a character counter. |
| #29 / #65 Debounce rapid taps | New `View.onDebouncedClick{}` (500 ms) applied to the habit completion circle, level chips, return-today button and check-in chips, preventing double-firing during animations. |

### Engagement & correctness

| Gap | Resolution |
|---|---|
| #45 Haptics ignore setting | `View.haptic()`/`confirmHaptic()` now read `Prefs.hapticsEnabled` when no Prefs is supplied, so every tap respects the setting. |
| #2 / #8 / #63 Proactive, non-pushy AI suggestions | New local `ai.Suggestions` engine derives one contextual nudge from existing data (return candidates, streak-at-risk, low-consistency redesign, high-consistency recognition, reduce-mode resistances, day-complete); surfaced as a dismissible card on the Today screen with a Tiny-check-in or open-habit action. No network, no push, and gentle in tone. |
| #83 Track count per check-in | `Repository.checkInCounts`/`repetitions` SQL aggregation (Wave 0); reduce-mode suggestion counts resistances this week. |
| #48 System-locale dates | All `SfTime`/`Dates` formatters already use `Locale.getDefault()`. |
| #1 Text-to-speech for AI responses | New `ai.Speech` wraps the platform `TextToSpeech` (lazy init, rate/pitch from Prefs); Coach speaks each assistant reply when TTS is enabled and stops on pause/destroy. |
| #29 Duplicate-habit warning | `create_habit` now flags an active duplicate title and returns a warning in the result. |
| #20 Backup restore UI | Data Management lists saved backups with a confirm-restore chooser and "delete oldest". |

### Remaining gap items completed

| Gap | Resolution |
|---|---|
| #3 Blueprint dumps everything | Tightened the Compiler's intent-first `looksActionable` so pasted prose/descriptive sentences do not become junk habits; only clear first-person intentions, frequency phrases, and action verbs map to requirements. |
| #4 / #8 No progressive difficulty / static ladder | New offline `domain.GrowthEngine` recommends stepping a habit's standard version up after sustained high consistency (≥85%, 14-day run) or down after repeated struggle, with a numeric increment helper; surfaced via `Suggestions` and a new `evolve_habit` capability (catalog v4). |
| #9 Reviews produce no follow-through | New `domain.ReviewActions` parses a review's "one change" into bullets, persists done-state in Prefs, shows checkboxes on past reviews, and surfaces the top open item on Today. |
| #10 Obstacle plans never surfaced | When a habit with a written if-then plan is still open, the plan is shown as the Today suggestion. |
| #22 Checkpoints show nothing when tapped | Checkpoint taps now open a guided dialog with time-of-day content (scheduled count, progress, focus, next step). |
| #23 No Plan Tomorrow flow | New `PlanTomorrowSheet` lets the user pick up to three of tomorrow's habits to pre-fill Daily Focus. |
| #24 Flows have no guided execution | Each flow card gains a "Run flow" button that walks through steps with Done (checks in a linked habit) / Skip. |
| #25 Scorecard never revisited | A monthly re-score prompt appears after 14+ days of inactivity. |
| #27 No habit graduation | `GrowthEngine`/`Suggestions` proposes retiring a habit at 95%+ consistency over a 66-day best run. |
| #63 No dynamic shortcuts | `DynamicShortcuts` publishes up to three most-used habits as long-press launcher shortcuts, refreshed on pause. |
| #64 Coach keyboard on small screens | The message list now scrolls to the latest message while the IME is open. |
| #66 No caching of computed Insights | New revision-aware `InsightsCache` (5-min TTL) backs `Insights.forHabit/allStats`. |
| #67 Corrupted JSON imports | `DataPolicy.validateImport` throws specific, actionable errors; the import flow shows them and applies via `applyImport`. |
| #68 No AI call rate limiting | The Agent enforces a sliding 10-calls-per-minute window against the Cloud Main Brain. |
| #76 "I don't know what to track" guided flow | Onboarding welcome adds an offline mood → starter-template discovery path that pre-fills identity/habit/system. |
| #77 No dark mode scheduling | Dark schedule (off / sunset–sunrise / custom hours) in Settings, applied and re-evaluated by `SuperFlowApp`. |
| #78 Multi-user support | Lightweight active-profile label + switcher (Me / Partner / Family / custom) in Settings for shared tablets. |
| #2 STT on de-Googled devices | Clearer no-recogniser message and a voice-input settings intent. |
| #5 AI never acts proactively | The local `Suggestions` engine provides proactive, non-pushy contextual nudges (see above). |
| #6 Energy data never used | Energy pattern feeds an energy-aware scheduling suggestion when evening energy is markedly lower. |
| #79 Wear OS / #80 Slices | Explicitly future scope — not built (companion app / Assistant integration). |

---

### Core Growth Systems Upgrade (Wave 1 — Data Layer Complete)

The [Core Growth Systems Upgrade Plan](CORE_GROWTH_SYSTEMS_UPGRADE_PLAN.md) data model is fully implemented:

| System | Status | New Commands |
|--------|--------|-------------|
| Identity → Living (§1) | Full: model, serialization, capabilities, evidence journal, 30-day review prompt | `evolve_identity`, `add_identity_evidence` |
| Goals → Measurable (§2) | Full: milestones, metric tracking; Journey shows progress | `add_goal_milestone`, `complete_goal_milestone`, `update_goal_metric` |
| Systems → Healthy (§3) | Full: health score in Journey/Insights/AI context, templates, capacity warning | `get_system_health`, templates via `create_system` |
| Four Laws → Living Tools (§4) | Full: ratings, living fields, Four Laws Health section in habit detail, prep reminders | `rate_reward`, `rate_reframe`, `rate_bundle`, `update_four_laws` |
| Ladder → Adaptive (§5) | Full: evolution history, ladder advice, difficulty ratings | `evolve_ladder` |
| Check-In → Rich Data (§7) | Full: context tags, quality, difficulty, amount/duration on `check_in` | `rate_checkin_difficulty`, `rate_checkin_quality` |
| Recovery → Preventive (§8) | Full: miss reasons, pattern detection, comeback celebration, preventive nudges; Recovery screen captures "why did I miss?" | `record_miss_reason`, `get_miss_patterns` |
| Reviews → Data-Driven (§9) | Full: auto pre-fill, action-item tracking, review → action pipeline | `add_review_action_item`, `complete_review_action` |
| Obstacle Plans → Surfaced (§10) | Full: usage tracking, effectiveness ratings, surfacing on Recovery | `activate_obstacle_plan`, `rate_obstacle_plan` |
| Flows → Runnable (§11) | Full: run/complete flow, step timing, completion counts | `run_flow`, `complete_flow` |
| Scorecard → Actionable (§12) | Full: convert-to-habit pipeline, re-scoring | `rescore_scorecard`, `convert_scorecard_to_habit` |
| Checkpoints → Guided (§13) | Partial: energy-aware suggestions on `log_energy` | — |
| Energy → Actionable (§14) | Full: energy-habit correlation in Insights | `get_energy_correlation` |
| Capacity Management (§15) | Full: daily load card on Today, capacity fields | `set_habit_capacity`, `get_daily_load` |
| Daily Focus → Linked (§6) | Full: priority star, carry-over | `set_focus_priority`, `carry_over_focus` |

**New capabilities added:** 28 (77 total, up from 49) — all 25 from the plan's CommandBus table plus `get_system_health`, `get_energy_correlation`, `get_miss_patterns`.

**Database:** v4 migration adds all new columns across identity, goal, sys, habit, checkin, focus, obstacle, flow, flowstep, review tables, plus the new `evidence` (identity evidence journal) table.

**Insights:** new analytics — `systemHealth`/`systemHealthAll` (§3), `dailyLoad` (§15), `missReasons` (§8), `weekdayPattern` (§8), `energyCorrelation` (§14), `ladderAdvice` (§5), `reviewData` (§9), `isRecovery` (§8), `identityReviewDue` (§1), `preventiveNudge` (§8).

**Coordinator:** natural-language patterns for the new commands ("today's load", "missed X because…", "rate the reward for X 4", "carry X to tomorrow", "star X", "i am now someone who…", "run flow X", "system health").

**Reminders:** environment-prep reminders fire the night before a morning habit (or 2h before), with a dedicated `prep` notification.

**UI:** Today load card + primary identity first; Journey system health + goal milestones; habit detail Four Laws Health + ladder advice; Recovery obstacle surfacing + miss reflection; Insights system health / miss reasons / energy correlation; Review data pre-fill + action items; Scorecard convert-to-habit; Flows run mode.

**Tests:** new GrowthTest suite covers nested-field serialization round-trips, model defaults, and the template catalog.

---

## Alpha2 upgrade

`docs/ALPHA2_UPGRADE_PLAN.md` lists 20 new features. Their status:

| # | Feature | Status |
|---|---|---|
| 1 | Global search across all entities | Done — `domain/Search.kt` (ranked, fuzzy), `search` capability, `SearchActivity`, toolbar entry on Today + Coach |
| 2 | Habit templates library | Done — 44 templates across 8 areas, `list_templates`/`apply_template`, Designer picker |
| 3 | Guided checkpoint screens | Done — `CheckpointActivity`: energy, plan, focus picker (morning); progress ring + reflection (evening) |
| 4 | Plan Tomorrow flow | Done — `PlanTomorrowActivity`: review → focus → energy forecast → confirm |
| 5 | Pause / Vacation mode | Done — Settings UI with date-range + reason; model/capability pre-existed |
| 6 | Habit graduation | Done — `Habit.graduated`, schema v4, `Graduation`, `graduate`/`ungraduate`/`upgrade`/`status` capabilities, Maintenance section, detail UI |
| 7 | Smart notification actions | Already done — Done/Tiny/Skip actions on habit reminders |
| 8 | Weekly summary notification | Done — `WeeklySummaryWorker`, `weekly_summary` channel, Settings day/time |
| 9 | App lock (PIN + biometric) | Done — `AppLockActivity` (framework `BiometricPrompt` on API 28+, PIN fallback), salted SHA-256 PIN in the secrets file, timeout settings |
| 10 | Auto-backup | Done — `BackupWorker` + schedule; data-management UI pre-existed |
| 11 | Drag-and-drop reordering | Done — `reorder_habits` capability + `ItemTouchHelper` long-press drag in Journey |
| 12 | Duplicate habit | Done — `duplicate_habit` capability + detail-screen button |
| 13 | Share progress as image | Done — `ShareCard` (1080×1350 canvas card) + FileProvider share + MediaStore save on API 29+ |
| 14 | Quiet hours per day | Done — weekday/weekend windows, fire-time enforcement |
| 15 | Notification channels (7) | Done — habits, checkpoints, reviews, milestones, ai_suggestions, weekly_summary, backup |
| 16 | Fuzzy habit search | Done — Levenshtein fallback in `Repository.findHabit` |
| 17 | Data integrity diagnostics | Done — shared `Diagnostics` + `check_integrity`/`fix_integrity` capabilities; UI delegates to it |
| 18 | RTL layout support | Already done — `supportsRtl`, start/end everywhere |
| 19 | Locale-aware date formatting | Already done — `SfTime` takes a `Locale` throughout |
| 20 | Dynamic app shortcuts | Done — `Shortcuts.update` (top-3 check-ins + Blueprint) |

Domain changes are covered by the new `Alpha2Test` suite (26 assertions). As with
the rest of the app, the APK has not been executed on a device in this
environment — the framework-independent logic is unit-tested, but runtime
behaviour of the new screens and workers is unverified.

## Partial

| Area | State |
|---|---|
| Room | Uses `androidx.sqlite` (Room's own support layer) with hand-written DAOs and real versioned migrations (v2→v3 converts `daysMask` to recurrence rules; v3→v4 adds foreign-key/query indexes). Room's annotation processor is unavailable offline; the runtime contract is identical, but there is no compile-time query verification. |
| DataStore | Linked and available; settings still read through `Prefs` (SharedPreferences) with a StateFlow change feed. Migrating the backing store is a mechanical follow-up. |
| PDF ingestion | Text extraction for digitally generated, Flate-compressed PDFs including hex strings. Scanned PDFs yield nothing and the UI says so. No OCR. |
| Dynamic colour | Material You wallpaper extraction is not wired up; the app ships a fixed brand palette in light and dark. |
| Localization | English only; strings are fully externalized and ready to translate. |
| Navigation component | Uses `ViewPager2` + explicit Activities rather than a nav graph. |

## Not built

- Optional account, cloud sync, managed AI proxy backend
- On-device model runtime (the Local Coordinator is rules-based, which the plan
  designates as the universal fallback)
- Android instrumentation / UI tests — no emulator was available
- AAB packaging — requires `bundletool`, unreachable in this environment

---

## Verification

```
$ apksigner verify --verbose build/outputs/superflow-release.apk
Verifies
Verified using v2 scheme (APK Signature Scheme v2): true
Verified using v3 scheme (APK Signature Scheme v3): true

$ aapt2 dump badging build/outputs/superflow-release.apk
package: name='com.superflow' versionCode='2' versionName='2.0.0'
minSdkVersion:'26'  targetSdkVersion:'34'
```

Present in the dex, confirmed by inspection: `MaterialCardView`,
`BottomNavigationView`, `BottomSheetDialogFragment`, `ListAdapter`,
`ViewPager2`, `ViewModel`, `MutableStateFlow`, `SupportSQLiteDatabase`,
`ConstraintLayout`, `LottieAnimationView`, and all 311 SuperFlow classes.

**The APK has not been executed on a device or emulator** — none was available.
It compiles, links, dexes, packages and verifies, and its framework-independent
logic is covered by the test suites, but runtime behaviour on real hardware is
unverified. That is the first thing to check on a device.
