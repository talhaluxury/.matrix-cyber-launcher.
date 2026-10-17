package com.matrix.launcher.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.matrix.launcher.anim.Fx
import com.matrix.launcher.data.AppInfo
import com.matrix.launcher.data.AppRepository
import com.matrix.launcher.system.Feedback
import com.matrix.launcher.ui.theme.MatrixColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

val LocalFx = staticCompositionLocalOf { Fx() }
val LocalFeedback = staticCompositionLocalOf { Feedback.None }

val PanelShape = CutCornerShape(topStart = 0.dp, topEnd = 10.dp, bottomEnd = 0.dp, bottomStart = 10.dp)
val ButtonShape = CutCornerShape(6.dp)
val WarnColor = Color(0xFFFFC107)
val ErrorColor = Color(0xFFFF4D4D)

/** Click + long-click with press scale, ripple, optional haptic/sound. Also focusable for keyboards. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.feedbackClick(onClick: () -> Unit, onLongClick: (() -> Unit)? = null): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, label = "press")
    val fb = LocalFeedback.current
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .combinedClickable(
            interactionSource = source,
            indication = LocalIndication.current,
            onClick = {
                fb.tap()
                onClick()
            },
            onLongClick = if (onLongClick != null) {
                {
                    fb.tap()
                    onLongClick()
                }
            } else null,
        )
}

@Composable
fun Panel(modifier: Modifier = Modifier, title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val fx = LocalFx.current
    val border = MatrixColors.Neon.copy(alpha = if (fx.highContrast) 0.85f else 0.35f)
    Column(
        modifier
            .background(MatrixColors.Surface.copy(alpha = 0.84f), PanelShape)
            .border(1.dp, border, PanelShape)
            .padding(12.dp)
    ) {
        if (title != null) {
            Text(title, color = MatrixColors.Neon2, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(6.dp))
        }
        content()
    }
}

@Composable
fun MatrixButton(text: String, modifier: Modifier = Modifier, accent: Color = MatrixColors.Neon, onClick: () -> Unit) {
    Box(
        modifier
            .border(1.dp, accent.copy(alpha = 0.7f), ButtonShape)
            .background(accent.copy(alpha = 0.08f), ButtonShape)
            .feedbackClick(onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = accent, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

@Composable
fun PulseDot(color: Color, animate: Boolean, size: Dp = 8.dp) {
    val a = if (animate) {
        val tr = rememberInfiniteTransition(label = "pulse")
        tr.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "pulseAlpha",
        ).value
    } else 1f
    Box(Modifier.size(size).alpha(a).background(color, CircleShape))
}

@Composable
fun TypewriterText(text: String, animate: Boolean, color: Color, style: TextStyle, msPerChar: Long = 22) {
    var shown by remember(text) { mutableIntStateOf(if (animate) 0 else text.length) }
    LaunchedEffect(text, animate) {
        if (!animate) {
            shown = text.length
        } else {
            while (shown < text.length) {
                delay(msPerChar)
                shown++
            }
        }
    }
    Text(text.take(shown), color = color, style = style)
}

@Composable
fun AppIcon(app: AppInfo, repo: AppRepository, size: Dp, modifier: Modifier = Modifier) {
    val bmp by produceState<ImageBitmap?>(initialValue = repo.cachedIcon(app), key1 = app.key) {
        value = repo.cachedIcon(app) ?: withContext(Dispatchers.IO) { repo.loadIcon(app) }
    }
    val b = bmp
    if (b != null) {
        Image(bitmap = b, contentDescription = app.label, modifier = modifier.size(size), contentScale = ContentScale.Fit)
    } else {
        Box(modifier.size(size).border(1.dp, MatrixColors.Muted, PanelShape), contentAlignment = Alignment.Center) {
            Text(app.label.take(1).uppercase(), color = MatrixColors.Neon, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
fun OverlayScaffold(
    title: String,
    onClose: () -> Unit,
    trailing: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MatrixColors.Background.copy(alpha = 0.95f))
            .pointerInput(Unit) { detectTapGestures { } }
            .systemBarsPadding()
            .imePadding()
            .padding(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = MatrixColors.Neon, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1)
            trailing()
            MatrixButton("[X]", accent = MatrixColors.Neon2, onClick = onClose)
        }
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
fun StatRow(label: String, value: String, valueColor: Color = MatrixColors.Text) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MatrixColors.Neon2.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(10.dp))
        Text(value, color = valueColor, style = MaterialTheme.typography.bodyMedium)
    }
}
