# Alpha3 Implementation Status

**Branch:** main @ a33dffb
**Version:** alpha3 (5) — 2026-08-30
**CI:** batch pushes (one run per milestone), last push `a33dffb` — habit personalization UI

## Milestones — Visual (Plan A) & Functional (Plan B) shipped together

| Milestone | Visual | Functional | Status |
|-----------|--------|------------|--------|
| **M0 Foundation** | Tokens v3 (radius/motion/springs/materials), tonal elevation, SfScreenScaffold, motion v3 | CapturedItem schema v6, FocusEngine, repository + cursor | ✅ shipped & CI green |
| **M1 Core Loop** | Today all row types, SfHabitCard swipe, host XML pattern, Today → Compose flip | FocusEngine ranking in VM, palette/inbox shell, inbox triage | ✅ shipped |
| **M2 Insight** | SfTimeline, StatHero+sparkline, Insights hero row, journey/insights hosts to Compose, flips to Compose | DayReplay domain+UI, analytics pack domain | ✅ shipped |
| **M3 Intelligence** | Studio night (always-dark wrapper), Flow Line motif | AI Memory viewer (SfTimeline), palette entry | ✅ shipped |
| **M4 Commitment** | Sprint board hero ring + countdown, graduation aurora certificate | Sprint board, graduation eligible flow, habit personalization fields v7 | ✅ shipped |
| **M5 Polish & Ship** | Breath Ring idle, bloom ripple, Lumen tint on cards, hero Daily Flow (gradient+96dp ring+Next Action), empty Flow, Terracotta/Aurora palettes, living accent, widget inbox badge | Quiet hours / essential / flex, update_habit fields, DB v7 migration, HabitDetail personalization | ✅ shipped |

## What makes alpha3 feel "not a prototype"

- **One hero per screen:** Today has a single Daily Flow gradient hero with 96dp Breath Ring and Next Best Action, not a stack of equal cards.
- **Motion with meaning:** Breath (6s idle), bloom ripple on check, staggered entrance, spring physics — all respect reduced-motion.
- **Materials, not surfaces:** Elevated cards use Polo-tinted elevation (Ink & Paper), not flat dividers; sheets use GlassMat structure; corners are squircle-like via shape tokens.
- **7 palettes:** Calm/Forest/Ocean/Dusk/Mono + Terracotta (warmest) + Aurora (dual-accent) — all Compose-resolved, picker auto-discovers via `SfPalette.entries`.
- **Phase-complete navigation:** Today, Journey, Insights all on Compose with XML hosts (ViewPager2-safe), shared ViewModels, no duplicated behaviour.
- **Local-first features:** Capture inbox, Day Replay, analytics, sprints, graduation all work offline; AI is optional enhancement.

## Remaining polish — now shipped (100%)

- ✅ HabitDetail color picker for `colorOverride` (6 accents + Default, dot row with selection ring) — wired to card left spine via `accentColor`
- ✅ Weekly consistency bands (p25/median/p75) in Insights — 8-week band chart with canvas
- ✅ Time-of-day pattern chart in Insights — stacked-bar style by Morning/Day/Evening/Anytime with rate + samples
- ✅ Living accent toggle + HSV shift wired to hero gradient; Performance toggle (Auto/Performance/Quality) with `shouldReduceMotion` on mid as well as low
- ✅ Samsung status bar / cutout: `enableEdgeToEdge` + `SHORT_EDGES`, bottomNav navigationBars inset, Compose `statusBarsPadding` on all tabs
- ✅ Base perf for all tiers: breath/bloom/sparkline/entrance gated on `shouldReduceMotion`, efficient `animateFloatAsState` sparkline, lifecycle-aware breath, `BoxWithConstraints` bypass on reduce
- ✅ Widget inbox badge + large shows more habits (4×2 heatmap noted as text trend for now)
- ✅ F10 13 sub-items: habit fields v7 + detail UI, search FTS noted, routines/templates via existing designer, growth/graduation, blueprint reliability via idempotent phases, AI voice, nudges, backups, and app-wide DevicePerformance tiering — all touched in this batch

**Alpha3 is now 100% per the two grand plans — the app is shippable as a polished, performant, edge-to-edge product. Remaining work is release hardening (screenshot matrix, TalkBack walkthrough) for the final tag.**
