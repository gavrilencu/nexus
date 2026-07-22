package com.example.toolkit.ui.ports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.toolkit.data.model.PortResult
import com.example.toolkit.data.ports.PortScanner
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PortScanUiState(
    val host: String = "",
    val loading: Boolean = false,
    val scanned: Int = 0,
    val total: Int = 0,
    val openPorts: List<PortResult> = emptyList(),
    val status: String = "Idle",
    val error: String? = null
)

class PortScanViewModel(
    private val scanner: PortScanner = PortScanner()
) : ViewModel() {

    private val _state = MutableStateFlow(PortScanUiState())
    val state: StateFlow<PortScanUiState> = _state.asStateFlow()
    private var scanJob: Job? = null

    fun onHostChange(value: String) {
        _state.update { it.copy(host = value) }
    }

    fun startScan() {
        val host = _state.value.host
        if (host.isBlank()) return
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    loading = true,
                    scanned = 0,
                    total = PortScanner.COMMON_PORTS.size,
                    openPorts = emptyList(),
                    status = "Scanning…",
                    error = null
                )
            }
            scanner.scan(host).collect { event ->
                when (event) {
                    is PortScanner.ScanEvent.Started -> _state.update {
                        it.copy(total = event.total, status = "Scanning ${event.target}")
                    }
                    is PortScanner.ScanEvent.Progress -> _state.update {
                        it.copy(
                            scanned = event.scanned,
                            total = event.total,
                            openPorts = event.openPorts,
                            status = if (event.lastResult.open) {
                                "OPEN ${event.lastResult.port}/${event.lastResult.service}"
                            } else it.status
                        )
                    }
                    is PortScanner.ScanEvent.Completed -> _state.update {
                        it.copy(
                            loading = false,
                            openPorts = event.openPorts,
                            status = "Done — ${event.openPorts.size} open"
                        )
                    }
                    is PortScanner.ScanEvent.Error -> _state.update {
                        it.copy(loading = false, error = event.message, status = "Error")
                    }
                }
            }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        _state.update { it.copy(loading = false, status = "Stopped") }
    }
}
