package com.example.toolkit.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.example.toolkit.ui.ports.PortScanViewModel
import com.example.toolkit.ui.theme.AlertRed
import com.example.toolkit.ui.theme.BorderGreen
import com.example.toolkit.ui.theme.GhostWhite
import com.example.toolkit.ui.theme.MuteGreen
import com.example.toolkit.ui.theme.NeonGreen
import com.example.toolkit.ui.theme.PanelGreen
import com.example.toolkit.ui.theme.VoidBlack

@Composable
fun PortScanScreen(vm: PortScanViewModel = viewModel()) {
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
            title = "Port Scanner",
            subtitle = "Probe common service ports with concurrent sockets"
        )
        Spacer(modifier = Modifier.height(12.dp))
        WarningBanner("Scan only hosts you are authorized to assess.")

        Spacer(modifier = Modifier.height(16.dp))
        NexusTextField(
            value = state.host,
            onValueChange = vm::onHostChange,
            label = "host / ip",
            imeAction = ImeAction.Go,
            onDone = vm::startScan
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NexusButton(
                text = if (state.loading) "Scanning" else "Start Scan",
                onClick = vm::startScan,
                loading = state.loading,
                enabled = state.host.isNotBlank(),
                modifier = Modifier.weight(1f)
            )
            if (state.loading) {
                NexusButton(
                    text = "Stop",
                    onClick = vm::stopScan,
                    modifier = Modifier.weight(0.5f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusChip(state.status)
            if (state.total > 0) {
                StatusChip("${state.scanned}/${state.total}")
            }
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
        NexusPanel(title = "OPEN PORTS (${state.openPorts.size})") {
            if (state.openPorts.isEmpty()) {
                Text("No open ports reported yet", color = MuteGreen)
            } else {
                state.openPorts.forEach { port ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .border(1.dp, BorderGreen, RoundedCornerShape(2.dp))
                            .background(PanelGreen)
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${port.port}",
                            color = NeonGreen,
                            fontWeight = FontWeight.Bold
                        )
                        Text(port.service, color = GhostWhite)
                        Text(
                            text = port.latencyMs?.let { "${it}ms" } ?: "open",
                            color = MuteGreen
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}
