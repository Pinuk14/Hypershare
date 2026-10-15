package com.hypershare.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.hypershare.application.UserIdentityManager
import com.hypershare.model.PeerMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MeshNetworkService : Service() {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    val lanSocketManager = LanSocketManager.getInstance()
    lateinit var wifiDirectController: WifiDirectController
        private set
    var activeMode: PeerMode? = null
        private set
    private var hadP2pConnection = false

    inner class LocalBinder : Binder() {
        fun getService(): MeshNetworkService = this@MeshNetworkService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        wifiDirectController = WifiDirectController(this)
        serviceScope.launch {
            lanSocketManager.peerHandshakeEventsFlow.collect { event ->
                wifiDirectController.associateSelectedPeerId(event.peerId)
            }
        }
        serviceScope.launch {
            wifiDirectController.connectionState.collect { state ->
                if (activeMode == PeerMode.MODE_2_MESH && hadP2pConnection && !state.isConnected) {
                    lanSocketManager.closeActiveConnections()
                }
                hadP2pConnection = state.isConnected
                if (
                    activeMode == PeerMode.MODE_2_MESH &&
                    state.isConnected &&
                    !state.isGroupOwner &&
                    state.groupOwnerIpAddress != null
                ) {
                    val myPeerId = UserIdentityManager.getInstance(this@MeshNetworkService).getPeerId()
                    lanSocketManager.connectToPeer(
                        targetIp = state.groupOwnerIpAddress,
                        port = PORT,
                        senderPeerId = myPeerId
                    )
                } else if (
                    activeMode == PeerMode.MODE_2_MESH &&
                    !state.isConnected &&
                    !state.isConnecting &&
                    !state.isDiscovering
                ) {
                    // Auto-restart discovery if idle and disconnected in Emergency Mode
                    kotlinx.coroutines.delay(1500)
                    if (activeMode == PeerMode.MODE_2_MESH && !wifiDirectController.connectionState.value.isConnected) {
                        wifiDirectController.startPeerDiscovery()
                    }
                }
            }
        }

        // Autonomous Wi-Fi Direct auto-connection loop in Emergency Mode
        serviceScope.launch {
            wifiDirectController.discoveredPeers.collect { peers ->
                if (activeMode != PeerMode.MODE_2_MESH) return@collect
                val connState = wifiDirectController.connectionState.value
                if (connState.isConnected || connState.isConnecting) return@collect

                val repository = com.hypershare.db.MessageRepository(this@MeshNetworkService)
                val trustedContacts = repository.getAllConversations().filter { it.isTrusted && it.hasPeerAccepted }
                if (trustedContacts.isEmpty()) return@collect

                fun clean(s: String) = s.lowercase().replace(Regex("[^a-z0-9]"), "")

                // Filter out non-mobile appliances (TVs, PCs, printers) FIRST
                val candidatePeers = peers.filter { !wifiDirectController.isBlacklistedDevice(it.deviceName) }
                if (candidatePeers.isEmpty()) return@collect

                var resolvedTargetPeerId = ""
                val candidate = candidatePeers.firstOrNull { device ->
                    val rememberedPeer = wifiDirectController.getRememberedPeerForMac(device.deviceAddress)
                    if (rememberedPeer != null && trustedContacts.any { it.peerId == rememberedPeer }) {
                        resolvedTargetPeerId = rememberedPeer
                        return@firstOrNull true
                    }
                    val cleanDeviceName = clean(device.deviceName)
                    val matchedContact = trustedContacts.firstOrNull { 
                        val cleanName = clean(it.displayName)
                        cleanName.isNotEmpty() && (cleanName.contains(cleanDeviceName) || cleanDeviceName.contains(cleanName))
                    }
                    if (matchedContact != null) {
                        resolvedTargetPeerId = matchedContact.peerId
                        return@firstOrNull true
                    }
                    false
                } ?: candidatePeers.firstOrNull {
                    if (candidatePeers.size == 1) {
                        resolvedTargetPeerId = trustedContacts.firstOrNull()?.peerId.orEmpty()
                        true
                    } else false
                }

                if (candidate != null) {
                    val myPeerId = UserIdentityManager.getInstance(this@MeshNetworkService).getPeerId()
                    // Symmetrical intent negotiation: one device gets 15, the other gets 0.
                    // Both call connect() simultaneously, satisfying WPS PBC with zero OS invitation prompts.
                    val goIntent = wifiDirectController.calculateGoIntent(
                        targetMac = candidate.deviceAddress,
                        myPeerId = myPeerId,
                        targetPeerId = resolvedTargetPeerId
                    )
                    wifiDirectController.autoConnectToPeer(candidate, preferredIntent = goIntent)
                }
            }
        }
        startForegroundNotification()
        // NOTE: startServer is intentionally NOT called here.
        // onStartCommand receives the MODE intent and starts the server with the correct port.
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val mode = intent?.getStringExtra(EXTRA_MODE)
            ?.let { runCatching { PeerMode.valueOf(it) }.getOrNull() }
            ?: PeerMode.MODE_1_WIFI
        if (mode != activeMode) {
            if (mode == PeerMode.MODE_1_WIFI) {
                if (activeMode == PeerMode.MODE_2_MESH) {
                    wifiDirectController.stop()
                    lanSocketManager.closeActiveConnections()
                    hadP2pConnection = false
                }
                lanSocketManager.startServer(PORT)
            } else {
                lanSocketManager.startServer(PORT)
                wifiDirectController.start()
            }
            activeMode = mode
        }
        return START_STICKY
    }

    private fun startForegroundNotification() {
        val channelId = "hypershare_mesh_channel"
        val channelName = "HyperShare Mesh Network Service"
        val manager = getSystemService(NotificationManager::class.java)

        val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
        manager?.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("HyperShare Mesh Active")
            .setContentText("Listening for peer connections on port $PORT")
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setOngoing(true)
            .build()

        startForeground(1001, notification)
    }

    fun requestConnectToPeer(peerId: String) {
        if (activeMode != PeerMode.MODE_2_MESH) return
        val connState = wifiDirectController.connectionState.value
        if (connState.isConnected || connState.isConnecting) return

        val candidatePeers = wifiDirectController.discoveredPeers.value.filter {
            !wifiDirectController.isBlacklistedDevice(it.deviceName)
        }
        val mac = wifiDirectController.getRememberedMacForPeer(peerId)
        val candidate = if (mac != null) {
            candidatePeers.find { it.deviceAddress == mac }
        } else {
            val repository = com.hypershare.db.MessageRepository(this)
            val name = repository.getPeerDisplayName(peerId).orEmpty().lowercase().replace(Regex("[^a-z0-9]"), "")
            candidatePeers.find { 
                val cleanName = it.deviceName.lowercase().replace(Regex("[^a-z0-9]"), "")
                cleanName.isNotEmpty() && (cleanName.contains(name) || name.contains(cleanName))
            } ?: candidatePeers.singleOrNull()
        }
        if (candidate != null) {
            val myPeerId = UserIdentityManager.getInstance(this).getPeerId()
            val goIntent = wifiDirectController.calculateGoIntent(
                targetMac = candidate.deviceAddress,
                myPeerId = myPeerId,
                targetPeerId = peerId
            )
            wifiDirectController.autoConnectToPeer(candidate, preferredIntent = goIntent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        wifiDirectController.stop()
        lanSocketManager.stopServer()
        serviceScope.cancel()
    }

    companion object {
        const val PORT = 47200
        const val EXTRA_MODE = "MODE"
    }
}
