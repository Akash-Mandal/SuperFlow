# SuperFlow — Blueprint Studio Recreation Plan (Plan B)

> **Scope:** Intelligent habit merging, flood fix, incremental phased rollout, Auto Reinforce, countless quality frameworks, complete panel UI/UX recreation.  
> **Companion:** `CORE_STABILITY_PLAN.md` (Plan A) + `APP_REDESIGN_PLAN.md` (Plan C) — parallel execution, separate docs for scope.  
> **Decisions locked:** Q1 B→A→C internally parallel; Q2 fetch models; Q3 dual Auto-Reinforce (default Propose); Q4 5 logos in Plan A.  
> **Date:** 2026-08-23 · **Status:** Approved for build  
> **Pre-read:** `docs/BLUEPRINT_STUDIO_PLAN.md` (authoritative 22-point), `docs/GRAND_PLAN.md` §7.12, `docs/FULL_CONTROL_PLAN.md`

---

## 0. Why the current Blueprint fails a real year's plan — a first-principles diagnosis

Imagine a user pastes a year plan: January “walk daily”, February “walk + meditate daily, read 20 pages”, March “walk 5km + meditate 15min + journal + language”, with weekly incremental upgrades, daily new targets, and evolving systems/identities/goals/flows. Today `blueprint/Compiler.kt:373` does:

```
for (src) for (line) if (looksActionable(line)) Requirement(text=line.take(280), status=ACCEPTED, plannedCommand=planCommand(line))
```

`looksActionable` true if first-person/I-will/every-day/daily/weekly/habit/routine/track/practice/at HH:MM or section contains habit. `planCommand` success requires `Coordinator.parseHabitPhrase(clean) != null` — which succeeds for almost any 2-70 char title. Result: **500-line plan → 500 Requirements → 500 cards in a LinearLayout (`BlueprintActivity:ledgerSection`) → 500 `create_habit` executions grouped + snapshot.** The user must tap 500 cards to accept/reject. That is not intentional design; it is a line parser masquerading as an Intent Compiler.

**Four deeper failures hide beneath the count:**

1. **No semantic merging.** “walk 10 min”, “walking 10 minutes after breakfast”, “daily walk”, “go for a walk” are four Requirements for one habit. `markConflicts` only merges exact `command|title` lowercase, so duplicates survive.
2. **No minimality.** The flagship promise is “minimal, manageable, doable”. The compiler is maximal. It never asks “how many habits do you want?” nor estimates daily time.
3. **No time horizon.** A year plan is ingested as an atemporal bag. Weekly increments (“add 5 min per week”) become separate habits, not phases of one habit’s ladder (Tiny→Minimum→Standard→Stretch).
4. **No growth loop.** After applying 500 habits, the AI never asks “should we generate or improve a routine/flow vs the previous one?” nor analyzes all mechanics for the user.

**The unlocked key:** `blueprint/CompilerV2.kt:513` already solves 1-3 but is **not wired to UI**. It has `captureIntent(goal,dailyTimeMinutes<=240,currentLevel,existingRoutines,durationWeeks 4-52, priorityAreas) → UserIntent`, `extractRawItems` (same per-line but with `priorityBonus 10` for goal lines), `groupIntoThemes { sectionName }` canonicalizing to Movement/Mindfulness/Learning/Nutrition/Sleep/Focus/Relationships/Finance/Creativity/General, `estimateThemeMinutes`, `prioritiseThemes` by goal keywords, `generateProgressivePlan(themes,intent,existingHabits) → ProgressivePlan(phases List<PlanPhase>, totalWeeks)` with `twoWeekPhases=(weeks+1)/2` (min 2), `maxHabitsPerPhase=(dailyTime/10).coerce 1..3`, `themeToHabits` via `HabitTemplates.suggestForGoal`, and `compilePhase(phase,projectId,phaseIndex)` emitting only this phase’s habits with ladder-aware `standard` (phase0=tinyStart, 1=minimum, else standard). It is used only by `Capabilities.create_progressive_blueprint` tool, not by `BlueprintActivity.compile()` which calls V1.

**Recreation thesis:** Turn the Blueprint Studio from a line-to-card factory into a **growth-aware Intent Compiler** that (a) understands, (b) compresses, (c) phases, (d) auto-reinforces, and (e) proposes flows — with a panel that is coll

---

## 1. Product principles for the recreated Studio

1. **Minimal beats maximal.** Ask preferred amount (word-approx) for every mechanism — identities (≈10 words? 1-3?), systems (1-5?), goals (1-3?), habits (3-8?), flows/routines (0-3?) — then enforce via `maxHabitsPerPhase` and theme caps.
2. **Phased, not flooded.** A year plan with incremental upgrades becomes 2-week phases, not 500 simultaneous habits. Current phase applies now; future phases become scheduled `Auto Reinforce` entries.
3. **Collapsible, not endless.** Theme-grouped, paginated, searchable ledger — never 500 cards at once.
4. **Doable, not heroic.** `GrowthEngine` + `ProactiveAiWorker` + `PhaseMetrics` (minConsistency 50-80, minWeeksInPhase, struggleThreshold) guard each phase.
5. **Flows are first-class.** Habits alone are insufficient; after all mechanics, AI must propose routine/flow generation or improvement (add/remove/rearrange/edit) vs previous flow and analyze similarly for every mechanic.
6. **Auto Reinforce is visible.** In-app stored data `what/when/where/how + condition → action`, triggerable via chat (“reinforce now”), inspectable, undoable.

---

## 2. Architecture — before vs after

### 2.1 Current (V1) — line → habit

```
Sources (text/pdf) → lines → looksActionable → planCommand → Requirement (ACCEPTED if command) → ledger LinearLayout (no pagination) → doExecute loop all ACCEPTED → bus.execute create_habit ×500 → VERIFIED/GAP → report "Compiled N from M sources"
Optional: blueprintCloudRefine → MainBrain.chat refinementPrompt (id|status|citation|text.take160) → applyRefinement allowlist 6 commands
```

### 2.2 Recreated (V2-wired) — intent → themes → phases → Auto Reinforce

```
Sources + Instructions + Preference Dialog (amounts, dailyTime, durationWeeks, currentLevel, priorityAreas, goal)
  → UserIntent (CompilerV2.captureIntent)
  → extractRawItems (per-line + priorityBonus)
  → groupIntoThemes (sectionName canonical 10 themes, + General)
  → estimateThemeMinutes + prioritiseThemes
  → generateProgressivePlan (twoWeekPhases, maxHabitsPerPhase, HabitTemplates.suggestForGoal)
  → ProgressivePlan { phases: PlanPhase(label, weekNumber, habitsWithLevel, targetDays, metrics) }
  → Pre-apply dialog: "We propose 5 themes → 26 weeks → 6 phases → 12 habits total (2/phase, ~18 min/day). Accept / Edit amounts / Regenerate"
  → compilePhase(phase0) → Requirements (phase0 only, ~2-3 habits) → collapsible Theme ledger
  → user accepts/rejects per Theme (bulk) + per card
  → doExecute(phase0) → VERIFIED
  → auto-schedule phases 1..N-1 as BlueprintAutoPlan entries (what/when/where/how auto-changes)
  → post-execute proposal: "Generate/Improve routine/flow vs previous? Add/remove/rearrange/edit?" + analyze all mechanics
Cloud refine remains but now refines *phases*, not lines.
```

**Key file moves:**

- `BlueprintActivity.compile(p)` → call `CompilerV2.compileForBlueprint` (or new `CompilerV3` that wraps V2 with merging). Keep `Compiler` for `verify()` and `refinementPrompt` reuse.
- `HabitTemplates.suggestForGoal(themeName)` fallback stays; add `themeToHabits` synthetic templating when gap.
- New `domain/BlueprintRecreation.kt` orchestrator; new `data/entity/BlueprintAutoPlan.kt`; existing `GrowthPlan/Phase/Metrics`, `WeeklySnapshot`, `ProactiveSuggestion` extended.
- New `Capabilities.create_progressive_blueprint` already correct — wire UI to it.

---

## 3. Intelligent merging — from 500 to minimal

### 3.1 Merging pipeline (creative, high-signal)

**Step — Extract (unchanged, but capped):** `extractRawItems` keeps per-line but adds `candidate.length 6..300`, filters `?`, `startsWith "the/this/it/they/we "` — good. Add `maxRawItems 400` guard + snackbar “Truncated to 400 most actionable lines — split source for full” (mirrors 2MB guard).

**Step — Theme grouping (new):** `sectionName(section)` already canonicalizes. Extend to keyword fallback: if section empty, infer from `candidate` (“meditat”→Mindfulness, “run/walk/push”→Movement, etc.). Comment each mapping.

**Step — Semantic dedup (upgrade `markConflicts`):**
- Current: exact `command|title` lower dedup.
- New: normalize title (`lowercase, trim "i want to/will/should`, strip punctuation, stem `walk/walking/walks`→`walk`), compute Jaccard 2-gram similarity ≥0.8 → merge group. Keep `citation` list (all LNs) not single `citation`. Winner: `priorityBonus` highest + `goal` keyword match + shortest title (habit-friendly). Losers: `REJECTED note="Merged into #id — overlapping"`.

**Step — Prioritise themes:**
- `prioritiseThemes` sorts themes by `goal keyword overlap + user priorityAreas + estimateThemeMinutes fit vs dailyTime`. `theme.estimateMinutes` via `HabitTemplates` avg or synthetic 10 min.
- Enforce user preferred amounts: if user says “≤5 habits total”, `totalWeeks=12 → phases=6 → maxHabitsPerPhase=1` so 6 total, not 15. If `dailyTime=20` and `maxHabitsPerPhase=2`, cap theme count to `dailyTime/10 =2` per phase, queue rest for later phases.

**Step — Minimality dialog (new UX, satisfies #12 “ask preferred amount in words approx”):**
Pre-compile bottom sheet `AmountPicker` — sliders or chips:
- Identities: 1-3 (word-approx 5-10 words each)
- Systems: 1-5
- Goals: 1-4
- Habits: 3-12 (word-approx total, e.g., “~12 words for habit titles”)
- Flows/Routines: 0-4
- Daily time: 15-240 min (default 25)
- Duration: 4-52 weeks (default 12)
- Current level: Tiny→Stretch (self-assessed)
Persist to `Prefs` per project. Show estimate: “We’ll propose 3 habits now (8 min/day) + 9 over 12 weeks (22 min peak).”

**Verification:** 500-line fixture → ≤15 Requirements pre-dedup → ≤8 after merge → ≤3 in phase0. Unit `CompilerV2_MergeTest` checks 4 “walk” variants → 1 habit.

---

## 4. Flood UI fix — collapsible, paginated, searchable

**Current ledger:** `ledgerSection()` appends `item_text_card` per Requirement to `LinearLayout content` — no recycling, no grouping, no search, tip “Tap a requirement to accept or reject it.” Ready count `reqs.count{ACCEPTED && plannedCommand not blank}`.

**Recreated ledger:**

- **Data structure:** `Map<Theme, List<Requirement>>` via `groupIntoThemes`. Theme header shows `theme.name · N items · ~M min/day · priority rank`.
- **Component:** `ui/blueprint/BlueprintLedgerScreen.kt` (Compose) — `LazyColumn` with `stickyHeader` per theme (reuses Journey pattern). Each theme header is collapsible (`SfEntityRow` pattern: `IconButton rotate 180°, AnimatedVisibility count "$count below"`, `count = habitCount`, `expandable = childCount>0`). Default: first theme expanded, rest collapsed. “Expand all / Collapse all” + “Accept theme / Reject theme” bulk actions.
- **Cards:** `SfBlueprintCard` — `text`, `citation` chips (multiple LNs), `status` pill (ACCEPTED=green, REJECTED=muted, CONFLICTED=amber, NEEDS_CLARIFICATION=blue), `note` (merge rationale), `plannedCommand` preview `Plans to run: create_habit "Walk 10 min"`. Tap toggles `REJECTED<->ACCEPTED` with haptic `TICK`.
- **Pagination & search:** Show 20 cards, “Show 20 more” (`hidden` count like `StudioModel.OlderFold`), search field filters `text/citation/theme`, filter chips [All, To Apply, Merged, Gaps].
- **Ready strip:**sticky bottom bar `"$ready ready to apply · $phases phases · $minutes min/day"` + primary `Build phase 1 ($ready)` + secondary `View phases`.

**Reuses:** Journey `SfEntityRow ExpandToggle`, Studio `OlderFold` pattern, `Motion.staggerDelay`.

---

## 5. Incremental phased rollout — a year plan without heroics

**Scenario:** User pastes “Year plan with incremental upgrades, weekly/daily new targets for current habits/systems/goals/identity/flows or add new ones.”

**Correct behavior:** Do not add everything at once. Classify each target as either **phase progression** (same habit’s ladder level increase) or **new habit/system**. Example:

- Week 1-2: Walk 10 min (Tiny→Minimum) + Meditate 2 min
- Week 3-4: Walk 15 min (Standard) + Meditate 5 min + Journal prompt
- Week 5-6: Walk 5km 3×/week + Meditate 10 min + Journal + Read 20 pages
- …

**Engine:**

- `generateProgressivePlan` already does `maxHabitsPerPhase 1..3`, `twoWeekPhases`, `HabitTemplates.suggestForGoal`. For existing habit `title` match, emit `update_habit` with `standardVersion` bump (phase0=tinyStart, 1=minimum, else standard) via `compilePhase` `habit.copy(...)` TODO path — fix TODO: lookup `repo.findHabit(title)` → `habitId`.
- `tellGrowth` integration: After `doExecute` phase0, create `GrowthPlan(phases=phases.map{ GrowthPhase(...) }, currentPhaseIndex=0, upgradePolicy=UpgradePolicy(autoUpgrade, upgradeDay=MON, minWeeksInPhase=2, ...) )` and attach to project.

**Auto scheduling (see §6):** phases 1..N-1 not executed now; they become `BlueprintAutoPlan` entries scheduled by `GrowthEngine.evaluateWeekly` + `ProactiveAiWorker`.

---

## 6. Auto Reinforce — the in-app “when/what/where/how” auto-changer

**Requirement:** “AI also should have a in-app-stored data section for what, when, where and how should be a thing changed in the app automatically, user can trigger via AI chat — call this Auto Reinforce.”

### 6.1 Data model

```kotlin
@Entity(tableName="blueprint_auto_plans")
data class BlueprintAutoPlan(
  @PrimaryKey val id:String = newId(),
  val projectId:String,
  val phaseIndex:Int,
  val what:String, // JSON plannedCommand (create_habit/update_habit/create_flow etc)
  val `when`:String, // ISO trigger: "2026-09-06T07:00" or "WEEKLY:MON 07:00" or "AFTER_CONSISTENCY:3"
  val where:String, // domain: "habit","flow","system","goal","identity"
  val how:String, // mode: "ADD","UPGRADE","REARRANGE","REMOVE","EDIT"
  val conditionJson:String?, // {"minConsistency":60,"maxMissesInRow":2,"minWeeksInPhase":2}
  val status:String, // PENDING|SCHEDULED|APPLIED|SKIPPED|FAILED
  val createdAt:Long, val appliedAt:Long?
)
// DAO: byProject(projectId), pending(now), byPhase
```

Persist via `Database.kt` migration `v5 add blueprint_auto_plans`. `Prefs` switch `growthPlansEnabled` already exists; add `autoReinforceEnabled Boolean` + `autoReinforceMode String "propose"/"auto"` (Q3).

### 6.2 UI — in-app stored section

New section `Auto Reinforce` inside `BlueprintActivity` after `runSection` and before `versionsSection`:

- Card per plan: `Phase 2 · IN 2 WEEKS · WHAT: Upgrade "Walk" 10→15 min · WHEN: Mon 07:00 if consistency ≥60% · WHERE: Habit "Walk" · HOW: UPGRADE · STATUS: Scheduled` + chips [Trigger now][Edit][Skip][Delete].
- Global toggle `Auto Reinforce [on/off]` + mode chips `Propose (default)·Auto-apply` (Q3). `Propose` → `ProactiveSuggestion` card “Ready to upgrade Walk?” with Accept/Dismiss; `Auto-apply` → direct `bus.execute`.
- “Trigger via AI chat” — `Agent` tool `trigger_auto_reinforce(projectId, phaseIndex?)` + natural language “reinforce now” → `Coordinator.interpret` regex `reinforce|auto apply|next phase` → execute pending.

### 6.3 Execution engine

- `work/AutoReinforceWorker: CoroutineWorker` — periodic `PERIODIC 6h` like `ProactiveAiWorker`, plus `evaluateWeekly` hook in `GrowthEngine.evaluate` loop: if `today.dayOfWeek == upgradeDay && condition met → apply`.
- `apply(plan)` → `Snapshots.save` if `autoSnapshot` + destructive, `bus.execute(command, args, Actor.SYSTEM, group=newId())`, update `status APPLIED`, write audit + `BlueprintVersion` entry.

### 6.4 Chat trigger path

`ai/Agent.kt:handle` already does `Coordinator.interpret` → `cloud` → `fallback`. Add `Coordinator` pattern `autoReinforceRegex = Regex("reinforce|next phase|apply pending|auto.*upgrade")`. `MayRun` checks `aiEnabled && autoReinforceEnabled`. Capability `trigger_auto_reinforce` registered in `domain/Capabilities.kt:CATALOG_VERSION 4→5`.

---

## 7. Routine/flow proposal after all mechanics — close the loop

**Requirement:** “After implementing all these, AI should ask and then propose user if routine/flow could be generated or be made better than a previous flow (add/remove/rearrange/edit) and analyse and do similar for all every other app mechanic.”

**Design:**

- **Trigger:** Immediately after `doExecute` + `verify` (BUILD REPORT string), call `PostExecuteAnalyzer.analyze(repo, project, previousStateSnapshot)`. It diffs `Repository.snapshot()` vs `before` across all mechanics: identities, goals, systems, habits, flows, routines, obstacles, reviews.

- **Proposal payload:** Generates `RoutineProposal` / `FlowProposal`:
  ```
  RoutineProposal(type: NEW|IMPROVE, target: Flow/Routine id or null,
    operations: List<Op{ADD(name),REMOVE(name),REARRANGE(oldIndex,newIndex),EDIT(name, patch)}>,
    rationale: String, evidence: citations, impact: timeDelta, preview: Today composition)
  ```

- **UI:** New card `Suggested Improvements` after report: “We noticed your ‘Morning’ flow could be better: Add ‘Journal 2 min’ after ‘Meditate’, move ‘Walk’ earlier (higher energy), remove duplicate ‘Drink water’ (already in Habit Ladder). [Apply][Preview][Dismiss]”. Similar for identities (“becoming someone who reads” → add System “Read after coffee”), goals (“Walk 5km” missing obstacle plan), etc.

- **Telemetry:** `ProactiveSuggestion` type `ROUTINE_IMPROVEMENT` / `MECHANIC_ANALYSIS`. `Insights` already has `analyzePatterns` etc — reuse.

---

## 8. Frameworks brainstorm — countless quality systems

**Requirement:** “Brainstorm countless quality systems and frameworks like this for Blueprint Studio’s functional redesign.”

**Framework catalog (implement as `HabitTemplates` + `FlowTemplates` + `SystemTemplates`, each with science citation):**

| Theme | System name | Trigger | Tiny → Standard | Flow role | Auto Reinforce condition |
|---|---|---|---|---|---|
| Movement | Morning Activation | After wake, drink water | 1 min stretch → 20 min walk | Flow start | consistency 60% 2w |
| Mindfulness | Mindful Pause | Before lunch | 1 breath → 10 min meditate | Flow middle | missesInRow ≤2 |
| Learning | Deep Work Prime | After coffee | open book → 30 min read | Flow block | recoveries ≥2 |
| Nutrition | Plate Setup | Before dinner | fill ½ plate veg → track macros | standalone | weekly snapshot |
| Sleep | Wind-Down | After brush teeth | dim lights → read fiction | PM Flow | evening energy low |
| Focus | Ultradian Sprint | Start work | 5 min plan → 50 min focus | Flow core | ProactiveAi ENERGY |
| Relationships | Connection Ping | After walk | text 1 person → call weekly | Swap plan | social norm |
| Finance | Spend Pause | Before purchase | wait 10 min → log spend | environment friction | impulse log |
| Creativity | Idea Capture | After journal | 1 sentence → 30 min write | anchor lab | weekly review |
| Plus | Obstacle Plan | If [time barrier] then tiny | — | — | miss pattern |
| Plus | Recovery Sprint | After miss | tiny only next opp | — | GrowthEngine DOWNGRADE |

Each template includes `tinyStart/minimum/standard/stretch`, `cueTime/place/anchor`, `days`, `haptic`, `checkpoints`. `generateProgressivePlan` picks top `priorityAreas` first.

---

## 9. Complete panel UI/UX recreation — from list to studio

**Current:** `ScrollActivity` collapsing toolbar + `LinearLayout content` with `textCard`, `sourcesSection` cards, `instructionsSection` `TextInputSheet`, `ledgerSection` every Requirement, `runSection` compile/build buttons, `versionsSection` last 6.

**Recreated:** Full Compose `BlueprintStudioScreen.kt` (material from `docs/BLUEPRINT_STUDIO_PLAN.md` §3-6):

```
Header: project name · State DRAFT→COMPILED→VERIFIED · vN · N sources · N reqs · progress ring (7/10 sources parsed)
Tabs: [Sources][Ledger][Phases][Auto Reinforce][Report]  (like Review segmented)
Sources tab:
  - empty: illustration + [Paste text][Import file] + ISOLATION_NOTE
  - filled: carousel cards kind · lines · chars, long-press delete, reorder, per-source instruction chip
Instructions: prominent TextInputSheet 4 lines "Outrank the documents"
Ledger tab: Theme-grouped collapsible LazyColumn (§4) + search/filter + bulk
Phases tab: timeline (Phase 1 IN 2 WEEKS etc) + metrics + upgradePolicy
Auto Reinforce: §6.2
Run bar: sticky bottom "Compile $ready" + "Build phase 1" + "Undo whole build" + import guard 2MB
Versions: last 6 BlueprintVersion cards + diff dialog added.take(6)/removed.take(6)/changed.take(8)
Report: markdown + [Dismiss] + share
```

**Empty states:** `SfEmptyState` with Lottie `blueprint_loading.json` (from `UI_UX_GRAND_UPGRADE_PLAN.md`).

**Motion:** orchestrated cascade `staggerDelay` per Theme header; `AnimatedVisibility` for expand.

---

## 10. Data & migration, security, undo

- **Migration v5:** `blueprint_auto_plans` table, `blueprint_projects.modelOverride`, `amountPrefs Json`.
- **Policy:** Source prompt-injection isolation stays (`ISOLATION_NOTE`, `isInjectionAttempt`), PDF password never sent to model, cloud refine optional.
- **Undo:** Existing `Snapshots.save` before `doExecute` + `group=newId()` for grouped undo; extend to per-phase undo + `BlueprintVersion` before recompile; `ActivityLog` shows group.

---

## 11. Verification — the 22-point definition of done (abridged)

Per `BLUEPRINT_STUDIO_PLAN.md` §12: source coverage 100%, high-priority verified, no critical conflict unresolved, no blocked tool marked complete, protected objects unchanged unless Full Control, reminder/starter limits or resource-based pass, actual-state assertions pass, assumptions/gaps visible → else `COMPLETED_WITH_GAPS`/`FAILED`. Add torture: paste 500-line year plan → ≤15 ledger cards, phase0=2, Auto Reinforce shows 5 future phases, chat “reinforce now” applies next, routine proposal appears after.

---

## 12. Task breakdown (22 tasks, 3 lanes)

- **Intelligence lane (8):** B1 captureIntent dialog + Prefs → B2 theme grouping + estimate → B3 semantic dedup → B4 progressive plan → B5 AmountPicker UI → B6 migrate compiler → B7 post-analyze proposals → B8 framework templates
- **UI lane (8):** U1 Compose ledger stickyHeader + collapsible → U2 pagination/search → U3 Phases timeline → U4 Auto Reinforce section → U5 run/versions/report polish → U6 empty/skeleton gating → U7 model selector header (Q2) → U8 accessibility/healing
- **Execution lane (6):** E1 BlueprintAutoPlan entity/migration → E2 AutoReinforceWorker + GrowthEngine hook → E3 chat trigger capability → E4 snapshot/group/undo per phase → E5 cloud refine for phases → E6 verification/assertions

**Gates:** `testDebugUnitTest` (new `CompilerV2_MergeTest`, `AutoReinforceTest`), `lintDebug`, `assembleDebug`, `verify.sh --device` API 26/34, `check_*.py`.

**Next:** `APP_REDESIGN_PLAN.md` delivers the calm, sophisticated shell that holds this Studio.
