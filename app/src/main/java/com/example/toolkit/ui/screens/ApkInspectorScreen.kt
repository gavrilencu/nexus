package com.example.toolkit.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.toolkit.data.apk.ApkReport
import com.example.toolkit.data.apk.ComponentInfo
import com.example.toolkit.data.apk.ContentKind
import com.example.toolkit.data.apk.EntryContent
import com.example.toolkit.data.apk.EntryNode
import com.example.toolkit.ui.apkinspector.ApkInspectorViewModel
import com.example.toolkit.ui.apkinspector.InspectorTab
import com.example.toolkit.ui.components.KeyValueRow
import com.example.toolkit.ui.components.NexusButton
import com.example.toolkit.ui.components.NexusSearchField
import com.example.toolkit.ui.components.ScreenHeader
import com.example.toolkit.ui.components.StatusChip
import com.example.toolkit.ui.theme.AccentGradient
import com.example.toolkit.ui.theme.AlertAmber
import com.example.toolkit.ui.theme.AlertRed
import com.example.toolkit.ui.theme.BorderGreen
import com.example.toolkit.ui.theme.GhostWhite
import com.example.toolkit.ui.theme.MuteGreen
import com.example.toolkit.ui.theme.NeonGreen
import com.example.toolkit.ui.theme.PanelGreen
import com.example.toolkit.ui.theme.SoftGreen
import com.example.toolkit.ui.theme.TerminalGray
import com.example.toolkit.ui.theme.VoidBlack

@Composable
fun ApkInspectorScreen(vm: ApkInspectorViewModel = viewModel()) {
    val state by vm.state.collectAsState()

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { vm.analyzeFile(it) } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VoidBlack)
            .padding(16.dp)
    ) {
        ScreenHeader(
            title = "APK Inspector",
            subtitle = "Deep static analysis · RE · browse files — APK/AAB/XAPK/APKS or installed"
        )
        Spacer(modifier = Modifier.height(12.dp))

        when {
            state.analyzing -> CenterStatus("Analyzing package…", spinner = true)
            state.viewer != null || state.viewerLoading -> FileViewer(vm)
            state.browsing && state.report != null -> FileBrowser(state.report!!, vm)
            state.report != null -> ReportView(state.report!!, vm)
            else -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TabChip("From file", state.tab == InspectorTab.FILE) { vm.selectTab(InspectorTab.FILE) }
                    TabChip("Installed apps", state.tab == InspectorTab.INSTALLED) { vm.selectTab(InspectorTab.INSTALLED) }
                }
                Spacer(modifier = Modifier.height(12.dp))
                if (state.tab == InspectorTab.FILE) {
                    FilePicker(onPick = { picker.launch(arrayOf("*/*")) }, error = state.error)
                } else {
                    InstalledList(vm)
                }
            }
        }
    }
}

@Composable
private fun FilePicker(onPick: () -> Unit, error: String?) {
    Column {
        NexusButton("Pick .apk / .aab / .xapk / .apks", onClick = onPick)
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            "Choose an app package from Downloads or anywhere on the device — it does not need to be " +
                "installed. Bundles (XAPK/APKS/APKM) and App Bundles (AAB) are unpacked automatically.",
            color = TerminalGray, style = MaterialTheme.typography.bodyMedium
        )
        error?.let {
            Spacer(modifier = Modifier.height(10.dp))
            Text(it, color = AlertRed, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun InstalledList(vm: ApkInspectorViewModel) {
    val state by vm.state.collectAsState()
    Column(modifier = Modifier.fillMaxSize()) {
        NexusSearchField(
            value = state.installedQuery,
            onValueChange = vm::setInstalledQuery,
            placeholder = "filter installed apps…"
        )
        Spacer(modifier = Modifier.height(10.dp))
        if (state.installedLoading) {
            CenterStatus("Loading installed apps…", spinner = true)
            return@Column
        }
        val q = state.installedQuery.trim()
        val list = if (q.isBlank()) state.installed
        else state.installed.filter { it.label.contains(q, true) || it.packageName.contains(q, true) }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(list, key = { it.packageName }) { app ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(PanelGreen.copy(alpha = 0.6f))
                        .border(1.dp, BorderGreen, RoundedCornerShape(12.dp))
                        .clickable { vm.analyzeInstalled(app.packageName) }
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

// ---- report ---------------------------------------------------------------

@Composable
private fun ReportView(report: ApkReport, vm: ApkInspectorViewModel) {
    val state by vm.state.collectAsState()
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    fun isOpen(id: String, default: Boolean) = expanded[id] ?: default
    fun toggle(id: String, default: Boolean) { expanded[id] = !isOpen(id, default) }

    val extractLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> uri?.let { vm.extractTo(it) } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                report.appLabel ?: report.packageName ?: report.source,
                color = GhostWhite, style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            BackLink("‹ back") { vm.clearReport() }
        }
        Spacer(modifier = Modifier.height(4.dp))
        FlowChips(listOfNotNull(report.archiveType, report.origin, humanSize(report.sizeBytes)))
        Spacer(modifier = Modifier.height(12.dp))

        // Primary actions
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NexusButton("Browse files (${report.entryCount})", onClick = vm::openBrowser, modifier = Modifier.weight(1f))
            NexusButton("Extract to folder", onClick = { extractLauncher.launch(null) },
                modifier = Modifier.weight(1f), loading = state.extracting)
        }
        state.extractMessage?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(it, color = SoftGreen, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(modifier = Modifier.height(14.dp))

        Section("Overview", "app --info", isOpen("ov", true), { toggle("ov", true) }) {
            KV("Package", report.packageName ?: "—")
            KV("Label", report.appLabel ?: "—")
            KV("Version", buildString { append(report.versionName ?: "—"); report.versionCode?.let { append(" (code $it)") } })
            KV("Min SDK", report.minSdk?.let { "$it${sdkName(it)}" } ?: "—")
            KV("Target SDK", report.targetSdk?.let { "$it${sdkName(it)}" } ?: "—")
            report.compileSdk?.let { KV("Compile SDK", "$it${sdkName(it)}") }
            report.installLocation?.let { KV("Install location", it) }
            KV("Debuggable", report.debuggable?.let { if (it) "YES (risky)" else "no" } ?: "—",
                if (report.debuggable == true) AlertAmber else GhostWhite)
            KV("Entries", report.entryCount.toString())
        }

        Section("Language & Frameworks", "detect --stack", isOpen("fw", true), { toggle("fw", true) }) {
            if (report.languages.isNotEmpty()) {
                Label("Languages"); FlowChips(report.languages, accent = true); Spacer(modifier = Modifier.height(12.dp))
            }
            Label("Frameworks / runtime")
            if (report.frameworks.isEmpty()) Text("No specific framework detected.", color = TerminalGray)
            else report.frameworks.forEach { KeyValueRow(it.name, it.evidence, valueColor = TerminalGray) }
        }

        if (report.sdks.isNotEmpty()) {
            Section("SDKs & trackers (${report.sdks.size})", "detect --libs", isOpen("sdk", false), { toggle("sdk", false) }) {
                FlowChips(report.sdks.map { it.name })
            }
        }

        Section("Signature", "cert --show", isOpen("sig", false), { toggle("sig", false) }) {
            val c = report.cert
            if (c == null) Text("No certificate found.", color = TerminalGray)
            else {
                KV("Subject", c.subject)
                KV("Issuer", c.issuer)
                KV("Serial", c.serial)
                KV("Key", c.publicKey)
                KV("Algorithm", c.algorithm)
                KV("Valid", "${c.validFrom} → ${c.validUntil}")
                KV("v1 (JAR) signed", if (report.v1Signed) "yes" else "no")
                Spacer(modifier = Modifier.height(6.dp))
                Label("SHA-256"); Mono(c.sha256, SoftGreen)
                Label("SHA-1"); Mono(c.sha1, TerminalGray)
            }
        }

        Section("Permissions (${report.permissions.size})", "perms --list", isOpen("perm", false), { toggle("perm", false) }) {
            if (report.permissions.isEmpty()) Text("None requested.", color = TerminalGray)
            report.permissions.forEach { p ->
                val dangerous = DANGEROUS.any { p.endsWith(it) }
                Mono(p, if (dangerous) AlertAmber else TerminalGray)
            }
        }

        if (report.features.isNotEmpty()) {
            Section("Required features (${report.features.size})", "features --list", isOpen("feat", false), { toggle("feat", false) }) {
                report.features.forEach { Mono(it, TerminalGray) }
            }
        }

        val compTotal = report.activities.size + report.services.size + report.receivers.size + report.providers.size
        Section("Components ($compTotal)", "manifest --components", isOpen("comp", false), { toggle("comp", false) }) {
            ComponentGroup("Activities", report.activities)
            ComponentGroup("Services", report.services)
            ComponentGroup("Receivers", report.receivers)
            ComponentGroup("Providers", report.providers)
        }

        Section("Native libraries (${report.abis.size} ABI · ${report.nativeLibs.size})", "libs --abi", isOpen("abi", false), { toggle("abi", false) }) {
            if (report.abis.isEmpty()) Text("No native libraries.", color = TerminalGray)
            else {
                FlowChips(report.abis, accent = true)
                Spacer(modifier = Modifier.height(8.dp))
                report.nativeLibs.forEach { KeyValueRow("${it.name}", "${it.abi} · ${humanSize(it.bytes)}", valueColor = TerminalGray) }
            }
        }

        Section("DEX & Obfuscation", "dex --stats", isOpen("dex", false), { toggle("dex", false) }) {
            KV("DEX files", report.dexCount.toString())
            KV("Classes", report.classCount.toString())
            KV("Methods", report.methodCount.toString())
            KV("Strings", report.stringCount.toString())
            Spacer(modifier = Modifier.height(8.dp))
            Text(report.obfuscationLabel, color = obfColor(report.obfuscationScore), fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { report.obfuscationScore / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = obfColor(report.obfuscationScore), trackColor = BorderGreen
            )
        }

        val findings = report.urls.size + report.ips.size + report.emails.size + report.secrets.size
        Section("Strings: URLs / IPs / emails / secrets ($findings)", "strings --grep", isOpen("sec", false), { toggle("sec", false) }) {
            if (report.secrets.isNotEmpty()) {
                Label("Potential secrets (${report.secrets.size})")
                SelectionContainer { Column { report.secrets.forEach { Mono("[${it.type}] ${it.value}", AlertRed) } } }
                Spacer(modifier = Modifier.height(10.dp))
            }
            StringGroup("URLs", report.urls)
            StringGroup("IP addresses", report.ips)
            StringGroup("Emails", report.emails)
        }

        Section("File types (${report.fileTypes.size})", "ls --by-type", isOpen("ft", false), { toggle("ft", false) }) {
            report.fileTypes.forEach { KeyValueRow("${it.ext}  (${it.count})", humanSize(it.bytes), valueColor = TerminalGray) }
        }

        Section("Top folders", "du -sh", isOpen("tree", false), { toggle("tree", false) }) {
            report.topDirs.forEach { KeyValueRow("${it.name}  (${it.count})", humanSize(it.bytes), valueColor = TerminalGray) }
        }

        report.manifestXml?.let { xml ->
            Section("AndroidManifest.xml", "aapt dump xmltree", isOpen("man", false), { toggle("man", false) }) {
                CodeBlock(xml)
            }
        }

        if (report.notes.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            report.notes.forEach {
                Text("• $it", color = MuteGreen, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp))
            }
        }
        if (report.origin != "File") {
            Spacer(modifier = Modifier.height(8.dp))
            Text("Tip: for full smali/Java, extract then run jadx or apktool from the Linux Terminal.",
                color = MuteGreen, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ---- file browser ---------------------------------------------------------

@Composable
private fun FileBrowser(report: ApkReport, vm: ApkInspectorViewModel) {
    val state by vm.state.collectAsState()
    val path = state.browsePath
    val (dirs, files) = remember(path, report.entries) { childrenOf(report.entries, path) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Files", color = GhostWhite, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            BackLink("‹ report") { vm.closeBrowser() }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text("/${path}", color = NeonGreen, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (path.isNotEmpty()) {
                item(key = "..") { BrowserRow(Icons.AutoMirrored.Filled.ArrowBack, "..", null, MuteGreen) { vm.navigateUp() } }
            }
            items(dirs, key = { "d/$it" }) { d ->
                BrowserRow(Icons.Default.Folder, d, null, NeonGreen) { vm.navigateTo(path + d + "/") }
            }
            items(files, key = { "f/${it.path}" }) { f ->
                BrowserRow(Icons.Default.Description, f.path.substringAfterLast('/'), humanSize(f.size), GhostWhite) {
                    vm.openEntry(f.path)
                }
            }
        }
    }
}

@Composable
private fun BrowserRow(icon: androidx.compose.ui.graphics.vector.ImageVector, name: String, meta: String?, tint: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(PanelGreen.copy(alpha = 0.5f))
            .border(1.dp, BorderGreen, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Text(name, color = GhostWhite, fontFamily = FontFamily.Monospace, fontSize = 13.sp,
            modifier = Modifier.weight(1f).padding(start = 10.dp))
        meta?.let { Text(it, color = TerminalGray, fontSize = 11.sp) }
    }
}

@Composable
private fun FileViewer(vm: ApkInspectorViewModel) {
    val state by vm.state.collectAsState()
    val content = state.viewer
    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                content?.path?.substringAfterLast('/') ?: "…",
                color = GhostWhite, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f)
            )
            BackLink("‹ files") { vm.closeViewer() }
        }
        content?.let {
            Text(it.path, color = MuteGreen, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                StatusChip(it.kind.name.lowercase())
                StatusChip(humanSize(it.sizeBytes))
            }
            it.note?.let { n -> Text(n, color = AlertAmber, fontSize = 11.sp) }
        }
        Spacer(modifier = Modifier.height(8.dp))
        when {
            state.viewerLoading -> CenterStatus("Reading…", spinner = true)
            content == null -> CenterStatus("Could not read entry.")
            content.kind == ContentKind.IMAGE -> CenterStatus("Image asset — extract to view it.")
            content.kind == ContentKind.DEX -> CenterStatus("Dalvik bytecode — run jadx on the extracted file.")
            content.text.isNullOrEmpty() -> CenterStatus("Empty or non-previewable file.")
            else -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black)
                    .border(1.dp, BorderGreen, RoundedCornerShape(12.dp))
                    .padding(10.dp)
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState())
            ) {
                SelectionContainer {
                    Text(content.text, color = SoftGreen, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp)
                }
            }
        }
    }
}

// ---- shared bits ----------------------------------------------------------

@Composable
private fun ComponentGroup(title: String, items: List<ComponentInfo>) {
    if (items.isEmpty()) return
    Text("$title (${items.size})", color = MuteGreen, style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
    items.forEach { c ->
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
            if (c.exported) Text("[exp] ", color = AlertAmber, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            Text(c.name.substringAfterLast('.'),
                color = if (c.exported) GhostWhite else TerminalGray,
                fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        }
    }
}

@Composable
private fun StringGroup(title: String, items: List<String>) {
    Label("$title (${items.size})")
    if (items.isEmpty()) { Text("none", color = TerminalGray, fontSize = 11.sp); Spacer(modifier = Modifier.height(8.dp)); return }
    SelectionContainer { Column { items.forEach { Mono(it, TerminalGray) } } }
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun CodeBlock(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 420.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black)
            .border(1.dp, BorderGreen, RoundedCornerShape(10.dp))
            .padding(10.dp)
            .verticalScroll(rememberScrollState())
            .horizontalScroll(rememberScrollState())
    ) {
        SelectionContainer {
            Text(text, color = SoftGreen, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp)
        }
    }
}

@Composable
private fun Section(title: String, command: String, expanded: Boolean, onToggle: () -> Unit, content: @Composable () -> Unit) {
    val rot by animateFloatAsState(if (expanded) 180f else 0f, label = "sec")
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
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
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = GhostWhite, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("$ $command", color = NeonGreen.copy(alpha = 0.85f), style = MaterialTheme.typography.labelSmall)
            }
            Icon(Icons.Default.ExpandMore, contentDescription = null, tint = MuteGreen, modifier = Modifier.rotate(rot))
        }
        AnimatedVisibility(expanded, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .background(PanelGreen.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                    .border(1.dp, BorderGreen.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) { content() }
        }
    }
}

@Composable
private fun KV(key: String, value: String, valueColor: Color = GhostWhite) = KeyValueRow(key, value, valueColor = valueColor)

@Composable
private fun Label(text: String) =
    Text(text, color = MuteGreen, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 4.dp))

@Composable
private fun Mono(text: String, color: Color) =
    Text(text, color = color, fontFamily = FontFamily.Monospace, fontSize = 11.sp, modifier = Modifier.padding(vertical = 1.dp))

@Composable
private fun BackLink(label: String, onClick: () -> Unit) =
    Text(label, color = NeonGreen, modifier = Modifier
        .clip(RoundedCornerShape(8.dp))
        .clickable(onClick = onClick)
        .padding(horizontal = 8.dp, vertical = 4.dp))

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowChips(items: List<String>, accent: Boolean = false) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { StatusChip(it, color = if (accent) NeonGreen else MuteGreen) }
    }
}

@Composable
private fun TabChip(label: String, selected: Boolean, onClick: () -> Unit) {
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
        Text(label, color = if (selected) Color.Black else MuteGreen, fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun CenterStatus(text: String, spinner: Boolean = false) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (spinner) { CircularProgressIndicator(color = NeonGreen); Spacer(modifier = Modifier.height(12.dp)) }
        Text(text, color = MuteGreen, fontFamily = FontFamily.Monospace)
    }
}

// ---- helpers --------------------------------------------------------------

private fun childrenOf(entries: List<EntryNode>, path: String): Pair<List<String>, List<EntryNode>> {
    val dirs = LinkedHashSet<String>()
    val files = ArrayList<EntryNode>()
    for (e in entries) {
        if (!e.path.startsWith(path)) continue
        val rest = e.path.substring(path.length)
        val slash = rest.indexOf('/')
        if (slash >= 0) dirs.add(rest.substring(0, slash)) else if (rest.isNotEmpty()) files.add(e)
    }
    return dirs.sorted() to files.sortedBy { it.path.lowercase() }
}

private val DANGEROUS = listOf(
    "READ_CONTACTS", "WRITE_CONTACTS", "READ_SMS", "SEND_SMS", "RECEIVE_SMS", "READ_CALL_LOG",
    "WRITE_CALL_LOG", "CAMERA", "RECORD_AUDIO", "ACCESS_FINE_LOCATION", "ACCESS_BACKGROUND_LOCATION",
    "READ_EXTERNAL_STORAGE", "WRITE_EXTERNAL_STORAGE", "READ_PHONE_STATE", "QUERY_ALL_PACKAGES",
    "REQUEST_INSTALL_PACKAGES", "SYSTEM_ALERT_WINDOW", "MANAGE_EXTERNAL_STORAGE", "READ_MEDIA_IMAGES"
)

private fun sdkName(level: Int): String = when (level) {
    36 -> " (16)"; 35 -> " (15)"; 34 -> " (14)"; 33 -> " (13)"; 32, 31 -> " (12)"; 30 -> " (11)"
    29 -> " (10)"; 28 -> " (9)"; 27, 26 -> " (8)"; 24, 25 -> " (7)"; 23 -> " (6)"; 21, 22 -> " (5)"
    else -> ""
}

private fun humanSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = listOf("B", "KB", "MB", "GB")
    var v = bytes.toDouble(); var i = 0
    while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
    return if (i == 0) "$bytes B" else "%.1f %s".format(v, units[i])
}

private fun obfColor(score: Int): Color = when {
    score >= 55 -> AlertRed
    score >= 25 -> AlertAmber
    else -> SoftGreen
}
