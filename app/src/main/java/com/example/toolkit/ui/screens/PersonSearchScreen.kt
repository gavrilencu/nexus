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
import com.example.toolkit.ui.person.PersonViewModel
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
fun PersonSearchScreen(vm: PersonViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val result = state.result
    val uriHandler = LocalUriHandler.current

    val categories = listOf("ALL") + (result?.links?.map { it.category }?.distinct()?.sorted().orEmpty())
    val filteredLinks = result?.links?.filter {
        state.categoryFilter == "ALL" || it.category == state.categoryFilter
    }.orEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VoidBlack)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        ScreenHeader(
            title = "Person Search",
            subtitle = "Name · phone · keyword · username across public web sources"
        )
        Spacer(modifier = Modifier.height(12.dp))
        WarningBanner(
            "Public sources only (search engines, social, pastes, archives). Private carrier/government DBs are not accessible without legal authority."
        )

        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NexusTextField(state.firstName, vm::onFirst, "first name", modifier = Modifier.weight(1f))
            NexusTextField(state.lastName, vm::onLast, "last name", modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(8.dp))
        NexusTextField(state.phone, vm::onPhone, "phone (+407xxxxxxxx)")
        Spacer(modifier = Modifier.height(8.dp))
        NexusTextField(state.username, vm::onUsername, "username / handle")
        Spacer(modifier = Modifier.height(8.dp))
        NexusTextField(state.keyword, vm::onKeyword, "keyword / company / alias")
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NexusTextField(state.city, vm::onCity, "city", modifier = Modifier.weight(1f))
            NexusTextField(state.country, vm::onCountry, "country", modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(12.dp))
        NexusButton("Search Everywhere (public)", onClick = vm::search, loading = state.loading)

        result?.error?.let {
            Spacer(modifier = Modifier.height(12.dp))
            Text(it, color = AlertRed)
        }

        result?.takeIf { it.error == null }?.let { r ->
            Spacer(modifier = Modifier.height(16.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip(r.mode.uppercase())
                StatusChip(r.query)
                StatusChip("${r.links.size} links")
                StatusChip("${r.probes.count { it.exists == true }} probes HIT")
            }

            Spacer(modifier = Modifier.height(12.dp))
            NexusPanel(title = "NOTES") {
                r.notes.forEach {
                    Text("• $it", color = GhostWhite, modifier = Modifier.padding(vertical = 2.dp))
                }
            }

            if (r.probes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                NexusPanel(title = "LIVE PLATFORM PROBES") {
                    r.probes.forEach { probe ->
                        val color = when (probe.exists) {
                            true -> NeonGreen
                            false -> MuteGreen
                            null -> AlertAmber
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .border(1.dp, BorderGreen, RoundedCornerShape(2.dp))
                                .background(PanelGreen)
                                .clickable { uriHandler.openUri(probe.url) }
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(probe.platform, color = NeonGreen, fontWeight = FontWeight.Bold)
                                Text(probe.url, color = MuteGreen)
                            }
                            Text(probe.status, color = color, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                categories.forEach { cat ->
                    val count = if (cat == "ALL") r.links.size
                    else r.links.count { it.category == cat }
                    FilterChip(
                        selected = state.categoryFilter == cat,
                        onClick = { vm.onFilter(cat) },
                        label = { Text("$cat ($count)") },
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
            NexusPanel(title = "OPEN WEB RESULTS (${filteredLinks.size})") {
                filteredLinks.forEach { link ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .border(1.dp, BorderGreen, RoundedCornerShape(2.dp))
                            .background(PanelGreen)
                            .clickable { uriHandler.openUri(link.url) }
                            .padding(10.dp)
                    ) {
                        Text(link.source, color = NeonGreen, fontWeight = FontWeight.Bold)
                        Text(link.title, color = GhostWhite)
                        Text(link.category, color = MuteGreen)
                        Text("Open ›", color = NeonGreen)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}
