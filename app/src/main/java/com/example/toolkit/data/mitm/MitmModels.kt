package com.example.toolkit.data.mitm

data class HttpExchange(
    val id: Long,
    val timestamp: Long,
    val scheme: String,          // http | https
    val method: String,
    val host: String,
    val path: String,
    val url: String,
    val requestHeaders: Map<String, String>,
    val requestBody: String,
    val responseCode: Int,
    val responseReason: String,
    val responseHeaders: Map<String, String>,
    val responseBody: String,
    val durationMs: Long,
    val error: String? = null,
    val via: String = "proxy"    // proxy | vpn
) {
    val shortLine: String
        get() = "$method $host$path → ${if (responseCode > 0) responseCode else "ERR"}"
}

data class MitmStats(
    val running: Boolean = false,
    val mode: String = "idle",   // proxy | vpn | idle
    val total: Long = 0,
    val https: Long = 0,
    val http: Long = 0,
    val errors: Long = 0,
    val proxyPort: Int = MitmProxyServer.DEFAULT_PORT,
    val listenHint: String = "",
    /** `user:pass` a LAN client must set as proxy credentials; blank = none required. */
    val proxyAuth: String = ""
)
