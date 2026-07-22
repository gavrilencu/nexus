package com.example.toolkit.ui.fingerprint

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.toolkit.data.fingerprint.FingerprintEngine
import com.example.toolkit.data.fingerprint.FingerprintResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FingerprintUiState(
    val url: String = "",
    val loading: Boolean = false,
    val result: FingerprintResult? = null
)

class FingerprintViewModel(
    private val engine: FingerprintEngine = FingerprintEngine()
) : ViewModel() {

    private val _state = MutableStateFlow(FingerprintUiState())
    val state: StateFlow<FingerprintUiState> = _state.asStateFlow()

    fun onUrl(v: String) = _state.update { it.copy(url = v) }

    fun analyze() {
        val url = _state.value.url
        if (url.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, result = null) }
            val result = engine.analyze(url)
            _state.update { it.copy(loading = false, result = result) }
        }
    }
}
