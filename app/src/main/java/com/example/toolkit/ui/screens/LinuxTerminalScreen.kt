package com.example.toolkit.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.toolkit.ui.components.NexusButton
import com.example.toolkit.ui.components.NexusPanel
import com.example.toolkit.ui.components.NexusTextField
import com.example.toolkit.ui.components.ScreenHeader
import com.example.toolkit.ui.components.StatusChip
import com.example.toolkit.ui.components.WarningBanner
import com.example.toolkit.ui.linux.LineKind
import com.example.toolkit.ui.linux.LinuxTerminalViewModel
import com.example.toolkit.ui.theme.AlertRed
import com.example.toolkit.ui.theme.BorderGreen
import com.example.toolkit.ui.theme.GhostWhite
import com.example.toolkit.ui.theme.MatrixBlack
import com.example.toolkit.ui.theme.MuteGreen
import com.example.toolkit.ui.theme.NeonGreen
import com.example.toolkit.ui.theme.PanelGreen
import com.example.toolkit.ui.theme.SoftGreen
import com.example.toolkit.ui.theme.TerminalGray
import com.example.toolkit.ui.theme.VoidBlack

@Composable
fun LinuxTerminalScreen(vm: LinuxTerminalViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    var showTools by remember { mutableStateOf(false) }

    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { vm.setBackgroundService(true) }

    fun requestBackground(enabled: Boolean) {
        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            vm.setBackgroundService(enabled)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VoidBlack)
            .padding(16.dp)
    ) {
        ScreenHeader(
            title = "Linux Terminal",
            subtitle = "Real Ubuntu 24.04 + apt · install packages · run background services"
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (!state.installed) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                WarningBanner(
                    "Files are saved in app private storage (no storage/notification permission needed). " +
                        "Needs internet for the first ~28 MB download."
                )
                Spacer(modifier = Modifier.height(12.dp))
                NexusPanel(title = "Ubuntu + apt") {
                    Text(
                        "Ubuntu ${state.architecture}",
                        color = NeonGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "This is a real Ubuntu root filesystem with a real apt package manager, running " +
                            "under PRoot so Android won't block executables (fixes error 13/126). Anything you " +
                            "install with apt — Python, Node, nginx, openssh — is the genuine package.",
                        color = TerminalGray,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    if (state.installing) {
                        LinearProgressIndicator(
                            progress = { state.installPercent / 100f },
                            modifier = Modifier.fillMaxWidth(),
                            color = NeonGreen,
                            trackColor = BorderGreen
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "${state.installPercent}% · ${state.installDetail}",
                            color = GhostWhite,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    } else {
                        NexusButton(
                            text = "Install Ubuntu + apt (~28 MB)",
                            onClick = vm::installLinux
                        )
                    }
                    state.error?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(it, color = AlertRed, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        NexusButton(
                            text = "Retry install",
                            onClick = {
                                vm.resetLinux()
                                vm.installLinux()
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                NexusPanel(title = "After install") {
                    CommandExample("apt update")
                    CommandExample("apt install python3")
                    CommandExample("apt install nodejs npm")
                    CommandExample("apt install git curl openssh-client")
                    CommandExample("nohup python3 -m http.server 8080 &")
                }
            }
            return
        }

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            StatusChip(if (state.running) "Shell on" else "Ready", color = if (state.running) SoftGreen else MuteGreen)
            if (state.backgroundService) StatusChip("Background on", color = SoftGreen)
            StatusChip("Ubuntu")
            StatusChip("apt")
            StatusChip(state.architecture)
        }
        Spacer(modifier = Modifier.height(8.dp))

        if (!state.running) {
            NexusButton("Start terminal", onClick = vm::startTerminal)
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Background persistence — the actual fix for "can't run services":
        // with this on, a process you start (e.g. a dev server) keeps running
        // even after you leave this screen or switch apps.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(PanelGreen, RoundedCornerShape(14.dp))
                .border(1.dp, BorderGreen, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Run in background", color = GhostWhite, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Keeps the shell (and any service you started) alive after you leave this screen",
                    color = TerminalGray,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Switch(
                checked = state.backgroundService,
                onCheckedChange = { requestBackground(it) },
                enabled = state.running,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = VoidBlack,
                    checkedTrackColor = NeonGreen,
                    uncheckedTrackColor = BorderGreen
                )
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (showTools) "Hide quick tools ▴" else "Show quick tools (apt · services) ▾",
                color = NeonGreen,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clickable { showTools = !showTools }
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("A", color = MuteGreen, fontSize = (state.fontSize - 2).sp)
                Row {
                    NexusButton("−", onClick = vm::decreaseFont, modifier = Modifier.width(44.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    NexusButton("+", onClick = vm::increaseFont, modifier = Modifier.width(44.dp))
                }
            }
        }

        if (showTools) {
            Spacer(modifier = Modifier.height(8.dp))
            QuickPackages(vm, enabled = state.running)
            Spacer(modifier = Modifier.height(8.dp))
            ServicesPanel(vm, enabled = state.running)
        }
        Spacer(modifier = Modifier.height(8.dp))

        TerminalOutput(
            lines = state.lines.map { Triple(it.id, it.text, it.kind) },
            fontSize = state.fontSize,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
        Spacer(modifier = Modifier.height(8.dp))

        SymbolRow(onInsert = vm::insertAtCursor)
        Spacer(modifier = Modifier.height(6.dp))

        NexusTextField(
            value = state.input,
            onValueChange = vm::setInput,
            label = if (state.running) "root@nexus:~# command" else "terminal stopped",
            enabled = state.running,
            imeAction = ImeAction.Send,
            onDone = vm::submit
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NexusButton(
                text = "Run",
                onClick = vm::submit,
                modifier = Modifier.weight(1f),
                enabled = state.running && state.input.isNotBlank()
            )
            NexusButton(
                text = "↑",
                onClick = vm::historyPrevious,
                modifier = Modifier.weight(0.45f),
                enabled = state.history.isNotEmpty()
            )
            NexusButton(
                text = "↓",
                onClick = vm::historyNext,
                modifier = Modifier.weight(0.45f),
                enabled = state.history.isNotEmpty()
            )
            NexusButton(
                text = "Ctrl-C",
                onClick = vm::sendControlC,
                modifier = Modifier.weight(0.75f),
                enabled = state.running
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NexusButton("Clear", onClick = vm::clearOutput, modifier = Modifier.weight(1f))
            NexusButton(
                "Stop",
                onClick = vm::stopTerminal,
                modifier = Modifier.weight(1f),
                enabled = state.running
            )
            NexusButton("Reinstall", onClick = vm::resetLinux, modifier = Modifier.weight(1f))
        }
        state.error?.let {
            Spacer(modifier = Modifier.height(4.dp))
            Text(it, color = AlertRed, fontSize = 11.sp)
        }
    }
}

@Composable
private fun QuickPackages(vm: LinuxTerminalViewModel, enabled: Boolean) {
    Column {
        Text("Quick install (apt)", color = MuteGreen, style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            NexusButton("Update", onClick = { vm.runCommand("apt update") }, modifier = Modifier.width(110.dp), enabled = enabled)
            NexusButton("Python", onClick = { vm.runCommand("apt install -y python3") }, modifier = Modifier.width(110.dp), enabled = enabled)
            NexusButton("Node", onClick = { vm.runCommand("apt install -y nodejs npm") }, modifier = Modifier.width(110.dp), enabled = enabled)
            NexusButton("Git", onClick = { vm.runCommand("apt install -y git") }, modifier = Modifier.width(110.dp), enabled = enabled)
            NexusButton("nginx", onClick = { vm.runCommand("apt install -y nginx") }, modifier = Modifier.width(110.dp), enabled = enabled)
            NexusButton("OpenSSH", onClick = { vm.runCommand("apt install -y openssh-server") }, modifier = Modifier.width(110.dp), enabled = enabled)
        }
    }
}

/**
 * Real, working one-tap examples of running a Linux service inside the
 * Ubuntu rootfs. PRoot shares the device's real network stack (no network
 * namespace), so 127.0.0.1:PORT inside the shell is the same as
 * 127.0.0.1:PORT on the phone — open it straight in the phone's browser.
 * Ports below 1024 need real root and won't bind; use 1024+ instead.
 */
@Composable
private fun ServicesPanel(vm: LinuxTerminalViewModel, enabled: Boolean) {
    NexusPanel(title = "Run it as a service") {
        Text(
            "A trailing `&` (or nohup … &) backgrounds a process inside this same shell session. " +
                "Turn on \"Run in background\" above so it survives leaving the screen. Use ports ≥ 1024 " +
                "(no real root) — reach them from this phone at 127.0.0.1:PORT.",
            color = TerminalGray,
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            NexusButton(
                "Start demo web server on :8080",
                onClick = { vm.runCommand("nohup python3 -m http.server 8080 --directory /root > /tmp/http.log 2>&1 & echo Started on 8080") },
                enabled = enabled
            )
            NexusButton(
                "Show background jobs",
                onClick = { vm.runCommand("jobs -l") },
                enabled = enabled
            )
            NexusButton(
                "Show listening ports",
                onClick = { vm.runCommand("(command -v ss >/dev/null && ss -tlnp) || (command -v netstat >/dev/null && netstat -tlnp) || echo 'apt install -y iproute2'") },
                enabled = enabled
            )
            NexusButton(
                "Stop demo web server",
                onClick = { vm.runCommand("pkill -f 'http.server 8080' && echo Stopped || echo 'Not running'") },
                enabled = enabled
            )
        }
    }
}

@Composable
private fun SymbolRow(onInsert: (String) -> Unit) {
    val symbols = listOf("|", "&&", "&", ">", ">>", "<", "~", "/", "-", "_", ":", "$", "\"", "'", "`", "*")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        symbols.forEach { symbol ->
            Box(
                modifier = Modifier
                    .background(MatrixBlack, RoundedCornerShape(8.dp))
                    .border(1.dp, BorderGreen, RoundedCornerShape(8.dp))
                    .clickable { onInsert(symbol) }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(symbol, color = GhostWhite, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun TerminalOutput(
    lines: List<Triple<Long, String, LineKind>>,
    fontSize: Int,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.scrollToItem(lines.lastIndex)
    }
    // The weight/size modifier must land on SelectionContainer itself (the
    // actual direct child of the parent Column) rather than on the LazyColumn
    // nested inside it, or the outer Column won't see it.
    SelectionContainer(
        modifier = modifier
            .border(1.dp, BorderGreen, RoundedCornerShape(12.dp))
            .background(MatrixBlack, RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(lines, key = { it.first }) { (_, text, kind) ->
                Text(
                    text = text,
                    color = when (kind) {
                        LineKind.SYSTEM -> NeonGreen
                        LineKind.COMMAND -> GhostWhite
                        LineKind.OUTPUT -> TerminalGray
                        LineKind.ERROR -> AlertRed
                    },
                    fontFamily = FontFamily.Monospace,
                    fontSize = fontSize.sp,
                    lineHeight = (fontSize + 3).sp
                )
            }
        }
    }
}

@Composable
private fun CommandExample(command: String) {
    Text(
        "root@nexus:~# $command",
        color = GhostWhite,
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        modifier = Modifier
            .fillMaxWidth()
            .background(PanelGreen, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp)
    )
    Spacer(modifier = Modifier.height(3.dp))
}
