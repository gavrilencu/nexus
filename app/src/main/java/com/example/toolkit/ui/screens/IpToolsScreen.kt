package com.example.toolkit.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.toolkit.ui.components.KeyValueRow
import com.example.toolkit.ui.components.NexusButton
import com.example.toolkit.ui.components.NexusPanel
import com.example.toolkit.ui.components.NexusTextField
import com.example.toolkit.ui.components.ScreenHeader
import com.example.toolkit.ui.components.StatusChip
import com.example.toolkit.ui.ip.IpViewModel
import com.example.toolkit.ui.theme.AlertRed
import com.example.toolkit.ui.theme.GhostWhite
import com.example.toolkit.ui.theme.MuteGreen
import com.example.toolkit.ui.theme.NeonGreen
import com.example.toolkit.ui.theme.VoidBlack

@Composable
fun IpToolsScreen(vm: IpViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val result = state.result

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VoidBlack)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        ScreenHeader(
            title = "IP Tools",
            subtitle = "Resolve · reverse DNS · geo · port 80/443 reachability"
        )
        Spacer(modifier = Modifier.height(16.dp))
        NexusTextField(
            state.input, vm::onInput, "IP or hostname",
            imeAction = ImeAction.Go, onDone = vm::analyze
        )
        Spacer(modifier = Modifier.height(12.dp))
        NexusButton("Analyze", onClick = vm::analyze, loading = state.loading, enabled = state.input.isNotBlank())

        result?.error?.let {
            Spacer(modifier = Modifier.height(12.dp))
            Text(it, color = AlertRed)
        }

        result?.takeIf { it.error == null }?.let { r ->
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip(if (r.isIp) "IP" else "HOST")
                r.reachable443?.let { StatusChip(if (it) "443 UP" else "443 DOWN") }
                r.reachable80?.let { StatusChip(if (it) "80 UP" else "80 DOWN") }
            }
            Spacer(modifier = Modifier.height(12.dp))
            NexusPanel(title = "RESOLUTION") {
                KeyValueRow("Input", r.input)
                KeyValueRow("Hostname", r.resolvedHost.orEmpty())
                KeyValueRow("IPs", r.resolvedIps.joinToString(", "))
                KeyValueRow("Reverse DNS", r.reverseDns.orEmpty())
            }
            Spacer(modifier = Modifier.height(12.dp))
            NexusPanel(title = "GEO / ASN") {
                KeyValueRow("Country", r.country.orEmpty())
                KeyValueRow("City", r.city.orEmpty())
                KeyValueRow("ISP", r.isp.orEmpty())
                KeyValueRow("ORG", r.org.orEmpty())
                KeyValueRow("ASN", r.asInfo.orEmpty())
                KeyValueRow("Timezone", r.timezone.orEmpty())
            }
            Spacer(modifier = Modifier.height(12.dp))
            NexusPanel(title = "REACHABILITY") {
                KeyValueRow("HTTPS :443", when (r.reachable443) {
                    true -> "open${r.latency443Ms?.let { " (${it}ms)" } ?: ""}"
                    false -> "closed / filtered"
                    null -> "—"
                }, valueColor = if (r.reachable443 == true) NeonGreen else GhostWhite)
                KeyValueRow("HTTP :80", when (r.reachable80) {
                    true -> "open"
                    false -> "closed / filtered"
                    null -> "—"
                })
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}
