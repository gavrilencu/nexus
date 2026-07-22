package com.example.toolkit.data.cve

import com.example.toolkit.data.network.HttpClients
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONObject

data class CveItem(
    val id: String,
    val description: String,
    val severity: String?,
    val score: Double?,
    val published: String?,
    val url: String
)

data class CveSearchResult(
    val query: String,
    val total: Int,
    val items: List<CveItem>,
    val error: String? = null
)

class CveLookupEngine {

    suspend fun search(query: String, limit: Int = 20): CveSearchResult = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isBlank()) {
            return@withContext CveSearchResult(q, 0, emptyList(), "Enter CVE id or keyword (e.g. CVE-2021-44228, apache log4j)")
        }

        try {
            // NVD 2.0 public API
            val urlBuilder = "https://services.nvd.nist.gov/rest/json/cves/2.0".toHttpUrl().newBuilder()
            if (q.uppercase().startsWith("CVE-")) {
                urlBuilder.addQueryParameter("cveId", q.uppercase())
            } else {
                urlBuilder.addQueryParameter("keywordSearch", q)
                urlBuilder.addQueryParameter("resultsPerPage", limit.toString())
            }

            val request = Request.Builder()
                .url(urlBuilder.build())
                .header("User-Agent", "NEXUS-Toolkit/1.0 (authorized-security-lab)")
                .header("Accept", "application/json")
                .get()
                .build()

            HttpClients.default.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext CveSearchResult(
                        q, 0, emptyList(),
                        "NVD HTTP ${response.code} — try again later"
                    )
                }
                val body = response.body?.string().orEmpty()
                val json = JSONObject(body)
                val total = json.optInt("totalResults", 0)
                val vulns = json.optJSONArray("vulnerabilities")
                val items = mutableListOf<CveItem>()
                if (vulns != null) {
                    for (i in 0 until vulns.length()) {
                        val cveObj = vulns.getJSONObject(i).getJSONObject("cve")
                        val id = cveObj.getString("id")
                        val desc = cveObj.optJSONArray("descriptions")
                            ?.let { arr ->
                                (0 until arr.length()).map { arr.getJSONObject(it) }
                                    .firstOrNull { it.optString("lang") == "en" }
                                    ?.optString("value")
                            }.orEmpty()

                        var severity: String? = null
                        var score: Double? = null
                        val metrics = cveObj.optJSONObject("metrics")
                        if (metrics != null) {
                            val cvss31 = metrics.optJSONArray("cvssMetricV31")
                            val cvss30 = metrics.optJSONArray("cvssMetricV30")
                            val cvss2 = metrics.optJSONArray("cvssMetricV2")
                            val metric = when {
                                cvss31 != null && cvss31.length() > 0 -> cvss31.getJSONObject(0)
                                cvss30 != null && cvss30.length() > 0 -> cvss30.getJSONObject(0)
                                cvss2 != null && cvss2.length() > 0 -> cvss2.getJSONObject(0)
                                else -> null
                            }
                            metric?.optJSONObject("cvssData")?.let { data ->
                                severity = data.optString("baseSeverity").ifBlank {
                                    metric.optString("baseSeverity")
                                }.ifBlank { null }
                                if (data.has("baseScore")) score = data.optDouble("baseScore")
                            }
                        }

                        items += CveItem(
                            id = id,
                            description = desc.take(400),
                            severity = severity,
                            score = score,
                            published = cveObj.optString("published").ifBlank { null },
                            url = "https://nvd.nist.gov/vuln/detail/$id"
                        )
                    }
                }
                CveSearchResult(q, total, items)
            }
        } catch (e: Exception) {
            CveSearchResult(q, 0, emptyList(), e.message)
        }
    }
}
