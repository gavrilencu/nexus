package com.example.toolkit.data.capture

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SsdpResult(
    val server: String? = null,
    val friendlyName: String? = null,
    val manufacturer: String? = null,
    val modelName: String? = null
) {
    val label: String?
        get() = friendlyName
            ?: listOfNotNull(manufacturer, modelName).joinToString(" ").takeIf { it.isNotBlank() }
            ?: server
}

/**
 * SSDP/UPnP discovery (the protocol behind "devices" showing up in a smart
 * TV's network browser). Sends a multicast M-SEARCH, then fetches each
 * responder's device-description XML for its *actual* manufacturer/model —
 * this is where "brand + model" for routers, TVs, printers, and NAS boxes
 * comes from; most of those don't bother with mDNS.
 */
class SsdpDiscovery {

    suspend fun discover(timeoutMs: Int = 2500): Map<String, SsdpResult> = withContext(Dispatchers.IO) {
        val raw = mutableMapOf<String, Pair<String?, String?>>() // ip -> (server, location)
        try {
            DatagramSocket().use { socket ->
                socket.soTimeout = timeoutMs
                socket.reuseAddress = true
                val group = InetAddress.getByName("239.255.255.250")
                val msg = "M-SEARCH * HTTP/1.1\r\n" +
                    "HOST: 239.255.255.250:1900\r\n" +
                    "MAN: \"ssdp:discover\"\r\n" +
                    "MX: 2\r\n" +
                    "ST: ssdp:all\r\n\r\n"
                val bytes = msg.toByteArray(Charsets.US_ASCII)
                socket.send(DatagramPacket(bytes, bytes.size, group, 1900))

                val deadline = System.currentTimeMillis() + timeoutMs
                val buf = ByteArray(4096)
                while (System.currentTimeMillis() < deadline) {
                    try {
                        val resp = DatagramPacket(buf, buf.size)
                        socket.receive(resp)
                        val text = String(resp.data, 0, resp.length, Charsets.UTF_8)
                        val ip = resp.address?.hostAddress ?: continue
                        val server = header(text, "SERVER")
                        val location = header(text, "LOCATION")
                        if (server != null || location != null) {
                            raw[ip] = server to location
                        }
                    } catch (_: Exception) {
                        break // socket timeout — normal end of the collection window
                    }
                }
            }
        } catch (_: Exception) {
            // no multicast route / permission issue — degrade gracefully
        }

        raw.mapValues { (_, pair) ->
            val (server, location) = pair
            val description = location?.let { fetchDescription(it) }
            SsdpResult(
                server = server,
                friendlyName = description?.first,
                manufacturer = description?.second,
                modelName = description?.third
            )
        }
    }

    private fun header(response: String, name: String): String? =
        Regex("(?im)^$name:\\s*(.+)$").find(response)?.groupValues?.get(1)?.trim()?.trimEnd('\r')

    /** Best-effort fetch of a UPnP device description document. Returns
     *  (friendlyName, manufacturer, modelName). */
    private fun fetchDescription(url: String): Triple<String?, String?, String?>? {
        return try {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 900
                readTimeout = 900
                requestMethod = "GET"
            }
            val text = connection.inputStream.bufferedReader().use { it.readText() }.take(6000)
            connection.disconnect()
            Triple(
                xmlTag(text, "friendlyName"),
                xmlTag(text, "manufacturer"),
                xmlTag(text, "modelName")
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun xmlTag(xml: String, tag: String): String? =
        Regex("<$tag>(.*?)</$tag>", RegexOption.IGNORE_CASE)
            .find(xml)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
}
