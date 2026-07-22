package com.example.toolkit.ui.navigation

sealed class NexusRoute(val route: String, val title: String) {
    data object Dashboard : NexusRoute("dashboard", "Dashboard")
    data object Recon : NexusRoute("recon", "Recon")
    data object Ports : NexusRoute("ports", "Port Scan")
    data object Traffic : NexusRoute("traffic", "Traffic")
    data object Osint : NexusRoute("osint", "OSINT")
    data object Api : NexusRoute("api", "API Lab")
    data object Hash : NexusRoute("hash", "Hash Lab")
    data object Dns : NexusRoute("dns", "DNS Dig")
    data object Subdomain : NexusRoute("subdomain", "Subdomains")
    data object Jwt : NexusRoute("jwt", "JWT Lab")
    data object Cve : NexusRoute("cve", "CVE Lookup")
    data object Ip : NexusRoute("ip", "IP Tools")
    data object Person : NexusRoute("person", "Person Search")
    data object Hibp : NexusRoute("hibp", "HIBP")
    data object WifiMonitor : NexusRoute("wifi_monitor", "Wi‑Fi Capture")
    data object LinuxTerminal : NexusRoute("linux_terminal", "Linux Terminal")
    data object DirScan : NexusRoute("dirscan", "Content Discovery")
    data object Fingerprint : NexusRoute("fingerprint", "Web Fingerprint")
    data object Whois : NexusRoute("whois", "WHOIS / RDAP")
    data object Cors : NexusRoute("cors", "CORS Scanner")
    data object HttpMethods : NexusRoute("httpmethods", "HTTP Methods")
    data object HashCrack : NexusRoute("hashcrack", "Hash Cracker")

    companion object {
        val modules = listOf(
            Recon, Ports, Traffic, Osint, Api,
            Hash, Dns, Subdomain, Jwt, Cve, Ip,
            Person, Hibp, WifiMonitor, LinuxTerminal,
            DirScan, Fingerprint, Whois, Cors, HttpMethods, HashCrack
        )
    }
}
