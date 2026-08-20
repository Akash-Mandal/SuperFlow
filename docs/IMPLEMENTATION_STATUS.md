# SuperFlow — Implementation Status

A full AndroidX / Material 3 Android application built from the product plans in
this repository. This document records honestly what is implemented, what is
partial, and what is not built.

**Build:** `com.superflow` 2.0.0 · minSdk 26 · targetSdk 34 · ~7.8 MB ·
v2+v3 signed · 76 AndroidX/Material libraries · 108 Kotlin source files ·
141 resource files · 49 capabilities · **3972 logic assertions passing**.

> **Build environment caveat (current):** Google Maven is unreachable from
> this environment, so the AAR set cannot be fetched and **no APK can be
> produced right now**. The Kotlin, XML, resource and `design/` layers are
> fully verified by the offline suites; the Compose layer under `ui/theme`,
> `ui/components` and `ui/screens` is **written but has never been
> compiled**. Because of that, `design/Rendering` keeps Today, Journey and
> Insights on their View implementations - which have been exercised - and
> the Compose versions are one constant away from being live. See
> "UI/UX upgrade" below.

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
├── domain/          CommandBus, 49 Capabilities, Serial, Insights, mappers
├── ai/              Coordinator (local), MainBrain (cloud), Agent, Snapshots, VoiceInput
├── blueprint/       Intent Compiler, Requirement Ledger, PdfText
├── notify/          Reminders, checkpoints, boot rescheduling
├── widget/          Home-screen widget
├── design/          Pure UI logic: tokens, palettes, roles, geometry,
│                    navigation, accessibility, sound, widget and icon
│                    variants. No Android imports, no R - all of it testable.
└── ui/
    ├── theme/       Compose: SfTheme, palettes, typography, shapes, motion
    ├── components/  Compose: cards, chips, fields, charts, skeletons, rings
    ├── screens/     Compose: Today, Journey, Insights, Studio, Onboarding
    ├── common/      Design helpers, ProgressRing, BarChart, HeatmapView,
    │                HistoryStrip, ComposeHost, SfHaptics, SfSound
    ├── today/ journey/ insights/ studio/ settings/   (four tabs, MVVM)
    ├── designer/ detail/ recovery/ scorecard/ flows/ review/
    ├── blueprint/ engine/ activity/ onboarding/
    └── sheets/      Material bottom-sheet editors
```

**The `design/` package is the load-bearing idea.** Any UI decision that can
be written as a pure function of its inputs lives there: which tone fills a
role, how a chart's axis is chosen, what a widget says at 8am with two of five
habits done, which launcher alias to enable. It imports nothing from Android
and references no `R`, so it runs on a desktop JVM - which is why 3063 of the
suite's 3972 assertions are about the user interface. Rendering code in `ui/`
is then only rendering, and both the View and Compose layers read the same
answers.

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

---

## Partial

| Area | State |
|---|---|
| Room | Uses `androidx.sqlite` (Room's own support layer) with hand-written DAOs and a real versioned migration (v2→v3 converts `daysMask` to recurrence rules). Room's annotation processor is unavailable offline; the runtime contract is identical, but there is no compile-time query verification. |
| DataStore | Linked and available; settings still read through `Prefs` (SharedPreferences) with a StateFlow change feed. Migrating the backing store is a mechanical follow-up. |
| PDF ingestion | Text extraction for digitally generated, Flate-compressed PDFs including hex strings. Scanned PDFs yield nothing and the UI says so. No OCR. |
| Dynamic colour | Resolved and applied in the View layer (`SfTheme.apply`, Android 12+), and honoured only on the default palette so an explicitly chosen palette outranks the wallpaper. Untested on a device, since none was available. |
| Localization | English only; strings are fully externalized and ready to translate. |
| Navigation component | Uses `ViewPager2` + explicit Activities rather than a nav graph. |

## UI/UX upgrade (Calm Precision)

Work against `docs/UI_UX_GRAND_UPGRADE_PLAN.md`. All five phases have been
worked through. The design system and the View layer are verified; the
Compose rendering layer is written and statically checked but has never been
compiled, because the artifacts to compile it against are unreachable here.

**Verified — pure logic, covered by the offline suites**

| Module | What it owns |
|---|---|
| `design/DesignTokens` | Spacing, radius, type scale, motion, 10 haptic patterns, density metrics |
| `design/ThemeSelection` | Which theme overlays apply, in what order, and when dynamic colour wins |
| `design/ColorRoles` | Which tone fills which colour role, per palette and mode |
| `design/SurfaceRoles` | Surfaces, outlines and error roles per mode and dark flavour |
| `design/TypeRoles` | The type scale as data, including tracking and weight |
| `design/Ramps` | Tonal ramps and flat colours, generated from the XML |
| `design/Contrast` | WCAG ratios and legible-on-colour selection |
| `design/HistoryStates` | The day-state encoding, streak and completion rules |
| `design/ChartGeometry` | Axis ticks, bar metrics and hit testing, heatmap bucketing, correlation |
| `design/Catalog` | The option lists every appearance surface shares |
| `design/Periods` | Insight windows, chart bucketing, and the sample-size thresholds that gate every claim |
| `design/Navigation` | Tabs, routes, legacy key migration, nav placement by width class |
| `design/Accessibility` | Touch-target floor, text-scale reflow, colour-vision alternatives, undo timing |
| `design/SoundDesign` + `ToneSynth` | When a cue may sound, how loud, and the synthesis of all four |
| `design/OnboardingFlow` | Six steps, what each requires, what blocks advancing, when to ask for notifications |
| `design/JourneyTree` | The identity-goal-system-habit hierarchy: placement, counts, orphans, gaps |
| `design/WidgetLayout` | Widget size selection, row budgets, and the copy for every state |
| `design/IconVariants` | Which launcher alias to enable, and in what order |
| `design/Rendering` | Which renderer owns each screen while the migration lands |
| `domain/JourneyMapper` | The four entity tables projected into hierarchy nodes |
| `domain/StudioMapper` | Attributing data changes to the AI reply that caused them |

`RoleTest` parses the real theme XML and asserts the Kotlin model reproduces
it role by role, so the two rendering layers cannot drift apart. It found
three shipped bugs when first written, including a WCAG AA failure on
light-mode `colorSecondary` in three of five palettes.

**Shipped and reachable in the View layer**

- Five palettes, three dark flavours, three densities, high contrast, all as
  stacking theme overlays
- Inter / Source Serif 4 / JetBrains Mono, SIL OFL 1.1, with the full type scale
- Appearance & Experience settings, promoted to their own Activity
- Launcher icon variants (default / minimal / paper) via activity-aliases,
  re-asserted after an update
- `SfHaptics`: 10 tuned patterns across three device capability tiers
- `SfSound`: four synthesised interface cues, opt-in, quiet-hours aware,
  suppressed by the ringer switch
- Journey rebuilt as one hierarchy - indent, connectors, subtree counts,
  dormant and orphan treatment, gap prompts, and a level spoken to TalkBack
- Home-screen widget in four sizes, chosen from the launcher's measured cell,
  palette-aware, with per-row check-in and correct resize handling
- Theme application and recreate-on-change across every Activity
- Appearance and experience preferences, round-tripped through export/import

**Written, statically checked, never compiled — Compose**

`ui/theme/` (SfTheme, SfPalette, SfTypography, SfShapes, SfMotion),
`ui/components/` (SfCard, SfChipGroup, SfTextField, SfSectionHeader,
SfSkeleton, SfHistoryStrip, SfProgressRing, SfHabitCard, SfBarChart,
SfHeatmap), `ui/screens/` (Today, Journey, Insights, Studio, Onboarding) and
the fragments that host them.

`tools/check_compose.py` scans all 27 of these - discovered by import, not
listed by hand - and catches missing imports, recursive shadowing, delegation
without `getValue`, naming violations and dangling resource references. It is
not a compiler. **Treat this layer as unreviewed until it builds.**

Studio is the exception: it has no View predecessor, because it is the merge
of Coach, Blueprint and the AI engine and was written new. It is Compose or
nothing, and `design/Rendering` says so.

**Deviations from the plan, and why**

| Plan asked for | Shipped | Why |
|---|---|---|
| Glance widgets | RemoteViews | Glance is a Compose runtime; not resolvable here. The split into `WidgetLayout` + `WidgetChrome` is the one Glance would impose, so only the binding file would change. |
| Pinch-to-zoom heatmap | Scrolling heatmap | Pinch on a chart inside a vertically scrolling list fights the parent for the gesture; scrolling is unambiguous. |
| 8 onboarding steps | 6 | The life-area picker folded into the identity step; asking twice about the same thing is what made it eight. |
| Sampled sound design | Synthesised | No audio assets were reachable. `ToneSynth` generates all four cues from partials, which also keeps the APK smaller. |
| Settings as a tab | Settings as an Activity | Plan 10.1 reduces the tab bar to four; settings is a route you return from, not a place you live. |
| App-level text-size setting | Not built | It would duplicate the system font-size setting and diverge from it. `Accessibility` honours the system scale instead. |

**Still outstanding**

- Nothing in the Compose layer has been run. That is the whole of Phase 2 and
  most of Phase 3 as *rendered* output.
- Performance profiling (plan's <16ms frame budget) needs a device.
- Screen-reader testing was done by reading the semantics, not by listening
  to TalkBack.

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
(That inspection predates this upgrade; the APK cannot currently be rebuilt.)

**The APK has not been executed on a device or emulator** — none was available.
It compiles, links, dexes, packages and verifies, and its framework-independent
logic is covered by the test suites, but runtime behaviour on real hardware is
unverified. That is the first thing to check on a device.
