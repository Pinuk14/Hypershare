package com.hypershare.ui.peerlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hypershare.model.ConnectedPeer
import com.hypershare.model.PeerDiscoveryEvent
import com.hypershare.model.PeerMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PeerListUiState(
    val peers: List<ConnectedPeer> = emptyList(),
    val currentMode: PeerMode = PeerMode.MODE_1_WIFI,
    val showDemoPeers: Boolean = true,
    val isRefreshing: Boolean = false
)

class PeerListViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(PeerListUiState())
    val uiState: StateFlow<PeerListUiState> = _uiState.asStateFlow()

    fun setMode(mode: PeerMode) {
        _uiState.value = _uiState.value.copy(currentMode = mode)
    }

    fun toggleMode() {
        val nextMode = if (_uiState.value.currentMode == PeerMode.MODE_1_WIFI) {
            PeerMode.MODE_2_MESH
        } else {
            PeerMode.MODE_1_WIFI
        }
        _uiState.value = _uiState.value.copy(currentMode = nextMode)
    }

    fun toggleShowDemoPeers() {
        _uiState.value = _uiState.value.copy(showDemoPeers = !_uiState.value.showDemoPeers)
    }

    fun refreshPeers(onTriggerRefresh: (() -> Unit)? = null) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            onTriggerRefresh?.invoke()
            delay(1200L)
            _uiState.value = _uiState.value.copy(isRefreshing = false)
        }
    }

    fun handleDiscoveryEvent(event: PeerDiscoveryEvent) {
        when (event) {
            is PeerDiscoveryEvent.PeerDiscovered -> {
                val newPeer = event.peer
                val newIp = newPeer.ipAddress?.hostAddress
                // Remove any existing peer with same peerId OR same IP (avoids mDNS + UDP duplicates)
                val currentPeers = _uiState.value.peers.filterNot { existing ->
                    existing.peerId == newPeer.peerId ||
                    (newIp != null && existing.ipAddress?.hostAddress == newIp)
                }.toMutableList()
                currentPeers.add(newPeer)
                _uiState.value = _uiState.value.copy(peers = currentPeers)
            }
            is PeerDiscoveryEvent.PeerLost -> {
                val currentPeers = _uiState.value.peers.filterNot { it.peerId == event.peerId }
                _uiState.value = _uiState.value.copy(peers = currentPeers)
            }
        }
    }
}
