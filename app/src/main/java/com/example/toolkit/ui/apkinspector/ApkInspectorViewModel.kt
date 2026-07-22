package com.example.toolkit.ui.apkinspector

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.toolkit.data.apk.ApkInspectorEngine
import com.example.toolkit.data.apk.ApkReport
import com.example.toolkit.data.apk.EntryContent
import com.example.toolkit.data.apk.InstalledApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class InspectorTab { FILE, INSTALLED }

data class ApkInspectorState(
    val tab: InspectorTab = InspectorTab.FILE,
    val analyzing: Boolean = false,
    val report: ApkReport? = null,
    val error: String? = null,
    val installedLoading: Boolean = false,
    val installed: List<InstalledApp> = emptyList(),
    val installedQuery: String = "",
    val extracting: Boolean = false,
    val extractMessage: String? = null,
    // file browser / viewer
    val browsing: Boolean = false,
    val browsePath: String = "",
    val viewerLoading: Boolean = false,
    val viewer: EntryContent? = null
)

class ApkInspectorViewModel(app: Application) : AndroidViewModel(app) {

    private val engine = ApkInspectorEngine()
    private val _state = MutableStateFlow(ApkInspectorState())
    val state: StateFlow<ApkInspectorState> = _state.asStateFlow()

    fun selectTab(tab: InspectorTab) {
        _state.update { it.copy(tab = tab) }
        if (tab == InspectorTab.INSTALLED && _state.value.installed.isEmpty()) loadInstalled()
    }

    fun analyzeFile(uri: Uri) {
        resetForAnalyze()
        viewModelScope.launch {
            runCatching { engine.analyzeFile(getApplication(), uri) }
                .onSuccess { r -> _state.update { it.copy(analyzing = false, report = r) } }
                .onFailure { e -> _state.update { it.copy(analyzing = false, error = e.message ?: "Analysis failed") } }
        }
    }

    fun analyzeInstalled(packageName: String) {
        resetForAnalyze()
        viewModelScope.launch {
            runCatching { engine.analyzeInstalled(getApplication(), packageName) }
                .onSuccess { r -> _state.update { it.copy(analyzing = false, report = r) } }
                .onFailure { e -> _state.update { it.copy(analyzing = false, error = e.message ?: "Analysis failed") } }
        }
    }

    private fun resetForAnalyze() = _state.update {
        it.copy(
            analyzing = true, report = null, error = null, extractMessage = null,
            browsing = false, browsePath = "", viewer = null, viewerLoading = false
        )
    }

    fun loadInstalled() {
        _state.update { it.copy(installedLoading = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val apps = runCatching { engine.listInstalled(getApplication()) }.getOrDefault(emptyList())
            _state.update { it.copy(installedLoading = false, installed = apps) }
        }
    }

    fun setInstalledQuery(q: String) = _state.update { it.copy(installedQuery = q) }

    fun clearReport() = _state.update {
        it.copy(report = null, error = null, extractMessage = null, browsing = false, browsePath = "", viewer = null)
    }

    // ---- file browser -----------------------------------------------------

    fun openBrowser() = _state.update { it.copy(browsing = true, browsePath = "") }
    fun closeBrowser() = _state.update { it.copy(browsing = false, browsePath = "", viewer = null) }
    fun navigateTo(path: String) = _state.update { it.copy(browsePath = path) }

    fun navigateUp() = _state.update {
        val parent = it.browsePath.trimEnd('/').substringBeforeLast('/', "")
        it.copy(browsePath = if (parent.isEmpty()) "" else "$parent/")
    }

    fun openEntry(path: String) {
        val working = _state.value.report?.workingFile ?: return
        _state.update { it.copy(viewerLoading = true, viewer = null) }
        viewModelScope.launch {
            val content = runCatching { engine.readEntry(working, path) }.getOrNull()
            _state.update { it.copy(viewerLoading = false, viewer = content) }
        }
    }

    fun closeViewer() = _state.update { it.copy(viewer = null) }

    // ---- extraction to a chosen folder ------------------------------------

    fun extractTo(treeUri: Uri) {
        val report = _state.value.report ?: return
        val working = report.workingFile
        if (working == null) {
            _state.update { it.copy(extractMessage = "This source can't be extracted.") }
            return
        }
        _state.update { it.copy(extracting = true, extractMessage = null) }
        viewModelScope.launch {
            runCatching { engine.extractToTree(getApplication(), working, treeUri, report.source) }
                .onSuccess { msg -> _state.update { it.copy(extracting = false, extractMessage = msg) } }
                .onFailure { e -> _state.update { it.copy(extracting = false, extractMessage = "Extract failed: ${e.message}") } }
        }
    }
}
