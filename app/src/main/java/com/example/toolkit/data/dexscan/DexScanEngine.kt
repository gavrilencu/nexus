package com.example.toolkit.data.dexscan

import android.content.Context
import android.net.Uri
import com.example.toolkit.data.apk.ApkInspectorEngine
import com.example.toolkit.data.apk.InstalledApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

data class DexFinding(
    val category: String,
    val severity: String,     // HIGH | MEDIUM | LOW
    val api: String,
    val description: String,
    val hits: Int
)

data class DexScanResult(
    val label: String,
    val dexCount: Int = 0,
    val findings: List<DexFinding> = emptyList(),
    val riskScore: Int = 0,
    val error: String? = null
)

/**
 * DEX dangerous-API scanner. Reuses [ApkInspectorEngine] to obtain the working
 * archive, then streams every classes*.dex string pool and matches it against a
 * rule set of risky APIs — WebView JS bridges, weak crypto, insecure TLS,
 * command execution, dynamic code loading, world-readable storage — reporting
 * which categories are present and how often.
 */
class DexScanEngine {

    private val inspector = ApkInspectorEngine()
    private val maxBytesPerDex = 24L * 1024 * 1024

    private data class Rule(val category: String, val severity: String, val marker: String, val description: String)

    private val rules = listOf(
        Rule("WebView JS bridge", "HIGH", "addJavascriptInterface", "Exposes native objects to JS — RCE if content is untrusted."),
        Rule("WebView", "MEDIUM", "setJavaScriptEnabled", "JavaScript enabled in WebView."),
        Rule("WebView", "HIGH", "setAllowUniversalAccessFromFileURLs", "file:// pages can access any origin."),
        Rule("WebView", "MEDIUM", "setAllowFileAccessFromFileURLs", "file:// pages can read other local files."),
        Rule("WebView", "LOW", "setAllowFileAccess", "WebView file access enabled."),
        Rule("Weak crypto", "HIGH", "AES/ECB", "ECB mode leaks plaintext patterns."),
        Rule("Weak crypto", "HIGH", "DES/", "DES is broken; use AES."),
        Rule("Weak crypto", "MEDIUM", "DESede", "3DES is deprecated."),
        Rule("Weak crypto", "MEDIUM", "RC4", "RC4 stream cipher is broken."),
        Rule("Weak hashing", "MEDIUM", "MD5", "MD5 is collision-prone; avoid for security."),
        Rule("Weak hashing", "LOW", "SHA-1", "SHA-1 is weak for signatures."),
        Rule("Insecure TLS", "HIGH", "AllowAllHostnameVerifier", "Disables hostname verification — MITM."),
        Rule("Insecure TLS", "HIGH", "ALLOW_ALL_HOSTNAME_VERIFIER", "Accepts any hostname — MITM."),
        Rule("Insecure TLS", "HIGH", "X509TrustManager", "Custom trust manager — verify it isn't trust-all."),
        Rule("Insecure TLS", "MEDIUM", "setHostnameVerifier", "Custom hostname verifier — verify correctness."),
        Rule("Insecure TLS", "MEDIUM", "SSLSocketFactory", "Manual SSL socket handling."),
        Rule("Command execution", "HIGH", "Ljava/lang/Runtime;", "Runtime.exec — native command execution."),
        Rule("Command execution", "HIGH", "Ljava/lang/ProcessBuilder;", "ProcessBuilder — spawns OS processes."),
        Rule("Command execution", "MEDIUM", "/system/bin/sh", "Shell invocation reference."),
        Rule("Dynamic code loading", "HIGH", "Ldalvik/system/DexClassLoader;", "Loads DEX at runtime — code injection risk."),
        Rule("Dynamic code loading", "MEDIUM", "Ldalvik/system/PathClassLoader;", "Dynamic class loading."),
        Rule("Native load", "MEDIUM", "Ljava/lang/System;->load", "Loads native libraries dynamically."),
        Rule("Insecure storage", "HIGH", "MODE_WORLD_READABLE", "World-readable storage — data leaks to other apps."),
        Rule("Insecure storage", "HIGH", "MODE_WORLD_WRITEABLE", "World-writable storage — tampering by other apps."),
        Rule("Insecure random", "LOW", "Ljava/util/Random;", "Non-cryptographic RNG; use SecureRandom for secrets."),
        Rule("SQL", "MEDIUM", "rawQuery", "Raw SQL query — check for injection."),
        Rule("SQL", "MEDIUM", "execSQL", "Direct SQL execution — check for injection."),
        Rule("Logging", "LOW", "Landroid/util/Log;", "Verbose logging may leak sensitive data."),
        Rule("Root check", "LOW", "/system/app/Superuser", "Root detection / SU reference."),
        Rule("Root check", "LOW", "test-keys", "Custom-ROM / root detection reference.")
    )

    suspend fun listInstalled(context: Context): List<InstalledApp> = inspector.listInstalled(context)

    suspend fun scanFile(context: Context, uri: Uri): DexScanResult = withContext(Dispatchers.IO) {
        runCatching {
            val report = inspector.analyzeFile(context, uri)
            scanWorking(report.appLabel ?: report.source, report.workingFile)
        }.getOrElse { DexScanResult("", error = it.message ?: "Analysis failed") }
    }

    suspend fun scanInstalled(context: Context, pkg: String): DexScanResult = withContext(Dispatchers.IO) {
        runCatching {
            val report = inspector.analyzeInstalled(context, pkg)
            scanWorking(report.appLabel ?: pkg, report.workingFile)
        }.getOrElse { DexScanResult("", error = it.message ?: "Analysis failed") }
    }

    private fun scanWorking(label: String, workingFile: String?): DexScanResult {
        if (workingFile == null) return DexScanResult(label, error = "No working archive to scan.")
        val counts = HashMap<String, Int>()
        var dexCount = 0
        ZipFile(File(workingFile)).use { zf ->
            val entries = zf.entries()
            while (entries.hasMoreElements()) {
                val e = entries.nextElement()
                if (e.isDirectory) continue
                val short = e.name.substringAfterLast('/')
                if (short.startsWith("classes") && short.endsWith(".dex")) {
                    dexCount++
                    scanDex(zf, e, counts)
                }
            }
        }
        val findings = rules.mapNotNull { rule ->
            val hits = counts[rule.marker] ?: 0
            if (hits > 0) DexFinding(rule.category, rule.severity, rule.marker, rule.description, hits) else null
        }.sortedWith(compareBy({ sevOrder(it.severity) }, { it.category }))

        val risk = findings.sumOf { when (it.severity) { "HIGH" -> 12; "MEDIUM" -> 6; else -> 2 } }.coerceAtMost(100)
        return DexScanResult(label, dexCount, findings, risk)
    }

    private fun scanDex(zf: ZipFile, entry: ZipEntry, counts: HashMap<String, Int>) {
        var consumed = 0L
        runCatching {
            zf.getInputStream(entry).use { input ->
                val buf = ByteArray(64 * 1024)
                val token = StringBuilder(256)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    consumed += n
                    for (i in 0 until n) {
                        val b = buf[i].toInt()
                        if (b in 0x20..0x7E) {
                            if (token.length < 8192) token.append(b.toChar())
                        } else {
                            if (token.length >= 3) matchToken(token.toString(), counts)
                            token.setLength(0)
                        }
                    }
                    if (consumed >= maxBytesPerDex) break
                }
                if (token.length >= 3) matchToken(token.toString(), counts)
            }
        }
    }

    private fun matchToken(token: String, counts: HashMap<String, Int>) {
        for (rule in rules) {
            if (token.contains(rule.marker)) counts[rule.marker] = (counts[rule.marker] ?: 0) + 1
        }
    }

    private fun sevOrder(s: String) = when (s) { "HIGH" -> 0; "MEDIUM" -> 1; else -> 2 }
}
