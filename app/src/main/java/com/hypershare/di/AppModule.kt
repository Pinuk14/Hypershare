package com.hypershare.di

import com.hypershare.application.SessionManager
import com.hypershare.application.StreamController
import com.hypershare.application.TransferQueue
import com.hypershare.routing.GroupOwnerElection
import com.hypershare.routing.ModeController
import com.hypershare.routing.RoutingTable
import com.hypershare.security.PeerKeyStore
import com.hypershare.security.TofuManager

object AppModule {

    fun provideSessionManager(): SessionManager = SessionManager()

    fun provideTransferQueue(): TransferQueue = TransferQueue()

    fun provideStreamController(): StreamController = StreamController()

    fun provideRoutingTable(): RoutingTable = RoutingTable()

    fun provideModeController(): ModeController = ModeController()

    fun provideGroupOwnerElection(): GroupOwnerElection = GroupOwnerElection()

    fun providePeerKeyStore(): PeerKeyStore = PeerKeyStore()

    fun provideTofuManager(peerKeyStore: PeerKeyStore): TofuManager = TofuManager(peerKeyStore)
}
