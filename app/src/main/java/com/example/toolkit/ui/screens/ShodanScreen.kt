package com.example.toolkit.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.toolkit.data.shodan.ShodanService
import com.example.toolkit.ui.components.KeyValueRow
import com.example.toolkit.ui.components.NexusButton
import com.example.toolkit.ui.components.NexusPanel
import com.example.toolkit.ui.components.NexusTextField
import com.example.toolkit.ui.components.ScreenHeader
import com.example.toolkit.ui.components.StatusChip
import com.example.toolkit.ui.shodan.ShodanViewModel
import com.example.toolkit.ui.theme.AlertRed
import com.example.toolkit.ui.theme.GhostWhite
import com.example.toolkit.ui.theme.MuteGreen
import com.example.toolkit.ui.theme.NeonGreen
import com.example.toolkit.ui.theme.TerminalGray
import com.example.toolkit.ui.theme.VoidBlack

@Composable
fun ShodanScreen(vm: ShodanViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val r = state.result

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VoidBlack)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        ScreenHeader("Shodan Lookup", "IP exposure · ports · CVEs · free InternetDB")
        Spacer(modifier = Modifier.height(16.dp))
        NexusTextField(state.ip, vm::onIp, "8.8.8.8",
            imeAction = ImeAction.Go, onDone = vm::lookup)
        Spacer(modifier = Modifier.height(10.dp))
        NexusTextField(state.apiKey, vm::onApiKey, "Shodan API key (optional — richer data)")
        Spacer(modifier = Modifier.height(12.dp))
        NexusButton("Lookup", onClick = vm::lookup, loading = state.loading, enabled = state.ip.isNotBlank())

        r?.error?.let {
            Spacer(modifier = Modifier.height(12.dp))
            Text(it, color = AlertRed)
        }

        r?.takeIf { it.error == null }?.let { res ->
            Spacer(modifier = Modifier.height(16.dp))
            StatusChip(res.source, color = NeonGreen)
            Spacer(modifier = Modifier.height(12.dp))

            NexusPanel(title = "Host") {
                KeyValueRow("IP", res.ip)
                if (res.org != null) KeyValueRow("Org", res.org)
                if (res.isp != null) KeyValueRow("ISP", res.isp)
                if (res.country != null) KeyValueRow("Country", res.country)
                if (res.os != null) KeyValueRow("OS", res.os)
                KeyValueRow("Open ports", res.ports.joinToString(", ").ifBlank { "—" })
                if (res.hostnames.isNotEmpty()) KeyValueRow("Hostnames", res.hostnames.joinToString(", "))
                if (res.tags.isNotEmpty()) KeyValueRow("Tags", res.tags.joinToString(", "))
            }

            if (res.vulns.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                NexusPanel(title = "Known vulnerabilities (${res.vulns.size})") {
                    SelectionContainer {
                        Text(res.vulns.joinToString(", "), color = AlertRed)
                    }
                }
            }

            if (res.cpes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                NexusPanel(title = "CPEs") {
                    res.cpes.forEach { Text(it, color = TerminalGray, style = MaterialTheme.typography.bodySmall) }
                }
            }

            if (res.services.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                NexusPanel(title = "Services") {
                    res.services.forEach { ServiceRow(it) }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun ServiceRow(s: ShodanService) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text("${s.port}/${s.transport}${s.product?.let { "  ·  $it" } ?: ""}",
            color = GhostWhite, fontWeight = FontWeight.SemiBold)
        s.banner?.let {
            Text(it, color = TerminalGray, style = MaterialTheme.typography.bodySmall)
        }
    }
}
