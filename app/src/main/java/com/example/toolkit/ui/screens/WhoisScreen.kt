package com.example.toolkit.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
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
import com.example.toolkit.ui.theme.AlertRed
import com.example.toolkit.ui.theme.GhostWhite
import com.example.toolkit.ui.theme.MuteGreen
import com.example.toolkit.ui.theme.VoidBlack
import com.example.toolkit.ui.whois.WhoisViewModel

@Composable
fun WhoisScreen(vm: WhoisViewModel = viewModel()) {
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
            title = "WHOIS / RDAP",
            subtitle = "Înregistrare domeniu & IP prin RDAP (JSON peste HTTPS)"
        )
        Spacer(modifier = Modifier.height(16.dp))
        NexusTextField(
            state.query, vm::onQuery, "domeniu sau IP",
            imeAction = ImeAction.Go, onDone = vm::lookup
        )
        Spacer(modifier = Modifier.height(12.dp))
        NexusButton("Lookup", onClick = vm::lookup, loading = state.loading, enabled = state.query.isNotBlank())

        result?.error?.let {
            Spacer(modifier = Modifier.height(12.dp))
            Text(it, color = AlertRed)
        }

        result?.takeIf { it.error == null }?.let { r ->
            Spacer(modifier = Modifier.height(16.dp))
            StatusChip(if (r.type == "ip") "IP" else "DOMAIN")

            Spacer(modifier = Modifier.height(12.dp))
            NexusPanel(title = "IDENTITY") {
                KeyValueRow("Query", r.query)
                KeyValueRow("Handle", r.handle ?: "—")
                r.name?.let { KeyValueRow("Name", it) }
                r.range?.let { KeyValueRow("Range", it) }
                r.country?.let { KeyValueRow("Country", it) }
                r.dnssec?.let { KeyValueRow("DNSSEC", it) }
            }

            if (r.statuses.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                NexusPanel(title = "STATUS") {
                    r.statuses.forEach { Text("• $it", color = GhostWhite, modifier = Modifier.padding(vertical = 2.dp)) }
                }
            }

            if (r.events.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                NexusPanel(title = "TIMELINE") {
                    r.events.forEach { KeyValueRow(it.action, it.date.ifBlank { "—" }) }
                }
            }

            if (r.entities.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                NexusPanel(title = "ENTITIES") {
                    r.entities.forEach { Text("• $it", color = GhostWhite, modifier = Modifier.padding(vertical = 2.dp)) }
                }
            }

            if (r.nameservers.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                NexusPanel(title = "NAMESERVERS") {
                    r.nameservers.forEach { Text(it, color = MuteGreen, modifier = Modifier.padding(vertical = 2.dp)) }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}
