package com.example.toolkit.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.toolkit.ui.docs.ModuleDoc
import com.example.toolkit.ui.docs.moduleDocs
import com.example.toolkit.ui.navigation.NexusRoute
import com.example.toolkit.ui.navigation.nexusCategories
import com.example.toolkit.ui.navigation.nexusModules
import com.example.toolkit.ui.screens.ApiScreen
import com.example.toolkit.ui.screens.ApkInspectorScreen
import com.example.toolkit.ui.screens.CorsScreen
import com.example.toolkit.ui.screens.CveScreen
import com.example.toolkit.ui.screens.DashboardScreen
import com.example.toolkit.ui.screens.DirScanScreen
import com.example.toolkit.ui.screens.DnsScreen
import com.example.toolkit.ui.screens.FingerprintScreen
import com.example.toolkit.ui.screens.HashCrackScreen
import com.example.toolkit.ui.screens.HashScreen
import com.example.toolkit.ui.screens.HibpScreen
import com.example.toolkit.ui.screens.HttpMethodsScreen
import com.example.toolkit.ui.screens.IpToolsScreen
import com.example.toolkit.ui.screens.JwtScreen
import com.example.toolkit.ui.screens.LinuxTerminalScreen
import com.example.toolkit.ui.screens.NetworkMonitorScreen
import com.example.toolkit.ui.screens.OsintScreen
import com.example.toolkit.ui.screens.PersonSearchScreen
import com.example.toolkit.ui.screens.PortScanScreen
import com.example.toolkit.ui.screens.ReconScreen
import com.example.toolkit.ui.screens.SettingsScreen
import com.example.toolkit.ui.screens.SubdomainScreen
import com.example.toolkit.ui.screens.TrafficScreen
import com.example.toolkit.ui.screens.WhoisScreen
import com.example.toolkit.ui.screens.TlsScreen
import com.example.toolkit.ui.screens.HeadersScreen
import com.example.toolkit.ui.screens.WebVulnScreen
import com.example.toolkit.ui.screens.FuzzerScreen
import com.example.toolkit.ui.screens.JsReconScreen
import com.example.toolkit.ui.screens.ExposedScreen
import com.example.toolkit.ui.screens.GraphqlScreen
import com.example.toolkit.ui.screens.TakeoverScreen
import com.example.toolkit.ui.screens.WebSocketScreen
import com.example.toolkit.ui.screens.CrtShScreen
import com.example.toolkit.ui.screens.WaybackScreen
import com.example.toolkit.ui.screens.ExifScreen
import com.example.toolkit.ui.screens.DorkScreen
import com.example.toolkit.ui.screens.ShodanScreen
import com.example.toolkit.ui.screens.ApkAuditScreen
import com.example.toolkit.ui.screens.DeepLinkScreen
import com.example.toolkit.ui.screens.FirebaseScreen
import com.example.toolkit.ui.screens.DexScanScreen
import com.example.toolkit.ui.screens.MitmScreen
import com.example.toolkit.ui.theme.AccentGradient
import com.example.toolkit.ui.theme.AccentSoft
import com.example.toolkit.ui.theme.AlertAmber
import com.example.toolkit.ui.theme.BorderGreen
import com.example.toolkit.ui.theme.GhostWhite
import com.example.toolkit.ui.theme.MatrixBlack
import com.example.toolkit.ui.theme.MuteGreen
import com.example.toolkit.ui.theme.NeonGreen
import com.example.toolkit.ui.theme.PanelGreen
import com.example.toolkit.ui.theme.TerminalGray
import com.example.toolkit.ui.theme.VoidBlack
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NexusApp() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route ?: NexusRoute.Dashboard.route
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showHelp by remember { mutableStateOf(false) }
    val currentDoc = moduleDocs[currentRoute]
    // Outside NavHost so categories stay open after opening a module and going back.
    val dashboardExpanded = rememberSaveable(
        saver = listSaver(
            save = { it.toList() },
            restore = { it.toMutableStateList() }
        )
    ) { mutableStateListOf<String>() }
    val drawerExpanded = rememberSaveable(
        saver = listSaver(
            save = { it.toList() },
            restore = { it.toMutableStateList() }
        )
    ) { mutableStateListOf<String>() }

    fun go(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
        scope.launch { drawerState.close() }
    }

    val title = when (currentRoute) {
        NexusRoute.Dashboard.route -> "NEXUS"
        NexusRoute.Settings.route -> "Settings"
        NexusRoute.Recon.route -> "Domain Recon"
        NexusRoute.Ports.route -> "Port Scanner"
        NexusRoute.Traffic.route -> "Traffic Monitor"
        NexusRoute.Osint.route -> "OSINT Lookup"
        NexusRoute.Api.route -> "API Lab"
        NexusRoute.Hash.route -> "Hash / Encoder"
        NexusRoute.Dns.route -> "DNS Dig"
        NexusRoute.Subdomain.route -> "Subdomain Finder"
        NexusRoute.Jwt.route -> "JWT Lab"
        NexusRoute.Cve.route -> "CVE Lookup"
        NexusRoute.Ip.route -> "IP Tools"
        NexusRoute.Person.route -> "Person Search"
        NexusRoute.Hibp.route -> "Have I Been Pwned"
        NexusRoute.WifiMonitor.route -> "Wi-Fi/SIM Monitor"
        NexusRoute.Mitm.route -> "MITM / Proxy Capture"
        NexusRoute.LinuxTerminal.route -> "Linux Terminal"
        NexusRoute.DirScan.route -> "Content Discovery"
        NexusRoute.Fingerprint.route -> "Web Fingerprint"
        NexusRoute.Whois.route -> "WHOIS / RDAP"
        NexusRoute.Cors.route -> "CORS Scanner"
        NexusRoute.HttpMethods.route -> "HTTP Methods"
        NexusRoute.HashCrack.route -> "Hash Cracker"
        NexusRoute.ApkInspector.route -> "APK Inspector"
        NexusRoute.Tls.route -> "TLS/SSL Analyzer"
        NexusRoute.Headers.route -> "Security Headers"
        NexusRoute.WebVuln.route -> "Web Vuln Scanner"
        NexusRoute.Fuzzer.route -> "Fuzzer"
        NexusRoute.JsRecon.route -> "JS Recon"
        NexusRoute.Exposed.route -> "Exposed Files"
        NexusRoute.Graphql.route -> "GraphQL Inspector"
        NexusRoute.Takeover.route -> "Subdomain Takeover"
        NexusRoute.WebSocket.route -> "WebSocket Tester"
        NexusRoute.CrtSh.route -> "CT Log Enum"
        NexusRoute.Wayback.route -> "Wayback URLs"
        NexusRoute.Exif.route -> "EXIF Extractor"
        NexusRoute.Dork.route -> "Dork Builder"
        NexusRoute.Shodan.route -> "Shodan Lookup"
        NexusRoute.ApkAudit.route -> "APK Security Audit"
        NexusRoute.DeepLink.route -> "Deep-Link Tester"
        NexusRoute.Firebase.route -> "Firebase Checker"
        NexusRoute.DexScan.route -> "DEX API Scanner"
        else -> "NEXUS"
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MatrixBlack,
                drawerContentColor = GhostWhite
            ) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    DrawerBrandHeader()
                    Spacer(modifier = Modifier.height(4.dp))
                    DrawerNavItem(
                        label = "Dashboard",
                        icon = Icons.Default.Home,
                        selected = currentRoute == NexusRoute.Dashboard.route,
                        onClick = { go(NexusRoute.Dashboard.route) }
                    )
                    DrawerNavItem(
                        label = "Settings",
                        icon = Icons.Default.Settings,
                        selected = currentRoute == NexusRoute.Settings.route,
                        onClick = { go(NexusRoute.Settings.route) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = BorderGreen)
                    nexusCategories.forEach { category ->
                        val modules = nexusModules.filter { it.category == category.id }
                        val isExpanded = category.id in drawerExpanded
                        DrawerCategoryHeader(
                            title = category.title,
                            count = modules.size,
                            expanded = isExpanded,
                            onClick = {
                                if (isExpanded) drawerExpanded.remove(category.id)
                                else drawerExpanded.add(category.id)
                            }
                        )
                        AnimatedVisibility(
                            visible = isExpanded,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column {
                                modules.forEach { module ->
                                    DrawerNavItem(
                                        label = module.title,
                                        icon = module.icon,
                                        selected = currentRoute == module.route,
                                        onClick = { go(module.route) }
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    ) {
        Scaffold(
            containerColor = VoidBlack,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = title,
                            color = GhostWhite,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    navigationIcon = {
                        if (currentRoute == NexusRoute.Dashboard.route) {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu", tint = GhostWhite)
                            }
                        } else {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = GhostWhite
                                )
                            }
                        }
                    },
                    actions = {
                        if (currentDoc != null) {
                            IconButton(onClick = { showHelp = true }) {
                                Icon(
                                    Icons.Default.HelpOutline,
                                    contentDescription = "Documentație modul",
                                    tint = GhostWhite
                                )
                            }
                        }
                        if (currentRoute == NexusRoute.Dashboard.route) {
                            IconButton(onClick = { go(NexusRoute.Settings.route) }) {
                                Icon(
                                    Icons.Default.Settings,
                                    contentDescription = "Settings",
                                    tint = GhostWhite
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MatrixBlack,
                        titleContentColor = GhostWhite,
                        navigationIconContentColor = GhostWhite
                    )
                )
            }
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = NexusRoute.Dashboard.route,
                modifier = Modifier.padding(padding)
            ) {
                composable(NexusRoute.Dashboard.route) {
                    DashboardScreen(
                        expandedCategoryIds = dashboardExpanded,
                        onNavigate = { route -> go(route) }
                    )
                }
                composable(NexusRoute.Settings.route) { SettingsScreen() }
                composable(NexusRoute.Recon.route) { ReconScreen() }
                composable(NexusRoute.Ports.route) { PortScanScreen() }
                composable(NexusRoute.Traffic.route) { TrafficScreen() }
                composable(NexusRoute.Osint.route) { OsintScreen() }
                composable(NexusRoute.Api.route) { ApiScreen() }
                composable(NexusRoute.Hash.route) { HashScreen() }
                composable(NexusRoute.Dns.route) { DnsScreen() }
                composable(NexusRoute.Subdomain.route) { SubdomainScreen() }
                composable(NexusRoute.Jwt.route) { JwtScreen() }
                composable(NexusRoute.Cve.route) { CveScreen() }
                composable(NexusRoute.Ip.route) { IpToolsScreen() }
                composable(NexusRoute.Person.route) { PersonSearchScreen() }
                composable(NexusRoute.Hibp.route) { HibpScreen() }
                composable(NexusRoute.WifiMonitor.route) { NetworkMonitorScreen() }
                composable(NexusRoute.Mitm.route) { MitmScreen() }
                composable(NexusRoute.LinuxTerminal.route) { LinuxTerminalScreen() }
                composable(NexusRoute.DirScan.route) { DirScanScreen() }
                composable(NexusRoute.Fingerprint.route) { FingerprintScreen() }
                composable(NexusRoute.Whois.route) { WhoisScreen() }
                composable(NexusRoute.Cors.route) { CorsScreen() }
                composable(NexusRoute.HttpMethods.route) { HttpMethodsScreen() }
                composable(NexusRoute.HashCrack.route) { HashCrackScreen() }
                composable(NexusRoute.ApkInspector.route) { ApkInspectorScreen() }
                composable(NexusRoute.Tls.route) { TlsScreen() }
                composable(NexusRoute.Headers.route) { HeadersScreen() }
                composable(NexusRoute.WebVuln.route) { WebVulnScreen() }
                composable(NexusRoute.Fuzzer.route) { FuzzerScreen() }
                composable(NexusRoute.JsRecon.route) { JsReconScreen() }
                composable(NexusRoute.Exposed.route) { ExposedScreen() }
                composable(NexusRoute.Graphql.route) { GraphqlScreen() }
                composable(NexusRoute.Takeover.route) { TakeoverScreen() }
                composable(NexusRoute.WebSocket.route) { WebSocketScreen() }
                composable(NexusRoute.CrtSh.route) { CrtShScreen() }
                composable(NexusRoute.Wayback.route) { WaybackScreen() }
                composable(NexusRoute.Exif.route) { ExifScreen() }
                composable(NexusRoute.Dork.route) { DorkScreen() }
                composable(NexusRoute.Shodan.route) { ShodanScreen() }
                composable(NexusRoute.ApkAudit.route) { ApkAuditScreen() }
                composable(NexusRoute.DeepLink.route) { DeepLinkScreen() }
                composable(NexusRoute.Firebase.route) { FirebaseScreen() }
                composable(NexusRoute.DexScan.route) { DexScanScreen() }
            }
        }
    }

    if (showHelp && currentDoc != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showHelp = false },
            sheetState = sheetState,
            containerColor = MatrixBlack,
            contentColor = GhostWhite
        ) {
            ModuleDocSheetContent(currentDoc)
        }
    }
}

@Composable
private fun ModuleDocSheetContent(doc: ModuleDoc) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp)
    ) {
        item {
            Text(doc.title, color = GhostWhite, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(doc.tagline, color = TerminalGray, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(AccentGradient)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(doc.overview, color = GhostWhite, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(20.dp))
            DocSectionLabel("Cum funcționează")
        }
        items(doc.howItWorks) { line -> DocBullet(line, NeonGreen) }
        item {
            Spacer(modifier = Modifier.height(16.dp))
            DocSectionLabel("Cum se folosește")
        }
        items(doc.usage) { line -> DocBullet(line, NeonGreen) }
        if (doc.limitations.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                DocSectionLabel("Limitări și avertismente")
            }
            items(doc.limitations) { line -> DocBullet(line, AlertAmber) }
        }
        item { Spacer(modifier = Modifier.height(8.dp)) }
    }
}

@Composable
private fun DocSectionLabel(text: String) {
    Text(
        text = text,
        color = GhostWhite,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun DocBullet(text: String, dotColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(PanelGreen.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .padding(top = 6.dp, end = 10.dp)
                .size(6.dp)
                .clip(RoundedCornerShape(50))
                .background(dotColor)
        )
        Text(text, color = GhostWhite, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun DrawerBrandHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(AccentGradient),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Shield, contentDescription = null, tint = com.example.toolkit.ui.theme.OnAccent)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text("NEXUS", color = GhostWhite, fontWeight = FontWeight.ExtraBold)
            Text("Security Toolkit", color = MuteGreen, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun DrawerCategoryHeader(
    title: String,
    count: Int,
    expanded: Boolean,
    onClick: () -> Unit
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "drawerChevron"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title.uppercase(),
            color = MuteGreen,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(100.dp))
                .background(AccentSoft)
                .padding(horizontal = 8.dp, vertical = 1.dp)
        ) {
            Text(
                text = count.toString(),
                color = NeonGreen,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            Icons.Default.ExpandMore,
            contentDescription = if (expanded) "Collapse" else "Expand",
            tint = MuteGreen,
            modifier = Modifier
                .size(18.dp)
                .rotate(chevronRotation)
        )
    }
}

@Composable
private fun DrawerNavItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) AccentSoft else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected) GhostWhite else MuteGreen,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            label,
            color = if (selected) GhostWhite else MuteGreen,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}
