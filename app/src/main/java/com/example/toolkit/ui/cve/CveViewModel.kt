package com.example.toolkit.ui.cve

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.toolkit.data.cve.CveLookupEngine
import com.example.toolkit.data.cve.CveSearchResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CveUiState(
    val query: String = "",
    val loading: Boolean = false,
    val result: CveSearchResult? = null
)

class CveViewModel(
    private val engine: CveLookupEngine = CveLookupEngine()
) : ViewModel() {
    private val _state = MutableStateFlow(CveUiState())
    val state: StateFlow<CveUiState> = _state.asStateFlow()

    fun onQuery(v: String) = _state.update { it.copy(query = v) }

    fun search() {
        val q = _state.value.query
        if (q.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, result = null) }
            val result = engine.search(q)
            _state.update { it.copy(loading = false, result = result) }
        }
    }
}
