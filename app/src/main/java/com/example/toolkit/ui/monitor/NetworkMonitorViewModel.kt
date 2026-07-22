package com.example.toolkit.ui.monitor

import android.app.Application
import android.content.Intent
import android.net.VpnService
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.toolkit.data.capture.CapturedPacket
import com.example.toolkit.data.capture.CaptureStats
import com.example.toolkit.data.capture.CredentialSniffer
import com.example.toolkit.data.capture.IpInfo
import com.example.toolkit.data.capture.IpInfoEngine
import com.example.toolkit.data.capture.LanDiscovery
import com.example.toolkit.data.capture.LanHost
import com.example.toolkit.data.capture.NexusCaptureService
import com.example.toolkit.data.capture.PacketCaptureBus
import com.example.toolkit.data.capture.SimInfo
import com.example.toolkit.data.capture.SimInfoProvider
import com.example.toolkit.data.capture.TrafficFlow
import com.example.toolkit.data.capture.WifiInfoProvider
import com.example.toolkit.data.capture.WifiSessionInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NetworkMonitorUiState(
    val wifi: WifiSessionInfo? = null,
    val sim: SimInfo = SimInfo.NONE,
    val tab: String = "DEVICES", // DEVICES | TRAFFIC | FLOWS
    val filter: String = "ALL", // ALL | TCP | UDP | DNS | ICMP | CREDS
    val query: String = "",
    val selectedEmail: String? = null,
    val lanHosts: List<LanHost> = emptyList(),
    val lanScanning: Boolean = false,
    val lanError: String? = null,
    val expandedPacketId: Long? = null,
    val statusMessage: String = "Idle",
    val ipLookups: Map<String, IpLookup> = emptyMap()
)

data class IpLookup(
    val ip: String,
    val loading: Boolean = false,
    val info: IpInfo? = null,
    val error: String? = null
)

class NetworkMonitorViewModel(app: Application) : AndroidViewModel(app) {
    private val lan = LanDiscovery(app)
    private val _ui = MutableStateFlow(NetworkMonitorUiState())
    val ui: StateFlow<NetworkMonitorUiState> = _ui.asStateFlow()

    val packets: StateFlow<List<CapturedPacket>> = PacketCaptureBus.recent
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val flows: StateFlow<List<TrafficFlow>> = PacketCaptureBus.flows
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val stats: StateFlow<CaptureStats> = PacketCaptureBus.stats
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CaptureStats())

    val running: StateFlow<Boolean> = PacketCaptureBus.running
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private var lanJob: Job? = null

    init {
        refreshWifi()
        scanLan()
    }

    fun refreshWifi() {
        _ui.update {
            it.copy(
                wifi = WifiInfoProvider.current(getApplication()),
                sim = SimInfoProvider.current(getApplication())
            )
        }
    }

    fun onTab(v: String) = _ui.update { it.copy(tab = v) }
    fun onFilter(v: String) = _ui.update { it.copy(filter = v, selectedEmail = if (v != "CREDS") null else it.selectedEmail) }
    fun onQuery(v: String) = _ui.update { it.copy(query = v) }

    /** Selecting an email switches into the CREDS filter and narrows the
     *  packet list to only packets that mention that exact address. */
    fun selectEmail(email: String?) {
        _ui.update {
            it.copy(
                selectedEmail = email,
                filter = if (email != null) "CREDS" else it.filter
            )
        }
    }

    fun detectedEmails(all: List<CapturedPacket>): List<String> =
        all.asSequence()
            .flatMap { CredentialSniffer.find(it.payloadAscii).emails.asSequence() }
            .distinct()
            .sorted()
            .toList()

    fun togglePacket(id: Long) {
        _ui.update {
            it.copy(expandedPacketId = if (it.expandedPacketId == id) null else id)
        }
    }

    fun filteredPackets(all: List<CapturedPacket>): List<CapturedPacket> {
        val f = _ui.value.filter
        val q = _ui.value.query.trim()
        val email = _ui.value.selectedEmail
        return all.filter { p ->
            val protoOk = when (f) {
                "ALL" -> true
                "TCP" -> p.protocol.equals("TCP", true)
                "UDP" -> p.protocol.equals("UDP", true) || p.protocol.equals("DNS", true)
                "DNS" -> p.protocol.equals("DNS", true) || p.info.contains("DNS", true)
                "ICMP" -> p.protocol.contains("ICMP", true)
                "CREDS" -> CredentialSniffer.hasFinding(p.payloadAscii)
                else -> true
            }
            val emailOk = email == null ||
                CredentialSniffer.find(p.payloadAscii).emails.any { it.equals(email, ignoreCase = true) }
            val queryOk = q.isBlank() ||
                p.sourceIp.contains(q) ||
                p.destIp.contains(q) ||
                p.info.contains(q, true) ||
                p.protocol.contains(q, true) ||
                p.payloadAscii.contains(q, true)
            protoOk && emailOk && queryOk
        }
    }

    fun filteredHosts(all: List<LanHost>): List<LanHost> {
        val q = _ui.value.query.trim()
        if (q.isBlank()) return all
        return all.filter {
            it.ip.contains(q) ||
                it.displayName.contains(q, true) ||
                (it.hostname?.contains(q, true) == true) ||
                (it.mac?.contains(q, true) == true) ||
                (it.vendor?.contains(q, true) == true) ||
                (it.manufacturer?.contains(q, true) == true) ||
                (it.modelName?.contains(q, true) == true) ||
                it.role.contains(q, true)
        }
    }

    fun prepareVpn(): Intent? = VpnService.prepare(getApplication())

    fun startCapture() {
        if (prepareVpn() != null) return
        _ui.update { it.copy(statusMessage = "Live traffic on") }
        val ctx = getApplication<Application>()
        val intent = Intent(ctx, NexusCaptureService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.startForegroundService(intent)
        } else {
            ctx.startService(intent)
        }
        refreshWifi()
    }

    fun onVpnPermissionGranted() = startCapture()

    fun stopCapture() {
        val ctx = getApplication<Application>()
        ctx.startService(
            Intent(ctx, NexusCaptureService::class.java).setAction(NexusCaptureService.ACTION_STOP)
        )
        _ui.update { it.copy(statusMessage = "Stopped") }
        refreshWifi()
    }

    fun clearPackets() = PacketCaptureBus.clear()

    /** The remote (non-phone, public) endpoint of a flow — the one worth identifying. */
    fun remoteIpOf(sourceIp: String, destIp: String): String {
        val phoneIp = _ui.value.wifi?.ipAddress
        return when {
            destIp == phoneIp -> sourceIp
            sourceIp == phoneIp -> destIp
            IpInfoEngine.isPrivate(sourceIp) && !IpInfoEngine.isPrivate(destIp) -> destIp
            !IpInfoEngine.isPrivate(sourceIp) && IpInfoEngine.isPrivate(destIp) -> sourceIp
            else -> destIp
        }
    }

    fun lookupIp(ip: String) {
        val existing = _ui.value.ipLookups[ip]
        if (existing?.loading == true) return
        _ui.update { s ->
            s.copy(ipLookups = s.ipLookups + (ip to IpLookup(ip = ip, loading = true)))
        }
        viewModelScope.launch {
            val result = try {
                val info = IpInfoEngine.lookup(ip)
                IpLookup(ip = ip, loading = false, info = info, error = info.error)
            } catch (e: Exception) {
                IpLookup(ip = ip, loading = false, error = e.message ?: "Eroare lookup")
            }
            _ui.update { s -> s.copy(ipLookups = s.ipLookups + (ip to result)) }
        }
    }

    fun clearIpLookup(ip: String) {
        _ui.update { s -> s.copy(ipLookups = s.ipLookups - ip) }
    }

    fun scanLan() {
        refreshWifi()
        val wifi = _ui.value.wifi
        val ip = wifi?.ipAddress
        if (ip.isNullOrBlank() || ip == "—") {
            _ui.update { it.copy(lanError = "Conectează-te la Wi‑Fi mai întâi") }
            return
        }
        lanJob?.cancel()
        lanJob = viewModelScope.launch {
            _ui.update { it.copy(lanScanning = true, lanError = null) }
            try {
                val hosts = lan.scanSubnet(
                    localIp = ip,
                    gateway = wifi.gateway.takeIf { it != "—" },
                    dnsServers = wifi.dns
                )
                _ui.update {
                    it.copy(
                        lanScanning = false,
                        lanHosts = hosts,
                        statusMessage = "${hosts.size} dispozitive pe rețea"
                    )
                }
            } catch (e: Exception) {
                _ui.update { it.copy(lanScanning = false, lanError = e.message) }
            }
        }
    }
}
