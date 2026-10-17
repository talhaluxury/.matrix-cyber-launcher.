package com.matrix.launcher.anim

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.matrix.launcher.ui.theme.MatrixColors
import kotlin.math.cos
import kotlin.math.sin

private class Blip(val r: Float, val deg: Float)

/** DECORATIVE radar. The blips are simulated and do not represent real devices or networks. */
@Composable
fun RadarView(modifier: Modifier = Modifier, fx: Fx, fast: Boolean = false) {
    val active = fx.animate && fx.radar
    val time = rememberFrameTime(active, fx.fps)
    val acc = remember { FloatArray(2) }
    val blips = remember { listOf(Blip(0.35f, 40f), Blip(0.62f, 150f), Blip(0.8f, 250f), Blip(0.5f, 310f), Blip(0.9f, 95f)) }
    Canvas(modifier.aspectRatio(1f)) {
        val t = time.value
        val dt = (t - acc[1]).coerceIn(0f, 0.25f)
        acc[1] = t
        acc[0] += dt * (if (fast) 2.2f else 1f)
        val sweep = (acc[0] * 90f) % 360f
        val b = fx.brightness
        val c = center
        val r = size.minDimension / 2f * 0.96f
        val neon = MatrixColors.Neon

        for (i in 1..4) drawCircle(neon.copy(alpha = 0.3f * b), r * i / 4f, c, style = Stroke(1.dp.toPx()))
        drawLine(neon.copy(alpha = 0.25f * b), Offset(c.x - r, c.y), Offset(c.x + r, c.y), 1.dp.toPx())
        drawLine(neon.copy(alpha = 0.25f * b), Offset(c.x, c.y - r), Offset(c.x, c.y + r), 1.dp.toPx())
        for (i in 0 until 72) {
            val rad = Math.toRadians(i * 5.0).toFloat()
            val len = if (i % 6 == 0) 10.dp.toPx() else 5.dp.toPx()
            drawLine(
                neon.copy(alpha = 0.5f * b),
                Offset(c.x + cos(rad) * (r - len), c.y + sin(rad) * (r - len)),
                Offset(c.x + cos(rad) * r, c.y + sin(rad) * r),
                1.dp.toPx(),
            )
        }
        rotate(sweep, c) {
            drawCircle(
                brush = Brush.sweepGradient(
                    0f to Color.Transparent,
                    0.8f to Color.Transparent,
                    1f to neon.copy(alpha = 0.5f * b),
                    center = c,
                ),
                radius = r,
                center = c,
            )
        }
        val sr = Math.toRadians(sweep.toDouble()).toFloat()
        drawLine(neon.copy(alpha = 0.9f * b), c, Offset(c.x + cos(sr) * r, c.y + sin(sr) * r), 2.dp.toPx())

        var best = -1f
        var bestPos = Offset.Zero
        for (bl in blips) {
            val diff = ((sweep - bl.deg) % 360f + 360f) % 360f
            val a = if (diff < 140f) 1f - diff / 140f else 0.12f
            val rad = Math.toRadians(bl.deg.toDouble()).toFloat()
            val pos = Offset(c.x + cos(rad) * r * bl.r, c.y + sin(rad) * r * bl.r)
            drawCircle(MatrixColors.Neon2.copy(alpha = (a * b).coerceIn(0f, 1f)), 4.dp.toPx(), pos)
            if (a > best) { best = a; bestPos = pos }
        }
        if (best > 0.5f) { // scanning crosshair on the freshest blip
            val k = 12.dp.toPx()
            val col = Color.White.copy(alpha = 0.8f * b)
            drawLine(col, Offset(bestPos.x - k, bestPos.y), Offset(bestPos.x - k / 2, bestPos.y), 1.5f)
            drawLine(col, Offset(bestPos.x + k / 2, bestPos.y), Offset(bestPos.x + k, bestPos.y), 1.5f)
            drawLine(col, Offset(bestPos.x, bestPos.y - k), Offset(bestPos.x, bestPos.y - k / 2), 1.5f)
            drawLine(col, Offset(bestPos.x, bestPos.y + k / 2), Offset(bestPos.x, bestPos.y + k), 1.5f)
        }
    }
}
