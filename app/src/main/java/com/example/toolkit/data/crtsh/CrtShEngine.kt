package com.example.toolkit.data.crtsh

import com.example.toolkit.data.network.HttpClients
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray

data class CrtEntry(val name: String, val issuers: Set<String>, val lastSeen: String?)

data class CrtShResult(
    val domain: String,
    val subdomains: List<CrtEntry> = emptyList(),
    val total: Int = 0,
    val error: String? = null
)

/**
 * Passive subdomain enumeration via Certificate Transparency logs (crt.sh).
 * Queries the public crt.sh JSON API for every certificate issued for the
 * domain and its wildcards, then dedupes the SAN names into a subdomain list —
 * no packets sent to the target itself.
 */
class CrtShEngine {

    suspend fun enumerate(rawDomain: String): CrtShResult = withContext(Dispatchers.IO) {
        val domain = rawDomain.trim().removePrefix("https://").removePrefix("http://")
            .substringBefore("/").lowercase().removePrefix("*.")
        if (domain.isBlank() || !domain.contains(".")) {
            return@withContext CrtShResult(domain, error = "Enter a valid domain, e.g. example.com")
        }

        try {
            val req = Request.Builder()
                .url("https://crt.sh/?q=%25.$domain&output=json")
                .header("User-Agent", "NEXUS-Toolkit/1.0")
                .header("Accept", "application/json")
                .get().build()
            HttpClients.default.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext CrtShResult(domain, error = "crt.sh HTTP ${resp.code}")
                val body = resp.body?.string().orEmpty()
                if (body.isBlank()) return@withContext CrtShResult(domain, error = "Empty response (crt.sh may be rate-limiting).")

                val arr = JSONArray(body)
                val map = LinkedHashMap<String, CrtEntry>()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val issuer = o.optString("issuer_name").let { extractOrg(it) }
                    val lastSeen = o.optString("not_before").take(10)
                    val nameValue = o.optString("name_value")
                    nameValue.split("\n").map { it.trim().lowercase().removePrefix("*.") }
                        .filter { it.isNotBlank() && it.endsWith(domain) && !it.contains(" ") }
                        .forEach { name ->
                            val prev = map[name]
                            if (prev == null) map[name] = CrtEntry(name, setOfNotNull(issuer.ifBlank { null }), lastSeen)
                            else map[name] = prev.copy(issuers = prev.issuers + setOfNotNull(issuer.ifBlank { null }))
                        }
                }
                val sorted = map.values.sortedBy { it.name }
                CrtShResult(domain, sorted, sorted.size)
            }
        } catch (e: Exception) {
            CrtShResult(domain, error = e.message ?: "Request failed")
        }
    }

    private fun extractOrg(issuer: String): String =
        Regex("O=([^,]+)").find(issuer)?.groupValues?.get(1)?.trim('"', ' ') ?: issuer.take(40)
}
