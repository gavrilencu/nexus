package com.example.toolkit.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.toolkit.data.websocket.WsDirection
import com.example.toolkit.data.websocket.WsMessage
import com.example.toolkit.ui.components.NexusButton
import com.example.toolkit.ui.components.NexusTextField
import com.example.toolkit.ui.components.ScreenHeader
import com.example.toolkit.ui.components.StatusChip
import com.example.toolkit.ui.theme.AlertRed
import com.example.toolkit.ui.theme.BorderGreen
import com.example.toolkit.ui.theme.GhostWhite
import com.example.toolkit.ui.theme.MatrixBlack
import com.example.toolkit.ui.theme.MuteGreen
import com.example.toolkit.ui.theme.NeonGreen
import com.example.toolkit.ui.theme.SoftGreen
import com.example.toolkit.ui.theme.TerminalGray
import com.example.toolkit.ui.theme.VoidBlack
import com.example.toolkit.ui.websocket.WebSocketViewModel

@Composable
fun WebSocketScreen(vm: WebSocketViewModel = viewModel()) {
    val state by vm.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VoidBlack)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        ScreenHeader("WebSocket Tester", "Connect · send frames · inspect live")
        Spacer(modifier = Modifier.height(16.dp))
        NexusTextField(state.url, vm::onUrl, "wss://echo.websocket.org", enabled = !state.connected)
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NexusButton(if (state.connected) "Disconnect" else "Connect",
                onClick = vm::toggleConnection, modifier = Modifier.weight(1f),
                enabled = state.url.isNotBlank())
        }

        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatusChip(if (state.connected) "CONNECTED" else "DISCONNECTED",
                color = if (state.connected) SoftGreen else MuteGreen)
            StatusChip("${state.messages.size} frames", color = NeonGreen)
        }

        Spacer(modifier = Modifier.height(12.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 200.dp, max = 460.dp)
                .background(MatrixBlack, RoundedCornerShape(14.dp))
                .border(1.dp, BorderGreen, RoundedCornerShape(14.dp))
                .padding(12.dp)
                .verticalScroll(rememberScrollState())
        ) {
            if (state.messages.isEmpty()) {
                Text("No frames yet.", color = TerminalGray)
            }
            SelectionContainer {
                Column {
                    state.messages.forEach { FrameLine(it) }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        NexusTextField(state.outgoing, vm::onOutgoing, "Message to send", singleLine = false)
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NexusButton("Send", onClick = vm::send, modifier = Modifier.weight(1f),
                enabled = state.connected && state.outgoing.isNotBlank())
            NexusButton("Clear", onClick = vm::clear, modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun FrameLine(m: WsMessage) {
    val (prefix, color) = when (m.direction) {
        WsDirection.SENT -> "→ " to NeonGreen
        WsDirection.RECEIVED -> "← " to SoftGreen
        WsDirection.SYSTEM -> "• " to MuteGreen
        WsDirection.ERROR -> "! " to AlertRed
    }
    Text(
        "$prefix${m.text}",
        color = color,
        fontFamily = FontFamily.Monospace,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(vertical = 2.dp)
    )
}
