package com.example.toolkit.data.webvuln

import com.example.toolkit.data.network.HttpClients
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import kotlin.math.abs

data class VulnFinding(
    val type: String,
    val param: String,
    val severity: String,   // HIGH | MEDIUM | LOW | INFO
    val payload: String,
    val evidence: String,
    val requestUrl: String
)

data class WebVulnResult(
    val url: String,
    val testedParams: List<String> = emptyList(),
    val findings: List<VulnFinding> = emptyList(),
    val notes: List<String> = emptyList(),
    val error: String? = null
)

/**
 * Active injection scanner. For every query parameter it establishes a
 * baseline response, then injects XSS, SQLi (error / boolean / time-based),
 * open-redirect, path-traversal and SSRF payloads, comparing reflections,
 * error signatures, response-length deltas and response timing to flag likely
 * vulnerabilities. Authorized-targets-only, single-request-per-payload.
 */
class WebVulnEngine {

    private val client = HttpClients.default

    private val xssPayloads = listOf(
        "<nexus>xss'\"", "\"><script>nx()</script>", "'\"><img src=x onerror=nx>"
    )
    private val sqlErrorPayloads = listOf("'", "\"", "')", "';")
    private val sqlErrorSignatures = listOf(
        "you have an error in your sql syntax", "warning: mysql", "unclosed quotation mark",
        "quoted string not properly terminated", "pg_query", "sqlite3::", "sqlstate",
        "odbc sql server driver", "ora-01756", "ora-00933", "psqlexception", "mysql_fetch"
    )
    private val redirectPayloads = listOf("https://nexus-evil.example", "//nexus-evil.example", "/\\nexus-evil.example")
    private val traversalPayloads = listOf("../../../../etc/passwd", "..%2f..%2f..%2f..%2fetc%2fpasswd", "....//....//etc/passwd")
    private val traversalSignatures = listOf("root:x:0:0:", "root:.*:0:0:", "\\[boot loader\\]", "; for 16-bit app support")
    private val ssrfPayloads = listOf("http://169.254.169.254/latest/meta-data/", "http://127.0.0.1:80/")

    suspend fun scan(rawUrl: String): WebVulnResult = withContext(Dispatchers.IO) {
        val httpUrl = normalize(rawUrl).toHttpUrlOrNull()
            ?: return@withContext WebVulnResult(rawUrl, error = "Invalid URL")
        val params = httpUrl.queryParameterNames.toList()
        if (params.isEmpty()) {
            return@withContext WebVulnResult(httpUrl.toString(),
                notes = listOf("No query parameters found. Add parameters, e.g. ?id=1&q=test, to test injection points."))
        }

        val findings = ArrayList<VulnFinding>()
        val baseline = fetch(httpUrl.toString())

        for (param in params) {
            // Reflected XSS
            for (p in xssPayloads) {
                val u = replaceParam(httpUrl, param, p)
                val resp = fetch(u)
                if (resp.body.contains(p, ignoreCase = false)) {
                    findings.add(VulnFinding("Reflected XSS", param, "HIGH", p,
                        "Payload reflected unencoded in response body.", u))
                    break
                }
            }
            // SQLi error-based
            for (p in sqlErrorPayloads) {
                val u = replaceParam(httpUrl, param, "1$p")
                val resp = fetch(u)
                val sig = sqlErrorSignatures.firstOrNull { resp.body.lowercase().contains(it) }
                if (sig != null) {
                    findings.add(VulnFinding("SQL Injection (error-based)", param, "HIGH", "1$p",
                        "SQL error signature in response: \"$sig\".", u))
                    break
                }
            }
            // SQLi boolean-based
            run {
                val trueU = replaceParam(httpUrl, param, "1' AND '1'='1")
                val falseU = replaceParam(httpUrl, param, "1' AND '1'='2")
                val t = fetch(trueU); val f = fetch(falseU)
                if (t.code == 200 && f.code == 200 && abs(t.body.length - f.body.length) > 40 &&
                    abs(t.body.length - baseline.body.length) < 40) {
                    findings.add(VulnFinding("SQL Injection (boolean-based)", param, "HIGH", "1' AND '1'='1 / ='2",
                        "TRUE/FALSE conditions produce different response sizes (Δ=${abs(t.body.length - f.body.length)}).", trueU))
                }
            }
            // SQLi time-based
            run {
                val payload = "1'; SELECT pg_sleep(4)-- -"
                val u = replaceParam(httpUrl, param, payload)
                val elapsed = timed(u)
                if (elapsed in 3500..11000 && baseline.elapsed < 2500) {
                    findings.add(VulnFinding("SQL Injection (time-based)", param, "HIGH", payload,
                        "Response delayed ${elapsed}ms vs baseline ${baseline.elapsed}ms.", u))
                }
            }
            // Open redirect
            for (p in redirectPayloads) {
                val u = replaceParam(httpUrl, param, p)
                val loc = fetchLocation(u)
                if (loc != null && (loc.contains("nexus-evil.example"))) {
                    findings.add(VulnFinding("Open Redirect", param, "MEDIUM", p,
                        "Location header redirects to attacker-controlled host: $loc", u))
                    break
                }
            }
            // Path traversal
            for (p in traversalPayloads) {
                val u = replaceParam(httpUrl, param, p)
                val resp = fetch(u)
                val sig = traversalSignatures.firstOrNull { Regex(it).containsMatchIn(resp.body) }
                if (sig != null) {
                    findings.add(VulnFinding("Path Traversal", param, "HIGH", p,
                        "System file contents leaked (pattern: $sig).", u))
                    break
                }
            }
            // SSRF (best-effort — reflected content / behavior change)
            for (p in ssrfPayloads) {
                val u = replaceParam(httpUrl, param, p)
                val resp = fetch(u)
                if (resp.body.contains("ami-id") || resp.body.contains("instance-id") ||
                    resp.body.contains("iam/security-credentials")) {
                    findings.add(VulnFinding("SSRF", param, "HIGH", p,
                        "Cloud metadata content returned in response.", u))
                    break
                }
            }
        }

        WebVulnResult(httpUrl.toString(), testedParams = params, findings = findings,
            notes = if (findings.isEmpty()) listOf("No injection vulnerabilities detected across ${params.size} parameter(s).") else emptyList())
    }

    private data class Resp(val code: Int, val body: String, val elapsed: Long)

    private fun fetch(url: String): Resp = try {
        val req = Request.Builder().url(url).header("User-Agent", "NEXUS-Toolkit/1.0").get().build()
        val start = System.currentTimeMillis()
        client.newCall(req).execute().use { r ->
            val body = r.body?.string()?.take(200_000) ?: ""
            Resp(r.code, body, System.currentTimeMillis() - start)
        }
    } catch (e: Exception) { Resp(0, "", 0) }

    private fun timed(url: String): Long = try {
        val req = Request.Builder().url(url).header("User-Agent", "NEXUS-Toolkit/1.0").get().build()
        val start = System.currentTimeMillis()
        client.newCall(req).execute().use { it.body?.string() }
        System.currentTimeMillis() - start
    } catch (e: Exception) { 0 }

    private fun fetchLocation(url: String): String? = try {
        val req = Request.Builder().url(url).header("User-Agent", "NEXUS-Toolkit/1.0").get().build()
        HttpClients.noRedirect.newCall(req).execute().use { it.header("Location") }
    } catch (e: Exception) { null }

    private fun replaceParam(base: okhttp3.HttpUrl, param: String, value: String): String {
        val b = base.newBuilder()
        b.removeAllQueryParameters(param)
        // preserve order roughly by re-adding after removal
        b.addQueryParameter(param, value)
        return b.build().toString()
    }

    private fun normalize(raw: String): String {
        var s = raw.trim()
        if (!s.startsWith("http://") && !s.startsWith("https://")) s = "https://$s"
        return s
    }
}
