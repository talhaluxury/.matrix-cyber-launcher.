package com.matrix.launcher.net

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Builds the plain-text report. Pure function of the data it is given: nothing is invented or filled in. */
object ReportFormatter {
    fun stamp(ms: Long, tz: TimeZone = TimeZone.getDefault()): String {
        val f = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        f.timeZone = tz
        return f.format(Date(ms))
    }

    fun hasContent(d: DiscoveryState, p: PortsState, g: DiagState): Boolean = d.hasData || p.hasData || g.history.isNotEmpty()

    fun format(
        nowMs: Long,
        info: LocalNetInfo,
        discovery: DiscoveryState,
        ports: PortsState,
        diag: DiagState,
        tz: TimeZone = TimeZone.getDefault(),
    ): String {
        val sb = StringBuilder()
        sb.appendLine("MATRIX CYBER NETWORK TOOLKIT - LOCAL REPORT")
        sb.appendLine("Generated: ${stamp(nowMs, tz)}")
        sb.appendLine("Stored only on this device. Nothing was uploaded.")
        sb.appendLine()
        sb.appendLine("== THIS DEVICE'S CONNECTION (as reported by Android) ==")
        sb.appendLine("Connected: ${if (info.connected) "yes" else "no"}")
        sb.appendLine("Transport: ${info.transport}")
        sb.appendLine("IPv4: ${info.ipv4 ?: "Unknown"}${info.prefix?.let { "/$it" } ?: ""}")
        sb.appendLine("Gateway: ${info.gateway ?: "Unknown"}")
        sb.appendLine("DNS servers: ${if (info.dnsServers.isEmpty()) "Unknown" else info.dnsServers.joinToString(", ")}")
        sb.appendLine()

        if (discovery.hasData) {
            sb.appendLine("== LOCAL NETWORK DISCOVERY ==")
            sb.appendLine("Range: ${discovery.range}")
            sb.appendLine("Started: ${stamp(discovery.startedAtMs, tz)}")
            sb.appendLine("Finished: ${discovery.finishedAtMs?.let { stamp(it, tz) } ?: "still running"}")
            sb.appendLine("Status: ${if (discovery.cancelled) "CANCELLED (partial)" else if (discovery.error != null) "ERROR: ${discovery.error}" else if (discovery.running) "RUNNING" else "COMPLETE"}")
            sb.appendLine("Addresses probed: ${discovery.probed} of ${discovery.total}")
            sb.appendLine("Devices found: ${discovery.hosts.size}")
            for (h in discovery.hosts) {
                sb.appendLine(
                    "  ${h.ip.padEnd(15)}  host=${h.hostname ?: "Unknown"}  mac=Unknown  via=${h.method}  rtt=${if (h.isThisDevice) "n/a" else "${h.rttMs}ms"}  at=${stamp(h.discoveredAtMs, tz)}",
                )
            }
            sb.appendLine("Note: results can be incomplete. Firewalls, Wi-Fi client isolation, Android restrictions and sleeping devices hide hosts. MAC addresses and OS types cannot be read reliably, so they are not reported.")
            sb.appendLine()
        }

        if (diag.history.isNotEmpty()) {
            sb.appendLine("== NETWORK DIAGNOSTICS (oldest first) ==")
            for (r in diag.history.sortedBy { it.timestampMs }) {
                val v = r.valueMs?.let { " [${it} ms]" } ?: ""
                sb.appendLine("  ${stamp(r.timestampMs, tz)}  ${r.kind.label}  ${r.outcome}$v  ${r.message}")
            }
            sb.appendLine()
        }

        if (ports.hasData) {
            sb.appendLine("== TCP PORT REACHABILITY ==")
            sb.appendLine("Target: ${ports.target}${ports.resolved?.let { " ($it)" } ?: ""}")
            sb.appendLine("Started: ${stamp(ports.startedAtMs, tz)}")
            sb.appendLine("Finished: ${ports.finishedAtMs?.let { stamp(it, tz) } ?: "still running"}")
            sb.appendLine("Status: ${if (ports.cancelled) "CANCELLED (partial)" else if (ports.error != null) "ERROR: ${ports.error}" else if (ports.running) "RUNNING" else "COMPLETE"}")
            for (r in ports.results.sortedBy { it.port }) {
                sb.appendLine("  ${r.port.toString().padEnd(6)} ${r.state.label.padEnd(16)} ${r.elapsedMs} ms${r.detail?.let { "  ($it)" } ?: ""}")
            }
            sb.appendLine("Summary: ${summary(ports.results)}")
            sb.appendLine("Note: REACHABLE only means a TCP connection was accepted at that moment. The service, its version and its security status are NOT determined.")
            sb.appendLine()
        }
        return sb.toString()
    }

    fun summary(results: List<PortResult>): String {
        if (results.isEmpty()) return "no results"
        val parts = PortState.values().mapNotNull { st ->
            val n = results.count { it.state == st }
            if (n > 0) "$n ${st.label.lowercase()}" else null
        }
        return "${results.size} tested: " + parts.joinToString(", ")
    }
}
