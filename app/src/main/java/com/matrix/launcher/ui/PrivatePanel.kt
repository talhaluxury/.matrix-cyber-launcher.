package com.matrix.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.matrix.launcher.MainViewModel
import com.matrix.launcher.core.PinHasher
import com.matrix.launcher.data.PinState
import com.matrix.launcher.ui.theme.MatrixColors

/**
 * Launcher-only vault: private shortcuts behind a PIN. It does NOT replace Android's lock screen and
 * does NOT protect any other app or data on the phone.
 */
@Composable
fun PrivatePanel(vm: MainViewModel) {
    val pin by vm.pinState.collectAsState()
    val unlocked by vm.privateUnlocked.collectAsState()
    OverlayScaffold(title = "VAULT // LAUNCHER ONLY", onClose = { vm.back() }) {
        Text(
            "Private shortcut list protected by a PIN inside this launcher only. It is not Android's lock " +
                "screen and does not protect other apps or files.",
            color = WarnColor, style = MaterialTheme.typography.labelSmall,
        )
        Spacer(Modifier.height(10.dp))
        when {
            pin is PinState.Loading -> Text("LOADING...", color = MatrixColors.Text, style = MaterialTheme.typography.bodyMedium)
            pin is PinState.None -> PinSetup(vm, title = "CREATE A 4-8 DIGIT PIN")
            !unlocked -> PinEntry(vm)
            else -> VaultContent(vm)
        }
    }
}

@Composable
private fun PinField(value: String, hint: String, onChange: (String) -> Unit, onDone: () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().liquidGlass(ButtonShape).padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = value,
            onValueChange = { v -> onChange(v.filter { c -> c in '0'..'9' }.take(8)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            textStyle = TextStyle(color = MatrixColors.SoftWhite, fontFamily = FontFamily.Monospace, fontSize = 18.sp),
            cursorBrush = SolidColor(MatrixColors.Neon),
            modifier = Modifier.weight(1f).padding(vertical = 8.dp),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) Text(hint, color = MatrixColors.Muted, style = MaterialTheme.typography.bodyMedium)
                    inner()
                }
            },
        )
    }
}

@Composable
private fun PinSetup(vm: MainViewModel, title: String) {
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    fun save() {
        err = when {
            !PinHasher.isValidPin(a) -> "PIN MUST BE 4-8 DIGITS"
            a != b -> "PINS DO NOT MATCH"
            !vm.setPin(a) -> "COULD NOT SAVE PIN"
            else -> null
        }
    }
    Panel(Modifier.fillMaxWidth(), title = title) {
        PinField(a, "NEW PIN", { a = it })
        Spacer(Modifier.height(8.dp))
        PinField(b, "CONFIRM PIN", { b = it }, onDone = { save() })
        err?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = ErrorColor, style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(8.dp))
        MatrixButton("SAVE PIN", Modifier.fillMaxWidth(), accent = MatrixColors.Neon2, onClick = { save() })
        Spacer(Modifier.height(6.dp))
        Text("There is no recovery. If you forget the PIN, clear the launcher's storage in Android settings.", color = MatrixColors.Muted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun PinEntry(vm: MainViewModel) {
    var pin by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    fun go() {
        err = vm.unlockPrivate(pin)
        pin = ""
    }
    Panel(Modifier.fillMaxWidth(), title = "ENTER PIN") {
        PinField(pin, "PIN", { pin = it }, onDone = { go() })
        err?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = ErrorColor, style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(8.dp))
        MatrixButton("UNLOCK", Modifier.fillMaxWidth(), accent = MatrixColors.Neon2, onClick = { go() })
    }
}

@Composable
private fun VaultContent(vm: MainViewModel) {
    val apps by vm.apps.collectAsState()
    val keys by vm.privateApps.collectAsState()
    var picking by remember { mutableStateOf(false) }
    var changing by remember { mutableStateOf(false) }
    val mine = remember(apps, keys) { keys.mapNotNull { k -> apps.firstOrNull { it.key == k } } }

    if (changing) {
        PinSetup(vm, title = "CHOOSE A NEW PIN")
        Spacer(Modifier.height(8.dp))
        MatrixButton("DONE", Modifier.fillMaxWidth(), onClick = { changing = false })
        return
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        MatrixButton(if (picking) "BACK" else "ADD APP", Modifier.weight(1f), accent = MatrixColors.Neon2, onClick = { picking = !picking })
        MatrixButton("LOCK", Modifier.weight(1f), onClick = { vm.lockPrivate() })
        MatrixButton("CHANGE PIN", Modifier.weight(1f), hPad = 4.dp, onClick = { changing = true })
        MatrixButton("REMOVE PIN", Modifier.weight(1f), accent = ErrorColor, hPad = 4.dp, onClick = { vm.removePin() })
    }
    Spacer(Modifier.height(8.dp))
    if (picking) {
        Text("TAP AN APP TO ADD / REMOVE IT", color = MatrixColors.Neon2, style = MaterialTheme.typography.labelSmall)
        LazyColumn(Modifier.weight(1f)) {
            items(apps, key = { it.key }) { app ->
                val on = app.key in keys
                Row(
                    Modifier.fillMaxWidth().feedbackClick({ vm.togglePrivateApp(app.key) }).padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIcon(app, vm.appRepo, 36.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(app.label, color = MatrixColors.Text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (on) "[X]" else "[ ]", color = if (on) MatrixColors.Neon else MatrixColors.Muted, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    } else {
        if (mine.isEmpty()) {
            Text("NO PRIVATE SHORTCUTS YET. TAP ADD APP.", color = MatrixColors.Text, style = MaterialTheme.typography.bodyMedium)
        }
        LazyColumn(Modifier.weight(1f)) {
            items(mine, key = { it.key }) { app ->
                Row(
                    Modifier.fillMaxWidth().feedbackClick({ vm.launch(app) }, { vm.togglePrivateApp(app.key) }).padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIcon(app, vm.appRepo, 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(app.label, color = MatrixColors.Text, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Text("TAP TO OPEN, LONG-PRESS TO REMOVE FROM VAULT", color = MatrixColors.Muted, style = MaterialTheme.typography.labelSmall)
    }
}
