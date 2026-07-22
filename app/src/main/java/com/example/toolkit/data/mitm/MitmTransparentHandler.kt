package com.example.toolkit.data.mitm

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.concurrent.thread

/**
 * Transparent HTTP/HTTPS MITM over an already-accepted client [Socket]
 * (from a loopback pair fed by the VPN TUN relay).
 *
 * HTTPS is forwarded to the local [MitmProxyServer] via CONNECT so TLS MITM
 * uses one code path (SSLSocket server wrap) instead of a fragile SSL bridge.
 */
object MitmTransparentHandler {

    fun handle(
        client: Socket,
        destIp: String,
        destPort: Int,
        ca: MitmCaManager,
        protect: (Socket) -> Boolean,
        via: String = "vpn",
        proxyPort: Int = MitmProxyServer.DEFAULT_PORT
    ) {
        client.soTimeout = 30_000
        client.tcpNoDelay = true
        try {
            if (destPort == 443 || destPort == 8443) {
                handleHttpsViaLocalProxy(client, destIp, destPort, proxyPort, via)
            } else {
                handleHttp(client, destIp, destPort, protect, via)
            }
        } catch (_: Exception) {
        } finally {
            runCatching { client.close() }
        }
    }

    private fun handleHttp(
        client: Socket,
        destIp: String,
        destPort: Int,
        protect: (Socket) -> Boolean,
        via: String
    ) {
        val clientIn = BufferedInputStream(client.getInputStream())
        val clientOut = BufferedOutputStream(client.getOutputStream())
        val req = HttpMessageIo.readMessage(clientIn, isRequest = true) ?: return
        val host = req.header("Host")?.substringBefore(':') ?: destIp
        val path = normalizePath(req.target)
        val started = System.currentTimeMillis()
        try {
            val remote = Socket()
            if (!protect(remote)) throw IllegalStateException("protect() failed")
            remote.tcpNoDelay = true
            remote.connect(InetSocketAddress(destIp, destPort), 8_000)
            remote.soTimeout = 25_000
            val headers = LinkedHashMap(req.headers)
            headers["Connection"] = "close"
            headers["Content-Length"] = req.body.size.toString()
            val outbound = RawHttpMessage("${req.method} $path HTTP/1.1", headers, req.body)
            HttpMessageIo.writeAll(BufferedOutputStream(remote.getOutputStream()), outbound.toByteArray())
            val resp = HttpMessageIo.readMessage(BufferedInputStream(remote.getInputStream()), isRequest = false)
                ?: RawHttpMessage("HTTP/1.1 502 Bad Gateway", LinkedHashMap(), ByteArray(0))
            val rh = LinkedHashMap(resp.headers)
            rh["Connection"] = "close"
            HttpMessageIo.writeAll(clientOut, RawHttpMessage(resp.startLine, rh, resp.body).toByteArray())
            remote.close()
            emitOk("http", host, path, req, outbound, resp, started, via)
        } catch (e: Exception) {
            emitErr("http", req.method, host, path, e.message, via)
        }
    }

    /**
     * Peek SNI, CONNECT to in-process proxy, splice raw TLS bytes.
     * Capture/decrypt happens inside [MitmProxyServer] (with VpnService.protect).
     */
    private fun handleHttpsViaLocalProxy(
        client: Socket,
        destIp: String,
        destPort: Int,
        proxyPort: Int,
        via: String
    ) {
        val rawIn = BufferedInputStream(client.getInputStream())
        val hello = readTlsRecordPrefix(rawIn) ?: run {
            emitErr("https", "TLS", destIp, "/", "Not a TLS ClientHello", via)
            return
        }
        val sni = parseSni(hello) ?: destIp

        val proxy = Socket()
        try {
            proxy.tcpNoDelay = true
            proxy.connect(InetSocketAddress("127.0.0.1", proxyPort), 5_000)
            proxy.soTimeout = 30_000
            val proxyOut = BufferedOutputStream(proxy.getOutputStream())
            val proxyIn = BufferedInputStream(proxy.getInputStream())

            val connect = buildString {
                append("CONNECT ").append(sni).append(':').append(destPort).append(" HTTP/1.1\r\n")
                append("Host: ").append(sni).append(':').append(destPort).append("\r\n")
                append("X-Nexus-Via: ").append(via).append("\r\n")
                append("Proxy-Connection: keep-alive\r\n\r\n")
            }
            proxyOut.write(connect.toByteArray())
            proxyOut.flush()

            val status = readLine(proxyIn) ?: throw IllegalStateException("No CONNECT response")
            while (true) {
                val line = readLine(proxyIn) ?: break
                if (line.isEmpty()) break
            }
            if (!status.contains("200")) {
                emitErr("https", "CONNECT", sni, "/", "Local proxy: $status", via)
                return
            }

            // Forward peeked ClientHello, then splice both directions.
            proxyOut.write(hello)
            proxyOut.flush()

            val up = thread(isDaemon = true, name = "mitm-splice-up") {
                try {
                    copyStream(rawIn, proxyOut)
                } catch (_: Exception) {
                } finally {
                    runCatching { proxy.shutdownOutput() }
                }
            }
            try {
                copyStream(proxyIn, client.getOutputStream())
            } catch (_: Exception) {
            } finally {
                up.interrupt()
                runCatching { client.shutdownOutput() }
            }
        } catch (e: Exception) {
            emitErr("https", "TLS", sni, "/", e.message ?: "HTTPS splice failed", via)
        } finally {
            runCatching { proxy.close() }
        }
    }

    private fun copyStream(input: InputStream, output: OutputStream) {
        val buf = ByteArray(16_384)
        val out = if (output is BufferedOutputStream) output else BufferedOutputStream(output)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            out.flush()
        }
    }

    private fun readLine(input: BufferedInputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = input.read()
            if (b < 0) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) break
            if (b != '\r'.code) sb.append(b.toChar())
        }
        return sb.toString()
    }

    private fun readTlsRecordPrefix(input: BufferedInputStream): ByteArray? {
        val header = ByteArray(5)
        if (!readFully(input, header, 0, 5)) return null
        if (header[0] != 0x16.toByte()) return null
        val len = ((header[3].toInt() and 0xFF) shl 8) or (header[4].toInt() and 0xFF)
        if (len <= 0 || len > 16_384) return null
        val total = 5 + len
        val buf = ByteArray(total)
        System.arraycopy(header, 0, buf, 0, 5)
        if (!readFully(input, buf, 5, len)) return null
        return buf
    }

    private fun readFully(input: BufferedInputStream, buf: ByteArray, off: Int, len: Int): Boolean {
        var got = 0
        while (got < len) {
            val n = input.read(buf, off + got, len - got)
            if (n < 0) return false
            got += n
        }
        return true
    }

    private fun normalizePath(target: String): String {
        if (!target.startsWith("http", true)) return target.ifBlank { "/" }
        return try {
            val u = java.net.URI(target)
            (u.rawPath.ifBlank { "/" }) + (u.rawQuery?.let { "?$it" } ?: "")
        } catch (_: Exception) {
            target
        }
    }

    private fun emitOk(
        scheme: String,
        host: String,
        path: String,
        req: RawHttpMessage,
        outbound: RawHttpMessage,
        resp: RawHttpMessage,
        started: Long,
        via: String
    ) {
        MitmCaptureBus.emit(
            HttpExchange(
                id = MitmCaptureBus.nextId(),
                timestamp = started,
                scheme = scheme,
                method = req.method,
                host = host,
                path = path,
                url = "$scheme://$host$path",
                requestHeaders = outbound.headers,
                requestBody = outbound.bodyText(),
                responseCode = resp.statusCode,
                responseReason = resp.reason,
                responseHeaders = resp.headers,
                responseBody = resp.bodyText(),
                durationMs = System.currentTimeMillis() - started,
                via = via
            )
        )
    }

    private fun emitErr(scheme: String, method: String, host: String, path: String, err: String?, via: String) {
        MitmCaptureBus.emit(
            HttpExchange(
                id = MitmCaptureBus.nextId(),
                timestamp = System.currentTimeMillis(),
                scheme = scheme,
                method = method,
                host = host,
                path = path,
                url = "$scheme://$host$path",
                requestHeaders = emptyMap(),
                requestBody = "",
                responseCode = 0,
                responseReason = "",
                responseHeaders = emptyMap(),
                responseBody = "",
                durationMs = 0,
                error = err,
                via = via
            )
        )
    }

    fun parseSni(data: ByteArray): String? {
        try {
            if (data.size < 43 || data[0] != 0x16.toByte()) return null
            var i = 5
            if (data[i] != 0x01.toByte()) return null
            i += 4
            i += 2
            i += 32
            if (i >= data.size) return null
            val sessionLen = data[i].toInt() and 0xFF
            i += 1 + sessionLen
            if (i + 2 > data.size) return null
            val csLen = ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            i += 2 + csLen
            if (i >= data.size) return null
            val compLen = data[i].toInt() and 0xFF
            i += 1 + compLen
            if (i + 2 > data.size) return null
            val extLen = ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            i += 2
            val extEnd = (i + extLen).coerceAtMost(data.size)
            while (i + 4 <= extEnd) {
                val type = ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
                val len = ((data[i + 2].toInt() and 0xFF) shl 8) or (data[i + 3].toInt() and 0xFF)
                i += 4
                if (type == 0 && i + len <= data.size) {
                    var j = i + 2
                    while (j + 3 <= i + len) {
                        val nameType = data[j].toInt() and 0xFF
                        val nameLen = ((data[j + 1].toInt() and 0xFF) shl 8) or (data[j + 2].toInt() and 0xFF)
                        j += 3
                        if (nameType == 0 && j + nameLen <= data.size) {
                            return String(data, j, nameLen, Charsets.US_ASCII)
                        }
                        j += nameLen
                    }
                }
                i += len
            }
        } catch (_: Exception) {
        }
        return null
    }
}
