package com.example.toolkit.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.toolkit.ui.components.NexusButton
import com.example.toolkit.ui.components.NexusPanel
import com.example.toolkit.ui.components.NexusTextField
import com.example.toolkit.ui.components.ScreenHeader
import com.example.toolkit.ui.components.StatusChip
import com.example.toolkit.ui.components.WarningBanner
import com.example.toolkit.ui.osint.OsintFilter
import com.example.toolkit.ui.osint.OsintViewModel
import com.example.toolkit.ui.theme.AlertAmber
import com.example.toolkit.ui.theme.AlertRed
import com.example.toolkit.ui.theme.BorderGreen
import com.example.toolkit.ui.theme.GhostWhite
import com.example.toolkit.ui.theme.MuteGreen
import com.example.toolkit.ui.theme.NeonGreen
import com.example.toolkit.ui.theme.PanelGreen
import com.example.toolkit.ui.theme.VoidBlack

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OsintScreen(vm: OsintViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val result = state.result
    val uriHandler = LocalUriHandler.current
    val profiles = vm.filteredProfiles()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VoidBlack)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        ScreenHeader(
            title = "OSINT Lookup",
            subtitle = "30+ platforms · content-verified hits · username variants"
        )
        Spacer(modifier = Modifier.height(12.dp))
        WarningBanner(
            "HIT = profile markers confirmed. Login walls (Instagram/X) are NOT counted as hits."
        )

        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NexusTextField(
                value = state.firstName,
                onValueChange = vm::onFirstName,
                label = "first name",
                modifier = Modifier.weight(1f)
            )
            NexusTextField(
                value = state.lastName,
                onValueChange = vm::onLastName,
                label = "last name",
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        NexusTextField(state.username, vm::onUsername, "username / handle (best signal)")
        Spacer(modifier = Modifier.height(8.dp))
        NexusTextField(state.email, vm::onEmail, "email")
        Spacer(modifier = Modifier.height(8.dp))
        NexusTextField(state.company, vm::onCompany, "company / domain hint")
        Spacer(modifier = Modifier.height(12.dp))
        NexusButton(
            text = if (state.loading) "Scanning…" else "Run OSINT",
            onClick = vm::search,
            loading = state.loading
        )
        if (state.progressLabel.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(state.progressLabel, color = MuteGreen)
        }

        result?.error?.let {
            Spacer(modifier = Modifier.height(12.dp))
            Text(it, color = AlertRed)
        }

        result?.takeIf { it.error == null }?.let { r ->
            val hits = r.profiles.count { it.exists == true }
            val miss = r.profiles.count { it.exists == false }
            val unk = r.profiles.count { it.exists == null }

            Spacer(modifier = Modifier.height(16.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip(r.queryType.uppercase())
                StatusChip("${hits} HIT")
                StatusChip("${miss} MISS")
                StatusChip("${unk} ?")
            }

            if (r.handlesTried.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                NexusPanel(title = "HANDLES TRIED") {
                    Text(
                        text = r.handlesTried.joinToString("  ·  ") { "@$it" },
                        color = GhostWhite
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            NexusPanel(title = "NOTES") {
                r.notes.forEach {
                    Text("• $it", color = GhostWhite, modifier = Modifier.padding(vertical = 2.dp))
                }
            }

            if (r.emailPatterns.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                NexusPanel(title = "EMAIL PATTERNS") {
                    r.emailPatterns.forEach {
                        Text(it, color = NeonGreen, modifier = Modifier.padding(vertical = 3.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OsintFilter.entries.forEach { filter ->
                    val label = when (filter) {
                        OsintFilter.ALL -> "ALL (${r.profiles.size})"
                        OsintFilter.HITS -> "HITS ($hits)"
                        OsintFilter.MISS -> "MISS ($miss)"
                        OsintFilter.UNKNOWN -> "? ($unk)"
                    }
                    FilterChip(
                        selected = state.filter == filter,
                        onClick = { vm.onFilter(filter) },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NeonGreen.copy(alpha = 0.2f),
                            selectedLabelColor = NeonGreen,
                            containerColor = PanelGreen,
                            labelColor = MuteGreen
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            NexusPanel(title = "PLATFORM RESULTS (${profiles.size})") {
                if (profiles.isEmpty()) {
                    Text(
                        text = when (state.filter) {
                            OsintFilter.HITS -> "No confirmed hits. Try ALL or another username."
                            else -> "No results in this filter."
                        },
                        color = MuteGreen
                    )
                } else {
                    profiles.forEach { profile ->
                        val color = when (profile.exists) {
                            true -> NeonGreen
                            false -> MuteGreen
                            null -> AlertAmber
                        }
                        val badge = when (profile.exists) {
                            true -> "HIT"
                            false -> "MISS"
                            null -> "?"
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(2.dp))
                                .background(PanelGreen)
                                .clickable { uriHandler.openUri(profile.url) }
                                .padding(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = profile.platform,
                                        color = NeonGreen,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text("@${profile.username}", color = MuteGreen)
                                    Text(profile.note.orEmpty(), color = GhostWhite)
                                }
                                Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                                    Text(badge, color = color, fontWeight = FontWeight.Bold)
                                    if (profile.exists == true) {
                                        Text("${profile.confidence}%", color = MuteGreen)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Open  ›  ${profile.url}",
                                color = if (profile.exists == true) NeonGreen else MuteGreen
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
