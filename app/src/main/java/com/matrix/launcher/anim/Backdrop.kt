package com.matrix.launcher.anim

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.matrix.launcher.ui.theme.MatrixColors

@Composable
fun MovingGrid(modifier: Modifier = Modifier, fx: Fx) {
    val time = rememberFrameTime(fx.animate, 20)
    Canvas(modifier.fillMaxSize().clipToBounds()) {
        val step = 36.dp.toPx()
        val off = (time.value * 10f * fx.speed) % step
        val color = MatrixColors.Neon.copy(alpha = 0.05f * fx.brightness)
        var x = -off
        while (x < size.width) {
            drawLine(color, Offset(x, 0f), Offset(x, size.height), 1f)
            x += step
        }
        var y = -off
        while (y < size.height) {
            drawLine(color, Offset(0f, y), Offset(size.width, y), 1f)
            y += step
        }
    }
}

@Composable
fun Scanlines(modifier: Modifier = Modifier, fx: Fx) {
    Box(modifier.fillMaxSize().clipToBounds()) {
        Canvas(Modifier.fillMaxSize()) {
            val step = 3.dp.toPx()
            val c = Color.Black.copy(alpha = 0.2f)
            var y = 0f
            while (y < size.height) {
                drawLine(c, Offset(0f, y), Offset(size.width, y), 1f)
                y += step
            }
        }
        if (fx.animate) {
            val time = rememberFrameTime(true, 20)
            Canvas(Modifier.fillMaxSize()) {
                val band = 100.dp.toPx()
                val y = (time.value * 90f) % (size.height + band * 2f) - band
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(Color.Transparent, MatrixColors.Neon.copy(alpha = 0.06f * fx.brightness), Color.Transparent),
                        startY = y,
                        endY = y + band,
                    ),
                    topLeft = Offset(0f, y),
                    size = Size(size.width, band),
                )
            }
        }
    }
}

@Composable
fun Backdrop(fx: Fx) {
    Box(Modifier.fillMaxSize()) {
        MovingGrid(fx = fx)
        if (fx.rain) MatrixRain(fx = fx)
        if (fx.scanlines) Scanlines(fx = fx)
    }
}
