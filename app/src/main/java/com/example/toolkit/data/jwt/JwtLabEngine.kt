package com.example.toolkit.data.jwt

import android.util.Base64
import org.json.JSONObject
import java.nio.charset.StandardCharsets

data class JwtDecodeResult(
    val headerJson: String,
    val payloadJson: String,
    val algorithm: String?,
    val subject: String?,
    val issuer: String?,
    val audience: String?,
    val expiresAt: Long?,
    val issuedAt: Long?,
    val expired: Boolean?,
    val claims: Map<String, String>,
    val error: String? = null
)

class JwtLabEngine {

    fun decode(token: String): JwtDecodeResult {
        val raw = token.trim()
        if (raw.isBlank()) {
            return JwtDecodeResult(
                headerJson = "", payloadJson = "", algorithm = null,
                subject = null, issuer = null, audience = null,
                expiresAt = null, issuedAt = null, expired = null,
                claims = emptyMap(), error = "Empty token"
            )
        }

        return try {
            val parts = raw.split('.')
            require(parts.size >= 2) { "JWT must have at least header.payload" }

            val headerJson = decodePart(parts[0])
            val payloadJson = decodePart(parts[1])
            val header = JSONObject(headerJson)
            val payload = JSONObject(payloadJson)

            val exp = payload.optLong("exp", -1).takeIf { it > 0 }
            val iat = payload.optLong("iat", -1).takeIf { it > 0 }
            val now = System.currentTimeMillis() / 1000
            val expired = exp?.let { it < now }

            val claims = mutableMapOf<String, String>()
            payload.keys().forEach { key ->
                claims[key] = payload.opt(key)?.toString().orEmpty()
            }

            JwtDecodeResult(
                headerJson = pretty(headerJson),
                payloadJson = pretty(payloadJson),
                algorithm = header.optString("alg").ifBlank { null },
                subject = payload.optString("sub").ifBlank { null },
                issuer = payload.optString("iss").ifBlank { null },
                audience = payload.optString("aud").ifBlank { null },
                expiresAt = exp,
                issuedAt = iat,
                expired = expired,
                claims = claims
            )
        } catch (e: Exception) {
            JwtDecodeResult(
                headerJson = "", payloadJson = "", algorithm = null,
                subject = null, issuer = null, audience = null,
                expiresAt = null, issuedAt = null, expired = null,
                claims = emptyMap(), error = e.message ?: "Decode failed"
            )
        }
    }

    private fun decodePart(part: String): String {
        var b64 = part.replace('-', '+').replace('_', '/')
        while (b64.length % 4 != 0) b64 += "="
        val bytes = Base64.decode(b64, Base64.DEFAULT)
        return String(bytes, StandardCharsets.UTF_8)
    }

    private fun pretty(json: String): String = try {
        JSONObject(json).toString(2)
    } catch (_: Exception) {
        json
    }
}
