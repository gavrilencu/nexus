package com.example.toolkit.ui.crtsh

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.toolkit.data.crtsh.CrtShEngine
import com.example.toolkit.data.crtsh.CrtShResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CrtShUiState(
    val domain: String = "",
    val loading: Boolean = false,
    val result: CrtShResult? = null
)

class CrtShViewModel(private val engine: CrtShEngine = CrtShEngine()) : ViewModel() {
    private val _state = MutableStateFlow(CrtShUiState())
    val state: StateFlow<CrtShUiState> = _state.asStateFlow()

    fun onDomain(v: String) = _state.update { it.copy(domain = v) }

    fun enumerate() {
        val d = _state.value.domain
        if (d.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, result = null) }
            val r = engine.enumerate(d)
            _state.update { it.copy(loading = false, result = r) }
        }
    }
}
