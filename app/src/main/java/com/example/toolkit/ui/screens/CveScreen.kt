package com.example.toolkit.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.toolkit.ui.components.NexusButton
import com.example.toolkit.ui.components.NexusPanel
import com.example.toolkit.ui.components.NexusTextField
import com.example.toolkit.ui.components.ScreenHeader
import com.example.toolkit.ui.components.StatusChip
import com.example.toolkit.ui.cve.CveViewModel
import com.example.toolkit.ui.theme.AlertAmber
import com.example.toolkit.ui.theme.AlertRed
import com.example.toolkit.ui.theme.BorderGreen
import com.example.toolkit.ui.theme.GhostWhite
import com.example.toolkit.ui.theme.MuteGreen
import com.example.toolkit.ui.theme.NeonGreen
import com.example.toolkit.ui.theme.PanelGreen
import com.example.toolkit.ui.theme.VoidBlack

@Composable
fun CveScreen(vm: CveViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val result = state.result
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VoidBlack)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        ScreenHeader(
            title = "CVE Lookup",
            subtitle = "Search NVD by CVE-ID or keyword (Log4j, openssl…)"
        )
        Spacer(modifier = Modifier.height(16.dp))
        NexusTextField(
            state.query, vm::onQuery, "CVE-2021-44228 or keyword",
            imeAction = ImeAction.Search, onDone = vm::search
        )
        Spacer(modifier = Modifier.height(12.dp))
        NexusButton("Search NVD", onClick = vm::search, loading = state.loading, enabled = state.query.isNotBlank())

        result?.error?.let {
            Spacer(modifier = Modifier.height(12.dp))
            Text(it, color = AlertRed)
        }

        result?.takeIf { it.error == null }?.let { r ->
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip("total ${r.total}")
                StatusChip("${r.items.size} shown")
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (r.items.isEmpty()) {
                Text("No vulnerabilities found", color = MuteGreen)
            } else {
                r.items.forEach { item ->
                    val sevColor = when (item.severity?.uppercase()) {
                        "CRITICAL" -> AlertRed
                        "HIGH" -> AlertAmber
                        "MEDIUM" -> NeonGreen
                        else -> MuteGreen
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .border(1.dp, BorderGreen, RoundedCornerShape(2.dp))
                            .background(PanelGreen)
                            .clickable { uriHandler.openUri(item.url) }
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(item.id, color = NeonGreen, fontWeight = FontWeight.Bold)
                            Text(
                                text = buildString {
                                    item.severity?.let { append(it) }
                                    item.score?.let {
                                        if (isNotEmpty()) append(" ")
                                        append("%.1f".format(it))
                                    }
                                    if (isEmpty()) append("N/A")
                                },
                                color = sevColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(item.description, color = GhostWhite)
                        item.published?.let {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(it.take(10), color = MuteGreen)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Open NVD ›", color = MuteGreen)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}
