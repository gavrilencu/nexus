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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.toolkit.data.dirscan.DirHit
import com.example.toolkit.ui.components.NexusButton
import com.example.toolkit.ui.components.NexusPanel
import com.example.toolkit.ui.components.NexusTextField
import com.example.toolkit.ui.components.ScreenHeader
import com.example.toolkit.ui.components.StatusChip
import com.example.toolkit.ui.components.WarningBanner
import com.example.toolkit.ui.dirscan.DirScanViewModel
import com.example.toolkit.ui.theme.AlertAmber
import com.example.toolkit.ui.theme.AlertRed
import com.example.toolkit.ui.theme.BorderGreen
import com.example.toolkit.ui.theme.DimGreen
import com.example.toolkit.ui.theme.GhostWhite
import com.example.toolkit.ui.theme.MuteGreen
import com.example.toolkit.ui.theme.NeonGreen
import com.example.toolkit.ui.theme.SoftGreen
import com.example.toolkit.ui.theme.TerminalGray
import com.example.toolkit.ui.theme.VoidBlack

@Composable
fun DirScanScreen(vm: DirScanViewModel = viewModel()) {
    val state by vm.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VoidBlack)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        ScreenHeader(
            title = "Content Discovery",
            subtitle = "Brute-force de directoare & fișiere — status live"
        )
        Spacer(modifier = Modifier.height(16.dp))
        WarningBanner("Scanează doar site-uri pe care ai autorizație scrisă să le testezi.")
        Spacer(modifier = Modifier.height(12.dp))

        NexusTextField(
            state.url, vm::onUrl, "https://target.com",
            imeAction = ImeAction.Go, onDone = vm::start, enabled = !state.loading
        )
        Spacer(modifier = Modifier.height(12.dp))
        if (state.loading) {
            NexusButton("Stop", onClick = vm::stop)
        } else {
            NexusButton("Start scan", onClick = vm::start, enabled = state.url.isNotBlank())
        }

        if (state.total > 0) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip("${state.scanned}/${state.total}")
                StatusChip("${state.hits.size} hits", color = SoftGreen)
                StatusChip(state.status, color = DimGreen)
            }
            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { if (state.total == 0) 0f else state.scanned.toFloat() / state.total },
                modifier = Modifier.fillMaxWidth(),
                color = NeonGreen,
                trackColor = BorderGreen
            )
        }

        state.notice?.let {
            Spacer(modifier = Modifier.height(12.dp))
            Text(it, color = AlertAmber)
        }
        state.error?.let {
            Spacer(modifier = Modifier.height(12.dp))
            Text(it, color = AlertRed)
        }

        if (state.hits.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            NexusPanel(title = "FOUND (${state.hits.size})") {
                state.hits.forEach { hit ->
                    HitRow(hit)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun HitRow(hit: DirHit) {
    val color = when (hit.status) {
        in 200..299 -> SoftGreen
        in 300..399 -> DimGreen
        401, 403 -> AlertAmber
        in 500..599 -> AlertRed
        else -> GhostWhite
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusChip(hit.status.toString(), color = color)
            Text(hit.path, color = GhostWhite, fontWeight = FontWeight.SemiBold)
        }
        Text("${hit.size} bytes", color = MuteGreen, modifier = Modifier.padding(top = 2.dp))
        hit.location?.let { Text("→ $it", color = TerminalGray) }
    }
}
