# Studio Chat Redesign Plan — 2026-09-10

Goal: aesthetic, clean, and — most importantly — useful. Keep the existing
design system; fix structure, feedback, and density. No new theme, no new
component library.

## 1. Principles
1. Transcript is the product. Everything else (status, chips, projects) is chrome — it must never push messages around or scroll away critical state.
2. One input behavior everywhere: tap fills, send sends. No mixed tap semantics.
3. Every send gets feedback within 200ms and a way out (cancel). No dead air.
4. Density over decoration: fewer cards, fewer buttons per message, richer message content (markdown, selection, timestamps).

## 2. Layout (new structure)

```
StudioScreen (max 600dp centered, SfThemeFromPrefs — drop forced-dark island)
├─ Sticky header (NEW, not a LazyColumn item):
│   └─ Status pill: dot + "Guided · cloud ready" + chevron → AiEngineActivity
│       Collapses to 40dp bar on scroll; never scrolls away.
├─ LazyColumn transcript (16dp sides, cardGap spacing, 96dp bottom pad)
│   ├─ DateBreak rows (keep HairLine + overline)
│   ├─ Message rows (redesigned, §3)
│   ├─ Project rows (keep, max 2, collapsible)
│   ├─ Typing/streaming row (keep TypingRow dots → staged status text)
│   └─ Smart autoscroll: follow tail ONLY if already at bottom; else show
│       scroll-to-bottom FAB with unread count (NEW)
├─ Quick replies (NEW position): single h-scroll AssistChip row PINNED above
│   composer, max 4, only when relevant (empty state or after assistant turn
│   with follow-ups). Replaces the current 5 QuickActions + 5 Suggestions +
│   CoachCard stack (~11 competing CTAs → 4).
└─ Composer (redesigned, §4)
```

Changes vs now (`ui/screens/StudioScreen.kt:122-183`, `StudioModel.kt:155-196`):
- `StatusRow`, `QuickActionRow`, `ProjectRow` move OUT of `LazyColumn` items into sticky/pinned slots. `rows()` keeps only DateBreak/Message/Fold.
- Remove `StudioNightTheme` forced dark (`ui/studio/StudioNightTheme.kt:29`) — use `SfThemeFromPrefs()` like every other screen. Tab switch flash disappears.
- Empty state: `SfEmptyState` (icon + title + body + single CTA "Ask Studio") + 4 quick-reply chips. Delete `SuggestionRow` vertical cards + `CoachRow` accent stacking.

## 3. Message rows

Keep: `mine→End+secondaryContainer`, `assistant→Start+surfaceVariant`,
`widthIn(max=460dp)`, asymmetric 18/4dp corners (`StudioScreen.kt:333-409`).

Fix:
- Render markdown: lists, bold, `Done:` action lines as real bullets (assistant replies are plain `Text bodyLarge` today — `Agent.kt:193-200` assembles `·` lines nobody formats).
- `SelectionContainer` around bubble text (copy by selection, not a button).
- Timestamp `bodySmall + onSurfaceVariant` under bubble (`HH:mm`); keep day breaks.
- Move `Copy/Explain/Retry` into long-press / `...` menu (`ModalBottomSheet` pattern from `InboxSheet.kt:38-98`). Only `Undo` stays inline, and only when `undoable` (`actionsFor`, `StudioModel.kt:220-231`).
- Status chip: keep `labelSmall + liveRegion Polite`, but shorten meta — move route strings ("on device — cloud unavailable") into the sticky status pill tap-target, not every bubble.
- `SYSTEM` centered `bodySmall` — keep.

## 4. Composer

Current: `Surface tonal2dp + SfTextField + Attach + Mic + Send`, locked while
sending (`StudioScreen.kt:546-629`).

Fix:
- Typing while sending: `enabled=true` always; `Send` queues a follow-up instead of spinner-locking the field. `canSend` gates the button, not the field.
- Cancel: while `sending`, Send morphs to Stop (square) wired to existing `Agent.stop()` — currently no button calls it.
- Attach: file becomes a removable chip above the field (name + size + ×), not 6000 pasted chars (`StudioFragment.kt:150-161`). Keep 2MB cap, announce via snack.
- Counter: keep `3600/4000` but reserve the `supportingText` slot always so the layout never jumps.
- Mic: keep waveform + add `mm:ss` + "Tap to stop — reviews before sending" (voice currently auto-sends on result, `StudioFragment.kt:262-265` — change to fill-then-confirm).
- Haptics: send→`COMPLETE`, chip→`SELECT` (`ui/common/SfHaptics.kt`).

## 5. Sending feedback (kill the dead air)

`VM.send` today blocks on `agent.send()` with only dots (`StudioFragment.kt:399-410`).
- Staged status line under the typing dots: "Thinking on device…" → "Contacting cloud…" → "Running 3 actions…" → tokens. `Agent.kt:88-131` already knows the stage — expose it as `Flow<SendStage>`.
- Phase 1 (no protocol change): staged text + Cancel. Phase 2: true token streaming into the bubble.
- Rate-limit (10/min, `Agent.kt:61-68`) surfaces as a composer hint, not a failed bubble.

## 6. Voice + read-aloud

- Voice input: confirm-before-send (fixes misfire commands). Keep `level()` RMS bars.
- TTS: the engine exists with zero Studio call sites (`SfTextToSpeech.kt`, `Speech.kt`). Add a speaker toggle per assistant bubble + "auto-read coach" setting. Closes the a11y gap for coach content.

## 7. History

- `VISIBLE_TURNS=40` fold becomes expandable AND collapsible ("Show earlier / Hide").
- Autoscroll only when pinned to bottom; otherwise FAB.
- Keep `ProjectRow` progress bars; make them collapsible.

## 8. Visual polish (cheap, high-value)

- Bubbles: keep colors; add 1dp `outlineVariant` hairline on assistant bubbles for definition in light mode (after forced-dark removal).
- DateBreak: keep. Fold: keep `TextButton` style.
- Motion: all via `SfTheme.motion.tween/spring`, respect `motion.enabled` (static dots already handled in `TypingRow:483-533`).
- A11y: keep `heading()`, `contentDescription`, `liveRegion Polite`; 48dp targets; message actions in `customActions` like `SfHabitCard`.

## 9. Phases

1. **Chrome**: sticky status pill, pinned quick replies (max 4, unified semantics), `SfEmptyState` empty state, drop forced dark. Files: `StudioScreen.kt`, `StudioModel.kt rows()`, `StudioNightTheme.kt` (delete/use prefs).
2. **Messages**: markdown + selection + timestamps + `...` sheet, Undo-only inline. Files: `StudioScreen.kt MessageRow`, `StudioModel.kt actionsFor`.
3. **Send**: typing-while-sending + queue, Cancel→`Agent.stop()`, staged status, attach chips, voice confirm. Files: `StudioFragment.kt send/quick/message`, `Agent.kt` expose stage, `StudioScreen.kt StudioComposer`.
4. **History + TTS**: smart scroll + FAB, collapsible fold, per-bubble speaker + auto-read setting. Files: `StudioScreen.kt transcript`, `SfTextToSpeech.kt` wiring.
5. **Streaming**: token flow into bubble (needs `Agent.send` refactor — last for a reason).

Acceptance per phase: light+dark pass, 48dp targets, screen-reader pass on transcript/composer, no `BoxWithConstraints` crash on foldables, manual send/cancel/undo/voice/attach matrix green.

## 10. Out of scope

New palette, new font, View→Compose migration of toolbar (keep `fragment_studio.xml` shell), `Agent` routing changes beyond stage exposure + stop wiring.
