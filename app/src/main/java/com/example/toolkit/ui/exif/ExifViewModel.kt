package com.example.toolkit.ui.exif

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.toolkit.data.exif.ExifEngine
import com.example.toolkit.data.exif.ExifResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExifUiState(
    val loading: Boolean = false,
    val result: ExifResult? = null
)

class ExifViewModel(app: Application) : AndroidViewModel(app) {
    private val engine = ExifEngine()
    private val _state = MutableStateFlow(ExifUiState())
    val state: StateFlow<ExifUiState> = _state.asStateFlow()

    fun analyze(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, result = null) }
            val r = engine.extract(getApplication(), uri)
            _state.update { it.copy(loading = false, result = r) }
        }
    }
}
