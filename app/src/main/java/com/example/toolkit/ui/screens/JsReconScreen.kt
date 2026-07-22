package com.example.toolkit.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.example.toolkit.data.jsrecon.SecretHit
import com.example.toolkit.ui.components.NexusButton
import com.example.toolkit.ui.components.NexusPanel
import com.example.toolkit.ui.components.NexusTextField
import com.example.toolkit.ui.components.ScreenHeader
import com.example.toolkit.ui.components.StatusChip
import com.example.toolkit.ui.components.UrlLink
import com.example.toolkit.ui.jsrecon.JsReconViewModel
import com.example.toolkit.ui.theme.AlertRed
import com.example.toolkit.ui.theme.GhostWhite
import com.example.toolkit.ui.theme.MuteGreen
import com.example.toolkit.ui.theme.NeonGreen
import com.example.toolkit.ui.theme.SoftGreen
import com.example.toolkit.ui.theme.TerminalGray
import com.example.toolkit.ui.theme.VoidBlack

@Composable
fun JsReconScreen(vm: JsReconViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val r = state.result

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VoidBlack)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        ScreenHeader("JS Recon", "Endpoints · API routes · secrets from JS")
        Spacer(modifier = Modifier.height(16.dp))
        NexusTextField(state.url, vm::onUrl, "https://target.com",
            imeAction = ImeAction.Go, onDone = vm::analyze)
        Spacer(modifier = Modifier.height(12.dp))
        NexusButton("Extract", onClick = vm::analyze, loading = state.loading, enabled = state.url.isNotBlank())

        r?.error?.let {
            Spacer(modifier = Modifier.height(12.dp))
            Text(it, color = AlertRed)
        }

        r?.takeIf { it.error == null }?.let { res ->
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatusChip("${res.jsFiles.size} JS", color = NeonGreen)
                StatusChip("${res.endpoints.size} endpoints", color = SoftGreen)
                StatusChip("${res.secrets.size} secrets",
                    color = if (res.secrets.isNotEmpty()) AlertRed else MuteGreen)
            }

            if (res.secrets.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                NexusPanel(title = "Secrets") {
                    res.secrets.forEach { SecretRow(it) }
                }
            }

            if (res.endpoints.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                NexusPanel(title = "Endpoints") {
                    Text("tap open · hold copy", color = MuteGreen)
                    Spacer(modifier = Modifier.height(4.dp))
                    res.endpoints.forEach { ep ->
                        UrlLink(url = ep, baseUrl = state.url, showHint = false, maxLines = 2)
                    }
                }
            }

            if (res.jsFiles.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                NexusPanel(title = "JS files") {
                    Text("tap open · hold copy", color = MuteGreen)
                    Spacer(modifier = Modifier.height(4.dp))
                    res.jsFiles.forEach { js ->
                        UrlLink(url = js, color = TerminalGray, showHint = false, maxLines = 2)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun SecretRow(s: SecretHit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusChip(s.type, color = AlertRed)
        }
        Spacer(modifier = Modifier.height(3.dp))
        SelectionContainer {
            Text(s.value, color = GhostWhite, fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodySmall)
        }
        Text(s.source, color = TerminalGray, style = MaterialTheme.typography.bodySmall)
    }
}
