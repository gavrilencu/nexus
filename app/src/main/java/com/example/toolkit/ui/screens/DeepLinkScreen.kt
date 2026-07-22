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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.example.toolkit.data.deeplink.DeepLinkTarget
import com.example.toolkit.ui.components.NexusButton
import com.example.toolkit.ui.components.NexusPanel
import com.example.toolkit.ui.components.NexusSearchField
import com.example.toolkit.ui.components.NexusTextField
import com.example.toolkit.ui.components.ScreenHeader
import com.example.toolkit.ui.components.StatusChip
import com.example.toolkit.ui.deeplink.DeepLinkViewModel
import com.example.toolkit.ui.theme.BorderGreen
import com.example.toolkit.ui.theme.GhostWhite
import com.example.toolkit.ui.theme.MuteGreen
import com.example.toolkit.ui.theme.NeonGreen
import com.example.toolkit.ui.theme.PanelGreen
import com.example.toolkit.ui.theme.SoftGreen
import com.example.toolkit.ui.theme.TerminalGray
import com.example.toolkit.ui.theme.VoidBlack

@Composable
fun DeepLinkScreen(vm: DeepLinkViewModel = viewModel()) {
    val state by vm.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VoidBlack)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        ScreenHeader("Deep-Link Tester", "Enumerate app links · resolve · launch intents")
        Spacer(modifier = Modifier.height(16.dp))

        NexusPanel(title = "Manual intent") {
            NexusTextField(state.manualUri, vm::onManualUri, "myapp://path or https://host/x")
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NexusButton("Resolve", onClick = { vm.resolve(state.manualUri) },
                    modifier = Modifier.weight(1f), enabled = state.manualUri.isNotBlank())
                NexusButton("Launch", onClick = { vm.launch(state.manualUri) },
                    modifier = Modifier.weight(1f), enabled = state.manualUri.isNotBlank())
            }
            state.message?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(it, color = SoftGreen, style = MaterialTheme.typography.bodySmall)
            }
            state.handlers.forEach { h ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(h.label, color = GhostWhite, style = MaterialTheme.typography.bodyMedium)
                        Text(h.packageName, color = TerminalGray, style = MaterialTheme.typography.bodySmall)
                    }
                    Text("launch", color = NeonGreen, modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { vm.launch(state.manualUri, h.packageName) }
                        .padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val current = state.current
        if (current != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(current.label, color = GhostWhite, style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f))
                Text("‹ back", color = NeonGreen, modifier = Modifier
                    .clip(RoundedCornerShape(8.dp)).clickable { vm.clearApp() }.padding(8.dp))
            }
            current.error?.let { Text(it, color = com.example.toolkit.ui.theme.AlertRed) }
            Spacer(modifier = Modifier.height(8.dp))
            if (current.links.isEmpty() && current.error == null)
                Text("No BROWSABLE deep links declared in the manifest.", color = MuteGreen)
            current.links.forEach { LinkRow(it, current.packageName) { uri, pkg -> vm.launch(uri, pkg) } }
        } else {
            Text("Enumerate an installed app's deep links:", color = MuteGreen)
            Spacer(modifier = Modifier.height(8.dp))
            NexusSearchField(value = state.query, onValueChange = vm::setQuery, placeholder = "filter installed apps…")
            Spacer(modifier = Modifier.height(10.dp))
            if (state.enumerating) Text("Reading manifest…", color = MuteGreen)
            val q = state.query.trim()
            val list = if (q.isBlank()) state.installed
            else state.installed.filter { it.label.contains(q, true) || it.packageName.contains(q, true) }
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(list, key = { it.packageName }) { app ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(PanelGreen.copy(alpha = 0.6f))
                            .border(1.dp, BorderGreen, RoundedCornerShape(12.dp))
                            .clickable { vm.enumerate(app.packageName) }
                            .padding(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(app.label, color = GhostWhite, style = MaterialTheme.typography.titleMedium)
                            Text(app.packageName, color = MuteGreen, style = MaterialTheme.typography.bodySmall)
                        }
                        if (app.system) StatusChip("sys", color = MuteGreen)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun LinkRow(d: DeepLinkTarget, pkg: String, onLaunch: (String, String?) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            .background(PanelGreen.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .border(1.dp, BorderGreen, RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(d.uri, color = GhostWhite, fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall)
            if (d.autoVerify) Text("autoVerify", color = SoftGreen, style = MaterialTheme.typography.labelSmall)
        }
        Text("launch", color = NeonGreen, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clip(RoundedCornerShape(8.dp))
                .clickable { onLaunch(d.uri, pkg) }.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}
