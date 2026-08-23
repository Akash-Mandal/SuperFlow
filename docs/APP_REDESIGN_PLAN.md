# SuperFlow — App Redesign Plan (Plan C)

> **Scope:** Complete, sophisticated, organized premium redesign — the calm, refined shell that holds Core Stability (Plan A) and Blueprint Studio (Plan B).  
> **Companion:** `CORE_STABILITY_PLAN.md` + `BLUEPRINT_STUDIO_RECREATION_PLAN.md` — all 3 ship together, tracked separately.  
> **Inherited decisions:** Q1 B→A→C parallel; Q2 model fetch; Q3 Auto-Reinforce propose default; Q4 5 logos.  
> **North star:** “A quiet studio, not a loud gym.” — Dieter Rams “less but better.”  
> **Date:** 2026-08-23 · **Status:** Approved for build  
> **Pre-read:** `docs/UI_UX_GRAND_UPGRADE_PLAN.md` (1191 lines, 20 chapters), `docs/GRAND_PLAN.md` §9, `docs/BUILD.md`

---

## 0. Why the UI feels unsophisticated & unorganized — a brutally honest audit

The user’s words are not cosmetic: “unsophisticated and very unorganized, theme customization is trash.” That is a *system* judgment, not a color tweak request. The audit of the real codebase shows why the feeling arises, even though engineering is sound:

**Visual language is correct but generic.** `res/values/themes.xml` parent `Theme.Material3.DayNight.NoActionBar`, correct tonal surfaces `sf_neutral_98/100`, `sfSuccess/caution/energy1-5`, shapes `extraSmall..extraLarge`, type `Inter + Source Serif + JetBrains Mono` already exist. `ui/theme/SfTheme.kt` wraps `MaterialTheme` with 6 composition locals (`SfColors, SfTypeStyles, SfDensity, SfMotion, SfShapes, SfHighContrast`) and resolves `SfPalette.Calm/Forest/Ocean/Dusk/Mono` + `SfDarkVariant Warm/Oled/Midnight` via `ColorRoles.schemeFor` + `SurfaceRoles.surfacesFor` (pinned by `RoleTest`, no literals). It is *correct* Material 3 — but it looks like every other M3 app because there is no **editorial system**: no spacing editorial, no density editorial, no layered surface story, no illustration language, no density-aware card anatomy.

**Information architecture is honest but flat.** `design/Navigation.kt` defines `Tab(TODAY,JOURNEY,INSIGHTS,STUDIO)` + `Route(SETTINGS/APPEARANCE/ONBOARDING)` + `placementFor(w,h) → BOTTOM/RAIL/WIDE_RAIL (600/840 dp)`, `MainActivity` ViewPager2 `offscreenPageLimit=2`. It is navigable — but Today shows 11 view types in a single `RecyclerView`; Journey shows a flat list with depth indent 16dp; Insights stacks bar+stats+heat without period story; Studio is text chat. The user never feels hierarchy, never feels progress.

**Motion is gated but not authored.** `DesignTokens.Motion` (INSTANT 50 … DELIBERATE 500, STAGGER 40, ORCHESTRATION_BUDGET 800) + `SfMotionSpecs.tween/spring/bouncy/ringSpring` correctly snap when `motionEnabled==false`. But `Charts.kt` and `Ui.runEntryAnimation` ignore it, and orchestrated sequences are ad-hoc (`TodayScreen 75 staggerDelay(index)`) not authored per screen. No shared-element, no spring personality.

**Theme customization is a checklist, not a studio.** `Prefs` holds `palette/darkVariant/density/highContrast/dynamicColor/serifAccents/monoFigures` + `themeMode` + `appearanceRevision` → `recreate()`, `ThemeSelection.overlaysFor` orders palette→dark→density→contrast, `AppearanceFragment` segmented controls + `PaletteSwatches.colorsFor(context,id)` raw swatch. It *works* — but it feels like a developer toggle sheet, not a premium appearance studio: no live preview, no hue builder, no dark preview, no density preview, no export.

**Result:** The app is engineering-rich but editorially poor. A redesign cannot be “new colors” — it must be a **Calm Precision design system** that turns the same correct tokens into a distinctive, organized, sophisticated experience.

---

## 1. Design philosophy — Calm Precision v2

**Principles (from UI_UX_GRAND_UPGRADE_PLAN.md, distilled):**

| Principle | In practice | Anti-pattern we remove |
|---|---|---|
| Calm Precision | 8-point spacing, 4 density-aware card densities, 200ms spring | Packed dashboard, 0dp breathing |
| Evidence over Judgment | `sfCaution` amber not red, recovery lavender, celebration gold | Red miss, shame streak |
| Depth through Restraint | 6 surface layers + grain 3-5% + gradient 4% wash | Flat + stroke everywhere |
| Intentional Friction | 3-Step Habit Designer preview, review friction | One-tap everything |
| Fluid Continuity | Shared element card→detail, predictive back | Jarring activity slide |
| Warm Authority | Source Serif italic for identity, brief mentor copy | Cheerleader confetti |

**Influences named to keep us honest:** Things 3 (calm density), Linear (type+motion), Notion (flex content), Oura/Whoop (evidence), Muji (restraint), Rams (less but better).

**Material doctrine:** Keep Material 3 as the chassis, but wrap it in `SfTheme` so we own palette/density/motion/haptics — never raw `?colorPrimary` in screens.

---

## 2. Tokens — the invisible grid that makes it feel organized

### 2.1 Spacing — geometric 2xs→3xl

```
2xs  2dp hairline    xs  4dp tight   sm  8dp related  md 12dp card pad
base 16dp standard   lg 24dp section  xl 32dp major  2xl 48dp screen  3xl 64dp hero
```
Replace ad-hoc `16dp/20dp` scatter with `Space.*` in `DesignTokens.kt` + `Dimen`.

### 2.2 Density — user-selectable, live

| Mode | Card pad | List height | Gap | Line spacing | Best for |
|---|---|---|---|---|---|
| Compact | 12dp | 48dp | 8dp | 1.15× | Power, tablet |
| Comfortable (default) | 20dp | 56dp | 12dp | 1.25× | Daily |
| Spacious | 24dp | 64dp | 16dp | 1.40× | Calm, a11y |

Already in `DesignTokens.Density` + `SfDensityMetrics` — redesign just **exposes it live** with preview cards (see §8).

### 2.3 Layers & surfaces

```
0 Background ?colorBackground / gradient
1 Surface cards/sheets (Level 1, 1dp outline fallback)
2 SurfaceVariant filled cards/chips
3 Interactive buttons/FAB/inputs
4 Overlay snackbar/tooltip/dialog (Level 5 12dp)
5 System status/nav
```
Add subtle warmth: paper grain 3-5% overlay (optional toggle), gradient washes at 4% opacity on hero sections (primary), glass blur behind sheets on Android 12+ (`RenderEffect`).

### 2.4 Type — three voices

- **Inter Variable** (primary, SIL OFL) — body, labels, nav. 400 Regular → 500 Medium → 600 SemiBold → 700 Bold. Tabular numerals via `monoFigures` already.
- **Source Serif 4 Variable Italic** — identity statements, quotes, journal at 20sp 1.5× line height — signals reflection, not data.
- **JetBrains Mono** — data/timer/code.

Scale: Display 40/48, Headline L 32/40, Headline M 24/32, Title L 20/28, Title M 16/24, Body L 16/24, Body M 14/20, Label L 14/20 Medium, Label M 12/16, Overline 11/16 tracked uppercase, Data 13/16 mono.

### 2.5 Color — 5 palettes + 3 darks, editorially pinned

| Palette | Primary | Secondary | Character |
|---|---|---|---|
| Calm (default) | Forest #3A7D5C | Amber #B4703A | Paper grounded |
| Forest | Emerald #2D6A4F | Moss #B5A642 | Nature growth |
| Ocean | Teal #1B7A8A | Coral #D4826A | Clarity depth |
| Dusk | Purple #6B5B95 | Rose #C4868A | Reflective evening |
| Mono | Gray #5A5652 | Gray #8A8580 | Distraction-free |

Darks: **Warm Dark #14130F** default, **OLED Black #000000**, **Midnight Blue #0A0E1A**. Dynamic color (Material You) option on Android 12+ only when `palette==CALM` (so explicit choice wins) via `DynamicColors.applyToActivityIfAvailable` first, then overlay wins.

Semantic stays: `sfSuccess` soft green, `sfWarning` amber, `sfCaution` coral (never red), `sfRecovery` lavender, `sfCelebration` gold, `sfEnergy1-5`, `sfLevelTiny→Stretch` 4 greens, `stateSkipped/Missed/Empty`, `skeletonBase/Highlight`. Accent borders per Journey entity (Identity green, Goal amber, System indigo, Habit neutral).

### 2.6 Shape & elevation — tonal, not flat

Radius: NONE 0, 2xs 4 chips, xs 8 inputs, sm 12 list, md 16 card std, lg 24 featured/sheet, xl 32 dialog, FULL 50% avatar. Elevations Level 0 0dp bg → 1 1dp card → 2 3dp selected → 3 6dp FAB/nav → 4 8dp sheet → 5 12dp dialog. Cards variants: Elevated (habit), Filled (section), Outlined (interactive), Accent (identity), Warm (tip), Glass (sheet 12+).

### 2.7 Motion tokens — authored, not ad-hoc

```
SfMotion durationInstant 50 toggle, Quick 150 chip, Standard 300 card/page, Slow 500 chart/orchestrated, Dramatic 800 onboarding/celebration
springSnappy High (chip), springStandard Medium (card/sheet), springGentle Low (page/chart)
Easing: standard CubicBezier(0.2,0,0,1), decelerate, accelerate, linear
Orchestration: STAGGER 40ms, STAGGER_MAX_ITEMS 8, BUDGET 800ms — last finishes <800ms even with 50 cards
```

Shared elements: habit Today/Journey → HabitDetail (card morph), entity Journey → editor sheet (expand), ProgressRing Today → Insights (ring scales), Studio message → Blueprint (card expands).

Micro: check-in circle fill spring bounce + check draw 200ms; skip fade 60% + slide 4dp 150ms; miss amber pulse 300ms; recovery lift + wash 400ms; add row slide 200ms; delete collapse 250ms; pull refresh custom ring draw; tab crossfade 8dp 200ms; streak subtle shimmer (no confetti).

### 2.8 Haptics — 10-pattern vocabulary

Tick chip/toggle `EFFECT_TICK`, Click card `EFFECT_CLICK`, HeavyClick check `EFFECT_HEAVY_CLICK`, DoubleTick skip, Success day complete (tick+100ms+tick), SoftThud drag start `VibrationEffect 20ms low`, Rumble pull threshold 50ms medium, Pulse error 30ms high, Rising energy up (3 ascending ticks), Falling down. Intensity levels Off 0×, Subtle 0.5×, Normal 1× default, Strong 1.5× via `SfHaptics.scaled` + `Accessibility.animates` guard. 4 levels user-selectable in Experience tab with Test button cycling.

Sound opt-in: chime 0.5s check, bell 1.2s 100%, page-turn 0.3s review, whoosh 0.2s swipe — <40dB, pitch-matched, quiet-hours muted, toggleable.

---

## 3. Navigation & IA — from 5 tabs to calm adaptive

**Current 5+1:** `Navigation.tabAt(position)` Today/Journey/Insights/Studio + Settings Route, `ViewPager2 offscreenPageLimit=2`, `BottomNavigationView`/`NavigationRailView` dual.

**Proposed 4 primary + avatar:**

| Tab | Content | Why |
|---|---|---|
| Today | Focus, Do Now, flows, Tiny/Minimum, checkpoints, recovery | Primary surface |
| Journey | Identity→Goal→System→Habit tree + Scorecard/Flows/Review tools | Growth hierarchy |
| Insights | Repetitions, consistency, recovery, identity evidence, energy/challenge, trends | Evidence |
| Studio | Ask Studio + Blueprint + AI Engine unified | AI as workspace |

**Settings** moves to avatar/profile button in Today header (Gmail/Linear pattern) → full-screen categorized settings with `[🎨 Appearance] [⚡ Experience] [🤖 AI] [🔔 Reminders] [📊 Data]` tab row.

**Adaptive:** phone portrait bottom bar, phone landscape/foldable navigation rail (80dp / 220dp wideRail via `Navigation.railWidth`), tablet persistent rail + content pane `contentWidth max 600` + `twoPane(w)`.

**Gestures:** swipe right on habit = Standard check, swipe left = Skip, long-press = context menu, pull Today = peek Plan Tomorrow, double-tap ring = jump Insights, pinch heatmap = zoom week/month/year, swipe tabs ViewPager2 gentle overscroll, predictive back with shared element return.

**Shortcuts (4):** Check in (Today), Add habit (Journey), Ask Studio (Studio), Review (Insights) via `Navigation.shortcuts` already.

---

## 4. Component library — Compose-first, hybrid intact

Keep hybrid: `design/Rendering.kt` `isCompose(tab)` keeps View for secondary screens, Compose via `ComposeView/setContent` for primary. Migrate gradually: first `SfCard`/`SfChipGroup`/`SfTextField`/`SfSectionHeader`, then Today+Journey, then Insights+Studio.

| Component | Role |
|---|---|
| `SfCard` | 6 variants Elevated/Filled/Outlined/Accent/Warm/Glass |
| `SfHabitCard` | HistoryStrip + level chips pill + swipe + check spring |
| `SfProgressRing` | Gradient + expandable detail, ringSpring |
| `SfBarChart` | Touch tooltip, gesture zoom |
| `SfHeatmap` | Pinch-zoom, month labels, scroll-snap |
| `SfHistoryStrip` | 14-day dot row, tap detail |
| `SfEntityRow` | Identity/Goal/System/Habit with accent border, expand toggle, count badge |
| `SfChipGroup` | Single/multi adaptive |
| `SfTextField` | Count + validation + fade |
| `SfBottomSheet` | Glass + drag handle |
| `SfEmptyState` | Lottie + action |
| `SfSkeleton` | Content-aware shimmer (gated) |
| `SfSnackbar` | Undo pattern |
| `SfStatCard` | Animated metric |
| `SfSectionHeader` | Label + action |

**Illustration system:** SVG VectorDrawable, line 1.5dp rounded, palette 40-60% saturation, motifs seeds/rings/flowing/sunrise/compass, subtle slow loop max 2s muted.

---

## 5. Screen-by-screen — the organized, sophisticated moments

### 5.1 Today — from list to rhythm

```
☀ Good morning — Display 40/48 variable     34
Wednesday, August 19 — Label M muted
┌──67% 4/6 ──┐ animated ring, tap expands to time-of-day breakdown
Identity overline  "I am becoming someone who moves every day" — Source Serif italic 20sp, 47 votes mono
Daily Focus 2/3  ☐ Review brief ☑ Call dentist ☐ add inline — swipe right = done with spring
Your Habits  ○ Walk 10 min 7d ▸ After breakfast Tiny·Min·Std·Stretch — history strip inline
Energy ●●●○○ 3/5 animated dots
Checkpoints [Morning][Midday][Eve] filled active
```

Improvements: interactive ring, inline history (no detail open), swipe quick actions, animated dot energy, current checkpoint highlight, custom pull refresh, skeleton, orchestrated cascade greeting 0ms → date 50ms → ring 100ms (700ms draw) → identity 200ms → habits 300ms+80ms/card (fits 800ms budget).

### 5.2 Journey — from flat to tree

```
Journey · Identity → Goal → System → Habit breadcrumbs
[Score][Flows][Review] tool row icons
Identities +  (accent green left border, life area emoji, 3 goals)
  Goal +  "Because I want to…" why italic, System → tap expands chain
Habits draggable, long-press menu, contextual Add per section, dormant alpha 0.55, orphan emphasis, empty illustrations.
```

Visual hierarchy tree + downstream count expandable + drag reorder + connection weight.

### 5.3 Insights — from static to interactive

Interactive bar/heatmap/heat with touch tooltips, period switcher 7d/30d/90d/Year crossfade `fadeIn quick + fadeOut fast`, heatmap progressive draw on scroll, habit comparison overlay, energy scatter, milestone gold dots at 7/21/66/100, long-press chart export via `FileProvider` share.

### 5.4 Studio — from chat to workspace

```
Studio pill ⚡ Full Control active — Provider gpt-4o-mini
[Blueprint][Audit][Plan] quick chips horizontal scroll
🤖 rich card "..." via MainBrain  |  You: "..." right-aligned
Rich input bar Tell Studio what to do  🎤 + attach (Plan A media) + model chip ▼ + waveform
```

Rich cards with inline `routeLabel` + `groupId`, suggestions/project cards, typing breathing dots (gated), undo via `StudioModel.Turn.state`.

### 5.5 Secondary (organized, not loud)

HabitDetail collapsing header + tabs Overview/Design/History/Obstacles with heat tap detail + ladder ladder-viz + inline contract + FAB quick check. HabitDesigner 6-step dots + live card preview + schedule week visual + contract preview.

---

## 6. Data viz — evidence, never gamification

Current Canvas `RingView/BarView` ValueAnimator 700ms DecelerateInterpolator + `SfBarChart` + `SfHeatmap` Canvas + `SfHistoryStrip` dots. Upgrade to Compose Canvas with gradient ticks, gesture zoom, `drawWithCache`, pinch, scroll-snap. New: consistency line (rolling 7d), energy scatter, timing clock face, recovery arc (miss→return trend), vote counter, weekly rhythm calendar overlay.

---

## 7. Onboarding reimagined — 6 steps with editorial

From 8 linear text steps to 6 rich:

1 Welcome hero flowing shapes → 2 Who are you becoming? cycling example cards → 3 What matters? goal+why coaching → 4 One small habit AI suggestion animation → 5 When/where? time+anchor schedule → 6 Your first day live Today preview.

Full-bleed illustration 30%, connected line progress not percent bar, shared-element morph, always-visible Skip → demo workspace, replay in Settings.

---

## 8. Appearance & Experience tabs — the premium studio

**From flat list to categorized studio:**

```
👤 [Profile]
[🎨 Appearance] [⚡ Experience] [🤖 AI] [🔔 Reminders] [📊 Data]

🎨 APPEARANCE
 Theme [Light][Dark][System] segmented + preview 3 cards side-by-side
 Palette ● Calm ● Forest ● Ocean ● Dusk ● Mono ● Dynamic swatches (live card preview on pick)
 Dark style ○ Warm Dark ○ OLED Black ○ Midnight Blue radios
 Typography [Small/Default/Large/XL] + [Inter/System/Serif]
 Density [Compact][Comfortable][Spacious] live preview cards
 Visual effects Animations/Paper grain/Blur/Gradient washes toggles
 App Icon [Default][Dark][Minimal][Mono] picker (warns home-screen remove via AppIcons)

⚡ EXPERIENCE
 Haptics on + Intensity Off/Subtle/Normal/Strong + [Test haptics]
 Sounds off + Volume 40% + [Preview]
 Celebrations day/milestone on + Style Subtle/Moderate/Full
 Gestures swipe/long/refresh toggles
 Navigation tab labels Always/Selected/Never + FAB on + Predictive back on
 Checkpoints on + Morning 08:00/Midday 13:00/Evening 20:30 + Energy on
```

Live preview miniature habit card updates with `SfThemeFromPrefs` without recreating; Reset per section; Export theme JSON.

---

## 9. Illustrations, widget, accessibility, perf — sophistication is detail

**Illustrations:** Lottie/Rive subtle looping muted: empty Today sunrise breathing, Journey seed sprout, Insights compass needle settle, day complete golden light expand, recovery wave “Welcome back”, onboarding flowing identity→habit, Blueprint pages into grid.

**Widget (Glance Compose):** Small 2×2 ring+%, Medium 4×2 ring+top habit+check, Large 4×4 all habits+buttons, Wide 5×2 bar+focus+next, dynamic color, interactive check without opening app, subtle shimmer, contextual time-of-day, round corners matching shape system.

**A11y:** contentDescription+stateDescription=Expanded/Collapsed already → keep; semantic headings, live region check changes, custom swipe actions, font 0.85×-2.0× without clip, high contrast stroke raise, reduced motion via `SfTheme.motion.enabled` + `Accessibility.animates`, monoFigures guard, 48dp min (fix 17 exceptions), keyboard + adaptive tablet nav.

**Perf perceived:** skeleton shimmer content-aware shapes (gated), optimistic check-in (<50ms) with revert shimmer on fail, no spinner for local ops, VectorDrawable + lazy Lottie + subset fonts + R8, splash `?colorBackground` + centered `ic_launcher_fg` not blank white, defer WorkManager/widget `AppBackground.launch`, cache Insights.

---

## 10. Implementation — 5 phases over 14 weeks, hybrid safe

**Phase 1: Foundation (W1-3)** Design system, theme engine, core components, Appearance/Experience tab skeleton, Prefs for new appearance.  
**Phase 2: Today & Motion (W4-6)** Today Compose rewrite, SfHabitCard swipe+spring, SfProgressRing spring, shared elements, orchestrated load, 8-pattern haptics, custom pull refresh, Lottie empties.  
**Phase 3: Journey & Insights (W7-9)** Journey Compose tree+reorder, Insights interactive charts, period switcher, new viz.  
**Phase 4: Studio & System (W10-12)** Merge Studio (Plan A media+model selector already), onboarding redesign, Glance widgets, sound opt-in, a11y audit, tablet nav.  
**Phase 5: Refinement (W13-14)** Micro-tune springs/timings, 3 dark variants, 5 icons (Plan A), TalkBack + profiling to 60fps <16ms, docs.

**Hybrid guard:** `Rendering.isCompose(tab)` keeps View fallbacks live; fallback flag per screen.

---

## 11. Metrics — what “sophisticated” means measurably

Visual: every screen uses tokens, 5 palettes × light/dark, dynamic on 12+, Inter/Source Serif rendered. Motion: spring only, orchestrated <800ms, reduced motion off. Haptics 8+ patterns, 4 intensities. A11y TalkBack pass, 2.0× font no break, 48dp everywhere. Perf cold <1.5s, tab <100ms, check <50ms, 0 jank on scroll. Settings Appearance 6 sections + Experience 6 + live preview.

## 12. Risk & ownership

Risk: Compose migration fatigue → mitigate lane isolation + View fallback flag. Risk: grain/blur perf → gate behind `isHighEnd()` + toggle. Risk: dark OLED contrast fails AA → test with `Contrast.kt` 3.95:1. Owner: `design/` (tokens) + `ui/theme/` (SfTheme) + `ui/components/` + `ui/screens/` + `ui/settings/AppearanceFragment` + `res/`.

---

*This plan makes “unsophisticated & unorganized” impossible: every pixel sits on an 8-point grid, every color on a 5-palette system, every motion on an 800ms budget, every density on a preview card. Calm is a feature.*
