package com.matrix.launcher.ui

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.matrix.launcher.MainViewModel
import com.matrix.launcher.ui.theme.MatrixColors

/** Neural interface: offline rule-based assistant, optionally backed by the user's own remote AI. */
@Composable
fun AssistantScreen(vm: MainViewModel, onVoice: () -> Unit) {
    val lines by vm.chat.collectAsState()
    val busy by vm.aiBusy.collectAsState()
    val settings by vm.settings.collectAsState()
    val key by vm.aiKey.collectAsState()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val focus = remember { FocusRequester() }
    val remote = settings.aiEnabled && settings.aiBaseUrl.isNotBlank() && settings.aiModel.isNotBlank() && key.isNotBlank()

    LaunchedEffect(lines.size) { if (lines.isNotEmpty()) listState.animateScrollToItem(lines.size - 1) }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    fun send() {
        vm.askAssistant(input)
        input = ""
    }

    OverlayScaffold(title = "NEURAL INTERFACE // ASSISTANT", onClose = { vm.back() }) {
        Text(
            if (remote) "MODE: LOCAL RULES FIRST, THEN YOUR REMOTE AI (ONLY THE TYPED QUESTION IS SENT)"
            else "MODE: LOCAL / OFFLINE. NO AI MODEL. GENERAL QUESTIONS NEED THE OPTIONAL REMOTE AI (SETTINGS).",
            color = if (remote) WarnColor else MatrixColors.Muted, style = MaterialTheme.typography.labelSmall,
        )
        Spacer(Modifier.height(6.dp))
        Panel(Modifier.fillMaxWidth().weight(1f)) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(lines) { line ->
                    Text(
                        line,
                        color = if (line.startsWith(">")) MatrixColors.Neon2 else MatrixColors.Text,
                        style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = settings.terminalTextSize.sp),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth().liquidGlass(ButtonShape).padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = input,
                onValueChange = { input = it.take(300) },
                singleLine = true,
                textStyle = TextStyle(color = MatrixColors.SoftWhite, fontFamily = FontFamily.Monospace, fontSize = 14.sp),
                cursorBrush = SolidColor(MatrixColors.Neon),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
                modifier = Modifier.weight(1f).padding(vertical = 8.dp).focusRequester(focus),
                decorationBox = { inner ->
                    androidx.compose.foundation.layout.Box {
                        if (input.isEmpty()) Text("ASK OR COMMAND...", color = MatrixColors.Muted, style = MaterialTheme.typography.bodyMedium)
                        inner()
                    }
                },
            )
            if (settings.voiceEnabled) {
                MatrixButton("MIC", accent = MatrixColors.Neon2, onClick = onVoice)
                Spacer(Modifier.width(6.dp))
            }
            MatrixButton(if (busy) "..." else "SEND", accent = MatrixColors.Neon2, onClick = { send() })
        }
    }
}
