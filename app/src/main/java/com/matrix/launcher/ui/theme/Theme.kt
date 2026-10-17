package com.matrix.launcher.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

object MatrixColors {
    val Background = Color(0xFF020503)
    val Surface = Color(0xFF07110A)
    val Neon = Color(0xFF39FF14)
    val Neon2 = Color(0xFF00FF88)
    val Muted = Color(0xFF174A2B)
    val Text = Color(0xFFD6FFD9)
    val SoftWhite = Color(0xFFEAF5EC)
}

private val Scheme = darkColorScheme(
    primary = MatrixColors.Neon,
    secondary = MatrixColors.Neon2,
    background = MatrixColors.Background,
    surface = MatrixColors.Surface,
    onPrimary = MatrixColors.Background,
    onBackground = MatrixColors.Text,
    onSurface = MatrixColors.Text,
    outline = MatrixColors.Muted,
)

private val Mono = FontFamily.Monospace
private val MatrixTypography = Typography(
    headlineMedium = TextStyle(fontFamily = Mono, fontSize = 22.sp, letterSpacing = 2.sp),
    bodyLarge = TextStyle(fontFamily = Mono, fontSize = 15.sp),
    bodyMedium = TextStyle(fontFamily = Mono, fontSize = 13.sp),
    labelSmall = TextStyle(fontFamily = Mono, fontSize = 11.sp, letterSpacing = 1.sp),
)

@Composable
fun MatrixTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = MatrixTypography, content = content)
}
