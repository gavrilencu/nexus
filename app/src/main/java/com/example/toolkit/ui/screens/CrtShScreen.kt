package com.example.toolkit.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
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
import com.example.toolkit.data.crtsh.CrtEntry
import com.example.toolkit.ui.components.NexusButton
import com.example.toolkit.ui.components.NexusPanel
import com.example.toolkit.ui.components.NexusTextField
import com.example.toolkit.ui.components.ScreenHeader
import com.example.toolkit.ui.components.StatusChip
import com.example.toolkit.ui.crtsh.CrtShViewModel
import com.example.toolkit.ui.theme.AlertRed
import com.example.toolkit.ui.theme.GhostWhite
import com.example.toolkit.ui.theme.MuteGreen
import com.example.toolkit.ui.theme.NeonGreen
import com.example.toolkit.ui.theme.TerminalGray
import com.example.toolkit.ui.theme.VoidBlack

@Composable
fun CrtShScreen(vm: CrtShViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val r = state.result

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VoidBlack)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        ScreenHeader("CT Log Enum", "Passive subdomains from crt.sh CT logs")
        Spacer(modifier = Modifier.height(16.dp))
        NexusTextField(state.domain, vm::onDomain, "example.com",
            imeAction = ImeAction.Go, onDone = vm::enumerate)
        Spacer(modifier = Modifier.height(12.dp))
        NexusButton("Enumerate", onClick = vm::enumerate, loading = state.loading, enabled = state.domain.isNotBlank())

        r?.error?.let {
            Spacer(modifier = Modifier.height(12.dp))
            Text(it, color = AlertRed)
        }

        r?.takeIf { it.error == null }?.let { res ->
            Spacer(modifier = Modifier.height(16.dp))
            StatusChip("${res.total} unique subdomains", color = NeonGreen)
            Spacer(modifier = Modifier.height(12.dp))
            NexusPanel(title = "Subdomains") {
                if (res.subdomains.isEmpty()) Text("None found in CT logs.", color = TerminalGray)
                SelectionContainer {
                    Column {
                        res.subdomains.forEach { SubRow(it) }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun SubRow(e: CrtEntry) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(e.name, color = GhostWhite, fontWeight = FontWeight.SemiBold)
        val meta = buildString {
            e.lastSeen?.let { append("since $it") }
            if (e.issuers.isNotEmpty()) { if (isNotEmpty()) append("  ·  "); append(e.issuers.joinToString(", ")) }
        }
        if (meta.isNotBlank()) Text(meta, color = MuteGreen, style = MaterialTheme.typography.bodySmall)
    }
}
