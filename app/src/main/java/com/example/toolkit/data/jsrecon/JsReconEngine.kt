package com.example.toolkit.data.jsrecon

import com.example.toolkit.data.network.HttpClients
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request

data class SecretHit(val type: String, val value: String, val source: String)

data class JsReconResult(
    val url: String,
    val jsFiles: List<String> = emptyList(),
    val endpoints: List<String> = emptyList(),
    val secrets: List<SecretHit> = emptyList(),
    val error: String? = null
)

/**
 * LinkFinder-style JS reconnaissance. Downloads the page, discovers all linked
 * (and inline) JavaScript, then regex-mines each script for hidden endpoints
 * (relative/absolute paths, API routes) and leaked secrets (API keys, tokens,
 * JWTs, cloud keys).
 */
class JsReconEngine {

    private val client = HttpClients.default

    // LinkFinder's classic endpoint regex (paths, api routes, files).
    private val endpointRegex = Regex(
        "(?:\"|')(" +
            "(?:/|\\.\\./|\\./)[^\"'><,;| *()(%%$^/\\\\\\[\\]][^\"'><,;|()]{1,300}" +
            "|[a-zA-Z0-9_\\-/]{1,}/[a-zA-Z0-9_\\-/]{1,}\\.(?:php|asp|aspx|jsp|json|js|xml|do|action)(?:[?][^\"']{0,120})?" +
            "|[a-zA-Z0-9_\\-]{1,}\\.(?:php|asp|aspx|jsp|json|action|html|js)(?:[?][^\"']{0,120})?" +
            ")(?:\"|')"
    )

    private val secretPatterns = listOf(
        "Google API Key" to Regex("AIza[0-9A-Za-z_\\-]{35}"),
        "Google OAuth" to Regex("ya29\\.[0-9A-Za-z_\\-]+"),
        "AWS Access Key" to Regex("A(?:KIA|SIA|GPA|IDA|ROA)[0-9A-Z]{16}"),
        "Slack Token" to Regex("xox[baprs]-[0-9A-Za-z\\-]{10,48}"),
        "Slack Webhook" to Regex("https://hooks\\.slack\\.com/services/[A-Za-z0-9/]+"),
        "GitHub Token" to Regex("gh[pousr]_[0-9A-Za-z]{36}"),
        "Stripe Key" to Regex("(?:sk|pk|rk)_(?:live|test)_[0-9A-Za-z]{16,}"),
        "Firebase URL" to Regex("[a-z0-9-]+\\.firebaseio\\.com"),
        "Firebase (App)" to Regex("[a-z0-9-]+\\.firebaseapp\\.com"),
        "JWT" to Regex("eyJ[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{5,}"),
        "Generic API key" to Regex("(?i)(?:api[_-]?key|apikey|secret|token|access[_-]?token)\\s*[:=]\\s*[\"']([A-Za-z0-9_\\-]{16,})[\"']"),
        "Private Key" to Regex("-----BEGIN (?:RSA |EC |DSA |OPENSSH )?PRIVATE KEY-----"),
        "Mailgun Key" to Regex("key-[0-9a-zA-Z]{32}"),
        "Twilio SID" to Regex("AC[0-9a-fA-F]{32}")
    )

    suspend fun analyze(rawUrl: String): JsReconResult = withContext(Dispatchers.IO) {
        val base = normalize(rawUrl)
        val baseUrl = base.toHttpUrlOrNull() ?: return@withContext JsReconResult(rawUrl, error = "Invalid URL")

        val html = try { fetch(base) } catch (e: Exception) {
            return@withContext JsReconResult(base, error = e.message ?: "Fetch failed")
        }

        val jsUrls = LinkedHashSet<String>()
        Regex("<script[^>]+src\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
            .findAll(html).forEach { m ->
                baseUrl.resolve(m.groupValues[1])?.let { jsUrls.add(it.toString()) }
            }

        val endpoints = sortedSetOf<String>()
        val secrets = LinkedHashSet<SecretHit>()

        // Inline scripts of the page.
        mineText(html, base, endpoints, secrets)

        // External JS in parallel.
        coroutineScope {
            jsUrls.take(40).map { js ->
                async {
                    val content = runCatching { fetch(js) }.getOrNull() ?: return@async
                    mineText(content, js, endpoints, secrets)
                }
            }.awaitAll()
        }

        JsReconResult(
            url = base,
            jsFiles = jsUrls.toList(),
            endpoints = endpoints.toList().take(500),
            secrets = secrets.toList()
        )
    }

    private fun mineText(text: String, source: String, endpoints: MutableSet<String>, secrets: MutableSet<SecretHit>) {
        endpointRegex.findAll(text).forEach { m ->
            val ep = m.groupValues[1].trim()
            if (ep.length in 2..300 && !ep.startsWith("//w3.org") && !ep.contains("\n")) endpoints.add(ep)
        }
        secretPatterns.forEach { (type, rx) ->
            rx.findAll(text).take(10).forEach { m ->
                val value = if (m.groupValues.size > 1 && m.groupValues[1].isNotBlank()) m.groupValues[1] else m.value
                secrets.add(SecretHit(type, value.take(80), source))
            }
        }
    }

    private fun fetch(url: String): String {
        val req = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0 NEXUS-Toolkit/1.0").get().build()
        client.newCall(req).execute().use { r ->
            return r.body?.string()?.take(2_000_000) ?: ""
        }
    }

    private fun normalize(raw: String): String {
        var s = raw.trim()
        if (!s.startsWith("http://") && !s.startsWith("https://")) s = "https://$s"
        return s
    }
}
