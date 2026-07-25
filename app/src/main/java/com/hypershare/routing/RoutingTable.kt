package com.hypershare.routing

import com.hypershare.model.RouteEntry
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

class RoutingTable {
    private val table = ConcurrentHashMap<String, RouteEntry>()

    fun updateRoute(
        destinationPeerId: String,
        nextHopPeerId: String,
        nextHopAddress: InetAddress?,
        hopCount: Int,
        sequenceNumber: Int
    ) {
        val existing = table[destinationPeerId]
        if (existing == null || sequenceNumber > existing.sequenceNumber ||
            (sequenceNumber == existing.sequenceNumber && hopCount < existing.hopCount)
        ) {
            table[destinationPeerId] = RouteEntry(
                destinationPeerId = destinationPeerId,
                nextHopPeerId = nextHopPeerId,
                nextHopAddress = nextHopAddress,
                hopCount = hopCount,
                sequenceNumber = sequenceNumber
            )
        }
    }

    fun getRoute(destinationPeerId: String): RouteEntry? {
        val route = table[destinationPeerId]
        return if (route != null && !route.isStale) route else null
    }

    fun markRouteStale(destinationPeerId: String) {
        table[destinationPeerId]?.let {
            table[destinationPeerId] = it.copy(isStale = true)
        }
    }

    fun pruneStaleRoutes(ttlMs: Long = 30_000L) {
        val now = System.currentTimeMillis()
        table.entries.removeIf { (_, entry) ->
            entry.isStale || (now - entry.lastUpdatedTimestamp > ttlMs)
        }
    }

    fun getAllRoutes(): List<RouteEntry> = table.values.toList()
}
