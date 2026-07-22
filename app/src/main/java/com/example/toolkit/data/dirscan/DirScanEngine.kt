package com.example.toolkit.data.dirscan

import com.example.toolkit.data.network.HttpClients
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.Request
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

data class DirHit(
    val path: String,
    val url: String,
    val status: Int,
    val size: Long,
    val location: String? = null
)

/**
 * Content-discovery / directory brute-forcer (like dirb / gobuster / ffuf).
 * For every entry in the wordlist it issues a GET against base+entry and keeps
 * the responses whose status code is "interesting" (not 404 / not soft-404).
 * Redirects are NOT followed so 301/302 to a login wall stay visible.
 */
class DirScanEngine {

    companion object {
        private val INTERESTING = setOf(
            200, 201, 202, 203, 204, 206,
            301, 302, 303, 307, 308,
            401, 403, 405,
            500, 501, 503
        )

        val WORDLIST: List<String> = listOf(
            "admin", "administrator", "administrator/index.php", "login", "logout",
            "wp-admin", "wp-login.php", "wp-config.php", "wp-config.php.bak", "wp-json",
            "wp-json/wp/v2/users", "wp-content", "wp-includes",
            "dashboard", "panel", "cpanel", "console", "manage", "management",
            "config", "config.php", "config.json", ".env", ".env.bak", ".env.local",
            ".git/HEAD", ".git/config", ".gitignore", ".svn/entries", ".hg",
            "backup", "backups", "backup.zip", "backup.tar.gz", "backup.sql",
            "db.sql", "dump.sql", "database.sql", "site.zip", "www.zip",
            "api", "api/v1", "api/v2", "graphql", "swagger", "swagger-ui.html",
            "openapi.json", "api-docs", "v1", "v2",
            "robots.txt", "sitemap.xml", "crossdomain.xml", ".well-known/security.txt",
            "server-status", "server-info", "status", "health", "healthz",
            "metrics", "actuator", "actuator/health", "actuator/env", "actuator/mappings",
            "phpinfo.php", "info.php", "test", "test.php", "tests", "debug",
            "dev", "development", "staging", "stage", "prod", "old", "new",
            "tmp", "temp", "cache", "uploads", "upload", "files", "file",
            "images", "img", "assets", "static", "media", "documents", "docs",
            "js", "css", "includes", "inc", "lib", "libs", "vendor", "src",
            "node_modules", "storage", "logs", "log", "error.log", "access.log",
            "phpmyadmin", "pma", "adminer.php", "mysql", "sql",
            "webmail", "mail", "email", "portal", "intranet", "internal",
            "user", "users", "account", "accounts", "register", "signup", "signin",
            "settings", "setup", "install", "install.php", "installer",
            "readme.md", "README.md", "readme.txt", "CHANGELOG.md", "LICENSE",
            ".htaccess", ".htpasswd", "web.config", ".DS_Store", "Thumbs.db",
            "composer.json", "composer.lock", "package.json", "package-lock.json",
            "yarn.lock", "Dockerfile", "docker-compose.yml", ".dockerignore",
            "Gemfile", "requirements.txt", "Makefile",
            "secret", "secrets", "private", "hidden", "credentials", "creds",
            "jenkins", "gitlab", "grafana", "kibana", "jira", "monitoring",
            "cgi-bin", "cgi-bin/test-cgi", "shell", "cmd", "webshell"
        )
    }

    sealed class ScanEvent {
        data class Started(val target: String, val total: Int, val softNotice: String?) : ScanEvent()
        data class Progress(val scanned: Int, val total: Int, val hits: List<DirHit>) : ScanEvent()
        data class Completed(val target: String, val hits: List<DirHit>) : ScanEvent()
        data class Error(val message: String) : ScanEvent()
    }

    fun scan(
        rawUrl: String,
        wordlist: List<String> = WORDLIST,
        concurrency: Int = 16
    ): Flow<ScanEvent> = callbackFlow {
        val base = normalize(rawUrl)
        if (base == null) {
            trySend(ScanEvent.Error("Invalid URL"))
            close()
            return@callbackFlow
        }

        // Soft-404 baseline: hit a random path that should not exist and remember
        // how the server answers, so we can drop bogus 200s that echo the same body.
        val baseline = probe(base, "nexus-${System.currentTimeMillis()}-notexist-xyz")
        val baselineSize = if (baseline?.status == 200) baseline.size else -1L
        val softNotice = if (baseline != null && baseline.status in 200..399)
            "Serverul răspunde ${baseline.status} și pentru căi inexistente (soft-404) — rezultatele 200 cu aceeași dimensiune sunt filtrate."
        else null

        trySend(ScanEvent.Started(base, wordlist.size, softNotice))

        val hits = mutableListOf<DirHit>()
        val scanned = AtomicInteger(0)
        val semaphore = Semaphore(concurrency)

        val job = launch(Dispatchers.IO) {
            coroutineScope {
                wordlist.map { word ->
                    async {
                        semaphore.withPermit {
                            val hit = probe(base, word)
                            val count = scanned.incrementAndGet()
                            if (hit != null && interesting(hit, baselineSize)) {
                                synchronized(hits) { hits.add(hit) }
                            }
                            val snapshot = synchronized(hits) { hits.sortedByDescending { it.status == 200 }.toList() }
                            trySend(ScanEvent.Progress(count, wordlist.size, snapshot))
                        }
                    }
                }.awaitAll()
            }
            val finalHits = synchronized(hits) { hits.sortedBy { it.status }.toList() }
            trySend(ScanEvent.Completed(base, finalHits))
            close()
        }

        awaitClose { job.cancel() }
    }.flowOn(Dispatchers.Default)

    private fun interesting(hit: DirHit, baselineSize: Long): Boolean {
        if (hit.status == 404) return false
        if (hit.status == 200 && baselineSize >= 0 && abs(hit.size - baselineSize) < 48) return false
        return hit.status in INTERESTING
    }

    private fun probe(base: String, path: String): DirHit? {
        val url = base + path
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android) NEXUS-Toolkit/1.0")
                .get()
                .build()
            HttpClients.noRedirect.newCall(request).execute().use { resp ->
                val declared = resp.header("Content-Length")?.toLongOrNull()
                val size = declared?.takeIf { it >= 0 } ?: resp.peekBody(16_384L).contentLength()
                DirHit(
                    path = "/$path",
                    url = url,
                    status = resp.code,
                    size = size,
                    location = resp.header("Location")
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun normalize(raw: String): String? {
        var s = raw.trim()
        if (s.isBlank()) return null
        if (!s.startsWith("http://") && !s.startsWith("https://")) s = "https://$s"
        if (!s.endsWith("/")) s += "/"
        return s
    }
}
