package com.example.toolkit.data.capture

import android.util.Base64

/** One decoded credential/value pulled out of a payload, in clear text. */
data class Credential(
    val label: String,   // e.g. "password", "email", "Basic Auth user"
    val value: String,   // decoded, human-readable value
    val sensitive: Boolean = false
)

data class CredentialFinding(
    val emails: List<String>,
    val credentials: List<Credential>
) {
    val isEmpty: Boolean get() = emails.isEmpty() && credentials.isEmpty()

    /** Flat, human-readable lines (values are shown IN CLEAR, not masked). */
    val hints: List<String>
        get() = credentials.map { "${it.label} = ${it.value}" }
}

/**
 * Best-effort scanner over a captured packet's ASCII payload preview,
 * looking for plaintext emails/usernames/passwords/tokens — i.e. things a
 * misconfigured app or an unencrypted (HTTP, not HTTPS) request might leak.
 *
 * Values are decoded (URL/percent-decoded, Base64 for Basic-Auth) and shown
 * IN CLEAR — this is a personal traffic-leak auditor for your own traffic, so
 * you can actually read what your apps are sending.
 */
object CredentialSniffer {

    private val emailRegex = Regex(
        "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"
    )

    private val formFieldRegex = Regex(
        "(?i)(?:^|[&?\\s])(pass(?:word)?|pwd|passwd|user(?:name)?|login|email|e-mail|mail|token|apikey|api_key|secret|otp|pin|auth)=([^&\\s\"'<>]{1,120})"
    )

    private val jsonFieldRegex = Regex(
        "(?i)\"(pass(?:word)?|pwd|username|user|email|e-mail|mail|login|token|secret|apikey|api_key|otp|pin|phone|mobile)\"\\s*:\\s*\"([^\"]{1,120})\""
    )

    private val basicAuthRegex = Regex("(?i)Authorization:\\s*Basic\\s+([A-Za-z0-9+/=]+)")
    private val bearerAuthRegex = Regex("(?i)Authorization:\\s*Bearer\\s+([A-Za-z0-9._-]{8,})")
    private val cookieAuthRegex = Regex("(?i)(?:Cookie|Set-Cookie):[^\\r\\n]*?(sess[a-z]*|auth|token|jwt|sid)=([^;\\r\\n]{1,120})")

    fun find(payloadAscii: String): CredentialFinding {
        if (payloadAscii.isBlank()) return CredentialFinding(emptyList(), emptyList())

        // Percent-encoding hides '@' and other chars, which is exactly why
        // emails/values looked "wrong" before — decode first, then scan.
        val decoded = PayloadDecoder.urlDecode(payloadAscii)

        val emails = (emailRegex.findAll(decoded).map { it.value } +
            emailRegex.findAll(payloadAscii).map { it.value })
            .distinct()
            .toList()

        val creds = mutableListOf<Credential>()

        formFieldRegex.findAll(decoded).forEach { m ->
            val field = m.groupValues[1].lowercase()
            val value = PayloadDecoder.urlDecode(m.groupValues[2])
            creds += Credential("form: $field", value, isSensitive(field))
        }

        jsonFieldRegex.findAll(decoded).forEach { m ->
            val field = m.groupValues[1].lowercase()
            val value = m.groupValues[2]
            creds += Credential("json: $field", value, isSensitive(field))
        }

        basicAuthRegex.find(payloadAscii)?.let { m ->
            val clear = runCatching {
                String(Base64.decode(m.groupValues[1], Base64.DEFAULT), Charsets.UTF_8)
            }.getOrNull()
            if (clear != null && clear.contains(':')) {
                val user = clear.substringBefore(':')
                val pass = clear.substringAfter(':')
                creds += Credential("Basic Auth user", user)
                creds += Credential("Basic Auth password", pass, sensitive = true)
            } else {
                creds += Credential("Basic Auth (Base64)", m.groupValues[1], sensitive = true)
            }
        }

        bearerAuthRegex.find(payloadAscii)?.let {
            creds += Credential("Bearer token", it.groupValues[1], sensitive = true)
        }

        cookieAuthRegex.find(payloadAscii)?.let { m ->
            creds += Credential("cookie: ${m.groupValues[1]}", m.groupValues[2], sensitive = true)
        }

        return CredentialFinding(emails.distinct(), creds.distinctBy { it.label + it.value })
    }

    fun hasFinding(payloadAscii: String): Boolean = !find(payloadAscii).isEmpty

    private fun isSensitive(field: String): Boolean =
        field.contains("pass") || field.contains("pwd") || field.contains("secret") ||
            field.contains("token") || field.contains("apikey") || field.contains("api_key") ||
            field.contains("otp") || field.contains("pin") || field.contains("auth")
}
