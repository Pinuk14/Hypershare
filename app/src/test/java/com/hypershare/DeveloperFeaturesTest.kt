package com.hypershare

import com.hypershare.application.DeveloperSettingsManager
import com.hypershare.model.PacketType
import com.hypershare.protocol.PacketBuilder
import com.hypershare.routing.ForwardDecision
import com.hypershare.routing.HopManager
import com.hypershare.routing.RoutingTable
import com.hypershare.security.PeerKeyStore
import com.hypershare.service.LanSocketManager
import com.hypershare.ui.chat.ChatMessageItem
import com.hypershare.ui.chat.MessageStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.nio.charset.StandardCharsets

class DeveloperFeaturesTest {

    @Test
    fun testDeveloperModeFlowDefaultsAndObservation() {
        // Given initial state
        val devFlow = DeveloperSettingsManager.isDeveloperMode
        assertFalse(devFlow.value)
    }

    @Test
    fun testForwardedPacketsMetricTracking() {
        val lanSocketMgr = LanSocketManager.getInstance()
        lanSocketMgr.resetForwardedPacketsCount()
        assertEquals(0, lanSocketMgr.forwardedPacketsCount.value)
    }

    @Test
    fun testPeerKeyStorePurgeOnContactDeletion() {
        val keyStore = PeerKeyStore.instance
        val peerId = "peer-victim-test"
        val fakeKey = "dummy-public-key".toByteArray(StandardCharsets.UTF_8)

        keyStore.storePeerKey(peerId, fakeKey)
        assertTrue(keyStore.hasPeerKey(peerId))

        // On contact delete, key is removed
        keyStore.removePeerKey(peerId)
        assertFalse(keyStore.hasPeerKey(peerId))
        assertNull(keyStore.getPeerKey(peerId))
    }

    @Test
    fun testMultiMessageSelectionAndTextCopyConcatenation() {
        val messages = listOf(
            ChatMessageItem("m1", "local", "Message One", true),
            ChatMessageItem("m2", "peer-b", "Message Two", false),
            ChatMessageItem("m3", "local", "Message Three", true)
        )

        // Select m1 and m3
        val selectedIds = setOf("m1", "m3")
        val copiedText = messages
            .filter { selectedIds.contains(it.id) }
            .map { it.text }
            .joinToString("\n")

        assertEquals("Message One\nMessage Three", copiedText)
    }

    @Test
    fun testMultiMessageDeletionInMemoryFiltering() {
        val messages = listOf(
            ChatMessageItem("m1", "local", "Message One", true),
            ChatMessageItem("m2", "peer-b", "Message Two", false),
            ChatMessageItem("m3", "local", "Message Three", true)
        )

        val idsToDelete = setOf("m1", "m2")
        val remaining = messages.filterNot { it.id in idsToDelete }

        assertEquals(1, remaining.size)
        assertEquals("m3", remaining.first().id)
    }

    @Test
    fun testRoutingHopInspectionForTechnicalDetails() {
        val routingTable = RoutingTable.getInstance()
        routingTable.clear()

        val destPeerId = "peer-charlie"
        val nextHopId = "peer-bob"
        val address = InetAddress.getByName("192.168.49.2")

        routingTable.updateRoute(
            destinationPeerId = destPeerId,
            nextHopPeerId = nextHopId,
            nextHopAddress = address,
            hopCount = 2,
            sequenceNumber = 10
        )

        val route = routingTable.getRoute(destPeerId)
        assertEquals(2, route?.hopCount)
        assertEquals(nextHopId, route?.nextHopPeerId)

        // Verifying HopManager decision
        val packet = com.hypershare.model.Packet(
            com.hypershare.model.PacketHeader(
                type = PacketType.MSG,
                sequenceNumber = 5,
                sourcePeerId = "peer-alice",
                destinationPeerId = destPeerId,
                ttl = 7
            ),
            "Hello multi-hop!".toByteArray(StandardCharsets.UTF_8)
        )

        val hopManager = HopManager(routingTable, localPeerId = "peer-my-node")
        val decision = hopManager.processPacket(packet)

        assertTrue("Expected Relay decision for non-local destination", decision is ForwardDecision.Relay)
        val relayDecision = decision as ForwardDecision.Relay
        assertEquals(6.toByte(), relayDecision.packet.header.ttl)
        assertEquals(nextHopId, relayDecision.nextHopRoute.nextHopPeerId)
    }
}
