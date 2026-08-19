# SuperFlow — Grand UI/UX Upgrade Plan

**Version:** 1.0 · August 2026  
**Scope:** Complete interface, interaction, haptic, visual, and experiential overhaul  
**Goal:** Transform SuperFlow from a well-built Material 3 app into a *frontier-level* personal growth experience — the calmest, most refined, most intentional habit app on Android.

---

## Table of Contents

1. [Philosophy & Design Principles](#1-philosophy--design-principles)
2. [Current State Assessment](#2-current-state-assessment)
3. [Architecture Modernization](#3-architecture-modernization)
4. [Design System v2: "Calm Precision"](#4-design-system-v2-calm-precision)
5. [Typography System](#5-typography-system)
6. [Color System v2](#6-color-system-v2)
7. [Shape & Elevation System](#7-shape--elevation-system)
8. [Motion & Animation System](#8-motion--animation-system)
9. [Haptic & Sensory System](#9-haptic--sensory-system)
10. [Navigation & Information Architecture](#10-navigation--information-architecture)
11. [Screen-by-Screen Redesign](#11-screen-by-screen-redesign)
12. [Component Library](#12-component-library)
13. [Data Visualization System](#13-data-visualization-system)
14. [Onboarding Reimagined](#14-onboarding-reimagined)
15. [Settings: Appearance & Experience Tab](#15-settings-appearance--experience-tab)
16. [Widget Redesign](#16-widget-redesign)
17. [Accessibility](#17-accessibility)
18. [Performance & Perceived Speed](#18-performance--perceived-speed)
19. [Implementation Phases](#19-implementation-phases)
20. [Metrics & Success Criteria](#20-metrics--success-criteria)

---

## 1. Philosophy & Design Principles

### The North Star
SuperFlow should feel like **a quiet studio, not a loud gym**. Every pixel should communicate calm confidence. The interface should disappear, leaving only the user and their intentions.

### Core Design Principles

| Principle | Meaning | Anti-Pattern |
|-----------|---------|-------------|
| **Calm Precision** | Deliberate, measured, no visual noise | Flashy gamification, confetti explosions |
| **Evidence over Judgment** | Data illuminates, never scolds | Red streaks, shame-inducing missed-day alerts |
| **Depth through Restraint** | Richness comes from spacing, typography, and subtle motion | Packed dashboards, information overload |
| **Intentional Friction** | Some moments should slow the user down (reviews, identity) | One-tap everything, mindless check-ins |
| **Fluid Continuity** | Every screen flows naturally into the next | Jarring transitions, context loss |
| **Warm Authority** | The app speaks like a trusted mentor, not a cheerleader | Toxic positivity, hollow encouragement |

### Design Influences
- **Things 3** (iOS) — masterful information density with calm
- **Linear** — precision typography and motion
- **Notion** — flexible content within consistent structure
- **Oura / Whoop** — health data presented as evidence, not gamification
- **Muji** — restraint as a feature, not an absence
- **Dieter Rams** — "less but better"

---

## 2. Current State Assessment

### What Works Well
- Warm, paper-like color palette with intentional miss-as-amber
- Material 3 theming with proper tonal surfaces
- Edge-to-edge with correct inset handling
- Hand-drawn animated charts (ProgressRing, BarChart, HeatmapView)
- DiffUtil-powered RecyclerView updates
- Haptics on key interactions
- Bottom sheet editors with large corner radius

### What Needs Upgrading

| Area | Current | Target |
|------|---------|--------|
| **UI Framework** | XML Views + RecyclerView | Jetpack Compose + XML hybrid |
| **Typography** | System sans-serif | Custom typeface with variable weight |
| **Color** | Static palette, no dynamic color | Dynamic color (Material You) + palette switching |
| **Motion** | Basic slide-up, chart reveals | Shared elements, spring physics, orchestrated sequences |
| **Haptics** | Two patterns (tap, confirm) | Full haptic vocabulary (8+ patterns) |
| **Navigation** | 5-tab bottom nav, ViewPager2 | Adaptive nav + gesture shortcuts + predictive back |
| **Empty States** | Text-only cards | Illustrated, contextual, actionable |
| **Loading** | No skeleton states | Shimmer skeletons with content-aware shapes |
| **Charts** | Basic Canvas drawings | Rich animated visualizations with gestures |
| **Dark Mode** | Single dark palette | OLED black + warm dark + system-adaptive |
| **Accessibility** | Minimal | Full screen reader, reduced motion, contrast, scaling |
| **Settings** | Flat toggle list | Categorized, searchable, with dedicated Appearance tab |
| **Widget** | Basic text + progress bar | Rich interactive widget with multiple sizes |
| **Sound** | None | Optional ambient sounds for focus rituals |
| **Gestures** | Tap only | Swipe, long-press, pinch, drag |
| **Transitions** | Activity slide | Shared element transitions, morphing containers |

---

## 3. Architecture Modernization

### 3.1 Gradual Jetpack Compose Migration

**Strategy:** Compose-first for new screens, Compose interop for existing screens.

```
Phase 1: Compose for new components (cards, charts, settings tab)
Phase 2: Compose for Today and Journey (highest impact)  
Phase 3: Compose for Insights and Coach
Phase 4: Full Compose, remove XML layouts
```

**Why Compose:**
- Declarative UI eliminates the adapter/viewholder boilerplate (TodayAdapter is ~200 lines of imperative binding)
- Built-in animation APIs (animate*AsState, AnimatedVisibility, spring physics)
- Material 3 Compose has richer component set than XML M3
- Composition-based rendering is more testable
- Predictive back gesture support is native

**Hybrid Approach:**
- Keep `ScrollActivity` and XML layouts for secondary screens initially
- Use `ComposeView` inside existing fragments for progressive enhancement
- `AndroidView` composable for custom Canvas charts during migration

### 3.2 Theme Engine

Create a centralized `SfTheme` Compose theme that wraps Material 3:

```kotlin
@Composable
fun SfTheme(
    palette: SfPalette = SfPalette.Calm,    // Calm, Forest, Ocean, Dusk, Mono
    isDark: Boolean = isSystemInDarkTheme(),
    useDynamicColor: Boolean = true,
    typography: SfTypography = SfTypography.Default,
    density: SfDensity = SfDensity.Comfortable,
    motion: SfMotion = SfMotion.Standard,
    content: @Composable () -> Unit
)
```

### 3.3 State Management

- Migrate from `StateFlow<List<Row>>` to Compose `State` with `collectAsStateWithLifecycle()`
- Introduce `UiState` sealed interfaces per screen for loading/empty/content/error states
- Add `SfSnackbar` composable with consistent undo pattern

---

## 4. Design System v2: "Calm Precision"

### 4.1 Spacing Scale

Replace the current ad-hoc spacing with a geometric scale:

```
2xs  =  2dp    Hairline gaps
xs   =  4dp    Tight element grouping
sm   =  8dp    Related items
md   = 12dp    Card internal padding
base = 16dp    Standard separation
lg   = 24dp    Section separation
xl   = 32dp    Major section breaks
2xl  = 48dp    Screen-level breathing room
3xl  = 64dp    Hero/empty state spacing
```

### 4.2 Density Modes

User-selectable content density (in the new Appearance settings tab):

| Mode | Card Padding | List Item Height | Line Spacing | Best For |
|------|-------------|------------------|-------------|----------|
| **Compact** | 12dp | 48dp | 1.15× | Power users, tablets |
| **Comfortable** (default) | 20dp | 56dp | 1.25× | Daily use |
| **Spacious** | 24dp | 64dp | 1.4× | Calm reading, accessibility |

### 4.3 Layer System

Every screen is composed of semantic layers:

```
Layer 0: Background        (colorBackground / gradient)
Layer 1: Surface           (cards, sheets)
Layer 2: Surface Variant   (filled cards, chips)
Layer 3: Interactive        (buttons, FABs, inputs)
Layer 4: Overlay           (snackbars, tooltips, dialogs)
Layer 5: System            (status bar, navigation bar)
```

### 4.4 Surface Treatments

- **Glass surfaces** — subtle `BlurHash` behind bottom sheets on Android 12+
- **Grain texture** — optional paper grain overlay at 3-5% opacity for warmth
- **Gradient washes** — very subtle directional gradients on hero sections (primary at 4% opacity)

---

## 5. Typography System

### 5.1 Custom Typeface

Replace system `sans-serif` with a curated typeface. Recommended options:

| Typeface | Rationale | License |
|----------|-----------|---------|
| **Inter** (primary) | Best-in-class screen readability, variable weight, excellent numerals | SIL OFL |
| **Source Serif 4** (accent) | Warm, literary feel for identity statements and quotes | SIL OFL |
| **JetBrains Mono** (data) | Tabular numerals for stats, timers, code-like precision | SIL OFL |

### 5.2 Type Scale

```
Display      40/48sp  — Hero greeting ("Good morning")
Headline L   32/40sp  — Screen titles
Headline M   24/32sp  — Section titles  
Title L      20/28sp  — Card titles, toolbar
Title M      16/24sp  — List item primary
Body L       16/24sp  — Primary reading text
Body M       14/20sp  — Secondary text
Label L      14/20sp  — Button labels, chip text (medium weight)
Label M      12/16sp  — Overlines, captions
Overline     11/16sp  — Section headers (tracked, uppercase)
Data         13/16sp  — Stats, numerals (mono)
```

### 5.3 Variable Weight

Use Inter's variable weight axis for subtle emphasis without bold/normal binary:

```
Regular    400  — Body text
Medium     500  — Labels, navigation
SemiBold   600  — Emphasis, card titles
Bold       700  — Display, headlines only
```

### 5.4 Typography in Identity Statements

Identity cards and journal entries use **Source Serif 4** in italic at 20sp with generous line height (1.5×) — a typographic signal that this is reflection, not data.

---

## 6. Color System v2

### 6.1 Dynamic Color (Material You)

On Android 12+, offer the user's wallpaper-derived palette as an option alongside the curated palettes.

```kotlin
val dynamicLight = dynamicLightColorScheme(context)
val dynamicDark = dynamicDarkColorScheme(context)
```

### 6.2 Palette Variants

Five curated palettes the user can select in the new Appearance tab:

| Palette | Primary | Secondary | Character |
|---------|---------|-----------|-----------|
| **Calm** (current) | Forest green #3A7D5C | Warm amber #B4703A | Grounded, paper-like |
| **Forest** | Deep emerald #2D6A4F | Moss gold #B5A642 | Nature, growth |
| **Ocean** | Deep teal #1B7A8A | Coral warm #D4826A | Clarity, depth |
| **Dusk** | Muted purple #6B5B95 | Rose gold #C4868A | Reflective, evening |
| **Mono** | Warm gray #5A5652 | Warm gray #8A8580 | Minimal, distraction-free |

### 6.3 Dark Mode Variants

| Variant | Background | Surface | Best For |
|---------|-----------|---------|----------|
| **Warm Dark** (current) | #14130F | #1C1B17 | Default night mode |
| **OLED Black** | #000000 | #0D0D0D | Battery saving, AMOLED |
| **Midnight Blue** | #0A0E1A | #121624 | Night reading |

### 6.4 Semantic Color Tokens

Extend beyond Material 3's built-in roles:

```
sfSuccess       — Soft green for completions (never harsh)
sfWarning       — Warm amber for attention
sfCaution       — Muted coral for misses (NOT red)
sfInfo          — Tertiary indigo
sfRecovery      — Soft lavender for recovery actions
sfCelebration   — Gentle gold for milestones (subtle, not flashy)
sfEnergy1..5    — Five-step energy gradient (cool blue → warm amber)
sfLevelTiny     — Lightest green
sfLevelMinimum  — Light green
sfLevelStandard — Primary green
sfLevelStretch  — Deep green
```

### 6.5 Contextual Accent Colors

Each Journey entity type gets a subtle left-border accent:
- **Identity**: Primary green
- **Goal**: Secondary amber
- **System**: Tertiary indigo
- **Habit**: Surface variant (neutral)

---

## 7. Shape & Elevation System

### 7.1 Corner Radius Scale

```
None       0dp    Full-width elements
2xs        4dp    Chips, small badges
xs         8dp    Input fields, toggles
sm        12dp    List items
md        16dp    Cards (standard)
lg        24dp    Cards (featured), sheets
xl        32dp    Dialogs, hero cards
Full      50%     Avatars, circular buttons
```

### 7.2 Elevation Model

Replace flat-with-stroke cards with a proper tonal elevation system:

```
Level 0: 0dp  — Background elements
Level 1: 1dp  — Cards (default), with 1dp outline as fallback
Level 2: 3dp  — Active/selected cards
Level 3: 6dp  — FABs, bottom nav
Level 4: 8dp  — Bottom sheets, navigation drawers
Level 5: 12dp — Dialogs
```

### 7.3 Card Variants

| Variant | Use | Treatment |
|---------|-----|-----------|
| **Elevated** | Habit cards, entity cards | Level 1 + subtle shadow |
| **Filled** | Section headers, inline content | SurfaceVariant fill, no shadow |
| **Outlined** | Interactive areas | Surface + 1dp outline |
| **Accent** | Identity cards, celebrations | PrimaryContainer fill |
| **Warm** | Coaching tips, "Worth knowing" | SecondaryContainer fill |
| **Glass** | Bottom sheets (Android 12+) | Blur + translucent |

---

## 8. Motion & Animation System

### 8.1 Motion Tokens

```kotlin
object SfMotion {
    val durationInstant  = 50    // Toggle states
    val durationQuick    = 150   // Button presses, chip selection
    val durationStandard = 300   // Card transitions, page changes
    val durationSlow     = 500   // Chart reveals, orchestrated sequences
    val durationDramatic = 800   // Onboarding, celebrations

    val springSnappy   = Spring.StiffnessHigh     // Toggles, chips
    val springStandard = Spring.StiffnessMedium    // Cards, sheets
    val springGentle   = Spring.StiffnessLow       // Page transitions, charts
}
```

### 8.2 Shared Element Transitions

Implement Compose shared element transitions for key flows:

| Transition | From → To | Effect |
|-----------|-----------|--------|
| **Habit card → Detail** | Today/Journey → HabitDetail | Card morphs into detail header |
| **Entity card → Editor** | Journey → Sheet | Card expands into editor |
| **Progress ring → Insights** | Today → Insights tab | Ring scales into chart container |
| **Coach message → Blueprint** | Coach → BlueprintActivity | Message card expands |

### 8.3 Micro-Animations

| Interaction | Animation |
|------------|-----------|
| **Check-in** | Circle fills with spring bounce + check mark draws in (200ms) |
| **Skip** | Card gently fades to 60% opacity, slides right 4dp (150ms) |
| **Missed** | Warm amber pulse from center outward (300ms) |
| **Recovery** | Card lifts slightly, primary color washes in from left (400ms) |
| **Add focus** | New row slides in from top with fade (200ms) |
| **Delete** | Row collapses height to 0 with fade (250ms) |
| **Pull to refresh** | Custom indicator: ring draws progressively (not Material default) |
| **Tab switch** | Content crossfade with 8dp upward slide (200ms) |
| **Streak celebration** | Subtle particle shimmer along progress ring (no confetti) |
| **Day complete** | Ring fills to 100%, gentle gold pulse, then settles |

### 8.4 Orchestrated Sequences

**Today screen load sequence** (staggered, total ~600ms):
1. Greeting text fades in (0ms)
2. Date slides up from below (50ms delay)
3. Progress ring draws arc (100ms delay, 700ms duration)
4. Identity card slides in (200ms delay)
5. Habit cards cascade in (300ms base + 80ms per card)

**Onboarding step transition:**
1. Current content fades out + slides left (200ms)
2. Progress bar advances with spring (200ms)
3. New content fades in + slides from right (200ms, 100ms delay)

### 8.5 Lottie / Rive Integration

Add rich illustrations for key moments:

| Moment | Animation |
|--------|-----------|
| **Empty state: Today** | Gentle sunrise illustration with slow breathing |
| **Empty state: Journey** | Seed/sprout illustration |
| **Empty state: Insights** | Compass illustration, needle slowly settles |
| **Day complete** | Soft golden light expanding outward |
| **Recovery welcome** | Gentle wave, text: "Welcome back" |
| **Onboarding welcome** | Abstract flowing shapes representing identity→habit chain |
| **Loading Blueprint** | Pages flowing into a structured grid |

These should be **subtle, slow, and looping** — never distracting. Max 2 seconds per loop, muted colors.

---

## 9. Haptic & Sensory System

### 9.1 Haptic Vocabulary

Expand from 2 patterns to a complete sensory language:

| Pattern | Trigger | API |
|---------|---------|-----|
| **Tick** | Chip selection, toggle switch | `EFFECT_TICK` (Android 13+) |
| **Click** | Button press, card tap | `EFFECT_CLICK` |
| **Heavy Click** | Check-in confirmation | `EFFECT_HEAVY_CLICK` |
| **Double Tick** | Skip action | `EFFECT_DOUBLE_CLICK` |
| **Success** | Day completion, milestone | `EFFECT_TICK` + 100ms + `EFFECT_TICK` |
| **Soft Thud** | Drag start, long press | `VibrationEffect(20ms, low amplitude)` |
| **Rumble** | Pull-to-refresh threshold | `VibrationEffect(50ms, medium amplitude)` |
| **Pulse** | Error, validation fail | `VibrationEffect(30ms, high amplitude)` |
| **Rising** | Energy slider increase | Ascending 3-tick pattern |
| **Falling** | Energy slider decrease | Descending 3-tick pattern |

### 9.2 Haptic Intensity Levels

User-configurable in Appearance tab:

| Level | Multiplier |
|-------|-----------|
| **Off** | 0× |
| **Subtle** | 0.5× |
| **Normal** (default) | 1.0× |
| **Strong** | 1.5× |

### 9.3 Sound Design (Optional)

An opt-in ambient sound layer for focus rituals:

| Sound | Trigger | Duration |
|-------|---------|----------|
| **Soft chime** | Check-in complete | 0.5s |
| **Gentle bell** | Day 100% complete | 1.2s |
| **Page turn** | Review saved | 0.3s |
| **Soft whoosh** | Swipe dismiss | 0.2s |

All sounds must be:
- Under 40dB equivalent
- Pitch-matched to the app's calm identity
- Individually toggleable
- Muted during quiet hours

---

## 10. Navigation & Information Architecture

### 10.1 Navigation Restructure

**Current (5 tabs):** Today · Journey · Insights · Coach · Settings  
**Proposed (4 primary + drawer):**

| Tab | Content | Rationale |
|-----|---------|-----------|
| **Today** | Daily focus, habits, checkpoints, recovery | Primary surface |
| **Journey** | Identity/goal/system/habit tree + tools | Growth hierarchy |
| **Insights** | Charts, stats, heatmap, energy, reviews | Evidence |
| **Studio** | Coach + Blueprint + AI Engine (unified) | AI as a workspace, not a chat |

**Settings** moves to a profile/avatar button in the Today header (like Gmail/Linear), opening a full-screen settings page with sections.

### 10.2 Adaptive Navigation

- **Phone portrait**: Bottom navigation bar (4 tabs)
- **Phone landscape / Foldable**: Navigation rail (left side)
- **Tablet**: Persistent navigation rail with content pane

### 10.3 Gesture Shortcuts

| Gesture | Action |
|---------|--------|
| **Swipe right on habit card** | Quick check-in at Standard level |
| **Swipe left on habit card** | Quick skip |
| **Long press habit card** | Context menu (edit, detail, archive) |
| **Pull down on Today** | Refresh + show "Plan tomorrow" peek |
| **Swipe between tabs** | ViewPager2 with gentle overscroll |
| **Double tap progress ring** | Jump to Insights tab |
| **Pinch on heatmap** | Zoom between week/month/year views |
| **Back gesture** | Predictive back with shared element return |

### 10.4 Quick Actions

App shortcuts (long press launcher icon):
- "Check in" → Opens Today with keyboard focus on first unchecked habit
- "Quick journal" → Opens Review with today's reflection
- "Blueprint" → Opens Blueprint Studio directly
- "Morning review" → Opens checkpoint flow

### 10.5 Contextual Navigation

Within screens, show only relevant actions:

**Today header actions** (contextual):
- Morning: "Plan tomorrow" + "Energy check"
- Afternoon: "Minimum mode" (if behind) + Recovery
- Evening: "Review today" + "Plan tomorrow"

---

## 11. Screen-by-Screen Redesign

### 11.1 Today Screen

**Current:** RecyclerView with mixed card types, basic greeting, progress ring at top.

**Redesigned:**

```
┌──────────────────────────────────┐
│  ☀ Good morning                  │  ← Display type, variable weight
│  Wednesday, August 19            │  ← Label M, muted
│  ┌──────────┐                    │
│  │  67%     │  ← Animated ring   │  ← Tap for detail expansion
│  │  4/6     │                    │
│  └──────────┘                    │
│                                  │
│  IDENTITY                        │  ← Overline, tracked
│  ┌────────────────────────────┐  │
│  │ "I am becoming someone who │  │  ← Source Serif italic
│  │  moves every day"          │  │
│  │  47 votes                  │  │  ← Data type, mono
│  └────────────────────────────┘  │
│                                  │
│  DAILY FOCUS              2/3   │
│  ┌────────────────────────────┐  │
│  │ ☐ Review project brief     │  │  ← Swipe right = done
│  │ ☑ Call the dentist         │  │  ← Strikethrough + fade
│  │ ☐                          │  │  ← Tap to add inline
│  └────────────────────────────┘  │
│                                  │
│  YOUR HABITS                     │
│  ┌────────────────────────────┐  │
│  │ ○  Walk 10 min        7d ▸ │  │  ← HistoryStrip inline
│  │    After breakfast         │  │  ← Cue text, muted
│  │    Tiny · Min · Std · Stretch│  │  ← Level chips, pill style
│  └────────────────────────────┘  │
│  ...                             │
│                                  │
│  ENERGY          ●●●○○  3/5     │  ← Animated dot indicator
│  ──────────────────────────────  │
│  CHECKPOINTS                     │
│  [ Morning ] [ Midday ] [ Eve ]  │  ← Chip group, filled active
│                                  │
└──────────────────────────────────┘
```

**Key improvements:**
- Progress ring is interactive: tap to expand into a time-of-day breakdown
- Habit cards have inline history strip (no need to open detail)
- Swipe gestures for quick check-in/skip
- Energy tracker uses animated dot indicators instead of a slider
- Checkpoints auto-highlight the current period
- Pull-to-refresh with custom indicator
- Skeleton loading states while data loads

### 11.2 Journey Screen

**Current:** Flat list of entities grouped by type with section headers.

**Redesigned:**

```
┌──────────────────────────────────┐
│  Journey                         │
│  Identity → Goal → System → Habit│
│                                  │
│  ┌──────┐ ┌──────┐ ┌──────┐    │  ← Tool cards row
│  │Score │ │ Flows│ │Review│    │  ← With subtle icons
│  └──────┘ └──────┘ └──────┘    │
│                                  │
│  IDENTITIES                +    │
│  ┌────────────────────────────┐  │
│  │ 🌱 Someone who moves daily │  │  ← Life area emoji + accent border
│  │    Health · 3 goals        │  │
│  │    ── Connected habits: 4  │  │  ← Expandable connection count
│  └────────────────────────────┘  │
│                                  │
│  GOALS                     +    │
│  ┌────────────────────────────┐  │
│  │ 🎯 Walk 5km comfortably   │  │  ← Connected to identity above
│  │    "Because I want to…"   │  │  ← Why text, italic
│  │    System →                │  │  ← Tap to expand chain
│  └────────────────────────────┘  │
│                                  │
│  ...                             │
└──────────────────────────────────┘
```

**Key improvements:**
- Visual hierarchy tree showing identity→goal→system→habit connections
- Expandable cards showing downstream connections
- Drag-and-drop reordering of entities
- Long-press context menu replacing popup
- "Add" button contextual to each section (not just FAB)
- Empty states with illustrations and guided prompts
- Visual weight for entities with active habits vs dormant ones

### 11.3 Insights Screen

**Current:** Bar chart, stats row, heatmap, text cards — all basic Canvas drawings.

**Redesigned:**

- **Interactive charts** with touch-to-reveal data points
- **Period switcher** (7d / 30d / 90d / Year) with animated transitions
- **Animated heatmap** that draws progressively on scroll into view
- **Habit comparison** — tap two habits to overlay their consistency curves
- **Energy correlation** — scatter plot of energy vs completion rate
- **Milestone markers** — subtle gold dots on charts at 7, 21, 66, 100 day marks
- **Exportable** — long press any chart to save as image

### 11.4 Studio (Coach + Blueprint + AI Engine)

**Current:** Chat-like interface with status card, suggestions, and message history.

**Redesigned:**

```
┌──────────────────────────────────┐
│  Studio                          │
│  ┌────────────────────────────┐  │
│  │ ⚡ Full Control active     │  │  ← Compact status pill
│  │ Provider: gpt-4o-mini      │  │
│  └────────────────────────────┘  │
│                                  │
│  ┌─ Quick actions ──────────┐   │  ← Horizontal scroll chips
│  │ [Blueprint] [Audit] [Plan]│   │
│  └───────────────────────────┘   │
│                                  │
│  ┌────────────────────────────┐  │
│  │ 🤖 "Your system is..."     │  │  ← AI messages with avatar
│  │    via MainBrain           │  │
│  └────────────────────────────┘  │
│  ┌────────────────────────────┐  │
│  │ You: "Add a morning..."    │  │  ← User messages, right-aligned
│  └────────────────────────────┘  │
│                                  │
│  ┌────────────────────────────┐  │  ← Rich input bar
│  │ Tell Studio what to do  🎤 │  │
│  └────────────────────────────┘  │
└──────────────────────────────────┘
```

**Key improvements:**
- AI messages rendered as rich cards (not just text)
- Command execution shown inline with status indicators
- Blueprint projects shown as cards within Studio
- Typing indicator with subtle animation
- Voice input with real-time waveform visualization
- Context menu for message actions (copy, undo, explain)

### 11.5 Habit Detail Screen

**Current:** ScrollActivity with linear text cards.

**Redesigned:**
- **Collapsing header** with habit title, streak counter, and today's status
- **Tabbed content**: Overview | Design | History | Obstacles
- **Interactive heatmap** in History tab with day-tap detail
- **Animated ladder visualization** showing the four levels as a physical ladder
- **Contract card** with editable fields inline
- **Floating action**: Quick check-in button

### 11.6 Habit Designer

**Current:** 6-step wizard with linear progression.

**Redesigned:**
- **Step indicators** as connected dots with labels (not just progress bar)
- **Preview panel** that shows the habit card updating in real-time as you type
- **Smart suggestions** that appear contextually (not just a button)
- **Tiny start generator** with multiple options
- **Schedule visualizer** showing a week view with selected days highlighted
- **Contract preview** that updates live with all design decisions

---

## 12. Component Library

### 12.1 Compose Components

| Component | Description |
|-----------|-------------|
| `SfCard` | Elevation-aware card with variants (elevated, filled, outlined, accent, warm, glass) |
| `SfHabitCard` | Rich habit card with history strip, level chips, swipe actions, check animation |
| `SfProgressRing` | Animated ring with expandable detail, spring physics |
| `SfBarChart` | Interactive bar chart with touch tooltips |
| `SfHeatmap` | GitHub-style heatmap with pinch-zoom, tap detail |
| `SfHistoryStrip` | Compact 14-day strip with color-coded states |
| `SfChipGroup` | Adaptive chip group with single/multi select |
| `SfTextField` | Enhanced text input with character count, validation, animation |
| `SfBottomSheet` | Glass-effect bottom sheet with drag handle |
| `SfEmptyState` | Illustrated empty state with Lottie + action button |
| `SfSkeleton` | Content-aware shimmer loading placeholder |
| `SfSnackbar` | Consistent snackbar with undo action pattern |
| `SfTimePicker` | Enhanced time picker with quick-select presets |
| `SfSlider` | Custom slider with haptic ticks and animated value |
| `SfIdentityCard` | Serif-styled card for identity statements |
| `SfStatCard` | Metric display with animated value change |
| `SfSectionHeader` | Section label with optional action button |
| `SfNavigationRail` | Adaptive nav for tablets/foldables |

### 12.2 Illustration System

Create a consistent illustration language:

- **Style**: Abstract, geometric, muted palette
- **Line weight**: 1.5dp, rounded caps
- **Color**: Uses current palette at 40-60% saturation
- **Motifs**: Seeds, rings, flowing lines, sunrise, compass
- **Format**: SVG/VectorDrawable for crispness at any density

---

## 13. Data Visualization System

### 13.1 Chart Improvements

| Chart | Current | Upgraded |
|-------|---------|----------|
| **Progress Ring** | Canvas, single arc | Compose Canvas with gradient fill, tick marks, expandable |
| **Bar Chart** | Canvas, rounded bars | Compose with gesture zoom, touch tooltips, animated entry |
| **Heatmap** | Canvas, flat grid | Compose with pinch-zoom, month labels, scroll-snap |
| **History Strip** | Canvas, colored dots | Compose with tap-to-expand day detail |
| **Linear Progress** | Material indicator | Custom with gradient fill, animated shimmer |

### 13.2 New Visualizations

| Visualization | Purpose |
|--------------|---------|
| **Consistency curve** | Line chart of rolling 7-day consistency per habit |
| **Energy scatter** | Energy rating vs completion rate correlation |
| **Habit timing** | Clock face showing when habits are typically completed |
| **Recovery arc** | Time from miss → return, showing improvement trend |
| **Identity vote counter** | Animated counter with vote-cast animation |
| **Weekly rhythm** | Calendar-style week view with habit completion overlay |

---

## 14. Onboarding Reimagined

### 14.1 Structure

**Current:** 8 linear steps with text fields and chips.  
**Upgraded:** 6 focused steps with rich illustrations and animations.

| Step | Title | Interaction |
|------|-------|-------------|
| 1 | **Welcome** | Animated hero illustration, swipe to begin |
| 2 | **Who are you becoming?** | Identity prompt with example cards that cycle |
| 3 | **What matters to you?** | Goal + Why, with gentle coaching cards |
| 4 | **One small habit** | Habit + Tiny Start with AI suggestion animation |
| 5 | **When and where?** | Time picker + anchor with visual schedule |
| 6 | **Your first day** | Preview of the completed Today screen |

### 14.2 Visual Language

- Each step has a full-bleed illustration at the top (30% of screen)
- Illustrations are abstract and flow into each other across steps
- Progress shown as a connected line, not a percentage bar
- Step transitions use shared-element morphing
- The final step shows a live preview of their Today screen with the habit they just created

### 14.3 Skip Behavior

- "Skip for now" always visible at bottom
- Skipped onboarding creates a demo workspace with example data
- A "Replay onboarding" option remains in settings

---

## 15. Settings: Appearance & Experience Tab

### 15.1 Settings Restructure

**Current flat list:**
```
Theme (light/dark/system)
AI (engine, enable, voice)
Reminders (enable, quiet hours, budget)
Checkpoints (enable, times, energy)
Experience (haptics, celebrations)
Your Data (export, import, share, delete)
Privacy (crash reporting)
About (version, replay onboarding)
```

**Upgraded categorized layout with dedicated Appearance tab:**

```
┌──────────────────────────────────────┐
│  Settings                            │
│  ┌────────────────────────────────┐  │
│  │ 👤  [Profile]                  │  │  ← Optional display name
│  └────────────────────────────────┘  │
│                                      │
│  [🎨 Appearance]  [⚡ Experience]   │  ← Tab row
│  [🤖 AI]  [🔔 Reminders]  [📊 Data]│
│                                      │
├────── 🎨 APPEARANCE TAB ─────────────┤
│                                      │
│  THEME                               │
│  ┌────────────────────────────────┐  │
│  │  [Light] [Dark] [System]       │  │  ← Segmented button group
│  └────────────────────────────────┘  │
│                                      │
│  PALETTE                             │
│  ┌────────────────────────────────┐  │
│  │  ● Calm  ● Forest  ● Ocean    │  │  ← Color swatch picker
│  │  ● Dusk   ● Mono   ● Dynamic  │  │
│  └────────────────────────────────┘  │
│                                      │
│  DARK MODE STYLE                     │
│  ┌────────────────────────────────┐  │
│  │  ○ Warm Dark (default)        │  │  ← Radio list
│  │  ○ OLED Black                 │  │
│  │  ○ Midnight Blue              │  │
│  └────────────────────────────────┘  │
│                                      │
│  TYPOGRAPHY                          │
│  ┌────────────────────────────────┐  │
│  │  Text size       [ Aa ▼ ]     │  │  ← Small/Default/Large/XL
│  │  Font family     [ Inter ▼ ]  │  │  ← Inter/System/Serif
│  └────────────────────────────────┘  │
│                                      │
│  DENSITY                             │
│  ┌────────────────────────────────┐  │
│  │  [Compact] [Comfortable]      │  │  ← With live preview cards
│  │  [Spacious]                   │  │
│  └────────────────────────────────┘  │
│                                      │
│  VISUAL EFFECTS                      │
│  ┌────────────────────────────────┐  │
│  │  Animations           [on]    │  │
│  │  Paper grain texture  [off]   │  │
│  │  Blur effects (12+)   [on]    │  │
│  │  Gradient washes      [on]    │  │
│  └────────────────────────────────┘  │
│                                      │
│  APP ICON                            │
│  ┌────────────────────────────────┐  │
│  │  [Default] [Dark] [Minimal]   │  │  ← Icon variant picker
│  └────────────────────────────────┘  │
│                                      │
├────── ⚡ EXPERIENCE TAB ─────────────┤
│                                      │
│  HAPTICS                             │
│  ┌────────────────────────────────┐  │
│  │  Haptic feedback     [on]     │  │
│  │  Intensity      [Normal ▼]    │  │  ← Off/Subtle/Normal/Strong
│  │  [Test haptics]               │  │  ← Button that cycles patterns
│  └────────────────────────────────┘  │
│                                      │
│  SOUNDS                              │
│  ┌────────────────────────────────┐  │
│  │  Interface sounds    [off]    │  │
│  │  Volume            [40%]      │  │
│  │  [Preview sounds]             │  │
│  └────────────────────────────────┘  │
│                                      │
│  CELEBRATIONS                        │
│  ┌────────────────────────────────┐  │
│  │  Day completion      [on]     │  │
│  │  Milestone moments   [on]     │  │
│  │  Style       [Subtle ▼]      │  │  ← Subtle/Moderate/Full
│  └────────────────────────────────┘  │
│                                      │
│  GESTURES                            │
│  ┌────────────────────────────────┐  │
│  │  Swipe to check-in   [on]     │  │
│  │  Long press menus    [on]     │  │
│  │  Pull to refresh     [on]     │  │
│  └────────────────────────────────┘  │
│                                      │
│  NAVIGATION                          │
│  ┌────────────────────────────────┐  │
│  │  Tab bar style  [Labels ▼]    │  │  ← Labels/Icons only/Hidden
│  │  Show FAB            [on]     │  │
│  │  Predictive back     [on]     │  │
│  └────────────────────────────────┘  │
│                                      │
│  CHECKPOINTS                         │
│  ┌────────────────────────────────┐  │
│  │  Daily checkpoints   [on]     │  │
│  │  Morning        [08:00]       │  │
│  │  Midday         [13:00]       │  │
│  │  Evening        [20:30]       │  │
│  │  Track energy        [on]     │  │
│  └────────────────────────────────┘  │
│                                      │
└──────────────────────────────────────┘
```

### 15.2 Appearance Tab Features

1. **Live Preview** — When changing palette, density, or typography, a miniature preview card updates in real-time showing a sample habit card in the new style
2. **Theme Preview** — Three preview cards showing light/dark/system modes side by side
3. **Reset** — "Reset to defaults" button at the bottom of each section
4. **Export Theme** — Share your appearance configuration as JSON for backup or sharing

### 15.3 Experience Tab Features

1. **Haptic Test** — Button that cycles through all haptic patterns so the user can feel each one
2. **Sound Preview** — Button that plays each interface sound
3. **Gesture Tutorial** — Link to an interactive gesture guide
4. **Celebration Style** — Three levels of visual celebration intensity

---

## 16. Widget Redesign

### 16.1 Widget Sizes

| Size | Content |
|------|---------|
| **Small (2×2)** | Progress ring + percentage |
| **Medium (4×2)** | Ring + today's top habit + quick check-in |
| **Large (4×4)** | Ring + all today's habits + check-in buttons |
| **Wide (5×2)** | Progress bar + daily focus items + next habit |

### 16.2 Widget Features

- **Dynamic color** matching the app's current palette
- **Glance framework** (Jetpack Glance) for Compose-based widgets
- **Interactive** — check-in directly from widget without opening app
- **Animated** — subtle shimmer on progress update
- **Contextual** — Shows different content based on time of day
- **Round corners** matching the app's shape system

---

## 17. Accessibility

### 17.1 Screen Reader

- Full `contentDescription` on every interactive element
- Semantic headings for screen reader navigation
- Live region announcements for check-in state changes
- Custom accessibility actions for swipe gestures

### 17.2 Visual Accessibility

| Feature | Implementation |
|---------|---------------|
| **Font scaling** | Support 0.85× to 2.0× text size |
| **High contrast** | Toggle that increases outline/border contrast |
| **Reduced motion** | Respects system "Remove animations" setting |
| **Color blind modes** | Alternative color mappings for deuteranopia/protanopia |
| **Bold text** | System bold text setting respected |

### 17.3 Motor Accessibility

- Minimum 48dp touch targets everywhere (already present)
- All swipe actions have button alternatives
- No time-limited interactions
- Full keyboard navigation support

---

## 18. Performance & Perceived Speed

### 18.1 Skeleton Loading

Every screen shows a shimmer skeleton that matches the content layout:

```
┌────────────────────────────┐
│  ████████                  │  ← Greeting shimmer
│  ████████████████          │  ← Date shimmer
│  ┌──────────┐              │
│  │          │              │  ← Ring skeleton with pulse
│  │          │              │
│  └──────────┘              │
│  ████████████████████████  │  ← Card skeletons
│  ████████████████████████  │
│  ████████████████████████  │
└────────────────────────────┘
```

### 18.2 Optimistic Updates

- Check-in immediately updates UI before confirming with repository
- If the operation fails, the UI reverts with a subtle error animation
- No spinner or loading state for local operations

### 18.3 Image & Asset Optimization

- All illustrations as VectorDrawable (not PNG)
- Lottie animations loaded lazily
- Font files subset to only needed weights
- R8/ProGuard for maximum code shrinking

### 18.4 Startup Performance

- Splash screen with brand mark (not blank white)
- Defer non-critical initialization (WorkManager, widget refresh)
- Pre-warm the repository on app launch
- Cache computed Insights for instant display

---

## 19. Implementation Phases

### Phase 1: Foundation (Weeks 1-3)
**Priority: Design system, theme engine, core components**

- [ ] Set up Compose dependencies and hybrid build
- [ ] Implement `SfTheme` with palette variants and dynamic color
- [ ] Add Inter + Source Serif typefaces
- [ ] Create spacing/density/shape tokens
- [ ] Build `SfCard`, `SfChipGroup`, `SfTextField`, `SfSectionHeader`
- [ ] Implement skeleton loading component
- [ ] Create the Appearance & Experience settings tab (XML for now)
- [ ] Add new Prefs for all appearance/experience options

### Phase 2: Today & Motion (Weeks 4-6)
**Priority: Today screen Compose rewrite, animation system**

- [ ] Rewrite TodayFragment as Compose TodayScreen
- [ ] Implement `SfHabitCard` with swipe gestures and check animation
- [ ] Implement `SfProgressRing` in Compose with spring physics
- [ ] Add shared element transitions
- [ ] Implement orchestrated load sequences
- [ ] Add haptic vocabulary (8+ patterns)
- [ ] Add pull-to-refresh with custom indicator
- [ ] Lottie illustrations for empty states

### Phase 3: Journey & Insights (Weeks 7-9)
**Priority: Journey tree view, interactive charts**

- [ ] Rewrite JourneyFragment as Compose JourneyScreen
- [ ] Implement entity hierarchy tree with visual connections
- [ ] Rewrite InsightsFragment as Compose InsightsScreen
- [ ] Implement interactive `SfBarChart` and `SfHeatmap`
- [ ] Add period switcher with animated transitions
- [ ] Add new data visualizations (consistency curve, energy scatter)

### Phase 4: Studio & Polish (Weeks 10-12)
**Priority: Unified Studio, onboarding, widget**

- [ ] Merge Coach + Blueprint + AI Engine into Studio
- [ ] Redesign onboarding with illustrations and animations
- [ ] Implement Glance-based widgets
- [ ] Add sound design (opt-in)
- [ ] Complete accessibility audit and fixes
- [ ] Performance optimization pass
- [ ] Tablet/foldable navigation layouts

### Phase 5: Refinement (Weeks 13-14)
**Priority: Micro-polish, testing, documentation**

- [ ] Every micro-animation tuned (spring constants, timing)
- [ ] Dark mode variants complete
- [ ] App icon variants
- [ ] Complete screen reader testing
- [ ] Performance profiling (no jank, <16ms frames)
- [ ] Update BUILD.md and IMPLEMENTATION_STATUS.md

---

## 20. Metrics & Success Criteria

### Visual Quality
- [ ] Every screen uses the design token system (no ad-hoc colors/sizes)
- [ ] All 5 palette variants render correctly in light and dark
- [ ] Dynamic color works on Android 12+ devices
- [ ] Typography renders correctly with Inter/Source Serif

### Motion Quality
- [ ] All transitions use spring physics (no linear interpolators)
- [ ] Orchestrated sequences complete within 800ms
- [ ] No animation jank (profiled at 60fps)
- [ ] Reduced motion mode disables all non-essential animation

### Haptic Quality
- [ ] 8+ distinct haptic patterns implemented
- [ ] 4 intensity levels work correctly
- [ ] Haptics respect system accessibility settings
- [ ] No haptic on Android versions that don't support the API

### Accessibility
- [ ] All screens pass TalkBack navigation test
- [ ] Font scaling to 2.0× doesn't break layouts
- [ ] Color blind alternative colors implemented
- [ ] Touch targets ≥48dp everywhere

### Performance
- [ ] Cold start to interactive < 1.5s
- [ ] Tab switch < 100ms
- [ ] Check-in response < 50ms (optimistic)
- [ ] No frame drops during list scroll (profiled)

### Settings
- [ ] Appearance tab has 6 sections (theme, palette, dark mode, typography, density, visual effects, app icon)
- [ ] Experience tab has 6 sections (haptics, sounds, celebrations, gestures, navigation, checkpoints)
- [ ] All settings persist across app restarts
- [ ] Live preview updates correctly

---

## Appendix A: Technology Stack Additions

| Technology | Purpose | Size Impact |
|-----------|---------|-------------|
| Jetpack Compose | Declarative UI | +3.2 MB |
| Compose Material 3 | M3 Compose components | Included |
| Compose Animation | Spring physics, shared elements | Included |
| Jetpack Glance | Compose-based widgets | +0.8 MB |
| Lottie Compose | Rich illustrations | +1.1 MB |
| Accompanist | Navigation transitions, permissions | +0.4 MB |
| Inter Variable Font | Primary typeface | +0.3 MB |
| Source Serif 4 | Accent typeface | +0.2 MB |
| **Total added** | | **~6.0 MB** |
| **Estimated final APK** | | **~13.5 MB** |

## Appendix B: New Files Created

```
app/src/main/kotlin/com/superflow/ui/theme/
├── SfTheme.kt              # Central Compose theme
├── SfPalette.kt            # 5 palette definitions
├── SfTypography.kt         # Type scale with Inter/Source Serif
├── SfShapes.kt             # Shape tokens
├── SfMotion.kt             # Motion tokens and spring specs
├── SfHaptics.kt            # Haptic vocabulary
├── SfSounds.kt             # Sound effects manager
├── SfDensity.kt            # Density modes
└── DynamicColor.kt         # Material You integration

app/src/main/kotlin/com/superflow/ui/components/
├── SfCard.kt
├── SfHabitCard.kt
├── SfProgressRing.kt
├── SfBarChart.kt
├── SfHeatmap.kt
├── SfHistoryStrip.kt
├── SfChipGroup.kt
├── SfTextField.kt
├── SfBottomSheet.kt
├── SfEmptyState.kt
├── SfSkeleton.kt
├── SfSnackbar.kt
├── SfIdentityCard.kt
├── SfStatCard.kt
└── SfSectionHeader.kt

app/src/main/kotlin/com/superflow/ui/settings/
├── AppearanceTab.kt        # New Appearance settings
└── ExperienceTab.kt        # New Experience settings

app/src/main/res/raw/
├── sound_chime.ogg
├── sound_bell.ogg
├── sound_page_turn.ogg
└── sound_whoosh.ogg

app/src/main/res/font/
├── inter_variable.ttf
├── inter_variable_italic.ttf
└── source_serif_4_variable.ttf

app/src/main/assets/lottie/
├── empty_today.json
├── empty_journey.json
├── empty_insights.json
├── day_complete.json
├── recovery_welcome.json
├── onboarding_hero.json
└── blueprint_loading.json
```

---

*This plan is a living document. Every decision should be validated against the north star: does this make SuperFlow feel like a quiet studio where someone can think clearly about who they are becoming?*
