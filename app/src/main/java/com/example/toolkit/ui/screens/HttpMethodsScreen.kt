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
import com.example.toolkit.data.httpmethods.MethodResult
import com.example.toolkit.ui.components.NexusButton
import com.example.toolkit.ui.components.NexusPanel
import com.example.toolkit.ui.components.NexusTextField
import com.example.toolkit.ui.components.ScreenHeader
import com.example.toolkit.ui.components.StatusChip
import com.example.toolkit.ui.httpmethods.HttpMethodsViewModel
import com.example.toolkit.ui.theme.AlertRed
import com.example.toolkit.ui.theme.DimGreen
import com.example.toolkit.ui.theme.GhostWhite
import com.example.toolkit.ui.theme.MuteGreen
import com.example.toolkit.ui.theme.SoftGreen
import com.example.toolkit.ui.theme.VoidBlack

@Composable
fun HttpMethodsScreen(vm: HttpMethodsViewModel = viewModel()) {
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
            title = "HTTP Methods",
            subtitle = "Verbe permise · verbe periculoase (PUT/DELETE/TRACE)"
        )
        Spacer(modifier = Modifier.height(16.dp))
        NexusTextField(
            state.url, vm::onUrl, "https://target.com/path",
            imeAction = ImeAction.Go, onDone = vm::test
        )
        Spacer(modifier = Modifier.height(12.dp))
        NexusButton("Test methods", onClick = vm::test, loading = state.loading, enabled = state.url.isNotBlank())

        result?.error?.let {
            Spacer(modifier = Modifier.height(12.dp))
            Text(it, color = AlertRed)
        }

        result?.takeIf { it.error == null }?.let { r ->
            Spacer(modifier = Modifier.height(16.dp))
            r.allowHeader?.let {
                NexusPanel(title = "ALLOW HEADER") { Text(it, color = SoftGreen) }
                Spacer(modifier = Modifier.height(12.dp))
            }
            NexusPanel(title = "METHODS") {
                r.results.forEach { m ->
                    MethodRow(m)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun MethodRow(m: MethodResult) {
    val color = when {
        m.dangerous -> AlertRed
        m.allowed -> SoftGreen
        else -> MuteGreen
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusChip(m.method, color = color)
            StatusChip(m.status?.toString() ?: "—", color = DimGreen)
            if (m.dangerous) StatusChip("DANGER", color = AlertRed)
        }
        Text(m.note, color = if (m.dangerous) AlertRed else GhostWhite, modifier = Modifier.padding(top = 3.dp))
    }
}
