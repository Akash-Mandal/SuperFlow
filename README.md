# SuperFlow

**Shape your system. Become your future self, one small action at a time.**

SuperFlow is a planned native Android personal-growth app for turning meaningful goals into identity-aligned systems and sustainable daily habits. It will combine calm goal planning, tiny actions, habit and environment design, compassionate recovery, reflection, responsible progress insights, and a deeply integrated but optional AI control system.

The app has **two equal control surfaces**:

- Users can operate every feature manually through complete screens and settings.
- Users can tell the AI what they want by text or voice and let Full Control complete the entire supported job—including multi-step and destructive app-local work in the background—without repeated confirmations.

Both paths use the same application commands, validation, schedules, audit trail, and undo behavior. The app remains fully usable with AI, cloud access, and local models disabled.

The AI architecture combines a configurable **Cloud Main Brain** with SuperFlow's **Local Coordinator Mini-AI**. A detailed AI Engine settings area covers providers, API profiles, models, local runtime, routing and fallback, autonomy and capability permissions, context, memory, background automation, voice, budgets, activity history, privacy, safety, and diagnostics.

The flagship AI feature is **Blueprint Studio**: users can upload one or many Markdown, text, or PDF files, paste text, add detailed instructions, and launch a durable long-horizon mission. The AI extracts a source-linked Requirement Ledger, resolves conflicts, designs a complete personalized SuperFlow workspace, applies the configured changes in the background, verifies actual app state, and produces a gap/assumption/undo report. It can also audit an existing setup or export a product Design Pack.

**Full Control is the primary AI profile in the one SuperFlow app.** After one activation, AI can use every registered app-local capability—including bulk, destructive, settings, provider-routing, and Blueprint operations—without repeated confirmations. It supports local, LAN, remote self-hosted, BYOK, and mixed engines, no-question execution, optional unlimited budgets, raw orchestration controls, automatic snapshots, verification, stop, and grouped undo. APK and AAB expose the same complete feature set; there is no separately named or restricted edition.

The product is designed around the major behavior-change principles described in James Clear's *Atomic Habits*: identity-based change, systems over goals, small repeated improvements, the cue–desire–action–reward loop, the four laws and their inversions, habit stacking, environment design, easy starts, immediate feedback, recovery after a miss, manageable difficulty, and periodic review.

It also integrates a practical self-discipline system: Plan Ahead/Do Now, up to three Daily Focus actions, morning/midday/evening checkpoints, Obstacle Plans, Tiny/Minimum/Standard/Stretch levels, optional energy-aware scheduling, visual anchors, behavior substitution, early support, commitment sprints, meaningful celebration, and compassionate recovery. Fixed-day and percentage ideas are treated as configurable heuristics rather than guarantees.

> SuperFlow is an independent project. It is not affiliated with or endorsed by James Clear or the publishers of *Atomic Habits*.

## Project status

The app is **built and shipping as a signed APK**, on real Android architecture.

```bash
tools/build_apk.sh release   # -> build/outputs/superflow-release.apk
tools/run_tests.sh           # 169 logic assertions
```

| | |
|---|---|
| Package | `com.superflow` 2.0.0 |
| minSdk / targetSdk | 26 / 34 |
| Size | ~7.5 MB, v2+v3 signed |
| Stack | Material 3, AppCompat, ConstraintLayout, RecyclerView, Fragment, ViewPager2, ViewModel/LiveData, Coroutines, androidx.sqlite, WorkManager, DataStore, Lottie |
| Capabilities | 59 registered commands shared by the UI and the AI |

SuperFlow is a full AndroidX application: MVVM with `ViewModel` + `StateFlow`,
`RecyclerView` + `DiffUtil` lists, Material 3 theming with a complete dark mode,
edge-to-edge layouts, collapsing toolbars, bottom-sheet editors, custom animated
charts, haptics, a home-screen widget and voice control.

The domain core follows the plan's rules by construction: scheduling is a
recurrence rule rather than a weekday mask, and adherence, runs, recoveries and
misses are **derived from an opportunity series** rather than stored — so
planned skips and pauses never create misses, schedule edits never rewrite
history, and date maths is `java.time` against an injected clock that survives
reboots, time-zone travel, daylight saving and leap days.

It is built **without Gradle**, because the build environment cannot reach
Google Maven, Maven Central or the Gradle distribution servers. The script in
`tools/` drives `aapt2`, `kotlinc`, `dx` and `apksigner` directly against a
local set of 71 pre-exploded AARs. See **[BUILD.md](docs/BUILD.md)** for the
toolchain and the five non-obvious problems that had to be solved, and
**[IMPLEMENTATION_STATUS.md](docs/IMPLEMENTATION_STATUS.md)** for an honest
feature-by-feature account.

## Product plans

- **[Build and toolchain guide](docs/BUILD.md)** — how the APK is produced
- **[Implementation status](docs/IMPLEMENTATION_STATUS.md)** — what actually shipped
- **[Build and toolchain guide](docs/BUILD.md)** — how the APK is produced
- **[Implementation status](docs/IMPLEMENTATION_STATUS.md)** — what actually shipped
- **[SuperFlow Grand Product and Engineering Plan](docs/GRAND_PLAN.md)**
- **[Blueprint Studio Long-Horizon Intent Compiler Plan](docs/BLUEPRINT_STUDIO_PLAN.md)** — flagship feature
- **[Full Control Plan](docs/FULL_CONTROL_PLAN.md)** — primary AI profile
- **[AI Engine and Universal Control Plan](docs/AI_ENGINE_PLAN.md)**
- **[Self-Discipline Integration Plan](docs/SELF_DISCIPLINE_INTEGRATION.md)**

Together, the plans cover:

- Product vision, principles, users, and information architecture
- A 47-point Atomic Habits coverage matrix plus a 30-point self-discipline suitability matrix
- Onboarding, Plan Ahead/Do Now, Daily Focus, Habit Designer/Ladder, checkpoints, Energy Map, recovery, reviews, and insights
- Blueprint Studio multi-file ingestion, source citations, Requirement Ledger, conflict resolution, complete workspace design, durable execution, verification, amendments, and grouped undo
- Full Control as the primary AI profile, with no-question builds, custom endpoints, raw orchestration, and no repeated app-local confirmations
- Universal manual/AI capability parity and a versioned AI tool catalog
- Cloud Main-Brain providers, BYOK/custom profiles, and secure credentials
- Local Coordinator modes, model management, hardware, and offline fallback
- Full Control, optional Preview/Guided behavior, context receipts, memory, background jobs, voice, unlimited-budget option, audit, stop, and undo
- MVP, beta, v1, and later release scope
- Native Android architecture using Kotlin and Jetpack Compose
- Local-first data model, reminders, privacy, security, and AI safety
- Testing, delivery roadmap, metrics, risks, and definitions of done

## Intended distribution

SuperFlow has one complete product feature set, packaged as:

- An internal debug APK for engineering and testing
- A signed release APK for direct installation
- An Android App Bundle (AAB) for Google Play

The release APK and AAB contain the same Full Control, Blueprint Studio, self-hosted/custom engine, manual UI, and AI settings capabilities.

## License

This repository is licensed under the [MIT License](LICENSE).
