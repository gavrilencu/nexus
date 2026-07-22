package com.example.toolkit.ui.tls

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.toolkit.data.tls.TlsEngine
import com.example.toolkit.data.tls.TlsResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TlsUiState(
    val host: String = "",
    val loading: Boolean = false,
    val result: TlsResult? = null
)

class TlsViewModel(private val engine: TlsEngine = TlsEngine()) : ViewModel() {
    private val _state = MutableStateFlow(TlsUiState())
    val state: StateFlow<TlsUiState> = _state.asStateFlow()

    fun onHost(v: String) = _state.update { it.copy(host = v) }

    fun analyze() {
        val host = _state.value.host
        if (host.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, result = null) }
            val r = engine.analyze(host)
            _state.update { it.copy(loading = false, result = r) }
        }
    }
}
