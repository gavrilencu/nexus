package com.example.toolkit.ui.httpmethods

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.toolkit.data.httpmethods.HttpMethodsEngine
import com.example.toolkit.data.httpmethods.HttpMethodsResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HttpMethodsUiState(
    val url: String = "",
    val loading: Boolean = false,
    val result: HttpMethodsResult? = null
)

class HttpMethodsViewModel(
    private val engine: HttpMethodsEngine = HttpMethodsEngine()
) : ViewModel() {

    private val _state = MutableStateFlow(HttpMethodsUiState())
    val state: StateFlow<HttpMethodsUiState> = _state.asStateFlow()

    fun onUrl(v: String) = _state.update { it.copy(url = v) }

    fun test() {
        val url = _state.value.url
        if (url.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, result = null) }
            val result = engine.test(url)
            _state.update { it.copy(loading = false, result = result) }
        }
    }
}
