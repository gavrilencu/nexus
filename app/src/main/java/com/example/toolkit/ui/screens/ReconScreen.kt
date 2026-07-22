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
import com.example.toolkit.ui.recon.ReconViewModel
import com.example.toolkit.ui.theme.AlertRed
import com.example.toolkit.ui.theme.GhostWhite
import com.example.toolkit.ui.theme.MuteGreen
import com.example.toolkit.ui.theme.NeonGreen
import com.example.toolkit.ui.theme.VoidBlack

@Composable
fun ReconScreen(vm: ReconViewModel = viewModel()) {
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
            title = "Domain Recon",
            subtitle = "Resolve host intel: DNS, HTTP, TLS, geo, security headers"
        )
        Spacer(modifier = Modifier.height(16.dp))

        NexusTextField(
            value = state.target,
            onValueChange = vm::onTargetChange,
            label = "domain / host",
            imeAction = ImeAction.Go,
            onDone = vm::analyze
        )
        Spacer(modifier = Modifier.height(12.dp))
        NexusButton(
            text = "Run Recon",
            onClick = vm::analyze,
            loading = state.loading,
            enabled = state.target.isNotBlank()
        )

        result?.error?.let {
            Spacer(modifier = Modifier.height(12.dp))
            Text(it, color = AlertRed)
        }

        result?.takeIf { it.error == null }?.let { r ->
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip(r.target)
                r.httpStatus?.let { StatusChip("HTTP $it") }
                r.responseTimeMs?.let { StatusChip("${it}ms") }
            }

            Spacer(modifier = Modifier.height(12.dp))
            NexusPanel(title = "DNS / NETWORK") {
                KeyValueRow("IPs", r.resolvedIps.joinToString(", ").ifBlank { "—" })
                KeyValueRow("Hostname", r.hostname.orEmpty())
                KeyValueRow("Country", r.geoCountry.orEmpty())
                KeyValueRow("City", r.geoCity.orEmpty())
                KeyValueRow("ISP", r.geoIsp.orEmpty())
                KeyValueRow("ORG", r.geoOrg.orEmpty())
                KeyValueRow("ASN", r.geoAs.orEmpty())
            }

            Spacer(modifier = Modifier.height(12.dp))
            NexusPanel(title = "HTTP FINGERPRINT") {
                KeyValueRow("Status", r.httpStatus?.toString().orEmpty())
                KeyValueRow("Server", r.serverHeader.orEmpty())
                KeyValueRow("X-Powered-By", r.poweredBy.orEmpty())
                KeyValueRow("Content-Type", r.contentType.orEmpty())
                KeyValueRow("Latency", r.responseTimeMs?.let { "${it} ms" }.orEmpty())
            }

            Spacer(modifier = Modifier.height(12.dp))
            NexusPanel(title = "TLS CERTIFICATE") {
                KeyValueRow("Protocol", r.tlsVersion.orEmpty())
                KeyValueRow("Subject", r.certificateSubject.orEmpty())
                KeyValueRow("Issuer", r.certificateIssuer.orEmpty())
                KeyValueRow("Expires", r.certificateExpiry.orEmpty())
            }

            Spacer(modifier = Modifier.height(12.dp))
            NexusPanel(title = "SECURITY HEADERS") {
                if (r.securityHeaders.isEmpty()) {
                    Text("No common security headers detected", color = MuteGreen)
                } else {
                    r.securityHeaders.forEach { (k, v) ->
                        KeyValueRow(k, v, valueColor = NeonGreen)
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
