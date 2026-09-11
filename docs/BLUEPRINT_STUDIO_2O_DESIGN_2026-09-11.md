# Blueprint Studio 2.0 — Design

## Intent (from docs + old code)
"Give SuperFlow your dream + materials. It understands, reconciles with your
rules, designs a phased personal SuperFlow, builds phase 1 now, schedules the
rest, verifies real state, and everything stays editable, reversible, cited."

## Why 1.0 confused
One endless scroll showing everything at once (sources, instructions, ledger,
auto-reinforce dump, run, versions, report) in raw jargon (Ledger, Compile,
Coverage, Phase 0, plannedCommand). No steps, no progress, empty and built
states look identical.

## 2.0 shape — one project, five steps, plain words
1. **Dream** — one sentence outcome + sliders: daily minutes, duration,
   how many habits. (was: UserIntent form)
2. **Materials** — add text/PDF/paste; each shows pages covered. (was: Sources)
3. **Rules** — what to build/ignore, what never changes, who wins conflicts.
   (was: Instructions, now visibly ABOVE materials in authority)
4. **Your plan** — phased cards (Phase 1 now, rest later), each phase lists
   its 2–3 habits with Tiny Starts; accept/edit per phase. (was: Ledger)
5. **Build & grow** — big Build button for phase 1, progress bar, then a
   timeline of scheduled phases + undo. (was: Run/Build + auto dump)

Step dots on top, Back/Next, state persists per project. Steps unlock in
order; revisiting earlier steps marks later ones stale (re-plan, never
silent).

## Keep vs throw
KEEP (working, depended-on): `bp_*` tables + models + Repository/Cursors +
Serial/DataPolicy, 4 capability names (`create_progressive_blueprint`,
`evaluate_blueprint_phase`, `advance_blueprint_phase`,
`trigger_auto_reinforce`) so the Main Brain prompt keeps working,
`PdfText` (Studio file attach uses it), `AutoReinforceWorker`,
entry points (Studio menu/chip/shortcut/Project cards).
THROW: `BlueprintActivity` (rewrite as stepped wizard), `Compiler` V1
(flood factory), `CompilerV2` (rewritten as small clean `Planner` with the
same proven behavior: intent → themes → dedup → progressive phases).
No DB migration: 2.0 reads/writes the same tables, so existing missions
open in the new UI.
