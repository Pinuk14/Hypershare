package com.hypershare.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.hypershare.model.ConnectedPeer
import com.hypershare.model.PeerMode
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class DiscoveryService : Service() {

    private val binder = LocalBinder()
    private var nsdManager: NsdManager? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    private val _discoveredPeersFlow = MutableSharedFlow<ConnectedPeer>()
    val discoveredPeersFlow: SharedFlow<ConnectedPeer> = _discoveredPeersFlow.asSharedFlow()

    inner class LocalBinder : Binder() {
        fun getService(): DiscoveryService = this@DiscoveryService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        nsdManager = getSystemService(Context.NSD_SERVICE) as NsdManager
        startForegroundNotification()
    }

    fun startDiscovery(mode: PeerMode) {
        if (mode == PeerMode.MODE_1_WIFI) {
            registerMdnsService()
            discoverMdnsServices()
        } else {
            // Mode 2 WiFi Direct Discovery via WifiP2pManager
        }
    }

    private fun registerMdnsService() {
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "HyperShare_${android.os.Build.MODEL}"
            serviceType = SERVICE_TYPE
            port = 47200
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(NsdServiceInfo: NsdServiceInfo) {}
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
            override fun onServiceUnregistered(arg0: NsdServiceInfo) {}
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
        }

        nsdManager?.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
    }

    private fun discoverMdnsServices() {
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {}
            override fun onServiceFound(service: NsdServiceInfo) {
                if (service.serviceType == SERVICE_TYPE && !service.serviceName.contains(android.os.Build.MODEL)) {
                    nsdManager?.resolveService(service, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            val peer = ConnectedPeer(
                                peerId = serviceInfo.serviceName,
                                displayName = serviceInfo.serviceName,
                                ipAddress = serviceInfo.host,
                                port = serviceInfo.port
                            )
                            _discoveredPeersFlow.tryEmit(peer)
                        }
                    })
                }
            }
            override fun onServiceLost(service: NsdServiceInfo) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }

        nsdManager?.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    private fun startForegroundNotification() {
        val channelId = "hypershare_discovery_channel"
        val manager = getSystemService(NotificationManager::class.java)

        val channel = NotificationChannel(channelId, "HyperShare Discovery", NotificationManager.IMPORTANCE_LOW)
        manager?.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("HyperShare Peer Discovery")
            .setContentText("Scanning for nearby peers...")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()

        startForeground(1002, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        registrationListener?.let { nsdManager?.unregisterService(it) }
        discoveryListener?.let { nsdManager?.stopServiceDiscovery(it) }
    }

    companion object {
        const val SERVICE_TYPE = "_hypershare._tcp."
    }
}
