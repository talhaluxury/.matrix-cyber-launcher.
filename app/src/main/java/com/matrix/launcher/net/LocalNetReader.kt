package com.matrix.launcher.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.Inet4Address

/** Reads the active network from Android. Needs only ACCESS_NETWORK_STATE; no location or Wi-Fi permissions. */
class LocalNetReader(context: Context) {
    private val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun read(): LocalNetInfo {
        return try {
            val net = cm.activeNetwork ?: return LocalNetInfo.OFFLINE
            val caps = cm.getNetworkCapabilities(net) ?: return LocalNetInfo.OFFLINE
            val lp = cm.getLinkProperties(net)
            val transport = when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WI-FI"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "BLUETOOTH"
                else -> "OTHER"
            }
            var ip: String? = null
            var prefix: Int? = null
            var gateway: String? = null
            val dns = ArrayList<String>()
            if (lp != null) {
                for (la in lp.linkAddresses) {
                    val a = la.address
                    if (a is Inet4Address && ip == null) {
                        ip = a.hostAddress
                        prefix = la.prefixLength
                    }
                }
                for (r in lp.routes) {
                    val gw = r.gateway
                    if (r.isDefaultRoute && gw is Inet4Address && gateway == null) gateway = gw.hostAddress
                }
                for (d in lp.dnsServers) d.hostAddress?.let { dns.add(it) }
            }
            LocalNetInfo(
                connected = true,
                transport = transport,
                osValidated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
                metered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
                interfaceName = lp?.interfaceName,
                ipv4 = ip,
                prefix = prefix,
                gateway = gateway,
                dnsServers = dns,
            )
        } catch (e: Exception) {
            LocalNetInfo.OFFLINE
        }
    }
}
