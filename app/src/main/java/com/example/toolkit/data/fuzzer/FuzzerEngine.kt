package com.example.toolkit.data.fuzzer

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

data class FuzzHit(
    val word: String,
    val url: String,
    val status: Int,
    val size: Long,
    val words: Int,
    val lines: Int
)

data class FuzzFilter(
    val hideStatus: Set<Int> = setOf(404),
    val hideSizes: Set<Long> = emptySet(),
    val hideWords: Set<Int> = emptySet(),
    val matchStatus: Set<Int> = emptySet()
)

/**
 * Generic ffuf-style fuzzer. The user places a `FUZZ` keyword anywhere in the
 * URL (path, query value, subdomain, or Host header via `FUZZ.` prefix) and the
 * engine substitutes each wordlist entry, then filters responses by
 * status/size/word-count to surface only the interesting hits.
 */
class FuzzerEngine {

    companion object {
        const val KEYWORD = "FUZZ"

        val WORDLIST: List<String> = (DEFAULT).distinct()

        private val DEFAULT: List<String> get() = listOf(
            "admin", "api", "app", "auth", "backup", "beta", "blog", "cdn", "config",
            "dashboard", "data", "db", "dev", "docs", "files", "ftp", "git", "graphql",
            "help", "home", "images", "internal", "login", "mail", "media", "mobile",
            "news", "old", "panel", "portal", "private", "prod", "public", "secure",
            "server", "shop", "staging", "static", "status", "store", "support", "test",
            "upload", "uploads", "user", "users", "v1", "v2", "vpn", "web", "www",
            "id", "page", "q", "query", "search", "file", "path", "url", "redirect",
            "index.php", "index.html", "home.php", "main.php", "default.aspx"
        )
    }

    sealed class Event {
        data class Started(val total: Int) : Event()
        data class Progress(val scanned: Int, val total: Int, val hits: List<FuzzHit>) : Event()
        data class Completed(val hits: List<FuzzHit>) : Event()
        data class Error(val message: String) : Event()
    }

    fun fuzz(
        rawUrl: String,
        wordlist: List<String> = WORDLIST,
        filter: FuzzFilter = FuzzFilter(),
        concurrency: Int = 20
    ): Flow<Event> = callbackFlow {
        val template = normalize(rawUrl)
        if (template == null || !template.contains(KEYWORD)) {
            trySend(Event.Error("URL must contain the FUZZ keyword, e.g. https://target.com/FUZZ"))
            close(); return@callbackFlow
        }

        trySend(Event.Started(wordlist.size))
        val hits = mutableListOf<FuzzHit>()
        val scanned = AtomicInteger(0)
        val sem = Semaphore(concurrency)

        val job = launch(Dispatchers.IO) {
            coroutineScope {
                wordlist.map { word ->
                    async {
                        sem.withPermit {
                            val hit = probe(template, word)
                            val count = scanned.incrementAndGet()
                            if (hit != null && keep(hit, filter)) synchronized(hits) { hits.add(hit) }
                            val snap = synchronized(hits) { hits.sortedBy { it.status }.toList() }
                            trySend(Event.Progress(count, wordlist.size, snap))
                        }
                    }
                }.awaitAll()
            }
            trySend(Event.Completed(synchronized(hits) { hits.sortedBy { it.status }.toList() }))
            close()
        }
        awaitClose { job.cancel() }
    }.flowOn(Dispatchers.Default)

    private fun keep(hit: FuzzHit, f: FuzzFilter): Boolean {
        if (f.matchStatus.isNotEmpty()) return hit.status in f.matchStatus
        if (hit.status in f.hideStatus) return false
        if (hit.size in f.hideSizes) return false
        if (hit.words in f.hideWords) return false
        return true
    }

    private fun probe(template: String, word: String): FuzzHit? {
        val hostFuzz = template.contains("://$KEYWORD.") || template.contains(".$KEYWORD.")
        val url = template.replace(KEYWORD, word)
        return try {
            val builder = Request.Builder()
                .header("User-Agent", "Mozilla/5.0 NEXUS-Toolkit/1.0")
            if (hostFuzz) builder.url(url) else builder.url(url)
            val request = builder.get().build()
            HttpClients.noRedirect.newCall(request).execute().use { resp ->
                val body = resp.peekBody(200_000L).string()
                FuzzHit(
                    word = word, url = url, status = resp.code,
                    size = resp.header("Content-Length")?.toLongOrNull() ?: body.length.toLong(),
                    words = body.split(Regex("\\s+")).count { it.isNotBlank() },
                    lines = body.count { it == '\n' } + 1
                )
            }
        } catch (_: Exception) { null }
    }

    private fun normalize(raw: String): String? {
        var s = raw.trim()
        if (s.isBlank()) return null
        if (!s.startsWith("http://") && !s.startsWith("https://")) s = "https://$s"
        return s
    }
}
