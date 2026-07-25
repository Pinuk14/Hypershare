package com.hypershare.model

import java.net.InetAddress

enum class PeerMode {
    MODE_1_WIFI,
    MODE_2_MESH
}

enum class PeerStatus {
    DISCOVERED,
    CONNECTING,
    CONNECTED,
    RELAYING,
    LOST
}

data class ConnectedPeer(
    val peerId: String,
    val displayName: String,
    val ipAddress: InetAddress? = null,
    val port: Int = 47200,
    val mode: PeerMode = PeerMode.MODE_1_WIFI,
    val status: PeerStatus = PeerStatus.DISCOVERED,
    val hopCount: Int = 1,
    val viaRelayPeerId: String? = null,
    val signalStrengthDbm: Int = -60,
    val lastSeenTimestamp: Long = System.currentTimeMillis()
)
