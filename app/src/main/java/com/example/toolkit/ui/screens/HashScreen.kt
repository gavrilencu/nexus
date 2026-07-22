package com.example.toolkit.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.toolkit.ui.components.KeyValueRow
import com.example.toolkit.ui.components.NexusButton
import com.example.toolkit.ui.components.NexusPanel
import com.example.toolkit.ui.components.NexusTextField
import com.example.toolkit.ui.components.ScreenHeader
import com.example.toolkit.ui.hash.HashViewModel
import com.example.toolkit.ui.theme.BorderGreen
import com.example.toolkit.ui.theme.GhostWhite
import com.example.toolkit.ui.theme.MuteGreen
import com.example.toolkit.ui.theme.NeonGreen
import com.example.toolkit.ui.theme.PanelGreen
import com.example.toolkit.ui.theme.VoidBlack

@Composable
fun HashScreen(vm: HashViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val decodeTypes = listOf("Base64", "URL", "Hex")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VoidBlack)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        ScreenHeader(
            title = "Hash / Encoder",
            subtitle = "MD5 · SHA · Base64 · URL · Hex — local crypto lab"
        )
        Spacer(modifier = Modifier.height(16.dp))

        NexusTextField(state.input, vm::onInput, "input text")
        Spacer(modifier = Modifier.height(12.dp))
        NexusButton("Encode / Hash All", onClick = vm::encode, enabled = state.input.isNotEmpty())

        state.bundle?.let { b ->
            Spacer(modifier = Modifier.height(16.dp))
            NexusPanel(title = "HASHES") {
                CopyRow("MD5", b.md5, context)
                CopyRow("SHA-1", b.sha1, context)
                CopyRow("SHA-256", b.sha256, context)
                CopyRow("SHA-512", b.sha512, context)
            }
            Spacer(modifier = Modifier.height(12.dp))
            NexusPanel(title = "ENCODINGS") {
                CopyRow("Base64", b.base64, context)
                CopyRow("URL", b.url, context)
                CopyRow("Hex", b.hex, context)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        NexusPanel(title = "DECODE") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                decodeTypes.forEach { type ->
                    FilterChip(
                        selected = state.decodeType == type,
                        onClick = { vm.onDecodeType(type) },
                        label = { Text(type) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NeonGreen.copy(alpha = 0.2f),
                            selectedLabelColor = NeonGreen,
                            containerColor = PanelGreen,
                            labelColor = MuteGreen
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            NexusTextField(state.decodeInput, vm::onDecodeInput, "encoded value", singleLine = false)
            Spacer(modifier = Modifier.height(8.dp))
            NexusButton("Decode", onClick = vm::decode, enabled = state.decodeInput.isNotBlank())
            if (state.decodeResult.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(state.decodeResult, color = GhostWhite)
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun CopyRow(label: String, value: String, context: Context) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .border(1.dp, BorderGreen, RoundedCornerShape(2.dp))
            .background(PanelGreen)
            .clickable {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText(label, value))
            }
            .padding(10.dp)
    ) {
        Text(label, color = NeonGreen)
        Text(value, color = GhostWhite)
        Text("tap to copy", color = MuteGreen)
    }
}
