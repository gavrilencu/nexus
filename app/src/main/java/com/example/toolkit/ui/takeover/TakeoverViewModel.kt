package com.example.toolkit.ui.takeover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.toolkit.data.takeover.TakeoverEngine
import com.example.toolkit.data.takeover.TakeoverResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TakeoverUiState(
    val host: String = "",
    val loading: Boolean = false,
    val result: TakeoverResult? = null
)

class TakeoverViewModel(private val engine: TakeoverEngine = TakeoverEngine()) : ViewModel() {
    private val _state = MutableStateFlow(TakeoverUiState())
    val state: StateFlow<TakeoverUiState> = _state.asStateFlow()

    fun onHost(v: String) = _state.update { it.copy(host = v) }

    fun check() {
        val host = _state.value.host
        if (host.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, result = null) }
            val r = engine.check(host)
            _state.update { it.copy(loading = false, result = r) }
        }
    }
}
