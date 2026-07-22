package com.example.toolkit.ui.api

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.toolkit.data.api.ApiInspector
import com.example.toolkit.data.model.ApiProbeResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ApiUiState(
    val method: String = "GET",
    val url: String = "",
    val headers: String = "Accept: application/json",
    val body: String = "",
    val loading: Boolean = false,
    val result: ApiProbeResult? = null
)

class ApiViewModel(
    private val inspector: ApiInspector = ApiInspector()
) : ViewModel() {

    private val _state = MutableStateFlow(ApiUiState())
    val state: StateFlow<ApiUiState> = _state.asStateFlow()

    fun onMethod(v: String) = _state.update { it.copy(method = v) }
    fun onUrl(v: String) = _state.update { it.copy(url = v) }
    fun onHeaders(v: String) = _state.update { it.copy(headers = v) }
    fun onBody(v: String) = _state.update { it.copy(body = v) }

    fun send() {
        val s = _state.value
        if (s.url.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, result = null) }
            val result = inspector.probe(s.method, s.url, s.headers, s.body)
            _state.update { it.copy(loading = false, result = result) }
        }
    }
}
