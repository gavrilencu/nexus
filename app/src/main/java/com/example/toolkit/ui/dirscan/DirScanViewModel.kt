package com.example.toolkit.ui.dirscan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.toolkit.data.dirscan.DirHit
import com.example.toolkit.data.dirscan.DirScanEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DirScanUiState(
    val url: String = "",
    val loading: Boolean = false,
    val scanned: Int = 0,
    val total: Int = 0,
    val hits: List<DirHit> = emptyList(),
    val status: String = "Idle",
    val notice: String? = null,
    val error: String? = null
)

class DirScanViewModel(
    private val engine: DirScanEngine = DirScanEngine()
) : ViewModel() {

    private val _state = MutableStateFlow(DirScanUiState())
    val state: StateFlow<DirScanUiState> = _state.asStateFlow()
    private var job: Job? = null

    fun onUrl(v: String) = _state.update { it.copy(url = v) }

    fun start() {
        val url = _state.value.url
        if (url.isBlank()) return
        job?.cancel()
        job = viewModelScope.launch {
            _state.update {
                it.copy(loading = true, scanned = 0, total = 0, hits = emptyList(),
                    status = "Scanning…", notice = null, error = null)
            }
            engine.scan(url).collect { event ->
                when (event) {
                    is DirScanEngine.ScanEvent.Started -> _state.update {
                        it.copy(total = event.total, notice = event.softNotice, status = "Scanning ${event.target}")
                    }
                    is DirScanEngine.ScanEvent.Progress -> _state.update {
                        it.copy(scanned = event.scanned, total = event.total, hits = event.hits)
                    }
                    is DirScanEngine.ScanEvent.Completed -> _state.update {
                        it.copy(loading = false, hits = event.hits, status = "Done — ${event.hits.size} found")
                    }
                    is DirScanEngine.ScanEvent.Error -> _state.update {
                        it.copy(loading = false, error = event.message, status = "Error")
                    }
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        _state.update { it.copy(loading = false, status = "Stopped") }
    }
}
