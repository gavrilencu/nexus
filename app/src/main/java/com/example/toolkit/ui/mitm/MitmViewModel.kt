package com.example.toolkit.ui.mitm

import android.app.Application
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.security.KeyChain
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.toolkit.data.mitm.HttpExchange
import com.example.toolkit.data.mitm.MitmCaManager
import com.example.toolkit.data.mitm.MitmCaptureBus
import com.example.toolkit.data.mitm.MitmProxyService
import com.example.toolkit.data.mitm.MitmProxyServer
import com.example.toolkit.data.mitm.MitmStats
import com.example.toolkit.data.mitm.MitmVpnService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class MitmUiState(
    val hostFilter: String = "",
    val expandedId: Long? = null,
    val caReady: Boolean = false,
    val caSubject: String = "",
    val caError: String? = null,
    val statusMessage: String = "Preparing certificate authority…"
)

class MitmViewModel(app: Application) : AndroidViewModel(app) {
    @Volatile private var ca: MitmCaManager? = null

    private val _ui = MutableStateFlow(MitmUiState())
    val ui: StateFlow<MitmUiState> = _ui.asStateFlow()

    val stats: StateFlow<MitmStats> = MitmCaptureBus.stats
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MitmStats())

    val running: StateFlow<Boolean> = MitmCaptureBus.running
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val exchanges: StateFlow<List<HttpExchange>> = combine(
        MitmCaptureBus.exchanges,
        MitmCaptureBus.hostFilter
    ) { list, filter ->
        if (filter.isBlank()) list.reversed()
        else list.filter {
            it.host.contains(filter, true) ||
                it.url.contains(filter, true) ||
                it.path.contains(filter, true)
        }.reversed()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        // Heavy CA crypto must NOT run on the main thread during ViewModel construction —
        // that was crashing the app the moment the MITM screen opened.
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val manager = MitmCaManager(getApplication())
                ca = manager
                _ui.update {
                    it.copy(
                        caReady = true,
                        caSubject = manager.caCertificate.subjectX500Principal.name,
                        caError = null,
                        statusMessage = "Idle — Install CA (from ${manager.organizationName()}), then start Proxy or VPN"
                    )
                }
            } catch (e: Exception) {
                _ui.update {
                    it.copy(
                        caReady = false,
                        caError = e.message ?: "CA init failed",
                        statusMessage = "CA error: ${e.message}"
                    )
                }
            }
        }
    }

    fun onFilter(v: String) {
        _ui.update { it.copy(hostFilter = v) }
        MitmCaptureBus.setHostFilter(v)
    }

    fun toggleExpand(id: Long) {
        _ui.update { it.copy(expandedId = if (it.expandedId == id) null else id) }
    }

    fun clear() = MitmCaptureBus.clear()

    fun prepareVpn(): Intent? = VpnService.prepare(getApplication())

    fun startProxy() {
        if (ca == null) {
            _ui.update { it.copy(statusMessage = "Wait for CA to finish preparing…") }
            return
        }
        val ctx = getApplication<Application>()
        val intent = Intent(ctx, MitmProxyService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(intent)
        else ctx.startService(intent)
        val ip = MitmProxyService.localIpv4() ?: "127.0.0.1"
        _ui.update {
            it.copy(statusMessage = "Proxy on $ip:${MitmProxyServer.DEFAULT_PORT} — set Wi‑Fi proxy to this")
        }
    }

    fun startVpn() {
        if (ca == null) {
            _ui.update { it.copy(statusMessage = "Wait for CA to finish preparing…") }
            return
        }
        if (prepareVpn() != null) return
        val ctx = getApplication<Application>()
        ctx.startService(Intent(ctx, MitmProxyService::class.java).setAction(MitmProxyService.ACTION_STOP))
        val intent = Intent(ctx, MitmVpnService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(intent)
        else ctx.startService(intent)
        _ui.update {
            it.copy(statusMessage = "VPN MITM on — HTTPS needs NEXUS CA installed as user cert")
        }
    }

    fun onVpnPermissionGranted() = startVpn()

    fun stop() {
        val ctx = getApplication<Application>()
        ctx.startService(Intent(ctx, MitmProxyService::class.java).setAction(MitmProxyService.ACTION_STOP))
        ctx.startService(Intent(ctx, MitmVpnService::class.java).setAction(MitmVpnService.ACTION_STOP))
        _ui.update { it.copy(statusMessage = "Stopped") }
    }

    /**
     * Saves CA to Downloads as DER `.crt`, then opens the system certificate installer.
     * Android 11+ often refuses silent CA install from apps and asks you to finish in Settings —
     * the file is already in Downloads so Settings → Install CA can pick `nexus-mitm-ca.crt`.
     */
    fun installCa() {
        val manager = ca
        if (manager == null) {
            _ui.update { it.copy(statusMessage = "Wait for CA to finish preparing…") }
            return
        }
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val saved = withContext(Dispatchers.IO) {
                runCatching { saveCaToDownloads(manager) }.getOrNull()
                    ?: runCatching { saveCaToCache(manager) }.getOrNull()
            }
            if (saved == null) {
                _ui.update { it.copy(statusMessage = "Could not write CA file") }
                return@launch
            }
            _ui.update {
                it.copy(
                    statusMessage = "CA saved as nexus-mitm-ca.crt — pick it under " +
                        "Settings → Security → Encryption & credentials → Install a certificate → CA certificate"
                )
            }
            val install = KeyChain.createInstallIntent().apply {
                putExtra(KeyChain.EXTRA_CERTIFICATE, manager.caDerBytes())
                putExtra(KeyChain.EXTRA_NAME, manager.organizationName())
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val launched = runCatching {
                ctx.startActivity(install)
                true
            }.getOrDefault(false)
            if (!launched) {
                runCatching {
                    ctx.startActivity(
                        Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(saved, "application/x-x509-ca-cert")
                            addFlags(
                                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                    Intent.FLAG_ACTIVITY_NEW_TASK
                            )
                        }
                    )
                }
            }
        }
    }

    fun openSecuritySettings() {
        val ctx = getApplication<Application>()
        val intents = listOf(
            Intent("android.settings.SECURITY_SETTINGS"),
            Intent(Settings.ACTION_SECURITY_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
        for (intent in intents) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(ctx.packageManager) != null) {
                runCatching { ctx.startActivity(intent) }
                _ui.update {
                    it.copy(
                        statusMessage = "Open Encryption & credentials → Install a certificate → " +
                            "CA certificate → choose nexus-mitm-ca.crt from Downloads"
                    )
                }
                return
            }
        }
    }

    private fun saveCaToDownloads(manager: MitmCaManager): Uri {
        val ctx = getApplication<Application>()
        val resolver = ctx.contentResolver
        val name = "nexus-mitm-ca.crt"
        val mime = "application/x-x509-ca-cert"
        val der = manager.caDerBytes()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Replace previous export if present
            resolver.delete(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                "${MediaStore.Downloads.DISPLAY_NAME}=?",
                arrayOf(name)
            )
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.IS_PENDING, 1)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("MediaStore insert failed")
            resolver.openOutputStream(uri)?.use { it.write(der) }
                ?: error("Cannot write Downloads")
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri
        }

        @Suppress("DEPRECATION")
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, name)
        file.writeBytes(der)
        return Uri.fromFile(file)
    }

    private fun saveCaToCache(manager: MitmCaManager): Uri {
        val ctx = getApplication<Application>()
        val out = File(ctx.cacheDir, "nexus-mitm-ca.crt")
        out.writeBytes(manager.caDerBytes())
        return FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", out)
    }
}
