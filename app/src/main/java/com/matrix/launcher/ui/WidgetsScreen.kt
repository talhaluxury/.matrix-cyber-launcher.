package com.matrix.launcher.ui

import android.appwidget.AppWidgetProviderInfo
import android.view.View
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.matrix.launcher.MainViewModel
import com.matrix.launcher.system.WidgetController
import com.matrix.launcher.ui.theme.MatrixColors

/** Real Android widgets, hosted through AppWidgetHost. Android asks for your approval when a widget is added. */
@Composable
fun WidgetsScreen(
    vm: MainViewModel,
    controller: WidgetController,
    onAdd: (AppWidgetProviderInfo) -> Unit,
    onRemove: (Int) -> Unit,
) {
    val ids by vm.widgetIds.collectAsState()
    var picking by remember { mutableStateOf(false) }

    OverlayScaffold(
        title = "WIDGETS",
        onClose = { vm.back() },
        trailing = { MatrixButton("+ ADD", accent = MatrixColors.Neon2, onClick = { picking = true }) },
    ) {
        if (ids.isEmpty()) {
            Panel(Modifier.fillMaxWidth()) {
                Text("NO WIDGETS YET. TAP + ADD TO PLACE A REAL ANDROID WIDGET.", color = MatrixColors.Text, style = MaterialTheme.typography.bodyMedium)
            }
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(ids, key = { it }) { id ->
                val info = remember(id) { controller.info(id) }
                Panel(Modifier.fillMaxWidth()) {
                    if (info == null) {
                        Text("WIDGET UNAVAILABLE (APP REMOVED?)", color = WarnColor, style = MaterialTheme.typography.bodyMedium)
                    } else {
                        val h = with(LocalDensity.current) { info.minHeight.toDp() }.coerceAtLeast(80.dp)
                        AndroidView(
                            factory = { ctx -> controller.createView(id) ?: View(ctx) },
                            modifier = Modifier.fillMaxWidth().height(h),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    MatrixButton("REMOVE", accent = ErrorColor, onClick = { onRemove(id) })
                }
            }
        }
    }
    if (picking) {
        WidgetPicker(controller, onPick = { picking = false; onAdd(it) }, onDismiss = { picking = false })
    }
}

@Composable
private fun WidgetPicker(controller: WidgetController, onPick: (AppWidgetProviderInfo) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val items = remember {
        controller.providers().map { p ->
            val label = try { p.loadLabel(ctx.packageManager) ?: p.provider.packageName } catch (e: Exception) { p.provider.packageName }
            p to label
        }.sortedBy { it.second.lowercase() }
    }
    Dialog(onDismissRequest = onDismiss) {
        Panel(Modifier.fillMaxWidth(), title = "PICK A WIDGET") {
            if (items.isEmpty()) {
                Text("NO WIDGET PROVIDERS FOUND ON THIS DEVICE", color = WarnColor, style = MaterialTheme.typography.bodyMedium)
            }
            LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(items) { (info, label) ->
                    MatrixButton(label, Modifier.fillMaxWidth(), onClick = { onPick(info) })
                }
            }
        }
    }
}
