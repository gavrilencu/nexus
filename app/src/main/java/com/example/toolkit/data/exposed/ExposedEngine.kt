package com.example.toolkit.data.exposed

import com.example.toolkit.data.network.HttpClients
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.Request

data class ExposedHit(
    val path: String,
    val url: String,
    val status: Int,
    val size: Long,
    val severity: String,   // HIGH | MEDIUM | LOW
    val confirmed: Boolean,
    val note: String
)

data class ExposedResult(
    val base: String,
    val hits: List<ExposedHit> = emptyList(),
    val checked: Int = 0,
    val error: String? = null
)

/**
 * Sensitive/exposed file scanner. Requests a curated list of high-value files
 * (VCS metadata, env files, backups, configs) and confirms real exposure by
 * matching content signatures — not just the HTTP status — to avoid soft-404
 * false positives.
 */
class ExposedEngine {

    private val client = HttpClients.noRedirect

    private data class Probe(val path: String, val severity: String, val signature: Regex?, val note: String)

    private val probes = listOf(
        Probe(".git/HEAD", "HIGH", Regex("ref:\\s*refs/"), "Git repo exposed — full source recoverable."),
        Probe(".git/config", "HIGH", Regex("\\[core\\]|repositoryformatversion"), "Git config exposed."),
        Probe(".gitignore", "LOW", null, "Reveals internal file/path names."),
        Probe(".env", "HIGH", Regex("(?i)(APP_KEY|DB_PASSWORD|SECRET|API_KEY|AWS_)"), "Environment file with credentials."),
        Probe(".env.local", "HIGH", Regex("(?i)(APP_KEY|DB_|SECRET|API_KEY)"), "Local env file."),
        Probe(".env.backup", "HIGH", Regex("(?i)(APP_KEY|DB_|SECRET)"), "Backup env file."),
        Probe(".svn/entries", "HIGH", Regex("\\d+\\s|dir|svn:"), "SVN metadata exposed."),
        Probe(".hg/requires", "MEDIUM", null, "Mercurial metadata exposed."),
        Probe(".DS_Store", "LOW", Regex("Bud1|\\x00\\x00\\x00\\x01Bud1"), "macOS directory listing artifact."),
        Probe("backup.zip", "HIGH", null, "Backup archive exposed."),
        Probe("backup.tar.gz", "HIGH", null, "Backup archive exposed."),
        Probe("backup.sql", "HIGH", Regex("(?i)(INSERT INTO|CREATE TABLE|DROP TABLE)"), "SQL dump exposed."),
        Probe("dump.sql", "HIGH", Regex("(?i)(INSERT INTO|CREATE TABLE)"), "SQL dump exposed."),
        Probe("database.sql", "HIGH", Regex("(?i)(INSERT INTO|CREATE TABLE)"), "SQL dump exposed."),
        Probe("db.sqlite", "HIGH", Regex("SQLite format 3"), "SQLite database exposed."),
        Probe("config.php.bak", "HIGH", Regex("<\\?php|password|define\\("), "PHP config backup."),
        Probe("wp-config.php.bak", "HIGH", Regex("DB_PASSWORD|DB_NAME"), "WordPress config backup."),
        Probe("web.config", "MEDIUM", Regex("<configuration|connectionStrings"), "IIS/.NET config exposed."),
        Probe(".htpasswd", "HIGH", Regex("apr1|\\{SHA\\}|:\\d+:\\d+:|[A-Za-z0-9./]{13}"), "Basic-auth password file."),
        Probe(".htaccess", "LOW", Regex("RewriteRule|AuthType|Order"), "Apache config exposed."),
        Probe("phpinfo.php", "MEDIUM", Regex("phpinfo\\(\\)|PHP Version"), "phpinfo() page exposed."),
        Probe("info.php", "MEDIUM", Regex("phpinfo\\(\\)|PHP Version"), "phpinfo() page exposed."),
        Probe("composer.json", "LOW", Regex("\"require\"|\"name\""), "Dependency manifest."),
        Probe("composer.lock", "LOW", null, "Locked dependency versions."),
        Probe("package.json", "LOW", Regex("\"dependencies\"|\"name\""), "Node manifest."),
        Probe(".npmrc", "MEDIUM", Regex("_authToken|registry="), "npm auth token may leak."),
        Probe("Dockerfile", "LOW", Regex("FROM |RUN |COPY "), "Dockerfile exposed."),
        Probe("docker-compose.yml", "MEDIUM", Regex("services:|image:|environment:"), "Compose file with env."),
        Probe(".aws/credentials", "HIGH", Regex("aws_access_key_id|aws_secret"), "AWS credentials exposed."),
        Probe("id_rsa", "HIGH", Regex("BEGIN (RSA |OPENSSH )?PRIVATE KEY"), "SSH private key exposed."),
        Probe("server-status", "MEDIUM", Regex("Apache Server Status|Server Version"), "Apache status page."),
        Probe("actuator/env", "HIGH", Regex("\"propertySources\"|activeProfiles"), "Spring Actuator env exposed."),
        Probe("actuator/health", "LOW", Regex("\\{\"status\""), "Spring Actuator health."),
        Probe("phpMyAdmin/", "MEDIUM", Regex("phpMyAdmin|pma_"), "phpMyAdmin exposed."),
        Probe(".well-known/security.txt", "LOW", Regex("Contact:|Policy:"), "security.txt present (informational).")
    )

    suspend fun scan(rawUrl: String): ExposedResult = withContext(Dispatchers.IO) {
        val base = normalize(rawUrl)
        try {
            val hits = coroutineScope {
                probes.map { p -> async { check(base, p) } }.awaitAll().filterNotNull()
            }
            ExposedResult(base, hits.sortedByDescending { it.confirmed }, probes.size)
        } catch (e: Exception) {
            ExposedResult(base, error = e.message ?: "Scan failed")
        }
    }

    private fun check(base: String, p: Probe): ExposedHit? {
        val url = base + p.path
        return try {
            val req = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0 NEXUS-Toolkit/1.0").get().build()
            client.newCall(req).execute().use { resp ->
                if (resp.code !in intArrayOf(200, 206)) return null
                val body = resp.peekBody(65_536L).string()
                val confirmed = p.signature?.containsMatchIn(body) ?: true
                if (!confirmed && p.signature != null) return null
                ExposedHit(
                    path = "/${p.path}", url = url, status = resp.code,
                    size = resp.header("Content-Length")?.toLongOrNull() ?: body.length.toLong(),
                    severity = p.severity, confirmed = confirmed,
                    note = p.note
                )
            }
        } catch (_: Exception) { null }
    }

    private fun normalize(raw: String): String {
        var s = raw.trim()
        if (!s.startsWith("http://") && !s.startsWith("https://")) s = "https://$s"
        if (!s.endsWith("/")) s += "/"
        return s
    }
}
