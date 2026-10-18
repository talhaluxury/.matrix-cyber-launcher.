package com.matrix.launcher.net

/** Outcome of one TCP connection attempt. Only what the OS actually reported is mapped here. */
enum class PortState(val label: String) {
    REACHABLE("REACHABLE"),
    REFUSED("REFUSED"),
    TIMED_OUT("TIMED OUT"),
    UNREACHABLE("UNREACHABLE"),
    ERROR("ERROR / UNKNOWN"),
}

data class PortResult(val port: Int, val state: PortState, val elapsedMs: Long, val detail: String?)

data class DiscoveredHost(
    val ip: String,
    /** Reverse-DNS name, or null when none could be resolved ("Unknown" in the UI). */
    val hostname: String?,
    /** How the host was detected, e.g. "ICMP/ECHO" or "TCP 443 REFUSED (host replied)". */
    val method: String,
    val rttMs: Long,
    val discoveredAtMs: Long,
    val isThisDevice: Boolean,
)

enum class DiagKind(val label: String) {
    CONNECTIVITY("CONNECTIVITY"),
    INTERNET("INTERNET CHECK"),
    DNS("DNS RESOLUTION"),
    LATENCY("TCP CONNECT LATENCY"),
    HOST_REACH("HOST REACHABILITY"),
}

enum class DiagOutcome { OK, FAIL, SKIPPED }

data class DiagResult(
    val kind: DiagKind,
    val outcome: DiagOutcome,
    /** Set ONLY when the test succeeded and actually measured something. */
    val valueMs: Long?,
    val message: String,
    val timestampMs: Long,
)

/** Snapshot of the active network as reported by Android. Fields are null when Android does not expose them. */
data class LocalNetInfo(
    val connected: Boolean,
    val transport: String,
    val osValidated: Boolean,
    val metered: Boolean?,
    val interfaceName: String?,
    val ipv4: String?,
    val prefix: Int?,
    val gateway: String?,
    val dnsServers: List<String>,
) {
    companion object {
        val OFFLINE = LocalNetInfo(false, "NONE", false, null, null, null, null, null, emptyList())
    }
}

// ---------- UI / report state (kept local, in memory) ----------

data class DiscoveryState(
    val running: Boolean = false,
    val range: String = "",
    val probed: Int = 0,
    val total: Int = 0,
    val hosts: List<DiscoveredHost> = emptyList(),
    val startedAtMs: Long = 0L,
    val finishedAtMs: Long? = null,
    val cancelled: Boolean = false,
    val error: String? = null,
) {
    val hasData: Boolean get() = startedAtMs != 0L
    val progress: Float get() = if (total <= 0) 0f else (probed.toFloat() / total).coerceIn(0f, 1f)
}

data class PortsState(
    val running: Boolean = false,
    val target: String = "",
    val resolved: String? = null,
    val total: Int = 0,
    val results: List<PortResult> = emptyList(),
    val startedAtMs: Long = 0L,
    val finishedAtMs: Long? = null,
    val cancelled: Boolean = false,
    val error: String? = null,
) {
    val hasData: Boolean get() = startedAtMs != 0L
    val progress: Float get() = if (total <= 0) 0f else (results.size.toFloat() / total).coerceIn(0f, 1f)
}

data class DiagState(
    val running: Boolean = false,
    val step: String? = null,
    val history: List<DiagResult> = emptyList(),
)

enum class NetOp { NONE, DISCOVERY, DIAGNOSTICS, PORTS }

/** Result of a blocking call that was given a hard deadline. */
sealed class TimedResult<out T> {
    data class Ok<T>(val value: T) : TimedResult<T>()
    data class Failed(val error: Throwable) : TimedResult<Nothing>()
    object TimedOut : TimedResult<Nothing>()
}
