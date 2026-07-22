package com.example.toolkit.data.capture

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

object PacketCaptureBus {
    private val idGen = AtomicLong(0)
    private val _packets = MutableSharedFlow<CapturedPacket>(
        replay = 0,
        extraBufferCapacity = 512
    )
    private val _recent = MutableStateFlow<List<CapturedPacket>>(emptyList())
    private val _flows = MutableStateFlow<List<TrafficFlow>>(emptyList())
    private val flowMap = ConcurrentHashMap<String, TrafficFlow>()
    private val _stats = MutableStateFlow(CaptureStats())
    private val _running = MutableStateFlow(false)

    val packets: SharedFlow<CapturedPacket> = _packets.asSharedFlow()
    val recent: StateFlow<List<CapturedPacket>> = _recent.asStateFlow()
    val flows: StateFlow<List<TrafficFlow>> = _flows.asStateFlow()
    val stats: StateFlow<CaptureStats> = _stats.asStateFlow()
    val running: StateFlow<Boolean> = _running.asStateFlow()

    fun setRunning(value: Boolean) {
        _running.value = value
        if (value) {
            clear()
            _stats.value = CaptureStats(running = true)
        } else {
            _stats.update { it.copy(running = false) }
        }
    }

    fun clear() {
        _recent.value = emptyList()
        flowMap.clear()
        _flows.value = emptyList()
        _stats.update { CaptureStats(running = it.running) }
    }

    fun emit(packet: CapturedPacket) {
        val withId = if (packet.id == 0L) packet.copy(id = idGen.incrementAndGet()) else packet
        _packets.tryEmit(withId)
        _recent.update { list -> (listOf(withId) + list).take(500) }

        val key = flowKey(withId)
        flowMap.compute(key) { _, old ->
            if (old == null) {
                TrafficFlow(
                    key = key,
                    protocol = withId.protocol,
                    sourceIp = withId.sourceIp,
                    destIp = withId.destIp,
                    sourcePort = withId.sourcePort,
                    destPort = withId.destPort,
                    packetCount = 1,
                    bytes = withId.length.toLong(),
                    lastSeen = withId.timestamp,
                    lastInfo = withId.info
                )
            } else {
                old.copy(
                    packetCount = old.packetCount + 1,
                    bytes = old.bytes + withId.length,
                    lastSeen = withId.timestamp,
                    lastInfo = withId.info,
                    protocol = withId.protocol
                )
            }
        }
        _flows.value = flowMap.values.sortedByDescending { it.lastSeen }.take(80)

        _stats.update { s ->
            val proto = withId.protocol.uppercase()
            s.copy(
                totalPackets = s.totalPackets + 1,
                bytesCaptured = s.bytesCaptured + withId.length,
                tcpCount = s.tcpCount + if (proto == "TCP") 1 else 0,
                udpCount = s.udpCount + if (proto == "UDP") 1 else 0,
                icmpCount = s.icmpCount + if (proto.contains("ICMP")) 1 else 0,
                dnsCount = s.dnsCount + if (proto == "DNS" || withId.info.contains("DNS", true)) 1 else 0,
                otherCount = s.otherCount + if (proto !in setOf("TCP", "UDP", "DNS", "ICMP") && !proto.contains("ICMP")) 1 else 0
            )
        }
    }

    private fun flowKey(p: CapturedPacket): String {
        val a = "${p.sourceIp}:${p.sourcePort ?: 0}"
        val b = "${p.destIp}:${p.destPort ?: 0}"
        val ordered = if (a <= b) "$a|$b" else "$b|$a"
        return "${p.protocol}|$ordered"
    }
}
