# SuperFlow — Complete Gap Analysis & Fix Plan

**Version:** 1.0 · August 2026
**Scope:** Every remaining gap, bug, missing feature, and weakness across the entire app
**Status:** To be resolved before alpha2

---

## Gap Registry: 80 Issues Found

### 🔴 Critical — Breaks Core Functionality

| # | Gap | Location | Impact | Fix |
|---|-----|----------|--------|-----|
| 1 | **TTS not implemented** | No TTS code exists | AI responses are silent | `SfTextToSpeech` class (covered in Functional Plan) |
| 2 | **STT fails on de-Googled devices** | `VoiceInput.kt` | Voice input broken for many users | Multi-provider STT (covered in Functional Plan) |
| 3 | **Blueprint dumps everything** | `Compiler.kt` | Creates junk habits from documents | Rewrite with intent-first extraction (covered in Functional Plan) |
| 4 | **No progressive difficulty** | Entire codebase | Habits never grow with the user | Growth Engine (covered in Core Systems Plan) |
| 5 | **AI never acts proactively** | `Agent.kt` | AI is purely reactive | Proactive AI Worker (covered in Functional Plan) |
| 6 | **Energy data never used** | `Insights.energyPattern()` | Logged but ignored | Energy-aware scheduling (covered in Core Systems Plan) |
| 7 | **Four Laws are dead fields** | `HabitDesignerActivity.kt` | Filled once, never revisited | Living Four Laws (covered in Core Systems Plan) |
| 8 | **Ladder is static** | Habit model | Never auto-adjusts | Adaptive Ladder (covered in Core Systems Plan) |
| 9 | **Reviews produce no follow-through** | `ReviewActivity.kt` | Action items lost | Tracked action items (covered in Core Systems Plan) |
| 10 | **Obstacle plans never surfaced** | `ObstacleSheet.kt` | Written and forgotten | Contextual surfacing (covered in Core Systems Plan) |

### 🟡 Significant — Degrades User Experience

| # | Gap | Location | Fix |
|---|-----|----------|-----|
| 11 | **No search anywhere in the app** | All screens | Add global search bar in toolbar with cross-entity results |
| 12 | **No habit reordering** | Today, Journey | Drag-and-drop with `ItemTouchHelper` |
| 13 | **No duplicate habit action** | Journey menu | Add "Duplicate" to context menu, deep-copies all fields |
| 14 | **No "complete all remaining" for end of day** | Today | Evening checkpoint action: "Mark all open as tiny" |
| 15 | **No "undo all from today"** | Today | Toolbar action: reverts all today's check-ins |
| 16 | **No habit templates in Designer** | `HabitDesignerActivity.kt` | Template picker as first step: "Start from a template" |
| 17 | **No weekly summary notification** | `Reminders.kt` | Sunday evening notification with week's stats |
| 18 | **No share progress feature** | Settings only has text export | Rich shareable card image generation |
| 19 | **No app lock** | No security | PIN/biometric lock on app open |
| 20 | **No backup to Google Drive / file** | Settings export only | Scheduled auto-backup to local file or SAF |
| 21 | **No notification action buttons** | `Reminders.kt` | "Done (Tiny)" / "Skip" / "Open" inline actions |
| 22 | **Checkpoints show nothing when tapped** | `TodayViewModel.kt` | Guided checkpoint screens with content |
| 23 | **No "Plan tomorrow" flow** | `TodayFragment.kt` | Opens a guided tomorrow-planning sheet |
| 24 | **Flows have no guided execution** | `FlowActivity.kt` | "Run flow" mode with step-by-step guidance |
| 25 | **Scorecard never revisited** | `ScorecardActivity.kt` | Monthly re-score prompt |
| 26 | **No pause/vacation mode** | `PauseWindow` exists but no UI | "Going on break?" card in Settings |
| 27 | **No habit graduation** | No concept | After 66+ days at 95%+, suggest "This is automatic now" |
| 28 | **No data validation on text fields** | All TextInputEditTexts | Max lengths: title 100, description 500, note 1000 |
| 29 | **No duplicate habit prevention** | `create_habit` capability | Warn if title matches existing habit |
| 30 | **findHabit() has no fuzzy matching** | `Repository.findHabit()` | Add Levenshtein distance for typo tolerance |

### 🟡 Database & Data Integrity

| # | Gap | Location | Fix |
|---|-----|----------|-----|
| 31 | **No batch operations / transactions** | `Repository.kt` | Wrap multi-step operations in `db.beginTransaction()` / `setTransactionSuccessful()` |
| 32 | **No indexes on foreign keys** | `Schema.kt` | Add indexes: `habit(systemId)`, `habit(identityId)`, `goal(identityId)`, `sys(goalId)`, `checkin(habitId)`, `obstacle(habitId)` |
| 33 | **No orphan cleanup** | `deleteHabit()` handles some, others don't | `deleteGoal()` should cascade to systems; `deleteIdentity()` should cascade to goals |
| 34 | **`counts()` missing tables** | `Repository.counts()` | Add: pause, bp_source, bp_req, bp_version, profile, aimsg, flowstep |
| 35 | **`deleteAllData()` missing tables** | `Repository.deleteAllData()` | Add: flowstep (already cascaded via deleteFlow), but also new tables as they're added |
| 36 | **No data integrity check tool** | No diagnostics | Add "Check data integrity" in AI Engine diagnostics: find orphaned records |
| 37 | **No DB version bump for new columns** | `Schema.VERSION = 3` | Bump to 4, add migration for all new Prefs-backed columns |
| 38 | **No aggregation queries in SQL** | All aggregation in Kotlin | Add `fun habitStats(habitId)` with SQL GROUP BY for performance |
| 39 | **No pagination for audit/messages** | `audit(limit)` only | Add offset-based pagination for Activity Log |
| 40 | **No concurrent write protection** | Singleton only | Add `synchronized(db)` blocks for write operations |

### 🟡 Workers & Background

| # | Gap | Location | Fix |
|---|-----|----------|-----|
| 41 | **Only 2 workers exist** | `Workers.kt` | Add: GrowthEngineWorker, ProactiveAiWorker, MilestoneWorker, ReviewWorker, SnapshotCleanupWorker |
| 42 | **No snapshot cleanup** | `Snapshots.kt` | Auto-delete snapshots older than 30 days, keep max 20 |
| 43 | **No milestone detection worker** | None | Background job checks for milestone thresholds daily |
| 44 | **No review generation worker** | None | Sunday worker pre-generates weekly review |
| 45 | **Widget only refreshes on app pause** | `TodayWidget.kt` | Add periodic refresh via WorkManager (every 30 min) |

### 🟡 Insights / Statistics Tab (Major Redesign Needed)

| # | Gap | Fix |
|---|-----|-----|
| 46 | **Charts are non-interactive Canvas** | Replace with Compose Canvas with touch handling |
| 47 | **No period switcher** | Add 7d / 30d / 90d / Year segmented control |
| 48 | **No habit comparison** | Select 2 habits to overlay consistency curves |
| 49 | **No correlation analysis** | "On days you walk, you're 3× more likely to journal" |
| 50 | **No energy-consistency scatter plot** | Energy rating vs completion rate |
| 51 | **No time-of-day patterns** | Clock face showing when habits are completed |
| 52 | **No day-of-week patterns** | Bar chart of consistency by weekday |
| 53 | **No recovery speed tracking** | Average days from miss to return, trend |
| 54 | **No milestone timeline** | Visual timeline of milestones achieved |
| 55 | **No exportable charts** | Long-press to save chart as image |
| 56 | **No "what changed since last week"** | Delta comparison card |
| 57 | **Heatmap is static** | Add pinch-zoom, tap-for-detail, month labels |
| 58 | **No Insights card system** | Structured insight cards with types: trend, correlation, anomaly, achievement |
| 59 | **No Insights generation engine** | Background analysis that produces structured insight objects |
| 60 | **Stats section is just 3 numbers** | Expand to: consistency, current run, best run, recoveries, total reps, stretch count, avg level |

### 🟢 Polish & Edge Cases

| # | Gap | Fix |
|---|-----|-----|
| 61 | **No RTL layout support** | Add `android:supportsRtl="true"` and mirror layouts |
| 62 | **No locale-aware date formatting** | Use `DateTimeFormatter.ofLocalizedDate()` |
| 63 | **No Dynamic Shortcuts registration** | Register shortcuts dynamically based on user's habits |
| 64 | **Coach fragment keyboard issues on small screens** | Adjust IME mode and composer padding |
| 65 | **No debouncing on rapid check-in taps** | Add 500ms debounce to prevent double check-ins |
| 66 | **No caching of computed Insights** | Cache Insights results with TTL of 5 minutes |
| 67 | **No graceful handling of corrupted JSON imports** | Validate JSON structure before importing, show specific errors |
| 68 | **No rate limiting on AI calls within a session** | Max 10 calls per minute to prevent accidental loops |
| 69 | **No max character limits on text fields** | Enforce: title=100, description=500, note=1000, memory=2000 |
| 70 | **No "quiet mode" per day of week** | Allow different quiet hours for weekdays vs weekends |
| 71 | **No notification channel for AI/proactive** | Add separate channel for AI suggestions |
| 72 | **No notification channel for milestones** | Add separate channel for milestone acknowledgments |
| 73 | **No notification channel for reviews** | Add separate channel for review reminders |
| 74 | **Onboarding has no template selection** | Add "Start from a template" option in step 5 |
| 75 | **Onboarding has no preview of result** | Show "Here's what your Today will look like" before finishing |
| 76 | **No "I don't know what to track" guided flow** | AI-assisted goal discovery during onboarding |
| 77 | **No dark mode scheduling** | Auto-switch dark mode based on time (separate from system) |
| 78 | **No multi-user support** | Profile switcher for shared tablets |
| 79 | **No Wear OS companion** | Future: simple check-in widget for watch |
| 80 | **No Slices for Google Assistant** | Future: "Hey Google, check in my walk" |

---

## Fix Priority Matrix

### Wave 0: Data Integrity (Week 1)
Issues: 31, 32, 33, 34, 35, 36, 37, 38, 39, 40

### Wave 1: Search & Navigation (Week 2)
Issues: 11, 12, 13, 14, 15, 16, 26

### Wave 2: Background Workers (Week 3)
Issues: 41, 42, 43, 44, 45

### Wave 3: Notifications & Engagement (Week 4)
Issues: 17, 18, 19, 20, 21, 70, 71, 72, 73

### Wave 4: Insights Tab Redesign (Weeks 5-7)
Issues: 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60

### Wave 5: Polish & Edge Cases (Week 8)
Issues: 28, 29, 30, 61-69, 74-78

---

## Insights Tab Redesign — Detailed Spec

### Current State
A flat list of: bar chart → 3-number stats → heatmap → text cards. All charts are non-interactive Canvas drawings. No period switching. No comparisons. No structured insights.

### Redesigned Architecture

```
┌──────────────────────────────────────┐
│  Insights                            │
│  Evidence, not judgment.             │
│                                      │
│  [7 days] [30 days] [90 days] [Year] │  ← Period switcher
│                                      │
│  ┌────────────────────────────────┐  │
│  │ THIS WEEK vs LAST WEEK         │  │  ← Delta card
│  │ Consistency  82%  ↑12%        │  │
│  │ Repetitions  24   ↑4          │  │
│  │ Recoveries   3    ↑1          │  │
│  └────────────────────────────────┘  │
│                                      │
│  TODAY'S SNAPSHOT                    │
│  ┌────────────────────────────────┐  │
│  │  [Progress Ring]   4/6 done    │  │  ← Interactive, tap to expand
│  │  Energy: ●●●○○  3/5           │  │
│  └────────────────────────────────┘  │
│                                      │
│  CONSISTENCY OVER TIME               │
│  ┌────────────────────────────────┐  │
│  │  ╱╲    ╱╲                      │  │  ← Line chart, touch tooltips
│  │ ╱  ╲╱╱╱  ╲╱╲                   │  │  ← Per-habit toggleable lines
│  │ ───────────────────            │  │
│  │ Mon Tue Wed Thu Fri Sat Sun    │  │
│  └────────────────────────────────┘  │
│  [Walk ━] [Journal ━] [Meditate ━]  │  ← Legend with toggle
│                                      │
│  PATTERNS                            │
│  ┌────────────────────────────────┐  │
│  │ Best day: Tuesday (94%)        │  │  ← Structured insight cards
│  │ Hardest day: Friday (61%)      │  │
│  │ Best time: Morning (89%)       │  │
│  │ Energy correlation: +0.7       │  │
│  └────────────────────────────────┘  │
│                                      │
│  WEEKLY RHYTHM                       │
│  ┌────────────────────────────────┐  │
│  │  M  T  W  T  F  S  S           │  │  ← Day-of-week bar chart
│  │  █  █  █  █  ▄  ▂  █           │  │
│  │ 92 88 95 85 61 43 90           │  │
│  └────────────────────────────────┘  │
│                                      │
│  CONSISTENCY MAP                     │
│  ┌────────────────────────────────┐  │
│  │ ░▓█▓░░▓█▓▓░▓█▓░░▓█▓▓░▓█▓░░▓  │  │  ← Interactive heatmap
│  │ ░░▓█▓░▓█▓░░▓█▓▓░▓█▓░░▓█▓▓░▓  │  │  ← Pinch to zoom week/month/year
│  └────────────────────────────────┘  │
│  ← Scroll for earlier weeks         │
│                                      │
│  HABIT BREAKDOWN                     │
│  ┌────────────────────────────────┐  │
│  │ Walk           92%  ████░  ▸   │  │  ← Expandable per-habit cards
│  │ Journal        71%  ███░░  ▸   │  │
│  │ Meditate       85%  ████░  ▸   │  │
│  └────────────────────────────────┘  │
│                                      │
│  RECOVERY                            │
│  ┌────────────────────────────────┐  │
│  │ Avg return time: 1.2 days      │  │  ← Recovery analytics
│  │ Recoveries this month: 4       │  │
│  │ Longest gap before return: 3d  │  │
│  └────────────────────────────────┘  │
│                                      │
│  MILESTONES                          │
│  ┌────────────────────────────────┐  │
│  │ ● First check-in    Jan 15     │  │  ← Timeline
│  │ ● 7-day run         Jan 22     │  │
│  │ ● First recovery    Feb 3      │  │
│  │ ● 21 repetitions    Feb 12     │  │
│  └────────────────────────────────┘  │
│                                      │
│  ENERGY                              │
│  ┌────────────────────────────────┐  │
│  │  ● Morning avg: 3.8/5         │  │  ← Energy analytics
│  │  ● Midday avg: 3.2/5          │  │
│  │  ● Evening avg: 2.6/5         │  │
│  │  Correlation with habits: +0.7 │  │
│  └────────────────────────────────┘  │
│                                      │
│  IDENTITY EVIDENCE                   │
│  ┌────────────────────────────────┐  │
│  │ "Someone who moves daily"      │  │  ← Serif italic
│  │  127 votes from 3 habits       │  │
│  └────────────────────────────────┘  │
│                                      │
└──────────────────────────────────────┘
```

### New Insight Card Types

```kotlin
sealed class InsightCard {
    data class Delta(
        val metric: String,
        val currentValue: String,
        val previousValue: String,
        val changePercent: Int,
        val direction: Direction    // UP, DOWN, FLAT
    ) : InsightCard()

    data class Pattern(
        val title: String,
        val detail: String,
        val confidence: Int,        // 0-100
        val type: PatternType       // TIME_OF_DAY, DAY_OF_WEEK, CORRELATION, ANOMALY
    ) : InsightCard()

    data class Achievement(
        val title: String,
        val detail: String,
        val date: String,
        val habitId: String?
    ) : InsightCard()

    data class Suggestion(
        val title: String,
        val detail: String,
        val actionLabel: String?,
        val action: JSONObject?     // Optional command to execute
    ) : InsightCard()

    data class Comparison(
        val habit1: String,
        val habit2: String,
        val correlation: Double,    // -1.0 to 1.0
        val interpretation: String
    ) : InsightCard()
}
```

### New Chart Components (Compose)

| Component | Features |
|-----------|----------|
| `SfLineChart` | Multi-series, touch tooltips, animated entry, legend with toggle, period-aware |
| `SfBarChart` (upgraded) | Grouped bars, tap detail, animated height, axis labels |
| `SfHeatmap` (upgraded) | Pinch-zoom (week/month/year), tap-for-day-detail, month labels, scroll |
| `SfClockChart` | 24-hour clock face with habit completion density arcs |
| `SfRecoveryArc` | Time from miss to return, showing improvement trend |
| `SfMilestoneTimeline` | Vertical timeline with date markers |
| `SfDeltaCard` | Current vs previous period with arrow indicators |
| `SfEnergyChart` | Three-line chart (morning/midday/evening) over time |
| `SfCorrelationDot` | Scatter plot of energy vs completion rate |
| `SfHabitBreakdown` | Expandable card with: consistency bar, run info, level distribution, miss reasons |

### Insights Generation Engine

```kotlin
object InsightsEngine {
    fun generate(repo: Repository, period: Int): List<InsightCard> {
        val cards = mutableListOf<InsightCard>()
        val stats = Insights.allStats(repo)
        val today = repo.clock.today()

        // 1. Delta cards (this period vs previous)
        cards.addAll(generateDeltas(repo, period, today))

        // 2. Pattern detection
        cards.addAll(detectPatterns(repo, stats, period))

        // 3. Correlations
        cards.addAll(detectCorrelations(repo, stats, period))

        // 4. Achievements
        cards.addAll(detectAchievements(repo, stats, period))

        // 5. Suggestions
        cards.addAll(generateSuggestions(repo, stats))

        return cards
    }

    private fun detectPatterns(repo: Repository, stats: List<HabitStats>, period: Int): List<InsightCard> {
        val patterns = mutableListOf<InsightCard>()
        val checkIns = repo.checkIns()

        // Day-of-week pattern
        val byDow = checkIns.filter { it.isSuccess }
            .groupBy { LocalDate.parse(it.date).dayOfWeek }
        val bestDay = byDow.maxByOrNull { it.value.size }
        val worstDay = byDow.minByOrNull { it.value.size }
        if (bestDay != null && worstDay != null && bestDay.key != worstDay.key) {
            patterns.add(InsightCard.Pattern(
                "Best day: ${bestDay.key.name.lowercase().replaceFirstChar { it.uppercase() }}",
                "${bestDay.value.size} completions vs ${worstDay.value.size} on ${worstDay.key.name.lowercase()}",
                75, PatternType.DAY_OF_WEEK
            ))
        }

        // Time-of-day pattern
        // ... (analyze cueTime distribution of successful check-ins)

        // Energy correlation
        val energyLogs = repo.energyLogs()
        if (energyLogs.size >= 10) {
            val correlation = computeEnergyCorrelation(repo, energyLogs, period)
            if (kotlin.math.abs(correlation) > 0.3) {
                patterns.add(InsightCard.Pattern(
                    "Energy matters",
                    if (correlation > 0) "Higher energy days have ${"%.0f".format(correlation * 100)}% more completions"
                    else "Surprisingly, lower energy days don't hurt your consistency",
                    ("%.0f".format(kotlin.math.abs(correlation) * 100)).toInt(),
                    PatternType.CORRELATION
                ))
            }
        }

        return patterns
    }
}
```

---

*Every gap listed here has a concrete fix. The Insights tab redesign alone adds 10 new chart types and a structured insight generation engine that transforms raw data into actionable understanding.*
