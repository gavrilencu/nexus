package com.example.toolkit.data.capture

import android.content.Context
import java.io.BufferedReader
import java.io.FileReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.Charset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Discovers hosts on the local Wi‑Fi subnet and tries hard to attach a real
 * identity to each one — IP, MAC (ARP), OUI vendor, and (the part that used
 * to be missing) an actual device name/brand/model via mDNS and SSDP/UPnP,
 * the two protocols real devices use to announce themselves.
 */
class LanDiscovery(context: Context) {
    private val appContext = context.applicationContext
    private val mdns = MdnsDiscovery(appContext)
    private val ssdp = SsdpDiscovery()

    suspend fun scanSubnet(
        localIp: String,
        gateway: String? = null,
        dnsServers: List<String> = emptyList(),
        timeoutMs: Int = 350
    ): List<LanHost> = withContext(Dispatchers.IO) {
        val parts = localIp.split(".")
        if (parts.size != 4) return@withContext emptyList()
        val prefix = "${parts[0]}.${parts[1]}.${parts[2]}"

        // mDNS/SSDP take a couple of seconds of passive listening — run them
        // in the background for the whole scan instead of after it.
        val mdnsDeferred = async { runCatching { mdns.discover() }.getOrDefault(emptyMap()) }
        val ssdpDeferred = async { runCatching { ssdp.discover() }.getOrDefault(emptyMap()) }

        // Warm ARP: ping sweep lightly first
        val semPing = Semaphore(64)
        coroutineScope {
            (1..254).map { host ->
                async {
                    semPing.withPermit {
                        val ip = "$prefix.$host"
                        try {
                            InetAddress.getByName(ip).isReachable(timeoutMs)
                        } catch (_: Exception) {
                            false
                        }
                    }
                }
            }.awaitAll()
        }

        val arp = readArpTable()
        val sem = Semaphore(32)

        val hosts = coroutineScope {
            (1..254).map { host ->
                async {
                    sem.withPermit {
                        val ip = "$prefix.$host"
                        val isSelf = ip == localIp
                        val mac = arp[ip]
                        val open = if (isSelf) emptyList() else probePorts(
                            ip,
                            listOf(80, 443, 22, 445, 139, 8080, 8443, 53, 554, 8008, 8009, 5000, 32400),
                            220
                        )
                        val icmp = try {
                            InetAddress.getByName(ip).isReachable(timeoutMs)
                        } catch (_: Exception) {
                            false
                        }
                        val alive = isSelf || icmp || mac != null || open.isNotEmpty()
                        if (!alive) return@withPermit null

                        val dnsName = reverseDns(ip)
                        val nbName = if (!isSelf) netbiosName(ip) else null
                        val httpName = if (!isSelf && open.any { it.port == 80 || it.port == 8080 }) {
                            httpServerHint(ip, open.first { it.port == 80 || it.port == 8080 }.port)
                        } else null

                        val role = when {
                            isSelf -> "YOU"
                            gateway != null && ip == gateway -> "GATEWAY / ROUTER"
                            dnsServers.contains(ip) -> "DNS"
                            open.any { it.port == 445 || it.port == 139 } -> "PC / NAS"
                            open.any { it.port == 554 || it.port == 8008 || it.port == 8009 } -> "MEDIA / CAST"
                            open.any { it.port == 32400 } -> "PLEX"
                            open.any { it.port == 22 } -> "LINUX / SSH"
                            open.any { it.port == 80 || it.port == 443 } -> "WEB DEVICE"
                            else -> "HOST"
                        }

                        LanHost(
                            ip = ip,
                            mac = mac,
                            vendor = mac?.let { OuiLookup.vendor(it) },
                            hostname = dnsName,
                            deviceName = when {
                                isSelf -> "This phone (NEXUS)"
                                !nbName.isNullOrBlank() -> nbName
                                !httpName.isNullOrBlank() -> httpName
                                !dnsName.isNullOrBlank() && dnsName != ip -> dnsName.substringBefore('.')
                                else -> null
                            },
                            nameSource = when {
                                isSelf -> null
                                !nbName.isNullOrBlank() -> "NetBIOS"
                                !httpName.isNullOrBlank() -> "HTTP"
                                !dnsName.isNullOrBlank() && dnsName != ip -> "DNS"
                                else -> null
                            },
                            role = role,
                            reachable = true,
                            openPorts = open,
                            isThisDevice = isSelf
                        )
                    }
                }
            }.awaitAll().filterNotNull()
        }

        val mdnsResults = mdnsDeferred.await()
        val ssdpResults = ssdpDeferred.await()

        hosts.map { host ->
            if (host.isThisDevice) return@map host
            val mdnsHit = mdnsResults[host.ip]
            val ssdpHit = ssdpResults[host.ip]
            when {
                mdnsHit != null -> host.copy(
                    deviceName = mdnsHit.name,
                    nameSource = "mDNS",
                    role = mdnsServiceRoleHint(mdnsHit.serviceType) ?: host.role
                )
                ssdpHit?.label != null -> host.copy(
                    deviceName = ssdpHit.friendlyName ?: host.deviceName,
                    manufacturer = ssdpHit.manufacturer,
                    modelName = ssdpHit.modelName,
                    nameSource = "SSDP"
                )
                else -> host
            }
        }.sortedWith(
            compareByDescending<LanHost> { it.isThisDevice }
                .thenByDescending { it.role.contains("GATEWAY") }
                .thenBy { it.ip.substringAfterLast('.').toIntOrNull() ?: 999 }
        )
    }

    private fun mdnsServiceRoleHint(serviceType: String): String? = when {
        serviceType.contains("googlecast") || serviceType.contains("airplay") || serviceType.contains("raop") -> "MEDIA / CAST"
        serviceType.contains("ipp") || serviceType.contains("printer") || serviceType.contains("pdl-datastream") -> "PRINTER"
        serviceType.contains("homekit") || serviceType.contains("hap") -> "SMART HOME"
        serviceType.contains("workstation") || serviceType.contains("smb") || serviceType.contains("afpovertcp") -> "PC / NAS"
        serviceType.contains("sonos") || serviceType.contains("spotify") -> "SPEAKER"
        else -> null
    }

    fun readArpTable(): Map<String, String> {
        val map = LinkedHashMap<String, String>()
        try {
            BufferedReader(FileReader("/proc/net/arp")).use { br ->
                br.readLine() // header
                var line: String?
                while (br.readLine().also { line = it } != null) {
                    val parts = line!!.trim().split(Regex("\\s+"))
                    if (parts.size >= 4) {
                        val ip = parts[0]
                        val mac = parts[3].lowercase()
                        if (mac != "00:00:00:00:00:00" && mac.contains(":")) {
                            map[ip] = mac
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
        return map
    }

    private fun reverseDns(ip: String): String? = try {
        val name = InetAddress.getByName(ip).canonicalHostName
        if (name.isNullOrBlank() || name == ip) null else name
    } catch (_: Exception) {
        null
    }

    /** NetBIOS Node Status (NBSTAT) — often returns Windows / IoT device names. */
    private fun netbiosName(ip: String): String? {
        return try {
            DatagramSocket().use { sock ->
                sock.soTimeout = 600
                val query = ByteArray(50)
                // Transaction ID
                query[0] = 0x00
                query[1] = 0x10
                // Flags
                query[2] = 0x00
                query[3] = 0x00
                // Questions = 1
                query[4] = 0x00
                query[5] = 0x01
                // Name: * encoded
                query[12] = 0x20
                val star = "CKAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
                for (i in star.indices) query[13 + i] = star[i].code.toByte()
                query[45] = 0x00
                // Type NBSTAT = 0x0021, Class IN
                query[46] = 0x00
                query[47] = 0x21
                query[48] = 0x00
                query[49] = 0x01

                sock.send(DatagramPacket(query, query.size, InetAddress.getByName(ip), 137))
                val buf = ByteArray(1024)
                val resp = DatagramPacket(buf, buf.size)
                sock.receive(resp)

                // Parse first name from names block (rough)
                if (resp.length < 57) return null
                val nameCount = buf[56].toInt() and 0xFF
                if (nameCount <= 0) return null
                val nameBytes = buf.copyOfRange(57, minOf(resp.length, 57 + 15))
                val name = String(nameBytes, Charset.forName("US-ASCII"))
                    .trim { it <= ' ' || it == '\u0000' }
                    .trimEnd()
                name.takeIf { it.isNotBlank() && it.any { c -> c.isLetterOrDigit() } }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun httpServerHint(ip: String, port: Int): String? {
        return try {
            Socket().use { s ->
                s.connect(InetSocketAddress(ip, port), 500)
                s.soTimeout = 500
                s.getOutputStream().write("HEAD / HTTP/1.0\r\nHost: $ip\r\n\r\n".toByteArray())
                val text = s.getInputStream().bufferedReader().readText().take(800)
                Regex("(?i)Server:\\s*(.+)").find(text)?.groupValues?.getOrNull(1)?.trim()?.take(40)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun probePorts(ip: String, ports: List<Int>, timeout: Int): List<PortHint> {
        return ports.mapNotNull { port ->
            try {
                Socket().use { s ->
                    s.connect(InetSocketAddress(ip, port), timeout)
                    PortHint(port, serviceName(port))
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun serviceName(port: Int): String = when (port) {
        22 -> "SSH"
        53 -> "DNS"
        80 -> "HTTP"
        139 -> "NetBIOS"
        443 -> "HTTPS"
        445 -> "SMB"
        554 -> "RTSP"
        5000 -> "UPnP/AirPlay"
        8008, 8009 -> "Chromecast"
        8080 -> "HTTP-alt"
        8443 -> "HTTPS-alt"
        32400 -> "Plex"
        else -> "tcp/$port"
    }
}
