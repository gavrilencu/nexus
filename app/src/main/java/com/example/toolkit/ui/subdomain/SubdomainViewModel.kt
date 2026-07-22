package com.example.toolkit.ui.subdomain

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.toolkit.data.subdomain.SubdomainEngine
import com.example.toolkit.data.subdomain.SubdomainHit
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SubdomainUiState(
    val domain: String = "",
    val loading: Boolean = false,
    val scanned: Int = 0,
    val total: Int = 0,
    val hits: List<SubdomainHit> = emptyList(),
    val status: String = "Idle",
    val wildcard: Boolean = false,
    val wildcardIps: List<String> = emptyList(),
    val filteredWildcards: Int = 0,
    val error: String? = null
)

class SubdomainViewModel(
    private val engine: SubdomainEngine = SubdomainEngine()
) : ViewModel() {
    private val _state = MutableStateFlow(SubdomainUiState())
    val state: StateFlow<SubdomainUiState> = _state.asStateFlow()
    private var job: Job? = null

    fun onDomain(v: String) = _state.update { it.copy(domain = v) }

    fun start() {
        val domain = _state.value.domain
        if (domain.isBlank()) return
        job?.cancel()
        job = viewModelScope.launch {
            _state.update {
                it.copy(
                    loading = true,
                    scanned = 0,
                    total = SubdomainEngine.WORDLIST.size,
                    hits = emptyList(),
                    status = "Detecting wildcard DNS…",
                    wildcard = false,
                    wildcardIps = emptyList(),
                    filteredWildcards = 0,
                    error = null
                )
            }
            engine.scan(domain).collect { event ->
                when (event) {
                    is SubdomainEngine.Event.Started -> _state.update {
                        it.copy(
                            total = event.total,
                            wildcard = event.wildcard,
                            wildcardIps = event.wildcardIps,
                            status = if (event.wildcard) {
                                "Wildcard DNS detected — filtering fakes…"
                            } else {
                                "Scanning ${event.domain} (no wildcard)"
                            }
                        )
                    }
                    is SubdomainEngine.Event.Progress -> _state.update {
                        it.copy(
                            scanned = event.scanned,
                            total = event.total,
                            hits = if (event.hit != null) {
                                (it.hits + event.hit).distinctBy { h -> h.host }.sortedBy { h -> h.host }
                            } else it.hits,
                            status = event.hit?.let { h -> "FOUND ${h.host}" } ?: it.status
                        )
                    }
                    is SubdomainEngine.Event.Completed -> _state.update {
                        it.copy(
                            loading = false,
                            hits = event.hits,
                            wildcard = event.wildcard,
                            filteredWildcards = event.filteredWildcards,
                            status = buildString {
                                append("Done — ${event.hits.size} real")
                                if (event.filteredWildcards > 0) {
                                    append(" · ${event.filteredWildcards} wildcard fakes dropped")
                                }
                            }
                        )
                    }
                    is SubdomainEngine.Event.Error -> _state.update {
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
