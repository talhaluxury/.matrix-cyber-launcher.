package com.matrix.launcher.net

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Owns the three network tools, their state and the single "active operation" slot.
 * Only ONE operation can run at a time, so scans can never overlap or be started twice.
 * Everything stays in memory on the device; a file is written only when the user asks for a report.
 * Must be used from the main thread (the scope's dispatcher): state updates and callbacks rely on that.
 */
class NetToolkit(context: Context, private val scope: CoroutineScope) {
    private val app = context.applicationContext
    private val reader = LocalNetReader(app)
    private val checker = PortChecker()
    private val discoveryEngine = NetworkDiscovery()
    private val diagnostics = NetDiagnostics(checker)

    private val _op = MutableStateFlow(NetOp.NONE)
    val activeOp: StateFlow<NetOp> = _op.asStateFlow()
    private val _discovery = MutableStateFlow(DiscoveryState())
    val discovery: StateFlow<DiscoveryState> = _discovery.asStateFlow()
    private val _ports = MutableStateFlow(PortsState())
    val ports: StateFlow<PortsState> = _ports.asStateFlow()
    private val _diag = MutableStateFlow(DiagState())
    val diag: StateFlow<DiagState> = _diag.asStateFlow()

    /** Receives progress / result lines (the terminal subscribes to this). */
    var onLog: (String) -> Unit = {}

    private var job: Job? = null

    fun localInfo(): LocalNetInfo = reader.read()

    fun suggestedRange(): Ipv4Range? {
        val i = reader.read()
        val ip = i.ipv4?.let { NetValidation.parseIpv4(it) } ?: return null
        return NetValidation.suggestRange(ip, i.prefix ?: 24)
    }

    // ---------- discovery ----------

    /** Returns an error message, or null when the scan was started. */
    fun startDiscovery(rangeText: String): String? {
        if (_op.value != NetOp.NONE) return "ANOTHER OPERATION IS RUNNING. USE SCAN CANCEL FIRST."
        val range = NetValidation.parseCidr(rangeText)
            ?: return "INVALID RANGE. USE CIDR LIKE 192.168.1.0/24 (PREFIX /22 TO /30)."
        if (!NetValidation.isPrivateOrLinkLocal(range.network)) {
            return "ONLY PRIVATE LAN RANGES ARE ALLOWED (10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16, 169.254.0.0/16)."
        }
        val info = reader.read()
        if (!info.connected) return "NO ACTIVE NETWORK CONNECTION."
        val selfIp = info.ipv4?.let { NetValidation.parseIpv4(it) }
        val started = System.currentTimeMillis()
        _op.value = NetOp.DISCOVERY
        _discovery.value = DiscoveryState(running = true, range = range.toString(), total = range.hostCount, startedAtMs = started)
        onLog("DISCOVERY STARTED: $range (${range.hostCount} ADDRESSES, RATE-LIMITED).")
        job = scope.launch {
            try {
                discoveryEngine.run(
                    range, selfIp,
                    onProgress = { p, _ -> _discovery.update { it.copy(probed = p) } },
                    onHost = { h ->
                        _discovery.update { st -> st.copy(hosts = (st.hosts + h).sortedBy { x -> NetValidation.parseIpv4(x.ip) ?: 0L }) }
                    },
                )
                val done = _discovery.value
                _discovery.update { it.copy(running = false, finishedAtMs = System.currentTimeMillis()) }
                onLog("DISCOVERY COMPLETE: ${done.hosts.size} DEVICE(S) ANSWERED. MAY BE INCOMPLETE (FIREWALLS, CLIENT ISOLATION, SLEEPING DEVICES).")
            } catch (e: CancellationException) {
                _discovery.update { it.copy(running = false, cancelled = true, finishedAtMs = System.currentTimeMillis()) }
                onLog("DISCOVERY CANCELLED (PARTIAL RESULTS KEPT).")
                throw e
            } catch (e: Exception) {
                _discovery.update { it.copy(running = false, error = PortChecker.describe(e), finishedAtMs = System.currentTimeMillis()) }
                onLog("DISCOVERY ERROR: ${PortChecker.describe(e)}")
            } finally {
                _op.value = NetOp.NONE
                job = null
            }
        }
        return null
    }

    // ---------- port check ----------

    fun startPortCheck(host: String, portList: List<Int>): String? {
        if (_op.value != NetOp.NONE) return "ANOTHER OPERATION IS RUNNING. USE SCAN CANCEL FIRST."
        val target = host.trim()
        if (!NetValidation.isValidTarget(target)) return "INVALID TARGET. ENTER A HOST NAME OR IPV4 ADDRESS."
        if (portList.isEmpty()) return "NO PORTS SELECTED."
        if (portList.size > NetValidation.MAX_PORTS) return "TOO MANY PORTS (MAX ${NetValidation.MAX_PORTS})."
        val started = System.currentTimeMillis()
        _op.value = NetOp.PORTS
        _ports.value = PortsState(running = true, target = target, total = portList.size, startedAtMs = started)
        onLog("PORT CHECK STARTED: $target, ${portList.size} PORT(S).")
        job = scope.launch {
            try {
                val err = checker.check(
                    target, portList,
                    onResolved = { ip -> _ports.update { it.copy(resolved = ip) } },
                    onResult = { r ->
                        _ports.update { st -> st.copy(results = (st.results + r).sortedBy { x -> x.port }) }
                        onLog("  PORT ${r.port}: ${r.state.label} (${r.elapsedMs} MS)")
                    },
                )
                val st = _ports.value
                _ports.update { it.copy(running = false, error = err, finishedAtMs = System.currentTimeMillis()) }
                onLog(if (err != null) "PORT CHECK FAILED: $err" else "PORT CHECK DONE: ${ReportFormatter.summary(st.results)}. REACHABLE ONLY MEANS A CONNECTION WAS ACCEPTED.")
            } catch (e: CancellationException) {
                _ports.update { it.copy(running = false, cancelled = true, finishedAtMs = System.currentTimeMillis()) }
                onLog("PORT CHECK CANCELLED (PARTIAL RESULTS KEPT).")
                throw e
            } catch (e: Exception) {
                _ports.update { it.copy(running = false, error = PortChecker.describe(e), finishedAtMs = System.currentTimeMillis()) }
                onLog("PORT CHECK ERROR: ${PortChecker.describe(e)}")
            } finally {
                _op.value = NetOp.NONE
                job = null
            }
        }
        return null
    }

    // ---------- diagnostics ----------

    /** [kind] == null runs the full sequence. [host] is used by DNS, latency and reachability. */
    fun startDiagnostics(host: String, kind: DiagKind?): String? {
        if (_op.value != NetOp.NONE) return "ANOTHER OPERATION IS RUNNING. USE SCAN CANCEL FIRST."
        val target = host.trim()
        val needsHost = kind == null || kind == DiagKind.DNS || kind == DiagKind.LATENCY || kind == DiagKind.HOST_REACH
        if (needsHost && !NetValidation.isValidTarget(target)) return "INVALID HOST. ENTER A HOST NAME OR IPV4 ADDRESS."
        val steps = if (kind == null) {
            listOf(DiagKind.CONNECTIVITY, DiagKind.INTERNET, DiagKind.DNS, DiagKind.LATENCY, DiagKind.HOST_REACH)
        } else listOf(kind)
        _op.value = NetOp.DIAGNOSTICS
        _diag.update { it.copy(running = true, step = null) }
        job = scope.launch {
            try {
                for (k in steps) {
                    _diag.update { it.copy(step = k.label) }
                    val info = reader.read()
                    val res = when {
                        k != DiagKind.CONNECTIVITY && k != DiagKind.INTERNET && !info.connected && kind == null ->
                            DiagResult(k, DiagOutcome.SKIPPED, null, "SKIPPED: NO ACTIVE NETWORK.", System.currentTimeMillis())
                        k == DiagKind.CONNECTIVITY -> diagnostics.connectivity(info)
                        k == DiagKind.INTERNET -> diagnostics.internet(info)
                        k == DiagKind.DNS -> diagnostics.dns(target)
                        k == DiagKind.LATENCY -> diagnostics.latency(target, 443)
                        else -> diagnostics.hostReach(target)
                    }
                    _diag.update { st -> st.copy(history = (listOf(res) + st.history).take(MAX_HISTORY)) }
                    val v = res.valueMs?.let { " [$it MS]" } ?: ""
                    onLog("  ${res.kind.label}: ${res.outcome}$v - ${res.message}")
                }
                onLog("DIAGNOSTICS DONE.")
            } catch (e: CancellationException) {
                onLog("DIAGNOSTICS CANCELLED.")
                throw e
            } catch (e: Exception) {
                onLog("DIAGNOSTICS ERROR: ${PortChecker.describe(e)}")
            } finally {
                _diag.update { it.copy(running = false, step = null) }
                _op.value = NetOp.NONE
                job = null
            }
        }
        return null
    }

    fun clearDiagHistory() {
        if (!_diag.value.running) _diag.update { it.copy(history = emptyList()) }
    }

    // ---------- control ----------

    fun cancel(): Boolean {
        val j = job ?: return false
        j.cancel()
        return true
    }

    /** Used when the launcher goes to the background: unattended scans are stopped. */
    fun cancelIfRunning() {
        if (_op.value != NetOp.NONE) {
            onLog("OPERATION STOPPED: LAUNCHER LEFT THE FOREGROUND.")
            cancel()
        }
    }

    // ---------- text output for the terminal ----------

    fun statusLines(): List<String> {
        val i = reader.read()
        if (!i.connected) return listOf("NETWORK: OFFLINE (NO ACTIVE NETWORK)")
        return listOf(
            "TRANSPORT: ${i.transport}${if (i.metered == true) " (METERED)" else ""}",
            "ANDROID INTERNET VALIDATION: ${if (i.osValidated) "YES" else "NO"}",
            "INTERFACE: ${i.interfaceName ?: "UNKNOWN"}",
            "IPV4: ${i.ipv4 ?: "UNKNOWN"}${i.prefix?.let { "/$it" } ?: ""}",
            "GATEWAY: ${i.gateway ?: "UNKNOWN"}",
            "DNS: ${if (i.dnsServers.isEmpty()) "UNKNOWN" else i.dnsServers.joinToString(", ")}",
            "NOTE: SSID/BSSID ARE NOT READ (WOULD NEED LOCATION PERMISSION, WHICH THIS APP DOES NOT REQUEST).",
        )
    }

    fun scanStatusLines(): List<String> {
        val op = _op.value
        if (op == NetOp.NONE) {
            val d = _discovery.value
            val p = _ports.value
            val out = ArrayList<String>()
            out.add("NO OPERATION RUNNING.")
            if (d.hasData) out.add("LAST DISCOVERY: ${d.range}, ${d.hosts.size} FOUND, ${if (d.cancelled) "CANCELLED" else "DONE"}")
            if (p.hasData) out.add("LAST PORT CHECK: ${p.target}, ${ReportFormatter.summary(p.results)}${if (p.cancelled) " (CANCELLED)" else ""}")
            return out
        }
        return when (op) {
            NetOp.DISCOVERY -> _discovery.value.let { listOf("DISCOVERY RUNNING: ${it.probed}/${it.total} PROBED, ${it.hosts.size} FOUND") }
            NetOp.PORTS -> _ports.value.let { listOf("PORT CHECK RUNNING: ${it.target}, ${it.results.size}/${it.total} PORTS DONE") }
            NetOp.DIAGNOSTICS -> listOf("DIAGNOSTICS RUNNING: ${_diag.value.step ?: "STARTING"}")
            NetOp.NONE -> emptyList()
        }
    }

    // ---------- reports ----------

    fun hasReportData(): Boolean = ReportFormatter.hasContent(_discovery.value, _ports.value, _diag.value)

    fun reportText(): String =
        ReportFormatter.format(System.currentTimeMillis(), reader.read(), _discovery.value, _ports.value, _diag.value)

    /** Writes the report into the app's own storage. Returns the path, or throws on failure. */
    fun saveReport(): String {
        val dir = File(app.getExternalFilesDir(null) ?: app.filesDir, "reports")
        if (!dir.exists() && !dir.mkdirs()) throw java.io.IOException("cannot create ${dir.path}")
        val name = "matrix-net-report-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".txt"
        val f = File(dir, name)
        f.writeText(reportText())
        return f.absolutePath
    }

    fun saveReportLines(): List<String> {
        if (!hasReportData()) return listOf("NOTHING TO REPORT YET. RUN A DISCOVERY, DIAGNOSTICS OR PORT CHECK FIRST.")
        return try {
            listOf("REPORT SAVED LOCALLY:", "  ${saveReport()}", "NOT UPLOADED. USE EXPORT IN THE NET SCREEN TO SHARE IT.")
        } catch (e: Exception) {
            listOf("REPORT FAILED: ${PortChecker.describe(e)}")
        }
    }

    /** Share sheet intent. Only fired after the user taps EXPORT/SHARE. */
    fun shareIntent(): Intent {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Matrix network report")
            putExtra(Intent.EXTRA_TEXT, reportText().take(200_000))
        }
        return Intent.createChooser(send, "Export network report").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    companion object {
        const val MAX_HISTORY = 100
    }
}
