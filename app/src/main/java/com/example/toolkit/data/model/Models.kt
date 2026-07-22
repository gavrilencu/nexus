package com.example.toolkit.data.model

data class ReconResult(
    val target: String,
    val resolvedIps: List<String> = emptyList(),
    val hostname: String? = null,
    val httpStatus: Int? = null,
    val serverHeader: String? = null,
    val poweredBy: String? = null,
    val contentType: String? = null,
    val responseTimeMs: Long? = null,
    val tlsVersion: String? = null,
    val certificateSubject: String? = null,
    val certificateIssuer: String? = null,
    val certificateExpiry: String? = null,
    val geoCountry: String? = null,
    val geoCity: String? = null,
    val geoIsp: String? = null,
    val geoOrg: String? = null,
    val geoAs: String? = null,
    val securityHeaders: Map<String, String> = emptyMap(),
    val openRedirectHints: List<String> = emptyList(),
    val error: String? = null
)

data class PortResult(
    val port: Int,
    val open: Boolean,
    val service: String,
    val latencyMs: Long? = null
)

data class PortScanProgress(
    val scanned: Int,
    val total: Int,
    val openPorts: List<PortResult> = emptyList(),
    val isRunning: Boolean = false,
    val target: String = ""
)

data class TrafficEntry(
    val id: String,
    val method: String,
    val url: String,
    val statusCode: Int?,
    val durationMs: Long,
    val requestHeaders: Map<String, String>,
    val responseHeaders: Map<String, String>,
    val requestBody: String?,
    val responseBodyPreview: String?,
    val error: String?,
    val timestamp: Long = System.currentTimeMillis()
)

data class OsintProfile(
    val platform: String,
    val username: String,
    val url: String,
    val exists: Boolean?,
    val confidence: Int = 0,
    val note: String? = null
)

data class OsintResult(
    val query: String,
    val queryType: String,
    val handlesTried: List<String> = emptyList(),
    val profiles: List<OsintProfile> = emptyList(),
    val emailPatterns: List<String> = emptyList(),
    val notes: List<String> = emptyList(),
    val error: String? = null
)

data class ApiProbeResult(
    val url: String,
    val method: String,
    val statusCode: Int?,
    val durationMs: Long,
    val headers: Map<String, String>,
    val body: String,
    val error: String? = null
)
