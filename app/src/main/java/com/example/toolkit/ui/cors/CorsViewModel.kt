package com.example.toolkit.ui.cors

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.toolkit.data.cors.CorsEngine
import com.example.toolkit.data.cors.CorsResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CorsUiState(
    val url: String = "",
    val loading: Boolean = false,
    val result: CorsResult? = null
)

class CorsViewModel(
    private val engine: CorsEngine = CorsEngine()
) : ViewModel() {

    private val _state = MutableStateFlow(CorsUiState())
    val state: StateFlow<CorsUiState> = _state.asStateFlow()

    fun onUrl(v: String) = _state.update { it.copy(url = v) }

    fun scan() {
        val url = _state.value.url
        if (url.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, result = null) }
            val result = engine.scan(url)
            _state.update { it.copy(loading = false, result = result) }
        }
    }
}
