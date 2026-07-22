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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.toolkit.ui.api.ApiViewModel
import com.example.toolkit.ui.components.KeyValueRow
import com.example.toolkit.ui.components.NexusButton
import com.example.toolkit.ui.components.NexusPanel
import com.example.toolkit.ui.components.NexusTextField
import com.example.toolkit.ui.components.ScreenHeader
import com.example.toolkit.ui.components.StatusChip
import com.example.toolkit.ui.theme.AlertRed
import com.example.toolkit.ui.theme.GhostWhite
import com.example.toolkit.ui.theme.MuteGreen
import com.example.toolkit.ui.theme.NeonGreen
import com.example.toolkit.ui.theme.PanelGreen
import com.example.toolkit.ui.theme.VoidBlack

@Composable
fun ApiScreen(vm: ApiViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val methods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VoidBlack)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        ScreenHeader(
            title = "API Lab",
            subtitle = "Craft requests and inspect status, headers, and payload"
        )
        Spacer(modifier = Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            methods.forEach { method ->
                FilterChip(
                    selected = state.method == method,
                    onClick = { vm.onMethod(method) },
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
        NexusTextField(state.url, vm::onUrl, "endpoint url")
        Spacer(modifier = Modifier.height(8.dp))
        NexusTextField(
            value = state.headers,
            onValueChange = vm::onHeaders,
            label = "headers (Key: Value per line)",
            singleLine = false
        )
        Spacer(modifier = Modifier.height(8.dp))
        NexusTextField(
            value = state.body,
            onValueChange = vm::onBody,
            label = "request body",
            singleLine = false
        )
        Spacer(modifier = Modifier.height(12.dp))
        NexusButton(
            text = "Send Probe",
            onClick = vm::send,
            loading = state.loading,
            enabled = state.url.isNotBlank()
        )

        state.result?.let { result ->
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip(result.method)
                result.statusCode?.let { StatusChip("HTTP $it") }
                StatusChip("${result.durationMs}ms")
            }
            result.error?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(it, color = AlertRed)
            }

            Spacer(modifier = Modifier.height(12.dp))
            NexusPanel(title = "RESPONSE HEADERS") {
                if (result.headers.isEmpty()) {
                    Text("—", color = MuteGreen)
                } else {
                    result.headers.forEach { (k, v) -> KeyValueRow(k, v) }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            NexusPanel(title = "BODY") {
                Text(
                    text = result.body.ifBlank { "—" },
                    color = GhostWhite
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
