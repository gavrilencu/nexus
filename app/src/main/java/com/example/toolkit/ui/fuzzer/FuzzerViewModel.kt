package com.example.toolkit.ui.fuzzer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.toolkit.data.fuzzer.FuzzFilter
import com.example.toolkit.data.fuzzer.FuzzHit
import com.example.toolkit.data.fuzzer.FuzzerEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FuzzerUiState(
    val url: String = "",
    val customWords: String = "",
    val hideStatus: String = "404",
    val matchStatus: String = "",
    val loading: Boolean = false,
    val scanned: Int = 0,
    val total: Int = 0,
    val hits: List<FuzzHit> = emptyList(),
    val status: String = "Idle",
    val error: String? = null
)

class FuzzerViewModel(private val engine: FuzzerEngine = FuzzerEngine()) : ViewModel() {
    private val _state = MutableStateFlow(FuzzerUiState())
    val state: StateFlow<FuzzerUiState> = _state.asStateFlow()
    private var job: Job? = null

    fun onUrl(v: String) = _state.update { it.copy(url = v) }
    fun onCustomWords(v: String) = _state.update { it.copy(customWords = v) }
    fun onHideStatus(v: String) = _state.update { it.copy(hideStatus = v) }
    fun onMatchStatus(v: String) = _state.update { it.copy(matchStatus = v) }

    fun start() {
        val s = _state.value
        if (s.url.isBlank()) return
        val custom = s.customWords.split(Regex("[\\s,]+")).map { it.trim() }.filter { it.isNotBlank() }
        val wordlist = if (custom.isNotEmpty()) custom else FuzzerEngine.WORDLIST
        val filter = FuzzFilter(
            hideStatus = parseCodes(s.hideStatus),
            matchStatus = parseCodes(s.matchStatus)
        )
        job?.cancel()
        job = viewModelScope.launch {
            _state.update { it.copy(loading = true, scanned = 0, total = 0, hits = emptyList(), status = "Fuzzing…", error = null) }
            engine.fuzz(s.url, wordlist, filter).collect { e ->
                when (e) {
                    is FuzzerEngine.Event.Started -> _state.update { it.copy(total = e.total) }
                    is FuzzerEngine.Event.Progress -> _state.update { it.copy(scanned = e.scanned, total = e.total, hits = e.hits) }
                    is FuzzerEngine.Event.Completed -> _state.update { it.copy(loading = false, hits = e.hits, status = "Done — ${e.hits.size} hits") }
                    is FuzzerEngine.Event.Error -> _state.update { it.copy(loading = false, error = e.message, status = "Error") }
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        _state.update { it.copy(loading = false, status = "Stopped") }
    }

    private fun parseCodes(s: String): Set<Int> =
        s.split(Regex("[\\s,]+")).mapNotNull { it.trim().toIntOrNull() }.toSet()
}
