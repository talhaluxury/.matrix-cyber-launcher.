package com.matrix.launcher.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Plain TCP connect checks. A successful connect only proves that a connection was accepted at that moment;
 * nothing is sent and nothing is read, so no service or version is ever claimed.
 */
class PortChecker(
    private val connectTimeoutMs: Int = 2500,
    private val concurrency: Int = 6,
) {
    /** One blocking connect attempt. Always closes the socket. Never throws (apart from cancellation of the caller). */
    suspend fun probe(address: InetAddress, port: Int, timeoutMs: Int = connectTimeoutMs): PortResult = coroutineScope {
        val socket = Socket()
        val start = System.nanoTime()
        val worker = async(Dispatchers.IO) {
            try {
                socket.connect(InetSocketAddress(address, port), timeoutMs)
                null
            } catch (e: Exception) {
                e
            }
        }
        try {
            val error = worker.await()
            val ms = (System.nanoTime() - start) / 1_000_000
            PortResult(port, PortClassifier.classify(error), ms, error?.let { describe(it) })
        } finally {
            // Also runs when the caller is cancelled: closing the socket aborts the blocked connect().
            try {
                socket.close()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Resolves [host] (a literal IP needs no DNS) and tests every port with bounded concurrency.
     * Results are delivered through [onResult] in completion order. Returns an error text when the host
     * could not be resolved, otherwise null.
     */
    suspend fun check(
        host: String,
        ports: List<Int>,
        onResolved: (String) -> Unit,
        onResult: (PortResult) -> Unit,
    ): String? {
        val address: InetAddress = when (val r = Blocking.timed(RESOLVE_TIMEOUT_MS) { InetAddress.getByName(host) }) {
            is TimedResult.Ok -> r.value
            is TimedResult.Failed -> return "DNS FAILURE: COULD NOT RESOLVE \"$host\""
            TimedResult.TimedOut -> return "DNS TIMEOUT: NO ANSWER FOR \"$host\" WITHIN ${RESOLVE_TIMEOUT_MS / 1000}S"
        }
        onResolved(address.hostAddress ?: host)
        val gate = Semaphore(concurrency)
        coroutineScope {
            for (p in ports) {
                launch {
                    val result = gate.withPermit { probe(address, p) }
                    onResult(result)
                }
            }
        }
        return null
    }

    companion object {
        const val RESOLVE_TIMEOUT_MS = 5000L

        internal fun describe(e: Throwable): String {
            val m = e.message?.take(80)
            return if (m.isNullOrBlank()) e.javaClass.simpleName else m
        }
    }
}
