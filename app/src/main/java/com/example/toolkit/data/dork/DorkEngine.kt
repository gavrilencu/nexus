package com.example.toolkit.data.dork

import com.example.toolkit.data.network.HttpClients
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

data class Dork(val category: String, val label: String, val query: String, val url: String)

data class GithubHit(val repo: String, val path: String, val htmlUrl: String)

data class GithubSearchResult(
    val totalCount: Int = 0,
    val hits: List<GithubHit> = emptyList(),
    val error: String? = null
)

/**
 * Dorking helper. Generates ready-to-run Google and GitHub search queries for a
 * target (exposed files, login panels, config leaks, secrets) and — when a
 * GitHub token is supplied — runs live GitHub code searches for leaked secrets
 * referencing the target.
 */
class DorkEngine {

    fun googleDorks(target: String): List<Dork> {
        val t = target.trim()
        val site = "site:$t"
        val defs = listOf(
            "Files" to listOf(
                "Exposed directories" to "$site intitle:\"index of\"",
                "Backup / archive files" to "$site (ext:bak OR ext:old OR ext:zip OR ext:tar OR ext:gz OR ext:rar)",
                "SQL dumps" to "$site (ext:sql OR ext:dbf OR ext:mdb)",
                "Log files" to "$site ext:log",
                "Config files" to "$site (ext:env OR ext:ini OR ext:conf OR ext:cfg OR ext:yaml OR ext:yml)",
                "Documents" to "$site (ext:pdf OR ext:doc OR ext:docx OR ext:xls OR ext:xlsx)"
            ),
            "Login & Admin" to listOf(
                "Login portals" to "$site (inurl:login OR inurl:signin OR intitle:login)",
                "Admin panels" to "$site (inurl:admin OR inurl:administrator OR intitle:admin)",
                "phpMyAdmin" to "$site inurl:phpmyadmin",
                "Dashboards" to "$site (inurl:dashboard OR inurl:panel)"
            ),
            "Secrets & Errors" to listOf(
                "API keys / tokens" to "$site (intext:\"api_key\" OR intext:\"apikey\" OR intext:\"access_token\")",
                "Passwords in text" to "$site (intext:\"password\" OR intext:\"passwd\" OR intext:\"pwd\")",
                "SQL errors" to "$site (intext:\"sql syntax near\" OR intext:\"syntax error has occurred\")",
                "PHP errors" to "$site \"PHP Parse error\" OR \"PHP Warning\" OR \"PHP Error\"",
                "Env files" to "$site inurl:.env"
            ),
            "Infra & Cloud" to listOf(
                "Open S3 buckets" to "site:s3.amazonaws.com $t",
                "Git exposed" to "$site inurl:.git",
                "Jenkins" to "$site intitle:\"Dashboard [Jenkins]\"",
                "Swagger / API docs" to "$site (inurl:swagger OR inurl:api-docs OR intitle:swagger)"
            )
        )
        return defs.flatMap { (cat, items) ->
            items.map { (label, q) -> Dork(cat, label, q, "https://www.google.com/search?q=" + enc(q)) }
        }
    }

    fun githubDorks(target: String): List<Dork> {
        val t = target.trim()
        val queries = listOf(
            "Passwords" to "\"$t\" password",
            "API keys" to "\"$t\" api_key",
            "Secrets" to "\"$t\" secret",
            "Tokens" to "\"$t\" access_token",
            "AWS keys" to "\"$t\" aws_secret_access_key",
            "Private keys" to "\"$t\" \"BEGIN RSA PRIVATE KEY\"",
            "Env files" to "\"$t\" filename:.env",
            "Config" to "\"$t\" filename:config",
            "DB connection" to "\"$t\" jdbc OR mongodb OR postgres://",
            "Internal URLs" to "\"$t\" filename:.yml OR filename:.yaml"
        )
        return queries.map { (label, q) ->
            Dork("GitHub", label, q, "https://github.com/search?type=code&q=" + enc(q))
        }
    }

    suspend fun searchGithub(target: String, query: String, token: String): GithubSearchResult =
        withContext(Dispatchers.IO) {
            if (token.isBlank()) return@withContext GithubSearchResult(error = "GitHub token required for live code search.")
            val q = "\"$target\" $query"
            try {
                val req = Request.Builder()
                    .url("https://api.github.com/search/code?q=" + enc(q) + "&per_page=30")
                    .header("Authorization", "Bearer $token")
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "NEXUS-Toolkit/1.0")
                    .get().build()
                HttpClients.default.newCall(req).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        val msg = runCatching { JSONObject(body).optString("message") }.getOrNull()
                        return@withContext GithubSearchResult(error = "GitHub HTTP ${resp.code}: ${msg ?: ""}")
                    }
                    val json = JSONObject(body)
                    val total = json.optInt("total_count")
                    val items = json.optJSONArray("items")
                    val hits = ArrayList<GithubHit>()
                    if (items != null) for (i in 0 until items.length()) {
                        val it = items.getJSONObject(i)
                        hits.add(GithubHit(
                            repo = it.optJSONObject("repository")?.optString("full_name") ?: "?",
                            path = it.optString("path"),
                            htmlUrl = it.optString("html_url")
                        ))
                    }
                    GithubSearchResult(total, hits)
                }
            } catch (e: Exception) {
                GithubSearchResult(error = e.message ?: "Request failed")
            }
        }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}
