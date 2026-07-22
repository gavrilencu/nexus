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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.toolkit.ui.components.KeyValueRow
import com.example.toolkit.ui.components.NexusButton
import com.example.toolkit.ui.components.NexusPanel
import com.example.toolkit.ui.components.NexusTextField
import com.example.toolkit.ui.components.ScreenHeader
import com.example.toolkit.ui.components.StatusChip
import com.example.toolkit.ui.components.WarningBanner
import com.example.toolkit.ui.hibp.HibpViewModel
import com.example.toolkit.ui.theme.AlertAmber
import com.example.toolkit.ui.theme.AlertRed
import com.example.toolkit.ui.theme.BorderGreen
import com.example.toolkit.ui.theme.GhostWhite
import com.example.toolkit.ui.theme.MatrixBlack
import com.example.toolkit.ui.theme.MuteGreen
import com.example.toolkit.ui.theme.NeonGreen
import com.example.toolkit.ui.theme.PanelGreen
import com.example.toolkit.ui.theme.VoidBlack

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HibpScreen(vm: HibpViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val uriHandler = LocalUriHandler.current
    val tabs = listOf("EMAIL", "PASSWORD", "CATALOG")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VoidBlack)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        ScreenHeader(
            title = "Have I Been Pwned",
            subtitle = "Official HIBP API — real breaches · pastes · pwned passwords"
        )
        Spacer(modifier = Modifier.height(12.dp))
        WarningBanner("Email breach check uses the official haveibeenpwned.com API (requires your API key). Password check is free (k-anonymity).")

        Spacer(modifier = Modifier.height(16.dp))
        NexusPanel(title = "API KEY") {
            Text(
                text = "Get a key: haveibeenpwned.com/API/Key — then paste it below (saved on device).",
                color = MuteGreen
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = state.apiKey,
                onValueChange = vm::onApiKey,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("hibp-api-key", color = MuteGreen) },
                singleLine = true,
                visualTransformation = if (state.showApiKey) VisualTransformation.None
                else PasswordVisualTransformation(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonGreen,
                    unfocusedBorderColor = BorderGreen,
                    focusedTextColor = GhostWhite,
                    unfocusedTextColor = GhostWhite,
                    cursorColor = NeonGreen,
                    focusedContainerColor = MatrixBlack,
                    unfocusedContainerColor = MatrixBlack
                ),
                shape = RoundedCornerShape(4.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NexusButton("Save Key", onClick = vm::saveKey, modifier = Modifier.weight(1f))
                NexusButton(
                    if (state.showApiKey) "Hide" else "Show",
                    onClick = vm::toggleShowKey,
                    modifier = Modifier.weight(0.5f)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Open API key page ›",
                color = NeonGreen,
                modifier = Modifier.clickable {
                    uriHandler.openUri("https://haveibeenpwned.com/API/Key")
                }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            tabs.forEach { tab ->
                FilterChip(
                    selected = state.tab == tab,
                    onClick = { vm.onTab(tab) },
                    label = { Text(tab) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = NeonGreen.copy(alpha = 0.2f),
                        selectedLabelColor = NeonGreen,
                        containerColor = PanelGreen,
                        labelColor = MuteGreen
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        when (state.tab) {
            "EMAIL" -> {
                NexusTextField(state.email, vm::onEmail, "email@domain.com")
                Spacer(modifier = Modifier.height(12.dp))
                NexusButton(
                    "Check Email Breaches",
                    onClick = vm::checkEmail,
                    loading = state.loading,
                    enabled = state.email.isNotBlank()
                )

                state.emailResult?.let { r ->
                    Spacer(modifier = Modifier.height(16.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusChip(r.account)
                        if (r.breached) StatusChip("PWNED") else if (r.error == null) StatusChip("CLEAN")
                        StatusChip("${r.breaches.size} breaches")
                        if (r.pastes.isNotEmpty()) StatusChip("${r.pastes.size} pastes")
                    }
                    r.error?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(it, color = if (r.needsApiKey) AlertAmber else AlertRed)
                    }
                    if (r.error == null && !r.breached) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Good news — no breaches found for this account in HIBP.", color = NeonGreen)
                    }
                    r.breaches.forEach { b ->
                        Spacer(modifier = Modifier.height(10.dp))
                        NexusPanel(title = b.title.ifBlank { b.name }) {
                            KeyValueRow("Domain", b.domain)
                            KeyValueRow("Breach date", b.breachDate)
                            KeyValueRow("Accounts", "%,d".format(b.pwnCount))
                            KeyValueRow(
                                "Verified",
                                if (b.isVerified) "yes" else "no",
                                valueColor = if (b.isVerified) NeonGreen else AlertAmber
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(b.description, color = GhostWhite)
                            if (b.dataClasses.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("Compromised data:", color = NeonGreen)
                                Text(b.dataClasses.joinToString(" · "), color = MuteGreen)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "Open on HIBP ›",
                                color = NeonGreen,
                                modifier = Modifier.clickable {
                                    uriHandler.openUri("https://haveibeenpwned.com/PwnedWebsites#${b.name}")
                                }
                            )
                        }
                    }
                    if (r.pastes.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        NexusPanel(title = "PASTES (${r.pastes.size})") {
                            r.pastes.forEach { paste ->
                                Text(
                                    "${paste.source} · ${paste.title ?: paste.id} · ${paste.date ?: "?"}",
                                    color = GhostWhite,
                                    modifier = Modifier.padding(vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            }

            "PASSWORD" -> {
                OutlinedTextField(
                    value = state.password,
                    onValueChange = vm::onPassword,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("password to check", color = MuteGreen) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonGreen,
                        unfocusedBorderColor = BorderGreen,
                        focusedTextColor = GhostWhite,
                        unfocusedTextColor = GhostWhite,
                        cursorColor = NeonGreen,
                        focusedContainerColor = MatrixBlack,
                        unfocusedContainerColor = MatrixBlack
                    ),
                    shape = RoundedCornerShape(4.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("Password is hashed locally (SHA-1). Only 5-char prefix is sent to HIBP.", color = MuteGreen)
                Spacer(modifier = Modifier.height(12.dp))
                NexusButton(
                    "Check Password",
                    onClick = vm::checkPassword,
                    loading = state.loading,
                    enabled = state.password.isNotEmpty()
                )
                state.passwordResult?.let { r ->
                    Spacer(modifier = Modifier.height(16.dp))
                    r.error?.let { Text(it, color = AlertRed) }
                    if (r.error == null) {
                        if (r.pwned) {
                            Text("PWNED — seen ${"%,d".format(r.count)} times in breaches.", color = AlertRed, fontWeight = FontWeight.Bold)
                            Text("Do not use this password anywhere.", color = AlertAmber)
                        } else {
                            Text("Not found in Pwned Passwords dataset.", color = NeonGreen, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            "CATALOG" -> {
                NexusButton("Load Breach Catalog", onClick = vm::loadCatalog, loading = state.loading)
                state.catalogError?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(it, color = AlertRed)
                }
                state.catalog.forEach { b ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, BorderGreen, RoundedCornerShape(2.dp))
                            .background(PanelGreen)
                            .clickable {
                                uriHandler.openUri("https://haveibeenpwned.com/PwnedWebsites#${b.name}")
                            }
                            .padding(10.dp)
                    ) {
                        Text(b.title, color = NeonGreen, fontWeight = FontWeight.Bold)
                        Text("${b.domain} · ${b.breachDate} · ${"%,d".format(b.pwnCount)} accounts", color = MuteGreen)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}
