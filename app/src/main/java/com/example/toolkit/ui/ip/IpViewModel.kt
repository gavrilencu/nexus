package com.example.toolkit.ui.ip

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.toolkit.data.ip.IpToolResult
import com.example.toolkit.data.ip.IpToolsEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class IpUiState(
    val input: String = "",
    val loading: Boolean = false,
    val result: IpToolResult? = null
)

class IpViewModel(
    private val engine: IpToolsEngine = IpToolsEngine()
) : ViewModel() {
    private val _state = MutableStateFlow(IpUiState())
    val state: StateFlow<IpUiState> = _state.asStateFlow()

    fun onInput(v: String) = _state.update { it.copy(input = v) }

    fun analyze() {
        val input = _state.value.input
        if (input.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, result = null) }
            val result = engine.analyze(input)
            _state.update { it.copy(loading = false, result = result) }
        }
    }
}
