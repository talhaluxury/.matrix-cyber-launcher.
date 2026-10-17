package com.matrix.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.matrix.launcher.ui.theme.MatrixColors
import kotlinx.coroutines.delay

/** Short, skippable launcher-init animation. Tap anywhere to skip. The home screen is already live underneath. */
@Composable
fun BootSequence(appCount: Int, error: String?, animate: Boolean, onDone: () -> Unit) {
    val base = listOf(
        "MATRIX CORE INITIALIZING",
        "LOADING INTERFACE",
        "RESTORING USER SETTINGS",
        "INITIALIZING APP INDEX",
        "SYSTEM READY",
    )
    var shown by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (shown < base.size) {
            delay(if (animate) 260L else 80L)
            shown++
        }
        delay(350)
        onDone()
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.96f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDone() }
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start,
    ) {
        Text("> LAUNCHER INIT (NOT A SYSTEM BOOT)", color = MatrixColors.Muted, style = MaterialTheme.typography.labelSmall)
        for (i in 0 until shown.coerceAtMost(base.size)) {
            val suffix = when {
                i == 3 && error != null -> "  [ERROR]"
                i == 3 && appCount > 0 -> "  [$appCount APPS]"
                i == 3 -> "  [...]"
                else -> "  [OK]"
            }
            val color = if (i == 3 && error != null) ErrorColor else MatrixColors.Neon
            TypewriterText(base[i] + suffix, animate, color, MaterialTheme.typography.bodyMedium, 12)
        }
        if (shown > 0) Text("TAP TO SKIP", color = MatrixColors.Muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 18.dp))
    }
}
