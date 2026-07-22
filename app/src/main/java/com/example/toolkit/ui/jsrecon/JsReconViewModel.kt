package com.example.toolkit.ui.jsrecon

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.toolkit.data.jsrecon.JsReconEngine
import com.example.toolkit.data.jsrecon.JsReconResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class JsReconUiState(
    val url: String = "",
    val loading: Boolean = false,
    val result: JsReconResult? = null
)

class JsReconViewModel(private val engine: JsReconEngine = JsReconEngine()) : ViewModel() {
    private val _state = MutableStateFlow(JsReconUiState())
    val state: StateFlow<JsReconUiState> = _state.asStateFlow()

    fun onUrl(v: String) = _state.update { it.copy(url = v) }

    fun analyze() {
        val url = _state.value.url
        if (url.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, result = null) }
            val r = engine.analyze(url)
            _state.update { it.copy(loading = false, result = r) }
        }
    }
}
