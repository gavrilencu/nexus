package com.example.toolkit.ui.websocket

import androidx.lifecycle.ViewModel
import com.example.toolkit.data.websocket.WebSocketEngine
import com.example.toolkit.data.websocket.WsMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class WebSocketUiState(
    val url: String = "",
    val outgoing: String = "",
    val connected: Boolean = false,
    val messages: List<WsMessage> = emptyList()
)

class WebSocketViewModel(private val engine: WebSocketEngine = WebSocketEngine()) : ViewModel() {
    private val _state = MutableStateFlow(WebSocketUiState())
    val state: StateFlow<WebSocketUiState> = _state.asStateFlow()

    fun onUrl(v: String) = _state.update { it.copy(url = v) }
    fun onOutgoing(v: String) = _state.update { it.copy(outgoing = v) }

    fun toggleConnection() {
        if (_state.value.connected || engine.isConnected) {
            engine.disconnect()
        } else {
            val url = _state.value.url
            if (url.isBlank()) return
            _state.update { it.copy(messages = emptyList()) }
            engine.connect(url, onEvent = { msg ->
                _state.update { it.copy(messages = it.messages + msg) }
            }, onState = { connected ->
                _state.update { it.copy(connected = connected) }
            })
        }
    }

    fun send() {
        val text = _state.value.outgoing
        if (text.isBlank()) return
        if (engine.send(text)) _state.update { it.copy(outgoing = "") }
    }

    fun clear() = _state.update { it.copy(messages = emptyList()) }

    override fun onCleared() {
        engine.disconnect()
        super.onCleared()
    }
}
