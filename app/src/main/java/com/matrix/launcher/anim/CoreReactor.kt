package com.matrix.launcher.anim

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.matrix.launcher.ui.theme.MatrixColors
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Reactor-style CORE: rotating dashed rings, orbitals, pulsing radial ticks, glow. Faster while [scanning]. */
@Composable
fun CoreReactor(modifier: Modifier = Modifier, fx: Fx, scanning: Boolean, label: String, sub: String) {
    val time = rememberFrameTime(fx.animate, fx.fps)
    val acc = remember { FloatArray(2) } // 0 = accumulated phase (s), 1 = last t
    val mul = if (scanning) 4f else 1f
    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val t = time.value
            val dt = (t - acc[1]).coerceIn(0f, 0.25f)
            acc[1] = t
            acc[0] += dt * mul * fx.speed
            val p = acc[0]
            val b = fx.brightness
            val c = center
            val r = size.minDimension / 2f
            val neon = MatrixColors.Neon
            val w = 2.dp.toPx()

            drawCircle(
                brush = Brush.radialGradient(listOf(neon.copy(alpha = 0.26f * b), Color.Transparent), center = c, radius = r),
                radius = r,
                center = c,
            )
            rotate(p * 12f, c) {
                drawCircle(
                    neon.copy(alpha = 0.8f * b), r * 0.95f, c,
                    style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 10f))),
                )
            }
            rotate(-p * 20f, c) {
                drawCircle(
                    MatrixColors.Neon2.copy(alpha = 0.7f * b), r * 0.82f, c,
                    style = Stroke(w * 0.7f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 12f))),
                )
            }
            rotate(p * 35f, c) {
                drawCircle(neon.copy(alpha = 0.5f * b), r * 0.58f, c, style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(40f, 16f))))
            }
            drawCircle(neon.copy(alpha = 0.35f * b), r * 0.4f, c, style = Stroke(1.dp.toPx()))

            // radial ticks with travelling pulse
            val inner = r * 0.66f
            val outer = r * 0.74f
            for (i in 0 until 48) {
                val rad = Math.toRadians(i * 7.5).toFloat()
                val a = (0.25f + 0.75f * abs(sin(p * 2f + i * 0.4f))) * b
                drawLine(
                    neon.copy(alpha = a.coerceIn(0f, 1f)),
                    Offset(c.x + cos(rad) * inner, c.y + sin(rad) * inner),
                    Offset(c.x + cos(rad) * outer, c.y + sin(rad) * outer),
                    strokeWidth = 1.5f.dp.toPx(),
                )
            }
            // orbital indicators
            val orbits = floatArrayOf(r * 0.95f, r * 0.82f, r * 0.58f)
            val speeds = floatArrayOf(40f, -70f, 110f)
            for (i in orbits.indices) {
                val rad = Math.toRadians((p * speeds[i] + i * 120f).toDouble()).toFloat()
                drawCircle(
                    Color.White.copy(alpha = 0.9f * b), 3.dp.toPx(),
                    Offset(c.x + cos(rad) * orbits[i], c.y + sin(rad) * orbits[i]),
                )
            }
            if (scanning) {
                drawArc(
                    color = neon.copy(alpha = 0.25f * b),
                    startAngle = p * 180f,
                    sweepAngle = 40f,
                    useCenter = true,
                    topLeft = Offset(c.x - r * 0.58f, c.y - r * 0.58f),
                    size = androidx.compose.ui.geometry.Size(r * 1.16f, r * 1.16f),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = MatrixColors.Neon, style = MaterialTheme.typography.headlineMedium)
            Text(sub, color = MatrixColors.Text, style = MaterialTheme.typography.labelSmall)
        }
    }
}
