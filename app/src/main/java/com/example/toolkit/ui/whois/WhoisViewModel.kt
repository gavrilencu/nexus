package com.example.toolkit.ui.whois

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.toolkit.data.whois.WhoisEngine
import com.example.toolkit.data.whois.WhoisResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WhoisUiState(
    val query: String = "",
    val loading: Boolean = false,
    val result: WhoisResult? = null
)

class WhoisViewModel(
    private val engine: WhoisEngine = WhoisEngine()
) : ViewModel() {

    private val _state = MutableStateFlow(WhoisUiState())
    val state: StateFlow<WhoisUiState> = _state.asStateFlow()

    fun onQuery(v: String) = _state.update { it.copy(query = v) }

    fun lookup() {
        val q = _state.value.query
        if (q.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, result = null) }
            val result = engine.lookup(q)
            _state.update { it.copy(loading = false, result = result) }
        }
    }
}
