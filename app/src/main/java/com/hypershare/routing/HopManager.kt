package com.hypershare.routing

import com.hypershare.model.Packet
import com.hypershare.model.RouteEntry

sealed class ForwardDecision {
    data class DeliverLocal(val packet: Packet) : ForwardDecision()
    data class Relay(val packet: Packet, val nextHopRoute: RouteEntry) : ForwardDecision()
    data object DropExpiredTtl : ForwardDecision()
    data object DropNoRoute : ForwardDecision()
}

class HopManager(private val routingTable: RoutingTable, private val localPeerId: String) {

    fun processPacket(packet: Packet): ForwardDecision {
        if (packet.header.destinationPeerId == localPeerId) {
            return ForwardDecision.DeliverLocal(packet)
        }

        val newTtl = (packet.header.ttl - 1).toByte()
        if (newTtl <= 0) {
            return ForwardDecision.DropExpiredTtl
        }

        val route = routingTable.getRoute(packet.header.destinationPeerId)
            ?: return ForwardDecision.DropNoRoute

        val decrementedPacket = packet.copy(
            header = packet.header.copy(ttl = newTtl)
        )
        return ForwardDecision.Relay(decrementedPacket, route)
    }
}
