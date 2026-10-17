package com.matrix.launcher

import android.Manifest
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
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
import com.matrix.launcher.system.WidgetController
import com.matrix.launcher.ui.LauncherRoot
import com.matrix.launcher.ui.LocalFx
import com.matrix.launcher.ui.theme.MatrixTheme

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()
    private var uiVisible by mutableStateOf(false)

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

    private val widgets by lazy { WidgetController(this) }
    private var pendingWidgetId = -1

    private val bindLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val id = pendingWidgetId
        if (res.resultCode == RESULT_OK && id != -1) configureWidget(id) else cancelPendingWidget()
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
                visible = uiVisible,
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
                    LauncherRoot(
                        vm,
                        widgets = widgets,
                        onAddWidget = { addWidget(it) },
                        onRemoveWidget = { removeWidget(it) },
                        onVoiceClick = { onVoiceClick() },
                        onMicConfirmed = { micPermission.launch(Manifest.permission.RECORD_AUDIO) },
                    )
                }
            }
        }
    }

    // ---------- widgets (AppWidgetHost) ----------
    private fun addWidget(info: AppWidgetProviderInfo) {
        val id = widgets.allocate()
        pendingWidgetId = id
        if (widgets.manager.bindAppWidgetIdIfAllowed(id, info.provider)) {
            configureWidget(id)
        } else {
            val i = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
            try {
                bindLauncher.launch(i)
            } catch (e: Exception) {
                cancelPendingWidget()
                vm.postMessage("WIDGET BIND NOT ALLOWED ON THIS DEVICE")
            }
        }
    }

    private fun configureWidget(id: Int) {
        val info = widgets.info(id)
        if (info?.configure != null) {
            try {
                widgets.host.startAppWidgetConfigureActivityForResult(this, id, 0, REQ_CONFIGURE, null)
            } catch (e: Exception) {
                finishWidget(id)
            }
        } else {
            finishWidget(id)
        }
    }

    private fun finishWidget(id: Int) {
        vm.addWidget(id)
        pendingWidgetId = -1
    }

    private fun cancelPendingWidget() {
        if (pendingWidgetId != -1) widgets.delete(pendingWidgetId)
        pendingWidgetId = -1
    }

    private fun removeWidget(id: Int) {
        widgets.delete(id)
        vm.removeWidget(id)
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_CONFIGURE) {
            val id = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId) ?: pendingWidgetId
            if (resultCode == RESULT_OK && id != -1) finishWidget(id) else cancelPendingWidget()
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
        widgets.start()
    }

    override fun onResume() {
        super.onResume()
        uiVisible = true
    }

    override fun onPause() {
        uiVisible = false
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
        runCatching { unregisterReceiver(pkgReceiver) }
        widgets.stop()
        voice.destroy()
        vm.setVoiceState(VoiceState.IDLE)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Home button pressed while we are already the foreground launcher: return to the home panel.
        if (intent.hasCategory(Intent.CATEGORY_HOME)) vm.goHome()
    }

    private companion object {
        const val REQ_CONFIGURE = 4711
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
