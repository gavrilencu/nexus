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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.toolkit.ui.components.KeyValueRow
import com.example.toolkit.ui.components.NexusButton
import com.example.toolkit.ui.components.NexusPanel
import com.example.toolkit.ui.components.NexusTextField
import com.example.toolkit.ui.components.ScreenHeader
import com.example.toolkit.ui.components.StatusChip
import com.example.toolkit.ui.fingerprint.FingerprintViewModel
import com.example.toolkit.ui.theme.AlertRed
import com.example.toolkit.ui.theme.DimGreen
import com.example.toolkit.ui.theme.GhostWhite
import com.example.toolkit.ui.theme.MuteGreen
import com.example.toolkit.ui.theme.NeonGreen
import com.example.toolkit.ui.theme.SoftGreen
import com.example.toolkit.ui.theme.TerminalGray
import com.example.toolkit.ui.theme.VoidBlack

@Composable
fun FingerprintScreen(vm: FingerprintViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val result = state.result

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VoidBlack)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        ScreenHeader(
            title = "Web Fingerprint",
            subtitle = "Server · limbaj · framework · CMS · CDN/WAF · JS"
        )
        Spacer(modifier = Modifier.height(16.dp))
        NexusTextField(
            state.url, vm::onUrl, "https://target.com",
            imeAction = ImeAction.Go, onDone = vm::analyze
        )
        Spacer(modifier = Modifier.height(12.dp))
        NexusButton("Fingerprint", onClick = vm::analyze, loading = state.loading, enabled = state.url.isNotBlank())

        result?.error?.let {
            Spacer(modifier = Modifier.height(12.dp))
            Text(it, color = AlertRed)
        }

        result?.takeIf { it.error == null }?.let { r ->
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                r.status?.let { StatusChip("HTTP $it", color = if (it in 200..399) SoftGreen else DimGreen) }
                StatusChip("${r.technologies.size} tech", color = NeonGreen)
            }
            r.title?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(it, color = GhostWhite, fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(12.dp))
            NexusPanel(title = "SERVER") {
                KeyValueRow("URL final", r.finalUrl ?: r.url)
                KeyValueRow("Server", r.server ?: "—")
                KeyValueRow("X-Powered-By", r.poweredBy ?: "—")
            }

            if (r.technologies.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                NexusPanel(title = "TECHNOLOGIES") {
                    r.technologies.forEach { t ->
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatusChip(t.category, color = DimGreen)
                                Text(t.name, color = GhostWhite, fontWeight = FontWeight.SemiBold)
                            }
                            Text(t.evidence, color = MuteGreen, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                }
            }

            if (r.cookies.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                NexusPanel(title = "COOKIES") {
                    r.cookies.forEach { Text(it, color = TerminalGray, modifier = Modifier.padding(vertical = 2.dp)) }
                }
            }

            if (r.securityHeaders.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                NexusPanel(title = "SECURITY HEADERS") {
                    r.securityHeaders.forEach { (k, v) -> KeyValueRow(k, v) }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}
