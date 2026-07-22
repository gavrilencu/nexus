package com.example.toolkit.data.shodan

import com.example.toolkit.data.network.HttpClients
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

data class ShodanService(val port: Int, val transport: String, val product: String?, val banner: String?)

data class ShodanResult(
    val ip: String,
    val source: String,           // "InternetDB (free)" or "Shodan API"
    val hostnames: List<String> = emptyList(),
    val ports: List<Int> = emptyList(),
    val tags: List<String> = emptyList(),
    val cpes: List<String> = emptyList(),
    val vulns: List<String> = emptyList(),
    val country: String? = null,
    val org: String? = null,
    val isp: String? = null,
    val os: String? = null,
    val services: List<ShodanService> = emptyList(),
    val error: String? = null
)

/**
 * Internet exposure lookup. By default uses Shodan's free, key-less InternetDB
 * API (open ports, hostnames, tags, CPEs and known CVEs for an IP). When a
 * Shodan API key is provided it upgrades to the full host API (org, ISP, OS,
 * per-service banners).
 */
class ShodanEngine {

    suspend fun lookup(rawIp: String, apiKey: String): ShodanResult = withContext(Dispatchers.IO) {
        val ip = rawIp.trim()
        if (ip.isBlank()) return@withContext ShodanResult(ip, "", error = "Enter an IP address.")
        if (apiKey.isNotBlank()) shodanHost(ip, apiKey) else internetDb(ip)
    }

    private fun internetDb(ip: String): ShodanResult {
        return try {
            val req = Request.Builder().url("https://internetdb.shodan.io/$ip")
                .header("User-Agent", "NEXUS-Toolkit/1.0").get().build()
            HttpClients.default.newCall(req).execute().use { resp ->
                if (resp.code == 404) return ShodanResult(ip, "InternetDB (free)",
                    error = "No information — IP not indexed by Shodan.")
                if (!resp.isSuccessful) return ShodanResult(ip, "InternetDB (free)", error = "HTTP ${resp.code}")
                val json = JSONObject(resp.body?.string().orEmpty())
                ShodanResult(
                    ip = ip, source = "InternetDB (free)",
                    hostnames = json.optJSONArray("hostnames").toStrList(),
                    ports = json.optJSONArray("ports").toIntList(),
                    tags = json.optJSONArray("tags").toStrList(),
                    cpes = json.optJSONArray("cpes").toStrList(),
                    vulns = json.optJSONArray("vulns").toStrList()
                )
            }
        } catch (e: Exception) {
            ShodanResult(ip, "InternetDB (free)", error = e.message ?: "Request failed")
        }
    }

    private fun shodanHost(ip: String, key: String): ShodanResult {
        return try {
            val req = Request.Builder().url("https://api.shodan.io/shodan/host/$ip?key=$key")
                .header("User-Agent", "NEXUS-Toolkit/1.0").get().build()
            HttpClients.default.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val msg = runCatching { JSONObject(body).optString("error") }.getOrNull()
                    return ShodanResult(ip, "Shodan API", error = "HTTP ${resp.code}: ${msg ?: ""}")
                }
                val json = JSONObject(body)
                val services = ArrayList<ShodanService>()
                json.optJSONArray("data")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val d = arr.optJSONObject(i) ?: continue
                        services.add(ShodanService(
                            port = d.optInt("port"),
                            transport = d.optString("transport", "tcp"),
                            product = d.optString("product").takeIf { it.isNotBlank() },
                            banner = d.optString("data").takeIf { it.isNotBlank() }?.take(400)
                        ))
                    }
                }
                ShodanResult(
                    ip = ip, source = "Shodan API",
                    hostnames = json.optJSONArray("hostnames").toStrList(),
                    ports = json.optJSONArray("ports").toIntList(),
                    tags = json.optJSONArray("tags").toStrList(),
                    vulns = json.optJSONObject("vulns")?.keys()?.asSequence()?.toList()
                        ?: json.optJSONArray("vulns").toStrList(),
                    country = json.optString("country_name").takeIf { it.isNotBlank() },
                    org = json.optString("org").takeIf { it.isNotBlank() },
                    isp = json.optString("isp").takeIf { it.isNotBlank() },
                    os = json.optString("os").takeIf { it.isNotBlank() && it != "null" },
                    services = services.sortedBy { it.port }
                )
            }
        } catch (e: Exception) {
            ShodanResult(ip, "Shodan API", error = e.message ?: "Request failed")
        }
    }

    private fun JSONArray?.toStrList(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { optString(it).takeIf { s -> s.isNotBlank() } }
    }

    private fun JSONArray?.toIntList(): List<Int> {
        if (this == null) return emptyList()
        return (0 until length()).map { optInt(it) }.sorted()
    }
}
