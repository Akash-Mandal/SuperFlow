# SuperFlow — Core Stability & Polish Plan (Plan A)

> **Scope:** Crashes, lag, animations, hit-targets, model selector, AI short replies, file/media chat, theme customization, log export, logo.  
> **Companion docs:** `BLUEPRINT_STUDIO_RECREATION_PLAN.md` (Plan B) + `APP_REDESIGN_PLAN.md` (Plan C) — all 3 ship together, tracked separately for scope.  
> **Decisions locked:** Q1 priority B→A→C internally but parallel execution; Q2 fetch `/v1/models` with fallback presets+free-text; Q3 dual Auto-Reinforce mode (default Propose); Q4 five logo concepts (1 refined uphill + 4 new).  
> **Date:** 2026-08-23 · **Status:** Approved for build

---

## 0. Chain-of-thought — why stability is not a patch but a system

The 14 reported issues look disparate (crash, scroll jank, “no animation”, “hard to tap”). The codebase audit (View RecyclerView + Compose LazyColumn native Kotlin, MVVM StateFlow, ViewPager2, Material3, dual View/Compose gated by `design/Rendering.kt`) reveals they share **three systemic parents**:

1. **Interaction pipeline is trustful, not resilient.** `View.setOnClickListener` on Journey expand ( `ui/journey/JourneyFragment.kt:513` ) bypasses the project’s own `util/Click.kt:onDebouncedClick(500ms)` that guards Today chips. `!!` in `blueprint/CompilerV2.kt:362` and `JournalActivity.kt:72` assumes parse success. `lateinit` + `requireContext()` inside `lifecycleScope.launch { withContext(IO) }` assumes the Fragment survives the IO. The app assumes the happy path; the user lives in the interrupt path (scroll settling, rotate, rapid double-tap).

2. **Performance is correct but not tuned.** Every list uses `ListAdapter+DiffUtil` + `setHasStableIds(true)` — textbook correct. But no `setHasFixedSize(true)`, no shared `RecycledViewPool` across three ViewPager2 pages, no `contentType` in Compose, `ScrollActivity`/`Settings*` misuse a `RecyclerView` with `getItemCount=1` (allocates LayoutManager for static text), `TodayAdapter:199` does `removeAllViews()+inflate` per bind, `SfHeatmap` draws a full-year Canvas off-screen, `JourneyTree.gaps()` recomputes `build()` (O(N) twice), and View charts run `ValueAnimator 700ms` without checking `motionEnabled`. Correctness without tuning = jank on a mid-tier device with 100+ habits.

3. **Configuration surfaces diverge from runtime.** `Prefs.motionLevel` (0=None/1=Reduced/2=Standard/3=Expressive) correctly scales via `DesignTokens.Motion.duration/scaleFor/staggerDelay` → `ui/theme/SfMotion.SfMotionSpecs.forLevel()` → `LocalSfMotion`. But legacy View charts (`ui/common/Charts.kt:90`, `Ui.kt:138 runEntryAnimation`) and skeletons (`ui/components/SfSkeleton.kt:58` `rememberInfiniteTransition`) ignore `SfMotion.enabled`. `Prefs.model` is free-text + 6 preset chips (`AiEngineActivity:140-157` OpenAI/Anthropic/Groq/Ollama/OpenRouter/Together) — never `GET /v1/models`. `Prefs.maxTokens 4096` caps provider at ~3000 words and `maxContextChars 12000` truncates the Context Broker, so even a verbose prompt returns clipped text. The user’s *intent* (“give me a long answer”, “use Gemini Pro”, “upload this PDF”) never reaches the runtime.

**Therefore the fix is not 10 patches but 4 layered repairs:** harden interactions (debounce/hitSlop/guards), tune rendering (pooling/caching/gating), reunify config→runtime (model fetch, token/context, media, motion gating), and polish trust (logs, theming, logo). Redesign (Plan C) and Blueprint (Plan B) then sit on a stable, truthful foundation.

---

## 1. Crash hardening — every tap must be safe under race

### 1.1 Diagnosis with code anchors

| Crash vector | File:line | Why it crashes | Frequency driver |
|---|---|---|---|
| Forced unwrap on regex | `blueprint/CompilerV2.kt:362` `Regex("\\d+ min").find(text)!!.groupValues[1]` | `find` null when imported plan says “10 minutes” vs “10min” or non-English | Blueprint import of year-plan |
| Forced unwrap on Editable | `ui/journal/JournalActivity.kt:72` `content.text!!.length` | `TextInputEditText.text` nullable before layout; NPE on rapid open+save | Journal quick-tap |
| `lateinit` before init | `ui/MainActivity:53`, `JourneyFragment:54`, `LockActivity:26`, `ScrollActivity:33` | `pager/bottomNav/rail/adapter` accessed from `onCreate` early-return or `onDestroyView` collect | Rotation / ViewPager2 swipe |
| `requireContext()` after IO | `JourneyFragment:138`, `TodayFragment:88` `lifecycleScope.launch { withContext(IO) { rows } ; view.snack }` | `view` captured in `pendingRefresh` lambda after `onDestroyView`; `requireContext()` throws | Scroll→tap→rotate |
| Double-toggle race | `JourneyFragment:513` `expand.setOnClickListener { onToggle(row) }` vs `Click.kt:12` debounced elsewhere | No 500ms guard → `viewModel.toggle()` called twice → `expanded Set` + `submitList` diff interleaves → stale `backing` list | Rapid expand spam |
| Drag-vs-tap conflict | `JourneyFragment:146` `isLongPressDragEnabled=true` + `TodayFragment:88` | Drag start steals touch slop from expand chevron 32dp target | Journey stacks especially |

### 1.2 Creative, deep solution

**Hardening philosophy:** treat every callback as *maybe-dead, maybe-late, maybe-twice*. 

- **Eliminate `!!` at the type system.** Replace `find(...)!!` with `find(...)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: return null` and log `SfAI` with `prefs.requestLoggingEnabled`. Extract to `util/Parse.kt:parseMinutes(text):Int?`. Add unit test corpus for “10 min”, “10mins”, “10 minutes”, Bengali “১০ মিনিট”. Guard `JournalActivity` with `text?.length ?: 0` plus `doOnLayout { selection }`.

- **Make debounce the default, not the exception.** Extend `util/Click.kt` with `View.onDebouncedClick` + `Modifier.debouncedClickable(interval=500, enabled)` for Compose. Lint rule via `tools/check_compose.py`: fail if `setOnClickListener` appears in Journey/Studio without debounced wrapper. Expand button then cannot race.

- **Hit-target forgiveness.** Zero `hitSlop` today. For View: add `TouchDelegate` expansion to 48dp (inset 8dp) or `inset` on `MaterialButton`; for Compose: `Modifier.padding(vertical=8.dp).sizeIn(minWidth=48.dp, minHeight=48.dp)` plus `LocalMinimumInteractiveComponentEnforcement` false → manual min. Correct the 17 `setEnsureMinTouchTargetSize(false)` sites: keep false only for chip groups, set true for expand/chevron.

- **Lifecycle-aware collection.** Replace captured `view.snack` with `viewLifecycleOwner.repeatOnLifecycle(STARTED)` + `Channel` for one-shots; clear `pendingRefresh` in `onDestroyView`. Use `view ?: return@launch` guard before `requireContext()`. Store `expanded` as `StateFlow<Set<String>>` and use `update { toggle(it) }` atomically instead of `current = expanded ?: emptySet()` copy.

- **Global safety net.** Install `Thread.setDefaultUncaughtExceptionHandler` in `SuperFlowApp.onCreate` that writes to `files/crash.log` (ring 500KB) plus `Repository.saveAudit` entry `actor=system, command=crash, summary=stacktrace take 4000`. Respect `Prefs.crashReporting` opt-in for future Crashlytics; even when off, local file fuels export (see §5).

**Acceptance:** `./gradlew testDebugUnitTest` passes new `ParseMinutesTest`; manual torture: 20 rapid expand taps → exactly 1 toggle; rotate during Journey load → no ISE; import malformed plan → no NPE + snack.

---

## 2. Performance & scroll — from correct to fluid

### 2.1 Where the frames die (measured reasoning)

- **RecyclerView tuning gap:** No `setHasFixedSize(true)` → `onMeasure` re-measures on each `DiffUtil` update. No shared pool → ViewPager2’s three pages each allocate own scrap heap. `setItemViewCacheSize` default 2 → churn when flinging 50 habits.
- **Static overhead:** `ScrollActivity:60` and all Settings fragments wrap a `LinearLayout` in a `RecyclerView` with 1 item — allocates `LinearLayoutManager` + `Adapter` for static text. Replace with `NestedScrollView`.
- **Bind churn:** `TodayAdapter:199` `ReturnVH`/`FocusVH` `container.removeAllViews()` + `inflate` per bind; `CheckpointVH:260` `chips.removeAllViews()` recreates 5 `Chip`s per bind; `JourneyFragment.ToolsVH:403` `findViewById` per bind instead of cached.
- **Compose duplication:** `JourneyScreen.kt:95` `remember(state.nodes){ JourneyTree.build }` + `JourneyScreen:106` `gaps = remember(state.nodes){ JourneyTree.gaps }` where `gaps` *internally* calls `build()` → 2× traversal for N=500. `SfHeatmap` `Canvas` draws 14×W cells even when W off-screen; `TodayScreen:130` no `remember` for row list; no `contentType` → Compose cannot slot-reuse across `Progress/Identity/Section/HabitRow/Empty`.
- **Animator spam:** `Charts.kt` ValueAnimator 700ms DecelerateInterpolator on every `setProgress/setBars` — Insights has 1 ChartVH + 1 StatsVH + 1 HeatVH → 3 concurrent animators → 90 `invalidate()`/sec. `Ui.runEntryAnimation` loads `R.anim.layout_slide_up` for *every* `submitList`.

**Mental model:** The app is *doing the right work twice and animating it while measuring it*.

### 2.2 Fluidity plan (creative tuning, not rewrite)

**A. RecyclerView (View) — 2-hour wins, 60fps:**

```
list.setHasFixedSize(true)
list.setItemViewCacheSize(12)
list.setDrawingCacheEnabled(false)
val pool = RecyclerView.RecycledViewPool().apply { setMaxRecycledViews(VIEW_TYPE_HABIT, 16) }
viewPager2.children.filterIsInstance<RecyclerView>().forEach { it.setRecycledViewPool(pool) }
```
Cache `ViewHolder` lookups (`ToolsVH` holds `icons TextViews` once), reuse `Chip` pool (`RecycledChipPool`), `DiffUtil` with `getChangePayload` for partial bind (only check icon). Replace single-item `RecyclerView` with `NestedScrollView` + `LinearLayout`.

**B. Compose LazyColumn — slot reuse + memo:**

- `item(key, contentType="HabitRow")` for each `JourneyRow.Kind.rank` (IDENTITY/GOAL/SYSTEM/HABIT) — enables slot reuse.
- Hoist `tree = remember(nodes, expanded){ JourneyTree.build(nodes, expanded) }` once; derive `gaps = remember(tree){ tree.gaps }` without rebuilding.
- `weeks = remember(states, firstWeekday){ computeWeeks }` already good → add `drawWithCache` for heatmap Canvas.
- `TodayScreen` `todayRows = remember(viewModel.rows){ rows }` to avoid recomposing 50 cards when unrelated `prefs` changes.

**C. Animator gating + orchestration:**

- Central `SfTheme.systemAnimationsDisabled(ctx)` + `prefs.motionDisabled` → `SfMotion.enabled` must gate **every** animation (fix violations in `Charts.kt`, `Ui.kt`, `SfSkeleton`, `StudioScreen` typing dots — return `snapTo(target)` when `!enabled` instead of posting animator).
- Cap orchestrated entrance: `Motion.orchestrationMs`, `staggerDelay(index)`, `ENTRANCE_MAX 800ms` already correct — enforce via `SfMotionSpecs.tween` returning `snap()` when disabled.
- Debounce `runEntryAnimation` with `list.post { if (motion.enabled) apply }` only on first load, not on every `submitList`.

**D. Heavy work off main:**

- `InsightsViewModel.build` already `withContext(IO)` — ensure `analyzeCorrelations/seasonalTrends/optimalOrdering` (O(H²×D) H=10 cap) never called from Compose without IO. Add `ensureActive()` checkpoints.
- `ProgressCard` bitmap (`Bitmap.createBitmap(W,H) + Canvas + compress PNG`) must stay on `Dispatchers.IO` (already in `DataManagementFragment:324` — audit `ShareCard` call site in `MainActivity`).

**Verification:** Profile with `adb shell dumpsys gfxinfo com.superflow framestats` → <5% frames >16ms after; scroll 500-item Blueprint ledger → no dropped frames; cold start <1.5s.

---

## 3. Animations — make the setting honest

**Current:** `Prefs.motionLevel` 0-3 (`NONE 0f / REDUCED 0.5f / STANDARD 1f / EXPRESSIVE 1.25f` via `motionScale`), `Motion.duration(base,level,systemOff)` → 0 if disabled, `SfMotionSpecs.enabled` + `tween/spring/bouncy` → `snap()` when `!enabled`. Correctly gated in `TodayScreen 81`, `JourneyScreen 217`, `InsightsScreen 201`, `OnboardingScreen 128`, `SfEntityRow 233`. **Broken in 4 places above.**

**Design decision chain:**  
*If user says “No animation anywhere” we must not post a single animator, not even a 0ms one (still posts a frame & fires listener → flicker). If system says `ANIMATOR_DURATION_SCALE 0f`, we must obey even when user says Expressive (vestibular safety). Essential progress (ring fill) may still snap to target.*

**Implementation:**

1. **Fix violations** — patch list with owner + test:
   - `ui/common/Charts.kt:90-107` `ValueAnimator` → `if (!SfMotion.enabled) { animated=target; invalidate(); return }`
   - `ui/common/Ui.kt:138` `runEntryAnimation` → `if (!motion.enabled) return`
   - `ui/components/SfSkeleton.kt:58` `if (!motion.enabled) return static Brush` (no `rememberInfiniteTransition`)
   - `ui/screens/StudioScreen.kt:472` `if (!animate) return alpha 0.7f` **before** creating `rememberInfiniteTransition`
   - `SfBarChart.kt:71` check `enabled` before `animateFloatAsState`

2. **Separate celebration from motion.** `AppearanceFragment:232` `Catalog.motionLevels` (None/Reduced/Standard/Expressive) + `SettingsFragment:296` `celebrationsEnabled` (confetti on day complete) are conflated: motion=None should still allow `celebrations==false` to skip. Gate celebration via `if (prefs.celebrations && motion.enabled) confetti()`.

3. **Orchestration budget.** Keep `Motion.ORCHESTRATION_BUDGET 800ms`, `STAGGER 40ms`, `STAGGER_MAX_ITEMS 8` — last item finishes <800ms even with 50 habits. Use `fitsBudget(indices)` test.

**Polish:** Add `remember(context) { SfTheme.systemAnimationsDisabled }` logging; show `AppearanceFragment` note “System animations off” already does.

---

## 4. Model selector — from free-text to fetched

**Current:** `Prefs.providerName/baseUrl/fallbackUrl/model/apiKey(secure prefs)/organizationId/customHeaders`, `cloudReady()` checks non-blank. UI free `Model TextInputEditText` + 6 preset chips (`AiEngineActivity:140` OpenAI gpt-4o / Anthropic claude-sonnet-4 / Groq llama-3.3-70b / Ollama llama3.1 / OpenRouter gpt-4o / Together Llama-3.1-70B). Never `GET /models`. Q2 requires fetch + fallback.

**Deep design:**

- **Fetch layer:** `ai/ModelCatalog.kt` `suspend fun fetchModels(prefs):Result<List<Model>>` — `GET {buildUrl(base)}/models` with `Authorization Bearer apiKey` + `OpenAI-Organization` + split `customHeaders`, `connectTimeout/readTimeout = prefs.requestTimeoutSec*1000`, retry `prefs.retryCount` with backoff. Parse `data[].id` (OpenAI spec) + Anthropic `model.list` fallback. Cache `model_cache.json` in `files/` with `fetchedAt`, expiry 24h, visible staleness banner. On 401/403 show actionable snack (“Invalid key for …”), on 404 (provider without /models like Groq) fall back silently.

- **UI triad (same component, 3 hosts):** `ui/components/SfModelSelector.kt` — search `ExposedDropdownMenuBox` + free-text fallback. Hosts: `AiEngineActivity` (global default), `StudioScreen` header (per-chat override chip “Model · gpt-4o ▼”), `BlueprintActivity` pre-compile header (pre-action selector before Compile/Chat — satisfies issue #6 “before an action/chat”). Selection writes `prefs.model` (global) or `project.modelOverride` (per-feature) with `appearanceRevision` bump only if palette change, not model change.

- **Validation & UX:** Validate `model.isNotBlank()` — warn if fetched list non-empty and model not in list (typo) but allow (custom fine-tune). Show latency/cost hint via `MainBrain.chat` timing. Add to context receipt `MainBrain.buildContext` visible models line.

- **Security:** Key never logged; `maskedKey()` shows 4…4; `secrets` prefs excluded from backup/export (`DataPolicy`).

**Tasks:** Add `ai/ModelCatalogTest` with mocked HttpURLConnection; instrumented test for 401 snackbar; manual BYOK via custom endpoint.

---

## 5. AI short replies — give the user a long answer when they ask for one

**Diagnosis:** Default `Prefs.maxTokens 4096` (≈3k words) is safe for cost but the UI hides longer options. Default mode picker shows Short 1024 / Medium 4096 / Long 8192 / Very long 16384, but default selection is Medium; Intermediate adds Maximum 32768, Advanced free 64-131072. Most users never see Advanced. `maxContextChars 12000` truncates `MainBrain.buildContext(repo,prefs).take(maxChars) + "[truncated]"` → model sees generic prefix, replies generic short. `responseFormat json_object` vs `text` also trims.

**Solution chain (conservative, not “infinite”):**

1. **Raise safe default:** `maxTokens` default 4096 → **8192** (Long). Cost impact: ~2× output tokens only when model actually generates long; input stays bounded by `maxContextChars`. Keep Short for cost-sensitive.

2. **Surface picker in Default:** Show all 4 options in Default (not just Intermediate/Advanced) with explainer `AiEngineActivity + InfoButton.kt:125` “4,096 ≈3,000 words … 16,384 ≈12,000 words — needed for Blueprint compilation”. Keep Advanced free-number for power users.

3. **Expand context responsibly:** `maxContextChars` 12000 → **20000**; builder already slices `contextIncludeHabits/Insights/Reviews/Obstacles/Flows/Memory` with permits — add `take(4000)` preview dialog before send. Ensure blueprint ledger rows `text.take(160)` stays but high-priority requirements re-open full block via retrieval.

4. **Streaming UX:** `prefs.streamingEnabled false` → default off; when on, `MainBrain.chat` sends `stream:true` and UI shows token stream into `StudioScreen` typing row (replace infinite dots). Even when off, add “Continue” chip if reply truncated (`finish_reason length`).

5. **Prompt discipline:** `MainBrain.systemPrompt` already injects `Coordinator.toolCatalog()` and principles; add explicit instruction “Prefer comprehensive answer when user asks for detail; do not prematurely summarize.” Keep temperature 0.7 default, but expose per-model.

**Verification:** Unit `MaxTokensTest` checks payload `max_tokens` passes-through; manual: ask “explain in detail” → >600 words with 8192 cap; check cost `tokensThisMonth` increments via `noteCall()`.

---

## 6. File/media chat input — let the AI see what the user sees

**Current:** `StudioFragment` `StudioComposer` is `SfTextField + mic (SpeechRecognizer, RMS→waveform 28 bars) + send`. No picker. `BlueprintActivity` has `OpenDocument(["text/*","application/pdf","*/*"])` + `importFile(uri)` 2MB limit, `%PDF` sniff, `PdfText.extract` (stream/endstream inflate, 5000 streams max, 400k chars, no OCR) — but chat cannot use it.

**Design:**

- **Picker:** `ActivityResultContracts.OpenDocument` + `GetContent` + `TakePicture` (via `FileProvider @xml/file_paths`). Button `ic_attach` in composer opens bottom sheet: [Photo][Camera][File][Document]. Limits: 2MB like Blueprint (snack “Larger than 2 MB. Split it…”), MIME sniff + `PdfText.looksLikePdf`.

- **Rendering:** Preview chips below composer (thumbnail for image, icon+name+size for pdf/doc, waveform for video). Tapping chip removes. Max 5 attachments per message (like Blueprint sources).

- **Payload:** For image/video → base64 or `content://` URI passed via `AiMessage` extension `attachments JSONArray` (store references, not blobs in Room; blobs in `files/attachments/` encrypted). `Agent.handle` + `MainBrain.chat` branch: if attachments present, build `messages` with `content: [{type:"text",text:...},{type:"image_url",image_url:{url:"data:image/jpeg;base64,..."}}]` for OpenAI-compatible, else `PdfText.extract` → inject as quoted block `Source: name:L1-LN\ncontent`. Keep `ContextBroker` receipt showing attached files.

- **Telemetry & safety:** Secret scan (`isInjectionAttempt` regex) still runs; attachments excluded from backup if `prefs.unlimitedBudget`? No, keep but respect `superflow_secrets` exclusion.

**Edge:** Scanned PDF → `PdfText.extract` blank → snack “No readable text. For scanned PDFs, paste instead.” (reuse Blueprint).

---

## 7. Theme customization — from trash to premium

**Current critique:** Overlays are powerful (`ThemeSelection.overlaysFor` palette→darkVariant→density→contrast, `SfTheme.apply` before super.onCreate, `appearanceRevision` → `recreate()`, 5 palettes CALM/FOREST/OCEAN/DUSK/MONO, 3 dark WARM/OLED/MIDNIGHT, 3 densities, HIGH_CONTRAST, dynamicColor only if CALM + SDK≥S) but **UI is flat**: `AppearanceFragment` shows palette swatches reading raw `R` not themed, no live preview, density is a chip, no hue builder.

**Solution:**

- **Live preview card:** At top of Appearance, a miniature Today/Journey card that re-renders on `prefs.palette/density/darkVariant` change without `recreate()` (use `Compose` preview `SfThemeFromPrefs` reading `Prefs.get`). Palette pick uses `PaletteSwatches.colorsFor(context,id)` already correct.

- **Builder (stretch, not v1):** Hue slider 0-360 + saturation for custom CALM tint → writes `customPalette Json` → `ThemeSelection` maps to nearest `ColorRoles.schemeFor` via `Ramps`. Guard behind `Advanced` to avoid chaos.

- **Density live:** Slider with `DesignDensity.metrics(id).toCompose(level)` + line spacing preview.

- **Contrast & dynamic:** System high-contrast read `Settings.Secure.high_text_contrast_enabled` already; expose toggle; `useDynamicColor` only when `palette==CALM` stays.

**Keep:** `SfTheme.apply` before `super.onCreate` + `needsRecreate` remains’ `recreate()` path — no flicker.

---

## 8. Error log export — trust through transparency

**Current:** No SDK, `Prefs.crashReporting false` unwired, `Diagnostics.issues/checkIntegrity/fix`, `Repository.integrityReport`, `audit` table (`actor,command,summary,payload,undoPayload,groupId,undone,createdAt`) shown in `ActivityLogActivity`, `Log.d SfAI` only if `requestLoggingEnabled` (`AiEngineActivity:408`).

**Solution:**

- **Ring buffer:** `util/LogFile.kt` — `File(filesDir,"logs/app.log")` 512KB ring, `LogFile.write(tag,msg)` + `LogFile.read()`. `AppBackground` hook installs `LogFileTree` for `Log.w/e`. Crash handler from §1 appends stacktrace. `MainBrain` request/response (first 2000 chars) appends only if `requestLoggingEnabled`.

- **Export:** `DataManagementFragment` “Export logs” → `FileProvider` share `ACTION_SEND` `text/plain` + `application/json` (audit export already via `DataPolicy.exportAll`). Options: Share via system share sheet, save to `Documents/`, clear after export.

- **Integrity UI:** Expose “Check data integrity” + “Fix” + “Export integrity JSON” in Settings → Data.

---

## 9. Logo — five concepts, one system

**Requirement:** Replace default; generate 5 concepts: 1 refined uphill + 4 new.

**System constraints:** Adaptive icon 108dp viewport, `mipmap-anydpi-v26/ic_launcher.xml` `<adaptive-icon><background @drawable/ic_launcher_bg><foreground @drawable/ic_launcher_fg><monochrome>` + 6 variants (`ic_launcher`, `_round`, `_minimal`, `_mono`). Splash `drawable/splash_background` layer-list `?colorBackground` + centered `ic_launcher_fg` bitmap.

**Concepts to produce (vector `ic_launcher_fg` variants):**

1. **Refined Uphill (A):** Keep 4-point path `M30,70 L46,62 L62,50 L78,34` + 3 dots 5.5dp + mint last dot `#FFC8E9D8`, but refine stroke 4→3.5, dot 5.5→6, add subtle 0.3 alpha trail, optical centering, 8dp safe margin. Warm paper bg `#F8F5F0`.
2. **S/Flow Monogram (B1):** Abstract S formed by 2 overlapping rounded rects, gap = flow, 45° tilt.
3. **System Rings (B2):** 3 concentric quarter-rings (tiny→stretch levels) emanating from center dot.
4. **Anchored Arrow (B3):** Horizontal anchor bar + upward arrow emerging — “after X, I will Y”.
5. **Wordmark Minimal (B4):** “SF” letterform with negative-space path, mono-ready.

Each has `ic_launcher_bg` (warm), `_minimal` (only origin+destination), `_mono` (single glyph for themed icon). `AppIcons.apply(ctx,id)` remains but aliases now point to new drawables. Provide `tools/gen_splash.py` to update `splash_background`.

**Verification:** Install on API 26 + 34 emulators, launcher grid, themed icon (Android 13), splash, `aapt2 dump` no unresolved.

---

## 10. Delivery — tasks, order, risks, gates

**Task DAG (24 tasks, 4 lanes):**

- **Lane Crash (P0):** C1 `!!` fix + Test → C2 debounce/hitSlop → C3 lifecycle guards → C4 crash handler + LogFile
- **Lane Perf (P0/P1):** P1 RecyclerView tuning + pool → P2 Compose contentType + memo → P3 animator gating → P4 heatmap drawWithCache
- **Lane AI (P1):** A1 ModelCatalog fetch → A2 token/context bump + streaming → A3 media picker + payload
- **Lane Polish (P2):** L1 theme preview card → L2 log export → L3 five logos

**Risks → mitigations:** Debounce tuning too aggressive → test 500ms vs 300ms; model fetch auth leak → key only in header, never log; context bump cost → receipt + budget `monthlyCallBudget 5000` cap; logo adaptive safe zone → preview on 3 launchers.

**Gates (every lane):** `./gradlew testDebugUnitTest` (new `*Test`), `lintDebug` (NewApi `borderlessButtonStyle` check), `assembleDebug`, `aapt2 dump` manifest checks, `ci_emulator_verify.sh` cold+existing launch on API 26 & 34, `tools/check_{res,policy,compose,generated,widget}.py`.

---

## 11. Metrics — did we fix what hurts?

| Signal | Before | Target |
|---|---|---|
| Crash rate (rapid tap) | NPE observed | 0 NPE in torture |
| Expand tap success first try | <60% | ≥98% @48dp |
| Scroll jank >16ms | >15% frames | <5% frames |
| Motion OFF still animates | yes (Charts/Skeleton) | none |
| Model selector fetch success | never | cached 24h + fallback |
| Avg reply words (detail ask) | ~200 | >600 |
| Attachments per chat | 0 | up to 5, preview |
| Theme preview live | no | yes |

**Next doc:** `BLUEPRINT_STUDIO_RECREATION_PLAN.md` details intelligent merge, flood fix, phased rollout, Auto Reinforce, and frameworks. `APP_REDESIGN_PLAN.md` details the calm, sophisticated system.
