package com.hypershare.model

sealed class PeerDiscoveryEvent {
    data class PeerDiscovered(val peer: ConnectedPeer) : PeerDiscoveryEvent()
    data class PeerLost(val peerId: String) : PeerDiscoveryEvent()
}
