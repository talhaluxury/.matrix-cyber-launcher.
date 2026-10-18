package com.matrix.launcher.ui

import android.os.Build
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import com.matrix.launcher.anim.CyberGlobeView
import com.matrix.launcher.anim.RadarView
import com.matrix.launcher.anim.rememberTilt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.matrix.launcher.MainViewModel
import com.matrix.launcher.VoiceState
import com.matrix.launcher.Overlay
import com.matrix.launcher.anim.CoreReactor
import com.matrix.launcher.data.DeviceStatus
import com.matrix.launcher.data.LauncherSettings
import com.matrix.launcher.data.NetType
import com.matrix.launcher.system.SoundFx
import com.matrix.launcher.ui.theme.MatrixColors
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeScreen(vm: MainViewModel, onVoice: () -> Unit) {
    val fx = LocalFx.current
    val fb = LocalFeedback.current
    val settings by vm.settings.collectAsState()
    val status by vm.status.collectAsState()
    val apps by vm.apps.collectAsState()
    val favorites by vm.favorites.collectAsState()
    val dock by vm.dock.collectAsState()
    val scanning by vm.scanning.collectAsState()
    val appError by vm.appError.collectAsState()
    val voice by vm.voiceState.collectAsState()
    val favApps = remember(apps, favorites) { favorites.mapNotNull { k -> apps.firstOrNull { it.key == k } } }
    val panel by vm.panel.collectAsState()
    var ripple by remember { mutableIntStateOf(0) }
    val tilt = rememberTilt(settings.parallax && fx.visible && !fx.reduceMotion)

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .pointerInput(fb) {
                // long-press on empty space opens customization (children that handle touches win)
                detectTapGestures(onLongPress = {
                    fb.tap()
                    vm.openOverlay(Overlay.SETTINGS)
                })
            }
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        if (total < -140f) vm.openDrawer(false) else if (total > 140f) vm.openDrawer(true)
                    },
                    onDragCancel = { total = 0f },
                    onVerticalDrag = { _, dy -> total += dy },
                )
            }
            .pointerInput(Unit) {
                // swipe left/right switches the center panel (reactor / globe / radar)
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        if (total < -120f) vm.swipePanel(1) else if (total > 120f) vm.swipePanel(-1)
                    },
                    onDragCancel = { total = 0f },
                    onHorizontalDrag = { _, dx -> total += dx },
                )
            }
    ) {
        val landscape = maxWidth > maxHeight
        val scrollMod = if (landscape) Modifier.verticalScroll(rememberScrollState()) else Modifier
        Column(
            Modifier.fillMaxSize().then(scrollMod).padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Header(settings, status, fx.animate, vm)

            if (appError != null) {
                Panel(Modifier.fillMaxWidth()) {
                    Text(appError ?: "", color = ErrorColor, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(6.dp))
                    MatrixButton("RETRY APP INDEX", accent = ErrorColor, onClick = { vm.refreshApps() })
                }
            }

            val core: @Composable (androidx.compose.ui.unit.Dp) -> Unit = { s ->
                Box(
                    Modifier.size(s).graphicsLayer {
                        translationX = tilt.value.x * 14.dp.toPx()
                        translationY = tilt.value.y * 10.dp.toPx()
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    val tapScan = {
                        fb.play(SoundFx.Type.SCAN)
                        ripple++
                        vm.startScan()
                    }
                    when (panel) {
                        1 -> CyberGlobeView(Modifier.fillMaxSize(), fx)
                        2 -> RadarView(Modifier.fillMaxSize().feedbackClick(tapScan), fx = fx, fast = scanning)
                        else -> CoreReactor(
                            Modifier.fillMaxSize().feedbackClick(tapScan),
                            fx = fx,
                            scanning = scanning,
                            label = if (scanning) "SCANNING" else "CORE",
                            sub = if (scanning) "LOCAL TELEMETRY" else "TAP TO SCAN",
                        )
                    }
                    if (panel != 1 && !fx.reduceMotion) PulseRipple(ripple, Modifier.fillMaxSize())
                }
            }
            if (landscape) {
                Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) { core(230.dp) }
            } else {
                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    val s = minOf(maxWidth, maxHeight)
                    core(s)
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                listOf("CORE", "GLOBE", "RADAR").forEachIndexed { i, n ->
                    Text(
                        n,
                        color = if (panel == i) MatrixColors.Neon else MatrixColors.Muted,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.feedbackClick({ vm.setPanel(i) }).padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
            }

            if (settings.hudVisible) {
                Panel(Modifier.fillMaxWidth(), title = "LAUNCHER PANEL // LOCAL TELEMETRY") {
                    Row(Modifier.fillMaxWidth()) {
                        Stat("BAT", status?.batteryText() ?: "--", Modifier.weight(1f))
                        Spacer(Modifier.width(12.dp))
                        Stat("NET", status?.networkShort() ?: "--", Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth()) {
                        Stat("RAM FREE", status?.memShort() ?: "--", Modifier.weight(1f))
                        Spacer(Modifier.width(12.dp))
                        Stat("UPTIME", status?.uptimeText() ?: "--", Modifier.weight(1f))
                    }
                }
            }

            if (favApps.isEmpty()) {
                Text(
                    "FAVORITES EMPTY - LONG-PRESS AN APP IN THE DRAWER TO PIN IT",
                    color = MatrixColors.Neon2.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.labelSmall,
                )
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
                    items(favApps, key = { it.key }) { app ->
                        Column(
                            Modifier.feedbackClick({ vm.launch(app) }, { vm.openMenuFor(app) }).padding(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            AppIcon(app, vm.appRepo, (settings.iconSize - 8).dp)
                            Text(
                                app.label, color = MatrixColors.Text, style = MaterialTheme.typography.labelSmall,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width((settings.iconSize + 8).dp),
                            )
                        }
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MatrixButton("TERM", Modifier.weight(1f), hPad = 2.dp, onClick = { vm.openOverlay(Overlay.TERMINAL) })
                MatrixButton("RADAR", Modifier.weight(1f), hPad = 2.dp, onClick = { vm.openOverlay(Overlay.RADAR) })
                MatrixButton("DIAG", Modifier.weight(1f), hPad = 2.dp, onClick = { vm.openOverlay(Overlay.DIAGNOSTICS) })
                MatrixButton("WDG", Modifier.weight(1f), hPad = 2.dp, onClick = { vm.openOverlay(Overlay.WIDGETS) })
                MatrixButton("SET", Modifier.weight(1f), hPad = 2.dp, onClick = { vm.openOverlay(Overlay.SETTINGS) })
                if (settings.voiceEnabled) {
                    MatrixButton(
                        if (voice == VoiceState.LISTENING) "..." else "MIC", Modifier.weight(1f),
                        accent = MatrixColors.Neon2, hPad = 2.dp, onClick = onVoice,
                    )
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MatrixButton("AI", Modifier.weight(1f), hPad = 2.dp, accent = MatrixColors.Neon2, onClick = { vm.openOverlay(Overlay.ASSISTANT) })
                MatrixButton("SHOW", Modifier.weight(1f), hPad = 2.dp, accent = MatrixColors.Neon2, onClick = { vm.openOverlay(Overlay.SHOW) })
                MatrixButton("NET", Modifier.weight(1f), hPad = 2.dp, accent = MatrixColors.Neon2, onClick = { vm.openOverlay(Overlay.NETWORK) })
                MatrixButton("VAULT", Modifier.weight(1f), hPad = 2.dp, accent = MatrixColors.Neon2, onClick = { vm.openOverlay(Overlay.PRIVATE) })
            }

            Panel(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    dock.forEach { e ->
                        Box(Modifier.feedbackClick({ vm.launch(e.app) }, { vm.openMenuFor(e.app) }).padding(2.dp)) {
                            AppIcon(e.app, vm.appRepo, settings.iconSize.dp)
                        }
                    }
                    Box(
                        Modifier
                            .size(settings.iconSize.dp)
                            .feedbackClick({ vm.openDrawer(false) })
                            .liquidGlass(IconShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("APPS", color = MatrixColors.Neon, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun PulseRipple(trigger: Int, modifier: Modifier) {
    val p = remember { Animatable(1f) }
    LaunchedEffect(trigger) {
        if (trigger > 0) {
            p.snapTo(0f)
            p.animateTo(1f, tween(900))
        }
    }
    Canvas(modifier) {
        val v = p.value
        if (v < 1f) {
            val r = size.minDimension / 2f
            drawCircle(MatrixColors.Neon.copy(alpha = (1f - v) * 0.6f), r * (0.25f + 0.75f * v), center, style = Stroke(3.dp.toPx()))
            val v2 = (v - 0.2f).coerceAtLeast(0f)
            drawCircle(MatrixColors.Neon2.copy(alpha = (1f - v2) * 0.4f), r * (0.2f + 0.8f * v2), center, style = Stroke(2.dp.toPx()))
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MatrixColors.Neon2.copy(alpha = 0.85f), style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.width(6.dp))
        Text(value, color = MatrixColors.Text, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Header(settings: LauncherSettings, status: DeviceStatus?, animate: Boolean, vm: MainViewModel) {
    val linkColor = when {
        status == null -> MatrixColors.Muted
        status.networkType == NetType.OFFLINE -> ErrorColor
        status.networkValidated -> MatrixColors.Neon
        else -> WarnColor
    }
    val linkText = when {
        status == null -> "LINK ..."
        status.networkType == NetType.OFFLINE -> "OFFLINE"
        status.networkValidated -> "ONLINE"
        else -> "LOCAL LINK / NO INTERNET"
    }
    Panel(Modifier.fillMaxWidth()) {
        Text(
            "MATRIX CYBER LAUNCHER",
            color = MatrixColors.Neon,
            style = MaterialTheme.typography.bodyLarge.copy(
                letterSpacing = 2.sp,
                shadow = Shadow(MatrixColors.Neon.copy(alpha = 0.6f), blurRadius = 12f),
            ),
        )
        Text(
            settings.deviceLabel.ifBlank { Build.MODEL.uppercase(Locale.US) },
            color = MatrixColors.Muted.copy(alpha = 1f),
            style = MaterialTheme.typography.labelSmall,
        )
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Clock(settings.is24h, LocalFx.current.visible, Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PulseDot(linkColor, animate && linkColor != MatrixColors.Muted)
                    Spacer(Modifier.width(6.dp))
                    Text(linkText, color = linkColor, style = MaterialTheme.typography.labelSmall)
                }
                Text("BAT ${status?.batteryText() ?: "--"}", color = MatrixColors.Text, style = MaterialTheme.typography.labelSmall)
                Text("NET ${status?.networkShort() ?: "--"}", color = MatrixColors.Text, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun Clock(is24h: Boolean, active: Boolean, modifier: Modifier = Modifier) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(active) {
        while (active) {
            now = LocalDateTime.now()
            delay(1000L - (System.currentTimeMillis() % 1000L))
        }
    }
    val timeFmt = remember(is24h) { DateTimeFormatter.ofPattern(if (is24h) "HH:mm:ss" else "hh:mm:ss a", Locale.getDefault()) }
    val dateFmt = remember { DateTimeFormatter.ofPattern("EEEE, dd MMM yyyy", Locale.getDefault()) }
    Column(modifier) {
        Text(
            now.format(timeFmt),
            color = MatrixColors.Neon,
            style = MaterialTheme.typography.headlineMedium.copy(fontFamily = FontFamily.Monospace, fontSize = 28.sp),
        )
        Text(now.format(dateFmt).uppercase(Locale.getDefault()), color = MatrixColors.Text, style = MaterialTheme.typography.labelSmall)
    }
}
