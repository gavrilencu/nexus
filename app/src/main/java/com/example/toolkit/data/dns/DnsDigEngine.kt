package com.example.toolkit.data.dns

import com.example.toolkit.data.network.HttpClients
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.net.InetAddress

data class DnsRecord(
    val type: String,
    val values: List<String>,
    val error: String? = null
)

data class DnsResult(
    val host: String,
    val records: List<DnsRecord>,
    val error: String? = null
)

class DnsDigEngine {

    suspend fun dig(rawHost: String): DnsResult = withContext(Dispatchers.IO) {
        val host = rawHost.trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .substringBefore("/")
            .substringBefore(":")
            .lowercase()

        if (host.isBlank()) {
            return@withContext DnsResult(host = rawHost, records = emptyList(), error = "Invalid host")
        }

        try {
            coroutineScope {
                val aLocal = async { lookupLocalA(host) }
                val aaaaLocal = async { lookupLocalAaaa(host) }
                val aDoh = async { doh(host, "A") }
                val aaaaDoh = async { doh(host, "AAAA") }
                val mx = async { doh(host, "MX") }
                val ns = async { doh(host, "NS") }
                val txt = async { doh(host, "TXT") }
                val cname = async { doh(host, "CNAME") }
                val soa = async { doh(host, "SOA") }

                // Prefer DoH when available; merge with local A/AAAA
                val a = mergePreferNonEmpty(aDoh.await(), aLocal.await())
                val aaaa = mergePreferNonEmpty(aaaaDoh.await(), aaaaLocal.await())

                DnsResult(
                    host = host,
                    records = listOf(a, aaaa, mx.await(), ns.await(), txt.await(), cname.await(), soa.await())
                )
            }
        } catch (e: Exception) {
            DnsResult(host = host, records = emptyList(), error = e.message)
        }
    }

    private fun mergePreferNonEmpty(primary: DnsRecord, fallback: DnsRecord): DnsRecord {
        val primaryOk = primary.values.any { it != "—" && it.isNotBlank() } && primary.error == null
        return if (primaryOk) primary else fallback
    }

    private fun lookupLocalA(host: String): DnsRecord = try {
        val ips = InetAddress.getAllByName(host)
            .mapNotNull { it.hostAddress }
            .filter { !it.contains(":") }
            .distinct()
        DnsRecord("A", ips.ifEmpty { listOf("—") })
    } catch (e: Exception) {
        DnsRecord("A", emptyList(), e.message)
    }

    private fun lookupLocalAaaa(host: String): DnsRecord = try {
        val ips = InetAddress.getAllByName(host)
            .mapNotNull { it.hostAddress }
            .filter { it.contains(":") }
            .distinct()
        DnsRecord("AAAA", ips.ifEmpty { listOf("—") })
    } catch (e: Exception) {
        DnsRecord("AAAA", emptyList(), e.message)
    }

    private fun doh(host: String, type: String): DnsRecord {
        return try {
            val request = Request.Builder()
                .url("https://cloudflare-dns.com/dns-query?name=$host&type=$type")
                .header("Accept", "application/dns-json")
                .header("User-Agent", "NEXUS-Toolkit/1.0")
                .get()
                .build()
            HttpClients.default.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return DnsRecord(type, emptyList(), "DoH HTTP ${response.code}")
                }
                val json = JSONObject(response.body?.string().orEmpty())
                val answers = json.optJSONArray("Answer")
                val values = mutableListOf<String>()
                if (answers != null) {
                    for (i in 0 until answers.length()) {
                        val data = answers.getJSONObject(i).optString("data")
                        if (data.isNotBlank()) values += data.trim('"')
                    }
                }
                DnsRecord(type, values.ifEmpty { listOf("—") })
            }
        } catch (e: Exception) {
            DnsRecord(type, emptyList(), e.message)
        }
    }
}
