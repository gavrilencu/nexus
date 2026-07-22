package com.example.toolkit.data.mitm

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLSocket

/**
 * Explicit HTTP(S) MITM proxy.
 * Clients that set HTTP proxy to this port (or are redirected here) get
 * plaintext HTTP recorded, and HTTPS via CONNECT → forged cert → decrypt.
 *
 * When running under [MitmVpnService], [protect] **must** bypass the TUN —
 * otherwise upstream sockets are captured again (recursive MITM → bad_record_mac /
 * certificate_unknown / bad packet length).
 */
class MitmProxyServer(
    private val ca: MitmCaManager,
    private val port: Int = DEFAULT_PORT,
    private val protect: ((Socket) -> Boolean)? = null
) {
    private val running = AtomicBoolean(false)
    private var server: ServerSocket? = null
    private val pool = Executors.newCachedThreadPool()

    val listenPort: Int get() = server?.localPort ?: port

    fun start() {
        if (running.getAndSet(true)) return
        server = ServerSocket(port, 50, InetAddress.getByName("0.0.0.0"))
        pool.execute {
            while (running.get()) {
                try {
                    val client = server?.accept() ?: break
                    client.tcpNoDelay = true
                    pool.execute { handleClient(client) }
                } catch (_: Exception) {
                    if (!running.get()) break
                }
            }
        }
    }

    fun stop() {
        running.set(false)
        runCatching { server?.close() }
        server = null
    }

    private fun handleClient(client: Socket) {
        client.soTimeout = 30_000
        try {
            val input = BufferedInputStream(client.getInputStream())
            val output = BufferedOutputStream(client.getOutputStream())
            val req = HttpMessageIo.readMessage(input, isRequest = true) ?: return
            val via = req.header("X-Nexus-Via") ?: "proxy"
            if (req.method == "CONNECT") {
                handleConnect(client, output, req, via)
            } else {
                handlePlainHttp(client, output, req, via)
            }
        } catch (_: Exception) {
        } finally {
            runCatching { client.close() }
        }
    }

    private fun handleConnect(
        client: Socket,
        clientOut: BufferedOutputStream,
        connectReq: RawHttpMessage,
        via: String
    ) {
        val target = connectReq.target // host:port
        val host = target.substringBefore(':')
        val port = target.substringAfter(':', "443").toIntOrNull() ?: 443

        // Acknowledge CONNECT so the client starts TLS
        HttpMessageIo.writeAll(clientOut, "HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray())

        val sslClient = try {
            val ctx = ca.serverSslContext(host)
            val sock = ctx.socketFactory.createSocket(client, host, port, false) as SSLSocket
            sock.useClientMode = false
            sock.tcpNoDelay = true
            sock.enabledProtocols = sock.supportedProtocols.filter {
                it.startsWith("TLS")
            }.toTypedArray()
            sock.startHandshake()
            sock
        } catch (e: Exception) {
            emitError("https", "CONNECT", host, "/", e.message ?: "TLS handshake failed", via)
            return
        }

        try {
            val sslIn = BufferedInputStream(sslClient.inputStream)
            val sslOut = BufferedOutputStream(sslClient.outputStream)
            while (running.get() && !sslClient.isClosed) {
                val req = HttpMessageIo.readMessage(sslIn, isRequest = true) ?: break
                relayHttps(host, port, req, sslOut, via)
            }
        } catch (_: Exception) {
        } finally {
            runCatching { sslClient.close() }
        }
    }

    private fun relayHttps(
        host: String,
        port: Int,
        req: RawHttpMessage,
        clientOut: BufferedOutputStream,
        via: String
    ) {
        val path = req.target.ifBlank { "/" }
        val url = "https://$host${if (path.startsWith("/")) path else "/$path"}"
        val started = System.currentTimeMillis()
        try {
            val remote = openUpstreamSsl(host, port)
            val remoteOut = BufferedOutputStream(remote.outputStream)
            val remoteIn = BufferedInputStream(remote.inputStream)

            val originTarget = if (path.startsWith("http", true)) {
                try {
                    java.net.URI(path).rawPath.ifBlank { "/" } +
                        (java.net.URI(path).rawQuery?.let { "?$it" } ?: "")
                } catch (_: Exception) {
                    path
                }
            } else path
            val start = "${req.method} $originTarget HTTP/1.1"
            val headers = LinkedHashMap(req.headers)
            headers.keys.filter {
                it.equals("Proxy-Connection", true) ||
                    it.equals("Proxy-Authorization", true) ||
                    it.equals("X-Nexus-Via", true)
            }.forEach { headers.remove(it) }
            if (!headers.keys.any { it.equals("Host", true) }) headers["Host"] = host
            headers["Connection"] = "close"
            headers.remove("Transfer-Encoding")
            headers["Content-Length"] = req.body.size.toString()

            val outbound = RawHttpMessage(start, headers, req.body)
            HttpMessageIo.writeAll(remoteOut, outbound.toByteArray())
            val resp = HttpMessageIo.readMessage(remoteIn, isRequest = false)
                ?: RawHttpMessage("HTTP/1.1 502 Bad Gateway", LinkedHashMap(), ByteArray(0))
            val respHeaders = LinkedHashMap(resp.headers)
            respHeaders["Connection"] = "close"
            val toClient = RawHttpMessage(resp.startLine, respHeaders, resp.body)
            HttpMessageIo.writeAll(clientOut, toClient.toByteArray())
            remote.close()

            MitmCaptureBus.emit(
                HttpExchange(
                    id = MitmCaptureBus.nextId(),
                    timestamp = started,
                    scheme = "https",
                    method = req.method,
                    host = host,
                    path = originTarget,
                    url = url,
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
        } catch (e: Exception) {
            val err = "HTTP/1.1 502 Bad Gateway\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
            runCatching { HttpMessageIo.writeAll(clientOut, err.toByteArray()) }
            emitError("https", req.method, host, path, e.message ?: "upstream error", via)
        }
    }

    private fun handlePlainHttp(client: Socket, clientOut: BufferedOutputStream, req: RawHttpMessage, via: String) {
        val started = System.currentTimeMillis()
        val target = req.target
        val (host, port, path, url) = resolveHttpTarget(target, req)
        try {
            val remote = openUpstreamPlain(host, port)
            val remoteOut = BufferedOutputStream(remote.outputStream)
            val remoteIn = BufferedInputStream(remote.inputStream)
            val headers = LinkedHashMap(req.headers)
            headers.keys.filter {
                it.equals("Proxy-Connection", true) ||
                    it.equals("Proxy-Authorization", true) ||
                    it.equals("X-Nexus-Via", true)
            }.forEach { headers.remove(it) }
            if (!headers.keys.any { it.equals("Host", true) }) headers["Host"] = host
            headers["Connection"] = "close"
            headers["Content-Length"] = req.body.size.toString()
            val start = "${req.method} $path HTTP/1.1"
            val outbound = RawHttpMessage(start, headers, req.body)
            HttpMessageIo.writeAll(remoteOut, outbound.toByteArray())
            val resp = HttpMessageIo.readMessage(remoteIn, isRequest = false)
                ?: RawHttpMessage("HTTP/1.1 502 Bad Gateway", LinkedHashMap(), ByteArray(0))
            val respHeaders = LinkedHashMap(resp.headers)
            respHeaders["Connection"] = "close"
            HttpMessageIo.writeAll(clientOut, RawHttpMessage(resp.startLine, respHeaders, resp.body).toByteArray())
            remote.close()

            MitmCaptureBus.emit(
                HttpExchange(
                    id = MitmCaptureBus.nextId(),
                    timestamp = started,
                    scheme = "http",
                    method = req.method,
                    host = host,
                    path = path,
                    url = url,
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
        } catch (e: Exception) {
            emitError("http", req.method, host, path, e.message ?: "upstream error", via)
        }
    }

    private fun openUpstreamPlain(host: String, port: Int): Socket {
        val remote = Socket()
        ensureProtected(remote)
        remote.tcpNoDelay = true
        remote.connect(InetSocketAddress(host, port), 8_000)
        remote.soTimeout = 25_000
        return remote
    }

    private fun openUpstreamSsl(host: String, port: Int): SSLSocket {
        val plain = Socket()
        ensureProtected(plain)
        plain.tcpNoDelay = true
        plain.connect(InetSocketAddress(host, port), 8_000)
        val factory = javax.net.ssl.SSLSocketFactory.getDefault() as javax.net.ssl.SSLSocketFactory
        val remote = factory.createSocket(plain, host, port, true) as SSLSocket
        remote.soTimeout = 25_000
        remote.tcpNoDelay = true
        remote.startHandshake()
        return remote
    }

    private fun ensureProtected(socket: Socket) {
        val p = protect ?: return
        if (!p(socket)) {
            runCatching { socket.close() }
            throw IllegalStateException("VpnService.protect() failed — upstream would loop into TUN")
        }
    }

    private data class Target(val host: String, val port: Int, val path: String, val url: String)

    private fun resolveHttpTarget(target: String, req: RawHttpMessage): Target {
        return if (target.startsWith("http://", true) || target.startsWith("https://", true)) {
            val uri = java.net.URI(target)
            val host = uri.host ?: req.header("Host")?.substringBefore(':') ?: "localhost"
            val port = if (uri.port > 0) uri.port else if (uri.scheme.equals("https", true)) 443 else 80
            val path = (uri.rawPath.ifBlank { "/" }) + (uri.rawQuery?.let { "?$it" } ?: "")
            Target(host, port, path, target)
        } else {
            val hostHdr = req.header("Host") ?: "localhost"
            val host = hostHdr.substringBefore(':')
            val port = hostHdr.substringAfter(':', "80").toIntOrNull() ?: 80
            Target(host, port, target.ifBlank { "/" }, "http://$hostHdr$target")
        }
    }

    private fun emitError(scheme: String, method: String, host: String, path: String, err: String, via: String) {
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

    companion object {
        const val DEFAULT_PORT = 8888
    }
}
