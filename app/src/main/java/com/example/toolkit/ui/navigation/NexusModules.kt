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
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Single source of truth for the 15 module tiles — used by both the
 * Dashboard's search/grid and the navigation drawer, so the two never drift
 * out of sync.
 */
data class NexusModule(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val route: String,
    val category: String
)

val nexusModules: List<NexusModule> = listOf(
    NexusModule("Domain Recon", "DNS · HTTP · TLS · Geo · Headers", Icons.Default.TravelExplore, NexusRoute.Recon.route, "Recon & Network"),
    NexusModule("DNS Dig", "A · AAAA · MX · NS · TXT · CNAME · SOA", Icons.Default.Dns, NexusRoute.Dns.route, "Recon & Network"),
    NexusModule("Subdomain Finder", "Wordlist brute · live DNS resolve", Icons.Default.Hub, NexusRoute.Subdomain.route, "Recon & Network"),
    NexusModule("Port Scanner", "Common services · live progress", Icons.Default.Radar, NexusRoute.Ports.route, "Recon & Network"),
    NexusModule("IP Tools", "Resolve · reverse · geo · reachability", Icons.Default.Language, NexusRoute.Ip.route, "Recon & Network"),
    NexusModule("Traffic Monitor", "HTTP capture · headers · latency", Icons.Default.Dns, NexusRoute.Traffic.route, "Recon & Network"),
    NexusModule("Wi-Fi Monitor", "Devices · IP · hostname · live traffic in app", Icons.Default.WifiTethering, NexusRoute.WifiMonitor.route, "Recon & Network"),
    NexusModule("Content Discovery", "Dir/file brute-force · live status", Icons.Default.FolderOpen, NexusRoute.DirScan.route, "Recon & Network"),
    NexusModule("Web Fingerprint", "Server · CMS · framework · CDN/WAF · JS", Icons.Default.Widgets, NexusRoute.Fingerprint.route, "Recon & Network"),
    NexusModule("WHOIS / RDAP", "Domain & IP registration via RDAP", Icons.Default.Badge, NexusRoute.Whois.route, "Recon & Network"),
    NexusModule("Person Search", "Name · phone · keyword · public web everywhere", Icons.Default.PersonSearch, NexusRoute.Person.route, "Intel & Vulns"),
    NexusModule("OSINT Lookup", "Username probes · 30+ platforms · Facebook", Icons.Default.PersonSearch, NexusRoute.Osint.route, "Intel & Vulns"),
    NexusModule("Have I Been Pwned", "Real HIBP breaches · pastes · password check", Icons.Default.BugReport, NexusRoute.Hibp.route, "Intel & Vulns"),
    NexusModule("CVE Lookup", "NVD search by ID or keyword", Icons.Default.BugReport, NexusRoute.Cve.route, "Intel & Vulns"),
    NexusModule("API Lab", "Probe endpoints · inspect responses", Icons.Default.Api, NexusRoute.Api.route, "Intel & Vulns"),
    NexusModule("CORS Scanner", "Origin reflection · null · bypass tests", Icons.Default.Policy, NexusRoute.Cors.route, "Intel & Vulns"),
    NexusModule("HTTP Methods", "Allowed verbs · dangerous methods", Icons.Default.Http, NexusRoute.HttpMethods.route, "Intel & Vulns"),
    NexusModule("Linux Terminal", "Ubuntu · apt install · Python · Node · Git", Icons.Default.Terminal, NexusRoute.LinuxTerminal.route, "System & Shell"),
    NexusModule("Hash / Encoder", "MD5 · SHA · Base64 · URL · Hex", Icons.Default.Fingerprint, NexusRoute.Hash.route, "Crypto & Tokens"),
    NexusModule("Hash Cracker", "Identify hash · dictionary attack", Icons.Default.LockOpen, NexusRoute.HashCrack.route, "Crypto & Tokens"),
    NexusModule("JWT Lab", "Decode header · payload · claims", Icons.Default.VpnKey, NexusRoute.Jwt.route, "Crypto & Tokens")
)

val nexusCategories: List<String> = nexusModules.map { it.category }.distinct()
