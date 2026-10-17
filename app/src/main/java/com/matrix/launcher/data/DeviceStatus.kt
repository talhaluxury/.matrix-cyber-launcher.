package com.matrix.launcher.data

import java.util.Locale

enum class NetType { OFFLINE, WIFI, CELLULAR, ETHERNET, OTHER }

data class DeviceStatus(
    val batteryPct: Int?,
    val charging: Boolean,
    val memAvailMb: Long,
    val memTotalMb: Long,
    val lowMemory: Boolean,
    val networkType: NetType,
    val networkValidated: Boolean,
    val uptimeMs: Long,
    val storageFreeGb: Double?,
    val storageTotalGb: Double?,
    val refreshedAtMs: Long,
) {
    fun batteryText(): String =
        if (batteryPct == null) "UNAVAILABLE" else "$batteryPct%" + if (charging) " CHARGING" else ""

    fun networkShort(): String = when (networkType) {
        NetType.OFFLINE -> "OFFLINE"
        NetType.WIFI -> "WI-FI"
        NetType.CELLULAR -> "CELLULAR"
        NetType.ETHERNET -> "ETHERNET"
        NetType.OTHER -> "CONNECTED"
    }

    fun networkText(): String {
        if (networkType == NetType.OFFLINE) return "OFFLINE"
        return networkShort() + if (networkValidated) " / INTERNET VERIFIED" else " / INTERNET NOT VERIFIED"
    }

    fun memShort(): String = "$memAvailMb / $memTotalMb MB"

    fun memText(): String = "$memAvailMb MB FREE OF $memTotalMb MB (SYSTEM-REPORTED RAM)"

    fun uptimeText(): String {
        val s = uptimeMs / 1000
        val d = s / 86400
        val h = (s % 86400) / 3600
        val m = (s % 3600) / 60
        return if (d > 0) "${d}D ${h}H ${m}M" else "${h}H ${m}M"
    }

    fun storageText(): String {
        val f = storageFreeGb
        val t = storageTotalGb
        return if (f != null && t != null) String.format(Locale.US, "%.1f / %.1f GB FREE", f, t) else "UNAVAILABLE"
    }
}
