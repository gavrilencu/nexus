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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.toolkit.data.graphql.GqlField
import com.example.toolkit.ui.components.NexusButton
import com.example.toolkit.ui.components.NexusPanel
import com.example.toolkit.ui.components.NexusTextField
import com.example.toolkit.ui.components.ScreenHeader
import com.example.toolkit.ui.components.StatusChip
import com.example.toolkit.ui.graphql.GraphqlViewModel
import com.example.toolkit.ui.theme.AlertAmber
import com.example.toolkit.ui.theme.AlertRed
import com.example.toolkit.ui.theme.GhostWhite
import com.example.toolkit.ui.theme.MuteGreen
import com.example.toolkit.ui.theme.NeonGreen
import com.example.toolkit.ui.theme.SoftGreen
import com.example.toolkit.ui.theme.TerminalGray
import com.example.toolkit.ui.theme.VoidBlack

@Composable
fun GraphqlScreen(vm: GraphqlViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val r = state.result

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VoidBlack)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        ScreenHeader("GraphQL Inspector", "Detect · introspect · map schema")
        Spacer(modifier = Modifier.height(16.dp))
        NexusTextField(state.url, vm::onUrl, "https://target.com or .../graphql",
            imeAction = ImeAction.Go, onDone = vm::inspect)
        Spacer(modifier = Modifier.height(12.dp))
        NexusButton("Inspect", onClick = vm::inspect, loading = state.loading, enabled = state.url.isNotBlank())

        r?.error?.let {
            Spacer(modifier = Modifier.height(12.dp))
            Text(it, color = AlertRed)
        }

        r?.takeIf { it.error == null }?.let { res ->
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatusChip(if (res.reachable) "reachable" else "unreachable",
                    color = if (res.reachable) SoftGreen else AlertRed)
                StatusChip(if (res.introspectionEnabled) "introspection ON" else "introspection OFF",
                    color = if (res.introspectionEnabled) AlertRed else SoftGreen)
            }
            Spacer(modifier = Modifier.height(8.dp))
            SelectionContainer { Text(res.endpoint, color = TerminalGray) }
            res.note?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(it, color = if (res.introspectionEnabled) AlertAmber else MuteGreen)
            }

            if (res.queries.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                NexusPanel(title = "Queries (${res.queries.size})") {
                    res.queries.forEach { FieldRow(it, NeonGreen) }
                }
            }
            if (res.mutations.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                NexusPanel(title = "Mutations (${res.mutations.size})") {
                    res.mutations.forEach { FieldRow(it, AlertAmber) }
                }
            }
            if (res.types.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                NexusPanel(title = "Types (${res.types.size})") {
                    res.types.take(60).forEach { t ->
                        Text("${t.kind}  ${t.name}", color = GhostWhite, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 6.dp))
                        t.fields.take(30).forEach { f ->
                            Text("  ${f.name}: ${f.type}", color = TerminalGray,
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun FieldRow(f: GqlField, accent: androidx.compose.ui.graphics.Color) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(f.name, color = accent, fontWeight = FontWeight.SemiBold)
        if (f.args.isNotEmpty())
            Text("(${f.args.joinToString(", ")})", color = TerminalGray, style = MaterialTheme.typography.bodySmall)
        Text("→ ${f.type}", color = MuteGreen, style = MaterialTheme.typography.bodySmall)
    }
}
