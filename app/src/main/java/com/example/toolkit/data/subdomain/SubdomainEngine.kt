package com.example.toolkit.data.subdomain

import com.example.toolkit.data.network.HttpClients
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.Request
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

data class SubdomainHit(
    val host: String,
    val ips: List<String>,
    val cname: String? = null,
    val httpStatus: Int? = null,
    val evidence: String = "dns",
    val note: String? = null
)

class SubdomainEngine {

    companion object {
        val WORDLIST = listOf(
            "www", "mail", "ftp", "webmail", "smtp", "pop", "imap", "ns1", "ns2", "ns3",
            "dns", "dns1", "dns2", "vpn", "api", "api2", "dev", "devel", "development",
            "staging", "stage", "test", "testing", "qa", "uat", "preprod", "prod",
            "admin", "administrator", "portal", "app", "apps", "mobile", "m", "cdn",
            "cdn1", "cdn2", "static", "assets", "img", "images", "media", "video",
            "blog", "shop", "store", "secure", "login", "auth", "sso", "oauth", "id",
            "account", "accounts", "dashboard", "panel", "cpanel", "whm", "webdisk",
            "remote", "git", "gitlab", "github", "bitbucket", "jenkins", "ci", "cd",
            "status", "monitor", "monitoring", "grafana", "prometheus", "kibana",
            "db", "database", "mysql", "postgres", "pgsql", "redis", "mongo", "mongodb",
            "elastic", "elasticsearch", "beta", "alpha", "demo", "sandbox", "lab",
            "internal", "intranet", "extranet", "corp", "corporate", "office", "hr",
            "crm", "erp", "support", "help", "helpdesk", "docs", "documentation",
            "wiki", "forum", "community", "news", "edge", "proxy", "gw", "gateway",
            "fw", "firewall", "bastion", "jump", "ssh", "rdp", "vdi", "cloud",
            "aws", "azure", "gcp", "k8s", "kubernetes", "docker", "registry",
            "npm", "nexus", "artifactory", "sonar", "sonarqube", "jira", "confluence",
            "slack", "teams", "zoom", "meet", "calendar", "drive", "files", "share",
            "backup", "bak", "old", "new", "v1", "v2", "v3", "www2", "www3",
            "mx", "mx1", "mx2", "autodiscover", "lyncdiscover", "sip", "sipdir",
            "enterpriseregistration", "enterpriseenrollment", "connect", "gateway1",
            "mail1", "mail2", "owa", "exchange", "outlook", "office365", "spo",
            "sharepoint", "onedrive", "teams", "intune", "mdm", "enroll",
            "pay", "payment", "payments", "billing", "invoice", "checkout",
            "cart", "order", "orders", "tracking", "track", "statuspage",
            "metrics", "logs", "log", "elk", "sentry", "error", "errors",
            "upload", "uploads", "download", "downloads", "static1", "media1",
            "img1", "cache", "proxy1", "lb", "loadbalancer", "haproxy", "nginx",
            "origin", "origin1", "backend", "frontend", "fe", "be", "svc",
            "service", "services", "ws", "wss", "socket", "realtime", "rt",
            "push", "notify", "notification", "notifications", "sms", "email",
            "newsletter", "marketing", "ads", "ad", "analytics", "stats", "piwik",
            "matomo", "gtm", "tag", "cms", "wp", "wordpress", "drupal", "joomla",
            "magento", "shopify", "storefront", "catalog", "search", "solr",
            "algolia", "es", "queue", "mq", "rabbit", "kafka", "zookeeper",
            "consul", "vault", "secrets", "config", "cfg", "settings", "env",
            "info", "about", "contact", "careers", "jobs", "press", "investor",
            "investors", "ir", "partners", "partner", "affiliate", "affiliates",
            "reseller", "client", "clients", "customer", "customers", "my",
            "user", "users", "member", "members", "profile", "profiles",
            "home", "homepage", "landing", "lp", "go", "link", "links", "url",
            "short", "s", "t", "r", "u", "i", "x", "z"
        )
    }

    sealed class Event {
        data class Started(
            val domain: String,
            val total: Int,
            val wildcard: Boolean,
            val wildcardIps: List<String>
        ) : Event()

        data class Progress(
            val scanned: Int,
            val total: Int,
            val hit: SubdomainHit?
        ) : Event()

        data class Completed(
            val hits: List<SubdomainHit>,
            val wildcard: Boolean,
            val filteredWildcards: Int
        ) : Event()

        data class Error(val message: String) : Event()
    }

    private val http = HttpClients.default.newBuilder()
        .followRedirects(true)
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(7, TimeUnit.SECONDS)
        .build()

    fun scan(rawDomain: String, words: List<String> = WORDLIST): Flow<Event> = callbackFlow {
        val domain = normalizeDomain(rawDomain)
        if (domain == null) {
            trySend(Event.Error("Enter a valid domain, e.g. example.com"))
            close()
            return@callbackFlow
        }

        val job = launch(Dispatchers.IO) {
            try {
                val wildcard = detectWildcard(domain)
                trySend(
                    Event.Started(
                        domain = domain,
                        total = words.size,
                        wildcard = wildcard.detected,
                        wildcardIps = wildcard.ips
                    )
                )

                val hits = mutableListOf<SubdomainHit>()
                var filtered = 0
                var scanned = 0
                val semaphore = Semaphore(16)

                coroutineScope {
                    words.map { word ->
                        async {
                            semaphore.withPermit {
                                val host = "$word.$domain"
                                val candidate = probeHost(host)
                                val accepted = candidate?.let { hit ->
                                    if (isWildcardFalsePositive(hit, wildcard)) {
                                        synchronized(hits) { filtered++ }
                                        null
                                    } else hit
                                }
                                val progress = synchronized(hits) {
                                    scanned++
                                    if (accepted != null) hits += accepted
                                    Event.Progress(scanned, words.size, accepted)
                                }
                                trySend(progress)
                            }
                        }
                    }.awaitAll()
                }

                trySend(
                    Event.Completed(
                        hits = hits.sortedBy { it.host },
                        wildcard = wildcard.detected,
                        filteredWildcards = filtered
                    )
                )
            } catch (e: Exception) {
                trySend(Event.Error(e.message ?: "Scan failed"))
            } finally {
                close()
            }
        }
        awaitClose { job.cancel() }
    }

    private data class WildcardInfo(
        val detected: Boolean,
        val ips: List<String>,
        val cname: String?,
        val httpFingerprint: String?
    )

    private fun normalizeDomain(raw: String): String? {
        val domain = raw.trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .substringBefore("/")
            .substringBefore(":")
            .lowercase()
            .removePrefix("www.")
        return domain.takeIf { it.isNotBlank() && it.contains('.') && !it.startsWith('.') }
    }

    private fun detectWildcard(domain: String): WildcardInfo {
        val probes = (1..3).map {
            "nx-${UUID.randomUUID().toString().take(12)}.${domain}"
        }
        val results = probes.mapNotNull { probeHost(it) }
        if (results.isEmpty()) {
            return WildcardInfo(false, emptyList(), null, null)
        }
        // If random names resolve → wildcard (or catch-all) DNS
        val ips = results.flatMap { it.ips }.distinct().sorted()
        val cname = results.firstOrNull()?.cname
        val fp = results.firstOrNull()?.let { httpFingerprint(it.host) }
        return WildcardInfo(true, ips, cname, fp)
    }

    private fun probeHost(host: String): SubdomainHit? {
        val doh = dohLookup(host)
        if (doh.ips.isEmpty() && doh.cname == null) {
            return null
        }
        val httpStatus = tryHttpStatus(host)
        val evidence = when {
            doh.cname != null && doh.ips.isNotEmpty() -> "doh+cname"
            doh.cname != null -> "cname"
            httpStatus != null -> "doh+http"
            else -> "doh"
        }
        return SubdomainHit(
            host = host,
            ips = doh.ips,
            cname = doh.cname,
            httpStatus = httpStatus,
            evidence = evidence,
            note = null
        )
    }

    private data class DohAnswer(val ips: List<String>, val cname: String?)

    private fun dohLookup(host: String): DohAnswer {
        val a = dohType(host, "A", matchNameOnly = false)
        val aaaa = dohType(host, "AAAA", matchNameOnly = false)
        val cname = dohType(host, "CNAME", matchNameOnly = true).firstOrNull()
        val ips = (a + aaaa)
            .map { it.trim().trim('"').lowercase() }
            .filter { it.isNotBlank() && (it.contains('.') || it.contains(':')) }
            .filter { !it.equals(host, ignoreCase = true) }
            .distinct()
        return DohAnswer(ips, cname?.trim()?.trim('.')?.lowercase())
    }

    private fun dohType(host: String, type: String, matchNameOnly: Boolean = false): List<String> {
        return try {
            val request = Request.Builder()
                .url("https://cloudflare-dns.com/dns-query?name=$host&type=$type")
                .header("Accept", "application/dns-json")
                .header("User-Agent", "NEXUS-Toolkit/1.0")
                .get()
                .build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val json = JSONObject(response.body?.string().orEmpty())
                // Status 3 = NXDOMAIN — host does not exist
                if (json.optInt("Status", -1) == 3) return emptyList()
                val answers = json.optJSONArray("Answer") ?: return emptyList()
                val hostNorm = host.trimEnd('.').lowercase()
                val values = mutableListOf<String>()
                for (i in 0 until answers.length()) {
                    val obj = answers.getJSONObject(i)
                    val name = obj.optString("name").trimEnd('.').lowercase()
                    val data = obj.optString("data").trim()
                    if (data.isBlank()) continue
                    // For CNAME keep only records for the queried host.
                    // For A/AAAA keep chain answers (CNAME targets resolve to IPs).
                    if (matchNameOnly && name != hostNorm) continue
                    values += data
                }
                values
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun isWildcardFalsePositive(hit: SubdomainHit, wildcard: WildcardInfo): Boolean {
        if (!wildcard.detected) return false

        val sameIps = hit.ips.isNotEmpty() &&
            hit.ips.map { it.lowercase() }.toSet() == wildcard.ips.map { it.lowercase() }.toSet()

        val sameCname = hit.cname != null &&
            wildcard.cname != null &&
            hit.cname.equals(wildcard.cname, ignoreCase = true)

        // Different CNAME → real subdomain even on shared CDN IPs
        if (hit.cname != null && wildcard.cname != null &&
            !hit.cname.equals(wildcard.cname, ignoreCase = true)
        ) {
            return false
        }

        // Different IPs → real
        if (hit.ips.isNotEmpty() && !sameIps) {
            return false
        }

        // Same IPs / same CNAME as wildcard → compare HTTP fingerprint
        if (sameIps || sameCname || (hit.ips.isEmpty() && hit.cname == null)) {
            val fp = httpFingerprint(hit.host)
            if (fp != null && wildcard.httpFingerprint != null && fp != wildcard.httpFingerprint) {
                return false // different HTTP response → keep
            }
            return true // identical to wildcard catch-all → drop
        }

        return false
    }

    private fun tryHttpStatus(host: String): Int? {
        for (scheme in listOf("https", "http")) {
            try {
                val request = Request.Builder()
                    .url("$scheme://$host/")
                    .header("User-Agent", "NEXUS-Toolkit/1.0")
                    .head()
                    .build()
                http.newCall(request).execute().use { return it.code }
            } catch (_: Exception) {
                continue
            }
        }
        return null
    }

    private fun httpFingerprint(host: String): String? {
        for (scheme in listOf("https", "http")) {
            try {
                val request = Request.Builder()
                    .url("$scheme://$host/")
                    .header("User-Agent", "NEXUS-Toolkit/1.0")
                    .get()
                    .build()
                http.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty().take(2000)
                    val title = Regex(
                        "<title[^>]*>(.*?)</title>",
                        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
                    ).find(body)?.groupValues?.getOrNull(1)?.trim().orEmpty()
                    val server = response.header("Server").orEmpty()
                    val len = response.header("Content-Length") ?: body.length.toString()
                    return "${response.code}|$server|$len|${title.take(80)}"
                }
            } catch (_: Exception) {
                continue
            }
        }
        return null
    }
}
