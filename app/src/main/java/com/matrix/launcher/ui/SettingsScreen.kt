package com.matrix.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.matrix.launcher.MainViewModel
import com.matrix.launcher.data.Intensity
import com.matrix.launcher.data.LauncherSettings
import com.matrix.launcher.system.openHomeSettings
import com.matrix.launcher.ui.theme.MatrixColors
import com.matrix.launcher.ui.theme.ThemeId

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val s by vm.settings.collectAsState()
    val apps by vm.apps.collectAsState()
    val favorites by vm.favorites.collectAsState()
    val ctx = LocalContext.current
    val favApps = favorites.mapNotNull { k -> apps.firstOrNull { it.key == k } }
    fun upd(block: (LauncherSettings) -> LauncherSettings) = vm.updateSettings(block)

    OverlayScaffold(title = "SETTINGS", onClose = { vm.back() }) {
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Panel(Modifier.fillMaxWidth(), title = "ANIMATION") {
                    Text("INTENSITY", color = MatrixColors.Text, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Intensity.values().forEach { i ->
                            MatrixButton(
                                i.name, Modifier.weight(1f),
                                accent = if (s.intensity == i) MatrixColors.Neon else MatrixColors.Muted,
                                onClick = { upd { it.copy(intensity = i) } },
                            )
                        }
                    }
                    SwitchRow("MATRIX CODE RAIN", s.codeRain) { v -> upd { it.copy(codeRain = v) } }
                    SwitchRow("SCANLINES", s.scanlines) { v -> upd { it.copy(scanlines = v) } }
                    SwitchRow("RADAR ANIMATION", s.radarAnim) { v -> upd { it.copy(radarAnim = v) } }
                    SwitchRow("REDUCE MOTION (STATIC FRAMES)", s.reduceMotion) { v -> upd { it.copy(reduceMotion = v) } }
                    SwitchRow("STARTUP SEQUENCE", s.bootSequence) { v -> upd { it.copy(bootSequence = v) } }
                }
            }
            item {
                Panel(Modifier.fillMaxWidth(), title = "FEEDBACK") {
                    SwitchRow("SOUND EFFECTS (DEFAULT OFF)", s.sound) { v -> upd { it.copy(sound = v) } }
                    SwitchRow("HAPTIC FEEDBACK", s.haptics) { v -> upd { it.copy(haptics = v) } }
                }
            }
            item {
                Panel(Modifier.fillMaxWidth(), title = "THEME") {
                    ThemeId.values().toList().chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            row.forEach { t ->
                                MatrixButton(
                                    t.label, Modifier.weight(1f),
                                    accent = if (s.theme == t.name) t.neon else MatrixColors.Muted,
                                    onClick = { upd { it.copy(theme = t.name) } },
                                )
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
            item {
                Panel(Modifier.fillMaxWidth(), title = "DISPLAY") {
                    SwitchRow("24-HOUR CLOCK", s.is24h) { v -> upd { it.copy(is24h = v) } }
                    SwitchRow("HIGH CONTRAST", s.highContrast) { v -> upd { it.copy(highContrast = v) } }
                    SwitchRow("MATRIX GREEN ICON TINT", s.matrixIcons) { v -> upd { it.copy(matrixIcons = v) } }
                    Text("THEME BRIGHTNESS", color = MatrixColors.Text, style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = s.brightness,
                        onValueChange = { v -> upd { it.copy(brightness = v) } },
                        valueRange = 0.3f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = MatrixColors.Neon,
                            activeTrackColor = MatrixColors.Neon,
                            inactiveTrackColor = MatrixColors.Muted,
                        ),
                    )
                    StepperRow("APP GRID COLUMNS", s.gridColumns, 3, 6, 1) { v -> upd { it.copy(gridColumns = v) } }
                    StepperRow("ICON SIZE", s.iconSize, 36, 80, 4) { v -> upd { it.copy(iconSize = v) } }
                    StepperRow("TERMINAL TEXT SIZE", s.terminalTextSize, 10, 20, 1) { v -> upd { it.copy(terminalTextSize = v) } }
                    Spacer(Modifier.height(6.dp))
                    Text("DEVICE LABEL (HEADER)", color = MatrixColors.Text, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(4.dp))
                    BasicTextField(
                        value = s.deviceLabel,
                        onValueChange = { v -> upd { it.copy(deviceLabel = v.take(32)) } },
                        singleLine = true,
                        textStyle = TextStyle(color = MatrixColors.SoftWhite, fontFamily = FontFamily.Monospace, fontSize = 14.sp),
                        cursorBrush = SolidColor(MatrixColors.Neon),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MatrixColors.Muted, ButtonShape)
                            .background(MatrixColors.Background, ButtonShape)
                            .padding(10.dp),
                    )
                }
            }
            item {
                Panel(Modifier.fillMaxWidth(), title = "PERFORMANCE & HUD") {
                    SwitchRow("BATTERY-FRIENDLY (LOW FPS, NO RADAR)", s.batteryFriendly) { v -> upd { it.copy(batteryFriendly = v) } }
                    SwitchRow("HUD TELEMETRY PANEL", s.hudVisible) { v -> upd { it.copy(hudVisible = v) } }
                    SwitchRow("TILT PARALLAX (MOTION SENSOR)", s.parallax) { v -> upd { it.copy(parallax = v) } }
                    StepperRow("CODE RAIN SPEED %", s.rainSpeed, 50, 200, 10) { v -> upd { it.copy(rainSpeed = v) } }
                    Spacer(Modifier.height(6.dp))
                    Text("DEFAULT HOME PANEL", color = MatrixColors.Text, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("CORE", "GLOBE", "RADAR").forEachIndexed { i, n ->
                            MatrixButton(
                                n, Modifier.weight(1f),
                                accent = if (s.defaultPanel == i) MatrixColors.Neon else MatrixColors.Muted,
                                onClick = { upd { it.copy(defaultPanel = i) } },
                            )
                        }
                    }
                }
            }
            item { AiSettingsPanel(vm, s) }
            item {
                Panel(Modifier.fillMaxWidth(), title = "VOICE") {
                    SwitchRow("SHOW MIC BUTTONS", s.voiceEnabled) { v -> upd { it.copy(voiceEnabled = v) } }
                    Text(
                        "Voice starts only when you tap MIC and listens once. The speech service on your phone " +
                            "(usually Google) may need internet unless an offline language pack is installed. " +
                            "Nothing is recorded in the background.",
                        color = MatrixColors.Muted, style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            item {
                Panel(Modifier.fillMaxWidth(), title = "FAVORITES") {
                    if (favApps.isEmpty()) {
                        Text("NONE. LONG-PRESS ANY APP TO PIN IT. USE < > TO REORDER.", color = MatrixColors.Muted, style = MaterialTheme.typography.labelSmall)
                    }
                    favApps.forEach { app ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(app.label, color = MatrixColors.Text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1)
                            MatrixButton("<", hPad = 8.dp, onClick = { vm.moveFavorite(app.key, -1) })
                            Spacer(Modifier.width(4.dp))
                            MatrixButton(">", hPad = 8.dp, onClick = { vm.moveFavorite(app.key, 1) })
                            Spacer(Modifier.width(4.dp))
                            MatrixButton("REMOVE", accent = ErrorColor, hPad = 6.dp, onClick = { vm.toggleFavorite(app.key) })
                        }
                    }
                }
            }
            item {
                Panel(Modifier.fillMaxWidth(), title = "DEFAULT LAUNCHER") {
                    Text(
                        "Android > Settings > Apps > Default apps > Home app > MATRIX CYBER LAUNCHER. " +
                            "Or press the Home button and choose it from the list. To go back, pick another launcher there.",
                        color = MatrixColors.Text, style = MaterialTheme.typography.labelSmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    MatrixButton("OPEN HOME APP SETTINGS", Modifier.fillMaxWidth(), accent = MatrixColors.Neon2, onClick = { openHomeSettings(ctx) })
                }
            }
            item {
                Panel(Modifier.fillMaxWidth(), title = "PRIVACY") {
                    Text(
                        "No analytics, ads, accounts or tracking. The network is only read (connection state), unless YOU enable " +
                            "the optional remote AI above - then only the question you type is sent to the endpoint you entered. " +
                            "Microphone is requested only when you tap MIC. No location, contacts, SMS, accessibility " +
                            "or notification access is used. Settings and favorites stay on this device.",
                        color = MatrixColors.Text, style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            item {
                Column {
                    MatrixButton("RESTORE DEFAULT SETTINGS", Modifier.fillMaxWidth(), accent = WarnColor, onClick = { vm.resetSettings() })
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = MatrixColors.Text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MatrixColors.Background,
                checkedTrackColor = MatrixColors.Neon,
                uncheckedThumbColor = MatrixColors.Muted,
                uncheckedTrackColor = MatrixColors.Surface,
            ),
        )
    }
}

@Composable
private fun StepperRow(title: String, value: Int, min: Int, max: Int, step: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = MatrixColors.Text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        MatrixButton("-", onClick = { onChange((value - step).coerceAtLeast(min)) })
        Text("$value", color = MatrixColors.Neon, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(40.dp), maxLines = 1)
        MatrixButton("+", onClick = { onChange((value + step).coerceAtMost(max)) })
    }
}

@Composable
private fun AiSettingsPanel(vm: MainViewModel, s: LauncherSettings) {
    val savedKey by vm.aiKey.collectAsState()
    var url by remember(s.aiBaseUrl) { mutableStateOf(s.aiBaseUrl) }
    var model by remember(s.aiModel) { mutableStateOf(s.aiModel) }
    var key by remember { mutableStateOf("") }
    Panel(Modifier.fillMaxWidth(), title = "AI ASSISTANT (OPTIONAL REMOTE)") {
        Text(
            "The assistant works fully offline with built-in rules. For general questions you may connect YOUR OWN " +
                "OpenAI-compatible HTTPS endpoint. Off by default. When on, only the text you type in the assistant " +
                "is sent to that endpoint (never apps, contacts or device data). Your key is stored on this device " +
                "only, never in the app's code. Free access is not guaranteed - it depends on your provider.",
            color = MatrixColors.Muted, style = MaterialTheme.typography.labelSmall,
        )
        SwitchRow("ENABLE REMOTE AI", s.aiEnabled) { v -> vm.updateSettings { it.copy(aiEnabled = v) } }
        SettingsField("ENDPOINT (https://.../v1)", url, false) { url = it.take(200) }
        SettingsField("MODEL NAME", model, false) { model = it.take(80) }
        SettingsField(if (savedKey.isNotBlank()) "API KEY (SAVED - TYPE TO REPLACE)" else "API KEY", key, true) { key = it.take(300) }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MatrixButton("SAVE", Modifier.weight(1f), accent = MatrixColors.Neon2, onClick = {
                vm.updateSettings { it.copy(aiBaseUrl = url.trim(), aiModel = model.trim()) }
                if (key.isNotBlank()) {
                    vm.saveAiKey(key)
                    key = ""
                }
                vm.postMessage("AI SETTINGS SAVED")
            })
            MatrixButton("CLEAR KEY", Modifier.weight(1f), accent = ErrorColor, onClick = {
                vm.saveAiKey("")
                key = ""
                vm.postMessage("API KEY CLEARED")
            })
        }
    }
}

@Composable
private fun SettingsField(label: String, value: String, password: Boolean, onChange: (String) -> Unit) {
    Spacer(Modifier.height(6.dp))
    Text(label, color = MatrixColors.Text, style = MaterialTheme.typography.labelSmall)
    Spacer(Modifier.height(2.dp))
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        textStyle = TextStyle(color = MatrixColors.SoftWhite, fontFamily = FontFamily.Monospace, fontSize = 14.sp),
        cursorBrush = SolidColor(MatrixColors.Neon),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MatrixColors.Muted, ButtonShape)
            .background(MatrixColors.Background, ButtonShape)
            .padding(10.dp),
    )
}
