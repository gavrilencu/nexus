package com.example.toolkit.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Api
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Http
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Https
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Javascript
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.ManageSearch
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Code
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Single source of truth for the module tiles — used by both the Dashboard's
 * search/grid and the navigation drawer, so the two never drift out of sync.
 *
 * Every module belongs to exactly one [NexusCategory] via [category], which
 * matches a [NexusCategory.id]. Categories carry their own display metadata
 * (icon, description, terminal command tag) so the dashboard and drawer can
 * render rich, consistent section headers.
 */
data class NexusModule(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val route: String,
    val category: String
)

/** A logical grouping of modules, with metadata for rich section headers. */
data class NexusCategory(
    val id: String,
    val title: String,
    val description: String,
    val command: String,
    val icon: ImageVector
)

// Category ids — referenced by each module's `category` field.
object Cat {
    const val RECON = "recon"
    const val TRAFFIC = "traffic"
    const val WEB = "web"
    const val INTEL = "intel"
    const val CRYPTO = "crypto"
    const val MOBILE = "mobile"
    const val SYSTEM = "system"
}

/** Ordered categories. Dashboard/drawer render sections in this order. */
val nexusCategoryList: List<NexusCategory> = listOf(
    NexusCategory(
        id = Cat.RECON,
        title = "Recon & Mapping",
        description = "Discover hosts, domains, DNS and exposed surface",
        command = "recon --enumerate",
        icon = Icons.Default.Radar
    ),
    NexusCategory(
        id = Cat.TRAFFIC,
        title = "Traffic & Network",
        description = "Watch the wire — HTTP capture and live device monitoring",
        command = "sniff --live",
        icon = Icons.Default.Lan
    ),
    NexusCategory(
        id = Cat.WEB,
        title = "Web App Testing",
        description = "Probe HTTP endpoints, methods and cross-origin policy",
        command = "http --probe",
        icon = Icons.Default.Public
    ),
    NexusCategory(
        id = Cat.INTEL,
        title = "OSINT & Vulns",
        description = "People, breaches and CVE intelligence from public sources",
        command = "intel --gather",
        icon = Icons.Default.Security
    ),
    NexusCategory(
        id = Cat.CRYPTO,
        title = "Crypto & Tokens",
        description = "Hashing, encoding, cracking and token inspection",
        command = "crypto --decode",
        icon = Icons.Default.Key
    ),
    NexusCategory(
        id = Cat.MOBILE,
        title = "Mobile & RE",
        description = "Inspect and reverse-engineer Android app packages",
        command = "apk --inspect",
        icon = Icons.Default.Android
    ),
    NexusCategory(
        id = Cat.SYSTEM,
        title = "System & Shell",
        description = "A real Linux userland in your pocket",
        command = "sh -i",
        icon = Icons.Default.Terminal
    )
)

val nexusModules: List<NexusModule> = listOf(
    // Recon & Mapping
    NexusModule("Domain Recon", "DNS · HTTP · TLS · Geo · Headers", Icons.Default.TravelExplore, NexusRoute.Recon.route, Cat.RECON),
    NexusModule("DNS Dig", "A · AAAA · MX · NS · TXT · CNAME · SOA", Icons.Default.Dns, NexusRoute.Dns.route, Cat.RECON),
    NexusModule("Subdomain Finder", "Wordlist brute · live DNS resolve", Icons.Default.Hub, NexusRoute.Subdomain.route, Cat.RECON),
    NexusModule("Port Scanner", "Common services · live progress", Icons.Default.Radar, NexusRoute.Ports.route, Cat.RECON),
    NexusModule("IP Tools", "Resolve · reverse · geo · reachability", Icons.Default.Language, NexusRoute.Ip.route, Cat.RECON),
    NexusModule("Content Discovery", "Dir/file brute-force · live status", Icons.Default.FolderOpen, NexusRoute.DirScan.route, Cat.RECON),
    NexusModule("Web Fingerprint", "Server · CMS · framework · CDN/WAF · JS", Icons.Default.Widgets, NexusRoute.Fingerprint.route, Cat.RECON),
    NexusModule("WHOIS / RDAP", "Domain & IP registration via RDAP", Icons.Default.Badge, NexusRoute.Whois.route, Cat.RECON),

    // Traffic & Network
    NexusModule("Traffic Monitor", "HTTP capture · headers · latency", Icons.Default.Dns, NexusRoute.Traffic.route, Cat.TRAFFIC),
    NexusModule("Wi-Fi Monitor", "Devices · IP · hostname · live traffic in app", Icons.Default.WifiTethering, NexusRoute.WifiMonitor.route, Cat.TRAFFIC),

    // Web App Testing
    NexusModule("API Lab", "Probe endpoints · inspect responses", Icons.Default.Api, NexusRoute.Api.route, Cat.WEB),
    NexusModule("CORS Scanner", "Origin reflection · null · bypass tests", Icons.Default.Policy, NexusRoute.Cors.route, Cat.WEB),
    NexusModule("HTTP Methods", "Allowed verbs · dangerous methods", Icons.Default.Http, NexusRoute.HttpMethods.route, Cat.WEB),
    NexusModule("TLS/SSL Analyzer", "Protocols · ciphers · cert chain · grade", Icons.Default.Https, NexusRoute.Tls.route, Cat.WEB),
    NexusModule("Security Headers", "CSP · HSTS · XFO · A–F grade", Icons.Default.Shield, NexusRoute.Headers.route, Cat.WEB),
    NexusModule("Web Vuln Scanner", "XSS · SQLi · redirect · traversal · SSRF", Icons.Default.BugReport, NexusRoute.WebVuln.route, Cat.WEB),
    NexusModule("Fuzzer", "ffuf-style · FUZZ · status/size filters", Icons.Default.GridView, NexusRoute.Fuzzer.route, Cat.WEB),
    NexusModule("JS Recon", "Endpoints · API routes · secrets from JS", Icons.Default.Javascript, NexusRoute.JsRecon.route, Cat.WEB),
    NexusModule("Exposed Files", ".git · .env · backups · configs", Icons.Default.FolderOpen, NexusRoute.Exposed.route, Cat.WEB),
    NexusModule("GraphQL Inspector", "Detect · introspection · schema map", Icons.Default.Hub, NexusRoute.Graphql.route, Cat.WEB),
    NexusModule("Subdomain Takeover", "Dangling CNAME · provider fingerprints", Icons.Default.Dns, NexusRoute.Takeover.route, Cat.WEB),
    NexusModule("WebSocket Tester", "Connect · send frames · inspect live", Icons.Default.SwapHoriz, NexusRoute.WebSocket.route, Cat.WEB),

    // OSINT & Vulns
    NexusModule("Person Search", "Name · phone · keyword · public web everywhere", Icons.Default.PersonSearch, NexusRoute.Person.route, Cat.INTEL),
    NexusModule("OSINT Lookup", "Username probes · 30+ platforms · Facebook", Icons.Default.PersonSearch, NexusRoute.Osint.route, Cat.INTEL),
    NexusModule("Have I Been Pwned", "Real HIBP breaches · pastes · password check", Icons.Default.BugReport, NexusRoute.Hibp.route, Cat.INTEL),
    NexusModule("CVE Lookup", "NVD search by ID or keyword", Icons.Default.BugReport, NexusRoute.Cve.route, Cat.INTEL),
    NexusModule("CT Log Enum", "Passive subdomains from crt.sh", Icons.Default.Dns, NexusRoute.CrtSh.route, Cat.INTEL),
    NexusModule("Wayback URLs", "Historical URLs · web.archive.org CDX", Icons.Default.History, NexusRoute.Wayback.route, Cat.INTEL),
    NexusModule("EXIF Extractor", "Image metadata · GPS · device", Icons.Default.Image, NexusRoute.Exif.route, Cat.INTEL),
    NexusModule("Dork Builder", "Google + GitHub dorks · code search", Icons.Default.ManageSearch, NexusRoute.Dork.route, Cat.INTEL),
    NexusModule("Shodan Lookup", "IP exposure · ports · CVEs", Icons.Default.Public, NexusRoute.Shodan.route, Cat.INTEL),

    // Crypto & Tokens
    NexusModule("Hash / Encoder", "MD5 · SHA · Base64 · URL · Hex", Icons.Default.Fingerprint, NexusRoute.Hash.route, Cat.CRYPTO),
    NexusModule("Hash Cracker", "Identify hash · dictionary attack", Icons.Default.LockOpen, NexusRoute.HashCrack.route, Cat.CRYPTO),
    NexusModule("JWT Lab", "Decode header · payload · claims", Icons.Default.VpnKey, NexusRoute.Jwt.route, Cat.CRYPTO),

    // Mobile & RE
    NexusModule("APK Inspector", "APK/AAB/XAPK · frameworks · RE · browse · extract", Icons.Default.Android, NexusRoute.ApkInspector.route, Cat.MOBILE),
    NexusModule("APK Security Audit", "MobSF-style risk score · manifest", Icons.Default.Security, NexusRoute.ApkAudit.route, Cat.MOBILE),
    NexusModule("Deep-Link Tester", "Enumerate app links · launch intents", Icons.Default.Link, NexusRoute.DeepLink.route, Cat.MOBILE),
    NexusModule("Firebase Checker", "Open RTDB read test · /.json", Icons.Default.LocalFireDepartment, NexusRoute.Firebase.route, Cat.MOBILE),
    NexusModule("DEX API Scanner", "Dangerous APIs · crypto · WebView · exec", Icons.Default.Code, NexusRoute.DexScan.route, Cat.MOBILE),

    // System & Shell
    NexusModule("Linux Terminal", "Ubuntu · apt install · Python · Node · Git", Icons.Default.Terminal, NexusRoute.LinuxTerminal.route, Cat.SYSTEM)
)

/** Categories that actually contain at least one module, in defined order. */
val nexusCategories: List<NexusCategory> =
    nexusCategoryList.filter { cat -> nexusModules.any { it.category == cat.id } }

fun modulesIn(categoryId: String): List<NexusModule> =
    nexusModules.filter { it.category == categoryId }
