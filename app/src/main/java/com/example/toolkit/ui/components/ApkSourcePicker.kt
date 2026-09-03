package com.example.toolkit.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toolkit.data.apk.InstalledApp
import com.example.toolkit.ui.theme.AccentGradient
import com.example.toolkit.ui.theme.AlertRed
import com.example.toolkit.ui.theme.BorderGreen
import com.example.toolkit.ui.theme.GhostWhite
import com.example.toolkit.ui.theme.MuteGreen
import com.example.toolkit.ui.theme.NeonGreen
import com.example.toolkit.ui.theme.PanelGreen
import com.example.toolkit.ui.theme.TerminalGray

/**
 * Reusable "pick an APK to analyze" section shared by all Mobile RE modules.
 * Offers a file-picker tab (any package on disk) and an installed-apps tab with
 * a live filter, invoking [onPickFile] / [onPickInstalled] with the choice.
 */
@Composable
fun ApkSourcePicker(
    fromInstalledTab: Boolean,
    onSelectTab: (Boolean) -> Unit,
    installed: List<InstalledApp>,
    installedLoading: Boolean,
    query: String,
    onQuery: (String) -> Unit,
    onPickFile: (android.net.Uri) -> Unit,
    onPickInstalled: (String) -> Unit,
    pickLabel: String = "Pick .apk / .aab / .xapk / .apks",
    error: String? = null
) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { onPickFile(it) }
    }

    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tab("From file", !fromInstalledTab) { onSelectTab(false) }
            Tab("Installed apps", fromInstalledTab) { onSelectTab(true) }
        }
        Spacer(modifier = Modifier.height(12.dp))

        if (!fromInstalledTab) {
            NexusButton(pickLabel, onClick = { picker.launch(arrayOf("*/*")) })
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                "Choose an app package from Downloads or anywhere — it does not need to be installed.",
                color = TerminalGray, style = MaterialTheme.typography.bodyMedium
            )
            error?.let {
                Spacer(modifier = Modifier.height(10.dp))
                Text(it, color = AlertRed)
            }
        } else {
            NexusSearchField(value = query, onValueChange = onQuery, placeholder = "filter installed apps…")
            Spacer(modifier = Modifier.height(10.dp))
            if (installedLoading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = NeonGreen, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("  Loading installed apps…", color = MuteGreen)
                }
            } else {
                val q = query.trim()
                val list = if (q.isBlank()) installed
                else installed.filter { it.label.contains(q, true) || it.packageName.contains(q, true) }
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(list, key = { it.packageName }) { app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(PanelGreen.copy(alpha = 0.6f))
                                .border(1.dp, BorderGreen, RoundedCornerShape(12.dp))
                                .clickable { onPickInstalled(app.packageName) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(app.label, color = GhostWhite, style = MaterialTheme.typography.titleMedium)
                                Text(app.packageName, color = MuteGreen, style = MaterialTheme.typography.bodySmall)
                            }
                            if (app.system) StatusChip("sys", color = MuteGreen)
                            Text(app.versionName ?: "", color = TerminalGray, fontSize = 11.sp,
                                modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Tab(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .then(
                if (selected) Modifier.background(AccentGradient)
                else Modifier.background(PanelGreen.copy(alpha = 0.6f)).border(1.dp, BorderGreen, RoundedCornerShape(100.dp))
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(label, color = if (selected) com.example.toolkit.ui.theme.OnAccent else MuteGreen, fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelLarge)
    }
}
