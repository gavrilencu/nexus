package com.example.toolkit.data.mitm

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

object MitmCaptureBus {
    private val seq = AtomicLong(1)
    private val _exchanges = MutableStateFlow<List<HttpExchange>>(emptyList())
    private val _stats = MutableStateFlow(MitmStats())
    private val _running = MutableStateFlow(false)
    private val _hostFilter = MutableStateFlow("")

    val exchanges: StateFlow<List<HttpExchange>> = _exchanges.asStateFlow()
    val stats: StateFlow<MitmStats> = _stats.asStateFlow()
    val running: StateFlow<Boolean> = _running.asStateFlow()
    val hostFilter: StateFlow<String> = _hostFilter.asStateFlow()

    private const val MAX = 400

    fun nextId(): Long = seq.getAndIncrement()

    fun setHostFilter(v: String) {
        _hostFilter.value = v.trim()
    }

    fun setRunning(on: Boolean, mode: String = if (on) "proxy" else "idle", port: Int = MitmProxyServer.DEFAULT_PORT, hint: String = "") {
        _running.value = on
        _stats.update {
            it.copy(
                running = on,
                mode = mode,
                proxyPort = port,
                listenHint = hint
            )
        }
    }

    fun emit(ex: HttpExchange) {
        val f = _hostFilter.value
        if (f.isNotBlank() && !ex.host.contains(f, ignoreCase = true) && !ex.url.contains(f, ignoreCase = true)) {
            // Still count but don't show? Better: store all, filter in UI.
        }
        _exchanges.update { list -> (list + ex).takeLast(MAX) }
        _stats.update {
            it.copy(
                total = it.total + 1,
                https = it.https + if (ex.scheme.equals("https", true)) 1 else 0,
                http = it.http + if (ex.scheme.equals("http", true)) 1 else 0,
                errors = it.errors + if (ex.error != null || ex.responseCode <= 0) 1 else 0
            )
        }
    }

    fun clear() {
        _exchanges.value = emptyList()
        _stats.update {
            it.copy(total = 0, https = 0, http = 0, errors = 0)
        }
    }

    fun filtered(): List<HttpExchange> {
        val f = _hostFilter.value
        if (f.isBlank()) return _exchanges.value
        return _exchanges.value.filter {
            it.host.contains(f, true) || it.url.contains(f, true) || it.path.contains(f, true)
        }
    }
}
