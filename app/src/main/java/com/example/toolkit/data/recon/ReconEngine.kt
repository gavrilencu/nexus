package com.example.toolkit.data.recon

import com.example.toolkit.data.model.ReconResult
import com.example.toolkit.data.network.HttpClients
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.net.InetAddress
import java.net.URI
import java.security.cert.X509Certificate
import javax.net.ssl.SSLSocketFactory

class ReconEngine {

    private val securityHeaderKeys = listOf(
        "strict-transport-security",
        "content-security-policy",
        "x-frame-options",
        "x-content-type-options",
        "referrer-policy",
        "permissions-policy",
        "x-xss-protection"
    )

    suspend fun analyze(rawTarget: String): ReconResult = withContext(Dispatchers.IO) {
        val target = normalizeHost(rawTarget)
        if (target.isBlank()) {
            return@withContext ReconResult(target = rawTarget, error = "Invalid target")
        }

        try {
            coroutineScope {
                val dnsDeferred = async { resolveDns(target) }
                val httpDeferred = async { probeHttp(target) }
                val geoDeferred = async {
                    val ips = dnsDeferred.await()
                    ips.firstOrNull()?.let { geoLookup(it) } ?: emptyMap()
                }
                val tlsDeferred = async { probeTls(target) }

                val ips = dnsDeferred.await()
                val http = httpDeferred.await()
                val geo = geoDeferred.await()
                val tls = tlsDeferred.await()

                ReconResult(
                    target = target,
                    resolvedIps = ips,
                    hostname = try {
                        InetAddress.getByName(target).canonicalHostName
                    } catch (_: Exception) {
                        null
                    },
                    httpStatus = http["status"]?.toIntOrNull(),
                    serverHeader = http["server"],
                    poweredBy = http["poweredBy"],
                    contentType = http["contentType"],
                    responseTimeMs = http["latency"]?.toLongOrNull(),
                    tlsVersion = tls["tlsVersion"],
                    certificateSubject = tls["subject"],
                    certificateIssuer = tls["issuer"],
                    certificateExpiry = tls["expiry"],
                    geoCountry = geo["country"],
                    geoCity = geo["city"],
                    geoIsp = geo["isp"],
                    geoOrg = geo["org"],
                    geoAs = geo["as"],
                    securityHeaders = http.filterKeys { it.startsWith("sec:") }
                        .mapKeys { it.key.removePrefix("sec:") }
                )
            }
        } catch (e: Exception) {
            ReconResult(target = target, error = e.message ?: "Recon failed")
        }
    }

    private fun normalizeHost(input: String): String {
        var value = input.trim()
        if (value.startsWith("http://", true) || value.startsWith("https://", true)) {
            value = try {
                URI(value).host ?: value
            } catch (_: Exception) {
                value.substringAfter("://").substringBefore("/").substringBefore(":")
            }
        }
        return value.substringBefore("/").substringBefore(":").lowercase()
    }

    private fun resolveDns(host: String): List<String> = try {
        InetAddress.getAllByName(host).mapNotNull { it.hostAddress }.distinct()
    } catch (_: Exception) {
        emptyList()
    }

    private fun probeHttp(host: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val urls = listOf("https://$host", "http://$host")
        for (url in urls) {
            try {
                val start = System.currentTimeMillis()
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "NEXUS-Toolkit/1.0 (authorized-testing)")
                    .get()
                    .build()
                HttpClients.default.newCall(request).execute().use { response ->
                    result["status"] = response.code.toString()
                    result["latency"] = (System.currentTimeMillis() - start).toString()
                    result["server"] = response.header("Server") ?: "—"
                    result["poweredBy"] = response.header("X-Powered-By") ?: "—"
                    result["contentType"] = response.header("Content-Type") ?: "—"
                    securityHeaderKeys.forEach { key ->
                        response.header(key)?.let { result["sec:$key"] = it }
                    }
                }
                return result
            } catch (_: Exception) {
                continue
            }
        }
        return result
    }

    private fun probeTls(host: String): Map<String, String> {
        return try {
            val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
            factory.createSocket(host, 443).use { socket ->
                val ssl = socket as javax.net.ssl.SSLSocket
                ssl.startHandshake()
                val session = ssl.session
                val cert = session.peerCertificates.firstOrNull() as? X509Certificate
                mapOf(
                    "tlsVersion" to (session.protocol ?: "—"),
                    "subject" to (cert?.subjectX500Principal?.name ?: "—"),
                    "issuer" to (cert?.issuerX500Principal?.name ?: "—"),
                    "expiry" to (cert?.notAfter?.toString() ?: "—")
                )
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun geoLookup(ip: String): Map<String, String> {
        return try {
            val request = Request.Builder()
                .url("http://ip-api.com/json/$ip?fields=status,country,city,isp,org,as,query")
                .get()
                .build()
            HttpClients.default.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val json = JSONObject(body)
                if (json.optString("status") != "success") return emptyMap()
                mapOf(
                    "country" to json.optString("country"),
                    "city" to json.optString("city"),
                    "isp" to json.optString("isp"),
                    "org" to json.optString("org"),
                    "as" to json.optString("as")
                )
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }
}
