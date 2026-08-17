# SuperFlow Self-Discipline Integration Plan

> **Purpose:** Integrate the useful ideas from the user-provided *Complete Guide to Building Lasting Self-Discipline* into SuperFlow without turning the app into a punitive discipline score, an overwhelming task manager, or a source of unsupported behavioral claims.
>
> This plan extends the [Grand Product and Engineering Plan](GRAND_PLAN.md) and follows the universal manual/AI control contract in the [AI Engine Plan](AI_ENGINE_PLAN.md).

---

## 1. Integration stance

The guide fits SuperFlow well because it reinforces identity, small actions, implementation intentions, environment design, habit substitution, minimum versions, energy awareness, accountability, recovery, and review. Many ideas overlap the Atomic Habits framework already in the product plan. SuperFlow should combine them into one coherent system rather than expose duplicate theories.

### Product rules

1. **Translate principles into actions:** Teach briefly, then help the user configure a cue, fallback, environment, schedule, or review.
2. **Support different capacities:** Planning should happen when the user has perspective; execution should still work when they are tired, distracted, or stressed.
3. **Never moralize consistency:** “Discipline” is a designed support system, not a character score.
4. **Avoid unsupported certainty:** Fixed day ranges, exact growth percentages, and claims of automaticity are optional planning heuristics—not scientific guarantees.
5. **Protect stable cues:** Novelty may refresh enjoyment, but should not randomly disrupt a cue that is becoming reliable.
6. **Keep focus narrow:** Daily priorities support habits and systems; SuperFlow does not become a general project-management inbox.
7. **Preserve manual/AI parity:** Every feature in this plan has a complete manual path and typed AI capabilities using the shared command bus.
8. **Make recovery first-class:** Misses and relapses invoke adjustment, Minimum Mode, and the next useful action—not punishment.

---

## 2. Principle-to-feature adaptation matrix

| # | Guide idea | SuperFlow integration | Suitability adjustment |
|---:|---|---|---|
| 1 | Planning brain versus doing brain | **Plan Ahead / Do Now** experience: rich planning and review separately from a minimal execution surface. | Treat this as a useful design metaphor, not two literal brains or a diagnosis. |
| 2 | Visual anchors | **Anchor Lab** records physical cues, widgets, lock/home-screen cards, and printable/shareable cue cards. | The app suggests environmental action; it cannot verify physical placement. Wallpaper changes use Android-supported user confirmation. |
| 3 | Do sub-two-minute tasks immediately | **Quick Win** prompt during daily planning/capture. | Keep distinct from Atomic Habits' **Tiny Start**, which scales the beginning of a recurring habit. No endless task inbox. |
| 4 | Five-minute evening planning | **Plan Tomorrow** ritual with up to three focus actions, preparation, and optional mental rehearsal. | Visualization is optional and framed as rehearsal, not a guaranteed outcome technique. |
| 5 | Morning/midday/evening questions | Configurable **Daily Checkpoints** with concise focus, course-correct, and reflection prompts. | Defaults are suggested, not mandatory; reminders respect quiet hours and notification limits. |
| 6 | If-then planning | Expand implementation intentions to include **Obstacle Plans**: “If [situation], then [Tiny/alternative response].” | Both time/place and situational/emotional cues are supported without pretending all emotions can be detected automatically. |
| 7 | Motivation fluctuates | Capacity-aware Today screen and **Minimum Mode** reduce scope when motivation/energy is low. | No implication that low motivation is failure. |
| 8 | Discipline ladder | Each habit has **Tiny → Minimum → Standard → Stretch** levels and a gradual scaling review. | Calendar weeks are optional. Progress depends on repetitions, effort, and user choice—not time alone. |
| 9 | Increase no more than 25% | Scaling assistant defaults to small increments and may offer a conservative 25% ceiling for measurable habits. | Present 25% as a configurable safety heuristic, not a universal behavioral law. Never round a tiny baseline into a huge jump. |
| 10 | Minimum viable habit | Tiny/Minimum versions remain permanently available and are emphasized on hard days. | Tiny means “show up”; Minimum means a useful reduced dose. Users can define either or both. |
| 11 | Energy management | **Energy Map** captures lightweight energy check-ins and suggests time windows for demanding versus automatic actions. | Recommendations require enough personal data, explain uncertainty, and never diagnose health conditions. |
| 12 | Cue–routine–reward analysis | Existing Notice/Want/Start/Feel diagnostics map cue, response, and payoff. | Retain the richer four-stage model while accepting the guide's three-part view in plain language. |
| 13 | Substitute a routine while preserving need/reward | **Swap Plan** identifies the underlying need and chooses a safer replacement response. | The replacement should satisfy the need without assuming the old and new rewards are identical. High-risk behaviors route to professional help. |
| 14 | Friction engineering | Existing Environment Experiments remove steps for wanted behavior and add steps/delay for unwanted behavior. | AI cannot change protected device settings without the user completing Android controls. |
| 15 | Replace visual bad-habit cues | Anchor Lab pairs **Remove this cue** with **Put this helpful cue here**. | User confirms real-world changes; do not claim completion merely because a checklist was checked. |
| 16 | Days 3–7 are critical | **Early Support Window** monitors a user-selected or data-informed first set of opportunities. | Do not claim everyone quits on days 3–7. Support is based on opportunities and observed patterns, not a universal danger period. |
| 17 | Ten-day commitment | Optional **Commitment Sprint** for a chosen number of days or opportunities, default suggestion 10. | Completion does not imply automaticity. At the end, review whether to continue, redesign, or stop. |
| 18 | Staged accountability | Schedule partner/share-sheet check-ins during the Early Support Window. | Do not claim a habit becomes automatic on day 11; accountability cadence is user-selected and private. |
| 19 | Novelty every five days | **Freshness Options** vary an optional playlist, location, route, prompt, or reward when boredom appears. | No fixed five-day requirement. Preserve the core cue and behavior unless review shows they are the problem. |
| 20 | Celebrate milestones | **Meaningful Milestones** allow immediate small reinforcement and selected opportunity/repetition celebrations. | Rewards must align with identity and must not create harmful or conflicting behavior. Streak loss is never threatened. |
| 21 | Identity-based routines | Existing identities gain a situational prompt: “What is one action aligned with the person I am becoming?” | Keep identity flexible; one action or miss never defines the person. |
| 22 | Routine stacking | Existing Flow Builder provides morning/evening templates with stable anchors. | Templates are editable and start with very few new links. |
| 23 | Goldilocks difficulty | Existing challenge-fit reviews use Too easy / Manageable / Too hard plus effort and desire-to-continue feedback. | The app suggests; the user decides. Disability, illness, and changing capacity take precedence over progression. |
| 24 | Week-by-week implementation roadmap | Add a **Starter Path** template: foundation, observe, support, then expand. | Progression is readiness-based; week labels are guidance, not locks. |
| 25 | Never miss twice | Existing next-opportunity recovery card and Tiny/Minimum rescue. | No shame, red failure calendar, or rigid streak dependence. Planned rest is not a miss. |
| 26 | Motivation crash protocol | **Low-Capacity Reset:** Minimum Mode, five-minute/tiny start, permitted support contact, and personal Why. | “Five more minutes” is optional; stopping safely remains valid. |
| 27 | Old habit relapse protocol | **Return and Redesign:** acknowledge, map trigger, restore replacement, strengthen one system element. | For addiction, eating disorders, self-harm, or dangerous compulsions, provide professional resources and avoid app-only treatment claims. |
| 28 | Power Hour | Optional **Focus Hour** Flow template: Orient → Important Action → Restore/Energize. | Duration and 20/20/20 split are editable; never force a one-hour routine on a beginner. |
| 29 | Evening review | Plan Tomorrow includes accomplishment, friction, tomorrow's adjustment, and optional gratitude. | Gratitude remains optional and private. |
| 30 | Weekly reset | Existing Weekly Review gains daily-focus patterns, energy fit, obstacles, and the coming week's one system adjustment. | Keep it around 5–15 minutes and allow skip/pause without penalty. |

### Acceptance rule

Each matrix row must be traced to a screen, command/tool, product rule, or documented non-implementation decision before v1. Unsupported claims from the source guide must not appear in marketing, coaching copy, or AI system prompts as facts.

---

## 3. Core feature additions

### 3.1 Plan Ahead and Do Now

SuperFlow should explicitly design for two user states.

#### Plan Ahead

Used during onboarding, evening planning, and reviews:

- Clarify identity and Why.
- Select tomorrow's focus.
- Configure implementation intentions and Obstacle Plans.
- Prepare the environment and visual anchors.
- Choose Tiny, Minimum, Standard, and Stretch versions.
- Schedule around likely energy.
- Configure support and healthy reinforcement.

#### Do Now

Used from Today, widgets, notifications, and voice:

- Show the next action in one sentence.
- Make Tiny and Minimum available immediately.
- Avoid exposing every setting during execution.
- Allow Standard / Minimum / Tiny / Later / intentional Skip.
- Allow “I have low energy” to switch the current day or selected actions into Minimum Mode.
- Let AI execute quick check-ins or plan adjustments under capability policy.

Planning changes the system; Do Now helps the user act without reopening the design problem.

### 3.2 Daily Focus: up to three priorities

Daily Focus is a small bridge between long-term systems and today. It is not a general task manager.

A focus item may be:

- a scheduled habit opportunity,
- a system action,
- a one-off next action linked to a goal,
- or an intentional recovery/preparation action.

Rules:

- Recommend one to three items; permit an override without encouraging overload.
- Rank one as **Most Important** and allow two supporting items.
- One-off focus actions expire or are deliberately moved; they do not form an infinite backlog.
- Show estimated effort/capacity and a Tiny/Minimum fallback where meaningful.
- AI can draft the focus from the user's permitted schedule and priorities, but only auto-apply under an allowed daily-planning policy.
- The Today screen continues to show scheduled habits; Daily Focus highlights rather than hides them.

When a user captures a one-off action estimated under two minutes, **Quick Win** offers **Do now**, **Keep in Daily Focus**, or **Dismiss**. The app never marks a real-world action complete merely because AI suggested it. Quick Win is distinct from Tiny Start: Quick Win handles a short one-off action immediately, while Tiny Start makes a recurring habit easy to begin.

### 3.3 Plan Tomorrow ritual

A configurable five-minute ritual, usually in the evening:

1. Recognize what moved forward.
2. Select tomorrow's top one to three focus actions.
3. Check calendar/schedule conflicts.
4. Attach time/place/anchor cues.
5. Choose an Obstacle Plan for the most important action.
6. Prepare one physical/digital visual anchor.
7. Optionally rehearse beginning the action.
8. Save and optionally notify at the morning checkpoint.

AI can complete the routine conversationally:

> “Tomorrow I need to study chemistry and walk. Make chemistry the main priority after breakfast. If I feel overwhelmed, have me open the notes and answer one question. Remind me to put the notebook on the table tonight.”

The AI turns this into linked, inspectable objects and explains what it scheduled.

### 3.4 Daily Checkpoints

#### Morning — Orient

- What matters most today?
- Which identity does it support?
- What is the first visible action?

#### Midday — Course-correct

- Is the current plan still realistic?
- Keep, shrink, move, or intentionally release an item.
- Is current energy better suited to demanding or automatic work?

#### Evening — Learn and prepare

- What moved forward?
- What created friction?
- What one adjustment would help tomorrow?
- Optional gratitude/private note.

Settings:

- enable each checkpoint independently,
- choose time, anchor, days, reminder, and prompt length,
- use manual, AI-guided, or AI-drafted mode,
- set cloud/local context limits,
- and turn proactive AI adjustments on/off.

### 3.5 Obstacle Plans

Implementation intentions extend beyond “when and where”:

```text
If [obstacle/cue] happens, then I will [safe fallback/replacement].
```

Examples:

- If the planned time is lost, do the Minimum version at the next anchor.
- If energy is low, open the notes and complete one question.
- If the urge to scroll appears, place the phone down and take five slow breaths.
- If travelling, use the hotel-room version.

Obstacle categories may include schedule, location, energy, emotion, social context, missing equipment, connectivity, and custom. Emotion is self-reported; the app must not imply passive emotion detection without a separately consented capability.

### 3.6 Habit Ladder

Each habit may have four action levels:

| Level | Purpose | Example: reading |
|---|---|---|
| Tiny | Make starting almost effortless | Open the book |
| Minimum | Preserve a useful routine on a hard day | Read one page |
| Standard | Normal planned behavior | Read ten pages |
| Stretch | Optional challenge when capacity is high | Read twenty pages |

Rules:

- Tiny and Minimum never disappear when a user scales up.
- Stretch is never required for consistency or identity evidence.
- Scaling is proposed during review, not forced mid-action.
- Inputs include repetitions, perceived effort, quality, misses, recovery, health/capacity, and user desire.
- For numeric actions, an optional conservative growth guardrail may suggest no more than 25%, clearly labeled as a heuristic.
- “Standardize before optimize”: reliable starting comes before more volume.

### 3.7 Energy Map

For an optional observation period—suggested one week but configurable—the user records energy with one tap at selected times:

- Very low
- Low
- Steady
- High
- Very high

Optional context: sleep quality as self-report, location, day type, and short note. Avoid collecting medical details unless a future health integration explicitly needs them.

Insights:

- Typical energy range by user-defined time block and day type.
- Completion/effort patterns at different energy ranges.
- Suggested windows for demanding, routine, recovery, or planning actions.
- Confidence/coverage statement such as “Based on 8 check-ins across 5 days.”

The AI may suggest schedule experiments but must not silently move protected or important routines outside its time-shift permission.

### 3.8 Anchor Lab

For each target behavior:

- What should become more visible?
- Where will it be placed?
- Which existing anchor precedes it?
- Which unwanted cue should be hidden or moved?
- What can be prepared tonight?
- Would a widget, notification, focus card, or wallpaper cue help?

Outputs:

- Environment Experiment.
- Physical action checklist.
- Optional home-screen widget configuration.
- Shareable/image cue card or wallpaper preview; actual wallpaper application follows Android-supported user interaction.
- Evening preparation action.

### 3.9 Swap Plan

For an unwanted habit:

1. Describe the trigger: time, place, emotion, people, or preceding action.
2. Name the immediate benefit or need.
3. Describe the current response and actual costs.
4. Choose a safer replacement response.
5. Remove the old visual cue where possible.
6. Place the new cue in the same decision path where useful.
7. Add friction/delay to the unwanted response.
8. Define Tiny replacement and lapse recovery.
9. Review whether the replacement actually met the need.

This integrates with SuperFlow's four inversions instead of creating a second incompatible model.

### 3.10 Early Support Window and Commitment Sprint

#### Early Support Window

A temporary support plan for the first configurable number of scheduled opportunities:

- stronger cue/preparation checks,
- optional morning/evening focus,
- Tiny/Minimum rescue visibility,
- selected accountability moments,
- and a short review before changing scope.

Default recommendations may mention the first week, but the system adapts to actual frequency. A weekly habit's first seven opportunities span much longer than seven days, so opportunities are the primary unit.

#### Commitment Sprint

- User chooses a habit, duration by days or opportunities, start date, Minimum commitment, and review date.
- Ten days is an available template, not a promise.
- The sprint does not lock the user in or punish an exit.
- End states: continue, redesign, pause, stop with learning, or begin another sprint.
- Automaticity is never asserted from completion.

### 3.11 Freshness Options and meaningful celebrations

Freshness should respond to boredom without destabilizing a useful cue.

Safe variations:

- playlist or ambience,
- route or exercise variation,
- writing prompt,
- learning example,
- healthy immediate reward,
- supportive companion,
- or optional location when context stability is not important.

Celebrations:

- immediate subtle completion feedback,
- user-defined repetition/opportunity milestones,
- private message/share with explicit consent,
- values-aligned experience or reward,
- identity evidence review.

Do not add loot boxes, variable random rewards, public leaderboards, guilt countdowns, or rewards that contradict the habit.

### 3.12 Focus Hour template

An optional advanced Flow template:

1. **Orient:** review identity, Daily Focus, and environment.
2. **Important Action:** perform the highest-value habit/system action.
3. **Restore:** perform a healthy energizing or recovery action.

The guide's 20/20/20 structure is a preset. Users can choose 5/20/5, 10/40/10, another split, or no timer. Beginners should receive a much smaller Flow first.

### 3.13 Recovery Center

The Recovery Center consolidates three protocols.

#### After a missed opportunity

- Acknowledge neutrally.
- Protect the next opportunity.
- Select Tiny or Minimum.
- Identify one friction source.
- Make one system adjustment.

#### When motivation or capacity crashes

- Activate Minimum Mode for selected duration.
- Reconnect to identity/Why without a motivational lecture.
- Offer Tiny, Minimum, or optional five-minute version.
- Contact an accountability partner if configured.
- Reduce competing focus items.

#### When an unwanted habit returns

- Avoid judgment.
- Record the trigger if useful.
- Restore the Swap Plan/replacement at the next relevant cue.
- Add one environment/friction improvement.
- Offer professional support for high-risk behavior.

### 3.14 Starter Path and reset templates

An optional readiness-based path packages the guide's implementation roadmap without locking features to calendar weeks:

1. **Foundation:** choose no more than one or two starter habits, define Tiny/Minimum, prepare the environment, and configure only useful checkpoints.
2. **Observe and test:** try morning/evening anchors, notice friction and energy, and make one small adjustment while prioritizing consistency over volume.
3. **Add support:** introduce accountability, one habit stack, and a meaningful celebration only where useful.
4. **Sustain and expand:** scale a reliable habit gradually, add another only when capacity permits, review monthly, and reconnect actions to identity.

Morning and evening Flow presets are editable examples rather than required routines. The Weekly Reset template reviews repetitions, Daily Focus, obstacles, energy fit, support, and one next system change in roughly 5–15 minutes.

---

## 4. AI integration and universal control

Every feature above is available through manual screens and AI tools. The AI should handle orchestration, not bypass safeguards.

### Example commands

- “At 9 PM, help me choose tomorrow's three priorities. Draft them from my active systems but ask before saving.”
- “My energy is low today. Put exercise and study in Minimum Mode, but leave medication unchanged.”
- “For the next ten opportunities, help me protect my reading habit. Add a check-in with Arjun after opportunities three and seven.”
- “I keep scrolling when stressed after lunch. Create a Swap Plan using a five-minute walk, move the social app reminder out of view, and ask me before opening Android settings.”
- “Based on my energy check-ins, suggest a better time for deep study. Do not move anything yet.”
- “Make a Focus Hour tomorrow with 10 minutes planning, 35 minutes writing, and 15 minutes walking.”
- “I missed yesterday. Do the smallest recovery plan and prepare my visual cue for tonight.”
- “Turn off midpoint check-ins on weekends and use only the local coordinator for daily planning.”

### Capability additions

```text
daily_focus.read / generate / set / reorder / clear
focus_action.create / edit / complete / move / expire
checkpoint.read / configure / start / draft / complete / skip
obstacle_plan.create / edit / apply / archive
habit_level.read / set / recommend / complete
minimum_mode.enable / configure / disable
energy_checkin.record / edit / delete
energy_map.read / analyze / suggest_schedule_experiment
visual_anchor.create / edit / complete / archive / export_card
swap_plan.create / edit / review / activate_recovery
support_window.create / edit / pause / complete
commitment_sprint.create / edit / stop / review
freshness_option.create / apply / remove
celebration_plan.create / edit / trigger / share
focus_hour.create / start / pause / complete
recovery_protocol.start / apply_step / complete
```

### AI behavior rules

- AI may draft Daily Focus from permitted data but cannot treat its ranking as objective truth.
- AI may auto-apply safe, reversible planning under a narrow grant.
- Changes to many days, accountability sharing, external settings, health-sensitive actions, or data deletion follow higher risk classes.
- Optional Preview/Guided mode never reduces medication/clinical routines from generic energy logic. Full Control may alter any registered app-local routine without another prompt and records the change.
- Cloud providers see Energy Map, notes, checkpoint answers, or accountability data only when that profile has explicit category permission.
- Proactive AI can suggest a recovery or schedule experiment; it cannot nag repeatedly after dismissal.
- AI must say when a pattern has too little data and distinguish correlation from cause.
- Claims such as “days 3–7 are when people quit,” “day 11 is automatic,” or “25% growth is always safe” are prohibited as factual coaching claims.

### Blueprint Studio integration

A user may upload this guide, personal notes, schedules, and other plans to **Blueprint Studio** with instructions such as “integrate the useful ideas but keep the routine low-pressure.” The long-horizon Intent Compiler then:

- preserves page/line provenance for each extracted discipline requirement,
- maps suitable items to this document's 30-point matrix and the complete SuperFlow domain,
- marks fixed-day/percentage claims as editable heuristics rather than facts,
- resolves conflicts against the user's schedule, protected routines, privacy, and existing state,
- produces a complete target Blueprint and source-linked diff,
- applies only authorized changes through the universal tools,
- and verifies/undoes/amends the result through the **[Blueprint Studio Plan](BLUEPRINT_STUDIO_PLAN.md)**.

In optional Preview/Guided mode, discipline features use the protection and review defaults in this plan. Under **[Full Control](FULL_CONTROL_PLAN.md)**, AI may change all registered app-local routines/settings, resolve conflicts, and apply the complete Blueprint without repeated confirmation; source rationale, Activity, snapshots, verification, Stop, and undo remain.

---

## 5. Information architecture changes

### Today

- Daily Focus header with up to three items.
- Do Now next action.
- Current capacity and optional Minimum Mode.
- Morning/midday/evening checkpoint cards when due.
- Existing habit timeline and recovery card.
- Quick energy check-in, configurable and hideable.

### Journey

- Habit Ladder on each habit.
- Obstacle Plans.
- Early Support Window / Commitment Sprint.
- Anchor Lab and Swap Plan.
- Focus Hour and routine templates.

### Insights

- Daily Focus completion without a “productivity score.”
- Energy and effort patterns with sample size.
- Level distribution: Tiny/Minimum/Standard/Stretch.
- Recovery at next opportunity.
- Obstacle/experiment outcomes.
- Sprint review—not automaticity claim.

### AI

- Plan Tomorrow conversation.
- Daily checkpoint guidance.
- Energy-aware scheduling experiments.
- Recovery Center.
- All universal control capabilities and action history.

### Settings

- Daily Focus limits and carryover.
- Checkpoint schedule/prompt/AI mode.
- Energy Map collection and data access.
- Minimum Mode protected habits and duration.
- Early Support and celebration defaults.
- AI provider permissions for priorities, energy, reviews, and accountability.
- Proactive suggestion frequency and quiet hours.

---

## 6. Data model additions

```text
DailyPlan
- id, localDate, zoneId, status, createdBy, createdAt, updatedAt

FocusAction
- id, dailyPlanId, rank, importance
- sourceType [HABIT_OPPORTUNITY, SYSTEM_ACTION, GOAL_ACTION, PREPARATION, RECOVERY, CUSTOM]
- sourceId?, title, tinyFallback?, estimatedEffort?, status, expiresAt?

DailyCheckpoint
- id, type [MORNING, MIDDAY, EVENING], schedule, enabled
- promptMode [MANUAL, AI_GUIDED, AI_DRAFTED], providerScope?, reminderPolicy

CheckpointEntry
- id, checkpointId, localDate, answers, adjustments, createdAt

ObstaclePlan
- id, habitId?, focusActionId?, obstacleType, ifDescription
- thenAction, fallbackLevel?, status

HabitLevel
- id, habitId, level [TINY, MINIMUM, STANDARD, STRETCH]
- action, targetValue?, unit?, enabled

MinimumModeSession
- id, startsAt, endsAt?, scope, protectedObjectIds, reason?, createdBy

EnergyCheckIn
- id, occurredAt, localTimeBlock, energyLevel, dayType?, note?

VisualAnchor
- id, habitId?, focusActionId?, anchorType [PHYSICAL, WIDGET, CARD, WALLPAPER, NOTIFICATION]
- description, location?, preparationAction?, status

SupportWindow
- id, habitId, unit [DAYS, OPPORTUNITIES], length, currentProgress
- supportRules, startsAt, endsAt?, status

CommitmentSprint
- id, habitId, unit, targetLength, minimumLevel, reviewAt, status

SwapPlan
- id, habitId, trigger, underlyingNeed, currentResponse, replacementResponse
- cueRemoval, helpfulCue, frictionAction, recoveryAction, status

FreshnessOption
- id, habitId, type, description, applyWhen, status

CelebrationPlan
- id, habitId?, milestoneType, threshold, reward, sharingPolicy, status

RecoverySession
- id, type [MISS, LOW_CAPACITY, RETURN_OF_UNWANTED_HABIT]
- linkedObjectId?, trigger?, selectedSteps, adjustment?, startedAt, completedAt?
```

### Domain rules

- DailyPlan uniqueness uses local date plus zone context; travel changes do not silently rewrite completed days.
- Focus actions are capped by a user preference and default to three; override is possible and audited.
- Optional Preview/Guided behavior may give Minimum Mode protected exclusions; Full Control can override all app-local protections.
- Energy Check-ins are private local data by default and excluded from analytics.
- Energy suggestions require a minimum configurable sample and display coverage.
- Habit-level completion records the actual level used rather than converting every completion to Standard.
- Sprint/support progress is derived from opportunities; editing a schedule does not rewrite history.
- Celebration milestones use repetitions/opportunities, not only uninterrupted streaks.
- One-off focus actions cannot become an unbounded hidden backlog.

---

## 7. Reminder strategy additions

- **Plan Tomorrow:** one optional reminder tied to a time or evening anchor.
- **Morning checkpoint:** show Daily Focus and first visible action.
- **Midday checkpoint:** ask whether to keep, shrink, move, or release—not “Why are you behind?”
- **Evening checkpoint:** short reflection and preparation.
- **Visual anchor preparation:** notify at the setup time, not repeatedly at action time.
- **Early Support Window:** use existing reminders/accountability moments; do not multiply notification volume by default.
- **Minimum Mode:** suppress nonessential stretch prompts and use reduced action copy.
- **Recovery:** at most one next-opportunity return prompt unless the user requests more.

A reminder budget prevents the combined system from becoming noisy. AI-created reminders use the same budget and permission policy as manual reminders.

---

## 8. Scope and delivery

### MVP additions

- Plan Ahead / Do Now split.
- Daily Focus with up to three linked actions.
- Plan Tomorrow and configurable checkpoints.
- Obstacle Plans.
- Tiny/Minimum/Standard levels; Stretch may follow.
- Minimum Mode, optional Preview/Guided exclusions, and Full Control override behavior.
- Anchor Lab basics.
- Swap Plan integrated with unwanted-habit flow.
- Recovery Center.
- Manual/AI capability parity for each addition.

### Beta additions

- Energy Map observation and explainable schedule experiments.
- Early Support Window and Commitment Sprint.
- Complete four-level Habit Ladder and conservative scaling assistant.
- Focus Hour and Starter Path templates.
- Freshness Options and meaningful milestone plans.
- Widget/cue card and optional wallpaper preview.
- Accountability scheduling and private share-sheet updates.
- Full AI-controlled checkpoint drafting and background Plan Tomorrow within user policy.

### Later, after validation

- More sophisticated on-device energy/schedule pattern models.
- Calendar integration with granular read/write permissions.
- Contextual automation from supported device modes, without covert emotion inference.
- Partner service beyond share sheet.
- Wearable energy/check-in surfaces with explicit consent.

---

## 9. Testing

### Domain tests

- Focus cap, rank, expiration, carryover, and time-zone travel.
- Checkpoint recurrence, quiet hours, and skip behavior.
- Obstacle-plan matching and fallback-level validity.
- Habit-level scaling, rounding, and no forced progression.
- Minimum Mode scope/expiry, optional Preview/Guided exclusions, and Full Control override/audit.
- Energy aggregation, sample threshold, missing data, and uncertainty copy.
- Support Window/Sprint by day versus opportunity.
- Celebration by repetition without streak dependence.
- Recovery next-opportunity logic.

### Manual/AI parity tests

For every new command, compare manual and AI paths for:

- final domain state,
- schedule and reminder effects,
- attribution/audit,
- capability/risk policy,
- background execution,
- and undo/compensation.

### AI evaluations

- Selects no more than permitted Daily Focus items.
- Does not invent energy patterns from inadequate data.
- Preserves protected routines in optional Preview/Guided mode; Full Control may move them and must record the override.
- Distinguishes Quick Win from Tiny Start.
- Treats 10 days, early support ranges, novelty cadence, and 25% growth as options rather than facts.
- Preserves stable cues when suggesting freshness.
- Responds to misses without shame.
- Routes high-risk relapse content appropriately.
- Cannot share checkpoint/accountability details without permission.
- Cannot broaden its own access to energy, review, or note content.

### UX research questions

- Does Daily Focus clarify the day or add pressure?
- Do users understand Tiny versus Minimum?
- Does Plan Tomorrow reduce morning decisions?
- Are three checkpoints supportive or noisy?
- Does Energy Map produce useful experiments without health anxiety?
- Does Early Support feel encouraging rather than surveillance?
- Do celebrations support identity without becoming the goal?
- Can users complete every flow comfortably without AI?

---

## 10. Definition of done

This integration is complete for v1 when:

1. All 30 matrix rows have an implementation, test, or documented suitability decision.
2. Daily Focus remains limited and linked to systems instead of becoming an endless task manager.
3. Plan Ahead creates a plan that Do Now can execute with minimal decisions.
4. Tiny, Minimum, and Standard are distinct, available, and correctly recorded.
5. Obstacle Plans, visual anchors, substitution, friction, and recovery connect to existing habit design rather than duplicate it.
6. Energy features are optional, explain sample size/uncertainty, remain local by default, and never diagnose.
7. Fixed-day and percentage heuristics are never marketed as guarantees or automaticity thresholds.
8. Optional Preview/Guided behavior may preserve protected medication/clinical routines; Full Control may change them without another prompt while preserving source/activity provenance and undo.
9. Notification volume remains within user-configured budgets.
10. Every feature has equivalent manual and AI control through shared commands and parity tests.
11. AI automation obeys context, capability, confirmation, protected-action, audit, background, cost, and undo rules.
12. Miss, motivation-crash, and unwanted-habit-return protocols are compassionate and safety-reviewed.
13. The app remains fully useful with AI and Energy Map disabled.
