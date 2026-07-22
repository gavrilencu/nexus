package com.example.toolkit.data.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.example.toolkit.MainActivity
import com.example.toolkit.R
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.random.Random

/**
 * Device-local live traffic monitor via VpnService.
 * Shows packets in-app. Does not sniff other Wi‑Fi clients' traffic.
 */
class NexusCaptureService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var tunOut: FileOutputStream? = null
    private val running = AtomicBoolean(false)
    private val workers = mutableListOf<Thread>()

    private val udpSessions = ConcurrentHashMap<String, UdpSession>()
    private val tcpSessions = ConcurrentHashMap<String, TcpSession>()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopCapture()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> startCapture()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopCapture()
        super.onDestroy()
    }

    private fun startCapture() {
        if (running.getAndSet(true)) return
        startForeground(NOTIF_ID, buildNotification())
        PacketCaptureBus.setRunning(true)

        val builder = Builder()
            .setSession("NEXUS Live Traffic")
            .setMtu(1500)
            .addAddress(VPN_ADDRESS, 32)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("8.8.8.8")
            .addDnsServer("1.1.1.1")
            .setBlocking(true)

        try {
            builder.addDisallowedApplication(packageName)
        } catch (_: Exception) {
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }

        vpnInterface = builder.establish()
        if (vpnInterface == null) {
            PacketCaptureBus.setRunning(false)
            running.set(false)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        val iface = vpnInterface!!
        val input = FileInputStream(iface.fileDescriptor)
        tunOut = FileOutputStream(iface.fileDescriptor)

        val t = thread(name = "nexus-capture-loop", isDaemon = true) {
            val packet = ByteArray(32767)
            while (running.get()) {
                try {
                    val length = input.read(packet)
                    if (length <= 0) continue
                    record(packet, length)
                    handleIpv4(packet, length)
                } catch (_: Exception) {
                    if (!running.get()) break
                }
            }
        }
        workers += t
    }

    private fun record(packet: ByteArray, length: Int) {
        try {
            PacketParser.parse(packet, length, VPN_ADDRESS)?.let { PacketCaptureBus.emit(it) }
        } catch (_: Exception) {
        }
    }

    private fun handleIpv4(packet: ByteArray, length: Int) {
        if (length < 20) return
        if ((packet[0].toInt() shr 4) and 0x0F != 4) return
        val ihl = (packet[0].toInt() and 0x0F) * 4
        val protocol = packet[9].toInt() and 0xFF
        val srcIp = ipString(packet, 12)
        val dstIp = ipString(packet, 16)
        when (protocol) {
            17 -> handleUdp(packet, length, ihl, srcIp, dstIp)
            6 -> handleTcp(packet, length, ihl, srcIp, dstIp)
        }
    }

    // ─── UDP ───────────────────────────────────────────────────────────────

    private fun handleUdp(packet: ByteArray, length: Int, ihl: Int, srcIp: String, dstIp: String) {
        if (length < ihl + 8) return
        val srcPort = u16(packet, ihl)
        val dstPort = u16(packet, ihl + 2)
        val payload = packet.copyOfRange(ihl + 8, length)
        val key = "$srcIp:$srcPort->$dstIp:$dstPort"

        val session = udpSessions.getOrPut(key) {
            val sock = DatagramSocket()
            protect(sock)
            sock.soTimeout = 8_000
            val s = UdpSession(sock, srcIp, srcPort, dstIp, dstPort)
            thread(isDaemon = true, name = "udp-$key") {
                val buf = ByteArray(65535)
                while (running.get()) {
                    try {
                        val dp = DatagramPacket(buf, buf.size)
                        sock.receive(dp)
                        val reply = buildUdpPacket(
                            srcIp = dstIp,
                            dstIp = srcIp,
                            srcPort = dstPort,
                            dstPort = srcPort,
                            payload = buf.copyOf(dp.length)
                        )
                        writeTun(reply)
                    } catch (_: Exception) {
                        break
                    }
                }
                udpSessions.remove(key)
                try {
                    sock.close()
                } catch (_: Exception) {
                }
            }
            s
        }
        try {
            session.socket.send(
                DatagramPacket(payload, payload.size, InetAddress.getByName(dstIp), dstPort)
            )
        } catch (_: Exception) {
            udpSessions.remove(key)
            try {
                session.socket.close()
            } catch (_: Exception) {
            }
        }
    }

    // ─── TCP (userspace relay with SYN/ACK) ────────────────────────────────

    private fun handleTcp(packet: ByteArray, length: Int, ihl: Int, srcIp: String, dstIp: String) {
        if (length < ihl + 20) return
        val srcPort = u16(packet, ihl)
        val dstPort = u16(packet, ihl + 2)
        val seq = u32(packet, ihl + 4)
        val ack = u32(packet, ihl + 8)
        val dataOff = ((packet[ihl + 12].toInt() shr 4) and 0x0F) * 4
        val flags = packet[ihl + 13].toInt() and 0xFF
        val syn = flags and 0x02 != 0
        val ackFlag = flags and 0x10 != 0
        val fin = flags and 0x01 != 0
        val rst = flags and 0x04 != 0
        val payloadStart = ihl + dataOff
        val payload = if (payloadStart < length) packet.copyOfRange(payloadStart, length) else ByteArray(0)

        val key = "$srcIp:$srcPort->$dstIp:$dstPort"

        if (rst) {
            tcpSessions.remove(key)?.close()
            return
        }

        if (syn && !ackFlag) {
            // New connection from device
            tcpSessions.remove(key)?.close()
            try {
                val sock = Socket()
                protect(sock)
                sock.tcpNoDelay = true
                sock.connect(InetSocketAddress(dstIp, dstPort), 6_000)
                val ourSeq = Random.nextInt().toLong() and 0xFFFFFFFFL
                val session = TcpSession(
                    socket = sock,
                    clientIp = srcIp,
                    clientPort = srcPort,
                    serverIp = dstIp,
                    serverPort = dstPort,
                    clientNextSeq = (seq + 1) and 0xFFFFFFFFL,
                    serverSeq = (ourSeq + 1) and 0xFFFFFFFFL
                )
                tcpSessions[key] = session

                // SYN-ACK back to device
                writeTun(
                    buildTcpPacket(
                        srcIp = dstIp,
                        dstIp = srcIp,
                        srcPort = dstPort,
                        dstPort = srcPort,
                        seq = ourSeq,
                        ack = session.clientNextSeq,
                        flags = 0x12, // SYN+ACK
                        payload = ByteArray(0)
                    )
                )

                // Reader: remote -> TUN
                thread(isDaemon = true, name = "tcp-in-$key") {
                    val buf = ByteArray(16_384)
                    try {
                        while (running.get() && !sock.isClosed) {
                            val n = sock.getInputStream().read(buf)
                            if (n < 0) {
                                // FIN
                                writeTun(
                                    buildTcpPacket(
                                        srcIp = dstIp,
                                        dstIp = srcIp,
                                        srcPort = dstPort,
                                        dstPort = srcPort,
                                        seq = session.serverSeq,
                                        ack = session.clientNextSeq,
                                        flags = 0x11, // FIN+ACK
                                        payload = ByteArray(0)
                                    )
                                )
                                session.serverSeq = (session.serverSeq + 1) and 0xFFFFFFFFL
                                break
                            }
                            if (n == 0) continue
                            val chunk = buf.copyOf(n)
                            writeTun(
                                buildTcpPacket(
                                    srcIp = dstIp,
                                    dstIp = srcIp,
                                    srcPort = dstPort,
                                    dstPort = srcPort,
                                    seq = session.serverSeq,
                                    ack = session.clientNextSeq,
                                    flags = 0x18, // PSH+ACK
                                    payload = chunk
                                )
                            )
                            session.serverSeq = (session.serverSeq + n) and 0xFFFFFFFFL
                        }
                    } catch (_: Exception) {
                    } finally {
                        tcpSessions.remove(key)
                        session.close()
                    }
                }
            } catch (_: Exception) {
                // RST to client
                writeTun(
                    buildTcpPacket(
                        srcIp = dstIp,
                        dstIp = srcIp,
                        srcPort = dstPort,
                        dstPort = srcPort,
                        seq = 0,
                        ack = (seq + 1) and 0xFFFFFFFFL,
                        flags = 0x14, // RST+ACK
                        payload = ByteArray(0)
                    )
                )
            }
            return
        }

        val session = tcpSessions[key] ?: return

        if (payload.isNotEmpty()) {
            try {
                session.socket.getOutputStream().write(payload)
                session.socket.getOutputStream().flush()
                session.clientNextSeq = (seq + payload.size) and 0xFFFFFFFFL
                // ACK the data
                writeTun(
                    buildTcpPacket(
                        srcIp = dstIp,
                        dstIp = srcIp,
                        srcPort = dstPort,
                        dstPort = srcPort,
                        seq = session.serverSeq,
                        ack = session.clientNextSeq,
                        flags = 0x10, // ACK
                        payload = ByteArray(0)
                    )
                )
            } catch (_: Exception) {
                tcpSessions.remove(key)
                session.close()
            }
        } else if (ackFlag && !syn && !fin) {
            // pure ACK — update if needed
            if (ack != 0L) {
                // no-op
            }
        }

        if (fin) {
            session.clientNextSeq = (seq + 1) and 0xFFFFFFFFL
            writeTun(
                buildTcpPacket(
                    srcIp = dstIp,
                    dstIp = srcIp,
                    srcPort = dstPort,
                    dstPort = srcPort,
                    seq = session.serverSeq,
                    ack = session.clientNextSeq,
                    flags = 0x10,
                    payload = ByteArray(0)
                )
            )
            try {
                session.socket.shutdownOutput()
            } catch (_: Exception) {
            }
        }
    }

    // ─── packet builders ───────────────────────────────────────────────────

    private fun writeTun(packet: ByteArray) {
        val out = tunOut ?: return
        synchronized(out) {
            try {
                out.write(packet)
                record(packet, packet.size)
            } catch (_: Exception) {
            }
        }
    }

    private fun buildUdpPacket(
        srcIp: String,
        dstIp: String,
        srcPort: Int,
        dstPort: Int,
        payload: ByteArray
    ): ByteArray {
        val total = 20 + 8 + payload.size
        val buf = ByteArray(total)
        buf[0] = 0x45
        putU16(buf, 2, total)
        putU16(buf, 4, ipId.getAndIncrement() and 0xFFFF)
        putU16(buf, 6, 0x4000)
        buf[8] = 64
        buf[9] = 17
        putIp(buf, 12, srcIp)
        putIp(buf, 16, dstIp)
        putU16(buf, 20, srcPort)
        putU16(buf, 22, dstPort)
        putU16(buf, 24, 8 + payload.size)
        System.arraycopy(payload, 0, buf, 28, payload.size)
        putU16(buf, 10, checksum(buf, 0, 20))
        return buf
    }

    private fun buildTcpPacket(
        srcIp: String,
        dstIp: String,
        srcPort: Int,
        dstPort: Int,
        seq: Long,
        ack: Long,
        flags: Int,
        payload: ByteArray
    ): ByteArray {
        val tcpHdr = 20
        val total = 20 + tcpHdr + payload.size
        val buf = ByteArray(total)
        buf[0] = 0x45
        putU16(buf, 2, total)
        putU16(buf, 4, ipId.getAndIncrement() and 0xFFFF)
        putU16(buf, 6, 0x4000)
        buf[8] = 64
        buf[9] = 6
        putIp(buf, 12, srcIp)
        putIp(buf, 16, dstIp)
        putU16(buf, 10, checksum(buf, 0, 20))

        putU16(buf, 20, srcPort)
        putU16(buf, 22, dstPort)
        putU32(buf, 24, seq)
        putU32(buf, 28, ack)
        buf[32] = 0x50 // data offset 5 (20 bytes)
        buf[33] = flags.toByte()
        putU16(buf, 34, 65535) // window
        System.arraycopy(payload, 0, buf, 40, payload.size)

        // TCP checksum with pseudo-header
        val tcpLen = tcpHdr + payload.size
        val pseudo = ByteArray(12 + tcpLen)
        System.arraycopy(buf, 12, pseudo, 0, 8) // src+dst
        pseudo[9] = 6
        putU16(pseudo, 10, tcpLen)
        System.arraycopy(buf, 20, pseudo, 12, tcpLen)
        putU16(buf, 36, checksum(pseudo, 0, pseudo.size))
        return buf
    }

    private fun stopCapture() {
        running.set(false)
        PacketCaptureBus.setRunning(false)
        workers.forEach { it.interrupt() }
        workers.clear()
        udpSessions.values.forEach { try { it.socket.close() } catch (_: Exception) {} }
        udpSessions.clear()
        tcpSessions.values.forEach { it.close() }
        tcpSessions.clear()
        try {
            tunOut?.close()
        } catch (_: Exception) {
        }
        tunOut = null
        try {
            vpnInterface?.close()
        } catch (_: Exception) {
        }
        vpnInterface = null
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun buildNotification(): Notification {
        val channelId = "nexus_capture"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(channelId, "NEXUS Packet Capture", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, NexusCaptureService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("NEXUS live traffic")
            .setContentText("Monitoring this device — view in app")
            .setSmallIcon(R.drawable.ic_stat_nexus)
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .setOngoing(true)
            .build()
    }

    private data class UdpSession(
        val socket: DatagramSocket,
        val clientIp: String,
        val clientPort: Int,
        val serverIp: String,
        val serverPort: Int
    )

    private class TcpSession(
        val socket: Socket,
        val clientIp: String,
        val clientPort: Int,
        val serverIp: String,
        val serverPort: Int,
        @Volatile var clientNextSeq: Long,
        @Volatile var serverSeq: Long
    ) {
        fun close() {
            try {
                socket.close()
            } catch (_: Exception) {
            }
        }
    }

    companion object {
        const val ACTION_STOP = "com.example.toolkit.capture.STOP"
        const val VPN_ADDRESS = "10.8.0.2"
        private const val NOTIF_ID = 7101
        private val ipId = AtomicInteger(Random.nextInt(0, 65535))

        private fun ipString(b: ByteArray, o: Int) =
            "${b[o].toInt() and 0xFF}.${b[o + 1].toInt() and 0xFF}.${b[o + 2].toInt() and 0xFF}.${b[o + 3].toInt() and 0xFF}"

        private fun u16(b: ByteArray, o: Int) =
            ((b[o].toInt() and 0xFF) shl 8) or (b[o + 1].toInt() and 0xFF)

        private fun u32(b: ByteArray, o: Int): Long =
            ((b[o].toLong() and 0xFF) shl 24) or
                ((b[o + 1].toLong() and 0xFF) shl 16) or
                ((b[o + 2].toLong() and 0xFF) shl 8) or
                (b[o + 3].toLong() and 0xFF)

        private fun putU16(b: ByteArray, o: Int, v: Int) {
            b[o] = (v shr 8).toByte()
            b[o + 1] = v.toByte()
        }

        private fun putU32(b: ByteArray, o: Int, v: Long) {
            b[o] = (v shr 24).toByte()
            b[o + 1] = (v shr 16).toByte()
            b[o + 2] = (v shr 8).toByte()
            b[o + 3] = v.toByte()
        }

        private fun putIp(b: ByteArray, o: Int, ip: String) {
            val p = ip.split(".")
            for (i in 0..3) b[o + i] = p[i].toInt().toByte()
        }

        private fun checksum(buf: ByteArray, offset: Int, length: Int): Int {
            var sum = 0L
            var i = offset
            while (i < offset + length - 1) {
                sum += ((buf[i].toInt() and 0xFF) shl 8) or (buf[i + 1].toInt() and 0xFF)
                i += 2
            }
            if (length % 2 != 0) sum += (buf[offset + length - 1].toInt() and 0xFF) shl 8
            while (sum ushr 16 != 0L) sum = (sum and 0xFFFF) + (sum ushr 16)
            return sum.inv().toInt() and 0xFFFF
        }
    }
}
