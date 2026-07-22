package com.example.toolkit.ui.graphql

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.toolkit.data.graphql.GraphqlEngine
import com.example.toolkit.data.graphql.GraphqlResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GraphqlUiState(
    val url: String = "",
    val loading: Boolean = false,
    val result: GraphqlResult? = null
)

class GraphqlViewModel(private val engine: GraphqlEngine = GraphqlEngine()) : ViewModel() {
    private val _state = MutableStateFlow(GraphqlUiState())
    val state: StateFlow<GraphqlUiState> = _state.asStateFlow()

    fun onUrl(v: String) = _state.update { it.copy(url = v) }

    fun inspect() {
        val url = _state.value.url
        if (url.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, result = null) }
            val r = engine.inspect(url)
            _state.update { it.copy(loading = false, result = r) }
        }
    }
}
