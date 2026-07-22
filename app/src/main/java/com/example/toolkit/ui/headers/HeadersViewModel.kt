package com.example.toolkit.ui.headers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.toolkit.data.headers.HeadersEngine
import com.example.toolkit.data.headers.HeadersResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HeadersUiState(
    val url: String = "",
    val loading: Boolean = false,
    val result: HeadersResult? = null
)

class HeadersViewModel(private val engine: HeadersEngine = HeadersEngine()) : ViewModel() {
    private val _state = MutableStateFlow(HeadersUiState())
    val state: StateFlow<HeadersUiState> = _state.asStateFlow()

    fun onUrl(v: String) = _state.update { it.copy(url = v) }

    fun grade() {
        val url = _state.value.url
        if (url.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, result = null) }
            val r = engine.grade(url)
            _state.update { it.copy(loading = false, result = r) }
        }
    }
}
