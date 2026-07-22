package com.example.toolkit.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toolkit.ui.components.GradientBrandText
import com.example.toolkit.ui.components.ModuleCard
import com.example.toolkit.ui.components.NexusPanel
import com.example.toolkit.ui.components.NexusSearchField
import com.example.toolkit.ui.components.StatusChip
import com.example.toolkit.ui.components.WarningBanner
import com.example.toolkit.ui.navigation.nexusCategories
import com.example.toolkit.ui.navigation.nexusModules
import com.example.toolkit.ui.theme.BorderGreen
import com.example.toolkit.ui.theme.GhostWhite
import com.example.toolkit.ui.theme.MuteGreen
import com.example.toolkit.ui.theme.NeonGreen
import com.example.toolkit.ui.theme.PanelGreen
import com.example.toolkit.ui.theme.TerminalGray
import com.example.toolkit.ui.theme.VoidBlack

@Composable
fun DashboardScreen(onNavigate: (String) -> Unit) {
    // Hoisted here (inside the always-mounted nav graph) so it survives
    // lock/unlock cycles just like every other screen's state now does.
    var query by remember { mutableStateOf("") }
    val filtered = remember(query) {
        if (query.isBlank()) nexusModules
        else nexusModules.filter {
            it.title.contains(query, ignoreCase = true) ||
                it.subtitle.contains(query, ignoreCase = true) ||
                it.category.contains(query, ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VoidBlack)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        GradientBrandText(text = "NEXUS")
        Text(
            text = "Enterprise Security Toolkit",
            color = MuteGreen,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusChip("v1.2")
            StatusChip("${nexusModules.size} modules")
            StatusChip("Authorized use", color = NeonGreen)
        }

        Spacer(modifier = Modifier.height(20.dp))
        WarningBanner(
            text = "Use only on systems/domains you own or have written authorization to test."
        )

        Spacer(modifier = Modifier.height(20.dp))
        NexusSearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = "Search modules — recon, hash, JWT, ports…"
        )

        Spacer(modifier = Modifier.height(20.dp))

        if (query.isBlank()) {
            NexusPanel(title = "Mission Control") {
                Text(
                    text = "Recon · scan · OSINT · crypto · vulns. Built for developers and security engineers.",
                    color = TerminalGray,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(VoidBlack, RoundedCornerShape(12.dp))
                        .border(1.dp, BorderGreen, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Text("root@nexus:~# toolkit --status", color = NeonGreen)
                        Text("modules: ${nexusModules.size} online", color = GhostWhite)
                        Text("network: enabled", color = GhostWhite)
                        Text("policy: lab / authorized only", color = MuteGreen)
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))

            nexusCategories.forEach { category ->
                Text(
                    category,
                    color = GhostWhite,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    nexusModules.filter { it.category == category }.forEach { module ->
                        ModuleCard(
                            title = module.title,
                            subtitle = module.subtitle,
                            icon = module.icon,
                            onClick = { onNavigate(module.route) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        } else if (filtered.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.SearchOff,
                    contentDescription = null,
                    tint = MuteGreen
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("No modules match \"$query\"", color = MuteGreen)
            }
        } else {
            Text(
                "Results (${filtered.size})",
                color = GhostWhite,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                filtered.forEach { module ->
                    ModuleCard(
                        title = module.title,
                        subtitle = module.subtitle,
                        icon = module.icon,
                        onClick = { onNavigate(module.route) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(PanelGreen.copy(alpha = 0.82f), RoundedCornerShape(16.dp))
                .border(1.dp, BorderGreen, RoundedCornerShape(16.dp))
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "NEXUS — for authorized security operations",
                color = MuteGreen,
                fontSize = 11.sp
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}
