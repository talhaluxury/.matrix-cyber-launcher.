package com.matrix.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.matrix.launcher.MainViewModel
import com.matrix.launcher.data.AppInfo
import com.matrix.launcher.data.AppShortcut
import com.matrix.launcher.ui.theme.MatrixColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AppDrawer(vm: MainViewModel, focusSearch: Boolean, onVoice: () -> Unit) {
    val apps by vm.apps.collectAsState()
    val favorites by vm.favorites.collectAsState()
    val recents by vm.recents.collectAsState()
    val settings by vm.settings.collectAsState()
    val query by vm.query.collectAsState()
    val error by vm.appError.collectAsState()
    val focus = remember { FocusRequester() }
    val q = query.trim()
    var cat by remember { mutableStateOf("ALL") }
    val cats = remember(apps) { listOf("ALL") + apps.map { it.category }.distinct().sorted() }
    val activeCat = if (cat in cats) cat else "ALL"
    val filtered = remember(apps, q, activeCat) {
        apps.filter { (q.isEmpty() || it.label.contains(q, ignoreCase = true)) && (activeCat == "ALL" || it.category == activeCat) }
    }
    val favApps = remember(apps, favorites) { favorites.mapNotNull { k -> apps.firstOrNull { it.key == k } } }
    val recentApps = remember(apps, recents) { recents.mapNotNull { k -> apps.firstOrNull { it.key == k } } }

    LaunchedEffect(focusSearch) {
        if (focusSearch) runCatching { focus.requestFocus() }
    }

    OverlayScaffold(
        title = "APP INDEX // ${apps.size}",
        onClose = { vm.back() },
        trailing = {
            MatrixButton(if (settings.listView) "GRID" else "LIST", onClick = { vm.updateSettings { it.copy(listView = !it.listView) } })
        },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .liquidGlass(ButtonShape)
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(">", color = MatrixColors.Neon, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = query,
                onValueChange = { vm.setQuery(it) },
                singleLine = true,
                textStyle = TextStyle(color = MatrixColors.SoftWhite, fontFamily = FontFamily.Monospace, fontSize = 15.sp),
                cursorBrush = SolidColor(MatrixColors.Neon),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { filtered.firstOrNull()?.let { vm.launch(it) } }),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focus)
                    .onPreviewKeyEvent { e ->
                        if ((e.key == Key.Enter || e.key == Key.NumPadEnter) && e.type == KeyEventType.KeyDown) {
                            filtered.firstOrNull()?.let { vm.launch(it) }
                            true
                        } else false
                    },
                decorationBox = { inner ->
                    Box(Modifier.padding(vertical = 8.dp)) {
                        if (query.isEmpty()) Text("SEARCH APPS...  (CTRL+K)", color = MatrixColors.Muted, style = MaterialTheme.typography.bodyMedium)
                        inner()
                    }
                },
            )
            if (settings.voiceEnabled) MatrixButton("MIC", accent = MatrixColors.Neon2, onClick = onVoice)
        }
        Spacer(Modifier.height(8.dp))
        if (cats.size > 2) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                items(cats, key = { it }) { c ->
                    MatrixButton(c, accent = if (c == activeCat) MatrixColors.Neon else MatrixColors.Muted, onClick = { cat = c })
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        if (filtered.isEmpty()) {
            Panel(Modifier.fillMaxWidth()) {
                if (apps.isEmpty()) {
                    Text(error ?: "NO LAUNCHABLE APPS FOUND", color = ErrorColor, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    MatrixButton("REFRESH", onClick = { vm.refreshApps() })
                } else {
                    Text("NO APPLICATIONS MATCH \"$q\"", color = MatrixColors.Text, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    MatrixButton("CLEAR SEARCH", onClick = { vm.setQuery("") })
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(if (settings.listView) 1 else settings.gridColumns),
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (q.isEmpty() && activeCat == "ALL" && favApps.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) { StripSection("FAVORITES", favApps, vm, settings.iconSize) }
                }
                if (q.isEmpty() && activeCat == "ALL" && recentApps.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) { StripSection("RECENT", recentApps, vm, settings.iconSize) }
                }
                if (q.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text("ALL APPS (A-Z)", color = MatrixColors.Neon2, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
                items(filtered, key = { it.key }) { app ->
                    AppCell(app, vm, settings.iconSize, settings.listView)
                }
            }
        }
    }
}

@Composable
private fun StripSection(title: String, list: List<AppInfo>, vm: MainViewModel, iconDp: Int) {
    Column(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        Text(title, color = MatrixColors.Neon2, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(vertical = 4.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(list, key = { it.key }) { app ->
                Column(
                    Modifier.feedbackClick({ vm.launch(app) }, { vm.openMenuFor(app) }).padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AppIcon(app, vm.appRepo, (iconDp - 8).dp)
                    Text(
                        app.label, color = MatrixColors.Text, style = MaterialTheme.typography.labelSmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width((iconDp + 8).dp), textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun AppCell(app: AppInfo, vm: MainViewModel, iconDp: Int, list: Boolean) {
    if (list) {
        Row(
            Modifier.fillMaxWidth().feedbackClick({ vm.launch(app) }, { vm.openMenuFor(app) }).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(app, vm.appRepo, (iconDp - 12).dp)
            Spacer(Modifier.width(12.dp))
            Text(app.label, color = MatrixColors.Text, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    } else {
        Column(
            Modifier.fillMaxWidth().feedbackClick({ vm.launch(app) }, { vm.openMenuFor(app) }).padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppIcon(app, vm.appRepo, iconDp.dp)
            Spacer(Modifier.height(4.dp))
            Text(
                app.label, color = MatrixColors.Text, style = MaterialTheme.typography.labelSmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            )
        }
    }
}

/** Long-press menu: app shortcuts (when available), open, pin, app info, uninstall (system confirmation). */
@Composable
fun AppMenuDialog(app: AppInfo, isFavorite: Boolean, vm: MainViewModel) {
    val shortcuts by produceState(emptyList<AppShortcut>(), app.key) {
        value = withContext(Dispatchers.IO) { vm.appRepo.shortcutsFor(app) }
    }
    Dialog(onDismissRequest = { vm.closeMenu() }) {
        Panel(Modifier.fillMaxWidth(), title = app.label.uppercase()) {
            shortcuts.forEach { sc ->
                MatrixButton("> " + sc.label.uppercase(), Modifier.fillMaxWidth(), accent = MatrixColors.Neon2, onClick = {
                    vm.closeMenu()
                    vm.startShortcut(sc)
                })
                Spacer(Modifier.height(6.dp))
            }
            MatrixButton("OPEN", Modifier.fillMaxWidth(), onClick = { vm.closeMenu(); vm.launch(app) })
            Spacer(Modifier.height(6.dp))
            MatrixButton(if (isFavorite) "UNPIN FROM FAVORITES" else "PIN TO FAVORITES", Modifier.fillMaxWidth(), onClick = {
                vm.toggleFavorite(app.key)
                vm.closeMenu()
            })
            Spacer(Modifier.height(6.dp))
            MatrixButton("APP INFO", Modifier.fillMaxWidth(), onClick = { vm.closeMenu(); vm.openAppInfo(app) })
            Spacer(Modifier.height(6.dp))
            MatrixButton("UNINSTALL", Modifier.fillMaxWidth(), accent = ErrorColor, onClick = { vm.closeMenu(); vm.uninstall(app) })
        }
    }
}
