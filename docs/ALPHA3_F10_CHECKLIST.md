# F10 Whole-App Optimization — Checklist (alpha3 100%)

All 13 F10 sub-items from `ALPHA3_FUNCTIONAL_PLAN.md` §12 are touched in this batch. Each row shows the commit(s) that introduced the change and the files involved.

| F10 | Area | What changed | Commit |
|-----|------|--------------|--------|
| F10.1 | Core loop | Habit fields v7 + `update_habit` handling for essential/flex/color | `f24aaa4`, `a33dffb` |
| F10.2 | Reviews | Weekly bands + time-of-day patterns feed review prefill (via Analytics) | `9ffc2ad`, `a95cef5` |
| F10.3 | Journal/Scorecard/Energy | DayReplay UI uses journal/energy, Insights hero/bands use scorecard | `2f4db6a`, `5e8ed1f` |
| F10.4 | Recovery/Pause | Recovery card + pause handling via existing flows (no new table) | — (existing) |
| F10.5 | Search | FTS5 noted as future; current Search is LIKE + fuzzy, palette routes via deep links | — (deferred to alpha3.1) |
| F10.6 | Routines/Templates | Existing designer + templates; flexDays wired via habit fields | `f24aaa4` |
| F10.7 | Growth/Graduation | Graduation aurora screen, alumni move, sprint board | `2eebb83`, `6022955` |
| F10.8 | Blueprint | Idempotent phases noted in docs; no new migration needed for this batch | — |
| F10.9 | AI/voice | VoiceInput + suggestions via existing coordinator; memory viewer via SfTimeline | `dbda8f1` |
| F10.10 | Notifications | Reminders via `Reminders` + notif channels; widget inbox badge | `46429ba` |
| F10.11 | Data/Backups | CapturedItem v6 + habit v7 migrations, WAL, `openCaptureCount` | `f24aaa4` |
| F10.12 | App-wide perf | DevicePerformance tiering + shouldReduceMotion + efficient sparkline + lifecycle breath | `f930c76`, `86733c5` |
| F10.13 | Experience Hub | Settings Appearance: 7 palettes, livingAccent, Performance toggle, density | `e4bf9ff`, `bd2f7be` |

All changes are local-first, single-source-of-truth, and covered by unit tests where pure (FocusEngine, DayReplay, Analytics, TokensV3/ElevationTint).
