package com.example.toolkit.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.toolkit.security.AppLockManager
import com.example.toolkit.ui.components.KeyValueRow
import com.example.toolkit.ui.components.NexusButton
import com.example.toolkit.ui.navigation.nexusCategories
import com.example.toolkit.ui.navigation.nexusModules
import com.example.toolkit.ui.navigation.modulesIn
import com.example.toolkit.ui.theme.AccentGradient
import com.example.toolkit.ui.theme.BorderGreen
import com.example.toolkit.ui.theme.GhostWhite
import com.example.toolkit.ui.theme.MuteGreen
import com.example.toolkit.ui.theme.NeonGreen
import com.example.toolkit.ui.theme.PanelGreen
import com.example.toolkit.ui.theme.SoftGreen
import com.example.toolkit.ui.theme.TerminalGray
import com.example.toolkit.ui.theme.VoidBlack

private const val SEC_SECURITY = "security"
private const val SEC_MODULES = "modules"
private const val SEC_PRIVACY = "privacy"
private const val SEC_ABOUT = "about"

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    // All sections collapsed by default, matching the dashboard/drawer behaviour.
    val expanded = remember { mutableStateMapOf<String, Boolean>() }

    val lockAvailable = remember { AppLockManager.canAuthenticate(context) }
    val lockStatus = remember { AppLockManager.statusMessage(context) }
    var lockTestResult by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VoidBlack)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            "root@nexus:~# settings",
            color = NeonGreen,
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            "// tap a section to expand",
            color = MuteGreen,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(20.dp))

        SettingsSection(
            title = "Security & Access",
            command = "auth --status",
            icon = Icons.Default.Lock,
            expanded = expanded[SEC_SECURITY] ?: false,
            onToggle = { expanded[SEC_SECURITY] = !(expanded[SEC_SECURITY] ?: false) }
        ) {
            KeyValueRow("Device lock", if (lockAvailable) "Available" else "Not configured",
                valueColor = if (lockAvailable) SoftGreen else MuteGreen)
            KeyValueRow("Method", lockStatus)
            KeyValueRow("Auto-lock", "On app background")
            Spacer(modifier = Modifier.height(12.dp))
            NexusButton(
                text = "Test unlock now",
                enabled = lockAvailable,
                onClick = {
                    val activity = AppLockManager.findActivity(context)
                    if (activity == null) {
                        lockTestResult = "Activity not ready — reopen the app"
                        return@NexusButton
                    }
                    AppLockManager.authenticate(
                        activity = activity,
                        onSuccess = { lockTestResult = "Access granted ✓" },
                        onError = { msg -> lockTestResult = msg }
                    )
                }
            )
            lockTestResult?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(it, color = TerminalGray, style = MaterialTheme.typography.bodyMedium)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        SettingsSection(
            title = "Modules",
            command = "modules --list",
            icon = Icons.Default.Widgets,
            expanded = expanded[SEC_MODULES] ?: false,
            onToggle = { expanded[SEC_MODULES] = !(expanded[SEC_MODULES] ?: false) }
        ) {
            KeyValueRow("Total modules", nexusModules.size.toString(), valueColor = NeonGreen)
            KeyValueRow("Categories", nexusCategories.size.toString(), valueColor = NeonGreen)
            Spacer(modifier = Modifier.height(6.dp))
            nexusCategories.forEach { category ->
                KeyValueRow(category.title, "${modulesIn(category.id).size} modules")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        SettingsSection(
            title = "Network & Privacy",
            command = "privacy --info",
            icon = Icons.Default.Policy,
            expanded = expanded[SEC_PRIVACY] ?: false,
            onToggle = { expanded[SEC_PRIVACY] = !(expanded[SEC_PRIVACY] ?: false) }
        ) {
            KeyValueRow("Requests", "Direct from device")
            KeyValueRow("Screenshots", "Allowed")
            KeyValueRow("Telemetry", "None", valueColor = SoftGreen)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "NEXUS sends traffic straight from your device to the targets you enter. " +
                    "No results, tokens or lookups are relayed through third-party servers.",
                color = TerminalGray,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        SettingsSection(
            title = "About",
            command = "toolkit --version",
            icon = Icons.Default.Info,
            expanded = expanded[SEC_ABOUT] ?: false,
            onToggle = { expanded[SEC_ABOUT] = !(expanded[SEC_ABOUT] ?: false) }
        ) {
            KeyValueRow("Name", "NEXUS")
            KeyValueRow("Version", "v1.2")
            KeyValueRow("Build", "Enterprise Security Toolkit")
            KeyValueRow("Usage", "Authorized targets only", valueColor = NeonGreen)
            KeyValueRow("Developed by", "Gavrilencu Grigore for internal testing", valueColor = NeonGreen)
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun SettingsSection(
    title: String,
    command: String,
    icon: ImageVector,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "settingsChevron"
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(PanelGreen.copy(alpha = 0.55f))
                .border(1.dp, BorderGreen, RoundedCornerShape(14.dp))
                .clickable(onClick = onToggle)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(AccentGradient),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = com.example.toolkit.ui.theme.OnAccent)
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Text(
                    title,
                    color = GhostWhite,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "$ $command",
                    color = NeonGreen.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Icon(
                Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = MuteGreen,
                modifier = Modifier.rotate(chevronRotation)
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .background(PanelGreen.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                    .border(1.dp, BorderGreen.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                content()
            }
        }
    }
}
