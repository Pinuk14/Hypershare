package com.hypershare.routing

import com.hypershare.model.Packet
import com.hypershare.model.PacketHeader
import com.hypershare.model.PacketType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetAddress
import java.nio.charset.StandardCharsets

class MultiClientRoutingTest {

    private lateinit var routingTableA: RoutingTable
    private lateinit var routingTableB: RoutingTable
    private lateinit var routingTableC: RoutingTable

    private lateinit var hopManagerA: HopManager
    private lateinit var hopManagerB: HopManager
    private lateinit var hopManagerC: HopManager

    private val ipA = InetAddress.getByName("192.168.49.1")
    private val ipB = InetAddress.getByName("192.168.49.100")
    private val ipC = InetAddress.getByName("192.168.49.101")

    @Before
    fun setUp() {
        routingTableA = RoutingTable()
        routingTableB = RoutingTable()
        routingTableC = RoutingTable()

        hopManagerA = HopManager(routingTableA, "Peer_A")
        hopManagerB = HopManager(routingTableB, "Peer_B")
        hopManagerC = HopManager(routingTableC, "Peer_C")
    }

    @Test
    fun testThreeNodeGroupTopologyRouteSetup() {
        // Node A is GO. Node B and Node C connect directly to Node A (1 hop).
        routingTableA.updateRoute("Peer_B", "Peer_B", ipB, hopCount = 1, sequenceNumber = 1)
        routingTableA.updateRoute("Peer_C", "Peer_C", ipC, hopCount = 1, sequenceNumber = 1)

        // On Node A: B and C are direct peers (1 hop)
        assertEquals(2, routingTableA.getAllRoutes().size)
        assertEquals(2, routingTableA.getDirectPeers().size)
        assertEquals(1, routingTableA.getRoute("Peer_B")?.hopCount)
        assertEquals(1, routingTableA.getRoute("Peer_C")?.hopCount)

        // Node B has direct route to Node A (1 hop) and multi-hop route to Node C via Node A (2 hops)
        routingTableB.updateRoute("Peer_A", "Peer_A", ipA, hopCount = 1, sequenceNumber = 1)
        routingTableB.updateRoute("Peer_C", "Peer_A", ipA, hopCount = 2, sequenceNumber = 1)

        assertEquals(2, routingTableB.getAllRoutes().size)
        assertEquals(1, routingTableB.getDirectPeers().size)
        assertEquals("Peer_A", routingTableB.getRoute("Peer_C")?.nextHopPeerId)
        assertEquals(2, routingTableB.getRoute("Peer_C")?.hopCount)

        // Node C has direct route to Node A (1 hop) and multi-hop route to Node B via Node A (2 hops)
        routingTableC.updateRoute("Peer_A", "Peer_A", ipA, hopCount = 1, sequenceNumber = 1)
        routingTableC.updateRoute("Peer_B", "Peer_A", ipA, hopCount = 2, sequenceNumber = 1)

        assertEquals(2, routingTableC.getAllRoutes().size)
        assertEquals(1, routingTableC.getDirectPeers().size)
        assertEquals("Peer_A", routingTableC.getRoute("Peer_B")?.nextHopPeerId)
        assertEquals(2, routingTableC.getRoute("Peer_B")?.hopCount)
    }

    @Test
    fun testMultiHopPacketForwardingEndToEnd() {
        // Setup routes: B -> A (hop 1), B -> C via A (hop 2)
        routingTableB.updateRoute("Peer_A", "Peer_A", ipA, hopCount = 1, sequenceNumber = 1)
        routingTableB.updateRoute("Peer_C", "Peer_A", ipA, hopCount = 2, sequenceNumber = 1)

        // A -> B (hop 1), A -> C (hop 1)
        routingTableA.updateRoute("Peer_B", "Peer_B", ipB, hopCount = 1, sequenceNumber = 1)
        routingTableA.updateRoute("Peer_C", "Peer_C", ipC, hopCount = 1, sequenceNumber = 1)

        // C -> A (hop 1), C -> B via A (hop 2)
        routingTableC.updateRoute("Peer_A", "Peer_A", ipA, hopCount = 1, sequenceNumber = 1)
        routingTableC.updateRoute("Peer_B", "Peer_A", ipA, hopCount = 2, sequenceNumber = 1)

        // Step 1: Peer B constructs a packet for Peer C with initial TTL = 8
        val originalPacket = Packet(
            header = PacketHeader(
                type = PacketType.MSG,
                ttl = 8,
                sequenceNumber = 1,
                sourcePeerId = "Peer_B",
                destinationPeerId = "Peer_C"
            ),
            payload = "Hello from B to C".toByteArray(StandardCharsets.UTF_8)
        )

        // Step 2: Peer B evaluates hop forwarding
        val decisionB = hopManagerB.processPacket(originalPacket)
        assertTrue("Node B should relay packet to nextHop Peer_A", decisionB is ForwardDecision.Relay)
        val relayFromB = decisionB as ForwardDecision.Relay
        assertEquals("Peer_A", relayFromB.nextHopRoute.nextHopPeerId)
        assertEquals(7.toByte(), relayFromB.packet.header.ttl)

        // Step 3: Peer A (GO) receives the packet (TTL=7) and evaluates hop forwarding
        val decisionA = hopManagerA.processPacket(relayFromB.packet)
        assertTrue("Node A should relay packet to nextHop Peer_C", decisionA is ForwardDecision.Relay)
        val relayFromA = decisionA as ForwardDecision.Relay
        assertEquals("Peer_C", relayFromA.nextHopRoute.nextHopPeerId)
        assertEquals(6.toByte(), relayFromA.packet.header.ttl)

        // Step 4: Peer C receives the packet (TTL=6). Destination matches localPeerId
        val decisionC = hopManagerC.processPacket(relayFromA.packet)
        assertTrue("Node C should deliver packet locally", decisionC is ForwardDecision.DeliverLocal)
        val localDelivery = decisionC as ForwardDecision.DeliverLocal
        assertEquals("Peer_C", localDelivery.packet.header.destinationPeerId)
        assertEquals("Peer_B", localDelivery.packet.header.sourcePeerId)
    }

    @Test
    fun testTtlExpiryPreventsInfiniteLoops() {
        // Route exists on Node A to Peer_C
        routingTableA.updateRoute("Peer_C", "Peer_C", ipC, hopCount = 1, sequenceNumber = 1)

        // Packet arrives with TTL = 1
        val expiredPacket = Packet(
            header = PacketHeader(
                type = PacketType.MSG,
                ttl = 1,
                sequenceNumber = 42,
                sourcePeerId = "Peer_B",
                destinationPeerId = "Peer_C"
            ),
            payload = "Loop test".toByteArray(StandardCharsets.UTF_8)
        )

        val decision = hopManagerA.processPacket(expiredPacket)
        assertTrue("Packet with TTL=1 decremented to 0 must be dropped", decision is ForwardDecision.DropExpiredTtl)
    }

    @Test
    fun testDropPacketWhenNoRouteAvailable() {
        // Node A has route to B, but NOT to D
        routingTableA.updateRoute("Peer_B", "Peer_B", ipB, hopCount = 1, sequenceNumber = 1)

        val unroutablePacket = Packet(
            header = PacketHeader(
                type = PacketType.MSG,
                ttl = 8,
                sequenceNumber = 10,
                sourcePeerId = "Peer_B",
                destinationPeerId = "Peer_Unknown"
            ),
            payload = "Unreachable".toByteArray(StandardCharsets.UTF_8)
        )

        val decision = hopManagerA.processPacket(unroutablePacket)
        assertTrue("Packet for unknown destination must be dropped", decision is ForwardDecision.DropNoRoute)
    }

    @Test
    fun testClientDisconnectRouteCleanup() {
        routingTableA.updateRoute("Peer_B", "Peer_B", ipB, hopCount = 1, sequenceNumber = 1)
        routingTableA.updateRoute("Peer_C", "Peer_C", ipC, hopCount = 1, sequenceNumber = 1)
        assertEquals(2, routingTableA.getAllRoutes().size)

        // Peer C disconnects
        routingTableA.removeRoute("Peer_C")

        assertNull("Route for Peer_C must be removed", routingTableA.getRoute("Peer_C"))
        assertNotNull("Route for Peer_B must remain intact", routingTableA.getRoute("Peer_B"))
        assertEquals(1, routingTableA.getAllRoutes().size)
    }
}
