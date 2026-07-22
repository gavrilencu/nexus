package com.example.toolkit.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.toolkit.ui.components.KeyValueRow
import com.example.toolkit.ui.components.NexusButton
import com.example.toolkit.ui.components.NexusPanel
import com.example.toolkit.ui.components.NexusTextField
import com.example.toolkit.ui.components.ScreenHeader
import com.example.toolkit.ui.components.StatusChip
import com.example.toolkit.ui.components.UrlLink
import com.example.toolkit.ui.takeover.TakeoverViewModel
import com.example.toolkit.ui.theme.AlertAmber
import com.example.toolkit.ui.theme.AlertRed
import com.example.toolkit.ui.theme.GhostWhite
import com.example.toolkit.ui.theme.MuteGreen
import com.example.toolkit.ui.theme.SoftGreen
import com.example.toolkit.ui.theme.VoidBlack

@Composable
fun TakeoverScreen(vm: TakeoverViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val r = state.result

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VoidBlack)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        ScreenHeader("Subdomain Takeover", "Dangling CNAME · provider fingerprints")
        Spacer(modifier = Modifier.height(16.dp))
        NexusTextField(state.host, vm::onHost, "sub.target.com",
            imeAction = ImeAction.Go, onDone = vm::check)
        Spacer(modifier = Modifier.height(12.dp))
        NexusButton("Check", onClick = vm::check, loading = state.loading, enabled = state.host.isNotBlank())

        r?.error?.let {
            Spacer(modifier = Modifier.height(12.dp))
            Text(it, color = AlertRed)
        }

        r?.takeIf { it.error == null }?.let { res ->
            Spacer(modifier = Modifier.height(16.dp))
            val color = when {
                res.vulnerable -> AlertRed
                res.service != null -> AlertAmber
                else -> SoftGreen
            }
            StatusChip(
                when {
                    res.vulnerable -> "VULNERABLE"
                    res.service != null -> "POTENTIAL — verify"
                    else -> "Not vulnerable"
                }, color = color
            )
            Spacer(modifier = Modifier.height(12.dp))
            NexusPanel(title = "Result") {
                UrlLink(url = res.host, label = "Host", showHint = false)
                if (res.cname.isNotEmpty()) {
                    res.cname.forEach { UrlLink(url = it, label = "CNAME", showHint = false) }
                } else {
                    KeyValueRow("CNAME", "—")
                }
                KeyValueRow("Provider", res.service ?: "—")
                KeyValueRow("Fingerprint", res.fingerprint ?: "—")
                Spacer(modifier = Modifier.height(8.dp))
                Text(res.detail, color = if (res.vulnerable) AlertRed else GhostWhite)
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}
