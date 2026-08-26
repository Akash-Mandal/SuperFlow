# SuperFlow Alpha3 — Plan A: Visual & Experience Grand Redesign

**Version:** alpha3 · August 2026
**Companion document:** [ALPHA3_FUNCTIONAL_PLAN.md](ALPHA3_FUNCTIONAL_PLAN.md) — *Plan B: Functional Expansion*
**Coordination contract:** §14 (Shared Milestones) and §15 (Feature↔Surface Matrix) bind the two plans together. They are designed to be executed **simultaneously**: every functional feature in Plan B lands on a visual surface defined here, and every visual system defined here has at least one consumer named in Plan B.

---

## Table of Contents

1. [Vision: "The Quiet Studio, Refined"](#1-vision-the-quiet-studio-refined)
2. [Current State Audit](#2-current-state-audit)
3. [Design System v3 — Token Architecture](#3-design-system-v3--token-architecture)
4. [Color System v3](#4-color-system-v3)
5. [Typography System v3](#5-typography-system-v3)
6. [Shape, Elevation & Depth System](#6-shape-elevation--depth-system)
7. [Motion System v3](#7-motion-system-v3)
8. [Sensory Layer (Haptics + Sound)](#8-sensory-layer-haptics--sound)
9. [Navigation & Information Architecture Redesign](#9-navigation--information-architecture-redesign)
10. [Component Library v3](#10-component-library-v3)
11. [Screen-by-Screen Redesign](#11-screen-by-screen-redesign)
12. [Data Visualization v3](#12-data-visualization-v3)
13. [Widgets, Icons & System Surfaces](#13-widgets-icons--system-surfaces)
14. [Shared Milestones (with Plan B)](#14-shared-milestones-with-plan-b)
15. [Feature-Surface Matrix](#15-feature-surface-matrix)
16. [Accessibility & Inclusive Design](#16-accessibility--inclusive-design)
17. [Performance & Perceived Speed Budgets](#17-performance--perceived-speed-budgets)
18. [Quality Gates & Acceptance Criteria](#18-quality-gates--acceptance-criteria)

---

## 1. Vision: "The Quiet Studio, Refined"

Alpha2's design language was **Calm Precision**. It succeeded: consistent tokens, high-contrast support, density levels, palettes. But it reads as *well-crafted Material 3* — not as a product with its own unmistakable identity.

**Alpha3's north star:** the app should be recognizable from a single screenshot with the logo removed. A quiet studio that has been *furnished* — warm materials, considered light, everything within reach, nothing shouting.

### The five Alpha3 design principles

| # | Principle | What it changes | Anti-pattern it kills |
|---|-----------|-----------------|----------------------|
| P1 | **One glance answers "what now?"** | Today screen becomes a single focal hierarchy; the next action is always visually dominant | Equal-weight card grids where nothing leads |
| P2 | **Materials, not surfaces** | Cards become layered materials (glass-tinted, paper-grain, ink) instead of flat rounded rects | Every container looking identical |
| P3 | **Motion carries meaning** | Transitions explain hierarchy and causality; check-ins feel like an event, not a toggle | Fade-in defaults, teleporting sheets |
| P4 | **Progress is felt, not read** | Rings, ramps, and heatmaps animate continuously; numbers are secondary to shape | Static percentages |
| P5 | **Personality through restraint** | One serif accent voice, one signature motion (the Flow), one signature gesture language | Gamified confetti, emoji spam |

### Signature identity elements ("the three signatures")

1. **The Flow Line** — a continuous animated line motif used in onboarding, empty states, and the Today hero. It represents the streak/system as a flowing river: bends when you miss, never breaks.
2. **The Breath Ring** — SuperFlow's progress ring redrawn with a subtle breathing animation at rest (2% radius oscillation, 6s period, disabled under reduced-motion). Used for habit completion, sprint progress, and AI activity.
3. **Ink & Paper duality** — light themes are "paper" (warm off-whites, grain texture at ≤2% opacity), dark themes are "ink" (true layered blacks with tinted elevation). This replaces today's flat dark variants.

---

## 2. Current State Audit

### What exists and stays (foundation is good)

- `ui/theme/SfTheme.kt` — CompositionLocal architecture (`LocalSfColors`, `LocalSfTypeStyles`, `LocalSfDensity`, `LocalSfMotion`, `LocalSfShapes`, `LocalSfHighContrast`). **Keep; extend.**
- `design/DesignTokens.kt`, `design/Ramps.kt`, `design/ColorRoles.kt` — token pipeline. **Keep; version to v3.**
- Palettes: Calm, Forest, Ocean, Dusk, Mono (`design/ThemeSelection.kt`). **Keep all five; add two.**
- Dark variants Warm/OLED/Midnight. **Keep OLED/Midnight; redesign Warm into Paper-Warm.**
- Density levels Compact/Comfortable/Spacious, high-contrast mode, serif accent toggle. **Keep.**
- Component library: SfCard, SfHabitCard, SfProgressRing, SfHeatmap, SfBarChart, SfChipGroup, SfEntityRow, SfTextField, SfEmptyState, SfSkeleton, SfHistoryStrip, SfSectionHeader. **Keep APIs; restyle internals per §10.**
- Haptics (`ui/common/SfHaptics.kt`) and sound design (`design/SoundDesign.kt`, `ToneSynth.kt`). **Keep; re-map per §8.**

### Gaps this plan closes

| Gap | Evidence | Fix section |
|-----|----------|-------------|
| Flat visual sameness across cards/screens | All containers share one elevation+shape recipe | §6 |
| Motion exists but is generic (fade/slide) | `ui/theme/SfMotion.kt` lacks shared-axis + physics specs | §7 |
| No true edge-to-edge / status-bar-blend story | Screens pad insets inconsistently | §9.1 |
| Charts are functional but static | `ui/common/Charts.kt` draws once, no entry animations | §12 |
| Navigation is Activity-per-screen without predictive-back polish | Multiple activities in `ui/**/*Activity.kt` | §9 |
| Widgets look utilitarian | `design/WidgetLayout.kt` | §13 |
| No light-source/depth model; default shadows only | SfCard elevation only | §6 |
| Empty states are text-only-ish | SfEmptyState adoption spotty | §10 |

---

## 3. Design System v3 — Token Architecture

### 3.1 Token layering

```
Tier 0  Primitives      raw values (hex ramps, durations, curves, px scales)
Tier 1  Semantic roles  surface.primary, text.emphasis, state.success ...
Tier 2  Component slots sfCard.background, sfHero.title ...
Tier 3  Overrides       user settings (palette, density, contrast, serif, motion level)
```

**Work items**

- New file `design/tokens/TokensV3.kt`: Tier-0 primitives as `@Immutable` objects (no Compose dependency so they stay unit-testable).
- `SfColors`, `SfTypeStyles`, `SfShapeTokens`, `SfMotionSpecs` gain a `v3()` factory; old factories delegate during migration.
- Every Tier-1 role gets its **paired light+dark values defined in the same table row** — no more "derive dark by ramp lookup" drift.
- Add one new composition local: `LocalSfMaterial` (§6.2). No more locals after this.
- New file `design/ScreenScaffold.kt`: `SfScreenScaffold(title, subtitle, hero, content)` enforcing identical header anatomy on every screen.

### 3.2 Migration strategy

- Phase M0: add v3 tokens alongside v2; both compile.
- Phase M1: migrate shared components (`ui/components/*`) to Tier-2 slots only.
- Phase M2: migrate screens; enforce token purity via lint gate (§18).

---

## 4. Color System v3

### 4.1 Palette lineup

| Palette | Character | Changes vs alpha2 |
|---------|-----------|-------------------|
| Calm (default) | Warm neutrals, sage accent | Rebuilt around Paper-Warm light theme; deeper accent ramp for charts |
| Forest | Deep greens, moss | Contrast pass: secondary text ≥ 4.5:1 on all surfaces |
| Ocean | Cool blues | Unchanged hue; new elevation tinting applied |
| Dusk | Violet/amber | Promoted to co-flagship with Calm |
| Mono | Ink/paper grayscale | Becomes the "writer's palette" — pairs with serif accents ON by default |
| **NEW: Terracotta** | Clay, sand, rust accents | Warmest palette; pairs with energy features (Plan B F3) |
| **NEW: Aurora** | Teal→indigo dual-accent | First dual-accent palette; drives gradient tokens (§4.3); pairs with Blueprint Studio (Plan B F2) |

### 4.2 Ink & Paper elevation tinting

Replace flat surfaces with **tonal elevation**: each elevation step tints toward the accent by a fixed curve.

```kotlin
// design/tokens/ElevationTint.kt
fun surfaceFor(elevation: Dp, base: Color, accent: Color, isDark: Boolean): Color {
    val mix = if (isDark) (elevation.value / 24f).coerceIn(0f, 0.12f)
              else -(elevation.value / 96f).coerceIn(-0.04f, 0f)
    return lerp(base, accent, mix)
}
```

- Light ("Paper"): surfaces get *cooler and slightly darker* as they rise (paper stacking).
- Dark ("Ink"): surfaces get *warmer/accent-tinted* as they rise (light-from-above).
- OLED variant pins elevation 0 to pure black and halves the mix curve.

### 4.3 Gradient & glow tokens (used sparingly)

- `gradient.hero`: accent → accent-deep, 135°, used **only** on the Today Focus card and Blueprint Studio header.
- `gradient.aurora`: palette-defined dual-accent; Aurora palette hero + celebration moments (§8.3).
- `glow.breath`: accent @ 12% opacity, blur 24dp — behind Breath Ring at completion only.
- Rule: max one gradient element visible per screen. Lint-enforced.

### 4.4 Semantic state colors

Formalize a full state set (currently ad-hoc): `success`, `gentleMiss` (amber, never red), `recovering` (blue), `paused` (gray-blue), `graduated` (gold), `danger` reserved exclusively for destructive-action confirmation. Misses must never render red anywhere — Evidence over Judgment.

### 4.5 LifeArea identity colors

Each `LifeArea` gets a stable derived hue from the palette ramp, consistent across palettes. Habits, goals, chart series, and heatmap dots inherit it — instant scannability without configuration. Overridable per-habit in Habit Designer (metadata field added in Plan B F5).

---

## 5. Typography System v3

### 5.1 Type scale

Formalize around a modular scale:

| Role | Size/Line | Weight | Notes |
|------|-----------|--------|-------|
| displayHero | 34/40 | 700 | Once per screen max (Today greeting, Studio title) |
| displaySection | 22/28 | 650 | Section headers |
| titleEmphasis | 17/24 | 600 | Card titles, entity rows |
| bodyPrimary | 15/22 | 400 | Default reading |
| bodySecondary | 13/18 | 400 · 80% emphasis | Metadata, captions |
| labelCaps | 11/14 | 600 · +6% tracking · uppercase | Chips, tab labels, overlines |
| numericDisplay | 28/32 | 700 tabular | Streak counts, ring centers — **tabular figures mandatory** |

### 5.2 Serif accent voice

`serifAccents` pref already exists. Alpha3 defines exactly where serif may appear: identity statements, journal entries, review reflections, quote moments in empty states, Graduation certificate (Plan B F9). Everything else stays sans — a deliberate "human voice" register users will feel but not notice.

### 5.3 Dynamic type & scaling

- Full support for Android font scale to 2.0; layout audit per screen (no clipped text at 180% anywhere in §11).
- Numeric displays switch to condensed variant above 1.3 scale rather than wrapping.

---

## 6. Shape, Elevation & Depth System

### 6.1 Shape vocabulary

| Token | Radius | Usage |
|-------|--------|-------|
| shapeHero | 28dp | Hero/Focus cards, Studio header |
| shapeCard | 20dp | Standard cards |
| shapeInner | 14dp | Nested content inside cards |
| shapeControl | 12dp | Buttons, inputs |
| shapeChip | full | Chips, badges |
| shapeSheet | 28dp top corners | Bottom sheets |

Smooth corners (squircle-like, `RoundedPolygon`) for shapeHero and shapeCard — this alone moves perceived quality substantially.

### 6.2 The Material system (new `LocalSfMaterial`)

Three named materials replace "card":

| Material | Recipe | Usage |
|----------|--------|-------|
| **PaperMat** | Surface + 1dp hairline border (accent @ 8%) + grain overlay ≤2% | Content cards in light mode |
| **GlassMat** | 82% surface + background blur where available (fallback scrim) + hairline top-edge highlight | Sheets, floating bars, command palette |
| **InkMat** | Elevation-tinted surface (§4.2), no border | Dark-mode cards |

Components consume materials; they never hand-mix alpha over backgrounds again.

### 6.3 Light source model

Single top-center key light: soft shadows (blur 24–32dp), y-offset biased 2× x-offset, ambient shadow accent-tinted in dark mode. Encapsulated in an `SfSurface(elevation)` wrapper so every elevated element shares one shadow recipe.

---

## 7. Motion System v3

### 7.1 Foundations

- Durations: instant 90ms · fast 150 · normal 240 · slow 380 · cinematic 520.
- Curves: `emphasizedDecel` (entry), `emphasizedAccel` (exit), `springStandard` (0.85 / 380), `springSnappy` (0.7 / 800), `springBreath` (idle loops).
- All `SfMotionSpecs` consumers move onto these five; no ad-hoc tweens remain.

### 7.2 Shared-axis navigation grammar

- Forward navigation: X-axis slide 24dp + fade; outgoing layer −24dp + fade.
- Drill-down (Today → Habit Detail): Z-axis scale 0.94→1.0 entering, parallax + dim exiting.
- Sheets: Y spring with drag-to-dismiss velocity handoff; scrim fades at 0.6× sheet speed.
- Predictive back registered cross-activity; drill-downs reverse the Z-axis.

### 7.3 Signature motions

1. **Check-in bloom** — completing a habit: ring fills with overshoot (springSnappy), center number rolls, a single "ink drop" ripple in accent expands 0→48dp and fades. < 500ms total. Never confetti.
2. **The Flow Line draw** — path animates (`PathMeasure`) on first Today appearance each day; missed days bend the line gently, never break it.
3. **Breath Ring idle** — 2% radius oscillation, pauses while scrolling, disabled under reduced motion.
4. **Number roll-up** — all statistics count from 0 on first composition (static under reduced motion).
5. **Skeleton→content morph** — shimmer resolves into content with 150ms crossfade + 8dp upward shift; skeleton geometry matches final layout exactly.

### 7.4 Motion preference levels

New setting: Full / Reduced / Off. All signature motions declare their Off behavior (opacity-only ≤150ms or static).

---

## 8. Sensory Layer (Haptics + Sound)

### 8.1 Haptic remap (`SfHaptics.kt`)

| Event | Alpha3 pattern |
|-------|----------------|
| Check-in complete | double tick staggered 40ms ("vote cast") |
| Milestone/streak record | heavy click + short ramp |
| Undo performed | single low tick |
| Sheet open/close | tick on settle only |
| Danger confirm | distinct triple-tick warning before irreversible actions |

### 8.2 Sound (via ToneSynth)

Remap to Ink & Paper: warmer sine/triangle blends, lower velocities, master volume slider plus per-event toggles in Appearance settings. New sounds only for: check-in bloom (soft drop), graduation chime (Plan B F9), sprint completion.

### 8.3 Celebration policy

Three magnitudes only: micro (bloom), medium (aurora wash across hero card, 800ms), grand (Graduation full-screen ink-wash reveal). Nothing repeats within 24h — enforced by a celebration budget in prefs.

---

## 9. Navigation & Information Architecture Redesign

### 9.1 Shell

- Persistent shell: **bottom navigation (4 tabs)** — Today · Journey · Insights · Studio. No FAB: creation lives in the **Command Palette** (pull down on Today, or ⊕ in the top bar).
- Top bar per tab: contextual large title collapsing to 56dp compact on scroll; search glyph; overflow.
- True edge-to-edge: transparent status bar, scrim-free nav bar, per-screen `WindowInsets` contract centralized in `ui/common/ScrollActivity.kt`.

### 9.2 Gesture map

| Gesture | Action |
|---------|--------|
| Pull down on Today (at top) | Command Palette (search + quick create + quick actions) |
| Swipe right on habit row | Complete check-in |
| Swipe left on habit row | Open quick editor sheet |
| Long-press habit row | Drag reorder + context menu |
| Two-finger swipe down anywhere | Privacy veil (if app lock enabled) |

All gestures have visible affordances (drag dots, chevron hints); gestures accelerate, never gate.

### 9.3 Screen inventory & routes

Formal route table in `design/Navigation.kt` covering all destinations (existing Activities + new Plan B surfaces: Timeline, Sprint board, Memory viewer, Inbox). Each route declares transition type, insets, scroll-position preservation on return.

---

## 10. Component Library v3

Public APIs preserved; internals restyled. ★ = new component.

| # | Component | Alpha3 spec |
|---|-----------|-------------|
| 10.1 | SfCard | Materials (§6.2), smooth corners, optional `emphasis` slot for hero treatment, press scale 0.98 + shadow lift |
| 10.2 | SfHabitCard | Two-line max; left rail shows LifeArea color; trailing mini Breath Ring (28dp); swipe actions per §9.2 |
| 10.3 | SfProgressRing | Breath animation, gradient stroke option, gap-aware segments for multi-part habits |
| 10.4 | SfEntityRow | Unified list row for habits/goals/systems/identities: leading icon disc (area-colored), title+meta, trailing state chip |
| 10.5 | SfTextField | Floating label, focus glow (hairline accent), error shake ≤4dp, character-count only near limit |
| 10.6 | SfEmptyState | Flow Line illustrated scene + serif micro-copy + primary action; 6 canned scenes (today-clear, no-journal, no-results, first-run, recovery, offline-AI) |
| 10.7 | SfChipGroup | Wrapping chips with selection morph (grows 2dp + fill sweep) |
| 10.8 | SfHeatmap | Rounded cells, month labels, tap-day detail sheet, level legend, cascade entry animation |
| 10.9 | SfBarChart | Animated grow-on-enter, touch scrubbing with value bubble |
| 10.10 | SfBottomSheet | GlassMat, velocity dismiss, inset-aware, grabber pill |
| 10.11 | ★ SfCommandPalette | Full-screen GlassMat search/action surface (powers pull-down + ⊕); results grouped per domain/Search.kt categories |
| 10.12 | ★ SfTimeline | Vertical timeline rail (events, journal, snapshots, activity log) with date grouping and sticky day headers |
| 10.13 | ★ SfStatHero | Large numeric + label + delta arrow + sparkline slot |
| 10.14 | ★ SfSegmentedControl | View switches (Day/Week/Month; Build/Reduce; period pickers) |
| 10.15 | ★ SfConsentCard | Standardized permission/AI-capability explanation card (onboarding, Full Control, reminders) |
| 10.16 | ★ SfNudgeBanner | Slim GlassMat in-app banner for adaptive coaching nudges (Plan B F7) and system messages |
| 10.17 | SfSkeleton | Geometry-exact variants per major screen |

Every component ships with: token-driven theming, reduced-motion variant, high-contrast variant, preview set covering empty/populated/error.

---

## 11. Screen-by-Screen Redesign

Order of prominence = order of daily use.

### 11.1 Today (`today/*`, `screens/TodayScreen.kt`)
- Structure: Greeting block (displayHero + date + energy chip if logged) → **Focus card** (the single next action from Plan B F1; hero material, Breath Ring, big check button) → Today list (SfHabitCards grouped Morning/Afternoon/Evening/Anytime with sticky mini-headers) → Routines strip → horizontal stat minis (streak, week %, momentum).
- Focus card collapses to slim banner when done; "All done" state shows Flow Line scene.
- Minimum Mode renders "Essentials": larger type, essential habits only, muted palette (visual half of Plan B F3).
- Checkpoint flow restyled as full-screen ink-wash moment with large type.

### 11.2 Journey (`journey/*`, `screens/JourneyScreen.kt`)
- Identity-first ordering: Identities carousel (swipeable evidence cards, serif statements) → Goals (progress bars with milestone ticks) → Systems → Obstacle plans accordion.
- Recent evidence/journal/snapshot events rendered in SfTimeline.

### 11.3 Insights (`insights/*`)
- Header: SfStatHero row (momentum, consistency, best window) → segmented control Day/Week/Month → charts stack with scrubbing → per-habit heatmap selector chips.
- All charts animate on enter; numbers roll up.

### 11.4 Studio / AI (`studio/*`, `engine/*`, `blueprint/*`)
- Dark-ink always-on aesthetic regardless of theme (the "workshop at night") with Aurora gradient header.
- Conversation bubbles: user = accent-tinted, AI = InkMat with typing breath indicator.
- Blueprint run view: requirement-ledger rows with source-link chips, live phase timeline, verification checkmarks — visual grammar consumed by Plan B F2.

### 11.5 Review (`review/*`)
- Guided card-stack: one question per card, swipe or tap to answer; serif reflection prompts; end summary with commitment chips.

### 11.6 Onboarding (`onboarding/*`)
- Six-step cinematic flow with the Flow Line drawing across steps; consent cards for notifications/AI; ends by creating first identity + tiny habit with a medium celebration.

### 11.7 Settings
- Appearance tab gains: live-preview palette picker (now 7 palettes), dark variant, motion level, sound mixer (per-event toggles + volume), haptics intensity, serif toggle, density, high contrast.
- Every setting applies live behind the sheet (preview-in-place).

### 11.8 Remaining surfaces
Habit Designer, Habit Detail, Journal, Scorecard, Recovery, Pause, Search, Routine Builder, Activity Log, Lock screen — each receives material pass, motion grammar pass, empty states (§10.6), insets audit. Per-screen one-pagers written during milestone execution.

---

## 12. Data Visualization v3

- One charting kit extended from `ui/common/Charts.kt` + `design/ChartGeometry.kt`: shared axis painter, scrub layer, entry animations, reduced-motion fallbacks.
- Chart types: bar (existing), line/sparkline ★, ring (existing), heatmap (existing), stacked area for time-of-day patterns ★, radial multi-ring for weekly balance ★.
- Color rules: single-hue ramps from palette; semantics from §4.4; ≤4 series per chart.
- Numbers: tabular figures; delta arrows carry accessible text alternatives ("up 12% vs last week").

---

## 13. Widgets, Icons & System Surfaces

- Widgets (`design/WidgetLayout.kt`): Ink/Paper materials approximated in RemoteViews (solid tinted layers, no blur). Sizes: 2×1 next action + check; 4×1 three habits; 4×2 heatmap mini. New 2×2 **Sprint widget** (Plan B F8).
- Adaptive icon: refined bolt-flow mark; monochrome + themed variants verified on Android 13+.
- Splash: animated Flow Line draw via Android 12 SplashScreen API.
- Notifications: accent-colored, action buttons (check in / snooze) matching widget visuals.
- Share cards (`share/ProgressCard.kt`): template matching hero material, serif identity line, palette-aware.

---

## 14. Shared Milestones (with Plan B)

Both plans execute in lockstep; each milestone ships visual + functional work together.

| Milestone | Visual (this plan) | Functional (Plan B) | Exit criteria |
|-----------|--------------------|--------------------|---------------|
| **M0 · Foundation** (wk 1–2) | Tokens v3, materials, motion specs, ScreenScaffold | Data-layer prep: Timeline store, Sprint model, memory schema | Builds green; old+new tokens coexist; token unit tests pass |
| **M1 · Core Loop** (wk 3–5) | Today redesign, SfHabitCard v3, gestures, command palette UI | F1 Quick Capture + Focus engine, F3 Energy-aware days, F5 swipe/habit metadata | Daily loop fully on v3; flags on |
| **M2 · Insight** (wk 6–7) | Insights redesign, chart kit v3, SfTimeline component | F2 Day Replay, F6 Advanced analytics | Insights fully animated; replay renders from Timeline store |
| **M3 · Intelligence** (wk 8–9) | Studio night aesthetic, blueprint run visuals, nudge banners | F2 Blueprint upgrades, F4 AI Memory viewer, F7 Adaptive nudges | Studio on v3; memory + nudge surfaces shipped |
| **M4 · Commitment** (wk 10–11) | Review card-stack, sprint board visuals, consent cards | F8 Commitment Sprints, F9 Graduation ceremony | Sprint lifecycle usable end-to-end |
| **M5 · Polish & Ship** (wk 12) | Widgets, splash, icons, share cards, a11y audit, perf budgets | F10 Experience hub, QA, docs | Quality gates §18 all green; versionName=alpha3, versionCode=5 |

Sequencing rule: **no functional feature merges without its named visual surface**, and vice versa (§15). Feature flags (`Prefs`) allow independent rollout, but both sides of a pair merge within the same milestone.

---

## 15. Feature-Surface Matrix

Traceability contract between the two plans:

| Plan B feature | Visual surfaces/components it consumes | Sections |
|----------------|----------------------------------------|----------|
| F1 Quick Capture, Inbox & Focus engine | SfCommandPalette, SfEntityRow, Focus card hero treatment | §10.11, §10.4, §11.1 |
| F2 Blueprint Studio upgrades + Day Replay | Studio night theme, run timeline visuals, SfTimeline | §11.4, §10.12 |
| F3 Energy-aware days + Minimum Mode polish | Energy chip on greeting, gentle-state colors, Essentials rendering | §11.1, §4.4 |
| F4 AI Memory viewer | SfTimeline + entity detail sheet styling | §10.12 |
| F5 Gesture check-ins + habit personalization | Swipe affordances, check-in bloom, LifeArea color rails | §9.2, §7.3, §4.5 |
| F6 Analytics pack | Chart kit v3 (stacked area, radial rings), StatHero | §12, §10.13 |
| F7 Adaptive coaching nudges | Notification styling, SfNudgeBanner | §13, §10.16 |
| F8 Commitment Sprints | Sprint board screen (new), large Breath Ring, countdown numerals, sprint widget | §10.3, §5.1, §13 |
| F9 Graduation ceremony | Grand celebration (aurora wash), certificate layout, serif voice, chime | §8.3, §5.2 |
| F10 Experience hub | Settings live-preview pickers, sound mixer UI | §11.7 |

Reverse mapping guarantees no orphan polish: Flow Line ← Today/onboarding/replay; Breath Ring ← check-ins/sprints/Focus; GlassMat ← palette/nudges/banners; gradients ← hero cards only.

---

## 16. Accessibility & Inclusive Design

- Contrast: all text roles ≥ 4.5:1 across every palette × dark variant × high-contrast combination; automated matrix test extends `design/Contrast.kt` into CI.
- High-contrast mode adds borders (existing `LocalSfHighContrast`) and disables grain/glow/gradient decorations entirely.
- Touch targets ≥ 48dp; every swipe action duplicated as a button in overflow menus.
- TalkBack: custom semantics for rings/charts ("3 of 5 complete, streak 12 days"); liveRegion announcements for check-in feedback; chart data tables as alternative descriptions.
- Reduced motion honored globally (§7.4), including widgets' idle animations off.
- Font scale 2.0 audit per §5.3.

---

## 17. Performance & Perceived Speed Budgets

| Metric | Budget |
|--------|--------|
| Cold start → Today interactive | ≤ 900ms on mid-range reference device (baseline profile shipped) |
| Frame time during check-in bloom | p99 ≤ 8ms |
| Screen transitions | 0 dropped frames at 60Hz on reference device |
| Recomposition hygiene | Strong-skippability pass on all screens; no unstable lambdas in hot paths |
| APK growth from fonts/materials | ≤ +2.5MB |
| Skeleton→content swap | Never longer than data fetch + 150ms |

Perceived-speed tactics: optimistic check-in UI (state flips instantly, persistence async), skeleton geometry parity, precomputed widget bitmaps.

---

## 18. Quality Gates & Acceptance Criteria

1. **Token purity:** zero hardcoded colors/dp-text-scale outside token files (lint gate).
2. **Palette matrix:** screenshot tests × 7 palettes × {light, paper-dark, OLED, midnight} × {normal, high contrast} — all contrast-clean.
3. **Motion audit:** every navigation path uses shared-axis grammar; zero unstyled default fades.
4. **A11y audit:** TalkBack walkthrough passes on Today, Journey, Insights, Studio, Review.
5. **Performance:** budgets in §17 met on reference device profile.
6. **Consistency:** every Plan B feature demoable using only v3 components (matrix §15 checked both directions).
7. **Regression:** existing instrumented tests (`FirstLaunchFlowTest`, `MainActivityLaunchTest`, etc.) pass with updated selectors only.

---

*This document is the visual half of alpha3. Read together with [ALPHA3_FUNCTIONAL_PLAN.md](ALPHA3_FUNCTIONAL_PLAN.md); conflicts resolve in favor of the matrix in §15.*
