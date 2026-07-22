package com.example.toolkit.ui.navigation

sealed class NexusRoute(val route: String, val title: String) {
    data object Dashboard : NexusRoute("dashboard", "Dashboard")
    data object Settings : NexusRoute("settings", "Settings")
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
    data object ApkInspector : NexusRoute("apk_inspector", "APK Inspector")

    // Web App Testing
    data object Tls : NexusRoute("tls", "TLS/SSL Analyzer")
    data object Headers : NexusRoute("headers", "Security Headers")
    data object WebVuln : NexusRoute("webvuln", "Web Vuln Scanner")
    data object Fuzzer : NexusRoute("fuzzer", "Fuzzer")
    data object JsRecon : NexusRoute("jsrecon", "JS Recon")
    data object Exposed : NexusRoute("exposed", "Exposed Files")
    data object Graphql : NexusRoute("graphql", "GraphQL Inspector")
    data object Takeover : NexusRoute("takeover", "Subdomain Takeover")
    data object WebSocket : NexusRoute("websocket", "WebSocket Tester")

    // OSINT & Intel
    data object CrtSh : NexusRoute("crtsh", "CT Log Enum")
    data object Wayback : NexusRoute("wayback", "Wayback URLs")
    data object Exif : NexusRoute("exif", "EXIF Extractor")
    data object Dork : NexusRoute("dork", "Dork Builder")
    data object Shodan : NexusRoute("shodan", "Shodan Lookup")

    // Mobile & RE
    data object ApkAudit : NexusRoute("apk_audit", "APK Security Audit")
    data object DeepLink : NexusRoute("deeplink", "Deep-Link Tester")
    data object Firebase : NexusRoute("firebase", "Firebase Checker")
    data object DexScan : NexusRoute("dexscan", "DEX API Scanner")

    companion object {
        val modules = listOf(
            Recon, Ports, Traffic, Osint, Api,
            Hash, Dns, Subdomain, Jwt, Cve, Ip,
            Person, Hibp, WifiMonitor, LinuxTerminal,
            DirScan, Fingerprint, Whois, Cors, HttpMethods, HashCrack,
            ApkInspector,
            Tls, Headers, WebVuln, Fuzzer, JsRecon, Exposed, Graphql, Takeover, WebSocket,
            CrtSh, Wayback, Exif, Dork, Shodan,
            ApkAudit, DeepLink, Firebase, DexScan
        )
    }
}
