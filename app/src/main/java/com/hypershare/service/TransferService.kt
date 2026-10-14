package com.hypershare.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.hypershare.model.TransferJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class TransferService : Service() {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    inner class LocalBinder : Binder() {
        fun getService(): TransferService = this@TransferService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
        com.hypershare.application.TransferManager.getInstance().init(this)
    }

    fun processTransferJob(job: TransferJob) {
        if (job.fileUri != null) {
            com.hypershare.application.TransferManager.getInstance().startSendTransfer(
                fileUri = job.fileUri,
                targetPeerId = job.destinationPeerId,
                isViewOnly = job.permission == com.hypershare.model.TransferPermission.VIEW_ONLY
            )
        }
    }

    private fun startForegroundNotification() {
        val channelId = "hypershare_transfer_channel"
        val manager = getSystemService(NotificationManager::class.java)

        val channel = NotificationChannel(channelId, "HyperShare File Transfer", NotificationManager.IMPORTANCE_LOW)
        manager?.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("HyperShare Transfer Engine")
            .setContentText("Managing active file transfers...")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .build()

        startForeground(1003, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
