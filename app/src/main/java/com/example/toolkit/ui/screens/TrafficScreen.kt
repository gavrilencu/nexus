package com.example.toolkit.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.toolkit.ui.components.KeyValueRow
import com.example.toolkit.ui.components.NexusButton
import com.example.toolkit.ui.components.NexusPanel
import com.example.toolkit.ui.components.NexusTextField
import com.example.toolkit.ui.components.ScreenHeader
import com.example.toolkit.ui.components.StatusChip
import com.example.toolkit.ui.theme.AlertRed
import com.example.toolkit.ui.theme.BorderGreen
import com.example.toolkit.ui.theme.GhostWhite
import com.example.toolkit.ui.theme.MuteGreen
import com.example.toolkit.ui.theme.NeonGreen
import com.example.toolkit.ui.theme.PanelGreen
import com.example.toolkit.ui.theme.VoidBlack
import com.example.toolkit.ui.traffic.TrafficViewModel

@Composable
fun TrafficScreen(vm: TrafficViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val methods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VoidBlack)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        ScreenHeader(
            title = "Traffic Monitor",
            subtitle = "Issue requests and inspect timing, headers, and body previews"
        )
        Spacer(modifier = Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            methods.forEach { method ->
                FilterChip(
                    selected = state.method == method,
                    onClick = { vm.onMethodChange(method) },
                    label = { Text(method) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = NeonGreen.copy(alpha = 0.2f),
                        selectedLabelColor = NeonGreen,
                        containerColor = PanelGreen,
                        labelColor = MuteGreen
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        NexusTextField(state.url, vm::onUrlChange, "url")
        Spacer(modifier = Modifier.height(8.dp))
        NexusTextField(
            value = state.headers,
            onValueChange = vm::onHeadersChange,
            label = "headers (Key: Value per line)",
            singleLine = false
        )
        if (state.method !in listOf("GET", "HEAD", "DELETE")) {
            Spacer(modifier = Modifier.height(8.dp))
            NexusTextField(
                value = state.body,
                onValueChange = vm::onBodyChange,
                label = "body",
                singleLine = false
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NexusButton(
                text = "Capture",
                onClick = vm::capture,
                loading = state.loading,
                enabled = state.url.isNotBlank(),
                modifier = Modifier.weight(1f)
            )
            NexusButton(
                text = "Clear",
                onClick = vm::clear,
                modifier = Modifier.weight(0.5f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        NexusPanel(title = "SESSION LOG (${state.entries.size})") {
            if (state.entries.isEmpty()) {
                Text("No captures yet", color = MuteGreen)
            } else {
                state.entries.forEach { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .border(1.dp, BorderGreen, RoundedCornerShape(2.dp))
                            .background(
                                if (state.selected?.id == entry.id) NeonGreen.copy(0.08f)
                                else PanelGreen
                            )
                            .clickable { vm.select(entry) }
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(entry.method, color = NeonGreen, fontWeight = FontWeight.Bold)
                        Text(
                            text = entry.statusCode?.toString() ?: "ERR",
                            color = if (entry.error != null) AlertRed else GhostWhite
                        )
                        Text("${entry.durationMs}ms", color = MuteGreen)
                    }
                }
            }
        }

        state.selected?.let { entry ->
            Spacer(modifier = Modifier.height(12.dp))
            NexusPanel(title = "DETAIL") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusChip(entry.method)
                    entry.statusCode?.let { StatusChip("HTTP $it") }
                    StatusChip("${entry.durationMs}ms")
                }
                Spacer(modifier = Modifier.height(8.dp))
                KeyValueRow("URL", entry.url)
                entry.error?.let { Text(it, color = AlertRed) }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Response headers", color = NeonGreen)
                entry.responseHeaders.forEach { (k, v) ->
                    KeyValueRow(k, v)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Body preview", color = NeonGreen)
                Text(entry.responseBodyPreview ?: "—", color = GhostWhite)
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}
