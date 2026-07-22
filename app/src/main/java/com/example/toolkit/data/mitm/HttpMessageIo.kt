package com.example.toolkit.data.mitm

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.Charset
import java.util.Locale

data class RawHttpMessage(
    val startLine: String,
    val headers: LinkedHashMap<String, String>,
    val body: ByteArray
) {
    val method: String get() = startLine.substringBefore(' ').uppercase(Locale.US)
    val target: String get() = startLine.split(' ').getOrNull(1) ?: "/"
    val statusCode: Int
        get() = startLine.split(' ').getOrNull(1)?.toIntOrNull() ?: 0
    val reason: String
        get() = startLine.split(' ').drop(2).joinToString(" ").ifBlank { "" }

    fun header(name: String): String? =
        headers.entries.firstOrNull { it.key.equals(name, true) }?.value

    fun bodyText(max: Int = 64_000): String {
        if (body.isEmpty()) return ""
        val slice = if (body.size > max) body.copyOf(max) else body
        return String(slice, Charsets.UTF_8)
    }

    fun toByteArray(): ByteArray {
        val out = ByteArrayOutputStream()
        out.write("$startLine\r\n".toByteArray())
        headers.forEach { (k, v) -> out.write("$k: $v\r\n".toByteArray()) }
        out.write("\r\n".toByteArray())
        out.write(body)
        return out.toByteArray()
    }
}

object HttpMessageIo {

    fun readMessage(input: InputStream, isRequest: Boolean, maxBody: Int = 512_000): RawHttpMessage? {
        val start = readLine(input) ?: return null
        if (start.isBlank()) return null
        val headers = LinkedHashMap<String, String>()
        while (true) {
            val line = readLine(input) ?: break
            if (line.isEmpty()) break
            val idx = line.indexOf(':')
            if (idx > 0) {
                val name = line.substring(0, idx).trim()
                val value = line.substring(idx + 1).trim()
                headers[name] = value
            }
        }
        val body = readBody(input, headers, isRequest, maxBody)
        return RawHttpMessage(start, headers, body)
    }

    private fun readBody(
        input: InputStream,
        headers: Map<String, String>,
        isRequest: Boolean,
        maxBody: Int
    ): ByteArray {
        val te = headers.entries.firstOrNull { it.key.equals("Transfer-Encoding", true) }?.value
        if (te != null && te.contains("chunked", true)) {
            return readChunked(input, maxBody)
        }
        val cl = headers.entries.firstOrNull { it.key.equals("Content-Length", true) }?.value?.toIntOrNull()
        if (cl != null && cl > 0) {
            return readExact(input, cl.coerceAtMost(maxBody))
        }
        // No length: for requests assume empty; for responses without CL read until EOF (capped).
        if (!isRequest) {
            return readUntilEof(input, maxBody)
        }
        return ByteArray(0)
    }

    private fun readChunked(input: InputStream, maxBody: Int): ByteArray {
        val out = ByteArrayOutputStream()
        while (out.size() < maxBody) {
            val sizeLine = readLine(input) ?: break
            val size = sizeLine.substringBefore(';').trim().toIntOrNull(16) ?: break
            if (size == 0) {
                // trailers
                while (true) {
                    val t = readLine(input) ?: break
                    if (t.isEmpty()) break
                }
                break
            }
            val chunk = readExact(input, size.coerceAtMost(maxBody - out.size()))
            out.write(chunk)
            readLine(input) // CRLF after chunk
            if (chunk.size < size) break
        }
        return out.toByteArray()
    }

    private fun readExact(input: InputStream, n: Int): ByteArray {
        val buf = ByteArray(n)
        var off = 0
        while (off < n) {
            val r = input.read(buf, off, n - off)
            if (r < 0) break
            off += r
        }
        return if (off == n) buf else buf.copyOf(off)
    }

    private fun readUntilEof(input: InputStream, max: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8_192)
        while (out.size() < max) {
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    fun readLine(input: InputStream): String? {
        val bos = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b < 0) return if (bos.size() == 0) null else bos.toString(Charset.defaultCharset().name())
            if (b == '\n'.code) break
            if (b != '\r'.code) bos.write(b)
            if (bos.size() > 16_384) break
        }
        return bos.toString(Charsets.UTF_8.name())
    }

    fun writeAll(out: OutputStream, data: ByteArray) {
        out.write(data)
        out.flush()
    }
}
