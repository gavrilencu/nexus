package com.example.toolkit.ui.recon

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.toolkit.data.model.ReconResult
import com.example.toolkit.data.recon.ReconEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReconUiState(
    val target: String = "",
    val loading: Boolean = false,
    val result: ReconResult? = null
)

class ReconViewModel(
    private val engine: ReconEngine = ReconEngine()
) : ViewModel() {

    private val _state = MutableStateFlow(ReconUiState())
    val state: StateFlow<ReconUiState> = _state.asStateFlow()

    fun onTargetChange(value: String) {
        _state.update { it.copy(target = value) }
    }

    fun analyze() {
        val target = _state.value.target
        if (target.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, result = null) }
            val result = engine.analyze(target)
            _state.update { it.copy(loading = false, result = result) }
        }
    }
}
