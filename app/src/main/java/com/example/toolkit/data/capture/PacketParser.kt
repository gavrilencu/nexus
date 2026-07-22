package com.example.toolkit.data.capture

import java.nio.ByteBuffer
import java.nio.ByteOrder

object PacketParser {

    fun parse(raw: ByteArray, length: Int, ourVpnIp: String = "10.8.0.2"): CapturedPacket? {
        if (length < 20) return null
        val version = (raw[0].toInt() shr 4) and 0x0F
        if (version != 4) {
            if (version == 6 && length >= 40) return parseIpv6(raw, length)
            return null
        }
        return parseIpv4(raw, length, ourVpnIp)
    }

    private fun parseIpv4(raw: ByteArray, length: Int, ourVpnIp: String): CapturedPacket {
        val ihl = (raw[0].toInt() and 0x0F) * 4
        val totalLength = ((raw[2].toInt() and 0xFF) shl 8) or (raw[3].toInt() and 0xFF)
        val ttl = raw[8].toInt() and 0xFF
        val protocol = raw[9].toInt() and 0xFF
        val src = formatIpv4(raw, 12)
        val dst = formatIpv4(raw, 16)
        val direction = when {
            src == ourVpnIp || src.startsWith("10.8.0.") -> "OUT"
            dst == ourVpnIp || dst.startsWith("10.8.0.") -> "IN"
            else -> "—"
        }

        var sport: Int? = null
        var dport: Int? = null
        var info = ""
        var flagStr = ""
        var protoName = when (protocol) {
            1 -> "ICMP"
            6 -> "TCP"
            17 -> "UDP"
            else -> "IP/$protocol"
        }

        if ((protocol == 6 || protocol == 17) && length >= ihl + 4) {
            sport = ((raw[ihl].toInt() and 0xFF) shl 8) or (raw[ihl + 1].toInt() and 0xFF)
            dport = ((raw[ihl + 2].toInt() and 0xFF) shl 8) or (raw[ihl + 3].toInt() and 0xFF)
        }

        when (protocol) {
            6 -> {
                if (length >= ihl + 14) {
                    val flags = raw[ihl + 13].toInt() and 0xFF
                    flagStr = buildString {
                        if (flags and 0x02 != 0) append("SYN ")
                        if (flags and 0x10 != 0) append("ACK ")
                        if (flags and 0x01 != 0) append("FIN ")
                        if (flags and 0x04 != 0) append("RST ")
                        if (flags and 0x08 != 0) append("PSH ")
                    }.trim()
                    info = "TCP $sport → $dport"
                    when {
                        dport == 443 || sport == 443 -> info += " · HTTPS"
                        dport == 80 || sport == 80 -> info += " · HTTP"
                        dport == 53 || sport == 53 -> info += " · DNS"
                        dport == 22 || sport == 22 -> info += " · SSH"
                    }
                    if (flagStr.isNotBlank()) info += " · $flagStr"
                } else info = "TCP $sport → $dport"
            }
            17 -> {
                info = "UDP $sport → $dport"
                if (dport == 53 || sport == 53) {
                    protoName = "DNS"
                    info = parseDnsInfo(raw, ihl + 8, length) ?: "DNS $sport → $dport"
                } else if (dport == 443 || sport == 443) {
                    info += " · QUIC"
                } else if (dport == 5353 || sport == 5353) {
                    info += " · mDNS"
                } else if (dport == 1900 || sport == 1900) {
                    info += " · SSDP/UPnP"
                }
            }
            1 -> {
                if (length >= ihl + 2) {
                    val type = raw[ihl].toInt() and 0xFF
                    info = when (type) {
                        0 -> "ICMP Echo Reply"
                        8 -> "ICMP Echo Request"
                        3 -> "ICMP Dest Unreachable"
                        else -> "ICMP type=$type"
                    }
                }
            }
        }

        val payloadStart = when (protocol) {
            6 -> {
                if (length >= ihl + 13) {
                    val dataOffset = ((raw[ihl + 12].toInt() shr 4) and 0x0F) * 4
                    ihl + dataOffset
                } else ihl + 20
            }
            17 -> ihl + 8
            1 -> ihl + 8
            else -> ihl
        }
        // Captures far more than a short preview (was 96 B) so credential/email
        // scanning in CredentialSniffer actually has the HTTP body to look at,
        // not just headers. 1400 B covers almost a full MTU-sized packet.
        val payloadEnd = minOf(length, payloadStart + 1400)
        val payload = if (payloadStart in 0 until length && payloadStart < payloadEnd) {
            raw.copyOfRange(payloadStart, payloadEnd)
        } else ByteArray(0)

        return CapturedPacket(
            id = 0,
            timestamp = System.currentTimeMillis(),
            version = 4,
            protocol = protoName,
            protocolNumber = protocol,
            sourceIp = src,
            destIp = dst,
            sourcePort = sport,
            destPort = dport,
            length = if (totalLength in 1..length) totalLength else length,
            info = info,
            direction = direction,
            payloadHex = payload.joinToString(" ") { "%02x".format(it) },
            payloadAscii = payload.toAsciiPreview(),
            ttl = ttl,
            flags = flagStr
        )
    }

    private fun parseIpv6(raw: ByteArray, length: Int): CapturedPacket {
        val nextHeader = raw[6].toInt() and 0xFF
        val src = formatIpv6(raw, 8)
        val dst = formatIpv6(raw, 24)
        val proto = when (nextHeader) {
            6 -> "TCP"
            17 -> "UDP"
            58 -> "ICMPv6"
            else -> "IPv6/$nextHeader"
        }
        return CapturedPacket(
            id = 0,
            timestamp = System.currentTimeMillis(),
            version = 6,
            protocol = proto,
            protocolNumber = nextHeader,
            sourceIp = src,
            destIp = dst,
            sourcePort = null,
            destPort = null,
            length = length,
            info = "IPv6 packet",
            direction = "—"
        )
    }

    private fun parseDnsInfo(raw: ByteArray, offset: Int, length: Int): String? {
        if (offset + 12 > length) return null
        return try {
            val flags = ((raw[offset + 2].toInt() and 0xFF) shl 8) or (raw[offset + 3].toInt() and 0xFF)
            val isResponse = flags and 0x8000 != 0
            var pos = offset + 12
            val labels = mutableListOf<String>()
            while (pos < length) {
                val labLen = raw[pos].toInt() and 0xFF
                if (labLen == 0) break
                if (labLen and 0xC0 == 0xC0) break
                if (pos + 1 + labLen > length) break
                labels += String(raw, pos + 1, labLen)
                pos += 1 + labLen
            }
            val name = labels.joinToString(".")
            if (name.isBlank()) null
            else if (isResponse) "DNS response · $name" else "DNS query · $name"
        } catch (_: Exception) {
            null
        }
    }

    private fun formatIpv4(buf: ByteArray, offset: Int): String {
        return "${buf[offset].toInt() and 0xFF}." +
            "${buf[offset + 1].toInt() and 0xFF}." +
            "${buf[offset + 2].toInt() and 0xFF}." +
            "${buf[offset + 3].toInt() and 0xFF}"
    }

    private fun formatIpv6(buf: ByteArray, offset: Int): String {
        val bb = ByteBuffer.wrap(buf, offset, 16).order(ByteOrder.BIG_ENDIAN)
        val parts = (0 until 8).map { "%x".format(bb.short.toInt() and 0xFFFF) }
        return parts.joinToString(":")
    }

    private fun ByteArray.toAsciiPreview(): String =
        joinToString("") { b ->
            val c = b.toInt() and 0xFF
            if (c in 32..126) c.toChar().toString() else "."
        }
}
