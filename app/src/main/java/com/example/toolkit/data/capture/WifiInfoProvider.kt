package com.example.toolkit.data.capture

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import java.net.Inet4Address

object WifiInfoProvider {

    @SuppressLint("MissingPermission")
    fun current(context: Context): WifiSessionInfo {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        val network = cm.activeNetwork
        val caps = network?.let { cm.getNetworkCapabilities(it) }
        val links = network?.let { cm.getLinkProperties(it) }
        val wifiInfo = try {
            @Suppress("DEPRECATION")
            wm.connectionInfo
        } catch (_: Exception) {
            null
        }

        val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val isVpn = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true

        val ip = links?.linkAddresses
            ?.mapNotNull { it.address }
            ?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
            ?.hostAddress
            ?: legacyIp(wifiInfo)

        val gateway = links?.routes
            ?.firstOrNull { it.isDefaultRoute && it.gateway != null }
            ?.gateway
            ?.hostAddress
            ?: ""

        val dns = links?.dnsServers?.mapNotNull { it.hostAddress }.orEmpty()

        val ssid = when {
            !isWifi -> "Not on Wi‑Fi"
            wifiInfo?.ssid != null -> wifiInfo.ssid.removeSurrounding("\"")
            else -> "<unknown — need location permission>"
        }

        val bssid = wifiInfo?.bssid ?: "—"
        @Suppress("DEPRECATION")
        val speed = wifiInfo?.linkSpeed ?: 0
        @Suppress("DEPRECATION")
        val freq = if (Build.VERSION.SDK_INT >= 21) wifiInfo?.frequency ?: 0 else 0
        @Suppress("DEPRECATION")
        val rssi = wifiInfo?.rssi ?: 0
        @Suppress("DEPRECATION")
        val netId = wifiInfo?.networkId ?: -1

        return WifiSessionInfo(
            ssid = ssid,
            bssid = bssid,
            ipAddress = ip ?: "—",
            gateway = gateway.ifBlank { "—" },
            dns = dns,
            netmask = guessNetmask(links),
            linkSpeedMbps = speed,
            frequencyMhz = freq,
            rssi = rssi,
            networkId = netId,
            isVpnActive = isVpn,
            interfaceName = links?.interfaceName
        )
    }

    private fun guessNetmask(links: LinkProperties?): String {
        val prefix = links?.linkAddresses
            ?.firstOrNull { it.address is Inet4Address }
            ?.prefixLength
            ?: return "—"
        return prefixToMask(prefix)
    }

    private fun prefixToMask(prefix: Int): String {
        val mask = if (prefix <= 0) 0 else (-1 shl (32 - prefix))
        return "${(mask ushr 24) and 0xFF}." +
            "${(mask ushr 16) and 0xFF}." +
            "${(mask ushr 8) and 0xFF}." +
            "${mask and 0xFF}"
    }

    @Suppress("DEPRECATION")
    private fun legacyIp(info: android.net.wifi.WifiInfo?): String? {
        val ip = info?.ipAddress ?: return null
        if (ip == 0) return null
        return "${ip and 0xFF}.${ip shr 8 and 0xFF}.${ip shr 16 and 0xFF}.${ip shr 24 and 0xFF}"
    }
}
