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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.toolkit.data.dork.Dork
import com.example.toolkit.ui.components.NexusButton
import com.example.toolkit.ui.components.NexusPanel
import com.example.toolkit.ui.components.NexusTextField
import com.example.toolkit.ui.components.ScreenHeader
import com.example.toolkit.ui.components.StatusChip
import com.example.toolkit.ui.dork.DorkViewModel
import com.example.toolkit.ui.theme.AlertRed
import com.example.toolkit.ui.theme.BorderGreen
import com.example.toolkit.ui.theme.GhostWhite
import com.example.toolkit.ui.theme.MatrixBlack
import com.example.toolkit.ui.theme.MuteGreen
import com.example.toolkit.ui.theme.NeonGreen
import com.example.toolkit.ui.theme.SoftGreen
import com.example.toolkit.ui.theme.TerminalGray
import com.example.toolkit.ui.theme.VoidBlack

@Composable
fun DorkScreen(vm: DorkViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VoidBlack)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        ScreenHeader("Dork Builder", "Google + GitHub dorks · live code search")
        Spacer(modifier = Modifier.height(16.dp))
        NexusTextField(state.target, vm::onTarget, "example.com or keyword")
        Spacer(modifier = Modifier.height(12.dp))
        NexusButton("Build dorks", onClick = vm::build, enabled = state.target.isNotBlank())

        if (state.googleDorks.isNotEmpty()) {
            val byCat = state.googleDorks.groupBy { it.category }
            byCat.forEach { (cat, dorks) ->
                Spacer(modifier = Modifier.height(12.dp))
                NexusPanel(title = "Google · $cat") {
                    dorks.forEach { DorkRow(it) { uriHandler.openUri(it.url) } }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            NexusPanel(title = "GitHub dorks") {
                state.githubDorks.forEach { DorkRow(it) { uriHandler.openUri(it.url) } }
            }

            Spacer(modifier = Modifier.height(16.dp))
            NexusPanel(title = "Live GitHub code search (optional)") {
                Text("Paste a GitHub token for authenticated code search:", color = MuteGreen,
                    style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))
                NexusTextField(state.token, vm::onToken, "ghp_… (token, optional)")
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NexusButton("Search: password", onClick = { vm.searchGithub("password") },
                        modifier = Modifier.weight(1f), loading = state.searching,
                        enabled = state.token.isNotBlank())
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NexusButton("api_key", onClick = { vm.searchGithub("api_key") },
                        modifier = Modifier.weight(1f), enabled = state.token.isNotBlank())
                    NexusButton("secret", onClick = { vm.searchGithub("secret") },
                        modifier = Modifier.weight(1f), enabled = state.token.isNotBlank())
                }

                state.githubResult?.let { res ->
                    Spacer(modifier = Modifier.height(10.dp))
                    res.error?.let { Text(it, color = AlertRed) }
                    if (res.error == null) {
                        StatusChip("${res.totalCount} total matches",
                            color = if (res.totalCount > 0) AlertRed else SoftGreen)
                        res.hits.forEach { hit ->
                            Column(modifier = Modifier.fillMaxWidth()
                                .clickable { uriHandler.openUri(hit.htmlUrl) }
                                .padding(vertical = 5.dp)) {
                                Text(hit.repo, color = NeonGreen, fontWeight = FontWeight.SemiBold)
                                Text(hit.path, color = TerminalGray, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun DorkRow(d: Dork, onOpen: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(MatrixBlack, RoundedCornerShape(10.dp))
            .border(1.dp, BorderGreen, RoundedCornerShape(10.dp))
            .clickable(onClick = onOpen)
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(d.label, color = GhostWhite, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Icon(Icons.Default.OpenInNew, contentDescription = "Open", tint = NeonGreen,
                modifier = Modifier.height(16.dp))
        }
        Spacer(modifier = Modifier.height(3.dp))
        SelectionContainer {
            Text(d.query, color = MuteGreen, style = MaterialTheme.typography.bodySmall)
        }
    }
}
