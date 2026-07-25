package com.hypershare.di

import com.hypershare.protocol.ControlPacketHandler
import com.hypershare.routing.HopManager
import com.hypershare.routing.RoutingTable

object NetworkModule {

    fun provideControlPacketHandler(): ControlPacketHandler = ControlPacketHandler()

    fun provideHopManager(routingTable: RoutingTable, localPeerId: String): HopManager =
        HopManager(routingTable, localPeerId)
}
