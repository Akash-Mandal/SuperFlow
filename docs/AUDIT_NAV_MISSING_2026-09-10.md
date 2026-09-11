# Nav + Missing Features Audit — 2026-09-10

Shell: `ui/MainActivity.kt:53,137-152` ViewPager2 + BottomNav (`design/Navigation.kt:29-44` TODAY/JOURNEY/INSIGHTS/STUDIO).
Secondaries in `AndroidManifest.xml:122-158`.

## P0 Orphan/Unreachable
- `ui/journal/JournalActivity.kt:31` + `ui/routine/RoutineBuilderActivity.kt:30` NOT in manifest, 0 `startActivity()` — dead code.
- `ui/search/SearchActivity.kt:110-111` journal → `EXTRA_TAB "coach"` stale (valid: today/journey/insights/studio `Navigation.kt:36-40`). Identity/goal/system just opens Journey tab, no deep-link.

## P0 Compose parity
- `ComposeJourneyFragment.kt:105-112` Menu→open only vs `JourneyFragment.kt:265-313` full menu (Restore/Open/Edit/Duplicate/Reorder/Archive/Delete+confirm).

## P1 Confirms/affordance
Review long-press no confirm `:163`, Blueprint `deleteSource:133` no confirm, HabitDetail obstacle one-tap `:389` + archive no confirm `:425`, Memory tap=delete, Flow/Routine long-press only, Designer edit-mode no Delete.

## P1 No bulk anywhere
Today/Journey single-only, Inbox no bulk triage, Search open-only `:99-119`.

## P2 Polish
Pause no edit/cancel, Scorecard no edit/bulk, Search no row actions, empty states bare `textCard` not `SfEmptyState.kt:21`, DataManagement auto-prune no confirm `:528-529`.
