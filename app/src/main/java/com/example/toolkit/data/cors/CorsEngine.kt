package com.example.toolkit.data.cors

import com.example.toolkit.data.network.HttpClients
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.net.URI

data class CorsTest(
    val name: String,
    val origin: String,
    val allowOrigin: String?,
    val allowCredentials: Boolean,
    val vulnerable: Boolean,
    val severity: String,   // HIGH | MEDIUM | INFO | OK
    val detail: String
)

data class CorsResult(
    val url: String,
    val tests: List<CorsTest> = emptyList(),
    val error: String? = null
)

/**
 * CORS misconfiguration scanner. Sends the target a set of crafted `Origin`
 * headers and inspects `Access-Control-Allow-Origin` (ACAO) and
 * `Access-Control-Allow-Credentials` (ACAC) to detect classic weaknesses:
 * arbitrary-origin reflection, `null` origin trust, prefix/suffix bypasses and
 * credentialed wildcards.
 */
class CorsEngine {

    suspend fun scan(rawUrl: String): CorsResult = withContext(Dispatchers.IO) {
        val url = normalize(rawUrl) ?: return@withContext CorsResult(rawUrl, error = "Invalid URL")
        val host = try {
            URI(url).host ?: return@withContext CorsResult(url, error = "Nu pot extrage host-ul")
        } catch (e: Exception) {
            return@withContext CorsResult(url, error = e.message ?: "Bad URL")
        }

        val cases = listOf(
            "Reflectare origine arbitrară" to "https://nexus-evil-attacker.com",
            "Origine null" to "null",
            "Bypass sufix (evil după domeniu)" to "https://$host.nexus-evil.com",
            "Bypass prefix (domeniu în subdomeniu atacator)" to "https://evil-$host",
            "Subdomeniu arbitrar" to "https://nexus-attacker.$host"
        )

        try {
            val tests = coroutineScope {
                cases.map { (name, origin) ->
                    async { runCase(url, name, origin) }
                }.awaitAll()
            }
            CorsResult(url, tests)
        } catch (e: Exception) {
            CorsResult(url, error = e.message ?: "Request failed")
        }
    }

    private fun runCase(url: String, name: String, origin: String): CorsTest {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("Origin", origin)
                .header("User-Agent", "NEXUS-Toolkit/1.0")
                .get()
                .build()
            HttpClients.default.newCall(request).execute().use { resp ->
                val acao = resp.header("Access-Control-Allow-Origin")
                val acac = resp.header("Access-Control-Allow-Credentials")?.equals("true", true) == true
                evaluate(name, origin, acao, acac)
            }
        } catch (e: Exception) {
            CorsTest(name, origin, null, false, false, "OK", "Cerere eșuată: ${e.message}")
        }
    }

    private fun evaluate(name: String, origin: String, acao: String?, acac: Boolean): CorsTest {
        val reflects = acao != null && acao == origin
        val nullTrusted = origin == "null" && acao == "null"
        val wildcard = acao == "*"

        val (vulnerable, severity, detail) = when {
            (reflects || nullTrusted) && acac ->
                Triple(true, "HIGH", "ACAO reflectă originea „$origin” ȘI ACAC=true → date autentificate pot fi furate cross-origin.")
            reflects || nullTrusted ->
                Triple(true, "MEDIUM", "ACAO reflectă originea „$origin” (fără credentials) → resurse citibile de orice site.")
            wildcard && acac ->
                Triple(true, "MEDIUM", "ACAO=* împreună cu ACAC=true (config invalidă/riscantă).")
            wildcard ->
                Triple(false, "INFO", "ACAO=* → API public; risc doar dacă expune date sensibile.")
            acao != null ->
                Triple(false, "OK", "ACAO=„$acao” (nu reflectă originea de test).")
            else ->
                Triple(false, "OK", "Fără header ACAO — CORS nu e activat pentru această origine.")
        }
        return CorsTest(name, origin, acao, acac, vulnerable, severity, detail)
    }

    private fun normalize(raw: String): String? {
        var s = raw.trim()
        if (s.isBlank()) return null
        if (!s.startsWith("http://") && !s.startsWith("https://")) s = "https://$s"
        return s
    }
}
