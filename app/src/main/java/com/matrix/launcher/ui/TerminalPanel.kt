package com.matrix.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.matrix.launcher.MainViewModel
import com.matrix.launcher.core.CommandParser
import com.matrix.launcher.system.SoundFx
import com.matrix.launcher.ui.theme.MatrixColors

@Composable
fun TerminalPanel(vm: MainViewModel) {
    val lines by vm.terminal.collectAsState()
    val history by vm.history.collectAsState()
    val settings by vm.settings.collectAsState()
    val fb = LocalFeedback.current
    var input by remember { mutableStateOf(TextFieldValue("")) }
    var hIdx by remember { mutableIntStateOf(-1) }
    val listState = rememberLazyListState()
    val focus = remember { FocusRequester() }
    val size = settings.terminalTextSize.sp
    val suggestions = remember(input.text) { CommandParser.autocomplete(input.text) }

    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.size - 1)
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    fun setText(s: String) {
        input = TextFieldValue(s, TextRange(s.length))
    }

    fun submit() {
        fb.play(SoundFx.Type.COMMAND)
        vm.submitTerminal(input.text)
        setText("")
        hIdx = -1
    }

    OverlayScaffold(title = "TERMINAL // SIMULATED SHELL", onClose = { vm.back() }) {
        Text(
            "Launcher commands only. No shell, no network, no system access.",
            color = MatrixColors.Muted, style = MaterialTheme.typography.labelSmall,
        )
        Spacer(Modifier.height(6.dp))
        Panel(Modifier.fillMaxWidth().weight(1f)) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(lines) { line ->
                    Text(
                        line,
                        color = if (line.startsWith(">")) MatrixColors.Neon2 else MatrixColors.Text,
                        style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = size),
                    )
                }
            }
        }
        if (suggestions.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                suggestions.take(4).forEach { s -> MatrixButton(s, onClick = { setText("$s ") }) }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .liquidGlass(ButtonShape)
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(">", color = MatrixColors.Neon, style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = size))
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = input,
                onValueChange = { input = it },
                singleLine = true,
                textStyle = TextStyle(color = MatrixColors.SoftWhite, fontFamily = FontFamily.Monospace, fontSize = size),
                cursorBrush = SolidColor(MatrixColors.Neon),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit() }),
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp)
                    .focusRequester(focus)
                    .onPreviewKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (e.key) {
                            Key.Enter, Key.NumPadEnter -> { submit(); true }
                            Key.DirectionUp -> {
                                if (history.isNotEmpty()) {
                                    hIdx = if (hIdx == -1) history.size - 1 else maxOf(0, hIdx - 1)
                                    setText(history[hIdx])
                                }
                                true
                            }
                            Key.DirectionDown -> {
                                if (history.isNotEmpty() && hIdx != -1) {
                                    if (hIdx < history.size - 1) { hIdx++; setText(history[hIdx]) } else { hIdx = -1; setText("") }
                                }
                                true
                            }
                            Key.Tab -> {
                                suggestions.firstOrNull()?.let { setText("$it ") }
                                true
                            }
                            else -> false
                        }
                    },
            )
            MatrixButton("^", onClick = {
                if (history.isNotEmpty()) {
                    hIdx = if (hIdx == -1) history.size - 1 else maxOf(0, hIdx - 1)
                    setText(history[hIdx])
                }
            })
            Spacer(Modifier.width(6.dp))
            MatrixButton("RUN", accent = MatrixColors.Neon2, onClick = { submit() })
        }
    }
}
