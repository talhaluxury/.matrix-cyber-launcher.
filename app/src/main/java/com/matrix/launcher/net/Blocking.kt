package com.matrix.launcher.net

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Runs a blocking call (DNS, reverse DNS) on the IO dispatcher with a hard deadline for the CALLER.
 * Android's resolver cannot be interrupted, so on timeout the call is abandoned: it finishes on its own
 * later and its result is dropped. Cancelling the caller returns immediately as well.
 */
internal object Blocking {
    private val detached = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun <T> timed(timeoutMs: Long, block: () -> T): TimedResult<T> {
        val work = detached.async<TimedResult<T>> {
            try {
                TimedResult.Ok(block())
            } catch (e: Exception) {
                TimedResult.Failed(e)
            }
        }
        return withTimeoutOrNull(timeoutMs) { work.await() } ?: TimedResult.TimedOut
    }
}
