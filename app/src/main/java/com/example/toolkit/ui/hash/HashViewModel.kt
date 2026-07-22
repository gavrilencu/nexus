package com.example.toolkit.ui.hash

import androidx.lifecycle.ViewModel
import com.example.toolkit.data.hash.HashLabEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class HashUiState(
    val input: String = "",
    val mode: String = "ENCODE",
    val decodeInput: String = "",
    val decodeType: String = "Base64",
    val bundle: HashLabEngine.Bundle? = null,
    val decodeResult: String = ""
)

class HashViewModel(
    private val engine: HashLabEngine = HashLabEngine()
) : ViewModel() {
    private val _state = MutableStateFlow(HashUiState())
    val state: StateFlow<HashUiState> = _state.asStateFlow()

    fun onInput(v: String) = _state.update { it.copy(input = v) }
    fun onDecodeInput(v: String) = _state.update { it.copy(decodeInput = v) }
    fun onDecodeType(v: String) = _state.update { it.copy(decodeType = v) }

    fun encode() {
        val input = _state.value.input
        if (input.isEmpty()) return
        _state.update { it.copy(bundle = engine.encodeAll(input), mode = "ENCODE") }
    }

    fun decode() {
        val s = _state.value
        if (s.decodeInput.isBlank()) return
        val result = when (s.decodeType) {
            "Base64" -> engine.base64Decode(s.decodeInput)
            "URL" -> engine.urlDecode(s.decodeInput)
            "Hex" -> engine.hexDecode(s.decodeInput)
            else -> engine.base64Decode(s.decodeInput)
        }
        _state.update { it.copy(decodeResult = result, mode = "DECODE") }
    }
}
