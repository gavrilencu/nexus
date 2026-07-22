package com.example.toolkit.ui.person

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.toolkit.data.person.PersonSearchEngine
import com.example.toolkit.data.person.PersonSearchResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PersonUiState(
    val firstName: String = "",
    val lastName: String = "",
    val phone: String = "",
    val keyword: String = "",
    val username: String = "",
    val city: String = "",
    val country: String = "",
    val loading: Boolean = false,
    val categoryFilter: String = "ALL",
    val result: PersonSearchResult? = null
)

class PersonViewModel(
    private val engine: PersonSearchEngine = PersonSearchEngine()
) : ViewModel() {
    private val _state = MutableStateFlow(PersonUiState())
    val state: StateFlow<PersonUiState> = _state.asStateFlow()

    fun onFirst(v: String) = _state.update { it.copy(firstName = v) }
    fun onLast(v: String) = _state.update { it.copy(lastName = v) }
    fun onPhone(v: String) = _state.update { it.copy(phone = v) }
    fun onKeyword(v: String) = _state.update { it.copy(keyword = v) }
    fun onUsername(v: String) = _state.update { it.copy(username = v) }
    fun onCity(v: String) = _state.update { it.copy(city = v) }
    fun onCountry(v: String) = _state.update { it.copy(country = v) }
    fun onFilter(v: String) = _state.update { it.copy(categoryFilter = v) }

    fun search() {
        val s = _state.value
        viewModelScope.launch {
            _state.update { it.copy(loading = true, result = null) }
            val result = engine.search(
                firstName = s.firstName,
                lastName = s.lastName,
                phone = s.phone,
                keyword = s.keyword,
                username = s.username,
                city = s.city,
                country = s.country
            )
            _state.update { it.copy(loading = false, result = result, categoryFilter = "ALL") }
        }
    }
}
