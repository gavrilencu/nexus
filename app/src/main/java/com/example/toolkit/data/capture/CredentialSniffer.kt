package com.example.toolkit.data.capture

import android.util.Base64

data class CredentialFinding(
    val emails: List<String>,
    val hints: List<String>
) {
    val isEmpty: Boolean get() = emails.isEmpty() && hints.isEmpty()
}

/**
 * Best-effort scanner over a captured packet's ASCII payload preview,
 * looking for plaintext emails/usernames/passwords/tokens — i.e. things a
 * misconfigured app or an unencrypted (HTTP, not HTTPS) request might leak.
 *
 * Scope: this only ever sees payloads NEXUS already captured for *this
 * phone's own* traffic via the local VpnService — Android gives no way to
 * see other devices' packets without root/monitor-mode Wi‑Fi, which regular
 * phones don't support. This is a personal traffic-leak auditor, not a
 * network sniffer for other people's data.
 */
object CredentialSniffer {

    private val emailRegex = Regex(
        "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"
    )

    private val formFieldRegex = Regex(
        "(?i)(?:^|[&?\\s])(pass(?:word)?|pwd|passwd|user(?:name)?|login|email|token|apikey|api_key|secret)=([^&\\s\"'<>]{1,80})"
    )

    private val jsonFieldRegex = Regex(
        "(?i)\"(pass(?:word)?|pwd|username|user|email|login|token|secret|apikey)\"\\s*:\\s*\"([^\"]{1,80})\""
    )

    private val basicAuthRegex = Regex("(?i)Authorization:\\s*Basic\\s+([A-Za-z0-9+/=]+)")
    private val bearerAuthRegex = Regex("(?i)Authorization:\\s*Bearer\\s+([A-Za-z0-9._-]{8,})")
    private val cookieAuthRegex = Regex("(?i)(?:Cookie|Set-Cookie):[^\\r\\n]*?(sess[a-z]*|auth|token)=([^;\\r\\n]{1,40})")

    fun find(payloadAscii: String): CredentialFinding {
        if (payloadAscii.isBlank()) return CredentialFinding(emptyList(), emptyList())

        val emails = emailRegex.findAll(payloadAscii).map { it.value }.distinct().toList()
        val hints = mutableListOf<String>()

        formFieldRegex.findAll(payloadAscii).forEach { m ->
            val field = m.groupValues[1].lowercase()
            val value = m.groupValues[2]
            hints += if (field.contains("pass") || field.contains("pwd")) {
                "Form field \"$field\" = ${mask(value)}"
            } else {
                "Form field \"$field\" = $value"
            }
        }

        jsonFieldRegex.findAll(payloadAscii).forEach { m ->
            val field = m.groupValues[1].lowercase()
            val value = m.groupValues[2]
            hints += if (field.contains("pass") || field.contains("secret") || field.contains("token")) {
                "JSON field \"$field\" = ${mask(value)}"
            } else {
                "JSON field \"$field\" = $value"
            }
        }

        basicAuthRegex.find(payloadAscii)?.let { m ->
            val decoded = runCatching {
                String(Base64.decode(m.groupValues[1], Base64.DEFAULT), Charsets.UTF_8)
            }.getOrNull()
            hints += if (decoded != null && decoded.contains(':')) {
                val (user, pass) = decoded.split(':', limit = 2)
                "HTTP Basic Auth — user \"$user\", password ${mask(pass)}"
            } else {
                "HTTP Basic Auth header (base64 login)"
            }
        }

        bearerAuthRegex.find(payloadAscii)?.let {
            hints += "HTTP Bearer token = ${mask(it.groupValues[1])}"
        }

        cookieAuthRegex.find(payloadAscii)?.let { m ->
            hints += "Session cookie \"${m.groupValues[1]}\" = ${mask(m.groupValues[2])}"
        }

        return CredentialFinding(emails, hints.distinct())
    }

    fun hasFinding(payloadAscii: String): Boolean {
        val f = find(payloadAscii)
        return !f.isEmpty
    }

    private fun mask(value: String): String =
        if (value.length <= 4) "•".repeat(value.length)
        else value.take(2) + "•".repeat((value.length - 4).coerceAtMost(10)) + value.takeLast(2)
}
