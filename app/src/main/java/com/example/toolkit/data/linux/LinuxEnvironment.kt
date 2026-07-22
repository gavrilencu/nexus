package com.example.toolkit.data.linux

import android.content.Context
import android.os.Build
import android.system.Os
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream

data class LinuxSetupProgress(
    val stage: String,
    val percent: Int,
    val detail: String
)

/**
 * Ubuntu rootfs + real apt, running under PRoot.
 *
 * PRoot and its loader live in nativeLibraryDir (always executable on Android).
 * Guest binaries are NOT executed directly from app storage — this avoids
 * error=13 / exit 126 (Permission denied) on Android 10+.
 */
class LinuxEnvironment(private val context: Context) {
    private val appContext = context.applicationContext
    private val filesRoot = File(appContext.filesDir, "nexus-ubuntu")
    private val rootfs = File(filesRoot, "rootfs")
    private val downloadDir = File(appContext.cacheDir, "nexus-ubuntu-dl")
    private val tmpDir = File(appContext.cacheDir, "nexus-proot-tmp")
    private val marker = File(filesRoot, ".nexus-ubuntu-ready")
    private val client = OkHttpClient.Builder().build()
    private val preparing = AtomicBoolean(false)

    @Volatile
    private var shellProcess: Process? = null

    @Volatile
    private var stdin: java.io.OutputStream? = null

    val isInstalled: Boolean
        get() = marker.isFile &&
            File(rootfs, "bin/bash").isFile &&
            File(rootfs, "usr/bin/apt").isFile

    val architecture: String
        get() = ubuntuArch()

    val prefixPath: String
        get() = rootfs.absolutePath

    suspend fun prepare(onProgress: (LinuxSetupProgress) -> Unit) = withContext(Dispatchers.IO) {
        if (isInstalled) {
            onProgress(LinuxSetupProgress("ready", 100, "Ubuntu + apt ready"))
            return@withContext
        }
        check(preparing.compareAndSet(false, true)) { "Install already in progress" }
        try {
            // Wipe previous Termux / Alpine attempts
            File(appContext.filesDir, "nexus-linux").deleteRecursively()
            File(appContext.filesDir, "nexus-termux").deleteRecursively()
            File(appContext.codeCacheDir, "nexus-termux").deleteRecursively()
            File(appContext.filesDir, ".nexus-termux-version").delete()
            if (filesRoot.exists()) filesRoot.deleteRecursively()
            filesRoot.mkdirs()
            downloadDir.mkdirs()
            tmpDir.mkdirs()

            onProgress(LinuxSetupProgress("runtime", 3, "Checking PRoot runtime…"))
            val proot = requireProot()

            val arch = ubuntuArch()
            val fileName = "ubuntu-base-$UBUNTU_VERSION-base-$arch.tar.gz"
            val baseUrl = "https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release"
            val archive = File(downloadDir, fileName)
            val expected = EXPECTED_SHA256[arch]
                ?: error("Architecture $arch is not supported for Ubuntu base")

            onProgress(LinuxSetupProgress("download", 6, "Downloading Ubuntu (~28 MB)…"))
            downloadFile("$baseUrl/$fileName", archive) { done, total ->
                val fraction = if (total > 0) done.toDouble() / total else 0.0
                val percent = 6 + (fraction * 50).toInt().coerceIn(0, 50)
                onProgress(
                    LinuxSetupProgress(
                        "download",
                        percent,
                        "Download: ${formatBytes(done)} / ${formatBytes(total)}"
                    )
                )
            }

            onProgress(LinuxSetupProgress("verify", 58, "Verifying SHA-256…"))
            val actual = sha256(archive)
            check(actual.equals(expected, ignoreCase = true)) {
                archive.delete()
                "Ubuntu checksum mismatch"
            }

            rootfs.mkdirs()
            onProgress(LinuxSetupProgress("extract", 62, "Extracting Ubuntu rootfs…"))
            extractTarGz(archive, rootfs) { count ->
                val percent = (62 + count / 80).coerceAtMost(88)
                onProgress(LinuxSetupProgress("extract", percent, "Extracted $count files"))
            }

            onProgress(LinuxSetupProgress("setup", 90, "Configuring apt…"))
            configureRootfs()

            onProgress(LinuxSetupProgress("verify", 96, "Testing proot + bash + apt…"))
            smokeTest(proot)

            archive.delete()
            marker.writeText(
                "ubuntu-base\n" +
                    "version=$UBUNTU_VERSION\n" +
                    "arch=$arch\n" +
                    "root=${rootfs.absolutePath}\n" +
                    "installed=${System.currentTimeMillis()}\n"
            )
            onProgress(LinuxSetupProgress("ready", 100, "Ready — use apt install …"))
        } finally {
            preparing.set(false)
        }
    }

    fun startShell(onOutput: (String) -> Unit, onExit: (Int) -> Unit) {
        if (shellProcess?.isAlive == true) return
        check(isInstalled) { "Linux is not installed" }
        val proot = requireProot()

        val process = ProcessBuilder(prootCommand(proot, listOf("/bin/bash", "--noprofile", "--norc", "-i")))
            .redirectErrorStream(true)
            .apply {
                environment().clear()
                environment().putAll(hostEnv(proot))
            }
            .start()

        shellProcess = process
        stdin = process.outputStream

        Thread({
            try {
                val reader = BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8), 8192)
                val buf = CharArray(4096)
                val line = StringBuilder()
                while (true) {
                    val n = reader.read(buf)
                    if (n < 0) break
                    for (i in 0 until n) {
                        when (val c = buf[i]) {
                            '\n' -> {
                                onOutput(line.toString())
                                line.setLength(0)
                            }
                            '\r' -> Unit
                            else -> line.append(c)
                        }
                    }
                    if (line.length > 240) {
                        onOutput(line.toString())
                        line.setLength(0)
                    }
                }
                if (line.isNotEmpty()) onOutput(line.toString())
            } catch (e: Exception) {
                onOutput("[shell] ${e.message ?: "closed"}")
            } finally {
                val code = runCatching { process.waitFor() }.getOrDefault(-1)
                shellProcess = null
                stdin = null
                onExit(code)
            }
        }, "nexus-ubuntu-out").apply {
            isDaemon = true
            start()
        }

        sendRaw(
            """
            export PS1='root@nexus:\w${'$'} '
            export DEBIAN_FRONTEND=noninteractive
            echo ''
            echo 'NEXUS Ubuntu — apt ready'
            uname -a
            apt --version | head -1
            echo 'Examples: apt update · apt install python3 · apt install nodejs · apt install git'
            echo ''
            """.trimIndent()
        )
    }

    fun send(input: String) = sendRaw(input)

    private fun sendRaw(input: String) {
        val out = stdin ?: error("Shell is not running")
        val process = shellProcess ?: error("Shell is not running")
        check(process.isAlive) { "Shell exited" }
        synchronized(out) {
            out.write((input + "\n").toByteArray(Charsets.UTF_8))
            out.flush()
        }
    }

    fun stopShell() {
        runCatching { stdin?.close() }
        stdin = null
        shellProcess?.destroy()
        shellProcess = null
    }

    fun reset() {
        stopShell()
        filesRoot.deleteRecursively()
        downloadDir.deleteRecursively()
        tmpDir.deleteRecursively()
        File(appContext.filesDir, "nexus-linux").deleteRecursively()
        File(appContext.filesDir, "nexus-termux").deleteRecursively()
        File(appContext.codeCacheDir, "nexus-termux").deleteRecursively()
        File(appContext.filesDir, ".nexus-termux-version").delete()
    }

    private fun requireProot(): File {
        val native = File(appContext.applicationInfo.nativeLibraryDir)
        val proot = File(native, "libproot.so")
        val loader = File(native, "libproot-loader.so")
        check(proot.isFile) {
            "PRoot missing (libproot.so). Reinstall the APK."
        }
        check(loader.isFile) {
            "PRoot loader missing (libproot-loader.so). Reinstall the APK."
        }
        return proot
    }

    private fun hostEnv(proot: File): MutableMap<String, String> {
        val native = File(appContext.applicationInfo.nativeLibraryDir)
        tmpDir.mkdirs()
        return mutableMapOf(
            "PROOT_TMP_DIR" to tmpDir.absolutePath,
            "PROOT_LOADER" to File(native, "libproot-loader.so").absolutePath,
            "PROOT_LOADER_32" to File(native, "libproot-loader32.so").absolutePath,
            "PATH" to "/system/bin:/system/xbin",
            "ANDROID_DATA" to "/data",
            "ANDROID_ROOT" to "/system"
        ).also { map ->
            if (!File(native, "libproot-loader32.so").exists()) {
                map.remove("PROOT_LOADER_32")
            }
        }
    }

    private fun guestEnvExports(): String =
        "export HOME=/root USER=root LOGNAME=root; " +
            "export TERM=xterm-256color LANG=C.UTF-8; " +
            "export DEBIAN_FRONTEND=noninteractive TMPDIR=/tmp; " +
            "export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"

    private fun prootCommand(proot: File, guestArgs: List<String>): List<String> {
        // Single bash -c script: set guest env, then run the requested command.
        val guestScript = when {
            guestArgs.size >= 3 && guestArgs[1] == "-c" ->
                guestEnvExports() + "; " + guestArgs[2]
            guestArgs.isNotEmpty() ->
                guestEnvExports() + "; exec " + guestArgs.joinToString(" ") { shellQuote(it) }
            else ->
                guestEnvExports() + "; exec /bin/bash -i"
        }
        return buildList {
            add(proot.absolutePath)
            add("--link2symlink")
            add("-0")
            add("-r"); add(rootfs.absolutePath)
            add("-b"); add("/dev")
            add("-b"); add("/proc")
            add("-b"); add("/sys")
            add("-b"); add("/system")
            add("-b"); add("/apex")
            add("-b"); add("/storage")
            add("-w"); add("/root")
            add("/bin/bash")
            add("-c")
            add(guestScript)
        }
    }

    private fun shellQuote(arg: String): String {
        if (arg.matches(Regex("^[A-Za-z0-9._/=:@+-]+$"))) return arg
        return "'" + arg.replace("'", "'\\''") + "'"
    }

    private fun smokeTest(proot: File) {
        val pb = ProcessBuilder(
            prootCommand(
                proot,
                listOf("/bin/bash", "-c", "echo NEXUS_OK; apt --version | head -1; uname -a")
            )
        ).apply {
            environment().clear()
            environment().putAll(hostEnv(proot))
            redirectErrorStream(true)
        }
        val proc = pb.start()
        val out = proc.inputStream.bufferedReader().readText()
        val code = proc.waitFor()
        check(code == 0 && out.contains("NEXUS_OK")) {
            "Smoke test failed (code=$code): ${out.take(800)}"
        }
        check(out.contains("apt", ignoreCase = true)) {
            "apt not available inside Ubuntu rootfs"
        }
    }

    private fun configureRootfs() {
        File(rootfs, "root").mkdirs()
        File(rootfs, "tmp").mkdirs()
        File(rootfs, "var/tmp").mkdirs()
        File(rootfs, "etc").mkdirs()

        File(rootfs, "etc/resolv.conf").writeText(
            "nameserver 1.1.1.1\nnameserver 8.8.8.8\n"
        )
        File(rootfs, "etc/hosts").writeText(
            "127.0.0.1 localhost nexus\n::1 localhost ip6-localhost\n"
        )
        File(rootfs, "etc/hostname").writeText("nexus\n")

        // Avoid apt interactive prompts / broken machine-id
        File(rootfs, "etc/machine-id").takeIf { !it.exists() }?.writeText("nexus00000000000000000000000001\n")
        File(rootfs, "var/lib/dpkg").mkdirs()
        File(rootfs, "var/cache/apt/archives/partial").mkdirs()
        File(rootfs, "var/lib/apt/lists/partial").mkdirs()

        File(rootfs, "etc/apt/apt.conf.d").mkdirs()
        File(rootfs, "etc/apt/apt.conf.d/99nexus").writeText(
            """
            APT::Get::Assume-Yes "true";
            Dpkg::Use-Pty "0";
            Acquire::Languages "none";
            """.trimIndent() + "\n"
        )

        // Ensure sources.list exists (ubuntu-base usually ships one)
        val sources = File(rootfs, "etc/apt/sources.list")
        if (!sources.exists() || sources.length() < 10) {
            sources.parentFile?.mkdirs()
            sources.writeText(
                """
                deb http://ports.ubuntu.com/ubuntu-ports/ noble main restricted universe multiverse
                deb http://ports.ubuntu.com/ubuntu-ports/ noble-updates main restricted universe multiverse
                deb http://ports.ubuntu.com/ubuntu-ports/ noble-security main restricted universe multiverse
                """.trimIndent() + "\n"
            )
        }
        // amd64 uses archive.ubuntu.com — rewrite if needed
        if (ubuntuArch() == "amd64" && sources.readText().contains("ports.ubuntu.com")) {
            sources.writeText(
                """
                deb http://archive.ubuntu.com/ubuntu/ noble main restricted universe multiverse
                deb http://archive.ubuntu.com/ubuntu/ noble-updates main restricted universe multiverse
                deb http://archive.ubuntu.com/ubuntu/ noble-security main restricted universe multiverse
                """.trimIndent() + "\n"
            )
        }
    }

    private fun extractTarGz(archive: File, destination: File, onEntries: (Int) -> Unit) {
        val pendingLinks = mutableListOf<Pair<File, String>>()
        val pendingModes = mutableListOf<Pair<File, Int>>()
        var count = 0
        FileInputStream(archive).use { fileIn ->
            GzipCompressorInputStream.builder().setInputStream(fileIn).get().use { gzip ->
                TarArchiveInputStream(gzip).use { tar ->
                    var entry = tar.nextEntry
                    while (entry != null) {
                        val target = safeTarget(destination, entry.name)
                        when {
                            entry.isDirectory -> {
                                target.mkdirs()
                                pendingModes += target to entry.mode
                            }
                            entry.isSymbolicLink || entry.isLink -> {
                                target.parentFile?.mkdirs()
                                pendingLinks += target to entry.linkName
                            }
                            entry.isFile -> {
                                target.parentFile?.mkdirs()
                                FileOutputStream(target).use { tar.copyTo(it) }
                                pendingModes += target to entry.mode
                            }
                        }
                        count++
                        if (count % 40 == 0) onEntries(count)
                        entry = tar.nextEntry
                    }
                }
            }
        }
        pendingLinks.forEach { (link, target) ->
            runCatching {
                if (link.exists()) link.delete()
                Os.symlink(target, link.absolutePath)
            }
        }
        // Restore execute bits from the archive (Android FileOutputStream creates 0600/0660).
        pendingModes.forEach { (file, mode) ->
            runCatching { Os.chmod(file.absolutePath, mode and 0xFFF) }
        }
        // Hard guarantee for binaries the smoke test / shell need.
        listOf(
            "bin/bash", "bin/sh", "usr/bin/env", "usr/bin/apt", "usr/bin/dpkg",
            "lib/ld-linux-aarch64.so.1", "lib/ld-linux-armhf.so.3", "lib64/ld-linux-x86-64.so.2"
        ).forEach { rel ->
            val f = File(destination, rel)
            if (f.isFile) runCatching { Os.chmod(f.absolutePath, 0b111_101_101) } // 0755
        }
        onEntries(count)
    }

    private fun safeTarget(parent: File, name: String): File {
        val target = File(parent, name).canonicalFile
        val root = parent.canonicalFile
        check(target.path == root.path || target.path.startsWith(root.path + File.separator)) {
            "Unsafe archive path: $name"
        }
        return target
    }

    private fun downloadFile(url: String, target: File, progress: (Long, Long) -> Unit) {
        val response = client.newCall(Request.Builder().url(url).get().build()).execute()
        response.use {
            check(it.isSuccessful) { "Download failed: HTTP ${it.code}" }
            val body = it.body ?: error("Empty body")
            val total = body.contentLength()
            var done = 0L
            body.byteStream().use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        done += n
                        progress(done, total)
                    }
                }
            }
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun androidAbi(): String {
        val supported = setOf("arm64-v8a", "armeabi-v7a", "x86_64")
        return Build.SUPPORTED_ABIS.firstOrNull { it in supported }
            ?: error("Unsupported ABI. Need arm64 / arm / x86_64.")
    }

    private fun ubuntuArch(): String = when (androidAbi()) {
        "arm64-v8a" -> "arm64"
        "armeabi-v7a" -> "armhf"
        "x86_64" -> "amd64"
        else -> error("Unsupported architecture")
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 0) return "?"
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> String.format("%.1f MB", bytes / 1024.0 / 1024.0)
        }
    }

    /** True while an interactive shell process is alive. */
    val isRunning: Boolean
        get() = shellProcess?.isAlive == true

    /**
     * Client-side Tab completion against the Ubuntu rootfs (no PTY/readline).
     * [cwdGuest] is the guest path (e.g. `/root`), [token] is the path fragment
     * being completed (may be relative or absolute).
     *
     * Returns matching names; directories are suffixed with `/`.
     */
    fun completePath(cwdGuest: String, token: String): List<String> {
        if (!isInstalled) return emptyList()
        val raw = token.trim()
        val abs = when {
            raw.startsWith("/") -> raw
            raw.startsWith("~/") -> "/root/" + raw.removePrefix("~/")
            raw == "~" -> "/root/"
            else -> {
                val base = cwdGuest.trimEnd('/').ifBlank { "/root" }
                "$base/$raw"
            }
        }
        val slash = abs.lastIndexOf('/')
        val dirPart = if (slash >= 0) abs.substring(0, slash).ifBlank { "/" } else "/"
        val prefix = if (slash >= 0) abs.substring(slash + 1) else abs

        val hostDir = guestToHost(dirPart)
        if (!hostDir.isDirectory) return emptyList()
        val matches = hostDir.listFiles()
            ?.asSequence()
            ?.filter { it.name.startsWith(prefix) }
            ?.sortedBy { it.name.lowercase() }
            ?.map { f ->
                val name = f.name + if (f.isDirectory) "/" else ""
                // Rebuild the token as the user typed it (relative vs absolute).
                when {
                    raw.startsWith("/") -> dirPart.trimEnd('/') + "/" + name
                    raw.startsWith("~/") -> {
                        val rest = (dirPart.removePrefix("/root").trimStart('/') + "/" + name).trimStart('/')
                        "~/$rest"
                    }
                    raw == "~" -> "~/"
                    else -> {
                        // Prefer completing only the last segment for relative tokens
                        if (raw.contains('/')) {
                            val parent = raw.substringBeforeLast('/', missingDelimiterValue = "")
                            if (parent.isEmpty()) name else "$parent/$name"
                        } else name
                    }
                }
            }
            ?.toList()
            .orEmpty()
        return matches.take(80)
    }

    private fun guestToHost(guestPath: String): File {
        val clean = guestPath.trim().ifBlank { "/" }
        val relative = clean.trimStart('/').replace('\\', '/')
        return if (relative.isEmpty()) rootfs else File(rootfs, relative)
    }

    companion object {
        private const val UBUNTU_VERSION = "24.04.4"
        private val EXPECTED_SHA256 = mapOf(
            "amd64" to "c1e67ef7b17a6300e136118bd1dc04725009cb376c1aad10abcf8cd453628d58",
            "arm64" to "04207713ece899c3740823d33690441ad3a7f0ded1101aca744e2b0f37ac7ff2",
            "armhf" to "991520b47f6586f38a78505cf016e300b6191bb8ff86a0723481ec23a37ab7f4"
        )
    }
}

/**
 * A single [LinuxEnvironment] shared by the whole process.
 *
 * The terminal ViewModel and [LinuxSessionService] both go through this
 * holder so they talk to the *same* PRoot bash process. That's what makes a
 * background service actually possible: you run `nohup python3 -m
 * http.server 8080 &` from the terminal screen, leave the screen (even
 * close the app to Recents while the background-session toggle is on), and
 * the process — owned by this one shared instance, kept alive by
 * [LinuxSessionService]'s foreground priority — keeps serving requests on
 * 127.0.0.1:8080.
 */
object LinuxEnvironmentHolder {
    @Volatile private var instance: LinuxEnvironment? = null

    fun get(context: Context): LinuxEnvironment =
        instance ?: synchronized(this) {
            instance ?: LinuxEnvironment(context.applicationContext).also { instance = it }
        }
}
