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
import com.example.toolkit.data.exposed.ExposedHit
import com.example.toolkit.ui.components.NexusButton
import com.example.toolkit.ui.components.NexusPanel
import com.example.toolkit.ui.components.NexusTextField
import com.example.toolkit.ui.components.ScreenHeader
import com.example.toolkit.ui.components.StatusChip
import com.example.toolkit.ui.components.UrlLink
import com.example.toolkit.ui.exposed.ExposedViewModel
import com.example.toolkit.ui.theme.AlertAmber
import com.example.toolkit.ui.theme.AlertRed
import com.example.toolkit.ui.theme.GhostWhite
import com.example.toolkit.ui.theme.MuteGreen
import com.example.toolkit.ui.theme.SoftGreen
import com.example.toolkit.ui.theme.TerminalGray
import com.example.toolkit.ui.theme.VoidBlack

@Composable
fun ExposedScreen(vm: ExposedViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val r = state.result

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VoidBlack)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        ScreenHeader("Exposed Files", ".git · .env · backups · configs · keys")
        Spacer(modifier = Modifier.height(16.dp))
        NexusTextField(state.url, vm::onUrl, "https://target.com",
            imeAction = ImeAction.Go, onDone = vm::scan)
        Spacer(modifier = Modifier.height(12.dp))
        NexusButton("Scan", onClick = vm::scan, loading = state.loading, enabled = state.url.isNotBlank())

        r?.error?.let {
            Spacer(modifier = Modifier.height(12.dp))
            Text(it, color = AlertRed)
        }

        r?.takeIf { it.error == null }?.let { res ->
            Spacer(modifier = Modifier.height(16.dp))
            StatusChip(
                if (res.hits.isNotEmpty()) "${res.hits.size} exposed" else "Nothing exposed",
                color = if (res.hits.isNotEmpty()) AlertRed else SoftGreen
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text("Checked ${res.checked} known paths", color = TerminalGray)
            Spacer(modifier = Modifier.height(12.dp))
            res.hits.forEach { HitCard(it); Spacer(modifier = Modifier.height(10.dp)) }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun HitCard(h: ExposedHit) {
    val color = when (h.severity) {
        "HIGH" -> AlertRed; "MEDIUM" -> AlertAmber; else -> MuteGreen
    }
    NexusPanel {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusChip(h.severity, color = color)
            StatusChip("HTTP ${h.status}", color = MuteGreen)
            if (h.confirmed) StatusChip("confirmed", color = SoftGreen)
        }
        Spacer(modifier = Modifier.height(6.dp))
        SelectionContainer {
            Text(h.path, color = GhostWhite, fontWeight = FontWeight.SemiBold)
        }
        UrlLink(url = h.url, showHint = true)
        Spacer(modifier = Modifier.height(4.dp))
        Text(h.note, color = if (h.severity == "HIGH") color else GhostWhite)
    }
}
