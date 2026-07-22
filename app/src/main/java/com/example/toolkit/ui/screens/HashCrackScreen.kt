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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.toolkit.ui.components.KeyValueRow
import com.example.toolkit.ui.components.NexusButton
import com.example.toolkit.ui.components.NexusPanel
import com.example.toolkit.ui.components.NexusTextField
import com.example.toolkit.ui.components.ScreenHeader
import com.example.toolkit.ui.components.StatusChip
import com.example.toolkit.ui.components.WarningBanner
import com.example.toolkit.ui.hashcrack.HashCrackViewModel
import com.example.toolkit.ui.theme.AlertAmber
import com.example.toolkit.ui.theme.AlertRed
import com.example.toolkit.ui.theme.DimGreen
import com.example.toolkit.ui.theme.GhostWhite
import com.example.toolkit.ui.theme.MuteGreen
import com.example.toolkit.ui.theme.SoftGreen
import com.example.toolkit.ui.theme.VoidBlack

@Composable
fun HashCrackScreen(vm: HashCrackViewModel = viewModel()) {
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
            title = "Hash Cracker",
            subtitle = "Identificare tip hash + dictionary attack local"
        )
        Spacer(modifier = Modifier.height(16.dp))
        WarningBanner("Rulează local. Crăpuiește doar hash-uri pe care ai dreptul să le testezi.")
        Spacer(modifier = Modifier.height(12.dp))

        NexusTextField(state.hash, vm::onHash, "hash (MD5 / SHA-1 / SHA-256 …)", singleLine = false)
        Spacer(modifier = Modifier.height(10.dp))
        NexusTextField(state.extraWords, vm::onExtraWords, "cuvinte extra (opțional, separate prin virgulă/linie)", singleLine = false)
        Spacer(modifier = Modifier.height(12.dp))
        NexusButton("Identify & crack", onClick = vm::crack, loading = state.loading, enabled = state.hash.isNotBlank())

        result?.error?.let {
            Spacer(modifier = Modifier.height(12.dp))
            Text(it, color = AlertRed)
        }

        result?.takeIf { it.error == null }?.let { r ->
            Spacer(modifier = Modifier.height(16.dp))
            if (r.guesses.isNotEmpty()) {
                NexusPanel(title = "IDENTIFICATION") {
                    r.guesses.forEach { g ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatusChip(g.confidence, color = DimGreen)
                            Text(g.algo, color = GhostWhite, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (r.cracked != null) {
                NexusPanel(title = "CRACKED") {
                    StatusChip("FOUND", color = SoftGreen)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(r.cracked, color = SoftGreen, fontWeight = FontWeight.ExtraBold)
                    Spacer(modifier = Modifier.height(6.dp))
                    KeyValueRow("Algoritm", r.crackedAlgo ?: "—")
                    KeyValueRow("Candidați testați", r.tried.toString())
                }
            } else {
                NexusPanel(title = "RESULT") {
                    StatusChip("NOT CRACKED", color = AlertAmber)
                    r.note?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(it, color = MuteGreen, modifier = Modifier.fillMaxWidth())
                    }
                    if (r.tried > 0) {
                        Spacer(modifier = Modifier.height(6.dp))
                        KeyValueRow("Candidați testați", r.tried.toString())
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}
