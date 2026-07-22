package com.example.toolkit.data.deeplink

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.example.toolkit.data.apk.ApkInspectorEngine
import com.example.toolkit.data.apk.InstalledApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DeepLinkTarget(
    val uri: String,
    val scheme: String,
    val host: String?,
    val autoVerify: Boolean
)

data class IntentHandler(val packageName: String, val activity: String, val label: String)

data class DeepLinkEnum(
    val packageName: String,
    val label: String,
    val links: List<DeepLinkTarget> = emptyList(),
    val error: String? = null
)

/**
 * Deep-link / intent tester. Reuses [ApkInspectorEngine] to read a target app's
 * manifest and enumerate its BROWSABLE VIEW intent-filters (schemes/hosts),
 * resolves which installed apps handle a given URI via the PackageManager, and
 * fires real ACTION_VIEW intents so you can test how each link is handled.
 */
class DeepLinkEngine {

    private val inspector = ApkInspectorEngine()

    suspend fun listInstalled(context: Context): List<InstalledApp> = inspector.listInstalled(context)

    suspend fun enumerate(context: Context, pkg: String): DeepLinkEnum = withContext(Dispatchers.IO) {
        runCatching {
            val report = inspector.analyzeInstalled(context, pkg)
            val manifest = report.manifestXml ?: ""
            val links = parseDeepLinks(manifest)
            DeepLinkEnum(pkg, report.appLabel ?: pkg, links)
        }.getOrElse { DeepLinkEnum(pkg, pkg, error = it.message ?: "Failed to read manifest") }
    }

    fun resolveHandlers(context: Context, uri: String): List<IntentHandler> {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
            val pm = context.packageManager
            val flags = PackageManager.MATCH_DEFAULT_ONLY or PackageManager.MATCH_ALL
            pm.queryIntentActivities(intent, flags).map { ri ->
                IntentHandler(
                    packageName = ri.activityInfo.packageName,
                    activity = ri.activityInfo.name,
                    label = runCatching { ri.loadLabel(pm).toString() }.getOrDefault(ri.activityInfo.packageName)
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    fun launch(context: Context, uri: String, targetPackage: String? = null): String {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (targetPackage != null) setPackage(targetPackage)
            }
            context.startActivity(intent)
            "Launched $uri" + (targetPackage?.let { " → $it" } ?: "")
        } catch (e: android.content.ActivityNotFoundException) {
            "No app handles $uri"
        } catch (e: Exception) {
            "Launch failed: ${e.message}"
        }
    }

    private fun parseDeepLinks(manifest: String): List<DeepLinkTarget> {
        val out = LinkedHashSet<DeepLinkTarget>()
        val filterRe = Regex("<intent-filter([^>]*)>(.*?)</intent-filter>", RegexOption.DOT_MATCHES_ALL)
        filterRe.findAll(manifest).forEach { f ->
            val body = f.groupValues[2]
            if (!body.contains("android.intent.category.BROWSABLE") || !body.contains("android.intent.action.VIEW")) return@forEach
            val autoVerify = f.groupValues[1].contains("android:autoVerify=\"true\"")
            val schemes = Regex("android:scheme=\"([^\"]+)\"").findAll(body).map { it.groupValues[1] }.toList()
            val hosts = Regex("android:host=\"([^\"]+)\"").findAll(body).map { it.groupValues[1] }.toList()
            if (schemes.isEmpty()) return@forEach
            schemes.forEach { scheme ->
                if (hosts.isEmpty()) {
                    out.add(DeepLinkTarget("$scheme://", scheme, null, autoVerify))
                } else hosts.forEach { host ->
                    out.add(DeepLinkTarget("$scheme://$host/", scheme, host, autoVerify))
                }
            }
        }
        return out.toList()
    }
}
