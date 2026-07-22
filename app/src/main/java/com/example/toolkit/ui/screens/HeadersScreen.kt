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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.toolkit.data.headers.HeaderCheck
import com.example.toolkit.ui.components.NexusButton
import com.example.toolkit.ui.components.NexusPanel
import com.example.toolkit.ui.components.NexusTextField
import com.example.toolkit.ui.components.ScreenHeader
import com.example.toolkit.ui.components.StatusChip
import com.example.toolkit.ui.headers.HeadersViewModel
import com.example.toolkit.ui.theme.AlertAmber
import com.example.toolkit.ui.theme.AlertRed
import com.example.toolkit.ui.theme.GhostWhite
import com.example.toolkit.ui.theme.MuteGreen
import com.example.toolkit.ui.theme.NeonGreen
import com.example.toolkit.ui.theme.SoftGreen
import com.example.toolkit.ui.theme.TerminalGray
import com.example.toolkit.ui.theme.VoidBlack

@Composable
fun HeadersScreen(vm: HeadersViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val r = state.result

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VoidBlack)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        ScreenHeader("Security Headers", "CSP · HSTS · XFO · nosniff · Referrer · A–F")
        Spacer(modifier = Modifier.height(16.dp))
        NexusTextField(state.url, vm::onUrl, "https://target.com",
            imeAction = ImeAction.Go, onDone = vm::grade)
        Spacer(modifier = Modifier.height(12.dp))
        NexusButton("Grade Headers", onClick = vm::grade, loading = state.loading, enabled = state.url.isNotBlank())

        r?.error?.let {
            Spacer(modifier = Modifier.height(12.dp))
            Text(it, color = AlertRed)
        }

        r?.takeIf { it.error == null }?.let { res ->
            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GradeBox(res.grade)
                Column {
                    Text("Score: ${res.score}/100", color = GhostWhite, fontWeight = FontWeight.Bold)
                    Text("HTTP ${res.status}", color = MuteGreen)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            NexusPanel(title = "Security headers") {
                res.checks.forEach { HeaderRow(it) }
            }
            if (res.infoLeaks.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                NexusPanel(title = "Information disclosure") {
                    res.infoLeaks.forEach { HeaderRow(it) }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun GradeBox(grade: String) {
    val color = when (grade) {
        "A" -> SoftGreen; "B" -> NeonGreen; "C" -> AlertAmber
        "D", "E" -> AlertAmber; else -> AlertRed
    }
    Box(
        modifier = Modifier.size(56.dp).clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center
    ) {
        Text(grade, color = color, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun HeaderRow(c: HeaderCheck) {
    val color = when (c.severity) {
        "GOOD" -> SoftGreen; "WARN" -> AlertAmber; "BAD" -> AlertRed; else -> MuteGreen
    }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusChip(c.severity, color = color)
            Text(c.name, color = GhostWhite, fontWeight = FontWeight.SemiBold)
        }
        c.value?.let {
            Spacer(modifier = Modifier.height(3.dp))
            Text(it, color = TerminalGray, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(c.note, color = MuteGreen, style = MaterialTheme.typography.bodySmall)
    }
}
