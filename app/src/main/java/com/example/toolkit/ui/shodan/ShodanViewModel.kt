package com.example.toolkit.ui.shodan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.toolkit.data.shodan.ShodanEngine
import com.example.toolkit.data.shodan.ShodanResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ShodanUiState(
    val ip: String = "",
    val apiKey: String = "",
    val loading: Boolean = false,
    val result: ShodanResult? = null
)

class ShodanViewModel(private val engine: ShodanEngine = ShodanEngine()) : ViewModel() {
    private val _state = MutableStateFlow(ShodanUiState())
    val state: StateFlow<ShodanUiState> = _state.asStateFlow()

    fun onIp(v: String) = _state.update { it.copy(ip = v) }
    fun onApiKey(v: String) = _state.update { it.copy(apiKey = v) }

    fun lookup() {
        val ip = _state.value.ip
        if (ip.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, result = null) }
            val r = engine.lookup(ip, _state.value.apiKey)
            _state.update { it.copy(loading = false, result = r) }
        }
    }
}
