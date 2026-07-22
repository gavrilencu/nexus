package com.example.toolkit.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.selection.SelectionContainer
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
import com.example.toolkit.data.apkaudit.AuditFinding
import com.example.toolkit.data.apkaudit.DeepLink
import com.example.toolkit.ui.apkaudit.ApkAuditViewModel
import com.example.toolkit.ui.components.ApkSourcePicker
import com.example.toolkit.ui.components.NexusButton
import com.example.toolkit.ui.components.NexusPanel
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

@Composable
fun ApkAuditScreen(vm: ApkAuditViewModel = viewModel()) {
    val state by vm.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VoidBlack)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        ScreenHeader("APK Security Audit", "MobSF-style risk score · manifest hardening")
        Spacer(modifier = Modifier.height(12.dp))

        when {
            state.analyzing -> Center("Auditing package…")
            state.result != null -> ResultView(state.result!!, vm)
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
    }
}

@Composable
private fun ResultView(r: com.example.toolkit.data.apkaudit.AuditResult, vm: ApkAuditViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(r.label, color = GhostWhite, style = MaterialTheme.typography.titleLarge)
            r.packageName?.let { Text(it, color = MuteGreen, style = MaterialTheme.typography.bodySmall) }
        }
        Text("‹ back", color = NeonGreen, modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { vm.clear() }
            .padding(8.dp))
    }
    Spacer(modifier = Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        GradeCircle(r.grade, r.score)
        Column {
            Text("Risk score: ${r.score}/100", color = GhostWhite, fontWeight = FontWeight.Bold)
            Text("${r.findings.count { it.severity == "HIGH" }} high · ${r.findings.count { it.severity == "MEDIUM" }} medium",
                color = MuteGreen)
        }
    }
    Spacer(modifier = Modifier.height(12.dp))
    r.findings.forEach { FindingCard(it); Spacer(modifier = Modifier.height(8.dp)) }

    if (r.deepLinks.isNotEmpty()) {
        Spacer(modifier = Modifier.height(4.dp))
        NexusPanel(title = "Deep links (${r.deepLinks.size})") {
            r.deepLinks.forEach { DeepLinkRow(it) }
        }
    }
    Spacer(modifier = Modifier.height(24.dp))
}

@Composable
private fun GradeCircle(grade: String, score: Int) {
    val color = when (grade) { "A" -> SoftGreen; "B" -> NeonGreen; "C" -> AlertAmber; "D" -> AlertAmber; else -> AlertRed }
    Box(
        modifier = Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).background(color.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center
    ) {
        Text(grade, color = color, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.headlineMedium)
    }
}

@Composable
private fun FindingCard(f: AuditFinding) {
    val color = when (f.severity) {
        "HIGH" -> AlertRed; "MEDIUM" -> AlertAmber; "LOW" -> AlertAmber
        "GOOD" -> SoftGreen; else -> MuteGreen
    }
    NexusPanel {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusChip(f.severity, color = color)
            Text(f.title, color = GhostWhite, fontWeight = FontWeight.SemiBold)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(f.detail, color = GhostWhite, style = MaterialTheme.typography.bodyMedium)
        f.evidence?.let {
            Spacer(modifier = Modifier.height(6.dp))
            SelectionContainer {
                Text(it, color = TerminalGray, fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun DeepLinkRow(d: DeepLink) {
    val uri = buildString {
        append(d.scheme); append("://")
        d.host?.let { append(it) }
        d.path?.let { append(it) }
    }
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(uri, color = NeonGreen, fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        if (d.autoVerify) StatusChip("verified", color = SoftGreen)
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
