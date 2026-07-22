package com.example.toolkit.ui.wayback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.toolkit.data.wayback.WaybackEngine
import com.example.toolkit.data.wayback.WaybackResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WaybackUiState(
    val domain: String = "",
    val includeSubs: Boolean = true,
    val onlyInteresting: Boolean = false,
    val loading: Boolean = false,
    val result: WaybackResult? = null
)

class WaybackViewModel(private val engine: WaybackEngine = WaybackEngine()) : ViewModel() {
    private val _state = MutableStateFlow(WaybackUiState())
    val state: StateFlow<WaybackUiState> = _state.asStateFlow()

    fun onDomain(v: String) = _state.update { it.copy(domain = v) }
    fun toggleSubs() = _state.update { it.copy(includeSubs = !it.includeSubs) }
    fun toggleOnlyInteresting() = _state.update { it.copy(onlyInteresting = !it.onlyInteresting) }

    fun fetch() {
        val d = _state.value.domain
        if (d.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, result = null) }
            val r = engine.fetch(d, _state.value.includeSubs)
            _state.update { it.copy(loading = false, result = r) }
        }
    }
}
