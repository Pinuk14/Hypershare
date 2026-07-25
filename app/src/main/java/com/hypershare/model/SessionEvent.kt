package com.hypershare.model

sealed class SessionEvent {
    data class PeerJoined(val peer: ConnectedPeer) : SessionEvent()
    data class PeerLost(val peerId: String) : SessionEvent()
    data class ModeChanged(val newMode: PeerMode) : SessionEvent()
    data class MessageReceived(val senderPeerId: String, val text: String, val timestamp: Long) : SessionEvent()
    data class TransferProgressUpdated(val job: TransferJob) : SessionEvent()
}
