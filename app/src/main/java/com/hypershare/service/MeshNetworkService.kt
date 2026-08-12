package com.hypershare.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.hypershare.model.PeerMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class MeshNetworkService : Service() {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    val lanSocketManager = LanSocketManager.getInstance()

    inner class LocalBinder : Binder() {
        fun getService(): MeshNetworkService = this@MeshNetworkService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
        // NOTE: startServer is intentionally NOT called here.
        // onStartCommand receives the MODE intent and starts the server with the correct port.
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val mode = intent?.getStringExtra("MODE") ?: PeerMode.MODE_1_WIFI.name
        if (mode == PeerMode.MODE_1_WIFI.name) {
            lanSocketManager.startServer(PORT)
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
        lanSocketManager.stopServer()
        serviceScope.cancel()
    }

    companion object {
        const val PORT = 47200
    }
}
