package com.example.toolkit.data.fingerprint

import com.example.toolkit.data.network.HttpClients
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

data class TechFinding(
    val name: String,
    val category: String,
    val evidence: String
)

data class FingerprintResult(
    val url: String,
    val finalUrl: String? = null,
    val status: Int? = null,
    val server: String? = null,
    val poweredBy: String? = null,
    val title: String? = null,
    val technologies: List<TechFinding> = emptyList(),
    val cookies: List<String> = emptyList(),
    val securityHeaders: Map<String, String> = emptyMap(),
    val error: String? = null
)

/**
 * Passive web technology fingerprinter (like WhatWeb / Wappalyzer): fetches the
 * page once and infers the stack (server, language, framework, CMS, CDN/WAF,
 * JS libraries) from response headers, Set-Cookie names and HTML markers.
 */
class FingerprintEngine {

    private data class Sig(
        val name: String,
        val category: String,
        val test: (Ctx) -> String?
    )

    private class Ctx(
        val headers: Map<String, String>,
        val cookies: List<String>,
        val bodyLower: String,
        val body: String
    ) {
        fun header(name: String): String? = headers[name.lowercase()]
        fun cookie(prefix: String): Boolean =
            cookies.any { it.substringBefore("=").trim().lowercase().startsWith(prefix.lowercase()) }
        fun bodyHas(vararg needles: String): Boolean =
            needles.any { bodyLower.contains(it.lowercase()) }
    }

    suspend fun analyze(rawUrl: String): FingerprintResult = withContext(Dispatchers.IO) {
        val url = normalize(rawUrl) ?: return@withContext FingerprintResult(rawUrl, error = "Invalid URL")
        try {
            val request = Request.Builder()
                .url(url)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0 Mobile Safari/537.36"
                )
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .get()
                .build()

            HttpClients.default.newCall(request).execute().use { resp ->
                val headers = LinkedHashMap<String, String>()
                for (i in 0 until resp.headers.size) {
                    val n = resp.headers.name(i).lowercase()
                    // Keep last value for single-valued headers; join Set-Cookie separately below.
                    if (n != "set-cookie") headers[n] = resp.headers.value(i)
                }
                val cookies = resp.headers("set-cookie")
                val body = resp.peekBody(400_000L).string()
                val bodyLower = body.lowercase()
                val ctx = Ctx(headers, cookies, bodyLower, body)

                val techs = LinkedHashSet<TechFinding>()
                for (sig in SIGNATURES) {
                    val evidence = sig.test(ctx)
                    if (evidence != null) techs.add(TechFinding(sig.name, sig.category, evidence))
                }

                FingerprintResult(
                    url = url,
                    finalUrl = resp.request.url.toString(),
                    status = resp.code,
                    server = ctx.header("server"),
                    poweredBy = ctx.header("x-powered-by"),
                    title = extractTitle(body),
                    technologies = techs.toList(),
                    cookies = cookies.map { it.substringBefore(";") },
                    securityHeaders = collectSecurityHeaders(headers)
                )
            }
        } catch (e: Exception) {
            FingerprintResult(url, error = e.message ?: "Request failed")
        }
    }

    private fun collectSecurityHeaders(headers: Map<String, String>): Map<String, String> {
        val wanted = listOf(
            "strict-transport-security", "content-security-policy", "x-frame-options",
            "x-content-type-options", "referrer-policy", "permissions-policy"
        )
        return wanted.associateWith { headers[it] ?: "— lipsă" }
    }

    private fun extractTitle(body: String): String? {
        val m = Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(body) ?: return null
        return m.groupValues[1].trim().replace(Regex("\\s+"), " ").take(140).ifBlank { null }
    }

    private fun normalize(raw: String): String? {
        var s = raw.trim()
        if (s.isBlank()) return null
        if (!s.startsWith("http://") && !s.startsWith("https://")) s = "https://$s"
        return s
    }

    private val SIGNATURES: List<Sig> = listOf(
        // --- Servers ---
        Sig("Nginx", "Server") { c -> c.header("server")?.takeIf { it.contains("nginx", true) } },
        Sig("Apache", "Server") { c -> c.header("server")?.takeIf { it.contains("apache", true) } },
        Sig("Microsoft IIS", "Server") { c -> c.header("server")?.takeIf { it.contains("iis", true) } },
        Sig("LiteSpeed", "Server") { c -> c.header("server")?.takeIf { it.contains("litespeed", true) } },
        Sig("OpenResty", "Server") { c -> c.header("server")?.takeIf { it.contains("openresty", true) } },
        Sig("Caddy", "Server") { c -> c.header("server")?.takeIf { it.contains("caddy", true) } },

        // --- Languages ---
        Sig("PHP", "Language") { c ->
            c.header("x-powered-by")?.takeIf { it.contains("php", true) }
                ?: if (c.cookie("PHPSESSID")) "cookie PHPSESSID" else null
        },
        Sig("ASP.NET", "Language") { c ->
            c.header("x-powered-by")?.takeIf { it.contains("asp.net", true) }
                ?: c.header("x-aspnet-version")?.let { "X-AspNet-Version: $it" }
                ?: if (c.cookie("ASP.NET_SessionId")) "cookie ASP.NET_SessionId" else null
        },
        Sig("Java / Servlet", "Language") { c -> if (c.cookie("JSESSIONID")) "cookie JSESSIONID" else null },
        Sig("Python", "Language") { c -> c.header("server")?.takeIf { it.contains("gunicorn", true) || it.contains("werkzeug", true) || it.contains("uvicorn", true) } },
        Sig("Node.js", "Language") { c -> c.header("x-powered-by")?.takeIf { it.contains("express", true) } ?: if (c.cookie("connect.sid")) "cookie connect.sid" else null },
        Sig("Ruby on Rails", "Framework") { c -> if (c.cookie("_rails") || c.bodyHas("csrf-param") && c.bodyHas("authenticity_token")) "Rails markers" else null },

        // --- Frameworks / CMS ---
        Sig("WordPress", "CMS") { c ->
            when {
                c.bodyHas("/wp-content/", "/wp-includes/") -> "path /wp-content/"
                c.cookie("wordpress_") || c.cookie("wp-settings") -> "cookie WordPress"
                Regex("<meta[^>]+generator[^>]+wordpress", RegexOption.IGNORE_CASE).containsMatchIn(c.body) -> "meta generator"
                else -> null
            }
        },
        Sig("Joomla", "CMS") { c -> if (c.bodyHas("/media/jui/", "joomla") || Regex("generator[^>]+joomla", RegexOption.IGNORE_CASE).containsMatchIn(c.body)) "Joomla markers" else null },
        Sig("Drupal", "CMS") { c -> if (c.bodyHas("/sites/default/", "drupal-settings-json") || c.header("x-generator")?.contains("drupal", true) == true) "Drupal markers" else null },
        Sig("Magento", "CMS") { c -> if (c.bodyHas("/static/version", "mage/cookies") || c.cookie("X-Magento")) "Magento markers" else null },
        Sig("Shopify", "CMS") { c -> if (c.bodyHas("cdn.shopify.com", "shopify") || c.header("x-shopid") != null) "Shopify markers" else null },
        Sig("Laravel", "Framework") { c -> if (c.cookie("laravel_session") || c.cookie("XSRF-TOKEN")) "cookie laravel_session" else null },
        Sig("Django", "Framework") { c -> if (c.cookie("csrftoken") || c.cookie("sessionid") && c.bodyHas("csrfmiddlewaretoken")) "Django CSRF markers" else null },
        Sig("Next.js", "Framework") { c -> if (c.bodyHas("__next_data__", "/_next/static")) "__NEXT_DATA__" else null },
        Sig("Nuxt.js", "Framework") { c -> if (c.bodyHas("__nuxt__", "/_nuxt/")) "Nuxt markers" else null },
        Sig("React", "JS Library") { c -> if (c.bodyHas("data-reactroot", "react-dom", "_reactlisten")) "React markers" else null },
        Sig("Vue.js", "JS Library") { c -> if (c.bodyHas("data-v-", "vue.js", "__vue__")) "Vue markers" else null },
        Sig("Angular", "JS Library") { c -> if (c.bodyHas("ng-version", "ng-app", "angular.js")) "Angular markers" else null },
        Sig("jQuery", "JS Library") { c -> if (c.bodyHas("jquery.min.js", "jquery.js", "/jquery-")) "jQuery script" else null },
        Sig("Bootstrap", "UI") { c -> if (c.bodyHas("bootstrap.min.css", "bootstrap.css", "class=\"navbar")) "Bootstrap CSS" else null },

        // --- CDN / WAF / Proxy ---
        Sig("Cloudflare", "CDN / WAF") { c ->
            when {
                c.header("cf-ray") != null -> "header cf-ray"
                c.header("server")?.contains("cloudflare", true) == true -> "Server: cloudflare"
                c.cookie("__cf") -> "cookie __cf*"
                else -> null
            }
        },
        Sig("Amazon CloudFront", "CDN") { c -> if (c.header("x-amz-cf-id") != null || c.header("via")?.contains("cloudfront", true) == true) "x-amz-cf-id" else null },
        Sig("Fastly", "CDN") { c -> if (c.header("x-served-by")?.contains("cache", true) == true && c.header("x-cache") != null || c.header("fastly-io-info") != null) "Fastly headers" else null },
        Sig("Akamai", "CDN") { c -> if (c.header("x-akamai-transformed") != null || c.header("server")?.contains("akamai", true) == true) "Akamai headers" else null },
        Sig("Varnish", "Proxy") { c -> if (c.header("via")?.contains("varnish", true) == true || c.header("x-varnish") != null) "Varnish headers" else null },
        Sig("Sucuri WAF", "WAF") { c -> if (c.header("x-sucuri-id") != null || c.header("server")?.contains("sucuri", true) == true) "Sucuri headers" else null },
        Sig("Google (GSE)", "Server") { c -> c.header("server")?.takeIf { it.contains("gws", true) || it.equals("gse", true) } },

        // --- Analytics ---
        Sig("Google Analytics", "Analytics") { c -> if (c.bodyHas("google-analytics.com", "gtag(", "googletagmanager.com")) "GA/GTM script" else null }
    )
}
