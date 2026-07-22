package com.example.toolkit.data.firebase

import android.content.Context
import android.net.Uri
import com.example.toolkit.data.apk.ApkInspectorEngine
import com.example.toolkit.data.apk.InstalledApp
import com.example.toolkit.data.network.HttpClients
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

data class FirebaseCheck(
    val database: String,
    val url: String,
    val status: Int,
    val readable: Boolean,
    val severity: String,     // HIGH | INFO | OK | ERROR
    val detail: String,
    val sample: String? = null
)

data class FirebaseResult(
    val source: String,
    val databases: List<String> = emptyList(),
    val checks: List<FirebaseCheck> = emptyList(),
    val error: String? = null
)

/**
 * Firebase Realtime Database misconfiguration checker. Collects candidate
 * Firebase database URLs (from a picked/installed APK via [ApkInspectorEngine],
 * or entered manually), then requests each database's `/.json` root to detect
 * publicly readable databases (the classic open-Firebase leak).
 */
class FirebaseEngine {

    private val inspector = ApkInspectorEngine()
    private val client = HttpClients.default

    suspend fun listInstalled(context: Context): List<InstalledApp> = inspector.listInstalled(context)

    suspend fun fromFile(context: Context, uri: Uri): FirebaseResult = withContext(Dispatchers.IO) {
        runCatching {
            val report = inspector.analyzeFile(context, uri)
            check(report.appLabel ?: report.source, collectDbs(report.urls + report.secrets.map { it.value }))
        }.getOrElse { FirebaseResult("", error = it.message ?: "Analysis failed") }
    }

    suspend fun fromInstalled(context: Context, pkg: String): FirebaseResult = withContext(Dispatchers.IO) {
        runCatching {
            val report = inspector.analyzeInstalled(context, pkg)
            check(report.appLabel ?: pkg, collectDbs(report.urls + report.secrets.map { it.value }))
        }.getOrElse { FirebaseResult("", error = it.message ?: "Analysis failed") }
    }

    suspend fun fromManual(input: String): FirebaseResult = withContext(Dispatchers.IO) {
        val dbs = collectDbs(listOf(input)) + normalizeToDb(input)?.let { listOf(it) }.orEmpty()
        check("Manual", dbs.distinct())
    }

    private fun check(source: String, databases: List<String>): FirebaseResult {
        if (databases.isEmpty()) return FirebaseResult(source, error = "No Firebase database URLs found.")
        val checks = databases.map { db -> probe(db) }
        return FirebaseResult(source, databases, checks)
    }

    private fun probe(db: String): FirebaseCheck {
        val url = "https://$db/.json"
        return try {
            val req = Request.Builder().url(url).header("User-Agent", "NEXUS-Toolkit/1.0").get().build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string()?.take(2000).orEmpty()
                when {
                    resp.code == 200 && !body.contains("Permission denied") && body != "null" && body.isNotBlank() ->
                        FirebaseCheck(db, url, 200, true, "HIGH",
                            "Database root is PUBLICLY READABLE — anyone can read the data.", body.take(600))
                    resp.code == 200 && body == "null" ->
                        FirebaseCheck(db, url, 200, false, "INFO",
                            "Readable but root is empty (null). Check specific paths.", null)
                    resp.code == 401 || body.contains("Permission denied") ->
                        FirebaseCheck(db, url, resp.code, false, "OK",
                            "Access denied — read rules are locked down. Good.", null)
                    resp.code == 404 ->
                        FirebaseCheck(db, url, 404, false, "OK", "Database not found / project inactive.", null)
                    else ->
                        FirebaseCheck(db, url, resp.code, false, "INFO", "HTTP ${resp.code}", body.take(300))
                }
            }
        } catch (e: Exception) {
            FirebaseCheck(db, url, 0, false, "ERROR", "Request failed: ${e.message}", null)
        }
    }

    private fun collectDbs(strings: List<String>): List<String> {
        val out = LinkedHashSet<String>()
        val re = Regex("([a-z0-9-]+(?:-default-rtdb)?\\.(?:firebaseio\\.com|[a-z0-9-]+\\.firebasedatabase\\.app))")
        strings.forEach { s ->
            re.findAll(s).forEach { out.add(it.groupValues[1]) }
            // firebaseapp.com project ids → derive default rtdb guess
            Regex("([a-z0-9-]+)\\.firebaseapp\\.com").findAll(s).forEach {
                out.add("${it.groupValues[1]}.firebaseio.com")
                out.add("${it.groupValues[1]}-default-rtdb.firebaseio.com")
            }
        }
        return out.toList()
    }

    private fun normalizeToDb(input: String): String? {
        val s = input.trim().removePrefix("https://").removePrefix("http://").substringBefore("/")
        return when {
            s.endsWith("firebaseio.com") || s.endsWith("firebasedatabase.app") -> s
            s.isNotBlank() && !s.contains(".") -> "$s-default-rtdb.firebaseio.com"
            else -> null
        }
    }
}
