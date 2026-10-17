package com.matrix.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.matrix.launcher.MainViewModel
import com.matrix.launcher.Overlay
import com.matrix.launcher.VoiceState
import com.matrix.launcher.anim.Backdrop
import com.matrix.launcher.system.Feedback
import com.matrix.launcher.system.SoundFx
import com.matrix.launcher.ui.theme.MatrixColors
import kotlinx.coroutines.delay

@Composable
fun LauncherRoot(vm: MainViewModel, onVoiceClick: () -> Unit, onMicConfirmed: () -> Unit) {
    val fx = LocalFx.current
    val settings by vm.settings.collectAsState()
    val overlay by vm.overlay.collectAsState()
    val focusSearch by vm.focusSearch.collectAsState()
    val message by vm.message.collectAsState()
    val boot by vm.boot.collectAsState()
    val voice by vm.voiceState.collectAsState()
    val explain by vm.voiceExplain.collectAsState()
    val apps by vm.apps.collectAsState()
    val appError by vm.appError.collectAsState()
    val menuApp by vm.menuApp.collectAsState()
    val favorites by vm.favorites.collectAsState()
    val view = LocalView.current

    val sound = remember { SoundFx() }
    DisposableEffect(Unit) { onDispose { sound.release() } }
    val feedback = remember(view, settings.haptics, settings.sound) {
        Feedback(view, settings.haptics, sound, settings.sound)
    }

    // Telemetry is refreshed every 5 s, and only while the launcher is visible.
    LaunchedEffect(fx.visible) {
        while (fx.visible) {
            vm.refreshStatus()
            delay(5000)
        }
    }
    LaunchedEffect(message) {
        if (message != null) {
            delay(3500)
            vm.clearMessage()
        }
    }
    LaunchedEffect(overlay) { if (overlay == Overlay.DRAWER) feedback.play(SoundFx.Type.DRAWER) }
    LaunchedEffect(boot) { if (boot) feedback.play(SoundFx.Type.BOOT) }
    BackHandler(enabled = overlay != Overlay.NONE) { vm.back() }

    val enter: EnterTransition =
        if (fx.reduceMotion) EnterTransition.None else fadeIn(tween(160)) + slideInVertically(tween(200)) { it / 8 }
    val exit: ExitTransition = if (fx.reduceMotion) ExitTransition.None else fadeOut(tween(120))

    CompositionLocalProvider(LocalFeedback provides feedback) {
        Box(Modifier.fillMaxSize().background(MatrixColors.Background)) {
            Backdrop(fx)
            HomeScreen(vm, onVoiceClick)

            AnimatedVisibility(overlay == Overlay.DRAWER, enter = enter, exit = exit) { AppDrawer(vm, focusSearch, onVoiceClick) }
            AnimatedVisibility(overlay == Overlay.TERMINAL, enter = enter, exit = exit) { TerminalPanel(vm) }
            AnimatedVisibility(overlay == Overlay.RADAR, enter = enter, exit = exit) { RadarScreen(vm) }
            AnimatedVisibility(overlay == Overlay.DIAGNOSTICS, enter = enter, exit = exit) { DiagnosticsScreen(vm) }
            AnimatedVisibility(overlay == Overlay.SETTINGS, enter = enter, exit = exit) { SettingsScreen(vm) }

            val banner = when {
                voice == VoiceState.LISTENING -> "LISTENING... SPEAK NOW"
                else -> message
            }
            if (banner != null) {
                Box(Modifier.align(Alignment.TopCenter).systemBarsPadding().padding(12.dp)) {
                    Panel(Modifier.fillMaxWidth()) {
                        Text(banner, color = MatrixColors.Neon, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            if (boot) {
                BootSequence(appCount = apps.size, error = appError, animate = fx.animate, onDone = { vm.dismissBoot() })
            }

            val m = menuApp
            if (m != null) AppMenuDialog(m, favorites.contains(m.key), vm)

            if (explain) {
                AlertDialog(
                    onDismissRequest = { vm.dismissVoiceExplain() },
                    title = { Text("MICROPHONE ACCESS") },
                    text = {
                        Text(
                            "Voice control listens once, only after you tap MIC. Your phone's speech service " +
                                "(often Google) may send the audio over the internet unless an offline pack is installed. " +
                                "Nothing is recorded in the background."
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            vm.dismissVoiceExplain()
                            onMicConfirmed()
                        }) { Text("CONTINUE") }
                    },
                    dismissButton = { TextButton(onClick = { vm.dismissVoiceExplain() }) { Text("CANCEL") } },
                )
            }
        }
    }
}
