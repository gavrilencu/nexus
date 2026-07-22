package com.example.toolkit.ui.jwt

import androidx.lifecycle.ViewModel
import com.example.toolkit.data.jwt.JwtDecodeResult
import com.example.toolkit.data.jwt.JwtLabEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class JwtUiState(
    val token: String = "",
    val result: JwtDecodeResult? = null
)

class JwtViewModel(
    private val engine: JwtLabEngine = JwtLabEngine()
) : ViewModel() {
    private val _state = MutableStateFlow(JwtUiState())
    val state: StateFlow<JwtUiState> = _state.asStateFlow()

    fun onToken(v: String) = _state.update { it.copy(token = v) }

    fun decode() {
        val token = _state.value.token
        if (token.isBlank()) return
        _state.update { it.copy(result = engine.decode(token)) }
    }
}
