package com.hypershare.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.hypershare.application.UserIdentityManager
import com.hypershare.model.ConnectedPeer
import com.hypershare.model.PeerDiscoveryEvent
import com.hypershare.model.PeerMode
import com.hypershare.model.PeerStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class DiscoveryService : Service() {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var nsdManager: NsdManager? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var udpSocket: DatagramSocket? = null

    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    private val activePeers = ConcurrentHashMap<String, ConnectedPeer>()

    // replay=4 ensures new collectors get recent peers; buffer=128 avoids drops
    private val _discoveryEventsFlow = MutableSharedFlow<PeerDiscoveryEvent>(replay = 4, extraBufferCapacity = 128)
    val discoveryEventsFlow: SharedFlow<PeerDiscoveryEvent> = _discoveryEventsFlow.asSharedFlow()

    // Android NsdManager only supports ONE resolve at a time — queue flag prevents concurrent resolves
    private val isResolving = AtomicBoolean(false)
    private val pendingResolves = ArrayDeque<NsdServiceInfo>()

    private var localServiceName: String = ""

    inner class LocalBinder : Binder() {
        fun getService(): DiscoveryService = this@DiscoveryService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        nsdManager = getSystemService(Context.NSD_SERVICE) as NsdManager

        try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            multicastLock = wifiManager.createMulticastLock("HyperShareMulticastLock").apply {
                setReferenceCounted(true)
                acquire()
            }
        } catch (e: Exception) { }

        localServiceName = "HyperShare_${UserIdentityManager.getInstance(this).getUsername().replace(" ", "_")}"
        startForegroundNotification()
        startDiscovery(PeerMode.MODE_1_WIFI)
    }

    fun startDiscovery(mode: PeerMode = PeerMode.MODE_1_WIFI) {
        if (mode == PeerMode.MODE_1_WIFI) {
            registerMdnsService()
            discoverMdnsServices()
            startUdpLanBroadcastPing()
        }
    }

    fun refreshDiscovery() {
        activePeers.clear()
        stopDiscovery()
        startDiscovery(PeerMode.MODE_1_WIFI)
        sendSubnetUdpPings()
    }

    fun stopDiscovery() {
        registrationListener?.let {
            try { nsdManager?.unregisterService(it) } catch (e: Exception) { }
            registrationListener = null
        }
        discoveryListener?.let {
            try { nsdManager?.stopServiceDiscovery(it) } catch (e: Exception) { }
            discoveryListener = null
        }
        try { udpSocket?.close() } catch (e: Exception) { }
    }

    private fun registerMdnsService() {
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = localServiceName
            serviceType = SERVICE_TYPE
            port = DEFAULT_PORT
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(NsdServiceInfo: NsdServiceInfo) {
                localServiceName = NsdServiceInfo.serviceName
            }
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
                // Filter own service even if mDNS appends a suffix like "_2" or "_(2)"
                val isOwnService = service.serviceName.startsWith(localServiceName)
                if (service.serviceType.startsWith(SERVICE_TYPE_PREFIX) && !isOwnService) {
                    queueResolve(service)
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                val peerId = service.serviceName
                activePeers.remove(peerId)
                serviceScope.launch {
                    _discoveryEventsFlow.emit(PeerDiscoveryEvent.PeerLost(peerId))
                }
            }

            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }

        nsdManager?.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    /**
     * Queue-based serial mDNS resolver.
     * Android NsdManager ONLY supports one resolveService() call at a time.
     * Concurrent calls produce "no client mapping" errors and silently drop resolved IPs.
     */
    private fun queueResolve(service: NsdServiceInfo) {
        pendingResolves.addLast(service)
        drainResolveQueue()
    }

    private fun drainResolveQueue() {
        if (!isResolving.compareAndSet(false, true)) return  // already resolving
        val next = pendingResolves.removeFirstOrNull() ?: run {
            isResolving.set(false)
            return
        }
        resolveOne(next)
    }

    private fun resolveOne(service: NsdServiceInfo) {
        nsdManager?.resolveService(service, object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                isResolving.set(false)
                drainResolveQueue()  // try next in queue
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                isResolving.set(false)
                val peerId = serviceInfo.serviceName
                val host = serviceInfo.host
                if (host != null) {
                    val peer = ConnectedPeer(
                        peerId = peerId,
                        displayName = peerId.replace("HyperShare_", "").replace("_", " "),
                        ipAddress = host,
                        port = serviceInfo.port,
                        status = PeerStatus.DISCOVERED,
                        mode = PeerMode.MODE_1_WIFI,
                        lastSeenTimestamp = System.currentTimeMillis()
                    )
                    activePeers[peerId] = peer
                    serviceScope.launch {
                        _discoveryEventsFlow.emit(PeerDiscoveryEvent.PeerDiscovered(peer))
                    }
                }
                drainResolveQueue()  // resolve next queued service
            }
        })
    }

    private fun startUdpLanBroadcastPing() {
        serviceScope.launch {
            try {
                if (udpSocket == null || udpSocket?.isClosed == true) {
                    udpSocket = DatagramSocket(UDP_PORT).apply { broadcast = true }
                }

                // Subnet packet receiver loop
                launch {
                    val buffer = ByteArray(1024)
                    while (udpSocket?.isClosed == false) {
                        try {
                            val packet = DatagramPacket(buffer, buffer.size)
                            udpSocket?.receive(packet)
                            val message = String(packet.data, 0, packet.length, Charsets.UTF_8)
                            if (message.startsWith("HYPERSHARE_PING:")) {
                                val parts = message.split(":")
                                if (parts.size >= 3) {
                                    val peerId = parts[1]
                                    val displayName = parts[2]
                                    val senderIp = packet.address

                                    val myPeerId = UserIdentityManager.getInstance(this@DiscoveryService).getPeerId()
                                    if (peerId != myPeerId) {
                                        val peer = ConnectedPeer(
                                            peerId = peerId,
                                            displayName = displayName,
                                            ipAddress = senderIp,
                                            port = DEFAULT_PORT,
                                            status = PeerStatus.DISCOVERED,
                                            mode = PeerMode.MODE_1_WIFI,
                                            lastSeenTimestamp = System.currentTimeMillis()
                                        )

                                        activePeers[peerId] = peer
                                        _discoveryEventsFlow.emit(PeerDiscoveryEvent.PeerDiscovered(peer))
                                    }
                                }
                            }
                        } catch (e: Exception) { break }
                    }
                }

                // Periodic UDP broadcast loop (every 2 seconds)
                while (udpSocket?.isClosed == false) {
                    sendSubnetUdpPings()
                    delay(2000L)
                }
            } catch (e: Exception) { }
        }
    }

    private fun sendSubnetUdpPings() {
        try {
            val myPeerId = UserIdentityManager.getInstance(this@DiscoveryService).getPeerId()
            val myUsername = UserIdentityManager.getInstance(this@DiscoveryService).getUsername()
            val pingMessage = "HYPERSHARE_PING:$myPeerId:$myUsername"
            val bytes = pingMessage.toByteArray(Charsets.UTF_8)

            // 1. Broadcast to 255.255.255.255
            val globalBroadcast = DatagramPacket(bytes, bytes.size, InetAddress.getByName("255.255.255.255"), UDP_PORT)
            udpSocket?.send(globalBroadcast)

            // 2. Direct Subnet Broadcasts across local network interfaces
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue

                for (interfaceAddress in iface.interfaceAddresses) {
                    val broadcast = interfaceAddress.broadcast
                    if (broadcast != null) {
                        val packet = DatagramPacket(bytes, bytes.size, broadcast, UDP_PORT)
                        udpSocket?.send(packet)
                    }
                }
            }
        } catch (e: Exception) { }
    }

    fun getActivePeers(): List<ConnectedPeer> = activePeers.values.toList()

    private fun startForegroundNotification() {
        val channelId = "hypershare_discovery_channel"
        val manager = getSystemService(NotificationManager::class.java)

        val channel = NotificationChannel(channelId, "HyperShare Discovery", NotificationManager.IMPORTANCE_LOW)
        manager?.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("HyperShare Peer Discovery")
            .setContentText("Scanning local WiFi network for peers...")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()

        startForeground(1002, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopDiscovery()
        try { multicastLock?.release() } catch (e: Exception) { }
    }

    companion object {
        const val SERVICE_TYPE = "_hypershare._tcp."
        const val SERVICE_TYPE_PREFIX = "_hypershare"
        const val DEFAULT_PORT = 47200
        const val UDP_PORT = 47201
    }
}
