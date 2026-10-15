package com.hypershare.application

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.ContentValues
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.core.app.NotificationCompat
import com.hypershare.model.DataChunk
import com.hypershare.model.Packet
import com.hypershare.model.PacketType
import com.hypershare.model.TransferJob
import com.hypershare.model.TransferPermission
import com.hypershare.model.TransferPriority
import com.hypershare.model.TransferState
import com.hypershare.protocol.ChunkAckPayload
import com.hypershare.protocol.ChunkManager
import com.hypershare.protocol.PacketBuilder
import com.hypershare.protocol.TransferCancelPayload
import com.hypershare.protocol.TransferChunkPayload
import com.hypershare.protocol.TransferInitPayload
import com.hypershare.protocol.TransferResponsePayload
import com.hypershare.service.LanSocketManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.RandomAccessFile
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

data class IncomingTransferPrompt(
    val transferId: String,
    val senderPeerId: String,
    val fileName: String,
    val fileSize: Long,
    val totalChunks: Int,
    val fullCrc32: Long,
    val isViewOnly: Boolean,
    val mimeType: String,
    val receivedAt: Long = System.currentTimeMillis()
)

data class TransferCompletedEvent(
    val transferId: String,
    val fileName: String,
    val fileSize: Long,
    val durationMs: Long,
    val averageSpeedBps: Long,
    val peakSpeedBps: Long,
    val fullCrc32: Long,
    val isOutgoing: Boolean,
    val localUri: Uri? = null,
    val isViewOnly: Boolean = false
)

class TransferManager private constructor() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var appContext: Context? = null

    private val _jobs = MutableStateFlow<List<TransferJob>>(emptyList())
    val jobs: StateFlow<List<TransferJob>> = _jobs.asStateFlow()

    private val _incomingPrompts = MutableSharedFlow<IncomingTransferPrompt>(replay = 1, extraBufferCapacity = 16)
    val incomingPrompts: SharedFlow<IncomingTransferPrompt> = _incomingPrompts.asSharedFlow()

    private val _completionEvents = MutableSharedFlow<TransferCompletedEvent>(replay = 0, extraBufferCapacity = 16)
    val completionEvents: SharedFlow<TransferCompletedEvent> = _completionEvents.asSharedFlow()

    // Active incoming files being written: transferId -> InboundSession
    private val inboundSessions = ConcurrentHashMap<String, InboundSession>()

    // Active outbound transfers ACK coordination: transferId -> (chunkIndex -> Deferred)
    private val outboundAckMaps = ConcurrentHashMap<String, ConcurrentHashMap<Int, CompletableDeferred<Unit>>>()
    private val outboundResponseDeferreds = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
    private val activeTransferJobs = ConcurrentHashMap<String, Job>()

    private class InboundSession(
        val prompt: IncomingTransferPrompt,
        val tempFile: File,
        val raf: RandomAccessFile,
        val receivedChunks: BooleanArray,
        val localPeerId: String,
        val senderIp: String,
        val startTimeMs: Long = System.currentTimeMillis()
    ) {
        val bytesReceived = AtomicLong(0L)
        val recentBytes = AtomicLong(0L)
        val chunksReceived = java.util.concurrent.atomic.AtomicInteger(0)
        @Volatile var peakSpeedBps: Long = 0L
        @Volatile var currentSpeedBps: Long = 0L
    }

    private val lastUiProgressUpdate = ConcurrentHashMap<String, Long>()

    companion object {
        private const val TAG = "TransferManager"
        const val SLIDING_WINDOW_SIZE = 48
        const val CHUNK_TIMEOUT_MS = 3000L
        const val MAX_CHUNK_RETRIES = 3

        @Volatile private var INSTANCE: TransferManager? = null

        fun getInstance(context: Context? = null): TransferManager {
            val instance = INSTANCE ?: synchronized(this) {
                INSTANCE ?: TransferManager().also { INSTANCE = it }
            }
            if (context != null) {
                instance.init(context)
            }
            return instance
        }
    }

    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext

        // Listen for incoming transfer packets from LanSocketManager
        scope.launch {
            LanSocketManager.getInstance().incomingTransferPacketsFlow.collect { packet ->
                handleIncomingTransferPacket(packet)
            }
        }

        // Speed sampling ticker loop (every 500ms)
        scope.launch {
            while (isActive) {
                delay(500)
                updateSpeedMetrics()
            }
        }
    }

    fun startSendTransfer(
        fileUri: Uri,
        targetPeerId: String,
        targetIp: String = "",
        isViewOnly: Boolean = false,
        context: Context? = null
    ): String {
        if (context != null && appContext == null) {
            init(context)
        }
        val resolvedContext = appContext ?: context?.applicationContext ?: throw IllegalStateException("TransferManager not initialized")
        val transferId = UUID.randomUUID().toString()
        val localPeerId = UserIdentityManager.getInstance(resolvedContext).getPeerId()

        val (fileName, fileSize, mimeType) = resolveFileMetadata(resolvedContext, fileUri)
        val totalChunks = ((fileSize + ChunkManager.CHUNK_SIZE - 1) / ChunkManager.CHUNK_SIZE).toInt().coerceAtLeast(1)

        val job = TransferJob(
            id = transferId,
            sourcePeerId = localPeerId,
            destinationPeerId = targetPeerId,
            fileName = fileName,
            fileSize = fileSize,
            fileUri = fileUri,
            mimeType = mimeType,
            permission = if (isViewOnly) TransferPermission.VIEW_ONLY else TransferPermission.DOWNLOADABLE,
            priority = TransferPriority.FILE,
            state = TransferState.QUEUED,
            totalChunks = totalChunks,
            isOutgoing = true
        )
        addOrUpdateJob(job)

        try {
            val msgRepo = com.hypershare.db.MessageRepository(resolvedContext)
            msgRepo.insertMediaMessage(
                peerId = targetPeerId,
                messageId = transferId,
                isOutgoing = true,
                fileName = fileName,
                fileSize = fileSize,
                mimeType = mimeType,
                localUri = fileUri.toString(),
                transferId = transferId
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error inserting outgoing media message: ${e.message}", e)
        }

        val executionJob = scope.launch {
            executeOutboundTransfer(job, targetIp)
        }
        activeTransferJobs[transferId] = executionJob

        return transferId
    }

    private suspend fun executeOutboundTransfer(job: TransferJob, targetIp: String) {
        val context = appContext ?: return
        val transferId = job.id
        val fileUri = job.fileUri ?: throw IllegalStateException("File URI missing for transfer $transferId")

        try {
            // Keep state as QUEUED while negotiating transfer with peer

            // Step 1: Pre-calculate overall CRC32 (only dry-run read small files <= 2MB to start instant transmission)
            val (computedSize, fullCrc32) = if (job.fileSize in 1L..(2 * 1024 * 1024L)) {
                context.contentResolver.openInputStream(fileUri)?.use { stream ->
                    ChunkManager.computeStreamMetadata(stream)
                } ?: Pair(job.fileSize, 0L)
            } else if (job.fileSize > 0L) {
                Pair(job.fileSize, 0L)
            } else {
                context.contentResolver.openInputStream(fileUri)?.use { stream ->
                    ChunkManager.computeStreamMetadata(stream)
                } ?: throw IllegalStateException("Unable to open input stream for $fileUri")
            }

            val actualTotalChunks = ((computedSize + ChunkManager.CHUNK_SIZE - 1) / ChunkManager.CHUNK_SIZE).toInt().coerceAtLeast(1)
            updateJob(transferId) {
                it.copy(
                    fileSize = computedSize,
                    totalChunks = actualTotalChunks,
                    fullCrc32 = fullCrc32
                )
            }

            // Step 2: Send TRANSFER_INIT
            val initPayload = TransferInitPayload(
                transferId = transferId,
                fileName = job.fileName,
                fileSize = computedSize,
                totalChunks = actualTotalChunks,
                fullCrc32 = fullCrc32,
                isViewOnly = job.permission == TransferPermission.VIEW_ONLY,
                mimeType = job.mimeType
            )

            val initPacket = PacketBuilder.buildTransferInitPacket(
                sourcePeerId = job.sourcePeerId,
                destPeerId = job.destinationPeerId,
                seqNum = LanSocketManager.getInstance().nextSequenceNumber(),
                payload = initPayload
            )

            val responseDeferred = CompletableDeferred<Boolean>()
            outboundResponseDeferreds[transferId] = responseDeferred

            LanSocketManager.getInstance().sendPacket(initPacket, job.destinationPeerId, targetIp)
            Log.d(TAG, "Sent TRANSFER_INIT for $transferId, waiting for peer response...")

            // Wait for receiver to Accept or Reject (timeout after 45s)
            val accepted = withTimeoutOrNull(45000L) { responseDeferred.await() }
            outboundResponseDeferreds.remove(transferId)

            if (accepted != true) {
                val reason = if (accepted == null) "Transfer request timed out" else "Transfer declined by peer"
                updateJobState(transferId, TransferState.FAILED, reason = reason)
                return
            }

            Log.d(TAG, "Peer accepted transfer $transferId. Beginning sliding window chunk transmission...")
            updateJobState(transferId, TransferState.IN_PROGRESS)

            // Step 3: Sliding Window Transmission
            val ackMap = ConcurrentHashMap<Int, CompletableDeferred<Unit>>()
            outboundAckMaps[transferId] = ackMap
            val windowSemaphore = Semaphore(SLIDING_WINDOW_SIZE)
            val startTimeMs = System.currentTimeMillis()

            context.contentResolver.openInputStream(fileUri)?.use { rawInputStream ->
                java.io.BufferedInputStream(rawInputStream, 256 * 1024).use { inputStream ->
                    for (chunkIndex in 0 until actualTotalChunks) {
                        windowSemaphore.acquire()

                        val chunk = ChunkManager.readNextChunk(
                            inputStream = inputStream,
                            transferId = transferId,
                            chunkIndex = chunkIndex,
                            totalChunks = actualTotalChunks
                        ) ?: break

                        val ackDeferred = CompletableDeferred<Unit>()
                        ackMap[chunkIndex] = ackDeferred

                        // Launch transmission and timeout/retry for this chunk
                        scope.launch(Dispatchers.IO) {
                            var attempt = 0
                            var acknowledged = false

                            while (attempt < MAX_CHUNK_RETRIES && !acknowledged && isActive) {
                                attempt++
                                val chunkPacket = PacketBuilder.buildDataChunkPacket(
                                    sourcePeerId = job.sourcePeerId,
                                    destPeerId = job.destinationPeerId,
                                    seqNum = LanSocketManager.getInstance().nextSequenceNumber(),
                                    chunk = chunk
                                )
                                LanSocketManager.getInstance().sendPacketDirect(chunkPacket, job.destinationPeerId, targetIp)

                                val acked = withTimeoutOrNull(CHUNK_TIMEOUT_MS) { ackDeferred.await() }
                                if (acked != null) {
                                    acknowledged = true
                                    windowSemaphore.release()
                                    ackMap.remove(chunkIndex)

                                    // Update byte counters
                                    updateJobProgress(transferId, chunk.payload.size.toLong())
                                } else {
                                    Log.w(TAG, "Chunk $chunkIndex timeout (attempt $attempt/$MAX_CHUNK_RETRIES), retransmitting...")
                                }
                            }

                            if (!acknowledged) {
                                Log.e(TAG, "Chunk $chunkIndex failed after $MAX_CHUNK_RETRIES retries")
                                windowSemaphore.release()
                                cancelTransfer(transferId, "Network timeout on chunk $chunkIndex")
                            }
                        }
                    }
                }
            }

            // Step 4: Wait for all chunks to be ACKed
            while (ackMap.isNotEmpty() && currentCoroutineContext().isActive) {
                delay(100)
            }
            outboundAckMaps.remove(transferId)

            // Step 5: Send TRANSFER_COMPLETE
            val completePacket = PacketBuilder.buildTransferCompletePacket(
                sourcePeerId = job.sourcePeerId,
                destPeerId = job.destinationPeerId,
                seqNum = LanSocketManager.getInstance().nextSequenceNumber(),
                transferId = transferId
            )
            LanSocketManager.getInstance().sendPacket(completePacket, job.destinationPeerId, targetIp)

            val totalDurationMs = (System.currentTimeMillis() - startTimeMs).coerceAtLeast(1)
            val currentJob = _jobs.value.find { it.id == transferId } ?: job
            val avgSpeed = (currentJob.fileSize * 1000L) / totalDurationMs

            updateJob(transferId) {
                it.copy(
                    state = TransferState.COMPLETED,
                    bytesTransferred = it.fileSize,
                    transferredChunks = actualTotalChunks,
                    durationMs = totalDurationMs,
                    averageSpeedBps = avgSpeed,
                    currentSpeedBps = 0L,
                    estimatedRemainingSeconds = 0L
                )
            }

            _completionEvents.emit(
                TransferCompletedEvent(
                    transferId = transferId,
                    fileName = job.fileName,
                    fileSize = currentJob.fileSize,
                    durationMs = totalDurationMs,
                    averageSpeedBps = avgSpeed,
                    peakSpeedBps = currentJob.peakSpeedBps,
                    fullCrc32 = fullCrc32,
                    isOutgoing = true
                )
            )
            updateNotification(job.fileName, 100, "Transfer complete (${formatSpeed(avgSpeed)})", ongoing = false)
            Log.d(TAG, "Outbound transfer $transferId successfully completed in ${totalDurationMs}ms (Avg: ${formatSpeed(avgSpeed)})")

            try {
                val msgRepo = com.hypershare.db.MessageRepository(context)
                msgRepo.updateMessageStatus(transferId, com.hypershare.ui.chat.MessageStatus.DELIVERED)
            } catch (_: Exception) {}

        } catch (e: Exception) {
            Log.e(TAG, "Outbound transfer $transferId error: ${e.message}", e)
            updateJobState(transferId, TransferState.FAILED, reason = e.message ?: "Unknown error")
        } finally {
            activeTransferJobs.remove(transferId)
            outboundAckMaps.remove(transferId)
            outboundResponseDeferreds.remove(transferId)
        }
    }

    private fun handleIncomingTransferPacket(packet: Packet) {
        val context = appContext ?: return

        when (packet.header.type) {
            PacketType.TRANSFER_INIT -> {
                try {
                    val initPayload = TransferInitPayload.fromByteArray(packet.payload)
                    Log.d(TAG, "Received TRANSFER_INIT for ${initPayload.fileName} (${initPayload.fileSize} bytes) from ${packet.header.sourcePeerId}")

                    val prompt = IncomingTransferPrompt(
                        transferId = initPayload.transferId,
                        senderPeerId = packet.header.sourcePeerId,
                        fileName = initPayload.fileName,
                        fileSize = initPayload.fileSize,
                        totalChunks = initPayload.totalChunks,
                        fullCrc32 = initPayload.fullCrc32,
                        isViewOnly = initPayload.isViewOnly,
                        mimeType = initPayload.mimeType
                    )

                    // Register job in queued state
                    val job = TransferJob(
                        id = prompt.transferId,
                        sourcePeerId = prompt.senderPeerId,
                        destinationPeerId = packet.header.destinationPeerId,
                        fileName = prompt.fileName,
                        fileSize = prompt.fileSize,
                        mimeType = prompt.mimeType,
                        permission = if (prompt.isViewOnly) TransferPermission.VIEW_ONLY else TransferPermission.DOWNLOADABLE,
                        state = TransferState.QUEUED,
                        totalChunks = prompt.totalChunks,
                        fullCrc32 = prompt.fullCrc32,
                        isOutgoing = false
                    )
                    addOrUpdateJob(job)
                    _incomingPrompts.tryEmit(prompt)

                    appContext?.let { ctx ->
                        try {
                            val msgRepo = com.hypershare.db.MessageRepository(ctx)
                            msgRepo.insertMediaMessage(
                                peerId = prompt.senderPeerId,
                                messageId = prompt.transferId,
                                isOutgoing = false,
                                fileName = prompt.fileName,
                                fileSize = prompt.fileSize,
                                mimeType = prompt.mimeType,
                                localUri = null,
                                transferId = prompt.transferId
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Error inserting incoming media message: ${e.message}", e)
                        }
                    }

                    Log.d(TAG, "Auto-accepting incoming transfer ${prompt.transferId} from ${prompt.senderPeerId}")
                    acceptTransfer(prompt.transferId)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing TRANSFER_INIT: ${e.message}", e)
                }
            }

            PacketType.TRANSFER_ACCEPT -> {
                val payload = TransferResponsePayload.fromByteArray(packet.payload)
                outboundResponseDeferreds[payload.transferId]?.complete(true)
            }

            PacketType.TRANSFER_REJECT -> {
                val payload = TransferResponsePayload.fromByteArray(packet.payload)
                outboundResponseDeferreds[payload.transferId]?.complete(false)
            }

            PacketType.DATA -> {
                try {
                    val chunk = TransferChunkPayload.fromByteArray(packet.payload)
                    handleIncomingDataChunk(chunk, packet.header.sourcePeerId)
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling incoming DATA chunk: ${e.message}", e)
                }
            }

            PacketType.TRANSFER_CHUNK_ACK -> {
                try {
                    val ackPayload = ChunkAckPayload.fromByteArray(packet.payload)
                    outboundAckMaps[ackPayload.transferId]?.get(ackPayload.chunkIndex)?.complete(Unit)
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling chunk ACK: ${e.message}", e)
                }
            }

            PacketType.TRANSFER_COMPLETE -> {
                val transferId = String(packet.payload, Charsets.UTF_8)
                finalizeInboundTransfer(transferId)
            }

            PacketType.TRANSFER_CANCEL -> {
                try {
                    val cancelPayload = TransferCancelPayload.fromByteArray(packet.payload)
                    cancelTransfer(cancelPayload.transferId, "Cancelled by remote peer: ${cancelPayload.reason}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling cancel packet: ${e.message}", e)
                }
            }

            else -> {}
        }
    }

    fun acceptTransfer(transferId: String) {
        val context = appContext ?: return
        val job = _jobs.value.find { it.id == transferId } ?: return

        scope.launch {
            try {
                val transfersDir = File(context.cacheDir, "transfers").apply { mkdirs() }
                val tempFile = File(transfersDir, "${transferId}.tmp")
                if (tempFile.exists()) tempFile.delete()

                val raf = RandomAccessFile(tempFile, "rw")
                raf.setLength(job.fileSize) // Pre-allocate disk space

                val prompt = IncomingTransferPrompt(
                    transferId = transferId,
                    senderPeerId = job.sourcePeerId,
                    fileName = job.fileName,
                    fileSize = job.fileSize,
                    totalChunks = job.totalChunks,
                    fullCrc32 = job.fullCrc32,
                    isViewOnly = job.permission == TransferPermission.VIEW_ONLY,
                    mimeType = job.mimeType
                )

                val senderIp = LanSocketManager.getInstance().getPeerIp(job.sourcePeerId)
                val localPeerId = UserIdentityManager.getInstance(context).getPeerId()

                val session = InboundSession(
                    prompt = prompt,
                    tempFile = tempFile,
                    raf = raf,
                    receivedChunks = BooleanArray(job.totalChunks),
                    localPeerId = localPeerId,
                    senderIp = senderIp
                )
                inboundSessions[transferId] = session

                updateJobState(transferId, TransferState.IN_PROGRESS)

                // Send TRANSFER_ACCEPT packet
                val acceptPacket = PacketBuilder.buildTransferResponsePacket(
                    sourcePeerId = localPeerId,
                    destPeerId = job.sourcePeerId,
                    seqNum = LanSocketManager.getInstance().nextSequenceNumber(),
                    transferId = transferId,
                    accepted = true
                )
                LanSocketManager.getInstance().sendPacket(acceptPacket, job.sourcePeerId, senderIp)
                Log.d(TAG, "Accepted incoming transfer $transferId from ${job.sourcePeerId} ($senderIp)")

            } catch (e: Exception) {
                Log.e(TAG, "Error accepting transfer $transferId: ${e.message}", e)
                rejectTransfer(transferId, "Local error creating destination file")
            }
        }
    }

    fun rejectTransfer(transferId: String, reason: String = "Declined by user") {
        val job = _jobs.value.find { it.id == transferId } ?: return
        scope.launch {
            val rejectPacket = PacketBuilder.buildTransferResponsePacket(
                sourcePeerId = job.destinationPeerId,
                destPeerId = job.sourcePeerId,
                seqNum = LanSocketManager.getInstance().nextSequenceNumber(),
                transferId = transferId,
                accepted = false,
                reason = reason
            )
            val senderIp = LanSocketManager.getInstance().getPeerIp(job.sourcePeerId)
            LanSocketManager.getInstance().sendPacket(rejectPacket, job.sourcePeerId, senderIp)
            updateJobState(transferId, TransferState.FAILED, reason = reason)
        }
    }

    private fun handleIncomingDataChunk(chunk: DataChunk, senderPeerId: String) {
        val session = inboundSessions[chunk.transferId] ?: return

        // 1. Validate chunk CRC32
        val crc = java.util.zip.CRC32()
        crc.update(chunk.payload)
        if (crc.value != chunk.crc32Checksum) {
            Log.w(TAG, "Chunk ${chunk.chunkIndex} CRC32 mismatch! Dropping chunk.")
            return
        }

        // 2. Write chunk direct to disk via RandomAccessFile
        var isNewChunk = false
        synchronized(session.raf) {
            ChunkManager.writeChunkDirect(session.raf, chunk)
            if (!session.receivedChunks[chunk.chunkIndex]) {
                session.receivedChunks[chunk.chunkIndex] = true
                isNewChunk = true
                session.bytesReceived.addAndGet(chunk.payload.size.toLong())
                session.recentBytes.addAndGet(chunk.payload.size.toLong())
            }
        }

        // 3. Send back TRANSFER_CHUNK_ACK
        val ackPacket = PacketBuilder.buildChunkAckPacket(
            sourcePeerId = session.localPeerId,
            destPeerId = senderPeerId,
            seqNum = LanSocketManager.getInstance().nextSequenceNumber(),
            transferId = chunk.transferId,
            chunkIndex = chunk.chunkIndex
        )
        LanSocketManager.getInstance().sendPacketDirect(ackPacket, senderPeerId, session.senderIp)

        // 4. Update UI progress throttled
        if (isNewChunk) {
            val totalReceivedChunks = session.chunksReceived.incrementAndGet()
            val now = System.currentTimeMillis()
            val lastUpdate = lastUiProgressUpdate[chunk.transferId] ?: 0L
            if (now - lastUpdate >= 120L || totalReceivedChunks >= session.prompt.totalChunks) {
                lastUiProgressUpdate[chunk.transferId] = now
                val received = session.bytesReceived.get()
                updateJob(chunk.transferId) {
                    it.copy(
                        bytesTransferred = received,
                        transferredChunks = totalReceivedChunks
                    )
                }
            }
        }
    }

    private fun finalizeInboundTransfer(transferId: String) {
        val session = inboundSessions.remove(transferId) ?: return
        val context = appContext ?: return

        scope.launch(Dispatchers.IO) {
            try {
                synchronized(session.raf) {
                    session.raf.close()
                }

                // 1. Verify Full File CRC32 Checksum (if full checksum was provided)
                if (session.prompt.fullCrc32 != 0L) {
                    val isIntegrityValid = ChunkManager.verifyFileChecksum(session.tempFile, session.prompt.fullCrc32)
                    if (!isIntegrityValid) {
                        Log.e(TAG, "Full-file CRC32 verification failed for $transferId! Corrupted transfer.")
                        session.tempFile.delete()
                        updateJobState(transferId, TransferState.FAILED, reason = "Integrity check failed (CRC32 mismatch)")
                        return@launch
                    }
                    Log.d(TAG, "Full-file CRC32 verified successfully for $transferId!")
                }

                // 2. Move file to destination (MediaStore Movies/Pictures/Music/Downloads or Cache/view_only)
                val localUri = saveIncomingFile(
                    context = context,
                    sourceFile = session.tempFile,
                    fileName = session.prompt.fileName,
                    mimeType = session.prompt.mimeType,
                    isViewOnly = session.prompt.isViewOnly
                )

                try {
                    val msgRepo = com.hypershare.db.MessageRepository(context)
                    msgRepo.updateMediaMessageUri(transferId, localUri.toString())
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating media message localUri: ${e.message}", e)
                }

                val durationMs = (System.currentTimeMillis() - session.startTimeMs).coerceAtLeast(1)
                val avgSpeed = (session.prompt.fileSize * 1000L) / durationMs

                updateJob(transferId) {
                    it.copy(
                        state = TransferState.COMPLETED,
                        fileUri = localUri,
                        bytesTransferred = it.fileSize,
                        transferredChunks = session.prompt.totalChunks,
                        durationMs = durationMs,
                        averageSpeedBps = avgSpeed,
                        peakSpeedBps = session.peakSpeedBps,
                        currentSpeedBps = 0L,
                        estimatedRemainingSeconds = 0L
                    )
                }

                _completionEvents.emit(
                    TransferCompletedEvent(
                        transferId = transferId,
                        fileName = session.prompt.fileName,
                        fileSize = session.prompt.fileSize,
                        durationMs = durationMs,
                        averageSpeedBps = avgSpeed,
                        peakSpeedBps = session.peakSpeedBps,
                        fullCrc32 = session.prompt.fullCrc32,
                        isOutgoing = false,
                        localUri = localUri,
                        isViewOnly = session.prompt.isViewOnly
                    )
                )

                updateNotification(session.prompt.fileName, 100, "Received (${formatSpeed(avgSpeed)})", ongoing = false)
                Log.d(TAG, "Inbound transfer $transferId finished in ${durationMs}ms (Avg: ${formatSpeed(avgSpeed)})")

            } catch (e: Exception) {
                Log.e(TAG, "Error finalizing transfer $transferId: ${e.message}", e)
                session.tempFile.delete()
                updateJobState(transferId, TransferState.FAILED, reason = e.message ?: "File save error")
            }
        }
    }

    fun cancelTransfer(transferId: String, reason: String = "Cancelled by user") {
        activeTransferJobs[transferId]?.cancel()
        activeTransferJobs.remove(transferId)

        val job = _jobs.value.find { it.id == transferId }
        if (job != null) {
            scope.launch {
                val context = appContext
                val localPeerId = if (context != null) UserIdentityManager.getInstance(context).getPeerId() else job.sourcePeerId
                val destPeerId = if (job.isOutgoing) job.destinationPeerId else job.sourcePeerId
                val cancelPacket = PacketBuilder.buildTransferCancelPacket(
                    sourcePeerId = localPeerId,
                    destPeerId = destPeerId,
                    seqNum = LanSocketManager.getInstance().nextSequenceNumber(),
                    transferId = transferId,
                    reason = reason
                )
                val targetIp = LanSocketManager.getInstance().getPeerIp(destPeerId)
                LanSocketManager.getInstance().sendPacket(cancelPacket, destPeerId, targetIp)
            }
        }

        val session = inboundSessions.remove(transferId)
        if (session != null) {
            try { session.raf.close() } catch (_: Exception) {}
            session.tempFile.delete()
        }

        updateJobState(transferId, TransferState.FAILED, reason = reason)
    }

    fun dismissJob(jobId: String) {
        val list = _jobs.value.toMutableList()
        list.removeAll { it.id == jobId }
        _jobs.value = list
    }

    private fun updateSpeedMetrics() {
        // Update outbound jobs
        _jobs.value.filter { it.state == TransferState.IN_PROGRESS }.forEach { job ->
            val elapsed = (System.currentTimeMillis() - job.createdAt).coerceAtLeast(1)
            val avgSpeed = (job.bytesTransferred * 1000L) / elapsed
            val remainingBytes = (job.fileSize - job.bytesTransferred).coerceAtLeast(0)
            val currentSpeed = job.currentSpeedBps.coerceAtLeast(1)
            val etaSec = if (currentSpeed > 0) remainingBytes / currentSpeed else 0L

            updateJob(job.id) {
                it.copy(
                    averageSpeedBps = avgSpeed,
                    estimatedRemainingSeconds = etaSec
                )
            }
        }

        // Update inbound sessions
        inboundSessions.forEach { (transferId, session) ->
            val bytesInWindow = session.recentBytes.getAndSet(0L)
            val currentSpeed = bytesInWindow * 2L // 500ms * 2 = 1 sec
            session.currentSpeedBps = currentSpeed
            if (currentSpeed > session.peakSpeedBps) {
                session.peakSpeedBps = currentSpeed
            }

            val elapsed = (System.currentTimeMillis() - session.startTimeMs).coerceAtLeast(1)
            val totalReceived = session.bytesReceived.get()
            val avgSpeed = (totalReceived * 1000L) / elapsed
            val remainingBytes = (session.prompt.fileSize - totalReceived).coerceAtLeast(0)
            val etaSec = if (currentSpeed > 0) remainingBytes / currentSpeed else 0L

            updateJob(transferId) {
                it.copy(
                    currentSpeedBps = currentSpeed,
                    averageSpeedBps = avgSpeed,
                    peakSpeedBps = session.peakSpeedBps,
                    estimatedRemainingSeconds = etaSec
                )
            }

            val pct = if (session.prompt.fileSize > 0) ((totalReceived * 100) / session.prompt.fileSize).toInt() else 0
            updateNotification(session.prompt.fileName, pct, "Receiving: ${formatSpeed(currentSpeed)} (Avg: ${formatSpeed(avgSpeed)})")
        }
    }

    private fun updateJobProgress(transferId: String, deltaBytes: Long) {
        val now = System.currentTimeMillis()
        val lastUpdate = lastUiProgressUpdate[transferId] ?: 0L
        val shouldUpdateUi = (now - lastUpdate >= 120L)

        updateJob(transferId) { current ->
            val newBytes = (current.bytesTransferred + deltaBytes).coerceAtMost(current.fileSize)
            val newChunks = current.transferredChunks + 1
            val elapsed = (now - current.createdAt).coerceAtLeast(1)
            val avgSpeed = (newBytes * 1000L) / elapsed

            // Rolling speed estimation based on recent delta
            val currentSpeed = (deltaBytes * 1000L / 100L).coerceAtLeast(avgSpeed)
            val peakSpeed = maxOf(current.peakSpeedBps, currentSpeed)

            if (shouldUpdateUi || newBytes >= current.fileSize) {
                lastUiProgressUpdate[transferId] = now
            }

            current.copy(
                bytesTransferred = newBytes,
                transferredChunks = newChunks,
                currentSpeedBps = currentSpeed,
                averageSpeedBps = avgSpeed,
                peakSpeedBps = peakSpeed
            )
        }
    }

    private fun addOrUpdateJob(job: TransferJob) {
        val list = _jobs.value.toMutableList()
        val idx = list.indexOfFirst { it.id == job.id }
        if (idx != -1) list[idx] = job else list.add(0, job)
        _jobs.value = list
    }

    private fun updateJob(jobId: String, transform: (TransferJob) -> TransferJob) {
        val list = _jobs.value.toMutableList()
        val idx = list.indexOfFirst { it.id == jobId }
        if (idx != -1) {
            list[idx] = transform(list[idx])
            _jobs.value = list
        }
    }

    private fun updateJobState(jobId: String, state: TransferState, reason: String? = null) {
        updateJob(jobId) { it.copy(state = state, failureReason = reason) }
    }

    private fun resolveFileMetadata(context: Context, uri: Uri): Triple<String, Long, String> {
        var name = "file_${System.currentTimeMillis()}"
        var size = 0L
        val mime = context.contentResolver.getType(uri) ?: "*/*"

        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIdx != -1) {
                        cursor.getString(nameIdx)?.let { name = it }
                    }
                    if (sizeIdx != -1) {
                        size = cursor.getLong(sizeIdx)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed resolving metadata for $uri from cursor", e)
        }

        // Fallback for providers that return 0 or null for OpenableColumns.SIZE (e.g. some document providers or PDFs)
        if (size <= 0L) {
            try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    val statSize = pfd.statSize
                    if (statSize > 0L) {
                        size = statSize
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed resolving file size from openFileDescriptor for $uri", e)
            }
        }

        if (size < 0L) size = 0L
        return Triple(name, size, mime)
    }

    private fun updateNotification(fileName: String, progressPct: Int, speedText: String, ongoing: Boolean = true) {
        val context = appContext ?: return
        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val channelId = "hypershare_transfer_channel"

            val notification = NotificationCompat.Builder(context, channelId)
                .setContentTitle(fileName)
                .setContentText(speedText)
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setProgress(100, progressPct, false)
                .setOngoing(ongoing)
                .setOnlyAlertOnce(true)
                .build()

            manager.notify(1003, notification)
        } catch (_: Exception) {}
    }

    fun formatSpeed(bps: Long): String {
        return when {
            bps >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB/s", bps.toDouble() / (1024 * 1024))
            bps >= 1024 -> String.format(java.util.Locale.US, "%.1f KB/s", bps.toDouble() / 1024)
            else -> "$bps B/s"
        }
    }

    fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f GB", bytes.toDouble() / (1024 * 1024 * 1024))
            bytes >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB", bytes.toDouble() / (1024 * 1024))
            bytes >= 1024 -> String.format(java.util.Locale.US, "%.1f KB", bytes.toDouble() / 1024)
            else -> "$bytes B"
        }
    }

    private fun saveIncomingFile(context: Context, sourceFile: File, fileName: String, mimeType: String, isViewOnly: Boolean): Uri {
        if (isViewOnly) {
            val viewOnlyDir = File(context.cacheDir, "view_only").apply { mkdirs() }
            val finalFile = File(viewOnlyDir, fileName)
            if (finalFile.exists()) finalFile.delete()
            sourceFile.inputStream().use { input ->
                finalFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            sourceFile.delete()
            return try {
                androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    finalFile
                )
            } catch (e: Exception) {
                Uri.fromFile(finalFile)
            }
        }

        val ext = fileName.substringAfterLast('.', "").lowercase()
        val resolvedMime = mimeType.ifEmpty {
            if (ext.isNotEmpty()) MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*" else "*/*"
        }
        val isVideo = resolvedMime.startsWith("video/") || listOf("mp4", "mkv", "avi", "mov", "webm", "3gp", "flv", "ts").contains(ext)
        val isImage = resolvedMime.startsWith("image/") || listOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic").contains(ext)
        val isAudio = resolvedMime.startsWith("audio/") || listOf("mp3", "wav", "m4a", "aac", "flac", "ogg").contains(ext)

        // 1. Primary path: Save directly to Android MediaStore on API 29+ (Android 10+)
        // Videos and Images saved here immediately appear in the device's Gallery / Photos app
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val (collectionUri, relativeDir) = when {
                isVideo -> Pair(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "${Environment.DIRECTORY_MOVIES}/HyperShare")
                isImage -> Pair(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "${Environment.DIRECTORY_PICTURES}/HyperShare")
                isAudio -> Pair(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, "${Environment.DIRECTORY_MUSIC}/HyperShare")
                else -> Pair(MediaStore.Downloads.EXTERNAL_CONTENT_URI, "${Environment.DIRECTORY_DOWNLOADS}/HyperShare")
            }

            try {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, resolvedMime)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativeDir)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val itemUri = resolver.insert(collectionUri, values)
                if (itemUri != null) {
                    resolver.openOutputStream(itemUri)?.use { out ->
                        sourceFile.inputStream().use { input ->
                            input.copyTo(out)
                        }
                    }
                    values.clear()
                    values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(itemUri, values, null, null)
                    sourceFile.delete()
                    Log.d(TAG, "Successfully saved $fileName to MediaStore: $itemUri")
                    return itemUri
                }
            } catch (e: Exception) {
                Log.w(TAG, "MediaStore insert failed for $fileName: ${e.message}, falling back to direct storage", e)
            }
        }

        // 2. Direct storage fallback: stream bytes into Downloads/HyperShare or app external files
        val primaryDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "HyperShare"
        )
        val targetDir = if (primaryDir.exists() || primaryDir.mkdirs()) {
            primaryDir
        } else {
            File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir, "HyperShare").apply { mkdirs() }
        }

        val finalFile = File(targetDir, fileName)
        if (finalFile.exists()) finalFile.delete()
        sourceFile.inputStream().use { input ->
            finalFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        sourceFile.delete()

        // Index with MediaScanner so Gallery / Photos picks it up
        MediaScannerConnection.scanFile(context, arrayOf(finalFile.absolutePath), arrayOf(resolvedMime), null)

        return try {
            androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                finalFile
            )
        } catch (e: Exception) {
            Log.w(TAG, "FileProvider URI generation fallback: ${e.message}")
            Uri.fromFile(finalFile)
        }
    }
}
