package com.example.toolkit.ui.firebase

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.toolkit.data.apk.InstalledApp
import com.example.toolkit.data.firebase.FirebaseEngine
import com.example.toolkit.data.firebase.FirebaseResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FirebaseUiState(
    val manual: String = "",
    val fromInstalled: Boolean = false,
    val installed: List<InstalledApp> = emptyList(),
    val installedLoading: Boolean = false,
    val query: String = "",
    val loading: Boolean = false,
    val result: FirebaseResult? = null,
    val error: String? = null
)

class FirebaseViewModel(app: Application) : AndroidViewModel(app) {
    private val engine = FirebaseEngine()
    private val _state = MutableStateFlow(FirebaseUiState())
    val state: StateFlow<FirebaseUiState> = _state.asStateFlow()

    fun onManual(v: String) = _state.update { it.copy(manual = v) }
    fun setQuery(v: String) = _state.update { it.copy(query = v) }

    fun selectTab(installed: Boolean) {
        _state.update { it.copy(fromInstalled = installed) }
        if (installed && _state.value.installed.isEmpty()) loadInstalled()
    }

    private fun loadInstalled() {
        viewModelScope.launch {
            _state.update { it.copy(installedLoading = true) }
            val apps = engine.listInstalled(getApplication())
            _state.update { it.copy(installed = apps, installedLoading = false) }
        }
    }

    fun checkManual() {
        val m = _state.value.manual
        if (m.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, result = null, error = null) }
            val r = engine.fromManual(m)
            _state.update { it.copy(loading = false, result = r.takeIf { it.error == null }, error = r.error) }
        }
    }

    fun analyzeFile(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, result = null, error = null) }
            val r = engine.fromFile(getApplication(), uri)
            _state.update { it.copy(loading = false, result = r.takeIf { it.error == null }, error = r.error) }
        }
    }

    fun analyzeInstalled(pkg: String) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, result = null, error = null) }
            val r = engine.fromInstalled(getApplication(), pkg)
            _state.update { it.copy(loading = false, result = r.takeIf { it.error == null }, error = r.error) }
        }
    }

    fun clear() = _state.update { it.copy(result = null, error = null) }
}
