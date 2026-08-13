package com.hypershare.service

import com.hypershare.model.ConnectedPeer
import com.hypershare.model.PeerDiscoveryEvent
import com.hypershare.model.PeerMode
import com.hypershare.model.PeerStatus
import com.hypershare.ui.peerlist.PeerListViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class PeerDiscoveryEventTest {

    @Test
    fun testPeerDiscoveredEvent_addsPeerToViewModelState() {
        val viewModel = PeerListViewModel()
        val mockPeer = ConnectedPeer(
            peerId = "HyperShare_DeviceB",
            displayName = "Device B",
            ipAddress = InetAddress.getByName("192.168.1.105"),
            port = 47200,
            status = PeerStatus.DISCOVERED,
            mode = PeerMode.MODE_1_WIFI
        )

        viewModel.handleDiscoveryEvent(PeerDiscoveryEvent.PeerDiscovered(mockPeer))

        val peers = viewModel.uiState.value.peers
        assertEquals(1, peers.size)
        assertEquals("HyperShare_DeviceB", peers[0].peerId)
        assertEquals("192.168.1.105", peers[0].ipAddress?.hostAddress)
        assertEquals(47200, peers[0].port)
    }

    @Test
    fun testPeerLostEvent_removesPeerFromViewModelState() {
        val viewModel = PeerListViewModel()
        val mockPeer1 = ConnectedPeer(peerId = "Peer_1", displayName = "Peer 1")
        val mockPeer2 = ConnectedPeer(peerId = "Peer_2", displayName = "Peer 2")

        viewModel.handleDiscoveryEvent(PeerDiscoveryEvent.PeerDiscovered(mockPeer1))
        viewModel.handleDiscoveryEvent(PeerDiscoveryEvent.PeerDiscovered(mockPeer2))
        assertEquals(2, viewModel.uiState.value.peers.size)

        viewModel.handleDiscoveryEvent(PeerDiscoveryEvent.PeerLost("Peer_1"))

        val remainingPeers = viewModel.uiState.value.peers
        assertEquals(1, remainingPeers.size)
        assertEquals("Peer_2", remainingPeers[0].peerId)
    }
}
