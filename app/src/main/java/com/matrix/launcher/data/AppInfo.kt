package com.matrix.launcher.data

import com.matrix.launcher.core.AppRef

data class AppInfo(
    val label: String,
    val packageName: String,
    val activityName: String,
    val category: String = "OTHER",
) {
    val key: String get() = "$packageName/$activityName"
    fun ref() = AppRef(key, label)
}

sealed class PinState {
    object Loading : PinState()
    object None : PinState()
    data class Set(val salt: String, val hash: String) : PinState()
}

data class DockEntry(val role: String, val app: AppInfo)

data class AppShortcut(val id: String, val pkg: String, val label: String)
