package com.example.toolkit.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.toolkit.ui.components.NexusButton
import com.example.toolkit.ui.components.NexusPanel
import com.example.toolkit.ui.components.NexusTextField
import com.example.toolkit.ui.components.ScreenHeader
import com.example.toolkit.ui.components.StatusChip
import com.example.toolkit.ui.components.WarningBanner
import com.example.toolkit.ui.subdomain.SubdomainViewModel
import com.example.toolkit.ui.theme.AlertAmber
import com.example.toolkit.ui.theme.AlertRed
import com.example.toolkit.ui.theme.BorderGreen
import com.example.toolkit.ui.theme.GhostWhite
import com.example.toolkit.ui.theme.MuteGreen
import com.example.toolkit.ui.theme.NeonGreen
import com.example.toolkit.ui.theme.PanelGreen
import com.example.toolkit.ui.theme.VoidBlack

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SubdomainScreen(vm: SubdomainViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val progress = if (state.total > 0) state.scanned.toFloat() / state.total else 0f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VoidBlack)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        ScreenHeader(
            title = "Subdomain Finder",
            subtitle = "DoH · wildcard filter · HTTP verify · real hosts only"
        )
        Spacer(modifier = Modifier.height(12.dp))
        WarningBanner("Only scan domains you are authorized to assess. Wildcard DNS fakes are auto-filtered.")
        Spacer(modifier = Modifier.height(16.dp))

        NexusTextField(
            state.domain, vm::onDomain, "example.com",
            imeAction = ImeAction.Go, onDone = vm::start
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NexusButton(
                text = if (state.loading) "Scanning" else "Start",
                onClick = vm::start,
                loading = state.loading,
                enabled = state.domain.isNotBlank(),
                modifier = Modifier.weight(1f)
            )
            if (state.loading) {
                NexusButton("Stop", onClick = vm::stop, modifier = Modifier.weight(0.5f))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusChip(state.status)
            if (state.total > 0) StatusChip("${state.scanned}/${state.total}")
            StatusChip("${state.hits.size} real")
            if (state.wildcard) StatusChip("WILDCARD ON")
            if (state.filteredWildcards > 0) StatusChip("${state.filteredWildcards} fakes dropped")
        }

        if (state.wildcard && state.wildcardIps.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Wildcard / catch-all IPs: ${state.wildcardIps.joinToString(", ")}",
                color = AlertAmber
            )
        }

        if (state.loading || state.scanned > 0) {
            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = NeonGreen,
                trackColor = BorderGreen
            )
        }

        state.error?.let {
            Spacer(modifier = Modifier.height(12.dp))
            Text(it, color = AlertRed)
        }

        Spacer(modifier = Modifier.height(16.dp))
        NexusPanel(title = "REAL SUBDOMAINS (${state.hits.size})") {
            if (state.hits.isEmpty()) {
                Text(
                    text = if (state.loading) {
                        "Scanning… false positives are filtered live"
                    } else {
                        "No confirmed subdomains yet. Try another domain or wait for scan."
                    },
                    color = MuteGreen
                )
            } else {
                state.hits.forEach { hit ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .border(1.dp, BorderGreen, RoundedCornerShape(2.dp))
                            .background(PanelGreen)
                            .padding(10.dp)
                    ) {
                        Text(hit.host, color = NeonGreen, fontWeight = FontWeight.Bold)
                        if (hit.ips.isNotEmpty()) {
                            Text("A/AAAA: ${hit.ips.joinToString(", ")}", color = GhostWhite)
                        }
                        hit.cname?.let {
                            Text("CNAME: $it", color = MuteGreen)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("via ${hit.evidence}", color = MuteGreen)
                            hit.httpStatus?.let { Text("HTTP $it", color = MuteGreen) }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}
