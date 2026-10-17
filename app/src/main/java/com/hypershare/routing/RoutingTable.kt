package com.hypershare.routing

import com.hypershare.model.RouteEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

class RoutingTable {
    private val table = ConcurrentHashMap<String, RouteEntry>()
    private val _routesFlow = MutableStateFlow<List<RouteEntry>>(emptyList())
    val routesFlow: StateFlow<List<RouteEntry>> = _routesFlow.asStateFlow()

    private fun emitRoutesUpdate() {
        _routesFlow.value = table.values.toList()
    }

    fun updateRoute(
        destinationPeerId: String,
        nextHopPeerId: String,
        nextHopAddress: InetAddress?,
        hopCount: Int,
        sequenceNumber: Int
    ) {
        val existing = table[destinationPeerId]
        if (existing == null || sequenceNumber > existing.sequenceNumber ||
            (sequenceNumber == existing.sequenceNumber && hopCount < existing.hopCount) ||
            existing.isStale
        ) {
            table[destinationPeerId] = RouteEntry(
                destinationPeerId = destinationPeerId,
                nextHopPeerId = nextHopPeerId,
                nextHopAddress = nextHopAddress,
                hopCount = hopCount,
                sequenceNumber = sequenceNumber,
                isStale = false,
                lastUpdatedTimestamp = System.currentTimeMillis()
            )
            emitRoutesUpdate()
        }
    }

    fun getRoute(destinationPeerId: String): RouteEntry? {
        val route = table[destinationPeerId]
        return if (route != null && !route.isStale) route else null
    }

    fun markRouteStale(destinationPeerId: String) {
        table[destinationPeerId]?.let {
            table[destinationPeerId] = it.copy(isStale = true)
            emitRoutesUpdate()
        }
    }

    fun removeRoute(destinationPeerId: String) {
        if (table.remove(destinationPeerId) != null) {
            emitRoutesUpdate()
        }
    }

    fun removeRoutesVia(nextHopPeerId: String) {
        val removed = table.entries.removeIf { it.value.nextHopPeerId == nextHopPeerId }
        if (removed) {
            emitRoutesUpdate()
        }
    }

    fun pruneStaleRoutes(ttlMs: Long = 30_000L) {
        val now = System.currentTimeMillis()
        val removed = table.entries.removeIf { (_, entry) ->
            entry.isStale || (now - entry.lastUpdatedTimestamp > ttlMs)
        }
        if (removed) {
            emitRoutesUpdate()
        }
    }

    fun clear() {
        table.clear()
        emitRoutesUpdate()
    }

    fun getDirectPeers(): List<RouteEntry> = table.values.filter { it.hopCount == 1 && !it.isStale }

    fun getAllRoutes(): List<RouteEntry> = table.values.toList()

    companion object {
        @Volatile
        private var INSTANCE: RoutingTable? = null

        fun getInstance(): RoutingTable =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: RoutingTable().also { INSTANCE = it }
            }
    }
}

