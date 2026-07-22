package com.example.toolkit.ui.apkaudit

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.toolkit.data.apk.InstalledApp
import com.example.toolkit.data.apkaudit.ApkSecurityEngine
import com.example.toolkit.data.apkaudit.AuditResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ApkAuditUiState(
    val fromInstalled: Boolean = false,
    val installed: List<InstalledApp> = emptyList(),
    val installedLoading: Boolean = false,
    val query: String = "",
    val analyzing: Boolean = false,
    val result: AuditResult? = null,
    val error: String? = null
)

class ApkAuditViewModel(app: Application) : AndroidViewModel(app) {
    private val engine = ApkSecurityEngine()
    private val _state = MutableStateFlow(ApkAuditUiState())
    val state: StateFlow<ApkAuditUiState> = _state.asStateFlow()

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
            val r = engine.auditFile(getApplication(), uri)
            _state.update { it.copy(analyzing = false, result = r.takeIf { rr -> rr.error == null }, error = r.error) }
        }
    }

    fun analyzeInstalled(pkg: String) {
        viewModelScope.launch {
            _state.update { it.copy(analyzing = true, result = null, error = null) }
            val r = engine.auditInstalled(getApplication(), pkg)
            _state.update { it.copy(analyzing = false, result = r.takeIf { rr -> rr.error == null }, error = r.error) }
        }
    }

    fun clear() = _state.update { it.copy(result = null, error = null) }
}
