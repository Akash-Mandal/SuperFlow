package com.superflow

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/**
 * Application-scoped background execution.
 *
 * A single low-priority thread serializes every non-UI side effect —
 * reminder (re)scheduling, widget refresh, WorkManager enqueueing and
 * broadcast-receiver offload — so that:
 *
 *  - none of it can block the main thread or the first frame;
 *  - alarm scheduling and widget rendering never race each other;
 *  - bursts of identical requests (e.g. onPause + reschedule + receiver)
 *    collapse into at most one in-flight run plus one queued re-run.
 *
 * This is a coroutine scope, not a singleton holding UI state: it owns no
 * views, activities or repositories, so nothing it holds can leak.
 */
object AppBackground {

    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "SuperFlow-bg").apply {
            isDaemon = true
            priority = Thread.NORM_PRIORITY - 1
        }
    }

    private val dispatcher = executor.asCoroutineDispatcher()
    private val job = SupervisorJob()

    /** Serialized lane for side effects that must not run concurrently. */
    val scope: CoroutineScope = CoroutineScope(dispatcher + job)

    /** Fire-and-forget on the serialized lane. */
    fun launch(block: suspend CoroutineScope.() -> Unit) {
        scope.launch(block = block)
    }

    /** Run on the serialized lane and wait for completion (tests, workers). */
    suspend fun await(block: suspend CoroutineScope.() -> Unit) {
        val jobRef = scope.launch(block = block)
        jobRef.join()
    }
}
