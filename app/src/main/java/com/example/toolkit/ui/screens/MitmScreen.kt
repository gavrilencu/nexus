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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.toolkit.data.mitm.HttpExchange
import com.example.toolkit.ui.components.KeyValueRow
import com.example.toolkit.ui.components.NexusButton
import com.example.toolkit.ui.components.NexusPanel
import com.example.toolkit.ui.components.NexusTextField
import com.example.toolkit.ui.components.ScreenHeader
import com.example.toolkit.ui.components.StatusChip
import com.example.toolkit.ui.components.UrlLink
import com.example.toolkit.ui.components.WarningBanner
import com.example.toolkit.ui.mitm.MitmViewModel
import com.example.toolkit.ui.theme.AlertAmber
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
fun MitmScreen(vm: MitmViewModel = viewModel()) {
    val ui by vm.ui.collectAsState()
    val stats by vm.stats.collectAsState()
    val running by vm.running.collectAsState()
    val exchanges by vm.exchanges.collectAsState()

    val vpnLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) vm.onVpnPermissionGranted()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VoidBlack)
            .padding(16.dp)
    ) {
        ScreenHeader(
            title = "MITM / Proxy Capture",
            subtitle = "Local HTTPS decrypt · request/response · host filter"
        )
        Spacer(modifier = Modifier.height(8.dp))
        WarningBanner(
            "Authorized testing only. Install the NEXUS CA as a *user* certificate, " +
                "then start Proxy (set Wi‑Fi proxy) or VPN (transparent on this phone)."
        )
        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                NexusPanel(title = "CERTIFICATE AUTHORITY") {
                    KeyValueRow(
                        "CA",
                        ui.caSubject.substringAfter("CN=", ui.caSubject).substringBefore(',').ifBlank { "—" }
                    )
                    val org = remember(ui.caSubject) {
                        ui.caSubject.split(',')
                            .map { it.trim() }
                            .firstOrNull { it.startsWith("O=", ignoreCase = true) }
                            ?.substringAfter('=')
                            ?.trim()
                            .orEmpty()
                    }
                    if (org.isNotBlank()) {
                        KeyValueRow("Organization", org)
                    }
                    ui.caError?.let {
                        Text(it, color = AlertRed, fontSize = 12.sp)
                    }
                    Text(
                        "Android 11+ cannot install CA from apps silently. " +
                            "Install CA saves nexus-mitm-ca.crt to Downloads, then open: " +
                            "Settings → Security → Encryption & credentials → Install a certificate → CA certificate.",
                        color = MuteGreen,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NexusButton(
                            "Install CA",
                            onClick = vm::installCa,
                            modifier = Modifier.weight(1f),
                            enabled = ui.caReady
                        )
                        NexusButton(
                            "Open Settings",
                            onClick = vm::openSecuritySettings,
                            modifier = Modifier.weight(1f),
                            enabled = ui.caReady
                        )
                    }
                }
            }

            item {
                NexusPanel(title = "CAPTURE") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusChip(if (running) "LIVE" else "OFF", color = if (running) SoftGreen else MuteGreen)
                        StatusChip(stats.mode.uppercase())
                        StatusChip("${stats.total} req")
                        StatusChip("HTTPS ${stats.https}")
                        StatusChip("HTTP ${stats.http}")
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(ui.statusMessage, color = TerminalGray, fontSize = 12.sp)
                    if (stats.listenHint.isNotBlank()) {
                        Text("Listen: ${stats.listenHint}", color = NeonGreen, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    if (!running) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NexusButton(
                                "Start Proxy",
                                onClick = vm::startProxy,
                                modifier = Modifier.weight(1f),
                                enabled = ui.caReady
                            )
                            NexusButton(
                                "Start VPN MITM",
                                onClick = {
                                    val prep = vm.prepareVpn()
                                    if (prep != null) vpnLauncher.launch(prep) else vm.startVpn()
                                },
                                modifier = Modifier.weight(1f),
                                enabled = ui.caReady
                            )
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NexusButton("Stop", onClick = vm::stop, modifier = Modifier.weight(1f))
                            NexusButton("Clear", onClick = vm::clear, modifier = Modifier.weight(1f))
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Proxy mode: set Wi‑Fi → Proxy → Manual → ${stats.listenHint.ifBlank { "PHONE_IP:8888" }}\n" +
                            "VPN mode: captures this phone without Wi‑Fi proxy (apps with cert pinning still fail).",
                        color = MuteGreen,
                        fontSize = 11.sp
                    )
                }
            }

            item {
                NexusTextField(ui.hostFilter, vm::onFilter, "filter host / path (e.g. api.example.com)")
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Exchanges (${exchanges.size}) — tap for full request/response",
                    color = NeonGreen,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }

            if (exchanges.isEmpty()) {
                item {
                    Text(
                        if (running) "Waiting for HTTP/HTTPS…" else "Start Proxy or VPN to capture",
                        color = MuteGreen
                    )
                }
            } else {
                items(exchanges, key = { it.id }) { ex ->
                    ExchangeCard(
                        ex = ex,
                        expanded = ui.expandedId == ex.id,
                        onClick = { vm.toggleExpand(ex.id) }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(28.dp)) }
        }
    }
}

@Composable
private fun ExchangeCard(ex: HttpExchange, expanded: Boolean, onClick: () -> Unit) {
    val time = remember(ex.timestamp) {
        SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(ex.timestamp))
    }
    val codeColor = when {
        ex.error != null -> AlertRed
        ex.responseCode in 200..299 -> SoftGreen
        ex.responseCode in 300..399 -> NeonGreen
        ex.responseCode in 400..499 -> AlertAmber
        ex.responseCode >= 500 -> AlertRed
        else -> MuteGreen
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PanelGreen.copy(alpha = 0.82f), RoundedCornerShape(14.dp))
            .border(1.dp, GlassBorder.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(time, color = MuteGreen, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            Text(ex.scheme.uppercase(), color = NeonGreen, fontSize = 11.sp)
            Text(ex.method, color = GhostWhite, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            StatusChip(
                if (ex.responseCode > 0) ex.responseCode.toString() else "ERR",
                color = codeColor
            )
            StatusChip(ex.via)
            Text("${ex.durationMs} ms", color = TerminalGray, fontSize = 11.sp)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(ex.host + ex.path, color = GhostWhite, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        ex.error?.let {
            Text(it, color = AlertRed, fontSize = 11.sp)
        }
        if (expanded) {
            Spacer(modifier = Modifier.height(8.dp))
            UrlLink(url = ex.url, showHint = true)
            Spacer(modifier = Modifier.height(6.dp))
            Text("REQUEST HEADERS", color = NeonGreen, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            SelectionContainer {
                Column {
                    ex.requestHeaders.forEach { (k, v) ->
                        Text("$k: $v", color = TerminalGray, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
            if (ex.requestBody.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text("REQUEST BODY", color = NeonGreen, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                SelectionContainer {
                    Text(ex.requestBody.take(8_000), color = GhostWhite, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "RESPONSE ${ex.responseCode} ${ex.responseReason}",
                color = codeColor,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
            SelectionContainer {
                Column {
                    ex.responseHeaders.forEach { (k, v) ->
                        Text("$k: $v", color = TerminalGray, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
            if (ex.responseBody.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text("RESPONSE BODY", color = NeonGreen, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                SelectionContainer {
                    Text(ex.responseBody.take(12_000), color = GhostWhite, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}
