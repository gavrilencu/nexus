package com.example.toolkit.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.toolkit.data.dexscan.DexFinding
import com.example.toolkit.ui.components.ApkSourcePicker
import com.example.toolkit.ui.components.NexusPanel
import com.example.toolkit.ui.components.ScreenHeader
import com.example.toolkit.ui.components.StatusChip
import com.example.toolkit.ui.dexscan.DexScanViewModel
import com.example.toolkit.ui.theme.AlertAmber
import com.example.toolkit.ui.theme.AlertRed
import com.example.toolkit.ui.theme.GhostWhite
import com.example.toolkit.ui.theme.MuteGreen
import com.example.toolkit.ui.theme.NeonGreen
import com.example.toolkit.ui.theme.SoftGreen
import com.example.toolkit.ui.theme.TerminalGray
import com.example.toolkit.ui.theme.VoidBlack

@Composable
fun DexScanScreen(vm: DexScanViewModel = viewModel()) {
    val state by vm.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VoidBlack)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        ScreenHeader("DEX API Scanner", "Dangerous APIs · crypto · WebView · exec")
        Spacer(modifier = Modifier.height(12.dp))

        when {
            state.analyzing -> Center("Scanning DEX bytecode…")
            state.result != null -> {
                val r = state.result!!
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(r.label, color = GhostWhite, style = MaterialTheme.typography.titleLarge)
                        Text("${r.dexCount} DEX · ${r.findings.size} categories", color = MuteGreen)
                    }
                    Text("‹ back", color = NeonGreen, modifier = Modifier
                        .clip(RoundedCornerShape(8.dp)).clickable { vm.clear() }.padding(8.dp))
                }
                Spacer(modifier = Modifier.height(10.dp))
                StatusChip("Risk ${r.riskScore}/100",
                    color = when { r.riskScore >= 50 -> AlertRed; r.riskScore >= 20 -> AlertAmber; else -> SoftGreen })
                Spacer(modifier = Modifier.height(12.dp))
                if (r.findings.isEmpty())
                    Text("No dangerous API patterns matched.", color = SoftGreen)
                r.findings.forEach { FindingCard(it); Spacer(modifier = Modifier.height(8.dp)) }
            }
            else -> ApkSourcePicker(
                fromInstalledTab = state.fromInstalled,
                onSelectTab = vm::selectTab,
                installed = state.installed,
                installedLoading = state.installedLoading,
                query = state.query,
                onQuery = vm::setQuery,
                onPickFile = vm::analyzeFile,
                onPickInstalled = vm::analyzeInstalled,
                error = state.error
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun FindingCard(f: DexFinding) {
    val color = when (f.severity) { "HIGH" -> AlertRed; "MEDIUM" -> AlertAmber; else -> MuteGreen }
    NexusPanel {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusChip(f.severity, color = color)
            Text(f.category, color = GhostWhite, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            StatusChip("×${f.hits}", color = MuteGreen)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(f.api, color = NeonGreen, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.height(2.dp))
        Text(f.description, color = GhostWhite, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Center(text: String) {
    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        CircularProgressIndicator(color = NeonGreen)
        Spacer(modifier = Modifier.height(12.dp))
        Text(text, color = MuteGreen, fontFamily = FontFamily.Monospace)
    }
}
