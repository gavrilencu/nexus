package com.example.toolkit.data.capture

import android.util.Base64

/**
 * Turns a raw captured payload preview into something a human can actually
 * read. Real traffic hides values behind percent-encoding (%40 = @), Base64
 * (Basic auth, tokens, JSON blobs) and hex, so "email" or "password" fields
 * look like garbage in the raw ASCII. This decodes those layers so the value
 * is shown in plain text.
 */
object PayloadDecoder {

    data class Decoded(
        val label: String,   // what kind of decoding produced this
        val source: String,  // the original encoded chunk (may be trimmed)
        val value: String    // the decoded, human-readable text
    )

    private val printableRatio = 0.85

    /** URL/percent-decode ("+"-as-space aware). Safe on already-plain text. */
    fun urlDecode(input: String): String {
        if (input.isEmpty()) return input
        val out = StringBuilder(input.length)
        var i = 0
        while (i < input.length) {
            val c = input[i]
            when {
                c == '%' && i + 2 < input.length -> {
                    val hex = input.substring(i + 1, i + 3)
                    val code = hex.toIntOrNull(16)
                    if (code != null) {
                        out.append(code.toChar())
                        i += 3
                    } else {
                        out.append(c); i++
                    }
                }
                c == '+' -> { out.append(' '); i++ }
                else -> { out.append(c); i++ }
            }
        }
        return out.toString()
    }

    /** Decode a chunk of hex (with or without spaces/colons) into ASCII. */
    fun hexToAscii(input: String): String? {
        val clean = input.filter { it.isLetterOrDigit() }
        if (clean.length < 2 || clean.length % 2 != 0) return null
        if (!clean.all { it in "0123456789abcdefABCDEF" }) return null
        val bytes = ByteArray(clean.length / 2)
        for (j in bytes.indices) {
            bytes[j] = clean.substring(j * 2, j * 2 + 2).toInt(16).toByte()
        }
        val text = String(bytes, Charsets.UTF_8)
        return if (isMostlyPrintable(text)) text else null
    }

    private fun base64Decode(token: String): String? {
        return runCatching {
            val bytes = Base64.decode(token, Base64.DEFAULT)
            if (bytes.isEmpty()) return null
            val text = String(bytes, Charsets.UTF_8)
            if (isMostlyPrintable(text) && text.length >= 3) text else null
        }.getOrNull()
    }

    private val base64Token = Regex("[A-Za-z0-9+/]{12,}={0,2}")

    /**
     * Full decode pass over a payload. Produces every readable interpretation
     * we can find so the user can eyeball the plaintext credential/data.
     */
    fun decodeAll(ascii: String): List<Decoded> {
        if (ascii.isBlank()) return emptyList()
        val results = LinkedHashMap<String, Decoded>()

        // 1. URL-decode the whole payload if it actually changes anything.
        val url = urlDecode(ascii)
        if (url != ascii) {
            results["url"] = Decoded("URL-decoded (text lizibil)", ascii.take(240), url.take(1200))
        }

        // 2. HTTP Basic auth -> user:pass in clear.
        Regex("(?i)Authorization:\\s*Basic\\s+([A-Za-z0-9+/=]+)").find(ascii)?.let { m ->
            base64Decode(m.groupValues[1])?.let {
                results["basic"] = Decoded("Basic Auth (Base64)", m.groupValues[1], it)
            }
        }

        // 3. Standalone Base64 tokens (JSON parts, cookies, tokens...).
        base64Token.findAll(url).take(12).forEach { m ->
            val tok = m.value
            // skip pure-hex or things that are obviously not base64 payloads
            base64Decode(tok)?.let { dec ->
                if (dec != tok && dec.any { it.isLetterOrDigit() }) {
                    results["b64:$tok"] = Decoded("Base64 → text", tok.take(120), dec.take(600))
                }
            }
        }

        return results.values.toList()
    }

    private fun isMostlyPrintable(text: String): Boolean {
        if (text.isEmpty()) return false
        val printable = text.count { it == '\n' || it == '\r' || it == '\t' || it.code in 32..126 || it.code in 160..0x2FFF }
        return printable.toDouble() / text.length >= printableRatio
    }
}
