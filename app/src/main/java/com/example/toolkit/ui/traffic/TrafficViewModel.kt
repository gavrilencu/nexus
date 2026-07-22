package com.example.toolkit.ui.traffic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.toolkit.data.model.TrafficEntry
import com.example.toolkit.data.traffic.TrafficMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TrafficUiState(
    val method: String = "GET",
    val url: String = "",
    val headers: String = "",
    val body: String = "",
    val loading: Boolean = false,
    val entries: List<TrafficEntry> = emptyList(),
    val selected: TrafficEntry? = null
)

class TrafficViewModel(
    private val monitor: TrafficMonitor = TrafficMonitor()
) : ViewModel() {

    private val _state = MutableStateFlow(TrafficUiState())
    val state: StateFlow<TrafficUiState> = _state.asStateFlow()

    fun onMethodChange(value: String) = _state.update { it.copy(method = value) }
    fun onUrlChange(value: String) = _state.update { it.copy(url = value) }
    fun onHeadersChange(value: String) = _state.update { it.copy(headers = value) }
    fun onBodyChange(value: String) = _state.update { it.copy(body = value) }
    fun select(entry: TrafficEntry?) = _state.update { it.copy(selected = entry) }
    fun clear() = _state.update { it.copy(entries = emptyList(), selected = null) }

    fun capture() {
        val s = _state.value
        if (s.url.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val headers = s.headers.lines()
                .map { it.trim() }
                .filter { it.contains(":") }
                .associate {
                    val i = it.indexOf(':')
                    it.substring(0, i).trim() to it.substring(i + 1).trim()
                }
            val entry = monitor.captureRequest(s.method, s.url, headers, s.body.ifBlank { null })
            _state.update {
                it.copy(
                    loading = false,
                    entries = listOf(entry) + it.entries,
                    selected = entry
                )
            }
        }
    }
}
