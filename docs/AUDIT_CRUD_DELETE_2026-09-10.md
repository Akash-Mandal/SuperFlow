# CRUD Delete/Edit Audit — 2026-09-10

Hierarchy: `IDENTITY→GOAL→SYSTEM→HABIT` (`data/model/Models.kt:68,95,118,129`).
Repo deletes exist: `data/Repository.kt:172,209,234,317`. Undo via `domain/CommandBus.kt:195-211`.

## Findings
- **Journey View path OK, Compose path BROKEN**: `ui/journey/JourneyFragment.kt:264-313` has Delete+confirm → `model.delete:320-326`. `ui/journey/ComposeJourneyFragment.kt:105-113` only `open()` — no delete/archive. `ui/screens/JourneyScreen.kt:59-65` `JourneyAction` has no Delete.
- **Sprint MISSING**: `ui/sprint/SprintBoardActivity.kt:38`, `SprintBoardScreen.kt:31` read-only, `onSelect` no-op `:99`. `repo.deleteSprint:725` has 0 callers, no CommandBus case.
- **Journal create-only**: `ui/journal/JournalActivity.kt:31-113` save+cancel only. `repo.deleteJournalEntry:745` 0 callers. Not in `AndroidManifest.xml`.
- **Routine no edit, unsafe delete**: `ui/routine/RoutineBuilderActivity.kt:30` no rename/trigger/step edit/reorder. `makeDeleteButton:146-163` direct `repo.deleteRoutine` — no undo. Not in manifest, 0 entry points.
- **Hidden long-press deletes**: Flow (`ui/flows/FlowActivity.kt:105-113`), Review (`ui/review/ReviewActivity.kt:162-164` no confirm), Scorecard (`ui/scorecard/ScorecardActivity.kt:70-79`), Obstacle (`ui/detail/HabitDetailActivity.kt:388-389`).
- **Single-tap danger**: Memory tap=delete (`ui/memory/MemoryViewerActivity.kt:60-65`, `MemoryViewerScreen.kt:42-44` no confirm).
- **HabitDesigner edit mode** (`ui/designer/HabitDesignerActivity.kt:105-108,656-701`) has no Delete/Archive.
- **No bulk anywhere**: Today, Journey, Inbox (`ui/inbox/InboxSheet.kt:56`).

## Priority
P0: Compose Journey parity, Sprint detail, Journal list/edit/delete + manifest registration.
P1: Visible overflow buttons + confirms, Designer delete, bulk actions.
