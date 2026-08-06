package com.hypershare.ui.home

import androidx.lifecycle.ViewModel
import com.hypershare.model.PeerMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class HomeUiState(
    val currentMode: PeerMode = PeerMode.MODE_1_WIFI,
    val shareId: String = "HYPERSHARE-ID-8X92"
)

class HomeViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    fun selectMode(mode: PeerMode) {
        _uiState.value = _uiState.value.copy(currentMode = mode)
    }
}
