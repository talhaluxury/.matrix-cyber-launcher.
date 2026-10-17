package com.matrix.launcher.data

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

class AppRepository(context: Context) {
    private val ctx = context.applicationContext
    private val pm: PackageManager = ctx.packageManager
    private val iconCache = LruCache<String, ImageBitmap>(256)

    /** Real launchable apps from PackageManager, sorted A-Z. Safe to call off the main thread. */
    fun loadApps(): List<AppInfo> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val list = pm.queryIntentActivities(intent, 0)
        return list.mapNotNull { ri ->
            val ai = ri.activityInfo ?: return@mapNotNull null
            if (ai.packageName == ctx.packageName) return@mapNotNull null
            val label = try { ri.loadLabel(pm).toString() } catch (e: Exception) { ai.packageName }
            AppInfo(label = label, packageName = ai.packageName, activityName = ai.name)
        }.distinctBy { it.key }.sortedBy { it.label.lowercase() }
    }

    fun cachedIcon(app: AppInfo): ImageBitmap? = iconCache.get(app.key)

    fun loadIcon(app: AppInfo): ImageBitmap? {
        iconCache.get(app.key)?.let { return it }
        return try {
            val d = pm.getActivityIcon(ComponentName(app.packageName, app.activityName))
            val bmp = d.toBitmap(96, 96).asImageBitmap()
            iconCache.put(app.key, bmp)
            bmp
        } catch (e: Exception) {
            null
        }
    }

    /** Returns false when the app can no longer be launched (removed / disabled). */
    fun launch(app: AppInfo): Boolean {
        val i = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(ComponentName(app.packageName, app.activityName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return try {
            ctx.startActivity(i)
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: SecurityException) {
            false
        }
    }

    fun openAppInfo(app: AppInfo): Boolean = try {
        ctx.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    } catch (e: Exception) { false }

    fun uninstall(app: AppInfo): Boolean = try {
        ctx.startActivity(
            Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    } catch (e: Exception) { false }

    fun isDefaultHome(): Boolean {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val ri = pm.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)
        return ri?.activityInfo?.packageName == ctx.packageName
    }

    fun versionName(): String = try {
        pm.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
    } catch (e: Exception) { "?" }

    private fun resolvePkg(intent: Intent): String? {
        val ri = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) ?: return null
        val p = ri.activityInfo?.packageName ?: return null
        return if (p == "android") null else p
    }

    /** Dock shortcuts resolved from really installed apps (intent resolution first, label match as fallback). */
    fun dock(apps: List<AppInfo>): List<DockEntry> {
        if (apps.isEmpty()) return emptyList()
        fun byPkg(p: String?): AppInfo? = if (p == null) null else apps.firstOrNull { it.packageName == p }
        fun byLabel(vararg kws: String): AppInfo? =
            apps.firstOrNull { a -> kws.any { a.label.contains(it, ignoreCase = true) } }
        val out = ArrayList<DockEntry>()
        fun add(role: String, app: AppInfo?) {
            if (app != null && out.none { it.app.packageName == app.packageName }) out.add(DockEntry(role, app))
        }
        add("PHONE", byPkg(resolvePkg(Intent(Intent.ACTION_DIAL))) ?: byLabel("phone", "dialer"))
        add("MESSAGES", byPkg(resolvePkg(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")))) ?: byLabel("messages", "messaging", "sms"))
        add("CAMERA", byPkg(resolvePkg(Intent(MediaStore.ACTION_IMAGE_CAPTURE))) ?: byLabel("camera"))
        add(
            "BROWSER",
            byPkg(resolvePkg(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).addCategory(Intent.CATEGORY_BROWSABLE)))
                ?: byLabel("chrome", "browser", "firefox")
        )
        add("WHATSAPP", apps.firstOrNull { it.packageName == "com.whatsapp" } ?: byLabel("whatsapp"))
        return out
    }
}
