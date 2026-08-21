# SuperFlow — Code & Planning Alignment Grand Plan

**Version:** 1.0 · August 2026
**Purpose:** Bridge the gaps between the five recently-authored upgrade plan documents and the five merge-requested PRs that were generated from them, so that after all currently open PRs are merged, the app is a single, consistent, non-contradictory codebase — no dead features, no duplicated implementations, no conflicting schemas, and no plan that promises something the code does not deliver.
**Explicitly out of scope:** `docs/DATA_MANAGEMENT_AND_AI_SIMPLIFICATION_PLAN.md` (per instruction this plan has not been worked on yet; its future PR must obey the same discipline laid out here).

---

## 0. How to read this plan

This document is a **post-merge alignment plan**, not a feature design. It assumes the reader is about to (or has just) merged the five open PRs. It answers four questions:

1. **What did each plan promise, and what did each PR actually deliver?** (§4–§5)
2. **Where do the PRs collide with each other and with the current `main`?** (§6)
3. **What is the target end-state after a correct merge?** (§8)
4. **In what order, and with what decisions, do we get there?** (§9–§13)

Every claim below was verified against the actual git objects (commit trees, diffs, file contents), not just the PR descriptions.

---

## 1. Executive summary

Five open PRs exist, each claiming to fully implement one of five upgrade plan documents:

| PR | Plan document | Claims |
|----|---------------|--------|
| #6 `arena/01a01abb-superflow` | `ALPHA2_UPGRADE_PLAN.md` | all 20 Alpha2 features |
| #7 `arena/01a01aaa-superflow` | `CORE_GROWTH_SYSTEMS_UPGRADE_PLAN.md` | all 15 core systems |
| #8 `arena/01a01aa5-superflow` | `APP_FUNCTIONAL_GRAND_PLAN.md` | all 6 phases |
| #9 `arena/01a01a83-superflow` | `UI_UX_GRAND_UPGRADE_PLAN.md` | all 5 phases |
| #10 `arena/01a01aa8-superflow` | `GAP_ANALYSIS_AND_FIXES.md` | all 5 waves (80 gaps) |

**The single most important finding:** all five PRs were branched from a common *plan* branch at commit `5a699f18` (`plan/*`, all five plan branches point to it). That commit predates PR #5 (`main` = `7bc94b56`, "Standard Gradle build, startup/performance fixes, and full test coverage"). Consequently **every open PR, if merged as-is, reverts the build system**: it deletes the Gradle wrapper, the build files, `tools/verify.sh`, and all 12 JVM/instrumentation test files, and re-introduces the removed Gradle-less pipeline (`tools/build_apk.sh`, `tools/gen_res.py`, `tools/libs.txt`, `tools/run_tests.sh`, `tools/test/*`). Two of the PRs even list `main` as their GitHub base while their actual commit parent is the stale plan branch.

**The second most important finding:** the plans themselves overlap, and the PRs therefore **re-implement the same features independently and incompatibly**. The capability catalogue is simultaneously claimed at versions 2, 3, and 4 with 49, 59, 77, 94, and 53 capabilities. The database is migrated `v3 → v4` by four different PRs with four different contents. At least 11 features (search, templates, app lock, backup, weekly summary, graduation, checkpoints, plan-tomorrow, pause/vacation, share card, fuzzy matching, TTS, STT, growth engine, proactive AI, milestones, obstacle surfacing, review actions, Four Laws, energy) are implemented two or three times under different packages and names.

This plan defines **one target end-state** and a **phased reconciliation** that turns five overlapping PRs into a single coherent codebase, then re-syncs the five plan documents to the code actually shipped.

---

## 2. Evidence base (what was inspected)

- **Current `main`** — commit `7bc94b5687f7447680223d40b0ec1ec6fb5a32d5` (merge of PR #5). Contains PR #4 (AI engine + data management + the six grand upgrade plan *documents*) **and** PR #5 (Gradle build + tests + startup/perf fixes).
- **Plan base** — commit `5a699f18833a37c430e890bccd351470444f017a`, the tip of all five `plan/*` branches (`plan/alpha2-features`, `plan/core-growth-systems`, `plan/functional-overhaul`, `plan/ui-ux-upgrade`, `plan/gap-fixes-insights`, and also `plan/data-mgmt-ai-simplify`). This is PR #4's state **without** PR #5.
- **The five PR head branches** and their full diffs vs `main` and vs the plan base.
- **The five plan documents** (`ALPHA2`, `CORE`, `APP_FUNCTIONAL`, `UI_UX`, `GAP_ANALYSIS`) and the existing `IMPLEMENTATION_STATUS.md` / `BUILD.md` / `README.md` on `main`.
- **Current domain core on `main`**: `Capabilities.kt` (49 capabilities, `CATALOG_VERSION = 2`), `Database.kt` (`VERSION = 3`), `Models.kt` (359 lines), `Repository.kt` (493), `CommandBus.kt` (261), `Insights.kt` (299), `Serial.kt` (285).

### Commit topology (verified)

```
PR #4 commit (5a699f18)  ──► plan/* branches  ──► PR #6, #7, #8, #9, #10 heads
        │
        └────────► + PR #5 (6bac1fdc) = main (7bc94b56)
```

Verified with `git merge-base --is-ancestor`: `5a699f18` is an ancestor of **all five** PR heads, and `main` is `5a699f18` **plus** PR #5's commit (`6bac1fdc`). None of the five PR heads contains `6bac1fdc`.

| Branch | Parent chain (actual) | PR base (GitHub metadata) |
|--------|----------------------|---------------------------|
| `arena/01a01abb-superflow` (#6) | descends from `5a699f18` | `plan/alpha2-features` |
| `arena/01a01aaa-superflow` (#7) | descends from `5a699f18` | `plan/core-growth-systems` |
| `arena/01a01aa5-superflow` (#8) | parent is `5a699f18` | `main` ⚠️ (mismatch) |
| `arena/01a01a83-superflow` (#9) | descends from `5a699f18` | `plan/ui-ux-upgrade` |
| `arena/01a01aa8-superflow` (#10) | parent is `5a699f18` | `main` ⚠️ (mismatch) |

---

## 3. Baseline: what the current `main` actually is

This is the foundation the merged result must preserve and extend. It is **not** what the five PRs were built against.

| Axis | `main` today |
|------|-------------|
| Build | Standard Android Gradle Plugin; Gradle 8.11.1, AGP 8.9.1, Kotlin 2.2.0, JDK 17. `./gradlew assembleDebug`, `./gradlew testDebugUnitTest`, `tools/verify.sh --device`. |
| Capability catalogue | **49 capabilities**, `CATALOG_VERSION = 2`, single `CommandBus` shared by UI + AI. |
| Database | `androidx.sqlite`, **`VERSION = 3`** with real `v2`/`v3` migrations (`daysMask → recurrenceRule`, pauses, profile). |
| Tests | 8 JVM unit-test files under `src/test/kotlin` (143 assertions) + 4 `androidTest` files. |
| UI | Full Material 3 **XML Views + MVVM** (`AppCompatActivity` + `Fragment` + `ViewPager2` + `RecyclerView`), full dark mode, edge-to-edge, animated charts. |
| AI | Local Coordinator (rules-based) + cloud Main Brain; Blueprint Studio; Full Control; voice via `SpeechRecognizer`. |
| Data management | `DataPolicy.kt` (23 registered categories), `DataManagementFragment`, `InfoButton`. |
| Docs | `BUILD.md`, `IMPLEMENTATION_STATUS.md` honestly report "49 capabilities · 143 assertions · APK not executed on device". |

Anything a PR deletes from this baseline is a regression, not progress. The alignment plan therefore starts from **`main`**, not from the plan branches.

---

## 4. Plan ↔ PR mapping and coverage

| Plan doc | Open PR | Head branch | Base branch | Changed files | +lines / −lines |
|----------|---------|-------------|-------------|---------------|-----------------|
| `ALPHA2_UPGRADE_PLAN.md` | #6 | `arena/01a01abb-superflow` | `plan/alpha2-features` | 44 | +3136 / −109 |
| `CORE_GROWTH_SYSTEMS_UPGRADE_PLAN.md` | #7 | `arena/01a01aaa-superflow` | `plan/core-growth-systems` | 25 | +2157 / −88 |
| `APP_FUNCTIONAL_GRAND_PLAN.md` | #8 | `arena/01a01aa5-superflow` | `main`* | 27 | +5127 / −11 |
| `UI_UX_GRAND_UPGRADE_PLAN.md` | #9 | `arena/01a01a83-superflow` | `plan/ui-ux-upgrade` | 167 | +20825 / −1238 |
| `GAP_ANALYSIS_AND_FIXES.md` | #10 | `arena/01a01aa8-superflow` | `main`* | 64 | +4526 / −255 |

\* base metadata says `main`; the actual commit parent is the stale plan branch (see §2).

> "Changed files" is the PR's own diff against its (stale) plan base — i.e., what that PR itself authored. The **additional** files each PR would touch against `main` (deleting the Gradle wrapper, build files and tests, and re-adding the removed `tools/` pipeline) are listed separately in §5 and are not counted above.

### What each PR delivers (verified from diffs)

- **#6 (Alpha2)** — adds `domain/Search.kt`, `domain/HabitTemplates.kt` (44 templates), `domain/Graduation.kt`, `domain/Diagnostics.kt`, `ui/search/SearchActivity.kt`, `ui/today/CheckpointActivity.kt`, `ui/today/PlanTomorrowActivity.kt`, `ui/common/ShareCard.kt`, `util/Text.kt` (Levenshtein), `AppLock.kt`/`AppLockActivity.kt` (root package), drag-and-drop reorder, quiet-hours-per-weekday, 7 notification channels, dynamic shortcuts. Bumps catalog to **v3 / 59** and DB to **v4**.
- **#7 (Core)** — deep upgrades to the 15 growth systems, all in-place in `Models.kt`, `Database.kt`, `Repository.kt`, `Cursors.kt`, `Serial.kt`, `Insights.kt`, `Capabilities.kt`, `Coordinator.kt`, plus UI in Today/Journey/Detail/Recovery/Insights/Review/Scorecard/Flows. 28 new capabilities → claims **77** total, but leaves `CATALOG_VERSION = 2` (bug). DB → **v4**.
- **#8 (Functional)** — `ai/SfTextToSpeech.kt`, `ai/VoiceInputV2.kt`, `blueprint/CompilerV2.kt`, `domain/GrowthEngine.kt`, `domain/HabitTemplates.kt` (50+ templates), `domain/AutoReview.kt`, `notify/SmartNotifications.kt`, `work/ProactiveAiWorker.kt`, `ui/coach/StudioFragment.kt`, `ui/journal/JournalActivity.kt`, `ui/routine/RoutineBuilderActivity.kt`, 20+ new models, 10 new tables. Claims **94 capabilities**, catalog **v4**, DB **v4**.
- **#9 (UI/UX)** — a `design/*` pure-Kotlin design system (20 files), `ui/theme/*` and `ui/components/*` Compose layer, `ui/screens/*` (Today/Journey/Insights/Studio/Onboarding), `ui/studio/StudioFragment.kt`, `AppearanceFragment`/`SettingsActivity`, 4-size widget, haptics/sound, palette generator and static gates. Capability count unchanged (49, v2). Documents 6 plan deviations.
- **#10 (Gap)** — `util/Fuzzy.kt`, `util/Limits.kt`, `util/Click.kt`, `domain/InsightsCache.kt`, `domain/ReviewActions.kt`, `domain/GrowthEngine.kt`, `ai/Speech.kt`, `ai/Suggestions.kt`, `data/Backups.kt`, `security/AppLock.kt` + `LockActivity` + `PinSetupSheet`, `share/ProgressCard.kt`, `ui/designer/HabitTemplates.kt` (8 templates), `ui/pause/PauseActivity.kt`, `ui/search/SearchActivity.kt`, `ui/sheets/PlanTomorrowSheet.kt`, `work/WorkPrefs.kt`, 5 workers, insights redesign, dynamic shortcuts. Claims catalog **v4 / 53**, DB **v4**.

---

## 5. Critical finding #1 — stale base: every PR reverts the build system

The five PRs were written against the **pre-PR-#5** tree. Verified directly against the git trees:

| Artefact | `main` | All 5 PR heads |
|----------|--------|----------------|
| `gradlew`, `gradlew.bat`, `gradle/wrapper/*` | ✅ present | ❌ absent |
| `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `app/build.gradle.kts`, `app/proguard-rules.pro` | ✅ present | ❌ absent |
| `src/test/kotlin/**` (8 files, 143 assertions) | ✅ present | ❌ absent |
| `src/androidTest/kotlin/**` (4 files) | ✅ present | ❌ absent |
| `tools/verify.sh` | ✅ present | ❌ absent |
| `tools/build_apk.sh`, `tools/gen_res.py`, `tools/libs.txt`, `tools/run_tests.sh`, `tools/test/*` (shim harness) | ❌ removed by PR #5 | ✅ re-added |

`git diff main…<pr>` also shows PR #5's app changes being reverted across ~20 UI/domain files (Onboarding, TodayWidget, Workers, Charts, Insights, Flow/Review/Scorecard activities, JourneyViewModel, Settings/DataManagement fragments, Reminders, Coordinator, Prefs, Repository, Database, Insights, AndroidManifest, etc.).

**Consequence:** merging any single PR as-is removes the only supported build path and the entire test suite. This is the first and hardest gap to bridge, and it is why the merge cannot be a simple sequence of GitHub "merge" buttons.

---

## 6. Critical findings #2–#7 — where the PRs collide

### #2 Divergent capability catalogues

| Branch | `CATALOG_VERSION` | Capability count |
|--------|-------------------|------------------|
| `main` | 2 | 49 |
| #6 | 3 | 59 |
| #7 | 2 (not bumped — bug) | 77 |
| #8 | 4 | 94 |
| #9 | 2 | 49 (no change) |
| #10 | 4 | 53 |

Four PRs rewrite `Capabilities.kt` independently. Two PRs claim "catalog v4" with *different* 94 vs 53 capability lists. The merged app must have **exactly one** catalogue, one version number, and one total. (§9.1 gives the union and canonical naming.)

### #3 Conflicting database migrations (all claim v4)

`main` is at schema v3. Four PRs independently bump to v4 with incompatible content:

| PR | v4 migration content |
|----|----------------------|
| #6 | `Habit.graduated` column; schema v3→v4 |
| #7 | new `evidence` table + columns across 10 tables; additive v3→v4 |
| #8 | 10 new tables (`growth_plan`, `milestone`, `sprint`, `journal_entry`, `routine`, `routine_step`, `environment_design`, `ai_memory`, `proactive_suggestion`, `growth_phase_history`) |
| #10 | 15+ FK/query indexes; idempotent v3→v4 |

These cannot coexist. The merged schema must be **one** v4 (or v5) migration that is the *union* of all four, written idempotently, with a schema test that re-runs every historical migration from scratch (§9.2).

### #4 Genuine multi-PR file conflicts

Files that **more than one PR intentionally edited** (compared against the plan base, so this excludes the §5 build-revert):

| File | Edited by | Notes |
|------|-----------|-------|
| `domain/Capabilities.kt` | #6, #7, #8, #10 | catalogue rewrite × 4 |
| `data/db/Database.kt` | #6, #7, #8, #10 | v4 migration × 4 |
| `data/Repository.kt` | #6, #7, #8, #10 | CRUD/transactions × 4 |
| `domain/Serial.kt` | #6, #7, #8, #10 | JSON round-trips × 4 |
| `data/Prefs.kt` | #6, #8, #9, #10 | new pref keys × 4 |
| `notify/Reminders.kt` | #6, #7, #8, #10 | channels/actions × 4 |
| `domain/Insights.kt` | #7, #8, #10 | analytics × 3 |
| `data/model/Models.kt` | #6, #7, #8 | model fields × 3 |
| `work/Workers.kt` | #6, #8, #10 | new workers × 3 |
| `ui/MainActivity.kt` | #6, #9, #10 | nav/lock/host × 3 |
| `ai/MainBrain.kt` | #7, #8 | context enrichment × 2 |
| `domain/CommandBus.kt` | #7, #10 | bus wiring × 2 |
| `data/DataPolicy.kt` | #6, #9 | registry edits × 2 |
| `data/db/Cursors.kt` | #7 (+reverts) | row mappers |
| `ai/Coordinator.kt` | #7 (genuine); #6/#8/#10 carry PR-#5 revert | NLP patterns |

### #5 Feature duplication (same feature, different implementations)

| Feature | Implemented by | Conflict type |
|---------|----------------|---------------|
| Global search | #6 `domain/Search.kt` + `ui/search/SearchActivity.kt`; #10 `ui/search/SearchActivity.kt` | same file path + different logic |
| Habit templates | #6 `domain/HabitTemplates.kt` (44); #8 `domain/HabitTemplates.kt` (50+); #10 `ui/designer/HabitTemplates.kt` (8) | same class name in #6/#8; different package in #10 |
| App lock | #6 `AppLock.kt`/`AppLockActivity.kt` (root pkg); #10 `security/AppLock.kt`/`LockActivity`/`PinSetupSheet` | duplicate feature, different packages/layouts |
| Auto-backup | #6 in `Workers.kt`; #10 `data/Backups.kt` + `BackupWorker` | duplicate feature |
| Weekly summary | #6 in `Reminders.kt`/`Workers.kt`; #10 `ReviewWorker` | duplicate feature |
| Graduation | #6 `domain/Graduation.kt`; #10 graduation nudge | duplicate feature |
| Guided checkpoints | #6 `CheckpointActivity`; #10 checkpoint dialogs in Today | duplicate feature |
| Plan-tomorrow | #6 `PlanTomorrowActivity`; #10 `PlanTomorrowSheet` | duplicate feature |
| Pause/vacation UI | #6 settings UI; #10 `PauseActivity` | duplicate feature |
| Duplicate + reorder | #6 `duplicate_habit`/`reorder_habits`; #10 `duplicate_habit` + `ItemTouchHelper` | same capability name, two impls |
| Share-progress image | #6 `ui/common/ShareCard.kt`; #10 `share/ProgressCard.kt` | duplicate feature |
| Fuzzy/Levenshtein | #6 `util/Text.kt`; #10 `util/Fuzzy.kt` | duplicate helper |
| TTS | #8 `ai/SfTextToSpeech.kt`; #10 `ai/Speech.kt` | duplicate feature |
| STT fix | #8 `ai/VoiceInputV2.kt`; #10 clearer error in `VoiceInput` | overlapping |
| Growth engine | #7 `evolve_ladder`; #8 `domain/GrowthEngine.kt`; #10 `domain/GrowthEngine.kt` (same path!) | hard conflict + conceptual triple |
| Proactive AI | #8 `ProactiveAiWorker`; #10 `ai/Suggestions.kt` | overlapping |
| Milestones | #7 `add_goal_milestone`/`complete_goal_milestone`; #8 `Milestone` (16 types) + `list_milestones`; #10 `MilestoneWorker` | overlapping |
| Obstacle surfacing | #7 `activate_obstacle_plan`/`rate_obstacle_plan`; #8 `suggest_obstacle_plan`; #10 contextual surfacing | overlapping |
| Review action items | #7 `add_review_action_item`/`complete_review_action`; #8 `AutoReview`; #10 `domain/ReviewActions.kt` | overlapping |
| Four Laws | #7 `rate_reward`/`rate_reframe`/`rate_bundle`/`update_four_laws`; #10 "Living Four Laws" nudge | overlapping |
| Energy-aware advice | #7 `get_energy_correlation`; #8 energy-aware scheduling; #10 scheduling hint | overlapping |

### #6 The plan documents overlap each other

The plans were authored as five independent documents with no shared issue registry, so the same work is promised in several places at once:

- **Alpha2 ⇄ Gap-analysis** overlap on ~15 items: search (#11), reorder (#12), duplicate (#13), complete-all-tiny (#14), undo-today (#15), templates (#16), weekly summary (#17), share image (#18), app lock (#19), backup (#20), notification actions (#21), checkpoints (#22), plan-tomorrow (#23), pause (#26), graduation (#27), fuzzy search (#30), integrity diagnostics (#36), quiet-hours-per-day (#70), channels (#71–73), dynamic shortcuts (#63), locale dates (#62), RTL (#61), onboarding templates (#74).
- **Gap-analysis deliberately delegates** its critical items #1–10 to "the Functional Plan" (TTS, STT, Compiler, Proactive AI) and "the Core Systems Plan" (Growth, Energy, Four Laws, Ladder, Reviews, Obstacles) — so the *same* gap is both a Gap-plan item and a Functional/Core-plan item.
- **Functional ⇄ Core** overlap on: Four Laws, adaptive ladder/growth, obstacle surfacing, flows/routines, scorecard, milestones, reviews/action items, energy.
- **Functional ⇄ Alpha2** overlap on: habit templates, milestone acknowledgment, journaling vs. checkpoint reflections, notification actions, weekly summary.
- **Alpha2's own changelog** re-lists "AI Engine 30+ params, Insights redesign, Blueprint rewrite, Four Laws, Ladder, Reviews, Recovery, Check-ins, TTS, STT, Energy" — i.e., Alpha2 quietly claims the *other* plans' scope.

The alignment plan therefore includes **a single-source-of-truth feature matrix** (§9.3) that assigns every promised capability to exactly one owning document, so the docs stop contradicting each other.

### #7 Documentation drift

Each PR rewrites `IMPLEMENTATION_STATUS.md` (and some touch `README.md`/`BUILD.md`) to describe *its own* world — different capability totals (53/59/77/94), different test harnesses (`tools/run_tests.sh` shims vs. Gradle JUnit), and different claims about what is "done". After merging, exactly one `IMPLEMENTATION_STATUS.md` must exist, written against the unified catalogue, the Gradle build, and the real device results (§9.5).

### #8 Plan-vs-PR deviations and unverified claims

Every PR states it was **not built or run on a device** (no Android toolchain in the generation environment). The merged app must be compiled, unit-tested, and smoke-tested before any PR's claims are trusted. Documented deviations that must be carried forward (and re-validated) rather than silently dropped:

- **#9 (UI/UX)** ships a *View* design system + a *statically-checked but never-executed* Compose layer; `design/Rendering` deliberately keeps Today/Journey/Insights on their View implementations. Six documented deviations: RemoteViews widget instead of Glance; scrolling heatmap instead of pinch-zoom; 6 onboarding steps instead of 8; synthesised sound instead of sampled; Settings as an Activity instead of a tab; no app-level text-size setting.
- **#10** intentionally excludes gap items #79 (Wear OS) and #80 (Assistant Slices) — document as "out of scope", not "done".
- **#7** claims 77 capabilities while leaving `CATALOG_VERSION = 2` — the version bump is missing.

---

## 7. The gap list (what "no gap left behind" concretely means)

After merging, the following categories must each be *resolved*, not just "noticed":

1. **Build & test regression** — Gradle build and all `src/test` / `androidTest` files restored; shim harness removed; `tools/run_tests.sh` deleted or re-scoped. (Gap between all 5 PRs and `main`.)
2. **One catalogue** — a single `Capabilities.kt` with one `CATALOG_VERSION` and one total count; every manual action and AI tool name drawn from it. (Gap among #6/#7/#8/#10.)
3. **One schema** — a single, idempotent, additive `v4` (or `v5`) migration covering the union of all PRs' columns/tables/indexes, with a migration test. (Gap among #6/#7/#8/#10.)
4. **One implementation per feature** — every duplicate listed in §6.5 collapsed to a canonical owner; losers retired or renamed. (Gap among the 5 PRs.)
5. **One plan story** — the five plan docs reconciled so no feature is promised twice or owned by nobody. (Gap among the 5 plans.)
6. **One status report** — a single `IMPLEMENTATION_STATUS.md` that tells the truth. (Gap among the 5 PRs' doc edits.)
7. **Honest "not shipped" list** — unrun Compose screens, unbuilt APK, excluded Wear/Slices, deviations — all recorded, with a device-verification task.

---

## 8. Target end-state (the merged app)

The post-merge codebase should look like this:

- **Build**: standard AGP only (`./gradlew assembleDebug`, `./gradlew testDebugUnitTest`, `tools/verify.sh --device`). No `tools/build_apk.sh` / `tools/gen_res.py` / `tools/test/*` shim.
- **Domain**: one `Capabilities.kt`. Starting from `main`'s 49, the union of genuinely new capabilities is ~85–90 (see §9.1); the exact final count is decided once canonical owners are chosen, then `CATALOG_VERSION` is bumped to a single value (recommend `4`).
- **Schema**: `VERSION = 4` (or `5` if a v4 already shipped anywhere), one migration path, additive, idempotent, FK-indexed, with `integrityReport()` + `Diagnostics` UI.
- **Models/persistence**: one `Models.kt` / `Serial.kt` / `Cursors.kt` that round-trips every field; all new tables (from #7 and #8) present exactly once.
- **Feature layer**: each Alpha2/Core/Functional/Gap feature exists once, exposed through the same `CommandBus` for manual UI and AI.
- **UI**: View (Material 3) as the executed layer; the Compose design system from #9 as a shared, statically-checked source of design answers with a documented flip-switch per screen; Journey/Studio rebuilt per #9.
- **Workers**: one `Workers.kt` with the union of jobs (rollover, reminder refresh, milestone, review, snapshot cleanup, widget refresh, backup, proactive AI) and no duplicate scheduling.
- **Docs**: `GRAND_PLAN.md` stays the north star; the five upgrade plans remain *plans* but are re-synced to the shipped reality; `IMPLEMENTATION_STATUS.md` is the single source of truth.

---

## 9. Reconciliation decisions (the core of the plan)

These tables are the "bridge" itself. Each row is a decision to execute; together they remove every gap identified in §7.

### 9.1 Capability catalogue — canonical merge

Start from `main`'s 49. Add each PR's *new* capabilities, de-duplicated by name and intent:

| Source | New capabilities | Disposition |
|--------|------------------|-------------|
| #6 | `apply_template`, `list_templates`, `check_integrity`, `fix_integrity`, `graduate_habit`, `ungraduate_habit`, `upgrade_habit`, `graduation_status`, `duplicate_habit`, `reorder_habits` | Keep. `duplicate_habit`/`reorder_habits` also exist in #10 → adopt **one** (see feature table). |
| #7 | `evolve_identity`, `add_identity_evidence`, `add_goal_milestone`, `complete_goal_milestone`, `update_goal_metric`, `rate_reward`, `rate_reframe`, `rate_bundle`, `update_four_laws`, `evolve_ladder`, `rate_checkin_difficulty`, `rate_checkin_quality`, `record_miss_reason`, `activate_obstacle_plan`, `rate_obstacle_plan`, `add_review_action_item`, `complete_review_action`, `run_flow`, `complete_flow`, `rescore_scorecard`, `convert_scorecard_to_habit`, `set_habit_capacity`, `get_daily_load`, `set_focus_priority`, `carry_over_focus`, `get_system_health`, `get_energy_correlation`, `get_miss_patterns` | Keep all 28. Bump `CATALOG_VERSION` (fixes #7's bug). |
| #8 | `create_growth_plan`, `evaluate_growth_plan`, `upgrade_phase`, `downgrade_phase`, `list_growth_plans`, `create_sprint`, `complete_sprint`, `abandon_sprint`, `create_routine`, `add_routine_step`, `run_routine`, `check_in_routine`, `suggest_templates`, `create_journal_entry`, `suggest_journal_prompt`, `analyze_patterns`, `analyze_correlations`, `predict_consistency`, `difficulty_assessment`, `time_audit`, `weekly_coaching_report`, `suggest_obstacle_plan`, `suggest_environment`, `morning_briefing`, `evening_reflection`, `create_progressive_blueprint`, `evaluate_blueprint_phase`, `advance_blueprint_phase`, `remember`, `forget`, `list_memories`, `simulate_add_habit`, `simulate_remove_habit`, `simulate_reschedule`, `generate_report`, `export_weekly_summary`, `set_theme`, `set_density`, `set_haptics`, `set_quiet_hours`, `set_environment_design`, `diagnose_struggle`, `obstacle_plan_progress`, `list_milestones`, `reflect`, `delete_routine`, `set_reminders_enabled`, `set_growth_plans_enabled` | Keep, **except** the ones that collide below. |
| #10 | `complete_all_tiny`, `undo_today`, `evolve_habit` (+ `duplicate_habit` = collision) | Keep `complete_all_tiny`, `undo_today`. Resolve `evolve_habit` vs `evolve_ladder` vs Growth Engine (below). |

**Collisions to resolve to a single name/impl:**

| Group | Options | Decision (recommend) |
|-------|---------|----------------------|
| Growth | #7 `evolve_ladder` · #8 `create_growth_plan`/`evaluate_growth_plan`/`upgrade_phase`/`downgrade_phase` · #10 `evolve_habit` | Keep #8's Growth Engine as the canonical growth system (it is the superset); keep #7's `evolve_ladder` as the *ladder-history* command (distinct concern: ladder level vs. phased plan); retire #10's `evolve_habit` or alias it to `evaluate_growth_plan`. |
| Templates | #6/#8 `apply_template`, `list_templates`; #8 `suggest_templates` | One `HabitTemplates` catalogue (choose #8's richer 50+ set, merge #6's 44 if any differ); one `apply_template`/`list_templates`/`suggest_templates`. |
| Duplicate | #6 & #10 `duplicate_habit` | One implementation (deep copy incl. obstacle plans). Keep whichever is cleaner; the other is dropped. |
| Review actions | #7 `add_review_action_item`/`complete_review_action` · #10 `ReviewActions` · #8 `AutoReview`/`reflect`/`weekly_coaching_report` | Keep #7's action-item commands as the canonical review-follow-through; #10's `ReviewActions` becomes a data helper; #8's `weekly_coaching_report` is a separate *report* command. |
| Milestones | #7 goal-scoped `add_goal_milestone` · #8 global `Milestone`/`list_milestones` | Keep both but de-duplicate the worker: one `MilestoneWorker` (#8/#10 collide on this worker) using #8's 16-type model; #7's goal milestones link into it. |
| Obstacles | #7 `activate_obstacle_plan`/`rate_obstacle_plan` · #8 `suggest_obstacle_plan`/`obstacle_plan_progress` | Keep both: #7 = usage tracking, #8 = suggestion generation. |
| Energy | #7 `get_energy_correlation` · #8 `analyze_correlations` + energy-aware schedule · #10 hint | Keep #7's query + #8's scheduling; #10's hint folds into #8. |
| Proactive | #8 `ProactiveAiWorker`/`morning_briefing`/`evening_reflection` · #10 `Suggestions` | Keep #8's worker as the engine; #10's `Suggestions` becomes the Today-card renderer. |
| Flows/Routines | #7 `run_flow`/`complete_flow` · #8 `create_routine`/`run_routine` | Keep both (Flow = existing; Routine = #8 upgrade), but **one** "run" UI (choose #8's Routine Builder). |
| Four Laws | #7 `rate_*`/`update_four_laws` · #10 nudge | #7 is canonical; #10's nudge references it. |

**Result:** one catalogue of roughly **85–90 capabilities** (final count after de-dup), `CATALOG_VERSION = 4`, one total in `IMPLEMENTATION_STATUS.md` and the AI tool registry.

### 9.2 Database schema — one migration

- Choose a single new schema version (recommend `VERSION = 4`; use `5` if any v4 was ever released).
- Write **one** `onUpgrade` that, in order: applies #7's columns + `evidence` table, #8's 10 new tables, #6's `graduated` column, and #10's indexes — each guarded by `IF NOT EXISTS` / column-existence checks so the migration is idempotent and re-runnable.
- Keep #10's FK indexes and `integrityReport()`; keep #6's `Diagnostics` UI as the front door to it.
- Add a `DatabaseSchemaTest` (already scaffolded in `main`'s `androidTest`) that creates a fresh DB at each historical version and migrates forward, asserting the union schema.

### 9.3 Feature ownership (one implementation per feature)

| Feature | Canonical owner | Loser(s) — disposition |
|---------|-----------------|------------------------|
| Global search | #6 `domain/Search.kt` (ranked, grouped) + one `SearchActivity` | #10 `SearchActivity` folded in (or retired) |
| Habit templates | #8 catalogue (50+) + #6 extras merged | #10 `ui/designer/HabitTemplates.kt` (8) folded in |
| App lock | #10 `security/` (PIN + biometric, salted hash) | #6 `AppLock.kt`/`AppLockActivity.kt` retired |
| Auto-backup | #10 `data/Backups.kt` + `BackupWorker` | #6 worker code retired |
| Weekly summary | #10 `ReviewWorker` (Sunday draft + summary) | #6 summary code retired |
| Graduation | #6 `domain/Graduation.kt` (66-day/90%) | #10 nudge references it |
| Guided checkpoints | #6 `CheckpointActivity` (full screens) | #10 dialogs folded in |
| Plan-tomorrow | #6 `PlanTomorrowActivity` | #10 `PlanTomorrowSheet` retired (or vice versa — pick one) |
| Pause/vacation | #10 `PauseActivity` | #6 settings UI folded in |
| Duplicate + reorder | one `duplicate_habit` + one `reorder_habits` (#6) | #10 copies folded in |
| Share image | #6 `ShareCard` or #10 `ProgressCard` (pick one; keep `FileProvider` config) | other retired |
| Fuzzy match | one `Fuzzy`/`Text` util (single source) | other retired |
| TTS | #8 `SfTextToSpeech` | #10 `Speech` folded in |
| STT | #8 `VoiceInputV2` (multi-provider) | #10 error-path merged |
| Growth engine | #8 `GrowthEngine` (phased plans) | #7 `evolve_ladder` kept as ladder history; #10 `GrowthEngine` retired |
| Proactive AI | #8 `ProactiveAiWorker` | #10 `Suggestions` as renderer |
| Milestones | #8 model + one worker | #7 goal milestones linked; #10 worker retired |
| Obstacles | #7 usage tracking + #8 suggestion | — |
| Reviews/actions | #7 action items | #10 `ReviewActions` helper; #8 report command |
| Four Laws | #7 | #10 nudge references |

### 9.4 File-level conflict resolution order

For every file in §6.4, merge in this order so each later edit is applied on top of the previous one:

1. `main` (baseline, includes PR #5)
2. #7 (Core: models, schema, repository, serial, insights, coordinator)
3. #8 (Functional: growth engine, templates, AI overhaul) — resolving overlaps with #7 per §9.3
4. #10 (Gap: integrity, workers, polish) — resolving duplicates per §9.3
5. #6 (Alpha2: search, checkpoints, graduation, platform features) — resolving duplicates per §9.3
6. #9 (UI/UX: design system, theme, Journey/Studio, widget) — applied last because it touches the widest surface

This order is chosen so that the deepest domain work lands first and the broadest presentational work lands last. It is *not* the GitHub PR order (#6→#10); see §12 for the exact git procedure.

### 9.5 Documentation reconciliation

- **`IMPLEMENTATION_STATUS.md`** — rewrite once, after the merge: one catalogue version + count, one schema version, one test count (Gradle JUnit), one honest device-verification status, and the #9/#10 deviation lists.
- **`BUILD.md`** — keep `main`'s version (Gradle); remove any re-added Gradle-less pipeline references.
- **`README.md`** — update the "Capabilities" number and product-plan list to include this alignment plan.
- **The five plan docs** — add a shared "Ownership" header to each pointing at the single feature matrix (§9.3), and strike/re-scope the overlapping claims (esp. Alpha2's changelog and Gap's "covered in X plan" cross-references).
- **`DATA_MANAGEMENT_AND_AI_SIMPLIFICATION_PLAN.md`** — still unworked; when its PR is opened, it must branch from the *post-merge* `main` (not a stale `plan/*` branch) and follow the same single-catalogue/single-schema rules.

---

## 10. The grand phased roadmap

### Phase 0 — Rebase everything onto `main` (blocker removal)

1. For each PR head, rebase onto current `main` and **resolve the build-revert**: keep `main`'s `gradlew`, `gradle/*`, `build.gradle.kts`, `settings.gradle.kts`, `app/build.gradle.kts`, `app/proguard-rules.pro`, `tools/verify.sh`, and all `src/test` + `src/androidTest` files; drop the re-added `tools/build_apk.sh`, `tools/gen_res.py`, `tools/libs.txt`, `tools/run_tests.sh`, `tools/test/*`.
2. Re-translate each PR's new logic tests from the `tools/test/*` shim format into `src/test/kotlin` JUnit (the shim harness has no home in the Gradle build). This is the single largest mechanical task and is non-optional.
3. Fix the two PRs whose base metadata says `main` but whose parent is `5a699f18` — retarget to `main` after rebasing.

**Exit gate:** `./gradlew testDebugUnitTest` and `./gradlew assembleDebug` both pass on the rebased integration branch.

### Phase 1 — Merge in dependency order (§9.4)

Merge as: **#7 → #8 → #10 → #6 → #9**, resolving per §9.3 at each step, on a single `integration/alignment` branch. Do not merge in GitHub PR-number order.

**Exit gate:** each merge keeps the catalogue, schema, and feature tables consistent (no two implementations of one feature, no two v4 migrations, one `CATALOG_VERSION`).

### Phase 2 — Unify the domain core

- One `Capabilities.kt` (union, ~85–90 caps, `CATALOG_VERSION = 4`).
- One `Database.kt` v4 migration (idempotent union), one `Models.kt`/`Serial.kt`/`Cursors.kt`.
- Wire the AI tool registry + `MainBrain` context + `Coordinator` patterns to the unified catalogue.
- Re-add the §9.2 schema migration test.

### Phase 3 — Deduplicate features & platform layers

Execute §9.3 row by row; delete retired classes; remove dead menu/layout resources; ensure the `CommandBus` exposes every surviving feature exactly once.

### Phase 4 — Reconcile the plan documents

Execute §9.5; produce the single feature matrix and status report.

### Phase 5 — Verification & definition of done

Execute §11 gates, including the first on-device build/smoke test that none of the PRs were able to run.

---

## 11. Verification gates (definition of done)

| # | Gate | Command / evidence |
|---|------|--------------------|
| 1 | Clean build | `./gradlew clean assembleDebug` |
| 2 | Unit tests | `./gradlew testDebugUnitTest` — includes all migrated logic suites + existing 143 assertions |
| 3 | Schema migration test | fresh-install + upgrade from v2/v3 to final; asserts union schema |
| 4 | Instrumentation smoke | `tools/verify.sh --device` (launch, first-run, check-in) |
| 5 | Catalogue parity | generated tool list == `Capabilities.all()`; every capability has a manual path and an AI path |
| 6 | No duplicate features | grep confirms one `SearchActivity`, one `HabitTemplates`, one `GrowthEngine`, one `AppLock`, one `Backups`, one TTS, one STT, one `Fuzzy` |
| 7 | No stale pipeline | `tools/build_apk.sh`, `tools/gen_res.py`, `tools/libs.txt`, `tools/run_tests.sh`, `tools/test/*` absent |
| 8 | Docs single-sourced | one capability count / one schema version / one test count across `README`, `IMPLEMENTATION_STATUS`, and the five plans |
| 9 | Compose flip-switch documented | `design/Rendering` states exactly which screens are View vs Compose and how to flip each |

---

## 12. Concrete execution checklist (commands)

```bash
# 0) Create the integration branch off current main (NOT off a plan/* branch)
git checkout main && git pull
git checkout -b integration/alignment

# 1) Rebase each PR head onto main (do one at a time, resolve build-revert first)
git rebase --onto main 5a699f18 arena/01a01aaa-superflow   # #7 core
git rebase --onto main 5a699f18 arena/01a01aa5-superflow    # #8 functional
git rebase --onto main 5a699f18 arena/01a01aa8-superflow    # #10 gap
git rebase --onto main 5a699f18 arena/01a01abb-superflow    # #6 alpha2
git rebase --onto main 5a699f18 arena/01a01a83-superflow    # #9 ui/ux

# 2) Merge in dependency order, resolving per §9.3/§9.4
#    (adapt commit refs to your rebased heads)
git merge --no-ff <rebased-#7>
git merge --no-ff <rebased-#8>
git merge --no-ff <rebased-#10>
git merge --no-ff <rebased-#6>
git merge --no-ff <rebased-#9>

# 3) Gates
./gradlew testDebugUnitTest
./gradlew assembleDebug
./tools/verify.sh --device

# 4) When green, merge integration/alignment into main and update the 5 PRs
#    to target main (or close them in favor of the integration branch).
```

> Note: rebasing onto `5a699f18` is the mechanical way to drop the stale plan-branch ancestry; the intent is "re-apply the PR's own changes on top of current `main`, keeping `main`'s build/tests."

---

## 13. Risks & mitigations

| Risk | Impact | Mitigation |
|------|--------|-----------|
| Merging any PR as-is reverts the Gradle build | App unbuildable | Phase 0 gate is mandatory; never fast-forward a `plan/*`-based head into `main` |
| Four independent v4 migrations | Data loss / upgrade crash | One idempotent union migration + migration test (gate #3) |
| Duplicate features drift apart | Two search/templates/growth systems with different behavior | §9.3 ownership table; gate #6 (grep for single implementation) |
| Compose layer never executed | Shipping unrun screens as the only path | Keep View as executed layer until a device toolchain exists; document flip-switch (gate #9) |
| Test harness fragmentation | Logic suites exist only in removed `tools/test` shims | Migrate to `src/test/kotlin` JUnit in Phase 0 |
| Catalogue drift between UI and AI | AI can't do what UI can (or vice versa) | Single `Capabilities.all()` source; gate #5 |
| Doc claims outrun code | Trusted status page lies | One `IMPLEMENTATION_STATUS.md` written last (gate #8) |
| `DATA_MANAGEMENT` plan PR repeats the stale-base mistake | Another build revert | Enforce "branch from post-merge `main`" rule for all future plan PRs |

---

## 14. Open decisions to confirm with the user

1. **Merge strategy** — merge-by-PR in the §9.4 order on one integration branch (recommended), or fold all five PRs into a single squashed "unified upgrade" commit?
2. **Capability target** — accept the ~85–90 union (recommended), or prune to a smaller alpha2 surface?
3. **Compose flip** — keep the #9 View-as-executed/Compose-as-checked split until a device toolchain exists (recommended), or block the merge on a full Compose runtime?
4. **Schema version** — final version number `4` vs `5` (depends on whether any v4 DB ever shipped to a device).
5. **`tools/` shim tests** — migrate to Gradle JUnit (recommended) or keep a non-build test harness for environments without a toolchain?
6. **Share card & plan-tomorrow** — pick the surviving implementation for the two "either/or" rows in §9.3.

---

*End of the Code & Planning Alignment Grand Plan. It converts five independently-authored PRs and five overlapping plan documents into one coherent, testable codebase with a single catalogue, a single schema, a single implementation per feature, and one honest status report.*
