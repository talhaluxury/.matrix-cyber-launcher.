package com.matrix.launcher

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.matrix.launcher.anim.Fx
import com.matrix.launcher.system.VoiceController
import com.matrix.launcher.ui.LauncherRoot
import com.matrix.launcher.ui.LocalFx
import com.matrix.launcher.ui.theme.MatrixTheme

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()
    private var visible by mutableStateOf(false)

    private val voice by lazy {
        VoiceController(
            this,
            onResult = { vm.handleVoice(it) },
            onState = { vm.setVoiceState(it) },
            onFail = { vm.postMessage(it) },
        )
    }

    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) voice.start() else vm.postMessage("MICROPHONE DENIED - TOUCH CONTROLS STILL WORK")
    }

    private val pkgReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            vm.refreshApps()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val s by vm.settings.collectAsState()
            val systemReduce = systemReducesMotion()
            val fx = Fx(
                visible = visible,
                rain = s.codeRain,
                scanlines = s.scanlines,
                radar = s.radarAnim,
                intensity = s.intensity,
                reduceMotion = s.reduceMotion || systemReduce,
                brightness = s.brightness,
                highContrast = s.highContrast,
            )
            CompositionLocalProvider(LocalFx provides fx) {
                MatrixTheme {
                    LauncherRoot(vm, onVoiceClick = { onVoiceClick() }, onMicConfirmed = { micPermission.launch(Manifest.permission.RECORD_AUDIO) })
                }
            }
        }
    }

    private fun systemReducesMotion(): Boolean = try {
        Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    } catch (e: Exception) {
        false
    }

    private fun onVoiceClick() {
        if (!voice.isAvailable()) {
            vm.postMessage("VOICE UNAVAILABLE: NO SPEECH SERVICE ON THIS DEVICE")
            return
        }
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (granted) voice.start() else vm.showVoiceExplain()
    }

    override fun onStart() {
        super.onStart()
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this, pkgReceiver, f, ContextCompat.RECEIVER_NOT_EXPORTED)
        vm.refreshApps()
    }

    override fun onResume() {
        super.onResume()
        visible = true
    }

    override fun onPause() {
        visible = false
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
        runCatching { unregisterReceiver(pkgReceiver) }
        voice.destroy()
        vm.setVoiceState(VoiceState.IDLE)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Home button pressed while we are already the foreground launcher: return to the home panel.
        if (intent.hasCategory(Intent.CATEGORY_HOME)) vm.goHome()
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_ESCAPE) {
            vm.back()
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_K && event?.isCtrlPressed == true) {
            vm.openDrawer(true)
            return true
        }
        return super.onKeyUp(keyCode, event)
    }
}
