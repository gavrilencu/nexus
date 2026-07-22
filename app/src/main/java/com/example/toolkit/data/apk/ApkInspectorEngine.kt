package com.example.toolkit.data.apk

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

data class TechFinding(val name: String, val evidence: String)
data class SecretFinding(val type: String, val value: String)
data class ComponentInfo(val name: String, val exported: Boolean)
data class DirSize(val name: String, val bytes: Long, val count: Int)
data class FileTypeCount(val ext: String, val count: Int, val bytes: Long)
data class NativeLib(val name: String, val abi: String, val bytes: Long)

/** A single ZIP entry, for the in-app file browser. */
data class EntryNode(val path: String, val size: Long)

enum class ContentKind { TEXT, XML, DEX, IMAGE, BINARY, EMPTY }

/** The decoded/previewable content of one archive entry. */
data class EntryContent(
    val path: String,
    val kind: ContentKind,
    val text: String?,
    val sizeBytes: Long,
    val note: String? = null
)

data class InstalledApp(
    val packageName: String,
    val label: String,
    val versionName: String?,
    val system: Boolean
)

data class CertDetail(
    val subject: String,
    val issuer: String,
    val serial: String,
    val algorithm: String,
    val publicKey: String,
    val validFrom: String,
    val validUntil: String,
    val sha256: String,
    val sha1: String
)

data class ApkReport(
    val source: String,
    val origin: String,
    val archiveType: String,
    val workingFile: String?,
    val sizeBytes: Long,
    val packageName: String?,
    val appLabel: String?,
    val versionName: String?,
    val versionCode: Long?,
    val minSdk: Int?,
    val targetSdk: Int?,
    val compileSdk: Int?,
    val installLocation: String?,
    val permissions: List<String>,
    val features: List<String>,
    val activities: List<ComponentInfo>,
    val services: List<ComponentInfo>,
    val receivers: List<ComponentInfo>,
    val providers: List<ComponentInfo>,
    val abis: List<String>,
    val nativeLibs: List<NativeLib>,
    val languages: List<String>,
    val frameworks: List<TechFinding>,
    val sdks: List<TechFinding>,
    val cert: CertDetail?,
    val v1Signed: Boolean,
    val debuggable: Boolean?,
    val dexCount: Int,
    val classCount: Long,
    val methodCount: Long,
    val stringCount: Long,
    val obfuscationScore: Int,
    val obfuscationLabel: String,
    val urls: List<String>,
    val ips: List<String>,
    val emails: List<String>,
    val secrets: List<SecretFinding>,
    val topDirs: List<DirSize>,
    val fileTypes: List<FileTypeCount>,
    val entries: List<EntryNode>,
    val entryCount: Int,
    val manifestXml: String?,
    val notes: List<String>
)

/**
 * On-device static analyzer + reverse-engineering helper for Android app
 * packages (`.apk`, `.aab`, `.xapk`, `.apks`, `.apkm`), installed or not.
 *
 * Beyond identity/signing/permissions it decodes the binary AndroidManifest,
 * fingerprints the language/framework and the bundled SDKs/trackers, reads
 * DEX metrics + an obfuscation estimate, greps URLs/IPs/emails/secrets from
 * the code, and exposes the full ZIP entry tree so the UI can browse the
 * project structure and open individual files. It can also unzip the whole
 * package into a user-chosen folder via the Storage Access Framework.
 *
 * The working archive is kept in cache for the session so browsing/extracting
 * doesn't need to re-copy; the previous one is cleaned up on the next analyze.
 */
class ApkInspectorEngine {

    private val maxScanBytesPerDex = 16L * 1024 * 1024
    private val maxTotalScanBytes = 64L * 1024 * 1024
    private val maxUrls = 400
    private val maxList = 200
    private val maxEntries = 25000

    private val cacheFiles = mutableListOf<File>()

    // ---- public API -------------------------------------------------------

    suspend fun listInstalled(context: Context): List<InstalledApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        pm.getInstalledPackages(0).mapNotNull { pi ->
            val ai = pi.applicationInfo ?: return@mapNotNull null
            InstalledApp(
                packageName = pi.packageName,
                label = runCatching { pm.getApplicationLabel(ai).toString() }.getOrDefault(pi.packageName),
                versionName = pi.versionName,
                system = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            )
        }.sortedWith(compareBy({ it.system }, { it.label.lowercase(Locale.ROOT) }))
    }

    suspend fun analyzeInstalled(context: Context, packageName: String): ApkReport =
        withContext(Dispatchers.IO) {
            cleanupCache()
            val pm = context.packageManager
            val ai = pm.getApplicationInfo(packageName, 0)
            val baseApk = File(ai.sourceDir)
            val splitDirs = ai.splitSourceDirs?.map { File(it) } ?: emptyList()
            analyzeApkFile(
                context, baseApk, packageName, "Installed",
                if (splitDirs.isEmpty()) "APK (installed)" else "APK + splits (installed)",
                splitDirs, baseApk.length() + splitDirs.sumOf { it.length() }
            )
        }

    suspend fun analyzeFile(context: Context, uri: Uri): ApkReport = withContext(Dispatchers.IO) {
        cleanupCache()
        val (displayName, declaredSize) = queryName(context, uri)
        val cached = copyToCache(context, uri, displayName).also { cacheFiles.add(it) }
        when (detectArchiveType(displayName, cached)) {
            ArchiveKind.APK -> analyzeApkFile(
                context, cached, displayName, "File", "APK",
                emptyList(), if (declaredSize > 0) declaredSize else cached.length()
            )
            ArchiveKind.AAB -> analyzeAab(cached, displayName, declaredSize)
            ArchiveKind.BUNDLE -> analyzeBundle(context, cached, displayName, declaredSize)
        }
    }

    /** Lists a working archive's entries (already captured in the report too). */
    suspend fun listEntries(workingFile: String): List<EntryNode> = withContext(Dispatchers.IO) {
        runCatching {
            ZipFile(File(workingFile)).use { zf ->
                zf.entries().asSequence()
                    .filter { !it.isDirectory }
                    .map { EntryNode(it.name, if (it.size >= 0) it.size else 0L) }
                    .toList()
            }
        }.getOrDefault(emptyList())
    }

    /** Reads and, where possible, decodes a single entry for the file viewer. */
    suspend fun readEntry(workingFile: String, path: String): EntryContent = withContext(Dispatchers.IO) {
        runCatching {
            ZipFile(File(workingFile)).use { zf ->
                val entry = zf.getEntry(path)
                    ?: return@withContext EntryContent(path, ContentKind.EMPTY, null, 0, "Entry not found")
                val size = if (entry.size >= 0) entry.size else 0L
                if (size == 0L) return@withContext EntryContent(path, ContentKind.EMPTY, "", 0)
                val cap = 512 * 1024
                val bytes = zf.getInputStream(entry).use { readUpTo(it, cap + 1) }
                val truncated = bytes.size > cap
                val body = if (truncated) bytes.copyOf(cap) else bytes
                classifyContent(path, body, size, truncated)
            }
        }.getOrElse { EntryContent(path, ContentKind.BINARY, null, 0, "Read error: ${it.message}") }
    }

    /** Unzips the whole working archive into a user-picked SAF folder. Returns a summary. */
    suspend fun extractToTree(context: Context, workingFile: String, treeUri: Uri, folderName: String): String =
        withContext(Dispatchers.IO) {
            val root = DocumentFile.fromTreeUri(context, treeUri)
                ?: throw IllegalStateException("Cannot open the selected folder")
            val safeName = folderName.substringBeforeLast('.').ifBlank { "extracted" }
                .replace(Regex("[^A-Za-z0-9._-]"), "_")
            root.findFile(safeName)?.delete()
            val dest = root.createDirectory(safeName)
                ?: throw IllegalStateException("Cannot create folder in the chosen location")
            val dirs = HashMap<String, DocumentFile>()
            dirs[""] = dest
            var files = 0
            ZipInputStream(File(workingFile).inputStream().buffered()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val parent = ensureDir(dest, dirs, entry.name.substringBeforeLast('/', ""))
                        val fileName = entry.name.substringAfterLast('/')
                        val mime = mimeFor(fileName)
                        parent.createFile(mime, fileName)?.let { doc ->
                            context.contentResolver.openOutputStream(doc.uri)?.use { out ->
                                zis.copyTo(out, 256 * 1024)
                            }
                            files++
                        }
                    }
                    entry = zis.nextEntry
                }
            }
            "Extracted $files files to “$safeName” in the selected folder."
        }

    // ---- archive kinds ----------------------------------------------------

    private enum class ArchiveKind { APK, AAB, BUNDLE }

    private fun detectArchiveType(name: String, file: File): ArchiveKind {
        val lower = name.lowercase(Locale.ROOT)
        when {
            lower.endsWith(".aab") -> return ArchiveKind.AAB
            lower.endsWith(".xapk") || lower.endsWith(".apks") || lower.endsWith(".apkm") -> return ArchiveKind.BUNDLE
            lower.endsWith(".apk") -> return ArchiveKind.APK
        }
        val names = entryNames(file)
        return when {
            names.any { it == "BundleConfig.pb" || it.startsWith("base/manifest/") } -> ArchiveKind.AAB
            names.count { it.endsWith(".apk") } > 0 -> ArchiveKind.BUNDLE
            else -> ArchiveKind.APK
        }
    }

    private fun analyzeBundle(context: Context, file: File, displayName: String, declaredSize: Long): ApkReport {
        val label = when {
            displayName.endsWith(".xapk", true) -> "XAPK bundle"
            displayName.endsWith(".apkm", true) -> "APKM bundle"
            else -> "APKS bundle"
        }
        val apkEntries = entryNames(file).filter { it.endsWith(".apk", true) }
        val baseEntry = apkEntries.firstOrNull { it.contains("base", true) && !it.contains("config", true) }
            ?: apkEntries.firstOrNull { it.contains("base", true) }
            ?: apkEntries.minByOrNull { it.length }
            ?: return emptyReport(displayName, "File", label, null, declaredSize.coerceAtLeast(file.length()),
                listOf("No .apk found inside the bundle."))

        val baseApk = File(context.cacheDir, "base_${System.currentTimeMillis()}.apk").also { cacheFiles.add(it) }
        extractEntry(file, baseEntry, baseApk)
        val report = analyzeApkFile(
            context, baseApk, displayName, "File", label, emptyList(),
            declaredSize.coerceAtLeast(file.length())
        )
        val configAbis = apkEntries.filter { it != baseEntry }.mapNotNull {
            Regex("config\\.([a-z0-9_]+)").find(it.lowercase(Locale.ROOT))?.groupValues?.get(1)
        }.distinct()
        val extra = buildList {
            add("Bundle contains ${apkEntries.size} APK split(s); analysis is of the base APK ($baseEntry).")
            if (configAbis.isNotEmpty()) add("Config splits: ${configAbis.joinToString(", ")}")
        }
        return report.copy(notes = report.notes + extra)
    }

    private fun analyzeAab(file: File, displayName: String, declaredSize: Long): ApkReport {
        val scan = scanArchive(file, listOf("base/dex/", "classes"))
        return baseReport(
            source = displayName, origin = "File", archiveType = "AAB (App Bundle)",
            workingFile = file.absolutePath, size = declaredSize.coerceAtLeast(file.length()),
            scan = scan, pkg = null, ai = null, cert = null, manifestXml = null,
            notes = listOf(
                "AAB manifest is protobuf-encoded; package/version/permissions can't be read on-device.",
                "Framework, native libs, DEX metrics, SDKs and secrets below are parsed from the bundle."
            )
        )
    }

    private fun analyzeApkFile(
        context: Context, file: File, displayName: String, origin: String,
        archiveType: String, extraApks: List<File>, totalSize: Long
    ): ApkReport {
        val scan = scanArchive(file, listOf("classes"))
        extraApks.forEach { split ->
            runCatching {
                ZipFile(split).use { zf -> zf.entries().asSequence().forEach { collectAbi(it.name, scan.abis) } }
            }
        }

        val pm = context.packageManager
        var pkg: PackageInfo? = null
        val flags = PackageManager.GET_PERMISSIONS or PackageManager.GET_ACTIVITIES or
            PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS or
            PackageManager.GET_PROVIDERS or PackageManager.GET_CONFIGURATIONS or signingFlag()
        runCatching { pkg = pm.getPackageArchiveInfo(file.absolutePath, flags) }
        val ai = pkg?.applicationInfo?.apply { sourceDir = file.absolutePath; publicSourceDir = file.absolutePath }
        val cert = pkg?.let { readCertificate(it) }
        val manifestXml = decodeManifest(file)

        return baseReport(
            source = displayName, origin = origin, archiveType = archiveType,
            workingFile = file.absolutePath, size = totalSize,
            scan = scan, pkg = pkg, ai = ai, cert = cert, manifestXml = manifestXml,
            label = ai?.let { runCatching { pm.getApplicationLabel(it).toString() }.getOrNull() },
            notes = buildList {
                if (pkg == null) add("Could not parse the APK manifest (may be a split without one).")
                if (scan.truncated) add("Code scan was capped for speed; some strings may be omitted.")
            }
        )
    }

    // ---- report assembly --------------------------------------------------

    private fun baseReport(
        source: String, origin: String, archiveType: String, workingFile: String?, size: Long,
        scan: Scan, pkg: PackageInfo?, ai: ApplicationInfo?, cert: CertDetail?, manifestXml: String?,
        label: String? = null, notes: List<String>
    ): ApkReport = ApkReport(
        source = source, origin = origin, archiveType = archiveType, workingFile = workingFile, sizeBytes = size,
        packageName = pkg?.packageName, appLabel = label, versionName = pkg?.versionName,
        versionCode = pkg?.let { versionCodeOf(it) },
        minSdk = ai?.let { if (Build.VERSION.SDK_INT >= 24) it.minSdkVersion else null },
        targetSdk = ai?.targetSdkVersion,
        compileSdk = ai?.let { if (Build.VERSION.SDK_INT >= 31) it.compileSdkVersion else null },
        installLocation = installLocationOf(pkg),
        permissions = pkg?.requestedPermissions?.toList().orEmpty().sorted(),
        features = pkg?.reqFeatures?.mapNotNull { it.name }?.sorted().orEmpty(),
        activities = pkg?.activities?.map { ComponentInfo(it.name, it.exported) }.orEmpty(),
        services = pkg?.services?.map { ComponentInfo(it.name, it.exported) }.orEmpty(),
        receivers = pkg?.receivers?.map { ComponentInfo(it.name, it.exported) }.orEmpty(),
        providers = pkg?.providers?.map { ComponentInfo(it.name, it.exported) }.orEmpty(),
        abis = scan.abis.toList().sorted(),
        nativeLibs = scan.nativeLibs.sortedByDescending { it.bytes }.take(80),
        languages = deriveLanguages(scan),
        frameworks = scan.frameworks(),
        sdks = scan.sdkFindings(),
        cert = cert,
        v1Signed = scan.v1Signed,
        debuggable = ai?.let { (it.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0 },
        dexCount = scan.dexCount, classCount = scan.classCount,
        methodCount = scan.methodCount, stringCount = scan.stringCount,
        obfuscationScore = scan.obfuscationScore(), obfuscationLabel = scan.obfuscationLabel(),
        urls = scan.urls.take(maxUrls), ips = scan.ips.take(maxList), emails = scan.emails.take(maxList),
        secrets = scan.secrets.take(maxList), topDirs = scan.topDirs(), fileTypes = scan.fileTypes(),
        entries = scan.entries, entryCount = scan.entryCount, manifestXml = manifestXml, notes = notes
    )

    // ---- scanning ---------------------------------------------------------

    private class Scan {
        var entryCount = 0
        var dexCount = 0
        var classCount = 0L
        var methodCount = 0L
        var stringCount = 0L
        val abis = linkedSetOf<String>()
        val nativeLibs = ArrayList<NativeLib>()
        val dirBytes = HashMap<String, Long>()
        val dirCount = HashMap<String, Int>()
        val extBytes = HashMap<String, Long>()
        val extCount = HashMap<String, Int>()
        val entries = ArrayList<EntryNode>()
        val urls = LinkedHashSet<String>()
        val ips = LinkedHashSet<String>()
        val emails = LinkedHashSet<String>()
        val secrets = LinkedHashSet<SecretFinding>()
        val sdks = linkedSetOf<String>()
        var v1Signed = false
        var truncated = false

        var flutter = false; var reactNative = false; var unity = false
        var xamarin = false; var cordova = false; var capacitor = false
        var nativeScript = false; var qt = false; var kotlin = false
        var compose = false; var nativeCode = false; var maui = false

        var descriptorTotal = 0L
        var descriptorShort = 0L

        fun frameworks(): List<TechFinding> = buildList {
            if (flutter) add(TechFinding("Flutter (Dart)", "libflutter.so / flutter_assets"))
            if (reactNative) add(TechFinding("React Native (JS)", "index.android.bundle / libreactnative*.so"))
            if (unity) add(TechFinding("Unity (C#)", "libunity.so / libil2cpp.so"))
            if (maui) add(TechFinding(".NET MAUI (C#)", "Microsoft.Maui assemblies"))
            if (xamarin && !maui) add(TechFinding("Xamarin/.NET (C#)", "libmonodroid.so / assemblies"))
            if (capacitor) add(TechFinding("Capacitor (JS/TS)", "assets/public + capacitor.config"))
            if (cordova && !capacitor) add(TechFinding("Cordova/PhoneGap (JS)", "assets/www/cordova.js"))
            if (nativeScript) add(TechFinding("NativeScript (JS/TS)", "libNativeScript.so"))
            if (qt) add(TechFinding("Qt (C++)", "libQt*.so"))
            if (compose) add(TechFinding("Jetpack Compose", "androidx.compose in DEX"))
            if (kotlin) add(TechFinding("Kotlin", ".kotlin_module / kotlin metadata"))
            if (nativeCode) add(TechFinding("Native C/C++", "lib/*.so"))
            if (isEmpty() && dexCount > 0) add(TechFinding("Android (Java/Kotlin)", "classes.dex"))
        }

        fun sdkFindings(): List<TechFinding> = sdks.map { TechFinding(it, "found in DEX") }

        fun obfuscationScore(): Int {
            if (descriptorTotal < 200) return 0
            return ((descriptorShort.toDouble() / descriptorTotal) * 100).toInt().coerceIn(0, 100)
        }

        fun obfuscationLabel(): String = when {
            descriptorTotal < 200 -> "Unknown (too little code scanned)"
            obfuscationScore() >= 55 -> "Heavily obfuscated (R8/ProGuard-style)"
            obfuscationScore() >= 25 -> "Partially obfuscated"
            else -> "Minimal / not obfuscated"
        }

        fun topDirs(): List<DirSize> = dirBytes.entries
            .map { DirSize(it.key, it.value, dirCount[it.key] ?: 0) }
            .sortedByDescending { it.bytes }.take(16)

        fun fileTypes(): List<FileTypeCount> = extCount.entries
            .map { FileTypeCount(it.key, it.value, extBytes[it.key] ?: 0L) }
            .sortedByDescending { it.count }.take(20)
    }

    private fun scanArchive(file: File, dexPrefixes: List<String>): Scan {
        val scan = Scan()
        var scannedBytes = 0L
        ZipFile(file).use { zf ->
            val entries = zf.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.isDirectory) continue
                scan.entryCount++
                val name = entry.name
                val size = if (entry.size >= 0) entry.size else 0L
                if (scan.entries.size < maxEntries) scan.entries.add(EntryNode(name, size))
                accumulateDir(name, size, scan)
                accumulateExt(name, size, scan)
                detectFrameworkByName(name, scan)
                collectNativeLib(name, size, scan)
                if (name.startsWith("META-INF/") && (name.endsWith(".RSA") || name.endsWith(".DSA") || name.endsWith(".EC")))
                    scan.v1Signed = true
                val isDex = dexPrefixes.any { name.substringAfterLast('/').startsWith(it) || name.startsWith(it) } &&
                    name.endsWith(".dex")
                if (isDex) {
                    scan.dexCount++
                    readDexHeader(zf, entry, scan)
                    if (scannedBytes < maxTotalScanBytes) scannedBytes += scanDexStrings(zf, entry, scan)
                    else scan.truncated = true
                }
            }
        }
        return scan
    }

    private fun accumulateDir(name: String, size: Long, scan: Scan) {
        val top = if (name.contains('/')) name.substringBefore('/') + "/" else "(root)"
        scan.dirBytes[top] = (scan.dirBytes[top] ?: 0L) + size
        scan.dirCount[top] = (scan.dirCount[top] ?: 0) + 1
    }

    private fun accumulateExt(name: String, size: Long, scan: Scan) {
        val file = name.substringAfterLast('/')
        val ext = if (file.contains('.')) "." + file.substringAfterLast('.').lowercase(Locale.ROOT) else "(no ext)"
        scan.extBytes[ext] = (scan.extBytes[ext] ?: 0L) + size
        scan.extCount[ext] = (scan.extCount[ext] ?: 0) + 1
    }

    private fun collectAbi(name: String, abis: MutableSet<String>) {
        Regex("(?:^|/)lib/([^/]+)/").find(name)?.let { abis.add(it.groupValues[1]) }
    }

    private fun collectNativeLib(name: String, size: Long, scan: Scan) {
        val m = Regex("(?:^|/)lib/([^/]+)/(.+\\.so)$").find(name) ?: return
        scan.abis.add(m.groupValues[1])
        scan.nativeLibs.add(NativeLib(m.groupValues[2], m.groupValues[1], size))
    }

    private fun detectFrameworkByName(name: String, scan: Scan) {
        val n = name.lowercase(Locale.ROOT)
        val so = n.substringAfterLast('/')
        if (so == "libflutter.so" || n.contains("flutter_assets/") || so == "libapp.so") scan.flutter = true
        if (so == "libreactnativejni.so" || so == "libhermes.so" || so == "libjsc.so" || n.endsWith("index.android.bundle")) scan.reactNative = true
        if (so == "libunity.so" || so == "libil2cpp.so" || n.contains("assets/bin/data/")) scan.unity = true
        if (so == "libmonodroid.so" || so.startsWith("libmono") || so == "libxamarin-app.so" || n.contains("assemblies/")) scan.xamarin = true
        if (n.contains("microsoft.maui")) scan.maui = true
        if (n.endsWith("cordova.js") || n.contains("assets/www/")) scan.cordova = true
        if (n.contains("capacitor.config") || n.contains("assets/public/")) scan.capacitor = true
        if (so == "libnativescript.so" || n.contains("assets/app/package.json")) scan.nativeScript = true
        if (so.startsWith("libqt")) scan.qt = true
        if (n.endsWith(".kotlin_module") || n.contains("kotlin/kotlin.kotlin_builtins")) scan.kotlin = true
        if (n.endsWith(".so")) scan.nativeCode = true
    }

    private fun readDexHeader(zf: ZipFile, entry: ZipEntry, scan: Scan) {
        runCatching {
            zf.getInputStream(entry).use { input ->
                val header = ByteArray(112)
                var read = 0
                while (read < 112) {
                    val r = input.read(header, read, 112 - read)
                    if (r < 0) break
                    read += r
                }
                if (read >= 112) {
                    scan.stringCount += u4(header, 56)
                    scan.methodCount += u4(header, 88)
                    scan.classCount += u4(header, 96)
                }
            }
        }
    }

    private fun scanDexStrings(zf: ZipFile, entry: ZipEntry, scan: Scan): Long {
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
                            if (token.length < 4096) token.append(b.toChar())
                        } else {
                            if (token.length >= 4) processToken(token.toString(), scan)
                            token.setLength(0)
                        }
                    }
                    if (consumed >= maxScanBytesPerDex) { scan.truncated = true; break }
                }
                if (token.length >= 4) processToken(token.toString(), scan)
            }
        }
        return consumed
    }

    private fun processToken(token: String, scan: Scan) {
        if (token.startsWith("Lkotlin/")) scan.kotlin = true
        if (token.startsWith("Landroidx/compose")) scan.compose = true
        if (token.startsWith("Lcom/facebook/react")) scan.reactNative = true
        if (token.startsWith("Lio/flutter/")) scan.flutter = true
        if (token.startsWith("Lcom/unity3d/")) scan.unity = true

        if (token.startsWith("L") && scan.sdks.size < 60) detectSdk(token, scan)

        if (token.startsWith("L") && token.endsWith(";") && token.length < 160 && token.contains('/')) {
            scan.descriptorTotal++
            if (token.dropLast(1).substringAfterLast('/').length <= 2) scan.descriptorShort++
        }

        if (token.contains("://") && scan.urls.size < maxUrls)
            URL_RE.findAll(token).forEach { if (scan.urls.size < maxUrls) scan.urls.add(it.value) }
        if (token.contains('@') && scan.emails.size < maxList)
            EMAIL_RE.findAll(token).forEach { if (scan.emails.size < maxList) scan.emails.add(it.value) }
        if (token.length in 7..45 && token[0].isDigit() && token.contains('.') && scan.ips.size < maxList)
            IP_RE.findAll(token).forEach { if (scan.ips.size < maxList) scan.ips.add(it.value) }
        if (scan.secrets.size < maxList) matchSecret(token, scan)
    }

    private fun detectSdk(token: String, scan: Scan) {
        for ((prefix, label) in SDK_MARKERS) {
            if (token.startsWith(prefix)) { scan.sdks.add(label); return }
        }
    }

    private fun matchSecret(token: String, scan: Scan) {
        fun add(type: String, value: String) {
            if (scan.secrets.size < maxList) scan.secrets.add(SecretFinding(type, value.take(140)))
        }
        if (token.startsWith("AIza")) GOOGLE_KEY_RE.find(token)?.let { add("Google API key", it.value) }
        if (token.startsWith("AKIA")) AWS_KEY_RE.find(token)?.let { add("AWS access key", it.value) }
        if (token.startsWith("xox")) SLACK_RE.find(token)?.let { add("Slack token", it.value) }
        if (token.startsWith("ghp_") || token.startsWith("gho_")) GITHUB_RE.find(token)?.let { add("GitHub token", it.value) }
        if (token.startsWith("sk_") || token.startsWith("pk_")) STRIPE_RE.find(token)?.let { add("Stripe key", it.value) }
        if (token.startsWith("eyJ")) JWT_RE.find(token)?.let { add("JWT", it.value) }
        if (token.contains("firebaseio.com")) FIREBASE_RE.find(token)?.let { add("Firebase DB", it.value) }
        if (token.contains("-----BEGIN") && token.contains("PRIVATE")) add("Private key block", token)
        if (token.contains("s3.amazonaws.com") || token.contains(".s3.")) add("S3 bucket URL", token.take(140))
    }

    private fun deriveLanguages(scan: Scan): List<String> {
        val langs = LinkedHashSet<String>()
        if (scan.flutter) langs.add("Dart")
        if (scan.reactNative || scan.cordova || scan.capacitor || scan.nativeScript) langs.add("JavaScript/TypeScript")
        if (scan.unity || scan.xamarin || scan.maui) langs.add("C#")
        if (scan.qt) langs.add("C++")
        if (scan.kotlin) langs.add("Kotlin")
        if (scan.dexCount > 0 && !scan.flutter && !scan.reactNative && !scan.unity && !scan.xamarin)
            langs.add("Java/Kotlin (Android)")
        if (scan.nativeCode) langs.add("C/C++ (native libs)")
        if (langs.isEmpty() && scan.dexCount > 0) langs.add("Java/Kotlin (Android)")
        return langs.toList()
    }

    // ---- content classification (file viewer) -----------------------------

    private fun classifyContent(path: String, bytes: ByteArray, fullSize: Long, truncated: Boolean): EntryContent {
        val lower = path.lowercase(Locale.ROOT)
        val fileName = lower.substringAfterLast('/')
        if (AxmlDecoder.isAxml(bytes)) {
            val xml = AxmlDecoder.decode(bytes)
            if (xml != null) return EntryContent(path, ContentKind.XML, xml, fullSize,
                if (truncated) "Binary XML decoded (preview truncated)" else "Binary XML decoded")
        }
        if (fileName.endsWith(".dex")) return EntryContent(path, ContentKind.DEX, null, fullSize, "Dalvik bytecode — use jadx for source")
        if (IMAGE_EXT.any { lower.endsWith(it) }) return EntryContent(path, ContentKind.IMAGE, null, fullSize, "Image asset")
        val printable = bytes.count { it.toInt() in 0x09..0x0D || it.toInt() in 0x20..0x7E }
        val ratio = if (bytes.isEmpty()) 0.0 else printable.toDouble() / bytes.size
        return if (ratio > 0.85 || TEXT_EXT.any { lower.endsWith(it) }) {
            val text = String(bytes, Charsets.UTF_8)
            EntryContent(path, ContentKind.TEXT, text, fullSize, if (truncated) "Preview truncated to 512 KB" else null)
        } else {
            EntryContent(path, ContentKind.BINARY, hexDump(bytes.copyOf(minOf(bytes.size, 2048))), fullSize,
                "Binary file — hex preview of first ${minOf(bytes.size, 2048)} bytes")
        }
    }

    private fun hexDump(bytes: ByteArray): String {
        val sb = StringBuilder()
        var i = 0
        while (i < bytes.size) {
            val end = minOf(i + 16, bytes.size)
            sb.append("%08x  ".format(i))
            for (j in i until i + 16) {
                if (j < end) sb.append("%02x ".format(bytes[j])) else sb.append("   ")
            }
            sb.append(" |")
            for (j in i until end) {
                val c = bytes[j].toInt()
                sb.append(if (c in 0x20..0x7E) c.toChar() else '.')
            }
            sb.append("|\n")
            i += 16
        }
        return sb.toString()
    }

    // ---- manifest & signing ------------------------------------------------

    private fun decodeManifest(file: File): String? = runCatching {
        ZipFile(file).use { zf ->
            val entry = zf.getEntry("AndroidManifest.xml") ?: return null
            val bytes = zf.getInputStream(entry).use { it.readBytes() }
            AxmlDecoder.decode(bytes)
        }
    }.getOrNull()

    private fun signingFlag(): Int =
        if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES
        else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES

    private fun readCertificate(pkg: PackageInfo): CertDetail? {
        val certBytes: ByteArray = runCatching {
            if (Build.VERSION.SDK_INT >= 28) {
                val si = pkg.signingInfo ?: return null
                val signers = if (si.hasMultipleSigners()) si.apkContentsSigners else si.signingCertificateHistory
                signers?.firstOrNull()?.toByteArray()
            } else {
                @Suppress("DEPRECATION") pkg.signatures?.firstOrNull()?.toByteArray()
            }
        }.getOrNull() ?: return null

        return runCatching {
            val x509 = CertificateFactory.getInstance("X.509")
                .generateCertificate(certBytes.inputStream()) as X509Certificate
            val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT)
            val pk = x509.publicKey
            val keyInfo = "${pk.algorithm}" + keyBits(pk)?.let { " · $it-bit" }.orEmpty()
            CertDetail(
                subject = x509.subjectDN.name,
                issuer = x509.issuerDN.name,
                serial = x509.serialNumber.toString(16),
                algorithm = x509.sigAlgName,
                publicKey = keyInfo,
                validFrom = fmt.format(x509.notBefore),
                validUntil = fmt.format(x509.notAfter),
                sha256 = digestHex("SHA-256", x509.encoded),
                sha1 = digestHex("SHA-1", x509.encoded)
            )
        }.getOrNull()
    }

    private fun keyBits(pk: java.security.PublicKey): Int? = when (pk) {
        is java.security.interfaces.RSAPublicKey -> pk.modulus.bitLength()
        is java.security.interfaces.ECPublicKey -> pk.params.order.bitLength()
        else -> null
    }

    private fun installLocationOf(pkg: PackageInfo?): String? {
        if (pkg == null || Build.VERSION.SDK_INT < 21) return null
        return when (pkg.installLocation) {
            0 -> "internalOnly"
            1 -> "auto"
            2 -> "preferExternal"
            else -> null
        }
    }

    // ---- io helpers --------------------------------------------------------

    private fun cleanupCache() {
        cacheFiles.forEach { runCatching { it.delete() } }
        cacheFiles.clear()
    }

    private fun emptyReport(name: String, origin: String, type: String, working: String?, size: Long, notes: List<String>) =
        ApkReport(
            source = name, origin = origin, archiveType = type, workingFile = working, sizeBytes = size,
            packageName = null, appLabel = null, versionName = null, versionCode = null,
            minSdk = null, targetSdk = null, compileSdk = null, installLocation = null,
            permissions = emptyList(), features = emptyList(),
            activities = emptyList(), services = emptyList(), receivers = emptyList(), providers = emptyList(),
            abis = emptyList(), nativeLibs = emptyList(), languages = emptyList(),
            frameworks = emptyList(), sdks = emptyList(), cert = null, v1Signed = false, debuggable = null,
            dexCount = 0, classCount = 0, methodCount = 0, stringCount = 0,
            obfuscationScore = 0, obfuscationLabel = "Unknown",
            urls = emptyList(), ips = emptyList(), emails = emptyList(), secrets = emptyList(),
            topDirs = emptyList(), fileTypes = emptyList(), entries = emptyList(), entryCount = 0,
            manifestXml = null, notes = notes
        )

    private fun queryName(context: Context, uri: Uri): Pair<String, Long> {
        var name = "package.apk"
        var size = -1L
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = c.getColumnIndex(OpenableColumns.SIZE)
                if (c.moveToFirst()) {
                    if (nameIdx >= 0) c.getString(nameIdx)?.let { name = it }
                    if (sizeIdx >= 0 && !c.isNull(sizeIdx)) size = c.getLong(sizeIdx)
                }
            }
        }
        return name to size
    }

    private fun copyToCache(context: Context, uri: Uri, displayName: String): File {
        val ext = displayName.substringAfterLast('.', "bin")
        val out = File(context.cacheDir, "inspect_${System.currentTimeMillis()}.$ext")
        context.contentResolver.openInputStream(uri)?.use { input ->
            out.outputStream().use { input.copyTo(it, 256 * 1024) }
        } ?: throw IllegalStateException("Cannot open the selected file")
        return out
    }

    private fun readUpTo(input: java.io.InputStream, limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream(minOf(limit, 64 * 1024))
        val buf = ByteArray(32 * 1024)
        var total = 0
        while (total < limit) {
            val r = input.read(buf, 0, minOf(buf.size, limit - total))
            if (r < 0) break
            out.write(buf, 0, r)
            total += r
        }
        return out.toByteArray()
    }

    private fun entryNames(file: File): List<String> =
        runCatching { ZipFile(file).use { zf -> zf.entries().asSequence().map { it.name }.toList() } }
            .getOrDefault(emptyList())

    private fun extractEntry(archive: File, entryName: String, dest: File) {
        ZipFile(archive).use { zf ->
            val entry = zf.getEntry(entryName) ?: return
            zf.getInputStream(entry).use { input -> dest.outputStream().use { input.copyTo(it, 256 * 1024) } }
        }
    }

    private fun ensureDir(root: DocumentFile, cache: HashMap<String, DocumentFile>, path: String): DocumentFile {
        if (path.isEmpty()) return cache[""]!!
        cache[path]?.let { return it }
        val parentPath = path.substringBeforeLast('/', "")
        val parent = ensureDir(root, cache, parentPath)
        val segment = path.substringAfterLast('/')
        val dir = parent.findFile(segment)?.takeIf { it.isDirectory } ?: parent.createDirectory(segment)!!
        cache[path] = dir
        return dir
    }

    private fun mimeFor(name: String): String = when (name.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
        "xml", "txt", "json", "js", "html", "css", "properties", "md", "smali" -> "text/plain"
        "png" -> "image/png"; "jpg", "jpeg" -> "image/jpeg"; "so" -> "application/octet-stream"
        else -> "application/octet-stream"
    }

    private fun versionCodeOf(pkg: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= 28) pkg.longVersionCode else @Suppress("DEPRECATION") pkg.versionCode.toLong()

    private fun u4(b: ByteArray, off: Int): Long =
        (b[off].toLong() and 0xFF) or ((b[off + 1].toLong() and 0xFF) shl 8) or
            ((b[off + 2].toLong() and 0xFF) shl 16) or ((b[off + 3].toLong() and 0xFF) shl 24)

    private fun digestHex(algo: String, data: ByteArray): String =
        MessageDigest.getInstance(algo).digest(data).joinToString("") { "%02x".format(it) }

    companion object {
        private val URL_RE = Regex("""https?://[^\s"'<>()\\]+""")
        private val EMAIL_RE = Regex("""[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}""")
        private val IP_RE = Regex("""\b(?:(?:25[0-5]|2[0-4]\d|1?\d?\d)\.){3}(?:25[0-5]|2[0-4]\d|1?\d?\d)\b""")
        private val GOOGLE_KEY_RE = Regex("""AIza[0-9A-Za-z\-_]{35}""")
        private val AWS_KEY_RE = Regex("""AKIA[0-9A-Z]{16}""")
        private val SLACK_RE = Regex("""xox[baprs]-[0-9A-Za-z-]{10,}""")
        private val GITHUB_RE = Regex("""gh[po]_[0-9A-Za-z]{36}""")
        private val STRIPE_RE = Regex("""(?:sk|pk)_(?:live|test)_[0-9A-Za-z]{16,}""")
        private val JWT_RE = Regex("""eyJ[A-Za-z0-9_-]{6,}\.eyJ[A-Za-z0-9_-]{6,}\.[A-Za-z0-9_-]{6,}""")
        private val FIREBASE_RE = Regex("""[a-z0-9-]+\.firebaseio\.com""")

        private val TEXT_EXT = listOf(
            ".txt", ".xml", ".json", ".js", ".ts", ".html", ".htm", ".css", ".properties", ".md",
            ".smali", ".java", ".kt", ".dart", ".yml", ".yaml", ".csv", ".sql", ".ini", ".cfg",
            ".gradle", ".pro", ".version", ".map", ".pem", ".mf", ".sf"
        )
        private val IMAGE_EXT = listOf(".png", ".jpg", ".jpeg", ".gif", ".webp", ".bmp", ".ico")

        private val SDK_MARKERS = listOf(
            "Lcom/google/firebase/crashlytics" to "Firebase Crashlytics",
            "Lcom/google/firebase/messaging" to "Firebase Messaging (FCM)",
            "Lcom/google/firebase" to "Firebase",
            "Lcom/google/android/gms/ads" to "Google AdMob",
            "Lcom/google/android/gms/maps" to "Google Maps",
            "Lcom/google/android/gms" to "Google Play Services",
            "Lcom/facebook/appevents" to "Facebook Analytics",
            "Lcom/facebook/" to "Facebook SDK",
            "Lcom/appsflyer" to "AppsFlyer",
            "Lcom/adjust/sdk" to "Adjust",
            "Lcom/onesignal" to "OneSignal",
            "Lio/branch" to "Branch",
            "Lcom/flurry" to "Flurry",
            "Lio/sentry" to "Sentry",
            "Lcom/bugsnag" to "Bugsnag",
            "Lcom/amplitude" to "Amplitude",
            "Lcom/mixpanel" to "Mixpanel",
            "Lcom/squareup/okhttp" to "OkHttp",
            "Lokhttp3/" to "OkHttp",
            "Lretrofit2/" to "Retrofit",
            "Lcom/google/gson" to "Gson",
            "Lcom/squareup/moshi" to "Moshi",
            "Lcom/bumptech/glide" to "Glide",
            "Lcom/squareup/picasso" to "Picasso",
            "Ldagger/" to "Dagger/Hilt",
            "Lio/reactivex" to "RxJava",
            "Lkotlinx/coroutines" to "Kotlin Coroutines",
            "Landroidx/room" to "Room",
            "Lio/realm" to "Realm",
            "Lcom/unity3d/ads" to "Unity Ads",
            "Lcom/mopub" to "MoPub",
            "Lcom/ironsource" to "ironSource"
        )
    }
}
