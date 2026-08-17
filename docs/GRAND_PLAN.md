# SuperFlow — Grand Product and Engineering Plan

> **Working product promise:** Shape your system. Become your future self, one small action at a time.
>
> **Document status:** Product north star and implementation blueprint
>
> **Target:** Native Android application distributed as a signed APK and, for Google Play, an Android App Bundle (AAB)
>
> **Last updated:** 17 August 2026

---

## 1. Executive vision

SuperFlow is a calm, privacy-respecting personal growth app that helps people turn meaningful goals into sustainable daily systems. It is not merely a checklist or a streak counter. It guides a person from:

1. **The person I want to become** (identity),
2. to **the result that would matter** (goal),
3. to **the repeatable process that can produce it** (system),
4. to **the smallest useful action I can take now** (habit),
5. to **evidence, reflection, and adjustment** (growth loop).

The product should make a useful action easier to start, make an unwanted action harder to perform, provide an immediate sense of progress, and help the user recover compassionately after disruption. Goals provide direction; systems and repeated actions receive most of the product's attention.

SuperFlow separates reflective **Plan Ahead** moments from low-friction **Do Now** execution. Daily Focus, checkpoints, Habit Levels, Obstacle Plans, optional energy awareness, visual anchors, substitution, early support, and recovery help the system work across high- and low-capacity days without assigning a moral “discipline score.”

Its flagship capability is **Blueprint Studio**, a dedicated long-horizon Intent Compiler in the AI tab. Users provide one or many Markdown, text, PDF, or pasted sources plus instructions; SuperFlow builds a source-linked Requirement Ledger, reconciles conflicts, designs the entire personalized workspace, executes the configured changes, verifies actual state, and reports every assumption, gap, and undo path.

**Full Control is the primary AI profile in the one SuperFlow app.** After one activation, AI can run every registered app-local capability—including bulk, destructive, settings, provider-routing, no-question Blueprint, and background operations—without repeated confirmation, while automatic snapshots, verification, Activity, deterministic Stop, and grouped undo remain. APK and AAB expose the same capabilities.

SuperFlow is inspired by the behavior-change framework popularized in *Atomic Habits* by James Clear. It must apply the framework comprehensively while using SuperFlow's own brand, language, interface, examples, and content. It must not present itself as an official *Atomic Habits* product or reproduce substantial passages from the book.

### One-sentence pitch

**SuperFlow turns a goal into an identity-aligned daily flow, makes the next action easy, and helps the user improve the system instead of blaming themselves.**

### What SuperFlow is not

- Not a productivity inbox filled with endless tasks.
- Not a punitive streak game.
- Not a social feed or comparison engine.
- Not a substitute for medical, mental-health, addiction, financial, or other professional care.
- Not an AI that hides its actions, falsifies completion, reads credential values, crosses user/project boundaries, or claims to bypass Android/provider-owned interfaces.
- Not dependent on an account, cloud model, or network connection for its core habit features.

---

## 2. Product principles

Every product decision should satisfy these principles.

1. **Identity before outcome:** Ask who the user wants to become before asking how much they want to achieve.
2. **Systems before scoreboards:** Goals set direction; recurring processes drive the Today screen.
3. **Start tiny:** Every habit has a version that can be started in roughly two minutes or less.
4. **Design beats willpower:** Help users alter cues, context, friction, and rewards rather than depend on motivation.
5. **Repetition beats intensity:** Count showing up and useful repetitions; do not imply a magical number of days creates a habit.
6. **Recovery beats perfection:** One miss triggers a gentle reset plan, not shame. Prevent the second consecutive miss.
7. **Satisfaction should be immediate and honest:** Completion feedback is encouraging but never manipulative or casino-like.
8. **Progress is personal:** Favor self-comparison, reflection, and identity evidence over leaderboards.
9. **The user remains in control:** Local-first operation, optional AI, user-defined autonomy, complete action history, undo, explicit consent, export, and deletion.
10. **Manual and AI are equal:** Every meaningful operation is available through the manual interface and through permissioned AI control, using the same domain rules.
11. **Calm is a feature:** No ads, dark patterns, guilt notifications, or artificial urgency.
12. **Measure the right thing:** Metrics inform behavior but never replace the behavior or become the goal by accident.
13. **Adapt, do not prescribe:** The system should change with a user's capacity, context, season, and values.
14. **Plan with perspective; act with simplicity:** Rich planning and review belong in Plan Ahead, while Do Now presents the smallest clear action for tired or distracted moments.
15. **Capacity and energy matter:** Let users plan around observed energy, switch safely into Minimum Mode, and protect essential routines without treating low-capacity days as failure.
16. **Sources must remain traceable:** Document-driven AI must connect requirements, decisions, changes, and verification back to page/line evidence and visibly distinguish user instructions from AI assumptions.
17. **Long-horizon means durable and verified:** Intensive missions survive interruption and report actual results and gaps rather than claiming completion from model text; optional Preview/Guided modes use limits, while Full Control may use resource-based/unlimited product settings.
18. **Full autonomy is the primary AI profile:** A user activates Full Control once and receives no-question, no-repeat-confirm control across every registered app-local capability, with automatic execution correctness and undo rather than approval friction. Preview/Guided behavior is an optional preference inside the same app.

---

## 3. Behavior-change model

### 3.1 The SuperFlow growth hierarchy

```text
IDENTITY  →  GOAL  →  SYSTEM  →  HABIT  →  CHECK-IN  →  REVIEW
  who       where     how        next       evidence     improve
```

- **Identity:** “I am becoming someone who cares for their health.”
- **Goal:** “Comfortably complete a 5 km walk.”
- **System:** “Move after breakfast on weekdays and prepare tomorrow's gear at night.”
- **Habit:** “After breakfast, put on walking shoes and step outside.”
- **Tiny version:** “Put on the shoes.”
- **Check-in:** Tiny, Minimum, Standard, Stretch, skipped intentionally, or missed.
- **Review:** Keep, shrink, expand, reschedule, redesign the environment, or retire.

Identity and behavior form a feedback loop. Repeated actions provide evidence for an identity; a chosen identity makes aligned actions more meaningful. A completion is an identity vote, never proof that a person is “good,” and a miss does not remove previous evidence.

### 3.2 The habit loop

Every habit design captures four stages:

| Stage | SuperFlow question | Good-habit design | Unwanted-habit inversion |
|---|---|---|---|
| Cue | What will make me notice it? | Make it obvious | Make it invisible |
| Desire | Why will I want to do it now? | Make it attractive | Make it unattractive |
| Action | How can I start with less effort? | Make it easy | Make it difficult |
| Payoff | What makes completion feel worthwhile now? | Make it satisfying | Make it unsatisfying |

The app may use the friendlier labels **Notice → Want → Start → Feel**, while the internal model retains cue, craving, response, and reward for precision.

### 3.3 Goals versus systems

A goal is allowed, but cannot exist alone. The goal creation flow must end with at least one system and one tiny next action. The Home screen emphasizes actions under the user's control rather than distant outcomes. Goal progress can be displayed, but process adherence, recovery, and reflection receive equal or greater prominence.

### 3.4 Good habits and unwanted habits

SuperFlow supports both:

- **Build mode:** add cues, increase appeal, reduce friction, and add a healthy immediate reward.
- **Reduce mode:** hide cues, expose the real costs, add friction or a commitment device, and add accountable consequences.

Reduce mode should also offer a positive replacement behavior that addresses the underlying need. For high-risk behaviors such as substance dependence, eating disorders, self-harm, or dangerous compulsions, SuperFlow must encourage qualified help and avoid pretending that an app-only plan is sufficient.

---

## 4. Complete principle-to-product coverage

“All principles” can otherwise become an untestable statement. The following matrix is the acceptance checklist for comprehensive coverage of the book's major concepts. Wording is intentionally original and implementation-oriented.

| # | Principle to preserve | SuperFlow implementation |
|---:|---|---|
| 1 | Small changes compound and trajectory matters | “Smallest improvement” prompts, trend views, and weekly system adjustments; never promise a literal guaranteed 1% result. |
| 2 | Results can lag behind effort; early progress may be hidden | A “quiet progress” timeline celebrates repetitions before outcome movement and explains plateaus without false predictions. |
| 3 | Goals choose direction; systems create progress | Every goal links to a repeatable system; Today prioritizes the system. |
| 4 | Lasting change works at outcome, process, and identity layers | The data model and creation wizard represent all three, beginning with identity. |
| 5 | Decide who to become and prove it with small wins | Identity statements plus an evidence ledger derived from completed habits. |
| 6 | Identity and habits reinforce each other | Reviews show “evidence you created,” while allowing identities to evolve instead of becoming rigid labels. |
| 7 | Habits follow cue, desire, response, and reward | Four-stage Habit Designer and diagnostic tools. |
| 8 | Awareness precedes change | A nonjudgmental Habit Scorecard records current routines as helpful, neutral, or unhelpful. |
| 9 | Naming actions raises awareness | Optional “notice and name” prompt before a known unwanted routine; no public or intrusive voice requirement. |
| 10 | A specific time and place improve follow-through | Implementation-intention fields: “I will [action] at [time] in [place].” |
| 11 | Existing routines can cue new ones | Habit stacking: “After/Before [anchor], I will [action].” |
| 12 | Context and environment often outweigh motivation | Environment setup checklist, context fields, and weekly environment experiments. |
| 13 | Stable cues should be visible and unambiguous | Cue photos/notes, preparation prompts, location labels, and one primary cue per starter habit. |
| 14 | Self-control is easier when tempting cues are absent | Reduce mode begins with cue removal, notification blocking suggestions, and context avoidance—not a willpower lecture. |
| 15 | Anticipation drives motivation | Users choose a near-term benefit and can preview it on the Today card without exaggerated claims. |
| 16 | Pairing a wanted action with a needed action increases appeal | Temptation bundles connect a healthy pleasure to a target habit, with checks against harmful rewards. |
| 17 | Social norms influence behavior | Optional accountability circles and examples of supportive groups where the desired behavior is normal. No public popularity feed. |
| 18 | Proximity, group norms, and respected people are powerful | Accountability setup asks whether support comes from close contacts, a relevant community, or a trusted mentor. |
| 19 | Reframing highlights benefits of good habits and costs of bad ones | A “change the story” card contrasts immediate feeling, long-term benefit, and true cost in the user's own words. |
| 20 | A consistent pleasant ritual can precede difficult work | “Start ritual” templates for study, exercise, writing, and other effortful flows. |
| 21 | Repetition creates automaticity; elapsed days alone do not | Insights emphasize repetitions and context consistency, never a fixed habit-formation countdown. |
| 22 | Planning can feel productive while avoiding action | The app limits endless setup and ends every planning flow with “Do the tiny version now.” |
| 23 | People naturally favor the path requiring less effort | Friction audit asks what can be removed, prepared, shortened, or automated. |
| 24 | Prepare the environment for the future action | “Set up tomorrow” actions can be stacked after an evening anchor. |
| 25 | Small decisive moments steer the rest of a day | Mark gateway choices and show their downstream flow without implying one choice defines the person. |
| 26 | A new habit should be easy to begin in about two minutes | Every build habit requires or receives a suggested Tiny Start; Rescue mode launches it. |
| 27 | Establish showing up before optimizing performance | Habit scaling unlocks through review: tiny → starter → standard → stretch, while tiny remains available. |
| 28 | Ritualizing the beginning helps entry into focused work | A Flow Timer can start after the gateway action; duration is optional and secondary. |
| 29 | Commitment devices lock in better future choices | Optional commitments, app/site blocker deep links, deposits handled by external trusted services, or an accountability promise. |
| 30 | One-time choices and automation can repeatedly improve behavior | Environment actions include recurring transfers, automatic delivery, device settings, and calendar setup; the app never executes financial actions without explicit confirmation. |
| 31 | Immediate consequences shape behavior more strongly than delayed ones | Place immediate, values-aligned reinforcement after completion and immediate friction before unwanted actions. |
| 32 | Rewarded behavior tends to repeat; painful behavior tends to be avoided | Completion feedback and accountability consequences are transparent, proportionate, and user-selected. |
| 33 | Immediate rewards should support, not contradict, the desired identity | The reward picker rejects obvious conflicts and suggests restorative or symbolic rewards. |
| 34 | Visible tracking can make progress obvious and satisfying | One-tap check-ins, calendar and repetition views, widgets later, and immediate recording after action. |
| 35 | Do not break a run carelessly, but never miss twice | Streaks are secondary. A miss surfaces a next-occurrence recovery card and Tiny Start, without guilt. |
| 36 | Tracking must not become the behavior or distort it | Users can hide streaks, track quality notes, and review whether the metric still represents the real aim. |
| 37 | Accountability adds an immediate social consequence | Private partner check-ins and a configurable habit agreement; no involuntary sharing. |
| 38 | Choose habits suited to interests, abilities, constraints, and personality | Preference and energy assessment suggests formats, not fixed personality labels. |
| 39 | Select a favorable game, or create a niche that fits strengths | Reviews ask “Should I persist, adjust the method, or choose a better-fit path?” |
| 40 | Motivation peaks at a challenge just beyond current ability | Difficulty calibration recommends a manageable next level based on recent check-ins and user feedback. |
| 41 | Feedback helps maintain engagement | Immediate check-ins plus weekly qualitative reflection; no meaningless score spam. |
| 42 | Boredom is part of mastery; consistency matters after novelty fades | “Steady mode” removes novelty dependence, varies optional challenges without changing the core routine, and praises showing up. |
| 43 | Habits create a foundation for deliberate practice | Once a routine is reliable, users may attach a focused skill target and feedback note. |
| 44 | Automatic habits can hide mistakes | Scheduled reviews ask whether the habit is still effective, correctly performed, and aligned. |
| 45 | Periodic reflection and review enable long-term improvement | Weekly check-in, monthly systems review, and annual/quarterly identity-and-values review. Cadence remains configurable. |
| 46 | Identity can become too rigid | Users can rewrite, archive, or broaden an identity; copy avoids “I always/I never” shame traps. |
| 47 | Success is a process without a permanent finish line | Completed goals transition into maintain, graduate, replace, or close-with-learning states. |

### Coverage rule

A feature is not “Atomic Habits-aligned” merely because it tracks a streak. Before release, product QA must trace each matrix row to at least one implemented screen, rule, prompt, or documented design decision. Rows 1–36 and 40–46 are required for the first public release; social/accountability rows 17, 18, and 37 may initially be implemented as local planning and share-sheet flows before a full network service exists.

A second 30-point suitability and coverage matrix integrates the user-provided self-discipline guide—including Daily Focus, Plan Ahead/Do Now, checkpoints, Obstacle Plans, Habit Ladder, Energy Map, Early Support, Commitment Sprints, recovery, and weekly reset—without adopting unsupported fixed-day claims. See the **[Self-Discipline Integration Plan](SELF_DISCIPLINE_INTEGRATION.md)**.

---

## 5. Target users and jobs to be done

### Primary audiences

1. **The overwhelmed beginner:** Has an important intention but repeatedly starts too large.
2. **The inconsistent improver:** Knows what to do but cannot make it fit daily life.
3. **The reflective planner:** Wants goals, routines, notes, and review in one calm system.
4. **The recovery user:** Has broken a previous routine and needs a shame-free restart.
5. **The privacy-conscious user:** Wants useful tracking without surrendering personal content.

### Core jobs

- When I have a vague goal, help me turn it into a repeatable system and a small next action.
- When motivation is low, make the beginning easy enough that I can still show up.
- When a behavior keeps failing, help me diagnose the design rather than condemn myself.
- When I miss, help me return at the next opportunity.
- When I make progress, show evidence that I am becoming the person I chose to be.
- When a routine becomes stale or ineffective, help me adjust its difficulty or design.
- When I want guidance, give me supportive suggestions without exposing my private data.
- When I do not want to navigate forms, let me tell the AI what I want and have it safely complete the work in the background.
- When I prefer direct control—or AI is unavailable—let me perform every operation manually with equal capability.
- When AI acts for me, let me choose its autonomy, inspect exactly what changed, and undo or stop it.

---

## 6. Information architecture

### Bottom navigation

1. **Today** — Daily Focus, Do Now, current flows, Tiny/Minimum actions, optional energy/checkpoints, and recovery.
2. **Journey** — identities, goals, systems, Habit Ladders, habits, Obstacle/Swap Plans, anchors, and support sprints.
3. **Insights** — repetitions, consistency, recovery, identity evidence, energy/challenge fit, focus patterns, and trends.
4. **AI** — Ask SuperFlow for universal text/voice control plus the prominent Blueprint Studio for multi-file, long-horizon intent compilation, running missions, and action history.
5. **Settings** — profile, reminders, data/account, detailed AI Engine/Blueprint controls, and Full Control activation/topology/orchestration.

The global **Ask SuperFlow** action remains reachable from every tab. On compact layouts, secondary profile and review destinations can live inside Settings while all five primary destinations remain accessible.

### Core object vocabulary

- **Identity:** a flexible statement about who the user is becoming.
- **Goal:** a desired outcome that supplies direction and a reason.
- **System:** a repeatable process supporting one or more goals.
- **Habit:** a scheduled or context-triggered action within a system.
- **Tiny Start:** the easiest valid beginning of a habit.
- **Minimum:** a useful reduced version for a low-capacity day.
- **Flow:** an ordered chain of anchored habits.
- **Daily Focus:** up to three linked actions that deserve emphasis today, not a general task backlog.
- **Obstacle Plan:** an if-then fallback for a likely schedule, context, energy, or urge barrier.
- **Blueprint:** a versioned, source-linked target design compiled from files, pasted text, instructions, and permitted current app state.
- **Requirement:** a normalized source intention tracked through accepted, conflicted, modified, implemented, verified, deferred, rejected, or Gap status.
- **Check-in:** what happened at a scheduled opportunity, including the action level used.
- **Review:** a structured reflection that changes the system.
- **Reset:** a recovery plan after disruption.

---

## 7. Key experiences

### 7.1 Onboarding: from aspiration to first action

Target: under five minutes, skippable, with no account wall.

1. Welcome and clear privacy promise.
2. Choose one life area: health, learning, relationships, work, creativity, finance, mindfulness, home, or custom.
3. Complete “I am becoming someone who…” in the user's own words.
4. Add one meaningful outcome and why it matters.
5. Convert it into a repeatable system.
6. Pick one habit and its Tiny Start.
7. Attach either a time-and-place cue or an existing-routine anchor.
8. Choose preparation, appeal, and an immediate healthy payoff.
9. Optionally schedule a reminder; ask Android notification permission only at this point and explain why.
10. Offer “Do the Tiny Start now.”

Name, age, email, AI consent, and cloud sync are not required to get value. Ask only for data tied to a visible feature.

### 7.2 Habit Designer

A guided wizard with five short sections:

1. **Meaning:** identity, goal, and personal reason.
2. **Notice:** time/place implementation intention or habit-stack anchor.
3. **Want:** immediate benefit, temptation bundle, supportive people, and positive reframe.
4. **Start:** Tiny Start, normal version, friction removal, environment preparation, and low-energy fallback.
5. **Feel:** completion signal, healthy reward, tracking style, and recovery plan.

Before saving, show a plain-language contract:

> After breakfast at home, I will put on my shoes. I can stop there on a hard day. My normal action is a 10-minute walk. Tonight I will leave my shoes by the door. Afterward I will listen to my favorite morning playlist and mark it done.

### 7.3 Today

- Greeting that does not require a name.
- Identity focus, dismissible.
- Timeline grouped by Morning / Day / Evening or by user-defined flows.
- Each card shows cue, Tiny Start, normal action, and check-in.
- Swipe/quick actions: Standard, Minimum, Tiny, Skip intentionally, Reschedule once.
- A miss never produces red failure theatrics.
- If the previous opportunity was missed, place a **Return today** card at the top.
- “Prepare tomorrow” appears at the appropriate anchor, not as clutter all day.
- A small progress cue celebrates action, not app usage.

### 7.4 Flow Builder

Users chain habits around reliable anchors:

```text
Wake up → drink water → open curtains → 1 minute of stretching
```

Rules:

- Each link can cue the next.
- Recommend no more than three new links at once.
- Existing stable behavior is visually distinct from a new behavior.
- Users can run a flow one step at a time from the Today screen.
- If one optional link is skipped, later links remain available.

### 7.5 Habit Scorecard

For one day, users list recurring actions from waking to sleep, then mark each as helpful, neutral, or unhelpful relative to a chosen identity. The tone is observation, not judgment. The result can be converted into:

- an anchor for a new habit,
- a cue to remove,
- an environment experiment,
- or an unwanted-habit plan.

### 7.6 Reduce an unwanted habit

1. Identify the behavior and the need it appears to serve.
2. Map cue, immediate perceived benefit, response, and payoff.
3. **Invisible:** remove or hide cues.
4. **Unattractive:** write the real near- and long-term costs; reframe the alternative.
5. **Difficult:** add steps, delays, blockers, distance, or a commitment device.
6. **Unsatisfying:** add a private accountability consequence.
7. Choose a safer replacement response for the same underlying need.
8. Define lapse recovery and external-help resources where appropriate.

The app tracks successful responses and recoveries, not only abstinence streaks.

### 7.7 Rescue and recovery

**Tiny Start Rescue** is available from every habit card. It should take one tap to begin or mark the tiny version.

After a miss:

- Acknowledge neutrally: “That opportunity passed.”
- Ask whether the plan, cue, capacity, or context caused friction.
- Protect the next occurrence with one action: shrink, move, prepare, remove a cue, or ask for support.
- Never send “You failed,” loss-framed streak messages, or manipulative urgency.

Planned rest, illness, travel, observances, and schedule pauses are not failures. The calendar supports pause ranges and “minimum mode.”

### 7.8 Review system

#### Weekly, 3–5 minutes

- What did I repeat?
- Which actions felt easier?
- Where did friction appear?
- Did the metric reflect what mattered?
- What is one small system adjustment?
- Should any habit shrink, stay, expand, move, or pause?

#### Monthly, 10 minutes

- Is each goal still meaningful?
- Does each system support it?
- Is challenge too easy, manageable, or overwhelming?
- Has automaticity hidden poor quality?
- What identity evidence stands out?

#### Quarterly or annual, 20–30 minutes

- What values and identities matter now?
- Which labels have become restrictive?
- Which systems should be maintained, graduated, or retired?
- What has changed in environment, health, responsibilities, or interests?

### 7.9 Insights without obsession

Show:

- Repetitions by week/month.
- Tiny, Minimum, Standard, and Stretch completions separately, with each appropriately acknowledged.
- Follow-through by cue/context.
- Recovery rate after a miss.
- Consistency range rather than only a longest streak.
- Self-reported effort and challenge fit.
- Identity evidence timeline.
- Goal outcome measurements only when the user chooses them.

Do not show:

- Global rankings.
- A universal “discipline score.”
- A fake “37× better” number.
- Predictions that claim certainty about when automaticity will occur.
- Shameful red calendars or lost identity points.

### 7.10 AI universal control and growth companion

The AI is optional, but when enabled it is a permissioned control plane for the whole app—not a generic advice chatbot. Anything meaningful a user can do manually must also be expressible through text or voice and executed through the same domain use cases. The manual application remains complete when AI is off, offline, or unconfigured.

#### Supported control jobs

- Create, inspect, edit, link, pause, archive, restore, and reorganize identities, goals, systems, habits, Tiny Starts, and flows.
- Check in at Tiny, Minimum, Standard, or Stretch level; skip intentionally, reschedule, add notes, and undo actions.
- Configure cues, reminders, quiet hours, environment experiments, reduce-habit plans, reviews, insights, appearance, accessibility preferences, export, sync, and other application settings.
- Run safe multi-step work in a cancellable background job, then report exactly what changed.
- Inspect and configure the AI Engine itself: provider profiles, models, routing, local coordinator, permissions, context, memory, automation, voice, budgets, and diagnostics.
- Prepare protected actions and open the correct Android screen when an OS prompt, secret entry, sign-in, biometric check, purchase, or health permission requires the user's direct interaction.

#### Supported coaching jobs

- Turn a vague goal into identity → system → habit → Tiny Start.
- Diagnose a struggling habit using Notice/Want/Start/Feel.
- Suggest environment and friction experiments.
- Guide weekly and monthly reviews.
- Create a return plan after disruption.
- Calibrate difficulty using the user's feedback.
- Summarize permitted notes and patterns.

#### Dual-control and autonomy rules

- Manual UI and AI tool calls invoke a shared command bus, validation, scheduling, audit, and undo layer.
- Full Control is the primary/default profile. Advice only, Confirm every change, Safe automatic, Custom autonomy, and temporary scoped sessions are optional preferences in the same app.
- Reversible low-risk local actions may run automatically under the user's policy. Bulk, external, destructive, security, financial, and high-risk health actions receive deterministic safeguards and confirmation.
- Every AI mutation records its origin, provider, affected objects, before/after summary, permission decision, and undo status in the AI Activity Center.
- The model proposes tools but never executes database/network operations directly, classifies its own risk, grants itself permission, or bypasses Android protections.

#### Two-engine design

- A **Local Coordinator Mini-AI** handles simple commands, intent routing, minimum-context selection, offline fallback, tool coordination, and cloud escalation. Rules-only mode works on every device; an optional compact local model adds flexible language understanding.
- An optional **Cloud Main Brain** handles complex planning and review through configurable managed, BYOK, supported provider, OpenAI-compatible, or self-hosted profiles.
- Provider routing can optimize for local-only privacy, quality, speed, cost, network, battery, language, or task capability.
- API keys use secure entry and Keystore-backed protection and are never readable by either model. Managed mode keeps provider credentials off the device.

#### Privacy and safety boundaries

- The Context Broker sends only permitted structured data and records a user-visible receipt for every cloud request.
- Context, memory, chat retention, background work, and provider budgets are independently configurable.
- AI never diagnoses, prescribes treatment, fabricates progress, silently expands its authority, or applies unsafe punishment.
- Sensitive topics use maintained locale-appropriate safety routing.
- Emergency AI off cancels queued AI mutations while preserving all manual data and functionality.

#### Response style

Calm, concise, specific, autonomy-supportive, and nonjudgmental. Prefer a verified action summary or one useful next experiment over a long motivational speech. Never claim the user “lacks discipline.”

The complete architecture, capability catalog, settings design, security policy, and delivery gates are defined in the **[AI Engine and Universal Control Plan](AI_ENGINE_PLAN.md)**.

### 7.11 Capacity-aware self-discipline system

SuperFlow adapts the suitable ideas from the supplied self-discipline guide into one system rather than adding a separate “discipline score.”

- **Plan Ahead / Do Now:** Plan Ahead supports evening planning, environment design, and review. Do Now strips the interface down to the next visible Tiny, Minimum, or Standard action.
- **Daily Focus:** select up to three linked priorities for today, with one Most Important action. One-off actions expire or are deliberately moved and never create an endless task backlog.
- **Plan Tomorrow:** a short evening ritual reviews progress, chooses tomorrow's focus, checks conflicts, prepares a visual anchor, and defines an Obstacle Plan.
- **Daily Checkpoints:** optional morning orientation, midday course correction, and evening reflection with independently configurable schedules, prompts, reminders, and AI mode.
- **Habit Ladder:** Tiny → Minimum → Standard → optional Stretch. Scaling uses repetitions, effort, capacity, and review—not time alone. A 25% growth ceiling may be offered only as a configurable conservative heuristic.
- **Energy Map:** optional private energy check-ins support explainable scheduling experiments with sample-size and uncertainty disclosure. The feature never diagnoses or changes protected medical routines.
- **Anchor Lab and Swap Plan:** physical/digital visual cues, preparation, cue replacement, friction, and safer substitute responses integrate with the four laws and inversions.
- **Early Support Window and Commitment Sprint:** temporary scaffolding may use the first selected number of opportunities or a ten-day template, but never claims automaticity by a fixed day.
- **Freshness and celebration:** optional values-aligned variation and milestones address boredom without destabilizing reliable cues or creating manipulative rewards.
- **Recovery Center:** separate compassionate protocols for a miss, low capacity/motivation, and return of an unwanted habit.
- **Focus Hour:** an advanced editable Flow template—Orient, Important Action, Restore—rather than a mandatory one-hour routine.

Every item is manually configurable and AI-controllable through the same command, policy, audit, and undo system. Full UX, data, capability, safety, scope, and test details are in the **[Self-Discipline Integration Plan](SELF_DISCIPLINE_INTEGRATION.md)**.

### 7.12 Blueprint Studio — flagship Intent Compiler

Blueprint Studio is a dedicated project system inside the AI tab for the app's most intensive work.

#### Inputs

- One or many Markdown, plain-text, or PDF files through Android's safe file picker.
- One or many named pasted-text sources.
- A main instruction prompt plus optional per-source instructions and precedence.
- Current SuperFlow state within explicit read scope.
- Privacy, provider, quality, budget, autonomy, background, and protected-object settings.

#### Durable pipeline

```text
Import → Parse/OCR → Source Health → Analyze → Requirement Ledger
→ Conflicts/Clarifications → Blueprint → Independent Critique
→ Simulation/Diff → Authorized Execution → Actual-State Verification
→ Bounded Repair → Handoff Report
```

- Missions persist as versioned task graphs and survive app closure, process death, reboot, provider outage, and network interruption.
- Long files use hierarchical analysis with direct page/line anchors; sources are never silently truncated.
- Requirements are normalized, deduplicated, cited, classified, and tracked through implementation and verification.
- Material source conflicts produce batched clarification cards; low-impact choices require visible rationale.
- User prompt and confirmed mission scope outrank source content, while safety/security policy remains immutable.
- Uploaded text can propose requirements but cannot grant permissions, expose secrets, or issue model/tool-control instructions.

#### Outputs and execution

- **Build My SuperFlow:** complete target state across identity, goals, systems, habits, levels, cues, schedules, focus, checkpoints, environment, recovery, insights, appearance, AI behavior, automations, privacy, and settings.
- **Audit and Improve:** source-to-current-state comparison without mandatory mutation.
- **Design Pack:** exportable product/program/app requirements, screens, data, AI behavior, roadmap, risks, and source citations where requests exceed SuperFlow's representable domain.
- **Full Build is the default:** Full Control uses best judgment, auto-resolves conflicts/assumptions, and applies all registered app-local bulk/destructive/settings changes after one activation—without another confirmation. Blueprint-only, Guided, Safe automatic, and Custom are optional preferences.
- Additive, merge, reorganize, replace-selected, and audit-only existing-data strategies.
- Source-linked target-state diff, grouped execution, protected-object exclusions, actual-state assertions, bounded repair, whole/batch undo, and Completed-with-Gaps honesty.

“Design the whole app” means comprehensively personalizing SuperFlow's supported workspace, module visibility, navigation preferences, automations, and appearance. v1 does not rewrite or install APK code; unsupported requests become visible Gaps or Design Pack artifacts.

The authoritative architecture, source ingestion rules, Requirement Ledger, orchestration, verification, data model, delivery, and 22-point definition of done are in the **[Blueprint Studio Plan](BLUEPRINT_STUDIO_PLAN.md)**. Full-control/no-repeated-confirm behavior is defined in the **[Full Control Plan](FULL_CONTROL_PLAN.md)**.

---

## 8. Functional scope and release priorities

### 8.1 MVP / private alpha — local-first core

Must have:

- Guest-first onboarding.
- Identity, goal, system, habit, and Tiny Start creation/edit/archive.
- Time, place, and anchor cues.
- Habit stacking and simple flow execution.
- Binary, count, duration, and reduce-habit tracking.
- Tiny/Minimum/Standard/Stretch/intentional-skip/missed check-ins.
- Today timeline with Plan Ahead / Do Now and up to three Daily Focus actions.
- Plan Tomorrow plus configurable morning, midday, and evening checkpoints.
- Obstacle Plans and Tiny/Minimum/Standard Habit Ladder.
- Minimum Mode with protected-routine exclusions.
- Local notifications with quiet hours, action buttons, and a total reminder budget.
- Habit Designer covering all four laws and inversions.
- Anchor Lab, environment/friction checklist, and Swap Plan.
- Recovery Center for miss, low-capacity, and unwanted-habit-return protocols.
- Weekly review.
- Basic repetition, consistency, and recovery insights.
- Local database, export, import, and delete-all-data.
- Shared command bus used by manual UI and AI tools, with actor attribution, validation, idempotency, audit, and undo.
- Versioned AI capability manifest and tool schemas for every MVP domain action.
- Rules-only Local Coordinator for common offline reads, check-ins, undo, simple creation/edits, and navigation.
- Full Control as the default AI profile for every implemented capability, with one activation, no repeated app-local confirmations, automatic snapshots, Activity, deterministic Stop, and grouped undo; Preview/Guided behavior remains optional.
- Blueprint Studio foundation: persistent project/version/source workspace; multiple Markdown/text/pasted-text and text-PDF inputs; main/per-source instructions; source health, page/line citations, Requirement Ledger, conflict/clarification, and Blueprint-only Markdown/JSON output.
- Source prompt-injection isolation, visible coverage, no silent truncation, explicit gaps/assumptions, and Blueprint data deletion/export.
- Accessibility, dark theme, offline operation, and crash reporting only with consent.

Should have:

- Habit Scorecard.
- Flow Builder.
- Calendar pause.
- Optional Energy Map observation with explainable patterns.
- Early Support Window and Commitment Sprint.
- Home-screen widget and visual cue card.
- Monthly review.
- Deterministic offline coach cards.

### 8.2 Public beta — universal AI control and continuity

- Optional account and end-to-end-conscious encrypted sync design.
- Configurable Local Coordinator model plus Cloud Main Brain through a secure managed proxy, BYOK adapter, and tested OpenAI-compatible provider profile.
- Provider router, Context Broker, strict tool registry, cancellable background jobs, cost limits, context receipts, AI history deletion, and prompt-injection defenses.
- Detailed AI Engine settings for providers, models, coordinator, routing, permissions, context, memory, automation, voice, budgets, activity, safety, and diagnostics.
- Text control for every beta operation and voice control for common commands, with automated manual/AI parity tests.
- Identity evidence ledger.
- Complete Tiny/Minimum/Standard/Stretch Habit Ladder and conservative scaling assistant.
- Energy Map schedule experiments with sample-size/uncertainty disclosure.
- Early Support, Commitment Sprint, Freshness, milestone, Starter Path, and Focus Hour templates.
- AI-drafted Daily Focus/checkpoints and background Plan Tomorrow under Full Control, with optional Preview/Guided behavior if selected.
- Blueprint Studio full Coverage Matrix, current-state audit, declarative target state, source-linked diff, Guided/Safe Full Build, existing-data strategies, grouped undo, actual-state verification, bounded repair, durable task graph, amendments/branching, and handoff report.
- Hardened PDF page extraction plus optional OCR; Maximum Quality independent critic route and stage-specific provider settings.
- Extend Full Control across every beta capability and complete no-question/auto-destructive Blueprint, self-hosted endpoints, optional unlimited product budgets, raw orchestration diagnostics, deterministic Stop, snapshots, verification, and undo.
- Shareable private accountability summary via Android share sheet.
- App links and deep links from reminders/widgets.
- English plus localization-ready copy and layouts; prioritize Bengali and Hindi after copy stabilization.

### 8.3 Version 1.0 — trustworthy release

- All required Atomic Habits and self-discipline suitability/coverage matrix rows implemented, tested, or explicitly documented.
- Monthly and quarterly review.
- Full unwanted-habit inversion flow.
- Play Store disclosures, privacy policy, terms, safety copy, and support route.
- Signed APK for direct installation and signed AAB for Play.
- Automated database migration and restore tests.
- Complete manual/AI capability parity: every meaningful v1 operation has a typed AI tool or documented protected-interaction handoff.
- Blueprint Studio's 22-point definition of done passes, including source coverage, high-priority requirement citations, mission resumability, verified execution, gaps, amendments, receipts, deletion, and whole/batch undo.
- Full Control's 16-point definition of done passes, including one-time activation, no repeated app-local confirmations, custom engines, no-question execution, automatic destructive snapshots, Stop, raw diagnostics, and actual-state verification.
- Background AI plans are configurable, cancellable, attributable, and undoable where technically possible.
- Independent security/privacy/document-parser/long-horizon-agent review, AI red-team evaluation, accessibility audit, and closed beta feedback fixes.
- Reliable reminder behavior across reboot, time-zone changes, daylight-saving changes, and battery restrictions.

### 8.4 Later, only after core quality

- Trusted accountability partner/circle service.
- Wear OS check-ins and complications.
- Health Connect integration with explicit per-data-type consent.
- More widgets and Quick Settings tile.
- Tablet/foldable adaptive layouts.
- Web companion.
- Curated habit templates reviewed for safety.
- Larger local main-brain models and additional hardware runtimes if quality, licensing, storage, and device support are sufficient.

### Explicit non-goals for v1

- Public social network, follower counts, or leaderboards.
- Advertising or sale of personal data.
- Unmoderated community advice.
- Financial custody or automatic monetary penalties.
- Medical programs or diagnostic claims.
- A general-purpose task/project backlog, universal “discipline score,” or productivity leaderboard.
- Claims that everyone quits on fixed days, becomes automatic by a fixed day, or can safely scale by a universal percentage.
- Blueprint Studio rewriting/installing APK binaries, executing document code/scripts, silently truncating sources, or pretending unsupported requirements were implemented.
- Arbitrary shell/SQL/filesystem/code-execution tools, hidden mutations, model-created Full Control grants, credential disclosure, cross-user access, or false bypass claims for Android/auth/payment/installer interfaces.

---

## 9. UX and visual direction

### Brand character

- **Name:** SuperFlow
- **Tagline:** Shape your system. Become your future self.
- **Voice:** clear, warm, grounded, brief, never preachy.
- **Visual metaphor:** a small stream becoming a steady river—consistent movement rather than explosive transformation.

### Interface principles

- Material 3 with an original SuperFlow theme.
- Calm neutral surfaces with one cool primary color and a restrained growth accent.
- Motion communicates continuity; respect “remove animations.”
- Completion animation under 500 ms, no random reward mechanics.
- One dominant action per screen.
- Progressive disclosure keeps behavioral theory out of the user's way.
- Empty states teach one action, not every feature.
- Copy uses “opportunity,” “return,” and “adjust” instead of “failure,” “broken,” and “discipline.”

### Accessibility

- WCAG-conscious contrast and no color-only meaning.
- Dynamic font support without clipped cards.
- TalkBack labels, logical focus order, and 48 dp minimum touch targets.
- Reduced motion and haptic controls.
- Plain language and localization-safe strings; no text embedded in images.
- 12/24-hour settings, locale-aware dates, week starts, and time zones.
- Keyboard support and adaptive layouts for tablets/foldables where practical.

---

## 10. Android technical architecture

### 10.1 Recommended stack

Use current stable versions at implementation time rather than freezing this plan to version numbers.

- **Language:** Kotlin.
- **UI:** Jetpack Compose + Material 3.
- **Architecture:** modular Clean Architecture with unidirectional data flow, ViewModels, and a command/query bus shared by manual and AI control surfaces.
- **Concurrency/state:** Kotlin Coroutines and Flow.
- **AI orchestration:** provider-neutral Local Coordinator, Context Broker, Tool Registry, deterministic Policy Engine, and background Agent Job Runner.
- **Blueprint orchestration:** persisted mission task graph, source/requirement/Blueprint versioning, stage checkpoints, simulation, grouped execution, actual-state verification, bounded repair, amendments, and branching.
- **Document ingestion:** Storage Access Framework plus pluggable bounded Markdown/text/PDF parsers and optional separately consented OCR; original/parsed/artifact lifecycles remain separate.
- **On-device inference:** pluggable runtime adapters selected after device/model evaluation; deterministic rules remain the universal fallback.
- **Dependency injection:** Hilt.
- **Persistence:** Room for domain data; DataStore for preferences.
- **Background work:** WorkManager for deferrable work; AlarmManager only when an exact user-facing reminder genuinely requires it and platform policy permits it.
- **Navigation:** Navigation Compose with typed routes where stable.
- **Networking:** Ktor Client or Retrofit behind repository interfaces.
- **Serialization:** Kotlinx Serialization.
- **Authentication:** optional standards-based sign-in; core app remains usable without it.
- **Backend:** an authenticated managed-AI proxy, durable Blueprint workflow/artifact service, and optional sync service; PostgreSQL-compatible structured storage plus encrypted object storage are preferred. BYOK and custom provider profiles remain separate client adapters.
- **Secret handling:** Android Keystore-backed credential aliases; secrets are excluded from Room, prompts, logs, analytics, exports, and support bundles.
- **Observability:** privacy-filtered crash and performance reporting plus redacted AI routing/tool diagnostics, disabled or consent-gated where legally required.
- **Quality:** Android Lint, Detekt, formatting checks, unit/UI tests, dependency scanning, parser/malformed-file corpus, requirement-grounding evaluations, long-horizon reliability tests, AI evaluations, and command/tool parity checks.

### 10.2 SDK and distribution policy

- Start with **minSdk 26** unless user research proves meaningful Android 7 support is necessary.
- Target the latest Android API required by Google Play at release time.
- Produce one complete product feature set as:
  - an internal debug APK for engineering/testing,
  - a signed release APK for direct installation,
  - and a release AAB for Google Play.
- APK and AAB include the same Full Control, Blueprint Studio, self-hosted/custom engine, raw orchestration, manual UI, and settings capabilities. There is no reduced edition or separately named release.
- Keep signing keys and passwords outside Git; inject them through a secure local/CI secret store.
- Use Play App Signing for Play distribution and maintain a documented key-recovery process.

### 10.3 Suggested repository structure

```text
SuperFlow/
├── app/                         # Application shell and root navigation
├── build-logic/                 # Convention plugins
├── core/
│   ├── common/                  # Result types, time abstractions, utilities
│   ├── database/                # Room entities, DAOs, migrations
│   ├── datastore/               # Preferences
│   ├── designsystem/            # Theme and reusable UI
│   ├── domain/                  # Commands, queries, models, use cases
│   ├── ai-coordinator/          # Local routing, intent, context, memory
│   ├── ai-providers/            # Main-brain provider adapters
│   ├── ai-tools/                # Capability schemas and tool registry
│   ├── ai-policy/               # Risk, permissions, confirmation, audit
│   ├── ai-runtime/              # Optional local model runtime adapters
│   ├── agent-jobs/              # Background plan execution and cancellation
│   ├── documents/               # Safe import, parsers, OCR adapters, source health
│   ├── blueprint/               # Ledger, target state, diff, assertions, versions
│   ├── mission-runtime/         # Durable task graph, checkpoints, resume, repair
│   ├── artifacts/               # Encrypted source/parsed/Blueprint blob references
│   ├── notifications/           # Scheduling and actions
│   ├── network/                 # API clients and auth interceptors
│   ├── security/                # Credential aliases and redaction
│   └── testing/                 # Fakes, fixtures, test clocks, parity harness
├── feature/
│   ├── onboarding/
│   ├── today/
│   ├── journey/
│   ├── habitdesigner/
│   ├── scorecard/
│   ├── insights/
│   ├── review/
│   ├── recovery/
│   ├── coach/
│   ├── blueprint-studio/        # Sources, Ledger, conflicts, diff, progress, report
│   ├── ai-activity/
│   └── settings/                # Includes detailed AI Engine control center
├── backend/                     # Managed AI proxy, durable Blueprint workflow/artifacts, optional sync
├── docs/
├── gradle/
├── .github/workflows/
└── README.md
```

Start with fewer Gradle modules if build overhead slows a solo engineer, but preserve these boundaries in packages. Extract modules when ownership, compile time, or testing justifies it.

### 10.4 Architectural flow

```text
Manual Compose UI ───────────────┐
                                 ├─→ Shared Command / Query Bus
AI text or voice                 │       ↓ validation + policy
  → Local Coordinator            │   Domain Use Cases
  → optional Cloud Main Brain    │       ↓
  → Tool Registry + Policy ──────┘   Repository Interfaces
                                      ├── Room / DataStore (source of truth)
                                      ├── Notification scheduler
                                      └── Optional remote API / sync queue
```

- UI never talks directly to a DAO or AI provider.
- Models never access repositories, filesystem paths, secrets, arbitrary network endpoints, or Android services directly. Blueprint sources reach models only as policy-filtered sections through Context Broker.
- Blueprint Studio compiles sources and instructions into a declarative target state, then existing narrow domain tools; it never receives an unrestricted `apply_anything` capability.
- Manual actions and AI tools create the same typed commands and receive the same validation, scheduling, audit, sync, and undo behavior.
- Deterministic policy code—not the model—assigns risk and decides whether a command may run automatically, needs confirmation, or is blocked.
- Domain time uses an injected clock and explicit `ZoneId`.
- The local database is the source of truth.
- Remote sync, when enabled, is queued and conflict-aware.
- AI plans can execute automatically only within the user's capability grants; protected, high-risk, destructive, or out-of-scope steps pause for confirmation.
- The machine-readable capability manifest and manual/AI parity tests are release gates.

See the **[AI Engine and Universal Control Plan](AI_ENGINE_PLAN.md)** for the component architecture and command/tool contract.

---

## 11. Domain and data model

All IDs should be UUIDs. Store timestamps as instants plus the schedule's time-zone context. Treat history as valuable: archive instead of destructive cascade where practical.

### Main entities

```text
UserProfile
- id, displayName?, locale, zoneId, weekStart
- aiEnabled, syncEnabled, createdAt, updatedAt

Identity
- id, statement, lifeArea, status, createdAt, archivedAt?

Goal
- id, identityId?, title, why, outcomeMetric?, targetValue?, targetDate?
- status [ACTIVE, MAINTAINING, ACHIEVED, PAUSED, CLOSED]

System
- id, goalId?, title, description, reviewCadence, status

Habit
- id, systemId?, title, mode [BUILD, REDUCE]
- trackingType [BINARY, COUNT, DURATION]
- desiredDirection, unit?, target?
- difficultyLevel, status, createdAt, archivedAt?

HabitCue
- id, habitId, type [TIME_PLACE, ANCHOR, CONTEXT]
- localTime?, place?, anchorHabitId?, anchorText?, context?

HabitDesign
- habitId
- immediateBenefit?, temptationBundle?, positiveReframe?
- environmentPreparation?, frictionRemoved?, immediateReward?
- cueRemoval?, negativeReframe?, frictionAdded?, consequence?
- lowEnergyFallback?, recoveryAction?

HabitLevel / ObstaclePlan
- habitId, level [TINY, MINIMUM, STANDARD, STRETCH], action, targetValue?, enabled
- ifDescription, obstacleType, thenAction, fallbackLevel?, status

DailyPlan / FocusAction
- localDate, zoneId, status, createdBy
- rank, sourceType/sourceId?, title, tinyFallback?, estimatedEffort?, status, expiresAt?

DailyCheckpoint / CheckpointEntry
- type [MORNING, MIDDAY, EVENING], schedule, promptMode, providerScope?, reminderPolicy
- localDate, answers, adjustments, createdAt

EnergyCheckIn / MinimumModeSession
- occurredAt, localTimeBlock, energyLevel, dayType?, note?
- startsAt, endsAt?, scope, protectedObjectIds, reason?, createdBy

VisualAnchor / SupportWindow / CommitmentSprint
- physical/digital cue and preparation status
- habit, days/opportunities, support rules, progress, review, status

SwapPlan / FreshnessOption / CelebrationPlan / RecoverySession
- trigger/need/replacement/friction/recovery
- optional variation, aligned milestone, and compassionate return steps

Schedule
- id, habitId, recurrenceRule, zoneId, startDate, endDate?
- quietHoursPolicy, reminderOffset?, enabled

Flow
- id, title, scheduleId?, status

FlowStep
- flowId, habitId, position, optional

HabitOpportunity
- id, habitId, scheduledFor, localDate, generatedFromScheduleVersion
- status [PENDING, COMPLETED, SKIPPED_PLANNED, MISSED]

CheckIn
- id, opportunityId?, habitId, occurredAt, localDate
- completionLevel [TINY, MINIMUM, STANDARD, STRETCH], value?, effort?, satisfaction?, note?, source

Review
- id, cadence [WEEKLY, MONTHLY, QUARTERLY]
- periodStart, periodEnd, answers, chosenAdjustment, createdAt

EnvironmentExperiment
- id, habitId?, action, hypothesis?, status, reviewAt?

AccountabilityPlan
- id, habitId, partnerAlias?, sharingMode, promise, consequence?

CoachConversation / CoachMessage
- id, timestamps, user-controlled retention, contextManifest

AgentPlan / AgentPlanStep / AgentJob
- intent, tool/version, risk, permission decision, arguments, status, command/undo IDs
- provider, background constraints, progress, cost estimate, context receipt

BlueprintProject / BlueprintVersion / SourceDocument / SourceSection
- persistent mission/version, instructions, source hashes/order/precedence, parser/OCR/coverage state

Requirement / Citation / Conflict / Clarification / Assumption / Gap
- normalized source intent, page/line provenance, dependencies, status, resolution, implementation and verification mapping

BlueprintArtifact / Assertion / MissionTask / ExecutionBatch / Finding / Snapshot
- declarative target state, durable task graph, diff/commands, post-state assertions, bounded repair, grouped undo

ProviderProfile / LocalModelPackage / RoutingRule
- non-secret provider/model configuration, capabilities, priority, health, budget, network policy
- credentialAlias references Keystore-protected storage; no secret value is stored in Room

CapabilityDefinition / CapabilityGrant
- tool schema version, deterministic risk, required access, object/batch/time/provider scope, expiry

ContextReceipt / AiMemory / UsageRecord / AuditEvent
- permitted data categories and counts, editable memory, token/cost metadata, before/after action summary
```

### Important domain rules

- Check-ins are idempotent and editable with an audit-friendly `updatedAt`.
- Tiny, Minimum, Standard, and Stretch completions remain distinguishable; Tiny counts as showing up, Minimum records a useful low-capacity version, and Stretch is never required.
- Daily Focus defaults to a maximum of three linked items and one-off items cannot silently form a backlog.
- Optional Preview/Guided modes exclude protected routines from generic Minimum Mode. Full Control may change any registered app-local routine without another prompt and records the source/override.
- Energy data is optional, private local data by default, and insights disclose sample size/uncertainty.
- Planned skips and pause dates do not create misses.
- A habit can have no clock time if it uses only an anchor or context.
- Streaks are derived, never authoritative stored state.
- Identity evidence is derived from check-ins linked through habits/systems; no points can be “lost.”
- Schedule edits do not rewrite historical opportunities.
- Date calculations are covered for reboot, time-zone travel, daylight-saving gaps/overlaps, locale week starts, and leap dates.
- Deleting an account must not be required to delete local-only data.
- AI commands are idempotent, version-aware, attributable, policy-checked, and linked to grouped undo where possible.
- Secrets are never domain data; only opaque credential aliases may be referenced.
- AI audit, chat, memory, context receipts, provider usage, and domain history have separate user-controlled retention/deletion policies.

The full specialized schemas are defined in the **[AI Engine Plan](AI_ENGINE_PLAN.md#13-data-model-additions)**, **[Self-Discipline Integration Plan](SELF_DISCIPLINE_INTEGRATION.md#6-data-model-additions)**, **[Blueprint Studio Plan](BLUEPRINT_STUDIO_PLAN.md#17-domain-model)**, and **[Full Control Plan](FULL_CONTROL_PLAN.md#9-data-model-additions)**.

---

## 12. Reminder and notification strategy

Notifications should support a cue without becoming noise.

- Ask permission in context, after the user chooses a reminder.
- Default to no more than one reminder per habit opportunity and enforce a configurable total reminder budget across habits, checkpoints, support windows, and AI-created automation.
- Support quiet hours, batching, snooze, pause, and per-habit/checkpoint disable.
- Notification actions are selected from **Standard**, **Minimum**, **Tiny**, and **Later** up to Android's supported action limit; opening Do Now exposes the complete set.
- Optional Plan Tomorrow and morning/midday/evening checkpoints use supportive orient/course-correct/reflect language.
- Minimum Mode suppresses Stretch and nonessential prompts.
- Notification copy states the cue and next action: “After lunch: open your notes.”
- Recovery copy: “Make returning easy: do the tiny version today.”
- Never use guilt, threatened data loss, fake messages, or marketing urgency.
- Reschedule after reboot, app update, time-zone change, and relevant permission changes.
- Explain battery optimization limitations rather than promising exact delivery on every OEM.

---

## 13. Privacy, security, and safety

### Privacy defaults

- Core use without registration.
- Data stored locally by default.
- Collect the minimum: no age, email, contacts, precise location, or health data unless a chosen feature requires it.
- Analytics events exclude goal names, habit names, Daily Focus content, energy/checkpoint records, journal text, chat content, and notification text.
- Separate consent for analytics, crash reports, sync, each cloud AI provider's context categories—including focus, energy, reviews, and accountability—Health Connect, and sharing.
- Every cloud AI request creates a context receipt listing purpose, provider, data categories, object counts, source block IDs where applicable, and redactions.
- Blueprint source originals, parsed text, embeddings, requirements, artifacts, cloud copies, and applied app state have separate visible retention/deletion controls.
- Imported files remain local-only by default in optional Preview/Guided modes. Full Control may establish one project/provider grant covering every selected source so no per-request context confirmation interrupts the mission.
- In-app export in a documented JSON format plus a human-readable CSV where applicable.
- Delete individual records, conversations, AI memory, retrieval index, context receipts, audit history subject to security retention, cloud copy, or all local data.
- Publish a retention schedule and subprocessors before beta.
- Never sell personal data or use private growth content for advertising.

### Security controls

- TLS for all network traffic and Network Security Config that blocks cleartext in release.
- Android Keystore-backed protection for cryptographic keys, tokens, and BYOK credentials; models receive only opaque credential aliases.
- Do not place private content, tokens, prompts, model responses, secrets, or sensitive tool arguments in production logs, analytics, crash reports, exports, or support bundles. User-visible conversation storage is separate and follows explicit retention settings.
- Backend authorization on every object; never trust user IDs supplied only by the client.
- Rate limiting, abuse protection, dependency scanning, secret scanning, and key rotation.
- Signed export/import format validation and safe size limits.
- Room migration tests and encrypted backup strategy; evaluate SQLCipher only after threat-modeling its cost and key lifecycle.
- Models receive registered typed tools. Deterministic code enforces schemas, object ownership, grant mode, idempotency, snapshots/undo, and truthful verification. Optional Preview/Guided modes apply limits/confirmations; Full Control auto-approves all registered app-local operations.
- Managed managed profiles may enforce model/rate/budget policies. Full Control supports custom/self-hosted/BYOK routing, optional unlimited product budgets, high resource limits, raw orchestration, and deterministic Stop.
- Prompt-injection tests treat notes, Blueprint sources, imports, retrieved memories, and provider output as untrusted data. Source requirements cannot grant tools or change policy.
- Markdown/PDF active content, embedded files, scripts, forms, macros, and links are never executed; parsers are bounded and isolated against malformed/decompression-heavy input.
- Managed Blueprint storage uses per-project authorization, encrypted object storage, tenant isolation, short-lived access, and auditable deletion.
- A documented incident response and user notification process before public launch.

### Optional Preview/Guided and Full Control behavior

- Optional Preview/Guided behavior may present educational boundaries, locale-appropriate support, and risk-based review for high-impact health, financial, legal, destructive, privacy, and external actions.
- Full Control does not impose repeated content-based or app-local risk confirmations. It may configure every registered app-local routine, setting, schedule, source-derived plan, and automation; warnings may be logged without pausing no-question execution.
- Credential/tenant/parser isolation, schema/database integrity, truthful verification, deterministic Stop, and Android/auth/payment/installer ownership remain technical boundaries.
- Users can disable AI entirely without installing a different app. Cloud/account age and distribution policy receive legal review.

---

## 14. AI service design

SuperFlow uses a dual-engine, tool-based agent architecture:

1. The **Local Coordinator Mini-AI** handles common commands, routing, minimum-context selection, offline operation, tool coordination, and escalation. It supports universal deterministic rules plus an optional compact local model.
2. The optional **Cloud Main Brain** handles complex planning and reflection through managed, BYOK, supported first-party, OpenAI-compatible, self-hosted, or capable local profiles.
3. The **Tool Registry** exposes narrow, versioned capabilities. Models have no arbitrary database, file, shell, Android service, or network access.
4. The deterministic **Policy Engine** validates schemas, calculates risk, applies capability grants, and requests confirmation when required.
5. The shared **Command Bus** executes the same use cases used by manual UI, records audit/undo, and verifies final state.
6. **Blueprint Studio** adds safe document ingestion, source indexing, Requirement Ledger, durable mission orchestration, declarative target-state compilation, simulation, verified execution, and amendments above the same narrow tools.

### Universal control contract

- Every meaningful manual action has a typed AI tool or a documented Android/security protected-interaction exception.
- Every AI action has a manual screen for inspection and correction.
- Text can address the entire capability catalog—including Daily Focus, checkpoints, Obstacle Plans, Habit Levels, Minimum Mode, Energy Map, anchors, Swap Plans, support/sprints, celebrations, Focus Hour, and recovery; common commands support push-to-talk voice.
- Blueprint projects can import multiple sources, maintain instructions/precedence, expose requirements/conflicts/gaps, compile and compare versions, execute authorized diffs, verify, amend, export, and undo through versioned capabilities.
- Optional Preview/Guided modes use Advice, Confirm, Safe Automatic, Custom, or Temporary Autopilot policies.
- Full Control uses one durable grant: all registered app-local bulk, destructive, settings, provider-routing, Blueprint, and background operations run without repeated confirmations, using automatic snapshots, verification, Activity, Stop, and undo.
- Credential values and Android/provider-owned auth, purchase, payment, installer, file-picker, and OS permission interactions remain direct technical handoffs.
- The app remains fully functional in Rules-only mode with cloud and local models disabled.

### AI Engine Settings contract

The primary Settings tab includes detailed sections for:

- AI overview and emergency off.
- Cloud Main-Brain provider profiles, endpoints, credentials, model selection, model parameters, tool capabilities, health tests, retry, and fallback.
- Local Coordinator engine mode, downloadable model, license/hash/version, hardware acceleration, memory/context limits, language, confidence, battery, thermal, and background constraints.
- Routing presets and per-task fallback chains.
- Per-domain read/write/delete/share permissions, optional Preview/Guided autonomy/limits/temporary grants, plus Full Control/no-question/auto-destructive activation.
- Context categories—including Daily Focus, energy, checkpoints, reviews, and accountability—redaction, cloud receipts, and provider privacy.
- Editable memory, local retrieval index, retention, export, and deletion.
- Background automations such as Plan Tomorrow and checkpoints, job queue, network/charging conditions, expiry, reminder budget, and notification policy.
- Proactive planning/recovery frequency, dismissal cooldown, Minimum Mode protections, and energy-suggestion controls.
- Voice recognition/synthesis, language, audio privacy, and lock-screen behavior.
- Per-provider/model budgets, usage, latency, quality, and caching.
- AI Activity with plans, tool calls, affected objects, policy decisions, cost, context receipts, undo, retry, and diagnostics.
- Blueprint Studio source processing/OCR, stage routes, critic, source precedence, ask-versus-assume/no-question, execution strategy, limits/resource-based mode, retention, background constraints, mission budgets/unlimited mode, repair controls, exports, and diagnostics.
- Self-hosted topology, custom endpoints/headers, role prompts, stage routes, raw task/prompt/retrieval/tool/verification diagnostics, deterministic Stop, and SuperFlow release APK status.
- Optional Preview/Guided coaching style, proactive suggestions, provider/model diagnostics, and support bundle.

### Provider and secret contract

- Managed mode calls a secure backend proxy so provider secrets remain server-side.
- BYOK and custom profiles store credential aliases with Android Keystore-backed protection.
- A model may check whether a credential exists or open secure entry; it can never read, repeat, export, or receive the value.
- Optional Preview/Guided modes use minimum allowed context and constrained fallback. Full Control projects may grant all selected sources/app categories to configured routes once; every request still creates a visible receipt.
- Optional Preview/Guided modes use budgets/action caps. Full Control may set product budgets and source/action limits to unlimited/resource-based while usage, watchdog, cancellation, and deterministic Stop remain.

### AI evaluation suite

Create anonymized synthetic test cases for:

- vague goals, compound commands, ambiguous object names, and voice transcription errors,
- mixed Markdown/text/PDF/pasted sources, malformed/scanned/oversized files, incomplete extraction, source conflicts, citation grounding, and high-priority requirement omission,
- durable Blueprint pause/cancel/resume, process death/reboot, amendments, concurrent app edits, simulation, grouped undo, actual-state verification, and bounded repair,
- source prompt injection, tool/policy instructions in files, cross-project leakage, secret detection, context receipts, and parser denial of service,
- overambitious plans, repeated misses, contradictory rewards, unwanted habits, low-capacity days, and protected routines,
- Daily Focus limits, Tiny/Minimum distinction, inadequate Energy Map samples, Obstacle Plans, Sprint end states, and fixed-day/percentage claim rejection,
- bulk edits, cancellation, retry, rollback, undo, stale plans, and process death,
- one-time Full Control activation, no-question conflict resolution, no-repeat-confirm destructive/settings execution, custom self-hosted routes, unlimited budgets, raw diagnostics, Stop, snapshot, and undo,
- manual/AI state parity for every command family,
- local/cloud routing, context minimization, provider fallback, rate limits, and budget stops,
- disability and constrained schedules, cultural and religious routines, and unsupported languages,
- prompt injection inside notes/imports, privilege escalation, secret exfiltration, and malicious provider output,
- requests for diagnosis, unsafe punishment, crisis content, purchases, account deletion, or protected Android actions,
- and AI requests to broaden its own permissions or modify security settings.

Score usefulness, tool accuracy, verified task completion, parity, framework coverage, humility, safety, privacy, and actionability. Critical security/safety cases and capability parity gates must pass before release, with human review of sampled outputs.

The authoritative universal-control specification is **[AI Engine and Universal Control Plan](AI_ENGINE_PLAN.md)**. The flagship document-driven long-horizon specification is the **[Blueprint Studio Plan](BLUEPRINT_STUDIO_PLAN.md)**. No-repeat-confirm/self-hosted behavior is defined in the **[Full Control Plan](FULL_CONTROL_PLAN.md)**.

---

## 15. Quality and test strategy

### Unit tests

- Schedule and opportunity generation.
- Tiny/Minimum/Standard/Stretch/skip/miss transitions and level history.
- Daily Focus cap/rank/expiry/carryover; checkpoint recurrence; Obstacle Plans; Minimum Mode scope/protected exclusions.
- Energy aggregation, sample thresholds, uncertainty, support/sprint day-versus-opportunity progress, and milestone derivation.
- Recovery-card eligibility and “next occurrence” logic.
- Streak/consistency/repetition derivation.
- Identity evidence derivation.
- Difficulty recommendations.
- Pause and Minimum Mode.
- Time zones, DST gaps/overlaps, leap day, locale week starts, and clock changes.
- Export/import validation.
- Tool schema validation/versioning, risk classification, capability scopes/expiry, context redaction, budget stops, command idempotency, grouped undo, and provider routing.
- Blueprint source hashes/precedence/coverage, Requirement status/conflict dependencies, target-state diff, protected-object assertions, task graph scheduling, checkpoint/resume, bounded repair, amendments, and version migration.

### Persistence and integration tests

- Every Room migration from each released schema.
- Process death and state restoration.
- Reboot notification rescheduling.
- Offline-first writes and later sync.
- Conflict resolution across devices.
- AI background job process death, retry, cancellation, rollback, stale-plan conflict, and verified final-state handling.
- Blueprint source/artifact lifecycle, durable mission task graph across process death/reboot, concurrent app edits, cloud completion while offline, idempotent execution, grouped snapshot/undo, and deletion separation.
- Local-to-cloud fallback without permission/context expansion.
- Account sign-out without accidental local deletion.
- Delete-account and delete-local-data behavior.

### UI tests

- First-run to first Tiny completion.
- Goal → system → habit creation.
- Four-stage Habit Designer.
- Plan Tomorrow → Daily Focus → morning/midday checkpoint → Do Now.
- Habit Ladder, Obstacle Plan, Minimum Mode, Energy Map opt-in, Anchor/Swap Plan, and Support/Sprint review.
- Today level-aware check-in and undo.
- Miss/low capacity/unwanted-habit return → Recovery Center → next Tiny or Minimum completion.
- Reduce-habit setup.
- Weekly review adjustment.
- Complete equivalent flows with AI, compare domain/audit/notification state, inspect Activity, and undo.
- Configure Main Brain, Local Coordinator, routing, capabilities, context, memory, automations, voice, budget, and emergency off.
- Create Blueprint from multiple Markdown/text/PDF/pasted sources; inspect source health/citations; answer conflicts; review assumptions/gaps; preview/apply verified diff; pause/resume; amend; export; and undo whole mission.
- Export/delete, including separate Blueprint source, artifact, mission, cloud, and applied-state choices.
- Notification-permission denial and later enablement; AI may open but cannot bypass the protected OS flow.

### Nonfunctional tests

- TalkBack and large-font audit.
- Color contrast and reduced motion.
- Baseline profiles, startup, jank, memory, and battery impact.
- Airplane mode and poor network behavior.
- Android versions and representative low-/mid-range devices.
- Local-model storage, memory, latency, thermal, battery, and unsupported-hardware fallback.
- Markdown/text/PDF/OCR malformed-file, mixed-language, large-project, memory/decompression, extraction-coverage, and citation-grounding corpus.
- Blueprint long-horizon kill/reboot/outage/rate-limit/budget/credential/schema/amendment/branch/verification/undo reliability matrix.
- Full Control activation/revocation, no-question, no-confirm bulk/destructive/settings/provider, self-hosted topology, unlimited-budget reporting, raw diagnostics, Stop, snapshot, verification, and undo matrix.
- Integrity review, API abuse, source prompt-injection, parser abuse, cross-project leakage, credential isolation, malicious-provider, and runaway-resource watchdog tests.
- AI safety, tool accuracy, capability parity, and regression evaluation.

### Release quality gates

- No critical/high unresolved security defects.
- No known data-loss migration defects.
- Crash-free target defined and monitored during beta.
- Reminder reliability measured on the supported device matrix.
- All required 47-point Atomic Habits and 30-point self-discipline integration rows are traced to tests, UX acceptance checks, or documented suitability decisions.
- Unsupported fixed-day, automaticity, novelty-cadence, and universal growth-percentage claims are absent from product/AI/marketing copy.
- Every meaningful manual command is mapped to a versioned AI capability or documented protected-interaction exception, and parity tests pass.
- Blueprint Studio passes all 22 dedicated completion gates; required source coverage, high-priority requirement grounding, actual-state verification, and no-false-completion checks are release blockers.
- Full Control passes all 16 dedicated completion gates, including no repeated app-local confirmations and deterministic integrity/Stop behavior.
- No credentials appear in model context, logs, Room, analytics, exports, or support bundles; Full Control project-wide source context remains covered by the initial grant and receipts.
- AI jobs honor permission, context, cost, background, cancellation, audit, and emergency-off gates.
- Privacy disclosures match actual SDK, provider, model, and network behavior.
- Accessibility critical paths pass.

---

## 16. Delivery roadmap

The schedule below assumes one experienced Android engineer with part-time product/design support. A solo beginner should expect a longer timeline. Quality gates, not dates, determine release.

### Phase 0 — Product validation (1–2 weeks)

- Interview 8–12 target users about failed routines, privacy, reminders, and recovery.
- Validate vocabulary: identity, system, Tiny, Minimum, Standard, Daily Focus, Obstacle Plan, Flow, and review.
- Prototype onboarding, Plan Ahead/Do Now, Daily Focus, checkpoints, Habit Designer, recovery, conversational creation, AI action preview/undo, and the AI Settings information architecture.
- Prototype Blueprint Studio multi-file intake, instructions, source health, Requirement Ledger/conflicts, coverage, diff, progress, verification, and undo; threat-model malicious/confidential sources and destructive merges.
- Test whether setup completes in under five minutes and whether users understand manual/AI parity, Blueprint scope, source routing, optional Preview/Guided autonomy versus one-time Full Control, automatic destructive snapshots, Stop, and undo.
- Finalize v1 scope, capability catalog, Blueprint source limits/parsers, protected-interaction exceptions, and safety boundaries.

**Exit:** users can explain the product and complete the prototype without coaching.

### Phase 1 — Android foundation (2 weeks)

- Initialize one full-feature Kotlin/Compose application target with internal debug and signed release packaging.
- Add CI, formatting, linting, dependency injection, navigation, Room, DataStore, and test clock.
- Implement design system, themes, accessibility foundations, and privacy-safe logging.
- Define domain models and database schema.
- Implement the shared command/query bus, actor attribution, idempotency, audit, undo contract, capability manifest, tool-schema versioning, and parity-test harness before feature screens diverge.
- Establish Blueprint project/version/source/Requirement/task/artifact schemas, encrypted blob references, safe Storage Access Framework import, and bounded Markdown/text parser interfaces.

**Exit:** debug APK builds in CI, a smoke test runs, the same sample command succeeds through manual and test-tool paths, and a source snapshot/parse survives process recreation.

### Phase 2 — Local growth core (3–4 weeks)

- Identity, goal, system, habit, schedules, and check-ins.
- Onboarding and Journey management.
- Today timeline, Daily Focus, Plan Tomorrow/checkpoints, and Tiny/Minimum/Standard/skip actions.
- Basic Obstacle Plans, Habit Levels, Minimum Mode with protected exclusions, and Recovery Center.
- Opportunity generation, reminder budget, and local reminders.
- Export/delete foundations.
- Typed AI read/write tools for every Phase 2 operation, Rules-only Local Coordinator, default Full Control grant, automatic snapshots, AI Activity, background job skeleton, and deterministic Stop.
- Blueprint-only MVP: multiple Markdown/text/pasted/text-PDF sources, source health/page-line anchors, instructions/precedence, source analysis, Requirement Ledger, conflicts/clarifications, assumptions/gaps, and Blueprint Markdown/JSON export without app mutation.

**Exit:** a user can operate one habit for a week completely offline, entirely manually or with common rules-based AI commands; a mixed-source Blueprint fixture is fully traceable or honestly reports failed coverage.

### Phase 3 — Comprehensive behavior design (3 weeks)

- Habit Designer: Notice, Want, Start, Feel.
- Habit stacks and Flow Builder.
- Habit Scorecard, Anchor Lab, visual cues, and environment experiments.
- Reduce mode, four inversions, and Swap Plan.
- Rescue, recovery, pause, Early Support Window, and Commitment Sprint.
- Extend the capability catalog and parity suite with every new operation; add compound conversational plans and grouped undo.
- Extend Blueprint Coverage Matrix and declarative target-state compiler across every implemented behavior domain; add current-state audit, source-linked preview, and Design Pack mode.

**Exit:** alpha covers the four laws for build and reduce modes through both manual and AI control, and Blueprint designs every implemented domain without unsupported mutation.

### Phase 4 — Feedback and adaptation (2–3 weeks)

- Insights, identity evidence, level-aware completion, Daily Focus, and recovery metrics.
- Weekly/monthly reviews and checkpoint learning.
- Optional Energy Map with coverage/uncertainty and explainable schedule experiments.
- Habit Ladder scaling, manageable-difficulty calibration, Freshness Options, and meaningful celebrations.
- Starter Path and editable Focus Hour template.
- Metric-quality check and identity flexibility.
- Widget/cue card if stability allows.
- Add Blueprint simulation, existing-data strategies, execution batches through narrow tools, protected scope, grouped snapshot/undo, actual-state assertions, bounded repair, and handoff report.

**Exit:** every habit can be reviewed and systematically adjusted; supported Blueprint fixtures execute to verified state with manual equivalence and reversible app-local changes.

### Phase 5 — Full AI Engine and optional account (4–6 weeks)

- Add the optional compact Local Coordinator runtime, signed model manager, hardware benchmark, local context/retrieval controls, and deterministic fallback.
- Add the Cloud Main Brain managed proxy, one thoroughly tested provider adapter, OpenAI-compatible/BYOK profiles, strict tool calling, routing/fallback, and context receipts.
- Complete detailed AI/Blueprint/Full Control settings for providers, coordinator, routing, capabilities, context, memory, automation, voice, budgets/unlimited mode, activity/raw diagnostics, Blueprint source/orchestration, and self-hosted topology.
- Complete Full Control across all v1 capabilities: no-question/no-repeat-confirm bulk/destructive/settings/provider execution, resource-based limits, automatic snapshots, deterministic Stop, and grouped undo.
- Add Blueprint durable task graph, checkpoints, parallel source analysis, managed/self-hosted workflow/artifacts, pause/cancel/resume, cost/context receipts, incremental amendments, branching, stage-specific routes, and independent critics.
- Add push-to-talk common commands, cancellable background plans, prompt-injection defenses, safety routing, usage controls, deletion, and the complete evaluation suite.
- Optional authentication/sync only if local-core reliability is already strong.

**Exit:** every current app operation has manual/AI parity or a documented external handoff; both AI engines can be independently configured or disabled; Full Control, credential/tenant/parser integrity, truthful verification, Stop, undo, and usage-reporting gates pass.

### Phase 6 — Beta hardening and release (2–4 weeks)

- Closed testing on diverse Android/OEM devices.
- Accessibility, performance, battery, credential/tenant/parser integrity, Full Control, document-parser/OCR, long-horizon execution, and provider-failure audits.
- Harden scanned/mixed/encrypted/malformed/large PDF handling and source viewer citations.
- Verify the capability manifest, manual/AI parity, Blueprint 22-point gate, requirement grounding, Full Control auto-approval, credential isolation, context receipts, background cancellation, unlimited-usage reporting, deterministic Stop, and undo.
- Migration, backup, and restore drills.
- Store listing, screenshots, policy disclosures, support docs, and legal review.
- Release signed APK and Play AAB in staged rollout.

**Exit:** v1 release gates pass and rollback/support plans exist.

---

## 17. Product metrics and learning plan

### North-star behavior

**Weekly supported growth:** the number/percentage of active users who complete meaningful planned actions on at least three distinct days and either recover after a miss or perform a review.

This is a learning metric, not a score shown to users.

### Activation funnel

- Started onboarding.
- Created or selected an identity.
- Created a system-backed habit.
- Defined a Tiny Start and cue.
- Performed first check-in, ideally from action rather than merely setup.
- Returned for the next planned opportunity.

### Healthy outcome metrics

- Week 1 and week 4 retained builders.
- Median useful repetitions per active habit.
- Recovery at the next opportunity after a miss and appropriate use of Tiny/Minimum without treating them as failure.
- Percentage of habits adjusted rather than abandoned after friction.
- Daily Focus usage, intentional release, and carryover patterns without creating a public productivity score.
- Checkpoint/reminder usefulness, Energy Map coverage, and whether schedule experiments are accepted or reverted—without collecting private content.
- Review completion and resulting system change.
- Reminder disable rate and notification complaint rate.
- AI task completion, clarification, confirmation, cancellation, undo, blocked-action, and manual-correction rates without collecting private content.
- Manual/AI parity failures, provider/router failures, background-job completion, local-model fallback, and budget-stop reliability.
- Blueprint required-source coverage, high-priority requirement verification, citation correctness, conflict/gap resolution, execution/verification, resume, undo/amendment, and user-rated intention alignment.
- Export/delete success and support incidents.
- Crash-free sessions, ANRs, startup, battery, and reminder reliability.

### Guardrail metrics

- Excessive combined notification/checkpoint/AI reminder volume.
- Users maintaining too many simultaneous starter habits or Daily Focus items.
- Daily planning pressure, Energy Map anxiety, or confusion between Tiny and Minimum reported in research.
- Any optional-mode protected-routine violation, any Full Control mutation missing its grant/audit, or unsupported fixed-day/percentage claim.
- Streak anxiety or guilt reported in research.
- Harmful or overconfident AI output.
- Unauthorized, surprising, untraceable, or non-undoable AI changes.
- Prompt injection, permission escalation, secret exposure, context oversharing, and runaway model cost.
- Blueprint high-priority omission, false completion, wrong citation, silent source truncation, unintended merge/destruction, parser failure/abuse, cross-project leakage, and non-resumable mission.
- Accidental private-content telemetry.
- Account/sync data loss or conflicts.
- Accessibility regressions.

Avoid optimizing raw check-ins, time in app, chat length, or streak length in isolation. The best session may last ten seconds because the user took action in real life.

---

## 18. Business model recommendation

A sustainable model must align with calm behavior change.

### Free core

- Local identities, goals, systems, and habits.
- Daily Focus, Tiny/Minimum actions, Plan Tomorrow/checkpoints, Today, reminders, Recovery Center, and core insights.
- Export and deletion.
- No ads and no sale of data.

### Optional paid tier

- Higher AI allowance.
- Encrypted multi-device sync when mature.
- Advanced reviews and pattern summaries.
- Extra widgets, themes, and carefully reviewed programs.
- Family/accountability features only with explicit consent.

Never paywall data export, delete, lapse recovery, safety help, or basic privacy controls. Validate willingness to pay before final pricing. Offer a clear subscription state, easy cancellation, and no countdown pressure.

---

## 19. Risks and mitigations

| Risk | Mitigation |
|---|---|
| Product becomes another streak tracker | Make identity, system design, environment, recovery, and review first-class release requirements. |
| Setup is too theoretical | Progressive disclosure, templates, and an immediate Tiny Start; limit initial setup to one habit. |
| Too many goals overwhelm users | Recommend one starter identity/system and cap simultaneous “new” habits with an override. |
| Daily Focus turns into a task manager or pressure score | Default to three linked actions, expire/move one-offs deliberately, omit backlog/project machinery, and never create a universal productivity score. |
| Checkpoints and Early Support become nagging | Independent opt-outs, quiet hours, total reminder budget, dismissal cooldowns, and research on perceived pressure. |
| Energy insights create false certainty or health anxiety | Optional local-first check-ins, minimum sample/coverage display, correlation language, easy deletion, and no diagnosis. |
| Scaling/critical-day heuristics are treated as facts | Label 10 days/25%/early windows as editable templates, use opportunities and personal evidence, and prohibit automaticity guarantees. |
| Minimum Mode weakens a routine unexpectedly | Optional Preview/Guided mode protects selected routines. Full Control may change them, but Activity/source provenance and grouped undo make the override visible/reversible. |
| Notifications become the only cue | Encourage physical/environmental cues and habit stacks; reminders are optional scaffolding. |
| Users feel shame after misses | Neutral states, planned pauses, recovery-first copy, and no lost identity score. |
| AI gives generic or unsafe guidance | Structured jobs, minimum permitted context, deterministic alternatives, safety routing, evaluation, and risk-based confirmation. |
| AI makes an unauthorized or surprising change | One explicit Full Control grant, shared command bus, automatic snapshot, attribution, actual-state verification, deterministic Stop, and grouped undo; optional Preview/Guided behavior remains user-selectable. |
| New screens break manual/AI parity | Capability manifest and CI gate require a manual path, typed AI tool/exception, policy metadata, and parity test for every domain command. |
| Prompt injection or malicious provider escalates access | Treat all content/model output as untrusted, allow only strict typed tools, and enforce permissions entirely outside models. |
| Provider keys or private context leak | Secure secret entry, opaque Keystore-backed aliases, per-provider context scopes/receipts, redacted diagnostics, and automated leak tests. |
| Background AI runs away or creates high costs | Optional Preview/Guided modes use caps. Full Control unlimited mode still exposes live usage, provider constraints, resource watchdog, pause/cancel, deterministic Stop, checkpoints, and resumable state. |
| Local model harms performance or excludes devices | Rules-only baseline, optional signed model packages, device benchmark, hardware/runtime fallback, and storage/battery controls. |
| Detailed AI settings overwhelm ordinary users | Simple overview and safe presets first; progressively disclose provider, routing, model, and diagnostic controls. |
| Blueprint silently misses or invents a requirement | Required section coverage, citation-grounded Ledger, high-priority omission tests, assumption labels, independent critic, and Completed-with-Gaps status. |
| Conflicting sources produce a wrong whole-app design | User-defined precedence, typed conflicts, batched clarification, rationale, branching, preview, and protected existing-data strategies. |
| Document prompt injection controls the app | Treat source text as data/requirements only; source content cannot grant permissions or enter tool-control channels. |
| PDF/parser attacks or exhausts the device | MIME sniffing, parser isolation, strict page/size/time/memory/decompression limits, malformed corpus, and remote kill switch for vulnerable adapters. |
| Long mission loses progress or repeats changes | Persisted task graph/checkpoints, hashes, idempotency, object versions, reboot/process/outage tests, and actual-state reconciliation. |
| Blueprint applies a broad destructive merge | Stable-ID diff/simulation, object versions, automatic pre-snapshot, actual-state verification, deterministic Stop, grouped undo, and no title-only destructive matching. Full Control does not add a confirmation interruption. |
| Source/confidential content leaks | Local-first originals, per-provider source scopes, encrypted artifacts, context receipts, tenant isolation, separate deletion, and content-free analytics. |
| User expects arbitrary APK generation | Clearly separate Build My SuperFlow from Design Pack and state that v1 personalizes supported state rather than rewriting/installing executable code. |
| Private content leaks through analytics/logs | Content-free event schema, automated log tests, consent controls, and vendor audit. |
| Android reminder inconsistency | Correct platform APIs, reboot/time-zone handling, OEM guidance, and transparent expectations. |
| Sync causes data loss | Local source of truth, append-aware check-ins, tombstones/versioning, conflict tests, staged rollout. |
| Copyright/trademark confusion | Original copy/design, no book text corpus in product, no endorsement claim, attribution and legal review. |
| Gamification undermines intrinsic motivation | Subtle feedback, user-controlled streak visibility, no loot/leaderboards, and identity-aligned rewards. |
| Metrics distort the real behavior | Metric-quality questions in reviews and no universal score. |

---

## 20. Definition of done for v1

SuperFlow v1 is done only when:

1. A new user can install the signed APK and perform a Tiny Start without an account or network.
2. Every goal can be traced to an identity, system, and actionable habit.
3. Good-habit and unwanted-habit flows implement all four laws/inversions.
4. A miss produces a useful return plan, never a punishment.
5. Repetitions, recovery, and reviews work across calendar/time-zone edge cases.
6. Every required row in the 47-point Atomic Habits matrix and 30-point self-discipline integration matrix has an implementation, acceptance test, or documented suitability decision.
7. Plan Ahead produces a clear Do Now experience; Daily Focus defaults to three linked items and cannot become an invisible task backlog or discipline score.
8. Tiny, Minimum, Standard, and Stretch remain distinct; optional Preview/Guided mode protections and Full Control overrides are correct; Obstacle Plans, Anchor/Swap Plans, and Recovery Center work together.
9. Energy Map is optional and local-first, discloses sample size/uncertainty, and makes no diagnosis. Optional Preview/Guided mode preserves protected routines; Full Control may change them and logs the override.
10. Fixed-day, ten-day, novelty-cadence, and 25% scaling concepts are editable heuristics/templates rather than marketing or coaching guarantees.
11. Blueprint Studio imports multiple Markdown, text, pasted-text, and supported PDF sources safely; page/line health proves coverage and never hides extraction/OCR failure or truncation.
12. Every accepted high-priority Blueprint requirement has correct provenance, status, implementation mapping, and verification; conflicts, assumptions, optional-mode modifications, and Gaps remain explicit.
13. Build My SuperFlow evaluates the complete Blueprint Coverage Matrix; Audit, Design Pack, Blueprint-only, Guided, Safe Full Build, Full Build, and existing-data strategies behave as documented.
14. Blueprint missions are durable, usage-tracked, cancellable, resumable, versioned, amendable, source/context-receipted, actually verified, configurable in repair limits, and whole/batch undoable.
15. Uploaded sources cannot grant tools or broaden permissions; source originals, parsed text, embeddings, artifacts, cloud copies, app state, audit, and undo have secure separate lifecycles.
16. Every meaningful manual operation has a versioned typed AI capability or documented Android/security protected-interaction handoff.
17. Manual and AI paths use the same command/use-case layer and pass equivalent state, validation, schedule, audit, sync, and undo tests.
18. Optional Preview/Guided modes apply configured confirmations; Full Control executes every registered app-local bulk/destructive/settings/Blueprint action without another prompt, while external credentials/auth/payment/installer/OS interfaces remain technical handoffs.
19. Local Coordinator, Cloud Main Brain, and self-hosted topology can be independently configured; Settings covers providers, custom endpoints, models, routing, permissions/Full Control, context, memory, automation, voice, budgets/unlimited mode, Activity/raw diagnostics, Stop, and Blueprint Studio.
20. The application remains fully usable with AI, network, account, Energy Map, Blueprint Studio, and all model runtimes disabled; Rules-only mode still handles common local commands.
21. Every AI mutation is attributable and inspectable, with undo or a clear irreversible warning, and deterministic Stop AI reliably blocks new mutations without model cooperation.
22. Credential values never appear in model context, Room, logs, analytics, exports, or support bundles; cloud/self-hosted requests create accurate context receipts.
23. AI control passes tool-accuracy, fixed-claim, source-grounding, parser, source injection, credential isolation, Full Control, no-repeat-confirm, provider-failure, unlimited-usage reporting, Stop, cancellation, process-death, integrity, and parity tests.
24. Export and full deletion work and have automated tests.
25. The app passes accessibility, migration, performance, battery, credential/tenant/parser isolation, document-parser, Full Control, and long-horizon reliability gates.
26. CI produces test reports and release candidates; release signing secrets are not in Git.
27. The signed SuperFlow APK and Play AAB expose the same complete product capabilities and data model.
28. Store/distribution claims are accurate, modest, and supported by implemented behavior.
29. Full Control passes its dedicated 16-point definition of done.

---

## 21. First implementation backlog

Execute in this order after plan approval:

1. Write product vocabulary—including Blueprint, Requirement, Gap, Tiny versus Minimum—manual/AI parity, source-grounding, optional Preview/Guided autonomy, and Full Control behavior.
2. Prototype onboarding, habit features, conversational control, Blueprint Studio, and Full Control activation/no-question/no-confirm/self-hosted/raw-diagnostics/Stop/undo flows.
3. Test manual, conversational, and multi-source Blueprint prototypes with target users; validate trust, cloud exposure, conflict handling, and app-personalization versus APK-generation expectations.
4. Record architecture decisions for domain/source/artifact storage, parsers/OCR, durable mission graph, command bus/tools, managed/self-hosted routes, one product build with Full Control, and optional sync.
5. Bootstrap the single full-feature Kotlin/Compose app and CI with parser, grounding, parity, Full Control, no-confirm, and long-horizon harnesses.
6. Implement domain, Blueprint, FullControlGrant/topology/execution entities, shared command/query bus, actors, idempotency, audit, automatic snapshots/undo, and parity harness.
7. Implement versioned capability/tool registry, deterministic risk/policy engine, Context Broker, Rules-only Local Coordinator, and source-content isolation.
8. Build safe multi-file Markdown/text/pasted/text-PDF source workspace, health reports, page/line anchors, instructions/precedence, secure retention/export/delete, and Blueprint-only UI.
9. Build source analysis, Requirement Ledger, conflicts/clarifications, assumptions/Gaps, Coverage Matrix, target-state Blueprint, citations, Design Pack, and grounding evaluations.
10. Implement schedule/opportunity/level-aware check-ins, Daily Focus/checkpoints, Minimum Mode, reminder budget, identity → goal → system → Habit Ladder, and Today through both control surfaces.
11. Add Habit Designer, Obstacle Plans, Anchor Lab, Flow Builder, Reduce/Swap Plan, support/sprints, Recovery Center, Energy Map, reviews, insights, Freshness, and milestones with capability parity.
12. Compile Blueprint targets to narrow domain tools; implement simulation/diff, existing-data strategies, Guided/Safe/Full Build, no-question auto-conflict, no-confirm destructive batches, actual-state verification, repair, Stop, and grouped undo.
13. Conduct private alpha in manual-only, Rules-only, Blueprint-only, Guided, Safe Full, and Full Control modes using mixed-source destructive fixtures.
14. Add optional compact Local Coordinator, Context Broker retrieval, embeddings, model manager, stage-specific routes, independent critics, and source-grounding evaluations.
15. Add managed/self-hosted durable Blueprint workflow, BYOK/custom profiles, parallel graph, pause/cancel/resume, process/reboot/outage recovery, receipts, unlimited-budget option, amendments, and branching.
16. Finish the SuperFlow release APK/AAB, custom endpoints/role prompts/stage routes, resource-based limits, raw diagnostics, PDF/OCR, voice, Settings, parser/integrity tests, and full manual/AI/Blueprint/Full Control audit.
17. Conduct closed beta and release only after the Grand, Blueprint Studio, Full Control, AI Engine, and Self-Discipline definitions of done pass.

---

## 22. Research and attribution notes

The behavioral framework in this plan is based on ideas described in James Clear's *Atomic Habits*. Product and engineering teams should read the source material rather than use this plan as a replacement for the book. Useful official references include:

- [Official Atomic Habits summary](https://jamesclear.com/atomic-habits-summary)
- [How to start new habits that stick](https://jamesclear.com/three-steps-habit-change)
- [Habit stacking](https://jamesclear.com/habit-stacking)
- [The Two-Minute Rule](https://jamesclear.com/how-to-stop-procrastinating)
- [Habit tracking and recovery](https://jamesclear.com/habit-tracker)
- [The Goldilocks Rule](https://jamesclear.com/goldilocks-rule)

The user-provided *Complete Guide to Building Lasting Self-Discipline* supplied additional product ideas. They are adapted in the **[Self-Discipline Integration Plan](SELF_DISCIPLINE_INTEGRATION.md)**. Fixed day ranges, a 25% increase, five-day novelty, and automaticity timing are treated as optional heuristics/templates rather than universal facts.

Before commercial release, obtain legal review of branding, educational copy, attribution, privacy terms, health/safety claims, age policy, AI provider terms, and Play policies. SuperFlow should acknowledge inspiration where appropriate while remaining an independent product with original content.
