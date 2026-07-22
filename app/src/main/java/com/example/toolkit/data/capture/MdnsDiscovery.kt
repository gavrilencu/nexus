package com.example.toolkit.data.capture

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class MdnsResult(
    val name: String,
    val serviceType: String
)

/**
 * Resolves real device names via mDNS (Bonjour/Avahi) using Android's
 * built-in [NsdManager] — this is how iPhones ("Andrei's iPhone"), Macs,
 * Chromecasts, smart speakers, and most printers actually announce
 * themselves on the LAN. It's the single biggest fix for "Unknown device":
 * ARP alone only gives a MAC address, never a name.
 */
class MdnsDiscovery(private val context: Context) {

    /** Common service types worth browsing — covers the vast majority of
     *  consumer devices without having to enumerate every possible type. */
    private val serviceTypes = listOf(
        "_device-info._tcp.", "_companion-link._tcp.", "_airplay._tcp.",
        "_raop._tcp.", "_googlecast._tcp.", "_http._tcp.", "_https._tcp.",
        "_ipp._tcp.", "_ipps._tcp.", "_printer._tcp.", "_pdl-datastream._tcp.",
        "_smb._tcp.", "_ssh._tcp.", "_sftp-ssh._tcp.", "_afpovertcp._tcp.",
        "_spotify-connect._tcp.", "_homekit._tcp.", "_hap._tcp.",
        "_workstation._tcp.", "_rdlink._tcp.", "_nvstream._tcp.",
        "_sonos._tcp.", "_amzn-wplay._tcp."
    )

    suspend fun discover(durationMs: Long = 3500): Map<String, MdnsResult> =
        withContext(Dispatchers.IO) {
            val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
                ?: return@withContext emptyMap()
            val wifiManager = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val lock = runCatching {
                wifiManager?.createMulticastLock("nexus-mdns")?.apply {
                    setReferenceCounted(true)
                    acquire()
                }
            }.getOrNull()

            val results = ConcurrentHashMap<String, MdnsResult>()
            val listeners = mutableListOf<NsdManager.DiscoveryListener>()

            try {
                serviceTypes.forEach { type ->
                    val listener = object : NsdManager.DiscoveryListener {
                        override fun onDiscoveryStarted(regType: String) = Unit
                        override fun onServiceFound(service: NsdServiceInfo) {
                            resolve(nsdManager, service, results)
                        }
                        override fun onServiceLost(service: NsdServiceInfo) = Unit
                        override fun onDiscoveryStopped(serviceType: String) = Unit
                        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
                        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
                    }
                    listeners += listener
                    runCatching {
                        nsdManager.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener)
                    }
                }
                delay(durationMs)
            } finally {
                listeners.forEach { runCatching { nsdManager.stopServiceDiscovery(it) } }
                runCatching { lock?.release() }
            }

            results
        }

    private fun resolve(
        nsdManager: NsdManager,
        service: NsdServiceInfo,
        results: ConcurrentHashMap<String, MdnsResult>
    ) {
        val listener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                val ip = serviceInfo.host?.hostAddress ?: return
                val name = serviceInfo.serviceName?.takeIf { it.isNotBlank() } ?: return
                // Prefer the first result for a given IP; later resolves for
                // other service types just fill in additional detail, they
                // shouldn't clobber an already-good name.
                results.putIfAbsent(ip, MdnsResult(name = name, serviceType = serviceInfo.serviceType.orEmpty()))
            }
        }
        // NsdManager historically only allows one outstanding resolve per
        // listener instance; failures here are non-fatal, just skip that entry.
        runCatching { nsdManager.resolveService(service, listener) }
    }
}
