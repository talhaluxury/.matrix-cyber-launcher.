package com.matrix.launcher.data

import com.matrix.launcher.core.AppRef

data class AppInfo(val label: String, val packageName: String, val activityName: String) {
    val key: String get() = "$packageName/$activityName"
    fun ref() = AppRef(key, label)
}

data class DockEntry(val role: String, val app: AppInfo)
