package com.matrix.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.matrix.launcher.MainViewModel
import com.matrix.launcher.anim.CoreReactor
import com.matrix.launcher.anim.CyberGlobeView
import com.matrix.launcher.anim.MatrixRain
import com.matrix.launcher.anim.RadarView
import com.matrix.launcher.anim.rememberFrameTime
import com.matrix.launcher.ui.theme.MatrixColors
import kotlinx.coroutines.delay

private val BOOT_LINES = listOf(
    "MATRIX CORE INITIALIZING",
    "SYSTEM ONLINE",
    "NEURAL INTERFACE READY",
    "CYBER HUD ACTIVE",
    "COMMAND CENTER READY",
)

/**
 * 25-second cinematic DEMONSTRATION. Everything here is animation and text; no network, device or
 * system operation is performed. EXIT is always visible and the system Back button also leaves.
 */
@Composable
fun ShowModeScreen(vm: MainViewModel) {
    val base = LocalFx.current
    val fx = base.copy(rain = true, radar = true)
    val status by vm.status.collectAsState()
    var stage by remember { mutableIntStateOf(0) }
    val log = remember { mutableStateListOf<String>() }

    LaunchedEffect(Unit) {
        fun add(s: String) {
            log.add(s)
            while (log.size > 6) log.removeAt(0)
        }
        stage = 0
        for (l in BOOT_LINES) { add(l); delay(800) }
        stage = 1
        add("DIGITAL RAIN ONLINE"); delay(2300)
        add("GRID SWEEP (VISUAL ONLY)"); delay(2200)
        stage = 2
        add("REACTOR ACTIVATION"); delay(2300)
        add("REACTOR STABLE"); delay(1700)
        stage = 3
        add("RADAR SWEEP (SIMULATED)"); delay(2300)
        add("NO REAL SIGNALS ARE SCANNED"); delay(1700)
        stage = 4
        add("GLOBE LINK (SIMULATED DATA)"); delay(2400)
        add("ARCS ARE DECORATION ONLY"); delay(2100)
        stage = 5
        add("HUD STATUS: NOMINAL (DEMO)"); delay(2300)
        add("TELEMETRY BELOW IS REAL, LOCAL"); delay(2200)
        stage = 6
        add("DEMO COMPLETE")
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        MatrixRain(Modifier.fillMaxSize(), fx)
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "SHOW MODE - VISUAL DEMONSTRATION ONLY",
                    color = WarnColor, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f),
                )
                MatrixButton("EXIT", accent = ErrorColor, onClick = { vm.back() })
            }
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                when (stage) {
                    0 -> Text(
                        "MATRIX CORE", color = MatrixColors.Neon, textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.headlineMedium.copy(fontSize = 30.sp),
                    )
                    1 -> ScanGrid(fx)
                    2 -> CoreReactor(Modifier.widthIn(max = 360.dp).fillMaxWidth(), fx, scanning = true, label = "REACTOR", sub = "DEMO")
                    3 -> RadarView(Modifier.widthIn(max = 360.dp).fillMaxWidth(), fx, fast = true)
                    4 -> CyberGlobeView(Modifier.widthIn(max = 380.dp).fillMaxWidth().height(380.dp), fx, interactive = false)
                    5 -> Panel(Modifier.fillMaxWidth(), title = "HUD // LIVE LOCAL TELEMETRY") {
                        StatRow("BATTERY", status?.batteryText() ?: "--")
                        StatRow("NETWORK", status?.networkShort() ?: "--")
                        StatRow("STORAGE", status?.storageText() ?: "--")
                        StatRow("UPTIME", status?.uptimeText() ?: "--")
                    }
                    else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "SYSTEM ONLINE", color = MatrixColors.Neon, textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.headlineMedium.copy(fontSize = 34.sp, letterSpacing = 4.sp),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("DEMONSTRATION COMPLETE", color = MatrixColors.Text, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Panel(Modifier.fillMaxWidth()) {
                log.forEach { Text("> $it", color = MatrixColors.Neon2, style = MaterialTheme.typography.labelSmall) }
                if (log.isEmpty()) Text(">", color = MatrixColors.Neon2, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun ScanGrid(fx: com.matrix.launcher.anim.Fx) {
    val time = rememberFrameTime(fx.animate, fx.fps)
    Canvas(Modifier.fillMaxSize().clipToBounds()) {
        val step = 32.dp.toPx()
        val neon = MatrixColors.Neon
        var x = 0f
        while (x <= size.width) {
            drawLine(neon.copy(alpha = 0.22f), Offset(x, 0f), Offset(x, size.height), 1f)
            x += step
        }
        var y = 0f
        while (y <= size.height) {
            drawLine(neon.copy(alpha = 0.22f), Offset(0f, y), Offset(size.width, y), 1f)
            y += step
        }
        val sweepY = ((time.value * 0.45f) % 1f) * size.height
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color.Transparent, neon.copy(alpha = 0.55f), Color.Transparent),
                startY = sweepY - 60.dp.toPx(), endY = sweepY + 60.dp.toPx(),
            ),
            topLeft = Offset(0f, sweepY - 60.dp.toPx()),
            size = androidx.compose.ui.geometry.Size(size.width, 120.dp.toPx()),
        )
    }
}
