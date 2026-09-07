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
    private var activeMode: PeerMode? = null
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
                    lanSocketManager.connectToPeer(
                        targetIp = state.groupOwnerIpAddress,
                        port = PORT,
                        senderPeerId = UserIdentityManager.getInstance(this@MeshNetworkService).getPeerId()
                    )
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
