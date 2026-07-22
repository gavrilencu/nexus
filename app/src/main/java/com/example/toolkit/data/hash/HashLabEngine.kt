package com.example.toolkit.data.hash

import android.util.Base64
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

class HashLabEngine {

    fun md5(input: String): String = digest("MD5", input)
    fun sha1(input: String): String = digest("SHA-1", input)
    fun sha256(input: String): String = digest("SHA-256", input)
    fun sha512(input: String): String = digest("SHA-512", input)

    fun base64Encode(input: String): String =
        Base64.encodeToString(input.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)

    fun base64Decode(input: String): String = try {
        String(Base64.decode(input.trim(), Base64.DEFAULT), StandardCharsets.UTF_8)
    } catch (e: Exception) {
        "ERROR: ${e.message}"
    }

    fun urlEncode(input: String): String =
        URLEncoder.encode(input, StandardCharsets.UTF_8.name())

    fun urlDecode(input: String): String = try {
        URLDecoder.decode(input, StandardCharsets.UTF_8.name())
    } catch (e: Exception) {
        "ERROR: ${e.message}"
    }

    fun hexEncode(input: String): String =
        input.toByteArray(StandardCharsets.UTF_8).joinToString("") { "%02x".format(it) }

    fun hexDecode(input: String): String = try {
        val clean = input.replace(Regex("\\s+"), "")
        require(clean.length % 2 == 0) { "Odd hex length" }
        val bytes = clean.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        String(bytes, StandardCharsets.UTF_8)
    } catch (e: Exception) {
        "ERROR: ${e.message}"
    }

    data class Bundle(
        val md5: String,
        val sha1: String,
        val sha256: String,
        val sha512: String,
        val base64: String,
        val url: String,
        val hex: String
    )

    fun encodeAll(input: String): Bundle = Bundle(
        md5 = md5(input),
        sha1 = sha1(input),
        sha256 = sha256(input),
        sha512 = sha512(input),
        base64 = base64Encode(input),
        url = urlEncode(input),
        hex = hexEncode(input)
    )

    private fun digest(algo: String, input: String): String {
        val md = MessageDigest.getInstance(algo)
        val bytes = md.digest(input.toByteArray(StandardCharsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
