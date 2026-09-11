# Fix Plan — Delete buttons + Provider cards — 2026-09-10

## Phase 1 — Compose Journey parity (P0)
- `ui/screens/JourneyScreen.kt:59-65`: add `Delete/Archive/Duplicate` to `JourneyAction`.
- `ui/journey/ComposeJourneyFragment.kt:105-128`: full popup menu mirroring `JourneyFragment.kt:265-313` (confirm + `model.delete` + snackbar undo).
- Acceptance: Compose delete/archive works with confirm + undo.

## Phase 2 — Provider template cards (P0)
- New `ai/ProviderTemplate.kt` data class: name, desc, baseUrl, model, headers, fallbackUrl, orgId.
- `AiEngineActivity.kt:145-169`: replace `ChipGroup` with `MaterialCardView` list (pattern `HabitDesignerActivity.kt:193-210`); tap = fill + `saveProvider()` + `rebuild()` + selected-state.
- Correct values: Anthropic headers/endpoint note, Ollama `10.0.2.2`, OpenRouter `/v1` + Referer, add DeepSeek/Mistral/Gemini/LM-Studio, drop deprecated Together model.
- Fix `fetchModels:996-1018` to write `prefs.model`; add URL validation; Test/Fetch feedback.
- Acceptance: one-tap apply persists, test passes, model fetch sticks.

## Phase 3 — Sprint/Journal/Routine rescue (P0)
- Sprint: detail sheet edit/delete/complete/archive + empty CTA (`SprintBoardActivity.kt:99`, `SprintBoardScreen.kt:47-60`); wire `repo.deleteSprint:725` + CommandBus case.
- Journal: manifest register + list/edit/delete UI wiring `repo.deleteJournalEntry:745`.
- Routine: manifest + entry point, step edit/delete/reorder, route delete via CommandBus (undo).
- Fix `SearchActivity.kt:110` coach→studio + deep-links.

## Phase 4 — Confirms + visible buttons (P1)
Single-tap/long-press deletes get confirm + overflow button: Review, Blueprint, HabitDetail obstacle/archive, Memory (explicit button), Flow/Routine, Designer edit-mode Delete/Archive, DataManagement prune.

## Phase 5 — Bulk + polish (P1-P2)
Multi-select bulk delete/archive (Today/Journey), bulk triage Inbox, `SfEmptyState`+CTA everywhere, Pause edit/cancel, Scorecard edit/bulk-clear.

Order: 1 → 2 → 3 → 4 → 5. Each phase independently testable (`./gradlew :app:assembleDebug`, manual UI pass).
