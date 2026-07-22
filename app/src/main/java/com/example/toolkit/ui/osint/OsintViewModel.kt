package com.example.toolkit.ui.osint

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.toolkit.data.model.OsintProfile
import com.example.toolkit.data.model.OsintResult
import com.example.toolkit.data.osint.OsintEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class OsintFilter { ALL, HITS, MISS, UNKNOWN }

data class OsintUiState(
    val firstName: String = "",
    val lastName: String = "",
    val username: String = "",
    val email: String = "",
    val company: String = "",
    val loading: Boolean = false,
    val progressLabel: String = "",
    val filter: OsintFilter = OsintFilter.ALL,
    val result: OsintResult? = null
)

class OsintViewModel(
    private val engine: OsintEngine = OsintEngine()
) : ViewModel() {

    private val _state = MutableStateFlow(OsintUiState())
    val state: StateFlow<OsintUiState> = _state.asStateFlow()

    fun onFirstName(v: String) = _state.update { it.copy(firstName = v) }
    fun onLastName(v: String) = _state.update { it.copy(lastName = v) }
    fun onUsername(v: String) = _state.update { it.copy(username = v) }
    fun onEmail(v: String) = _state.update { it.copy(email = v) }
    fun onCompany(v: String) = _state.update { it.copy(company = v) }
    fun onFilter(filter: OsintFilter) = _state.update { it.copy(filter = filter) }

    fun filteredProfiles(): List<OsintProfile> {
        val profiles = _state.value.result?.profiles.orEmpty()
        return when (_state.value.filter) {
            OsintFilter.ALL -> profiles
            OsintFilter.HITS -> profiles.filter { it.exists == true }
            OsintFilter.MISS -> profiles.filter { it.exists == false }
            OsintFilter.UNKNOWN -> profiles.filter { it.exists == null }
        }
    }

    fun search() {
        val s = _state.value
        viewModelScope.launch {
            _state.update {
                it.copy(
                    loading = true,
                    result = null,
                    progressLabel = "Probing platforms…",
                    filter = OsintFilter.HITS
                )
            }
            val result = engine.search(
                firstName = s.firstName.trim(),
                lastName = s.lastName.trim(),
                username = s.username.trim(),
                email = s.email.trim(),
                company = s.company.trim()
            )
            val preferredFilter = when {
                result.error != null -> OsintFilter.ALL
                result.profiles.any { it.exists == true } -> OsintFilter.HITS
                else -> OsintFilter.ALL
            }
            _state.update {
                it.copy(
                    loading = false,
                    result = result,
                    progressLabel = "",
                    filter = preferredFilter
                )
            }
        }
    }
}
