package com.example.toolkit.data.ip

import com.example.toolkit.data.network.HttpClients
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

data class IpToolResult(
    val input: String,
    val isIp: Boolean,
    val resolvedHost: String?,
    val resolvedIps: List<String>,
    val reverseDns: String?,
    val country: String?,
    val city: String?,
    val isp: String?,
    val org: String?,
    val asInfo: String?,
    val timezone: String?,
    val reachable443: Boolean?,
    val reachable80: Boolean?,
    val latency443Ms: Long?,
    val error: String? = null
)

class IpToolsEngine {

    suspend fun analyze(raw: String): IpToolResult = withContext(Dispatchers.IO) {
        val input = raw.trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .substringBefore("/")
            .substringBefore(":")

        if (input.isBlank()) {
            return@withContext IpToolResult(
                input = raw, isIp = false, resolvedHost = null, resolvedIps = emptyList(),
                reverseDns = null, country = null, city = null, isp = null, org = null,
                asInfo = null, timezone = null, reachable443 = null, reachable80 = null,
                latency443Ms = null, error = "Enter IP or hostname"
            )
        }

        try {
            val isIp = input.matches(Regex("""^(\d{1,3}\.){3}\d{1,3}$""")) || input.contains(":")
            coroutineScope {
                val resolveDeferred = async { resolve(input) }
                val (host, ips) = resolveDeferred.await()
                val primaryIp = ips.firstOrNull() ?: if (isIp) input else null

                val reverseDeferred = async {
                    primaryIp?.let { reverse(it) }
                }
                val geoDeferred = async {
                    primaryIp?.let { geo(it) } ?: emptyMap()
                }
                val port443 = async { probePort(primaryIp, 443) }
                val port80 = async { probePort(primaryIp, 80) }

                val reverse = reverseDeferred.await()
                val geo = geoDeferred.await()
                val r443 = port443.await()
                val r80 = port80.await()

                IpToolResult(
                    input = input,
                    isIp = isIp,
                    resolvedHost = host,
                    resolvedIps = ips,
                    reverseDns = reverse,
                    country = geo["country"],
                    city = geo["city"],
                    isp = geo["isp"],
                    org = geo["org"],
                    asInfo = geo["as"],
                    timezone = geo["timezone"],
                    reachable443 = r443.first,
                    reachable80 = r80.first,
                    latency443Ms = r443.second
                )
            }
        } catch (e: Exception) {
            IpToolResult(
                input = input, isIp = false, resolvedHost = null, resolvedIps = emptyList(),
                reverseDns = null, country = null, city = null, isp = null, org = null,
                asInfo = null, timezone = null, reachable443 = null, reachable80 = null,
                latency443Ms = null, error = e.message
            )
        }
    }

    private fun resolve(host: String): Pair<String?, List<String>> = try {
        val all = InetAddress.getAllByName(host)
        val canonical = all.firstOrNull()?.canonicalHostName
        val ips = all.mapNotNull { it.hostAddress }.distinct()
        canonical to ips
    } catch (_: Exception) {
        null to emptyList()
    }

    private fun reverse(ip: String): String? = try {
        InetAddress.getByName(ip).canonicalHostName
    } catch (_: Exception) {
        null
    }

    private fun geo(ip: String): Map<String, String> = try {
        val request = Request.Builder()
            .url("http://ip-api.com/json/$ip?fields=status,country,city,isp,org,as,timezone,query")
            .get()
            .build()
        HttpClients.default.newCall(request).execute().use { response ->
            val json = JSONObject(response.body?.string().orEmpty())
            if (json.optString("status") != "success") return emptyMap()
            mapOf(
                "country" to json.optString("country"),
                "city" to json.optString("city"),
                "isp" to json.optString("isp"),
                "org" to json.optString("org"),
                "as" to json.optString("as"),
                "timezone" to json.optString("timezone")
            )
        }
    } catch (_: Exception) {
        emptyMap()
    }

    private fun probePort(ip: String?, port: Int): Pair<Boolean?, Long?> {
        if (ip == null) return null to null
        val start = System.currentTimeMillis()
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), 1500)
                true to (System.currentTimeMillis() - start)
            }
        } catch (_: Exception) {
            false to null
        }
    }
}
