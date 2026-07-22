package com.example.toolkit.ui.dexscan

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.toolkit.data.apk.InstalledApp
import com.example.toolkit.data.dexscan.DexScanEngine
import com.example.toolkit.data.dexscan.DexScanResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DexScanUiState(
    val fromInstalled: Boolean = false,
    val installed: List<InstalledApp> = emptyList(),
    val installedLoading: Boolean = false,
    val query: String = "",
    val analyzing: Boolean = false,
    val result: DexScanResult? = null,
    val error: String? = null
)

class DexScanViewModel(app: Application) : AndroidViewModel(app) {
    private val engine = DexScanEngine()
    private val _state = MutableStateFlow(DexScanUiState())
    val state: StateFlow<DexScanUiState> = _state.asStateFlow()

    fun selectTab(installed: Boolean) {
        _state.update { it.copy(fromInstalled = installed) }
        if (installed && _state.value.installed.isEmpty()) loadInstalled()
    }

    fun setQuery(v: String) = _state.update { it.copy(query = v) }

    private fun loadInstalled() {
        viewModelScope.launch {
            _state.update { it.copy(installedLoading = true) }
            val apps = engine.listInstalled(getApplication())
            _state.update { it.copy(installed = apps, installedLoading = false) }
        }
    }

    fun analyzeFile(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(analyzing = true, result = null, error = null) }
            val r = engine.scanFile(getApplication(), uri)
            _state.update { it.copy(analyzing = false, result = r.takeIf { it.error == null }, error = r.error) }
        }
    }

    fun analyzeInstalled(pkg: String) {
        viewModelScope.launch {
            _state.update { it.copy(analyzing = true, result = null, error = null) }
            val r = engine.scanInstalled(getApplication(), pkg)
            _state.update { it.copy(analyzing = false, result = r.takeIf { it.error == null }, error = r.error) }
        }
    }

    fun clear() = _state.update { it.copy(result = null, error = null) }
}
