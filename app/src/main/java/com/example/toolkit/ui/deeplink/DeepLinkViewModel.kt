package com.example.toolkit.ui.deeplink

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.toolkit.data.apk.InstalledApp
import com.example.toolkit.data.deeplink.DeepLinkEngine
import com.example.toolkit.data.deeplink.DeepLinkEnum
import com.example.toolkit.data.deeplink.IntentHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DeepLinkUiState(
    val manualUri: String = "",
    val installed: List<InstalledApp> = emptyList(),
    val installedLoading: Boolean = false,
    val query: String = "",
    val enumerating: Boolean = false,
    val current: DeepLinkEnum? = null,
    val handlers: List<IntentHandler> = emptyList(),
    val message: String? = null
)

class DeepLinkViewModel(app: Application) : AndroidViewModel(app) {
    private val engine = DeepLinkEngine()
    private val _state = MutableStateFlow(DeepLinkUiState())
    val state: StateFlow<DeepLinkUiState> = _state.asStateFlow()

    init { loadInstalled() }

    fun onManualUri(v: String) = _state.update { it.copy(manualUri = v) }
    fun setQuery(v: String) = _state.update { it.copy(query = v) }

    private fun loadInstalled() {
        viewModelScope.launch {
            _state.update { it.copy(installedLoading = true) }
            val apps = engine.listInstalled(getApplication())
            _state.update { it.copy(installed = apps, installedLoading = false) }
        }
    }

    fun enumerate(pkg: String) {
        viewModelScope.launch {
            _state.update { it.copy(enumerating = true, current = null, message = null) }
            val r = engine.enumerate(getApplication(), pkg)
            _state.update { it.copy(enumerating = false, current = r) }
        }
    }

    fun clearApp() = _state.update { it.copy(current = null) }

    fun resolve(uri: String) {
        val handlers = engine.resolveHandlers(getApplication(), uri)
        _state.update { it.copy(handlers = handlers, message = "${handlers.size} handler(s) for $uri") }
    }

    fun launch(uri: String, pkg: String? = null) {
        val msg = engine.launch(getApplication(), uri, pkg)
        _state.update { it.copy(message = msg) }
    }
}
