# SuperFlow — Implementation Status

A full AndroidX / Material 3 Android application built from the product plans in
this repository. This document records honestly what is implemented, what is
partial, and what is not built.

**Build:** `com.superflow` 2.0.0 · minSdk 26 · targetSdk 34 · ~7.5 MB ·
v2+v3 signed · 71 AndroidX/Material libraries · 311 app classes ·
103 logic assertions passing.

---

## Architecture

```
app/src/main/kotlin/com/superflow/
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
| Room | Uses `androidx.sqlite` (Room's own support layer) with hand-written DAOs. Room's annotation processor is unavailable offline; the runtime contract is identical, but there is no compile-time query verification. |
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
