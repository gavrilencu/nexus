package com.example.toolkit.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.toolkit.data.tls.TlsCert
import com.example.toolkit.ui.components.KeyValueRow
import com.example.toolkit.ui.components.NexusButton
import com.example.toolkit.ui.components.NexusPanel
import com.example.toolkit.ui.components.NexusTextField
import com.example.toolkit.ui.components.ScreenHeader
import com.example.toolkit.ui.components.StatusChip
import com.example.toolkit.ui.theme.AlertAmber
import com.example.toolkit.ui.theme.AlertRed
import com.example.toolkit.ui.theme.GhostWhite
import com.example.toolkit.ui.theme.MuteGreen
import com.example.toolkit.ui.theme.NeonGreen
import com.example.toolkit.ui.theme.SoftGreen
import com.example.toolkit.ui.theme.TerminalGray
import com.example.toolkit.ui.theme.VoidBlack
import com.example.toolkit.ui.tls.TlsViewModel

@Composable
fun TlsScreen(vm: TlsViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val r = state.result

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VoidBlack)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        ScreenHeader("TLS/SSL Analyzer", "Protocol versions · ciphers · cert chain · grade")
        Spacer(modifier = Modifier.height(16.dp))
        NexusTextField(state.host, vm::onHost, "example.com or host:443",
            imeAction = ImeAction.Go, onDone = vm::analyze)
        Spacer(modifier = Modifier.height(12.dp))
        NexusButton("Analyze TLS", onClick = vm::analyze, loading = state.loading, enabled = state.host.isNotBlank())

        r?.error?.let {
            Spacer(modifier = Modifier.height(12.dp))
            Text(it, color = AlertRed)
        }

        r?.takeIf { it.error == null }?.let { res ->
            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GradeBadge(res.grade)
                Column {
                    Text("${res.host}:${res.port}", color = GhostWhite, fontWeight = FontWeight.Bold)
                    Text(res.negotiatedProtocol ?: "—", color = MuteGreen)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            NexusPanel(title = "Negotiated") {
                KeyValueRow("Protocol", res.negotiatedProtocol ?: "—")
                KeyValueRow("Cipher", res.negotiatedCipher ?: "—",
                    valueColor = if (res.cipherWeak) AlertRed else GhostWhite)
                KeyValueRow("Hostname match", when (res.hostnameMatches) {
                    true -> "yes"; false -> "NO"; null -> "—" },
                    valueColor = if (res.hostnameMatches == false) AlertRed else GhostWhite)
            }
            Spacer(modifier = Modifier.height(12.dp))

            NexusPanel(title = "Protocol support") {
                res.protocols.forEach { p ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(p.name, color = GhostWhite)
                        val (label, color) = when {
                            p.supported && p.weak -> "enabled (weak)" to AlertRed
                            p.supported -> "enabled" to SoftGreen
                            else -> "disabled" to TerminalGray
                        }
                        StatusChip(label, color = color)
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            NexusPanel(title = "Findings") {
                res.issues.forEach { issue ->
                    val ok = issue.startsWith("No ")
                    Text("• $issue", color = if (ok) SoftGreen else AlertAmber,
                        modifier = Modifier.padding(vertical = 2.dp))
                }
            }

            res.chain.forEachIndexed { i, c ->
                Spacer(modifier = Modifier.height(12.dp))
                CertCard(i, c)
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun GradeBadge(grade: String) {
    val color = when {
        grade.startsWith("A") -> SoftGreen
        grade == "B" -> NeonGreen
        grade == "C" -> AlertAmber
        else -> AlertRed
    }
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center
    ) {
        Text(grade, color = color, fontWeight = FontWeight.ExtraBold,
            style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun CertCard(index: Int, c: TlsCert) {
    NexusPanel(title = if (index == 0) "Certificate · leaf" else "Certificate · chain #$index") {
        KeyValueRow("Subject", c.subject)
        KeyValueRow("Issuer", c.issuer)
        KeyValueRow("Valid from", c.validFrom)
        KeyValueRow("Valid until", c.validUntil, valueColor = if (c.expired) AlertRed else GhostWhite)
        KeyValueRow("Self-signed", if (c.selfSigned) "yes" else "no",
            valueColor = if (c.selfSigned) AlertAmber else GhostWhite)
        KeyValueRow("Signature", c.sigAlg)
        KeyValueRow("Key", c.keyInfo)
        if (c.san.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text("SAN", color = MuteGreen)
            Text(c.san.joinToString(", "), color = TerminalGray)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text("SHA-256", color = MuteGreen)
        Text(c.sha256, color = TerminalGray, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
    }
}
