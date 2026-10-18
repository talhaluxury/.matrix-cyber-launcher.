package com.matrix.launcher.ui

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.matrix.launcher.MainViewModel
import com.matrix.launcher.net.DiagKind
import com.matrix.launcher.net.DiagOutcome
import com.matrix.launcher.net.DiagResult
import com.matrix.launcher.net.DiscoveredHost
import com.matrix.launcher.net.DiscoveryState
import com.matrix.launcher.net.NetOp
import com.matrix.launcher.net.NetValidation
import com.matrix.launcher.net.PortResult
import com.matrix.launcher.net.PortState
import com.matrix.launcher.net.PortsState
import com.matrix.launcher.net.ReportFormatter
import com.matrix.launcher.ui.theme.MatrixColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private class PendingAuth(val text: String, val action: () -> Unit)

private fun clock(ms: Long): String = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(ms))

private fun stateColor(s: PortState): Color = when (s) {
    PortState.REACHABLE -> MatrixColors.Neon
    PortState.REFUSED -> WarnColor
    PortState.TIMED_OUT -> MatrixColors.Text.copy(alpha = 0.6f)
    PortState.UNREACHABLE -> ErrorColor
    PortState.ERROR -> ErrorColor.copy(alpha = 0.7f)
}

private fun outcomeColor(o: DiagOutcome): Color = when (o) {
    DiagOutcome.OK -> MatrixColors.Neon
    DiagOutcome.FAIL -> ErrorColor
    DiagOutcome.SKIPPED -> MatrixColors.Text.copy(alpha = 0.6f)
}

/** Network Command Center: three real tools (discovery, diagnostics, TCP port reachability). Nothing here is simulated. */
@Composable
fun NetworkScreen(vm: MainViewModel) {
    val net = vm.net
    val discovery by net.discovery.collectAsState()
    val ports by net.ports.collectAsState()
    val diag by net.diag.collectAsState()
    val op by net.activeOp.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    var pending by remember { mutableStateOf<PendingAuth?>(null) }

    OverlayScaffold(title = "NET // COMMAND CENTER", onClose = { vm.back() }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val names = listOf("DISCOVER", "DIAGNOSE", "PORTS")
            names.forEachIndexed { i, n ->
                MatrixButton(
                    if (tab == i) "[$n]" else n,
                    Modifier.weight(1f),
                    accent = if (tab == i) MatrixColors.Neon else MatrixColors.Neon2.copy(alpha = 0.55f),
                    hPad = 2.dp,
                    onClick = { tab = i },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (tab) {
                0 -> DiscoverTab(vm, discovery, op) { pending = it }
                1 -> DiagnoseTab(vm, diag.history, diag.running, diag.step, op) { pending = it }
                else -> PortsTab(vm, ports, op) { pending = it }
            }
            PermissionsPanel()
        }
    }

    val p = pending
    if (p != null) {
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("AUTHORIZATION") },
            text = {
                Text(
                    p.text + "\n\nOnly continue if you own this network/host or have explicit permission to test it. " +
                        "Results stay on this phone unless you export them.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pending = null
                    p.action()
                }) { Text("I AM AUTHORIZED") }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("CANCEL") } },
        )
    }
}

@Composable
private fun PermissionsPanel() {
    Panel(Modifier.fillMaxWidth(), title = "PERMISSIONS & PRIVACY") {
        Text(
            "Uses only ACCESS_NETWORK_STATE and INTERNET (normal permissions, Android shows no prompt). " +
                "No location, contacts, messages or storage access. Wi-Fi name (SSID) is not shown because Android " +
                "would require location permission for it. Results are kept in memory and written to a file only when you " +
                "tap SAVE; SHARE sends them only where you choose. No root needed.",
            color = MatrixColors.Text.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall,
        )
    }
}

// ---------- shared widgets ----------

private class Blip(val angleDeg: Float, val radius: Float, val color: Color)

/**
 * Radar whose sweep angle IS the real progress (done / total). Dots are only drawn for results that really
 * came back; their position encodes address (angle) and response time (distance). Nothing loops on its own,
 * so it costs no battery while idle and needs no special reduced-motion handling beyond skipping the easing.
 */
@Composable
private fun ScanRadar(progress: Float, blips: List<Blip>, modifier: Modifier = Modifier) {
    val fx = LocalFx.current
    val spec: AnimationSpec<Float> = if (fx.reduceMotion) snap() else tween(250)
    val p by animateFloatAsState(progress, spec, label = "radarProgress")
    Canvas(modifier.aspectRatio(1f)) {
        val c = center
        val r = size.minDimension / 2f * 0.95f
        val neon = MatrixColors.Neon
        val dim = MatrixColors.Muted
        for (i in 1..4) {
            drawCircle(color = dim, radius = r * i / 4f, center = c, style = Stroke(width = 1.5f))
        }
        drawLine(dim, Offset(c.x - r, c.y), Offset(c.x + r, c.y), strokeWidth = 1.5f)
        drawLine(dim, Offset(c.x, c.y - r), Offset(c.x, c.y + r), strokeWidth = 1.5f)
        if (p > 0f) {
            drawArc(
                color = neon.copy(alpha = 0.16f),
                startAngle = -90f,
                sweepAngle = 360f * p,
                useCenter = true,
                topLeft = Offset(c.x - r, c.y - r),
                size = Size(r * 2f, r * 2f),
            )
            val a = (-90f + 360f * p) * (Math.PI.toFloat() / 180f)
            drawLine(neon, c, Offset(c.x + r * cos(a), c.y + r * sin(a)), strokeWidth = 3f)
        }
        for (b in blips) {
            val a = (-90f + b.angleDeg) * (Math.PI.toFloat() / 180f)
            val rr = r * b.radius
            drawCircle(color = b.color, radius = 6f, center = Offset(c.x + rr * cos(a), c.y + rr * sin(a)))
        }
    }
}

@Composable
private fun NetField(label: String, value: String, onChange: (String) -> Unit, enabled: Boolean = true) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, color = MatrixColors.Neon2.copy(alpha = 0.85f), style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(3.dp))
        Row(Modifier.fillMaxWidth().liquidGlass(ButtonShape).padding(horizontal = 14.dp)) {
            BasicTextField(
                value = value,
                onValueChange = { onChange(it.take(120)) },
                enabled = enabled,
                singleLine = true,
                textStyle = TextStyle(color = MatrixColors.SoftWhite, fontFamily = FontFamily.Monospace, fontSize = 14.sp),
                cursorBrush = SolidColor(MatrixColors.Neon),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun StatusLine(running: Boolean, text: String) {
    val fx = LocalFx.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PulseDot(if (running) MatrixColors.Neon else MatrixColors.Muted, animate = running && fx.animate)
        Text(text, color = MatrixColors.Text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ReportButtons(vm: MainViewModel, enabled: Boolean) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        MatrixButton("SAVE REPORT", Modifier.weight(1f), onClick = {
            if (!enabled) {
                vm.postMessage("NOTHING TO EXPORT YET")
            } else {
                scope.launch {
                    val lines = withContext(Dispatchers.IO) { vm.net.saveReportLines() }
                    vm.postMessage(lines.take(2).joinToString(" ").take(140))
                }
            }
        })
        MatrixButton("SHARE", Modifier.weight(1f), accent = MatrixColors.Neon2, onClick = {
            if (!enabled) {
                vm.postMessage("NOTHING TO EXPORT YET")
            } else {
                try {
                    ctx.startActivity(vm.net.shareIntent())
                } catch (e: Exception) {
                    vm.postMessage("NO APP CAN HANDLE SHARING")
                }
            }
        })
    }
}

// ---------- DISCOVER ----------

@Composable
private fun DiscoverTab(vm: MainViewModel, st: DiscoveryState, op: NetOp, ask: (PendingAuth) -> Unit) {
    val net = vm.net
    var info by remember { mutableStateOf(net.localInfo()) }
    var range by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        if (range.isEmpty()) range = net.suggestedRange()?.toString().orEmpty()
    }
    val busy = op != NetOp.NONE

    Panel(Modifier.fillMaxWidth(), title = "THIS PHONE'S CONNECTION") {
        StatRow("STATUS", if (info.connected) "CONNECTED" else "OFFLINE", if (info.connected) MatrixColors.Neon else ErrorColor)
        StatRow("TRANSPORT", info.transport)
        StatRow("IPV4", (info.ipv4 ?: "Unknown") + (info.prefix?.let { "/$it" } ?: ""))
        StatRow("GATEWAY", info.gateway ?: "Unknown")
        StatRow("DNS", if (info.dnsServers.isEmpty()) "Unknown" else info.dnsServers.joinToString(", "))
        Spacer(Modifier.height(6.dp))
        MatrixButton("REFRESH", Modifier.fillMaxWidth(), accent = MatrixColors.Neon2, onClick = { info = net.localInfo() })
    }

    NetField("AUTHORIZED RANGE (CIDR, PRIVATE LAN ONLY, /22../30)", range, { range = it }, enabled = !busy)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (st.running) {
            MatrixButton("CANCEL SCAN", Modifier.weight(1f), accent = ErrorColor, onClick = { net.cancel() })
        } else {
            MatrixButton("START DISCOVERY", Modifier.weight(1f), onClick = {
                val err = when {
                    busy -> "ANOTHER OPERATION IS RUNNING"
                    NetValidation.parseCidr(range) == null -> "INVALID RANGE. EXAMPLE: 192.168.1.0/24"
                    else -> null
                }
                if (err != null) {
                    vm.postMessage(err)
                } else {
                    ask(PendingAuth("Probe every address in $range (ping plus a few quick TCP connects, rate-limited).") {
                        net.startDiscovery(range)?.let { vm.postMessage(it) }
                    })
                }
            })
        }
    }

    Panel(Modifier.fillMaxWidth(), title = "RADAR // REAL PROGRESS") {
        val blips = st.hosts.map { h ->
            val last = h.ip.substringAfterLast('.').toIntOrNull() ?: 0
            Blip(
                angleDeg = last / 256f * 360f,
                radius = if (h.isThisDevice) 0.02f else 0.25f + 0.65f * (min(h.rttMs, 500L) / 500f),
                color = if (h.isThisDevice) MatrixColors.Neon2 else MatrixColors.Neon,
            )
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            ScanRadar(st.progress, blips, Modifier.widthIn(max = 260.dp).fillMaxWidth())
        }
        Text(
            "Sweep angle = addresses probed / total. Each dot is a device that really answered: angle = last address number, distance = response time.",
            color = MatrixColors.Text.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall,
        )
    }

    val statusText = when {
        !st.hasData -> "IDLE"
        st.running -> "SCANNING ${st.probed}/${st.total} - FOUND ${st.hosts.size}"
        st.cancelled -> "CANCELLED ${clock(st.finishedAtMs ?: st.startedAtMs)} - ${st.probed}/${st.total} PROBED, FOUND ${st.hosts.size}"
        st.error != null -> "ERROR: ${st.error}"
        else -> "COMPLETE ${clock(st.finishedAtMs ?: st.startedAtMs)} - FOUND ${st.hosts.size} OF ${st.total} ADDRESSES"
    }
    Panel(Modifier.fillMaxWidth(), title = "DEVICES (${st.hosts.size})") {
        StatusLine(st.running, statusText)
        Spacer(Modifier.height(6.dp))
        if (st.hosts.isEmpty()) {
            Text(
                if (st.hasData && !st.running) "NO DEVICE ANSWERED. THAT DOES NOT MEAN NONE EXIST." else "NO RESULTS YET.",
                color = MatrixColors.Text.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyMedium,
            )
        }
        st.hosts.forEach { HostRow(it) }
    }

    Panel(Modifier.fillMaxWidth(), title = "READ THIS") {
        Text(
            "Results can be incomplete: firewalls, Wi-Fi client isolation, Android restrictions and sleeping devices stay silent. " +
                "Host names appear only if reverse DNS answers. MAC addresses and OS types can't be read reliably on Android 10+, so they show as Unknown. " +
                "The scan stops if you leave the launcher.",
            color = WarnColor, style = MaterialTheme.typography.labelSmall,
        )
    }
    ReportButtons(vm, st.hasData)
}

@Composable
private fun HostRow(h: DiscoveredHost) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            h.ip + if (h.isThisDevice) "   (THIS DEVICE)" else "",
            color = MatrixColors.Neon, style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "host: ${h.hostname ?: "Unknown"}   mac: Unknown",
            color = MatrixColors.Text, style = MaterialTheme.typography.labelSmall,
        )
        Text(
            "via ${h.method}" + (if (h.isThisDevice) "" else " - ${h.rttMs} ms") + " - found ${clock(h.discoveredAtMs)}",
            color = MatrixColors.Text.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall,
        )
    }
}

// ---------- DIAGNOSE ----------

@Composable
private fun DiagnoseTab(
    vm: MainViewModel,
    history: List<DiagResult>,
    running: Boolean,
    step: String?,
    op: NetOp,
    ask: (PendingAuth) -> Unit,
) {
    val net = vm.net
    var host by remember { mutableStateOf("example.com") }
    val busy = op != NetOp.NONE

    fun run(kind: DiagKind?, needsConsent: Boolean) {
        val label = kind?.label ?: "FULL SEQUENCE"
        val hostOk = kind == DiagKind.CONNECTIVITY || kind == DiagKind.INTERNET || NetValidation.isValidTarget(host.trim())
        when {
            busy -> vm.postMessage("ANOTHER OPERATION IS RUNNING")
            !hostOk -> vm.postMessage("INVALID HOST NAME OR IPV4")
            needsConsent -> ask(PendingAuth("Run $label. This contacts ${host.trim().ifEmpty { "the target" }} and/or the public address 1.1.1.1 from your phone.") {
                net.startDiagnostics(host, kind)?.let { vm.postMessage(it) }
            })
            else -> net.startDiagnostics(host, kind)?.let { vm.postMessage(it) }
        }
    }

    NetField("HOST FOR DNS / LATENCY / REACHABILITY", host, { host = it }, enabled = !busy)
    if (running) {
        StatusLine(true, "RUNNING: ${step ?: "STARTING"}")
        MatrixButton("CANCEL", Modifier.fillMaxWidth(), accent = ErrorColor, onClick = { net.cancel() })
    } else {
        MatrixButton("RUN FULL SEQUENCE", Modifier.fillMaxWidth(), onClick = { run(null, true) })
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MatrixButton("CONNECTION", accent = MatrixColors.Neon2, onClick = { run(DiagKind.CONNECTIVITY, false) })
            MatrixButton("INTERNET", accent = MatrixColors.Neon2, onClick = { run(DiagKind.INTERNET, true) })
            MatrixButton("DNS", accent = MatrixColors.Neon2, onClick = { run(DiagKind.DNS, false) })
            MatrixButton("LATENCY", accent = MatrixColors.Neon2, onClick = { run(DiagKind.LATENCY, true) })
            MatrixButton("REACH", accent = MatrixColors.Neon2, onClick = { run(DiagKind.HOST_REACH, true) })
        }
    }

    Panel(Modifier.fillMaxWidth(), title = "THREE DIFFERENT THINGS") {
        Text(
            "DNS time = how long the name lookup took. TCP connect latency = how long a connection to the host's port 443 took (lookup excluded). " +
                "Internet check = whether one public address (1.1.1.1:443) accepts a connection; it does not prove every site works. " +
                "A value is shown only when that test succeeded.",
            color = MatrixColors.Text.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall,
        )
    }

    Panel(Modifier.fillMaxWidth(), title = "HISTORY (${history.size}, THIS SESSION)") {
        if (history.isEmpty()) {
            Text("NO TESTS RUN YET.", color = MatrixColors.Text.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyMedium)
        }
        history.forEach { r ->
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(clock(r.timestampMs), color = MatrixColors.Text.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
                    Text(r.kind.label, color = MatrixColors.Neon2, style = MaterialTheme.typography.labelSmall)
                    Text(
                        r.outcome.name + (r.valueMs?.let { "  $it ms" } ?: ""),
                        color = outcomeColor(r.outcome), style = MaterialTheme.typography.labelSmall,
                    )
                }
                Text(r.message, color = MatrixColors.Text, style = MaterialTheme.typography.labelSmall)
            }
        }
        if (history.isNotEmpty() && !running) {
            Spacer(Modifier.height(6.dp))
            MatrixButton("CLEAR HISTORY", Modifier.fillMaxWidth(), accent = MatrixColors.Neon2, onClick = { net.clearDiagHistory() })
        }
    }
    ReportButtons(vm, history.isNotEmpty())
}

// ---------- PORTS ----------

@Composable
private fun PortsTab(vm: MainViewModel, st: PortsState, op: NetOp, ask: (PendingAuth) -> Unit) {
    val net = vm.net
    var host by remember { mutableStateOf("") }
    var portText by remember { mutableStateOf("22,80,443") }
    val busy = op != NetOp.NONE

    fun toggle(p: Int) {
        val cur = NetValidation.parsePortList(portText).ports
        val next = if (p in cur) cur - p else cur + p
        portText = next.joinToString(",")
    }

    NetField("TARGET (HOST NAME OR IPV4 - YOURS OR AUTHORIZED ONLY)", host, { host = it }, enabled = !busy)
    NetField("TCP PORTS (EXAMPLE: 22,80,443 OR 8000-8010, MAX ${NetValidation.MAX_PORTS})", portText, { portText = it }, enabled = !busy)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        val selected = NetValidation.parsePortList(portText).ports
        NetValidation.COMMON_PORTS.forEach { p ->
            MatrixButton(
                p.toString(),
                accent = if (p in selected) MatrixColors.Neon else MatrixColors.Neon2.copy(alpha = 0.5f),
                onClick = { if (!busy) toggle(p) },
            )
        }
    }

    if (st.running) {
        MatrixButton("CANCEL CHECK", Modifier.fillMaxWidth(), accent = ErrorColor, onClick = { net.cancel() })
    } else {
        MatrixButton("START PORT CHECK", Modifier.fillMaxWidth(), onClick = {
            val target = host.trim()
            val parsed = NetValidation.parsePortList(portText)
            when {
                busy -> vm.postMessage("ANOTHER OPERATION IS RUNNING")
                !NetValidation.isValidTarget(target) -> vm.postMessage("INVALID TARGET: USE A HOST NAME OR IPV4")
                parsed.error != null -> vm.postMessage(parsed.error)
                else -> ask(PendingAuth("Try TCP connections to $target on ${parsed.ports.size} port(s). Nothing is sent after connecting.") {
                    net.startPortCheck(target, parsed.ports)?.let { vm.postMessage(it) }
                })
            }
        })
    }

    Panel(Modifier.fillMaxWidth(), title = "RADAR // REAL PROGRESS") {
        val blips = st.results.map { r ->
            Blip(
                angleDeg = (r.port % 360).toFloat(),
                radius = 0.2f + 0.7f * (min(r.elapsedMs, 2500L) / 2500f),
                color = stateColor(r.state),
            )
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            ScanRadar(st.progress, blips, Modifier.widthIn(max = 260.dp).fillMaxWidth())
        }
        Text(
            "Sweep = ports finished / total. Dots are real results: angle = port number mod 360 (position only), distance = connect time.",
            color = MatrixColors.Text.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall,
        )
    }

    val statusText = when {
        !st.hasData -> "IDLE"
        st.running -> "CHECKING ${st.target}: ${st.results.size}/${st.total} PORTS"
        st.cancelled -> "CANCELLED ${clock(st.finishedAtMs ?: st.startedAtMs)} - ${st.results.size}/${st.total} DONE"
        st.error != null -> "FAILED: ${st.error}"
        else -> "COMPLETE ${clock(st.finishedAtMs ?: st.startedAtMs)}"
    }
    Panel(Modifier.fillMaxWidth(), title = "RESULTS") {
        StatusLine(st.running, statusText)
        if (st.resolved != null) StatRow("RESOLVED TO", st.resolved)
        Spacer(Modifier.height(6.dp))
        if (st.results.isEmpty()) {
            Text("NO RESULTS YET.", color = MatrixColors.Text.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyMedium)
        }
        st.results.forEach { PortRow(it) }
        if (st.hasData && !st.running && st.results.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            val total = (st.finishedAtMs ?: st.startedAtMs) - st.startedAtMs
            Text("SUMMARY: ${ReportFormatter.summary(st.results)}", color = MatrixColors.Neon, style = MaterialTheme.typography.bodyMedium)
            Text(
                "Elapsed ${total} ms, finished ${ReportFormatter.stamp(st.finishedAtMs ?: st.startedAtMs)}.",
                color = MatrixColors.Text, style = MaterialTheme.typography.labelSmall,
            )
        }
    }

    Panel(Modifier.fillMaxWidth(), title = "READ THIS") {
        Text(
            "REACHABLE means only that a TCP connection was accepted at that moment. It does not identify the service, its version or whether it is secure. " +
                "REFUSED = the host answered but nothing listens. TIMED OUT = no answer (blocked, dropped or host down). " +
                "Use this only on your own systems, hosts you are authorized to test, or lab targets.",
            color = WarnColor, style = MaterialTheme.typography.labelSmall,
        )
    }
    ReportButtons(vm, st.hasData)
}

@Composable
private fun PortRow(r: PortResult) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(r.port.toString(), color = MatrixColors.Text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.2f))
        Text(r.state.label, color = stateColor(r.state), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.5f))
        Text("${r.elapsedMs} ms", color = MatrixColors.Text.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.3f))
    }
}
