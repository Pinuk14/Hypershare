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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
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
    private var udpJob: Job? = null
    private var reaperJob: Job? = null

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

        val myId = UserIdentityManager.getInstance(this).getPeerId()
        localServiceName = "HyperShare_${myId}"
        startForegroundNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val mode = intent?.getStringExtra(MeshNetworkService.EXTRA_MODE)
            ?.let { runCatching { PeerMode.valueOf(it) }.getOrNull() }
            ?: PeerMode.MODE_1_WIFI
        if (mode == PeerMode.MODE_1_WIFI) {
            startDiscovery(mode)
        } else {
            stopDiscovery()
            activePeers.clear()
        }
        return START_STICKY
    }

    fun startDiscovery(mode: PeerMode = PeerMode.MODE_1_WIFI) {
        if (mode == PeerMode.MODE_1_WIFI) {
            registerMdnsService()
            discoverMdnsServices()
            startUdpLanBroadcastPing()
            startPeerLivenessReaper()
        }
    }

    fun refreshDiscovery() {
        val oldPeers = activePeers.values.toList()
        activePeers.clear()
        serviceScope.launch {
            for (p in oldPeers) {
                _discoveryEventsFlow.emit(PeerDiscoveryEvent.PeerLost(p.peerId))
            }
        }
        stopDiscovery()
        startDiscovery(PeerMode.MODE_1_WIFI)
        sendSubnetUdpPings()
    }

    fun stopDiscovery() {
        sendSubnetUdpBye()
        udpJob?.cancel()
        udpJob = null
        reaperJob?.cancel()
        reaperJob = null
        registrationListener?.let {
            try { nsdManager?.unregisterService(it) } catch (e: Exception) { }
            registrationListener = null
        }
        discoveryListener?.let {
            try { nsdManager?.stopServiceDiscovery(it) } catch (e: Exception) { }
            discoveryListener = null
        }
        try { udpSocket?.close() } catch (e: Exception) { }
        udpSocket = null
    }

    private fun registerMdnsService() {
        val myId = UserIdentityManager.getInstance(this).getPeerId()
        val myName = UserIdentityManager.getInstance(this).getUsername()
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = localServiceName
            serviceType = SERVICE_TYPE
            port = DEFAULT_PORT
            try {
                setAttribute("peerId", myId)
                setAttribute("name", myName)
            } catch (_: Exception) {}
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
                val myId = UserIdentityManager.getInstance(this@DiscoveryService).getPeerId()
                // Filter own service even if mDNS appends a suffix like "_2" or "_(2)"
                val isOwnService = service.serviceName.startsWith(localServiceName) ||
                                   service.serviceName.contains(myId)
                if (service.serviceType.startsWith(SERVICE_TYPE_PREFIX) && !isOwnService) {
                    queueResolve(service)
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                val sName = service.serviceName
                val cleanName = sName.removePrefix("HyperShare_").lowercase().replace(Regex("[^a-z0-9]"), "")
                val matchingPeers = activePeers.values.filter { peer ->
                    peer.peerId == sName ||
                    peer.peerId == sName.removePrefix("HyperShare_") ||
                    peer.displayName.lowercase().replace(Regex("[^a-z0-9]"), "") == cleanName
                }
                for (p in matchingPeers) {
                    activePeers.remove(p.peerId)
                    serviceScope.launch {
                        _discoveryEventsFlow.emit(PeerDiscoveryEvent.PeerLost(p.peerId))
                    }
                }
                val fallbackId = sName.removePrefix("HyperShare_")
                activePeers.remove(fallbackId)
                serviceScope.launch {
                    _discoveryEventsFlow.emit(PeerDiscoveryEvent.PeerLost(fallbackId))
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
                val myPeerId = UserIdentityManager.getInstance(this@DiscoveryService).getPeerId()

                // Extract cryptographic peerId from TXT record attribute or service name
                val peerId = try {
                    serviceInfo.attributes["peerId"]?.let { String(it, Charsets.UTF_8) }
                } catch (_: Exception) { null }
                    ?: serviceInfo.serviceName.removePrefix("HyperShare_")

                val displayName = try {
                    serviceInfo.attributes["name"]?.let { String(it, Charsets.UTF_8) }
                } catch (_: Exception) { null }
                    ?: peerId.take(12)

                val myName = UserIdentityManager.getInstance(this@DiscoveryService).getUsername()
                val host = serviceInfo.host

                val isSelf = host == null ||
                        isLocalDeviceAddress(host) ||
                        peerId == myPeerId ||
                        displayName.trim().equals(myName.trim(), ignoreCase = true) ||
                        serviceInfo.serviceName.startsWith(localServiceName)

                if (!isSelf && host != null) {
                    // Check if an active peer already exists with this exact IP
                    val existingPeer = activePeers.values.find { it.ipAddress?.hostAddress == host.hostAddress }
                    val isExistingRealId = existingPeer != null && (existingPeer.peerId.length >= 16 || existingPeer.peerId.startsWith("peer-"))

                    // If existing peer on same IP has genuine cryptographic ID, do not override with mDNS suffix
                    if (!isExistingRealId) {
                        // Remove old entry if key changed
                        if (existingPeer != null && existingPeer.peerId != peerId) {
                            activePeers.remove(existingPeer.peerId)
                        }

                        val peer = ConnectedPeer(
                            peerId = peerId,
                            displayName = displayName,
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
                }
                drainResolveQueue()  // resolve next queued service
            }
        })
    }

    private fun startPeerLivenessReaper() {
        reaperJob?.cancel()
        reaperJob = serviceScope.launch {
            while (isActive) {
                delay(2000L)
                val now = System.currentTimeMillis()
                val expiredPeers = activePeers.values.filter { peer ->
                    now - peer.lastSeenTimestamp > PEER_OFFLINE_TIMEOUT_MS
                }
                for (peer in expiredPeers) {
                    activePeers.remove(peer.peerId)
                    _discoveryEventsFlow.emit(PeerDiscoveryEvent.PeerLost(peer.peerId))
                }
            }
        }
    }

    private fun startUdpLanBroadcastPing() {
        udpJob?.cancel()
        udpJob = serviceScope.launch {
            try {
                if (udpSocket == null || udpSocket?.isClosed == true) {
                    udpSocket = DatagramSocket(UDP_PORT).apply { broadcast = true }
                }

                // Subnet packet receiver loop
                launch {
                    val buffer = ByteArray(1024)
                    while (isActive && udpSocket?.isClosed == false) {
                        try {
                            val packet = DatagramPacket(buffer, buffer.size)
                            udpSocket?.receive(packet)
                            val message = String(packet.data, 0, packet.length, Charsets.UTF_8)
                            if (message.startsWith("HYPERSHARE_BYE:")) {
                                val peerId = message.removePrefix("HYPERSHARE_BYE:").trim()
                                val senderIp = packet.address?.hostAddress
                                val matchingPeers = activePeers.values.filter {
                                    it.peerId == peerId ||
                                    (senderIp != null && it.ipAddress?.hostAddress == senderIp)
                                }
                                for (p in matchingPeers) {
                                    activePeers.remove(p.peerId)
                                    _discoveryEventsFlow.emit(PeerDiscoveryEvent.PeerLost(p.peerId))
                                }
                                activePeers.remove(peerId)
                                _discoveryEventsFlow.emit(PeerDiscoveryEvent.PeerLost(peerId))
                            } else if (message.startsWith("HYPERSHARE_PING:")) {
                                val parts = message.split(":")
                                if (parts.size >= 3) {
                                    val peerId = parts[1]
                                    val displayName = parts[2]
                                    val senderIp = packet.address

                                    val myPeerId = UserIdentityManager.getInstance(this@DiscoveryService).getPeerId()
                                    val myName = UserIdentityManager.getInstance(this@DiscoveryService).getUsername()

                                    val isSelf = peerId == myPeerId ||
                                            displayName.trim().equals(myName.trim(), ignoreCase = true) ||
                                            isLocalDeviceAddress(senderIp)

                                    if (!isSelf && senderIp != null) {
                                        // Remove any existing transient mDNS peer on this exact IP
                                        val existing = activePeers.values.find { it.ipAddress?.hostAddress == senderIp.hostAddress }
                                        if (existing != null && existing.peerId != peerId) {
                                            activePeers.remove(existing.peerId)
                                        }

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
                while (isActive && udpSocket?.isClosed == false) {
                    sendSubnetUdpPings()
                    delay(2000L)
                }
            } catch (e: Exception) { }
        }
    }

    private fun sendSubnetUdpBye() {
        try {
            val myPeerId = UserIdentityManager.getInstance(this).getPeerId()
            val byeMessage = "HYPERSHARE_BYE:$myPeerId"
            val bytes = byeMessage.toByteArray(Charsets.UTF_8)
            val globalBroadcast = DatagramPacket(bytes, bytes.size, InetAddress.getByName("255.255.255.255"), UDP_PORT)
            udpSocket?.send(globalBroadcast)
        } catch (_: Exception) {}
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

    private fun isLocalDeviceAddress(address: InetAddress?): Boolean {
        if (address == null) return true
        if (address.isLoopbackAddress || address.isAnyLocalAddress) return true

        // 1. Direct Wi-Fi interface IP check
        try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val ipInt = wifiManager?.connectionInfo?.ipAddress ?: 0
            if (ipInt != 0) {
                val wifiIp = android.text.format.Formatter.formatIpAddress(ipInt)
                if (address.hostAddress == wifiIp) {
                    return true
                }
            }
        } catch (_: Exception) { }

        // 2. All system network interfaces check
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return false
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                val addrs = iface.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (addr.hostAddress == address.hostAddress) {
                        return true
                    }
                }
            }
        } catch (_: Exception) { }
        return false
    }

    companion object {
        const val SERVICE_TYPE = "_hypershare._tcp."
        const val SERVICE_TYPE_PREFIX = "_hypershare"
        const val DEFAULT_PORT = 47200
        const val UDP_PORT = 47201
        const val PEER_OFFLINE_TIMEOUT_MS = 6000L
    }
}
