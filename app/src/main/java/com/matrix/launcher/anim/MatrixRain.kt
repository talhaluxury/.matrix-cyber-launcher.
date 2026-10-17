package com.matrix.launcher.anim

import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import java.util.Random
import android.graphics.Color as AColor
import android.graphics.Paint as NativePaint

private const val GLYPHS = "0123456789ABCDEF<>+=*#%&/|:;"
private const val GLYPH_ROWS = 64

/** All random data is created once; frames only do arithmetic. */
private class RainColumns(val count: Int) {
    private val rnd = Random(42)
    val xFrac = FloatArray(count) { (it + 0.5f) / count }
    val speed = FloatArray(count) { 0.6f + rnd.nextFloat() * 1.2f }
    val offset = FloatArray(count) { rnd.nextFloat() * 100f }
    val trail = IntArray(count) { 8 + rnd.nextInt(8) }
    val glyphs = CharArray(count * GLYPH_ROWS) { GLYPHS[rnd.nextInt(GLYPHS.length)] }
}

@Composable
fun MatrixRain(modifier: Modifier = Modifier, fx: Fx) {
    val columns = remember(fx.rainColumns) { RainColumns(fx.rainColumns) }
    val time = rememberFrameTime(fx.animate, fx.fps)
    val paint = remember {
        NativePaint().apply {
            isAntiAlias = true
            typeface = Typeface.MONOSPACE
        }
    }
    Canvas(modifier.fillMaxSize().clipToBounds()) {
        val t = time.value * fx.speed
        val cell = 15.dp.toPx()
        paint.textSize = 12.dp.toPx()
        val rows = (size.height / cell).toInt() + 1
        val cycle = (rows + 20).toFloat()
        val dim = (if (fx.highContrast) 0.4f else 1f) * fx.brightness
        drawIntoCanvas { canvas ->
            val nc = canvas.nativeCanvas
            for (i in 0 until columns.count) {
                val x = columns.xFrac[i] * size.width
                val head = (t * columns.speed[i] * 6f + columns.offset[i]) % cycle
                val headRow = head.toInt()
                val trail = columns.trail[i]
                for (k in 0 until trail) {
                    val row = headRow - k
                    if (row < 0 || row >= rows) continue
                    val a = (255 * (1f - k / trail.toFloat()) * 0.5f * dim).toInt().coerceIn(0, 255)
                    paint.color = if (k == 0) {
                        AColor.argb((a + 90).coerceAtMost(255), 200, 255, 200)
                    } else {
                        AColor.argb(a, 57, 255, 20)
                    }
                    val gi = if (k == 0) (row * 7 + i + (t * 8f).toInt()) and 63 else (row * 7 + i) and 63
                    nc.drawText(columns.glyphs, i * GLYPH_ROWS + gi, 1, x, (row + 1) * cell, paint)
                }
            }
        }
    }
}
