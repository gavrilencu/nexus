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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
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
import com.example.toolkit.ui.theme.AccentSoft
import com.example.toolkit.ui.theme.AlertAmber
import com.example.toolkit.ui.theme.AlertRed
import com.example.toolkit.ui.theme.BorderGreen
import com.example.toolkit.ui.theme.GhostWhite
import com.example.toolkit.ui.theme.MuteGreen
import com.example.toolkit.ui.theme.NeonGreen
import com.example.toolkit.ui.theme.TerminalGray
import com.example.toolkit.ui.theme.VoidBlack
import com.example.toolkit.ui.wayback.WaybackViewModel

@Composable
fun WaybackScreen(vm: WaybackViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val r = state.result

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VoidBlack)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        ScreenHeader("Wayback URLs", "Historical URLs from web.archive.org CDX")
        Spacer(modifier = Modifier.height(16.dp))
        NexusTextField(state.domain, vm::onDomain, "example.com",
            imeAction = ImeAction.Go, onDone = vm::fetch)
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Toggle("Include subdomains", state.includeSubs, vm::toggleSubs)
            Toggle("Only interesting", state.onlyInteresting, vm::toggleOnlyInteresting)
        }
        Spacer(modifier = Modifier.height(12.dp))
        NexusButton("Fetch", onClick = vm::fetch, loading = state.loading, enabled = state.domain.isNotBlank())

        r?.error?.let {
            Spacer(modifier = Modifier.height(12.dp))
            Text(it, color = AlertRed)
        }

        r?.takeIf { it.error == null }?.let { res ->
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatusChip("${res.total} URLs", color = NeonGreen)
                StatusChip("${res.params.size} params", color = MuteGreen)
            }
            if (res.params.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                NexusPanel(title = "Discovered parameters") {
                    SelectionContainer {
                        Text(res.params.joinToString(", "), color = GhostWhite,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            val urls = if (state.onlyInteresting) res.urls.filter { it.interesting } else res.urls
            NexusPanel(title = "URLs (${urls.size})") {
                SelectionContainer {
                    Column {
                        urls.take(1500).forEach { u ->
                            Text(
                                u.url,
                                color = if (u.interesting) AlertAmber else TerminalGray,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun Toggle(label: String, on: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .background(if (on) AccentSoft else VoidBlack, RoundedCornerShape(100.dp))
            .border(1.dp, if (on) NeonGreen else BorderGreen, RoundedCornerShape(100.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(label, color = if (on) NeonGreen else MuteGreen,
            style = MaterialTheme.typography.labelLarge)
    }
}
