package com.example.toolkit.data.whois

import com.example.toolkit.data.network.HttpClients
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

data class WhoisEvent(val action: String, val date: String)

data class WhoisResult(
    val query: String,
    val type: String,               // "domain" | "ip"
    val handle: String? = null,
    val name: String? = null,
    val statuses: List<String> = emptyList(),
    val events: List<WhoisEvent> = emptyList(),
    val nameservers: List<String> = emptyList(),
    val entities: List<String> = emptyList(),
    val country: String? = null,
    val range: String? = null,
    val dnssec: String? = null,
    val error: String? = null
)

/**
 * WHOIS via RDAP (the modern, JSON-over-HTTPS successor to port-43 WHOIS).
 * rdap.org bootstraps to the authoritative registry/RIR for the domain or IP,
 * so a single endpoint works for most TLDs and all IP ranges.
 */
class WhoisEngine {

    suspend fun lookup(raw: String): WhoisResult = withContext(Dispatchers.IO) {
        val query = raw.trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .substringBefore("/")
            .substringBefore(":")
            .lowercase()

        if (query.isBlank()) return@withContext WhoisResult(raw, "domain", error = "Invalid input")

        val isIp = isIpAddress(query)
        val type = if (isIp) "ip" else "domain"
        val url = if (isIp) "https://rdap.org/ip/$query" else "https://rdap.org/domain/$query"

        try {
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/rdap+json")
                .header("User-Agent", "NEXUS-Toolkit/1.0")
                .get()
                .build()

            HttpClients.default.newCall(request).execute().use { resp ->
                if (resp.code == 404) {
                    return@withContext WhoisResult(query, type, error = "Nu există înregistrare RDAP (404) — domeniu/IP neînregistrat sau TLD fără RDAP.")
                }
                if (!resp.isSuccessful) {
                    return@withContext WhoisResult(query, type, error = "RDAP HTTP ${resp.code}")
                }
                val json = JSONObject(resp.body?.string().orEmpty())
                if (isIp) parseIp(query, json) else parseDomain(query, json)
            }
        } catch (e: Exception) {
            WhoisResult(query, type, error = e.message ?: "Lookup failed")
        }
    }

    private fun parseDomain(query: String, json: JSONObject): WhoisResult {
        val statuses = json.optJSONArray("status").toStringList()
        val events = parseEvents(json.optJSONArray("events"))
        val nameservers = mutableListOf<String>()
        json.optJSONArray("nameservers")?.let { arr ->
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.optString("ldhName")?.takeIf { it.isNotBlank() }?.let { nameservers.add(it.lowercase()) }
            }
        }
        val entities = parseEntities(json.optJSONArray("entities"))
        val dnssec = json.optJSONObject("secureDNS")?.let {
            if (it.optBoolean("delegationSigned")) "signed (DNSSEC activ)" else "unsigned"
        }
        return WhoisResult(
            query = query,
            type = "domain",
            handle = json.optString("handle").ifBlank { json.optString("ldhName").ifBlank { null } },
            name = json.optString("ldhName").ifBlank { null },
            statuses = statuses,
            events = events,
            nameservers = nameservers,
            entities = entities,
            dnssec = dnssec
        )
    }

    private fun parseIp(query: String, json: JSONObject): WhoisResult {
        val start = json.optString("startAddress")
        val end = json.optString("endAddress")
        val range = when {
            start.isNotBlank() && end.isNotBlank() -> "$start – $end"
            else -> json.optString("handle").ifBlank { null }
        }
        return WhoisResult(
            query = query,
            type = "ip",
            handle = json.optString("handle").ifBlank { null },
            name = json.optString("name").ifBlank { null },
            statuses = json.optJSONArray("status").toStringList(),
            events = parseEvents(json.optJSONArray("events")),
            entities = parseEntities(json.optJSONArray("entities")),
            country = json.optString("country").ifBlank { null },
            range = range
        )
    }

    private fun parseEvents(arr: JSONArray?): List<WhoisEvent> {
        if (arr == null) return emptyList()
        val out = mutableListOf<WhoisEvent>()
        for (i in 0 until arr.length()) {
            val e = arr.optJSONObject(i) ?: continue
            val action = e.optString("eventAction")
            val date = e.optString("eventDate")
            if (action.isNotBlank()) out.add(WhoisEvent(action, date.take(19).replace("T", " ")))
        }
        return out
    }

    private fun parseEntities(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        val out = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val ent = arr.optJSONObject(i) ?: continue
            val roles = ent.optJSONArray("roles").toStringList().joinToString("/").ifBlank { "entity" }
            val name = vcardName(ent) ?: ent.optString("handle").ifBlank { null }
            if (name != null) out.add("$roles: $name")
        }
        return out.distinct()
    }

    /** Extract the "fn" (formatted name) from an RDAP entity's jCard/vcardArray. */
    private fun vcardName(entity: JSONObject): String? {
        val vcardArray = entity.optJSONArray("vcardArray") ?: return null
        val props = vcardArray.optJSONArray(1) ?: return null
        for (i in 0 until props.length()) {
            val prop = props.optJSONArray(i) ?: continue
            if (prop.optString(0).equals("fn", ignoreCase = true)) {
                val value = prop.opt(3)
                if (value is String && value.isNotBlank()) return value
            }
        }
        return null
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        val out = mutableListOf<String>()
        for (i in 0 until length()) {
            optString(i).takeIf { it.isNotBlank() }?.let { out.add(it) }
        }
        return out
    }

    private fun isIpAddress(s: String): Boolean {
        val ipv4 = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")
        if (ipv4.matches(s)) return s.split(".").all { it.toIntOrNull() in 0..255 }
        return s.contains(":") && s.count { it == ':' } >= 2 // rough IPv6
    }
}
