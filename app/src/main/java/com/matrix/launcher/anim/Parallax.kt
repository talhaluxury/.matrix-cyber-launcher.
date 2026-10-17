package com.matrix.launcher.anim

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext

/**
 * Optional tilt parallax. Uses the accelerometer (present on virtually every phone) to estimate tilt, in the
 * range -1..1. Listens only while [enabled]; if the sensor is missing it silently stays at zero.
 * Read the returned State inside graphicsLayer/draw lambdas only, so tilting never recomposes.
 */
@Composable
fun rememberTilt(enabled: Boolean): State<Offset> {
    val ctx = LocalContext.current
    val tilt = remember { mutableStateOf(Offset.Zero) }
    DisposableEffect(enabled) {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (!enabled || sm == null || sensor == null) {
            tilt.value = Offset.Zero
            onDispose { }
        } else {
            val l = object : SensorEventListener {
                override fun onSensorChanged(e: SensorEvent) {
                    val x = (-e.values[0] / 9.81f).coerceIn(-1f, 1f)
                    val y = ((e.values[1] / 9.81f - 0.7f) * 1.5f).coerceIn(-1f, 1f)
                    val cur = tilt.value
                    tilt.value = Offset(cur.x + (x - cur.x) * 0.1f, cur.y + (y - cur.y) * 0.1f)
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            sm.registerListener(l, sensor, SensorManager.SENSOR_DELAY_UI)
            onDispose { sm.unregisterListener(l) }
        }
    }
    return tilt
}
