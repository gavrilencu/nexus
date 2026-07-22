package com.example.toolkit.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.toolkit.ui.components.KeyValueRow
import com.example.toolkit.ui.components.NexusButton
import com.example.toolkit.ui.components.NexusPanel
import com.example.toolkit.ui.components.NexusTextField
import com.example.toolkit.ui.components.ScreenHeader
import com.example.toolkit.ui.components.StatusChip
import com.example.toolkit.ui.jwt.JwtViewModel
import com.example.toolkit.ui.theme.AlertAmber
import com.example.toolkit.ui.theme.AlertRed
import com.example.toolkit.ui.theme.GhostWhite
import com.example.toolkit.ui.theme.MuteGreen
import com.example.toolkit.ui.theme.NeonGreen
import com.example.toolkit.ui.theme.VoidBlack
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun JwtScreen(vm: JwtViewModel = viewModel()) {
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
            title = "JWT Lab",
            subtitle = "Decode header + payload locally (no signature verify)"
        )
        Spacer(modifier = Modifier.height(16.dp))
        NexusTextField(
            value = state.token,
            onValueChange = vm::onToken,
            label = "paste JWT (header.payload.signature)",
            singleLine = false
        )
        Spacer(modifier = Modifier.height(12.dp))
        NexusButton("Decode", onClick = vm::decode, enabled = state.token.isNotBlank())

        result?.error?.let {
            Spacer(modifier = Modifier.height(12.dp))
            Text(it, color = AlertRed)
        }

        result?.takeIf { it.error == null }?.let { r ->
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                r.algorithm?.let { StatusChip("alg=$it") }
                when (r.expired) {
                    true -> StatusChip("EXPIRED")
                    false -> StatusChip("VALID TIME")
                    null -> StatusChip("NO EXP")
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            NexusPanel(title = "CLAIMS") {
                KeyValueRow("sub", r.subject.orEmpty())
                KeyValueRow("iss", r.issuer.orEmpty())
                KeyValueRow("aud", r.audience.orEmpty())
                KeyValueRow("iat", r.issuedAt?.let { formatEpoch(it) }.orEmpty())
                KeyValueRow(
                    "exp",
                    r.expiresAt?.let { formatEpoch(it) }.orEmpty(),
                    valueColor = if (r.expired == true) AlertAmber else GhostWhite
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            NexusPanel(title = "HEADER") {
                Text(r.headerJson, color = NeonGreen)
            }
            Spacer(modifier = Modifier.height(12.dp))
            NexusPanel(title = "PAYLOAD") {
                Text(r.payloadJson, color = GhostWhite)
            }
            if (r.claims.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                NexusPanel(title = "ALL CLAIMS") {
                    r.claims.forEach { (k, v) ->
                        KeyValueRow(k, v)
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Signature is NOT verified — decode only.", color = MuteGreen)
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

private fun formatEpoch(epochSec: Long): String {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US)
        sdf.format(Date(epochSec * 1000))
    } catch (_: Exception) {
        epochSec.toString()
    }
}
