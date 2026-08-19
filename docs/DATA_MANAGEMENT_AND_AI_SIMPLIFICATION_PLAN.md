# SuperFlow — Data Management, AI Simplification & Info System Plan

**Version:** 1.0 · August 2026
**Scope:** All-inclusive data policy, data management settings, AI Default/Advanced modes, info button system, AI instructions & memory
**Companion docs:** [Gap Analysis](GAP_ANALYSIS_AND_FIXES.md) · [Alpha2 Plan](ALPHA2_UPGRADE_PLAN.md) · [Core Systems](CORE_GROWTH_SYSTEMS_UPGRADE_PLAN.md)

---

## 1. All-Inclusive Data Policy

### The Principle

**Every piece of data in SuperFlow is registered, exportable, importable, and backed up. Nothing is silently left out.** When the app adds new features, their data is automatically covered by the policy.

### Data Registry (`DataPolicy.kt`)

23 registered data categories across 6 groups:

| Group | Categories | Included in Export |
|-------|-----------|-------------------|
| **Core hierarchy** | Identities, Goals, Systems, Habits | ✓ |
| **Daily activity** | Check-ins, Daily Focus, Energy Logs | ✓ |
| **Design tools** | Obstacle Plans, Scorecard, Flows, Flow Steps | ✓ |
| **Reflection** | Reviews, Pause Windows | ✓ |
| **Blueprint Studio** | Projects, Sources, Requirements, Versions | ✓ |
| **AI & system** | AI Conversation, Activity Trail, User Profile, App Settings | ✓ |
| **Excluded by design** | API Keys & Secrets, Safety Snapshots | ✗ (documented) |

### What Was Previously Missing

| Data | Before | After |
|------|--------|-------|
| **App Settings (all Prefs)** | ❌ Not exported | ✓ All 70+ preferences exported |
| **AI Conversation** | ❌ Not exported | ✓ Full message history |
| **Activity Trail** | ❌ Not exported | ✓ Complete audit log |
| **Blueprint Versions** | ❌ Not exported | ✓ Ledger version history |
| **User Profile** | ❌ Not exported | ✓ Display name, locale, timezone |
| **Daily Focus items** | ❌ Not exported | ✓ All focus items with dates |

### Selective Export

Users can choose which categories to include:

```
┌──────────────────────────────────┐
│  Select categories to export     │
│                                  │
│  [✓] Identities (3 items)       │
│  [✓] Goals (2 items)            │
│  [✓] Systems (4 items)          │
│  [✓] Habits (8 items)           │
│  [✓] Check-ins (342 items)      │
│  [✓] Daily Focus (12 items)     │
│  [ ] Energy Logs (45 items)     │
│  [✓] Obstacle Plans (6 items)   │
│  ...                             │
│  [✓] App Settings               │
│  [ ] AI Conversation (89 msgs)  │
│  [✓] Activity Trail (234 entries)│
│                                  │
│  [Export selected]               │
└──────────────────────────────────┘
```

### Import Validation

Before applying an import, the system validates:
1. Is it a SuperFlow export? (checks `app` field)
2. Is it from a compatible version? (warns on version mismatch)
3. Are expected categories present? (warns on missing data)
4. Is the JSON structurally valid?

Warnings are shown but don't block import — the user decides.

### Policy Extensibility

When a new feature is added, the developer MUST:
1. Add a `DataCategory` entry to `DataPolicy.categories`
2. Add serialization in `Serial.of()` and `Serial.from()`
3. Add to `DataPolicy.exportFull()` and `DataPolicy.importPreferences()`
4. Add to `Repository.deleteAllData()` if it has a table

The policy version bumps with each addition, so old exports are recognised.

---

## 2. Data Management Settings Section

A dedicated `DataManagementFragment` accessible from Settings → "Data Management":

```
┌──────────────────────────────────────┐
│  Data Management                     │
│  All-inclusive: everything can be    │
│  exported, imported, and backed up.  │
│                                      │
│  YOUR DATA                           │
│  ┌────────────────────────────────┐  │
│  │ Total records   647 items      │  │
│  │ Database size   234 KB         │  │
│  │ Snapshots       8 saved        │  │
│  │ Policy version  v2 — inclusive │  │
│  └────────────────────────────────┘  │
│  [View data manifest]           ⓘ   │
│                                      │
│  EXPORT                              │
│  ┌────────────────────────────────┐  │
│  │ Full export (all-inclusive)    │  │
│  │ Everything except API keys.    │  │
│  │ 20 categories.                 │  │
│  ├────────────────────────────────┤  │
│  │ Selective export               │  │
│  │ Choose which categories.       │  │
│  ├────────────────────────────────┤  │
│  │ Share progress summary         │  │
│  │ A private text-only recap.     │  │
│  └────────────────────────────────┘  │
│                                      │
│  IMPORT                              │
│  ┌────────────────────────────────┐  │
│  │ Import from file               │  │
│  │ Paste or load a previous       │  │
│  │ export. Replaces all data.     │  │
│  ├────────────────────────────────┤  │
│  │ Merge import                   │  │
│  │ Add data without deleting      │  │
│  │ existing records.              │  │
│  └────────────────────────────────┘  │
│  ⓘ API keys are never included in   │
│  exports and must be re-entered.     │
│                                      │
│  AUTO-BACKUP                         │
│  ┌────────────────────────────────┐  │
│  │ Auto-backup enabled     [on]   │  │
│  │ Frequency             Daily    │  │
│  │ Keep backups          7        │  │
│  └────────────────────────────────┘  │
│  [Backup now]                        │
│                                      │
│  DATA INTEGRITY                      │
│  [Check data integrity]              │
│  Finds orphaned records and          │
│  inconsistencies.                    │
│                                      │
│  DANGEROUS                           │
│  ┌────────────────────────────────┐  │
│  │ Clear AI conversation          │  │
│  │ Clear activity trail           │  │
│  │ Delete all data                │  │
│  └────────────────────────────────┘  │
│                                      │
│  PRIVACY                             │
│  All data stays on your device.      │
│  Nothing is uploaded unless you      │
│  configure a cloud AI provider.      │
│  API keys are stored separately.     │
└──────────────────────────────────────┘
```

### Features

| Feature | Description |
|---------|-------------|
| **Data overview** | Total records, database size, snapshot count, policy version |
| **Data manifest** | Human-readable list of every category and its item count |
| **Full export** | All 20 categories in one JSON file, shared or saved locally |
| **Selective export** | Multi-choice picker for which categories to include |
| **Import with validation** | Validates before applying, shows warnings, supports merge |
| **Merge import** | Adds data without deleting existing records |
| **Auto-backup** | Configurable frequency (daily/3 days/weekly), retention (3-30) |
| **Integrity check** | Finds orphaned check-ins, obstacles, goals, systems, habits |
| **Fix integrity** | One-tap cleanup of orphaned records |
| **Dangerous actions** | Clear AI, clear audit, delete all — each with confirmation |

---

## 3. AI Settings: Default vs Advanced Mode

### The Problem

The AI Engine settings were too complex for general users. Sliders for frequency penalty, presence penalty, seed, stop sequences — most users don't know what these mean and shouldn't need to.

### The Solution: Two Modes

**Default Mode** — Curated presets with friendly labels:

```
┌──────────────────────────────────────┐
│  SETUP MODE                          │
│  ┌────────────────────────────────┐  │
│  │ Default Mode                   │  │
│  │ Curated presets for common     │  │
│  │ use cases.                     │  │
│  │ [Switch to Advanced Mode]      │  │
│  └────────────────────────────────┘  │
│                                      │
│  GENERATION                          │
│  ┌────────────────────────────────┐  │
│  │ Creativity level: Balanced     │  │
│  │ [Precise] [Balanced]           │  │
│  │ [Creative] [Very creative]     │  │
│  └────────────────────────────────┘  │
│  ⓘ How creative vs predictable      │
│                                      │
│  ┌────────────────────────────────┐  │
│  │ Response length: Medium        │  │
│  │ [Short] [Medium] [Long]        │  │
│  │ [Very long]                    │  │
│  └────────────────────────────────┘  │
│  ⓘ Maximum length of AI responses   │
│                                      │
│  ┌────────────────────────────────┐  │
│  │ Wait time: Normal              │  │
│  │ [Quick] [Normal] [Patient]     │  │
│  │ [Very patient]                 │  │
│  └────────────────────────────────┘  │
│  ⓘ How long to wait for a response  │
└──────────────────────────────────────┘
```

**Advanced Mode** — Every parameter with full control:

```
┌──────────────────────────────────────┐
│  SETUP MODE                          │
│  ┌────────────────────────────────┐  │
│  │ Advanced Mode                  │  │
│  │ All parameters visible with    │  │
│  │ full customization.            │  │
│  │ [Switch to Default Mode]       │  │
│  └────────────────────────────────┘  │
│                                      │
│  GENERATION                          │
│  Temperature: 0.70  ──────●────  ⓘ  │
│  Top-p: 1.00  ────────────●────  ⓘ  │
│  Max tokens: [4096_______]      ⓘ   │
│  Frequency penalty: 0.00  ──●──  ⓘ  │
│  Presence penalty: 0.00  ───●──  ⓘ  │
│  Seed: [-1_________________]    ⓘ   │
│  Stop sequences: [____________] ⓘ   │
│  Response format: [Auto ▼]      ⓘ   │
│                                      │
│  ADVANCED                            │
│  Timeout: [120_____________]    ⓘ   │
│  Retries: [2_______________]    ⓘ   │
│  Conversation history: [20___]  ⓘ   │
│  Max context chars: [12000___]  ⓘ   │
│  Streaming           [off]      ⓘ   │
│  Request logging     [off]      ⓘ   │
└──────────────────────────────────────┘
```

### Default Mode Presets

| Setting | Preset Options | Maps To |
|---------|---------------|---------|
| **Creativity** | Precise / Balanced / Creative / Very creative | Temperature: 0.3 / 0.7 / 1.0 / 1.5 |
| **Response length** | Short / Medium / Long / Very long | Max tokens: 1024 / 4096 / 8192 / 16384 |
| **Wait time** | Quick / Normal / Patient / Very patient | Timeout: 60s / 120s / 300s / 600s |

---

## 4. Info Button System (App-Wide)

### The Pattern

Every setting, parameter, or option that isn't self-explanatory gets a small **ⓘ** button next to it. Tapping it opens a dialog with:
- **Title**: The parameter name
- **Description**: What it does, recommended values, when to change it

### Implementation

```kotlin
// Reusable component
class InfoButton(context: Context) : LinearLayout(context) {
    var title: String = ""
    var description: String = ""
    // Shows MaterialAlertDialog on tap
}

// Usage anywhere in the app
content.addView(InfoButton.create(context,
    "Temperature",
    "Controls randomness. 0 = deterministic, 2 = creative. Recommended: 0.7"
))
```

### Info Descriptions Registry

`AiParameterInfo.kt` contains descriptions for all 17 AI parameters:

| Parameter | Short Description |
|-----------|------------------|
| Temperature | How creative vs predictable the AI is |
| Top-p | Limits the AI to the most likely words |
| Max tokens | Maximum length of AI responses |
| Frequency penalty | Reduces repetition of words |
| Presence penalty | Encourages new topics |
| Seed | For reproducible outputs |
| Stop sequences | Words that end the AI's response |
| Response format | How the AI structures its output |
| Timeout | How long to wait for a response |
| Retries | Automatic retry on failure |
| Conversation history | How much context the AI remembers |
| Max context chars | How much of your data the AI sees |
| Streaming | Show responses as they're generated |
| Logging | Log AI requests for debugging |
| Call budget | Maximum API calls per month |
| Token budget | Maximum tokens per month |
| Custom headers | Extra HTTP headers for your provider |
| System prompt | The AI's core instructions |
| Memory notes | Things the AI should always remember |
| AI instructions | Rules the AI must follow |
| Local memory | Structured facts the AI remembers |

### Expansion Plan

The info button system is designed to be used **throughout the app**, not just in AI settings:

| Screen | What Gets Info Buttons |
|--------|----------------------|
| **Habit Designer** | Each Four Laws field, schedule options, track type, mode |
| **Settings** | Theme options, reminder budget, quiet hours, checkpoints |
| **Today** | Minimum mode, plan tomorrow, energy tracking |
| **Insights** | Each chart type, consistency metrics, pattern cards |
| **Onboarding** | Each step's purpose and what it produces |

---

## 5. AI Instructions & Local Memory

### Explicit Instructions

A dedicated section in the AI Engine where users write rules the AI must always follow:

```
┌──────────────────────────────────────┐
│  INSTRUCTIONS & MEMORY               │
│                                      │
│  Explicit Instructions          ⓘ   │
│  ┌────────────────────────────────┐  │
│  │ Never suggest more than 3      │  │
│  │ habits at once                 │  │
│  │ Always suggest the tiny        │  │
│  │ version first                  │  │
│  │ Be more direct and less wordy  │  │
│  │ Always ask before changing     │  │
│  │ my schedule                    │  │
│  └────────────────────────────────┘  │
│  [Save instructions]                 │
│                                      │
│  Local Memory                   ⓘ   │
│  ┌────────────────────────────────┐  │
│  │ I have two kids                │  │
│  │ Morning energy is usually high │  │
│  │ I'm training for a 5K in Oct   │  │
│  │ Wednesday is my busiest day    │  │
│  │ I prefer outdoor activities    │  │
│  └────────────────────────────────┘  │
│  [Save memory]                       │
└──────────────────────────────────────┘
```

### How It Works

**Instructions** are included in every AI conversation as the highest-priority rules:

```
System prompt:
  [Built-in SuperFlow principles]
  [Autonomy profile]
  [Tool catalog]
  
  Explicit instructions from the user (highest priority):
  Never suggest more than 3 habits at once
  Always suggest the tiny version first
  ...
```

**Local Memory** is included as structured facts:

```
  Facts the user wants you to remember:
  I have two kids
  Morning energy is usually high
  I'm training for a 5K in October
  ...
```

### Integration with AI Context

Both are automatically included in `MainBrain.buildContext()`:

```kotlin
// In MainBrain.kt
if (prefs.aiInstructions.isNotBlank()) {
    sb.append("\nExplicit instructions from the user (highest priority):\n")
        .append(prefs.aiInstructions).append('\n')
}
if (prefs.aiLocalMemory.isNotBlank()) {
    sb.append("\nFacts the user wants you to remember:\n")
        .append(prefs.aiLocalMemory).append('\n')
}
```

### Difference from Memory Notes

| Feature | Memory Notes (existing) | Local Memory (new) | AI Instructions (new) |
|---------|------------------------|--------------------|-----------------------|
| **Purpose** | Free-text notes | Structured facts | Rules and constraints |
| **Format** | Paragraph | One fact per line | One rule per line |
| **Priority** | Medium (context) | Medium (context) | Highest (overrides) |
| **Example** | "I prefer morning workouts" | "Morning energy: high" | "Never suggest evening habits" |
| **Location** | Context & Memory section | Instructions & Memory section | Instructions & Memory section |

---

## Implementation Summary

### Files Created

| File | Lines | Purpose |
|------|-------|---------|
| `DataPolicy.kt` | 410 | All-inclusive data registry, export/import/validation |
| `InfoButton.kt` | 294 | Reusable info button + AI parameter descriptions |
| `DataManagementFragment.kt` | 548 | Dedicated data management settings screen |

### Files Modified

| File | Changes |
|------|---------|
| `Prefs.kt` | +30 lines: auto-backup, AI mode, instructions, memory |
| `AiEngineActivity.kt` | +200 lines: Default/Advanced mode, info buttons, instructions & memory section |
| `MainBrain.kt` | +10 lines: include instructions & memory in context |
| `Repository.kt` | +3 lines: public `delete()`, `focusAll()` |
| `Serial.kt` | +2 lines: focus in export/import |
| `SettingsFragment.kt` | Replaced old data section with Data Management link |

### All-Inclusion Coverage

| Category | Before | After |
|----------|--------|-------|
| Database tables exported | 14 of 18 | **18 of 18** (+focus, bp_version, aimsg, audit) |
| Preferences exported | 0 of 70+ | **70+ of 70+** |
| Profile exported | No | **Yes** |
| Selective export | No | **Yes** (per-category picker) |
| Import validation | None | **Version check, category check, structure check** |
| Auto-backup | No | **Yes** (configurable frequency & retention) |
| Data integrity check | No | **Yes** (orphan detection + auto-fix) |
| Merge import | No | **Yes** (add without deleting) |
| AI instructions | No | **Yes** (explicit rules, highest priority) |
| AI local memory | No | **Yes** (structured facts) |
| Info buttons | No | **Yes** (app-wide, 21 parameter descriptions) |
| AI Default/Advanced mode | No | **Yes** (curated presets vs full control) |
