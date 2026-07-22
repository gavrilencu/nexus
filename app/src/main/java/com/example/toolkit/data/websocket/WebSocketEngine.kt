package com.example.toolkit.data.websocket

import com.example.toolkit.data.network.HttpClients
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

enum class WsDirection { SENT, RECEIVED, SYSTEM, ERROR }

data class WsMessage(
    val direction: WsDirection,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Thin wrapper over OkHttp's native WebSocket client. Opens a connection to a
 * ws(s):// endpoint and surfaces the full lifecycle (open, messages in/out,
 * closing, failure) to a single [onEvent] sink so the ViewModel can render a
 * live frame log. Supports sending text frames and clean close.
 */
class WebSocketEngine {

    private var socket: WebSocket? = null
    private var onEvent: ((WsMessage) -> Unit)? = null
    private var onState: ((Boolean) -> Unit)? = null

    val isConnected get() = socket != null

    fun connect(rawUrl: String, onEvent: (WsMessage) -> Unit, onState: (Boolean) -> Unit) {
        this.onEvent = onEvent
        this.onState = onState
        val url = normalize(rawUrl)
        onEvent(WsMessage(WsDirection.SYSTEM, "Connecting to $url…"))

        val request = try {
            Request.Builder().url(url.replaceFirst("ws://", "http://").replaceFirst("wss://", "https://"))
                .header("User-Agent", "NEXUS-Toolkit/1.0")
                .build()
        } catch (e: Exception) {
            onEvent(WsMessage(WsDirection.ERROR, "Invalid URL: ${e.message}"))
            return
        }

        socket = HttpClients.default.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                onEvent(WsMessage(WsDirection.SYSTEM, "Connected (HTTP ${response.code}). Handshake OK."))
                response.headers.forEach { (n, v) ->
                    if (n.lowercase().startsWith("sec-websocket") || n.equals("upgrade", true))
                        onEvent(WsMessage(WsDirection.SYSTEM, "$n: $v"))
                }
                onState(true)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                onEvent(WsMessage(WsDirection.RECEIVED, text))
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                onEvent(WsMessage(WsDirection.RECEIVED, "[binary ${bytes.size}B] ${bytes.hex().take(120)}"))
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                onEvent(WsMessage(WsDirection.SYSTEM, "Closing: $code ${reason.ifBlank { "" }}"))
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                onEvent(WsMessage(WsDirection.SYSTEM, "Closed: $code ${reason.ifBlank { "" }}"))
                socket = null
                onState(false)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                onEvent(WsMessage(WsDirection.ERROR, "Failure: ${t.message ?: t.javaClass.simpleName}"))
                socket = null
                onState(false)
            }
        })
    }

    fun send(text: String): Boolean {
        val s = socket ?: return false
        val ok = s.send(text)
        if (ok) onEvent?.invoke(WsMessage(WsDirection.SENT, text))
        else onEvent?.invoke(WsMessage(WsDirection.ERROR, "Send failed (queue full or closed)."))
        return ok
    }

    fun disconnect() {
        socket?.close(1000, "Client closed")
        socket = null
    }

    private fun normalize(raw: String): String {
        val s = raw.trim()
        return when {
            s.startsWith("ws://") || s.startsWith("wss://") -> s
            s.startsWith("http://") -> s.replaceFirst("http://", "ws://")
            s.startsWith("https://") -> s.replaceFirst("https://", "wss://")
            else -> "wss://$s"
        }
    }
}
