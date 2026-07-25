package com.hypershare.ui.peerlist

import androidx.lifecycle.ViewModel
import com.hypershare.model.ConnectedPeer
import com.hypershare.model.PeerMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PeerListUiState(
    val peers: List<ConnectedPeer> = emptyList(),
    val currentMode: PeerMode = PeerMode.MODE_1_WIFI,
    val isScanning: Boolean = false
)

class PeerListViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(PeerListUiState())
    val uiState: StateFlow<PeerListUiState> = _uiState.asStateFlow()

    fun toggleMode() {
        val nextMode = if (_uiState.value.currentMode == PeerMode.MODE_1_WIFI) {
            PeerMode.MODE_2_MESH
        } else {
            PeerMode.MODE_1_WIFI
        }
        _uiState.value = _uiState.value.copy(currentMode = nextMode)
    }
}
