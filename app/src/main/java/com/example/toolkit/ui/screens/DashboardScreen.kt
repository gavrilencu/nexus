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
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toolkit.ui.components.GradientBrandText
import com.example.toolkit.ui.components.ModuleCard
import com.example.toolkit.ui.components.NexusSearchField
import com.example.toolkit.ui.components.StatusChip
import com.example.toolkit.ui.navigation.NexusCategory
import com.example.toolkit.ui.navigation.modulesIn
import com.example.toolkit.ui.navigation.nexusCategories
import com.example.toolkit.ui.navigation.nexusModules
import com.example.toolkit.ui.theme.AccentGradient
import com.example.toolkit.ui.theme.AccentSoft
import com.example.toolkit.ui.theme.BorderGreen
import com.example.toolkit.ui.theme.GhostWhite
import com.example.toolkit.ui.theme.MuteGreen
import com.example.toolkit.ui.theme.NeonGreen
import com.example.toolkit.ui.theme.PanelGreen
import com.example.toolkit.ui.theme.TerminalGray
import com.example.toolkit.ui.theme.VoidBlack

@Composable
fun DashboardScreen(
    expandedCategoryIds: MutableList<String>,
    onNavigate: (String) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }

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
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "Professional security toolkit",
            color = MuteGreen,
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusChip("${nexusModules.size} tools")
            StatusChip("${nexusCategories.size} categories")
        }

        Spacer(modifier = Modifier.height(20.dp))
        NexusSearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = "Search tools — recon, hash, JWT, ports…"
        )

        Spacer(modifier = Modifier.height(20.dp))

        if (query.isBlank()) {
            nexusCategories.forEach { category ->
                val isExpanded = category.id in expandedCategoryIds
                CategorySection(
                    category = category,
                    count = modulesIn(category.id).size,
                    expanded = isExpanded,
                    onToggle = {
                        if (isExpanded) expandedCategoryIds.remove(category.id)
                        else expandedCategoryIds.add(category.id)
                    }
                )
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(
                        modifier = Modifier.padding(top = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        modulesIn(category.id).forEach { module ->
                            ModuleCard(
                                title = module.title,
                                subtitle = module.subtitle,
                                icon = module.icon,
                                onClick = { onNavigate(module.route) }
                            )
                        }
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
                Text("No tools match \"$query\"", color = MuteGreen)
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
                text = "NEXUS — for authorized security testing only",
                color = MuteGreen,
                fontSize = 11.sp
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

/** A clickable, terminal-styled header for one collapsible module category. */
@Composable
private fun CategorySection(
    category: NexusCategory,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "chevron"
    )
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
            Icon(category.icon, contentDescription = null, tint = com.example.toolkit.ui.theme.OnAccent)
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) {
            Text(
                category.title,
                color = GhostWhite,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                category.description,
                color = MuteGreen,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1
            )
        }
        CountBadge(count)
        Icon(
            Icons.Default.ExpandMore,
            contentDescription = if (expanded) "Collapse" else "Expand",
            tint = MuteGreen,
            modifier = Modifier
                .padding(start = 8.dp)
                .rotate(chevronRotation)
        )
    }
}

@Composable
private fun CountBadge(count: Int) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(AccentSoft)
            .border(1.dp, NeonGreen.copy(alpha = 0.4f), RoundedCornerShape(100.dp))
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Text(
            count.toString(),
            color = NeonGreen,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
    }
}
