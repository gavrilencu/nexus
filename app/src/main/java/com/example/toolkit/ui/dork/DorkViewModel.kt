package com.example.toolkit.ui.dork

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.toolkit.data.dork.Dork
import com.example.toolkit.data.dork.DorkEngine
import com.example.toolkit.data.dork.GithubSearchResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DorkUiState(
    val target: String = "",
    val token: String = "",
    val googleDorks: List<Dork> = emptyList(),
    val githubDorks: List<Dork> = emptyList(),
    val searching: Boolean = false,
    val githubResult: GithubSearchResult? = null
)

class DorkViewModel(private val engine: DorkEngine = DorkEngine()) : ViewModel() {
    private val _state = MutableStateFlow(DorkUiState())
    val state: StateFlow<DorkUiState> = _state.asStateFlow()

    fun onTarget(v: String) = _state.update { it.copy(target = v) }
    fun onToken(v: String) = _state.update { it.copy(token = v) }

    fun build() {
        val t = _state.value.target
        if (t.isBlank()) return
        _state.update {
            it.copy(googleDorks = engine.googleDorks(t), githubDorks = engine.githubDorks(t), githubResult = null)
        }
    }

    fun searchGithub(query: String) {
        val s = _state.value
        if (s.target.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(searching = true, githubResult = null) }
            val r = engine.searchGithub(s.target, query, s.token)
            _state.update { it.copy(searching = false, githubResult = r) }
        }
    }
}
