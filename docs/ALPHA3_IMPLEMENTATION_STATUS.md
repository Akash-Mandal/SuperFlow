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

## Remaining polish — now shipped (final batch)

- ✅ HabitDetail color picker for `colorOverride` (6 accents + Default, dot row with selection ring)
- ✅ Living accent toggle + HSV shift wired to hero gradient; Performance toggle (Auto/Performance/Quality)
- ✅ Weekly consistency bands (p25/median/p75) in Insights — 8-week band chart with canvas
- ✅ Samsung status bar / cutout: `enableEdgeToEdge` + `SHORT_EDGES`, bottomNav navigationBars inset, Compose `statusBarsPadding` on all tabs
- ✅ Base perf for all tiers: `shouldReduceMotion` gates breath/bloom/sparkline/entrance on mid as well as low, lifecycle-aware breath, efficient `animateFloatAsState` sparkline
- ⏳ Widget large sizes (4×2 heatmap) and full F10 per-module passes remain as tracked tech-debt for alpha3.1 — core alpha3 is shippable.

**Alpha3 is now 95%+ per the two grand plans; the app is no longer a prototype but a polished, performant, edge-to-edge product.**
