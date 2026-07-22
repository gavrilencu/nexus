package com.example.toolkit.data.apkaudit

import android.content.Context
import android.net.Uri
import com.example.toolkit.data.apk.ApkInspectorEngine
import com.example.toolkit.data.apk.ApkReport
import com.example.toolkit.data.apk.InstalledApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AuditFinding(
    val title: String,
    val severity: String,     // HIGH | MEDIUM | LOW | INFO | GOOD
    val detail: String,
    val evidence: String? = null
)

data class DeepLink(val scheme: String, val host: String?, val path: String?, val autoVerify: Boolean)

data class AuditResult(
    val label: String,
    val packageName: String?,
    val score: Int = 100,
    val grade: String = "A",
    val findings: List<AuditFinding> = emptyList(),
    val deepLinks: List<DeepLink> = emptyList(),
    val error: String? = null
)

/**
 * MobSF-style static security audit. Reuses [ApkInspectorEngine] to load the
 * package, then evaluates the decoded manifest and report against a hardening
 * checklist (debuggable, allowBackup, cleartext traffic, network security
 * config, exported components without permission, dangerous permissions, low
 * minSdk, deep links) and produces a weighted risk score + letter grade.
 */
class ApkSecurityEngine {

    val inspector = ApkInspectorEngine()

    suspend fun listInstalled(context: Context): List<InstalledApp> = inspector.listInstalled(context)

    suspend fun auditFile(context: Context, uri: Uri): AuditResult = withContext(Dispatchers.IO) {
        runCatching { audit(inspector.analyzeFile(context, uri)) }
            .getOrElse { AuditResult("", null, error = it.message ?: "Analysis failed") }
    }

    suspend fun auditInstalled(context: Context, pkg: String): AuditResult = withContext(Dispatchers.IO) {
        runCatching { audit(inspector.analyzeInstalled(context, pkg)) }
            .getOrElse { AuditResult("", null, error = it.message ?: "Analysis failed") }
    }

    private fun audit(r: ApkReport): AuditResult {
        val manifest = r.manifestXml ?: ""
        val findings = ArrayList<AuditFinding>()
        var score = 100

        fun add(title: String, sev: String, detail: String, penalty: Int, evidence: String? = null) {
            findings.add(AuditFinding(title, sev, detail, evidence))
            score -= penalty
        }

        // debuggable
        if (r.debuggable == true) add("Debuggable enabled", "HIGH",
            "android:debuggable=\"true\" ships debug hooks — attackers can attach a debugger and read memory/state.", 20)

        // allowBackup
        val allowBackup = Regex("android:allowBackup=\"(true|false)\"").find(manifest)?.groupValues?.get(1)
        if (allowBackup == "true" || (allowBackup == null && (r.targetSdk ?: 0) < 31)) {
            add("Backup allowed", "MEDIUM",
                "allowBackup is enabled (explicitly or by default) — app data can be extracted via adb backup.", 8,
                allowBackup?.let { "android:allowBackup=\"$it\"" })
        }

        // cleartext traffic
        val cleartext = Regex("android:usesCleartextTraffic=\"(true|false)\"").find(manifest)?.groupValues?.get(1)
        if (cleartext == "true") add("Cleartext traffic permitted", "HIGH",
            "usesCleartextTraffic=\"true\" allows plaintext HTTP — susceptible to MITM.", 15, "usesCleartextTraffic=\"true\"")
        else if (cleartext == null && (r.targetSdk ?: 99) < 28)
            add("Cleartext traffic (legacy default)", "MEDIUM",
                "targetSdk < 28 and no explicit setting — cleartext HTTP is allowed by default.", 8)

        // network security config
        val nsc = Regex("android:networkSecurityConfig=\"([^\"]+)\"").find(manifest)?.groupValues?.get(1)
        if (nsc == null) add("No Network Security Config", "LOW",
            "No custom network_security_config — cannot enforce cert pinning / restrict cleartext per-domain.", 4)
        else findings.add(AuditFinding("Network Security Config present", "GOOD", "Uses $nsc", null))

        // cleartext HTTP endpoints in code
        val httpUrls = r.urls.filter { it.startsWith("http://") }
        if (httpUrls.isNotEmpty()) add("HTTP URLs in code", "MEDIUM",
            "${httpUrls.size} plaintext http:// endpoint(s) referenced in the code.", 6,
            httpUrls.take(4).joinToString("\n"))

        // exported components without permission
        val exportedNoPerm = exportedWithoutPermission(manifest)
        if (exportedNoPerm.isNotEmpty()) {
            val providers = exportedNoPerm.count { it.first == "provider" }
            add("Exported components without permission", if (providers > 0) "HIGH" else "MEDIUM",
                "${exportedNoPerm.size} component(s) are exported with no android:permission guard — reachable by any app.",
                minOf(4 * exportedNoPerm.size, 20),
                exportedNoPerm.take(8).joinToString("\n") { "${it.first}: ${it.second.substringAfterLast('.')}" })
        }

        // dangerous permissions
        val dangerous = r.permissions.filter { p -> DANGEROUS.any { p.endsWith(it) } }
        if (dangerous.isNotEmpty()) findings.add(AuditFinding("Dangerous permissions (${dangerous.size})", "INFO",
            "Requests sensitive runtime permissions.", dangerous.joinToString("\n") { it.substringAfterLast('.') }))

        // low minSdk
        r.minSdk?.let { if (it < 24) add("Low minSdk ($it)", "LOW",
            "Supports old Android with unpatched platform vulnerabilities.", 3) }

        // task affinity / launchMode singleTask hijack hints (informational)
        if (manifest.contains("android:exported=\"true\"") && manifest.contains("android.intent.action.MAIN"))
            findings.add(AuditFinding("Exported launcher activity", "INFO", "Main activity is exported (normal for launchable apps).", null))

        // deep links
        val deepLinks = parseDeepLinks(manifest)
        if (deepLinks.any { it.scheme == "http" || it.scheme == "https" } && deepLinks.none { it.autoVerify })
            add("App Links without autoVerify", "LOW",
                "http/https deep links declared without android:autoVerify — link hijacking by other apps is possible.", 4)

        if (findings.none { it.severity in setOf("HIGH", "MEDIUM") })
            findings.add(0, AuditFinding("No high/medium manifest issues", "GOOD",
                "Manifest hardening looks reasonable.", null))

        score = score.coerceIn(0, 100)
        return AuditResult(
            label = r.appLabel ?: r.packageName ?: r.source,
            packageName = r.packageName,
            score = score, grade = gradeOf(score),
            findings = findings.sortedBy { sevOrder(it.severity) },
            deepLinks = deepLinks
        )
    }

    private fun exportedWithoutPermission(manifest: String): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        val tagRe = Regex("<(activity|service|receiver|provider)\\b([^>]*)>", RegexOption.DOT_MATCHES_ALL)
        tagRe.findAll(manifest).forEach { m ->
            val type = m.groupValues[1]
            val attrs = m.groupValues[2]
            val exported = Regex("android:exported=\"(true|false)\"").find(attrs)?.groupValues?.get(1)
            val hasPerm = attrs.contains("android:permission=")
            val name = Regex("android:name=\"([^\"]+)\"").find(attrs)?.groupValues?.get(1) ?: "?"
            if (exported == "true" && !hasPerm) out.add(type to name)
        }
        return out
    }

    private fun parseDeepLinks(manifest: String): List<DeepLink> {
        val out = LinkedHashSet<DeepLink>()
        // Find intent-filters that are browsable, then their data elements.
        val filterRe = Regex("<intent-filter([^>]*)>(.*?)</intent-filter>", RegexOption.DOT_MATCHES_ALL)
        filterRe.findAll(manifest).forEach { f ->
            val body = f.groupValues[2]
            val browsable = body.contains("android.intent.category.BROWSABLE")
            val view = body.contains("android.intent.action.VIEW")
            if (!browsable || !view) return@forEach
            val autoVerify = f.groupValues[1].contains("android:autoVerify=\"true\"")
            Regex("<data([^>]*)/?>").findAll(body).forEach { d ->
                val a = d.groupValues[1]
                val scheme = Regex("android:scheme=\"([^\"]+)\"").find(a)?.groupValues?.get(1)
                val host = Regex("android:host=\"([^\"]+)\"").find(a)?.groupValues?.get(1)
                val path = Regex("android:(?:path|pathPrefix|pathPattern)=\"([^\"]+)\"").find(a)?.groupValues?.get(1)
                if (scheme != null) out.add(DeepLink(scheme, host, path, autoVerify))
            }
        }
        return out.toList()
    }

    private fun sevOrder(s: String) = when (s) {
        "HIGH" -> 0; "MEDIUM" -> 1; "LOW" -> 2; "INFO" -> 3; else -> 4
    }

    private fun gradeOf(score: Int) = when {
        score >= 90 -> "A"; score >= 75 -> "B"; score >= 60 -> "C"; score >= 40 -> "D"; else -> "F"
    }

    companion object {
        private val DANGEROUS = listOf(
            "READ_CONTACTS", "WRITE_CONTACTS", "READ_SMS", "SEND_SMS", "RECEIVE_SMS", "READ_CALL_LOG",
            "WRITE_CALL_LOG", "CAMERA", "RECORD_AUDIO", "ACCESS_FINE_LOCATION", "ACCESS_BACKGROUND_LOCATION",
            "READ_EXTERNAL_STORAGE", "WRITE_EXTERNAL_STORAGE", "READ_PHONE_STATE", "QUERY_ALL_PACKAGES",
            "REQUEST_INSTALL_PACKAGES", "SYSTEM_ALERT_WINDOW", "MANAGE_EXTERNAL_STORAGE", "READ_MEDIA_IMAGES"
        )
    }
}
