package com.hypershare.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.hypershare.model.PeerMode
import com.hypershare.routing.ModeController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.net.ServerSocket

class MeshNetworkService : Service() {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var serverSocket: ServerSocket? = null

    inner class LocalBinder : Binder() {
        fun getService(): MeshNetworkService = this@MeshNetworkService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val mode = intent?.getStringExtra("MODE") ?: PeerMode.MODE_1_WIFI.name
        if (mode == PeerMode.MODE_1_WIFI.name) {
            startMode1ServerSocket()
        } else {
            startMode2WifiDirectGroup()
        }
        return START_STICKY
    }

    private fun startMode1ServerSocket() {
        serviceScope.run {
            try {
                if (serverSocket == null || serverSocket?.isClosed == true) {
                    serverSocket = ServerSocket(PORT)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun startMode2WifiDirectGroup() {
        // Mode 2 WifiP2pManager GO / Client setup
    }

    private fun startForegroundNotification() {
        val channelId = "hypershare_mesh_channel"
        val channelName = "HyperShare Mesh Network Service"
        val manager = getSystemService(NotificationManager::class.java)

        val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
        manager?.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("HyperShare Active")
            .setContentText("Maintaining local mesh network connections")
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setOngoing(true)
            .build()

        startForeground(1001, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        serverSocket?.close()
        serviceScope.cancel()
    }

    companion object {
        const val PORT = 47200
    }
}
