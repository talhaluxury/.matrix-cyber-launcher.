package com.matrix.launcher.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

/** Original launcher palettes. Enum names double as the persisted id and the THEME terminal argument. */
enum class ThemeId(
    val label: String,
    val background: Color,
    val surface: Color,
    val neon: Color,
    val neon2: Color,
    val muted: Color,
    val text: Color,
) {
    MATRIX_GREEN("MATRIX GREEN", Color(0xFF020503), Color(0xFF07110A), Color(0xFF39FF14), Color(0xFF00FF88), Color(0xFF174A2B), Color(0xFFD6FFD9)),
    STEALTH_BLACK("STEALTH BLACK", Color(0xFF000000), Color(0xFF0B0B0B), Color(0xFF9AA5A0), Color(0xFFC9D1CD), Color(0xFF2A2F2D), Color(0xFFE2E6E4)),
    CYAN_CYBER("CYAN CYBER", Color(0xFF010508), Color(0xFF06131A), Color(0xFF00E5FF), Color(0xFF4DF3FF), Color(0xFF0E4552), Color(0xFFD4F8FF)),
    RED_ALERT("RED ALERT", Color(0xFF080102), Color(0xFF16070A), Color(0xFFFF2D3F), Color(0xFFFF6A5E), Color(0xFF55141C), Color(0xFFFFD9DC)),
    MINIMAL_HUD("MINIMAL HUD", Color(0xFF030403), Color(0xFF0A0D0B), Color(0xFF7FD68A), Color(0xFFA5E8AE), Color(0xFF223127), Color(0xFFDCEBDF));

    companion object {
        fun fromId(id: String?): ThemeId = values().firstOrNull { it.name == id } ?: MATRIX_GREEN
    }
}

/**
 * Colors are backed by snapshot state, so every composable or draw block that reads them
 * updates automatically when the theme changes.
 */
object MatrixColors {
    var themeId by mutableStateOf(ThemeId.MATRIX_GREEN)
    val Background: Color get() = themeId.background
    val Surface: Color get() = themeId.surface
    val Neon: Color get() = themeId.neon
    val Neon2: Color get() = themeId.neon2
    val Muted: Color get() = themeId.muted
    val Text: Color get() = themeId.text
    val SoftWhite = Color(0xFFEAF5EC)
}

private fun schemeFor(t: ThemeId) = darkColorScheme(
    primary = t.neon,
    secondary = t.neon2,
    background = t.background,
    surface = t.surface,
    onPrimary = t.background,
    onBackground = t.text,
    onSurface = t.text,
    outline = t.muted,
)

private val Mono = FontFamily.Monospace
private val MatrixTypography = Typography(
    headlineMedium = TextStyle(fontFamily = Mono, fontSize = 22.sp, letterSpacing = 2.sp),
    bodyLarge = TextStyle(fontFamily = Mono, fontSize = 15.sp),
    bodyMedium = TextStyle(fontFamily = Mono, fontSize = 13.sp),
    labelSmall = TextStyle(fontFamily = Mono, fontSize = 11.sp, letterSpacing = 1.sp),
)

@Composable
fun MatrixTheme(themeId: ThemeId = ThemeId.MATRIX_GREEN, content: @Composable () -> Unit) {
    SideEffect { if (MatrixColors.themeId != themeId) MatrixColors.themeId = themeId }
    MaterialTheme(colorScheme = schemeFor(themeId), typography = MatrixTypography, content = content)
}
