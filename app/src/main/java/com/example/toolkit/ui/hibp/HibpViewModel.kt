package com.example.toolkit.ui.hibp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.toolkit.data.hibp.HibpBreach
import com.example.toolkit.data.hibp.HibpEmailResult
import com.example.toolkit.data.hibp.HibpEngine
import com.example.toolkit.data.hibp.HibpPasswordResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HibpUiState(
    val tab: String = "EMAIL",
    val email: String = "",
    val password: String = "",
    val apiKey: String = "",
    val showApiKey: Boolean = false,
    val loading: Boolean = false,
    val emailResult: HibpEmailResult? = null,
    val passwordResult: HibpPasswordResult? = null,
    val catalog: List<HibpBreach> = emptyList(),
    val catalogError: String? = null
)

class HibpViewModel(app: Application) : AndroidViewModel(app) {
    private val engine = HibpEngine(app)
    private val _state = MutableStateFlow(HibpUiState(apiKey = engine.getApiKey()))
    val state: StateFlow<HibpUiState> = _state.asStateFlow()

    fun onTab(v: String) = _state.update { it.copy(tab = v) }
    fun onEmail(v: String) = _state.update { it.copy(email = v) }
    fun onPassword(v: String) = _state.update { it.copy(password = v) }
    fun onApiKey(v: String) = _state.update { it.copy(apiKey = v) }
    fun toggleShowKey() = _state.update { it.copy(showApiKey = !it.showApiKey) }

    fun saveKey() {
        engine.saveApiKey(_state.value.apiKey)
        _state.update { it.copy(apiKey = engine.getApiKey()) }
    }

    fun checkEmail() {
        val email = _state.value.email
        viewModelScope.launch {
            _state.update { it.copy(loading = true, emailResult = null) }
            engine.saveApiKey(_state.value.apiKey)
            val result = engine.checkEmail(email)
            _state.update { it.copy(loading = false, emailResult = result) }
        }
    }

    fun checkPassword() {
        val password = _state.value.password
        viewModelScope.launch {
            _state.update { it.copy(loading = true, passwordResult = null) }
            val result = engine.checkPassword(password)
            _state.update { it.copy(loading = false, passwordResult = result) }
        }
    }

    fun loadCatalog() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, catalogError = null) }
            val (list, err) = engine.listBreaches()
            _state.update {
                it.copy(loading = false, catalog = list.take(40), catalogError = err)
            }
        }
    }
}
