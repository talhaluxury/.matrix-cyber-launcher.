package com.matrix.launcher.system

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import com.matrix.launcher.data.DeviceStatus
import com.matrix.launcher.data.NetType

/** One-shot reads of real, locally available device info. Called every few seconds, never per frame. */
class SystemStatusReader(context: Context) {
    private val ctx = context.applicationContext

    fun read(): DeviceStatus {
        var pct: Int? = null
        var charging = false
        val bi = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (bi != null) {
            val level = bi.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = bi.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level >= 0 && scale > 0) pct = level * 100 / scale
            val st = bi.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
        }

        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)

        var type = NetType.OFFLINE
        var validated = false
        try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val net = cm.activeNetwork
            val caps = if (net != null) cm.getNetworkCapabilities(net) else null
            if (caps != null) {
                type = when {
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetType.WIFI
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetType.CELLULAR
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetType.ETHERNET
                    else -> NetType.OTHER
                }
                validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            }
        } catch (e: Exception) {
            type = NetType.OFFLINE
        }

        var free: Double? = null
        var total: Double? = null
        try {
            val sf = StatFs(Environment.getDataDirectory().path)
            val gb = 1024.0 * 1024.0 * 1024.0
            free = sf.availableBytes / gb
            total = sf.totalBytes / gb
        } catch (e: Exception) {
            // storage info unavailable
        }

        val mb = 1024L * 1024L
        return DeviceStatus(
            batteryPct = pct,
            charging = charging,
            memAvailMb = mi.availMem / mb,
            memTotalMb = mi.totalMem / mb,
            lowMemory = mi.lowMemory,
            networkType = type,
            networkValidated = validated,
            uptimeMs = SystemClock.elapsedRealtime(),
            storageFreeGb = free,
            storageTotalGb = total,
            refreshedAtMs = System.currentTimeMillis(),
        )
    }
}
