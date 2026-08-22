package com.superflow.ai

/**
 * The structured result of one agent turn.
 *
 * AI_ENGINE_PLAN §6.3 step 11 ends every execution pass by returning "a
 * structured result to the coordinator for a user-facing summary" — this is
 * that result. It is what [Agent.send] hands back, and the only thing the
 * Studio UI reads to decide what to show and whether the turn changed data.
 *
 * @property reply The user-facing summary written into the conversation. Always
 *   populated: the runtime falls back to "Nothing changed." rather than empty.
 * @property actions One message per command that actually executed and
 *   succeeded. Empty means the turn was conversational, was blocked, or failed —
 *   the Studio uses `actions.isNotEmpty()` to decide whether anything happened.
 * @property group The grouped-undo id, set when a turn ran more than one
 *   command so the whole plan reverts as a unit (AI_ENGINE_PLAN §6.5: "Bulk
 *   plans produce a single grouped undo where possible"). Null for single
 *   commands, which are undone by their own audit record.
 * @property route Which lane produced the reply — "local" (Local Coordinator),
 *   "cloud" (Main Brain), "local-fallback" (cloud failed, rules answered) or
 *   "rate-limited". Stored verbatim as the AiMessage `meta`, so it is a
 *   non-null String.
 * @property error Non-null when the turn failed or the cloud call errored, even
 *   if [reply] still carries readable text. Null on a clean turn.
 */
data class Outcome(
    val reply: String,
    val actions: List<String> = emptyList(),
    val group: String? = null,
    val route: String = "",
    val error: String? = null,
)
