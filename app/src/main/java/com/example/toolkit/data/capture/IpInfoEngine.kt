package com.example.toolkit.data.capture

import com.example.toolkit.data.network.HttpClients
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.net.InetAddress

data class IpInfo(
    val ip: String,
    val reverseDns: String? = null,
    val hostname: String? = null,   // from geo provider
    val org: String? = null,        // ISP / owning org
    val isp: String? = null,
    val asn: String? = null,        // e.g. "AS15169"
    val asnOrg: String? = null,     // e.g. "Google LLC"
    val domain: String? = null,     // company domain, e.g. "google.com"
    val company: String? = null,    // RDAP registered network owner
    val netName: String? = null,    // RDAP network name / handle
    val cidr: String? = null,       // RDAP allocation
    val abuseEmail: String? = null,
    val city: String? = null,
    val region: String? = null,
    val country: String? = null,
    val countryCode: String? = null,
    val continent: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val timezone: String? = null,
    val type: String? = null,       // IPv4 / IPv6
    val isPrivate: Boolean = false,
    val error: String? = null
) {
    val mapUrl: String?
        get() = if (latitude != null && longitude != null)
            "https://www.openstreetmap.org/?mlat=$latitude&mlon=$longitude#map=12/$latitude/$longitude"
        else null
}

object IpInfoEngine {

    /** True for RFC1918 / loopback / link-local addresses that can't be looked up. */
    fun isPrivate(ip: String): Boolean {
        return try {
            val a = InetAddress.getByName(ip)
            a.isSiteLocalAddress || a.isLoopbackAddress || a.isLinkLocalAddress ||
                a.isAnyLocalAddress || a.isMulticastAddress
        } catch (_: Exception) {
            ip.startsWith("10.") || ip.startsWith("192.168.") || ip.startsWith("127.") ||
                Regex("^172\\.(1[6-9]|2\\d|3[0-1])\\.").containsMatchIn(ip)
        }
    }

    suspend fun lookup(ip: String): IpInfo = withContext(Dispatchers.IO) {
        if (isPrivate(ip)) {
            return@withContext IpInfo(
                ip = ip,
                isPrivate = true,
                reverseDns = reverseDns(ip),
                error = "IP privat / local — nu are proprietar public pe internet."
            )
        }

        val reverse = async { reverseDns(ip) }
        val geo = async { runCatching { fetchGeo(ip) }.getOrNull() }
        val rdap = async { runCatching { fetchRdap(ip) }.getOrNull() }

        val g = geo.await()
        val r = rdap.await()

        val base = g ?: IpInfo(ip = ip)
        base.copy(
            reverseDns = reverse.await(),
            company = r?.company ?: base.company,
            netName = r?.netName ?: base.netName,
            cidr = r?.cidr ?: base.cidr,
            abuseEmail = r?.abuseEmail ?: base.abuseEmail,
            error = if (g == null && r == null) "Nu am putut interoga serviciile de IP intel." else null
        )
    }

    private fun reverseDns(ip: String): String? = try {
        val name = InetAddress.getByName(ip).canonicalHostName
        if (name.isNullOrBlank() || name == ip) null else name
    } catch (_: Exception) {
        null
    }

    /** ipwho.is — free, HTTPS, no key. Gives geo + ASN + org + company domain. */
    private fun fetchGeo(ip: String): IpInfo? {
        val req = Request.Builder()
            .url("https://ipwho.is/$ip")
            .header("User-Agent", "NexusToolkit")
            .build()
        HttpClients.default.newCall(req).execute().use { resp ->
            val body = resp.body?.string() ?: return null
            val json = JSONObject(body)
            if (!json.optBoolean("success", false)) {
                val msg = json.optString("message").takeIf { it.isNotBlank() }
                return IpInfo(ip = ip, error = msg ?: "Serviciul geo a refuzat cererea.")
            }
            val conn = json.optJSONObject("connection")
            val tz = json.optJSONObject("timezone")
            val asnRaw = conn?.optInt("asn", 0) ?: 0
            return IpInfo(
                ip = json.optString("ip", ip),
                type = json.optString("type").takeIf { it.isNotBlank() },
                city = json.optString("city").takeIf { it.isNotBlank() },
                region = json.optString("region").takeIf { it.isNotBlank() },
                country = json.optString("country").takeIf { it.isNotBlank() },
                countryCode = json.optString("country_code").takeIf { it.isNotBlank() },
                continent = json.optString("continent").takeIf { it.isNotBlank() },
                latitude = json.optDouble("latitude").takeIf { !it.isNaN() },
                longitude = json.optDouble("longitude").takeIf { !it.isNaN() },
                timezone = tz?.optString("id")?.takeIf { it.isNotBlank() },
                isp = conn?.optString("isp")?.takeIf { it.isNotBlank() },
                org = conn?.optString("org")?.takeIf { it.isNotBlank() },
                asn = if (asnRaw > 0) "AS$asnRaw" else null,
                asnOrg = conn?.optString("org")?.takeIf { it.isNotBlank() },
                domain = conn?.optString("domain")?.takeIf { it.isNotBlank() }
            )
        }
    }

    /** RDAP (rdap.org routes to the authoritative RIR) — real network owner / whois. */
    private fun fetchRdap(ip: String): IpInfo? {
        val req = Request.Builder()
            .url("https://rdap.org/ip/$ip")
            .header("Accept", "application/rdap+json")
            .header("User-Agent", "NexusToolkit")
            .build()
        HttpClients.default.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val body = resp.body?.string() ?: return null
            val json = JSONObject(body)

            val netName = json.optString("name").takeIf { it.isNotBlank() }
            val cidr = json.optJSONArray("cidr0_cidrs")?.let { arr ->
                (0 until arr.length()).joinToString(", ") {
                    val c = arr.getJSONObject(it)
                    val prefix = c.optString("v4prefix").ifBlank { c.optString("v6prefix") }
                    "$prefix/${c.optInt("length")}"
                }.takeIf { it.isNotBlank() }
            } ?: run {
                val start = json.optString("startAddress")
                val end = json.optString("endAddress")
                if (start.isNotBlank() && end.isNotBlank()) "$start – $end" else null
            }

            var company: String? = null
            var abuse: String? = null
            val entities = json.optJSONArray("entities")
            if (entities != null) {
                for (i in 0 until entities.length()) {
                    val e = entities.getJSONObject(i)
                    val roles = e.optJSONArray("roles")
                    val roleList = if (roles != null) (0 until roles.length()).map { roles.getString(it) } else emptyList()
                    val name = vcardName(e)
                    if (company == null && (roleList.contains("registrant") || roleList.contains("administrative") || roleList.contains("owner"))) {
                        company = name
                    }
                    if (abuse == null && roleList.contains("abuse")) {
                        abuse = vcardEmail(e)
                    }
                }
                if (company == null) {
                    // fall back to the first entity with a name
                    for (i in 0 until entities.length()) {
                        val n = vcardName(entities.getJSONObject(i))
                        if (n != null) { company = n; break }
                    }
                }
            }

            return IpInfo(
                ip = ip,
                netName = netName,
                cidr = cidr,
                company = company,
                abuseEmail = abuse
            )
        }
    }

    private fun vcardName(entity: JSONObject): String? {
        val vcard = entity.optJSONArray("vcardArray") ?: return entity.optString("handle").takeIf { it.isNotBlank() }
        // vcardArray = ["vcard", [ [name, {}, type, value], ... ]]
        val props = vcard.optJSONArray(1) ?: return null
        for (i in 0 until props.length()) {
            val p = props.optJSONArray(i) ?: continue
            if (p.optString(0) == "fn") return p.optString(3).takeIf { it.isNotBlank() }
        }
        return null
    }

    private fun vcardEmail(entity: JSONObject): String? {
        val vcard = entity.optJSONArray("vcardArray") ?: return null
        val props = vcard.optJSONArray(1) ?: return null
        for (i in 0 until props.length()) {
            val p = props.optJSONArray(i) ?: continue
            if (p.optString(0) == "email") return p.optString(3).takeIf { it.isNotBlank() }
        }
        return null
    }
}
