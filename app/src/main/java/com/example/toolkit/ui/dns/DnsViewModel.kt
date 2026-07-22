package com.example.toolkit.ui.dns

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.toolkit.data.dns.DnsDigEngine
import com.example.toolkit.data.dns.DnsResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DnsUiState(
    val host: String = "",
    val loading: Boolean = false,
    val result: DnsResult? = null
)

class DnsViewModel(
    private val engine: DnsDigEngine = DnsDigEngine()
) : ViewModel() {
    private val _state = MutableStateFlow(DnsUiState())
    val state: StateFlow<DnsUiState> = _state.asStateFlow()

    fun onHost(v: String) = _state.update { it.copy(host = v) }

    fun dig() {
        val host = _state.value.host
        if (host.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, result = null) }
            val result = engine.dig(host)
            _state.update { it.copy(loading = false, result = result) }
        }
    }
}
