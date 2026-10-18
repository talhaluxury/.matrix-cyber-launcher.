package com.matrix.launcher.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.ConnectException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger

/**
 * Rate-limited host discovery for a private LAN range the user owns or may test.
 *
 * Per address: one echo probe ([InetAddress.isReachable]: ICMP where Android allows it, otherwise a TCP echo
 * attempt), then, only if that failed, a short TCP connect to three well-known ports. A host counts as found
 * when something answered (connect accepted, or a refusal that proves the host replied). Silent hosts,
 * firewalled hosts, sleeping phones and Wi-Fi client isolation therefore stay invisible. MAC addresses are
 * not readable by normal apps on Android 10+, so none is ever shown.
 */
class NetworkDiscovery(
    private val concurrency: Int = 24,
    private val echoTimeoutMs: Int = 500,
    private val tcpTimeoutMs: Int = 350,
) {
    suspend fun run(
        range: Ipv4Range,
        selfIp: Long?,
        onProgress: (probed: Int, total: Int) -> Unit,
        onHost: (DiscoveredHost) -> Unit,
    ) {
        val addresses = range.hosts()
        val total = addresses.size
        val probed = AtomicInteger(0)
        val gate = Semaphore(concurrency)
        coroutineScope {
            for (a in addresses) {
                launch {
                    val found = gate.withPermit { probeHost(a, selfIp) }
                    if (found != null) {
                        val named = if (found.isThisDevice) found else found.copy(hostname = reverseName(found.ip))
                        onHost(named)
                    }
                    onProgress(probed.incrementAndGet(), total)
                }
            }
        }
    }

    private suspend fun probeHost(addr: Long, selfIp: Long?): DiscoveredHost? {
        val ip = NetValidation.formatIpv4(addr)
        if (selfIp != null && addr == selfIp) {
            return DiscoveredHost(ip, null, "THIS DEVICE (LOCAL INTERFACE)", 0L, System.currentTimeMillis(), true)
        }
        return withContext(Dispatchers.IO) {
            val inet = InetAddress.getByName(ip) // literal address: no DNS lookup
            val t0 = System.nanoTime()
            var method: String? = null
            if (runCatching { inet.isReachable(echoTimeoutMs) }.getOrDefault(false)) {
                method = "ICMP/ECHO"
            } else {
                for (port in FALLBACK_PORTS) {
                    ensureActive()
                    val state = tcpOnce(inet, port)
                    if (state == PortState.REACHABLE) {
                        method = "TCP $port ACCEPTED"
                        break
                    }
                    if (state == PortState.REFUSED) {
                        method = "TCP $port REFUSED (HOST REPLIED)"
                        break
                    }
                }
            }
            if (method == null) null
            else DiscoveredHost(ip, null, method, (System.nanoTime() - t0) / 1_000_000, System.currentTimeMillis(), false)
        }
    }

    private fun tcpOnce(inet: InetAddress, port: Int): PortState {
        val s = Socket()
        return try {
            s.connect(InetSocketAddress(inet, port), tcpTimeoutMs)
            PortState.REACHABLE
        } catch (e: ConnectException) {
            PortClassifier.classify(e)
        } catch (e: Exception) {
            PortClassifier.classify(e)
        } finally {
            try {
                s.close()
            } catch (_: Exception) {
            }
        }
    }

    /** Reverse DNS with a hard deadline. Returns null when there is no name, so the UI can show "Unknown". */
    private suspend fun reverseName(ip: String): String? {
        val r = Blocking.timed(REVERSE_TIMEOUT_MS) { InetAddress.getByName(ip).hostName }
        val name = (r as? TimedResult.Ok<String>)?.value ?: return null
        return if (name.isBlank() || name == ip) null else name
    }

    companion object {
        val FALLBACK_PORTS = listOf(443, 80, 53)
        const val REVERSE_TIMEOUT_MS = 1500L
    }
}
