package com.example.toolkit.ui.exposed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.toolkit.data.exposed.ExposedEngine
import com.example.toolkit.data.exposed.ExposedResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExposedUiState(
    val url: String = "",
    val loading: Boolean = false,
    val result: ExposedResult? = null
)

class ExposedViewModel(private val engine: ExposedEngine = ExposedEngine()) : ViewModel() {
    private val _state = MutableStateFlow(ExposedUiState())
    val state: StateFlow<ExposedUiState> = _state.asStateFlow()

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
