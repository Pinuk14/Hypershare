package com.hypershare.model

import java.net.InetAddress

data class RouteEntry(
    val destinationPeerId: String,
    val nextHopPeerId: String,
    val nextHopAddress: InetAddress?,
    val hopCount: Int,
    val sequenceNumber: Int,
    val isStale: Boolean = false,
    val lastUpdatedTimestamp: Long = System.currentTimeMillis()
)
