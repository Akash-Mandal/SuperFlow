# SuperFlow Whole Code Analysis — Latest on Disk (not just git)

**Date:** 2026-08-27 10:03 IST — HEAD `b5a1b2c` (1 ahead of origin), worktree clean
**Scope:** entire `app/src/main/kotlin` (171 files, 43,580 lines) + `res` + `data/db` + `build` — includes unpushed `SfFlowLine.kt`

---

## 1. Scale

- **171 Kotlin files**, 362 files total under `app/src`, 43.5k lines
- Packages: `ai` 11, `blueprint` 3, `core` 3, `data` 7, `design` 23 (21+2 tokens), `domain` 18 (6.5k), `ui` 84, `widget` 2, `work` 4, `util` 6, `notify` 2, `security` 3, `share` 1
- Largest files: `Capabilities.kt` 2,394, `Repository.kt` 1,078, `AiEngineActivity.kt` 1,006, `Prefs.kt` 911, `Models.kt` 812, `Insights.kt` 843, `HabitDesignerActivity.kt` 707, `BlueprintActivity.kt` 668 — all maintainable but `Capabilities` is a god object.
- Tests: 16 unit (`src/test`) + 4 instrumented (`androidTest`) — `FocusEngineTest`, `DayReplayTest`, `AnalyticsTest`, `TokensV3Test`, `OpportunitiesTest`, `RecurrenceTest`, `DatabaseSchemaTest` etc. 17 @Test methods counted.

## 2. Architecture — sound

- **Core:** `core/schedule/Recurrence` + `Opportunities` derived series (no stored streaks), `core/time/SfTime` + injected `Clock` — DST/leap correct by construction. Clean.
- **Data:** single `Database.kt` (586 lines, 28 tables) with hand `androidx.sqlite` DAOs, versioned migration idempotent. Single `Repository` reactive StateFlow, single `Prefs` + isolated `superflow_secrets` file — secrets excluded from backup/export/logs per `DataPolicy.kt` ✓.
- **Domain:** `CommandBus` spine — button/notification/AI/Blueprint share one path, snapshots + audit + undo identical. `Serial.kt` grouping intact.
- **Design:** pure `design/` (no Android imports) — `DesignTokens`, `ColorRoles`, `Ramps`, `ChartGeometry`, `JourneyTree`, `WidgetLayout` — 4,574 lines, high testability (3,071 design assertions per IMPLEMENTATION_STATUS). `tokens/TokensV3.kt` + `ElevationTint.kt` correctly separate T0 primitives from Compose `ui/theme`.
- **UI:** 84 files — `ui/theme` (SfTheme 4 locals), `ui/components` 17 comps, `ui/screens` 5 Compose screens (2,346 lines), legacy View `ui/today|journey|insights` still live via `design/Rendering`. `ui/components` now has both View and Compose paths — migration via `SfScreenScaffold`.
- **AI:** `ai/` 2,058 lines — `MainBrain` cloud + `Coordinator` local, `ModelCatalog` GET /v1/models, `VoiceInputV2` multi-provider (PLATFORM/WHISPER_API/LOCAL/VOSK), `SfTextToSpeech`, `Snapshots`, `Suggestions` ledger.

## 3. Latest-disk specifics (unpushed included)

- `ui/components/SfFlowLine.kt` 83 lines **exists on disk** — Canvas river line, `hasMiss` bend (0.35h vs 0.12h), `progress` clipped cubic, accent dot when miss+motion. Comment explicitly says minimal drawable, PathMeasure draw-on pending — matches plan §1 Flow Line but animation not yet.
- `SfProgressRing.kt` 162 lines — `animateFloatAsState(ringSpring)`, 6s breath 0.98→1.02 infiniteRepeatable Reverse, disabled when `!motion.enabled` ✓.
- `SfCard.kt` 174 lines — 6 variants, `ElevationTint.surfaceFor` wired, Lumen tint (b3c8c94) present.
- `SfHabitCard.kt` 420 lines — `animateDpAsState` settledOffset, `animateFloatAsState` fill/ringWidth/bloom (bloom 12 lines), swipe backdrop + haptics, semantics `customActions`.
- `SfTimeline.kt` 189 lines — rail, accent dots, grouped day headers, `onEntryClick`.
- `SfCommandPalette.kt` 163 lines GlassMat 92% — F1 search bridge present.
- `Capabilities.kt` CATALOG_VERSION 5, 134 Capability() entries — `create_sprint`, `log_energy`, `get_energy_correlation` etc. present.

## 4. Code quality scan (whole code)

**TODO/FIXME:** only 3 real TODOs:
- `VoiceInputV2.kt:194` TODO MediaRecorder actual recording — stub.
- `GrowthEngine.kt:81` TODO averageEnergy from energy logs — null placeholder.
- `CompilerV2.kt:447` TODO habitId placeholder — string literal "TODO".
Others are `XXXL` constant names false positive. Clean.

**Hardcoded colors:** only `ShareCard.kt` `PAPER 0xFFFBF7EE / INK 0xFF1C1B18` intentional (share image export); `Charts.kt` uses `themeColor()` + `R.color.state_missed` correct. No broad hardcoding — token purity largely holds, but 350 `.dp` usages include many raw `12.dp/16.dp` in UI — should be `V3R`/`Space` tokens per §18 gate.

**Compose hygiene:** 431 `MaterialTheme/SfTheme/LocalSf` usages, 249 `remember/StateFlow/@Composable`, 350 `.dp` — okay. No recursive shadowing flagged.

**Security:** `Prefs` secrets isolated, `DataPolicy` excludes apiKey/whisperApiKey/PIN from export, `clearSecrets()` present, prompt-injection isolation in Blueprint ledger visible. No leaked keys in grep.

**Workers:** `work/Workers.kt` 561 lines — DailyRollover, Reminder refresh, Milestones, Weekly review, Snapshot cleanup, Widget refresh via WorkManager, conservative miss materialisation (skip paused/planned) correct. `AutoReinforceWorker` 54 lines present.

## 5. Gaps — whole code vs composite plan

Same as audit plus code evidence:

- **DB schema 28 tables** — missing `nudge_ledger`, `day_timeline_events` materialized view, Habit cols `colorOverride/essential/flexDays/quietHours`, `sprint_outcome RELEASED`, graduation cert ref, prefs `nudgeBudget/motionLevel/soundMix` — grep confirms absent.
- **F7 Nudges:** zero `domain/Nudges.kt`, zero `SfNudgeBanner`, `SmartNotifications` 182 lines static only — plan §9 requires state-aware budget 2/day.
- **F5:** Habit model lacks 4 fields — code search confirms.
- **F2.1:** Blueprint resume/diff/template UI zero files under `ui/blueprint` beyond `BlueprintActivity`.
- **F10:** search still `LIKE` not FTS5 (`Search.kt` 100 lines naive), journal linking `[[habit]]` missing, Scorecard not incremental, backup ring not 7-daily, settings not restructured (single `SettingsFragment` 640 lines), Tune-your-app flow absent (`OnboardingScreen` 562 lines 6 steps but no palette/motion/nudge picker).
- **Visual polish:** Flow Line not integrated into Today hero/onboarding/empty states (only standalone component), gradients limited to one hero, glass blur fallback SOLID, splash via `values/themes.xml` Theme.SuperFlow.Splash static not animated, widget single `TodayWidget.kt` not 4 sizes, icons `IconVariants` logic exists but aliases not generated per `mipmap-anydpi-v26`.
- **Perf:** `tools/check_compose.py` static only, no baseline profile, no `gradlew assembleDebug` compile verification (Google Maven offline caveat still in IMPLEMENTATION_STATUS).

## 6. Whole-code strengths

- No crashes patterns (previous 14 issues fixed), pure design layer unit-testable, strong command parity, offline-first, 28-table migration union clean, versionCode 5 stable keystore ✓.

## 7. Next — code-complete path

Push `b5a1b2c`, wire Flow Line into 3 surfaces, add 4 Habit columns + migrations, implement `Nudges.kt` + ledger + banner, F2.1 resume, FTS journal, per-module F10 passes, then gates 1-8 green.

*This doc complements `ALPHA3_FULL_REPO_AUDIT.md` — that one is milestone↔commit, this one is disk-wide code.*
