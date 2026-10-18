package com.matrix.launcher.net

import java.net.InetAddress
import java.util.Locale

/**
 * Real, individually labelled checks. Each result carries a measured value ONLY when the test succeeded.
 * DNS time, TCP connect latency and the internet check are three different things and are never merged.
 */
class NetDiagnostics(private val checker: PortChecker = PortChecker()) {

    fun connectivity(info: LocalNetInfo): DiagResult {
        val now = System.currentTimeMillis()
        if (!info.connected) {
            return DiagResult(DiagKind.CONNECTIVITY, DiagOutcome.FAIL, null, "NO ACTIVE NETWORK. WI-FI AND MOBILE DATA APPEAR OFF OR UNAVAILABLE.", now)
        }
        val validated = if (info.osValidated) "ANDROID VALIDATED INTERNET ACCESS" else "ANDROID HAS NOT VALIDATED INTERNET ACCESS (CAPTIVE PORTAL OR NO ROUTE?)"
        val metered = when (info.metered) {
            true -> ", METERED"
            false -> ", UNMETERED"
            null -> ""
        }
        return DiagResult(DiagKind.CONNECTIVITY, DiagOutcome.OK, null, "TRANSPORT ${info.transport}$metered. $validated.", now)
    }

    /** Resolves [host] and times only the resolver. Android may serve answers from its cache, so repeat runs can be faster. */
    suspend fun dns(host: String): DiagResult {
        val t0 = System.nanoTime()
        val r = Blocking.timed(DNS_TIMEOUT_MS) { InetAddress.getAllByName(host) }
        val ms = (System.nanoTime() - t0) / 1_000_000
        val now = System.currentTimeMillis()
        return when (r) {
            is TimedResult.Ok -> {
                val addrs = r.value.mapNotNull { it.hostAddress }.distinct()
                DiagResult(
                    DiagKind.DNS, DiagOutcome.OK, ms,
                    "$host -> ${addrs.take(4).joinToString(", ")}" + if (addrs.size > 4) " (+${addrs.size - 4} MORE)" else "",
                    now,
                )
            }
            is TimedResult.Failed -> DiagResult(
                DiagKind.DNS, DiagOutcome.FAIL, null,
                "DNS FAILURE: \"$host\" COULD NOT BE RESOLVED (NAME DOES NOT EXIST, DNS SERVER UNREACHABLE, OR LOOKUP BLOCKED).",
                now,
            )
            TimedResult.TimedOut -> DiagResult(
                DiagKind.DNS, DiagOutcome.FAIL, null,
                "DNS TIMEOUT: NO ANSWER WITHIN ${DNS_TIMEOUT_MS / 1000}S.",
                now,
            )
        }
    }

    /** TCP connect time to [host]:[port], [samples] attempts. Name resolution is done first and is NOT part of the time. */
    suspend fun latency(host: String, port: Int, samples: Int = 3): DiagResult {
        val addr: InetAddress = when (val r = Blocking.timed(DNS_TIMEOUT_MS) { InetAddress.getByName(host) }) {
            is TimedResult.Ok -> r.value
            is TimedResult.Failed -> return DiagResult(DiagKind.LATENCY, DiagOutcome.FAIL, null, "CANNOT MEASURE: DNS FAILURE FOR \"$host\".", System.currentTimeMillis())
            TimedResult.TimedOut -> return DiagResult(DiagKind.LATENCY, DiagOutcome.FAIL, null, "CANNOT MEASURE: DNS TIMEOUT FOR \"$host\".", System.currentTimeMillis())
        }
        val times = ArrayList<Long>()
        var lastState = PortState.ERROR
        var lastDetail: String? = null
        for (i in 0 until samples) {
            val res = checker.probe(addr, port, LATENCY_TIMEOUT_MS)
            lastState = res.state
            lastDetail = res.detail
            if (res.state == PortState.REACHABLE) times.add(res.elapsedMs)
        }
        val now = System.currentTimeMillis()
        if (times.isEmpty()) {
            val why = when (lastState) {
                PortState.REFUSED -> "CONNECTION REFUSED (HOST ANSWERED BUT NOTHING LISTENS ON PORT $port)"
                PortState.TIMED_OUT -> "TIMED OUT (BLOCKED, DROPPED OR HOST DOWN)"
                PortState.UNREACHABLE -> "NETWORK/HOST UNREACHABLE"
                else -> "CONNECT ERROR" + (lastDetail?.let { ": $it" } ?: "")
            }
            return DiagResult(DiagKind.LATENCY, DiagOutcome.FAIL, null, "NO SUCCESSFUL CONNECT TO $host:$port - $why", now)
        }
        val avg = times.sum() / times.size
        val msg = String.format(
            Locale.US, "TCP CONNECT TO %s:%d (%s): AVG %d MS, MIN %d, MAX %d, %d/%d SUCCEEDED",
            host, port, addr.hostAddress, avg, times.min(), times.max(), times.size, samples,
        )
        return DiagResult(DiagKind.LATENCY, DiagOutcome.OK, avg, msg, now)
    }

    /** Echo-style reachability. Hosts that drop ICMP/echo look "unreachable" here even though they are up. */
    suspend fun hostReach(host: String): DiagResult {
        val addr: InetAddress = when (val r = Blocking.timed(DNS_TIMEOUT_MS) { InetAddress.getByName(host) }) {
            is TimedResult.Ok -> r.value
            is TimedResult.Failed -> return DiagResult(DiagKind.HOST_REACH, DiagOutcome.FAIL, null, "DNS FAILURE FOR \"$host\" - CANNOT TEST.", System.currentTimeMillis())
            TimedResult.TimedOut -> return DiagResult(DiagKind.HOST_REACH, DiagOutcome.FAIL, null, "DNS TIMEOUT FOR \"$host\" - CANNOT TEST.", System.currentTimeMillis())
        }
        val t0 = System.nanoTime()
        val r = Blocking.timed(REACH_TIMEOUT_MS + 1500L) { addr.isReachable(REACH_TIMEOUT_MS.toInt()) }
        val ms = (System.nanoTime() - t0) / 1_000_000
        val now = System.currentTimeMillis()
        return when (r) {
            is TimedResult.Ok -> if (r.value) {
                DiagResult(DiagKind.HOST_REACH, DiagOutcome.OK, ms, "$host (${addr.hostAddress}) ANSWERED AN ECHO PROBE.", now)
            } else {
                DiagResult(
                    DiagKind.HOST_REACH, DiagOutcome.FAIL, null,
                    "NO ECHO REPLY FROM $host. THIS DOES NOT PROVE IT IS DOWN: MANY HOSTS AND FIREWALLS IGNORE ECHO PROBES.",
                    now,
                )
            }
            is TimedResult.Failed -> DiagResult(DiagKind.HOST_REACH, DiagOutcome.FAIL, null, "REACHABILITY CHECK ERROR: ${PortChecker.describe(r.error)}", now)
            TimedResult.TimedOut -> DiagResult(DiagKind.HOST_REACH, DiagOutcome.FAIL, null, "REACHABILITY CHECK TIMED OUT.", now)
        }
    }

    /**
     * Basic internet check: a TCP connect to a public IP literal (no DNS involved) plus Android's own validation flag.
     * Success means a path to that ONE address exists. It does not prove any given website or service works.
     */
    suspend fun internet(info: LocalNetInfo): DiagResult {
        val now = System.currentTimeMillis()
        if (!info.connected) return DiagResult(DiagKind.INTERNET, DiagOutcome.SKIPPED, null, "SKIPPED: NO ACTIVE NETWORK.", now)
        val addr = InetAddress.getByName(INTERNET_PROBE_IP) // literal, no lookup
        val res = checker.probe(addr, INTERNET_PROBE_PORT, LATENCY_TIMEOUT_MS)
        val osNote = if (info.osValidated) "ANDROID ALSO REPORTS THE NETWORK AS VALIDATED." else "ANDROID DOES NOT REPORT THE NETWORK AS VALIDATED."
        val ts = System.currentTimeMillis()
        return if (res.state == PortState.REACHABLE) {
            DiagResult(
                DiagKind.INTERNET, DiagOutcome.OK, null,
                "TCP CONNECT TO $INTERNET_PROBE_IP:$INTERNET_PROBE_PORT SUCCEEDED. $osNote THIS DOES NOT PROVE EVERY SITE OR SERVICE IS REACHABLE.",
                ts,
            )
        } else {
            DiagResult(
                DiagKind.INTERNET, DiagOutcome.FAIL, null,
                "TCP CONNECT TO $INTERNET_PROBE_IP:$INTERNET_PROBE_PORT FAILED (${res.state.label}). $osNote A FIREWALL OR CAPTIVE PORTAL MAY BE BLOCKING THIS ONE TARGET.",
                ts,
            )
        }
    }

    companion object {
        const val DNS_TIMEOUT_MS = 5000L
        const val LATENCY_TIMEOUT_MS = 3000
        const val REACH_TIMEOUT_MS = 2000L
        const val INTERNET_PROBE_IP = "1.1.1.1"
        const val INTERNET_PROBE_PORT = 443
    }
}
