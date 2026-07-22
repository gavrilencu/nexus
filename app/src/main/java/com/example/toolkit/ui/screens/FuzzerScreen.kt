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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.toolkit.data.fuzzer.FuzzHit
import com.example.toolkit.ui.components.NexusButton
import com.example.toolkit.ui.components.NexusPanel
import com.example.toolkit.ui.components.NexusTextField
import com.example.toolkit.ui.components.ScreenHeader
import com.example.toolkit.ui.components.StatusChip
import com.example.toolkit.ui.components.UrlLink
import com.example.toolkit.ui.fuzzer.FuzzerViewModel
import com.example.toolkit.ui.theme.AlertAmber
import com.example.toolkit.ui.theme.AlertRed
import com.example.toolkit.ui.theme.GhostWhite
import com.example.toolkit.ui.theme.MuteGreen
import com.example.toolkit.ui.theme.NeonGreen
import com.example.toolkit.ui.theme.SoftGreen
import com.example.toolkit.ui.theme.TerminalGray
import com.example.toolkit.ui.theme.VoidBlack

@Composable
fun FuzzerScreen(vm: FuzzerViewModel = viewModel()) {
    val state by vm.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VoidBlack)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        ScreenHeader("Fuzzer", "ffuf-style · FUZZ keyword · status/size filters")
        Spacer(modifier = Modifier.height(16.dp))
        NexusTextField(state.url, vm::onUrl, "https://target.com/FUZZ or ?id=FUZZ")
        Spacer(modifier = Modifier.height(10.dp))
        NexusTextField(state.customWords, vm::onCustomWords, "Custom wordlist (space/comma) — empty = built-in",
            singleLine = false)
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NexusTextField(state.hideStatus, vm::onHideStatus, "Hide status", modifier = Modifier.weight(1f))
            NexusTextField(state.matchStatus, vm::onMatchStatus, "Match status", modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(12.dp))
        if (state.loading) {
            NexusButton("Stop", onClick = vm::stop)
        } else {
            NexusButton("Start Fuzzing", onClick = vm::start, enabled = state.url.isNotBlank())
        }

        state.error?.let {
            Spacer(modifier = Modifier.height(12.dp))
            Text(it, color = AlertRed)
        }

        if (state.total > 0) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatusChip("${state.scanned}/${state.total}", color = NeonGreen)
                StatusChip("${state.hits.size} hits", color = if (state.hits.isNotEmpty()) SoftGreen else MuteGreen)
                StatusChip(state.status, color = MuteGreen)
            }
        }

        state.hits.forEach { hit ->
            Spacer(modifier = Modifier.height(8.dp))
            HitCard(hit)
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun HitCard(h: FuzzHit) {
    val color = when (h.status) {
        in 200..299 -> SoftGreen
        in 300..399 -> NeonGreen
        401, 403 -> AlertAmber
        in 500..599 -> AlertRed
        else -> MuteGreen
    }
    NexusPanel {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusChip(h.status.toString(), color = color)
            Text(h.word, color = GhostWhite, fontWeight = FontWeight.SemiBold)
        }
        Spacer(modifier = Modifier.height(4.dp))
        UrlLink(url = h.url, showHint = true)
        Spacer(modifier = Modifier.height(2.dp))
        Text("size=${h.size}  words=${h.words}  lines=${h.lines}", color = MuteGreen,
            style = MaterialTheme.typography.bodySmall)
    }
}
