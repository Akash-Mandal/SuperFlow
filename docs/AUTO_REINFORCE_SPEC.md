# Auto Reinforce — In-App Scheduled Auto-Change System

> Part of Blueprint Studio Recreation (Plan B). Implements "what/when/where/how auto-change + chat trigger".

## Model
`blueprint_auto_plan(id, projectId, phaseIndex, whatJson, whenExpr, whereKind, howOp, conditionJson, status, createdAt, appliedAt)`

- **whatJson**: plannedCommand JSON (create_habit/update_habit/create_flow etc)
- **whenExpr**: `WEEK:1`, `WEEK:3`, or `WEEK:6T07:00`, or `AFTER_CONSISTENCY:60`
- **whereKind**: habit/system/flow/goal/identity
- **howOp**: ADD/UPGRADE/REMOVE/REARRANGE/EDIT
- **conditionJson**: `{"minConsistency":60,"maxMisses":2}`
- **status**: PENDING/APPLIED/FAILED/SKIPPED

## Storage
- Table `blueprint_auto_plan` (v5 migration), index on projectId
- Prefs `autoReinforceEnabled` (false) + `autoReinforceMode` (propose/auto)
- Proactive suggestion type GROWTH for propose mode

## Triggers
1. **Time-based**: `AutoReinforceWorker` every 6h checks `whenExpr` week <= todayWeek
2. **Chat**: `Coordinator` regex `reinforce|next phase` → `trigger_auto_reinforce` capability
3. **GrowthEngine**: `evaluateWeekly` on upgradeDay (Monday) checks consistency + `conditionJson`
4. **Blueprint UI**: button "Trigger pending now" in `autoReinforceSection`

## Execution
- **Propose (default)**: inserts `proactive_suggestion` ("Auto Reinforce ready: phase X — apply create_habit") — user taps Apply or says "reinforce now"
- **Auto**: `CommandBus.execute(cmd, args, Actor.SYSTEM, group)` + snapshot if `autoSnapshot` + record audit + mark APPLIED/FAILED

## UI
- BlueprintActivity section `AUTO REINFORCE` shows mode + pending count + cards Phase N · WEEK:X · whereKind · status + trigger button
- AI Engine settings toggle + mode chips (Propose/Auto)

## Verification
- Compile large plan (>20 reqs) → phase0 2-3 habits, future phases as PENDING rows
- Chat "reinforce now" → applies next 5 pending (LIMIT 5)
- Worker propose → suggestion appears in ProactiveAi list
- Undo whole build covers auto-applied habits via grouped audit
