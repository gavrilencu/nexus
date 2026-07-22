package com.example.toolkit.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.toolkit.data.capture.CapturedPacket
import com.example.toolkit.data.capture.CredentialSniffer
import com.example.toolkit.data.capture.LanHost
import com.example.toolkit.data.capture.PayloadDecoder
import com.example.toolkit.data.capture.TrafficFlow
import com.example.toolkit.ui.components.KeyValueRow
import com.example.toolkit.ui.components.NexusButton
import com.example.toolkit.ui.components.NexusPanel
import com.example.toolkit.ui.components.NexusTextField
import com.example.toolkit.ui.components.ScreenHeader
import com.example.toolkit.ui.components.StatusChip
import com.example.toolkit.ui.components.UrlLink
import com.example.toolkit.ui.components.WarningBanner
import com.example.toolkit.ui.monitor.NetworkMonitorViewModel
import com.example.toolkit.ui.theme.AlertRed
import com.example.toolkit.ui.theme.GhostWhite
import com.example.toolkit.ui.theme.GlassBorder
import com.example.toolkit.ui.theme.MuteGreen
import com.example.toolkit.ui.theme.NeonGreen
import com.example.toolkit.ui.theme.PanelGreen
import com.example.toolkit.ui.theme.SoftGreen
import com.example.toolkit.ui.theme.TerminalGray
import com.example.toolkit.ui.theme.VoidBlack
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NetworkMonitorScreen(vm: NetworkMonitorViewModel = viewModel()) {
    val ui by vm.ui.collectAsState()
    val packets by vm.packets.collectAsState()
    val flows by vm.flows.collectAsState()
    val stats by vm.stats.collectAsState()
    val running by vm.running.collectAsState()
    val hosts = remember(ui.lanHosts, ui.query) { vm.filteredHosts(ui.lanHosts) }
    val filtered = remember(packets, ui.filter, ui.query, ui.selectedEmail) { vm.filteredPackets(packets) }
    // Only scan every packet for emails when the CREDS view is actually visible —
    // this is a regex pass over up to 500 payloads, no need to pay for it on
    // every packet arrival while the user is looking at DEVICES or FLOWS.
    val detectedEmails = if (ui.tab == "TRAFFIC" && ui.filter == "CREDS") {
        remember(packets) { vm.detectedEmails(packets) }
    } else {
        emptyList()
    }

    val vpnLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) vm.onVpnPermissionGranted()
    }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        vm.refreshWifi()
        vm.scanLan()
    }

    LaunchedEffect(Unit) {
        locationLauncher.launch(
            arrayOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VoidBlack)
            .padding(16.dp)
    ) {
        ScreenHeader(
            title = "Wi‑Fi/SIM Monitor",
            subtitle = "Dispozitive · SIM/celular · IP · nume · trafic live decodat"
        )
        Spacer(modifier = Modifier.height(8.dp))
        WarningBanner(
            text = "Dispozitivele de pe Wi‑Fi (IP / MAC / nume) sunt vizibile aici. Traficul detaliat e al acestui telefon — nu poți citi pachetele altor telefoane fără root."
        )
        Spacer(modifier = Modifier.height(10.dp))

        // Tabs
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("DEVICES" to "Dispozitive", "TRAFFIC" to "Trafic live", "FLOWS" to "Conexiuni").forEach { (id, label) ->
                FilterChip(
                    selected = ui.tab == id,
                    onClick = { vm.onTab(id) },
                    label = { Text(label) },
                    colors = chipColors()
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                NexusPanel(title = "REȚEA WI‑FI") {
                    val w = ui.wifi
                    if (w == null) Text("—", color = MuteGreen)
                    else {
                        KeyValueRow("SSID", w.ssid)
                        KeyValueRow("IP telefon", w.ipAddress)
                        KeyValueRow("Gateway", w.gateway)
                        KeyValueRow("DNS", w.dns.joinToString(", ").ifBlank { "—" })
                        KeyValueRow("BSSID (AP)", w.bssid)
                        KeyValueRow("Link", "${w.linkSpeedMbps} Mbps · RSSI ${w.rssi} dBm")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NexusButton("Refresh", onClick = {
                            vm.refreshWifi()
                        })
                        NexusButton(
                            text = if (ui.lanScanning) "Scanez…" else "Rescan dispozitive",
                            onClick = vm::scanLan,
                            enabled = !ui.lanScanning
                        )
                    }
                }
            }

            item {
                NexusPanel(title = "SIM / CELULAR") {
                    val s = ui.sim
                    if (!s.hasTelephony) {
                        Text("Fără modul de telefonie (SIM) pe acest dispozitiv.", color = MuteGreen)
                    } else {
                        KeyValueRow("SIM", s.simState)
                        KeyValueRow("Rețea celulară", s.networkType)
                        KeyValueRow("Date mobile", s.dataState)
                        if (s.cards.isEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("SIM neinserat sau blocat.", color = MuteGreen, fontSize = 12.sp)
                        }
                        s.cards.forEach { c ->
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("SIM ${c.slot + 1}", color = NeonGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            KeyValueRow("Operator", c.carrier)
                            KeyValueRow("Operator SIM", c.simOperator)
                            KeyValueRow("Țară", c.countryIso)
                            KeyValueRow("Număr", c.phoneNumber ?: "— (permisiune necesară)")
                            KeyValueRow("Roaming", if (c.roaming) "DA" else "Nu")
                        }
                    }
                }
            }

            when (ui.tab) {
                "DEVICES" -> {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatusChip(
                                if (ui.lanScanning) "SCAN…" else "${hosts.size} HOSTS"
                            )
                            ui.statusMessage.takeIf { it.isNotBlank() }?.let { StatusChip(it) }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        NexusTextField(ui.query, vm::onQuery, "caută IP / nume / MAC / vendor")
                        ui.lanError?.let {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(it, color = AlertRed, fontSize = 12.sp)
                        }
                    }
                    if (!ui.lanScanning && hosts.isEmpty()) {
                        item {
                            Text(
                                "Niciun host găsit. Apasă Rescan pe Wi‑Fi-ul tău.",
                                color = MuteGreen
                            )
                        }
                    }
                    items(hosts, key = { it.ip }) { host ->
                        DeviceCard(host)
                    }
                }

                "TRAFFIC" -> {
                    item {
                        NexusPanel(title = "TRAFIC ACEST TELEFON") {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatusChip(if (running) "LIVE" else "OPRIT")
                                StatusChip("${stats.totalPackets} pkts")
                                StatusChip(formatBytes(stats.bytesCaptured))
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "TCP ${stats.tcpCount} · UDP ${stats.udpCount} · DNS ${stats.dnsCount} · ICMP ${stats.icmpCount}",
                                color = TerminalGray,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (!running) {
                                    NexusButton(
                                        text = "Start monitor",
                                        onClick = {
                                            val prepare = vm.prepareVpn()
                                            if (prepare != null) vpnLauncher.launch(prepare)
                                            else vm.startCapture()
                                        }
                                    )
                                } else {
                                    NexusButton("Stop", onClick = vm::stopCapture)
                                }
                                NexusButton("Clear", onClick = vm::clearPackets)
                            }
                        }
                    }
                    item {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("ALL", "TCP", "UDP", "DNS", "ICMP", "CREDS").forEach { f ->
                                FilterChip(
                                    selected = ui.filter == f,
                                    onClick = { vm.onFilter(f) },
                                    label = { Text(if (f == "CREDS") "Credențiale" else f) },
                                    colors = if (f == "CREDS") credsChipColors() else chipColors()
                                )
                            }
                        }
                        if (ui.filter == "CREDS") {
                            Spacer(modifier = Modifier.height(8.dp))
                            WarningBanner(
                                "Detectăm parole/emailuri/username-uri trimise NECRIPTAT de acest telefon (HTTP, nu HTTPS) — util ca să găsești scurgeri în propriile tale aplicații. Nu poți vedea traficul altor telefoane din rețea."
                            )
                            if (detectedEmails.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Emailuri găsite — atinge ca să filtrezi:",
                                    color = MuteGreen,
                                    fontSize = 11.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    detectedEmails.forEach { email ->
                                        FilterChip(
                                            selected = ui.selectedEmail.equals(email, ignoreCase = true),
                                            onClick = {
                                                vm.selectEmail(if (ui.selectedEmail == email) null else email)
                                            },
                                            label = { Text(email) },
                                            colors = credsChipColors()
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        NexusTextField(ui.query, vm::onQuery, "filtrează IP / DNS / payload")
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Pachete (${filtered.size}) — apasă pentru detalii complete",
                            color = NeonGreen,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                    if (filtered.isEmpty()) {
                        item {
                            Text(
                                if (running) "Aștept pachete…" else "Start monitor ca să vezi traficul live",
                                color = MuteGreen
                            )
                        }
                    } else {
                        items(filtered, key = { it.id }) { pkt ->
                            PacketCard(
                                p = pkt,
                                expanded = ui.expandedPacketId == pkt.id,
                                onClick = { vm.togglePacket(pkt.id) }
                            )
                        }
                    }
                }

                "FLOWS" -> {
                    item {
                        Text(
                            "Conexiuni agregate (acest telefon)",
                            color = NeonGreen,
                            fontWeight = FontWeight.Bold
                        )
                        if (!running && flows.isEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Pornește Trafic live ca să umpli lista.", color = MuteGreen)
                        }
                    }
                    items(flows, key = { it.key }) { flow ->
                        val remoteIp = remember(flow.sourceIp, flow.destIp, ui.wifi?.ipAddress) {
                            vm.remoteIpOf(flow.sourceIp, flow.destIp)
                        }
                        FlowCard(
                            f = flow,
                            remoteIp = remoteIp,
                            lookup = ui.ipLookups[remoteIp],
                            onLookup = { vm.lookupIp(remoteIp) },
                            onClear = { vm.clearIpLookup(remoteIp) }
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(28.dp)) }
        }
    }
}

@Composable
private fun chipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = NeonGreen.copy(alpha = 0.2f),
    selectedLabelColor = NeonGreen,
    containerColor = PanelGreen,
    labelColor = MuteGreen
)

@Composable
private fun credsChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = AlertRed.copy(alpha = 0.18f),
    selectedLabelColor = AlertRed,
    containerColor = PanelGreen,
    labelColor = MuteGreen
)

@Composable
private fun DeviceCard(h: LanHost) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PanelGreen.copy(alpha = 0.82f), RoundedCornerShape(16.dp))
            .border(1.dp, if (h.isThisDevice) NeonGreen else GlassBorder.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    h.displayName,
                    color = GhostWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                h.brandModel?.let {
                    Text(it, color = MuteGreen, fontSize = 12.sp)
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            StatusChip(h.role)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            h.nameSource?.let { StatusChip("via $it", color = SoftGreen) }
            if (h.isThisDevice) StatusChip("YOU", color = NeonGreen)
        }
        if (h.nameSource != null || h.isThisDevice) Spacer(modifier = Modifier.height(6.dp))
        KeyValueRow("IP", h.ip)
        KeyValueRow("MAC", h.mac ?: "— (not in ARP yet)")
        KeyValueRow("Vendor", h.vendor ?: "—")
        KeyValueRow("Hostname", h.hostname ?: "—")
        h.manufacturer?.let { KeyValueRow("Marcă", it) }
        h.modelName?.let { KeyValueRow("Model", it) }
        h.userName?.let { KeyValueRow("Utilizator", it) }
        if (h.openPorts.isNotEmpty()) {
            KeyValueRow(
                "Open services",
                h.openPorts.joinToString(", ") { "${it.port}/${it.service}" }
            )
        }
    }
}

@Composable
private fun PacketCard(p: CapturedPacket, expanded: Boolean, onClick: () -> Unit) {
    val time = remember(p.timestamp) {
        SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(p.timestamp))
    }
    val finding = remember(p.payloadAscii) { CredentialSniffer.find(p.payloadAscii) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PanelGreen.copy(alpha = 0.82f), RoundedCornerShape(14.dp))
            .border(
                1.dp,
                if (!finding.isEmpty) AlertRed.copy(alpha = 0.45f) else GlassBorder.copy(alpha = 0.10f),
                RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(time, color = MuteGreen, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            Text(p.direction, color = NeonGreen, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            Text(p.protocol, color = GhostWhite, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text("${p.length} B", color = TerminalGray, fontSize = 11.sp)
            if (!finding.isEmpty) {
                StatusChip("⚠ Credențial", color = AlertRed)
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "${p.sourceIp}${p.sourcePort?.let { ":$it" } ?: ""}  →  ${p.destIp}${p.destPort?.let { ":$it" } ?: ""}",
            color = GhostWhite,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
        if (p.info.isNotBlank()) {
            Text(p.info, color = TerminalGray, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }
        if (!finding.isEmpty) {
            Spacer(modifier = Modifier.height(4.dp))
            if (finding.emails.isNotEmpty()) {
                Text("Email: ${finding.emails.joinToString(", ")}", color = AlertRed, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            }
            finding.credentials.forEach { c ->
                Text(
                    "${c.label}: ${c.value}",
                    color = AlertRed,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        if (expanded) {
            Spacer(modifier = Modifier.height(8.dp))
            Text("DETALII COMPLETE", color = NeonGreen, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            KeyValueRow("IPv", "v${p.version}")
            KeyValueRow("TTL", p.ttl?.toString() ?: "—")
            KeyValueRow("Flags", p.flags.ifBlank { "—" })
            KeyValueRow("Proto #", p.protocolNumber.toString())

            val decoded = remember(p.payloadAscii) { PayloadDecoder.decodeAll(p.payloadAscii) }
            if (finding.credentials.isNotEmpty() || decoded.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text("DECODAT (text simplu)", color = AlertRed, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                finding.credentials.forEach { c ->
                    KeyValueRow(c.label, c.value)
                }
                decoded.forEach { d ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(d.label, color = MuteGreen, fontSize = 10.sp)
                    Text(d.value, color = GhostWhite, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
            }

            if (p.payloadAscii.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text("ASCII (brut)", color = MuteGreen, fontSize = 10.sp)
                Text(p.payloadAscii, color = GhostWhite, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                val hexDec = remember(p.payloadAscii) { PayloadDecoder.urlDecode(p.payloadAscii) }
                if (hexDec != p.payloadAscii) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("ASCII (URL-decodat)", color = MuteGreen, fontSize = 10.sp)
                    Text(hexDec, color = GhostWhite, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
            }
            if (p.payloadHex.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text("HEX", color = MuteGreen, fontSize = 10.sp)
                Text(p.payloadHex, color = TerminalGray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                val fromHex = remember(p.payloadHex) { PayloadDecoder.hexToAscii(p.payloadHex) }
                if (fromHex != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("HEX → text", color = MuteGreen, fontSize = 10.sp)
                    Text(fromHex, color = GhostWhite, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
private fun FlowCard(
    f: TrafficFlow,
    remoteIp: String,
    lookup: com.example.toolkit.ui.monitor.IpLookup?,
    onLookup: () -> Unit,
    onClear: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PanelGreen.copy(alpha = 0.82f), RoundedCornerShape(14.dp))
            .border(1.dp, GlassBorder.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
            .padding(10.dp)
    ) {
        Text(f.protocol, color = NeonGreen, fontWeight = FontWeight.Bold)
        Text(
            "${f.sourceIp}${f.sourcePort?.let { ":$it" } ?: ""}  ↔  ${f.destIp}${f.destPort?.let { ":$it" } ?: ""}",
            color = GhostWhite,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
        Text(
            "${f.packetCount} pkts · ${formatBytes(f.bytes)} · ${f.lastInfo}",
            color = TerminalGray,
            fontSize = 11.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NexusButton(
                text = when {
                    lookup?.loading == true -> "Caut $remoteIp…"
                    lookup?.info != null || lookup?.error != null -> "Reîncarcă $remoteIp"
                    else -> "Cine e $remoteIp?"
                },
                onClick = onLookup,
                enabled = lookup?.loading != true
            )
            if (lookup != null && lookup.loading != true) {
                NexusButton("Ascunde", onClick = onClear)
            }
        }
        lookup?.let { IpInfoView(it) }
    }
}

@Composable
private fun IpInfoView(lookup: com.example.toolkit.ui.monitor.IpLookup) {
    val info = lookup.info
    Spacer(modifier = Modifier.height(8.dp))
    when {
        lookup.loading -> Text("Interoghează geo + RDAP/WHOIS…", color = MuteGreen, fontSize = 12.sp)
        info != null -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(VoidBlack.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                    .border(1.dp, NeonGreen.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                    .padding(10.dp)
            ) {
                Text("IP INTEL — ${info.ip}", color = NeonGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(6.dp))
                info.company?.let { KeyValueRow("Companie (WHOIS)", it) }
                info.org?.let { KeyValueRow("Organizație", it) }
                info.isp?.let { KeyValueRow("ISP", it) }
                info.domain?.let { UrlLink(url = it, label = "Domeniu", showHint = false) }
                info.asn?.let { KeyValueRow("ASN", listOfNotNull(it, info.asnOrg).joinToString(" · ")) }
                info.netName?.let { KeyValueRow("Rețea", it) }
                info.cidr?.let { KeyValueRow("Bloc IP", it) }
                info.reverseDns?.let { UrlLink(url = it, label = "Reverse DNS", showHint = false) }
                info.hostname?.let { UrlLink(url = it, label = "Hostname", showHint = false) }
                val loc = listOfNotNull(info.city, info.region, info.country).joinToString(", ")
                if (loc.isNotBlank()) KeyValueRow("Locație", loc)
                info.continent?.let { KeyValueRow("Continent", it) }
                info.timezone?.let { KeyValueRow("Fus orar", it) }
                info.type?.let { KeyValueRow("Tip", it) }
                info.abuseEmail?.let { KeyValueRow("Abuse", it) }
                if (info.latitude != null && info.longitude != null) {
                    KeyValueRow("Coordonate", "${info.latitude}, ${info.longitude}")
                }
                info.error?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(it, color = MuteGreen, fontSize = 11.sp)
                }
            }
        }
        lookup.error != null -> Text(lookup.error, color = AlertRed, fontSize = 12.sp)
    }
}

private fun formatBytes(n: Long): String = when {
    n < 1024 -> "$n B"
    n < 1024 * 1024 -> "${n / 1024} KB"
    else -> String.format(Locale.US, "%.1f MB", n / (1024.0 * 1024.0))
}
