package com.hypershare.model

import android.net.Uri

enum class TransferPermission {
    VIEW_ONLY,
    DOWNLOADABLE
}

enum class TransferState {
    QUEUED,
    IN_PROGRESS,
    PAUSED,
    COMPLETED,
    FAILED
}

enum class TransferPriority(val value: Int) {
    CONTROL(0),
    DISASTER_MSG(1),
    FILE(2),
    STREAM(3)
}

data class TransferJob(
    val id: String,
    val sourcePeerId: String,
    val destinationPeerId: String,
    val fileName: String,
    val fileSize: Long,
    val fileUri: Uri? = null,
    val mimeType: String = "*/*",
    val permission: TransferPermission = TransferPermission.DOWNLOADABLE,
    val priority: TransferPriority = TransferPriority.FILE,
    val state: TransferState = TransferState.QUEUED,
    val bytesTransferred: Long = 0L,
    val totalChunks: Int = 0,
    val transferredChunks: Int = 0,
    val fullCrc32: Long = 0L,
    val currentSpeedBps: Long = 0L,
    val averageSpeedBps: Long = 0L,
    val peakSpeedBps: Long = 0L,
    val estimatedRemainingSeconds: Long = 0L,
    val durationMs: Long = 0L,
    val isOutgoing: Boolean = true,
    val failureReason: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    val progress: Float
        get() = if (fileSize > 0) (bytesTransferred.toFloat() / fileSize.toFloat()).coerceIn(0f, 1f) else 0f
}
