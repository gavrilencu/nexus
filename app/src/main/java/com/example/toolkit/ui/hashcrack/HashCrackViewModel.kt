package com.example.toolkit.ui.hashcrack

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.toolkit.data.hashcrack.CrackResult
import com.example.toolkit.data.hashcrack.HashCrackEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HashCrackUiState(
    val hash: String = "",
    val extraWords: String = "",
    val loading: Boolean = false,
    val result: CrackResult? = null
)

class HashCrackViewModel(
    private val engine: HashCrackEngine = HashCrackEngine()
) : ViewModel() {

    private val _state = MutableStateFlow(HashCrackUiState())
    val state: StateFlow<HashCrackUiState> = _state.asStateFlow()

    fun onHash(v: String) = _state.update { it.copy(hash = v) }
    fun onExtraWords(v: String) = _state.update { it.copy(extraWords = v) }

    fun crack() {
        val hash = _state.value.hash
        if (hash.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, result = null) }
            val result = engine.crack(hash, _state.value.extraWords)
            _state.update { it.copy(loading = false, result = result) }
        }
    }
}
