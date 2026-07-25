package com.hypershare.application

import com.hypershare.model.ConnectedPeer
import com.hypershare.model.PeerMode
import com.hypershare.model.SessionEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class SessionManager {

    private val _connectedPeers = MutableStateFlow<List<ConnectedPeer>>(emptyList())
    val connectedPeers: StateFlow<List<ConnectedPeer>> = _connectedPeers.asStateFlow()

    private val _currentMode = MutableStateFlow(PeerMode.MODE_1_WIFI)
    val currentMode: StateFlow<PeerMode> = _currentMode.asStateFlow()

    private val _sessionEvents = MutableSharedFlow<SessionEvent>()
    val sessionEvents: SharedFlow<SessionEvent> = _sessionEvents.asSharedFlow()

    fun addDiscoveredPeer(peer: ConnectedPeer) {
        val currentList = _connectedPeers.value.toMutableList()
        val index = currentList.indexOfFirst { it.peerId == peer.peerId }
        if (index >= 0) {
            currentList[index] = peer
        } else {
            currentList.add(peer)
            _sessionEvents.tryEmit(SessionEvent.PeerJoined(peer))
        }
        _connectedPeers.value = currentList
    }

    fun removePeer(peerId: String) {
        _connectedPeers.value = _connectedPeers.value.filterNot { it.peerId == peerId }
        _sessionEvents.tryEmit(SessionEvent.PeerLost(peerId))
    }

    fun updateMode(newMode: PeerMode) {
        _currentMode.value = newMode
        _sessionEvents.tryEmit(SessionEvent.ModeChanged(newMode))
    }
}
