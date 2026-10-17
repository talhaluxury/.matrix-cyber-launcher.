package com.matrix.launcher.anim

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.matrix.launcher.ui.theme.MatrixColors
import java.util.Random
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin

private const val NODES = 26
private const val TWO_PI = 6.2831855f
private const val PI_F = 3.1415927f

private class GlobeData {
    private val rnd = Random(7)
    // unit vectors (x, y, z) of simulated node positions, before rotation
    val vx = FloatArray(NODES)
    val vy = FloatArray(NODES)
    val vz = FloatArray(NODES)
    val arcA = IntArray(16)
    val arcB = IntArray(16)
    val arcPhase = FloatArray(16)

    init {
        for (i in 0 until NODES) {
            val lat = Math.toRadians(rnd.nextDouble() * 140.0 - 70.0).toFloat()
            val lon = Math.toRadians(rnd.nextDouble() * 360.0).toFloat()
            vx[i] = cos(lat) * sin(lon)
            vy[i] = sin(lat)
            vz[i] = cos(lat) * cos(lon)
        }
        for (k in 0 until arcA.size) {
            val a = rnd.nextInt(NODES)
            var b = rnd.nextInt(NODES)
            if (b == a) b = (a + 1) % NODES
            arcA[k] = a
            arcB[k] = b
            arcPhase[k] = rnd.nextFloat()
        }
    }
}

/**
 * Decorative 3D globe: rotating latitude/longitude lines, glowing nodes and animated arcs.
 * ALL nodes and arcs are SIMULATED and do not represent real locations or network activity.
 * Drag to rotate, pinch to zoom (when [interactive]).
 */
@Composable
fun CyberGlobe(modifier: Modifier = Modifier, fx: Fx, interactive: Boolean = true) {
    val time = rememberFrameTime(fx.animate, fx.fps)
    val data = remember { GlobeData() }
    // 0 = user yaw, 1 = pitch, 2 = zoom, 3 = last t, 4 = auto yaw
    val st = remember { floatArrayOf(0f, 0.35f, 1f, 0f, 0f) }
    var touchTick by remember { mutableIntStateOf(0) }
    val path = remember { Path() }
    val tmp = remember { FloatArray(3) }
    val tmp2 = remember { FloatArray(3) }

    val gesture = if (interactive) {
        Modifier.pointerInput(Unit) {
            detectTransformGestures { _, pan, zoom, _ ->
                st[0] += pan.x * 0.008f
                st[1] = (st[1] + pan.y * 0.008f).coerceIn(-1.2f, 1.2f)
                st[2] = (st[2] * zoom).coerceIn(0.6f, 2f)
                touchTick++
            }
        }
    } else Modifier

    Canvas(modifier.aspectRatio(1f).clipToBounds().then(gesture)) {
        @Suppress("UNUSED_VARIABLE")
        val redraw = touchTick // reading it makes touch changes redraw even when animation is paused
        val t = time.value
        val dt = (t - st[3]).coerceIn(0f, 0.25f)
        st[3] = t
        if (fx.animate) st[4] += dt * 0.35f * fx.speed
        val yaw = st[0] + st[4]
        val pitch = st[1]
        val cy = cos(yaw)
        val sy = sin(yaw)
        val cp = cos(pitch)
        val sp = sin(pitch)
        val b = fx.brightness
        val c = center
        val base = size.minDimension / 2f * 0.82f
        val r = base * st[2]
        val neon = MatrixColors.Neon
        val neon2 = MatrixColors.Neon2

        // rotate a world vector by yaw (around Y) then pitch (around X)
        fun rot(x: Float, y: Float, z: Float, out: FloatArray) {
            val x1 = x * cy + z * sy
            val z1 = z * cy - x * sy
            out[0] = x1
            out[1] = y * cp - z1 * sp
            out[2] = y * sp + z1 * cp
        }

        drawCircle(
            brush = Brush.radialGradient(listOf(neon.copy(alpha = 0.18f * b), Color.Transparent), center = c, radius = r * 1.25f),
            radius = r * 1.25f, center = c,
        )
        drawCircle(neon.copy(alpha = 0.55f * b), r, c, style = Stroke(1.5.dp.toPx()))

        // draws only the front-facing runs of a curve
        fun curve(n: Int, color: Color, width: Float, pointAt: (Int, FloatArray) -> Unit) {
            path.rewind()
            var pen = false
            for (i in 0..n) {
                pointAt(i, tmp)
                if (tmp[2] > 0f) {
                    val px = c.x + r * tmp[0]
                    val py = c.y - r * tmp[1]
                    if (!pen) { path.moveTo(px, py); pen = true } else path.lineTo(px, py)
                } else pen = false
            }
            drawPath(path, color, style = Stroke(width))
        }

        val gridColor = neon.copy(alpha = 0.28f * b)
        val gw = 1.dp.toPx()
        for (latDeg in intArrayOf(-60, -30, 0, 30, 60)) {
            val lat = Math.toRadians(latDeg.toDouble()).toFloat()
            val cl = cos(lat)
            val sl = sin(lat)
            curve(64, gridColor, gw) { i, out ->
                val lon = i / 64f * TWO_PI
                rot(cl * sin(lon), sl, cl * cos(lon), out)
            }
        }
        for (k in 0 until 12) {
            val lon = k / 12f * TWO_PI
            val sl = sin(lon)
            val cl = cos(lon)
            curve(48, gridColor, gw) { i, out ->
                val lat = -PI_F / 2f + i / 48f * PI_F
                rot(cos(lat) * sl, sin(lat), cos(lat) * cl, out)
            }
        }

        // simulated arcs along great circles, lifted off the surface
        for (k in data.arcA.indices) {
            val a = data.arcA[k]
            val bb = data.arcB[k]
            val dot = (data.vx[a] * data.vx[bb] + data.vy[a] * data.vy[bb] + data.vz[a] * data.vz[bb]).coerceIn(-0.999f, 0.999f)
            val omega = acos(dot)
            val so = sin(omega)
            fun arcPoint(u: Float, out: FloatArray) {
                val w1 = sin((1f - u) * omega) / so
                val w2 = sin(u * omega) / so
                val lift = 1f + 0.22f * sin(u * PI_F)
                rot(
                    (data.vx[a] * w1 + data.vx[bb] * w2) * lift,
                    (data.vy[a] * w1 + data.vy[bb] * w2) * lift,
                    (data.vz[a] * w1 + data.vz[bb] * w2) * lift,
                    out,
                )
            }
            curve(24, neon2.copy(alpha = 0.5f * b), 1.2f.dp.toPx()) { i, out -> arcPoint(i / 24f, out) }
            val u = (t * 0.25f * fx.speed + data.arcPhase[k]) % 1f
            arcPoint(u, tmp2)
            if (tmp2[2] > 0f) {
                val p = Offset(c.x + r * tmp2[0], c.y - r * tmp2[1])
                drawCircle(Color.White.copy(alpha = 0.9f * b), 2.5.dp.toPx(), p)
                drawCircle(neon2.copy(alpha = 0.35f * b), 6.dp.toPx(), p)
            }
        }

        // simulated nodes
        for (i in 0 until NODES) {
            rot(data.vx[i], data.vy[i], data.vz[i], tmp)
            if (tmp[2] > 0f) {
                val p = Offset(c.x + r * tmp[0], c.y - r * tmp[1])
                val s = 0.6f + 0.4f * tmp[2]
                val pulse = if (fx.animate) 0.6f + 0.4f * sin(t * 3f + i) else 1f
                drawCircle(neon.copy(alpha = 0.22f * b * pulse), 9.dp.toPx() * s, p)
                drawCircle(neon.copy(alpha = 0.95f * b), 2.8.dp.toPx() * s, p)
            }
        }
    }
}

/** Globe plus the mandatory "simulated" label. */
@Composable
fun CyberGlobeView(modifier: Modifier = Modifier, fx: Fx, interactive: Boolean = true) {
    Box(modifier, contentAlignment = Alignment.Center) {
        CyberGlobe(Modifier.fillMaxSize(), fx, interactive)
        Text(
            "SIMULATED DATA - NOT REAL NETWORK ACTIVITY",
            color = MatrixColors.Neon2.copy(alpha = 0.8f),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp),
        )
    }
}
