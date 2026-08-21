package com.superflow.util

import android.os.SystemClock
import android.view.View

/**
 * Debounced click listener (#65/#29): guards against double-taps that would
 * otherwise fire the same action twice in quick succession (e.g. a check-in
 * racing its own animation). The [interval] default (500 ms) is short enough
 * not to feel laggy on deliberate repeated presses.
 */
fun View.onDebouncedClick(
    interval: Long = 500L,
    action: (View) -> Unit
) {
    setOnClickListener(object : View.OnClickListener {
        private var last = 0L
        override fun onClick(v: View) {
            val now = SystemClock.elapsedRealtime()
            if (now - last >= interval) {
                last = now
                action(v)
            }
        }
    })
}
