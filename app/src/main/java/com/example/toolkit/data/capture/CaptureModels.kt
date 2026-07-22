package com.example.toolkit.data.capture

data class CapturedPacket(
    val id: Long,
    val timestamp: Long,
    val version: Int,
    val protocol: String,
    val protocolNumber: Int,
    val sourceIp: String,
    val destIp: String,
    val sourcePort: Int?,
    val destPort: Int?,
    val length: Int,
    val info: String,
    val direction: String,
    val payloadHex: String = "",
    val payloadAscii: String = "",
    val ttl: Int? = null,
    val flags: String = ""
)

data class TrafficFlow(
    val key: String,
    val protocol: String,
    val sourceIp: String,
    val destIp: String,
    val sourcePort: Int?,
    val destPort: Int?,
    val packetCount: Long,
    val bytes: Long,
    val lastSeen: Long,
    val lastInfo: String
)

data class WifiSessionInfo(
    val ssid: String,
    val bssid: String,
    val ipAddress: String,
    val gateway: String,
    val dns: List<String>,
    val netmask: String,
    val linkSpeedMbps: Int,
    val frequencyMhz: Int,
    val rssi: Int,
    val networkId: Int,
    val isVpnActive: Boolean,
    val interfaceName: String?
)

data class CaptureStats(
    val running: Boolean = false,
    val totalPackets: Long = 0,
    val tcpCount: Long = 0,
    val udpCount: Long = 0,
    val icmpCount: Long = 0,
    val dnsCount: Long = 0,
    val otherCount: Long = 0,
    val bytesCaptured: Long = 0
)

data class LanHost(
    val ip: String,
    val mac: String?,
    val vendor: String?,
    val hostname: String?,
    val deviceName: String?,
    val manufacturer: String? = null,
    val modelName: String? = null,
    val userName: String? = null,   // Android user / owner (this phone only)
    val nameSource: String? = null, // "mDNS" | "SSDP" | "NetBIOS" | "HTTP" | "DNS" | "System" | null
    val role: String,
    val reachable: Boolean,
    val openPorts: List<PortHint> = emptyList(),
    val isThisDevice: Boolean = false
) {
    /** Best real-world name we could establish for this device, in priority order. */
    val displayName: String
        get() = deviceName?.takeIf { it.isNotBlank() }
            ?: hostname?.takeIf { it.isNotBlank() && it != ip }
            ?: listOfNotNull(manufacturer, modelName).joinToString(" ").takeIf { it.isNotBlank() }
            ?: vendor?.takeIf { it.isNotBlank() && it != "Unknown vendor" }?.let { "$it device" }
            ?: "Unknown device"

    /** Brand + model line for the detail card, when we have either. */
    val brandModel: String?
        get() = listOfNotNull(manufacturer, modelName).joinToString(" ").takeIf { it.isNotBlank() }
            ?: vendor?.takeIf { it.isNotBlank() && it != "Unknown vendor" }
}

data class PortHint(
    val port: Int,
    val service: String
)
