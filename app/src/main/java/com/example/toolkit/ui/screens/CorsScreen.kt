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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.toolkit.data.cors.CorsTest
import com.example.toolkit.ui.components.NexusButton
import com.example.toolkit.ui.components.NexusPanel
import com.example.toolkit.ui.components.NexusTextField
import com.example.toolkit.ui.components.ScreenHeader
import com.example.toolkit.ui.components.StatusChip
import com.example.toolkit.ui.components.WarningBanner
import com.example.toolkit.ui.cors.CorsViewModel
import com.example.toolkit.ui.theme.AlertAmber
import com.example.toolkit.ui.theme.AlertRed
import com.example.toolkit.ui.theme.GhostWhite
import com.example.toolkit.ui.theme.MuteGreen
import com.example.toolkit.ui.theme.SoftGreen
import com.example.toolkit.ui.theme.TerminalGray
import com.example.toolkit.ui.theme.VoidBlack

@Composable
fun CorsScreen(vm: CorsViewModel = viewModel()) {
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
            title = "CORS Scanner",
            subtitle = "Reflectare origine · null · bypass prefix/sufix"
        )
        Spacer(modifier = Modifier.height(16.dp))
        WarningBanner("Testează doar aplicații pe care ai autorizație să le verifici.")
        Spacer(modifier = Modifier.height(12.dp))
        NexusTextField(
            state.url, vm::onUrl, "https://api.target.com/endpoint",
            imeAction = ImeAction.Go, onDone = vm::scan
        )
        Spacer(modifier = Modifier.height(12.dp))
        NexusButton("Scan CORS", onClick = vm::scan, loading = state.loading, enabled = state.url.isNotBlank())

        result?.error?.let {
            Spacer(modifier = Modifier.height(12.dp))
            Text(it, color = AlertRed)
        }

        result?.takeIf { it.error == null }?.let { r ->
            val vulnCount = r.tests.count { it.vulnerable }
            Spacer(modifier = Modifier.height(16.dp))
            StatusChip(
                if (vulnCount > 0) "$vulnCount finding(s)" else "No misconfig",
                color = if (vulnCount > 0) AlertRed else SoftGreen
            )
            Spacer(modifier = Modifier.height(12.dp))
            r.tests.forEach { test ->
                CorsTestCard(test)
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun CorsTestCard(test: CorsTest) {
    val color = severityColor(test.severity)
    NexusPanel {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusChip(test.severity, color = color)
            Text(test.name, color = GhostWhite, fontWeight = FontWeight.SemiBold)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text("Origin: ${test.origin}", color = TerminalGray)
        Text("ACAO: ${test.allowOrigin ?: "—"}", color = MuteGreen)
        Text("Allow-Credentials: ${test.allowCredentials}", color = MuteGreen)
        Spacer(modifier = Modifier.height(6.dp))
        Text(test.detail, color = if (test.vulnerable) color else GhostWhite, modifier = Modifier.fillMaxWidth())
    }
}

private fun severityColor(severity: String): Color = when (severity) {
    "HIGH" -> AlertRed
    "MEDIUM" -> AlertAmber
    "INFO" -> MuteGreen
    else -> SoftGreen
}
