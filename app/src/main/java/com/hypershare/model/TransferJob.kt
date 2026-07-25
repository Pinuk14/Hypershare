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
    val permission: TransferPermission = TransferPermission.DOWNLOADABLE,
    val priority: TransferPriority = TransferPriority.FILE,
    val state: TransferState = TransferState.QUEUED,
    val bytesTransferred: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
)
