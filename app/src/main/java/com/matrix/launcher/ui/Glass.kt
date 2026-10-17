package com.matrix.launcher.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.matrix.launcher.ui.theme.MatrixColors

/** iPhone-style squircle used for app icon tiles. */
val IconShape = RoundedCornerShape(percent = 26)

/** Duotone: keeps icon brightness, maps it to dark-green -> neon-green so icons match the Matrix theme. */
val MatrixIconFilter: ColorFilter = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            0.047f, 0.157f, 0.016f, 0f, 0f,
            0.245f, 0.823f, 0.083f, 0f, 8f,
            0.017f, 0.057f, 0.006f, 0f, 3f,
            0f, 0f, 0f, 1f, 0f,
        )
    )
)

/**
 * "Liquid glass" body: dark translucent base, tinted vertical gradient, soft outer glow,
 * top specular gloss and a bright rim. Pure drawing, no blur, so it is cheap on mid-range phones.
 */
fun Modifier.liquidGlass(shape: Shape, tint: Color = MatrixColors.Neon, strength: Float = 1f): Modifier =
    drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val base = SolidColor(MatrixColors.Background.copy(alpha = 0.72f))
        val body = Brush.verticalGradient(
            listOf(tint.copy(alpha = 0.24f * strength), tint.copy(alpha = 0.07f * strength), tint.copy(alpha = 0.15f * strength))
        )
        val glow = SolidColor(tint.copy(alpha = 0.10f * strength))
        val gloss = Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.28f), Color.White.copy(alpha = 0.04f), Color.Transparent),
            startY = 0f,
            endY = size.height * 0.55f,
        )
        val rim = Brush.linearGradient(
            listOf(Color.White.copy(alpha = 0.75f), tint.copy(alpha = 0.25f), tint.copy(alpha = 0.7f)),
            start = Offset.Zero,
            end = Offset(size.width, size.height),
        )
        val glowStroke = Stroke(width = 5.dp.toPx())
        val rimStroke = Stroke(width = 1.2f.dp.toPx())
        onDrawBehind {
            drawOutline(outline, brush = glow, style = glowStroke)
            drawOutline(outline, brush = base)
            drawOutline(outline, brush = body)
            drawOutline(outline, brush = gloss)
            drawOutline(outline, brush = rim, style = rimStroke)
        }
    }

/** Glass sheen drawn ON TOP of content (used over app icons): gloss + rim only. */
fun Modifier.glassSheen(shape: Shape, tint: Color = MatrixColors.Neon): Modifier =
    drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val gloss = Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.30f), Color.White.copy(alpha = 0.05f), Color.Transparent),
            startY = 0f,
            endY = size.height * 0.5f,
        )
        val rim = Brush.linearGradient(
            listOf(Color.White.copy(alpha = 0.8f), tint.copy(alpha = 0.3f), tint.copy(alpha = 0.75f)),
            start = Offset.Zero,
            end = Offset(size.width, size.height),
        )
        val rimStroke = Stroke(width = 1.4f.dp.toPx())
        onDrawWithContent {
            drawContent()
            drawOutline(outline, brush = gloss)
            drawOutline(outline, brush = rim, style = rimStroke)
        }
    }
