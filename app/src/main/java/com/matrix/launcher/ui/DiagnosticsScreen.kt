package com.matrix.launcher.ui

import android.Manifest
import android.content.pm.PackageManager
import android.speech.SpeechRecognizer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.matrix.launcher.MainViewModel
import com.matrix.launcher.system.openHomeSettings
import com.matrix.launcher.ui.theme.MatrixColors
import java.text.DateFormat
import java.util.Date

/** Accurate, local-only diagnostics. Nothing here is simulated. */
@Composable
fun DiagnosticsScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val status by vm.status.collectAsState()
    val apps by vm.apps.collectAsState()
    LaunchedEffect(Unit) { vm.refreshStatus() }
    val micGranted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    val speech = remember { SpeechRecognizer.isRecognitionAvailable(ctx) }
    val isHome = remember(status) { vm.isDefaultHome() }

    OverlayScaffold(title = "DIAGNOSTICS // LOCAL", onClose = { vm.back() }) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Panel(Modifier.fillMaxWidth(), title = "DEVICE") {
                val s = status
                if (s == null) {
                    Text("READING...", color = MatrixColors.Text, style = MaterialTheme.typography.bodyMedium)
                } else {
                    StatRow("BATTERY", s.batteryText())
                    StatRow("NETWORK", s.networkText())
                    StatRow("RAM", s.memText())
                    StatRow("LOW MEMORY", if (s.lowMemory) "YES" else "NO")
                    StatRow("STORAGE (DATA)", s.storageText())
                    StatRow("UPTIME", s.uptimeText())
                    StatRow("LAST REFRESH", DateFormat.getTimeInstance().format(Date(s.refreshedAtMs)))
                }
            }
            Panel(Modifier.fillMaxWidth(), title = "LAUNCHER") {
                StatRow("VERSION", vm.versionName())
                StatRow("DEFAULT HOME", if (isHome) "YES" else "NO", if (isHome) MatrixColors.Neon else WarnColor)
                StatRow("APPS INDEXED", apps.size.toString())
            }
            Panel(Modifier.fillMaxWidth(), title = "PERMISSIONS") {
                StatRow("MICROPHONE", if (micGranted) "GRANTED" else "NOT GRANTED (ASKED ONLY WHEN YOU TAP MIC)")
                StatRow("SPEECH SERVICE", if (speech) "AVAILABLE" else "NOT AVAILABLE")
                StatRow("NOTIFICATION ACCESS", "NOT USED")
                StatRow("LOCATION / CONTACTS / SMS", "NOT USED")
            }
        }
        Spacer(Modifier.height(8.dp))
        MatrixButton("REFRESH NOW", Modifier.fillMaxWidth(), onClick = { vm.refreshStatus(); vm.refreshApps() })
        Spacer(Modifier.height(6.dp))
        MatrixButton("OPEN DEFAULT HOME SETTINGS", Modifier.fillMaxWidth(), accent = MatrixColors.Neon2, onClick = { openHomeSettings(ctx) })
    }
}
