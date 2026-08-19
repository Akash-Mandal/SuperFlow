# SuperFlow — Core Growth Systems Upgrade Plan

**Version:** 1.0 · August 2026
**Scope:** Deep upgrade to every self-improvement system in the app — the mechanisms that help the user actually change
**Companion docs:** [UI/UX Grand Plan](UI_UX_GRAND_UPGRADE_PLAN.md) · [Functional Grand Plan](APP_FUNCTIONAL_GRAND_PLAN.md)

---

## The 15 Core Growth Systems — What's Wrong & How to Fix Each One

The app is built on 15 interconnected systems that together form the growth engine. Each one has real weaknesses in practice — fields filled once and forgotten, data collected but never used, mechanisms that exist on paper but don't activate at the right moment. This plan upgrades each system so it **actually works in daily life**.

---

### System 1: Identity — "Who Are You Becoming?"

**Current state:** A text statement + life area. Habit check-ins cast "votes" (a count). One identity shown on Today.

**What's wrong in practice:**
- The identity is written during onboarding and **never revisited**. After 3 months, the person has changed but the statement hasn't.
- Votes are just a number. "47 votes" doesn't *feel* like evidence. There's no qualitative connection between "I walked today" and "I am someone who takes care of my body."
- Only one identity is shown on Today — but people are becoming multiple things simultaneously.
- There's no moment where the app says "you've been living this identity for 6 months — is it still true?"
- Identity conflicts go undetected: "someone who pushes hard" vs "someone who rests" can clash.

**Upgrades:**

| Upgrade | What It Does |
|---------|-------------|
| **Identity Evolution Prompt** | Every 30 days, ask: "Is this still who you're becoming?" with evidence summary. Let user refine, keep, or evolve. |
| **Evidence Journal** | When checking in, optionally tag the action as evidence: "This walk was a vote for: [identity]." Shows qualitative evidence alongside vote count. |
| **Multiple Active Identities** | Support 2-3 active identities. Today shows the primary one, but Journey shows all with their vote counts and linked habits. |
| **Identity Milestones** | Quiet acknowledgment: "You've cast 100 votes for 'someone who moves daily.' That's not aspiration anymore — that's evidence." |
| **Identity Conflict Detection** | If two identities pull in opposite directions (e.g., one linked to late-night habits, another to early mornings), flag gently. |
| **Identity Review** | In weekly/monthly reviews, add: "What evidence did you collect this week about who you're becoming?" Auto-links to the identity card. |

**New data model additions:**
```kotlin
data class Identity(
    // ... existing fields ...
    val evolutionHistory: List<IdentityEvolution> = emptyList(),
    val isPrimary: Boolean = true
)

data class IdentityEvolution(
    val previousStatement: String,
    val newStatement: String,
    val reason: String,
    val votesAtEvolution: Int,
    val date: String
)

data class IdentityEvidence(
    val id: String = newId(),
    val identityId: String,
    val text: String,            // "Ran 5km in the rain — that's who I am now"
    val sourceHabitId: String?,  // Optional link to the habit that produced it
    val date: String,
    val createdAt: Long = System.currentTimeMillis()
)
```

---

### System 2: Goals — "What Outcome Would Matter?"

**Current state:** Title + why + linked identity + optional `outcomeMetric` (free text). Status enum exists (ACTIVE, ACHIEVED, etc.) but is never auto-detected.

**What's wrong in practice:**
- Goals are **direction-setting only** — they never produce milestones, deadlines, or progress measurements.
- The `outcomeMetric` field exists but is never tracked. "Run a 5K" has no way to record "I ran 3K today."
- Goals never inform the Today screen. The user's daily actions are disconnected from their stated goals.
- There's no "is this still the right goal?" moment. Goals sit forever.
- No goal decomposition — a big goal can't be broken into sub-goals.

**Upgrades:**

| Upgrade | What It Does |
|---------|-------------|
| **Goal Milestones** | Break each goal into 2-5 measurable milestones. "Walk 5km comfortably" → "Walk 1km", "Walk 3km", "Walk 5km". Each milestone is checkable. |
| **Goal Progress Tracking** | For measurable goals, track the metric over time. Show a simple progress bar: "3K of 5K (60%)". |
| **Goal-Habit Link Visibility** | On Today, show which habits serve which goal. "Walk 10 min → Walk 5km comfortably (60%)". |
| **Goal Review Prompt** | Monthly: "Is this goal still the right direction? Here's what your habits are producing..." |
| **Goal Achievement Detection** | When all milestones are checked, or the metric is reached, prompt: "Looks like you've achieved this. Celebrate?" |
| **Goal Timeline** | Optional target date. As the date approaches, show gentle progress awareness (not pressure). |

**New data model additions:**
```kotlin
data class Goal(
    // ... existing fields ...
    val milestones: List<GoalMilestone> = emptyList(),
    val targetDate: String? = null,
    val currentMetricValue: Double? = null,
    val metricUnit: String = ""
)

data class GoalMilestone(
    val id: String = newId(),
    val title: String,
    val achieved: Boolean = false,
    val achievedDate: String? = null,
    val linkedHabitIds: List<String> = emptyList()
)
```

---

### System 3: Systems — "The Repeatable Process"

**Current state:** Title + description + linked goal. That's it. A text label and a sentence.

**What's wrong in practice:**
- This is the **weakest layer** in the entire hierarchy. A "system" should be the engine that turns goals into daily behavior, but it's just a free-text description.
- No system health measurement. If all habits under a system are failing, the system itself should flag as unhealthy.
- No system maintenance. Systems need periodic review: "Is this routine still the right approach?"
- No capacity awareness. How many habits can one system hold?
- The system layer is **invisible in daily life** — Today never shows "your Morning Movement system is at 78%."

**Upgrades:**

| Upgrade | What It Does |
|---------|-------------|
| **System Health Score** | Derived from the average consistency of all habits under this system. Shown in Journey: "Morning Movement — 78% healthy." |
| **System Capacity Indicator** | Warns if a system has >5 active habits: "This system is carrying a lot. Consider if some could move to their own system." |
| **System Review** | In monthly review: "Your [system name] is at [X]%. What's working in this routine? What needs adjusting?" |
| **System → Today Visibility** | In Today, group habits by system. "Morning Movement system: Walk ✅, Stretch ☐, Meditate ☐" |
| **System Templates** | Pre-built systems: "Morning Routine," "Evening Wind-Down," "Movement Practice," "Creative Practice," "Learning Block." |

**New data model additions:**
```kotlin
data class Sys(
    // ... existing fields ...
    val templateId: String? = null,     // "morning_routine" etc.
    val reviewFrequency: String = "monthly"  // weekly, monthly, quarterly
)
```

---

### System 4: The Four Laws (Habit Designer)

**Current state:** Six fields — benefit, temptationBundle, reframe, frictionPlan, environmentPrep, reward. Filled during habit design, **never revisited**.

**What's wrong in practice:**
- These are the **most powerful behavior change tools** in Atomic Habits, but they're treated as one-time form fields.
- The user writes "reward: listen to my favorite podcast" and then never evaluates if the reward actually made the habit stick.
- Friction plans are written but never activated: "remove the phone from the bedroom" — does the app ever check if this was done?
- Environment preparation is never reminded: "lay out gym clothes the night before" — this should be a pre-habit nudge.
- Reframes are never tested: "this isn't exercise, it's me-time" — was this helpful during a hard day?
- Temptation bundles are never evaluated: "only listen to that podcast while walking" — is the bundle working?

**Upgrades:**

| Upgrade | What It Does |
|---------|-------------|
| **Living Four Laws** | Each law becomes a card that can be updated anytime, not just during design. "Edit reward," "Update friction plan." |
| **Reward Satisfaction Check** | After 7 check-ins, ask: "Is [reward] still making [habit] feel worth it? Rate 1-5." If low, prompt to redesign. |
| **Environment Prep Reminder** | If `environmentPrep` is filled, schedule a reminder at the right time: "Prep for [habit]: [environment prep text]" — e.g., at 9pm for a 7am walk: "Lay out your walking shoes." |
| **Friction Plan Activation** | For REDUCE mode habits, the friction plan becomes an actionable checklist shown when the user is at risk. |
| **Reframe Effectiveness** | After a miss, show the reframe: "You wrote: '[reframe].' Did this help? If not, let's find a better one." |
| **Temptation Bundle Tracker** | "You bundled [habit] with [bundle]. After 2 weeks: is the bundle making the habit easier, harder, or no difference?" |
| **Four Laws Review** | In habit detail, add a "Four Laws Health" section showing which laws are working and which need redesign. |

**New data model additions:**
```kotlin
data class Habit(
    // ... existing fields ...
    val rewardSatisfaction: Int? = null,       // 1-5, null = not yet rated
    val rewardLastRated: String? = null,
    val reframeHelpful: Boolean? = null,
    val bundleEffectiveness: Int? = null,      // 1-5
    val frictionPlanActive: Boolean = false,
    val environmentPrepReminderTime: String? = null
)
```

---

### System 5: The Ladder (Tiny / Minimum / Standard / Stretch)

**Current state:** Four text fields. User picks a level at check-in. The texts are static — they never change based on performance.

**What's wrong in practice:**
- The ladder is **the most innovative concept in the app** but it's completely static. "Walk 10 minutes" stays "Walk 10 minutes" for months.
- There's no auto-progression. If someone does Standard 14 days in a row, the app should suggest: "You're ready to upgrade Standard to 15 minutes."
- Stretch is almost never used because there's no incentive and no guidance.
- No difficulty feedback at check-in — the app never learns if Standard was too hard or too easy today.
- The tiny start is praised but never "graduated" — after 60 days of consistent Standard, the tiny version could evolve too.

**Upgrades:**

| Upgrade | What It Does |
|---------|-------------|
| **Adaptive Ladder** | After 14 consecutive Standard check-ins, suggest: "Upgrade Standard to [next level]?" User confirms, and the ladder evolves. |
| **Difficulty Rating at Check-in** | After checking in, optional: "How hard was that?" (Too Easy / Just Right / Too Hard). Feeds into ladder adjustment suggestions. |
| **Stretch Incentive** | When Stretch is completed, show: "You reached for more today. That's evidence of growth." Track stretch frequency. |
| **Ladder History** | Keep a log of how each level evolved: "Standard was 'Walk 10 min' from Jan 1 → 'Walk 15 min' from Feb 15 → 'Walk 20 min' from Apr 1." |
| **Guided Ladder Climbing** | In Insights: "You've done Standard 21 days in a row. Your Stretch version is waiting. Try it once this week?" |
| **Auto-Downgrade on Struggle** | If Standard is missed 3 times in a row, suggest: "Standard might be too much right now. Shrink it to [minimum version] for a week?" |

**New data model additions:**
```kotlin
data class LadderEvolution(
    val level: Level,
    val previousText: String,
    val newText: String,
    val reason: String,       // "14 consecutive standards" or "3 misses — downgraded"
    val date: String
)

data class Habit(
    // ... existing fields ...
    val ladderHistory: List<LadderEvolution> = emptyList(),
    val lastDifficultyRating: Int? = null,    // 1=too easy, 3=just right, 5=too hard
    val stretchCount: Int = 0,
    val consecutiveStandards: Int = 0
)
```

---

### System 6: Daily Focus

**Current state:** Up to 3 free-text items. Checkable. Suggest button pulls from open habits.

**What's wrong in practice:**
- Focus items are **disconnected from the growth hierarchy**. "Call the dentist" sits next to "Walk 10 min" with no sense of which serves a goal.
- No priority weighting — all 3 items look equal, but one might be the day's most important action.
- Undone items vanish at midnight — no carry-over, no "you've skipped this 3 days in a row."
- No "why" for each item — why does this deserve focus today?
- No time estimation — can't plan the day around focus items.

**Upgrades:**

| Upgrade | What It Does |
|---------|-------------|
| **Goal-Linked Focus** | When adding a focus item, optionally link it to a goal: "Call the dentist → Health goal." Shows which goals are being served today. |
| **Priority Star** | One item can be starred as "Today's #1." Shown prominently. If nothing else gets done, this one matters. |
| **Carry-Over Awareness** | If a focus item wasn't completed yesterday, show: "You've skipped 'Call the dentist' 3 times. Still important? Or time to remove it?" |
| **Time Estimation** | Optional minutes estimate per focus item. Shows total: "Today's focus: ~45 minutes." |
| **Focus Review** | At evening checkpoint: "Of your 3 focus items, you completed 2. The one that didn't happen: [item]. Worth rescheduling?" |

**New data model additions:**
```kotlin
data class FocusItem(
    // ... existing fields ...
    val goalId: String? = null,
    val isPriority: Boolean = false,
    val estimatedMinutes: Int? = null,
    val carryOverCount: Int = 0       // How many days this has been skipped
)
```

---

### System 7: Check-In

**Current state:** Pick a level (Tiny/Min/Std/Stretch), or Skip, or Missed. Optional text note.

**What's wrong in practice:**
- Check-in captures **what** was done but not **how it felt** or **what made it easy/hard**.
- For COUNT habits ("drink 8 glasses"), there's no UI to enter the actual count.
- For DURATION habits ("meditate 10 minutes"), there's no timer and no actual duration entry.
- No quality rating — a rushed 10-minute walk and a mindful 10-minute walk both count the same.
- The check-in moment is the **richest data point** in the app but captures almost nothing.

**Upgrades:**

| Upgrade | What It Does |
|---------|-------------|
| **Context Capture** | After check-in, optional quick tag: "What made this easy/hard?" with presets: Good sleep / Bad sleep / Good weather / Time pressure / Social support / Low energy / High energy / Custom. |
| **Count Entry** | For COUNT habits: number input at check-in. "Drank 6 of 8 glasses." Progress bar. |
| **Duration Entry** | For DURATION habits: optional timer or manual entry. "Meditated for 12 minutes (target: 10)." |
| **Quality Rating** | Optional 1-3 stars: "How was the quality of this session?" Not about completion — about experience. |
| **Check-In Streak Awareness** | At check-in, show: "Day 12 in a row" or "Welcome back after 2 days." Quiet, not flashy. |

**New data model additions:**
```kotlin
data class CheckIn(
    // ... existing fields ...
    val contextTags: List<String> = emptyList(),
    val actualAmount: Double? = null,      // For COUNT/DURATION
    val actualDurationMinutes: Int? = null,
    val qualityRating: Int? = null,        // 1-3
    val difficultyRating: Int? = null      // 1=too easy, 3=just right, 5=too hard
)
```

---

### System 8: Recovery

**Current state:** Shows habits that missed yesterday with their tiny versions. "Do the tiny version" button. Minimum Mode drops everything to minimum.

**What's wrong in practice:**
- Recovery is **reactive only** — it appears after a miss but never prevents one.
- No "why did you miss?" reflection. The miss is recorded but the reason is lost.
- No pattern detection: "Walk has missed every Wednesday for 3 weeks — something about Wednesdays is the problem."
- No preventive maintenance: "Based on your patterns, Friday evenings are risky for Journal. Here's your obstacle plan."
- Returning after a miss should feel like a **win**, but the current UI just shows the habit card again.

**Upgrades:**

| Upgrade | What It Does |
|---------|-------------|
| **Miss Reflection** | When marking a habit as missed, optional prompt: "What got in the way?" with presets: Time / Energy / Forgot / Motivation / Circumstance / Other. Stored with the check-in. |
| **Pattern Detection** | Weekly analysis: "Walk has missed 3 of the last 4 Wednesdays. Want to reschedule or add an obstacle plan for Wednesdays?" |
| **Preventive Nudges** | If a habit's historical miss rate for tomorrow's day-of-week is >40%, show a gentle evening nudge: "Tomorrow is Thursday — Journal has been hard on Thursdays. Your obstacle plan: [plan]." |
| **Recovery Celebration** | When checking in after a miss (a "recovery"), show a distinct acknowledgment: "You came back. That's the skill that matters most." Different visual treatment from a normal check-in. |
| **Recovery Streak** | Track "recoveries in a row" — how many times the user has bounced back. This is more meaningful than a completion streak. |
| **Miss Reason Analysis** | In Insights: "Your top miss reasons: Time (40%), Energy (30%), Forgot (20%). Consider: time-blocking, energy-aware scheduling, stronger cues." |

**New data model additions:**
```kotlin
data class CheckIn(
    // ... existing fields ...
    val missReason: String? = null,         // "time", "energy", "forgot", "motivation", "circumstance", "other"
    val missReasonDetail: String? = null
)
```

---

### System 9: Reviews

**Current state:** Weekly/Monthly/Quarterly with 4 text fields: whatWorked, whatDidnt, systemChange, identityEvidence.

**What's wrong in practice:**
- Reviews are **empty forms** — the user has to remember what happened and type it all manually.
- The `systemChange` field produces action items that are **never tracked**. "Shrink Walk to 5 minutes" is written but the app never checks if it was done.
- No trend across reviews — can't see "my last 4 weekly reviews" to spot patterns.
- No AI-assisted analysis — just raw text fields.
- Review timing is manual — user has to remember to do it.

**Upgrades:**

| Upgrade | What It Does |
|---------|-------------|
| **Data-Driven Pre-Fill** | Auto-generate the review with real data: "This week: 87% consistency. Strongest: Walk (100%). Struggling: Journal (43%). 2 recoveries. Miss reasons: Time (2), Energy (1)." |
| **Action Item Tracking** | `systemChange` becomes a structured action item with a checkbox. Next review asks: "Last week you decided to [change]. Did you do it? What happened?" |
| **Review Trend** | Show the last 4-8 reviews as a scrollable timeline. Spot patterns: "Your consistency has improved each week for 3 weeks." |
| **Smart Review Timing** | Sunday evening prompt with pre-filled data. If skipped, gentle Monday morning reminder. |
| **Review → Action Pipeline** | Action items from reviews can be converted to app commands: "Shrink Walk" → one tap to update the habit. |
| **Identity Evidence Auto-Collection** | The `identityEvidence` field is pre-filled with the week's check-ins linked to each identity. |

**New data model additions:**
```kotlin
data class Review(
    // ... existing fields ...
    val autoGeneratedData: String = "",     // Pre-filled stats
    val actionItems: List<ReviewActionItem> = emptyList(),
    val previousReviewId: String? = null    // For trend linking
)

data class ReviewActionItem(
    val id: String = newId(),
    val text: String,
    val completed: Boolean = false,
    val completedDate: String? = null,
    val linkedCommand: String? = null,      // Optional: "update_habit" JSON
    val outcome: String? = null             // "Did it, worked" / "Did it, didn't help" / "Didn't do it"
)
```

---

### System 10: Obstacle Plans (If-Then)

**Current state:** Text pairs per habit. "If it rains, then stretch indoors." Stored but never surfaced.

**What's wrong in practice:**
- Obstacle plans are the **#1 evidence-based tool** for habit survival, but they're written once and forgotten forever.
- The app never shows the plan at the moment it's needed — when the obstacle actually occurs.
- No tracking of whether the plan was used or whether it worked.
- No suggestion engine — the app could suggest plans based on miss patterns.
- No seasonal awareness — "it's monsoon season, your outdoor walk needs an indoor alternative."

**Upgrades:**

| Upgrade | What It Does |
|---------|-------------|
| **Contextual Surfacing** | When a habit is marked Missed, immediately show its obstacle plans: "You planned for this: If [obstacle], then [action]. Want to do the alternative now?" |
| **Usage Tracking** | When an obstacle plan is activated, record it. "This plan has been used 3 times. Last time: Tuesday." |
| **Effectiveness Rating** | After using a plan: "Did the alternative work?" If not, prompt to redesign. |
| **AI-Suggested Plans** | Based on miss reasons: "You've missed Walk 4 times due to rain. How about: If it rains, then do 5 minutes of stretching indoors?" |
| **Seasonal Awareness** | Based on date/location: "Monsoon season is here. Your outdoor habits might need indoor alternatives. Review obstacle plans?" |
| **Obstacle Plan Library** | Common plans pre-built: bad weather, travel, illness, low energy, social obligations, time pressure. |

**New data model additions:**
```kotlin
data class ObstaclePlan(
    // ... existing fields ...
    val timesUsed: Int = 0,
    val lastUsed: String? = null,
    val effectiveness: Int? = null,     // 1-5, null = not rated
    val category: String? = null        // "weather", "time", "energy", "social", "travel", "health"
)
```

---

### System 11: Flows (Habit Stacking / Routines)

**Current state:** Title + anchor + ordered steps. Warning if >3 new links. No guided execution.

**What's wrong in practice:**
- Flows are **designed but never run**. There's no "start my morning flow" button that guides through each step.
- No timing — how long should each step take?
- No flow-level check-in — can't mark the whole flow as done.
- No breakpoint awareness — if step 2 fails, the whole chain collapses. The app doesn't know which step broke.
- No flow success tracking — how often is the full flow completed?

**Upgrades:**

| Upgrade | What It Does |
|---------|-------------|
| **Guided Flow Execution** | "Run flow" mode: shows step 1 with a timer, then step 2, etc. Each step is checkable. At the end, the whole flow is checked in. |
| **Step Timing** | Each step has an optional duration. Total flow time shown: "Morning flow: ~25 minutes." |
| **Breakpoint Detection** | If a flow step's linked habit is missed, show: "Your Morning Flow broke at step 2 (Meditate). Want to redesign just that step?" |
| **Flow Consistency** | Track how often the full flow is completed vs partial vs not started. Show in Insights. |
| **Flow → Today Integration** | Flows appear on Today as a single card: "Morning Flow (4 steps, ~25 min)" with expand/collapse. |

**New data model additions:**
```kotlin
data class Flow(
    // ... existing fields ...
    val estimatedMinutes: Int = 0,
    val completionCount: Int = 0,
    val partialCount: Int = 0
)

data class FlowStep(
    // ... existing fields ...
    val durationMinutes: Int = 0,
    val isBreakpoint: Boolean = false    // If this fails, the rest likely fails too
)
```

---

### System 12: Scorecard

**Current state:** List of existing routines rated helpful/neutral/unhelpful. One-time exercise.

**What's wrong in practice:**
- The Scorecard is an **awareness tool from Atomic Habits Chapter 3**, but it's done once and never revisited.
- Unhelpful routines don't become action items. "Check phone in bed → Unhelpful" should lead to "Create an obstacle plan for phone-in-bed."
- No connection between the Scorecard and habit creation. An unhelpful routine is a perfect candidate for a REDUCE habit.
- No re-evaluation — routines change over time.

**Upgrades:**

| Upgrade | What It Does |
|---------|-------------|
| **Scorecard → Action Pipeline** | For each unhelpful routine, offer: "Turn this into a Reduce habit?" One tap creates a REDUCE habit with the routine as the title. |
| **Helpful Routine Protection** | For each helpful routine: "Is this linked to a habit?" If not, suggest creating one to protect it. |
| **Periodic Re-Score** | Every 30 days: "Your routines may have changed. Quick re-score?" Shows the existing list with swipe-to-change-verdict. |
| **Routine Time Mapping** | Add optional time-of-day to each routine. Shows: "Your mornings have 3 unhelpful routines. That's the biggest leverage point." |
| **Scorecard Insights** | "You have 8 helpful, 4 neutral, 3 unhelpful routines. The unhelpful ones cluster around [time/context]." |

---

### System 13: Checkpoints (Morning / Midday / Evening)

**Current state:** Three buttons that trigger a checkpoint. Energy slider. That's it.

**What's wrong in practice:**
- Tapping "Morning" does almost nothing visible. The checkpoint should be a **guided moment** — a 30-second pause that orients the user for the period ahead.
- No content is shown at a checkpoint. What should the morning checkpoint contain? Today's plan, open habits, energy check, focus items.
- Checkpoints aren't linked to habits. "After morning checkpoint, do Walk" should be a natural flow.
- Energy is logged but **never influences anything**. If energy is 2/5, the app should suggest minimum mode.

**Upgrades:**

| Upgrade | What It Does |
|---------|-------------|
| **Guided Checkpoint Content** | When a checkpoint is triggered, show a brief screen: (1) Energy check, (2) "Here's what's ahead:" — open habits for this period, focus items, (3) One coaching thought. |
| **Energy-Aware Suggestions** | If energy ≤ 2: "Low energy. Consider Minimum Mode — your habits will adjust to their minimum versions." If energy ≥ 4: "High energy. Good day for a Stretch version." |
| **Checkpoint → Habit Linking** | Habits can be anchored to checkpoints: "Walk is anchored to Morning checkpoint." The checkpoint screen shows these habits as "next up." |
| **Checkpoint Completion Tracking** | Track how often each checkpoint is actually done. "You've hit Morning checkpoint 5 of 7 days this week." |
| **Evening Checkpoint Reflection** | The evening checkpoint includes: "What went well today? What was hard? One thing for tomorrow?" — a micro-review. |

---

### System 14: Energy Tracking

**Current state:** 1-5 slider at checkpoints. Displayed as text in Insights: "Morning: 3.2/5 from 12 entries."

**What's wrong in practice:**
- Energy data is **collected but never used**. It doesn't influence scheduling, recommendations, or mode.
- No correlation shown between energy and habit completion.
- No energy pattern visualization — no chart showing energy over time.
- No energy forecasting — "Based on your patterns, tomorrow morning tends to be low energy."
- No "energy budget" concept — high-energy tasks scheduled during high-energy periods.

**Upgrades:**

| Upgrade | What It Does |
|---------|-------------|
| **Energy-Habit Correlation** | In Insights: "On days you rate energy ≥ 4, you complete 92% of habits. On days ≤ 2, only 54%. Consider scheduling harder habits on high-energy days." |
| **Energy-Aware Scheduling Suggestion** | "Your highest energy is usually 8-10am. Your hardest habit (Meditate) is scheduled at 9pm. Consider moving it to morning?" |
| **Energy Chart** | A simple line chart of energy over time, per checkpoint. Shows patterns: "Energy dips every Wednesday afternoon." |
| **Low-Energy Auto-Suggestion** | When energy ≤ 2 is logged, show: "Low energy detected. Want to switch today's habits to Minimum Mode?" |
| **Energy Forecasting** | After 14+ entries: "Based on your patterns, tomorrow morning will likely be [medium] energy. Plan accordingly." |

---

### System 15: Capacity Management (New System)

**Current state:** Does not exist.

**What's wrong:** The app has no concept of cognitive load. A user can add 12 habits, 3 focus items, 2 flows, and 3 checkpoints without any warning that they've taken on too much. Research consistently shows that **3-5 new behaviors at once** is the practical maximum.

**Upgrades:**

| Upgrade | What It Does |
|---------|-------------|
| **Daily Load Indicator** | Show on Today: "Today's load: 5 habits, ~45 minutes, 3 focus items." With a gentle color: green (light), amber (moderate), coral (heavy). |
| **Capacity Warning** | When adding a 6th+ active habit: "You already have 5 active habits. Research suggests 3-5 new behaviors is the maximum. Consider pausing one before adding another." |
| **Time Budget** | Estimate daily time commitment from all habits (using duration or defaults). Show: "Your habits need ~60 minutes/day. Is that realistic?" |
| **Cognitive Load Score** | A simple metric: number of active habits × average difficulty. If too high, suggest simplification. |
| **"Protected" Habits** | Mark 1-2 habits as "protected" — these are never suggested for pausing. The rest are flexible when capacity is exceeded. |
| **Minimum Mode Intelligence** | When entering Minimum Mode, show which habits drop to minimum and which are protected. Let the user confirm. |

**New data model additions:**
```kotlin
data class Habit(
    // ... existing fields ...
    val estimatedMinutes: Int = 5,
    val difficultyRating: Int = 3          // 1=easy, 5=challenging
)
```

---

## How the Systems Connect — The Growth Loop

The upgraded systems form a **closed loop** that runs continuously:

```
     ┌─────────────────────────────────────────────────┐
     │                                                  │
     ▼                                                  │
  IDENTITY ──→ GOAL ──→ SYSTEM ──→ HABIT ──→ CHECK-IN  │
     ▲                                    │        │    │
     │                                    │        │    │
     │         ┌──────────────────────────┘        │    │
     │         ▼                                    ▼    │
     │     OBSTACLE PLANS ←── RECOVERY ←── MISS    │    │
     │                                              │    │
     │         ┌────────────────────────────────────┘    │
     │         ▼                                         │
     │     REVIEW ──→ ACTION ITEMS ──→ SYSTEM CHANGE ────┘
     │         │
     │         ▼
     │     IDENTITY EVOLUTION
     │
     └──────────────────────────────────────────────────┘
```

**The loop in practice:**
1. **Identity** gives direction to goals
2. **Goals** need systems to achieve them
3. **Systems** run on habits
4. **Habits** are checked in daily (with context, quality, difficulty)
5. **Misses** trigger recovery with obstacle plan surfacing
6. **Reviews** analyze the data and produce action items
7. **Action items** change the system (ladder adjustments, schedule changes, new obstacle plans)
8. **Identity evolves** based on accumulated evidence

Every upgrade in this plan strengthens one or more links in this loop.

---

## Priority Implementation Order

### Wave 1: The Highest-Impact Fixes (Weeks 1-4)

These are the systems where the gap between "exists on paper" and "works in practice" is largest:

1. **Four Laws → Living Tools** (§4) — Environment prep reminders, reward satisfaction checks, reframe activation
2. **Recovery → Preventive** (§8) — Miss reason capture, pattern detection, preventive nudges
3. **Check-In → Rich Data** (§7) — Context tags, difficulty rating, count/duration entry
4. **Reviews → Data-Driven** (§9) — Auto pre-fill, action item tracking, review → action pipeline

### Wave 2: The Growth Engine (Weeks 5-8)

5. **Ladder → Adaptive** (§5) — Auto-progression, difficulty feedback, ladder history
6. **Energy → Actionable** (§14) — Energy-habit correlation, low-energy suggestions, energy chart
7. **Checkpoints → Guided** (§13) — Checkpoint content screens, energy-aware mode suggestions
8. **Obstacle Plans → Surfaced** (§10) — Contextual surfacing, usage tracking, AI suggestions

### Wave 3: The Hierarchy Deepens (Weeks 9-12)

9. **Identity → Living** (§1) — Evolution prompts, evidence journal, multiple identities
10. **Goals → Measurable** (§2) — Milestones, progress tracking, goal-habit visibility
11. **Systems → Healthy** (§3) — System health score, system-habit grouping on Today
12. **Capacity Management** (§15) — Daily load indicator, capacity warnings, time budget

### Wave 4: The Connective Tissue (Weeks 13-16)

13. **Daily Focus → Linked** (§6) — Goal linking, priority star, carry-over
14. **Flows → Runnable** (§11) — Guided execution, step timing, breakpoint detection
15. **Scorecard → Actionable** (§12) — Action pipeline, re-scoring, time mapping

---

## New Capabilities Added to CommandBus

| Capability | System | Description |
|-----------|--------|-------------|
| `evolve_identity` | Identity | Record an identity evolution with reason |
| `add_identity_evidence` | Identity | Add qualitative evidence to an identity |
| `add_goal_milestone` | Goal | Add a milestone to a goal |
| `complete_goal_milestone` | Goal | Mark a milestone as achieved |
| `update_goal_metric` | Goal | Update the current metric value |
| `rate_reward` | Four Laws | Rate a habit's reward satisfaction |
| `rate_reframe` | Four Laws | Rate whether a reframe helped |
| `rate_bundle` | Four Laws | Rate temptation bundle effectiveness |
| `update_four_laws` | Four Laws | Update any four-laws field post-design |
| `evolve_ladder` | Ladder | Record a ladder level change with reason |
| `rate_checkin_difficulty` | Check-In | Rate how hard a check-in was |
| `rate_checkin_quality` | Check-In | Rate session quality |
| `record_miss_reason` | Recovery | Record why a habit was missed |
| `activate_obstacle_plan` | Obstacle | Record that an obstacle plan was used |
| `rate_obstacle_plan` | Obstacle | Rate whether an obstacle plan worked |
| `add_review_action_item` | Review | Add a structured action item to a review |
| `complete_review_action` | Review | Mark a review action item as done |
| `run_flow` | Flows | Start guided flow execution |
| `complete_flow` | Flows | Mark a full flow as completed |
| `rescore_scorecard` | Scorecard | Re-evaluate a scorecard entry |
| `convert_scorecard_to_habit` | Scorecard | Turn a scorecard entry into a habit |
| `set_habit_capacity` | Capacity | Set estimated minutes and difficulty |
| `get_daily_load` | Capacity | Calculate today's total cognitive load |
| `set_focus_priority` | Focus | Mark a focus item as #1 priority |
| `carry_over_focus` | Focus | Move an undone focus item to tomorrow |

---

*Every upgrade in this plan serves one question: does this help the user actually change? Not track more, not see more data, not fill more fields — actually become the person they're trying to become.*
