package com.example.toolkit.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
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
import com.example.toolkit.ui.components.ScreenHeader
import com.example.toolkit.ui.components.StatusChip
import com.example.toolkit.ui.exif.ExifViewModel
import com.example.toolkit.ui.theme.AlertAmber
import com.example.toolkit.ui.theme.AlertRed
import com.example.toolkit.ui.theme.GhostWhite
import com.example.toolkit.ui.theme.MuteGreen
import com.example.toolkit.ui.theme.NeonGreen
import com.example.toolkit.ui.theme.SoftGreen
import com.example.toolkit.ui.theme.VoidBlack

@Composable
fun ExifScreen(vm: ExifViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val r = state.result

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { vm.analyze(it) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VoidBlack)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        ScreenHeader("EXIF Extractor", "Image metadata · GPS · device · privacy")
        Spacer(modifier = Modifier.height(16.dp))
        NexusButton(if (state.loading) "Reading…" else "Pick image", onClick = { picker.launch("image/*") },
            loading = state.loading)

        r?.error?.let {
            Spacer(modifier = Modifier.height(12.dp))
            Text(it, color = AlertRed)
        }

        r?.takeIf { it.error == null }?.let { res ->
            Spacer(modifier = Modifier.height(16.dp))
            NexusPanel(title = "File") {
                KeyValueRow("Name", res.fileName)
                KeyValueRow("Size", "${res.fileSize} bytes")
                KeyValueRow("Type", res.mimeType ?: "—")
                KeyValueRow("GPS", if (res.hasGps) "PRESENT" else "none",
                    valueColor = if (res.hasGps) AlertAmber else GhostWhite)
                res.latLon?.let { (lat, lon) ->
                    SelectionContainer {
                        Text("→ https://maps.google.com/?q=$lat,$lon", color = NeonGreen)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            NexusPanel(title = "Privacy") {
                res.privacyNotes.forEach { note ->
                    val ok = note.startsWith("No obvious")
                    Text("• $note", color = if (ok) SoftGreen else AlertAmber,
                        modifier = Modifier.padding(vertical = 2.dp))
                }
            }

            val groups = res.fields.groupBy { it.group }
            groups.forEach { (group, fields) ->
                Spacer(modifier = Modifier.height(12.dp))
                NexusPanel(title = group) {
                    fields.forEach { KeyValueRow(it.label, it.value) }
                }
            }
            if (res.fields.isEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text("No EXIF tags found (image may have been stripped).", color = MuteGreen,
                    fontWeight = FontWeight.Normal)
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}
