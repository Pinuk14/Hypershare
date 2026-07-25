package com.hypershare.ui.stream

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class StreamUiState(
    val isBuffering: Boolean = false,
    val bytesRead: Long = 0L
)

class StreamPlayerViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(StreamUiState())
    val uiState: StateFlow<StreamUiState> = _uiState.asStateFlow()
}
