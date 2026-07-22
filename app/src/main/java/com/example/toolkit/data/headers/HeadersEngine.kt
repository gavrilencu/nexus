package com.example.toolkit.data.headers

import com.example.toolkit.data.network.HttpClients
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

data class HeaderCheck(
    val name: String,
    val present: Boolean,
    val value: String?,
    val severity: String,   // GOOD | WARN | BAD | INFO
    val note: String
)

data class HeadersResult(
    val url: String,
    val status: Int = 0,
    val server: String? = null,
    val grade: String = "?",
    val score: Int = 0,
    val checks: List<HeaderCheck> = emptyList(),
    val infoLeaks: List<HeaderCheck> = emptyList(),
    val error: String? = null
)

/**
 * Security Headers grader. Fetches the response headers and scores the target
 * against the modern hardening baseline (CSP, HSTS, X-Frame-Options, etc.),
 * then converts the weighted score into an A–F grade. Also flags information
 * leakage headers (Server/X-Powered-By/X-AspNet-Version).
 */
class HeadersEngine {

    suspend fun grade(rawUrl: String): HeadersResult = withContext(Dispatchers.IO) {
        val url = normalize(rawUrl)
        try {
            val req = Request.Builder().url(url)
                .header("User-Agent", "NEXUS-Toolkit/1.0").get().build()
            HttpClients.default.newCall(req).execute().use { resp ->
                val h = resp.headers
                fun v(name: String) = h.values(name).firstOrNull()

                val checks = mutableListOf<HeaderCheck>()

                val csp = v("Content-Security-Policy")
                checks.add(HeaderCheck("Content-Security-Policy", csp != null, csp,
                    if (csp == null) "BAD" else if (csp.contains("unsafe-inline")) "WARN" else "GOOD",
                    when {
                        csp == null -> "Missing — no protection against XSS/injection."
                        csp.contains("unsafe-inline") -> "Present but allows 'unsafe-inline' (weaker)."
                        else -> "Present and reasonably strict."
                    }))

                val hsts = v("Strict-Transport-Security")
                checks.add(HeaderCheck("Strict-Transport-Security", hsts != null, hsts,
                    if (hsts == null) "BAD" else if (!hsts.contains("max-age") || maxAge(hsts) < 15552000) "WARN" else "GOOD",
                    when {
                        hsts == null -> "Missing — connection can be downgraded to HTTP."
                        maxAge(hsts) < 15552000 -> "max-age too low (< 180 days)."
                        else -> "Present with a strong max-age."
                    }))

                val xfo = v("X-Frame-Options")
                val cspFrame = csp?.contains("frame-ancestors") == true
                checks.add(HeaderCheck("X-Frame-Options", xfo != null || cspFrame, xfo,
                    if (xfo == null && !cspFrame) "BAD" else "GOOD",
                    if (xfo == null && !cspFrame) "Missing — clickjacking possible." else "Clickjacking protection present."))

                val xcto = v("X-Content-Type-Options")
                checks.add(HeaderCheck("X-Content-Type-Options", xcto != null, xcto,
                    if (xcto?.equals("nosniff", true) == true) "GOOD" else "BAD",
                    if (xcto?.equals("nosniff", true) == true) "nosniff set." else "Missing — MIME sniffing possible."))

                val ref = v("Referrer-Policy")
                checks.add(HeaderCheck("Referrer-Policy", ref != null, ref,
                    if (ref == null) "WARN" else "GOOD",
                    if (ref == null) "Missing — full referrer may leak." else "Present."))

                val pp = v("Permissions-Policy") ?: v("Feature-Policy")
                checks.add(HeaderCheck("Permissions-Policy", pp != null, pp,
                    if (pp == null) "WARN" else "GOOD",
                    if (pp == null) "Missing — powerful features not restricted." else "Present."))

                val coop = v("Cross-Origin-Opener-Policy")
                checks.add(HeaderCheck("Cross-Origin-Opener-Policy", coop != null, coop,
                    if (coop == null) "INFO" else "GOOD",
                    if (coop == null) "Optional — helps isolate browsing context." else "Present."))

                val infoLeaks = buildList {
                    v("Server")?.let { add(HeaderCheck("Server", true, it, "WARN", "Reveals server software/version.")) }
                    v("X-Powered-By")?.let { add(HeaderCheck("X-Powered-By", true, it, "WARN", "Reveals backend technology.")) }
                    v("X-AspNet-Version")?.let { add(HeaderCheck("X-AspNet-Version", true, it, "WARN", "Reveals ASP.NET version.")) }
                    v("X-AspNetMvc-Version")?.let { add(HeaderCheck("X-AspNetMvc-Version", true, it, "WARN", "Reveals ASP.NET MVC version.")) }
                }

                val score = scoreOf(checks)
                HeadersResult(
                    url = url, status = resp.code, server = v("Server"),
                    grade = gradeOf(score), score = score,
                    checks = checks, infoLeaks = infoLeaks
                )
            }
        } catch (e: Exception) {
            HeadersResult(url, error = e.message ?: "Request failed")
        }
    }

    private fun scoreOf(checks: List<HeaderCheck>): Int {
        val weights = mapOf(
            "Content-Security-Policy" to 25, "Strict-Transport-Security" to 20,
            "X-Frame-Options" to 15, "X-Content-Type-Options" to 15,
            "Referrer-Policy" to 10, "Permissions-Policy" to 10,
            "Cross-Origin-Opener-Policy" to 5
        )
        var s = 0
        checks.forEach { c ->
            val w = weights[c.name] ?: 0
            s += when (c.severity) { "GOOD" -> w; "WARN" -> w / 2; else -> 0 }
        }
        return s
    }

    private fun gradeOf(score: Int): String = when {
        score >= 90 -> "A"
        score >= 75 -> "B"
        score >= 55 -> "C"
        score >= 35 -> "D"
        score >= 15 -> "E"
        else -> "F"
    }

    private fun maxAge(hsts: String): Long =
        Regex("max-age=(\\d+)").find(hsts)?.groupValues?.get(1)?.toLongOrNull() ?: 0L

    private fun normalize(raw: String): String {
        var s = raw.trim()
        if (!s.startsWith("http://") && !s.startsWith("https://")) s = "https://$s"
        return s
    }
}
