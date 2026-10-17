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
                Panel(Modifier.fillMaxWidth(), title = "DISPLAY") {
                    SwitchRow("24-HOUR CLOCK", s.is24h) { v -> upd { it.copy(is24h = v) } }
                    SwitchRow("HIGH CONTRAST", s.highContrast) { v -> upd { it.copy(highContrast = v) } }
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
                        "No analytics, ads, accounts or tracking. Network is only read (connection state), never used. " +
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
