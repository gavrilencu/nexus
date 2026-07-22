package com.example.toolkit.data.takeover

import com.example.toolkit.data.network.HttpClients
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

data class TakeoverResult(
    val host: String,
    val cname: List<String> = emptyList(),
    val service: String? = null,
    val vulnerable: Boolean = false,
    val severity: String = "INFO",
    val fingerprint: String? = null,
    val detail: String = "",
    val error: String? = null
)

/**
 * Subdomain takeover detector. Resolves the target's CNAME chain, matches the
 * canonical name against known SaaS provider domains, then fetches the host and
 * checks the response body against each provider's "unclaimed resource"
 * fingerprint to confirm a dangling/takeable record.
 */
class TakeoverEngine {

    private data class Sig(val service: String, val cnameMarkers: List<String>, val bodyFingerprint: String)

    private val signatures = listOf(
        Sig("GitHub Pages", listOf("github.io", "githubusercontent"), "There isn't a GitHub Pages site here"),
        Sig("Amazon S3", listOf("s3.amazonaws.com", "s3-website", "s3.", ".amazonaws.com"), "NoSuchBucket"),
        Sig("Heroku", listOf("herokuapp.com", "herokudns.com", "herokussl.com"), "No such app"),
        Sig("Shopify", listOf("myshopify.com", "shops.myshopify.com"), "Sorry, this shop is currently unavailable"),
        Sig("Fastly", listOf("fastly.net"), "Fastly error: unknown domain"),
        Sig("Zendesk", listOf("zendesk.com"), "Help Center Closed"),
        Sig("Tumblr", listOf("domains.tumblr.com"), "Whatever you were looking for doesn't currently exist"),
        Sig("Surge.sh", listOf("surge.sh"), "project not found"),
        Sig("Bitbucket", listOf("bitbucket.io"), "Repository not found"),
        Sig("Ghost", listOf("ghost.io"), "The thing you were looking for is no longer here"),
        Sig("Pantheon", listOf("pantheonsite.io"), "The gods are wise, but do not know of the site"),
        Sig("Unbounce", listOf("unbouncepages.com"), "The requested URL was not found on this server"),
        Sig("Cargo", listOf("cargocollective.com"), "404 Not Found"),
        Sig("Webflow", listOf("proxy-ssl.webflow.com", "webflow.io"), "The page you are looking for doesn't exist"),
        Sig("Wordpress", listOf("wordpress.com"), "Do you want to register"),
        Sig("Netlify", listOf("netlify.app", "netlify.com"), "Not Found - Request ID"),
        Sig("Readme.io", listOf("readme.io"), "Project doesnt exist"),
        Sig("Help Scout", listOf("helpscoutdocs.com"), "No settings were found for this company"),
        Sig("Azure", listOf("azurewebsites.net", "cloudapp.net", "trafficmanager.net"), "404 Web Site not found")
    )

    suspend fun check(rawHost: String): TakeoverResult = withContext(Dispatchers.IO) {
        val host = rawHost.trim().removePrefix("https://").removePrefix("http://").substringBefore("/").lowercase()
        if (host.isBlank()) return@withContext TakeoverResult(host, error = "Invalid host")

        val cnames = resolveCname(host)
        if (cnames.isEmpty()) {
            return@withContext TakeoverResult(host, cname = emptyList(),
                detail = "No CNAME record. Takeover via dangling CNAME is unlikely (host points directly to A/AAAA).")
        }

        val chain = cnames.joinToString(" → ")
        val matched = signatures.firstOrNull { sig ->
            cnames.any { c -> sig.cnameMarkers.any { c.contains(it, ignoreCase = true) } }
        }

        if (matched == null) {
            return@withContext TakeoverResult(host, cname = cnames,
                detail = "CNAME points to $chain — not a known takeover-prone provider (or already claimed).")
        }

        val body = fetchBody(host)
        val fingerprintHit = body?.contains(matched.bodyFingerprint, ignoreCase = true) == true

        TakeoverResult(
            host = host, cname = cnames, service = matched.service,
            vulnerable = fingerprintHit,
            severity = if (fingerprintHit) "HIGH" else "MEDIUM",
            fingerprint = matched.bodyFingerprint,
            detail = if (fingerprintHit)
                "CNAME points to ${matched.service} ($chain) AND the unclaimed-resource fingerprint was found → likely takeable."
            else
                "CNAME points to ${matched.service} ($chain). Fingerprint not confirmed in body — verify manually (may already be claimed)."
        )
    }

    private fun resolveCname(host: String): List<String> {
        return try {
            val req = Request.Builder()
                .url("https://cloudflare-dns.com/dns-query?name=$host&type=CNAME")
                .header("Accept", "application/dns-json")
                .header("User-Agent", "NEXUS-Toolkit/1.0").get().build()
            HttpClients.default.newCall(req).execute().use { resp ->
                val json = JSONObject(resp.body?.string().orEmpty())
                val answers = json.optJSONArray("Answer") ?: return emptyList()
                val out = ArrayList<String>()
                for (i in 0 until answers.length()) {
                    val a = answers.getJSONObject(i)
                    if (a.optInt("type") == 5) out.add(a.optString("data").trim('"').trimEnd('.'))
                }
                out
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun fetchBody(host: String): String? = try {
        val req = Request.Builder().url("https://$host/")
            .header("User-Agent", "Mozilla/5.0 NEXUS-Toolkit/1.0").get().build()
        HttpClients.default.newCall(req).execute().use { it.body?.string()?.take(200_000) }
    } catch (_: Exception) {
        try {
            val req = Request.Builder().url("http://$host/")
                .header("User-Agent", "Mozilla/5.0 NEXUS-Toolkit/1.0").get().build()
            HttpClients.default.newCall(req).execute().use { it.body?.string()?.take(200_000) }
        } catch (_: Exception) { null }
    }
}
