package com.example.toolkit.ui.screens

import androidx.compose.foundation.background
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
import com.example.toolkit.ui.components.NexusButton
import com.example.toolkit.ui.components.NexusPanel
import com.example.toolkit.ui.components.NexusTextField
import com.example.toolkit.ui.components.ScreenHeader
import com.example.toolkit.ui.components.StatusChip
import com.example.toolkit.ui.dns.DnsViewModel
import com.example.toolkit.ui.theme.AlertRed
import com.example.toolkit.ui.theme.GhostWhite
import com.example.toolkit.ui.theme.MuteGreen
import com.example.toolkit.ui.theme.NeonGreen
import com.example.toolkit.ui.theme.VoidBlack
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row

@Composable
fun DnsScreen(vm: DnsViewModel = viewModel()) {
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
            title = "DNS Dig",
            subtitle = "A · AAAA · MX · NS · TXT · CNAME · SOA via DoH"
        )
        Spacer(modifier = Modifier.height(16.dp))
        NexusTextField(
            state.host, vm::onHost, "domain / host",
            imeAction = ImeAction.Go, onDone = vm::dig
        )
        Spacer(modifier = Modifier.height(12.dp))
        NexusButton("Dig", onClick = vm::dig, loading = state.loading, enabled = state.host.isNotBlank())

        result?.error?.let {
            Spacer(modifier = Modifier.height(12.dp))
            Text(it, color = AlertRed)
        }

        result?.takeIf { it.error == null }?.let { r ->
            Spacer(modifier = Modifier.height(16.dp))
            StatusChip(r.host)
            Spacer(modifier = Modifier.height(12.dp))
            r.records.forEach { record ->
                NexusPanel(title = record.type) {
                    if (record.error != null && record.values.isEmpty()) {
                        Text(record.error, color = MuteGreen)
                    } else {
                        record.values.forEach { value ->
                            Text(value, color = GhostWhite, modifier = Modifier.padding(vertical = 2.dp))
                        }
                        record.error?.let { Text(it, color = MuteGreen) }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}
