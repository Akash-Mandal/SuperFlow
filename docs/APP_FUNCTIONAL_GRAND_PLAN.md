# SuperFlow — Grand Functional Plan: Core Systems, Features & AI Overhaul

**Version:** 1.0 · August 2026  
**Scope:** Complete functional overhaul — core mechanisms, AI systems, Blueprint Studio rewrite, progressive growth engine, missing features, bug fixes, and new capabilities  
**Companion:** [UI/UX Grand Upgrade Plan](UI_UX_GRAND_UPGRADE_PLAN.md) — the visual/interaction layer

---

## Table of Contents

1. [Audit: What's Broken, Incomplete & Lacking](#1-audit-whats-broken-incomplete--lacking)
2. [The Progressive Growth Engine](#2-the-progressive-growth-engine)
3. [Blueprint Studio Rewrite](#3-blueprint-studio-rewrite)
4. [AI System Overhaul](#4-ai-system-overhaul)
5. [Core System Upgrades](#5-core-system-upgrades)
6. [New Feature Systems](#6-new-feature-systems)
7. [Expanded AI Capabilities Catalog](#7-expanded-ai-capabilities-catalog)
8. [Notification & Engagement System](#8-notification--engagement-system)
9. [Data & Insights Upgrades](#9-data--insights-upgrades)
10. [Implementation Roadmap](#10-implementation-roadmap)

---

## 1. Audit: What's Broken, Incomplete & Lacking

### 🔴 Critical Bugs

| # | Issue | Location | Impact |
|---|-------|----------|--------|
| 1 | **TTS (Text-to-Speech) not implemented** | No TTS code exists anywhere | AI responses are text-only, no voice output |
| 2 | **STT depends on Google Play Services** | `VoiceInput.kt` uses `SpeechRecognizer` | Fails on de-Googled phones, custom ROMs, many regions |
| 3 | **Blueprint Compiler dumps everything** | `Compiler.extractRequirements()` | Every bullet point becomes a requirement; no user intent filtering, no progressive plan |
| 4 | **No progressive difficulty system** | Entire codebase | User says "start small, grow weekly" — nothing exists to do this |
| 5 | **AI never proactively acts** | `Agent.kt`, `Coordinator.kt` | AI only responds to user messages; no background intelligence |
| 6 | **Energy tracking has no effect** | `Prefs.energyTracking`, `Insights.energyPattern()` | Energy is logged but never influences scheduling or recommendations |

### 🟡 Incomplete Features

| # | Feature | Status | What's Missing |
|---|---------|--------|---------------|
| 7 | **Flows** | Basic list of steps | No timing, no guided execution, no visual flow builder |
| 8 | **Scorecard** | Plain text entries | No scoring, no patterns, no trends over time |
| 9 | **Reviews** | Manual text fields | No AI-assisted analysis, no auto-generated insights, no action items |
| 10 | **Obstacle Plans** | If-then text pairs | Never surfaced at the right moment, no tracking of usage |
| 11 | **Recovery Center** | Static list | No guided recovery flow, no return celebration |
| 12 | **Blueprint Studio** | Extracts → Applies | No progressive plan generation, no weekly upgrade scheduling |
| 13 | **Widget** | Text + progress bar | No interactivity beyond one button, no multiple sizes |
| 14 | **App Shortcuts** | XML declared | Only 1 shortcut functional |
| 15 | **Notification Actions** | Opens app only | No inline check-in, no snooze, no "do tiny version" action |

### 🟢 Lacking in Practical Life

| # | Gap | Why It Matters |
|---|-----|---------------|
| 16 | **No habit templates** | Users face blank-page paralysis; pre-built templates for common goals would help |
| 17 | **No milestones** | Running 100 days without recognition feels empty (but avoid gamification — use quiet acknowledgment) |
| 18 | **No journaling** | Reviews are structured; there's no free-form reflection space |
| 19 | **No time estimation** | Habits don't estimate time cost; can't plan a realistic day |
| 20 | **No "day plan" view** | Can't see the day as a timeline with habits placed at times |
| 21 | **No sprint/season planning** | Everything is forever; no concept of "try this for 2 weeks" |
| 22 | **No habit difficulty rating** | No way to gauge if a habit is too hard before starting |
| 23 | **No weekly auto-review** | User must remember to do reviews; system should prompt with pre-filled analysis |
| 24 | **No AI memory across sessions** | AI forgets context between conversations |
| 25 | **No multi-habit routines** | "Morning routine" needs to chain habits, not just list them |
| 26 | **No accountability features** | Completely isolated; even a private weekly summary to share would help |
| 27 | **No what-if scenarios** | Can't preview "what if I add a 5th habit?" before committing |
| 28 | **AI can't analyze patterns** | No capability to detect time-of-day patterns, day-of-week patterns, correlations |
| 29 | **No contextual reminders** | Reminders are time-based only; no location, no "after you check in X" triggers |
| 30 | **No self-compassion mechanics** | Beyond "never miss twice" text, no structural forgiveness system |

---

## 2. The Progressive Growth Engine

### 2.1 Philosophy: "Start Small, Grow Every Week"

This is the **single most important new system** in SuperFlow. It transforms the app from a static habit tracker into an **adaptive growth system** that automatically scales difficulty, metrics, and expectations based on real performance.

### 2.2 The Growth Plan Model

```kotlin
data class GrowthPlan(
    val id: String = newId(),
    val habitId: String,
    val userId: String = "local",
    val createdAt: Long = System.currentTimeMillis(),
    
    // The progressive plan
    val phases: List<GrowthPhase>,
    val currentPhaseIndex: Int = 0,
    
    // Upgrade rules
    val upgradePolicy: UpgradePolicy,
    
    // Performance tracking
    val weeklySnapshots: List<WeeklySnapshot> = emptyList(),
    val lastUpgradeDate: String = "",
    val nextReviewDate: String = ""
)

data class GrowthPhase(
    val weekNumber: Int,           // 1-based week in the plan
    val label: String,             // "Foundation", "Building", "Growing", "Flourishing"
    val tinyStart: String,         // This phase's tiny version
    val minimumVersion: String,    // This phase's minimum
    val standardVersion: String,   // This phase's standard
    val stretchVersion: String,    // This phase's stretch
    val targetDays: Int,           // How many days per week
    val notes: String = "",        // What to focus on this phase
    val metrics: PhaseMetrics = PhaseMetrics()
)

data class PhaseMetrics(
    val minConsistency: Int = 60,   // Minimum % to consider upgrading
    val minRecoveries: Int = 0,     // Minimum recoveries showing resilience
    val maxMissesInARow: Int = 2,   // Maximum consecutive misses allowed
    val minEnergy: Int = 0          // Minimum average energy (0 = not tracked)
)

data class UpgradePolicy(
    val autoUpgrade: Boolean = true,       // Auto-upgrade or prompt user
    val upgradeDay: Int = 1,               // Day of week to evaluate (Monday=1)
    val minWeeksInPhase: Int = 1,          // Minimum weeks before upgrade
    val maxWeeksInPhase: Int = 4,          // Suggest review if stuck this long
    val downgradeOnStruggle: Boolean = true, // Auto-downgrade if failing
    val struggleThreshold: Int = 3          // Consecutive misses to trigger downgrade
)

data class WeeklySnapshot(
    val weekNumber: Int,
    val phaseIndex: Int,
    val consistency: Int,         // 0-100
    val repetitions: Int,
    val misses: Int,
    val recoveries: Int,
    val averageEnergy: Double?,
    val decision: UpgradeDecision,
    val date: String              // ISO date of the snapshot
)

enum class UpgradeDecision {
    UPGRADE,       // Ready for next phase
    HOLD,          // Stay in current phase
    DOWNGRADE,     // Go back a phase
    REVIEW_NEEDED  // Something unusual, human review needed
}
```

### 2.3 The Growth Engine Service

```kotlin
object GrowthEngine {
    
    /** Called daily by WorkManager. Evaluates all active growth plans. */
    fun evaluate(repo: Repository, prefs: Prefs) {
        val today = repo.clock.today()
        val plans = repo.growthPlans().filter { it.isActive() }
        
        for (plan in plans) {
            // Weekly review day?
            if (today.dayOfWeek.value == plan.upgradePolicy.upgradeDay) {
                evaluateWeekly(plan, repo, today)
            }
            
            // Daily struggle detection
            detectStruggle(plan, repo, today)
        }
    }
    
    /** Weekly evaluation: should we upgrade, hold, or downgrade? */
    fun evaluateWeekly(plan: GrowthPlan, repo: Repository, today: LocalDate): WeeklySnapshot {
        val phase = plan.phases[plan.currentPhaseIndex]
        val stats = Insights.forHabit(repo, repo.habit(plan.habitId)!!, today)
        
        val consistency = stats.consistency30
        val recoveries = stats.recoveries
        val missesInARow = stats.missesInARow
        
        val decision = when {
            consistency >= phase.metrics.minConsistency && 
            recoveries >= phase.metrics.minRecoveries &&
            missesInARow <= phase.metrics.maxMissesInARow &&
            plan.weeksInCurrentPhase() >= plan.upgradePolicy.minWeeksInPhase ->
                if (plan.currentPhaseIndex < plan.phases.lastIndex) UpgradeDecision.UPGRADE
                else UpgradeDecision.HOLD  // Already at max phase
                
            missesInARow >= plan.upgradePolicy.struggleThreshold &&
            plan.upgradePolicy.downgradeOnStruggle &&
            plan.currentPhaseIndex > 0 ->
                UpgradeDecision.DOWNGRADE
                
            plan.weeksInCurrentPhase() >= plan.upgradePolicy.maxWeeksInPhase ->
                UpgradeDecision.REVIEW_NEEDED
                
            else -> UpgradeDecision.HOLD
        }
        
        val snapshot = WeeklySnapshot(
            weekNumber = plan.weeksSinceStart() + 1,
            phaseIndex = plan.currentPhaseIndex,
            consistency = consistency,
            repetitions = stats.repetitions,
            misses = stats.missesInARow,
            recoveries = recoveries,
            averageEnergy = null, // TODO: from energy logs
            decision = decision,
            date = SfTime.format(today)
        )
        
        // Apply the decision
        when (decision) {
            UpgradeDecision.UPGRADE -> applyUpgrade(plan, repo)
            UpgradeDecision.DOWNGRADE -> applyDowngrade(plan, repo)
            UpgradeDecision.REVIEW_NEEDED -> notifyReviewNeeded(plan, repo)
            UpgradeDecision.HOLD -> {} // Stay put
        }
        
        repo.saveGrowthPlan(plan.copy(
            weeklySnapshots = plan.weeklySnapshots + snapshot
        ))
        
        return snapshot
    }
}
```

### 2.4 Growth Plan Generation

When a user creates a habit (or the AI creates one), the Growth Engine can generate a progressive plan:

```kotlin
fun generateGrowthPlan(habit: Habit, weeks: Int = 8): GrowthPlan {
    val phases = mutableListOf<GrowthPhase>()
    
    // Week 1-2: Foundation (tiny only)
    phases.add(GrowthPhase(
        weekNumber = 1,
        label = "Foundation",
        tinyStart = habit.tinyStart,
        minimumVersion = habit.tinyStart,  // Same as tiny — start easy
        standardVersion = habit.tinyStart,  // Standard IS tiny for now
        stretchVersion = habit.minimumVersion,
        targetDays = 3,  // Start with 3 days
        notes = "Just show up. The size doesn't matter yet."
    ))
    
    // Week 3-4: Building
    phases.add(GrowthPhase(
        weekNumber = 3,
        label = "Building",
        tinyStart = habit.tinyStart,
        minimumVersion = habit.minimumVersion.ifBlank { habit.tinyStart },
        standardVersion = habit.minimumVersion.ifBlank { habit.standardVersion },
        stretchVersion = habit.standardVersion,
        targetDays = 4,
        notes = "You've proven you can show up. Now grow a little."
    ))
    
    // Week 5-6: Growing
    phases.add(GrowthPhase(
        weekNumber = 5,
        label = "Growing",
        tinyStart = habit.tinyStart,
        minimumVersion = habit.minimumVersion.ifBlank { habit.tinyStart },
        standardVersion = habit.standardVersion,
        stretchVersion = habit.stretchVersion.ifBlank { habit.standardVersion },
        targetDays = 5,
        notes = "This is becoming who you are."
    ))
    
    // Week 7-8: Flourishing
    phases.add(GrowthPhase(
        weekNumber = 7,
        label = "Flourishing",
        tinyStart = habit.tinyStart,
        minimumVersion = habit.minimumVersion.ifBlank { habit.tinyStart },
        standardVersion = habit.standardVersion,
        stretchVersion = habit.stretchVersion.ifBlank { habit.standardVersion },
        targetDays = 7,  // Full week
        notes = "Full system. You've earned this."
    ))
    
    return GrowthPlan(
        habitId = habit.id,
        phases = phases,
        upgradePolicy = UpgradePolicy(
            autoUpgrade = true,
            minWeeksInPhase = 2,
            maxWeeksInPhase = 4,
            downgradeOnStruggle = true,
            struggleThreshold = 3
        )
    )
}
```

### 2.5 User-Facing Progressive System

**In the Habit Designer:**
- New section: "Growth Plan" after the Contract step
- Toggle: "Start small and grow automatically"
- When enabled: shows a visual timeline of phases
- User can customize each phase or accept AI-generated plan
- Preview: "Week 1: Walk for 2 minutes, 3 days a week → Week 8: Walk 30 minutes, daily"

**In Today:**
- Current phase indicator on habit cards: "Phase 2 · Building"
- When upgrade happens: subtle notification "Your system upgraded to Phase 3 — Growing"
- When downgrade happens: compassionate notification "Stepping back to Phase 1 — that's smart, not failure"

**In Insights:**
- Growth trajectory chart showing phase progression
- "You upgraded 3 times and downgraded once. That's a healthy pattern."

---

## 3. Blueprint Studio Rewrite

### 3.1 The Core Problem

The current Blueprint Compiler:
1. Extracts every bullet point and "actionable" line as a requirement
2. Creates habits from everything the document mentions
3. Has no concept of progressive difficulty or phasing
4. Doesn't understand user intent beyond keyword matching
5. Applies everything at once instead of building a sustainable plan

### 3.2 The New Blueprint Flow

```
User Intent → Plan Design → Phased Blueprint → Progressive Execution → Weekly Upgrades
```

**Step 1: Intent Capture** (new)
Before extracting from documents, the Blueprint asks:
- "What is your main goal with this plan?"
- "How much time per day can you realistically commit?"
- "What are you already doing that relates to this?"
- "What's your starting fitness/level?" (for health goals)
- "How many weeks do you want this to unfold over?"

**Step 2: Smart Extraction** (rewritten)
- AI reads documents and extracts **themes and principles**, not individual action items
- Groups related items into **capability areas**
- Prioritizes by the user's stated intent
- Identifies what's realistic for week 1 vs week 8

**Step 3: Phased Plan Generation** (new)
- Creates a GrowthPlan for each habit
- Week 1-2: Only the 2-3 most essential habits at their tiny versions
- Week 3-4: Add 1-2 more habits, increase existing ones
- Week 5+: Continue progressive addition and difficulty increase
- Each phase has clear metrics for advancement

**Step 4: Progressive Execution** (new)
- Doesn't apply everything at once
- Week 1: Creates only the foundation habits
- Each upgrade day: the Growth Engine evaluates and adjusts
- User sees: "This week: 3 habits at foundation level"
- Future habits shown as "Coming in Phase 3" (grayed out, locked)

**Step 5: Weekly Reports** (new)
- Every upgrade day, generates a report:
  - "Week 3 complete: 87% consistency, 2 recoveries"
  - "Upgrading to Phase 2: adding 'Meditation' at tiny level"
  - "Holding 'Journaling' at current level — 52% needs improvement"

### 3.3 New Compiler Architecture

```kotlin
object CompilerV2 {
    
    /** Phase 1: Extract themes and capabilities from sources */
    fun extractThemes(sources: List<BlueprintSource>, intent: UserIntent): List<Theme> {
        // Deterministic extraction groups related items
        val rawItems = extractRawItems(sources)
        val themes = groupIntoThemes(rawItems, intent)
        return prioritizeThemes(themes, intent)
    }
    
    /** Phase 2: Generate a progressive plan from themes */
    fun generateProgressivePlan(
        themes: List<Theme>,
        intent: UserIntent,
        existingHabits: List<Habit>
    ): ProgressivePlan {
        val totalWeeks = intent.durationWeeks.coerceIn(4, 52)
        val phasesNeeded = (totalWeeks + 1) / 2  // 2 weeks per phase
        
        // Sort themes by priority and dependency
        val ordered = topologicalSort(themes, intent)
        
        // Distribute across phases
        val phases = mutableListOf<PlanPhase>()
        val habitsPerPhase = (ordered.size.toFloat() / phasesNeeded).ceil().toInt().coerceAtLeast(1)
        
        for (i in 0 until phasesNeeded) {
            val phaseThemes = ordered.drop(i * habitsPerPhase).take(habitsPerPhase)
            phases.add(PlanPhase(
                weekStart = i * 2 + 1,
                weekEnd = (i + 1) * 2,
                label = phaseLabel(i, phasesNeeded),
                newHabits = phaseThemes.map { themeToHabit(it, i) },
                upgrades = if (i > 0) existingHabitsForUpgrade(i) else emptyList(),
                focusArea = phaseThemes.firstOrNull()?.name ?: ""
            ))
        }
        
        return ProgressivePlan(
            phases = phases,
            totalWeeks = totalWeeks,
            estimatedDailyTimeMinutes = estimateTime(phases)
        )
    }
    
    /** Phase 3: Compile to executable commands per phase */
    fun compilePhase(
        phase: PlanPhase,
        project: BlueprintProject
    ): List<Requirement> {
        // Only compile THIS phase's requirements
        val reqs = mutableListOf<Requirement>()
        
        for (habit in phase.newHabits) {
            reqs.add(Requirement(
                projectId = project.id,
                text = "Phase ${phase.weekStart}: ${habit.title}",
                status = RequirementStatus.ACCEPTED,
                plannedCommand = jsonOf(
                    "command" to "create_habit",
                    "args" to habitToArgs(habit, phase)  // Phase-appropriate difficulty
                ).toString()
            ))
        }
        
        return reqs
    }
}

data class UserIntent(
    val goal: String,
    val dailyTimeMinutes: Int = 30,
    val currentLevel: String = "beginner",
    val existingRoutines: List<String> = emptyList(),
    val durationWeeks: Int = 8,
    val priorityAreas: List<String> = emptyList()
)

data class Theme(
    val name: String,
    val items: List<String>,
    val priority: Int,
    val dependencies: List<String> = emptyList(),
    val estimatedMinutesPerDay: Int = 5
)

data class PlanPhase(
    val weekStart: Int,
    val weekEnd: Int,
    val label: String,
    val newHabits: List<PlannedHabit>,
    val upgrades: List<HabitUpgrade>,
    val focusArea: String
)
```

### 3.4 Blueprint UI Redesign

The Blueprint Studio screen becomes:

```
┌──────────────────────────────────┐
│  Blueprint Studio                │
│                                  │
│  ┌────────────────────────────┐  │
│  │ 🎯 Your Plan: "Get Fit"   │  │
│  │ Phase 3 of 4 · Week 5-6   │  │
│  │ ████████████░░░░ 62%       │  │
│  └────────────────────────────┘  │
│                                  │
│  THIS WEEK                       │
│  ┌────────────────────────────┐  │
│  │ ✅ Walk 15 min (upgraded)  │  │
│  │ ✅ Stretch 5 min           │  │
│  │ 🔒 Meditation (Phase 4)    │  │
│  └────────────────────────────┘  │
│                                  │
│  UPGRADE REPORT                  │
│  Last evaluation: Monday         │
│  Consistency: 87% → Upgraded     │
│  Walk: 15min → 20min next week  │
│                                  │
│  SOURCES (3)                     │
│  · fitness_plan.md               │
│  · my_notes.txt                  │
│  · workout_guide.pdf             │
│                                  │
│  [Add source] [Edit intent]     │
│  [View full plan timeline]      │
└──────────────────────────────────┘
```

---

## 4. AI System Overhaul

### 4.1 Text-to-Speech (TTS) — New Implementation

**Problem:** TTS doesn't exist at all.

**Solution:** Implement Android's `TextToSpeech` API with proper lifecycle management:

```kotlin
class SfTextToSpeech(private val context: Context) {
    
    private var tts: TextToSpeech? = null
    private var initialized = false
    private var pendingText: String? = null
    
    init {
        tts = TextToSpeech(context) { status ->
            initialized = status == TextToSpeech.SUCCESS
            if (initialized) {
                tts?.language = Locale.getDefault()
                tts?.setSpeechRate(0.9f)  // Slightly slower for calm feel
                tts?.setPitch(1.0f)
                pendingText?.let { speak(it); pendingText = null }
            }
        }
    }
    
    fun speak(text: String) {
        if (!Prefs.get(context).ttsEnabled) return
        if (!initialized) { pendingText = text; return }
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "sf_${System.currentTimeMillis()}")
    }
    
    fun stop() { tts?.stop() }
    fun destroy() { tts?.stop(); tts?.shutdown() }
}
```

**Integration points:**
- Coach AI responses auto-read aloud (toggleable)
- Checkpoint reminders spoken
- Check-in confirmations: brief spoken acknowledgment
- Blueprint reports read aloud on request

### 4.2 Speech-to-Text (STT) — Platform-Independent

**Problem:** Current `VoiceInput` uses Google's `SpeechRecognizer` which fails on de-Googled devices.

**Solution:** Multi-provider STT:

```kotlin
object VoiceInputV2 {
    
    enum class Provider {
        PLATFORM,        // Android SpeechRecognizer (Google)
        WHISPER_LOCAL,   // whisper.cpp local model (offline)
        WHISPER_API,     // OpenAI Whisper API
        VOSK             // Vosk offline models
    }
    
    fun availableProviders(context: Context): List<Provider> = buildList {
        if (SpeechRecognizer.isRecognitionAvailable(context)) add(Provider.PLATFORM)
        if (Prefs.get(context).whisperApiKey.isNotBlank()) add(Provider.WHISPER_API)
        // Check for local whisper.cpp binary
        if (File(context.filesDir, "whisper").exists()) add(Provider.WHISPER_LOCAL)
    }
    
    fun create(context: Context, provider: Provider? = null): VoiceEngine {
        val chosen = provider ?: Prefs.get(context).preferredSttProvider
            ?.let { runCatching { Provider.valueOf(it) }.getOrNull() }
            ?: availableProviders(context).firstOrNull()
            ?: throw IllegalStateException("No STT provider available")
            
        return when (chosen) {
            Provider.PLATFORM -> PlatformVoiceEngine(context)
            Provider.WHISPER_API -> WhisperApiVoiceEngine(context)
            Provider.WHISPER_LOCAL -> WhisperLocalVoiceEngine(context)
            Provider.VOSK -> VoskVoiceEngine(context)
        }
    }
}
```

### 4.3 Proactive AI — Background Intelligence

**Problem:** AI only responds to user messages. Never initiates.

**Solution:** `AiScheduler` — a WorkManager job that runs the AI's proactive analysis:

```kotlin
class ProactiveAiWorker(context: Context, params: WorkerParameters) 
    : CoroutineWorker(context, params) {
    
    override suspend fun doWork(): Result {
        val repo = Repository.get(applicationContext)
        val prefs = Prefs.get(applicationContext)
        if (!prefs.aiEnabled || !prefs.proactiveAi) return Result.success()
        
        val today = repo.clock.today()
        val suggestions = mutableListOf<ProactiveSuggestion>()
        
        // 1. Morning: suggest today's focus based on open habits
        if (isMorning(today)) {
            val open = repo.habitsForDay(today)
            if (open.size > 3) {
                suggestions.add(ProactiveSuggestion(
                    type = SuggestionType.FOCUS,
                    text = "You have ${open.size} habits today. Want me to pick 3 for focus?",
                    priority = Priority.MEDIUM
                ))
            }
        }
        
        // 2. Struggle detection: 3+ misses in a row
        val stats = Insights.allStats(repo, today)
        stats.filter { it.missesInARow >= 3 }.forEach { s ->
            suggestions.add(ProactiveSuggestion(
                type = SuggestionType.STRUGGLE,
                text = "\"${s.habit.title}\" has missed ${s.missesInARow} times in a row. " +
                        "I can shrink it to its tiny version for this week.",
                priority = Priority.HIGH,
                autoAction = jsonOf(
                    "command" to "update_habit",
                    "args" to jsonOf(
                        "habit" to s.habit.id,
                        "field" to "standardVersion",
                        "value" to s.habit.tinyStart
                    )
                )
            ))
        }
        
        // 3. Weekly review reminder (with pre-filled data)
        if (today.dayOfWeek == DayOfWeek.SUNDAY && repo.reviews().none { 
            it.kind == ReviewKind.WEEKLY && it.createdAt > weekStartMillis(today) 
        }) {
            val summary = Insights.summaryText(repo, 7)
            suggestions.add(ProactiveSuggestion(
                type = SuggestionType.REVIEW,
                text = "It's Sunday. Here's your week:\n\n$summary\n\nWant to save a review?",
                priority = Priority.LOW
            ))
        }
        
        // 4. Energy-aware scheduling
        if (prefs.energyTracking) {
            val energyLogs = repo.energyLogs()
            if (energyLogs.size >= 10) {
                val morningEnergy = energyLogs.filter { it.checkpoint == Checkpoint.MORNING }
                    .map { it.energy }.average()
                val eveningEnergy = energyLogs.filter { it.checkpoint == Checkpoint.EVENING }
                    .map { it.energy }.average()
                
                if (morningEnergy > eveningEnergy + 1.0) {
                    val eveningHabits = repo.habits().filter { 
                        it.cueTime.isNotBlank() && Dates.minutesOfDay(it.cueTime) > 17 * 60 
                    }
                    if (eveningHabits.isNotEmpty()) {
                        suggestions.add(ProactiveSuggestion(
                            type = SuggestionType.ENERGY,
                            text = "Your energy tends to be higher in the morning. " +
                                    "Consider moving ${eveningHabits.first().title} earlier.",
                            priority = Priority.LOW
                        ))
                    }
                }
            }
        }
        
        // 5. Growth plan evaluation
        val growthPlans = repo.growthPlans().filter { it.isActive() }
        for (plan in growthPlans) {
            if (today.dayOfWeek.value == plan.upgradePolicy.upgradeDay) {
                val snapshot = GrowthEngine.evaluateWeekly(plan, repo, today)
                val habit = repo.habit(plan.habitId) ?: continue
                when (snapshot.decision) {
                    UpgradeDecision.UPGRADE -> suggestions.add(ProactiveSuggestion(
                        type = SuggestionType.GROWTH,
                        text = "Great progress on \"${habit.title}\"! " +
                                "${snapshot.consistency}% consistency this week. " +
                                "Ready to upgrade to the next level?",
                        priority = Priority.MEDIUM
                    ))
                    UpgradeDecision.DOWNGRADE -> suggestions.add(ProactiveSuggestion(
                        type = SuggestionType.GROWTH,
                        text = "\"${habit.title}\" has been tough. " +
                                "Stepping back to an easier level isn't failure — it's smart.",
                        priority = Priority.MEDIUM
                    ))
                    else -> {}
                }
            }
        }
        
        // Deliver suggestions
        for (suggestion in suggestions.sortedByDescending { it.priority }) {
            repo.saveProactiveSuggestion(suggestion)
            if (prefs.proactiveNotifications) {
                Reminders.showProactiveNotification(applicationContext, suggestion)
            }
        }
        
        return Result.success()
    }
}
```

### 4.4 Structured AI Memory

**Problem:** AI forgets everything between conversations.

**Solution:** Structured memory system:

```kotlin
data class AiMemory(
    val id: String = newId(),
    val category: MemoryCategory,
    val content: String,
    val importance: Int = 5,       // 1-10
    val lastAccessed: Long = System.currentTimeMillis(),
    val accessCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

enum class MemoryCategory {
    USER_PREFERENCE,     // "User prefers morning workouts"
    USER_CONTEXT,        // "User has two kids, limited time"
    HABIT_PATTERN,       // "Walk habit always done by 8am"
    STRUGGLE,            // "Meditation has failed 3 times — too long"
    ACHIEVEMENT,         // "100-day reading streak"
    GOAL,                // "Training for a 5K in October"
    LIFE_EVENT           // "Going on vacation next week"
}
```

The `MainBrain.buildContext()` now includes top memories:
```kotlin
if (prefs.contextIncludeMemory) {
    val memories = repo.memories()
        .sortedByDescending { it.importance * it.accessCount }
        .take(10)
    if (memories.isNotEmpty()) {
        sb.append("\nThings you've told me to remember:\n")
        memories.forEach { sb.append("- [${it.category}] ${it.content}\n") }
    }
}
```

### 4.5 AI Studio Redesign

The Coach tab becomes **Studio** — a unified AI workspace:

```
┌──────────────────────────────────────┐
│  Studio                    [⚙] [📋] │
│                                      │
│  ┌────────────────────────────────┐  │
│  │ ⚡ Full Control   gpt-4o-mini │  │  ← Status bar
│  └────────────────────────────────┘  │
│                                      │
│  QUICK ACTIONS                       │
│  ┌─────┐┌─────┐┌─────┐┌─────┐     │
│  │📋Plan││📊Anz││🔄Rev││💡Ide│     │  ← Horizontal chips
│  └─────┘└─────┘└─────┘└─────┘     │
│                                      │
│  SUGGESTIONS                         │  ← Proactive AI suggestions
│  ┌────────────────────────────────┐  │
│  │ 💡 "Walk" missed 3×. Shrink?  │  │
│  │    [Apply] [Dismiss] [Edit]   │  │
│  └────────────────────────────────┘  │
│  ┌────────────────────────────────┐  │
│  │ 📊 Weekly: 87% consistency    │  │
│  │    [View report] [Review]     │  │
│  └────────────────────────────────┘  │
│                                      │
│  CONVERSATION                        │
│  ┌────────────────────────────────┐  │
│  │ 🤖 Your system is settling.   │  │
│  │    Walk is at 92%, Journal at │  │
│  │    71%. Consider upgrading... │  │
│  └────────────────────────────────┘  │
│  ┌────────────────────────────────┐  │
│  │ You: Add a morning stretch    │  │
│  └────────────────────────────────┘  │
│                                      │
│  ┌──────────────────────────┐ [🎤] │  ← Rich input
│  │ Tell Studio what to do   │ [➤] │
│  └──────────────────────────┘      │
└──────────────────────────────────────┘
```

### 4.6 AI Capabilities — Quick Actions

Each quick action chip triggers a specialized AI prompt:

| Action | What It Does |
|--------|-------------|
| **Plan** | "Design my week" — generates optimal habit scheduling |
| **Analyze** | Deep pattern analysis: time-of-day, day-of-week, correlations |
| **Review** | Pre-fills a weekly/monthly review with data and suggestions |
| **Ideas** | Suggests new habits, obstacle plans, environment changes |
| **Blueprint** | Opens Blueprint Studio |
| **Diagnose** | "Why am I struggling?" — analyzes patterns and suggests fixes |
| **Upgrade** | Evaluates all growth plans and suggests upgrades |
| **Reflect** | Guided journaling prompt based on current state |

---

## 5. Core System Upgrades

### 5.1 Habit Templates Library

Pre-built habit templates organized by life area:

```kotlin
object HabitTemplates {
    
    fun forArea(area: LifeArea): List<HabitTemplate> = when (area) {
        LifeArea.HEALTH -> listOf(
            HabitTemplate("Morning walk", "10 min walk", "Put on shoes and step outside",
                "07:30", "daily", "After waking up"),
            HabitTemplate("Drink water", "8 glasses", "Fill one glass",
                "", "daily", ""),
            HabitTemplate("Stretch", "10 min stretching", "Do one stretch",
                "07:00", "daily", "After waking up"),
            // ... 20+ templates per area
        )
        LifeArea.LEARNING -> listOf(
            HabitTemplate("Read", "20 min reading", "Open the book, read one page",
                "21:00", "daily", "Before bed"),
            HabitTemplate("Language practice", "15 min", "Open the app, do one lesson",
                "08:00", "weekdays", "After breakfast"),
            // ...
        )
        // ... all areas
    }
    
    fun suggestForGoal(goalTitle: String): List<HabitTemplate> {
        val g = goalTitle.lowercase()
        return when {
            g.contains("run") || g.contains("5k") || g.contains("marathon") -> runningPlan()
            g.contains("read") || g.contains("book") -> readingPlan()
            g.contains("write") || g.contains("novel") || g.contains("blog") -> writingPlan()
            g.contains("meditat") || g.contains("mindful") || g.contains("calm") -> mindfulnessPlan()
            g.contains("weight") || g.contains("fit") || g.contains("strong") -> fitnessPlan()
            g.contains("sleep") -> sleepPlan()
            g.contains("learn") || g.contains("study") -> learningPlan()
            g.contains("save") || g.contains("money") || g.contains("budget") -> financePlan()
            else -> generalWellbeing()
        }
    }
}
```

### 5.2 Milestone System (Quiet Acknowledgment)

```kotlin
data class Milestone(
    val id: String = newId(),
    val habitId: String?,        // null = overall
    val type: MilestoneType,
    val value: Int,
    val label: String,
    val achievedAt: Long = System.currentTimeMillis(),
    val acknowledged: Boolean = false
)

enum class MilestoneType {
    FIRST_CHECKIN,      // "First step taken"
    FIRST_WEEK,         // "First full week"
    CONSISTENCY_50,     // "Half the time — that's real"
    CONSISTENCY_80,     // "Most days — this is becoming you"
    CONSISTENCY_95,     // "Almost automatic"
    REPS_7,             // "7 repetitions"
    REPS_21,            // "21 repetitions"
    REPS_66,            // "66 repetitions" (the research number)
    REPS_100,           // "100 repetitions"
    REPS_365,           // "A year of showing up"
    RECOVERY_3,         // "Returned 3 times — that's the real skill"
    RECOVERY_10,        // "10 comebacks"
    STREAK_7,           // "7 in a row"
    STREAK_30,          // "30 in a row"
    ALL_DONE_DAY,       // "Everything done in one day"
    ALL_DONE_WEEK       // "Every habit, every day, all week"
}
```

Milestones are shown as **quiet acknowledgments** — never flashy celebrations:
- A subtle text card in Today: "21 repetitions of Walk. That's becoming real."
- A gentle gold dot on the Insights chart
- No confetti, no fireworks, no sound (unless the user enables it)

### 5.3 Sprint / Season Planning

```kotlin
data class Sprint(
    val id: String = newId(),
    val title: String,
    val startDate: String,
    val endDate: String,
    val focusHabits: List<String>,     // Habit IDs
    val goals: List<String>,           // Text goals for this sprint
    val status: SprintStatus = SprintStatus.PLANNED,
    val reviewNotes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

enum class SprintStatus { PLANNED, ACTIVE, COMPLETED, ABANDONED }
```

Users can create time-boxed experiments:
- "2-Week Morning Routine Sprint" — try 3 morning habits for 2 weeks, then evaluate
- "Summer Fitness Season" — 12-week progressive plan
- At the end: auto-generated review with data

### 5.4 Day Timeline View

A new view mode for Today that shows habits on a timeline:

```
06:00 ─── Wake up
07:00 ─── ☐ Walk 10 min (cue: 07:30)
07:30 ─── ☐ Meditate 5 min (anchor: after walk)
08:00 ─── Breakfast
12:00 ─── ☐ Drink water (midday check)
13:00 ─── Midday checkpoint
17:00 ─── ☐ Journal (cue: 17:00)
20:00 ─── ☐ Read 20 min (anchor: before bed)
20:30 ─── Evening checkpoint
21:00 ─── ☐ Stretch (cue: 21:00)
22:00 ─── Wind down
```

### 5.5 Journaling System

```kotlin
data class JournalEntry(
    val id: String = newId(),
    val date: String,
    val prompt: String = "",           // Optional guided prompt
    val content: String,
    val mood: Int? = null,             // 1-5
    val tags: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)
```

- **Guided prompts**: "What worked today?", "What would you tell your past self?", "What evidence did you collect about who you're becoming?"
- **Free-form**: Just write
- **AI reflection**: "Based on your journal, I notice..." (optional, with cloud AI)
- **Linked to identity**: Journal entries can be tagged as evidence for an identity statement

### 5.6 Environment Design Toolkit

The Habit Designer's "environmentPrep" field is currently unused. Add a structured toolkit:

```kotlin
data class EnvironmentDesign(
    val habitId: String,
    val makeObvious: List<String> = emptyList(),     // "Put running shoes by the door"
    val makeAttractive: List<String> = emptyList(),   // "Pair with favorite podcast"
    val makeEasy: List<String> = emptyList(),         // "Prep gym bag the night before"
    val makeSatisfying: List<String> = emptyList(),   // "Track on the wall calendar"
    // For REDUCE mode (inversions):
    val makeInvisible: List<String> = emptyList(),
    val makeUnattractive: List<String> = emptyList(),
    val makeDifficult: List<String> = emptyList(),
    val makeUnsatisfying: List<String> = emptyList()
)
```

### 5.7 Habit Difficulty Estimator

When creating a habit, estimate its difficulty:

```kotlin
fun estimateDifficulty(habit: Habit): DifficultyRating {
    var score = 0
    
    // Time estimate
    val minutes = estimateMinutes(habit.standardVersion)
    score += when {
        minutes <= 2 -> 0
        minutes <= 5 -> 1
        minutes <= 15 -> 2
        minutes <= 30 -> 3
        else -> 4
    }
    
    // Has tiny start? (reduces difficulty)
    if (habit.tinyStart.isNotBlank()) score -= 1
    
    // Has anchor? (reduces difficulty)
    if (habit.anchorText.isNotBlank()) score -= 1
    
    // Has reward? (reduces difficulty)
    if (habit.reward.isNotBlank()) score -= 1
    
    // Time of day (evening habits are harder)
    if (habit.cueTime.isNotBlank()) {
        val minutes = SfTime.minutesOfDay(habit.cueTime)
        if (minutes > 20 * 60) score += 1  // After 8pm
    }
    
    return when (score.coerceIn(0, 5)) {
        0, 1 -> DifficultyRating.EASY
        2, 3 -> DifficultyRating.MODERATE
        else -> DifficultyRating.CHALLENGING
    }
}
```

### 5.8 Obstacle Plan Surfacing

Obstacle plans are created but never shown at the right moment. Fix:

- When a user marks a habit as "Missed", immediately show relevant obstacle plans: "You planned for this: If [obstacle], then [action]"
- In Today, if a habit's cue time passes without check-in, show a gentle nudge with the obstacle plan
- AI can suggest obstacle plans based on common failure patterns

---

## 6. New Feature Systems

### 6.1 Habit Stacking / Routine Builder

Upgrade the existing Flows system into a proper routine builder:

```kotlin
data class Routine(
    val id: String = newId(),
    val title: String,                 // "Morning Routine"
    val trigger: String,               // "After waking up"
    val estimatedMinutes: Int = 30,
    val steps: List<RoutineStep>,
    val status: Status = Status.ACTIVE
)

data class RoutineStep(
    val id: String = newId(),
    val routineId: String,
    val habitId: String?,              // Link to existing habit or null
    val title: String,
    val durationMinutes: Int = 5,
    val orderIndex: Int = 0,
    val transitionNote: String = ""    // "Then move to..."
)
```

- Visual flow builder with drag-and-drop
- Timer for each step (optional)
- "Run routine" mode: guided step-by-step with timer
- Total time estimate shown
- Can be checked in as a single unit or step-by-step

### 6.2 Commitment Sprint System

```kotlin
data class CommitmentSprint(
    val id: String = newId(),
    val title: String,
    val habitIds: List<String>,
    val startDate: String,
    val durationDays: Int,
    val commitment: String,            // "I will do the tiny version every day"
    val stakes: String = "",           // Optional accountability stakes
    val status: SprintStatus = SprintStatus.ACTIVE,
    val createdAt: Long = System.currentTimeMillis()
)
```

- "I commit to walking for 2 minutes every day for 14 days"
- Progress tracked separately from the main system
- At the end: automatic review with data
- Success celebration, compassionate handling of failure

### 6.3 Weekly Auto-Review

Every Sunday (configurable), the system generates a pre-filled review:

```kotlin
fun generateAutoReview(repo: Repository, kind: ReviewKind): Review {
    val days = when (kind) {
        ReviewKind.WEEKLY -> 7
        ReviewKind.MONTHLY -> 30
        ReviewKind.QUARTERLY -> 90
    }
    val stats = Insights.allStats(repo)
    val summary = Insights.summaryText(repo, days)
    
    val whatWorked = buildString {
        val strong = stats.filter { it.consistency30 >= 80 }
        if (strong.isNotEmpty()) {
            append("Strong this ${kind.name.lowercase()}: ")
            append(strong.joinToString(", ") { "${it.habit.title} (${it.consistency30}%)" })
        }
    }
    
    val whatDidnt = buildString {
        val weak = stats.filter { it.consistency30 < 50 }
        if (weak.isNotEmpty()) {
            append("Struggling: ")
            append(weak.joinToString(", ") { "${it.habit.title} (${it.consistency30}%)" })
        }
    }
    
    val systemChange = buildString {
        val redesign = stats.filter { it.missesInARow >= 2 }
        if (redesign.isNotEmpty()) {
            append("Consider shrinking: ")
            append(redesign.joinToString(", ") { it.habit.title })
        }
    }
    
    return Review(
        kind = kind,
        periodLabel = periodLabel(kind, repo.clock.today()),
        whatWorked = whatWorked,
        whatDidnt = whatDidnt,
        systemChange = systemChange,
        identityEvidence = ""  // User fills this in
    )
}
```

### 6.4 Smart Notification Actions

Instead of just opening the app, notifications get actionable buttons:

```kotlin
// Habit reminder notification
NotificationCompat.Builder(context, CHANNEL_HABITS)
    .setContentTitle(habit.title)
    .setContentText(habit.tinyStart.ifBlank { habit.title })
    .addAction(R.drawable.ic_check, "Done (Standard)", checkInPendingIntent(habit, "STANDARD"))
    .addAction(R.drawable.ic_check, "Tiny", checkInPendingIntent(habit, "TINY"))
    .addAction(R.drawable.ic_pause, "Skip", skipPendingIntent(habit))
    .setContentIntent(openAppPendingIntent(habit))
```

### 6.5 What-If Simulator

Before adding a new habit, preview its impact:

```kotlin
fun simulateAddition(repo: Repository, newHabit: Habit): Simulation {
    val currentDaily = repo.habitsForDay(repo.clock.today()).size
    val currentAvgTime = estimateTotalDailyTime(repo)
    val newTime = estimateMinutes(newHabit.standardVersion)
    
    return Simulation(
        currentHabits = currentDaily,
        newHabits = currentDaily + 1,
        currentMinutes = currentAvgTime,
        newMinutes = currentAvgTime + newTime,
        riskLevel = when {
            currentDaily + 1 > 7 -> RiskLevel.HIGH   // Too many habits
            currentAvgTime + newTime > 120 -> RiskLevel.HIGH  // Too much time
            currentDaily + 1 > 5 -> RiskLevel.MEDIUM
            else -> RiskLevel.LOW
        },
        advice = when {
            currentDaily + 1 > 7 -> "You already have $currentDaily habits. Research suggests 3-5 new behaviours at once is the maximum."
            currentAvgTime + newTime > 120 -> "This would bring your daily commitment to ${currentAvgTime + newTime} minutes. That's a lot."
            else -> "This looks manageable. Start with the tiny version."
        }
    )
}
```

### 6.6 Accountability Export

Private, shareable weekly summary:

```kotlin
fun accountabilityReport(repo: Repository, days: Int = 7): String {
    val stats = Insights.allStats(repo)
    val (done, total) = Insights.dayProgress(repo)
    
    return buildString {
        append("My SuperFlow Week\n\n")
        append("Identity: ${repo.identities().firstOrNull()?.statement ?: "—"}\n\n")
        
        append("Habits this week:\n")
        stats.forEach { s ->
            val bar = "█".repeat(s.consistency30 / 10) + "░".repeat(10 - s.consistency30 / 10)
            append("  ${s.habit.title}: $bar ${s.consistency30}%\n")
        }
        
        append("\nRecoveries: ${stats.sumOf { it.recoveries }}\n")
        append("Best run: ${stats.maxOfOrNull { it.bestRun } ?: 0} days\n")
        append("\n— SuperFlow")
    }
}
```

---

## 7. Expanded AI Capabilities Catalog

### 7.1 Current: 49 capabilities
### 7.2 New Capabilities (target: 80+)

| Category | New Capability | Description |
|----------|---------------|-------------|
| **Growth** | `create_growth_plan` | Create a progressive growth plan for a habit |
| | `evaluate_growth_plan` | Run weekly evaluation on a growth plan |
| | `upgrade_phase` | Manually advance to next phase |
| | `downgrade_phase` | Manually step back a phase |
| | `list_growth_plans` | Show all active growth plans with status |
| **Sprints** | `create_sprint` | Create a time-boxed commitment sprint |
| | `complete_sprint` | Mark sprint as complete with review |
| | `abandon_sprint` | End sprint early with compassionate note |
| **Routines** | `create_routine` | Create a habit stacking routine |
| | `add_routine_step` | Add a step to a routine |
| | `run_routine` | Start guided routine execution |
| | `check_in_routine` | Check in entire routine as done |
| **Templates** | `suggest_templates` | Get habit templates for a goal |
| | `apply_template` | Create a habit from a template |
| | `list_templates` | Browse all templates by area |
| **Journal** | `create_journal_entry` | Write a journal entry |
| | `suggest_prompt` | Get a guided journal prompt |
| | `link_journal_identity` | Link entry as identity evidence |
| **Analysis** | `analyze_patterns` | Detect time-of-day, day-of-week patterns |
| | `analyze_correlations` | Find habit-habit correlations |
| | `predict_consistency` | Predict next week's consistency |
| | `difficulty_assessment` | Rate all habits by difficulty |
| | `time_audit` | Estimate daily time commitment |
| **Coaching** | `weekly_coaching_report` | Generate comprehensive weekly report |
| | `suggest_obstacle_plan` | Suggest an if-then plan based on patterns |
| | `suggest_environment` | Suggest environment changes |
| | `morning_briefing` | Today's plan with energy-aware ordering |
| | `evening_reflection` | End-of-day summary with prompt |
| **Blueprint** | `create_progressive_blueprint` | Blueprint with phased execution |
| | `evaluate_blueprint_phase` | Check if current phase is complete |
| | `advance_blueprint_phase` | Move to next blueprint phase |
| **Memory** | `remember` | Store a structured memory |
| | `forget` | Remove a memory |
| | `list_memories` | Show what AI remembers |
| **What-If** | `simulate_add_habit` | Preview impact of adding a habit |
| | `simulate_remove_habit` | Preview impact of removing a habit |
| | `simulate_reschedule` | Preview impact of changing schedule |
| **Accountability** | `generate_report` | Create shareable progress report |
| | `export_weekly_summary` | Export week's data as text |
| **Settings** | `set_theme` | Change app theme |
| | `set_density` | Change content density |
| | `set_haptics` | Configure haptic settings |
| | `set_quiet_hours` | Update quiet hours |

---

## 8. Notification & Engagement System

### 8.1 Smart Notification Scheduling

```kotlin
object SmartNotifications {
    
    fun scheduleAll(context: Context, repo: Repository, prefs: Prefs) {
        if (!prefs.remindersEnabled) return
        
        val today = repo.clock.today()
        val habits = repo.habitsForDay(today)
        
        for (habit in habits) {
            if (!habit.reminderEnabled) continue
            
            // Time-based reminder at cue time
            if (habit.cueTime.isNotBlank()) {
                scheduleReminder(context, habit, habit.cueTime)
            }
            
            // "Getting late" reminder if not done by evening
            if (habit.cueTime.isNotBlank()) {
                val cueMinutes = SfTime.minutesOfDay(habit.cueTime)
                val lateMinutes = cueMinutes + 120  // 2 hours after cue
                if (lateMinutes < 22 * 60) {  // Before quiet hours
                    scheduleLateReminder(context, habit, lateMinutes)
                }
            }
        }
        
        // Checkpoint reminders
        if (prefs.checkpointsEnabled) {
            scheduleCheckpoint(context, prefs.morningCheckpoint, Checkpoint.MORNING)
            scheduleCheckpoint(context, prefs.middayCheckpoint, Checkpoint.MIDDAY)
            scheduleCheckpoint(context, prefs.eveningCheckpoint, Checkpoint.EVENING)
        }
        
        // Weekly review reminder
        scheduleWeeklyReview(context, prefs)
        
        // Growth plan evaluation
        scheduleGrowthEvaluations(context, repo)
    }
}
```

### 8.2 Notification Content Intelligence

Instead of generic "Time for your habit!", notifications adapt:

| Situation | Notification |
|-----------|-------------|
| **Normal reminder** | "Walk 10 min — or just put on your shoes 🚶" |
| **Struggling habit** | "Walk: even the tiny version counts today" |
| **After a miss** | "Welcome back. Your tiny start: put on your shoes" |
| **Streak active** | "Walk: day 12. Your shoes are by the door?" |
| **Low energy logged** | "Low energy day. Tiny versions are enough." |
| **Morning briefing** | "Good morning. 4 habits today. Your first: Walk at 07:30" |
| **Evening reflection** | "3 of 4 done. Journal is the last one. One sentence counts." |

---

## 9. Data & Insights Upgrades

### 9.1 New Analytics

| Analysis | Description |
|----------|-------------|
| **Time-of-day patterns** | "You complete 78% of morning habits but only 45% of evening ones" |
| **Day-of-week patterns** | "Wednesdays are your weakest day (62% vs 84% average)" |
| **Habit correlations** | "On days you walk, you're 3× more likely to journal" |
| **Energy-consistency curve** | Scatter plot of energy rating vs that day's completion rate |
| **Recovery speed** | "Average time from miss to return: 1.8 days (improving)" |
| **Difficulty drift** | "Your habits are getting easier over time — consider upgrading" |
| **Optimal ordering** | "You do best when Walk comes before Meditation" |
| **Seasonal trends** | "Consistency drops 15% in December — plan for it" |

### 9.2 New Chart Types

| Chart | Data |
|-------|------|
| **Consistency trend line** | Rolling 7-day consistency over time, per habit |
| **Energy heatmap** | Energy ratings by day and checkpoint |
| **Habit timing scatter** | When each habit is actually completed (time of day) |
| **Recovery arc** | Days from miss to return, showing improvement trend |
| **Growth phase chart** | Phase progression timeline with consistency overlay |
| **Time investment** | Stacked bar chart of estimated daily time per habit |
| **Correlation matrix** | Habit-to-habit completion correlation |

---

## 10. Implementation Roadmap

### Phase 1: Foundation & Bug Fixes (Weeks 1-2)

| Priority | Task |
|----------|------|
| 🔴 | Fix TTS — implement `SfTextToSpeech` with Android TTS API |
| 🔴 | Fix STT — add multi-provider support (Platform + Whisper API fallback) |
| 🔴 | Add `GrowthPlan`, `GrowthPhase`, `WeeklySnapshot` to data model |
| 🔴 | Add GrowthPlan database tables and Repository methods |
| 🟡 | Add `Milestone` model and detection logic |
| 🟡 | Add `Sprint` model and basic CRUD capabilities |
| 🟡 | Add `JournalEntry` model and basic capabilities |
| 🟡 | Add `Routine` model (upgrade from Flow) |
| 🟡 | Add new Prefs: `proactiveAi`, `ttsEnabled`, `growthPlansEnabled`, etc. |

### Phase 2: Progressive Growth Engine (Weeks 3-5)

| Priority | Task |
|----------|------|
| 🔴 | Implement `GrowthEngine.evaluate()` with weekly evaluation |
| 🔴 | Implement `GrowthEngine.generateGrowthPlan()` |
| 🔴 | Add growth plan UI to Habit Designer (new step) |
| 🔴 | Add growth plan status to Today habit cards |
| 🔴 | Implement auto-upgrade and auto-downgrade logic |
| 🟡 | Add growth trajectory chart to Insights |
| 🟡 | Add growth plan notifications (upgrade day) |
| 🟡 | Wire GrowthEngine into WorkManager daily job |

### Phase 3: Blueprint Studio Rewrite (Weeks 6-8)

| Priority | Task |
|----------|------|
| 🔴 | Rewrite `CompilerV2` with intent-first extraction |
| 🔴 | Implement phased plan generation |
| 🔴 | Add intent capture UI to Blueprint |
| 🔴 | Implement progressive execution (phase-by-phase) |
| 🔴 | Add weekly upgrade reports |
| 🟡 | Add Blueprint timeline view |
| 🟡 | Add cloud AI refinement for phased plans |
| 🟡 | Migrate existing Blueprint projects to new format |

### Phase 4: AI System Overhaul (Weeks 9-12)

| Priority | Task |
|----------|------|
| 🔴 | Implement `ProactiveAiWorker` with WorkManager |
| 🔴 | Implement structured AI memory (`AiMemory` model) |
| 🔴 | Implement all new AI capabilities (30+ new commands) |
| 🔴 | Redesign Studio (Coach → Studio) UI |
| 🔴 | Add quick action chips and proactive suggestions |
| 🟡 | Implement `SmartNotifications` with contextual content |
| 🟡 | Add AI analysis capabilities (patterns, correlations, predictions) |
| 🟡 | Add notification action buttons (Done, Tiny, Skip) |
| 🟡 | Implement Whisper API STT provider |
| 🟢 | Add local Whisper provider (whisper.cpp) |

### Phase 5: New Feature Systems (Weeks 13-16)

| Priority | Task |
|----------|------|
| 🔴 | Habit Templates library with 100+ templates |
| 🔴 | Milestone detection and quiet acknowledgment |
| 🟡 | Sprint/Season planning system |
| 🟡 | Day Timeline view for Today |
| 🟡 | Journaling system with guided prompts |
| 🟡 | Routine Builder (upgrade from Flows) |
| 🟡 | Environment Design toolkit |
| 🟡 | Habit Difficulty estimator |
| 🟡 | Weekly Auto-Review generation |
| 🟡 | What-If Simulator |
| 🟡 | Accountability Export |
| 🟢 | Obstacle Plan surfacing at the right moment |
| 🟢 | Commitment Sprint system |

### Phase 6: Insights & Polish (Weeks 17-20)

| Priority | Task |
|----------|------|
| 🔴 | New analytics: time-of-day, day-of-week, correlations |
| 🔴 | New chart types (trend lines, scatter plots, correlation matrix) |
| 🟡 | Energy-aware scheduling recommendations |
| 🟡 | Difficulty drift detection |
| 🟡 | Optimal habit ordering suggestions |
| 🟢 | Seasonal trend analysis |
| 🟢 | Recovery speed tracking |

---

## Appendix: Complete New Data Models

```kotlin
// Growth Engine
data class GrowthPlan(...)        // See §2.2
data class GrowthPhase(...)
data class WeeklySnapshot(...)
data class UpgradePolicy(...)

// Milestones
data class Milestone(...)          // See §5.2

// Sprints
data class CommitmentSprint(...)   // See §6.2

// Journaling
data class JournalEntry(...)       // See §5.5

// Routines (upgrade from Flow)
data class Routine(...)            // See §6.1
data class RoutineStep(...)

// Environment Design
data class EnvironmentDesign(...)  // See §5.6

// AI Memory
data class AiMemory(...)           // See §4.4

// Proactive AI
data class ProactiveSuggestion(
    val id: String = newId(),
    val type: SuggestionType,
    val text: String,
    val priority: Priority,
    val autoAction: JSONObject? = null,
    val habitId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val dismissed: Boolean = false,
    val applied: Boolean = false
)

enum class SuggestionType {
    FOCUS, STRUGGLE, REVIEW, ENERGY, GROWTH, MILESTONE, OPPORTUNITY
}

enum class Priority { LOW, MEDIUM, HIGH }

// Difficulty Rating
data class DifficultyRating(
    val level: DifficultyLevel,
    val score: Int,
    val factors: List<String>,
    val advice: String
)

enum class DifficultyLevel { EASY, MODERATE, CHALLENGING }

// Simulation
data class Simulation(
    val currentHabits: Int,
    val newHabits: Int,
    val currentMinutes: Int,
    val newMinutes: Int,
    val riskLevel: RiskLevel,
    val advice: String
)

enum class RiskLevel { LOW, MEDIUM, HIGH }

// Sprint
data class Sprint(
    val id: String = newId(),
    val title: String,
    val startDate: String,
    val endDate: String,
    val focusHabits: List<String>,
    val goals: List<String>,
    val status: SprintStatus,
    val reviewNotes: String,
    val createdAt: Long
)
```

## Appendix: New Database Tables

```sql
-- Growth Engine
CREATE TABLE growth_plans (
    id TEXT PRIMARY KEY,
    habit_id TEXT NOT NULL,
    phases_json TEXT NOT NULL,
    current_phase_index INTEGER DEFAULT 0,
    upgrade_policy_json TEXT NOT NULL,
    weekly_snapshots_json TEXT DEFAULT '[]',
    last_upgrade_date TEXT DEFAULT '',
    next_review_date TEXT DEFAULT '',
    created_at INTEGER NOT NULL
);

-- Milestones
CREATE TABLE milestones (
    id TEXT PRIMARY KEY,
    habit_id TEXT,
    type TEXT NOT NULL,
    value INTEGER NOT NULL,
    label TEXT NOT NULL,
    acknowledged INTEGER DEFAULT 0,
    achieved_at INTEGER NOT NULL
);

-- Sprints
CREATE TABLE sprints (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    start_date TEXT NOT NULL,
    end_date TEXT NOT NULL,
    focus_habits_json TEXT DEFAULT '[]',
    goals_json TEXT DEFAULT '[]',
    status TEXT DEFAULT 'PLANNED',
    review_notes TEXT DEFAULT '',
    created_at INTEGER NOT NULL
);

-- Journal
CREATE TABLE journal_entries (
    id TEXT PRIMARY KEY,
    date TEXT NOT NULL,
    prompt TEXT DEFAULT '',
    content TEXT NOT NULL,
    mood INTEGER,
    tags_json TEXT DEFAULT '[]',
    created_at INTEGER NOT NULL
);

-- Routines (upgrade from flows)
CREATE TABLE routines (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    trigger_text TEXT DEFAULT '',
    estimated_minutes INTEGER DEFAULT 0,
    status TEXT DEFAULT 'ACTIVE',
    created_at INTEGER NOT NULL
);

CREATE TABLE routine_steps (
    id TEXT PRIMARY KEY,
    routine_id TEXT NOT NULL,
    habit_id TEXT,
    title TEXT NOT NULL,
    duration_minutes INTEGER DEFAULT 5,
    order_index INTEGER DEFAULT 0,
    transition_note TEXT DEFAULT ''
);

-- AI Memory
CREATE TABLE ai_memories (
    id TEXT PRIMARY KEY,
    category TEXT NOT NULL,
    content TEXT NOT NULL,
    importance INTEGER DEFAULT 5,
    last_accessed INTEGER NOT NULL,
    access_count INTEGER DEFAULT 0,
    created_at INTEGER NOT NULL
);

-- Proactive Suggestions
CREATE TABLE proactive_suggestions (
    id TEXT PRIMARY KEY,
    type TEXT NOT NULL,
    text TEXT NOT NULL,
    priority TEXT DEFAULT 'MEDIUM',
    auto_action_json TEXT,
    habit_id TEXT,
    dismissed INTEGER DEFAULT 0,
    applied INTEGER DEFAULT 0,
    created_at INTEGER NOT NULL
);

-- Environment Design
CREATE TABLE environment_designs (
    habit_id TEXT PRIMARY KEY,
    make_obvious_json TEXT DEFAULT '[]',
    make_attractive_json TEXT DEFAULT '[]',
    make_easy_json TEXT DEFAULT '[]',
    make_satisfying_json TEXT DEFAULT '[]',
    make_invisible_json TEXT DEFAULT '[]',
    make_unattractive_json TEXT DEFAULT '[]',
    make_difficult_json TEXT DEFAULT '[]',
    make_unsatisfying_json TEXT DEFAULT '[]'
);

-- Growth plan phase history
CREATE TABLE growth_phase_history (
    id TEXT PRIMARY KEY,
    growth_plan_id TEXT NOT NULL,
    phase_index INTEGER NOT NULL,
    action TEXT NOT NULL,  -- UPGRADE, DOWNGRADE, HOLD
    consistency INTEGER,
    date TEXT NOT NULL,
    notes TEXT DEFAULT ''
);
```

---

*This plan transforms SuperFlow from a static habit tracker into an adaptive growth system that starts small, learns from the user, and upgrades itself weekly — the way real behavior change actually works.*
