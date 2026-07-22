package com.example.toolkit.data.traffic

import com.example.toolkit.data.model.TrafficEntry
import com.example.toolkit.data.network.HttpClients
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID

class TrafficMonitor {

    suspend fun captureRequest(
        method: String,
        url: String,
        headers: Map<String, String> = emptyMap(),
        body: String? = null
    ): TrafficEntry = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val normalizedUrl = if (url.startsWith("http")) url else "https://$url"

        try {
            val builder = Request.Builder().url(normalizedUrl)
            headers.forEach { (k, v) -> builder.header(k, v) }
            if (!headers.keys.any { it.equals("User-Agent", true) }) {
                builder.header("User-Agent", "NEXUS-Toolkit/1.0")
            }

            val upper = method.uppercase()
            when (upper) {
                "GET", "HEAD", "DELETE" -> builder.method(upper, null)
                else -> {
                    val media = "application/json; charset=utf-8".toMediaTypeOrNull()
                    builder.method(upper, (body ?: "").toRequestBody(media))
                }
            }

            HttpClients.noRedirect.newCall(builder.build()).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                val preview = if (responseBody.length > 4000) {
                    responseBody.take(4000) + "\n… [truncated]"
                } else responseBody

                TrafficEntry(
                    id = UUID.randomUUID().toString(),
                    method = upper,
                    url = normalizedUrl,
                    statusCode = response.code,
                    durationMs = System.currentTimeMillis() - start,
                    requestHeaders = headers,
                    responseHeaders = response.headers.toMultimap().mapValues { it.value.joinToString() },
                    requestBody = body,
                    responseBodyPreview = preview,
                    error = null
                )
            }
        } catch (e: Exception) {
            TrafficEntry(
                id = UUID.randomUUID().toString(),
                method = method.uppercase(),
                url = normalizedUrl,
                statusCode = null,
                durationMs = System.currentTimeMillis() - start,
                requestHeaders = headers,
                responseHeaders = emptyMap(),
                requestBody = body,
                responseBodyPreview = null,
                error = e.message
            )
        }
    }
}
