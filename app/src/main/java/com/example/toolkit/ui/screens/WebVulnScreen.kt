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
import com.example.toolkit.data.webvuln.VulnFinding
import com.example.toolkit.ui.components.NexusButton
import com.example.toolkit.ui.components.NexusPanel
import com.example.toolkit.ui.components.NexusTextField
import com.example.toolkit.ui.components.ScreenHeader
import com.example.toolkit.ui.components.StatusChip
import com.example.toolkit.ui.components.WarningBanner
import com.example.toolkit.ui.theme.AlertAmber
import com.example.toolkit.ui.theme.AlertRed
import com.example.toolkit.ui.theme.GhostWhite
import com.example.toolkit.ui.theme.MuteGreen
import com.example.toolkit.ui.theme.SoftGreen
import com.example.toolkit.ui.theme.TerminalGray
import com.example.toolkit.ui.theme.VoidBlack
import com.example.toolkit.ui.webvuln.WebVulnViewModel

@Composable
fun WebVulnScreen(vm: WebVulnViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val r = state.result

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VoidBlack)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        ScreenHeader("Web Vuln Scanner", "XSS · SQLi · redirect · traversal · SSRF")
        Spacer(modifier = Modifier.height(16.dp))
        WarningBanner("Injectează payload-uri active. Testează DOAR ținte pe care ai autorizație.")
        Spacer(modifier = Modifier.height(12.dp))
        NexusTextField(state.url, vm::onUrl, "https://target.com/page?id=1&q=x",
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
                if (res.findings.isNotEmpty()) "${res.findings.size} finding(s)" else "No findings",
                color = if (res.findings.isNotEmpty()) AlertRed else SoftGreen
            )
            if (res.testedParams.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Tested params: ${res.testedParams.joinToString(", ")}", color = TerminalGray)
            }
            res.notes.forEach {
                Spacer(modifier = Modifier.height(8.dp))
                Text(it, color = MuteGreen)
            }
            Spacer(modifier = Modifier.height(12.dp))
            res.findings.forEach { FindingCard(it); Spacer(modifier = Modifier.height(10.dp)) }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun FindingCard(f: VulnFinding) {
    val color = when (f.severity) {
        "HIGH" -> AlertRed; "MEDIUM" -> AlertAmber; "LOW" -> AlertAmber; else -> MuteGreen
    }
    NexusPanel {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusChip(f.severity, color = color)
            Text(f.type, color = GhostWhite, fontWeight = FontWeight.SemiBold)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text("Parameter: ${f.param}", color = MuteGreen)
        Text("Payload: ${f.payload}", color = TerminalGray, style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.height(4.dp))
        Text(f.evidence, color = color, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(4.dp))
        SelectionContainer {
            Text(f.requestUrl, color = TerminalGray, style = MaterialTheme.typography.bodySmall)
        }
    }
}
