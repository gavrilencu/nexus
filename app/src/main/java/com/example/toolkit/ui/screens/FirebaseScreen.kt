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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.toolkit.data.firebase.FirebaseCheck
import com.example.toolkit.ui.components.ApkSourcePicker
import com.example.toolkit.ui.components.NexusButton
import com.example.toolkit.ui.components.NexusPanel
import com.example.toolkit.ui.components.NexusTextField
import com.example.toolkit.ui.components.ScreenHeader
import com.example.toolkit.ui.components.StatusChip
import com.example.toolkit.ui.firebase.FirebaseViewModel
import com.example.toolkit.ui.theme.AlertRed
import com.example.toolkit.ui.theme.GhostWhite
import com.example.toolkit.ui.theme.MuteGreen
import com.example.toolkit.ui.theme.NeonGreen
import com.example.toolkit.ui.theme.SoftGreen
import com.example.toolkit.ui.theme.TerminalGray
import com.example.toolkit.ui.theme.VoidBlack

@Composable
fun FirebaseScreen(vm: FirebaseViewModel = viewModel()) {
    val state by vm.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VoidBlack)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        ScreenHeader("Firebase Checker", "Open RTDB read test · /.json probe")
        Spacer(modifier = Modifier.height(16.dp))

        NexusPanel(title = "Manual database") {
            NexusTextField(state.manual, vm::onManual, "project-id or project.firebaseio.com")
            Spacer(modifier = Modifier.height(10.dp))
            NexusButton("Check", onClick = vm::checkManual, loading = state.loading,
                enabled = state.manual.isNotBlank())
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("…or extract Firebase URLs from an APK:", color = MuteGreen)
        Spacer(modifier = Modifier.height(10.dp))
        ApkSourcePicker(
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

        state.result?.let { res ->
            Spacer(modifier = Modifier.height(16.dp))
            Row {
                Text("Result: ${res.source}", color = GhostWhite, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f))
                Text("‹ clear", color = NeonGreen, modifier = Modifier
                    .clip(RoundedCornerShape(8.dp)).clickable { vm.clear() }.padding(8.dp))
            }
            Spacer(modifier = Modifier.height(6.dp))
            StatusChip("${res.databases.size} database(s)", color = NeonGreen)
            Spacer(modifier = Modifier.height(10.dp))
            res.checks.forEach { CheckCard(it); Spacer(modifier = Modifier.height(8.dp)) }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun CheckCard(c: FirebaseCheck) {
    val color = when (c.severity) {
        "HIGH" -> AlertRed; "OK" -> SoftGreen; "ERROR" -> AlertRed; else -> MuteGreen
    }
    NexusPanel {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusChip(if (c.readable) "OPEN" else c.severity, color = color)
            StatusChip("HTTP ${c.status}", color = MuteGreen)
        }
        Spacer(modifier = Modifier.height(6.dp))
        SelectionContainer {
            Text(c.url, color = GhostWhite, fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(c.detail, color = if (c.readable) AlertRed else GhostWhite)
        c.sample?.let {
            Spacer(modifier = Modifier.height(6.dp))
            SelectionContainer {
                Text(it, color = TerminalGray, fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
