# AI Provider Template Cards Audit — 2026-09-10

## Core files
- `ui/engine/AiEngineActivity.kt:45-1030` — all logic in Activity, no ViewModel. `buildContent()`, `saveProvider():872`, `testConnection():986`, `fetchModels():996`, `field():816`, `picker():849`.
- `ui/settings/SettingsFragment.kt:167-170` entry. `data/Prefs.kt:630-652,903-926` persistence. `ai/MainBrain.kt:229-380` chat/buildUrl/test. `ai/ModelCatalog.kt:1-92` fetch.
- No `ProviderTemplate` data class exists. No `*provider*template*card.xml` exists.

## Broken
1. **No cards, only chips** `AiEngineActivity.kt:145-169`: `Chip` with `isCheckable=false`, name-only, no URL/model/desc, no selected-state, no key-status.
2. **Click fills 3 fields only** (`pf,bf,mf`), no `saveProvider()` call, no `Prefs` write, no `rebuild()`. Snack "Preset loaded — edit and save" — easily lost.
3. **No model**: `Pair<String,Pair<String,String>>` drops `fallbackUrl`, `organizationId`, `customHeaders`, timeout. Wrong values:
   - Anthropic fails (`MainBrain:285` sends Bearer + `/v1/chat/completions`; needs `x-api-key` + `/v1/messages`).
   - Ollama `http://localhost:11434` unreachable on device (needs `10.0.2.2`/LAN).
   - OpenRouter missing `/v1` + `HTTP-Referer`. Together model deprecated.
   - Missing DeepSeek/Mistral/Azure/Gemini/LM-Studio.
4. **Save gap** `L872-892`: blank-key-to-keep can't clear via template; Test/Fetch use `quiet=true` no feedback; no URL validation.
5. **fetchModels bug** `L996-1018`: `rebuild()` detaches `modelField`, dialog writes to detached view, final `rebuild():1017` discards, `prefs.model` never updated.

## Fix direction
Introduce `ProviderTemplate` data class + `MaterialCardView` list (mirror `HabitDesignerActivity.kt:193-210` pattern), one-tap apply = fill + `saveProvider()` + `rebuild()`, correct per-provider headers/URLs, URL validation, fix fetchModels to write `prefs.model`.
