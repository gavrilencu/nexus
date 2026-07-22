package com.example.toolkit.data.api

import com.example.toolkit.data.model.ApiProbeResult
import com.example.toolkit.data.network.HttpClients
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class ApiInspector {

    suspend fun probe(
        method: String,
        url: String,
        headersText: String,
        body: String
    ): ApiProbeResult = withContext(Dispatchers.IO) {
        val normalizedUrl = if (url.startsWith("http")) url.trim() else "https://${url.trim()}"
        val start = System.currentTimeMillis()
        val headers = parseHeaders(headersText)

        try {
            val builder = Request.Builder().url(normalizedUrl)
            headers.forEach { (k, v) -> builder.header(k, v) }
            if (!headers.keys.any { it.equals("User-Agent", true) }) {
                builder.header("User-Agent", "NEXUS-Toolkit/1.0")
            }

            val upper = method.uppercase()
            when (upper) {
                "GET", "HEAD", "DELETE", "OPTIONS" -> builder.method(upper, null)
                else -> {
                    val media = (headers["Content-Type"] ?: headers["content-type"]
                        ?: "application/json; charset=utf-8").toMediaTypeOrNull()
                    builder.method(upper, body.toRequestBody(media))
                }
            }

            HttpClients.noRedirect.newCall(builder.build()).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                val preview = if (responseBody.length > 8000) {
                    responseBody.take(8000) + "\n… [truncated]"
                } else responseBody

                ApiProbeResult(
                    url = normalizedUrl,
                    method = upper,
                    statusCode = response.code,
                    durationMs = System.currentTimeMillis() - start,
                    headers = response.headers.toMultimap().mapValues { it.value.joinToString() },
                    body = preview
                )
            }
        } catch (e: Exception) {
            ApiProbeResult(
                url = normalizedUrl,
                method = method.uppercase(),
                statusCode = null,
                durationMs = System.currentTimeMillis() - start,
                headers = emptyMap(),
                body = "",
                error = e.message
            )
        }
    }

    private fun parseHeaders(raw: String): Map<String, String> {
        return raw.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.contains(":") }
            .associate { line ->
                val idx = line.indexOf(':')
                line.substring(0, idx).trim() to line.substring(idx + 1).trim()
            }
    }
}
