package com.example.toolkit.data.wayback

import com.example.toolkit.data.network.HttpClients
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.net.URLEncoder

data class WaybackUrl(val url: String, val interesting: Boolean, val reason: String?)

data class WaybackResult(
    val domain: String,
    val urls: List<WaybackUrl> = emptyList(),
    val total: Int = 0,
    val params: List<String> = emptyList(),
    val error: String? = null
)

/**
 * Historical URL fetcher backed by the Wayback Machine CDX API. Pulls every
 * archived URL captured for a domain (and its subpaths), dedupes them, extracts
 * distinct query parameter names, and highlights interesting endpoints
 * (secrets, configs, API routes, files with sensitive extensions) for recon.
 */
class WaybackEngine {

    private val interestingExt = listOf(
        ".json", ".xml", ".sql", ".bak", ".old", ".zip", ".tar", ".gz", ".log",
        ".config", ".env", ".yaml", ".yml", ".pem", ".key", ".txt", ".csv"
    )
    private val interestingWords = listOf(
        "admin", "api", "token", "secret", "key", "password", "passwd", "config",
        "backup", "internal", "debug", "test", "graphql", "upload", "download", "redirect"
    )

    suspend fun fetch(rawDomain: String, includeSubs: Boolean = true, limit: Int = 5000): WaybackResult =
        withContext(Dispatchers.IO) {
            val domain = rawDomain.trim().removePrefix("https://").removePrefix("http://")
                .substringBefore("/").lowercase()
            if (domain.isBlank() || !domain.contains(".")) {
                return@withContext WaybackResult(domain, error = "Enter a valid domain.")
            }

            val matchTarget = if (includeSubs) "*.$domain/*" else "$domain/*"
            val api = "https://web.archive.org/cdx/search/cdx?url=" +
                URLEncoder.encode(matchTarget, "UTF-8") +
                "&output=text&fl=original&collapse=urlkey&limit=$limit"

            try {
                val req = Request.Builder().url(api)
                    .header("User-Agent", "NEXUS-Toolkit/1.0").get().build()
                HttpClients.default.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext WaybackResult(domain, error = "Wayback HTTP ${resp.code}")
                    val body = resp.body?.string().orEmpty()
                    val lines = body.split("\n").map { it.trim() }.filter { it.isNotBlank() }.distinct()
                    val params = sortedSetOf<String>()
                    val urls = lines.map { url ->
                        extractParams(url).forEach { params.add(it) }
                        val reason = interestReason(url)
                        WaybackUrl(url, reason != null, reason)
                    }.sortedByDescending { it.interesting }
                    WaybackResult(domain, urls, urls.size, params.toList())
                }
            } catch (e: Exception) {
                WaybackResult(domain, error = e.message ?: "Request failed")
            }
        }

    private fun extractParams(url: String): List<String> {
        val q = url.substringAfter("?", "")
        if (q.isBlank()) return emptyList()
        return q.split("&").mapNotNull { it.substringBefore("=").takeIf { p -> p.isNotBlank() } }
    }

    private fun interestReason(url: String): String? {
        val lower = url.lowercase()
        interestingExt.firstOrNull { lower.substringBefore("?").endsWith(it) }?.let { return "sensitive extension $it" }
        interestingWords.firstOrNull { lower.contains(it) }?.let { return "keyword: $it" }
        if (lower.contains("?") && lower.substringAfter("?").isNotBlank()) return "has parameters"
        return null
    }
}
