package com.example.toolkit.data.httpmethods

import com.example.toolkit.data.network.HttpClients
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

data class MethodResult(
    val method: String,
    val status: Int?,
    val allowed: Boolean,
    val dangerous: Boolean,
    val note: String
)

data class HttpMethodsResult(
    val url: String,
    val allowHeader: String? = null,
    val results: List<MethodResult> = emptyList(),
    val error: String? = null
)

/**
 * HTTP verb tester. Probes each method against the target and reports which are
 * handled (status ≠ 405/501), reads the `Allow` header from OPTIONS, and flags
 * dangerous verbs (PUT/DELETE/PATCH/TRACE) that appear enabled — classic checks
 * for misconfigured servers (WebDAV left on, Cross-Site Tracing, etc.).
 */
class HttpMethodsEngine {

    companion object {
        private val METHODS = listOf("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "TRACE")
        private val BODY_METHODS = setOf("POST", "PUT", "PATCH")
        private val DANGEROUS = setOf("PUT", "DELETE", "PATCH", "TRACE", "CONNECT")
    }

    suspend fun test(rawUrl: String): HttpMethodsResult = withContext(Dispatchers.IO) {
        val url = normalize(rawUrl) ?: return@withContext HttpMethodsResult(rawUrl, error = "Invalid URL")
        try {
            val results = coroutineScope {
                METHODS.map { m -> async { probe(url, m) } }.awaitAll()
            }
            val allow = results.firstOrNull { it.method == "OPTIONS" }?.let { fetchAllow(url) }
            HttpMethodsResult(url, allow, results.sortedBy { METHODS.indexOf(it.method) })
        } catch (e: Exception) {
            HttpMethodsResult(url, error = e.message ?: "Request failed")
        }
    }

    private fun fetchAllow(url: String): String? = try {
        val request = Request.Builder().url(url).method("OPTIONS", null)
            .header("User-Agent", "NEXUS-Toolkit/1.0").build()
        HttpClients.noRedirect.newCall(request).execute().use { it.header("Allow") ?: it.header("allow") }
    } catch (_: Exception) {
        null
    }

    private fun probe(url: String, method: String): MethodResult {
        return try {
            val body = if (method in BODY_METHODS) "".toRequestBody("text/plain".toMediaTypeOrNull()) else null
            val request = Request.Builder()
                .url(url)
                .method(method, body)
                .header("User-Agent", "NEXUS-Toolkit/1.0")
                .build()
            HttpClients.noRedirect.newCall(request).execute().use { resp ->
                val code = resp.code
                val allowed = code != 405 && code != 501 && code != 400
                val dangerous = method in DANGEROUS && allowed
                val note = when {
                    method == "TRACE" && code == 200 -> "TRACE activ → posibil Cross-Site Tracing (XST)."
                    dangerous -> "Verb periculos aparent acceptat — verifică manual dacă modifică resurse."
                    code == 405 -> "Method Not Allowed."
                    code == 501 -> "Not Implemented."
                    else -> "HTTP $code"
                }
                MethodResult(method, code, allowed, dangerous, note)
            }
        } catch (e: Exception) {
            MethodResult(method, null, false, false, "Eroare: ${e.message}")
        }
    }

    private fun normalize(raw: String): String? {
        var s = raw.trim()
        if (s.isBlank()) return null
        if (!s.startsWith("http://") && !s.startsWith("https://")) s = "https://$s"
        return s
    }
}
