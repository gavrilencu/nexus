package com.example.toolkit.ui.webvuln

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.toolkit.data.webvuln.WebVulnEngine
import com.example.toolkit.data.webvuln.WebVulnResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WebVulnUiState(
    val url: String = "",
    val loading: Boolean = false,
    val result: WebVulnResult? = null
)

class WebVulnViewModel(private val engine: WebVulnEngine = WebVulnEngine()) : ViewModel() {
    private val _state = MutableStateFlow(WebVulnUiState())
    val state: StateFlow<WebVulnUiState> = _state.asStateFlow()

    fun onUrl(v: String) = _state.update { it.copy(url = v) }

    fun scan() {
        val url = _state.value.url
        if (url.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, result = null) }
            val r = engine.scan(url)
            _state.update { it.copy(loading = false, result = r) }
        }
    }
}
