package com.matrix.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.matrix.launcher.MainViewModel
import com.matrix.launcher.anim.RadarView
import com.matrix.launcher.system.SoundFx
import com.matrix.launcher.ui.theme.MatrixColors

@Composable
fun RadarScreen(vm: MainViewModel) {
    val fx = LocalFx.current
    val fb = LocalFeedback.current
    var fast by remember { mutableStateOf(false) }
    OverlayScaffold(title = "RADAR // SIMULATION", onClose = { vm.back() }) {
        Text(
            "VISUAL EFFECT ONLY. Blips are simulated decoration - no devices, networks or signals are being scanned.",
            color = WarnColor, style = MaterialTheme.typography.labelSmall,
        )
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            RadarView(Modifier.widthIn(max = 420.dp).fillMaxWidth(), fx = fx, fast = fast)
        }
        MatrixButton(
            if (fast) "SWEEP: FAST (TAP FOR NORMAL)" else "SWEEP: NORMAL (TAP FOR FAST)",
            Modifier.fillMaxWidth(),
            onClick = {
                fast = !fast
                fb.play(SoundFx.Type.SCAN)
            },
        )
    }
}
